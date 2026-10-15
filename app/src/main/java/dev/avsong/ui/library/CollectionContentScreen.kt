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
    /** 头部「播放」= 整队替换:把已加载部分整队入列并起播(不走 playNow 插播)。 */
    onPlayQueue: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onFavorite: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    /** ⋮ 菜单「下载全部」：把已加载的歌曲整批入队(队列自动去重已下载/已在队的)。 */
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
        // 头部「播放」= 整队替换(2026-10-15 用户反馈:播放歌单不该往旧队列里追加——
        // 旧队列与合集的重复曲会让补全去重跳过,合集里那几首反而没进队)。点行仍=插播不换队。
        onPlayAll = { loaded -> vm.playWithFill(0, { songs, _ -> onPlayQueue(songs) }, replace = true) },
        // 播放前先拉全量:否则大合集点播放只入列已加载的第一页(20 首)
        // 搜索期间播过滤子集(直接整队,不走 playWithFill——它的 loadingAll 护栏会
        // 吞掉搜索加载中的点击,且全量列表第 index 首与过滤子集第 index 首对不上,用户实测两 bug)
        onPlay = { playing, index ->
            when {
                playing !== state.songs -> onPlay(playing, index)
                // 补全进行中的点击不能被 loadingAll 护栏静默吞掉(978 首合集补全要跑几十秒,
                // 用户实测期间点行没反应):直播所点那一首(playNow:插当前曲后立即播,
                // 不换队列不动 session,补全循环继续把剩余页灌进同一队列)
                loadingAll -> onPlay(listOf(state.songs[index]), 0)
                else -> vm.playWithFill(index, onPlay)
            }
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
        onRemoveSong = null,
        loadingAll = loadingAll,
        loadingHint = if (loadingAll) "已加载 ${state.songs.size}/${state.total},补全中…" else null,
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
