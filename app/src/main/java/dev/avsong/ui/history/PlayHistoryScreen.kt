package dev.avsong.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.avsong.domain.Song
import dev.avsong.ui.common.SongList
import dev.avsong.ui.common.StateHint

/**
 * 全部播放历史页(「我的」页设置卡存储组入口):
 * 与队列抽屉「历史播放」tab 同一数据源(PlayHistoryStore,上限在设置可调),
 * 但整页可滚动、行数不受抽屉高度限制。
 * 点行回放策略 = playFromHistory(在队列跳播/队列空单曲入列/否则追加队尾),不打乱现有队列;
 * **点当前曲 = 暂停/续播**(不再 seekTo(0) 从头重播,与已下载页同一交互)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayHistoryScreen(
    history: List<Song>,
    currentMediaId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayFromHistory: (Song) -> Unit,
    onPlayPause: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (history.isEmpty()) {
                StateHint(
                    Icons.Rounded.History,
                    "还没有播放记录",
                    "播放过的歌会出现在这里",
                )
            } else {
                SongList(
                    songs = history,
                    onPlay = { songs, index ->
                        // 点当前曲 = 暂停/续播:playFromHistory 会 seekTo(0) 重播,不该用于当前曲
                        if (songs.getOrNull(index)?.mediaId == currentMediaId) onPlayPause()
                        else onPlayFromHistory(songs[index])
                    },
                    showPlayButton = false,
                    currentMediaId = currentMediaId,
                    isPlaying = isPlaying,
                    // 二级页底部只有悬浮迷你条胶囊(列表可从其两侧穿过),避让只保文字不被压
                    contentBottomPadding = 24.dp,
                )
            }
        }
    }
}
