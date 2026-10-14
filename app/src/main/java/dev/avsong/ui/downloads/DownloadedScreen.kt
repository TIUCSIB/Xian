package dev.avsong.ui.downloads

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.avsong.AvSongApp
import dev.avsong.data.DownloadedTrack
import dev.avsong.domain.Song
import dev.avsong.ui.common.SongList
import dev.avsong.ui.common.StateHint
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
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    currentMediaId: String?,
    isPlaying: Boolean,
) {
    val app = LocalContext.current.applicationContext as AvSongApp
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracks by app.downloadedStore.state.collectAsState()
    var deleteTarget by remember { mutableStateOf<DownloadedTrack?>(null) }

    // 进页扫孤儿(有文件无元数据的旧下载补进列表);load 自愈在 Application 启动已做。
    // LaunchedEffect 在主线程,文件扫描/落盘务必切 IO
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { app.downloadedStore.scanOrphans() } }

    // 只渲染文件仍在的条目(扫描后仍可能被外部清掉)
    val songs = tracks.mapNotNull { t ->
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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                "已下载 (${songs.size})",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Box(Modifier.fillMaxSize()) {
            if (songs.isEmpty()) {
                StateHint(
                    icon = Icons.Rounded.Download,
                    title = "还没有下载",
                    subtitle = "在队列抽屉或歌单行菜单点「下载」,离线也能听",
                )
            } else {
                SongList(
                    songs = songs,
                    onPlay = onPlay,
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
                        Toast.makeText(
                            context,
                            if (deleted) "已删除" else "文件已不存在,记录已清理",
                            Toast.LENGTH_SHORT,
                        ).show()
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
}
