package dev.avsong.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 播放现场持久化:队列(bvid+元数据)+ 当前索引 + 进度 + 随机/循环模式。
 * 重启后 MusicService 读取恢复。存 bili:// 语义的 bvid,播放时仍现取直链(天然规避过期)。
 */
class PlaybackPersistenceStore(context: Context) {

    @Serializable
    data class PersistedItem(val bvid: String, val title: String, val artist: String, val cover: String?)

    @Serializable
    data class Snapshot(
        val items: List<PersistedItem> = emptyList(),
        val startIndex: Int = 0,
        val positionMs: Long = 0,
        val shuffle: Boolean = false,
        val repeatMode: Int = 0,
        val playWhenReady: Boolean = false,
    ) {
        val isEmpty get() = items.isEmpty()
    }

    private val datastore = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(snapshot: Snapshot) {
        datastore.edit { it[KEY] = json.encodeToString(Snapshot.serializer(), snapshot) }
    }

    suspend fun load(): Snapshot = try {
        val raw = datastore.data.first()[KEY] ?: return Snapshot()
        json.decodeFromString(Snapshot.serializer(), raw)
    } catch (e: Exception) {
        Snapshot()
    }

    suspend fun clear() {
        datastore.edit { it.remove(KEY) }
    }

    private companion object {
        val Context.dataStore by preferencesDataStore(name = "playback_state")
        val KEY = stringPreferencesKey("snapshot")
    }
}
