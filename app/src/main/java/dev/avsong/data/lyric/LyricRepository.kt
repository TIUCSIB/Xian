package dev.avsong.data.lyric

import dev.avsong.data.BiliConstants
import dev.avsong.data.BiliHttp
import dev.avsong.data.remote.PlayerV2Response
import dev.avsong.data.remote.SubtitleBody
import dev.avsong.data.remote.ViewResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.IOException

/**
 * 歌词多源:
 * 1. 网易云公开接口:多候选查询(清洗标题 × 歌手变体)+ 时长校验,取第一个靠谱结果
 * 2. lrclib.net(开源歌词库):网易云没有的新歌/独立音乐常有,响应直接带时间轴歌词
 * 3. B 站视频字幕(/x/player/v2 → subtitle_url)兜底 —— 翻唱/搬运两个库都没有时才有
 * 4. bgm_info 元数据(视频自带 BGM 标题/歌手)增强第三方搜索
 *
 * ⚠ 网易云/lrclib 必须排在 B 站字幕前面(2026-10-13 调整):B 站 AI 字幕是对着**视频音轨**转写的,
 * 带口播/解说的音乐视频会把「这个大家都知道/但是呢…」这类说话当成歌词(用户实测:Dear D
 * 显示成讲新能源汽车泄压阀)。第三方库有严格时长校验,命中即真歌词;只有都搜不到(翻唱/小众)
 * 才轮到 B 站字幕——它至少时间轴和视频对齐。
 *
 * 结果按 bvid 缓存(含"无歌词"负缓存)。
 */
class LyricRepository {

    // ---------- 网易云 DTO ----------

    @Serializable
    private data class NeteaseSearchResponse(val result: NeteaseResult? = null)

    @Serializable
    private data class NeteaseResult(val songs: List<NeteaseSong> = emptyList())

    @Serializable
    private data class NeteaseSong(
        val id: Long = 0,
        val duration: Long = 0, // ms
        val name: String = "", // 歌名(换源候选预览用)
        val artists: List<NeteaseArtist>? = null,
    )

    @Serializable
    private data class NeteaseArtist(val name: String = "")

    @Serializable
    private data class NeteaseLyricResponse(
        val lrc: NeteaseLrc? = null,
        val tlyric: NeteaseLrc? = null, // 翻译歌词(双语),常为空
    )

    @Serializable
    private data class NeteaseLrc(val lyric: String = "")

    // ---------- lrclib DTO ----------

