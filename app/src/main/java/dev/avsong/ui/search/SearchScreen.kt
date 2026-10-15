package dev.avsong.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import dev.avsong.domain.Song
import dev.avsong.ui.MainUiState
import dev.avsong.ui.SearchUiState
import dev.avsong.ui.common.LoadingHint
import dev.avsong.ui.common.SectionHeader
import dev.avsong.ui.common.SkeletonList
import dev.avsong.ui.common.SongList
import dev.avsong.ui.common.StateHint

/**
 * 搜索页(底部 tab「搜索」):胶囊搜索框 + 历史 chips + 结果列表。
 * 顶部黄色导航块已移除,入口统一走 AppBottomBar。
 */
@Composable
fun SearchScreen(
    state: MainUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onLoadMore: () -> Unit = {},
    /** 输入联想词(防抖拉取;点词直接搜)。 */
    suggests: List<String> = emptyList(),
    onSuggestClick: (String) -> Unit = {},
    onHistoryClick: (String) -> Unit = {},
    onHistoryRemove: (String) -> Unit = {},
    onHistoryClear: () -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onFavorite: (Song) -> Unit = {},
    onDownload: (Song) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // 深灰胶囊搜索框 + 联想浮层(Box 使浮层可 Z 轴覆盖下方内容)
        Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索歌曲 / MV / 歌手", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onSearch) {
                Text(
                    "搜索",
                    color = if (state.query.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        }


        // 联想浮层:Popup 独立窗口,天然悬浮于一切内容之上(不占布局、不推走历史/结果);
        // 挂在搜索框正下方。仅 Idle 态显示——点搜索/键盘搜索后进入结果,浮层随之消失。
        if (state.search == SearchUiState.Idle && state.query.isNotBlank() && suggests.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopStart,
                offset = with(LocalDensity.current) {
                    IntOffset(16.dp.roundToPx(), 84.dp.roundToPx())
                },
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 500.dp)
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    suggests.forEach { term ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestClick(term) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                term,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        when (val search = state.search) {
            is SearchUiState.Result -> {
                if (search.songs.isEmpty()) {
                    StateHint(Icons.Rounded.MusicOff, "没有找到「${state.query}」", "换个关键词试试")
                } else {
                    // BV/av 直达 = 单曲结果,筛选与播放全部都无意义
                    val isDirectHit = search.songs.size == 1 && !search.hasMore
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("搜索结果", style = MaterialTheme.typography.titleMedium)
                            Text(
                                // B 站 numResults 封顶 1000 无真实总数;没到底显示已加载数,到底才说「共」
                                if (search.hasMore) "已加载 ${search.songs.size} 首" else "共 ${search.songs.size} 首",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        // 点行=插队即播;「播放全部」才整队替换,两种意图各有入口
                        if (!isDirectHit) {
                            TextButton(onClick = { onPlay(search.songs, 0) }) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("播放全部")
                            }
                        }
                    }
                    SongList(
                        songs = search.songs,
                        onPlay = onPlay,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onFavorite = onFavorite,
                        onDownload = onDownload,
                        showPlayButton = false,
                        currentMediaId = state.player.currentMediaId,
                        isPlaying = state.player.isPlaying,
                        hasMore = search.hasMore,
                        loadingMore = search.loadingMore,
                        onLoadMore = onLoadMore,
                    )
                }
            }

            is SearchUiState.Loading -> {
                // 换词搜索:旧结果原地保留(头部小字提示加载中),不闪白;首搜无旧结果才全屏转圈
                val prev = search.previous
                if (prev != null && prev.songs.isNotEmpty()) {
                    Text(
                        "正在搜索「${state.query}」…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                    SongList(
                        songs = prev.songs,
                        onPlay = onPlay,
                        showPlayButton = false,
                    )
                } else {
                    // 首搜无旧结果可保留:骨架行占位(与 SongRow 同构,数据到达不跳版)
                    SkeletonList(rows = 10)
                }
            }
            is SearchUiState.Error -> StateHint(Icons.Rounded.MusicOff, search.message, "检查网络后重试,或换个词")

            // Idle:搜索历史 + 提示
            SearchUiState.Idle -> {
                if (state.searchHistory.isNotEmpty()) {
                    HistoryChips(
                        history = state.searchHistory,
                        onClick = onHistoryClick,
                        onRemove = onHistoryRemove,
                        onClear = onHistoryClear,
                    )
                } else {
                    StateHint(
                        Icons.Filled.Search,
                        "搜点什么吧",
                        "支持歌名 / 歌手 / MV,也可直接粘贴 BV 号或分享链接",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryChips(
    history: List<String>,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    // 清空前模态确认,防误触(与播放队列的清空确认同款,用户要求)
    var showClearConfirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.History,
                null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "搜索历史",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "清空",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { showClearConfirm = true }
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 2.dp),
            )
        }
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("清空搜索历史?") },
                text = {
                    Text("将删除全部 ${history.size} 条历史记录,此操作不可撤销。")
                },
                confirmButton = {
                    TextButton(onClick = {
                        onClear()
                        showClearConfirm = false
                    }) { Text("清空", color = MaterialTheme.colorScheme.primary) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            history.forEach { term ->
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onClick(term) }
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        term,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "删除",
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp)
                            // 裸 Icon 上的 clickable 涟漪是方形底,视觉很差,关掉;
                            // 与主题色板/底部导航的无涟漪约定一致
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onRemove(term) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
