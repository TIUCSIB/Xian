package dev.avsong.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.avsong.domain.Song
import dev.avsong.domain.formatClock

/**
 * 首页/搜索页共用的歌曲列表组件(纯色扁平:无阴影/渐变/边框)。
 * 从 SearchScreen 抽出,避免热歌榜与搜索结果两套样式漂移。
 */

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun SongList(
    songs: List<Song>,
    onPlay: (List<Song>, Int) -> Unit,
    showRank: Boolean = false,
    /** 名次偏移:列表是整榜截掉前 N 首(进精选轮播)时,行首名次要接着 N 往下数。 */
    rankOffset: Int = 0,
    showPlayButton: Boolean = true,
    /** 当前播放曲的 mediaId:命中行的歌名高亮 + 播放中封面盖音波条徽章。 */
    currentMediaId: String? = null,
    isPlaying: Boolean = false,
    /** 非空时长按行弹菜单(下一首播放/加入队列/收藏/下载/删除);全空则无长按。 */
    onPlayNext: ((Song) -> Unit)? = null,
    onAddToQueue: ((Song) -> Unit)? = null,
    onFavorite: ((Song) -> Unit)? = null,
    onDownload: ((Song) -> Unit)? = null,
    /** 行菜单「删除」项(已下载页用:删文件+元数据);一般调用方传 null。 */
    onRemove: ((Song) -> Unit)? = null,
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    /** 由调用方持有:首页「定位当前播放」要按索引滚列表,内部状态拿不到。 */
    listState: LazyListState = rememberLazyListState(),
    /** 列表顶部留白;定位滚动到第 0 行时它能保证该行不贴住上方的精选卡片区。 */
    contentTopPadding: Dp = 0.dp,
) {
    // 接近底部时触发加载下一页
    LaunchedEffect(listState, hasMore, loadingMore) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            hasMore && !loadingMore && info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }.collect { shouldLoad -> if (shouldLoad) onLoadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = contentTopPadding, bottom = 120.dp),
    ) {
        itemsIndexed(songs, key = { _, s -> s.mediaId }) { index, song ->
            SongRow(
                song = song,
                rank = if (showRank) index + 1 + rankOffset else null,
                showPlayButton = showPlayButton,
                isCurrent = song.mediaId == currentMediaId,
                isPlaying = isPlaying,
                onClick = { onPlay(songs, index) },
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onFavorite = onFavorite,
                onDownload = onDownload,
                onRemove = onRemove,
            )
        }
        if (loadingMore) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        } else if (!hasMore && songs.isNotEmpty()) {
            // 到底提示:分页列表拉完后再往下滑能明确看到尽头,而不是无声中断
            item(key = "list-end") {
                Text(
                    "— 没有更多了 —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    rank: Int?,
    onClick: () -> Unit,
    showPlayButton: Boolean = true,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onPlayNext: ((Song) -> Unit)? = null,
    onAddToQueue: ((Song) -> Unit)? = null,
    onFavorite: ((Song) -> Unit)? = null,
    onDownload: ((Song) -> Unit)? = null,
    onRemove: ((Song) -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // 浅色/夜间分支用:主题不带 isDark 标记,以 surface 亮度判定(两套方案的 surface 差异极大)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                // 点歌=换队是默认逻辑;长按是无损插入入口(与主流音乐软件的行内菜单一致)
                onLongClick = if (onPlayNext != null || onAddToQueue != null || onRemove != null) {
                    { menuExpanded = true }
                } else null,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                text = "$rank",
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = if (rank <= 3) FontWeight.Black else FontWeight.Medium,
                color = if (rank <= 3) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 当前播放曲:整封面暗色遮罩(跟随封面圆角)+ 居中音波条,歌名高亮。
        // 遮罩深浅统一 0.45(2026-10-14 用户要求):音波条已改白色,浅色主题下也得靠深遮罩衬托;
        // 旧的「浅色减档到 12%」是给主题色音波条留的,现在不需要了(标题在遮罩外,不受影响)
        Box {
            CoverArt(url = song.coverUrl)
            if (isCurrent && isPlaying) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayingEqualizer(
                        modifier = Modifier.size(width = 18.dp, height = 14.dp),
                        onArtwork = true,
                    )
                }
            }
        }

        Spacer(Modifier.width(if (rank != null) 12.dp else 14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent && !dark) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent && dark) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    song.artist.takeIf { it.isNotBlank() },
                    song.progressHint,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (song.durationSec > 0) {
            Text(
                (song.durationSec * 1000).formatClock(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp),
            )
        }
        // 播放圆钮可关(首页列表行尾只留时长);前三黄色,其余深灰(纯色,无阴影)
        if (showPlayButton) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (rank != null && rank <= 3) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = if (rank != null && rank <= 3) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (menuExpanded) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                onPlayNext?.let { action ->
                    DropdownMenuItem(
                        text = { Text("下一首播放") },
                        leadingIcon = { Icon(Icons.Rounded.Queue, null) },
                        onClick = {
                            menuExpanded = false
                            action(song)
                        },
                    )
                }
                onAddToQueue?.let { action ->
                    DropdownMenuItem(
                        text = { Text("加入队列") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                        onClick = {
                            menuExpanded = false
                            action(song)
                        },
                    )
                }
                onFavorite?.let { action ->
                    DropdownMenuItem(
                        text = { Text("收藏") },
                        leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null) },
                        onClick = {
                            menuExpanded = false
                            action(song)
                        },
                    )
                }
                onDownload?.let { action ->
                    DropdownMenuItem(
                        text = { Text("下载") },
                        leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        onClick = {
                            menuExpanded = false
                            action(song)
                        },
                    )
                }
                onRemove?.let { action ->
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuExpanded = false
                            action(song)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun CoverArt(url: String?) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                // Crop:让封面撑满方形、不留白边(B 站封面宽高比不定,不指定会按 Fit 露出底色)
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(shape),
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 播放中的音波条:4 根错相位往复的竖条(纯色,非渐变)。
 * Canvas 直接读动画值,只重绘不重组;只在「当前曲 && 正在播放」时由调用方组合,
 * 暂停或非当前曲不出现。
 *
 * 配色按场景分(用户要求):[onArtwork] = true 表示音波条压在**封面**上(列表行封面遮罩/
 * 精选卡/歌单卡播放钮)——白色,靠加深后的封面遮罩(0.45)衬出对比;
 * false(默认)= 坐在纯色底上(队列抽屉行/歌单详情行)——主题色 primary。
 * ⚠ 不加描底:白色条叠深色描边在浅遮罩上会显出"描边感",用户反馈很怪;靠遮罩深度解决。
 */
@Composable
fun PlayingEqualizer(
    modifier: Modifier = Modifier,
    onArtwork: Boolean = false,
    color: Color = if (onArtwork) Color.White else MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bars = List(4) { i ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 380 + i * 110, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "eq$i",
        )
    }
    Canvas(modifier) {
        val n = bars.size
        val gap = 2.dp.toPx()
        val barW = (size.width - gap * (n - 1)) / n
        bars.forEachIndexed { i, anim ->
            val h = size.height * anim.value
            val x = i * (barW + gap)
            val y = size.height - h
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(width = barW, height = h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

@Composable
fun LoadingHint(text: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StateHint(
    icon: ImageVector,
    title: String,
    subtitle: String,
    /** 非空时在副标题下方渲染「重试」胶囊(加载失败态用;纯空态不给按钮)。 */
    onRetry: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                "重试",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRetry,
                    )
                    .padding(horizontal = 28.dp, vertical = 10.dp),
            )
        }
    }
}
