package dev.avsong.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.avsong.domain.Song
import dev.avsong.ui.rememberFolderContentViewModel

/**
 * 收藏夹内容页:布局走共享的 [PlaylistDetailContent](网易云式详情稿,2026-10-11 改版)。
 * 收藏夹专属:行菜单带「从收藏夹移除」;头部封面没有现成字段(B 站接口不回),
 * 用第一首的封面兜底 —— B 站网页上收藏夹缩略图本来就是首视频封面,视觉一致。
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
    currentMediaId: String?,
    isPlaying: Boolean,
) {
    val vm = rememberFolderContentViewModel(folderId)
    val state by vm.state.collectAsState()

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
        onPlay = onPlay,
        onShuffle = onShuffle,
        currentMediaId = currentMediaId,
        isPlaying = isPlaying,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onDownload = onDownload,
        onRemoveSong = { vm.removeFromFolder(it) },
    )
}
