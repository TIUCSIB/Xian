package dev.avsong.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.avsong.AvSongApp
import dev.avsong.data.DownloadedTrack
import dev.avsong.domain.Song
import dev.avsong.ui.common.SongList
import dev.avsong.ui.common.StateHint
import dev.avsong.ui.common.ThemedNoticeHost
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

    // 进页扫孤儿(有文件无元数据的旧下载补进列表);load 自愈在 Application 启动已做。
    // LaunchedEffect 在主线程,文件扫描/落盘务必切 IO
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { app.downloadedStore.scanOrphans() } }

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
            if (searching) {
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
                songs.isEmpty() -> StateHint(
                    icon = Icons.Rounded.SearchOff,
                    title = "没有匹配的歌曲",
                    subtitle = "换个关键词试试",
                )
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

    ThemedNoticeHost(message = deleteNotice, onConsumed = { deleteNotice = null })
}
