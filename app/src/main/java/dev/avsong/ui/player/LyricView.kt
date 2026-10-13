package dev.avsong.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.avsong.data.lyric.LyricLine
import dev.avsong.ui.LyricUiState

/**
 * 歌词视图:当前行高亮放大,自动滚动居中;点击某行 seek 到该时间点。
 */
@Composable
fun LyricView(
    lyric: LyricUiState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (lyric) {
            LyricUiState.None -> Text(
                "暂无歌词",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LyricUiState.Loading -> Text(
                "歌词加载中…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is LyricUiState.Ready -> LyricLines(lyric.lines, positionMs, onSeek)
        }
    }
}

@Composable
private fun LyricLines(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
) {
    val listState = rememberLazyListState()

    // 当前行 = 时间 ≤ position 的最后一行
    val activeIndex by remember(lines) {
        derivedStateOf {
            var idx = 0
            for (i in lines.indices) {
                if (lines[i].timeMs <= positionMs) idx = i else break
            }
            idx
        }
    }

    // 上下留白 = 半屏:第一行也能滚到中央;偏移量按视口比例算
    // (旧版写死 160dp padding + -400px 偏移,塞进小框时当前行被顶出可视区,看不清)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val halfPad = maxHeight / 2
        val centerOffset = with(LocalDensity.current) { (maxHeight / 2 - 40.dp).toPx().toInt() }
        LaunchedEffect(activeIndex, lines) {
            if (!listState.isScrollInProgress) {
                listState.animateScrollToItem(activeIndex, -centerOffset)
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = halfPad),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines) { index, line ->
                val active = index == activeIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = 28.dp, vertical = 8.dp)
                        .clickable { onSeek(line.timeMs) },
                ) {
                    Text(
                        text = line.text,
                        textAlign = TextAlign.Center,
                        fontSize = if (active) 20.sp else 15.sp,
                        lineHeight = if (active) 28.sp else 21.sp,
                        fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.Bold
                        else androidx.compose.ui.text.font.FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                    // 双语:翻译行(小一号、更淡)
                    line.sub?.let { sub ->
                        Text(
                            text = sub,
                            textAlign = TextAlign.Center,
                            fontSize = if (active) 15.sp else 12.sp,
                            lineHeight = if (active) 21.sp else 16.sp,
                            color = if (active) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}
