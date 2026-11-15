package dev.avsong.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.avsong.data.lyric.LyricLine
import dev.avsong.ui.LyricUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * 歌词视图:当前行高亮放大,自动滚动居中;点击某行 seek 到该时间点。
 * [onArtwork] = 歌词坐在封面黑纱上(播放页统一背景),文字用白色系;
 * 无封面时退回主题纯色底,用主题 onSurface 系。
 *
 * 滚动带「圆柱弧度」:离中心越远的行 rotationX 越大、越透明、略缩小
 * (graphicsLayer 里读 layoutInfo,只重绘不重组;用户要求的弧形观感,
 * 边缘透明即「还没滚进来先淡着」)。
 */
@Composable
fun LyricView(
    lyric: LyricUiState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onArtwork: Boolean = false,
    /** 拖动进度时的预览时间(非空=预览态:歌词滚到目标行并盖指示线);松手后传 null。 */
    previewMs: Long? = null,
) {
    // 高亮行用品牌黄:白 vs 灰的对比在弧度衰减(圆柱透明度)下不够醒目,
    // MV 同屏小视口里几乎看不出高亮(用户实测);黄=播放键/进度条的品牌语义,黑纱上对比最强
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = if (onArtwork) Color.White.copy(alpha = 0.55f)
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val subActiveColor = if (onArtwork) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val subInactiveColor = if (onArtwork) Color.White.copy(alpha = 0.4f)
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val hintColor = if (onArtwork) Color.White.copy(alpha = 0.7f)
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (lyric) {
            LyricUiState.None -> Text(
                "暂无歌词",
                style = MaterialTheme.typography.bodyLarge,
                color = hintColor,
            )

            LyricUiState.Loading -> Text(
                "歌词加载中…",
                style = MaterialTheme.typography.bodyMedium,
                color = hintColor,
            )

            is LyricUiState.Ready -> LyricLines(
                lyric.lines, positionMs, onSeek,
                activeColor, inactiveColor, subActiveColor, subInactiveColor,
            )
        }
    }
}

@Composable
private fun LyricLines(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    subActiveColor: Color,
    subInactiveColor: Color,
) {
    val listState = rememberLazyListState()

    // 当前行 = 时间 ≤ position 的最后一行。
    // ⚠ 不能用 derivedStateOf 包 positionMs —— 它是普通参数不是 State,
    // derivedStateOf 感知不到它的变化,activeIndex 会永远停在首帧值(踩过:歌词完全不滚)。
    var activeIndex by remember(lines) { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(lines, positionMs) {
        var idx = 0
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) idx = i else break
        }
        activeIndex = idx
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 当前行变化时把它滚到视口正中(用户手动滚动中不打断)。
        // ⚠ scrollOffset 是「滚动距离」,不是「上移量」:正值=继续往前滚(内容上移)、
        // 负值=往回滚(目标行在屏上更靠下)。contentPadding 已经是视口半高,offset=0 时
        // 目标行「顶边」正落在视口中线;传 -maxHeight/2 会把它一路推到视口下缘之外
        // (整条高亮看不见,实测踩过;半行高 40dp 那版只是沉到下半,方向也是错的)。
        // 要让行心对齐中线,offset 只能是「目标行实测行高的一半」——行高不可预算(长句换行/有无译文),
        // 所以先量再滚。⚠ 必须在 BoxWithConstraints 内:maxHeight 是它的作用域属性。
        LaunchedEffect(activeIndex, lines, maxHeight) {
            if (listState.isScrollInProgress) return@LaunchedEffect
            suspend fun activeItemSize(): Int? =
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }?.size

            var h = activeItemSize()
            if (h == null) {
                // 目标行不在可见区(seek/大跳行):先把它顶到中线,下一帧量到真实行高
                listState.scrollToItem(activeIndex)
                h = withTimeoutOrNull(400) {
                    snapshotFlow { listState.layoutInfo }
                        .first { info -> info.visibleItemsInfo.any { it.index == activeIndex } }
                        .visibleItemsInfo.first { it.index == activeIndex }.size
                }
            }
            if (h != null) {
                listState.animateScrollToItem(activeIndex, h / 2)
                // 高亮行字号更大:切换瞬间量到的可能还是「未激活」高度,按布局落地后的真实值再校一次
                activeItemSize()?.let { real ->
                    if (real != h) listState.animateScrollToItem(activeIndex, real / 2)
                }
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = maxHeight / 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines) { index, line ->
                val active = index == activeIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .graphicsLayer {
                            // 距视口中心的归一化偏移(-1..1):越远越斜、越淡、越小 → 圆柱感。
                            // ⚠ 当前行豁免:即使还没滚到正中也保持全亮 —— 衰减压在黄色高亮上
                            // 会暗得像有遮罩(用户实测);颜色对比本来就是高亮的语义,不能被吃掉
                            if (active) {
                                alpha = 1f
                                return@graphicsLayer
                            }
                            val info = listState.layoutInfo
                            val vpCenter =
                                (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val half =
                                (info.viewportEndOffset - info.viewportStartOffset) / 2f
                            val item =
                                info.visibleItemsInfo.firstOrNull { it.index == index }
                            if (item != null && half > 0f) {
                                val d =
                                    ((item.offset + item.size / 2f) - vpCenter) / half
                                rotationX = (d * 26f).coerceIn(-40f, 40f)
                                // 透视相机距离:graphicsLayer 的 lambda 是 Density,直接换算 dp
                                cameraDistance = 12.dp.toPx()
                                val f = abs(d).coerceIn(0f, 1f)
                                alpha = 1f - f * 0.72f
                                scaleX = 1f - f * 0.08f
                                scaleY = scaleX
                            } else {
                                alpha = 0.28f
                            }
                        }
                        .padding(horizontal = 28.dp, vertical = 8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSeek(line.timeMs) },
                ) {
                    Text(
                        text = line.text,
                        textAlign = TextAlign.Center,
                        fontSize = if (active) 20.sp else 15.sp,
                        lineHeight = if (active) 28.sp else 21.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) activeColor else inactiveColor,
                    )
                    // 双语:翻译行(小一号、更淡)
                    line.sub?.let { sub ->
                        Text(
                            text = sub,
                            textAlign = TextAlign.Center,
                            fontSize = if (active) 15.sp else 12.sp,
                            lineHeight = if (active) 21.sp else 16.sp,
                            color = if (active) subActiveColor else subInactiveColor,
                        )
                    }
                }
            }
        }

    }
}
