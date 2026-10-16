package dev.avsong.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import dev.avsong.data.update.AppUpdater
import dev.avsong.ui.common.ThemedNoticeHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「关于」页(「我的」页存储组下方进入):项目介绍 + 版本信息 + 检查更新 + 项目主页。
 *
 * 检查更新 = 拉 GitHub releases/latest 的 tag 与本地 BuildConfig.VERSION_NAME 比对;
 * 有新版时点「新版本」**应用内直接下载 APK(带进度)→ 拉起系统安装器**(AppUpdater)。
 * 本仓库无私有后端(纯客户端 app),更新源即 GitHub 发布页(仓库未公开时提示去主页看)。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 检查更新状态:idle=未检查 checking=进行中 latest=已是最新 found=有新版 failed=查不到
    var updateState by remember { mutableStateOf("idle") }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    // 新版 APK 直链(checkLatest 顺手从资产里抠出,下载用)
    var latestApkUrl by remember { mutableStateOf<String?>(null) }
    // 下载进度(AppUpdater.download 全局流:进页恢复显示,离页下载不中断)
    val dl by AppUpdater.download.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }

    fun checkUpdate() {
        updateState = "checking"
        scope.launch {
            val latest = withContext(Dispatchers.IO) { AppUpdater.checkLatest() }
            when {
                latest == null -> updateState = "failed"
                latest.version == BuildConfig.VERSION_NAME -> updateState = "latest"
                else -> {
                    latestVersion = latest.version
                    latestApkUrl = latest.apkUrl
                    updateState = "found"
                }
            }
        }
    }

    /** 有新版时点「新版本」:开始下载(进度由 dl 流回显);done 自动拉起安装器。 */
    fun startUpdate() {
        val url = latestApkUrl ?: return
        when (dl.status) {
            "running" -> return
            "done" -> AppUpdater.install(context)
            else -> AppUpdater.downloadApk(context, url)
        }
    }

    // 下载完成自动拉安装器(用户点一下就走完全程);失败提示就地回显
    LaunchedEffect(dl.status) {
        if (dl.status == "done") AppUpdater.install(context)
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
                        .clickable(
                            enabled = updateState != "checking",
                            // found=开始下载;下载中不重复触发;done=再点重拉安装器
                            onClick = if (updateState == "found") ::startUpdate else ::checkUpdate,
                        )
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
                        // 下载中换文案,给「在干什么」的明确反馈
                        when (dl.status) {
                            "running" -> "正在下载更新…"
                            "done" -> "下载完成,点按安装"
                            else -> "检查更新"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    when {
                        updateState == "checking" -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        // 下载中:整型百分比 + 细进度条压在行底(见下方 Box 覆盖层)
                        dl.status == "running" -> Text(
                            "${dl.progress}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        dl.status == "failed" -> Text(
                            "下载失败,点按重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        updateState == "latest" -> Text(
                            "已是最新",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        updateState == "found" -> Text(
                            "新版本 v$latestVersion",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        updateState == "failed" -> Text(
                            "检查失败,点按重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 下载进度细条:纯色扁平,贴行底
                if (dl.status == "running") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(dl.progress / 100f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary),
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
