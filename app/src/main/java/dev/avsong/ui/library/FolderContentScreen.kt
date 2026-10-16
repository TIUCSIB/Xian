package dev.avsong.ui.library

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.avsong.domain.Song
import dev.avsong.ui.rememberFolderContentViewModel

/**
 * 收藏夹内容页:布局走共享的 [PlaylistDetailContent](网易云式详情稿,2026-10-11 改版)。
 * 收藏夹专属:行菜单带「从收藏夹移除」;顶栏「⋮」= 删除收藏夹(确认弹窗,远端删除不可撤销)。
 * 头部封面没有现成字段(B 站接口不回),用第一首的封面兜底 ——
 * B 站网页上收藏夹缩略图本来就是首视频封面,视觉一致。
 */
@Composable
fun FolderContentScreen(
    folderId: Long,
    folderTitle: String,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    /** 头部「播放」= 整队替换:把已加载部分整队入列并起播(不走 playNow 插播)。 */
    onPlayQueue: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onFavorite: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    /** ⋮ 菜单「下载全部」：把已加载的歌曲整批入队(队列自动去重已下载/已在队的)。 */
    onDownloadAll: (List<Song>) -> Unit,
    /** 多选批量「下一首播放」/「加入队列」(保持选中顺序)。 */
    onPlayNextBatch: (List<Song>) -> Unit,
    onAddToQueueBatch: (List<Song>) -> Unit,
    /** 多选批量收藏:弹窗选定夹组合后执行(经 MainViewModel.favoriteSongs)。 */
    onFavoriteBatch: (List<Song>, List<Long>) -> Unit,
    currentMediaId: String?,
    isPlaying: Boolean,
) {
    val vm = rememberFolderContentViewModel(folderId)
    val state by vm.state.collectAsState()
    val loadingAll by vm.loadingAll.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchFav by remember { mutableStateOf<List<Song>?>(null) }

    PlaylistDetailContent(
        title = folderTitle,
        metaLine = "收藏夹 · ${state.songs.size} 首",
        subtitle = state.intro,
        cover = state.songs.firstOrNull()?.coverUrl,
        songs = state.songs,
        loading = state.loading,
        error = state.error,
        emptyText = "收藏夹是空的",
        loadingMore = state.loadingMore,
        hasMore = state.hasMore,
        onLoadMore = vm::loadMore,
        onBack = onBack,
        // 头部「播放」= 整队替换(与合集详情同款,2026-10-15 用户反馈);点行仍=插播不换队
        onPlayAll = { _ -> vm.playWithFill(0, { songs, _ -> onPlayQueue(songs) }, replace = true) },
        onDownloadAllParam = onDownloadAll,
        // 播放前先拉全量:否则大收藏夹点播放只入列已加载的第一页(20 首);与合集详情同款
        onPlay = { _, index ->
            // 补全进行中的点击不被护栏吞掉(与合集详情同坑):直播所点那一首
            if (loadingAll) onPlay(listOf(state.songs[index]), 0)
            else vm.playWithFill(index, onPlay)
        },
        // 随机播放:补全/加载全量进行中不能被 loadAll 护栏吞掉(与播放三分流同坑)——
        // 此时把已加载部分就地洗牌整队替换(用户点的是「随机」不是「等全量」);
        // 空闲时维持原策略:拉全量后洗(大合集随机要摇到全量)
        onShuffle = { _ ->
            if (loadingAll) onShuffle(state.songs.shuffled())
            else vm.loadAll { all -> onShuffle(all) }
        },
        currentMediaId = currentMediaId,
        isPlaying = isPlaying,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onFavorite = onFavorite,
        onDownload = onDownload,
        onRemoveSong = { vm.removeFromFolder(it) },
        loadingAll = loadingAll,
        loadingHint = if (loadingAll) "已加载 ${state.songs.size} 首,补全中…" else null,
        onLoadAll = vm::loadAll,
        batchActions = dev.avsong.ui.library.BatchActions(
            onDownload = onDownloadAll,
            onFavorite = { songs -> showBatchFav = songs },
            onPlayNext = onPlayNextBatch,
            onAddToQueue = onAddToQueueBatch,
            onRemove = { songs -> songs.forEach { vm.removeFromFolder(it) } },
            removeLabel = "从收藏夹移除",
        ),
        menuActions = listOf(
            "下载全部(已加载 " + state.songs.size.toString() + " 首)" to { onDownloadAll(state.songs) },
            "删除收藏夹" to { showDeleteConfirm = true },
        ),
    )

    // 删除确认:B 站远端删除不可撤销,必须模态确认
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除收藏夹?") },
            text = { Text("「$folderTitle」及其中的全部收藏会被删除,视频本身不受影响,操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deleteFolder(onDeleted = onBack)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    // 删除失败(网络/接口拒绝):弹窗提示,不让操作静默失败
    state.actionError?.let { err ->
        AlertDialog(
            onDismissRequest = vm::clearActionError,
            title = { Text("操作失败") },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = vm::clearActionError) { Text("知道了") } },
        )
    }

    // 多选批量收藏:复用队列多选同款夹选择弹窗(勾夹 → 逐首 modify)
    showBatchFav?.let { songs ->
        BatchFavoriteDialog(
            favRepo = vm.favRepo,
            songCount = songs.size,
            onConfirm = { folderIds -> onFavoriteBatch(songs, folderIds) },
            onDismiss = { showBatchFav = null },
        )
    }
}
