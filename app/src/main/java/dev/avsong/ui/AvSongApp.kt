package dev.avsong.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.media3.common.Player
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.avsong.ui.home.HomeScreen
import dev.avsong.ui.library.BatchFavoriteDialog
import dev.avsong.ui.library.CollectionContentScreen
import dev.avsong.ui.library.FavoritePickerDialog
import dev.avsong.ui.library.FolderContentScreen
import dev.avsong.ui.library.LibraryScreen
import dev.avsong.ui.library.LocalPlaylistContentScreen
import dev.avsong.ui.library.PlaylistScreen
import dev.avsong.ui.library.LoginEntryDialog
import dev.avsong.ui.player.PlayerScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.remember
import dev.avsong.ui.common.SongList
import dev.avsong.ui.common.StateHint
import dev.avsong.ui.common.ThemedNoticeHost
import dev.avsong.ui.downloads.DownloadedScreen
import dev.avsong.ui.queue.QueueContent
import dev.avsong.ui.search.SearchScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val PLAYLIST = "playlist"
    const val MINE = "mine"
    const val FOLDER = "library/folder/{folderId}?title={title}"
    const val COLLECTION = "library/collection/{mid}/{seasonId}?title={title}"
    const val LOCAL_PLAYLIST = "library/local/{playlistId}?title={title}"
    const val DOWNLOADED = "library/downloaded"
    const val PLAY_HISTORY = "library/play-history"
    const val PLAYBACK_SETTINGS = "library/playback-settings"
    const val ABOUT = "library/about"

    fun folder(id: Long, title: String): String =
        "library/folder/$id?title=${android.net.Uri.encode(title)}"

    fun collection(mid: Long, seasonId: Long, title: String): String =
        "library/collection/$mid/$seasonId?title=${android.net.Uri.encode(title)}"

    fun localPlaylist(id: Long, title: String): String =
        "library/local/$id?title=${android.net.Uri.encode(title)}"
}

