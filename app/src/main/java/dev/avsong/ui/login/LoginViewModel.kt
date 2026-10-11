package dev.avsong.ui.login

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.avsong.data.auth.AuthStore
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
    data class LoggedIn(val uname: String, val vipLabel: String?) : LoginUiState
    data class Failed(val message: String) : LoginUiState
}

class LoginViewModel(private val authStore: AuthStore) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authStore.state.collect { a ->
                if (a.isLoggedIn) {
                    _state.value = LoginUiState.LoggedIn(a.uname ?: "已登录", a.vipLabel)
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
                    authStore.saveCookies(r.sessData, r.biliJct, r.dedeUserId)
                    val profile = QrLogin.fetchProfile()
                    profile?.let { authStore.saveProfile(it.uname, it.vipLabel) }
                    _state.value = LoginUiState.LoggedIn(profile?.uname ?: "已登录", profile?.vipLabel)
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
