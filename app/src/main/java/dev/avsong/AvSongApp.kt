package dev.avsong

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import dev.avsong.data.BiliHttp
import dev.avsong.data.BiliSongRepository
import dev.avsong.data.CollectionStore
import dev.avsong.data.DownloadManager
import dev.avsong.data.DownloadedStore
import dev.avsong.data.FavoriteRepository
import dev.avsong.data.PlayHistoryStore
import dev.avsong.data.SearchHistoryStore
import dev.avsong.data.auth.AuthStore
import dev.avsong.player.PlayerController
import dev.avsong.ui.theme.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AvSongApp : Application(), SingletonImageLoader.Factory {

    /** 应用级协程作用域(Application 无 lifecycleScope,进程存活即作用域存活)。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var playerController: PlayerController
        private set

    lateinit var authStore: AuthStore
        private set

    lateinit var favoriteRepository: FavoriteRepository
        private set

    lateinit var searchHistoryStore: SearchHistoryStore
        private set

    lateinit var collectionStore: CollectionStore
        private set

    lateinit var downloadedStore: DownloadedStore
        private set

    lateinit var downloadManager: DownloadManager
        private set

    lateinit var playHistoryStore: PlayHistoryStore
        private set

    lateinit var themeStore: ThemeStore
        private set

    lateinit var settingsStore: dev.avsong.ui.settings.SettingsStore
        private set

    /** 真实 B 站数据源(匿名单例,UI 与 ExoPlayer resolver 共用)。 */
    val songRepository: BiliSongRepository get() = BiliSongRepository

    override fun onCreate() {
        super.onCreate()
        playerController = PlayerController(this)
        authStore = AuthStore(this)
        favoriteRepository = FavoriteRepository(authStore)
        searchHistoryStore = SearchHistoryStore(this)
        collectionStore = CollectionStore(this)
        downloadedStore = DownloadedStore(this)
        downloadManager = DownloadManager(this)
        playHistoryStore = PlayHistoryStore(this)
        themeStore = ThemeStore(this)
        dev.avsong.ui.settings.SettingsStore.init(this)
        settingsStore = dev.avsong.ui.settings.SettingsStore

        // 登录态 → OkHttp 拦截器的 Cookie 注入点(含启动时的持久化恢复)
        appScope.launch {
            authStore.load()
            authStore.state.collect { BiliHttp.authCookie = it.cookieHeader() }
        }
        // 已保存合集(UP 主合集=歌单)的本地持久化恢复
        appScope.launch { collectionStore.load() }
        // 已下载歌曲元数据恢复(自愈剔除文件已不存在的条目;孤儿扫描在下载页打开时做)
        appScope.launch { downloadedStore.load() }
        // 日夜间模式持久化恢复
        appScope.launch { themeStore.load() }
        // 音质/历史上限等设置恢复(仓库层选路直接读它的内存流)
        appScope.launch { settingsStore.load() }
        // 倍速:设置变更即应用。⚠ MediaController 只能在主线程调,collect 须在 Main(appScope 是 Default)
        appScope.launch(Dispatchers.Main) {
            settingsStore.state.collect { state -> playerController.setPlaybackSpeed(state.playbackSpeed) }
        }
    }

    /**
     * Coil 单例 ImageLoader:封面磁盘缓存(cacheDir/image_cache,上限约 2% 可用空间)。
     * App 重启/滚动回列表时封面秒开,不再重复走网络。
     * (crossfade 淡入需额外 coil-transition artifact,暂不引入)
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
}
