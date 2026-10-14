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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.avsong.data.FavoriteRepository

/**
 * 加入收藏夹弹窗:列出我的收藏夹,勾选=加入/取消=移出。
 * 打开时先经 view 解析 aid(收藏操作要 aid,bvid 不行),再拉收藏夹与当前状态。
 */
@Composable
fun FavoritePickerDialog(
    favRepo: FavoriteRepository,
    bvid: String,
    onDismiss: () -> Unit,
    /** 关闭时回传:当前曲是否仍在至少一个收藏夹里,供播放页爱心同步状态。 */
    onDismissWithState: ((Boolean) -> Unit)? = null,
) {
    var folders by remember { mutableStateOf<List<FavoriteRepository.Folder>?>(null) }
    var checked by remember { mutableStateOf(setOf<Long>()) }
    var aid by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var reloadTick by remember { mutableStateOf(0) }
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

                    else -> LazyColumn(Modifier.height(280.dp)) {
                        items(folders!!) { folder ->
                            val isChecked = folder.id in checked
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // 乐观更新,后台提交差量
                                        checked = if (isChecked) checked - folder.id else checked + folder.id
                                        scope.launch {
                                            runCatching {
                                                if (isChecked) {
                                                    favRepo.modify(aid, delIds = folder.id.toString())
                                                } else {
                                                    favRepo.modify(aid, addIds = folder.id.toString())
                                                }
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
                        item {
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
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismissWithState?.invoke(checked.isNotEmpty())
                onDismiss()
            }) { Text("完成") }
        },
    )
}
