package dev.avsong.ui.library

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.avsong.AvSongApp
import dev.avsong.ui.login.LoginUiState
import dev.avsong.ui.rememberLoginViewModel
import dev.avsong.ui.settings.SettingsStore
import dev.avsong.ui.theme.ThemeSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 「我的」tab(底部导航第四项):账户信息头(登录态/去登录/退出)。
 * 歌单类内容(UP 主合集 + 收藏夹)2026-10-11 起移到「歌单」tab,这里只剩账户。
 * 头部版式参考 B 站 app:左头像,右侧昵称 + 等级/会员徽章 + 硬币;无卡片背景。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onLogin: () -> Unit,
    onOpenDownloads: () -> Unit,
    /** 睡眠定时剩余分钟(null=未启用),状态在 MainViewModel。 */
    sleepMinutesLeft: Int?,
    onSetSleepTimer: (Int) -> Unit,
) {
    val loginVm = rememberLoginViewModel()
    val loginState by loginVm.state.collectAsState()
    val app = LocalContext.current.applicationContext as AvSongApp
    val theme by app.themeStore.state.collectAsState()
    val settings by app.settingsStore.state.collectAsState()
    val downloadedTracks by app.downloadedStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var cacheToast by remember { mutableStateOf<String?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    // 睡眠定时自定义输入(1–720 分钟)
    var showSleepCustomDialog by remember { mutableStateOf(false) }
    var sleepCustomText by remember { mutableStateOf("") }
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var cacheRefreshTick by remember { mutableStateOf(0) }
    val loggedIn = loginState as? LoginUiState.LoggedIn
    val context = LocalContext.current

    // 缓存大小:Coil 图片磁盘缓存(cacheDir/image_cache)。进页/清理后各测一次(IO 线程,别卡主线程)
    LaunchedEffect(cacheRefreshTick) {
        cacheBytes = withContext(Dispatchers.IO) {
            val dir = java.io.File(context.cacheDir, "image_cache")
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    // 缓存大小:Coil 图片磁盘缓存(cacheDir/image_cache),点击清理时现算
    fun clearAppCache() {
        scope.launch(Dispatchers.IO) {
            val dir = java.io.File(context.cacheDir, "image_cache")
            val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            dir.deleteRecursively()
            cacheToast = "已清理 ${"%.1f".format(size / 1048576.0)} MB"
            cacheRefreshTick++
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        FunctionBar(
            isDark = theme.dark,
            onToggleTheme = { scope.launch { app.themeStore.setDark(!theme.dark) } },
            onScan = {
                if (loggedIn?.uname == null) showLoginDialog = true
                else Toast.makeText(app, "扫码功能开发中", Toast.LENGTH_SHORT).show()
            },
        )
        AccountHeader(loginState, onLogin = { showLoginDialog = true })
        // 只有设置卡滚动(用户反馈:账户头不该跟着动);weight(1f) 吃掉剩余高度,
        // 内容超出时在卡片区域内滚,迷你条/底栏不会遮住滚出的内容
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsCard(
                themeLabel = theme.customHue?.let { "自定义" } ?: theme.seed.label,
                onOpenTheme = { showThemeSheet = true },
                quality = settings.quality,
                onQualityChange = { app.settingsStore.setQuality(it) },
                historyLimit = settings.historyLimit,
                onHistoryChange = { app.settingsStore.setHistoryLimit(it) },
                sleepMinutesLeft = sleepMinutesLeft,
                onSetSleepTimer = onSetSleepTimer,
                onCustomSleep = { showSleepCustomDialog = true },
                downloadedCount = downloadedTracks.size,
                onOpenDownloads = onOpenDownloads,
                cacheLabel = cacheBytes?.let { formatCacheSize(it) },
                onClearCache = { clearAppCache() },
                // 已登录才出现「退出登录」行(未登录时该行整个不渲染)
                showLogout = loggedIn != null,
                onLogout = { showLogoutConfirm = true },
            )
        }
    }

    // 睡眠定时自定义分钟数:数字键盘,1–720 校验(超出给行内错误提示)
    if (showSleepCustomDialog) {
        val v = sleepCustomText.toIntOrNull()
        val invalid = v == null || v !in 1..720
        AlertDialog(
            onDismissRequest = { showSleepCustomDialog = false },
            title = { Text("自定义睡眠定时") },
            text = {
                Column {
                    Text(
                        "播放到设定时长后自动暂停(1–720 分钟)。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sleepCustomText,
                        onValueChange = { sleepCustomText = it.filter { c -> c.isDigit() }.take(3) },
                        singleLine = true,
                        isError = sleepCustomText.isNotEmpty() && invalid,
                        supportingText = {
                            if (sleepCustomText.isNotEmpty() && invalid) {
                                Text("请输入 1–720 之间的整数", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !invalid,
                    onClick = {
                        showSleepCustomDialog = false
                        v?.let { onSetSleepTimer(it) }
                        sleepCustomText = ""
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showSleepCustomDialog = false }) { Text("取消") }
            },
        )
    }

    // 退出登录确认:退出后需重新扫码,防误触
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录?") },
            text = { Text("退出后需重新扫码登录才能使用收藏、歌单等账号功能。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    loginVm.logout()
                }) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            },
        )
    }

    // 清理缓存完成 Toast(cacheToast 置空防重复消费)
    cacheToast?.let {
        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        cacheToast = null
    }

    // 登录弹窗(参考 BiliTune 设计稿):二维码/轮询/结果全在弹窗内,不跳页
    if (showLoginDialog) {
        LoginEntryDialog(onDismiss = { showLoginDialog = false })
    }

    if (showThemeSheet) {
        // 改的是浅色 seed;深浅切换走右上角功能栏。弹窗不自动关,便于在真实页面逐个预览
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    "主题色",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 12.dp),
                )
                // 8 个色板两行排布;一行塞不下(8×46dp 超屏宽)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeSeed.entries.chunked(4).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            row.forEach { s ->
                                ThemeSwatch(
                                    label = s.label,
                                    previewBg = s.previewBg,
                                    previewAccent = s.previewAccent,
                                    selected = theme.customHue == null && s == theme.seed,
                                    onClick = { scope.launch { app.themeStore.setSeed(s) } },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
                    HueWheel(
                        hue = theme.customHue ?: 210f,
                        onHueChange = { app.themeStore.previewHue(it) },
                        onDragFinished = { scope.launch { app.themeStore.persistHue() } },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "自定义",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (theme.customHue != null) FontWeight.Bold else FontWeight.Medium,
                        color = if (theme.customHue != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 色相轮盘:拖动/点击环上任一点选色相,实时回调;松手落盘。 */
@Composable
private fun HueWheel(
    hue: Float,
    onHueChange: (Float) -> Unit,
    onDragFinished: () -> Unit,
) {
    val ringColors = remember { List(37) { i -> Color.hsl(i * 10f, 0.55f, 0.5f) } }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    fun hueAt(pos: Offset): Float? {
        if (sizePx == IntSize.Zero) return null
        val dx = pos.x - sizePx.width / 2f
        val dy = pos.y - sizePx.height / 2f
        if (dx == 0f && dy == 0f) return null
        val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        return ((deg + 360.0) % 360.0).toFloat()
    }

    Box(
        modifier = Modifier
            .size(150.dp)
            .onSizeChanged { sizePx = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    hueAt(down.position)?.let(onHueChange)
                    drag(down.id) { change ->
                        hueAt(change.position)?.let(onHueChange)
                        change.consume()
                    }
                    onDragFinished()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 20.dp.toPx()
            val radius = size.minDimension / 2f - stroke / 2f
            // SweepGradient 从 3 点钟方向顺时针,hue 0(红)在正右,与下方 knob 角度公式一致
            drawCircle(
                brush = Brush.sweepGradient(ringColors),
                radius = radius,
                style = Stroke(width = stroke),
            )
            val knobR = 13.dp.toPx()
            val rad = Math.toRadians(hue.toDouble())
            val knobCenter = Offset(
                size.center.x + radius * cos(rad).toFloat(),
                size.center.y + radius * sin(rad).toFloat(),
            )
            drawCircle(Color.White, radius = knobR, center = knobCenter)
            drawCircle(
                Color.hsl(hue, 0.55f, 0.5f),
                radius = knobR - 2.dp.toPx(),
                center = knobCenter,
            )
        }
    }
}

/** 缓存大小人类可读:0→「无缓存」,其余按 B/KB/MB 取合适单位(与清理 Toast 口径一致)。 */
private fun formatCacheSize(bytes: Long): String = when {
    bytes <= 0 -> "无缓存"
    bytes < 1024 -> "$bytes B"
    bytes < 1048576 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1048576.0)
}

/**
 * 设置区:三张卡片——播放(默认音质/播放历史上限)、主题(主题色)、缓存(缓存大小/清理)。
 * 音质/历史上限是短单选,点击**就地展开**选项(2026-10-13 用户反馈:每行都弹底部抽屉显得怪);
 * 主题色仍走 ModalBottomSheet(色板+色相轮盘是视觉选择器,且弹层不自动关便于逐色预览);
 * 缓存清理直接执行。
 */
@Composable
private fun SettingsCard(
    themeLabel: String,
    onOpenTheme: () -> Unit,
    quality: SettingsStore.Quality,
    onQualityChange: (SettingsStore.Quality) -> Unit,
    historyLimit: Int,
    onHistoryChange: (Int) -> Unit,
    sleepMinutesLeft: Int?,
    onSetSleepTimer: (Int) -> Unit,
    onCustomSleep: () -> Unit,
    downloadedCount: Int,
    onOpenDownloads: () -> Unit,
    cacheLabel: String?,
    onClearCache: () -> Unit,
    showLogout: Boolean,
    onLogout: () -> Unit,
) {
    // 同一时间只展开一项(两项同开卡片会很长)
    var expanded by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 播放组
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            SettingsExpandableItem(
                icon = Icons.Rounded.GraphicEq,
                title = "默认音质",
                value = quality.label,
                expanded = expanded == "quality",
                onToggle = { expanded = if (expanded == "quality") null else "quality" },
            ) {
                SettingsStore.Quality.entries.forEach { q ->
                    SettingsOptionRow(
                        label = q.label + if (q == SettingsStore.Quality.LOSSLESS) "(需大会员)" else "",
                        selected = q == quality,
                        onClick = {
                            onQualityChange(q)
                            expanded = null
                        },
                    )
                }
            }
            SettingsExpandableItem(
                icon = Icons.Rounded.History,
                title = "播放历史上限",
                value = if (historyLimit == 0) "不限" else "$historyLimit 首",
                expanded = expanded == "history",
                onToggle = { expanded = if (expanded == "history") null else "history" },
            ) {
                listOf(20, 50, 100, 0).forEach { n ->
                    SettingsOptionRow(
                        label = if (n == 0) "不限" else "$n 首",
                        selected = n == historyLimit,
                        onClick = {
                            onHistoryChange(n)
                            expanded = null
                        },
                    )
                }
            }
            // 睡眠定时:全局功能放设置卡(用户反馈:不该放播放页);到点暂停播放,不持久化
            SettingsExpandableItem(
                icon = Icons.Rounded.Bedtime,
                title = "睡眠定时",
                value = when {
                    sleepMinutesLeft == null -> "关"
                    sleepMinutesLeft < 60 -> "${sleepMinutesLeft} 分钟"
                    sleepMinutesLeft % 60 == 0 -> "${sleepMinutesLeft / 60} 小时"
                    else -> "${sleepMinutesLeft / 60} 小时 ${sleepMinutesLeft % 60} 分钟"
                },
                expanded = expanded == "sleep",
                onToggle = { expanded = if (expanded == "sleep") null else "sleep" },
            ) {
                if (sleepMinutesLeft != null) {
                    SettingsOptionRow(label = "关闭定时", selected = false, onClick = {
                        onSetSleepTimer(0)
                        expanded = null
                    })
                }
                listOf(15, 30, 60, 90).forEach { min ->
                    SettingsOptionRow(
                        label = when {
                            min < 60 -> "$min 分钟"
                            min % 60 == 0 -> "${min / 60} 小时"
                            else -> "${min / 60} 小时 ${min % 60} 分钟"
                        },
                        selected = sleepMinutesLeft == min,
                        onClick = {
                            onSetSleepTimer(min)
                            expanded = null
                        },
                    )
                }
                // 自定义分钟数:当前值不是预设档时显示选中(回显当前值)
                SettingsOptionRow(
                    label = if (sleepMinutesLeft != null && sleepMinutesLeft !in listOf(15, 30, 60, 90))
                        "自定义(${sleepMinutesLeft} 分钟)" else "自定义…",
                    selected = sleepMinutesLeft != null && sleepMinutesLeft !in listOf(15, 30, 60, 90),
                    onClick = {
                        onCustomSleep()
                        expanded = null
                    },
                )
            }
        }
        // 主题组
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            SettingsItem(
                icon = Icons.Rounded.Palette,
                title = "主题色",
                value = themeLabel,
                onClick = onOpenTheme,
            )
        }
        // 存储组:已下载(离线曲库入口)+ 清理缓存(行尾显示当前图片缓存大小)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            SettingsItem(
                icon = Icons.Rounded.Download,
                title = "已下载",
                value = "${downloadedCount} 首",
                onClick = onOpenDownloads,
            )
            SettingsItem(
                icon = Icons.Rounded.CleaningServices,
                title = "清理缓存",
                value = cacheLabel ?: "…",
                onClick = onClearCache,
            )
        }
        // 账号组:仅登录时出现(原账户头右上角的退出图标收进这里,2026-10-13 用户要求)
        if (showLogout) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                SettingsItem(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    title = "退出登录",
                    value = null,
                    onClick = onLogout,
                    destructive = true,
                )
            }
        }
    }
}

