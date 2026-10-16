package dev.avsong.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.avsong.data.remote.CardResponse
import dev.avsong.data.remote.FavSeasonInfoResponse
import dev.avsong.data.remote.SeasonArchivesResponse
import dev.avsong.domain.Song
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Request

/** 「我的」页 DataStore(已保存合集)。 */
private val Context.collectionDataStore by preferencesDataStore(name = "collections")

/**
 * UP 主合集 = 歌单。
 *
 * B 站没有「订阅的合集」列表接口,这里走本地收藏:粘贴
 * `space.bilibili.com/<mid>/lists/<sid>?type=season` 链接 → 拉 meta 存本地(DataStore)
 * → 点开按页拉曲目播放。任何人的合集都能存(实测匿名可用,不需要登录)。
 * ⚠ 仅支持 type=season(合集);type=series(系列)是另一套接口,暂未接。
 */
object CollectionRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private const val baseUrl = "https://api.bilibili.com"

    data class Meta(
        val mid: Long,
        val seasonId: Long,
        val title: String,
        val cover: String?,
        val total: Int,
        val intro: String = "",
    )

    data class Page(
        val meta: Meta?,
        val songs: List<Song>,
        val hasMore: Boolean,
    )

    /**
     * 从分享文本里提取合集 (mid, seasonId)。
     * `space.bilibili.com/3493093607213343/lists/8762233?type=season` 这类链接都能命中;
     * `type=series` 是「系列」(另一套接口),明确报错而不是静默当合集处理。
     */
    fun parse(text: String): Pair<Long, Long>? {
        if (Regex("type=series").containsMatchIn(text)) throw IOException("「系列」暂不支持,请用合集(type=season)链接")
        val m = Regex("space\\.bilibili\\.com/(\\d+)/lists/(\\d+)").find(text) ?: return null
        return m.groupValues[1].toLong() to m.groupValues[2].toLong()
    }

    /**
     * 取合集一页。meta 在每一页都会返回,首页顺带当详情用。
     * 条目没有 owner → UP 名按 mid 取一次(card 接口,进程内缓存),整合集共用。
     * [mid] <= 0 时(「我追的合集」条目可能不带 UP mid)按 season_id 反查一次补上。
     */
    suspend fun page(mid: Long, seasonId: Long, pageNum: Int, pageSize: Int = 20): Page =
        withContext(Dispatchers.IO) {
            val ownerMid = if (mid > 0) mid else resolveUpperMid(seasonId)
            val resp = getJson(
                "$baseUrl/x/polymer/web-space/seasons_archives_list" +
                    "?mid=$ownerMid&season_id=$seasonId&page_num=$pageNum&page_size=$pageSize",
                referer = "https://space.bilibili.com/$ownerMid/",
            )
            val parsed = json.decodeFromString<SeasonArchivesResponse>(resp)
            if (parsed.code != 0) throw IOException("合集加载失败 ${parsed.code}: ${parsed.message}")
            val data = parsed.data ?: throw IOException("合集不存在或已删除")
            val upper = upperNameBlocking(ownerMid)
            Page(
                meta = data.meta?.let {
                    Meta(
                        mid = ownerMid,
                        seasonId = seasonId,
                        title = it.title.ifBlank { it.name }.ifBlank { "未命名合集" },
                        cover = it.cover.fixProtocol(),
                        total = it.total,
                        intro = it.intro,
                    )
                },
                songs = data.archives.mapNotNull { a ->
                    if (a.bvid.isBlank()) return@mapNotNull null
                    Song(
                        bvid = a.bvid,
                        cid = null, // 播放永远经 bili:// 现取真实 cid
                        aid = a.aid,
                        title = a.title.stripEmTags().ifBlank { "未知曲目" },
                        artist = upper.orEmpty(),
                        coverUrl = a.pic.fixProtocol(),
                        durationSec = a.duration,
                    )
                },
                hasMore = (data.page?.total ?: data.meta?.total ?: 0) > pageNum * pageSize,
            )
        }

    /** 按 season_id 反查合集 UP 主(网页端「我追的合集」用的接口,匿名可用)。 */
    private suspend fun resolveUpperMid(seasonId: Long): Long {
        val resp = getJson(
            "$baseUrl/x/space/fav/season/list?season_id=$seasonId&pn=1&ps=1",
            referer = BiliConstants.REFERER,
        )
        val parsed = json.decodeFromString<FavSeasonInfoResponse>(resp)
        return parsed.data?.info?.upper?.mid?.takeIf { it > 0 }
            ?: throw IOException("找不到合集作者(season_id=$seasonId)")
    }

    private val upperNames = ConcurrentHashMap<Long, String>()

    private suspend fun upperNameBlocking(mid: Long): String? {
        upperNames[mid]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val resp = getJson("$baseUrl/x/web-interface/card?mid=$mid", referer = BiliConstants.REFERER)
                json.decodeFromString<CardResponse>(resp).data?.name?.takeIf { it.isNotBlank() }
            }.getOrNull()?.also { upperNames[mid] = it }
        }
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

/** 本地保存的合集(不是 B 站收藏夹,也不是自建合集,是「我收藏的合集入口」)。 */
@Serializable
data class SavedCollection(
    val mid: Long,
    val seasonId: Long,
    val title: String,
    val cover: String? = null,
    val total: Int = 0,
)

/** 已保存合集的 DataStore 持久化,最新添加在前,按 (mid, seasonId) 去重。 */
class CollectionStore(context: Context) {

    private val datastore = context.applicationContext.collectionDataStore
    private val _state = MutableStateFlow<List<SavedCollection>>(emptyList())
    val state: StateFlow<List<SavedCollection>> = _state.asStateFlow()

    suspend fun load() {
        val raw = datastore.data.first()[stringPreferencesKey(KEY)] ?: return
        _state.value = runCatching {
            json.decodeFromString(ListSerializer(SavedCollection.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun add(item: SavedCollection) {
        persist(listOf(item) + _state.value.filterNot {
            it.mid == item.mid && it.seasonId == item.seasonId
        })
    }

    suspend fun remove(mid: Long, seasonId: Long) {
        persist(_state.value.filterNot { it.mid == mid && it.seasonId == seasonId })
    }

    /** 按 seasonId 移除(取消订阅用;追合集条目可能没有 mid,按 mid+season 匹配会漏)。 */
    suspend fun removeBySeasonId(seasonId: Long) {
        persist(_state.value.filterNot { it.seasonId == seasonId })
    }

    private suspend fun persist(list: List<SavedCollection>) {
        _state.value = list
        datastore.edit {
            it[stringPreferencesKey(KEY)] =
                json.encodeToString(ListSerializer(SavedCollection.serializer()), list)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val KEY = "collections"
    }
}

// ---------- 小工具(与 BiliSongRepository.kt 同款,该处为 private 无法复用) ----------

/** 搜索标题可能含 <em class="keyword">高亮标签,剥离。 */
private fun String.stripEmTags(): String = replace(Regex("<[^>]+>"), "")

/** //i0.hdslb.com/... → https://i0.hdslb.com/... */
private fun String.fixProtocol(): String? = when {
    isBlank() -> null
    startsWith("//") -> "https:$this"
    startsWith("http://") -> replaceFirst("http://", "https://")
    else -> this
}
