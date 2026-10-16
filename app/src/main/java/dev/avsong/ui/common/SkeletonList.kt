package dev.avsong.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 骨架屏:加载中的灰色占位块列表。
 * 设计规范「纯色扁平:禁渐变、禁阴影」,故不用 shimmer 扫光,改纯 alpha 呼吸
 * (0.45↔1.0,整列表同步明暗,无位移无渐变)。
 * 骨架行结构与 SongRow 对齐(52dp 封面 + 两行文字 + 时长块),数据到达换屏不跳版。
 */
@Composable
fun SkeletonList(
    rows: Int = 8,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    // 整列表一个 alpha 驱动,所有块同步呼吸;比每块独立动画便宜,观感也更稳
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val blockColor = MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(8.dp)

    Column(modifier = modifier.padding(top = 8.dp)) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(blockColor.copy(alpha = alpha)),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp)
                            .clip(shape)
                            .background(blockColor.copy(alpha = alpha)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .clip(shape)
                            .background(blockColor.copy(alpha = alpha)),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .width(36.dp)
                        .height(12.dp)
                        .clip(shape)
                        .background(blockColor.copy(alpha = alpha)),
                )
            }
        }
    }
}

/**
 * 歌单页骨架(2026-10-16):匹配 PlaylistRow 的列表行结构(56dp 方图 + 标题条 + 「N 首」条),
 * 而不是旧的两列网格卡。歌单页从网格改列表行后,SkeletonGrid 已不匹配(用户实测:骨架样式不对)。
 */
@Composable
fun SkeletonPlaylistRows(
    rows: Int = 6,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val blockColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.padding(top = 8.dp)) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(blockColor.copy(alpha = alpha)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(blockColor.copy(alpha = alpha)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.25f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(blockColor.copy(alpha = alpha)),
                    )
                }
            }
        }
    }
}
