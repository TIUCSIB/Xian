package dev.avsong.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.avsong.AvSongApp
import dev.avsong.ui.login.LoginUiState
import dev.avsong.ui.rememberLoginViewModel
import kotlinx.coroutines.launch

/**
 * 「我的」tab(底部导航第四项):账户信息头(登录态/去登录/退出)。
 * 歌单类内容(UP 主合集 + 收藏夹)2026-10-11 起移到「歌单」tab,这里只剩账户。
 * 头部版式参考 B 站 app:左头像,右侧昵称 + 等级/会员徽章 + 硬币;无卡片背景。
 */
@Composable
fun LibraryScreen(
    onLogin: () -> Unit,
) {
    val loginVm = rememberLoginViewModel()
    val loginState by loginVm.state.collectAsState()
    val app = LocalContext.current.applicationContext as AvSongApp
    val isDark by app.themeStore.dark.collectAsState()
    val scope = rememberCoroutineScope()
    val loggedIn = loginState as? LoginUiState.LoggedIn

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        FunctionBar(
            isLoggedIn = loggedIn?.uname != null,
            isDark = isDark,
            onToggleTheme = { scope.launch { app.themeStore.setDark(!isDark) } },
            onScan = {
                if (loggedIn?.uname == null) onLogin()
                else Toast.makeText(app, "扫码功能开发中", Toast.LENGTH_SHORT).show()
            },
        )
        AccountHeader(loginState, onLogin = onLogin, onLogout = loginVm::logout)
    }
}

/** 功能栏:右端日夜间切换 + 扫码(B 站 app「我的」页同款布局,裸图标无底色)。 */
@Composable
private fun FunctionBar(
    isLoggedIn: Boolean,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onScan: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 2.dp, top = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标显示「可切换到的模式」:深色中显示太阳
        IconButton(onClick = onToggleTheme) {
            Icon(
                if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                contentDescription = if (isDark) "切换日间模式" else "切换夜间模式",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onScan) {
            Icon(
                Icons.Rounded.QrCodeScanner,
                contentDescription = "扫一扫",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 账户头:登录态显示头像/昵称/等级/会员/硬币,未登录点击进登录页。纯色扁平、无卡片底。 */
@Composable
private fun AccountHeader(login: LoginUiState, onLogin: () -> Unit, onLogout: () -> Unit) {
    val loggedIn = login as? LoginUiState.LoggedIn
    val uname = loggedIn?.uname
    val vip = loggedIn?.vipLabel

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = uname == null, onClick = onLogin)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像:有真实头像用头像,否则黄色圆 + 用户名首字(未登录用问号位)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    when {
                        loggedIn?.face != null -> MaterialTheme.colorScheme.surfaceVariant
                        uname != null -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val face = loggedIn?.face
            if (face != null) {
                AsyncImage(
                    model = face,
                    contentDescription = "头像",
                    modifier = Modifier.size(56.dp).clip(CircleShape),
                )
            } else {
                Text(
                    uname?.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (uname != null) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    uname ?: "点击登录 B 站账号",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (uname != null) {
                    Spacer(Modifier.width(8.dp))
                    ProfileChip("Lv${loggedIn.level}", filled = true)
                }
            }
            Spacer(Modifier.height(4.dp))
            if (uname != null) {
                // 会员状态独占一行(昵称与硬币之间):大会员黄描边高亮,非大会员灰描边
                ProfileChip(vip ?: "正式会员", filled = false, vip = vip != null)
                Spacer(Modifier.height(4.dp))
                Text(
                    "硬币 ${formatCoin(loggedIn.coin)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "大会员账号可解锁更高音质",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (uname != null) {
            IconButton(onClick = onLogout) {
                Icon(
                    Icons.Rounded.Logout,
                    contentDescription = "退出登录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 硬币显示:整数不带小数点,有小数保留(nav 的 money 是 double,实测可能带小数)。 */
private fun formatCoin(coin: Double) =
    if (coin % 1.0 == 0.0) coin.toInt().toString() else coin.toString()

/**
 * 徽章胶囊。filled=实心黄(Lv 等级,高亮);描边式(会员状态):大会员黄描边黄字,
 * 正式会员灰描边灰字。border 是用户点名的例外(全局扁平禁边框不含此徽章)。
 */
@Composable
private fun ProfileChip(text: String, filled: Boolean, vip: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    val contentColor = when {
        filled -> MaterialTheme.colorScheme.onPrimary
        vip -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val base = if (filled) {
        Modifier.clip(shape).background(MaterialTheme.colorScheme.primary)
    } else {
        Modifier
            .clip(shape)
            .border(1.dp, contentColor.copy(alpha = 0.5f), shape)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = contentColor,
        modifier = base.padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
