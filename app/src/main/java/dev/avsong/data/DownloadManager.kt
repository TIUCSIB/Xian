package dev.avsong.data

import android.content.Context
import dev.avsong.AvSongApp
import dev.avsong.domain.Song
import dev.avsong.ui.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * 下载任务管理:并发队列(上限 = 设置「下载并发」1~3)+ 进度 + 暂停/续传 + 取消/重试。
 *
 * 断点续传:边下边写 `<bvid>_<title>.m4a.part`,暂停保留半成品;恢复时按已有字节数发
 * `Range: bytes=N-` 续传(服务端回 200 = 不支持续传,清空重下;416 = 半成品长度不合法,重来一次),
 * 完成后重命名为正式文件名并写 DownloadedStore。
 * ⚠ 队列状态全在内存:进程被杀后排队的任务消失(已完成的文件在);残留 `.part` 由本类
 * 构造时清理(此时不可能有在跑的任务)。
 */
class DownloadManager(context: Context) {

    enum class State { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELLED }

    data class Task(
        val bvid: String,
        val title: String,
        val artist: String,
        val coverUrl: String?,
        val durationSec: Long,
        val state: State = State.QUEUED,
        /** 0..100;-1 = 未知大小的不确定态。 */
        val progress: Int = -1,
        val receivedBytes: Long = 0,
        val totalBytes: Long = -1,
        val error: String? = null,
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store: DownloadedStore get() = (appContext as AvSongApp).downloadedStore

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    /** 进行中的任务快照(排队/运行/暂停/失败;完成即移除)。最新加入在前。 */
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    /** 终点反馈(下载页之外的用户也需要知道结果):失败立即报,整批跑完汇总一条。 */
    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()
    fun consumeEvent() {
        _events.value = null
    }

    /** 正在跑的任务(bvid → job)。UI 线程入队/暂停/取消与 IO 收尾都会动它,读改写一律进 [lock]。 */
    private val lock = Any()
    private val runningJobs = mutableMapOf<String, Job>()
    private val succeededInBatch = AtomicInteger(0)

    init {
        // 进程刚起:不可能有活着的任务,残留 .part 都是上次被杀留下的,清掉免得占空间
        scope.launch {
            runCatching {
                store.musicDir()?.listFiles { f -> f.isFile && f.name.endsWith(".part") }
                    ?.forEach { it.delete() }
            }
        }
    }

    /** 入队一首。已在队列(任何状态)或已下载过则跳过;返回是否真的入了队。 */
    fun enqueue(song: Song): Boolean {
        val current = _tasks.value
        if (current.any { it.bvid == song.bvid }) return false
        if (store.state.value.any { it.bvid == song.bvid }) return false
        _tasks.update { listOf(Task(song.bvid, song.title, song.artist, song.coverUrl, song.durationSec)) + it }
        pump()
        return true
    }

    /** 批量入队(歌单/合集一键下载);返回实际入队数。 */
    fun enqueueAll(songs: List<Song>): Int = songs.count { enqueue(it) }

    /** 取消 = 不要了:任务移出列表、半成品删掉。 */
    fun cancel(bvid: String) {
        val removed = _tasks.value.firstOrNull { it.bvid == bvid } ?: return
        val job = synchronized(lock) {
            _tasks.update { list -> list.filterNot { it.bvid == bvid } }
            runningJobs[bvid]
        }
        job?.cancel()
        scope.launch { runCatching { partFileOf(removed)?.delete() } }
    }

    /** 暂停:保留 .part 与进度;正在跑的任务取消协程(读循环立刻退出并按现有字节落盘)。 */
    fun pause(bvid: String) {
        var job: Job? = null
        synchronized(lock) {
            val t = _tasks.value.firstOrNull { it.bvid == bvid } ?: return
            when (t.state) {
                State.RUNNING -> {
                    update(bvid) { it.copy(state = State.PAUSED) }
                    job = runningJobs[bvid]
                }
                State.QUEUED -> update(bvid) { it.copy(state = State.PAUSED) }
                else -> Unit
            }
        }
        job?.cancel()
    }

    /** 继续:回到排队,按 .part 已有字节 Range 续传。 */
    fun resume(bvid: String) {
        _tasks.update { list ->
            list.map { if (it.bvid == bvid && it.state == State.PAUSED) it.copy(state = State.QUEUED) else it }
        }
        pump()
    }

    /** 失败任务重试:重置为排队并续上已有半成品。 */
    fun retry(bvid: String) {
        _tasks.update { list ->
            list.map { if (it.bvid == bvid && it.state == State.FAILED) it.copy(state = State.QUEUED, progress = -1, error = null) else it }
        }
        pump()
    }

    /** 清掉失败/已取消的残留(排队/运行/暂停保留)。 */
    fun clearFinished() {
        _tasks.update { list ->
            list.filter { it.state == State.QUEUED || it.state == State.RUNNING || it.state == State.PAUSED }
        }
    }

    /** 拉起任务直到并发上限;同一时刻最多 N 个 job。 */
    private fun pump() {
        synchronized(lock) {
            val max = SettingsStore.state.value.downloadConcurrency.coerceIn(1, 3)
            while (runningJobs.size < max) {
                val next = _tasks.value.firstOrNull { it.state == State.QUEUED } ?: break
                update(next.bvid) { it.copy(state = State.RUNNING, error = null) }
                runningJobs[next.bvid] = startTask(next)
            }
        }
        maybeEmitSummary()
    }

    private fun startTask(task: Task): Job = scope.launch {
        try {
            execute(task)
            // 成功:从任务列表移除(成果在 DownloadedStore)
            _tasks.update { list -> list.filterNot { it.bvid == task.bvid } }
            succeededInBatch.incrementAndGet()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 暂停:任务仍在列表且态为 PAUSED,保留 .part;取消:任务已被移出,清掉半成品
            val paused = _tasks.value.firstOrNull { it.bvid == task.bvid }?.state == State.PAUSED
            if (paused) {
                update(task.bvid) { it.copy(state = State.PAUSED) }
            } else {
                runCatching { partFileOf(task)?.delete() }
            }
        } catch (e: Exception) {
            update(task.bvid) { it.copy(state = State.FAILED, error = e.message ?: e.javaClass.simpleName) }
            _events.value = "下载失败:${task.title}"
        } finally {
            synchronized(lock) { runningJobs.remove(task.bvid) }
            pump()
        }
    }

    /** 整批跑完(无排队/运行中)时汇总一条提示;暂停的任务不算未完,不用等它。 */
    private fun maybeEmitSummary() {
        if (_tasks.value.any { it.state == State.QUEUED || it.state == State.RUNNING }) return
        val n = succeededInBatch.getAndSet(0)
        if (n > 0) _events.value = if (n == 1) "下载完成" else "已下载 $n 首"
    }

    private fun fileNameOf(task: Task): String {
        val safe = task.title.replace(Regex("""[\/:*?"<>|]"""), "_").take(80)
        return "${task.bvid}_$safe.m4a"
    }

    private fun partFileOf(task: Task): File? = store.musicDir()?.let { File(it, fileNameOf(task) + ".part") }

    private suspend fun execute(task: Task) {
        val url = dev.avsong.data.BiliSongRepository.audioUrlBlocking(task.bvid)
        val dir = store.musicDir() ?: throw IOException("外部存储不可用")
        val fileName = fileNameOf(task)
        val part = File(dir, "$fileName.part")

        var attempt = 0
        while (true) {
            attempt++
            val startAt = if (part.exists()) part.length() else 0L
            val request = okhttp3.Request.Builder().url(url)
                .apply { if (startAt > 0) header("Range", "bytes=$startAt-") }
                .build()
            var retryNeeded = false
            dev.avsong.data.BiliHttp.client.newCall(request).execute().use { resp ->
                if (resp.code == 416 && attempt == 1) {
                    // 半成品长度对不上(文件被换/上次写坏):清掉重来一次
                    part.delete()
                    retryNeeded = true
                    return@use
                }
                if (!resp.isSuccessful && resp.code != 206) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("空响应体")
                // 206=按 Range 续传;200=服务端没理 Range(或本来就没有半成品),从头写
                val append = if (resp.code == 206 && startAt > 0) startAt else 0L
                if (resp.code != 206 && startAt > 0) part.delete()
                val remaining = body.contentLength()
                val total = if (remaining > 0) append + remaining else -1L
                var received = append
                body.byteStream().use { input ->
                    FileOutputStream(part, append > 0).use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            // 协程取消时 read 不会中断,主动检查;.part 交给外层决定保/删
                            if (!currentCoroutineContext().isActive) {
                                throw kotlinx.coroutines.CancellationException("cancelled")
                            }
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            received += n
                            val pct = if (total > 0) (received * 100 / total).toInt() else -1
                            update(task.bvid) { it.copy(progress = pct, receivedBytes = received, totalBytes = total) }
                        }
                    }
                }
            }
            if (retryNeeded) continue
            break
        }

        // 完成:半成品改名成正式文件(rename 失败就复制,跨文件系统兜底)
        val file = File(dir, fileName)
        if (file.exists()) file.delete()
        if (!part.renameTo(file)) {
            part.copyTo(file, overwrite = true)
            part.delete()
        }
        store.add(
            DownloadedTrack(
                bvid = task.bvid,
                title = task.title,
                artist = task.artist,
                coverUrl = task.coverUrl,
                fileName = file.name,
                durationSec = task.durationSec,
                sizeBytes = file.length(),
                downloadedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun update(bvid: String, transform: (Task) -> Task) {
        _tasks.update { list -> list.map { if (it.bvid == bvid) transform(it) else it } }
    }
}
