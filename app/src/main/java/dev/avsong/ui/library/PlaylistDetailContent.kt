package dev.avsong.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.avsong.domain.Song
import dev.avsong.domain.formatClock
import dev.avsong.ui.common.PlayingEqualizer

/**
 * 歌单详情页共享布局(收藏夹内容页 / 合集内容页 2026-10-11 起共用,参考网易云式详情稿):
 * 顶栏裸返回键;头部 = 大方封面 + 元信息/大标题/UP 名;「播放/随机播放」双胶囊钮(黑黄适配:
 * 主操作黄色实心、随机深灰);曲目行 = 序号(当前曲换成音波条 + 灰底圆角垫)+ 标题 + UP·时长,
 * 行尾「⋯」菜单 = 下一首播放 / 加入队列 / 下载 / 移出收藏夹([onRemoveSong] 非空时,收藏夹专属)。
 * 随内容滚动,顶栏只有返回键。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailContent(
    title: String,
    metaLine: String,
    subtitle: String,
    cover: String?,
    songs: List<Song>,
    loading: Boolean,
    error: String?,
    emptyText: String,
    loadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    currentMediaId: String?,
    isPlaying: Boolean,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onRemoveSong: ((Song) -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        when {
            loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            error != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("加载失败:$error", color = MaterialTheme.colorScheme.onSurfaceVariant) }

            songs.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            else -> {
                val listState = rememberLazyListState()
                // 接近列表底部时自动加载下一页(原两个详情页的同款逻辑收拢到这里)
                LaunchedEffect(listState) {
                    snapshotFlow {
                        val info = listState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                        info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
                    }.collect { nearEnd -> if (nearEnd) onLoadMore() }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                ) {
                    item(key = "header") {
                        DetailHeader(
                            cover = cover,
                            metaLine = metaLine,
                            title = title,
                            subtitle = subtitle,
                            onPlayAll = { onPlay(songs, 0) },
                            onShuffleAll = { onShuffle(songs) },
                        )
                    }
                    itemsIndexed(songs, key = { _, s -> s.bvid }) { index, song ->
                        NumberedSongRow(
                            number = index + 1,
                            song = song,
                            isCurrent = song.mediaId == currentMediaId,
                            isPlaying = isPlaying,
                            onClick = { onPlay(songs, index) },
                            onPlayNext = onPlayNext,
                            onAddToQueue = onAddToQueue,
                            onDownload = onDownload,
                            onRemove = onRemoveSong,
                        )
                    }
                    if (loadingMore) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 头部:大方封面 + 右侧元信息/大标题/UP 名;下方「播放(黄)/随机播放(灰)」双胶囊。 */
@Composable
private fun DetailHeader(
    cover: String?,
    metaLine: String,
    title: String,
    subtitle: String,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    AsyncImage(
                        cover,
                        null,
                        Modifier.size(104.dp).clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    metaLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 播放:黄色实心胶囊(主操作,与全 app 黄色主键一致)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlayAll),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "播放",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            // 随机播放:深灰胶囊(随机开起后队列抽屉的模式胶囊会同步显示「随机播放」)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onShuffleAll),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "随机播放",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * 序号曲目行:左序号(当前曲换成黄色音波条,整行垫深灰圆角底)+ 标题(当前曲 primary 高亮)
 * + UP·时长,行尾「⋯」弹 下一首播放/加入队列/(移出收藏夹) 菜单;长按行同样弹菜单。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberedSongRow(
    number: Int,
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onRemove: ((Song) -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            )
            .padding(start = 12.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                PlayingEqualizer(Modifier.size(18.dp))
            } else {
                Text(
                    if (number < 100) "%02d".format(number) else number.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    song.artist.takeIf { it.isNotBlank() },
                    (song.durationSec * 1000).formatClock(),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.MoreHoriz,
                    contentDescription = "更多操作",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("下一首播放") },
                    onClick = {
                        onPlayNext(song)
                        menuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("加入队列") },
                    onClick = {
                        onAddToQueue(song)
                        menuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("下载") },
                    onClick = {
                        onDownload(song)
                        menuExpanded = false
                    },
                )
                if (onRemove != null) {
                    DropdownMenuItem(
                        text = { Text("从收藏夹移除") },
                        onClick = {
                            onRemove(song)
                            menuExpanded = false
                        },
                    )
                }
            }
        }
    }
}
