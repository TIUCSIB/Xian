package dev.avsong.ui.library

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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
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
import dev.avsong.ui.common.ThemedNoticeHost
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
    onOpenDownloads: () -> Unit,
    onOpenPlayHistory: () -> Unit,
    /** 播放设置子页(音质/历史上限/倍速/睡眠定时/热评展示收纳处,2026-10-15 起)。 */
    onOpenPlaybackSettings: () -> Unit,
    /** 关于页(项目介绍/版本/检查更新)。 */
    onOpenAbout: () -> Unit,
    /** 睡眠定时剩余分钟(null=未启用),状态在 MainViewModel;入口行摘要用。 */
    sleepMinutesLeft: Int?,
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
    // 本页局部操作反馈(清理缓存/退出登录):与全局队列/下载反馈同一套主题化提示条
    var cacheToast by remember { mutableStateOf<String?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
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
            onOpenTheme = { showThemeSheet = true },
        )
        AccountHeader(loginState, onLogin = { showLoginDialog = true })
        // 只有设置区滚动兜底;weight(1f) 吃掉剩余高度(迷你条/底栏不遮内容)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsCard(
                onOpenPlaybackSettings = onOpenPlaybackSettings,
                downloadedCount = downloadedTracks.size,
                onOpenDownloads = onOpenDownloads,
                onOpenPlayHistory = onOpenPlayHistory,
                cacheLabel = cacheBytes?.let { formatCacheSize(it) },
                onClearCache = { clearAppCache() },
                onOpenAbout = onOpenAbout,
                // 已登录才出现「退出登录」行(未登录时该行整个不渲染)
                showLogout = loggedIn != null,
                onLogout = { showLogoutConfirm = true },
            )
        }
    }

    // 睡眠定时自定义分钟数输入已随播放设置整体迁到 PlaybackSettingsScreen(2026-10-15)

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
                    cacheToast = "已退出登录"
                }) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            },
        )
    }

    // 清理缓存完成提示:与全局队列/下载反馈同一套主题化提示条(cacheToast 置空防重复消费)
    ThemedNoticeHost(message = cacheToast, onConsumed = { cacheToast = null })

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
 * 设置区(2026-10-15 重新规划,用户要求按功能归纳、本页不超出一屏滚动):
 * ①「播放设置」入口行(音质/历史上限/倍速/睡眠定时/热评展示全部收进子页);
 * ② 存储组(已下载入口 + 清理缓存);
 * ③ 关于组(项目介绍/版本/检查更新);
 * ④ 账号组(退出登录,仅登录时)。
 */
@Composable
private fun SettingsCard(
    onOpenPlaybackSettings: () -> Unit,
    downloadedCount: Int,
    onOpenDownloads: () -> Unit,
    onOpenPlayHistory: () -> Unit,
    cacheLabel: String?,
    onClearCache: () -> Unit,
    onOpenAbout: () -> Unit,
    showLogout: Boolean,
    onLogout: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 播放设置入口:行尾摘要显示核心偏好当前值(点进去逐项调整)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            SettingsItem(
                icon = Icons.Rounded.Tune,
                title = "播放设置",
                value = null,
                onClick = onOpenPlaybackSettings,
            )
        }
        // 存储组:播放历史 + 已下载(离线曲库入口)+ 清理缓存(行尾显示当前图片缓存大小)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            SettingsItem(
                icon = Icons.Rounded.History,
                title = "播放历史",
                value = null,
                onClick = onOpenPlayHistory,
            )
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
        // 关于组:项目介绍/版本号/检查更新/项目主页
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            SettingsItem(
                icon = Icons.Rounded.Info,
                title = "关于",
                value = null,
                onClick = onOpenAbout,
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
            // 标题恒占剩余宽度(单行省略):值/箭头恒靠行尾;长标题被压时省略而不是换行(竖排踩过)
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
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
    /** 扫一扫已移除(无实际功能),右端图标换成主题色入口(用户要求,省出设置卡一行)。 */
    onOpenTheme: () -> Unit,
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
        IconButton(onClick = onOpenTheme) {
            Icon(
                Icons.Rounded.Palette,
                contentDescription = "主题色",
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
fun LoginEntryDialog(onDismiss: () -> Unit) {
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
