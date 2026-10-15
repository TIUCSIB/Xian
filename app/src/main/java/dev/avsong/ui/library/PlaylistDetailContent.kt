package dev.avsong.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    /** 头部「播放」钮专用(2026-10-15 起)= 整队替换语义;缺省回退 onPlay(songs, 0)。 */
    onPlayAll: ((List<Song>) -> Unit)? = null,
    /** 列表顶部进度行(如「已加载 45/978,补全中…」);null=不渲染。大合集补全时的加载反馈。 */
    loadingHint: String? = null,
    onShuffle: (List<Song>) -> Unit,
    currentMediaId: String?,
    isPlaying: Boolean,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onFavorite: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onRemoveSong: ((Song) -> Unit)? = null,
    /** 移除项文案(收藏夹=「从收藏夹移除」,本地歌单=「从歌单移除」)。 */
    removeSongLabel: String = "从收藏夹移除",
    /** 歌单内搜索:打开搜索时触发「加载全部」(大合集要搜得到就得全量),进度经 [loadingAll]。 */
    loadingAll: Boolean = false,
    onLoadAll: () -> Unit = {},
    /** 顶栏「⋮」菜单项(如 取消订阅/删除收藏夹);空则不出「⋮」。 */
    menuActions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    // 搜索态:顶栏换成搜索框,列表隐藏头部只渲染过滤结果
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var moreExpanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 沉浸式背景:歌单封面放大模糊 + 压暗,头部与列表浮在上面(参考播放页毛玻璃先例)。
        // 只盖住 Scaffold 内容区,无封面时不垫(避免纯色上突兀的暗块)
        if (!loading && error == null && songs.isNotEmpty() && cover != null && !searching) {
            AsyncImage(
                cover,
                null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)),
            )
        }
    Scaffold(
        topBar = {
            if (searching) {
                SearchTopBar(
                    query = query,
                    onQueryChange = { query = it },
                    onExit = {
                        searching = false
                        query = ""
                    },
                )
            } else {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (songs.isNotEmpty()) {
                            IconButton(onClick = {
                                searching = true
                                onLoadAll()
                            }) {
                                Icon(Icons.Rounded.Search, contentDescription = "搜索歌单内歌曲")
                            }
                        }
                        if (menuActions.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { moreExpanded = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
                                }
                                DropdownMenu(
                                    expanded = moreExpanded,
                                    onDismissRequest = { moreExpanded = false },
                                ) {
                                    menuActions.forEach { (label, action) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                moreExpanded = false
                                                action()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            }
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

            searching -> SearchResults(
                songs = songs,
                query = query,
                loadingAll = loadingAll,
                currentMediaId = currentMediaId,
                isPlaying = isPlaying,
                onPlay = onPlay,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onFavorite = onFavorite,
                onDownload = onDownload,
                onRemove = onRemoveSong,
                removeSongLabel = removeSongLabel,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

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
                // 「定位当前播放」:当前曲在本歌单里的曲目下标(-1=不在歌单中,钮不出现)
                val locateIndex = remember(songs, currentMediaId) {
                    songs.indexOfFirst { it.mediaId == currentMediaId }
                }
                var locateTick by remember { mutableIntStateOf(0) }
                // tick 变化执行滚动:曲目 i 在列表里的 index 是 i+1(index 0 是 header)
                LaunchedEffect(locateTick) {
                    if (locateTick > 0 && locateIndex >= 0) {
                        // 目标超出已加载范围时先连续触发 loadMore 把页拉出来,滚动由 animate 追进
                        while (locateIndex >= songs.size && hasMore) {
                            onLoadMore()
                            kotlinx.coroutines.delay(300)
                        }
                        listState.animateScrollToItem(
                            (locateIndex + 1).coerceAtMost(listState.layoutInfo.totalItemsCount - 1),
                        )
                    }
                }
                Box(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    item(key = "header") {
                        DetailHeader(
                            cover = cover,
                            metaLine = metaLine,
                            title = title,
                            subtitle = subtitle,
                            onPlayAll = { if (onPlayAll != null) onPlayAll(songs) else onPlay(songs, 0) },
                            onShuffleAll = { onShuffle(songs) },
                        )
                    }
                    if (loadingHint != null) {
                        item(key = "loading-hint") {
                            Text(
                                loadingHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
                            )
                        }
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
                            onFavorite = onFavorite,
                            onDownload = onDownload,
                            onRemove = onRemoveSong,
                            removeSongLabel = removeSongLabel,
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
                if (locateIndex >= 0) {
                    // 「定位当前播放」悬浮圆钮:压在列表右下角(迷你条上方),与首页同款交互。
                    // 半透明底不抢列表内容;歌单/合集都可定位——「浏览到几千首找当前曲」是真实痛点。
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 18.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .clickable { locateTick++ },
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
    onFavorite: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onRemove: ((Song) -> Unit)?,
    removeSongLabel: String = "从收藏夹移除",
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
            when {
                // 播放中才走音波动画;当前曲但暂停时显示播放三角(与队列抽屉同款,别让暂停装作在播)
                isCurrent && isPlaying ->
                    PlayingEqualizer(Modifier.size(width = 18.dp, height = 14.dp))
                isCurrent -> Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                else -> Text(
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
                    text = { Text("收藏") },
                    leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null) },
                    onClick = {
                        onFavorite(song)
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
                        text = { Text(removeSongLabel) },
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

/** 搜索态顶栏:返回键(退出搜索)+ 自动聚焦的搜索框 + 清空键。 */
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onExit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出搜索")
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    "搜索歌单内的歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "清空")
            }
        }
    }
}

/**
 * 搜索结果列表:按标题/UP 名实时过滤已加载歌曲(打开搜索时已触发加载全部,
 * 结果会随后台拉页逐渐变全)。点行播放的是**过滤后的子集**(队列只装筛出来的歌)。
 */
@Composable
private fun SearchResults(
    songs: List<Song>,
    query: String,
    loadingAll: Boolean,
    currentMediaId: String?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onFavorite: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onRemove: ((Song) -> Unit)?,
    removeSongLabel: String = "从收藏夹移除",
    modifier: Modifier = Modifier,
) {
    val filtered = if (query.isBlank()) songs else songs.filter {
        it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
    }
    Column(modifier) {
        if (loadingAll) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "正在加载全部歌曲… 已加载 ${songs.size} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (loadingAll) "还没有匹配的结果…" else "没有找到「$query」",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                itemsIndexed(filtered, key = { _, s -> s.bvid }) { index, song ->
                    NumberedSongRow(
                        number = index + 1,
                        song = song,
                        isCurrent = song.mediaId == currentMediaId,
                        isPlaying = isPlaying,
                        onClick = { onPlay(filtered, index) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onFavorite = onFavorite,
                        onDownload = onDownload,
                        onRemove = onRemove,
                        removeSongLabel = removeSongLabel,
                    )
                }
            }
        }
    }
}
