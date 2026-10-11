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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class HistoryEntry(
    val bvid: String,
    val title: String,
    val artist: String,
    val coverUrl: String? = null,
    val durationSec: Long = 0,
)

private val Context.playHistoryDataStore by preferencesDataStore(name = "play_history")

/**
 * 本 app 的播放历史:每次起播记录一首,去重置顶、上限 50,DataStore 持久化。
 * 不用 B 站 history 接口 —— 那是「观看历史」,app 内的播放不会上报成 B 站播放,
 * 所以本地自己记快照(bvid/title/artist/封面/时长),离线也能看。
 */
class PlayHistoryStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val datastore = context.applicationContext.playHistoryDataStore
    private val _history = MutableStateFlow<List<Song>>(emptyList())
    val history: StateFlow<List<Song>> = _history.asStateFlow()

    suspend fun load() {
        val raw = datastore.data.first()[KEY] ?: "[]"
        val entries = runCatching {
            json.decodeFromString<List<HistoryEntry>>(raw)
        }.getOrDefault(emptyList())
        _history.value = entries.map { it.toSong() }
    }

    /** 起播即记录:同曲去重置顶,超出上限丢弃最旧的。 */
    suspend fun record(song: Song) {
        if (song.bvid.isBlank() || song.title.isBlank()) return
        val entry = HistoryEntry(song.bvid, song.title, song.artist, song.coverUrl, song.durationSec)
        // 旧列表先转回 entry 再拼接,避免 List<Song>/List<HistoryEntry> 混成 List<Any>
        val next = (listOf(entry) + _history.value.map { it.toEntry() }.filter { it.bvid != entry.bvid })
            .take(50)
        _history.value = next.map { it.toSong() }
        datastore.edit { it[KEY] = json.encodeToString(next) }
    }

    private fun Song.toEntry() =
        HistoryEntry(bvid, title, artist, coverUrl, durationSec)

    private fun HistoryEntry.toSong() = Song(
        bvid = bvid,
        cid = null,
        aid = 0,
        title = title,
        artist = artist,
        coverUrl = coverUrl,
        durationSec = durationSec,
    )

    private companion object {
        val KEY = stringPreferencesKey("entries_json")
    }
}
