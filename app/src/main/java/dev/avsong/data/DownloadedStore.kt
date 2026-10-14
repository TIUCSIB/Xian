package dev.avsong.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * 一条已下载记录。fileName 是应用私有 MUSIC 目录下的文件名(带 bvid 前缀,天然去重键);
 * bvid 保留原视频 id —— 播放走 Song(audioUrl=file://…) 时 mediaId 仍是 bvid,
 * 收藏/歌词/历史链路全部照常工作。
 */
@Serializable
data class DownloadedTrack(
    val bvid: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val fileName: String,
    val durationSec: Long = 0,
    val sizeBytes: Long = 0,
    val downloadedAt: Long = 0,
)

/**
 * 已下载歌曲的元数据持久化(DataStore JSON 列表,最新在前,按 bvid 去重)。
 * 音频文件本身在 getExternalFilesDir(MUSIC)(应用私有,卸载即清);这里只存「哪首下了/叫什么/多大」。
 * load() 自愈:文件被系统/用户清掉后剔除条目;scanOrphans() 把「有文件无元数据」的旧下载
 * (文件名带 bvid 前缀之前的历史文件、或元数据写失败的文件)以文件名兜底合成条目,不丢歌。
 */
class DownloadedStore(context: Context) {

    private val appContext = context.applicationContext
    private val datastore = appContext.downloadedDataStore
    private val _state = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    val state: StateFlow<List<DownloadedTrack>> = _state.asStateFlow()

    /** 下载/播放共用的落盘目录(与 MainViewModel.downloadSong 同一处)。 */
    fun musicDir(): File? =
        appContext.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)

    fun fileOf(track: DownloadedTrack): File? = musicDir()?.resolve(track.fileName)

    suspend fun load() {
        val raw = datastore.data.first()[KEY] ?: return
        val list = runCatching {
            json.decodeFromString(ListSerializer(DownloadedTrack.serializer()), raw)
        }.getOrDefault(emptyList())
        // 自愈:文件已不在(手动清存储/清理工具)的条目剔除
        _state.value = list.filter { fileOf(it)?.exists() == true }
    }

    suspend fun add(track: DownloadedTrack) {
        persist(listOf(track) + _state.value.filterNot { it.bvid == track.bvid })
    }

    /** 删除记录 + 磁盘文件;返回是否删掉了文件(供 UI 反馈)。 */
    suspend fun remove(bvid: String): Boolean {
        val track = _state.value.firstOrNull { it.bvid == bvid } ?: return false
        val deleted = fileOf(track)?.takeIf { it.exists() }?.delete() ?: true
        persist(_state.value.filterNot { it.bvid == bvid })
        return deleted
    }

    /**
     * 与磁盘同步(下载页打开时调用):
     * 1) 剪除「有元数据无文件」的条目(用户/清理工具外部删过文件,「我的」页计数才不会虚高);
     * 2) 补「有文件无元数据」的孤儿(改动前的旧下载、或元数据写失败的文件),以文件名兜底合成条目。
     * 文件名约定 `<bvid>_<标题>.m4a`(bvid 以 BV/av 开头);不合约定的(历史遗留纯标题名)
     * 用整个文件名当 bvid 兜底 —— 反正 mediaId 只要唯一即可,离线播放不依赖它是真 bvid。
     */
    suspend fun scanOrphans() {
        val dir = musicDir() ?: return
        // 先剪失效
        val alive = _state.value.filter { fileOf(it)?.exists() == true }
        if (alive.size != _state.value.size) persist(alive)
        val known = alive.map { it.fileName }.toSet()
        val orphans = dir.listFiles { f -> f.isFile && (f.name.endsWith(".m4a") || f.name.endsWith(".mp4") || f.name.endsWith(".mka")) }
            ?.filter { it.name !in known }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        if (orphans.isEmpty()) return
        val made = orphans.map { f ->
            val stem = f.name.substringBeforeLast('.')
            val head = stem.substringBefore('_')
            val isBvid = head.startsWith("BV", ignoreCase = true) || head.startsWith("av", ignoreCase = true)
            DownloadedTrack(
                bvid = if (isBvid) head else stem,
                title = if (isBvid) stem.substringAfter('_').ifBlank { stem } else stem,
                artist = "",
                coverUrl = null,
                fileName = f.name,
                sizeBytes = f.length(),
                downloadedAt = f.lastModified(),
            )
        }
        persist(made + _state.value)
    }

    private suspend fun persist(list: List<DownloadedTrack>) {
        _state.value = list
        datastore.edit {
            it[KEY] = json.encodeToString(ListSerializer(DownloadedTrack.serializer()), list)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        val Context.downloadedDataStore by preferencesDataStore(name = "downloaded")
        val KEY = stringPreferencesKey("tracks")
    }
}
