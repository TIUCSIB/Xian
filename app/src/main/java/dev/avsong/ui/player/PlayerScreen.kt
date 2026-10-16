package dev.avsong.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.sin
import dev.avsong.domain.formatClock
import dev.avsong.player.PlayerState
import dev.avsong.ui.LyricUiState
import dev.avsong.ui.library.FavoritePickerDialog
import dev.avsong.ui.common.ThemedNoticeHost
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/**
 * 全屏播放页(参考设计稿):封面铺满整屏 + 半透明黑纱保证可读性,
 * 底部叠两张深灰圆角卡片(信息卡 + 控制卡),播放键为柠檬黄实心圆。
 * 纯色扁平:无阴影、无渐变、无光晕。进度用本地 ticker 每秒采样。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    state: PlayerState,
    lyric: LyricUiState,
    favoriteRepository: dev.avsong.data.FavoriteRepository,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    /** 收藏弹窗的本地歌单区(2026-10-15 加):null=不渲染该区。 */
    localPlaylists: List<dev.avsong.data.LocalPlaylist>? = null,
    /** 勾选/取消本地歌单(checked=当前是否已在);当前曲快照由本页从 state 组装。 */
    onToggleLocal: ((dev.avsong.data.LocalPlaylist, Boolean) -> Unit)? = null,
    /** 本地歌单列表 StateFlow(爱心「或本地歌单」判定用;与 localPlaylists 同源)。 */
    localPlaylistsState: kotlinx.coroutines.flow.StateFlow<List<dev.avsong.data.LocalPlaylist>>? = null,
    /** 亮爱心点击=一键取消收藏:B 站全部已收藏夹 + 所有本地歌单全移出;null=点击仍弹管理弹窗。 */
    onUnfavoriteAll: (() -> Unit)? = null,
    /** 一键取消收藏的 B 站侧结果(bvid→B 站半区是否仍亮);失败时爱心回滚。 */
    unfavResult: Pair<String, Boolean>? = null,
    /** 校正结果已展示,消费置空。 */
    onConsumeUnfavResult: () -> Unit = {},
    /** 本地歌单区就地新建。 */
    onCreateLocal: ((String) -> Unit)? = null,
    /** 热评展示:danmakuEnabled 开关 + danmakuMode(0=弹幕,1=列表抽屉);数据由本页按需拉取。 */
    danmakuEnabled: Boolean = true,
    danmakuMode: Int = 0,
    positionProvider: () -> Long = { state.positionMs },
    /** 换源应用后回调:把新歌词写回 VM 的 LyricUiState(Repository 缓存已更新)。 */
    onLyricReplaced: (List<dev.avsong.data.lyric.LyricLine>) -> Unit = {},
    /** 换源候选列表/应用:LyricRepository 实例(AvSongApp 从 VM 取传入)。 */
    lyricRepository: dev.avsong.data.lyric.LyricRepository? = null,
    /** 热评点赞要 bili_jct;null/空 = 未登录,点赞钮提示去登录。 */
    getCsrf: () -> String? = { null },
) {
    var position by remember(state.currentMediaId, state.isPlaying) { mutableLongStateOf(state.positionMs) }
    var showLyric by remember { mutableStateOf(false) }
    // 歌词换源:候选弹窗开合与数据(点「换源」时才拉三源候选,拉一次缓存到关闭)
    var showLyricPicker by remember { mutableStateOf(false) }
    // 热评数据:点热评键时按需拉一次(进页不自动拉不自动飘,用户要求)。
    // 固定首页 20 条,不翻页(实测后续页混入大量 0 赞低质评论,「热评」语义不成立,用户拍板)。
    var hotReplies by remember { mutableStateOf<List<dev.avsong.data.ReplyRepository.HotReply>>(emptyList()) }
    var repliesLoaded by remember { mutableStateOf(false) }
    var repliesLoading by remember { mutableStateOf(false) }
    var replyError by remember { mutableStateOf<String?>(null) }
    // 拉取时的 aid(评论点赞的 oid);点赞是登录态操作,csrf 由调用方注入
    var repliesAid by remember { mutableStateOf(0L) }
    // 列表抽屉模式(mode=1)的开合
    var showReplySheet by remember { mutableStateOf(false) }
    // 弹幕模式(mode=0)的开合:点热评键开/再点关;两模式都是点击触发,不再自动显示
    var showDanmaku by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 拉一次(20 条);失败置 replyError 供抽屉显示「点按重试」
    fun loadReplies() {
        val bvid = state.currentMediaId ?: return
        if (repliesLoading) return
        repliesLoading = true
        replyError = null
        scope.launch {
            val song = dev.avsong.domain.Song(
                bvid = bvid,
                title = state.title.orEmpty(),
                artist = state.artist.orEmpty(),
                coverUrl = state.artworkUri,
            )
            try {
                repliesAid = if (song.aid > 0) song.aid
                else runCatching { favoriteRepository.fetchAid(song.bvid) }.getOrDefault(0L)
                hotReplies = dev.avsong.data.ReplyRepository.hotRepliesFor(song) {
                    favoriteRepository.fetchAid(it)
                }
                if (hotReplies.isEmpty()) replyError = "暂无热评"
            } catch (e: Exception) {
                replyError = "加载失败:${e.message}"
            } finally {
                repliesLoaded = true
                repliesLoading = false
            }
        }
    }

    /** 热评点赞/取消:乐观翻转本地态,请求失败回滚并经 replyError 提示。 */
    fun toggleReplyLike(reply: dev.avsong.data.ReplyRepository.HotReply) {
        val csrf = getCsrf()
        if (csrf.isNullOrBlank()) {
            replyError = "点赞需要先登录"
            return
        }
        val liked = reply.action == 1
        val idx = hotReplies.indexOfFirst { it.rpid == reply.rpid }
        if (idx < 0) return
        // 乐观更新
        hotReplies = hotReplies.toMutableList().apply {
            set(idx, reply.copy(action = if (liked) 0 else 1, likeCount = reply.likeCount + if (liked) -1 else 1))
        }
        scope.launch {
            try {
                dev.avsong.data.ReplyRepository.likeReply(repliesAid, reply.rpid, !liked, csrf)
            } catch (e: Exception) {
                // 回滚
                hotReplies = hotReplies.toMutableList().apply {
                    set(idx, reply)
                }
                replyError = "点赞失败:${e.message}"
            }
        }
    }

    // 首次加载入口(点热评键/开弹幕时):没拉过才发请求
    fun ensureReplies() {
        if (!repliesLoaded && !repliesLoading) loadReplies()
    }
    // 失败后的重试:清状态再走一次
    fun retryReplies() {
        repliesLoaded = false
        replyError = null
        loadReplies()
    }

    // 换曲清数据;若弹幕开着则自动重拉(用户开着的弹幕换曲不消失,后续曲也持续有弹幕)
    LaunchedEffect(state.currentMediaId) {
        hotReplies = emptyList()
        repliesLoaded = false
        repliesLoading = false
        replyError = null
        if (showDanmaku) loadReplies()
    }
    // 收藏:爱心回显 + 弹窗开关(声明在函数级,信息卡爱心与弹窗共用)。
    // 爱心 = B 站收藏夹之一有它 **或** 在任一本地歌单(弹窗是双区,只看 B 站一半会漏)
    var favCheckedBili by remember(state.currentMediaId) { mutableStateOf(false) }
    // 已收藏进哪几个 B 站夹(id):亮爱心点击=一键取消收藏,从这里拿 delIds
    var favFolderIds by remember(state.currentMediaId) { mutableStateOf<List<Long>>(emptyList()) }
    var favAid by remember(state.currentMediaId) { mutableStateOf(0L) }
    var showFavPicker by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentMediaId) {
        val bvid = state.currentMediaId ?: return@LaunchedEffect
        val aid = runCatching { favoriteRepository.fetchAid(bvid) }.getOrDefault(0L)
        favAid = aid
        if (aid > 0) {
            val ids = runCatching { favoriteRepository.checkFavorite(aid) }.getOrDefault(emptyList())
            favFolderIds = ids
            favCheckedBili = ids.isNotEmpty()
        }
    }
    val localPlaylists = localPlaylistsState?.collectAsState()?.value ?: emptyList()
    val favChecked = favCheckedBili || localPlaylists.any { pl -> pl.tracks.any { it.bvid == state.currentMediaId } }
    // 一键取消收藏的 B 站侧结果校正:失败(B 站仍亮)时乐观置灭的爱心回滚
    unfavResult?.let { (bvid, stillBili) ->
        if (bvid == state.currentMediaId && stillBili) favCheckedBili = true
        onConsumeUnfavResult()
    }
    // 浅色/夜间分支用:主题不带 isDark 标记,以 surface 亮度判定(两套方案的 surface 差异极大)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // 播放中每秒采样一次进度
    LaunchedEffect(state.currentMediaId, state.isPlaying) {
        while (state.isPlaying) {
            position = positionProvider()
            delay(500)
        }
        position = positionProvider()
    }

    // seek 必须同步刷新本地 position:暂停态下上面的 ticker 不跑,只发 onSeek 的话
    // 播放器位置变了但 UI 还显示旧采样,松手"跳回原位"(用户报的 bug);恢复播放后才见新位置
    fun seekAndShow(ms: Long) {
        position = ms
        onSeek(ms)
    }

    // 根节点消费点击:面板是覆盖层,不拦截的话点击会穿透到后面的内容(实测踩过)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        // 背景层:全幅封面 + 纯色黑纱(半透明实色,非渐变)。
        // 封面态/歌词态共用 —— 两种状态只差在中间那张进度卡上,背景不换(用户要求);
        // 但歌词态黑纱加深(0.55→0.78),亮封面(录音棚/暖色实拍)会把白字歌词吃掉看不清(用户实测)
        if (state.artworkUri != null) {
            AsyncImage(
                model = state.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (showLyric) 0.78f else 0.55f)))
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }

        val cardShape = RoundedCornerShape(28.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            // 顶栏:收起箭头(裸图标)。图标直接坐在封面上,用白色+淡黑描底保证任意封面可读
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCollapse, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "收起",
                        modifier = Modifier.size(28.dp),
                        // 坐在封面黑纱上用白色;无封面时退回主题底,改用 onSurface
                        tint = if (state.artworkUri != null) Color.White
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            // 歌词态:歌词铺满上半区(坐在封面黑纱上,用白色系文字),信息卡整个隐藏,
            // 点歌词空白处切回封面(歌词行本身的点击仍是 seek);封面态:留白把信息卡推到底部。
            if (showLyric) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    LyricView(
                        lyric = lyric,
                        positionMs = position,
                        onSeek = ::seekAndShow,
                        onArtwork = state.artworkUri != null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showLyric = false },
                    )
                    // 换源入口:歌词态右上角小图标(比文字钮轻,不抢歌词;点开候选弹窗)
                    Icon(
                        Icons.Rounded.SwapHoriz,
                        contentDescription = "换歌词源",
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 20.dp)
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showLyricPicker = true },
                    )
                }
            } else {
            // 热评弹幕层:点热评键才显示(进页不自动飘)。占满整个上层区——封面态原本
            // Spacer 与弹幕层各 weight 一半,上部全空着(用户反馈),改为弹幕开着时不放 Spacer。
            // 横向破出 Column 的 16dp 内边距(左右各扩 16dp):弹幕必须贴真正屏幕边缘游入游出,
            // 否则裁剪边界落在半路,字符被「切」在离屏边 16dp 处(用户实测突兀)。
            // 不消费点击 → 封面点击切歌词/歌词点击 seek 的手势不受影响;歌词态不显示(沉浸阅读)
            if (showDanmaku && hotReplies.isNotEmpty()) {
                ReplyDanmaku(
                    replies = hotReplies,
                    onLikeReply = ::toggleReplyLike,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .layout { measurable, constraints ->
                            val extra = 16.dp.roundToPx()
                            val p = measurable.measure(
                                constraints.copy(minWidth = constraints.minWidth + extra * 2, maxWidth = constraints.maxWidth + extra * 2)
                            )
                            layout(p.width, p.height) { p.place(-extra, 0) }
                        },
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            // 信息卡:半透明纯色卡(卡内不再各铺一份模糊封面——两张卡各裁一块封面,
            // 用户反馈「背景是单独的」;去掉后统一透出面板的全幅封面底);歌词态不显示
            if (!showLyric) {
            Box(modifier = Modifier.fillMaxWidth().clip(cardShape)) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (dark) 0.85f else 0.92f)),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showLyric = !showLyric }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.title ?: "未在播放",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = state.artist ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 收藏键(信息卡):**亮爱心点击=一键取消收藏**(B 站全夹+本地歌单全移出,
                    // 用户实测「取消收藏还弹收藏界面不合逻辑」);灭=弹「收藏到」双区弹窗。
                    // 长按 = 无论状态都弹管理弹窗(只想撤个别夹的人走这里)。
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .combinedClickable(
                                // indication=null:默认方形涟漪在小控件上很难看(全局约定裸点无涟漪)
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                onClick = {
                                    if (favChecked) {
                                        // 乐观更新:爱心先灭,失败由 unfavoriteEverywhere 的结果回写
                                        favCheckedBili = false
                                        favFolderIds = emptyList()
                                        onUnfavoriteAll?.invoke()
                                    } else {
                                        showFavPicker = true
                                    }
                                },
                                onLongClick = { showFavPicker = true },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (favChecked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (favChecked) "已收藏,点按管理收藏夹" else "收藏到收藏夹",
                            tint = if (favChecked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 热评键(两种模式统一,均点击触发不自动显示):
                    // 弹幕模式=开/关弹幕(激活 primary 高亮);抽屉模式=开热评列表抽屉。
                    // 首次点击按需拉数据(ensureReplies),没拉过才发请求
                    if (danmakuEnabled && !showLyric) {
                        IconButton(
                            onClick = {
                                ensureReplies()
                                if (danmakuMode == 0) showDanmaku = !showDanmaku else showReplySheet = true
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Chat,
                                contentDescription = if (danmakuMode == 0) "热评弹幕" else "查看热评",
                                tint = when {
                                    danmakuMode == 0 && showDanmaku -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }

                }
                Spacer(Modifier.height(14.dp))
                // 波形进度条常驻信息卡(歌词态也要有进度);歌词本体已移到上半区
                val dur = if (state.durationMs > 0) state.durationMs.toFloat() else 1f
                WaveformSeekBar(
                    progress = (position.toFloat() / dur).coerceIn(0f, 1f),
                    onSeekFraction = { frac -> seekAndShow((frac * dur).roundToLong()) },
                    durationMs = state.durationMs,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        position.formatClock(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        state.durationMs.formatClock(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
            }
            } // 外层 else(showLyric==false 分支)收尾

            Spacer(Modifier.height(12.dp))

            // 控制卡:半透明纯色卡(同信息卡,卡内不再各铺模糊封面) / 模式 | 上一首 | 播放(黄) | 下一首 | 队列
            Box(modifier = Modifier.fillMaxWidth().clip(cardShape)) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (dark) 0.85f else 0.92f)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 裸图标统一白色(无圆底后不注入会继承到黑色)
                CompositionLocalProvider(LocalContentColor provides if (dark) Color.White else MaterialTheme.colorScheme.onSurface) {
                // 播放模式键:单击循环切换(列表循环→单曲循环→随机→顺序),图标随模式变
                IconButton(onClick = onCycleMode, modifier = Modifier.size(44.dp)) {
                    val (modeIcon, modeDesc) = when {
                        state.shuffleEnabled -> Icons.Rounded.Shuffle to "随机播放"
                        state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE ->
                            Icons.Rounded.RepeatOne to "单曲循环"
                        else -> Icons.Rounded.Repeat to "列表循环"
                    }
                    Icon(
                        modeIcon,
                        contentDescription = modeDesc,
                        modifier = Modifier.size(24.dp),
                        tint = if (state.shuffleEnabled ||
                            state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(26.dp),
                    )
                }
                // 播放/暂停:柠檬黄实心圆(onPrimary=近黑图标)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onPlayPause, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(26.dp),
                    )
                }
                    IconButton(onClick = onQueue, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = "播放队列",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // 热评列表抽屉(mode=1):3/4 屏(用户指定),用户名+内容+点赞,可滚动
        if (showReplySheet) {
            ModalBottomSheet(
                onDismissRequest = { showReplySheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
            ) {
                // 高度定在 3/4 屏(fillMaxHeight 直接量内容列;M3 sheet 容器 wrap 内容,
                // modifier 上的 fillMaxHeight 不生效——用户实测「抽屉矮、下面空白」)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f),
                ) {
                    Text(
                        "热评",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp),
                    )
                    when {
                        // 首页失败/真没评论:给明确文案;失败带「点按重试」(点行重发首页请求)
                        hotReplies.isEmpty() && replyError != null -> Box(
                            Modifier.fillMaxWidth().clickable { retryReplies() }.padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    replyError!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (replyError!!.startsWith("加载失败")) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "点按重试",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        hotReplies.isEmpty() && repliesLoading -> Box(
                            Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        }

                        else -> {
                            // 固定 20 条(用户拍板不翻页):后续页混入大量 0 赞低质评论,「热评」语义不成立
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                items(hotReplies) { r ->
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
                                        Text(
                                            r.uname,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            r.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // 点赞钮:已赞 primary 高亮;未登录点按提示去登录
                                            Icon(
                                                Icons.Filled.ThumbUp,
                                                contentDescription = if (r.action == 1) "取消点赞" else "点赞",
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null,
                                                    ) { toggleReplyLike(r) },
                                                tint = if (r.action == 1) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "${r.likeCount}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (r.action == 1) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                // 尾部:固定 20 条到此为止(不翻页);失败才显示重试
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (replyError != null) {
                                            Text(
                                                "${replyError} · 点按重试",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.clickable { retryReplies() },
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

        // 歌词换源候选弹窗:列三源候选(网易云/lrclib/B站字幕),点选即应用并写缓存
        if (showLyricPicker) {
            val bvid = state.currentMediaId ?: ""
            ModalBottomSheet(
                onDismissRequest = { showLyricPicker = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
            ) {
                var candidates by remember(bvid) {
                    mutableStateOf<List<dev.avsong.data.lyric.LyricCandidate>?>(null)
                }
                var loadError by remember(bvid) { mutableStateOf<String?>(null) }
                // 拉候选:进弹窗一次性收集三源(每源内部已限个数,总时长可控)
                LaunchedEffect(bvid) {
                    if (candidates == null && bvid.isNotBlank()) {
                        try {
                            candidates = lyricRepository!!.listCandidates(
                                bvid = bvid,
                                title = state.title.orEmpty(),
                                artist = state.artist.orEmpty(),
                                durationSec = state.durationMs / 1000,
                            )
                        } catch (e: Exception) {
                            loadError = e.message ?: "加载失败"
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
                ) {
                    Text(
                        "换歌词源",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    when {
                        candidates == null && loadError == null -> Box(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }

                        candidates?.isEmpty() == true || (loadError != null && candidates == null) -> Text(
                            loadError ?: "没有找到其他歌词源",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )

                        else -> Column {
                            candidates!!.forEach { c ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            lyricRepository!!.applyCandidate(bvid, c)
                                            onLyricReplaced(c.lines)
                                            showLyricPicker = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "${c.source} · ${c.lines.size} 行",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            c.displayTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (c.durationDeltaSec != 0L) {
                                        Text(
                                            (if (c.durationDeltaSec > 0) "+" else "") + "${c.durationDeltaSec}s",
                                            style = MaterialTheme.typography.labelSmall,
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

        // 收藏夹选择弹窗(从 AvSongApp 挪入:爱心状态要在这里同步)
        if (showFavPicker) {
            val bvid = state.currentMediaId ?: ""
            if (bvid.isNotBlank()) {
                // 弹窗结果经局部提示条反馈(播放页是组合内 overlay,AvSongApp 的全局 Host 也盖得住,
                // 但消息源在 VM 流里,这里就地挂一个最直接)
                var favNotice by remember { mutableStateOf<String?>(null) }
                ThemedNoticeHost(message = favNotice, onConsumed = { favNotice = null })
                FavoritePickerDialog(
                    favRepo = favoriteRepository,
                    bvid = bvid,
                    onDismissWithState = { favCheckedBili = it }, // 回传的是 B 站夹勾选;本地歌单半区由 StateFlow 派生
                    onResult = { favNotice = it },
                    localPlaylists = localPlaylists,
                    onToggleLocal = onToggleLocal?.let { toggle ->
                        { pl, checked ->
                            // 本地歌单要歌曲快照:当前曲的 PlayerState 字段拼一份(爱心只作用于当前曲)
                            toggle(pl, checked)
                        }
                    },
                    onCreateLocal = onCreateLocal,
                    onDismiss = { showFavPicker = false },
                )
            }
        }
    }
}

/**
 * 音波进度条:竖条波形,已播黄条 / 未播灰条;点击与横向拖动均直接 seek。
 * 条高取稳定伪随机序列,视觉上是固定形状的一条音波(与进度无关)。
 * 拖动中在手指上方显示目标时间气泡(长歌拖动不知道拖到哪,用户反馈)。
 */
@Composable
private fun WaveformSeekBar(
    progress: Float,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barCount: Int = 44,
    /** 总时长(ms):拖动气泡换算目标时间用;<=0 时不显示气泡。 */
    durationMs: Long = 0L,
) {
    val bars = remember(barCount) {
        List(barCount) { i -> 0.22f + 0.78f * abs(sin(i * 12.9898f) * 43758.5453f % 1f) }
    }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: progress
    val playedColor = MaterialTheme.colorScheme.primary
    // 未播条用实心灰(用户反馈半透明灰会让卡片显得透明)
    val unplayedColor = MaterialTheme.colorScheme.outline

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onSeekFraction((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFraction?.let(onSeekFraction)
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
    ) {
        val gap = 2.dp.toPx()
        val barW = (size.width - gap * (barCount - 1)) / barCount
        // 全量未播灰条
        bars.forEachIndexed { i, h ->
            drawRoundRect(
                color = unplayedColor,
                topLeft = Offset(x = i * (barW + gap), y = (size.height - size.height * h) / 2),
                size = Size(width = barW, height = size.height * h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
        // 已播黄条:按播放进度像素级裁剪,边界条平滑填充(不整格跳变,用户要求)
        clipRect(right = size.width * fraction.coerceIn(0f, 1f)) {
            bars.forEachIndexed { i, h ->
                drawRoundRect(
                    color = playedColor,
                    topLeft = Offset(x = i * (barW + gap), y = (size.height - size.height * h) / 2),
                    size = Size(width = barW, height = size.height * h),
                    cornerRadius = CornerRadius(barW / 2f),
                )
            }
        }
    }

        // 拖动中的时间气泡:跟随拖点横向位置,显示目标时间(总时长未知则不显示)
        dragFraction?.let { frac ->
            if (durationMs > 0) {
                Text(
                    (frac * durationMs).toLong().formatClock(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(x = ((frac - 0.5f) * 180).dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}
