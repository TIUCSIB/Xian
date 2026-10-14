package dev.avsong.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.sin
import dev.avsong.domain.formatClock
import dev.avsong.player.PlayerState
import dev.avsong.ui.LyricUiState
import dev.avsong.ui.library.FavoritePickerDialog
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/**
 * 全屏播放页(参考设计稿):封面铺满整屏 + 半透明黑纱保证可读性,
 * 底部叠两张深灰圆角卡片(信息卡 + 控制卡),播放键为柠檬黄实心圆。
 * 纯色扁平:无阴影、无渐变、无光晕。进度用本地 ticker 每秒采样。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerState,
    lyric: LyricUiState,
    favoriteRepository: dev.avsong.data.FavoriteRepository,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    /** 热评弹幕开关(持久化在 SettingsStore)+ 拉到的热评数据。 */
    danmakuEnabled: Boolean = true,
    onToggleDanmaku: () -> Unit = {},
    hotReplies: List<dev.avsong.data.ReplyRepository.HotReply> = emptyList(),
    positionProvider: () -> Long = { state.positionMs },
) {
    var position by remember(state.currentMediaId, state.isPlaying) { mutableLongStateOf(state.positionMs) }
    var showLyric by remember { mutableStateOf(false) }
    // 热评弹幕:开关开且换曲时拉一次(失败=空列表,弹幕层静默不显示)。
    // 注意 key 用 currentMediaId+danmakuEnabled:关再开会重新拉(数据可能已过期)
    var hotRepliesState by remember(state.currentMediaId, danmakuEnabled) {
        mutableStateOf<List<dev.avsong.data.ReplyRepository.HotReply>?>(null)
    }
    LaunchedEffect(state.currentMediaId, danmakuEnabled) {
        hotRepliesState = null
        if (danmakuEnabled && state.currentMediaId != null) {
            val song = dev.avsong.domain.Song(
                bvid = state.currentMediaId!!,
                title = state.title.orEmpty(),
                artist = state.artist.orEmpty(),
                coverUrl = state.artworkUri,
            )
            hotRepliesState = dev.avsong.data.ReplyRepository.hotRepliesFor(song) { bvid ->
                favoriteRepository.fetchAid(bvid)
            }
        }
    }
    // 收藏:爱心回显 + 弹窗开关(声明在函数级,信息卡爱心与弹窗共用)
    var favChecked by remember(state.currentMediaId) { mutableStateOf(false) }
    var showFavPicker by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentMediaId) {
        val bvid = state.currentMediaId ?: return@LaunchedEffect
        val aid = runCatching { favoriteRepository.fetchAid(bvid) }.getOrDefault(0L)
        if (aid > 0) {
            favChecked = runCatching { favoriteRepository.checkFavorite(aid).isNotEmpty() }
                .getOrDefault(false)
        }
    }
    // 浅色/夜间分支用:主题不带 isDark 标记,以 surface 亮度判定(两套方案的 surface 差异极大)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val scope = rememberCoroutineScope()

    // 播放中每秒采样一次进度
    LaunchedEffect(state.currentMediaId, state.isPlaying) {
        while (state.isPlaying) {
            position = positionProvider()
            delay(500)
        }
        position = positionProvider()
    }

    // seek 必须同步刷新本地 position:暂停态下上面的 ticker 不跑,只发 onSeek 的话
    // 播放器位置变了但 UI 还显示旧采样,松手"跳回原位"(用户报的 bug);恢复播放后才见新位置
    fun seekAndShow(ms: Long) {
        position = ms
        onSeek(ms)
    }

    // 根节点消费点击:面板是覆盖层,不拦截的话点击会穿透到后面的内容(实测踩过)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        // 背景层:全幅封面 + 纯色黑纱(半透明实色,非渐变)。
        // 封面态/歌词态共用 —— 两种状态只差在中间那张进度卡上,背景不换(用户要求);
        // 但歌词态黑纱加深(0.55→0.78),亮封面(录音棚/暖色实拍)会把白字歌词吃掉看不清(用户实测)
        if (state.artworkUri != null) {
            AsyncImage(
                model = state.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (showLyric) 0.78f else 0.55f)))
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }

        val cardShape = RoundedCornerShape(28.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            // 顶栏:收起箭头(裸图标)。图标直接坐在封面上,用白色+淡黑描底保证任意封面可读
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCollapse, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "收起",
                        modifier = Modifier.size(28.dp),
                        // 坐在封面黑纱上用白色;无封面时退回主题底,改用 onSurface
                        tint = if (state.artworkUri != null) Color.White
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.weight(1f))
                // 热评弹幕开关:开=primary 色弹幕图标,关=灰(与播放页图标风格一致)
                IconButton(onClick = onToggleDanmaku, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Rounded.Subtitles,
                        contentDescription = if (danmakuEnabled) "关闭热评弹幕" else "开启热评弹幕",
                        modifier = Modifier.size(24.dp),
                        tint = if (danmakuEnabled) MaterialTheme.colorScheme.primary
                        else if (state.artworkUri != null) Color.White
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // 歌词态:歌词铺满上半区(坐在封面黑纱上,用白色系文字),信息卡整个隐藏,
            // 点歌词空白处切回封面(歌词行本身的点击仍是 seek);封面态:留白把信息卡推到底部。
            if (showLyric) {
                LyricView(
                    lyric = lyric,
                    positionMs = position,
                    onSeek = ::seekAndShow,
                    onArtwork = state.artworkUri != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showLyric = false },
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            // 热评弹幕层:叠在上半区(封面/歌词之上),权重 1f 区域内飘过;
            // 不消费点击 → 封面点击切歌词/歌词点击 seek 的手势不受影响
            if (danmakuEnabled && hotRepliesState.orEmpty().isNotEmpty()) {
                ReplyDanmaku(
                    replies = hotRepliesState.orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            // 信息卡:半透明纯色卡(卡内不再各铺一份模糊封面——两张卡各裁一块封面,
            // 用户反馈「背景是单独的」;去掉后统一透出面板的全幅封面底);歌词态不显示
            if (!showLyric) {
            Box(modifier = Modifier.fillMaxWidth().clip(cardShape)) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (dark) 0.85f else 0.92f)),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showLyric = !showLyric }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.title ?: "未在播放",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = state.artist ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 收藏键从顶栏移入信息卡(顶栏浅色下在亮封面上看不清)。
                    // 点击一律弹「收藏到」选择框(勾选=加入/取消勾=移出,收藏夹自选);
                    // 弹窗「完成」回传勾选状态同步爱心(favChecked/showFavPicker 声明在函数级)
                    IconButton(onClick = { showFavPicker = true }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            if (favChecked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (favChecked) "已收藏,点按管理收藏夹" else "收藏到收藏夹",
                            tint = if (favChecked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                }
                Spacer(Modifier.height(14.dp))
                // 波形进度条常驻信息卡(歌词态也要有进度);歌词本体已移到上半区
                val dur = if (state.durationMs > 0) state.durationMs.toFloat() else 1f
                WaveformSeekBar(
                    progress = (position.toFloat() / dur).coerceIn(0f, 1f),
                    onSeekFraction = { frac -> seekAndShow((frac * dur).roundToLong()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        position.formatClock(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        state.durationMs.formatClock(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
            }

            Spacer(Modifier.height(12.dp))

            // 控制卡:半透明纯色卡(同信息卡,卡内不再各铺模糊封面) / 模式 | 上一首 | 播放(黄) | 下一首 | 队列
            Box(modifier = Modifier.fillMaxWidth().clip(cardShape)) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (dark) 0.85f else 0.92f)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 裸图标统一白色(无圆底后不注入会继承到黑色)
                CompositionLocalProvider(LocalContentColor provides if (dark) Color.White else MaterialTheme.colorScheme.onSurface) {
                // 播放模式键:单击循环切换(列表循环→单曲循环→随机→顺序),图标随模式变
                IconButton(onClick = onCycleMode, modifier = Modifier.size(44.dp)) {
                    val (modeIcon, modeDesc) = when {
                        state.shuffleEnabled -> Icons.Rounded.Shuffle to "随机播放"
                        state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE ->
                            Icons.Rounded.RepeatOne to "单曲循环"
                        else -> Icons.Rounded.Repeat to "列表循环"
                    }
                    Icon(
                        modeIcon,
                        contentDescription = modeDesc,
                        modifier = Modifier.size(24.dp),
                        tint = if (state.shuffleEnabled ||
                            state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(26.dp),
                    )
                }
                // 播放/暂停:柠檬黄实心圆(onPrimary=近黑图标)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onPlayPause, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(26.dp),
                    )
                }
                    IconButton(onClick = onQueue, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = "播放队列",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // 收藏夹选择弹窗(从 AvSongApp 挪入:爱心状态要在这里同步)
        if (showFavPicker) {
            val bvid = state.currentMediaId ?: ""
            if (bvid.isNotBlank()) {
                FavoritePickerDialog(
                    favRepo = favoriteRepository,
                    bvid = bvid,
                    onDismissWithState = { favChecked = it },
                    onDismiss = { showFavPicker = false },
                )
            }
        }
    }
}

/**
 * 音波进度条:竖条波形,已播黄条 / 未播灰条;点击与横向拖动均直接 seek。
 * 条高取稳定伪随机序列,视觉上是固定形状的一条音波(与进度无关)。
 */
@Composable
private fun WaveformSeekBar(
    progress: Float,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barCount: Int = 44,
) {
    val bars = remember(barCount) {
        List(barCount) { i -> 0.22f + 0.78f * abs(sin(i * 12.9898f) * 43758.5453f % 1f) }
    }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: progress
    val playedColor = MaterialTheme.colorScheme.primary
    // 未播条用实心灰(用户反馈半透明灰会让卡片显得透明)
    val unplayedColor = MaterialTheme.colorScheme.outline

    Canvas(
        modifier
            .height(56.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeekFraction((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let(onSeekFraction)
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    change.consume()
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        val gap = 2.dp.toPx()
        val barW = (size.width - gap * (barCount - 1)) / barCount
        // 全量未播灰条
        bars.forEachIndexed { i, h ->
            drawRoundRect(
                color = unplayedColor,
                topLeft = Offset(x = i * (barW + gap), y = (size.height - size.height * h) / 2),
                size = Size(width = barW, height = size.height * h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
        // 已播黄条:按播放进度像素级裁剪,边界条平滑填充(不整格跳变,用户要求)
        clipRect(right = size.width * fraction.coerceIn(0f, 1f)) {
            bars.forEachIndexed { i, h ->
                drawRoundRect(
                    color = playedColor,
                    topLeft = Offset(x = i * (barW + gap), y = (size.height - size.height * h) / 2),
                    size = Size(width = barW, height = size.height * h),
                    cornerRadius = CornerRadius(barW / 2f),
                )
            }
        }
    }
}
