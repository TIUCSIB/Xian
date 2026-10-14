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
import dev.avsong.ui.rememberCollectionContentViewModel

/**
 * UP 主合集内容页:布局走共享的 [PlaylistDetailContent](网易云式详情稿,2026-10-11 改版)。
 * 只读(曲目是别人的,无移除);顶栏「⋮」= 取消订阅(确认弹窗:
 * 真取消 B 站「追合集」+ 移除本地订阅,然后返回歌单页)。
 * 头部数据用合集 meta 的真实封面/标题/UP/总数,UP 名可能拿不到(新版大 mid 的 card 接口不返回),
 * 空串时不渲染该行。
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
    /** ⋮ 菜单「下载全部」:把已加载的歌曲整批入队(队列自动去重已下载/已在队的)。 */
    onDownloadAll: (List<Song>) -> Unit,
    currentMediaId: String?,
    isPlaying: Boolean,
) {
    val vm = rememberCollectionContentViewModel(mid, seasonId)
    val state by vm.state.collectAsState()
    // 与播放补全(playWithFill)共用同一个 loadingAll 标记,互相防竞态
    val loadingAll by vm.loadingAll.collectAsState()
    var showUnsubConfirm by remember { mutableStateOf(false) }
    val displayTitle = state.title.ifBlank { fallbackTitle }

    PlaylistDetailContent(
        title = displayTitle,
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
        onPlay = { _, index -> vm.playWithFill(index, onPlay) },
        onShuffle = onShuffle,
        currentMediaId = currentMediaId,
        isPlaying = isPlaying,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onDownload = onDownload,
        onRemoveSong = null,
        loadingAll = loadingAll,
        onLoadAll = vm::loadAll,
        menuActions = listOf(
            "下载全部(已加载 " + state.songs.size.toString() + " 首)" to { onDownloadAll(state.songs) },
            "取消订阅" to { showUnsubConfirm = true },
        ),
    )

    if (showUnsubConfirm) {
        AlertDialog(
            onDismissRequest = { showUnsubConfirm = false },
            title = { Text("取消订阅合集?") },
            text = { Text("将取消「$displayTitle」的追合集并从歌单页移除(合集本身不受影响,之后可以重新添加)。") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsubConfirm = false
                    vm.unsubscribe(onDone = onBack)
                }) { Text("取消订阅", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showUnsubConfirm = false }) { Text("再想想") }
            },
        )
    }
}