    @Serializable
    private data class LrclibTrack(
        val trackName: String = "",
        val artistName: String = "",
        val duration: Double = 0.0, // 秒
        val syncedLyrics: String? = null, // LRC 格式(带时间轴)
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 歌词缓存:LRU 上限 60 首——长会话听几百首时无界 ConcurrentHashMap 会一直涨
     * (单份几十 KB,不致命但没必要);换源 applyCandidate 也写这里,淘汰即回退自动拉取。
     * fetch 本身在 per-bvid Mutex 内读写,LinkedHashMap 非并发安全但已被互斥覆盖。
     */
    private val cache = object : LinkedHashMap<String, List<LyricLine>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricLine>>): Boolean =
            size > 60
    }
    private val inFlight = mutableMapOf<String, Mutex>()
    private val inFlightGuard = Mutex()

    /** 返回歌词行列表;空列表 = 无歌词(含负缓存)。 */
    suspend fun fetch(bvid: String, title: String, artist: String, durationSec: Long = 0): List<LyricLine> {
        cache[bvid]?.let { return it }
        val mutex = inFlightGuard.withLock { inFlight.getOrPut(bvid) { Mutex() } }
        return mutex.withLock {
            try {
                cache[bvid]?.let { return@withLock it }
                val lines = withContext(Dispatchers.IO) { loadBlocking(bvid, title, artist, durationSec) }
                cache[bvid] = lines
                lines
            } finally {
                inFlightGuard.withLock { inFlight.remove(bvid) }
            }
        }
    }

    /**
     * 手动换源(用户点名「词不对」时自己挑):
     * 三源**并行**收集候选(网易云多查询/lrclib 多查询/B 站字幕互不依赖,串行要 5-10s,
     * 并行后压到最慢一路的耗时);每项带来源标签、标题/歌手预览、行数、时长偏差。
     * [LyricCandidate.lyrics] 在收集时立即取词(网易云取词很快,且 lrclib 响应自带;
     * 只对能取到词的候选填,取不到的跳过不进列表)。
     */
    suspend fun listCandidates(
        bvid: String,
        title: String,
        artist: String,
        durationSec: Long = 0,
    ): List<LyricCandidate> = withContext(Dispatchers.IO) { coroutineScope {
        val ctxDeferred = async { runCatching { biliContext(bvid) }.getOrNull() }
        // 网易云与 lrclib 不依赖 ctx(直接用歌名/歌手查询),先行并行;B 站字幕等 ctx
        val neteaseDeferred = async {
            // 逐查询词搜索,词之间也并发(每词内部:search→lyric 串行,取词很轻)
            val queries = buildQueries(title, artist, null, null).take(4)
            val found = mutableListOf<LyricCandidate>()
            queries.map { q ->
                async {
                    if (found.size >= 4) return@async
                    val s = neteaseSearch(q).firstOrNull {
                        durationSec <= 0 || kotlin.math.abs(it.duration / 1000 - durationSec) <= 15
                    } ?: return@async
                    synchronized(found) {
                        if (found.any { it.remoteId == s.id.toString() } || found.size >= 4) return@synchronized
                    }
                    val lyric = neteaseLyric(s.id) ?: return@async
                    val lines = parseLrc(lyric.first)
                    if (lines.isEmpty()) return@async
                    val trans = lyric.second
                    val merged = if (trans != null) mergeTranslation(lines, parseLrc(trans)) else lines
                    val c = LyricCandidate(
                        source = "网易云",
                        remoteId = s.id.toString(),
                        displayTitle = listOf(s.name, s.artists?.joinToString(",") { it.name })
                            .filterNotNull().filter { it.isNotBlank() }.joinToString(" / ")
                            .ifBlank { q },
                        lines = merged,
                        durationDeltaSec = (s.duration / 1000 - durationSec),
                    )
                    synchronized(found) {
                        if (found.none { it.remoteId == c.remoteId } && found.size < 4) found += c
                    }
                }
            }.joinAll()
            found
        }
        val lrclibDeferred = async {
            val queries = buildQueries(title, artist, null, null).take(4)
            val found = mutableListOf<LyricCandidate>()
            queries.map { q ->
                async {
                    if (found.size >= 2) return@async
                    for (t in lrclibTracks(q)) {
                        if (durationSec > 0 && kotlin.math.abs(t.duration.toLong() - durationSec) > 15) continue
                        val lines = t.syncedLyrics?.let { parseLrc(it) }.orEmpty()
                        if (lines.isEmpty()) continue
                        synchronized(found) {
                            if (found.any { it.displayTitle == t.trackName + " / " + t.artistName } || found.size >= 2)
                                return@synchronized
                            found += LyricCandidate(
                                source = "lrclib",
                                remoteId = "",
                                displayTitle = t.trackName + " / " + t.artistName,
                                lines = lines,
                                durationDeltaSec = (t.duration.toLong() - durationSec),
                            )
                        }
                        break
                    }
                }
            }.joinAll()
            found
        }
        val ctx = ctxDeferred.await()

        val out = mutableListOf<LyricCandidate>()
        out += neteaseDeferred.await()
        out += lrclibDeferred.await()
        // bgm 增强词补查:并行阶段不带 ctx(不等它),ctx 回来后若 bgm 歌名还没出现在
        // 候选查询里,补一轮(只补一个词,一次搜索+取词;bgm 词命中率最高,值得补)
        ctx?.bgmTitle?.takeIf { it.isNotBlank() }?.let { bgm ->
            val q = buildQueries(bgm, ctx?.bgmSinger.orEmpty(), null, null).firstOrNull() ?: return@let
            val alreadyCovered = out.any { it.source == "网易云" && it.displayTitle.contains(bgm, ignoreCase = true) }
            if (!alreadyCovered && out.count { it.source == "网易云" } < 4) {
                val s = neteaseSearch(q).firstOrNull {
                    durationSec <= 0 || kotlin.math.abs(it.duration / 1000 - durationSec) <= 15
                }
                if (s != null) {
                    val lyric = neteaseLyric(s.id)
                    if (lyric != null) {
                        val lines = parseLrc(lyric.first)
                        if (lines.isNotEmpty()) {
                            val trans = lyric.second
                            val merged = if (trans != null) mergeTranslation(lines, parseLrc(trans)) else lines
                            out += LyricCandidate(
                                source = "网易云",
                                remoteId = s.id.toString(),
                                displayTitle = listOf(s.name, s.artists?.joinToString(",") { it.name })
                                    .filterNotNull().filter { it.isNotBlank() }.joinToString(" / ")
                                    .ifBlank { q },
                                lines = merged,
                                durationDeltaSec = (s.duration / 1000 - durationSec),
                            )
                        }
                    }
                }
            }
        }
        // B 站字幕:固定一个候选(若存在);它的查询需要 ctx(bgm 信息增强),放主流程尾部
        ctx?.subtitleLines?.takeIf { it.isNotEmpty() }?.let {
            out += LyricCandidate(
                source = "B站字幕",
                remoteId = "",
                displayTitle = "视频字幕(可能含口播)",
                lines = it,
                durationDeltaSec = 0,
            )
        }
        out
        }
    }

    /**
     * 换源落地:把用户选中的歌词写入缓存并返回。
     * fetch() 有缓存短路,下次切回这首歌直接命中选中版本。
     */
    fun applyCandidate(bvid: String, candidate: LyricCandidate): List<LyricLine> {
        cache[bvid] = candidate.lines
        return candidate.lines
    }

    /** 读缓存(换源弹窗应用后回读用);无缓存返回 null。 */
    fun fetchCached(bvid: String): List<LyricLine>? = cache[bvid]

    private fun loadBlocking(bvid: String, title: String, artist: String, durationSec: Long): List<LyricLine> {
        // 上下文只取一次:view(cid)+ player/v2(subtitles + bgm_info)
        val ctx = runCatching { biliContext(bvid) }.getOrNull()

        // 源 1:网易云,多候选(bgm/《》提取/清洗标题 × 歌手变体),带时长校验 —— 命中即真歌词
        val candidates = buildQueries(title, artist, ctx?.bgmTitle, ctx?.bgmSinger)
        for (q in candidates) {
            val song = neteaseSearch(q).firstOrNull { s ->
                durationSec <= 0 || kotlin.math.abs(s.duration / 1000 - durationSec) <= 15
            } ?: continue
            val (lrc, tlyric) = neteaseLyric(song.id) ?: continue
            val lines = parseLrc(lrc)
            if (lines.isEmpty()) continue
            // 有翻译则按时间戳近似对齐合并(网易云 tlyric 时间轴常与原文错位)
            return if (tlyric != null) mergeTranslation(lines, parseLrc(tlyric)) else lines
        }

        // 源 2:lrclib(开源歌词库,列表响应直接带 syncedLyrics)—— 网易云没有的新歌/
        // 独立音乐它常有(实测:李健《人间共鸣》2025 新歌网易云无,B 站字幕是口播,只有 lrclib 命中)
        for (q in candidates.take(4)) {
            val lines = lrclibSearch(q, durationSec)
            if (lines.isNotEmpty()) return lines
        }

        // 源 3:B 站字幕兜底 —— 翻唱/搬运两个第三方库都搜不到时才用(时间轴和视频对齐,
        // 但口播视频会转出说话当歌词,故排在最后,见类注释)
        ctx?.let { c ->
            c.subtitleLines.takeIf { it.isNotEmpty() }?.let { return it }
        }

        return emptyList()
    }

    // ---------- B 站上下文 ----------

    private data class BiliContext(
        val subtitleLines: List<LyricLine>,
        val bgmTitle: String?,
        val bgmSinger: String?,
    )

    private fun biliContext(bvid: String): BiliContext {
        val viewBody = get(
            "${BiliConstants.HOST_API}/x/web-interface/view?bvid=$bvid",
            referer = "https://www.bilibili.com/video/$bvid",
            client = BiliHttp.client,
        )
        val view = json.decodeFromString<ViewResponse>(viewBody).data ?: return BiliContext(emptyList(), null, null)
        val cid = view.cid.takeIf { it > 0 } ?: view.pages.firstOrNull()?.cid ?: return BiliContext(emptyList(), null, null)

        val pBody = get(
            "${BiliConstants.HOST_API}/x/player/v2?bvid=$bvid&cid=$cid",
            referer = "https://www.bilibili.com/video/$bvid",
            client = BiliHttp.client,
        )
        val pdata = json.decodeFromString<PlayerV2Response>(pBody).data
        val subs = pdata?.subtitle?.subtitles.orEmpty()
            .filter { it.subtitleUrl.isNotBlank() }
            .sortedBy { if (it.lan.contains("zh")) 0 else 1 }
        val lines = subs.firstOrNull()?.let { track ->
            fetchSubtitle(track.subtitleUrl, bvid)
        }.orEmpty()

        // "发现《XXX》" → XXX
        val bgmTitle = pdata?.bgmInfo?.musicTitle
            ?.let { Regex("《(.+?)》").find(it)?.groupValues?.get(1) ?: it }
            ?.takeIf { it.isNotBlank() }
        val bgmSinger = pdata?.bgmInfo?.musicSinger?.takeIf { it.isNotBlank() }

        return BiliContext(lines, bgmTitle, bgmSinger)
    }

    private fun fetchSubtitle(url: String, bvid: String): List<LyricLine> {
        val full = if (url.startsWith("//")) "https:$url" else url
        return runCatching {
            val body = get(full, referer = "https://www.bilibili.com/video/$bvid", client = BiliHttp.client)
            json.decodeFromString<SubtitleBody>(body).body
                .filter { it.content.isNotBlank() }
                .map { LyricLine((it.from * 1000).toLong(), it.content.trim()) }
        }.getOrDefault(emptyList())
    }

    // ---------- 查询构造与清洗 ----------

    internal fun buildQueries(title: String, artist: String, bgmTitle: String?, bgmSinger: String?): List<String> {
        val titles = buildList {
            bgmTitle?.let { add(it) }
            // 《》提取:嵌套书名号(《剪(电视剧《雁回时》ost)》)用**最外层**贪心配对,
            // 非贪婪 `《(.+?)》` 会截出「剪(电视剧《雁回时」半截词,搜出来必然不对;
            // 外层内容 cleanTitle 砍掉(剧名ost)括注得真歌名「剪」。cleanTitle 无缩短
            // (《A》《B》双书名号的外层=A》《B)说明没嵌套,跳过别占查询名额
            Regex("《(.+)》").find(title)?.groupValues?.get(1)?.let { outer ->
                val cleaned = outer.cleanTitle().trim()
                if (cleaned.isNotBlank() && cleaned != outer.trim()) add(cleaned)
            }
            // 内层书名号逐个抠(非嵌套《歌名》、双书名号《A》《B》、剧名《雁回时》兜底)
            Regex("《([^《》]+)》").findAll(title).forEach { add(it.groupValues[1].trim()) }
            add(title)
            title.cleanTitle().let { if (it != title) add(it) }
        }.distinct()
        val artists = buildList {
            bgmSinger?.let { add(it) }
            add(artist)
            artist.substringBefore(",").trim().let { if (it != artist) add(it) }
            add("")
        }.distinct()

        val out = ArrayList<String>(8)
        for (t in titles) {
            for (a in artists) {
                val q = if (a.isBlank()) t else "$t $a"
                if (q.isNotBlank() && q !in out) out.add(q)
                if (out.size >= 8) return out
            }
        }
        return out
    }

    /** 去【】[]前缀、去 " - xxx" / "| xxx" 尾巴。 */
    private fun String.cleanTitle(): String =
        replace(Regex("[【\\[(（][^】\\])）]*[】\\])）]"), "")
            .replace(Regex("\\s*[-|——]\\s*[^-|——]*$"), "")
            .trim()

    // ---------- 网易云 ----------

    private fun neteaseSearch(keyword: String, limit: Int = 5): List<NeteaseSong> {
        val url = "https://music.163.com/api/search/get/web" +
            "?s=${java.net.URLEncoder.encode(keyword, "UTF-8")}&type=1&offset=0&limit=$limit"
        return runCatching {
            json.decodeFromString<NeteaseSearchResponse>(get(url, referer = "https://music.163.com/", client = thirdPartyClient)).result?.songs.orEmpty()
        }.getOrDefault(emptyList())
    }

    // ---------- 手动搜索(换源弹窗「自己搜」) ----------

    /**
     * 用户手动输入关键词搜歌词:网易云 + lrclib 两源并行。
     * 手动搜索**不硬卡 ±15s 时长**(用户可能就是在找剪辑版/不同版本的视频词),
     * 只按 |偏差| 排序让最接近的排前;每源取前若干条,网易云逐条取词(轻请求)。
     */
    suspend fun searchCandidates(
        keyword: String,
        durationSec: Long,
    ): List<LyricCandidate> = withContext(Dispatchers.IO) { coroutineScope {
        if (keyword.isBlank()) return@coroutineScope emptyList()
        val netease = async {
            neteaseSearch(keyword, limit = 10).map { s ->
                async {
                    val lyric = neteaseLyric(s.id) ?: return@async null
                    val lines = parseLrc(lyric.first)
                    if (lines.isEmpty()) return@async null
                    val trans = lyric.second
                    val merged = if (trans != null) mergeTranslation(lines, parseLrc(trans)) else lines
                    LyricCandidate(
                        source = "网易云",
                        remoteId = s.id.toString(),
                        displayTitle = listOf(s.name, s.artists?.joinToString(",") { it.name })
                            .filterNotNull().filter { it.isNotBlank() }.joinToString(" / ")
                            .ifBlank { keyword },
                        lines = merged,
                        durationDeltaSec = (s.duration / 1000 - durationSec),
                    )
                }
            }.awaitAll().filterNotNull()
                .let { list -> if (durationSec > 0) list.filter { kotlin.math.abs(it.durationDeltaSec) <= 30 }.take(5) else list.take(5) }
        }
        val lrc = async {
            lrclibTracks(keyword).filter { !it.syncedLyrics.isNullOrBlank() }
                .let { list ->
                    if (durationSec > 0) list.sortedBy { t -> kotlin.math.abs(t.duration.toLong() - durationSec) } else list
                }
                .take(3)
                .map { t ->
                    LyricCandidate(
                        source = "lrclib",
                        remoteId = "",
                        displayTitle = t.trackName + " / " + t.artistName,
                        lines = parseLrc(t.syncedLyrics!!),
                        durationDeltaSec = (t.duration.toLong() - durationSec),
                    )
                }
        }
        (netease.await() + lrc.await())
            .distinctBy { it.source + it.displayTitle }
            .sortedBy { kotlin.math.abs(it.durationDeltaSec) }
    } }

    private fun neteaseLyric(songId: Long): Pair<String, String?>? = runCatching {
        val url = "https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1"
        val resp = json.decodeFromString<NeteaseLyricResponse>(
            get(url, referer = "https://music.163.com/", client = thirdPartyClient),
        )
        val main = resp.lrc?.lyric.orEmpty()
        val trans = resp.tlyric?.lyric?.takeIf { it.isNotBlank() }
        main.takeIf { it.isNotBlank() }?.let { it to trans }
    }.getOrNull()

    // ---------- lrclib ----------

    /**
     * lrclib.net 搜索:响应数组每项直接带 syncedLyrics(LRC),省一次取词请求。
     * 时长校验 ±15s(durationSec 未知则放行)。lrclib 要求 UA 标注应用,不复用浏览器 UA。
     */
    private fun lrclibSearch(keyword: String, durationSec: Long): List<LyricLine> {
        val hit = lrclibTracks(keyword).firstOrNull { t ->
            !t.syncedLyrics.isNullOrBlank() &&
                (durationSec <= 0 || kotlin.math.abs(t.duration.toLong() - durationSec) <= 15)
        } ?: return emptyList()
        return parseLrc(hit.syncedLyrics!!)
    }

    /** lrclib 原始 track 列表(换源候选用:每项自带 syncedLyrics,无需二次请求)。 */
    private fun lrclibTracks(keyword: String): List<LrclibTrack> {
        val url = "https://lrclib.net/api/search?q=" + java.net.URLEncoder.encode(keyword, "UTF-8")
        return runCatching {
            val request = Request.Builder().url(url)
                .header("User-Agent", "AvSong/1.0 (bilibili music player)")
                .get().build()
            thirdPartyClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                json.decodeFromString<List<LrclibTrack>>(resp.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    // ---------- HTTP ----------

    private fun get(url: String, referer: String, client: okhttp3.OkHttpClient): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BiliConstants.UA)
            .header("Referer", referer)
            .get()
            .build()
        client.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code} $url")
            return it.body?.string() ?: throw IOException("空响应")
        }
    }

    private companion object {
        // 网易云用独立 client:不带 B 站 Cookie/buvid,防凭据发给第三方域
        val thirdPartyClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            // 同 BiliHttp:仅 debug 包挂日志(release 不泄漏请求行)
            .apply {
                if (dev.avsong.BuildConfig.DEBUG) {
                    addInterceptor(
                        okhttp3.logging.HttpLoggingInterceptor().apply {
                            level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .build()
    }
}

/** 一行歌词:时间(毫秒)+ 文本 + 可选翻译行。 */
data class LyricLine(val timeMs: Long, val text: String, val sub: String? = null)

/**
 * 换源候选:一个来自网易云/lrclib/B站字幕的完整歌词版本。
 * [lines] 在列候选时就取好(网易云取词快、lrclib 自带,避免选中后再等一次网络)。
 */
data class LyricCandidate(
    val source: String,
    val remoteId: String,
    val displayTitle: String,
    val lines: List<LyricLine>,
    /** 候选时长与当前视频时长的偏差(秒),0 表示对齐/未知。 */
    val durationDeltaSec: Long,
)

/**
 * 把翻译歌词按时间戳就近合并进主歌词。
 * 网易云 tlyric 的时间轴常与原文错位(翻译行少/时间偏移),
 * 策略:翻译行时间落在主行 [t, 下一行t) 区间内则归属该行;容差 ±1.5s。
 */
internal fun mergeTranslation(main: List<LyricLine>, trans: List<LyricLine>): List<LyricLine> {
    if (trans.isEmpty()) return main
    val subs = arrayOfNulls<String>(main.size)
    var ti = 0
    for (t in trans) {
        // 找 main 中 timeMs <= t.timeMs + 容差 的最后一行
        while (ti < main.size && main[ti].timeMs <= t.timeMs + 1500) ti++
        val idx = ti - 1
        if (idx in main.indices && subs[idx] == null) subs[idx] = t.text
    }
    return main.mapIndexed { i, line -> line.copy(sub = subs[i]) }
}

/**
 * LRC 解析:"[mm:ss.xx]文本",支持一行多时间标签;
 * 跳过元信息行([ar:] [ti:] [by:] [offset:] 等),去重排序。
 */
fun parseLrc(raw: String): List<LyricLine> {
    val timeTag = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    val metaOnly = Regex("""^\[(ar|ti|by|al|au|offset|length):.*]$""", RegexOption.IGNORE_CASE)
    val out = LinkedHashMap<Long, String>()

    for (line in raw.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || metaOnly.matches(trimmed)) continue
        val tags = timeTag.findAll(trimmed).toList()
        if (tags.isEmpty()) continue
        val text = trimmed.substring(tags.last().range.last + 1).trim()
        if (text.isEmpty()) continue
        for (t in tags) {
            val min = t.groupValues[1].toLong()
            val sec = t.groupValues[2].toLong()
            val frac = t.groupValues[3].let { f ->
                when (f.length) {
                    0 -> 0L
                    1 -> f.toLong() * 100
                    2 -> f.toLong() * 10
                    else -> f.take(3).toLong()
                }
            }
            val ms = min * 60_000 + sec * 1000 + frac
            out.putIfAbsent(ms, text)
        }
    }
    return out.entries.sortedBy { it.key }.map { LyricLine(it.key, it.value) }
}
