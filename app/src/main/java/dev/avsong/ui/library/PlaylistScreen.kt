package dev.avsong.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.avsong.data.FavoriteRepository
import dev.avsong.data.SavedCollection
import dev.avsong.ui.login.LoginUiState
import dev.avsong.ui.rememberLibraryViewModel
import dev.avsong.ui.rememberLoginViewModel
import kotlinx.coroutines.launch

/**
 * 「歌单」tab(底部导航第三项):歌单中心 = UP 主合集(本地订阅)+ B 站收藏夹。
 * 版式(用户 2026-10-11 指定):两列网格卡片 —— 正方形封面,左上角数字徽标,
 * 封面右下角播放钮(直接播整个歌单),封面下方一行歌单名(上方图片下方标题)。
 * ⚠ B 站收藏夹/合集的列表接口没有播放数字段,徽标放的是歌单项数(media_count/total)。
 * 播放队列不在此页(只留迷你条/播放页抽屉)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    onOpenFolder: (FavoriteRepository.Folder) -> Unit,
    onOpenCollection: (SavedCollection) -> Unit,
    onPlayFolder: (FavoriteRepository.Folder) -> Unit,
    onPlayCollection: (SavedCollection) -> Unit,
    onLogin: () -> Unit,
) {
    val vm = rememberLibraryViewModel()
    val state by vm.state.collectAsState()
    // 仅用于判断登录态:未登录时收藏夹必然拉不到,错误区给「去登录」比干巴巴的「重试」有用
    val loggedIn = rememberLoginViewModel().state.collectAsState().value is LoginUiState.LoggedIn
    var showCreate by remember { mutableStateOf(false) }
    var showAddCollection by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部柠檬黄大色块(与首页/我的一致)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .statusBarsPadding()
                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "歌单",
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // UP 主合集(本地订阅):任何人的合集,粘链接添加,当歌单播
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 2.dp, top = 6.dp),
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        state.collectionError!!,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            items(state.collections, key = { "col-${it.mid}-${it.seasonId}" }) { collection ->
                PlaylistCard(
                    title = collection.title,
                    cover = collection.cover,
                    count = collection.total,
                    onOpen = { onOpenCollection(collection) },
                    onPlay = { onPlayCollection(collection) },
                    // 长按移除本地订阅(随时可重新添加,故不加确认)
                    onLongClick = { vm.removeCollection(collection.mid, collection.seasonId) },
                )
            }

            when {
                state.loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                state.error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = vm::refresh) {
                                Text("重试", color = MaterialTheme.colorScheme.primary)
                            }
                            if (!loggedIn) {
                                TextButton(onClick = onLogin) {
                                    Text("去登录", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                else -> {
                    if (state.folders.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader("我创建的 (${state.folders.size})")
                        }
                        items(state.folders, key = { "c${it.id}" }) { folder ->
                            PlaylistCard(
                                title = folder.title,
                                cover = folder.cover,
                                count = folder.mediaCount,
                                isPrivate = folder.isPrivate,
                                onOpen = { onOpenFolder(folder) },
                                onPlay = { onPlayFolder(folder) },
                            )
                        }
                    }
                    if (state.subscribed.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader("收藏的 (${state.subscribed.size})")
                        }
                        items(state.subscribed, key = { "s${it.id}" }) { folder ->
                            PlaylistCard(
                                title = folder.title,
                                cover = folder.cover,
                                count = folder.mediaCount,
                                isPrivate = folder.isPrivate,
                                onOpen = { onOpenFolder(folder) },
                                onPlay = { onPlayFolder(folder) },
                            )
                        }
                    }
                    if (state.folders.isEmpty() && state.subscribed.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
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

/** 分区小标题(我创建的/收藏的),整行占满。 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp),
    )
}

/**
 * 歌单网格卡片:上方正方形封面 + 下方标题。
 * 徽标(左上)= 项数;右下黄圆播放钮 = 直接整队播放本歌单(与首页卡片播放键同款黄底)。
 * 合集卡传 [onLongClick] 长按移除本地订阅;收藏夹卡不传(删除是 B 站远端破坏性操作,未开放)。
 */
@Composable
private fun PlaylistCard(
    title: String,
    cover: String?,
    count: Int,
    isPrivate: Boolean = false,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (cover != null) {
                AsyncImage(
                    cover,
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Folder,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            // 左上角数字徽标(半透明黑纱胶囊,纯色扁平无阴影)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    formatPlaylistCount(count),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            // 右下播放钮:直接播整个歌单
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "播放整个歌单",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Row(
            modifier = Modifier.padding(start = 2.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isPrivate) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "私密",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 徽标数字:1 万以上按「x.x万」缩写(网易云歌单卡的读法)。 */
private fun formatPlaylistCount(count: Int): String {
    if (count < 10000) return count.toString()
    val dec = (count / 1000).toDouble() / 10.0
    return if (dec % 1.0 == 0.0) "${dec.toInt()}万" else "${dec}万"
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
