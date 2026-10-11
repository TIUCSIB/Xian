package dev.avsong.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import dev.avsong.data.CollectionRepository
import dev.avsong.player.PlayerController
import kotlinx.coroutines.delay
import dev.avsong.ui.toMediaItem
import dev.avsong.data.CollectionStore
import dev.avsong.data.FavoriteRepository
import dev.avsong.data.SavedCollection
import dev.avsong.domain.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val favRepo: FavoriteRepository,
    private val collectionRepo: CollectionRepository,
    private val collectionStore: CollectionStore,
) : ViewModel() {

    data class UiState(
        val folders: List<FavoriteRepository.Folder> = emptyList(),
        val subscribed: List<FavoriteRepository.Folder> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        // ---- UP 主合集(本地保存)----
        val collections: List<SavedCollection> = emptyList(),
        val addingCollection: Boolean = false,
        val collectionError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            collectionStore.state.collect { list ->
                _state.update { it.copy(collections = list) }
            }
        }
        viewModelScope.launch { collectionStore.load() }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val mine = favRepo.myFolders()
                val sub = runCatching { favRepo.subscribedFolders() }.getOrDefault(emptyList())
                _state.update { it.copy(folders = mine, subscribed = sub, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败(需登录)") }
            }
        }
    }

    fun createFolder(title: String, isPrivate: Boolean) {
        viewModelScope.launch {
            runCatching { favRepo.createFolder(title, isPrivate) }
            refresh()
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            runCatching { favRepo.deleteFolder(folderId) }
            refresh()
        }
    }

    // ---------- UP 主合集 ----------

    /**
     * 粘贴合集链接添加。解析失败抛异常返回 false(对话框里就地提示,不打断输入);
     * 解析成功则拉一次 meta 取标题/封面/总数并入库,网络失败经 state.collectionError 提示。
     */
    suspend fun addCollection(text: String): Boolean {
        val ids = try {
            collectionRepo.parse(text)
        } catch (e: Exception) {
            _state.update { it.copy(collectionError = e.message) }
            return false
        } ?: run {
            _state.update { it.copy(collectionError = "没识别出合集链接,请粘贴 space.bilibili.com/…/lists/… 形式的链接") }
            return false
        }
        val (mid, seasonId) = ids
        _state.update { it.copy(addingCollection = true, collectionError = null) }
        return try {
            val meta = collectionRepo.page(mid, seasonId, 1, pageSize = 1).meta
                ?: throw Exception("合集不存在或已删除")
            collectionStore.add(
                SavedCollection(
                    mid = mid,
                    seasonId = seasonId,
                    title = meta.title,
                    cover = meta.cover,
                    total = meta.total,
                ),
            )
            _state.update { it.copy(addingCollection = false) }
            true
        } catch (e: Exception) {
            _state.update { it.copy(addingCollection = false, collectionError = e.message ?: "添加失败") }
            false
        }
    }

    fun removeCollection(mid: Long, seasonId: Long) {
        viewModelScope.launch { collectionStore.remove(mid, seasonId) }
    }

    fun clearCollectionError() {
        _state.update { it.copy(collectionError = null) }
    }
}

