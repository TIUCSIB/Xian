package dev.avsong.data

import dev.avsong.data.remote.AudioStream
import dev.avsong.data.remote.HistoryItem
import dev.avsong.data.remote.HistoryResponse
import dev.avsong.data.remote.PlayUrlResponse
import dev.avsong.data.remote.RankResponse
import dev.avsong.data.remote.RcmdResponse
import dev.avsong.data.remote.SearchResponse
import dev.avsong.data.remote.SpiResponse
import dev.avsong.data.remote.VideoItem
import dev.avsong.data.remote.VideoListResponse
import dev.avsong.data.remote.ViewData
import dev.avsong.data.remote.ViewResponse
import dev.avsong.domain.NotLoggedInException
import dev.avsong.domain.PagedResult
import dev.avsong.domain.Song
import dev.avsong.domain.SongRepository
import dev.avsong.domain.formatClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * B 站音乐数据源(匿名,无登录)。单例,UI 与 ExoPlayer resolver 共用。
 *
 * 调用链(实测配方,详见 docs/bilibili-api.md):
 * - spi → buvid3/4 进程缓存(搜索必须带,否则 -412)
 * - 搜索: /x/web-interface/search/type?search_type=video (结果无 cid,标题含 <em> 标签)
 * - 榜单: /x/centralization/interface/music/comprehensive/web/rank
 *   ⚠ 榜单给的 cid 是 music-center 内部 id,与视频真实 cid 不一致(playurl 会 -404),
 *   一律以 view 返回的 cid 为准
 * - 取流: /x/player/playurl?fnval=16&platform=pc → data.dash.audio 按 bandwidth 取最高
 *   (免费 192K=30280)。直链 ~3h 过期 → 播放层用 bili:// scheme + ResolvingDataSource 现取现用
 */
