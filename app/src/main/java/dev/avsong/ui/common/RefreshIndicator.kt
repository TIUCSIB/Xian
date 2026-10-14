package dev.avsong.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp

/**
 * 纯色扁平下拉指示器:主色圆 + onPrimary 箭头(随下拉进度旋转)/转圈(刷新中)。
 * 不用 PullToRefreshDefaults.Indicator,因为它带 elevation 阴影,违反「禁阴影」设计约束。
 * 首页与歌单页共用(2026-10-11 从 HomeScreen 抽出)。
 */
@Composable
fun FlatRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(top = 12.dp)
            .graphicsLayer {
                val f = (if (isRefreshing) 1f else state.distanceFraction).coerceIn(0f, 1f)
                scaleX = f
                scaleY = f
                alpha = f
            }
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                Icons.Rounded.ArrowDownward,
                contentDescription = "下拉刷新",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = state.distanceFraction.coerceIn(0f, 1f) * 180f },
            )
        }
    }
}
