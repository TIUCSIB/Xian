package dev.avsong.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.avsong.domain.Song
import dev.avsong.ui.rememberCollectionContentViewModel

/**
 * UP 主合集内容页:布局走共享的 [PlaylistDetailContent](网易云式详情稿,2026-10-11 改版)。
 * 只读(曲目是别人的,无移除);头部数据用合集 meta 的真实封面/标题/UP/总数,
 * UP 名可能拿不到(新版大 mid 的 card 接口不返回),空串时不渲染该行。
 */
@Composable
fun CollectionContentScreen(
    mid: Long,
    seasonId: Long,
    fallbackTitle: String,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    currentMediaId: String?,
    isPlaying: Boolean,
) {
    val vm = rememberCollectionContentViewModel(mid, seasonId)
    val state by vm.state.collectAsState()

    PlaylistDetailContent(
        title = state.title.ifBlank { fallbackTitle },
        metaLine = if (state.total > 0) "合集 · 共 ${state.total} 个视频" else "合集",
        subtitle = state.upperName,
        cover = state.cover ?: state.songs.firstOrNull()?.coverUrl,
        songs = state.songs,
        loading = state.loading,
        error = state.error,
        emptyText = "合集是空的",
        loadingMore = state.loadingMore,
        hasMore = state.hasMore,
        onLoadMore = vm::loadMore,
        onBack = onBack,
        // 播放前先拉全量:否则大合集点播放只入列已加载的第一页(20 首)
        onPlay = { _, index -> vm.playAll(index, onPlay) },
        onShuffle = onShuffle,
        currentMediaId = currentMediaId,
        isPlaying = isPlaying,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onDownload = onDownload,
        onRemoveSong = null,
    )
}
