package dev.avsong.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.avsong.AvSongApp
import dev.avsong.data.DownloadedTrack
import dev.avsong.domain.formatClock
import dev.avsong.domain.Song
import dev.avsong.ui.common.SongList
import dev.avsong.ui.common.CoverArt
import dev.avsong.ui.common.StateHint
import dev.avsong.ui.common.ThemedNoticeHost
import dev.avsong.ui.library.BatchFavoriteDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「已下载」页:应用私有 MUSIC 目录的离线歌曲(浏览/播放/删除)。
 * 数据源 = DownloadedStore 元数据 + 进页扫一次孤儿目录(改动前的旧下载以文件名兜底入列)。
 * 播放行 = Song(audioUrl = file://…):mediaId 仍是 bvid,收藏/歌词/历史链路照常工作;
 * 删除走模态确认(与清空队列同款防误触),连文件带元数据一起移除。
 */
@Composable
fun DownloadedScreen(
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    /** 点到「当前播放中」的行:播放/暂停切换(而不是重建队列从头播)。 */
    onPlayPause: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    currentMediaId: String?,
    isPlaying: Boolean,
    /** 多选批量动作(2026-10-15 加):下一首/入队/收藏走 MainViewModel 批量,删除走 store。 */
    onPlayNextBatch: (List<Song>) -> Unit = {},
    onAddToQueueBatch: (List<Song>) -> Unit = {},
    onFavoriteBatch: (songs: List<Song>, folderIds: List<Long>) -> Unit = { _, _ -> },
) {
    val app = LocalContext.current.applicationContext as AvSongApp
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracks by app.downloadedStore.state.collectAsState()
    val downloadTasks by app.downloadManager.tasks.collectAsState()
    var deleteTarget by remember { mutableStateOf<DownloadedTrack?>(null) }
    // 删除结果反馈:主题化提示条(与全局队列/下载反馈同款,不用系统 Toast)
    var deleteNotice by remember { mutableStateOf<String?>(null) }
    // 本地搜索:纯内存过滤,不走网络(与歌单详情页的搜索态同款交互,但无需 loadAll)
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    // 多选模式:选中集合(bvid);进入入口=顶栏「多选」,退出即清空
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    // 批量收藏弹窗(多选态「收藏」键 → 勾收藏夹 → onFavoriteBatch)
    var showBatchFav by remember { mutableStateOf(false) }
    var batchFavSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    // 批量删除确认(多选态「删除」键;删除不可逆,与单曲删除同款防误触)
    var showBatchDelete by remember { mutableStateOf(false) }

    // 进页扫孤儿(有文件无元数据的旧下载补进列表);load 自愈在 Application 启动已做。
    // LaunchedEffect 在主线程,文件扫描/落盘务必切 IO
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { app.downloadedStore.scanOrphans() } }

    // 多选态按系统返回=退出多选(不关页);搜索态同理
    androidx.activity.compose.BackHandler(enabled = selecting || searching) {
        if (selecting) {
            selecting = false
            selected = emptySet()
        } else {
            searching = false
            query = ""
        }
    }

    // 只渲染文件仍在的条目(扫描后仍可能被外部清掉)
    val allSongs = tracks.mapNotNull { t ->
        val f = app.downloadedStore.fileOf(t)?.takeIf { it.exists() } ?: return@mapNotNull null
        Song(
            bvid = t.bvid,
            title = t.title,
            artist = t.artist,
            coverUrl = t.coverUrl,
            durationSec = t.durationSec,
            audioUrl = android.net.Uri.fromFile(f).toString(),
        )
    }
    // 本地过滤:标题/歌手不区分大小写(与歌单内搜索同口径)
    val songs = if (query.isBlank()) allSongs else {
        val q = query.trim().lowercase()
        allSongs.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                // 多选态顶栏:返回=退出多选;全选/反选;搜索态与多选互斥(进多选先退搜索)
                IconButton(onClick = { selecting = false; selected = emptySet() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "退出多选")
                }
                Text(
                    "已选 " + selected.size + "/" + songs.size,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { selected = songs.map { it.bvid }.toSet() }) { Text("全选") }
                TextButton(onClick = {
                    val all = songs.map { it.bvid }.toSet()
                    selected = all - selected
                }) { Text("反选") }
            } else if (searching) {
                // 搜索态:返回键退出搜索(清空关键词),输入框自动聚焦
                IconButton(onClick = {
                    searching = false
                    query = ""
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "退出搜索")
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索已下载") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocus),
                )
                LaunchedEffect(Unit) { searchFocus.requestFocus() }
            } else {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "已下载 (${allSongs.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (allSongs.isNotEmpty()) {
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = {
                        selecting = true
                        selected = emptySet()
                    }) {
                        Icon(Icons.Rounded.Checklist, contentDescription = "多选")
                    }
                }
            }
        }
        // 下载任务条:排队/进行中/失败的任务逐条显示(进度+取消/重试);完成即消失
        if (downloadTasks.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                downloadTasks.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val status = when {
                                t.state == dev.avsong.data.DownloadManager.State.QUEUED -> "排队中"
                                t.state == dev.avsong.data.DownloadManager.State.FAILED -> "失败:${t.error}"
                                t.progress in 0..100 -> "下载中 $t.progress%".replace("t.progress", t.progress.toString())
                                else -> "下载中"
                            }
                            Text(
                                status,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (t.state == dev.avsong.data.DownloadManager.State.FAILED) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (t.state == dev.avsong.data.DownloadManager.State.RUNNING && t.progress in 0..100) {
                                LinearProgressIndicator(
                                    progress = { t.progress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                        when (t.state) {
                            dev.avsong.data.DownloadManager.State.FAILED -> TextButton(onClick = { app.downloadManager.retry(t.bvid) }) {
                                Text("重试")
                            }
                            else -> IconButton(onClick = { app.downloadManager.cancel(t.bvid) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "取消下载")
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                allSongs.isEmpty() -> StateHint(
                    icon = Icons.Rounded.Download,
                    title = "还没有下载",
                    subtitle = "在队列抽屉或歌单行菜单点「下载」,离线也能听",
                )
                songs.isEmpty() && !selecting -> StateHint(
                    icon = Icons.Rounded.SearchOff,
                    title = "没有匹配的歌曲",
                    subtitle = "换个关键词试试",
                )
                selecting -> {
                    // 多选态:同一份列表就地加勾选圈(与队列多选同款)——勾选圈在最左,
                    // 封面/标题/歌手/时长全保留;点行=切换选中不播歌。不跳别的页面,此页解决
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            songs.forEach { song ->
                                val isSel = song.bvid in selected
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selected = if (isSel) selected - song.bvid else selected + song.bvid
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // 勾选圈在最左(队列多选同款):勾中实心 primary 白勾,未勾描边圈
                                    Box(
                                        Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .then(
                                                if (isSel) Modifier else Modifier.border(
                                                    1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape,
                                                ),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isSel) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = "已勾选",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    CoverArt(url = song.coverUrl)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            song.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
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
                        }
                        // 批量操作栏:下一首/入队/收藏/删除(下载对已下载无意义,不提供);
                        // 删除走模态确认,其余执行完即退出多选
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            val selSongs = songs.filter { it.bvid in selected }
                            val enabled = selSongs.isNotEmpty()
                            ActionButton("下一首", Modifier.weight(1f), enabled) {
                                onPlayNextBatch(selSongs)
                                selecting = false; selected = emptySet()
                            }
                            ActionButton("入队", Modifier.weight(1f), enabled) {
                                onAddToQueueBatch(selSongs)
                                selecting = false; selected = emptySet()
                            }
                            ActionButton("收藏", Modifier.weight(1f), enabled) {
                                batchFavSongs = selSongs
                                showBatchFav = true
                            }
                            ActionButton("删除", Modifier.weight(1f), enabled) {
                                showBatchDelete = true
                            }
                        }
                    }
                }
                else -> SongList(
                    songs = songs,
                    // 行尾播放钮不显示(与首页列表一致,行本身可点);
                    // 点当前曲 = 播放/暂停(重复点击重建队列从头播的旧逻辑不对,用户反馈)
                    showPlayButton = false,
                    onPlay = { list, index ->
                        if (list.getOrNull(index)?.bvid == currentMediaId) onPlayPause()
                        else onPlay(list, index)
                    },
                    currentMediaId = currentMediaId,
                    isPlaying = isPlaying,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onRemove = { song -> tracks.firstOrNull { it.bvid == song.bvid }?.let { deleteTarget = it } },
                )
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除已下载?") },
            text = { Text("「${target.title}」的离线文件将从存储中移除,删除后需重新联网下载。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val deleted = app.downloadedStore.remove(target.bvid)
                        deleteNotice = if (deleted) "已删除" else "文件已不存在,记录已清理"
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    // 批量收藏弹窗:勾收藏夹 → onFavoriteBatch(MainViewModel.favoriteSongs,防 -412 逐首 modify)
    if (showBatchFav) {
        BatchFavoriteDialog(
            favRepo = app.favoriteRepository,
            songCount = batchFavSongs.size,
            onConfirm = { folderIds ->
                showBatchFav = false
                onFavoriteBatch(batchFavSongs, folderIds)
                selecting = false
                selected = emptySet()
            },
            onDismiss = { showBatchFav = false },
        )
    }

    // 批量删除确认:删除不可逆(连文件带元数据),与单曲删除同款防误触
    if (showBatchDelete) {
        AlertDialog(
            onDismissRequest = { showBatchDelete = false },
            title = { Text("删除 " + selected.size + " 首已下载?") },
            text = { Text("选中的离线文件将从存储中移除,删除后需重新联网下载。") },
            confirmButton = {
                TextButton(onClick = {
                    showBatchDelete = false
                    val targets = selected.toList()
                    scope.launch(Dispatchers.IO) {
                        var ok = 0
                        targets.forEach { bvid -> if (app.downloadedStore.remove(bvid)) ok++ }
                        withContext(Dispatchers.Main) {
                            deleteNotice = "已删除 " + ok + " 首"
                            selecting = false
                            selected = emptySet()
                        }
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDelete = false }) { Text("取消") }
            },
        )
    }

    ThemedNoticeHost(message = deleteNotice, onConsumed = { deleteNotice = null })
}

/** 多选操作栏按钮:纯文本 TextButton(等宽),禁用态由 enabled 控制。 */
@Composable
private fun ActionButton(label: String, modifier: Modifier = Modifier, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}
