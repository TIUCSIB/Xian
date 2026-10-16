package dev.avsong.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.avsong.data.ReplyRepository
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 热评弹幕:热评从右往左飘过播放页上半区。
 *
 * ⚠ 调用方不要给本层横向 padding(全幅):弹幕要贴着真正的屏幕边缘游入游出;
 * 若被内边距框住,clip 边界落在半路,弹幕字符会被"切"在离屏边 16dp 的 invisible 边界上
 * (用户实测:看着像被限定范围,应该像游出屏幕)。
 *
 * 轨道铺满整个上层区域(weight 三等分),不用固定 160dp 居中带(上下留大段空白,用户反馈)。
 *
 * 每轨**严格串行**(同一时刻一条)但节拍**全随机**——⚠ 固定节拍是踩过的坑:
 * 错峰延迟只对首条生效时,三轨周期相同(飘行+间隔),第一轮后必然重新锁成同步,
 * 之后永远三条齐发、纵向等距(用户两次反馈)。所以每条的间隔/速度/纵向位置都抽随机数,
 * 三轨永不重同步,节奏才像 B 站弹幕的错落感。
 */
@Composable
fun ReplyDanmaku(
    replies: List<ReplyRepository.HotReply>,
    modifier: Modifier = Modifier,
    /** 详情弹窗内点赞(翻转 action/likeCount 由调用方在数据层做);null=不显示点赞钮。 */
    onLikeReply: ((ReplyRepository.HotReply) -> Unit)? = null,
) {
    if (replies.isEmpty()) return
    // 点中的弹幕(非空时弹详情 Modal 看全文);状态提在根级,chip 与弹窗解耦
    var selectedReply by remember { mutableStateOf<ReplyRepository.HotReply?>(null) }
    val lanes = 3
    // 全量热评按序轮流分轨,每轨独立循环播放自己的队列
    val laneQueues = remember(replies) {
        List(lanes) { lane -> replies.withIndex().filter { it.index % lanes == lane }.map { it.value } }
    }
    Column(
        modifier
            .fillMaxSize()
            .clipToBounds(),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        laneQueues.forEachIndexed { lane, queue ->
            if (queue.isNotEmpty()) {
                DanmakuLane(
                    queue = queue,
                    onReplyClick = { selectedReply = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }

    // 弹幕详情:点弹幕看全文(用户要求);Dialog 居中卡片,点遮罩/返回关闭
    // selectedReply 每次 recompose 从 replies 里找最新版(点赞翻转后赞数/高亮同步)
    selectedReply = selectedReply?.let { sel -> replies.firstOrNull { it.rpid == sel.rpid } ?: sel }
    selectedReply?.let { reply ->
        Dialog(onDismissRequest = { selectedReply = null }) {
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .widthIn(max = 320.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        reply.uname,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 右上角赞数 = 点赞入口(与热评抽屉行内赞钮同款操作):
                    // 点按翻转赞态,已赞 primary 高亮;onLikeReply=null 时退化为纯展示
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (onLikeReply != null) {
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onLikeReply(reply) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        } else Modifier,
                    ) {
                        Icon(
                            Icons.Filled.ThumbUp,
                            contentDescription = if (reply.action == 1) "取消点赞" else "点赞",
                            modifier = Modifier.size(14.dp),
                            tint = if (reply.action == 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${reply.likeCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (reply.action == 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 长评限高滚动:歌词全文这类超长热评会把弹窗撑出屏幕(用户实测),
                // 正文限 55% 屏高、超出滚动;头部(用户名/点赞)固定不动
                Text(
                    reply.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.55).dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

@Composable
private fun DanmakuLane(
    queue: List<ReplyRepository.HotReply>,
    onReplyClick: (ReplyRepository.HotReply) -> Unit,
    modifier: Modifier = Modifier,
) {
    var containerWidth by remember { mutableIntStateOf(0) }
    var chipWidth by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(0) }
    // gen 每轮 +1 驱动重启:单条队列时 index 不变、新弹幕宽度可能与上一条恰好相同——
    // 只按 (宽度, index) 做 key 会漏掉这两种重启,弹幕卡在屏外不再动
    var gen by remember { mutableIntStateOf(0) }
    var firstLaunch by remember { mutableStateOf(true) }
            // 本条的纵向抖动(dp):轨道位置固定等距看着机械(用户反馈),每条在轨内小范围随机偏移;
            // chip 允许两行后高度变大,幅度收窄 ±6dp 防触邻轨
    var yJitterDp by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }

    Box(modifier, contentAlignment = Alignment.CenterStart) {
        LaunchedEffect(containerWidth, chipWidth, gen) {
            if (containerWidth == 0 || chipWidth == 0) return@LaunchedEffect
            if (firstLaunch) {
                firstLaunch = false
                // 首条随机入场(0.2–3.8s):三轨别同时出现
                delay(Random.Default.nextLong(200, 3_800))
            }
            yJitterDp = Random.Default.nextInt(-6, 7)
            progress.snapTo(0f)
            // 飘行时长微随机(7.5–9.3s):速度一致会强化"机械感"
            progress.animateTo(1f, tween(durationMillis = (7_500 + Random.Default.nextLong(0, 1_800)).toInt(), easing = LinearEasing))
            // 条间间隔随机(0.7–3.9s):三轨各自随机漂移,永不重新同步(三条齐发的根因就是固定周期)
            delay(700 + Random.Default.nextLong(0, 3_200))
            index = (index + 1) % queue.size
            gen++
        }
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { containerWidth = it.width },
        ) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            // progress=0 → 整体在右缘外;progress=1 → 完全出左缘
                            (containerWidth - progress.value * (containerWidth + chipWidth)).roundToInt(),
                            yJitterDp.dp.roundToPx(),
                        )
                    }
                    .onSizeChanged { chipWidth = it.width },
            ) {
                DanmakuChip(queue[index], onReplyClick)
            }
        }
    }
}

@Composable
private fun DanmakuChip(
    reply: ReplyRepository.HotReply,
    onReplyClick: (ReplyRepository.HotReply) -> Unit,
) {
    // 宽度上限 = 屏宽 − 24dp(左右各留 12dp 呼吸);内容两行,绝大多数热评可完整显示,
    // 只有超长评(>2 行)才省略——此前单行+固定 320dp,多数热评被切得只剩半句(用户反馈「字幕有点短」)
    val maxChipWidth = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 24).dp
    Column(
        Modifier
            // 点击弹幕看全文(用户要求):只点 chip 本体,chip 外空白不消费——
            // 封面点击切歌词/歌词 seek 的手势不受影响
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onReplyClick(reply) }
            .widthIn(max = maxChipWidth)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
