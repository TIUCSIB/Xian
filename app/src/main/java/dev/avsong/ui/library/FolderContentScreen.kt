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
    onShuffle: (List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    /** ⋮ 菜单「下载全部」:把已加载的歌曲整批入队(队列自动去重已下载/已在队的)。 */
    onDownloadAll: (List<Song>) -> Unit,
    currentMediaId: String?,
    isPlaying: Boolean,
) {
    val vm = rememberFolderContentViewModel(folderId)
    val state by vm.state.collectAsState()
    val loadingAll by vm.loadingAll.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    PlaylistDetailContent(
        title = folderTitle,
        metaLine = "收藏夹 · ${state.songs.size} 首",
        subtitle = "",
        cover = state.songs.firstOrNull()?.coverUrl,
        songs = state.songs,
        loading = state.loading,
        error = state.error,
        emptyText = "收藏夹是空的",
        loadingMore = state.loadingMore,
        hasMore = state.hasMore,
        onLoadMore = vm::loadMore,
        onBack = onBack,
        // 播放前先拉全量:否则大收藏夹点播放只入列已加载的第一页(20 首);与合集详情同款
        onPlay = { _, index -> vm.playWithFill(index, onPlay) },
        onShuffle = onShuffle,
        currentMediaId = currentMediaId,
        isPlaying = isPlaying,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onDownload = onDownload,
        onRemoveSong = { vm.removeFromFolder(it) },
        loadingAll = loadingAll,
        onLoadAll = vm::loadAll,
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
}
