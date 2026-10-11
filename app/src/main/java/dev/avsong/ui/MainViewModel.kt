package dev.avsong.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.avsong.data.lyric.LyricLine
import dev.avsong.data.lyric.LyricRepository
import dev.avsong.domain.NotLoggedInException
import dev.avsong.domain.Song
import dev.avsong.domain.SongRepository
import dev.avsong.player.PlayerController
import dev.avsong.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
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
                homeHasMore = source == HomeSource.POPULAR && homeCache.containsKey(source),
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
                    HomeSource.RANKING -> repository.getRanking()
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
                        homeHasMore = source == HomeSource.POPULAR && songs.isNotEmpty(),
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

    /** 上拉加载下一页(目前只有「热门」支持翻页)。 */
    fun loadMoreHome() {
        val cur = _state.value
        if (cur.homeSource != HomeSource.POPULAR) return
        if (!cur.homeHasMore || cur.homeLoadingMore || cur.homeLoading) return
        viewModelScope.launch {
            _state.update { it.copy(homeLoadingMore = true) }
            try {
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
            } catch (e: Exception) {
                _state.update { it.copy(homeLoadingMore = false) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) {
            _state.update { it.copy(search = SearchUiState.Idle) }
            return
        }
        _state.update { it.copy(search = SearchUiState.Loading) }
        viewModelScope.launch {
            try {
                // 1. BV/av/分享链接直达
                repository.resolveDirect(q)?.let { direct ->
                    searchHistoryStore.add(q)
                    _state.update {
                        it.copy(search = SearchUiState.Result(listOf(direct), keyword = q, hasMore = false))
                    }
                    return@launch
                }
                // 2. 普通搜索第一页
                val result = repository.search(q, 1)
                searchHistoryStore.add(q)
                _state.update {
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
                _state.update { it.copy(search = SearchUiState.Error(e.message ?: "请求失败")) }
            }
        }
    }

    /** 上拉加载下一页,追加到现有结果。 */
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

    suspend fun aidFor(bvid: String): Long =
        aidCache[bvid] ?: favoriteRepository.fetchAid(bvid).also { aidCache[bvid] = it }

    fun playPause() = playerController.playPause()
    fun next() = playerController.skipToNext()
    fun previous() = playerController.skipToPrevious()
    fun seekTo(ms: Long) = playerController.seekTo(ms)
    fun toggleShuffle() = playerController.toggleShuffle()
    fun cycleRepeat() = playerController.cycleRepeatMode()
    fun retryPlayback() = playerController.retry()
    fun playAt(index: Int) = playerController.playAt(index)

    /** 历史回放:在队列则跳播,否则追加队尾起播(不打乱现有队列)。 */
    fun playFromHistory(song: Song) = playerController.playFromHistory(song.toMediaItem())
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
