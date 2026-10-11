package dev.avsong.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * 登录态:存 B 站 Cookie 关键字段 + 用户名。
 * 内存快照 [state] 供拦截器同步读取;持久化走 DataStore。
 */
class AuthStore(context: Context) {

    data class AuthState(
        val sessData: String? = null,
        val biliJct: String? = null,
        val dedeUserId: String? = null,
        val uname: String? = null,
        val vipLabel: String? = null, // 会员等级描述,如 "年度大会员"
    ) {
        val isLoggedIn: Boolean get() = !sessData.isNullOrBlank()
        val isVip: Boolean get() = !vipLabel.isNullOrBlank()
        fun cookieHeader(): String? = buildString {
            if (!sessData.isNullOrBlank()) append("SESSDATA=").append(sessData)
            if (!biliJct.isNullOrBlank()) append("; bili_jct=").append(biliJct)
            if (!dedeUserId.isNullOrBlank()) append("; DedeUserID=").append(dedeUserId)
        }.ifBlank { null }
    }

    private val datastore = context.applicationContext.dataStore
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun load() {
        val p = datastore.data.first()
        _state.value = AuthState(
            sessData = p[KEY_SESS],
            biliJct = p[KEY_JCT],
            dedeUserId = p[KEY_UID],
            uname = p[KEY_UNAME],
            vipLabel = p[KEY_VIP],
        )
    }

    suspend fun saveCookies(sessData: String, biliJct: String, dedeUserId: String) {
        datastore.edit {
            it[KEY_SESS] = sessData
            it[KEY_JCT] = biliJct
            it[KEY_UID] = dedeUserId
        }
        load()
    }

    suspend fun saveProfile(uname: String?, vipLabel: String?) {
        datastore.edit {
            if (uname != null) it[KEY_UNAME] = uname else it.remove(KEY_UNAME)
            if (vipLabel != null) it[KEY_VIP] = vipLabel else it.remove(KEY_VIP)
        }
        load()
    }

    suspend fun logout() {
        datastore.edit {
            it.remove(KEY_SESS); it.remove(KEY_JCT); it.remove(KEY_UID)
            it.remove(KEY_UNAME); it.remove(KEY_VIP)
        }
        load()
    }

    private companion object {
        val Context.dataStore by preferencesDataStore(name = "auth")
        val KEY_SESS = stringPreferencesKey("sessdata")
        val KEY_JCT = stringPreferencesKey("bili_jct")
        val KEY_UID = stringPreferencesKey("dede_user_id")
        val KEY_UNAME = stringPreferencesKey("uname")
        val KEY_VIP = stringPreferencesKey("vip_label")
    }
}
