package dev.avsong.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.avsong.player.PlayerState

/**
 * 底部迷你播放条:深灰圆角条,内含
 * 黄色圆钮(展开播放页)| 曲名| 深灰圆钮(播放/暂停)| 深灰圆钮(队列)。
 * 纯色扁平:无阴影、无渐变。
 *
 * [shape]/[outerPadding] 供与 AppBottomBar 熔接:与 tab 同屏时调用方传上圆角形状 +
 * 底边距 0,两者拼成一个「上圆下圆」的整块;单独悬浮时保持默认全圆胶囊。
 */
@Composable
fun MiniPlayerBar(
    state: PlayerState,
    onPlayPause: () -> Unit,
    onQueue: () -> Unit,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(50),
    outerPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    /** 同步采样播放位置:环形进度靠它定时读数(PlayerState 只在事件时推送,不含连续进度)。 */
    positionProvider: () -> Long = { 0L },
) {
    Row(
        modifier = Modifier
            .padding(outerPadding)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 黄色圆钮:封面缩略图,点击展开全屏播放页。
        // 播放中匀速旋转(唱片感,6s/圈):Animatable 循环被取消即停在当前角度,
        // 续播从当前角度接着转,暂停不会跳回 0 点;无封面时是播放图标,不转。
        val spinning = state.isPlaying && state.artworkUri != null
        val rotation = remember { Animatable(0f) }
        LaunchedEffect(spinning) {
            if (!spinning) return@LaunchedEffect
            while (true) {
                rotation.animateTo(
                    rotation.value + 360f,
                    animationSpec = tween(durationMillis = 6000, easing = LinearEasing),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer { rotationZ = rotation.value }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (state.artworkUri != null) {
                AsyncImage(
                    model = state.artworkUri,
                    contentDescription = "展开播放页",
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "展开播放页",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        Text(
            state.title ?: "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.width(8.dp))

        // 播放/暂停与队列:裸图标(用户要求按钮不带圆形背景)。
        // 播放键外圈加环形进度:不进播放页也能看到播到哪了(进度只在播放中轮询)。
        val durationMs = state.durationMs
        var positionMs by remember { mutableLongStateOf(0L) }
        LaunchedEffect(state.currentMediaId, state.isPlaying, durationMs) {
            while (true) {
                positionMs = positionProvider()
                if (!state.isPlaying) break
                kotlinx.coroutines.delay(500)
            }
        }
        val fraction = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else 0f
        // Canvas 的 DrawScope 读不到 MaterialTheme,颜色先在组合期取好
        val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        val arcColor = MaterialTheme.colorScheme.primary
        Box(contentAlignment = Alignment.Center) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    // PlayArrow/Pause 的矢量字形留白大(实心部分约占 box 七成),同 dp 会比
                    // 队列的 PlaylistPlay(几乎填满)显小,故放大到 30dp 让两者可见尺寸对齐
                    modifier = Modifier.size(30.dp),
                )
            }
            // 环压在裸图标外圈:底轨 + 进度弧(纯描边,无填充无阴影)
            Canvas(Modifier.size(36.dp)) {
                val sw = 2.dp.toPx()
                val r = (size.minDimension - sw) / 2f
                val topLeft = Offset(sw / 2f, sw / 2f)
                drawArc(
                    color = trackColor,
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft,
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = sw),
                )
                if (fraction > 0f) {
                    drawArc(
                        color = arcColor,
                        startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                        topLeft = topLeft,
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = sw, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Spacer(Modifier.width(2.dp))

        IconButton(onClick = onQueue, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.PlaylistPlay,
                contentDescription = "播放队列",
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
    }
}
