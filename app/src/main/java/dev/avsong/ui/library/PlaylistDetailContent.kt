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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    /** 头部「下载」胶囊(整队入下载队列);null=胶囊不渲染。 */
    onDownloadAllParam: ((List<Song>) -> Unit)? = null,
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
    /** 多选模式批量动作(2026-10-15 加);null=不出「批量管理」入口。 */
    batchActions: BatchActions? = null,
) {
    // 搜索态:顶栏换成搜索框,列表隐藏头部只渲染过滤结果
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var moreExpanded by remember { mutableStateOf(false) }
    // 多选模式(2026-10-15 用户定版:**参考已下载页**——顶栏「多选」进入,顶栏换已选计数,
    // 行首出勾选圈,返回退出;不常驻勾选圈)。操作栏浮出时定位钮隐藏(防重叠)
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<String>()) }
    // 头部「下载」确认弹窗:批量下载太重(大合集几百首),先问再入队(2026-10-15 用户要求)
    var showDownloadConfirm by remember { mutableStateOf(false) }

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
                                    if (batchActions != null && songs.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("批量管理") },
                                            onClick = {
                                                moreExpanded = false
                                                selecting = true
                                                selected.value = emptySet()
                                            },
                                        )
                                    }
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
                    // 多选态底部有操作栏:bottom padding 加大避让(多选态无迷你条熔接问题,栏浮在列表上)
                    contentPadding = PaddingValues(bottom = if (selecting) 200.dp else 120.dp),
                ) {

                    stickyHeader(key = "header") {
                        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                        DetailHeader(
                            cover = cover,
                            metaLine = metaLine,
                            title = title,
                            subtitle = subtitle,
                            onPlayAll = { if (onPlayAll != null) onPlayAll(songs) else onPlay(songs, 0) },
                            onDownloadAll = { if (onDownloadAllParam != null) showDownloadConfirm = true },
                            onEnterSelect = {
                                if (selecting) {
                                    selecting = false
                                    selected.value = emptySet()
                                } else {
                                    selecting = true
                                }
                            },
                            selectActive = selecting,
                        )
                        }
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
                        if (selecting) {
                            SelectableSongRow(
                                song = song,
                                isCurrent = song.mediaId == currentMediaId,
                                isPlaying = isPlaying,
                                isSelected = song.bvid in selected.value,
                                onToggle = {
                                    selected.value =
                                        if (song.bvid in selected.value) selected.value - song.bvid
                                        else selected.value + song.bvid
                                },
                            )
                        } else {
                            NumberedSongRow(
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
                if (selecting && batchActions != null) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        BatchActionBar(
                            selectedCount = selected.value.size,
                            actions = batchActions,
                            selectedSongs = songs.filter { it.bvid in selected.value },
                            onDone = {
                                selecting = false
                                selected.value = emptySet()
                            },
                            onSelectAll = { selected.value = songs.map { it.bvid }.toSet() },
                            onInvert = {
                                val all = songs.map { it.bvid }.toSet()
                                selected.value = all - selected.value
                            },
                        )
                    }
                }
                // 头部「下载」确认:明确告知会把已加载的全部 N 首入队
                if (showDownloadConfirm && onDownloadAllParam != null) {
                    AlertDialog(
                        onDismissRequest = { showDownloadConfirm = false },
                        title = { Text("下载整个歌单?") },
                        text = { Text("将把已加载的 ${songs.size} 首歌全部加入下载队列(已下载的会自动跳过)。") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDownloadConfirm = false
                                onDownloadAllParam?.invoke(songs)
                            }) { Text("下载") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDownloadConfirm = false }) { Text("取消") }
                        },
                    )
                }
                if (locateIndex >= 0 && !selecting) {
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

/** 头部:大方封面 + 右侧元信息/大标题/UP 名;下方「播放(黄)/下载/多选」三胶囊
 *  (2026-10-15 用户改版:随机播放删除,空间让给下载与多选 —— 随机仍可从 ⋮/队列抽屉开)。 */
@Composable
private fun DetailHeader(
    cover: String?,
    metaLine: String,
    title: String,
    subtitle: String,
    onPlayAll: () -> Unit,
    onDownloadAll: () -> Unit,
    onEnterSelect: () -> Unit,
    /** 多选进行中:胶囊高亮 + 文案变「完成」(再点退出)。 */
    selectActive: Boolean = false,
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
            // 下载:深灰胶囊(整队入下载队列,Manager 去重已下载/已在队)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onDownloadAll),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "下载",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            // 多选开关:点击=行前出现勾选圈(原地多选,页面结构不变);再点=退出
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selectActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable(onClick = onEnterSelect),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Checklist,
                        contentDescription = null,
                        tint = if (selectActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (selectActive) "完成" else "多选",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
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
        // 行首封面缩略图:44dp 圆角,无封面时音符占位;
        // 当前曲封面叠黑纱(0.45,与共享 SongRow/首页精选卡同款)+ 白音波条(播放中)/白三角(暂停)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (song.coverUrl != null) {
                AsyncImage(
                    song.coverUrl,
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) {
                        PlayingEqualizer(
                            Modifier.size(width = 18.dp, height = 14.dp),
                            onArtwork = true,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
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


/**
 * 多选模式的批量动作集(2026-10-15 加)。全部对「选中的曲目子集」操作;
 * [onRemove] 为空(合集)时操作栏不渲染移除键,收藏夹/本地歌单各自传。
 */
data class BatchActions(
    val onDownload: (List<Song>) -> Unit,
    val onFavorite: (List<Song>) -> Unit,
    val onPlayNext: (List<Song>) -> Unit,
    val onAddToQueue: (List<Song>) -> Unit,
    val onRemove: ((List<Song>) -> Unit)? = null,
    /** 移除键文案(收藏夹=「从收藏夹移除」,本地歌单=「从歌单移除」)。 */
    val removeLabel: String = "移除",
)

/**
 * 多选态行(参考已下载页):序号位换成圆形勾选圈(选中=实心 primary+白勾,未选=描边圈),
 * 点整行=切换选中;当前曲仍 primary 高亮标题,便于在多选里认出正在播的歌。
 */
@Composable
private fun SelectableSongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .then(
                    if (isSelected) Modifier
                    else Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // 行首封面缩略图:44dp 圆角,无封面时音符占位;
        // 当前曲封面叠黑纱(0.45,与共享 SongRow/首页精选卡同款)+ 白音波条(播放中)/白三角(暂停)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (song.coverUrl != null) {
                AsyncImage(
                    song.coverUrl,
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) {
                        PlayingEqualizer(
                            Modifier.size(width = 18.dp, height = 14.dp),
                            onArtwork = true,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
    }
}

/**
 * 多选态底部操作栏:浮在列表底部(覆盖层,非导航)。纯色扁平无阴影;
 * 按钮横排,溢出可横向滚动;执行完回调 onDone 退出多选(动作本身由调用方反馈)。
 */
@Composable
private fun BatchActionBar(
    selectedCount: Int,
    actions: BatchActions,
    selectedSongs: List<Song>,
    onDone: () -> Unit,
    onSelectAll: () -> Unit = {},
    onInvert: () -> Unit = {},
) {
    // 纵向紧凑:计数行 + 等宽按钮行(2026-10-15 用户实测横排+滚动把「下载/移除」挤没)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "已选 $selectedCount 首",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // 全选/反选(原地多选无顶栏,这两键收在这里)
            TextButton(onClick = onSelectAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("全选", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onInvert, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("反选", style = MaterialTheme.typography.labelMedium)
            }
        }
        val enabled = selectedCount > 0
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BatchActionButton("下载", Modifier.weight(1f), enabled) {
                actions.onDownload(selectedSongs)
                onDone()
            }
            BatchActionButton("收藏", Modifier.weight(1f), enabled) {
                actions.onFavorite(selectedSongs)
                onDone()
            }
            BatchActionButton("下一首", Modifier.weight(1f), enabled) {
                actions.onPlayNext(selectedSongs)
                onDone()
            }
            BatchActionButton("入队", Modifier.weight(1f), enabled) {
                actions.onAddToQueue(selectedSongs)
                onDone()
            }
            actions.onRemove?.let { remove ->
                BatchActionButton("移除", Modifier.weight(1f), enabled) {
                    remove(selectedSongs)
                    onDone()
                }
            }
        }
    }
}

@Composable
private fun BatchActionButton(label: String, modifier: Modifier = Modifier, enabled: Boolean, onClick: () -> Unit) {
    // TextButton 默认水平 contentPadding 会吃掉等宽宽度,三字「下一首」被挤换行(用户实测)——压零 + maxLines=1
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Clip)
    }
}
