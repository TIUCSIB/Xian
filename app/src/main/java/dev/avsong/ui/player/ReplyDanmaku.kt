package dev.avsong.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.avsong.data.ReplyRepository
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 热评弹幕:热评像弹幕一样从右往左飘过播放页上半区,循环播放。
 *
 * 行程公式:x = containerW − progress × (containerW + chipW)。
 * progress=0 时弹幕左缘 = 容器宽(整体在右缘外),progress=1 时右缘完全离开左缘——
 * ⚠ chipWidth 必须等 onSizeChanged 上报后才开始动画,否则 travel 缺了弹幕宽,
 * 会出现「起始露一截/结束左侧残留」(用户实测反馈)。
 *
 * 节拍:同轨弹幕**上一条完全飘出后下一条才出发**(8s 飘完 + 1s 间隔),
 * 旧版按固定 delay 发车(同轨间隔 < 飘完时长)会叠在一起(用户实测反馈)。
 */
@Composable
fun ReplyDanmaku(
    replies: List<ReplyRepository.HotReply>,
    modifier: Modifier = Modifier,
) {
    if (replies.isEmpty()) return
    val lanes = 3
    val batch = replies.take(lanes * 3)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(160.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            repeat(lanes) { lane ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    batch
                        .withIndex()
                        .filter { (i, _) -> i % lanes == lane }
                        .forEach { (i, reply) ->
                            DanmakuItem(
                                reply = reply,
                                // 同轨错峰:第几条 × (飘完时长 8s + 间隔 1s);随机抖动避免三轨同步
                                startDelayMs = (i / lanes) * 9_500L + Random(i).nextLong(0, 1_500),
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun DanmakuItem(
    reply: ReplyRepository.HotReply,
    startDelayMs: Long,
) {
    var containerWidth by remember { mutableIntStateOf(0) }
    var chipWidth by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }

    // chipWidth 未测到前不启动(否则行程缺弹幕宽 → 边缘残留/提前出现)
    LaunchedEffect(reply, containerWidth, chipWidth) {
        if (containerWidth == 0 || chipWidth == 0) return@LaunchedEffect
        delay(startDelayMs)
        while (true) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 8_000, easing = LinearEasing))
            delay(1_000L)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { containerWidth = it.width },
    ) {
        Row(
            Modifier
                .offset {
                    IntOffset(
                        // progress=0 → x=containerW(弹幕整体在右缘外);progress=1 → x=−chipW(完全出左缘)
                        (containerWidth - progress.value * (containerWidth + chipWidth)).roundToInt(),
                        0,
                    )
                }
                .onSizeChanged { chipWidth = it.width },
        ) {
            DanmakuChip(reply)
        }
    }
}

@Composable
private fun DanmakuChip(reply: ReplyRepository.HotReply) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "${reply.uname} · ${reply.likeCount} 赞",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFE8E8E8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = reply.message,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
