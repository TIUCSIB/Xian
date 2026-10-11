package dev.avsong.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
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

        // 深灰圆钮:播放/暂停
        CircleButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state.isPlaying) "暂停" else "播放",
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(4.dp))

        // 深灰圆钮:播放队列
        CircleButton(onClick = onQueue) {
            Icon(
                Icons.AutoMirrored.Rounded.PlaylistPlay,
                contentDescription = "播放队列",
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
    }
}

/** 深灰圆形按钮:纯色扁平,无阴影。 */
@Composable
private fun CircleButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
