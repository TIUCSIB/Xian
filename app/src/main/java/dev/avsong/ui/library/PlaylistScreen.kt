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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.avsong.data.FavoriteRepository
import dev.avsong.player.PlayerState
import dev.avsong.ui.common.PlayingEqualizer
import dev.avsong.data.SavedCollection
import dev.avsong.ui.common.FlatRefreshIndicator
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
    onPlayPause: () -> Unit,
    player: PlayerState,
    /** 正在播的歌单 id("folder:<id>"/"collection:<seasonId>");null=没有歌单在播。 */
    playingId: String?,
    onLogin: () -> Unit,
) {
    val vm = rememberLibraryViewModel()
    val state by vm.state.collectAsState()
    // 仅用于判断登录态:未登录时收藏夹必然拉不到,错误区给「去登录」比干巴巴的「重试」有用
    val loggedIn = rememberLoginViewModel().state.collectAsState().value is LoginUiState.LoggedIn
    var showCreate by remember { mutableStateOf(false) }
    var showAddCollection by remember { mutableStateOf(false) }
    // 长按菜单选中的动作 → 确认弹窗(破坏性操作不直接执行)
    var pendingDelete by remember { mutableStateOf<FavoriteRepository.Folder?>(null) }
    // 返回本页时同步状态:详情页里取消订阅/删除后,卡片要立刻消失(首载由 VM init 负责,不重复请求)
    LaunchedEffect(Unit) { vm.refreshIfLoaded() }
    var pendingUnsub by remember { mutableStateOf<SavedCollection?>(null) }
    val scope = rememberCoroutineScope()


    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 下拉刷新(2026-10-11 用户要求):刷新收藏夹列表(合集来自本地订阅,不需刷新)。
        // 指示器与首页同一份纯色扁平款;已有内容时刷新保持列表可见,只在顶部转圈
        val hasContent = state.folders.isNotEmpty() || state.subscribed.isNotEmpty() ||
            state.collections.isNotEmpty() || state.followedSeasons.isNotEmpty()
        // 合集区 = 本地订阅 ∪ B 站账号「我追的合集」(去重,本地条目优先——它的 mid 一定有)
        val allCollections = remember(state.collections, state.followedSeasons) {
            (state.collections + state.followedSeasons).distinctBy { it.seasonId }
        }
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.loading && hasContent,
            onRefresh = vm::refresh,
            state = pullState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            indicator = {
                FlatRefreshIndicator(
                    pullState,
                    state.loading && hasContent,
                    Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                // 黄色大标题横幅已按用户要求去掉:它带的「新建收藏夹」+ 与合集区的 + 冗余,
                // 新建入口收进「我创建的」标题行(2026-10-11)
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // UP 主合集:本地订阅(粘链接添加)+ B 站「我追的合集」自动同步
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 2.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "合集 (${allCollections.size})",
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
                items(allCollections, key = { "col-${it.mid}-${it.seasonId}" }) { collection ->
                    PlaylistCard(
                        title = collection.title,
                        cover = collection.cover,
                        count = collection.total,
                        onOpen = { onOpenCollection(collection) },
                        onPlay = { onPlayCollection(collection) },
                        onPause = onPlayPause,
                        isCurrent = playingId == "collection:${collection.seasonId}",
                        isPlaying = player.isPlaying,
                        // 长按 → 取消订阅(同时取消 B 站「追合集」+ 移除本地,确认后执行防误触)
                        menuActions = listOf("取消订阅" to { pendingUnsub = collection }),
                    )
                }

                when {
                    // 首次加载才铺全屏转圈;下拉刷新时(已有内容)保持列表可见,进度在顶部指示器
                    state.loading && !hasContent -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // 错误只在没有内容可展示时占屏;刷新失败时保留旧列表,不把用户内容换走
                    state.error != null && !hasContent -> item(span = { GridItemSpan(maxLineSpan) }) {
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
                        // 「我创建的」标题行带「新建收藏夹」+;空夹时也在,否则新建入口消失
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 6.dp, end = 2.dp, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "我创建的 (${state.folders.size})",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                IconButton(onClick = { showCreate = true }) {
                                    Icon(Icons.Filled.Add, contentDescription = "新建收藏夹")
                                }
                            }
                        }
                        if (state.actionError != null) {
                            // 删除收藏夹失败(网络/接口拒绝)时的就地提示,不让操作静默失败
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    state.actionError!!,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        items(state.folders, key = { "c${it.id}" }) { folder ->
                            PlaylistCard(
                                title = folder.title,
                                cover = folder.cover,
                                count = folder.mediaCount,
                                isPrivate = folder.isPrivate,
                                onOpen = { onOpenFolder(folder) },
                                onPlay = { onPlayFolder(folder) },
                                onPause = onPlayPause,
                                isCurrent = playingId == "folder:${folder.id}",
                                isPlaying = player.isPlaying,
                                // 长按 → 删除收藏夹(B 站远端破坏性操作,必须确认)
                                menuActions = listOf("删除收藏夹" to { pendingDelete = folder }),
                            )
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
                                    onPause = onPlayPause,
                                    isCurrent = playingId == "folder:${folder.id}",
                                    isPlaying = player.isPlaying,
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
                                        "还没有收藏夹,点上面的 + 新建",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
            onCreate = { title, intro, priv ->
                vm.createFolder(title, intro, priv)
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

    // 删除收藏夹确认:会同时删掉 B 站上的文件夹(视频本身不受影响),不可撤销
    pendingDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除收藏夹?") },
            text = {
                Text(
                    if (folder.mediaCount > 0) {
                        "「${folder.title}」及其中的 ${folder.mediaCount} 项收藏会被删除,视频本身不受影响,操作不可撤销。"
                    } else {
                        "「${folder.title}」会被删除,操作不可撤销。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFolder(folder.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    // 取消订阅确认:只移除本地记录,合集本身在 B 站不受影响
    pendingUnsub?.let { collection ->
        AlertDialog(
            onDismissRequest = { pendingUnsub = null },
            title = { Text("取消订阅合集?") },
            text = { Text("将取消「${collection.title}」的追合集并从歌单页移除(合集本身不受影响,之后可以重新添加)。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.unsubscribeCollection(collection)
                    pendingUnsub = null
                }) { Text("取消订阅", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingUnsub = null }) { Text("再想想") } },
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
 * 封面底部有向上渐变的黑纱遮罩,右下角裸白色图标(无底色)直接播整个歌单;
 * **正在播这个歌单时图标换成动画音波条、点击变暂停;暂停中显示播放三角、点击续播**
 * (用户 2026-10-14 要求:播放态别一直显示播放键;交互对齐首页精选卡)。
 * 徽标(左上)= 项数。
 * [menuActions] 非空时长按卡片弹操作菜单(合集=取消订阅,自建夹=删除收藏夹);
 * 菜单只负责挑动作,破坏性操作的真实确认在调用方的 AlertDialog。
 */
@Composable
private fun PlaylistCard(
    title: String,
    cover: String?,
    count: Int,
    isPrivate: Boolean = false,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit = {},
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    menuActions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { if (menuActions.isNotEmpty()) menuExpanded = true },
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
            // 底部往上渐变的黑纱遮罩:托住右下角白色播放图标(用户要求的例外,全局禁渐变不含此处)
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
            )
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
            // 右下播放钮:裸白色图标(无底色),对比度靠底部渐变遮罩;
            // clip 圆形必须保留——没有它 clickable 的涟漪会画成方形。
            // 正在播本歌单 → 音波动画(点击暂停);暂停中 → 播放三角(点击续播);
            // 未在播 → 播放三角(点击播整个歌单)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 2.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = if (isCurrent && isPlaying) onPause else onPlay),
                contentAlignment = Alignment.Center,
            ) {
                if (isCurrent && isPlaying) {
                    PlayingEqualizer(
                        Modifier.size(width = 22.dp, height = 16.dp),
                        onArtwork = true,
                    )
                } else {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = if (isCurrent) "继续播放" else "播放整个歌单",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
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
        // 长按菜单:锚在卡片左上角(DropdownMenu 是 popup,不占布局空间)
        if (menuActions.isNotEmpty()) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                menuActions.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            menuExpanded = false
                            action()
                        },
                    )
                }
            }
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
private fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var intro by remember { mutableStateOf("") }
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
                OutlinedTextField(
                    value = intro,
                    onValueChange = { intro = it },
                    placeholder = { Text("简介(可选)") },
                    singleLine = true,
                )
                // 公开/私密用 Switch(用户指定样式):文案随状态变,开关在行尾
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isPrivate) "私密(仅自己可见)" else "公开",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onCreate(title.trim(), intro.trim(), isPrivate) },
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