/**
 * 可展开设置行:点击整行就地展开/收起选项(不弹层);箭头折叠朝下、展开转 180°。
 * 折叠态右侧显示当前值;展开的选项缩进 52dp 对齐标题(16 内边距 + 22 图标 + 14 间隔)。
 */
@Composable
private fun SettingsExpandableItem(
    icon: ImageVector,
    title: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    options: @Composable () -> Unit,
) {
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "settingsArrow")
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(arrowRotation),
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(bottom = 6.dp)) { options() }
        }
    }
}

/** 展开后的选项行:选中 = primary 加粗 + 勾(与行尾箭头同列,视觉对齐)。 */
@Composable
private fun SettingsOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 52.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 设置行:左裸图标 + 标题 + 当前值 + 右箭头。destructive=危险操作(退出登录),图标/文字走 error 色。 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val fg = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = fg,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 主题色板:底色方块 + 强调色圆点 + 名称,选中态 primary 描边。
 * 无 ripple(indication=null):方形涟漪在色板上很难看,用户点名去掉。
 */
@Composable
private fun ThemeSwatch(
    label: String,
    previewBg: Color,
    previewAccent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(shape)
                .background(previewBg)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(previewAccent),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 功能栏:右端日夜间切换 + 扫码(B 站 app「我的」页同款布局,裸图标无底色)。 */
@Composable
private fun FunctionBar(
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
private fun AccountHeader(login: LoginUiState, onLogin: () -> Unit) {
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
        // 退出登录已移入设置卡末行(2026-10-13 用户要求),此处不再放图标
    }
}

/** 硬币显示:整数不带小数点,有小数保留(nav 的 money 是 double,实测可能带小数)。 */
private fun formatCoin(coin: Double) =
    if (coin % 1.0 == 0.0) coin.toInt().toString() else coin.toString()

/**
 * 登录弹窗(参考 BiliTune 设计稿):全流程在弹窗内完成,不跳页。
 * 打开即生成二维码;轮询状态(等待/已扫码/过期/已登录)在弹窗内回显;
 * 登录成功显示昵称+完成。已登录时打开直接是已登录态(可退出)。
 */
@Composable
private fun LoginEntryDialog(onDismiss: () -> Unit) {
    val vm = rememberLoginViewModel()
    val state by vm.state.collectAsState()
    // 弹窗内视图:方式选择页 ↔ 扫码页(登录成功后 fixed 为结果页,返回箭头隐藏)
    var view by remember { mutableStateOf(LoginView.Choose) }
    val loggedInState = state as? LoginUiState.LoggedIn
    if (loggedInState != null && view == LoginView.Qr) view = LoginView.Choose
    val inQrView = view == LoginView.Qr && loggedInState == null
    // 进入扫码视图即开始流程
    LaunchedEffect(view) {
        if (view == LoginView.Qr && vm.state.value is LoginUiState.Idle) vm.startLogin()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 扫码/Cookie 视图左上角显示返回(回方式选择);选择页/结果页无
                val inSubView = view != LoginView.Choose && loggedInState == null
                if (inSubView) {
                    IconButton(onClick = { view = LoginView.Choose }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    when {
                        inQrView -> "扫码登录"
                        view == LoginView.Cookie -> "手动填入 Cookie"
                        else -> "Bilibili 账号"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (view == LoginView.Cookie && loggedInState == null) {
                    CookieInputView(
                        authStore = vm.authStore,
                        onDone = onDismiss,
                        onBack = { view = LoginView.Choose },
                    )
                } else if (view != LoginView.Cookie && !inQrView) {
                    // 方式选择页(设计稿版式):说明 + 登录方式按钮组
                    Text(
                        "扫码登录适合常规使用,支持哔哩哔哩 App 扫码确认。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (loggedInState != null) {
                        // 已登录:直接显示账号信息(可退出);有真实头像用头像,回退首字圆
                        val s = loggedInState
                        DialogAvatar(face = s.face, fallback = s.uname.firstOrNull() ?: 'B')
                        Spacer(Modifier.height(8.dp))
                        Text(s.uname, style = MaterialTheme.typography.titleLarge)
                        Text(
                            loggedInState.vipLabel?.let { "$it · 可播放高音质" } ?: "普通用户(音质上限 192K)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        DialogPrimaryButton(icon = Icons.Filled.Check, text = "完成", onClick = onDismiss)
                        TextButton(onClick = { vm.logout() }) { Text("退出登录") }
                    } else {
                        DialogPrimaryButton(
                            icon = Icons.Rounded.QrCodeScanner,
                            text = "扫码登录",
                            onClick = { view = LoginView.Qr },
                        )
                        Spacer(Modifier.height(10.dp))
                        DialogOutlineButton(
                            icon = Icons.Rounded.Cookie,
                            text = "手动填入 Cookie",
                            onClick = { view = LoginView.Cookie },
                        )
                    }
                } else {
                    // 扫码视图:轮询状态机回显
                    when (val s = state) {
                        LoginUiState.Idle, is LoginUiState.Failed -> {
                            if (s is LoginUiState.Failed) {
                                Text(s.message, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                "扫码登录适合常规使用,支持哔哩哔哩 App 扫码确认。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            DialogPrimaryButton(icon = Icons.Rounded.QrCodeScanner, text = "生成二维码", onClick = vm::startLogin)
                        }

                        LoginUiState.Generating -> Column(
                            Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }

                        is LoginUiState.Showing -> {
                            // 二维码垫白底圆角框,深浅主题都可扫
                            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                                Image(
                                    bitmap = s.qr.asImageBitmap(),
                                    contentDescription = "登录二维码",
                                    modifier = Modifier.size(224.dp).padding(10.dp),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(s.status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "使用 B 站 App 扫码确认登录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        LoginUiState.Expired -> {
                            Text("二维码已过期", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(16.dp))
                            DialogPrimaryButton(icon = Icons.Rounded.QrCodeScanner, text = "重新生成", onClick = vm::startLogin)
                        }

                        is LoginUiState.LoggedIn -> {
                            DialogAvatar(face = s.face, fallback = s.uname.firstOrNull() ?: 'B')
                            Spacer(Modifier.height(8.dp))
                            Text(s.uname, style = MaterialTheme.typography.titleLarge)
                            Text(
                                s.vipLabel?.let { "$it · 可播放高音质" } ?: "普通用户(音质上限 192K)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))
                            DialogPrimaryButton(icon = Icons.Filled.Check, text = "完成", onClick = onDismiss)
                            TextButton(onClick = { vm.logout() }) { Text("退出登录") }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/** 登录弹窗内视图。 */
private enum class LoginView { Choose, Qr, Cookie }

/** 弹窗内头像:真实头像优先,缺失回退 primary 首字圆(与账户头同一数据源 AuthState.face)。 */
@Composable
private fun DialogAvatar(face: String?, fallback: Char) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(50))
            .background(if (face != null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (face != null) {
            AsyncImage(
                model = face,
                contentDescription = "头像",
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(50)),
            )
        } else {
            Text(
                fallback.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * 手动填入 Cookie 视图:粘贴浏览器复制的 Cookie(或单个字段),解析 SESSDATA/bili_jct/DedeUserID,
 * 经 nav 校验有效才入库;成功即回登录态。失败(缺 SESSDATA/无效)给出具体原因。
 */
@Composable
private fun CookieInputView(
    authStore: dev.avsong.data.auth.AuthStore,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "在浏览器登录 B 站后,复制 Cookie 粘贴到下面(至少包含 SESSDATA;开发者工具 → 应用 → Cookie,或 document.cookie)。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("SESSDATA=xxx; bili_jct=xxx; DedeUserID=xxx") },
            // 固定高度防长 Cookie 撑爆弹窗,内容在框内滚动,按钮始终可见
            singleLine = false,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .focusRequester(focus),
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        DialogPrimaryButton(
            icon = Icons.Filled.Check,
            text = if (busy) "校验中…" else "校验并登录",
            onClick = {
                if (busy) return@DialogPrimaryButton
                error = null
                val parsed = parseCookieInput(text)
                if (parsed == null) {
                    error = "没有解析到 SESSDATA,请确认粘贴内容包含它"
                    return@DialogPrimaryButton
                }
                busy = true
                scope.launch {
                    // 先临时入库(拦截器带上 Cookie),nav 探测有效才算成功,否则回滚
                    authStore.saveCookies(parsed.sessData, parsed.biliJct, parsed.dedeUserId)
                    val profile = runCatching { dev.avsong.data.auth.QrLogin.fetchProfile() }.getOrNull()
                    if (profile?.uname != null) {
                        authStore.saveProfile(profile.uname, profile.face, profile.vipLabel, profile.level, profile.coin)
                        onDone()
                    } else {
                        error = "Cookie 无效或已过期(nav 校验失败),请重新复制"
                        authStore.logout()
                    }
                    busy = false
                }
            },
        )
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * 解析用户粘贴的 Cookie:容忍整段 Cookie 头 / k=v 分号串 / document.cookie 输出。
 * 只取三个关键字段;SESSDATA 缺失视为无效(null)。
 */
private fun parseCookieInput(raw: String): dev.avsong.data.auth.AuthStore.ParsedCookie? {
    fun find(name: String): String? =
        Regex("$name=([^;\\s]+)").find(raw)?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
    val sess = find("SESSDATA") ?: return null
    return dev.avsong.data.auth.AuthStore.ParsedCookie(
        sessData = sess,
        biliJct = find("bili_jct").orEmpty(),
        dedeUserId = find("DedeUserID").orEmpty(),
    )
}

/** 弹窗内主按钮:实心 primary 全宽胶囊。 */
@Composable
private fun DialogPrimaryButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** 弹窗内次按钮:描边全宽胶囊(未实现的登录方式占位)。 */
@Composable
private fun DialogOutlineButton(icon: ImageVector, text: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
