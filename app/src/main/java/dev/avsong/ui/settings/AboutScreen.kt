package dev.avsong.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.avsong.BuildConfig
import dev.avsong.ui.common.ThemedNoticeHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 「关于」页(「我的」页存储组下方进入):项目介绍 + 版本信息 + 检查更新 + 项目主页。
 *
 * 检查更新 = 拉 GitHub releases/latest 的 tag 与本地 BuildConfig.VERSION_NAME 比对;
 * 本仓库无私有后端(纯客户端 app),更新源即 GitHub 发布页(仓库未公开时提示去主页看)。
 * 网络走 HttpURLConnection + IO 线程(BiliHttp 带 B 站头,这里不需要,不复用)。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 检查更新状态:idle=未检查 checking=进行中 latest=已是最新 found=有新版 failed=查不到
    var updateState by remember { mutableStateOf("idle") }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun checkUpdate() {
        updateState = "checking"
        scope.launch {
            val tag = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL("https://api.github.com/repos/TIUCSIB/Xian/releases/latest")
                        .openConnection() as HttpURLConnection
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 8_000
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.inputStream.use { input ->
                        // 只抠 tag 字段,不值得为一次检查引入 JSON 解析依赖
                        Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
                            .find(input.bufferedReader().readText())?.groupValues?.get(1)
                    }
                }.onFailure { null }.getOrNull()
            }
            when {
                tag == null -> updateState = "failed"
                tag.removePrefix("v") == BuildConfig.VERSION_NAME -> updateState = "latest"
                else -> {
                    latestVersion = tag.removePrefix("v")
                    updateState = "found"
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                "关于",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 项目介绍卡:AvSong 是什么、技术栈、设计定位
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        BuildConfig.APP_NAME,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "基于 B 站公开 API 的第三方音乐播放器。一根弦是一个音——把散落在 B 站视频里的好听声音捡起来,「发现好歌、听到好歌」都在一处。" +
                        "纯客户端、匿名可用、无后端;登录 B 站账号可解锁收藏夹歌单与更高音质。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 更新卡:检查更新(结果就地回显)+ 项目主页
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = updateState != "checking", onClick = ::checkUpdate)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "检查更新",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    when (updateState) {
                        "checking" -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        "latest" -> Text(
                            "已是最新",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        "found" -> Text(
                            "新版本 v$latestVersion",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        "failed" -> Text(
                            "检查失败,点按重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 项目主页:浏览器打开(GitHub 未公开时会 404,如实反馈)
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(BuildConfig.PROJECT_URL),
                                    )
                                )
                            }.onFailure { notice = "无法打开浏览器" }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "项目主页",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "GitHub",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }

    ThemedNoticeHost(message = notice, onConsumed = { notice = null })
}
