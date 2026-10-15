package dev.avsong.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.avsong.data.FavoriteRepository
import dev.avsong.data.LocalPlaylist

/**
 * 加入收藏夹弹窗:列出我的收藏夹,勾选=加入/取消=移出。
 * 打开时先经 view 解析 aid(收藏操作要 aid,bvid 不行),再拉收藏夹与当前状态。
 *
 * 2026-10-15 起为**双区**:下方追加「本地歌单」分组([localPlaylists] 非空时渲染),
 * 勾选 = 存入/移出本地歌单([onToggleLocal]),纯 DataStore 落盘零网络,即时生效 ——
 * 收藏一个动作同时覆盖 B 站收藏夹与本地歌单,不用分两个入口。
 */
@Composable
fun FavoritePickerDialog(
    favRepo: FavoriteRepository,
    bvid: String,
    onDismiss: () -> Unit,
    /** 关闭时回传:当前曲是否仍在至少一个收藏夹里,供播放页爱心同步状态。 */
    onDismissWithState: ((Boolean) -> Unit)? = null,
    /** 关闭时回传结果消息(成功/部分失败),由调用方走主题化提示条;null=无话可说。 */
    onResult: ((String?) -> Unit)? = null,
    /** 本地歌单列表;null=不渲染本地歌单区(旧调用方兼容)。 */
    localPlaylists: List<LocalPlaylist>? = null,
    /** 勾选/取消本地歌单(checked=当前是否已在);本地操作不失败,无需失败回传。 */
    onToggleLocal: ((LocalPlaylist, Boolean) -> Unit)? = null,
    /** 本地歌单区就地新建。 */
    onCreateLocal: ((String) -> Unit)? = null,
) {
    var folders by remember { mutableStateOf<List<FavoriteRepository.Folder>?>(null) }
    var checked by remember { mutableStateOf(setOf<Long>()) }
    var aid by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var reloadTick by remember { mutableStateOf(0) }
    // 本地歌单区的新建输入框开合(与 B 站收藏夹的新建互不干扰)
    var showCreateLocal by remember { mutableStateOf(false) }
    var newLocalTitle by remember { mutableStateOf("") }
    // 乐观更新后台提交失败的夹 id:完成时若有残留,提示条报「部分收藏未成功」
    val failedIds = remember { mutableStateOf(setOf<Long>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(bvid, reloadTick) {
        runCatching {
            val a = favRepo.fetchAid(bvid)
            favRepo.myFolders() to (if (a > 0) favRepo.checkFavorite(a) else emptyList()) to a
        }.onSuccess { (pair, resolvedAid) ->
            val (list, inFolders) = pair
            aid = resolvedAid
            folders = list
            checked = inFolders.toSet()
        }.onFailure { error = it.message }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收藏到") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (showCreate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            placeholder = { Text("新收藏夹名称") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            if (newTitle.isNotBlank()) {
                                scope.launch {
                                    runCatching { favRepo.createFolder(newTitle.trim(), "", false) }
                                        .onFailure { error = "创建失败:${it.message}" }
                                    newTitle = ""
                                    showCreate = false
                                    reloadTick++
                                }
                            }
                        }) { Text("建") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                when {
                    error != null -> Text("加载失败:$error", color = MaterialTheme.colorScheme.error)
                    folders == null -> Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    else -> LazyColumn(Modifier.height(320.dp)) {
                        items(folders!!, key = { "f${it.id}" }) { folder ->
                            val isChecked = folder.id in checked
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // 乐观更新,后台提交差量;失败记入 failedIds,完成时统一提示
                                        checked = if (isChecked) checked - folder.id else checked + folder.id
                                        scope.launch {
                                            runCatching {
                                                if (isChecked) {
                                                    favRepo.modify(aid, delIds = folder.id.toString())
                                                } else {
                                                    favRepo.modify(aid, addIds = folder.id.toString())
                                                }
                                            }.onFailure {
                                                failedIds.value = failedIds.value + folder.id
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(22.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isChecked) {
                                        Icon(
                                            Icons.Filled.Check,
                                            null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    folder.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (folder.isPrivate) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    " ${folder.mediaCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item(key = "create-fav") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCreate = !showCreate }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(22.dp))
                                Text("新建收藏夹", color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // ---- 本地歌单区(勾选即落盘,零网络)----
                        if (localPlaylists != null && onToggleLocal != null) {
                            item(key = "local-header") {
                                Text(
                                    "本地歌单",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 2.dp, top = 12.dp, bottom = 2.dp),
                                )
                            }
                            items(localPlaylists, key = { "l${it.id}" }) { pl ->
                                val isChecked = pl.tracks.any { it.bvid == bvid }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleLocal(pl, isChecked) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                        if (isChecked) {
                                            Icon(
                                                Icons.Filled.Check,
                                                null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        pl.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${pl.tracks.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            item(key = "create-local-input") {
                                if (showCreateLocal) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = newLocalTitle,
                                            onValueChange = { newLocalTitle = it },
                                            placeholder = { Text("新歌单名称") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = {
                                            if (newLocalTitle.isNotBlank()) {
                                                onCreateLocal?.invoke(newLocalTitle.trim())
                                                newLocalTitle = ""
                                                showCreateLocal = false
                                            }
                                        }) { Text("建") }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showCreateLocal = true }
                                            .padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(22.dp))
                                        Text("新建歌单", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val failed = failedIds.value.size
                onResult?.invoke(
                    when {
                        failed > 0 -> "$failed 个收藏夹未保存,请检查网络后重试"
                        checked.isNotEmpty() -> "已收藏到 ${checked.size} 个收藏夹"
                        else -> null // 全部取消且无失败:静默(移出本身可见于列表刷新)
                    },
                )
                onDismissWithState?.invoke(checked.isNotEmpty())
                onDismiss()
            }) { Text("完成") }
        },
    )
}
