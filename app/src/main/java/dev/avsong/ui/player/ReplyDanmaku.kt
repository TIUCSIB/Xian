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
 * 热评弹幕:热评像弹幕一样从右往左飘过播放页上半区(封面黑纱上),循环播放。
 *
 * - 固定 3 条轨道;弹幕按序分配轨道,「右缘外 → 左缘外」匀速横穿
 *   (Animatable + LinearEasing;offset 像素级计算,随容器/弹幕实际宽度);
 * - 错峰:不同弹幕加不同启动延迟 + 飘完后统一休息 4s 再发下一批;
 * - 黑纱圆角底(45% 黑)保证任意封面可读;不消费点击,不挡封面/歌词手势;
 * - 最多同时 9 条(3 轨 × 3 批),够了——评论再多屏上也读不过来。
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
                        .height(44.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    batch
                        .withIndex()
                        .filter { (i, _) -> i % lanes == lane }
                        .forEach { (i, reply) ->
                            DanmakuItem(
                                reply = reply,
                                order = i / lanes,
                                startDelayMs = i * 2_800L + Random(i).nextLong(0, 1_000),
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
    order: Int,
    startDelayMs: Long,
) {
    var containerWidth by remember { mutableIntStateOf(0) }
    var chipWidth by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(reply, containerWidth, chipWidth) {
        if (containerWidth == 0 || chipWidth == 0) return@LaunchedEffect
        val travel = containerWidth + chipWidth
        delay(startDelayMs)
        while (true) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 13_000, easing = LinearEasing))
            delay(4_000L)
        }
    }

    // 行容器(占满轨道宽):弹幕绝对偏移。x = 容器宽 - progress * (容器宽 + 弹幕宽)
    // progress=0:左缘在容器右缘外;progress=1:右缘完全飘出容器左缘
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { containerWidth = it.width },
    ) {
        Row(
            Modifier
                .offset { IntOffset((containerWidth - progress.value * (containerWidth + chipWidth)).roundToInt(), 0) }
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