/** 收藏夹内容页状态。 */
class FolderContentViewModel(
    private val favRepo: FavoriteRepository,
    val folderId: Long,
) : ViewModel() {

    data class UiState(
        val songs: List<Song> = emptyList(),
        val page: Int = 1,
        val hasMore: Boolean = false,
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val result = favRepo.folderContents(folderId, 1)
                _state.update {
                    it.copy(songs = result.songs, page = 1, hasMore = result.hasMore, loading = false)
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || _loadingAll.value || !s.hasMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val result = favRepo.folderContents(folderId, s.page + 1)
                _state.update {
                    it.copy(
                        songs = (it.songs + result.songs).distinctBy { song -> song.bvid },
                        page = s.page + 1,
                        hasMore = result.hasMore,
                        loadingMore = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /** 搜索用的「加载全部」:拉完剩余页进 state(不动队列),期间 loadMore 让位防竞态。 */
    private val _loadingAll = MutableStateFlow(false)
    val loadingAll: StateFlow<Boolean> = _loadingAll.asStateFlow()

    fun loadAll() {
        if (_loadingAll.value) return
        viewModelScope.launch {
            _loadingAll.value = true
            try {
                var page = _state.value.page
                var hasMore = _state.value.hasMore
                var songs = _state.value.songs
                var failures = 0
                while (hasMore) {
                    val result = try {
                        favRepo.folderContents(folderId, page + 1)
                    } catch (e: Exception) {
                        if (++failures < 3) {
                            delay(1500)
                            continue
                        }
                        throw e
                    }
                    failures = 0
                    page += 1
                    hasMore = result.hasMore
                    songs = (songs + result.songs).distinctBy { it.bvid }
                    _state.update { it.copy(songs = songs, page = page, hasMore = hasMore) }
                }
            } catch (e: Exception) {
                // 拉全量失败:保留已加载部分,搜索照常在已加载范围里过滤
            } finally {
                _loadingAll.value = false
            }
        }
    }

    /** 从本收藏夹移除(不删原视频)。 */
    fun removeFromFolder(song: Song, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val aid = ensureAid(song)
            if (aid > 0) {
                runCatching { favRepo.modify(aid, delIds = folderId.toString()) }
            }
            onDone()
            load()
        }
    }

    private suspend fun ensureAid(song: Song): Long =
        if (song.aid > 0) song.aid else favRepo.fetchAid(song.bvid)
}

/** 合集内容页状态(UP 主合集,只读播放,无移除)。 */
class CollectionContentViewModel(
    private val collectionRepo: CollectionRepository,
    val mid: Long,
    val seasonId: Long,
    private val playerController: PlayerController,
) : ViewModel() {

    data class UiState(
        val title: String = "",
        val cover: String? = null,
        val upperName: String = "",
        val total: Int = 0,
        val songs: List<Song> = emptyList(),
        val page: Int = 1,
        val hasMore: Boolean = false,
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val loadingAll: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val result = collectionRepo.page(mid, seasonId, 1)
                _state.update {
                    it.copy(
                        title = result.meta?.title.orEmpty(),
                        cover = result.meta?.cover,
                        total = result.meta?.total ?: 0,
                        upperName = result.songs.firstOrNull()?.artist.orEmpty(),
                        songs = result.songs,
                        page = 1,
                        hasMore = result.hasMore,
                        loading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /** 播放前先拉全量的进度标记(合集按页加载,直接播只入列已加载部分)。 */
    private val _loadingAll = MutableStateFlow(false)
    val loadingAll: StateFlow<Boolean> = _loadingAll.asStateFlow()

    /**
     * 合集播放:已加载部分立即起播,后台继续拉剩余页并**逐批追加进队列**,
     * 队列会从 20 首一路长到合集全量(用户反馈:等全部拉完再播太慢)。
     * 单页失败重试 2 次(间隔 1.5s,防 B 站限流 -412),仍失败则中止补全(保留已入列部分)。
     */
    fun playWithFill(startIndex: Int, playLoaded: (List<Song>, Int) -> Unit) {
        val s = _state.value
        if (s.loading || s.loadingAll) return
        viewModelScope.launch {
            _loadingAll.value = true
            // 1) 已加载部分先播(点哪首从哪首起)
            playLoaded(s.songs, startIndex)
            try {
                var songs = s.songs
                var page = s.page
                var hasMore = s.hasMore
                var failures = 0
                while (hasMore) {
                    val result = try {
                        collectionRepo.page(mid, seasonId, page + 1)
                    } catch (e: Exception) {
                        if (++failures < 3) {
                            delay(1500)
                            continue
                        }
                        throw e
                    }
                    failures = 0
                    page += 1
                    hasMore = result.hasMore
                    val fresh = result.songs.filter { new -> songs.none { it.bvid == new.bvid } }
                    if (fresh.isEmpty()) continue
                    songs = songs + fresh
                    _state.update { it.copy(songs = songs, page = page, hasMore = hasMore) }
                    playerController.appendSongsToQueue(fresh.map { it.toMediaItem() })
                }
            } catch (e: Exception) {
                // 补全中止:队列保留已入列的部分
            } finally {
                _loadingAll.value = false
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || _loadingAll.value || !s.hasMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val result = collectionRepo.page(mid, seasonId, s.page + 1)
                _state.update {
                    it.copy(
                        // 978 首的大合集翻页期间可能重复,去重防 LazyColumn key 冲突
                        songs = (it.songs + result.songs).distinctBy { song -> song.bvid },
                        page = s.page + 1,
                        hasMore = result.hasMore,
                        loadingMore = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /**
     * 搜索用的「加载全部」:把剩余页全部拉进 state(不动队列,与 playWithFill 的补队列分开),
     * 边加载边过滤。-412 防护同 playWithFill;失败保留已加载部分。
     */
    fun loadAll() {
        if (_loadingAll.value) return
        viewModelScope.launch {
            _loadingAll.value = true
            try {
                var page = _state.value.page
                var hasMore = _state.value.hasMore
                var songs = _state.value.songs
                var failures = 0
                while (hasMore) {
                    val result = try {
                        collectionRepo.page(mid, seasonId, page + 1)
                    } catch (e: Exception) {
                        if (++failures < 3) {
                            delay(1500)
                            continue
                        }
                        throw e
                    }
                    failures = 0
                    page += 1
                    hasMore = result.hasMore
                    val fresh = result.songs.filter { new -> songs.none { it.bvid == new.bvid } }
                    if (fresh.isNotEmpty()) {
                        songs = songs + fresh
                        _state.update { it.copy(songs = songs, page = page, hasMore = hasMore) }
                    } else {
                        _state.update { it.copy(page = page, hasMore = hasMore) }
                    }
                }
            } catch (e: Exception) {
                // 拉全量中止:保留已加载部分
            } finally {
                _loadingAll.value = false
            }
        }
    }
}