/** 顶级 tab 路由 → 对应 tab;非 tab 页(播放/队列/登录/收藏夹内容)返回 null。 */
private fun routeToTab(route: String?): TopTab? = TopTab.entries.firstOrNull { it.route == route }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvSongApp(navController: NavHostController, viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = routeToTab(currentRoute)

    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as dev.avsong.AvSongApp
    val snackbarHostState = remember { SnackbarHostState() }
    var showFavoritePicker by remember { mutableStateOf(false) }
    // 全局登录弹窗:各页(首页未登录态/歌单错误区)的「去登录」统一走它(旧整页 LoginScreen 已删)
    var showLoginDialog by remember { mutableStateOf(false) }
    // 列表行长按「收藏」的目标曲(非空时弹 FavoritePickerDialog);迷你条收藏用当前曲
    var favoriteTargetBvid by remember { mutableStateOf<String?>(null) }
    // 迷你条的队列入口:底部抽屉(队列页已删,播放队列只在此抽屉展示)
    var showQueueSheet by remember { mutableStateOf(false) }
    // 播放详情:与队列抽屉同款的底部上滑面板(非导航页,用户要求)
    var showPlayerSheet by remember { mutableStateOf(false) }
    // 抽屉内双 tab:0=当前播放 1=历史播放(打开时保持上次选中的 tab)
    var queueTab by remember { mutableStateOf(0) }
    val playHistory by viewModel.playHistory.collectAsState()
    // 睡眠定时剩余分钟(null=未启用);播放页顶栏图标显示
    val sleepMinutesLeft by viewModel.sleepMinutesLeft.collectAsState()
    // 热评展示设置(弹幕/列表/关);数据由 PlayerScreen 点热评键时按需拉取(进页不自动拉)
    val danmakuSettings by app.settingsStore.state.collectAsState()
    val speedSettings by app.settingsStore.state.collectAsState()

    // 播放错误 → Snackbar(带重试动作)
    LaunchedEffect(state.player.error) {
        val err = state.player.error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "播放失败:$err",
            actionLabel = "重试",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.retryPlayback()
    }

    // 播放面板打开时拦截系统返回:收回面板而不是退出应用
    BackHandler(enabled = showPlayerSheet) {
        showPlayerSheet = false
    }

    // 下载/队列插入反馈:主题化应用内提示条(系统 Toast 带机器人图标且配色固定,与主题脱节,用户点名换掉)。
    // 多路消息共用一个 Host,同时到达时显示最新的那条(消费即置空,StateFlow 末值天然是"最新")。
    val appContext = LocalContext.current.applicationContext
    val downloadMsg by viewModel.downloadMessage.collectAsState()
    val queueNotice by viewModel.queueNotice.collectAsState()
    // DownloadManager 终点事件(下载完成汇总/失败),与上面两路同通道
    val dlEvents by app.downloadManager.events.collectAsState()
    val notice = queueNotice ?: downloadMsg ?: dlEvents
    ThemedNoticeHost(
        message = notice,
        onConsumed = {
            when {
                queueNotice != null -> viewModel.consumeQueueNotice()
                downloadMsg != null -> viewModel.consumeDownloadMessage()
                else -> app.downloadManager.consumeEvent()
            }
        },
    )

    // 全局登录弹窗(扫码/Cookie 方式选择,与「我的」页账户头同款入口;旧整页登录已删)
    if (showLoginDialog) {
        LoginEntryDialog(onDismiss = { showLoginDialog = false })
    }

    if (showFavoritePicker) {
        val bvid = favoriteTargetBvid ?: state.player.currentMediaId ?: ""
        if (bvid.isNotBlank()) {
            // 本地歌单勾选需要歌曲快照:先查播放队列,再查播放历史(播放过必有记录)。
            // 都没有(从未播过、列表直接弹)时构造最小快照 —— 本地歌单只要 bvid 就能播(bili:// 现取)。
            val localTarget = remember(bvid, playHistory) {
                state.player.queue.firstOrNull { it.mediaId == bvid }?.let {
                    dev.avsong.domain.Song(bvid, title = it.title, artist = it.artist, coverUrl = it.artworkUri)
                }
                    ?: playHistory.firstOrNull { it.bvid == bvid }
                    ?: dev.avsong.domain.Song(bvid, title = "", artist = "", coverUrl = null)
            }
            val localPlaylists by viewModel.localPlaylistStore.playlists.collectAsState()
            FavoritePickerDialog(
                favRepo = viewModel.favoriteRepository,
                bvid = bvid,
                // 收藏结果 push 进全局提示条流(与队列插入反馈同一通道)
                onResult = viewModel::pushNotice,
                localPlaylists = localPlaylists,
                onToggleLocal = { pl, checked ->
                    if (checked) viewModel.removeSongFromLocalPlaylist(pl.id, bvid)
                    else viewModel.saveSongToLocalPlaylist(pl.id, localTarget)
                },
                onCreateLocal = { name -> viewModel.createLocalPlaylist(name) },
                onDismiss = {
                    showFavoritePicker = false
                    favoriteTargetBvid = null
                },
            )
        }
    }



    // 迷你条队列抽屉:从底部滑出,内嵌 QueueContent(播放队列唯一入口,歌单 tab 已改为歌单中心);
    // 内容固定 4/5 屏(fillMaxHeight(0.8f),用户要求 2/3 仍不够),tonalElevation=0 贴合扁平风
    if (showQueueSheet) {
        val queueListState = rememberLazyListState()
        // 打开抽屉直接滚到当前播放曲,保证它在视口内(用户要求);队列为空/索引非法则跳过
        LaunchedEffect(queueListState) {
            val idx = state.player.currentIndex
            if (idx in 0 until state.player.queue.size) {
                queueListState.scrollToItem(idx)
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DrawerTab(
                        text = "当前播放",
                        selected = queueTab == 0,
                        badge = state.player.queue.size.takeIf { it > 0 },
                        onClick = { queueTab = 0 },
                    )
                    DrawerTab(
                        text = "历史播放",
                        selected = queueTab == 1,
                        onClick = { queueTab = 1 },
                    )
                }
                when (queueTab) {
                    0 -> {
                        // 多选状态:队列批量下载/收藏(选中集合按队列下标记)
                        var multiSelect by remember { mutableStateOf(false) }
                        var selectedIdx by remember { mutableStateOf(setOf<Int>()) }
                        var showBatchFav by remember { mutableStateOf(false) }
                        val selectedEntries = selectedIdx.mapNotNull { state.player.queue.getOrNull(it) }

                        if (!multiSelect) {
                        // 功能条:单键循环切换播放模式 + 下载/收藏/多选/清空(清空带确认弹窗)
                        val (modeLabel, modeIcon) = when {
                            state.player.shuffleEnabled -> "随机播放" to Icons.Rounded.Shuffle
                            state.player.repeatMode == Player.REPEAT_MODE_ONE ->
                                "单曲循环" to Icons.Rounded.RepeatOne
                            state.player.repeatMode == Player.REPEAT_MODE_ALL ->
                                "列表循环" to Icons.Rounded.Repeat
                            else -> "顺序播放" to Icons.Rounded.MusicNote
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 12.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ModeChip(modeLabel, modeIcon, active = true) {
                                viewModel.cyclePlayMode()
                            }
                            Spacer(Modifier.weight(1f))
                            BarAction(Icons.Rounded.Download, "下载当前歌曲") {
                                viewModel.downloadCurrentSong(appContext)
                            }
                            BarAction(Icons.Rounded.FavoriteBorder, "收藏当前歌曲") {
                                if (state.player.currentMediaId != null) showFavoritePicker = true
                            }
                            BarAction(Icons.Rounded.Checklist, "多选(批量下载/收藏)") {
                                multiSelect = true
                                selectedIdx = emptySet()
                            }
                            var showClearConfirm by remember { mutableStateOf(false) }
                            BarAction(Icons.Rounded.DeleteSweep, "清空播放队列") {
                                showClearConfirm = true
                            }
                            // 清空确认用模态对话框(用户要求,不用气泡菜单)
                            if (showClearConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showClearConfirm = false },
                                    title = { Text("清空播放队列?") },
                                    text = {
                                        Text("将移除队列中的全部 ${state.player.queue.size} 首歌曲,此操作不可撤销。")
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            viewModel.clearQueue()
                                            showClearConfirm = false
                                        }) { Text("清空", color = MaterialTheme.colorScheme.primary) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
                                    },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                            }
                        }
                        } else {
                            // 批量操作条:全选/反选 + 下载 + 收藏 + 完成(勾选 N 首实时回显)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 12.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ModeChip("已选 ${selectedEntries.size} 首", Icons.Rounded.Checklist, active = true) {
                                    // 点已选计数 = 全选/反选切换:全选中时点击清空,否则全选
                                    selectedIdx =
                                        if (selectedIdx.size == state.player.queue.size) emptySet()
                                        else state.player.queue.indices.toSet()
                                }
                                Spacer(Modifier.weight(1f))
                                BarAction(Icons.Rounded.SwapVert, "反选") {
                                    selectedIdx = state.player.queue.indices.toSet() - selectedIdx
                                }
                                BarAction(Icons.Rounded.Download, "批量下载") {
                                    if (selectedEntries.isNotEmpty()) {
                                        viewModel.downloadFromQueue(appContext, selectedEntries)
                                        multiSelect = false
                                        selectedIdx = emptySet()
                                    }
                                }
                                BarAction(Icons.Rounded.FavoriteBorder, "批量收藏") {
                                    if (selectedEntries.isNotEmpty()) showBatchFav = true
                                }
                                BarAction(Icons.Rounded.Close, "退出多选") {
                                    multiSelect = false
                                    selectedIdx = emptySet()
                                }
                            }
                        }
                        QueueContent(
                            queue = state.player.queue,
                            currentIndex = state.player.currentIndex,
                            onPlayAt = { index ->
                                // 点其他歌:直接切过去播;点当前曲:什么都不做。
                                // 抽屉保持打开,由用户手动下滑关闭(用户要求)
                                if (index != state.player.currentIndex) viewModel.playAt(index)
                            },
                            onRemoveAt = viewModel::removeFromQueue,
                            onMove = viewModel::moveInQueue,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            isPlaying = state.player.isPlaying,
                            showReorder = false,
                            listState = queueListState,
                            selectionMode = multiSelect,
                            selected = selectedIdx,
                            onToggleSelect = { idx ->
                                selectedIdx = if (idx in selectedIdx) selectedIdx - idx else selectedIdx + idx
                            },
                        )
                        // 批量收藏夹选择:确定后逐首 modify(VM 串行+间隔防风控),结果走全局提示条
                        if (showBatchFav) {
                            BatchFavoriteDialog(
                                favRepo = viewModel.favoriteRepository,
                                songCount = selectedEntries.size,
                                onConfirm = { folderIds ->
                                    showBatchFav = false
                                    viewModel.favoriteFromQueue(selectedEntries, folderIds)
                                    multiSelect = false
                                    selectedIdx = emptySet()
                                },
                                onDismiss = { showBatchFav = false },
                            )
                        }
                    }
                    1 -> if (playHistory.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            StateHint(
                                Icons.Rounded.History,
                                "还没有播放记录",
                                "播放过的歌会出现在这里",
                            )
                        }
                    } else {
                        // 打开抽屉时定格历史列表:点行起播会把该曲「去重置顶」写回 store,
                        // 实时列表会让被点的歌瞬间跳到顶部(用户报的 bug)。
                        // 定格后本次抽屉内顺序不变(高亮/音波条仍实时),下次打开才见新序。
                        val historySnapshot = remember { playHistory }
                        SongList(
                            songs = historySnapshot,
                            onPlay = { songs, index -> viewModel.playFromHistory(songs[index]) },
                            showPlayButton = false,
                            currentMediaId = state.player.currentMediaId,
                            isPlaying = state.player.isPlaying,
                        )
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // 顶部 insets 由各页面自行处理(都有 statusBarsPadding);Scaffold 若再算一次
        // 会与页面层叠加——真机挖孔屏状态栏 40dp 双倍=顶部空一大截(模拟器 insets≈0 遂无症状)
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // 迷你条与 tab 栏熔接为一个圆角容器:迷你条出上圆角、tab 出下圆角,
            // 中缝零间距贴合;二者单独出现时各自保持全圆角。播放页两者都隐藏。
            val showMini = state.player.currentMediaId != null
            val showTabs = currentTab != null
            Column {
                if (showMini) {
                    MiniPlayerBar(
                        state = state.player,
                        onPlayPause = viewModel::playPause,
                        onQueue = { showQueueSheet = true },
                        onClick = { showPlayerSheet = true },
                        shape = if (showTabs) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        else RoundedCornerShape(50),
                        outerPadding = if (showTabs) PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp)
                        else PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        // 播放键环形进度:定时向控制器同步读位置
                        positionProvider = viewModel::currentPosition,
                    )
                }
                if (showTabs) {
                    AppBottomBar(
                        current = currentTab,
                        onSelect = { tab ->
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        shape = if (showMini) RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        else RoundedCornerShape(26.dp),
                        outerPadding = if (showMini) PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        else PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                // 页面切换过渡:200ms 淡入 + 12dp 上移,替代硬切(用户 2026-10-14 采纳优化清单)
                enterTransition = {
                    fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 12 }
                },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 12 } },
                popExitTransition = { fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 12 } },
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        state = state,
                        onPlay = { songs, index -> viewModel.playQueue(songs, index) },
                        onPlayPause = viewModel::playPause,
                        onRefresh = { viewModel.loadHome(force = true) },
                        onLoadMore = viewModel::loadMoreHome,
                        onLogin = { showLoginDialog = true },
                        onPlayNext = viewModel::playNext,
                        onAddToQueue = viewModel::addToQueue,
                        onFavorite = { song ->
                            favoriteTargetBvid = song.bvid
                            showFavoritePicker = true
                        },
                        onDownload = { song -> viewModel.downloadSong(appContext, song) },
                        // 定位当前播放:整榜下标 - 3(列表是整榜 drop(3)),第 4 首落到列表第 0 行
                        onLocate = { viewModel.locateInHome()?.let { idx -> (idx - 3).coerceAtLeast(0) } },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        state = state,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = viewModel::search,
                        // 搜索结果点歌:插到当前曲后立即播,不替换队列(用户指定;长按菜单另有无损插入)
                        onPlay = { songs, index -> viewModel.playNow(songs[index]) },
                        onLoadMore = viewModel::loadMoreSearch,
                        suggests = viewModel.suggests.collectAsState().value,
                        onSuggestClick = { term ->
                            viewModel.onQueryChange(term)
                            viewModel.search()
                        },
                        onHistoryClick = viewModel::searchFromHistory,
                        onHistoryRemove = viewModel::removeHistory,
                        onHistoryClear = viewModel::clearHistory,
                        onPlayNext = viewModel::playNext,
                        onAddToQueue = viewModel::addToQueue,
                        onFavorite = { song ->
                            favoriteTargetBvid = song.bvid
                            showFavoritePicker = true
                        },
                        onDownload = { song -> viewModel.downloadSong(appContext, song) },
                    )
                }
                composable(Routes.PLAYLIST) {
                    // 本地自建歌单数据经 StateFlow 直读(建夹/删夹/重命名/存曲都即时反映)
                    val localPlaylists by viewModel.localPlaylistStore.playlists.collectAsState()
                    PlaylistScreen(
                        onOpenFolder = { folder ->
                            navController.navigate(Routes.folder(folder.id, folder.title))
                        },
                        onOpenCollection = { collection ->
                            navController.navigate(
                                Routes.collection(collection.mid, collection.seasonId, collection.title),
                            )
                        },
                        onPlayFolder = { folder -> viewModel.playFolderPlaylist(folder.id) },
                        onPlayCollection = { collection ->
                            viewModel.playCollectionPlaylist(collection.mid, collection.seasonId)
                        },
                        onPlayPause = viewModel::playPause,
                        player = state.player,
                        playingId = viewModel.playingPlaylistId(state.player),
                        onLogin = { showLoginDialog = true },
                        localPlaylists = localPlaylists,
                        onCreateLocalPlaylist = { name ->
                            viewModel.createLocalPlaylist(name)
                        },
                        onDeleteLocalPlaylist = { pl -> viewModel.deleteLocalPlaylist(pl.id) },
                        onRenameLocalPlaylist = { pl, name -> viewModel.renameLocalPlaylist(pl.id, name) },
                        onOpenLocalPlaylist = { pl ->
                            navController.navigate(Routes.localPlaylist(pl.id, pl.name))
                        },
                        onPlayLocalPlaylist = { pl -> viewModel.playLocalPlaylist(pl.id) },
                    )
                }
                composable(Routes.MINE) {
                    LibraryScreen(
                        onOpenDownloads = { navController.navigate(Routes.DOWNLOADED) },
                        onOpenPlayHistory = { navController.navigate(Routes.PLAY_HISTORY) },
                        onOpenPlaybackSettings = { navController.navigate(Routes.PLAYBACK_SETTINGS) },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                        sleepMinutesLeft = sleepMinutesLeft,
                    )
                }
                composable(Routes.PLAYBACK_SETTINGS) {
                    // 播放设置子页:五项播放偏好收纳处(「我的」页只留入口行,2026-10-15)
                    dev.avsong.ui.settings.PlaybackSettingsScreen(
                        quality = speedSettings.let { app.settingsStore.state.value.quality },
                        onQualityChange = { app.settingsStore.setQuality(it) },
                        historyLimit = app.settingsStore.state.value.historyLimit,
                        onHistoryChange = { app.settingsStore.setHistoryLimit(it) },
                        playbackSpeed = speedSettings.playbackSpeed,
                        onSpeedChange = { speed ->
                            app.settingsStore.setPlaybackSpeed(speed) { viewModel.setPlaybackSpeed(it) }
                        },
                        sleepMinutesLeft = sleepMinutesLeft,
                        onSetSleepTimer = viewModel::setSleepTimer,
                        hotReplyMode = if (danmakuSettings.danmakuEnabled) danmakuSettings.danmakuMode else -1,
                        onHotReplyModeChange = { mode ->
                            if (mode == -1) app.settingsStore.setDanmakuEnabled(false)
                            else {
                                app.settingsStore.setDanmakuEnabled(true)
                                app.settingsStore.setDanmakuMode(mode)
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.DOWNLOADED) {
                    DownloadedScreen(
                        onBack = { navController.popBackStack() },
                        onPlay = { songs, index -> viewModel.playQueue(songs, index) },
                        onPlayPause = viewModel::playPause,
                        onPlayNext = viewModel::playNext,
                        onAddToQueue = viewModel::addToQueue,
                        currentMediaId = state.player.currentMediaId,
                        isPlaying = state.player.isPlaying,
                        // 多选批量(2026-10-15):下一首/入队/收藏直连 VM 批量方法
                        onPlayNextBatch = viewModel::playNextBatch,
                        onAddToQueueBatch = { songs -> songs.forEach(viewModel::addToQueue) },
                        onFavoriteBatch = viewModel::favoriteSongs,
                    )
                }
                composable(Routes.PLAY_HISTORY) {
                    dev.avsong.ui.history.PlayHistoryScreen(
                        history = playHistory,
                        currentMediaId = state.player.currentMediaId,
                        isPlaying = state.player.isPlaying,
                        onBack = { navController.popBackStack() },
                        onPlayFromHistory = viewModel::playFromHistory,
                    )
                }
                composable(Routes.ABOUT) {
                    dev.avsong.ui.settings.AboutScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.FOLDER) { entry ->
                    val folderId = entry.arguments?.getString("folderId")?.toLongOrNull() ?: 0L
                    val title = entry.arguments?.getString("title") ?: "收藏夹"
                    FolderContentScreen(
                        folderId = folderId,
                        folderTitle = title,
                        onBack = { navController.popBackStack() },
                        // 点歌=插到当前曲后立即播,不替换队列(与搜索页一致);播放全部/随机播放仍整队
                        onPlay = { songs, index -> viewModel.playNow(songs[index]) },
                        onPlayQueue = { songs -> viewModel.playQueue(songs, 0) },
                        onShuffle = { songs -> viewModel.playQueueShuffled(songs) },
                        onPlayNext = viewModel::playNext,
                        onAddToQueue = viewModel::addToQueue,
                        onFavorite = { song ->
                            favoriteTargetBvid = song.bvid
                            showFavoritePicker = true
                        },
                        onDownload = { song -> viewModel.downloadSong(appContext, song) },
                        onPlayNextBatch = { songs -> viewModel.playNextBatch(songs) },
                        onAddToQueueBatch = { songs -> songs.forEach { viewModel.addToQueue(it) } },
                        onFavoriteBatch = { songs, folderIds -> viewModel.favoriteSongs(songs, folderIds) },
                        onDownloadAll = { songs -> viewModel.downloadAll(appContext, songs) },
                        currentMediaId = state.player.currentMediaId,
                        isPlaying = state.player.isPlaying,
                    )
                }
                composable(Routes.COLLECTION) { entry ->
                    val mid = entry.arguments?.getString("mid")?.toLongOrNull() ?: 0L
                    val seasonId = entry.arguments?.getString("seasonId")?.toLongOrNull() ?: 0L
                    val title = entry.arguments?.getString("title") ?: "合集"
                    CollectionContentScreen(
                        mid = mid,
                        seasonId = seasonId,
                        fallbackTitle = title,
                        onBack = { navController.popBackStack() },
                        // 点歌=插到当前曲后立即播,不替换队列(与搜索页一致);播放全部/随机播放仍整队
                        onPlay = { songs, index -> viewModel.playNow(songs[index]) },
                        onPlayQueue = { songs -> viewModel.playQueue(songs, 0) },
                        onShuffle = { songs -> viewModel.playQueueShuffled(songs) },
                        onPlayNext = viewModel::playNext,
                        onAddToQueue = viewModel::addToQueue,
                        onFavorite = { song ->
                            favoriteTargetBvid = song.bvid
                            showFavoritePicker = true
                        },
                        onDownload = { song -> viewModel.downloadSong(appContext, song) },
                        onPlayNextBatch = { songs -> viewModel.playNextBatch(songs) },
                        onAddToQueueBatch = { songs -> songs.forEach { viewModel.addToQueue(it) } },
                        onFavoriteBatch = { songs, folderIds -> viewModel.favoriteSongs(songs, folderIds) },
                        onDownloadAll = { songs -> viewModel.downloadAll(appContext, songs) },
                        currentMediaId = state.player.currentMediaId,
                        isPlaying = state.player.isPlaying,
                    )
                }
                composable(Routes.LOCAL_PLAYLIST) { entry ->
                    val playlistId = entry.arguments?.getString("playlistId")?.toLongOrNull() ?: 0L
                    LocalPlaylistContentScreen(
                        playlistId = playlistId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        // 点歌=插到当前曲后立即播,不替换队列(与收藏夹/合集详情一致);播放全部/随机播放仍整队
                        onPlay = { songs, index -> viewModel.playNow(songs[index]) },
                        onShuffle = { songs -> viewModel.playQueueShuffled(songs) },
                        onPlayNext = viewModel::playNext,
                        onAddToQueue = viewModel::addToQueue,
                        onFavorite = { song ->
                            favoriteTargetBvid = song.bvid
                            showFavoritePicker = true
                        },
                        onDownload = { song -> viewModel.downloadSong(appContext, song) },
                        onPlayQueue = { songs -> viewModel.playQueue(songs, 0) },
                        onDownloadAll = { songs -> viewModel.downloadAll(appContext, songs) },
                        onPlayNextBatch = { songs -> viewModel.playNextBatch(songs) },
                        onAddToQueueBatch = { songs -> songs.forEach { viewModel.addToQueue(it) } },
                        onFavoriteBatch = { songs, folderIds -> viewModel.favoriteSongs(songs, folderIds) },
                        currentMediaId = state.player.currentMediaId,
                        isPlaying = state.player.isPlaying,
                    )
                }
            }
        }
    }

        // 播放面板:从底部上滑覆盖(组合内 overlay,输入必然可达;非弹窗窗口)
        val localPlaylists2 = viewModel.localPlaylistStore.playlists.value
        AnimatedVisibility(
            visible = showPlayerSheet,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            PlayerScreen(
                state = state.player,
                lyric = state.lyric,
                favoriteRepository = viewModel.favoriteRepository,
                onCollapse = { showPlayerSheet = false },
                onQueue = { showQueueSheet = true },
                onPlayPause = viewModel::playPause,
                onCycleMode = viewModel::cyclePlayMode,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onSeek = viewModel::seekTo,
                danmakuEnabled = app.settingsStore.state.value.danmakuEnabled,
                danmakuMode = danmakuSettings.danmakuMode,
                positionProvider = viewModel::currentPosition,
                onLyricReplaced = { lines -> viewModel.replaceLyric(state.player.currentMediaId ?: "", lines) },
                lyricRepository = viewModel.lyricRepository,
                getCsrf = { app.authStore.state.value.biliJct },
                // 播放页爱心的本地歌单区:快照直接用当前播放曲的 PlayerState 字段拼
                localPlaylists = localPlaylists2,
                localPlaylistsState = viewModel.localPlaylistStore.playlists,
                onUnfavoriteAll = {
                    state.player.currentMediaId?.let { viewModel.unfavoriteEverywhere(it) }
                },
                unfavResult = viewModel.unfavResult.collectAsState().value,
                onConsumeUnfavResult = viewModel::consumeUnfavResult,
                onToggleLocal = { pl, checked ->
                    val ps = state.player
                    val bvid = ps.currentMediaId
                    if (bvid != null) {
                        val song = dev.avsong.domain.Song(
                            bvid = bvid,
                            title = ps.title.orEmpty(),
                            artist = ps.artist.orEmpty(),
                            coverUrl = ps.artworkUri,
                            durationSec = if (ps.durationMs > 0) ps.durationMs / 1000 else 0,
                        )
                        if (checked) viewModel.removeSongFromLocalPlaylist(pl.id, bvid)
                        else viewModel.saveSongToLocalPlaylist(pl.id, song)
                    }
                },
                onCreateLocal = { name -> viewModel.createLocalPlaylist(name) },
            )
        }
    }
}

/** 抽屉头部小 tab:选中变黄加粗;badge 数字贴在文字右上角(徽标样式,用户要求)。 */
@Composable
private fun DrawerTab(
    text: String,
    selected: Boolean,
    badge: Int? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            // 不 clip:徽标要向上溢出文字右上角,裁剪会切掉它的一半
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (badge == null) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // 徽标钉在文字右上角:Box 只包文字本身,与相邻 tab 的文字天然同轴
            Box {
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-5).dp)
                        .widthIn(min = 16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$badge",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** 抽屉功能条的模式胶囊:选中黄底黑字,未选透明灰字(纯色扁平)。 */
@Composable
private fun ModeChip(
    text: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = text,
            modifier = Modifier.size(14.dp),
            tint = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 抽屉功能条的圆形动作钮(下载/收藏/清空)。 */
@Composable
private fun BarAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
