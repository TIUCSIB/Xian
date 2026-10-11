package dev.avsong.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.avsong.data.FavoriteRepository
import dev.avsong.data.SavedCollection
import dev.avsong.ui.rememberLibraryViewModel
import dev.avsong.ui.rememberLoginViewModel
import dev.avsong.ui.login.LoginUiState
import kotlinx.coroutines.launch

/**
 * 「我的」tab(底部导航第四项):账户卡(登录态/去登录)+ UP 主合集(本地保存)+ 收藏夹列表。
 * 原顶部导航入口(文件夹/账号图标)已收进这里,页面自身无返回键。
 */
@Composable
fun LibraryScreen(
    onOpenFolder: (FavoriteRepository.Folder) -> Unit,
    onOpenCollection: (SavedCollection) -> Unit,
    onLogin: () -> Unit,
) {
    val vm = rememberLibraryViewModel()
    val state by vm.state.collectAsState()
    val loginVm = rememberLoginViewModel()
    val loginState by loginVm.state.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var showAddCollection by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部柠檬黄大色块(与首页一致)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .statusBarsPadding()
                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "我的音乐",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                IconButton(onClick = { showCreate = true }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "新建收藏夹",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { AccountCard(loginState, onLogin = onLogin, onLogout = loginVm::logout) }

            // UP 主合集(本地保存):任何人的合集,粘链接添加,当歌单播
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "合集 (${state.collections.size})",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(onClick = {
                        vm.clearCollectionError()
                        showAddCollection = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加合集")
                    }
                }
            }
            if (state.collectionError != null && !showAddCollection) {
                // 对话框关闭后残留的网络错误,贴在区块下提示
                item {
                    Text(
                        state.collectionError!!,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            items(state.collections, key = { "col-${it.mid}-${it.seasonId}" }) { collection ->
                CollectionRow(
                    collection = collection,
                    onClick = { onOpenCollection(collection) },
                    onRemove = { vm.removeCollection(collection.mid, collection.seasonId) },
                )
            }

            when {
                state.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                state.error != null -> item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = vm::refresh) { Text("重试", color = MaterialTheme.colorScheme.primary) }
                    }
                }

                else -> {
                    if (state.folders.isNotEmpty()) {
                        item { FolderSectionHeader("我创建的 (${state.folders.size})") }
                        items(state.folders, key = { "c${it.id}" }) { folder ->
                            FolderRow(folder, onClick = { onOpenFolder(folder) }, onLongClick = {
                                vm.deleteFolder(folder.id)
                            })
                        }
                    }
                    if (state.subscribed.isNotEmpty()) {
                        item { FolderSectionHeader("收藏的 (${state.subscribed.size})") }
                        items(state.subscribed, key = { "s${it.id}" }) { folder ->
                            FolderRow(folder, onClick = { onOpenFolder(folder) }, onLongClick = null)
                        }
                    }
                    if (state.folders.isEmpty() && state.subscribed.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "还没有收藏夹,点右上 + 新建",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateFolderDialog(
            onDismiss = { showCreate = false },
            onCreate = { title, priv ->
                vm.createFolder(title, priv)
                showCreate = false
            },
        )
    }

    if (showAddCollection) {
        AddCollectionDialog(
            state = state,
            onDismiss = {
                vm.clearCollectionError()
                showAddCollection = false
            },
            onConfirm = { text ->
                scope.launch { if (vm.addCollection(text)) showAddCollection = false }
            },
        )
    }
}

/** 账户卡:登录态显示头像/用户名/会员,未登录点击进登录页。纯色扁平。 */
@Composable
private fun AccountCard(login: LoginUiState, onLogin: () -> Unit, onLogout: () -> Unit) {
    val uname = (login as? LoginUiState.LoggedIn)?.uname
    val vip = (login as? LoginUiState.LoggedIn)?.vipLabel

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = uname == null, onClick = onLogin)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像:黄色圆 + 用户名首字(未登录用问号位)
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (uname != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                uname?.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (uname != null) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                uname ?: "点击登录 B 站账号",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (uname != null) (vip?.let { "$it · 高音质已解锁" } ?: "普通用户 · 音质上限 192K")
                else "大会员账号可解锁更高音质",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uname != null) {
            IconButton(onClick = onLogout) {
                Icon(
                    Icons.Rounded.Logout,
                    contentDescription = "退出登录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FolderSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FolderRow(
    folder: FavoriteRepository.Folder,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (folder.cover != null) {
                AsyncImage(folder.cover, null, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                if (folder.isPrivate) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "私密",
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "${folder.mediaCount} 项" + (folder.ownerName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建收藏夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("名称") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.clickable { isPrivate = !isPrivate }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        null,
                        tint = if (isPrivate) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPrivate) "私密(仅自己可见)" else "公开")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onCreate(title.trim(), isPrivate) },
                enabled = title.isNotBlank(),
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CollectionRow(
    collection: SavedCollection,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (collection.cover != null) {
                AsyncImage(collection.cover, null, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                collection.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (collection.total > 0) "合集 · 共 ${collection.total} 个视频" else "合集",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "移除合集",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 添加合集对话框:粘贴 space.bilibili.com/<mid>/lists/<sid> 链接。
 * 解析失败就地提示且不关对话框(不打断输入);添加成功由调用方关闭。
 */
@Composable
private fun AddCollectionDialog(
    state: LibraryViewModel.UiState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var link by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加合集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "在 B 站打开任意 UP 主的合集页,把地址栏链接粘贴进来,即可当歌单播放",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text("space.bilibili.com/…/lists/…") },
                    singleLine = true,
                )
                if (state.collectionError != null) {
                    Text(
                        state.collectionError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.addingCollection) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "正在获取合集信息…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(link) },
                enabled = link.isNotBlank() && !state.addingCollection,
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