object BiliSongRepository : SongRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private const val baseUrl = "https://api.bilibili.com"

    // ---------- 挂起接口(UI 层) ----------

    override suspend fun search(keyword: String, page: Int): PagedResult<Song> =
        withContext(Dispatchers.IO) {
            val data = searchBlocking(keyword, page)
            PagedResult(
                items = data.result.filter { it.bvid.isNotBlank() }.map { it.toSong() },
                page = page,
                hasMore = page < data.numPages,
            )
        }

    /** 粘贴 BV 号 / av 号 / 分享链接 → 直接解析成单首(不走搜索接口)。 */
    override suspend fun resolveDirect(query: String): Song? = withContext(Dispatchers.IO) {
        val bvid = Regex("BV[0-9A-Za-z]{10}").find(query)?.value
            ?: Regex("av(\\d+)", RegexOption.IGNORE_CASE).find(query)?.groupValues?.get(1)?.let { avToBvidBlocking(it) }
            ?: return@withContext null
        runCatching { viewToSongBlocking(bvid) }.getOrNull()
    }

    private fun avToBvidBlocking(av: String): String? = runCatching {
        val resp = getJson("$baseUrl/x/web-interface/view?aid=$av", referer = BiliConstants.REFERER)
        json.decodeFromString<ViewResponse>(resp).data?.bvid
    }.getOrNull()

    @Throws(IOException::class)
    private fun viewToSongBlocking(bvid: String): Song {
        val resp = getJson("$baseUrl/x/web-interface/view?bvid=$bvid", referer = "https://www.bilibili.com/video/$bvid")
        val d = json.decodeFromString<ViewResponse>(resp).data ?: throw IOException("view 空")
        return Song(
            bvid = d.bvid.ifBlank { bvid },
            aid = d.aid,
            title = d.title,
            artist = d.owner?.name.orEmpty(),
            coverUrl = d.pic.fixProtocol(),
            durationSec = d.duration,
        )
    }

    override suspend fun getRanking(): List<Song> = withContext(Dispatchers.IO) { rankingBlocking(1) }
    override suspend fun getRankingPage(page: Int): List<Song> =
        withContext(Dispatchers.IO) { rankingBlocking(page) }

    // ---------- 发现:推荐 / 热门 / 排行 / 最近播放 ----------

    override suspend fun getRecommend(): List<Song> = withContext(Dispatchers.IO) {
        ensureBuvid()
        // ⚠ 路径带 wbi 段,但实测匿名无需签名(与 wbi playurl 不同)
        val resp = getJson(
            "$baseUrl/x/web-interface/wbi/index/top/feed/rcmd?ps=20&fresh_type=4",
            referer = BiliConstants.REFERER,
        )
        json.decodeFromString<RcmdResponse>(resp).data?.item.orEmpty().mapNotNull { it.toSong() }
    }

    override suspend fun getPopular(page: Int): PagedResult<Song> = withContext(Dispatchers.IO) {
        ensureBuvid()
        val url = (baseUrl + "/x/web-interface/popular").toHttpUrl().newBuilder()
            .addQueryParameter("pn", page.toString())
            .addQueryParameter("ps", "20")
            .build()
        val resp = getJson(url.toString(), referer = BiliConstants.REFERER)
        val data = json.decodeFromString<VideoListResponse>(resp).data
        PagedResult(
            items = data?.list.orEmpty().mapNotNull { it.toSong() },
            page = page,
            hasMore = data != null && !data.noMore, // no_more=true 才是没有下一页
        )
    }

    override suspend fun getMusicRanking(): List<Song> = withContext(Dispatchers.IO) {
        ensureBuvid()
        // rid=3 = 音乐区;⚠ 实测本接口只认 rid=0/3,子分区(28/31/59…)一律 -400
        val resp = getJson(
            "$baseUrl/x/web-interface/ranking/v2?rid=3&type=all",
            referer = "https://www.bilibili.com/v/music/",
        )
        json.decodeFromString<VideoListResponse>(resp).data?.list.orEmpty().mapNotNull { it.toSong() }
    }

    override suspend fun getHistory(): List<Song> = withContext(Dispatchers.IO) {
        // ps 上限 30(50 → -400);游标分页本版未做,只取最近一批
        val resp = getJson(
            "$baseUrl/x/web-interface/history/cursor?ps=30&max=0&view_at=0",
            referer = "https://www.bilibili.com/account/history",
        )
        val parsed = json.decodeFromString<HistoryResponse>(resp)
        if (parsed.code == -101) throw NotLoggedInException()
        if (parsed.code != 0) throw IOException("历史记录错误 ${parsed.code}: ${parsed.message}")
        parsed.data?.list.orEmpty().mapNotNull { it.toHistorySong() }
    }

    /**
     * view 系视频条目(推荐/热门/排行同构) → Song。
     * cid 一律不取:播放永远经 bili:// 现取真实 cid,规避「榜单 cid 不可信」陷阱。
     */
    private fun VideoItem.toSong(): Song? {
        if (bvid.isBlank()) return null
        return Song(
            bvid = bvid,
            cid = null,
            aid = aid,
            title = title.stripEmTags().ifBlank { "未知曲目" },
            artist = owner?.name.orEmpty(),
            coverUrl = pic.fixProtocol(),
            durationSec = duration,
        )
    }

    /**
     * 历史条目 → Song。非视频条目(直播/番剧,`history.bvid` 为空)直接丢弃。
     * `progress = -1` 表示没有进度;只有落在 (0, duration) 区间才作为起播位置。
     */
    private fun HistoryItem.toHistorySong(): Song? {
        val bvid = history?.bvid.orEmpty()
        if (bvid.isBlank()) return null
        val pos = progress.coerceAtLeast(0)
        val resumable = pos in 1 until duration
        return Song(
            bvid = bvid,
            cid = null,
            aid = 0, // 历史不带 aid,收藏时经 view 补
            title = title.stripEmTags().ifBlank { "未知曲目" },
            artist = authorName,
            coverUrl = cover.fixProtocol(),
            durationSec = duration,
            resumePositionMs = if (resumable) pos * 1000 else 0,
            progressHint = if (resumable) "听到 ${(pos * 1000).formatClock()}" else null,
        )
    }

    // ---------- 阻塞接口(供 ExoPlayer resolver 在加载线程调用) ----------

    /** 取某视频最高免费音质的音频直链(约 3h 过期,不缓存)。 */
    @Throws(IOException::class)
    fun audioUrlBlocking(bvid: String): String = audioUrlsBlocking(bvid).first()

    /**
     * 取音频直链列表:主 baseUrl 在前、备用 CDN backupUrl 在后(去重、去空)。
     * resolver 在重试时按序换源,抗单点 CDN 失效。
     */
    @Throws(IOException::class)
    fun audioUrlsBlocking(bvid: String): List<String> {
        val cid = viewCidBlocking(bvid)
        val stream = playUrlBlocking(bvid, cid)
        val urls = buildList {
            if (stream.baseUrl.isNotBlank()) add(stream.baseUrl)
            stream.backupUrl.filter { it.isNotBlank() }.forEach { if (it !in this) add(it) }
        }
        if (urls.isEmpty()) throw IOException("无可用音频直链")
        return urls
    }

    @Throws(IOException::class)
    fun viewCidBlocking(bvid: String): Long {
        val resp = getJson("$baseUrl/x/web-interface/view?bvid=$bvid", referer = "https://www.bilibili.com/video/$bvid")
        val data = json.decodeFromString<ViewResponse>(resp).data ?: throw IOException("view 返回空数据")
        return data.cid.takeIf { it > 0 } ?: data.pages.firstOrNull()?.cid ?: throw IOException("无法获取 cid")
    }

    @Throws(IOException::class)
    fun playUrlBlocking(bvid: String, cid: Long): AudioStream {
        val url = (baseUrl + "/x/player/playurl").toHttpUrl().newBuilder()
            .addQueryParameter("bvid", bvid)
            .addQueryParameter("cid", cid.toString())
            .addQueryParameter("fnval", "4048") // 含 FLAC/杜比位域;普通账号仍只回 192K
            .addQueryParameter("platform", "pc")
            .build()
        val resp = getJson(url.toString(), referer = "https://www.bilibili.com/video/$bvid")
        val play = json.decodeFromString<PlayUrlResponse>(resp)
        if (play.code != 0) throw IOException("playurl 错误 ${play.code}: ${play.message}")
        val dash = play.data?.dash ?: throw IOException("无可用音频流(可能需要大会员)")
        // 优先级:FLAC(无损) > 杜比 > audio 里带宽最高(免费 192K=30280)
        dash.flac?.audio?.takeIf { it.baseUrl.isNotBlank() }?.let { return it }
        dash.dolby?.audio?.maxByOrNull { it.bandwidth }?.takeIf { it.baseUrl.isNotBlank() }?.let { return it }
        return dash.audio.maxByOrNull { it.bandwidth }
            ?: throw IOException("无可用音频流(可能需要大会员)")
    }

    // ---------- 搜索 / 榜单 ----------

    @Throws(IOException::class)
    private fun searchBlocking(keyword: String, page: Int): dev.avsong.data.remote.SearchData {
        ensureBuvid()
        val url = (baseUrl + "/x/web-interface/search/type").toHttpUrl().newBuilder()
            .addQueryParameter("search_type", "video")
            .addQueryParameter("keyword", keyword)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")
            .addQueryParameter("platform", "pc")
            .build()
        val resp = getJson(url.toString(), referer = "https://search.bilibili.com/")
        return json.decodeFromString<SearchResponse>(resp).data ?: throw IOException("搜索返回空数据")
    }

    private fun dev.avsong.data.remote.SearchItem.toSong() = Song(
        bvid = bvid,
        cid = null, // 搜索结果不含 cid,播放时经 bili:// 现取
        aid = aid, // 收藏操作要用
        title = title.stripEmTags(),
        artist = author,
        coverUrl = pic.fixProtocol(),
        durationSec = parseDuration(duration),
    )

    @Throws(IOException::class)
    private fun rankingBlocking(pageNum: Int): List<Song> {
        ensureBuvid()
        val resp = getJson(
            "$baseUrl/x/centralization/interface/music/comprehensive/web/rank?pn=$pageNum",
            referer = "https://music.bilibili.com/pc/music-center",
        )
        val list = json.decodeFromString<RankResponse>(resp).data?.list.orEmpty()
        return list.mapNotNull { item ->
            if (item.bvid.isBlank()) return@mapNotNull null
            Song(
                bvid = item.bvid,
                cid = item.cid.toLongOrNull(), // ⚠ 榜单 cid 不可信,仅展示用
                aid = item.aid.toLongOrNull() ?: 0L,
                title = item.musicTitle.ifBlank { "未知曲目" },
                artist = item.author,
                coverUrl = item.cover.fixProtocol(),
                // 行尾时长用关联稿件时长兜底;条目自身无时长字段
                durationSec = item.related_archive?.duration ?: 0,
            )
        }
    }

    // ---------- buvid / HTTP ----------

    private fun ensureBuvid() {
        if (BiliHttp.buvid3 != null) return
        val resp = getJson("$baseUrl/x/frontend/finger/spi")
        val data = json.decodeFromString<SpiResponse>(resp).data ?: return
        BiliHttp.buvid3 = data.b3
        BiliHttp.buvid4 = data.b4
    }

    private fun getJson(url: String, referer: String? = null): String {
        val request = Request.Builder()
            .url(url)
            .apply { if (referer != null) header("Referer", referer) }
            .get()
            .build()
        BiliHttp.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} $url")
            return response.body?.string() ?: throw IOException("空响应体")
        }
    }
}

// ---------- 小工具 ----------

/** 搜索标题含 <em class="keyword">高亮标签,剥离。 */
private fun String.stripEmTags(): String = replace(Regex("<[^>]+>"), "")

/** //i0.hdslb.com/... → https://i0.hdslb.com/... */
private fun String.fixProtocol(): String? = when {
    isBlank() -> null
    startsWith("//") -> "https:$this"
    startsWith("http://") -> replaceFirst("http://", "https://")
    else -> this
}

/** "3:46" / "1:02:03" → 秒 */
internal fun parseDuration(s: String): Long {
    val parts = s.split(":").mapNotNull { it.toLongOrNull() }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0
    }
}
