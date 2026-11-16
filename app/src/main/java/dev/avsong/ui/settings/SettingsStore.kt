package dev.avsong.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
 * 「我的」页设置项:默认音质 + 播放历史上限 + 倍速播放。
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
        // 默认求最高音质:大会员直接拿到 FLAC;非大会员在选路时自动降级到可用最高(192K),
        // 不会比默认 192K 更差,也省得大会员还要手动进设置切一次。
        val quality: Quality = Quality.LOSSLESS,
        val historyLimit: Int = 50, // 0=不限
        /** 播放页热评展示:0=弹幕(飘动),1=列表(底部抽屉 3/4 屏)。持久化。 */
        val danmakuMode: Int = 0,
        /** 播放页热评展示开关(持久化;默认开)。 */
        val danmakuEnabled: Boolean = true,
        /** 倍速播放(持久化;用户要求放「我的」页设置,是全局偏好而非每首歌的临时态)。 */
        val playbackSpeed: Float = 1.0f,
        /** 下载并发上限(1~3,持久化;默认串行。并发越高越容易撞 B 站限流 -412)。 */
        val downloadConcurrency: Int = 1,
    )

    /** 倍速档位(「我的」页可选;循环切换同序)。 */
    val SPEED_STEPS = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)

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
    private val KEY_SPEED = floatPreferencesKey("playback_speed")
    private val KEY_DOWNLOAD_CONCURRENCY = intPreferencesKey("download_concurrency")

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    suspend fun load() {
        val p = datastore.data.first()
        _state.value = SettingsState(
            quality = p[KEY_QUALITY]?.let { id -> Quality.entries.firstOrNull { it.id == id } }
                ?: Quality.LOSSLESS,
            historyLimit = p[KEY_HISTORY] ?: 50,
            danmakuEnabled = p[KEY_DANMAKU] ?: true,
            danmakuMode = p[KEY_DANMAKU_MODE] ?: 0,
            playbackSpeed = p[KEY_SPEED]?.takeIf { it > 0f } ?: 1.0f,
            downloadConcurrency = (p[KEY_DOWNLOAD_CONCURRENCY] ?: 1).coerceIn(1, 3),
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

    /** 下载并发(1~3):只影响之后的任务调度,已跑的 worker 不动。 */
    fun setDownloadConcurrency(value: Int) {
        val v = value.coerceIn(1, 3)
        _state.update { it.copy(downloadConcurrency = v) }
        scope.launch { datastore.edit { it[KEY_DOWNLOAD_CONCURRENCY] = v } }
    }

    /** 倍速变更:落盘并即时应用到播放器(播放中也能立刻变速)。 */
    fun setPlaybackSpeed(speed: Float, apply: (Float) -> Unit) {
        _state.update { it.copy(playbackSpeed = speed) }
        scope.launch { datastore.edit { it[KEY_SPEED] = speed } }
        apply(speed)
    }
}
