package dev.avsong.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * 主题状态 = 深浅开关 + 颜色 seed(夜间/浅色都随 seed 派生)。
 * dark 由功能栏图标切换;seed 由「主题色」弹窗选(预设或轮盘自定义色相)。
 * 轮盘拖动时走 [previewHue] 只改内存(拖动每帧写盘太重),松手 [persistHue] 落盘。
 */
class ThemeStore(context: Context) {

    data class ThemeState(
        val dark: Boolean = true,
        val seed: ThemeSeed = ThemeSeed.YELLOW,
        val customHue: Float? = null, // 非 null 时覆盖 seed
    )

    private val datastore = context.applicationContext.dataStore
    private val _state = MutableStateFlow(ThemeState())
    val state: StateFlow<ThemeState> = _state.asStateFlow()

    suspend fun load() {
        val p = datastore.data.first()
        val dark: Boolean
        val seed: ThemeSeed
        val legacyMode = p[KEY_LEGACY_MODE]
        if (legacyMode != null) {
            // 旧「ThemeMode 单值」迁移:DARK→柠檬黄(当时的黑黄夜间),其余映射为浅色 seed
            dark = legacyMode == "DARK"
            seed = when (legacyMode) {
                "CREAM" -> ThemeSeed.CREAM
                "INDIGO" -> ThemeSeed.INDIGO
                "GREEN" -> ThemeSeed.GREEN
                else -> ThemeSeed.YELLOW
            }
        } else {
            dark = p[KEY_DARK] ?: true
            seed = p[KEY_SEED]?.let { stored ->
                runCatching { ThemeSeed.valueOf(stored) }.getOrNull()
            } ?: ThemeSeed.YELLOW
        }
        _state.value = ThemeState(dark = dark, seed = seed, customHue = p[KEY_HUE])
    }

    suspend fun setDark(value: Boolean) {
        datastore.edit { it[KEY_DARK] = value }
        _state.update { it.copy(dark = value) }
    }

    suspend fun setSeed(value: ThemeSeed) {
        datastore.edit {
            it[KEY_SEED] = value.name
            it.remove(KEY_HUE) // 回到预设即清除自定义色相
        }
        _state.update { it.copy(seed = value, customHue = null) }
    }

    /** 轮盘拖动中的实时预览,不写盘。 */
    fun previewHue(hue: Float) {
        _state.update { it.copy(customHue = hue) }
    }

    /** 拖动结束,把当前预览的色相落盘。 */
    suspend fun persistHue() {
        val hue = _state.value.customHue ?: return
        datastore.edit { it[KEY_HUE] = hue }
    }

    private companion object {
        val Context.dataStore by preferencesDataStore(name = "ui_prefs")
        val KEY_DARK = booleanPreferencesKey("dark_mode")
        val KEY_SEED = stringPreferencesKey("light_seed")
        val KEY_HUE = floatPreferencesKey("custom_hue")
        val KEY_LEGACY_MODE = stringPreferencesKey("theme_mode") // 旧单值主题,读后不再写
    }
}
