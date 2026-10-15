package dev.avsong.domain

/**
 * 音乐数据源抽象。真实实现走 B 站 API(dev.avsong.data.BiliSongRepository)。
 * 音频直链的获取不在这里:播放时由 ExoPlayer 的 ResolvingDataSource 经 bili:// 现取。
 */
interface SongRepository {
    suspend fun search(keyword: String, page: Int = 1): PagedResult<Song>
    suspend fun getRanking(): List<Song>
    /** 搜索联想词;默认空(联想是增强,伪仓库/离线场景直接空列表)。 */
    suspend fun suggest(keyword: String): List<String> = emptyList()
    /** 热歌榜分页(pn);默认空实现(伪仓库无分页,load-more 自动停)。 */
    suspend fun getRankingPage(page: Int): List<Song> = emptyList()

    /** 首页「推荐」:rcmd 推荐流。 */
    suspend fun getRecommend(): List<Song> = emptyList()

    /** 首页「热门」:全站综合热门,可翻页。 */
    suspend fun getPopular(page: Int = 1): PagedResult<Song> = PagedResult(emptyList(), page, false)

    /** 首页「排行」:音乐区排行榜(rid=3)。 */
    suspend fun getMusicRanking(): List<Song> = emptyList()

    /**
     * 首页「最近」:播放历史,需登录。
     * 未登录时抛 [NotLoggedInException]。
     */
    suspend fun getHistory(): List<Song> = emptyList()

    /** 粘贴 BV 号 / av 号 / 分享链接 → 直接解析成单首;非链接返回 null。 */
    suspend fun resolveDirect(query: String): Song? = null
}
