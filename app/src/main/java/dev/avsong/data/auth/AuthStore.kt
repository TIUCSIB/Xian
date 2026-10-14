package dev.avsong.data.auth

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    /** 从用户粘贴内容解析出的 Cookie 三元组(手动填入 Cookie 登录用)。 */
    data class ParsedCookie(
        val sessData: String,
        val biliJct: String,
        val dedeUserId: String,
    )

    data class AuthState(
        val sessData: String? = null,
        val biliJct: String? = null,
        val dedeUserId: String? = null,
        /**
         * Cookie 续期令牌(ac_time_value)。**扫码登录不下发它**——它藏在轮询成功响应的
         * data.url 查询参数里(见 QrLogin.poll);没有它就无法续期,只能重新扫码。
         */
        val acTimeValue: String? = null,
        val uname: String? = null,
        val face: String? = null, // 头像 URL(已归一化 https)
        val vipLabel: String? = null, // 会员等级描述,如 "年度大会员"
        val level: Int = 0, // 账号等级 Lv0~Lv6
        val coin: Double = 0.0, // 硬币数
    ) {
        val isLoggedIn: Boolean get() = !sessData.isNullOrBlank()
        val isVip: Boolean get() = !vipLabel.isNullOrBlank()
        val canRefresh: Boolean get() = isLoggedIn && !acTimeValue.isNullOrBlank()
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
            acTimeValue = p[KEY_AC_TIME],
            uname = p[KEY_UNAME],
            face = p[KEY_FACE],
            vipLabel = p[KEY_VIP],
            level = p[KEY_LEVEL] ?: 0,
            coin = p[KEY_COIN] ?: 0.0,
        )
    }

    suspend fun saveCookies(sessData: String, biliJct: String, dedeUserId: String, acTimeValue: String? = null) {
        datastore.edit {
            it[KEY_SESS] = sessData
            it[KEY_JCT] = biliJct
            it[KEY_UID] = dedeUserId
            if (acTimeValue != null) it[KEY_AC_TIME] = acTimeValue else it.remove(KEY_AC_TIME)
        }
        load()
    }

    /**
     * 续期成功后整体替换三件套 + 新 refresh_token。
     * ⚠ 续期会轮换 SESSDATA/bili_jct,必须原子落盘,否则中途失败会留下半新半旧的 Cookie(两者不匹配 → 全部接口 403)。
     */
    suspend fun saveRefreshed(sessData: String, biliJct: String, dedeUserId: String, acTimeValue: String) {
        datastore.edit {
            it[KEY_SESS] = sessData
            it[KEY_JCT] = biliJct
            it[KEY_UID] = dedeUserId
            it[KEY_AC_TIME] = acTimeValue
        }
        load()
    }

    suspend fun saveProfile(
        uname: String?,
        face: String?,
        vipLabel: String?,
        level: Int = 0,
        coin: Double = 0.0,
    ) {
        datastore.edit {
            if (uname != null) it[KEY_UNAME] = uname else it.remove(KEY_UNAME)
            if (face != null) it[KEY_FACE] = face else it.remove(KEY_FACE)
            if (vipLabel != null) it[KEY_VIP] = vipLabel else it.remove(KEY_VIP)
            it[KEY_LEVEL] = level
            it[KEY_COIN] = coin
        }
        load()
    }

    suspend fun logout() {
        datastore.edit {
            it.remove(KEY_SESS); it.remove(KEY_JCT); it.remove(KEY_UID); it.remove(KEY_AC_TIME)
            it.remove(KEY_UNAME); it.remove(KEY_FACE); it.remove(KEY_VIP)
            it.remove(KEY_LEVEL); it.remove(KEY_COIN)
        }
        load()
    }

    private companion object {
        val Context.dataStore by preferencesDataStore(name = "auth")
        val KEY_SESS = stringPreferencesKey("sessdata")
        val KEY_JCT = stringPreferencesKey("bili_jct")
        val KEY_UID = stringPreferencesKey("dede_user_id")
        val KEY_AC_TIME = stringPreferencesKey("ac_time_value")
        val KEY_UNAME = stringPreferencesKey("uname")
        val KEY_FACE = stringPreferencesKey("face")
        val KEY_VIP = stringPreferencesKey("vip_label")
        val KEY_LEVEL = intPreferencesKey("level")
        val KEY_COIN = doublePreferencesKey("coin")
    }
}
