package dev.avsong.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.avsong.data.FavoriteRepository

/**
 * 批量收藏夹选择(队列多选收藏用):列出我的收藏夹,勾选若干夹,确定后由调用方
 * 对每首选中歌逐个 modify。与单曲版 FavoritePickerDialog 的区别:
 * 单曲版回显「这首歌已在哪些夹」,批量版是「把选中的 N 首都加进勾选的夹」——
 * 语义不同(没有 per-歌回显),新建对话框而非改造单曲版。
 */
@Composable
fun BatchFavoriteDialog(
    favRepo: FavoriteRepository,
    songCount: Int,
    onConfirm: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var folders by remember { mutableStateOf<List<FavoriteRepository.Folder>?>(null) }
    var checked by remember { mutableStateOf(setOf<Long>()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { favRepo.myFolders() }
            .onSuccess { folders = it }
            .onFailure { error = it.message }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收藏 \$songCount 首到") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                when {
                    error != null -> Text("加载失败:\$error", color = MaterialTheme.colorScheme.error)
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
                                        checked = if (isChecked) checked - folder.id else checked + folder.id
                                    }
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
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = checked.isNotEmpty() && folders != null,
                onClick = { onConfirm(checked.toList()) },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
