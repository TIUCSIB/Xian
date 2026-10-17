package dev.avsong.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * 队列列表内容:迷你条/播放页的队列抽屉共用(跳转/移除;歌单 tab 已改为歌单中心,不再渲染队列)。
 * 空态复用公共 StateHint,与首页/歌单页的「空」表达保持一致。
 *
 * [isPlaying] 当前曲正在播放时行首显示音波条动画;[showReorder] = false 时隐藏上下移
 * (迷你条抽屉用:排序功能不需要,腾出横向空间)。
 * [listState] 由调用方传入以便控制滚动位置(迷你条抽屉打开时滚到当前曲)。
 *
 * 多选模式([selectionMode]):点行=勾/取消(不再切播),行首显示勾选圈;
 * 批量操作(下载/收藏)由调用方在功能条渲染,本组件只负责勾选状态与回调。
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
    /** 多选模式开合与选中集合由调用方持有(功能条的批量按钮要读取 N)。 */
    selectionMode: Boolean = false,
    selected: Set<Int> = emptySet(),
    onToggleSelect: (Int) -> Unit = {},
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
                    entry = entry,
                    isCurrent = index == currentIndex,
                    isPlaying = isPlaying,
                    showReorder = showReorder,
                    canMoveUp = index > 0,
                    canMoveDown = index < queue.lastIndex,
                    onClick = { if (selectionMode) onToggleSelect(index) else onPlayAt(index) },
                    onRemove = { onRemoveAt(index) },
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) },
                    selectionMode = selectionMode,
                    selected = index in selected,
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
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
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    // 多选模式:勾中的行 primaryContainer 垫底(与当前曲同色系,勾圈另给)
                    selectionMode && selected -> MaterialTheme.colorScheme.primaryContainer
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 多选模式:行首勾选圈(所有行都有,横向对齐一致,不会错位)
        if (selectionMode) {
            Box(
                modifier = Modifier.width(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "已勾选",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        // 封面:当前曲把播放态叠在封面上(与首页列表同款),不再单占行首一列——
        // 单列会让当前曲的封面/标题整体右移,与其它行错位(用户反馈不协调)
        Box {
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
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) {
                        PlayingEqualizer(
                            modifier = Modifier.size(width = 18.dp, height = 14.dp),
                            onArtwork = true,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 无歌手不渲染空行:Row 已 CenterVertically,空 Text 会把标题顶到上半格(用户反馈不居中)
            if (entry.artist.isNotBlank()) {
                Text(
                    entry.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 上移 / 下移(排序,迷你条抽屉不显示)/ 移除:多选模式下全隐藏(防误触,行点击已是勾选)
        if (!selectionMode) {
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
}
