package dev.avsong.data.lyric

import dev.avsong.data.BiliConstants
import dev.avsong.data.BiliHttp
import dev.avsong.data.remote.PlayerV2Response
import dev.avsong.data.remote.SubtitleBody
import dev.avsong.data.remote.ViewResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

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
    )

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
    private val cache = ConcurrentHashMap<String, List<LyricLine>>()
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
            // 「"歌词片段"-歌手《歌名》」式搬运标题:《》里才是真歌名(cleanTitle 会把尾巴整段砍掉,
            // 反而丢了歌名),单独提取作高优先候选
            Regex("《(.+?)》").find(title)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { add(it) }
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

    private fun neteaseSearch(keyword: String): List<NeteaseSong> {
        val url = "https://music.163.com/api/search/get/web" +
            "?s=${java.net.URLEncoder.encode(keyword, "UTF-8")}&type=1&offset=0&limit=5"
        return runCatching {
            json.decodeFromString<NeteaseSearchResponse>(get(url, referer = "https://music.163.com/", client = thirdPartyClient)).result?.songs.orEmpty()
        }.getOrDefault(emptyList())
    }

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
        val url = "https://lrclib.net/api/search?q=" + java.net.URLEncoder.encode(keyword, "UTF-8")
        val tracks = runCatching {
            val request = Request.Builder().url(url)
                .header("User-Agent", "AvSong/1.0 (bilibili music player)")
                .get().build()
            thirdPartyClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                json.decodeFromString<List<LrclibTrack>>(resp.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
        val hit = tracks.firstOrNull { t ->
            !t.syncedLyrics.isNullOrBlank() &&
                (durationSec <= 0 || kotlin.math.abs(t.duration - durationSec) <= 15)
        } ?: return emptyList()
        return parseLrc(hit.syncedLyrics!!)
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
            .addInterceptor(
                okhttp3.logging.HttpLoggingInterceptor().apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                },
            )
            .build()
    }
}

/** 一行歌词:时间(毫秒)+ 文本 + 可选翻译行。 */
data class LyricLine(val timeMs: Long, val text: String, val sub: String? = null)

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
