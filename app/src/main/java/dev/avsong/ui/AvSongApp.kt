package dev.avsong.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.avsong.ui.home.HomeScreen
import dev.avsong.ui.library.CollectionContentScreen
import dev.avsong.ui.library.FavoritePickerDialog
import dev.avsong.ui.library.FolderContentScreen
import dev.avsong.ui.library.LibraryScreen
import dev.avsong.ui.login.LoginScreen
import dev.avsong.ui.player.PlayerScreen
import dev.avsong.ui.queue.PlaylistScreen
import dev.avsong.ui.queue.QueueContent
import dev.avsong.ui.queue.QueueScreen
import dev.avsong.ui.search.SearchScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val PLAYLIST = "playlist"
    const val MINE = "mine"
    const val PLAYER = "player"
    const val LOGIN = "login"
    const val QUEUE = "queue"
    const val FOLDER = "library/folder/{folderId}?title={title}"
    const val COLLECTION = "library/collection/{mid}/{seasonId}?title={title}"

    fun folder(id: Long, title: String): String =
        "library/folder/$id?title=${android.net.Uri.encode(title)}"

    fun collection(mid: Long, seasonId: Long, title: String): String =
        "library/collection/$mid/$seasonId?title=${android.net.Uri.encode(title)}"
}

/** 顶级 tab 路由 → 对应 tab;非 tab 页(播放/队列/登录/收藏夹内容)返回 null。 */
private fun routeToTab(route: String?): TopTab? = TopTab.entries.firstOrNull { it.route == route }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvSongApp(navController: NavHostController, viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onPlayerScreen = currentRoute == Routes.PLAYER
    val currentTab = routeToTab(currentRoute)

    val snackbarHostState = remember { SnackbarHostState() }
    var showFavoritePicker by remember { mutableStateOf(false) }
    // 迷你条的队列入口:底部抽屉(不再跳队列页;Routes.QUEUE 仅播放页右上入口在用)
    var showQueueSheet by remember { mutableStateOf(false) }

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

    if (showFavoritePicker) {
        val bvid = state.player.currentMediaId ?: ""
        if (bvid.isNotBlank()) {
            FavoritePickerDialog(
                favRepo = viewModel.favoriteRepository,
                bvid = bvid,
                onDismiss = { showFavoritePicker = false },
            )
        }
    }

    // 迷你条队列抽屉:从底部滑出,内嵌与队列页/歌单 tab 同一份 QueueContent;
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
                Text(
                    "播放队列 · ${state.player.queue.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
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
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // 迷你条与 tab 栏熔接为一个圆角容器:迷你条出上圆角、tab 出下圆角,
            // 中缝零间距贴合;二者单独出现时各自保持全圆角。播放页两者都隐藏。
            val showMini = state.player.currentMediaId != null && !onPlayerScreen
            val showTabs = currentTab != null
            Column {
                if (showMini) {
                    MiniPlayerBar(
                        state = state.player,
                        onPlayPause = viewModel::playPause,
                        onQueue = { showQueueSheet = true },
                        onClick = { navController.navigate(Routes.PLAYER) },
                        shape = if (showTabs) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        else RoundedCornerShape(50),
                        outerPadding = if (showTabs) PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp)
                        else PaddingValues(horizontal = 16.dp, vertical = 10.dp),
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
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        state = state,
                        onPlay = { songs, index -> viewModel.playQueue(songs, index) },
                        onPlayPause = viewModel::playPause,
                        onRefresh = { viewModel.loadHome(force = true) },
                        onLoadMore = viewModel::loadMoreHome,
                        onLogin = { navController.navigate(Routes.LOGIN) },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        state = state,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = viewModel::search,
                        onPlay = { songs, index -> viewModel.playQueue(songs, index) },
                        onLoadMore = viewModel::loadMoreSearch,
                        onHistoryClick = viewModel::searchFromHistory,
                        onHistoryRemove = viewModel::removeHistory,
                    )
                }
                composable(Routes.PLAYLIST) {
                    PlaylistScreen(
                        queue = state.player.queue,
                        currentIndex = state.player.currentIndex,
                        onPlayAt = viewModel::playAt,
                        onRemoveAt = viewModel::removeFromQueue,
                        onMove = viewModel::moveInQueue,
                    )
                }
                composable(Routes.MINE) {
                    LibraryScreen(
                        onOpenFolder = { folder ->
                            navController.navigate(Routes.folder(folder.id, folder.title))
                        },
                        onOpenCollection = { collection ->
                            navController.navigate(
                                Routes.collection(collection.mid, collection.seasonId, collection.title),
                            )
                        },
                        onLogin = { navController.navigate(Routes.LOGIN) },
                    )
                }
                composable(Routes.LOGIN) {
                    LoginScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.FOLDER) { entry ->
                    val folderId = entry.arguments?.getString("folderId")?.toLongOrNull() ?: 0L
                    val title = entry.arguments?.getString("title") ?: "收藏夹"
                    FolderContentScreen(
                        folderId = folderId,
                        folderTitle = title,
                        onBack = { navController.popBackStack() },
                        onPlay = { songs, index -> viewModel.playQueue(songs, index) },
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
                        onPlay = { songs, index -> viewModel.playQueue(songs, index) },
                    )
                }
                composable(Routes.QUEUE) {
                    QueueScreen(
                        queue = state.player.queue,
                        currentIndex = state.player.currentIndex,
                        onBack = { navController.popBackStack() },
                        onPlayAt = viewModel::playAt,
                        onRemoveAt = viewModel::removeFromQueue,
                        onMove = viewModel::moveInQueue,
                    )
                }
                composable(Routes.PLAYER) {
                    PlayerScreen(
                        state = state.player,
                        lyric = state.lyric,
                        onBack = { navController.popBackStack() },
                        onQueue = { navController.navigate(Routes.QUEUE) },
                        onFavorite = { showFavoritePicker = true },
                        onAddToQueue = viewModel::addCurrentToQueue,
                        onPlayPause = viewModel::playPause,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        onSeek = viewModel::seekTo,
                        onShuffle = viewModel::toggleShuffle,
                        onRepeat = viewModel::cycleRepeat,
                        positionProvider = viewModel::currentPosition,
                    )
                }
            }
        }
    }
}
