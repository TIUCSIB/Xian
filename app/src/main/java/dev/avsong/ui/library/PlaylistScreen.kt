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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.avsong.ui.common.SkeletonPlaylistRows
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
    /** 正在播的歌单 id("folder:<id>"/"collection:<seasonId>"/"local:<id>");null=没有歌单在播。 */
    playingId: String?,
    onLogin: () -> Unit,
    /** 本地自建歌单(2026-10-15 加):数据经 MainViewModel.localPlaylistStore,动作用 MainViewModel。 */
    localPlaylists: List<dev.avsong.data.LocalPlaylist> = emptyList(),
    onCreateLocalPlaylist: (String) -> Unit = {},
    onDeleteLocalPlaylist: (dev.avsong.data.LocalPlaylist) -> Unit = {},
    onRenameLocalPlaylist: (dev.avsong.data.LocalPlaylist, String) -> Unit = { _, _ -> },
    onOpenLocalPlaylist: (dev.avsong.data.LocalPlaylist) -> Unit = {},
    onPlayLocalPlaylist: (dev.avsong.data.LocalPlaylist) -> Unit = {},
) {
    val vm = rememberLibraryViewModel()
    val state by vm.state.collectAsState()
    // 仅用于判断登录态:未登录时收藏夹必然拉不到,错误区给「去登录」比干巴巴的「重试」有用
    val loggedIn = rememberLoginViewModel().state.collectAsState().value is LoginUiState.LoggedIn
    // 右上「+」动作菜单(网络 tab):添加合集 / 新建收藏夹
    var plusMenu by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showAddCollection by remember { mutableStateOf(false) }
    // 长按菜单选中的动作 → 确认弹窗(破坏性操作不直接执行)
    var pendingDelete by remember { mutableStateOf<FavoriteRepository.Folder?>(null) }
    // 两级 tab(2026-10-15 用户定版,参考设计图):0=网络歌单(合集+收藏夹) 1=本地歌单;
    // 网络歌单内再按分区标题分组(合集/我创建的/收藏的),内容一律列表行样式
    var tab by remember { mutableStateOf(0) }
    // 本地歌单:新建对话框 / 待删除(确认)/ 待重命名(输入)
    var showCreateLocal by remember { mutableStateOf(false) }
    var pendingDeleteLocal by remember { mutableStateOf<dev.avsong.data.LocalPlaylist?>(null) }
    var pendingRenameLocal by remember { mutableStateOf<dev.avsong.data.LocalPlaylist?>(null) }
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
            // 顶栏:两个大 tab(左对齐文字式)+ 右上「+」(动作按 tab 分发)
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.weight(1f)) {
                        listOf("网络歌单" to 0, "本地歌单" to 1).forEach { (label, i) ->
                            val active = tab == i
                            Text(
                                label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { tab = i }
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                    // 右上「+」:网络=弹动作菜单(添加合集/新建收藏夹),本地=直接新建本地歌单
                    Box {
                        IconButton(onClick = { if (tab == 0) plusMenu = true else showCreateLocal = true }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = if (tab == 0) "添加" else "新建本地歌单",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (plusMenu) {
                            DropdownMenu(expanded = plusMenu, onDismissRequest = { plusMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("添加合集") },
                                    onClick = {
                                        plusMenu = false
                                        vm.clearCollectionError()
                                        showAddCollection = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("新建收藏夹") },
                                    onClick = {
                                        plusMenu = false
                                        showCreate = true
                                    },
                                )
                            }
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                ) {
                    if (tab == 1) {
                        // ---- 本地歌单(tab 自带名字,不再重复标题行;右上 + = 新建) ----
                        if (localPlaylists.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                    Text("还没有本地歌单,点右上 + 新建", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        items(localPlaylists, key = { "local-${it.id}" }) { pl ->
                            PlaylistRow(
                                title = pl.name,
                                subtitle = "${pl.tracks.size} 首",
                                cover = pl.tracks.firstOrNull()?.coverUrl,
                                isCurrent = playingId == "local:${pl.id}",
                                isPlaying = player.isPlaying,
                                onClick = { onOpenLocalPlaylist(pl) },
                                onPlayPause = onPlayPause,
                                menuActions = listOf(
                                    "重命名歌单" to { pendingRenameLocal = pl },
                                    "删除歌单" to { pendingDeleteLocal = pl },
                                ),
                            )
                        }
                    } else {
                        // ---- 网络歌单 ----
                        // 未登录:整页居中登录态(不显示任何分区标题/空态,避免半页空)
                        if (!loggedIn && !state.loading) {
                            item {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "请先登录 B 站账号",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "登录后可查看收藏夹与订阅的合集",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    TextButton(onClick = onLogin) {
                                        Text("去登录", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        } else {
                            // 收藏夹区 = 我创建的 + 收藏的(都是收藏夹,不分来源;「收藏的」多余已合并)
                            val allFolders = state.folders + state.subscribed
                            val hasNet = allCollections.isNotEmpty() || allFolders.isNotEmpty()
                            // 加载中且什么都没有:骨架
                            if (state.loading && !hasNet) {
                                item { SkeletonPlaylistRows(rows = 6) }
                            } else if (state.error != null && !hasNet) {
                                item {
                                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(state.error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = vm::refresh) { Text("重试", color = MaterialTheme.colorScheme.primary) }
                                            if (!loggedIn) {
                                                TextButton(onClick = onLogin) { Text("去登录", color = MaterialTheme.colorScheme.primary) }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // 空态:无任何歌单时,整页提示 + 两个动作入口
                                if (allCollections.isEmpty() && allFolders.isEmpty() && !state.loading) {
                                    item {
                                        Column(
                                            Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                "还没有歌单",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(Modifier.height(14.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                TextButton(onClick = {
                                                    vm.clearCollectionError()
                                                    showAddCollection = true
                                                }) { Text("添加合集", color = MaterialTheme.colorScheme.primary) }
                                                TextButton(onClick = { showCreate = true }) { Text("新建收藏夹", color = MaterialTheme.colorScheme.primary) }
                                            }
                                        }
                                    }
                                }

                                // 合集区:只在有内容/出错时显示(空态已整页处理)
                                if (allCollections.isNotEmpty() || state.collectionError != null) {
                                    item { SectionHeader("合集 (${allCollections.size})") }
                                    if (state.collectionError != null && !showAddCollection) {
                                        item {
                                            Text(
                                                state.collectionError!!,
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                    if (allCollections.isEmpty()) {
                                        item {
                                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                                Text("还没有合集,点标题行 + 添加(粘贴 UP 主合集链接)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    items(allCollections, key = { "col-${it.mid}-${it.seasonId}" }) { collection ->
                                        PlaylistRow(
                                            title = collection.title,
                                            subtitle = "${collection.total} 首",
                                            cover = collection.cover,
                                            isCurrent = playingId == "collection:${collection.seasonId}",
                                            isPlaying = player.isPlaying,
                                            onClick = { onOpenCollection(collection) },
                                            onPlayPause = onPlayPause,
                                            menuActions = listOf("取消订阅" to { pendingUnsub = collection }),
                                        )
                                    }
                                }

                                // 收藏夹区:同规则,空时整块不显示
                                if (allFolders.isNotEmpty()) {
                                    item { SectionHeader("收藏夹 (${allFolders.size})") }
                                    if (state.actionError != null) {
                                        item {
                                            Text(
                                                state.actionError!!,
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                    itemsIndexed(allFolders, key = { _, f -> "fold-${f.id}" }) { i, folder ->
                                        val mine = i < state.folders.size
                                        PlaylistRow(
                                            title = folder.title,
                                            subtitle = "${folder.mediaCount} 首",
                                            cover = folder.cover,
                                            isPrivate = folder.isPrivate,
                                            isCurrent = playingId == "folder:${folder.id}",
                                            isPlaying = player.isPlaying,
                                            onClick = { onOpenFolder(folder) },
                                            onPlayPause = onPlayPause,
                                            menuActions = if (mine) {
                                                listOf("删除收藏夹" to { pendingDelete = folder })
                                            } else {
                                                emptyList()
                                            },
                                        )
                                    }
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

    // ---- 本地自建歌单(2026-10-15)----
    if (showCreateLocal) {
        CreateLocalPlaylistDialog(
            onDismiss = { showCreateLocal = false },
            onCreate = { name ->
                onCreateLocalPlaylist(name)
                showCreateLocal = false
            },
        )
    }
    pendingDeleteLocal?.let { pl ->
        AlertDialog(
            onDismissRequest = { pendingDeleteLocal = null },
            title = { Text("删除歌单?") },
            text = { Text("「${pl.name}」及其中的 ${pl.tracks.size} 首歌会被移除,只删本地歌单,不影响 B 站收藏,操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteLocalPlaylist(pl)
                    pendingDeleteLocal = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteLocal = null }) { Text("取消") } },
        )
    }
    pendingRenameLocal?.let { pl ->
        RenamePlaylistDialog(
            initial = pl.name,
            onDismiss = { pendingRenameLocal = null },
            onConfirm = { name ->
                onRenameLocalPlaylist(pl, name)
                pendingRenameLocal = null
            },
        )
    }
}

/** 分区标题(合集/收藏夹):与顶部 tabs 左边距一致(20dp),不带行尾动作(动作收敛到右上 +)。 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp),
    )
}

/** 歌单列表行(2026-10-15 用户定版,参考设计图):64dp 方图 + 标题 + 副标题「N 首」+ 右 chevron。
 * 正在播本歌单时 chevron 位换音波条(播放中)/播放三角(暂停),点击=暂停/续播;
 * 行尾「>」= 进入详情;长按行弹 [menuActions]。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    title: String,
    subtitle: String,
    cover: String?,
    isPrivate: Boolean = false,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    menuActions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (menuActions.isNotEmpty()) menuExpanded = true },
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPrivate) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "私密",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        // 行尾区:chevron/播放态 + 长按菜单的锚点(DropdownMenu 必须锚在行内 Box 上,
        // 游离在函数尾部会弹到屏幕左下角——2026-10-16 用户实测)
        Box {
        if (isCurrent) {
            // 正在播本歌单:chevron 位换播放态控件(音波条=播放中,三角=暂停;点击=暂停/续播)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick = onPlayPause,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isPlaying) {
                    PlayingEqualizer(Modifier.size(width = 18.dp, height = 14.dp))
                } else {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "继续播放",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看歌单详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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


/**
 * 新建本地歌单对话框:只要一个名称(简介/公开开关是 B 站概念,本地不需要)。
 */
@Composable
private fun CreateLocalPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建歌单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("歌单名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 重命名本地歌单对话框(歌单页长按与详情页 ⋮ 菜单共用)。 */
@Composable
internal fun RenamePlaylistDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名歌单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("歌单名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && name.trim() != initial) onConfirm(name.trim()) },
                enabled = name.isNotBlank() && name.trim() != initial,
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
