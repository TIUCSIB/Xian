package dev.avsong.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import coil3.compose.AsyncImage
import dev.avsong.domain.Song
import dev.avsong.ui.MainUiState
import dev.avsong.ui.common.FlatRefreshIndicator
import dev.avsong.ui.common.LoadingHint
import dev.avsong.ui.common.PlayingEqualizer
import dev.avsong.ui.common.SongList
import dev.avsong.ui.common.StateHint

/** 精选轮播取榜单前 N 首,下方列表从第 N+1 首起(名次经 rankOffset 接着数)。 */
private const val FEATURED_COUNT = 3

/** 自动轮播间隔:每页停留 8s;用户拖动/触摸期间暂停。 */
private const val AUTO_CAROUSEL_MS = 8000L

/**
 * 首页(底部 tab「首页」):只展示热歌榜,布局一分为二(参考设计稿)——
 * 上半是黄色背景块,榜单前三首做成可左右滑动的圆角大卡(封面铺满)压在黄块上,
 * 卡片下沿越出黄块压到深色底;下半是第 4 首起的歌曲列表,行尾只留时长不放播放键。
 *
 * 五源切换已从 UI 摘除(VM 里的 HomeSource 机制保留,当前恒为 RANKING)。
 */
@Composable
fun HomeScreen(
    state: MainUiState,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayPause: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onLogin: () -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onFavorite: (Song) -> Unit = {},
    onDownload: (Song) -> Unit = {},
    /** 「定位当前播放」:返回该曲在列表(第 4 首起)中的下标,null = 当前榜里没有这首歌。 */
    onLocate: suspend () -> Int? = { null },
) {
    val listState = rememberLazyListState()
    // 定位序号:自增一次触发一次滚动(同一个目标连点也重新定位)
    var locateRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(locateRequest) {
        val index = onLocate() ?: return@LaunchedEffect
        // 前三首在列表里没有对应行,定位到它们时滚回列表顶部即可(卡片就在上方)
        listState.animateScrollToItem(index.coerceAtLeast(0))
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        ) {
            // 精选区在下拉刷新区之外,下拉时不动
            FeaturedSection(
                songs = state.homeSongs,
                loading = state.homeLoading,
                currentMediaId = state.player.currentMediaId,
                isPlaying = state.player.isPlaying,
                onPlay = onPlay,
                onTogglePlayPause = onPlayPause,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onFavorite = onFavorite,
                onDownload = onDownload,
            )

            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                // 已有内容时下拉才显示转圈;首次加载的空白交给 LoadingHint,避免两个加载态打架
                isRefreshing = state.homeLoading && state.homeSongs.isNotEmpty(),
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                indicator = {
                    FlatRefreshIndicator(
                        pullState,
                        state.homeLoading && state.homeSongs.isNotEmpty(),
                        Modifier.align(Alignment.TopCenter),
                    )
                },
            ) {
                when {
                    state.homeNeedsLogin -> LoginRequiredList(onLogin)
                    state.homeLoading && state.homeSongs.isEmpty() ->
                        LoadingHint("正在加载热歌榜…")
                    state.homeSongs.isEmpty() -> EmptyHomeList()
                    else -> SongList(
                        songs = state.homeSongs.drop(FEATURED_COUNT),
                        // 队列仍用整榜,点第 4 行实际入列的是全榜第 4 位
                        onPlay = { _, index -> onPlay(state.homeSongs, index + FEATURED_COUNT) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onFavorite = onFavorite,
                        onDownload = onDownload,
                        showRank = true,
                        rankOffset = FEATURED_COUNT,
                        showPlayButton = false,
                        currentMediaId = state.player.currentMediaId,
                        isPlaying = state.player.isPlaying,
                        hasMore = state.homeHasMore,
                        loadingMore = state.homeLoadingMore,
                        onLoadMore = onLoadMore,
                        listState = listState,
                        // 列表顶部留白:定位滚到第 0 行时不贴住精选卡片区
                        contentTopPadding = 12.dp,
                    )
                }
            }
        }

        // 「定位当前播放」悬浮圆钮:压在列表右下角(迷你条上方),半透明底不抢列表内容。
        // 榜内没有当前曲时不出现——点了也没得定位,不做无用交互。
        if (state.player.currentMediaId != null &&
            state.homeSongs.any { it.mediaId == state.player.currentMediaId }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 30.dp, bottom = 20.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { locateRequest++ },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.MyLocation,
                    contentDescription = "定位当前播放",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// 精选区几何常量:全部固定 dp,黄块与轮播的相对位置不随屏宽漂移
private val YELLOW_SIDE_MARGIN = 16.dp
private val YELLOW_INNER_H = 20.dp
private val TITLE_TOP = 10.dp
private val TITLE_ROW_H = 40.dp
private val CARD_H = 190.dp
/** 黄块底边切在卡片的哪个高度(其余部分压到深色底上)。 */
private const val CARD_OVER_YELLOW = 0.82f
/** 轮播距屏幕左缘 = 黄边距 + 黄内边距(与标题左缘对齐)。 */
private val PAGER_START = YELLOW_SIDE_MARGIN + YELLOW_INNER_H
/** 卡宽占轮播视口的比例;余量给右缘露边(卡改小后露边自然变宽)。 */
private const val CARD_WIDTH_FRACTION = 0.48f

/**
 * 精选区:黄色圆角大块 + 前三首轮播卡。
 * 黄块高度只包到卡片 CARD_OVER_YELLOW 处,pager 以固定偏移叠在同一 Box 里,
 * 卡片下半截自然压到深色底上;右缘露出下一张卡的边(卡片可越出黄块右边界)。
 */
@Composable
private fun FeaturedSection(
    songs: List<Song>,
    loading: Boolean,
    currentMediaId: String?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onFavorite: (Song) -> Unit,
    onDownload: (Song) -> Unit,
) {
    // 首次加载(还没有任何歌曲)只留空,卡片依赖歌曲封面,数据到了再渲染
    if (loading && songs.isEmpty()) return

    val featured = songs.take(FEATURED_COUNT)
    val pagerState = rememberPagerState { featured.size }
    val pagerTop = 8.dp + TITLE_TOP + TITLE_ROW_H

    // 自动轮播:每 8s 向前翻一页,到尾回卷第 0 页;用户拖动中跳过本轮。
    // ⚠ 不能拿 currentPage 判断"到尾":小卡宽+右缘露边布局下,末页吸附位(2*步长)
    // 远超最大滚动量,currentPage 永远停在倒数第二页——回卷条件永不成立,
    // 表现为"翻一次就卡住不回卷"(实测)。改为按 canScrollForward 推进:
    // 还能向前滚就走一页步长(animateScrollBy,末页同样可见),滚到头直接回卷第 0 页。
    LaunchedEffect(featured.size) {
        // 卡住检测:小卡+右缘露边布局下,末几页的吸附位被夹到最大滚动量处彼此重合,
        // currentPage 永远到不了 size-1(canScrollForward 也恒 true)——按页号判断"到尾"
        // 会永久卡死(实测)。改为:上轮请求的页这轮没到达 = 已顶进不可达区,回卷第 0 页。
        var lastRequested = -1
        while (isActive && featured.size > 1) {
            delay(AUTO_CAROUSEL_MS)
            if (pagerState.isScrollInProgress) continue
            val current = pagerState.currentPage
            // 没到达上轮请求的页 = 吸附位被夹住顶进不可达区(成功到达时 current == lastRequested)
            val stuck = lastRequested > current
            val next = when {
                stuck || !pagerState.canScrollForward -> 0
                else -> (current + 1).coerceAtMost(featured.size - 1)
            }
            lastRequested = if (next == 0) -1 else next
            try {
                pagerState.animateScrollToPage(next)
            } catch (e: Exception) {
                // 用户拖动抢占动画(CancellationException):放弃本轮
                if (!isActive) throw e
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
    ) {
        // 黄色背景块(层级在下):圆角块 + 左上 Discover 标题
        Box(
            Modifier
                .padding(start = YELLOW_SIDE_MARGIN, end = YELLOW_SIDE_MARGIN, top = 8.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                .height(TITLE_TOP + TITLE_ROW_H + CARD_H * CARD_OVER_YELLOW),
        ) {
            Text(
                "Discover",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = YELLOW_INNER_H, end = YELLOW_INNER_H, top = TITLE_TOP),
            )
        }

        // 前三首轮播(层级在黄块之上);小卡宽 + 右缘宽露边
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed((maxWidth - PAGER_START) * CARD_WIDTH_FRACTION),
            pageSpacing = 12.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = PAGER_START, top = pagerTop)
                .fillMaxWidth()
                .height(CARD_H),
        ) { page ->
            FeaturedSongCard(
                song = featured[page],
                isCurrent = featured[page].mediaId == currentMediaId,
                isPlaying = isPlaying,
                onClick = {
                    // 当前曲:整卡点击 = 切换播放/暂停;其他曲:从该曲入列整榜
                    if (featured[page].mediaId == currentMediaId) onTogglePlayPause()
                    else onPlay(songs, page)
                },
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onFavorite = onFavorite,
                onDownload = onDownload,
            )
        }
    }
}

/**
 * 单张精选卡:封面铺满圆角卡,右下播放键是唯一控件 ——
 * 当前曲在播时显示暂停态(点击暂停),否则显示播放态(点击从该曲入列整榜)。
 * 长按 = 弹「下一首播放/加入队列」菜单(与下方列表行同一套无损插入入口,
 * 前三首只在卡里出现,不补这里就没有别的入口了)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedSongCard(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onFavorite: (Song) -> Unit,
    onDownload: (Song) -> Unit,
) {
    val playingThis = isCurrent && isPlaying
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(CARD_H)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            ),
    ) {
        if (song.coverUrl != null) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        // 底部向上渐变遮罩:保证播放键在浅色封面上可读(用户明确要求,禁渐变规则的显式例外)
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    )
                ),
        )
        // 播放状态图标(用户要求):在播=音波条(与列表行/歌单卡同款,不再用 Pause);
        // 暂停/非当前曲=播放三角。整卡点击语义不变(当前曲点击=暂停/续播)
        if (playingThis) {
            PlayingEqualizer(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(width = 24.dp, height = 18.dp),
                onArtwork = true,
            )
        } else {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "播放${song.title}",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .size(32.dp),
            )
        }
        if (menuExpanded) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("下一首播放") },
                    leadingIcon = { Icon(Icons.Rounded.Queue, null) },
                    onClick = {
                        menuExpanded = false
                        onPlayNext(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("加入队列") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                    onClick = {
                        menuExpanded = false
                        onAddToQueue(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("收藏") },
                    leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null) },
                    onClick = {
                        menuExpanded = false
                        onFavorite(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("下载") },
                    leadingIcon = { Icon(Icons.Rounded.Download, null) },
                    onClick = {
                        menuExpanded = false
                        onDownload(song)
                    },
                )
            }
        }
    }
}

/**
 * 「最近」未登录态:整块可点,直接去登录页(比让用户自己找「我的」页少两步)。
 * 和其他空态一样必须由可滚动容器承载,否则下拉手势收不到 nested scroll。
 */
@Composable
private fun LoginRequiredList(onLogin: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillParentMaxSize().clickable(onClick = onLogin)) {
                StateHint(
                    Icons.Rounded.AccountCircle,
                    "最近播放需要登录",
                    "点这里扫码登录,登录后同步你的播放记录",
                )
            }
        }
    }
}

/**
 * 内容失败/为空空态。必须套一层可滚动容器:下拉手势经 nested scroll 从子级逐级上传,
 * 纯静态 Column 不产生滚动事件,「下拉重试」就会是摆设。
 * fillParentMaxSize 让 StateHint 铺满视口,内容仍居中。
 */
@Composable
private fun EmptyHomeList() {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillParentMaxSize()) {
                StateHint(
                    Icons.Rounded.MusicOff,
                    "这里还没有内容",
                    "下拉重试,或切到搜索页找歌",
                )
            }
        }
    }
}

/**
 * 纯色扁平下拉指示器已抽到 `ui/common/RefreshIndicator.kt`(首页与歌单页共用)。
 */
