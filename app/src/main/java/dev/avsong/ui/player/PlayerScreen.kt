package dev.avsong.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/**
 * 全屏播放页(参考设计稿):封面铺满整屏 + 半透明黑纱保证可读性,
 * 底部叠两张深灰圆角卡片(信息卡 + 控制卡),播放键为柠檬黄实心圆。
 * 纯色扁平:无阴影、无渐变、无光晕。进度用本地 ticker 每秒采样。
 */
@Composable
fun PlayerScreen(
    state: PlayerState,
    lyric: LyricUiState,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    positionProvider: () -> Long = { state.positionMs },
) {
    var position by remember(state.currentMediaId, state.isPlaying) { mutableLongStateOf(state.positionMs) }
    var showLyric by remember { mutableStateOf(false) }

    // 播放中每秒采样一次进度
    LaunchedEffect(state.currentMediaId, state.isPlaying) {
        while (state.isPlaying) {
            position = positionProvider()
            delay(500)
        }
        position = positionProvider()
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
        // 背景层:全幅封面 + 纯色黑纱(半透明实色,非渐变)
        if (state.artworkUri != null && !showLyric) {
            AsyncImage(
                model = state.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
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
            // 顶栏:收起箭头 / 标题 / 收藏(裸图标无圆形背景,用户要求)
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
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onFavorite, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Rounded.FavoriteBorder,
                        contentDescription = "收藏",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 信息卡:半透明深灰卡 + 轻度模糊封面(用户要求加一点模糊感)
            Box(modifier = Modifier.fillMaxWidth().clip(cardShape)) {
                if (state.artworkUri != null) {
                    AsyncImage(
                        model = state.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .blur(10.dp),
                    )
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLyric = !showLyric }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
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
                Spacer(Modifier.height(14.dp))
                if (showLyric) {
                    LyricView(
                        lyric = lyric,
                        positionMs = position,
                        onSeek = onSeek,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                } else {
                    val dur = if (state.durationMs > 0) state.durationMs.toFloat() else 1f
                    WaveformSeekBar(
                        progress = (position.toFloat() / dur).coerceIn(0f, 1f),
                        onSeekFraction = { frac -> onSeek((frac * dur).roundToLong()) },
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

            // 控制卡:半透明深灰卡 + 轻度模糊封面 / 模式 | 上一首 | 播放(黄) | 下一首 | 队列
            Box(modifier = Modifier.fillMaxWidth().clip(cardShape)) {
                if (state.artworkUri != null) {
                    AsyncImage(
                        model = state.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .blur(10.dp),
                    )
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 裸图标统一白色(无圆底后不注入会继承到黑色)
                CompositionLocalProvider(LocalContentColor provides Color.White) {
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
