package dev.avsong.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.viewinterop.AndroidView
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
    /** 歌词整体微调(±0.5s,换源弹窗底部校准行)。 */
    onAdjustLyric: (Long) -> Unit = {},
    /** 换源候选列表/应用:LyricRepository 实例(AvSongApp 从 VM 取传入)。 */
    lyricRepository: dev.avsong.data.lyric.LyricRepository? = null,
    /** 热评点赞要 bili_jct;null/空 = 未登录,点赞钮提示去登录。 */
    getCsrf: () -> String? = { null },
    /** 相似歌曲电台:以当前曲拉相关推荐整队替换起播(空=当前未在播放,不渲染)。 */
    onSimilarSongs: (() -> Unit)? = null,
    /** MV 画面开/关:service 侧原位替换当前曲源;结果(true=已开 MV)经 onResult 回传驱动本页画面层。 */
    onToggleVideo: ((onResult: (Boolean) -> Unit) -> Unit)? = null,
    /** 视频 surface 变更(挂画面传 Surface,摘画面传 null;MediaController 透传到服务端渲染)。 */
    onVideoSurfaceChanged: (android.view.Surface?) -> Unit = {},
) {
    var position by remember(state.currentMediaId, state.isPlaying) { mutableLongStateOf(state.positionMs) }
    var showLyric by remember { mutableStateOf(false) }
    // MV 画面态 = service 事实源(PlayerState.isVideo 从当前 item 的 metadata extras 回读)。
    // ⚠ 不能本地 remember:service 侧「移除+插回」会让 currentMediaId 瞬时变化把本地态重置,
    // 出现「再点关不掉 / 勾选态和画面不同步」(实测踩过)
    val mvActive = state.isVideo
    // MV 切源/缓冲中:视频轨还没配置(videoWidth=0)或正在 prepare —— 画面层显示 loading,
    // 否则切 MV 后那 1~2s 就是一整块黑框(用户反馈)
    val mvLoading = state.isVideo && (state.videoWidth <= 0 || state.isBuffering)
    // MV 全屏:点画面层右下角全屏键进入(锁横屏 + 隐藏系统栏),返回键/右上角 × 退出。
    // 关 MV 或切歌自动退出 —— 否则回到纯音频还停在横屏黑屏
    var mvFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(mvActive, state.currentMediaId) {
        if (!mvActive) mvFullscreen = false
    }
    // 系统栏与屏幕方向:进全屏→隐藏栏+锁横屏;退出/面板销毁→还原(面板是可销毁的覆盖层,onDispose 必须兜底)
    val rootView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(mvFullscreen) {
        val activity = rootView.context.findActivity()
        val window = activity?.window
        val bars = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, rootView) }
        if (mvFullscreen) {
            bars?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            bars?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // 退出后显式回竖屏:本 app 只做竖屏,推 UNSPECIFIED 在「自动旋转关闭」的设备上会永远停在横屏
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    // 全屏时返回键先退全屏(再按一次才收起播放面板)
    androidx.activity.compose.BackHandler(enabled = mvFullscreen) { mvFullscreen = false }
    // 歌词换源:候选抽屉(点 ⋮ 菜单「换歌词源」时拉三源候选,拉一次缓存到关闭)
    var showLyricPicker by remember { mutableStateOf(false) }
    // 歌词时间轴校准:独立抽屉(菜单「歌词时间调整」进入)
    var showLyricTimeSheet by remember { mutableStateOf(false) }
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
        // 下一首封面预热:切歌前就把它拉进 Coil 内存/磁盘缓存,切过去时直接命中。
        // ⚠ 目标取自 PlayerState.nextArtworkUri(ExoPlayer 的 nextMediaItemIndex),
        // 它已算过 shuffle/repeat —— 随机播放时 currentIndex+1 是错的。
        // 用 1dp 不可见 AsyncImage 预热:不引额外 Coil API,写的就是同一份缓存。
        state.nextArtworkUri?.let { next ->
            AsyncImage(
                model = next,
                contentDescription = null,
                modifier = Modifier.size(1.dp).alpha(0f),
            )
        }

        // 背景层:全幅封面 + 纯色黑纱(半透明实色,非渐变)。
        // ⚠ MV 态整个跳过:封面图会盖住 SurfaceView 的视频画面(SurfaceView 在视图树后层),
        // 只留浅纱提亮,视频条上下露出黑底(16:9 视频在竖屏本就上下留边)。
        // 封面态/歌词态共用背景 —— 两种状态只差在中间那张进度卡上,背景不换(用户要求);
        // 但歌词态黑纱加深(0.55→0.78),亮封面(录音棚/暖色实拍)会把白字歌词吃掉看不清(用户实测)
        //
        // 切歌时新封面要重新下载,直接换 URL 会闪一下空底(用户报的「切歌像没加载完」)。
        // 做法与网易云等播放器一致:底层垫上一张已加载成功的封面,顶层加载当前曲封面,
        // 加载完成后再淡入盖上去 → 全程无空帧、无生硬跳变。
        // ⚠ 顶层 model 必须恒为当前曲的 artworkUri,不能拿旧 URL 当 model(那样新图永远不会被请求)。
        var lastLoaded by remember { mutableStateOf<String?>(null) }
        val target = state.artworkUri
        // 顶层透明度:0=还是旧图,1=新图已就位
        val fade = animateFloatAsState(
            targetValue = if (target == null || target == lastLoaded) 1f else 0f,
            animationSpec = tween(durationMillis = 320),
            label = "artworkFade",
        )
        // 歌词态+MV 态:视频条不渲染,但没有封面垫底,直接深纱(歌词态 0.78 是实测的可读值)
        if (!mvActive) {
            Box(Modifier.fillMaxSize()) {
                if (target != null && lastLoaded != null && lastLoaded != target) {
                    AsyncImage(
                        model = lastLoaded,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (target != null) {
                    AsyncImage(
                        model = target,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(fade.value),
                        onSuccess = { lastLoaded = target },
                        // 加载失败也推进:否则 lastLoaded 永远停在上一首,背景会一直卡着旧图
                        onError = { lastLoaded = target },
                    )
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (showLyric) 0.78f else 0.55f)))
            }
        } else if (!showLyric) {
            // MV 封面态:浅纱(不遮画面,卡片文字仍可读);视频条外区域自然露黑底
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        } else {
            // MV 歌词态:视频条与歌词在 Column 内同屏(视频无纱/歌词区自带深纱),
            // 全屏浅纱统一提亮;无封面垫底
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }

        // MV 画面层:MV 态时 SurfaceView 以 16:9 视频条居中(叠在浅纱上、卡片之下)。
        // Surface 挂载双保险:
        // ① holder callback(destroyed 必须摘,后台不解码);
        // ② LaunchedEffect 跟随播放状态重发 surface:service 端 MV 模式切换会 replaceMediaItem
        //    重建渲染器,新 renderer 在 READY 前后需要重新拿到 surface,而视图没销毁时
        //    holder 不会再回调 created。setVideoSurface 幂等,重发无害;key 变化即重发
        var surfaceHolder by remember { mutableStateOf<android.view.SurfaceHolder?>(null) }
        LaunchedEffect(mvActive, state.currentMediaId, state.isBuffering, state.isPlaying) {
            surfaceHolder?.surface?.let { onVideoSurfaceChanged(it) }
        }
        DisposableEffect(mvActive) {
            onDispose { onVideoSurfaceChanged(null) }
        }
        // 封面态的视频条(歌词态在 Column 内渲染自己的视频条,与歌词同屏)
        if (mvActive && !showLyric && !mvFullscreen) {
            // 视频条要完整露出来:居中于「顶栏与卡片之间」的区域(底部预留卡片高度,
            // 实测全屏居中时视频下沿被信息卡压住歌词看不见)。
            // 比例用 ExoPlayer 上报的真实 VideoSize(跨 binder 同步到 controller):
            // 固定 16:9 会把非 16:9 源(宽银幕 MV/竖裁素材)压扁(实测);无信号回退 16:9
            val vw = state.videoWidth
            val vh = state.videoHeight
            val ratio = if (vw > 0 && vh > 0) vw.toFloat() / vh else 16f / 9f
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(bottom = 300.dp),
                contentAlignment = Alignment.Center,
            ) {
                MvVideoSurface(
                    // 宽屏源:按宽定高不超容器;高瘦源:按高定宽
                    modifier = Modifier.then(
                        if (ratio >= maxWidth / maxHeight) {
                            Modifier.fillMaxWidth().aspectRatio(ratio)
                        } else {
                            Modifier.fillMaxHeight().aspectRatio(ratio)
                        },
                    ),
                    loading = mvLoading,
                    onSurfaceCreated = { holder ->
                        surfaceHolder = holder
                        onVideoSurfaceChanged(holder.surface)
                    },
                    onSurfaceReleased = { onVideoSurfaceChanged(null) },
                    onToggleFullscreen = { mvFullscreen = true },
                )
            }
        }

        val cardShape = RoundedCornerShape(28.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            // 顶栏:左收起箭头、右「⋮」更多菜单(MV/电台等次级功能收进来,信息卡不再拥挤)。
            // 图标直接坐在封面上,用白色保证任意封面可读
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
                var showMoreMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Rounded.MoreHoriz,
                            contentDescription = "更多",
                            modifier = Modifier.size(24.dp),
                            tint = if (state.artworkUri != null) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        // MV 画面开关(激活态前勾):service 端原位替换当前曲源
                        if (onToggleVideo != null) {
                            DropdownMenuItem(
                                text = { Text("MV 画面") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.SmartDisplay,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (mvActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingIcon = {
                                    if (mvActive) Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    // 即将从 MV 切回纯音频:摘 surface —— renderer 移除有延迟,
                                    // 不摘的话 SurfaceView 残帧会盖住恢复的封面(实测);延迟 600ms
                                    // 再兜一次(renderer 侧 detach 是异步的,单次摘可能被晚到的
                                    // 重建流程重新占住)
                                    if (mvActive) {
                                        onVideoSurfaceChanged(null)
                                        scope.launch {
                                            delay(600)
                                            onVideoSurfaceChanged(null)
                                        }
                                    }
                                    // 不开本地状态:开关结果由 service 写回 item metadata,
                                    // 经 PlayerState.isVideo 回流(否则会与 service 真态漂移)
                                    onToggleVideo { }
                                },
                            )
                        }
                        // 相似歌曲电台:40 条相关推荐整队替换随机起播
                        if (onSimilarSongs != null) {
                            DropdownMenuItem(
                                text = { Text("相似歌曲电台") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Radio,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onSimilarSongs()
                                },
                            )
                        }
                        // 换歌词源:低频操作收进菜单(原先贴在歌词区右上角,占位且易误触),
                        // 点开仍是抽屉式候选列表(三源 + 手动搜索)
                        if (lyricRepository != null && state.currentMediaId != null) {
                            DropdownMenuItem(
                                text = { Text("换歌词源") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.SwapHoriz,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showLyricPicker = true
                                },
                            )
                            // 歌词时间调整:独立菜单项(±0.5s 整体校准),不再混在换源里
                            DropdownMenuItem(
                                text = { Text("歌词时间调整") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showLyricTimeSheet = true
                                },
                            )
                        }
                    }
                }
            }

            // 歌词态:歌词铺满上半区(坐在封面黑纱上,用白色系文字),信息卡整个隐藏,
            // 点歌词空白处切回封面(歌词行本身的点击仍是 seek);封面态:留白把信息卡推到底部。
            // MV 态同屏:视频条(真实比例)占上半区居中,歌词区在其下方继续滚动 —— 画面歌词一起看
            if (showLyric) {
                if (mvActive && !mvFullscreen) {
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().weight(0.62f),
                        contentAlignment = Alignment.Center,
                    ) {
                        MvVideoSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(
                                    if (state.videoWidth > 0 && state.videoHeight > 0) {
                                        state.videoWidth.toFloat() / state.videoHeight
                                    } else {
                                        16f / 9f
                                    },
                                ),
                            loading = mvLoading,
                            onSurfaceCreated = { holder ->
                                surfaceHolder = holder
                                onVideoSurfaceChanged(holder.surface)
                            },
                            onSurfaceReleased = { onVideoSurfaceChanged(null) },
                            onToggleFullscreen = { mvFullscreen = true },
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (mvActive) Modifier.weight(0.38f) else Modifier.weight(1f)),
                ) {
                    // MV 同屏时歌词区垫一层深纱:白字歌词在视频画面上可读(浅纱会吃掉文字)
                    if (mvActive) {
                        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.72f)))
                    }
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
                            // 走马灯:搬运标题动辄四五十字,截断后歌名根本看不见(用户要求滚动展示)
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
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

        // 换歌词源(抽屉式):⋮ 菜单「换歌词源」进入(低频操作,不再占歌词区常驻位),
        // 三源候选 + 手动搜索,点选即应用并写缓存
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
                // 手动搜索(自动查询词搜不到/搜错了自己来):结果非空时列表整体切到手动结果
                var manualQuery by remember(bvid) { mutableStateOf("") }
                var manualSearching by remember(bvid) { mutableStateOf(false) }
                var manualCandidates by remember(bvid) { mutableStateOf<List<dev.avsong.data.lyric.LyricCandidate>?>(null) }
                var manualError by remember(bvid) { mutableStateOf<String?>(null) }
                fun runManualSearch() {
                    val kw = manualQuery.trim()
                    if (kw.isEmpty() || manualSearching) return
                    manualSearching = true
                    manualError = null
                    scope.launch {
                        try {
                            manualCandidates = lyricRepository?.searchCandidates(kw, state.durationMs / 1000)
                            if (manualCandidates?.isEmpty() == true) manualError = "没搜到「$kw」的歌词"
                        } catch (e: Exception) {
                            manualError = e.message ?: "搜索失败"
                        } finally {
                            manualSearching = false
                        }
                    }
                }
                // 拉候选:进抽屉一次性收集三源(每源内部已限个数,总时长可控)
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
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
                ) {
                    Text(
                        "换歌词源",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    // 手动搜索框:自动查询词是搬运标题时经常搜不对(嵌套书名号/长标题),
                    // 用户自己敲真歌名最直接;键盘 Search 触发,清空回退自动候选
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = manualQuery,
                            onValueChange = {
                                manualQuery = it
                                if (it.isBlank()) {
                                    manualCandidates = null
                                    manualError = null
                                }
                            },
                            placeholder = {
                                Text(
                                    "自己搜:输入歌名(可带歌手)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runManualSearch() }),
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        if (manualSearching) {
                            CircularProgressIndicator(
                                Modifier.size(22.dp).padding(start = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    // 候选列表:超过 380dp 才滚,避免长列表把抽屉顶到全屏
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        when {
                            manualError != null -> Text(
                                manualError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )

                            manualCandidates != null -> Column {
                                manualCandidates!!.forEach { c ->
                                    LyricCandidateRow(c) {
                                        lyricRepository!!.applyCandidate(bvid, c)
                                        onLyricReplaced(c.lines)
                                        showLyricPicker = false
                                    }
                                }
                            }

                            candidates == null && loadError == null -> Box(
                                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }

                            candidates?.isEmpty() == true || (loadError != null && candidates == null) -> Text(
                                loadError ?: "没有找到其他歌词源,试试上面的自己搜",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )

                            else -> Column {
                                candidates!!.forEach { c ->
                                    LyricCandidateRow(c) {
                                        lyricRepository!!.applyCandidate(bvid, c)
                                        onLyricReplaced(c.lines)
                                        showLyricPicker = false
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }

        // 歌词时间调整(抽屉式,菜单「歌词时间调整」进入):整体 ±0.5s 校准、连点叠加,
        // 当前偏移就地回显。与换源分开后,校准时不必再翻候选列表。
        // 抽屉刻意做矮:M3 默认 dragHandle 上下各 22dp 很占高,换紧凑版(仅 14dp);
        // 内容间距同步收紧 —— 只有两个按钮,不该铺半屏
        if (showLyricTimeSheet) {
            val offsetMs = (lyric as? LyricUiState.Ready)?.offsetMs ?: 0L
            ModalBottomSheet(
                onDismissRequest = { showLyricTimeSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                dragHandle = {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        )
                    }
                },
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 18.dp),
                ) {
                    Text(
                        "歌词时间调整",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                    )
                    Text(
                        if (offsetMs == 0L) "当前:未校准"
                        else "当前偏移:${if (offsetMs > 0) "+" else ""}${"%.1f".format(offsetMs / 1000f)}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(-500L, 500L).forEach { delta ->
                            Text(
                                if (delta < 0) "提前 0.5s" else "延后 0.5s",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { onAdjustLyric(delta) }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                    Text(
                        "歌词比唱得快就「延后」,慢就「提前」",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
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

        // MV 全屏画面层(最上层):只留画面,顶栏/卡片/歌词全部被盖住;右上角 × 或返回键退出。
        // 全屏时两个条状视频层都停止组合(见上面的 !mvFullscreen 判断),保证同一时刻只有一个 SurfaceView
        if (mvActive && mvFullscreen) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* 消费点击,避免穿透到下层面板 */ },
                contentAlignment = Alignment.Center,
            ) {
                val vw = state.videoWidth
                val vh = state.videoHeight
                val ratio = if (vw > 0 && vh > 0) vw.toFloat() / vh else 16f / 9f
                MvVideoSurface(
                    // 横屏铺满高度、竖屏铺满宽度,另一维留黑边(contain)
                    modifier = Modifier.then(
                        if (ratio >= maxWidth / maxHeight) {
                            Modifier.fillMaxWidth().aspectRatio(ratio)
                        } else {
                            Modifier.fillMaxHeight().aspectRatio(ratio)
                        },
                    ),
                    loading = mvLoading,
                    onSurfaceCreated = { holder ->
                        surfaceHolder = holder
                        onVideoSurfaceChanged(holder.surface)
                    },
                    onSurfaceReleased = { onVideoSurfaceChanged(null) },
                )
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "退出全屏",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { mvFullscreen = false },
                )
            }
        }
    }
}

/**
 * MV 画面层:SurfaceView + 切源 loading 占位 + 全屏键。
 * Surface 生命周期仍由调用方上报 service(created 挂 / destroyed 摘);全屏键为 null 时不渲染。
 */
@Composable
private fun MvVideoSurface(
    modifier: Modifier,
    loading: Boolean,
    onSurfaceCreated: (android.view.SurfaceHolder) -> Unit,
    onSurfaceReleased: () -> Unit,
    onToggleFullscreen: (() -> Unit)? = null,
) {
    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                android.view.SurfaceView(ctx).apply {
                    holder.addCallback(object : android.view.SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                            onSurfaceCreated(holder)
                        }

                        override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}

                        override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                            onSurfaceReleased()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (loading) MvLoadingOverlay()
        if (onToggleFullscreen != null) {
            // 全屏键:视频画面明暗不定,垫一层半透黑圆保证白色图标可读(纯色,不违反禁阴影/边框约定)
            Icon(
                Icons.Rounded.Fullscreen,
                contentDescription = "全屏",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onToggleFullscreen() }
                    .padding(6.dp),
            )
        }
    }
}

/**
 * MV 画面占位:切源/缓冲期间盖在视频区域上的黑纱 + 转圈 + 文案。
 * 视频首帧出来前那块区域是黑的,不提示会被当成「点了没反应」。
 */
@Composable
private fun MvLoadingOverlay() {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "MV 加载中…",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * 换源候选行(自动候选与手动搜索结果共用):来源+行数、标题/歌手、时长偏差角标。
 */
@Composable
private fun LyricCandidateRow(c: dev.avsong.data.lyric.LyricCandidate, onApply: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onApply)
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
    /** 拖动中上报 fraction(松手/取消报 null):歌词区联动显示预览指示线。 */
    onDragChange: ((Float?) -> Unit)? = null,
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
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onDragChange?.invoke(dragFraction)
                        },
                        onDragEnd = {
                            dragFraction?.let(onSeekFraction)
                            dragFraction = null
                            onDragChange?.invoke(null)
                        },
                        onDragCancel = {
                            dragFraction = null
                            onDragChange?.invoke(null)
                        },
                    ) { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onDragChange?.invoke(dragFraction)
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


/** Compose LocalView 的 context 可能包着 ContextThemeWrapper,逐层找 Activity(全屏锁方向要用)。 */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx: android.content.Context = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
