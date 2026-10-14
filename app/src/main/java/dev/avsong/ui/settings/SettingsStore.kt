package dev.avsong.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 「我的」页设置项:默认音质 + 播放历史上限。
 * object 单例:BiliSongRepository/PlayHistoryStore 都是 object,选路/裁剪时直接读内存流,
 * 不经 Application 注入(与 BiliSongRepository 同款模式)。
 * DataStore 持久化;[init]+[load] 由 AvSongApp.onCreate 调一次。
 */
object SettingsStore {

    /** 音质档:数值=playurl 的 dash.audio id,按「目标档位取可用最高」降级匹配。 */
    enum class Quality(val id: Int, val label: String) {
        LOW(30216, "流畅 64K"),
        MEDIUM(30232, "清晰 132K"),
        HIGH(30280, "高清 192K"),
        LOSSLESS(0, "无损 FLAC"), // 0=dash.flac;仅大会员可拿到,普通账号自动降级
    }

    data class SettingsState(
        val quality: Quality = Quality.HIGH,
        val historyLimit: Int = 50, // 0=不限
        /** 播放页热评展示:0=弹幕(飘动),1=列表(底部抽屉 3/4 屏)。持久化。 */
        val danmakuMode: Int = 0,
        /** 播放页热评展示开关(持久化;默认开)。 */
        val danmakuEnabled: Boolean = true,
    )

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    // 落盘是 fire-and-forget:IO 域 + SupervisorJob(与 MusicService 同款)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // object 无法用构造参数拿 Context,由 init 存一份(DataStore 委托同样需要 Context 扩展)
    private var appContext: Context? = null
    private val datastore get() = appContext!!.dataStore

    private val Context.dataStore by preferencesDataStore(name = "settings")
    private val KEY_QUALITY = intPreferencesKey("audio_quality")
    private val KEY_HISTORY = intPreferencesKey("history_limit")
    private val KEY_DANMAKU = booleanPreferencesKey("danmaku_enabled")
    private val KEY_DANMAKU_MODE = intPreferencesKey("danmaku_mode")

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    suspend fun load() {
        val p = datastore.data.first()
        _state.value = SettingsState(
            quality = p[KEY_QUALITY]?.let { id -> Quality.entries.firstOrNull { it.id == id } }
                ?: Quality.HIGH,
            historyLimit = p[KEY_HISTORY] ?: 50,
            danmakuEnabled = p[KEY_DANMAKU] ?: true,
            danmakuMode = p[KEY_DANMAKU_MODE] ?: 0,
        )
    }

    fun setQuality(value: Quality) {
        _state.update { it.copy(quality = value) }
        scope.launch { datastore.edit { it[KEY_QUALITY] = value.id } }
    }

    fun setHistoryLimit(value: Int) {
        _state.update { it.copy(historyLimit = value) }
        scope.launch { datastore.edit { it[KEY_HISTORY] = value } }
    }

    fun setDanmakuEnabled(value: Boolean) {
        _state.update { it.copy(danmakuEnabled = value) }
        scope.launch { datastore.edit { it[KEY_DANMAKU] = value } }
    }

    fun setDanmakuMode(value: Int) {
        _state.update { it.copy(danmakuMode = value) }
        scope.launch { datastore.edit { it[KEY_DANMAKU_MODE] = value } }
    }
}
