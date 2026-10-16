package dev.avsong.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.avsong.domain.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 本地歌单里的一首歌。存完整歌曲快照(含 aid)而不是只存 bvid ——
 * 播放/收藏/下载/歌词链路零网络开销(收藏不用再 fetchAid 补 aid)。
 * 与 [Song] 的映射见 [toSong]/[Song.toLocalTrack];快照字段是歌单创建时刻的,
 * 之后视频改标题不会跟着变(可接受:播放历史/下载记录同款取舍)。
 */
@Serializable
data class LocalTrack(
    val bvid: String,
    val title: String,
    val artist: String = "",
    val coverUrl: String? = null,
    val durationSec: Long = 0,
    val aid: Long = 0,
)

/** 本地自建歌单(纯 DataStore,不依赖登录、不走 B 站接口)。id 用创建时刻的时间戳。 */
@Serializable
data class LocalPlaylist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val tracks: List<LocalTrack> = emptyList(),
)

private val Context.localPlaylistsDataStore by preferencesDataStore(name = "local_playlists")

/**
 * 本地自建歌单仓库:歌单列表 + 每个歌单内的曲目,DataStore JSON 持久化,
 * 内存 StateFlow 供 UI 直读(最新创建在前)。歌单内按 bvid 去重(同一首只存一份)。
 * 与 CollectionStore/PlayHistoryStore 同款模式:load() 启动恢复,操作即落盘。
 */
class LocalPlaylistStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val datastore = context.applicationContext.localPlaylistsDataStore
    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    suspend fun load() {
        val raw = datastore.data.first()[KEY] ?: return
        _playlists.value = runCatching {
            json.decodeFromString(ListSerializer(LocalPlaylist.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    /** 新建歌单,返回新歌单 id(调用方常需要立刻跳详情页/弹窗里直接勾上)。 */
    suspend fun create(name: String): Long {
        val id = System.currentTimeMillis()
        persist(listOf(LocalPlaylist(id, name.trim(), System.currentTimeMillis())) + _playlists.value)
        return id
    }

    suspend fun delete(id: Long) {
        persist(_playlists.value.filterNot { it.id == id })
    }

    suspend fun rename(id: Long, name: String) {
        if (name.isBlank()) return
        persist(_playlists.value.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    /**
     * 加入歌曲(按 bvid 去重)。返回 false = 已在歌单里,未改动(UI 提示「已在歌单里」用)。
     */
    suspend fun addSong(id: Long, song: Song): Boolean {
        val pl = _playlists.value.firstOrNull { it.id == id } ?: return false
        if (pl.tracks.any { it.bvid == song.bvid }) return false
        persist(_playlists.value.map {
            if (it.id == id) it.copy(tracks = it.tracks + song.toLocalTrack()) else it
        })
        return true
    }

    suspend fun removeSong(id: Long, bvid: String) = removeSongs(id, listOf(bvid))

    /** 批量移除:一次 persist 一次 StateFlow 发射——逐首调 removeSong 会 N 次重组,列表抖动。 */
    suspend fun removeSongs(id: Long, bvids: Collection<String>) {
        if (bvids.isEmpty()) return
        val set = bvids.toSet()
        persist(_playlists.value.map {
            if (it.id == id) it.copy(tracks = it.tracks.filterNot { t -> t.bvid in set }) else it
        })
    }

    private suspend fun persist(list: List<LocalPlaylist>) {
        _playlists.value = list
        datastore.edit { it[KEY] = json.encodeToString(ListSerializer(LocalPlaylist.serializer()), list) }
    }

    private companion object {
        val KEY = stringPreferencesKey("playlists_json")
    }
}

fun LocalTrack.toSong() = Song(
    bvid = bvid,
    cid = null,
    aid = aid,
    title = title,
    artist = artist,
    coverUrl = coverUrl,
    durationSec = durationSec,
)

fun Song.toLocalTrack() = LocalTrack(
    bvid = bvid,
    title = title,
    artist = artist,
    coverUrl = coverUrl,
    durationSec = durationSec,
    aid = aid,
)
