package dev.avsong.ui.login

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.avsong.data.auth.AuthStore
import dev.avsong.data.auth.CookieRefresh
import dev.avsong.data.auth.QrLogin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Generating : LoginUiState
    data class Showing(val qr: Bitmap, val status: String) : LoginUiState
    data object Expired : LoginUiState
    data class LoggedIn(
        val uname: String,
        val vipLabel: String?,
        val face: String? = null,
        val level: Int = 0,
        val coin: Double = 0.0,
    ) : LoginUiState
    data class Failed(val message: String) : LoginUiState
}

class LoginViewModel(val authStore: AuthStore) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // 每次 VM 创建只刷新一次资料,防 collect→saveProfile→collect 死循环
    private var profileRefreshed = false

    init {
        viewModelScope.launch {
            authStore.state.collect { a ->
                if (a.isLoggedIn) {
                    _state.value = LoginUiState.LoggedIn(a.uname ?: "已登录", a.vipLabel, a.face, a.level, a.coin)
                    if (!profileRefreshed) {
                        profileRefreshed = true
                        // 先进「我的」tab 时:先尝试 Cookie 续期(临近过期才真轮换),再刷资料。
                        // 顺序不能反 —— 续期会换掉 SESSDATA,资料要用新会话去拉。
                        if (a.canRefresh) {
                            when (val r = CookieRefresh.checkAndRefresh(a)) {
                                is CookieRefresh.Result.Success -> {
                                    android.util.Log.d("AvSongAuth", "cookie refreshed, SESSDATA rotated")
                                    authStore.saveRefreshed(r.sessData, r.biliJct, r.dedeUserId, r.acTimeValue)
                                }
                                // NotNeeded(还新鲜)/ Failed(网络等):都不动本地状态,静默继续
                                else -> android.util.Log.d("AvSongAuth", "cookie refresh skipped: $r")
                            }
                        } else {
                            android.util.Log.d("AvSongAuth", "cookie refresh unavailable: no ac_time_value")
                        }
                        // 刷新资料:硬币/等级是活数据,也兜底旧会话没存的头像等字段。
                        // ⚠ 会话已被服务端作废时(nav 回 -101)清本地登录态,回未登录 UI ——
                        // 否则本地只剩空资料,显示「? + Lv0 + 硬币 0」的半坏状态,比直接提示未登录更迷惑(用户实测反馈)
                        when (val r = QrLogin.fetchProfileResult()) {
                            is QrLogin.ProfileResult.Ok -> authStore.saveProfile(
                                r.profile.uname, r.profile.face, r.profile.vipLabel, r.profile.level, r.profile.coin,
                            )
                            QrLogin.ProfileResult.Invalid -> authStore.logout()
                            QrLogin.ProfileResult.Error -> Unit // 网络/风控失败:保留本地会话,下次再试
                        }
                    }
                }
            }
        }
    }

    fun startLogin() {
        _state.value = LoginUiState.Generating
        viewModelScope.launch {
            try {
                val qr = QrLogin.generate()
                _state.value = LoginUiState.Showing(renderQr(qr.url), "用哔哩哔哩 App 扫码登录")
                pollLoop(qr.key)
            } catch (e: Exception) {
                _state.value = LoginUiState.Failed(e.message ?: "二维码生成失败")
            }
        }
    }

    private suspend fun pollLoop(key: String) {
        var scanned = false
        // 二维码约 180s 过期;1.5s 轮询一次
        repeat(130) {
            delay(1500)
            when (val r = QrLogin.poll(key)) {
                is QrLogin.PollResult.Success -> {
                    authStore.saveCookies(r.sessData, r.biliJct, r.dedeUserId, r.acTimeValue)
                    val profile = QrLogin.fetchProfile()
                    profile?.let { authStore.saveProfile(it.uname, it.face, it.vipLabel, it.level, it.coin) }
                    _state.value = LoginUiState.LoggedIn(
                        profile?.uname ?: "已登录",
                        profile?.vipLabel,
                        profile?.face,
                        profile?.level ?: 0,
                        profile?.coin ?: 0.0,
                    )
                    return
                }
                QrLogin.PollResult.Expired -> {
                    _state.value = LoginUiState.Expired
                    return
                }
                QrLogin.PollResult.Scanned -> {
                    scanned = true
                    (_state.value as? LoginUiState.Showing)?.let {
                        _state.value = it.copy(status = "已扫码,请在手机上确认")
                    }
                }
                QrLogin.PollResult.Waiting -> {
                    (_state.value as? LoginUiState.Showing)?.let {
                        _state.value = it.copy(status = if (scanned) "已扫码,请在手机上确认" else "用哔哩哔哩 App 扫码登录")
                    }
                }
            }
        }
        _state.value = LoginUiState.Expired
    }

    fun logout() {
        viewModelScope.launch {
            authStore.logout()
            _state.value = LoginUiState.Idle
        }
    }

    private fun renderQr(content: String): Bitmap {
        val size = 512
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
