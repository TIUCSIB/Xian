package dev.avsong.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import dev.avsong.AvSongApp
import dev.avsong.data.lyric.LyricLine
import dev.avsong.data.lyric.LyricRepository
import dev.avsong.domain.NotLoggedInException
import dev.avsong.domain.Song
import dev.avsong.domain.SongRepository
import dev.avsong.data.BiliSongRepository
import dev.avsong.player.PlayerController
import dev.avsong.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import dev.avsong.data.BiliConstants
import dev.avsong.data.toSong
import dev.avsong.data.BiliHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SearchUiState {
    data object Idle : SearchUiState

    /** 加载中;保留上一份结果(换词搜索时旧列表原地挂着不闪白,响应到达后整体替换)。 */
    data class Loading(val previous: Result? = null) : SearchUiState
    data class Result(
        val songs: List<Song>,
        val keyword: String = "",
        val page: Int = 1,
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
    ) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

/** 首页发现源。缓存按源分别保存,切回来不重新请求。 */
enum class HomeSource(val label: String, val title: String, val subtitle: String) {
    RANKING("热歌榜", "B 站热歌榜", "实时飙升"),
    RECOMMEND("推荐", "猜你喜欢", "根据你的口味"),
    POPULAR("热门", "全站热门", "大家都在看"),
    MUSIC_RANK("排行", "音乐区排行", "官方分区榜"),
    HISTORY("最近", "最近播放", "接着上次听"),
}

data class MainUiState(
    val query: String = "",
    val search: SearchUiState = SearchUiState.Idle,
    val searchHistory: List<String> = emptyList(),
    val player: PlayerState = PlayerState(),
    val lyric: LyricUiState = LyricUiState.None,
    // ---- 首页发现区(五个源共用一套状态)----
    val homeSource: HomeSource = HomeSource.RANKING,
    val homeSongs: List<Song> = emptyList(),
    val homeLoading: Boolean = true,
    val homeError: String? = null,
    /** 该源需要登录(当前只有「最近」),UI 显示去登录而不是报错。 */
    val homeNeedsLogin: Boolean = false,
    val homeHasMore: Boolean = false,
    val homeLoadingMore: Boolean = false,
)

/** 歌词状态:按当前歌曲 mediaId 关联,避免切歌瞬间显示旧词。 */
sealed interface LyricUiState {
    data object None : LyricUiState
    data object Loading : LyricUiState
    data class Ready(val mediaId: String, val lines: List<LyricLine>) : LyricUiState
}

class MainViewModel(
    private val repository: SongRepository,
    private val playerController: PlayerController,
    val favoriteRepository: dev.avsong.data.FavoriteRepository,
    private val searchHistoryStore: dev.avsong.data.SearchHistoryStore,
    private val playHistoryStore: dev.avsong.data.PlayHistoryStore,
    val localPlaylistStore: dev.avsong.data.LocalPlaylistStore,
    private val downloadedStore: dev.avsong.data.DownloadedStore,
    private val lyricRepository: LyricRepository = LyricRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    /**
     * 首页各源已加载结果缓存(内存):切 tab 不重复请求;下拉刷新强制重取。
     * ⚠ 必须声明在 init 之前 —— Kotlin 实例初始化按声明顺序执行,init 里调用的
     * loadHome() 会同步读 homeCache,声明在后面会拿到 null(踩过,启动即 NPE 崩溃)。
     */
    private val homeCache = mutableMapOf<HomeSource, List<Song>>()
    private var popularPage = 1
    private var rankPage = 1

    init {
        viewModelScope.launch {
            playerController.playerState().collect { ps ->
                _state.update { it.copy(player = ps) }
                loadLyricFor(ps)
                recordPlayHistory(ps)
            }
        }
        viewModelScope.launch {
            searchHistoryStore.load()
            searchHistoryStore.history.collect { h ->
                _state.update { it.copy(searchHistory = h) }
            }
        }
        viewModelScope.launch { playHistoryStore.load() }
        loadHome()
    }

    private var lastLyricMediaId: String? = null

    /** 播放历史对外只读流;抽屉「历史播放」tab 用。 */
    val playHistory = playHistoryStore.history

    private var lastHistoryMediaId: String? = null

    /** 搜索代次:每次 search() 自增;响应只认可代次相符的(防连点两个词时旧响应后到覆盖新结果)。 */
    private var searchGeneration = 0
    private var searchJob: kotlinx.coroutines.Job? = null

    /**
     * 起播即记历史:当前曲变化时记一次快照(同曲的状态刷新不重复记)。
     * 快照取自 PlayerState —— mediaId=bvid、artworkUri=原始封面 URL,回放时够用。
     */
    private fun recordPlayHistory(ps: PlayerState) {
        val mediaId = ps.currentMediaId ?: return
        if (mediaId == lastHistoryMediaId) return
        lastHistoryMediaId = mediaId
        val title = ps.title.orEmpty()
        if (title.isBlank()) return
        viewModelScope.launch {
            playHistoryStore.record(
                Song(
                    bvid = mediaId,
                    cid = null,
                    aid = 0,
                    title = title,
                    artist = ps.artist.orEmpty(),
                    coverUrl = ps.artworkUri,
                    durationSec = ps.durationMs / 1000,
                )
            )
        }
    }

    private fun loadLyricFor(ps: PlayerState) {
        val mediaId = ps.currentMediaId
        val title = ps.title
        if (mediaId == null || title.isNullOrBlank() || mediaId == lastLyricMediaId) return
        lastLyricMediaId = mediaId
        viewModelScope.launch {
            _state.update { it.copy(lyric = LyricUiState.Loading) }
            // mediaId = bvid;时长优先取播放器真值(列表项可能缺)
            val durSec = playerController.duration().let { if (it > 0) it / 1000 else ps.durationMs / 1000 }
            val lines = lyricRepository.fetch(mediaId, title, ps.artist.orEmpty(), durSec)
            // 拉取期间可能已切歌,校验归属
            if (lastLyricMediaId == mediaId) {
                _state.update {
                    it.copy(
                        lyric = if (lines.isEmpty()) LyricUiState.None
                        else LyricUiState.Ready(mediaId, lines),
                    )
                }
            }
        }
    }

    // ---------- 首页发现区 ----------

    fun selectHomeSource(source: HomeSource) {
        if (_state.value.homeSource == source && homeCache.containsKey(source)) return
        _state.update {
            it.copy(
                homeSource = source,
                homeError = null,
                homeNeedsLogin = false,
                // 切源先清空,避免旧源列表在加载期间短暂串台
                homeSongs = homeCache[source].orEmpty(),
                homeLoading = !homeCache.containsKey(source),
                homeHasMore =
                    (source == HomeSource.POPULAR || source == HomeSource.RANKING) &&
                        homeCache.containsKey(source),
                homeLoadingMore = false,
            )
        }
        if (!homeCache.containsKey(source)) loadHome()
    }

    /** [force] = 下拉刷新:跳过缓存重新请求当前源。 */
    fun loadHome(force: Boolean = false) {
        val source = _state.value.homeSource
        if (!force && homeCache.containsKey(source)) return
        viewModelScope.launch {
            _state.update {
                it.copy(homeLoading = true, homeError = null, homeNeedsLogin = false)
            }
            try {
                val songs = when (source) {
                    HomeSource.RANKING -> {
                        rankPage = 1
                        repository.getRanking()
                    }
                    HomeSource.RECOMMEND -> repository.getRecommend()
                    HomeSource.POPULAR -> {
                        popularPage = 1
                        repository.getPopular(1).items
                    }
                    HomeSource.MUSIC_RANK -> repository.getMusicRanking()
                    HomeSource.HISTORY -> repository.getHistory()
                }
                // 列表用 bvid 作 LazyColumn key,重复 key 会直接崩 —— 统一去重后入状态。
                // 「最近」尤其需要:同一视频看了多个分P会有多条同 bvid 记录。
                .distinctBy { it.bvid }
                homeCache[source] = songs
                _state.update {
                    it.copy(
                        homeSongs = songs,
                        homeLoading = false,
                        homeHasMore =
                            (source == HomeSource.POPULAR || source == HomeSource.RANKING) &&
                                songs.isNotEmpty(),
                    )
                }
            } catch (e: NotLoggedInException) {
                _state.update {
                    it.copy(homeSongs = emptyList(), homeLoading = false, homeNeedsLogin = true)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        homeSongs = emptyList(),
                        homeLoading = false,
                        homeError = e.message ?: "加载失败",
                    )
                }
            }
        }
    }

    /** 上拉加载下一页(「热门」与「热歌榜」支持翻页)。 */
    fun loadMoreHome() {
        val cur = _state.value
        if (cur.homeSource != HomeSource.POPULAR && cur.homeSource != HomeSource.RANKING) return
        if (!cur.homeHasMore || cur.homeLoadingMore || cur.homeLoading) return
        viewModelScope.launch {
            _state.update { it.copy(homeLoadingMore = true) }
            try {
                if (cur.homeSource == HomeSource.RANKING) {
                    // 热歌榜:接口无总量字段,新增为 0(到底/循环)即停
                    val next = repository.getRankingPage(rankPage + 1)
                    val base = _state.value.homeSongs
                    val merged = (base + next).distinctBy { it.bvid }
                    rankPage += 1
                    homeCache[HomeSource.RANKING] = merged
                    _state.update {
                        it.copy(
                            homeSongs = merged,
                            homeHasMore = merged.size > base.size,
                            homeLoadingMore = false,
                        )
                    }
                } else {
                    val next = repository.getPopular(popularPage + 1)
                    // 翻页期间可能已切源/换页,基于最新状态合并
                    val base = _state.value.homeSongs
                    val merged = (base + next.items).distinctBy { it.bvid }
                    popularPage += 1
                    homeCache[HomeSource.POPULAR] = merged
                    _state.update {
                        it.copy(
                            homeSongs = merged,
                            homeHasMore = next.hasMore,
                            homeLoadingMore = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(homeLoadingMore = false) }
            }
        }
    }

    /**
     * 「定位当前播放」:在当前首页列表里找当前曲的行下标(UI 的列表是整榜 drop(3),可直接当行号用)。
     * 目标尚未加载时最多续拉 [maxExtraPages] 页(热歌榜/热门支持翻页,歌曲可能在更后面的页)。
     * 找不到就静默返回 null,不弹提示 —— 按钮本就只在榜内含当前曲时出现,
     * 走到这里多半是实时榜刷新把歌挤出了已加载页,弹 Toast 只会打扰(用户明确要求去掉)。
     * ⚠ 翻页页码必须每轮从 rankPage/popularPage 取:loadMoreHome 在协程里自增,闭包快照会取重复页。
     */
    suspend fun locateInHome(maxExtraPages: Int = 3): Int? {
        val mediaId = _state.value.player.currentMediaId ?: return null
        fun find(): Int = _state.value.homeSongs.indexOfFirst { it.mediaId == mediaId }
        var index = find()
        var loaded = 0
        while (index < 0 && loaded < maxExtraPages) {
            val before = _state.value.homeSongs.size
            val canPage = _state.value.homeHasMore
            if (!canPage) break
            loadMoreHome()
            withTimeoutOrNull(8_000) {
                // 等待本页落库(size 变化);接口失败时 loadingMore 归 false 也算结束,避免空转
                while (_state.value.homeLoadingMore || _state.value.homeSongs.size == before) {
                    delay(50)
                }
            }
            loaded++
            index = find()
        }
        return index.takeIf { it >= 0 }
    }

    fun onQueryChange(query: String) {        _state.update { cur ->
            // 清空输入 = 放弃本次搜索,回到历史页;否则旧结果会一直挂在列表里
            if (query.isBlank()) cur.copy(query = query, search = SearchUiState.Idle)
            else cur.copy(query = query)
        }
        suggestQuery.value = query.trim()
    }

    /** 搜索联想词(输入防抖 250ms 后拉取;Idle/Loading 态不显示)。 */
    private val suggestQuery = MutableStateFlow("")
    val suggests: StateFlow<List<String>> = suggestQuery
        .debounce(250)
        .mapLatest { term ->
            if (term.isEmpty()) emptyList()
            else runCatching { repository.suggest(term) }.getOrDefault(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) {
            searchJob?.cancel()
            _state.update { it.copy(search = SearchUiState.Idle) }
            return
        }
        // 作废在途请求:新代次生效,旧响应即使后到也会被代次比对丢弃
        searchJob?.cancel()
        suggestQuery.value = ""  // 清联想:搜完回到结果页,浮层不该再挂;防回 Idle 时旧词闪现
        val gen = ++searchGeneration
        val prevForLoading = _state.value.search as? SearchUiState.Result
        _state.update { it.copy(search = SearchUiState.Loading(prevForLoading)) }
        searchJob = viewModelScope.launch {
            // 双保险:代次比对(防旧响应覆盖)+ 输入框内容比对(防改词后旧词结果生效)
            fun stale() = gen != searchGeneration || _state.value.query.trim() != q
            try {
                // 1. BV/av/分享链接直达
                repository.resolveDirect(q)?.let { direct ->
                    searchHistoryStore.add(q)
                    if (!stale()) _state.update {
                        it.copy(search = SearchUiState.Result(listOf(direct), keyword = q, hasMore = false))
                    }
                    return@launch
                }
                // 2. 普通搜索第一页
                val result = repository.search(q, 1)
                searchHistoryStore.add(q)
                if (!stale()) _state.update {
                    it.copy(
                        search = SearchUiState.Result(
                            songs = result.items,
                            keyword = q,
                            page = 1,
                            hasMore = result.hasMore,
                        ),
                    )
                }
            } catch (e: Exception) {
                if (!stale()) _state.update { it.copy(search = SearchUiState.Error(e.message ?: "请求失败")) }
            }
        }
    }

    /** 上拉加载下一页,追加到现有结果(沿用当前筛选)。 */
    fun loadMoreSearch() {
        val cur = _state.value.search as? SearchUiState.Result ?: return
        if (!cur.hasMore || cur.loadingMore || cur.keyword.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(search = cur.copy(loadingMore = true)) }
            try {
                val next = repository.search(cur.keyword, cur.page + 1)
                val merged = (cur.songs + next.items).distinctBy { it.bvid }
                _state.update {
                    it.copy(
                        search = cur.copy(
                            songs = merged,
                            page = cur.page + 1,
                            hasMore = next.hasMore,
                            loadingMore = false,
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(search = cur.copy(loadingMore = false)) }
            }
        }
    }


    fun removeHistory(term: String) {
        viewModelScope.launch { searchHistoryStore.remove(term) }
    }

    fun clearHistory() {
        viewModelScope.launch { searchHistoryStore.clear() }
    }

    fun searchFromHistory(term: String) {
        onQueryChange(term)
        search()
    }

    /**
     * 播放:直接整队入列。真实 B 站歌曲用 bili:// URI,
     * ExoPlayer 加载时才经 ResolvingDataSource 现取直链(3h 过期问题天然自愈)。
     */
    fun playQueue(songs: List<Song>, startIndex: Int) {
        // 记录 bvid→aid,收藏操作要用(搜索结果/榜单/收藏夹条目尽量带 aid)
        songs.forEach { if (it.aid > 0) aidCache[it.bvid] = it.aid }
        val items = songs.map { it.toMediaItem() }
        // 「最近播放」点哪首就从哪首的进度续播;其他源 resumePositionMs 恒为 0
        playerController.playQueue(
            items,
            startIndex,
            songs.getOrNull(startIndex)?.resumePositionMs ?: 0L,
        )
    }

    /** bili:// 解析拿不到 aid;收藏前若缺失则经 view 补。 */
    private val aidCache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * 歌单详情页「随机播放」:开随机再整队入列。
     * 单曲循环必须先退出——REPEAT_MODE_ONE 优先级高于 shuffle,否则=当前曲无限循环(见 cyclePlayMode)。
     */
    fun playQueueShuffled(songs: List<Song>) {
        if (_state.value.player.repeatMode == Player.REPEAT_MODE_ONE) {
            playerController.setRepeatMode(Player.REPEAT_MODE_ALL)
        }
        playerController.setShuffle(true)
        playQueue(songs, 0)
    }

    /**
     * 正在播的歌单(卡片右下角音波条/暂停态的判定依据):
     * key = "folder:<id>" / "collection:<seasonId>",anchor = 起播时队列首曲 mediaId。
     * 判定 = 当前队列首项仍是 anchor(补全/切歌不影响;换队列则失效,由调用方覆盖)。
     * 用 StateFlow 而不是普通函数:queue 变化只在 playerState collect 里推进 UI,
     * 普通函数在组合期调用不会因 queue 更新而重组(实测:点了播放,卡片仍显示 ▶)。
     */
    private val playingPlaylistKey = MutableStateFlow<String?>(null)
    private var playingPlaylistAnchor: String? = null

    /** 当前哪个歌单在播(供歌单页卡片高亮);null=没有歌单在播或队列已被替换。 */
    fun playingPlaylistId(player: PlayerState): String? {
        val key = playingPlaylistKey.value ?: return null
        val anchor = playingPlaylistAnchor ?: return null
        val first = player.queue.firstOrNull() ?: return null
        return key.takeIf { first.mediaId == anchor }
    }

    private fun markPlayingPlaylist(key: String, firstSong: Song) {
        playingPlaylistKey.value = key
        playingPlaylistAnchor = firstSong.mediaId
    }

    /**
     * 歌单卡片右下播放钮:直接播整个歌单(第一页整队入列立即起播),
     * 后台继续拉剩余页**逐批追加进队列**(与合集详情页 playWithFill 同一目标:
     * 队列从 20 首一路长到全量)。用户期间切了别的播放/清空队列则中止补全。
     * 反馈走 queueNotice→Toast(成功不提示,迷你条封面变化本身就是反馈)。
     */
    fun playFolderPlaylist(folderId: Long) {
        viewModelScope.launch {
            try {
                val first = favoriteRepository.folderContents(folderId, 1)
                if (first.songs.isEmpty()) {
                    _queueNotice.value = "这个歌单还是空的"
                    return@launch
                }
                markPlayingPlaylist("folder:$folderId", first.songs.first())
                playQueue(first.songs, 0)
                val session = playerController.session
                var songs = first.songs
                var page = 1
                var hasMore = first.hasMore
                var failures = 0
                while (hasMore && playerController.session == session) {
                    val result = try {
                        favoriteRepository.folderContents(folderId, page + 1)
                    } catch (e: Exception) {
                        if (++failures < 3) {
                            delay(1500)
                            continue
                        }
                        break
                    }
                    failures = 0
                    page++
                    hasMore = result.hasMore
                    val fresh = result.songs.filter { new -> songs.none { it.bvid == new.bvid } }
                    if (fresh.isEmpty()) continue
                    songs = songs + fresh
                    playerController.appendSongsToQueue(fresh.map { it.toMediaItem() })
                }
            } catch (e: Exception) {
                _queueNotice.value = "歌单播放失败:${e.message}"
            }
        }
    }

    /** UP 主合集版:同上(第一页起播 + 后台逐页追加进队列,切歌即中止)。 */
    fun playCollectionPlaylist(mid: Long, seasonId: Long) {
        viewModelScope.launch {
            try {
                val first = dev.avsong.data.CollectionRepository.page(mid, seasonId, 1)
                if (first.songs.isEmpty()) {
                    _queueNotice.value = "这个合集还是空的"
                    return@launch
                }
                markPlayingPlaylist("collection:$seasonId", first.songs.first())
                playQueue(first.songs, 0)
                val session = playerController.session
                var songs = first.songs
                var page = 1
                var hasMore = first.hasMore
                var failures = 0
                while (hasMore && playerController.session == session) {
                    val result = try {
                        dev.avsong.data.CollectionRepository.page(mid, seasonId, page + 1)
                    } catch (e: Exception) {
                        if (++failures < 3) {
                            delay(1500)
                            continue
                        }
                        break
                    }
                    failures = 0
                    page++
                    hasMore = result.hasMore
                    val fresh = result.songs.filter { new -> songs.none { it.bvid == new.bvid } }
                    if (fresh.isEmpty()) continue
                    songs = songs + fresh
                    playerController.appendSongsToQueue(fresh.map { it.toMediaItem() })
                }
            } catch (e: Exception) {
                _queueNotice.value = "合集播放失败:${e.message}"
            }
        }
    }

    /**
     * 本地自建歌单(2026-10-15 加):曲目快照全量在内存,无补全循环 ——
     * markPlayingPlaylist + playQueue 两步,歌单卡片三态高亮(音波条/▶)自动生效。
     * 已下载的曲子填 file:// 直链:离线也能播(mediaId 仍是 bvid,收藏/歌词照常)。
     */
    fun playLocalPlaylist(playlistId: Long) {
        val pl = localPlaylistStore.playlists.value.firstOrNull { it.id == playlistId }
        if (pl == null || pl.tracks.isEmpty()) {
            _queueNotice.value = "这个歌单还是空的"
            return
        }
        val downloaded = downloadedStore.state.value.associateBy { it.bvid }
        val songs = pl.tracks.map { t ->
            val track = downloaded[t.bvid]
            val file = track?.let { downloadedStore.fileOf(it) }?.takeIf { it.exists() }
            if (file != null) {
                t.toSong().copy(audioUrl = android.net.Uri.fromFile(file).toString())
            } else {
                t.toSong()
            }
        }
        markPlayingPlaylist("local:$playlistId", songs.first())
        playQueue(songs, 0)
    }

    /** 新建本地歌单(歌单页「+」与存入弹窗的快捷建夹共用)。 */
    fun createLocalPlaylist(name: String) {
        viewModelScope.launch { localPlaylistStore.create(name) }
    }

    fun renameLocalPlaylist(id: Long, name: String) {
        viewModelScope.launch { localPlaylistStore.rename(id, name) }
    }

    fun deleteLocalPlaylist(id: Long) {
        viewModelScope.launch { localPlaylistStore.delete(id) }
    }

    /** 存入歌单(详情页行菜单 → 选歌单弹窗)。已在歌单里经 queueNotice 提示,不静默。 */
    fun saveSongToLocalPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            val added = localPlaylistStore.addSong(playlistId, song)
            if (!added) _queueNotice.value = "已在歌单里,不重复添加"
        }
    }

    fun removeSongFromLocalPlaylist(playlistId: Long, bvid: String) {
        viewModelScope.launch { localPlaylistStore.removeSong(playlistId, bvid) }
    }

    /**
     * 「下一首播放」(列表行长按):插到当前曲后面,不打断播放、不动现有队列。
     * 已在队列 = 移到当前曲后、当前曲本身 = 不动(PlayerController 内判定),提示相应区分。
     * 先设默认提示再下发指令:已连接时回调同步执行覆盖它,未连接时异步覆盖,终值都正确。
     */
    fun playNext(song: Song) {
        if (song.aid > 0) aidCache[song.bvid] = song.aid
        _queueNotice.value = "已插入下一首播放"
        playerController.playNext(song.toMediaItem()) { isCurrent ->
            _queueNotice.value = if (isCurrent) "就是当前这首" else "已在队列,已移到下一首"
        }
    }

    /** 「加入队列」(列表行长按):追加到队尾;已在队列不重复添加(否则连点会堆出一串重复歌)。 */
    fun addToQueue(song: Song) {
        if (song.aid > 0) aidCache[song.bvid] = song.aid
        _queueNotice.value = "已加入队列"
        playerController.addToQueue(song.toMediaItem()) { _queueNotice.value = "已在队列里,不重复添加" }
    }

    /**
     * 「点歌即播,不换队」(搜索结果行点击):插到当前曲后面立即播放,
     * 已在队列则跳播,不打断/不替换现有队列。
     */
    fun playNow(song: Song) {
        if (song.aid > 0) aidCache[song.bvid] = song.aid
        playerController.playNow(song.toMediaItem())
    }

    suspend fun aidFor(bvid: String): Long =
        aidCache[bvid] ?: favoriteRepository.fetchAid(bvid).also { aidCache[bvid] = it }

    /** 倍速播放(「我的」页设置;PlayerController 转发)。 */
    fun setPlaybackSpeed(speed: Float) = playerController.setPlaybackSpeed(speed)

    fun playPause() = playerController.playPause()

    // ---------- 睡眠定时(定时关闭) ----------
    //
    // 语义:设 N 分钟后暂停播放(不退出 app、不停服务,用户醒来还能续播)。
    // 状态只在内存不持久化 —— 「睡一觉后残留一个倒计时」比丢失设置更糟。
    private var sleepTimerJob: Job? = null
    private val _sleepMinutesLeft = MutableStateFlow<Int?>(null)

    /** 剩余分钟(UI 显示);null = 未启用。 */
    val sleepMinutesLeft: StateFlow<Int?> = _sleepMinutesLeft.asStateFlow()

    /** 设定睡眠定时;minutes<=0 取消。到点只 pause(不能 toggle:恰好暂停时会被反手起播)。 */
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes <= 0) {
            _sleepMinutesLeft.value = null
            return
        }
        _sleepMinutesLeft.value = minutes
        sleepTimerJob = viewModelScope.launch {
            repeat(minutes) {
                delay(60_000)
                val left = (_sleepMinutesLeft.value ?: return@launch) - 1
                if (left <= 0) {
                    playerController.pause()
                    _sleepMinutesLeft.value = null
                    return@launch
                }
                _sleepMinutesLeft.value = left
            }
        }
    }

    fun cancelSleepTimer() = setSleepTimer(0)

    fun next() = playerController.skipToNext()
    fun previous() = playerController.skipToPrevious()
    fun seekTo(ms: Long) = playerController.seekTo(ms)
    fun retryPlayback() = playerController.retry()
    fun playAt(index: Int) = playerController.playAt(index)

    /** 历史回放:在队列则跳播,否则追加队尾起播(不打乱现有队列)。 */
    fun playFromHistory(song: Song) = playerController.playFromHistory(song.toMediaItem())

    /**
     * 模式单键循环:列表循环 → 单曲循环 → 随机 → 顺序 → 列表循环。
     * 顺序 = REPEAT_OFF + 关随机(队列播完即止)。
     */
    fun cyclePlayMode() {
        val ps = _state.value.player
        when {
            ps.shuffleEnabled -> {
                playerController.setShuffle(false)
                playerController.setRepeatMode(Player.REPEAT_MODE_ALL)
            }
            ps.repeatMode == Player.REPEAT_MODE_ALL -> playerController.setRepeatMode(Player.REPEAT_MODE_ONE)
            // 开随机必须把单曲循环改走:REPEAT_MODE_ONE 优先级高于 shuffle,否则=当前曲无限循环
            ps.repeatMode == Player.REPEAT_MODE_ONE -> {
                playerController.setRepeatMode(Player.REPEAT_MODE_ALL)
                playerController.setShuffle(true)
            }
            else -> playerController.setRepeatMode(Player.REPEAT_MODE_ALL)
        }
    }

    fun clearQueue() = playerController.clearQueue()

    /** 下载反馈走 Toast(抽屉是独立窗口,Snackbar 会被盖住)。 */
    private val _downloadMessage = MutableStateFlow<String?>(null)
    val downloadMessage: StateFlow<String?> = _downloadMessage.asStateFlow()
    fun consumeDownloadMessage() {
        _downloadMessage.value = null
    }

    /** 队列插入反馈(下一首播放/加入队列),消费式一次性消息,UI 侧用 Toast 展示。 */
    private val _queueNotice = MutableStateFlow<String?>(null)
    val queueNotice: StateFlow<String?> = _queueNotice.asStateFlow()
    fun consumeQueueNotice() {
        _queueNotice.value = null
    }

    /** 任意操作结果的通用出口(收藏弹窗等无 VM 流的调用方 push,全局提示条消费)。 */
    fun pushNotice(message: String?) {
        _queueNotice.value = message
    }

    /** 队列抽屉下载钮:下载当前播放曲。走 DownloadManager 队列(进度/取消见下载页)。 */
    fun downloadCurrentSong(context: Context) {
        val ps = _state.value.player
        val bvid = ps.currentMediaId ?: return
        downloadSong(
            context, bvid,
            Song(
                bvid = bvid,
                title = ps.title.orEmpty().ifBlank { "audio" },
                artist = ps.artist.orEmpty(),
                coverUrl = ps.artworkUri,
                durationSec = if (ps.durationMs > 0) ps.durationMs / 1000 else 0,
            ),
        )
    }

    /** 歌单详情行菜单「下载」:任意歌曲,不必在播放队列里。 */
    fun downloadSong(context: Context, song: Song) {
        downloadSong(context, song.bvid, song)
    }

    /** 批量下载(歌单/合集详情「下载全部」):逐首入队,队列自动去重已在队/已下载的。 */
    fun downloadAll(context: Context, songs: List<Song>) {
        val manager = (context.applicationContext as AvSongApp).downloadManager
        val n = manager.enqueueAll(songs)
        _queueNotice.value = if (n > 0) "已加入下载队列 $n 首" else "没有可下载的新歌曲"
    }

    /**
     * 入队下载。去重逻辑在 DownloadManager(已在队/已下载跳过);
     * 进度/取消/重试在「已下载」页可见,这里不再弹 Toast。
     */
    private fun downloadSong(context: Context, bvid: String, song: Song) {
        val manager = (context.applicationContext as AvSongApp).downloadManager
        val queued = manager.enqueue(song)
        if (queued) _downloadMessage.value = "已加入下载队列:${song.title}"
    }

    fun removeFromQueue(index: Int) = playerController.removeAt(index)
    fun moveInQueue(from: Int, to: Int) = playerController.moveItem(from, to)

    /** 播放页「加入歌单」:把当前这首歌追加到播放队列末尾。 */
    fun addCurrentToQueue() = playerController.appendCurrentToQueue()
    fun currentPosition(): Long = playerController.currentPosition()
}

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setUri(playUri())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply { coverUrl?.let { setArtworkUri(android.net.Uri.parse(it)) } }
            .build(),
    )
    .build()
