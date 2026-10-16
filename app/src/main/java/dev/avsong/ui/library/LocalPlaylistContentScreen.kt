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
import dev.avsong.data.toSong
import dev.avsong.ui.MainViewModel

/**
 * 本地自建歌单详情页:布局走共享的 [PlaylistDetailContent](与收藏夹/合集详情同款,
 * 2026-10-15 加)。全量曲目在 DataStore,无分页 —— loading/hasMore 恒 false,
 * onLoadMore 空实现。专属点:行菜单「从歌单移除」、顶栏「⋮」= 重命名/删除歌单
 * (纯本地操作无网络失败面,删错只能重建,所以删除仍弹模态确认)。
 */
@Composable
fun LocalPlaylistContentScreen(
    playlistId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    /** 头部「播放」= 整队替换。 */
    onPlayQueue: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onFavorite: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    /** 多选批量「下一首播放」/「加入队列」(保持选中顺序)。 */
    onPlayNextBatch: (List<Song>) -> Unit,
    onAddToQueueBatch: (List<Song>) -> Unit,
    /** 多选批量收藏:弹窗选定夹组合后执行(经 MainViewModel.favoriteSongs)。 */
    onFavoriteBatch: (List<Song>, List<Long>) -> Unit,
    /** 批量下载(多选与将来可能的「下载全部」共用;经 MainViewModel.downloadAll)。 */
    onDownloadAll: (List<Song>) -> Unit,
    currentMediaId: String?,
    isPlaying: Boolean,
) {
    val playlists by viewModel.localPlaylistStore.playlists.collectAsState()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showBatchFav by remember { mutableStateOf<List<Song>?>(null) }

    // 歌单刚被删除时 playlist 为 null:直接退回歌单页(列表刷新已由 StateFlow 驱动)
    if (playlist == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }

    val songs = remember(playlist.tracks) { playlist.tracks.map { it.toSong() } }

    PlaylistDetailContent(
        title = playlist.name,
        metaLine = "本地歌单 · ${songs.size} 首",
        subtitle = "",
        // 封面用首曲 coverUrl 兜底(与收藏夹详情同款策略;无封面显示占位音符)
        cover = songs.firstOrNull()?.coverUrl,
        songs = songs,
        loading = false,
        error = null,
        emptyText = "歌单还是空的,去搜索页长按歌曲存进来",
        loadingMore = false,
        hasMore = false,
        onLoadMore = {},
        onBack = onBack,
        onPlayAll = { songs -> onPlayQueue(songs) },
        onDownloadAllParam = onDownloadAll,
        onPlay = onPlay,
        onShuffle = onShuffle,
        currentMediaId = currentMediaId,
        isPlaying = isPlaying,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onFavorite = onFavorite,
        onDownload = onDownload,
        onRemoveSong = { viewModel.removeSongFromLocalPlaylist(playlistId, it.bvid) },
        removeSongLabel = "从歌单移除",
        batchActions = dev.avsong.ui.library.BatchActions(
            onDownload = onDownloadAll,
            onFavorite = { songs -> showBatchFav = songs },
            onPlayNext = onPlayNextBatch,
            onAddToQueue = onAddToQueueBatch,
            onRemove = { songs -> songs.forEach { viewModel.removeSongFromLocalPlaylist(playlistId, it.bvid) } },
            removeLabel = "从歌单移除",
        ),
        menuActions = listOf(
            "重命名歌单" to { showRename = true },
            "删除歌单" to { showDeleteConfirm = true },
        ),
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除歌单?") },
            text = { Text("「${playlist.name}」及其中的 ${songs.size} 首歌会被移除,只删本地歌单,不影响 B 站收藏,操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteLocalPlaylist(playlistId)
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showRename) {
        RenamePlaylistDialog(
            initial = playlist.name,
            onDismiss = { showRename = false },
            onConfirm = { name ->
                viewModel.renameLocalPlaylist(playlistId, name)
                showRename = false
            },
        )
    }

    showBatchFav?.let { songs ->
        BatchFavoriteDialog(
            favRepo = viewModel.favoriteRepository,
            songCount = songs.size,
            onConfirm = { folderIds -> onFavoriteBatch(songs, folderIds) },
            onDismiss = { showBatchFav = null },
        )
    }
}
