package dev.avsong.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.avsong.player.QueueEntry

/**
 * 「歌单」tab(底部导航第三项):就是当前播放队列。
 * 用户心智里「歌单 = 我接下来要放的这串列表」,所以不做独立的本地歌单存储,
 * 直接复用 [QueueContent](与播放页右上入口的 [QueueScreen] 同一份列表与交互)。
 * 外壳换成与其它 tab 一致的黄色标题块,无返回键。
 */
@Composable
fun PlaylistScreen(
    queue: List<QueueEntry>,
    currentIndex: Int,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部柠檬黄大色块(与首页/我的一致)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "歌单",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                if (queue.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${queue.size} 首",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        }

        QueueContent(
            queue = queue,
            currentIndex = currentIndex,
            onPlayAt = onPlayAt,
            onRemoveAt = onRemoveAt,
            onMove = onMove,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
