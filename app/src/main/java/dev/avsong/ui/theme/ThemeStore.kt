package dev.avsong.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * 日夜间模式持久化。默认深色(品牌黑黄设计稿即深色)。
 * 2026-10-11 起支持「我的」页功能栏手动切换,不再锁死深色。
 */
class ThemeStore(context: Context) {

    private val datastore = context.applicationContext.dataStore
    private val _dark = MutableStateFlow(true)
    val dark: StateFlow<Boolean> = _dark.asStateFlow()

    suspend fun load() {
        _dark.value = datastore.data.first()[KEY_DARK] ?: true
    }

    suspend fun setDark(value: Boolean) {
        datastore.edit { it[KEY_DARK] = value }
        _dark.value = value
    }

    private companion object {
        val Context.dataStore by preferencesDataStore(name = "ui_prefs")
        val KEY_DARK = booleanPreferencesKey("dark_mode")
    }
}
