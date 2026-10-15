package dev.avsong.data

import android.content.Context
import dev.avsong.AvSongApp
import dev.avsong.data.remote.AudioStream
import dev.avsong.domain.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 下载任务管理:串行队列 + 进度 + 取消/重试。
 *
 * 串行(并发=1)而非并行:单任务已能跑满带宽,并行只会同时抢直链有效期与磁盘 IO,
 * 还会让「进度」一屏多条难读。失败任务保留在队列里(可重试),成功后自动移除
 * (成果进 DownloadedStore,列表页可见,任务条不留残影)。
 *
 * 进度按「已收字节 / Content-Length」计算;直链无 Content-Length 时给不确定态(-1)。
 * 队列状态全在内存:app 被杀后排队的任务消失(已完成的文件在,不受影响)——
 * 下载队列不值得做断点续盘,成本远超收益。
 */
class DownloadManager(context: Context) {

    enum class State { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

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
    /** 进行中的任务快照(排队/运行/失败;完成即移除)。最新加入在前。 */
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    /** 终点反馈(下载页之外的用户也需要知道结果):失败立即报,成功由 drain 收尾时汇总。 */
    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()
    fun consumeEvent() {
        _events.value = null
    }

    private var worker: Job? = null

    /** 入队一首。已在队列(任何状态)或已下载过则跳过;返回是否真的入了队。 */
    fun enqueue(song: Song): Boolean {
        val current = _tasks.value
        if (current.any { it.bvid == song.bvid }) return false
        if (store.state.value.any { it.bvid == song.bvid }) return false
        _tasks.update { listOf(Task(song.bvid, song.title, song.artist, song.coverUrl, song.durationSec)) + it }
        startWorkerIfNeeded()
        return true
    }

    /** 批量入队(歌单/合集一键下载);返回实际入队数。 */
    fun enqueueAll(songs: List<Song>): Int = songs.count { enqueue(it) }

    fun cancel(bvid: String) {
        _tasks.update { list ->
            list.mapNotNull { t ->
                when {
                    t.bvid == bvid && t.state == State.RUNNING -> {
                        // 运行中:标记取消,worker 协程感知后自行退出
                        worker?.cancel()
                        null
                    }
                    t.bvid == bvid -> null // 排队/失败:直接移除
                    else -> t
                }
            }
        }
    }

    /** 失败任务重试:重置为排队并重启 worker。 */
    fun retry(bvid: String) {
        _tasks.update { list ->
            list.map { if (it.bvid == bvid && it.state == State.FAILED) it.copy(state = State.QUEUED, progress = -1, error = null) else it }
        }
        startWorkerIfNeeded()
    }

    /** 清掉已完成/已取消/失败的残留(全部入 DownloadedStore 的成功不在此列)。 */
    fun clearFinished() {
        _tasks.update { list -> list.filter { it.state == State.QUEUED || it.state == State.RUNNING } }
    }

    private fun startWorkerIfNeeded() {
        if (worker?.isActive == true) return
        worker = scope.launch { drain() }
    }

    /** 逐个取队首 QUEUED 任务执行;任务被取消(worker job cancel)时静默退出。
     *  收尾时若本轮有成功,汇总一条「N 首下载完成」(批量 20 首不能连发 20 条提示)。 */
    private suspend fun drain() {
        var succeeded = 0
        while (true) {
            val next = _tasks.value.firstOrNull { it.state == State.QUEUED } ?: break
            update(next.bvid) { it.copy(state = State.RUNNING, progress = -1, receivedBytes = 0, totalBytes = -1) }
            try {
                execute(next)
                succeeded++
                // 成功:从任务列表移除(成果在 DownloadedStore)
                _tasks.update { list -> list.filterNot { it.bvid == next.bvid } }
            } catch (e: kotlinx.coroutines.CancellationException) {
                update(next.bvid) {
                    it.copy(state = State.CANCELLED, error = "已取消")
                }
                _tasks.update { list -> list.filterNot { it.bvid == next.bvid } }
                throw e // 让 job 真正结束,startWorkerIfNeeded 下次再拉起
            } catch (e: Exception) {
                update(next.bvid) { it.copy(state = State.FAILED, error = e.message ?: e.javaClass.simpleName) }
                _events.value = "下载失败:${next.title}"
            }
        }
        if (succeeded > 0) {
            _events.value = if (succeeded == 1) "下载完成" else "已下载 $succeeded 首"
        }
    }

    private suspend fun execute(task: Task) {
        val url = dev.avsong.data.BiliSongRepository.audioUrlBlocking(task.bvid)
        val request = okhttp3.Request.Builder().url(url).build()
        dev.avsong.data.BiliHttp.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val dir = store.musicDir() ?: throw IOException("外部存储不可用")
            val safe = task.title.replace(Regex("""[\/:*?"<>|]"""), "_").take(80)
            val file = java.io.File(dir, "${task.bvid}_$safe.m4a")
            val body = resp.body ?: throw IOException("空响应体")
            val total = body.contentLength()
            var received = 0L
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        // 协程取消时 read 不会中断,主动检查
                        if (kotlin.coroutines.coroutineContext[Job]?.isCancelled == true) {
                            file.delete()
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
    }

    private fun update(bvid: String, transform: (Task) -> Task) {
        _tasks.update { list -> list.map { if (it.bvid == bvid) transform(it) else it } }
    }
}
