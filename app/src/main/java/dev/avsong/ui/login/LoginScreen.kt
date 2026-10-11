package dev.avsong.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.avsong.ui.rememberLoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onBack: () -> Unit) {
    val vm = rememberLoginViewModel()
    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("登录 B 站账号", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                LoginUiState.Idle, is LoginUiState.Failed -> LoginCard {
                    if (s is LoginUiState.Failed) {
                        Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                    Text("扫码登录 B 站账号", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "普通用户音质上限 192K;大会员账号可解锁更高音质",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = vm::startLogin) { Text("生成登录二维码") }
                }

                LoginUiState.Generating -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)

                is LoginUiState.Showing -> LoginCard {
                    // 二维码垫在白底圆角框内,深浅色主题都可扫
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.padding(4.dp)) {
                        Image(
                            bitmap = s.qr.asImageBitmap(),
                            contentDescription = "登录二维码",
                            modifier = Modifier.size(232.dp).padding(10.dp),
                        )
                    }
                    Text(s.status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "使用 B 站 App 扫码 · 过期后可重新生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LoginUiState.Expired -> LoginCard {
                    Text("二维码已过期", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = vm::startLogin) { Text("重新生成") }
                }

                is LoginUiState.LoggedIn -> LoginCard {
                    // 用户名首字符做圆形头像
                    val first = s.uname.firstOrNull() ?: "B"
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            first.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Text(s.uname, style = MaterialTheme.typography.titleLarge)
                    if (s.vipLabel != null) {
                        Text(
                            "${s.vipLabel} · 可播放高音质",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text(
                            "普通用户(音质上限 192K;开通大会员可解锁更高音质)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(onClick = { vm.logout() }) { Text("退出登录") }
                    Button(onClick = onBack) { Text("完成") }
                }
            }
        }
    }
}

/** 居中的圆角卡片。纯色扁平,无阴影。 */
@Composable
private fun LoginCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}
