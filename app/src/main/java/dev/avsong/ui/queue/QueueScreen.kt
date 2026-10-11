package dev.avsong.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.avsong.player.QueueEntry
import dev.avsong.ui.common.PlayingEqualizer
import dev.avsong.ui.common.StateHint

/**
 * 播放队列页(播放页右上入口,带返回键):高亮当前项,点击跳转,尾部 X 移除。
 * 列表本体与「歌单」tab 共用 [QueueContent],只在外壳上区分。
 * (Media3 队列无稳定 id,拖拽排序暂不做——见 ROADMAP 技术债)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    queue: List<QueueEntry>,
    currentIndex: Int,
    onBack: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("播放队列", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        // 数量徽章:品牌纯色
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${queue.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        QueueContent(
            queue = queue,
            currentIndex = currentIndex,
            onPlayAt = onPlayAt,
            onRemoveAt = onRemoveAt,
            onMove = onMove,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

/**
 * 队列列表内容:「歌单」tab 与播放页入口共用(跳转/移除/上下移)。
 * 空态复用公共 StateHint,与首页/歌单页的「空」表达保持一致。
 *
 * [isPlaying] 当前曲正在播放时行首显示音波条动画;[showReorder] = false 时隐藏上下移
 * (迷你条抽屉用:排序功能不需要,腾出横向空间)。
 * [listState] 由调用方传入以便控制滚动位置(迷你条抽屉打开时滚到当前曲)。
 */
@Composable
fun QueueContent(
    queue: List<QueueEntry>,
    currentIndex: Int,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    showReorder: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
) {
    if (queue.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            StateHint(Icons.Rounded.MusicNote, "还没有歌", "去首页或搜索页点一首歌")
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            itemsIndexed(queue) { index, entry ->
                QueueRow(
                    index = index,
                    entry = entry,
                    isCurrent = index == currentIndex,
                    isPlaying = isPlaying,
                    showReorder = showReorder,
                    canMoveUp = index > 0,
                    canMoveDown = index < queue.lastIndex,
                    onClick = { onPlayAt(index) },
                    onRemove = { onRemoveAt(index) },
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    index: Int,
    entry: QueueEntry,
    isCurrent: Boolean,
    isPlaying: Boolean,
    showReorder: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                // 播放中:行首换成音波条动画;当前曲但暂停时保留播放三角(点行=续播)
                isCurrent && isPlaying ->
                    PlayingEqualizer(modifier = Modifier.size(width = 16.dp, height = 12.dp))
                isCurrent -> Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                else -> Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.artworkUri != null) {
                AsyncImage(entry.artworkUri, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 上移 / 下移(排序,迷你条抽屉不显示)/ 移除(紧凑触达区:48dp 三连会把标题挤到截断)
        if (showReorder) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "上移",
                    modifier = Modifier.size(18.dp),
                    tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "下移",
                    modifier = Modifier.size(18.dp),
                    tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                )
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "移除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
