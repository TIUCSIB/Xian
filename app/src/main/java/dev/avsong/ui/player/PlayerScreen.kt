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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
    onBack: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
    onAddToQueue: () -> Unit,
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                .padding(horizontal = 16.dp),
        ) {
            // 顶栏:返回 / 标题 / 队列
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(onClick = onBack, size = 44) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (showLyric) "歌词" else "正在播放",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                CircleButton(onClick = onFavorite, size = 44) {
                    Icon(
                        Icons.Rounded.StarBorder,
                        contentDescription = "收藏",
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                CircleButton(onClick = onAddToQueue, size = 44) {
                    Icon(
                        Icons.Rounded.PlaylistAdd,
                        contentDescription = "加入歌单",
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                CircleButton(onClick = onQueue, size = 44) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = "播放队列",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 信息卡:曲名 / 歌手 / 进度 / (点击切换歌词)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f))
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
                    Slider(
                        value = (position.toFloat() / dur).coerceIn(0f, 1f),
                        onValueChange = { frac -> onSeek((frac * dur).roundToLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
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

            Spacer(Modifier.height(12.dp))

            // 控制卡:随机 | 上一首 | 播放(黄) | 下一首 | 循环
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 播放模式键:单击循环切换(列表循环→单曲循环→随机→顺序),图标随模式变
                CircleButton(onClick = onCycleMode, size = 44) {
                    val (modeIcon, modeDesc) = when {
                        state.shuffleEnabled -> Icons.Rounded.Shuffle to "随机播放"
                        state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE ->
                            Icons.Rounded.RepeatOne to "单曲循环"
                        else -> Icons.Rounded.Repeat to "列表循环"
                    }
                    Icon(
                        modeIcon,
                        contentDescription = modeDesc,
                        modifier = Modifier.size(20.dp),
                        tint = if (state.shuffleEnabled ||
                            state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CircleButton(onClick = onPrevious, size = 48) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(24.dp),
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
                CircleButton(onClick = onNext, size = 48) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(24.dp),
                    )
                }
                // 右端 = 播放队列抽屉(与迷你条同款;循环键已并入左侧模式键)
                CircleButton(onClick = onQueue, size = 44) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = "播放队列",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 深灰圆形按钮:纯色扁平,无阴影。 */
@Composable
private fun CircleButton(
    onClick: () -> Unit,
    size: Int,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
