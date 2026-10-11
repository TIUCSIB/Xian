package dev.avsong.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * 本地搜索历史:去重、最新在前、上限 20 条。DataStore 持久化(单个分隔串保序)。
 * (借鉴 BiliTune 的本地历史;纯本地,不碰官方云端"最近搜索")
 */
class SearchHistoryStore(context: Context) {

    private val datastore = context.applicationContext.dataStore
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    suspend fun load() {
        val raw = datastore.data.first()[KEY] ?: ""
        _history.value = raw.split(SEP).filter { it.isNotBlank() }
    }

    suspend fun add(term: String) {
        val t = term.trim()
        if (t.isEmpty()) return
        val next = (listOf(t) + _history.value.filter { it != t }).take(20)
        _history.value = next
        datastore.edit { it[KEY] = next.joinToString(SEP) }
    }

    suspend fun remove(term: String) {
        val next = _history.value.filter { it != term }
        _history.value = next
        datastore.edit { it[KEY] = next.joinToString(SEP) }
    }

    suspend fun clear() {
        _history.value = emptyList()
        datastore.edit { it.remove(KEY) }
    }

    private companion object {
        const val SEP = "␟" // unit separator,不会出现在正常搜索词里
        val Context.dataStore by preferencesDataStore(name = "search_history")
        val KEY = stringPreferencesKey("history")
    }
}
