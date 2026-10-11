package dev.avsong.data

import dev.avsong.data.remote.FavFolder
import dev.avsong.data.remote.FavFolderListResponse
import dev.avsong.data.remote.FavResourceListResponse
import dev.avsong.data.remote.FavWriteResponse
import dev.avsong.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * B 站收藏夹(= 歌单)。读接口自动带登录 Cookie(BiliHttp 注入);
 * 写接口(form POST)必须带 csrf=bili_jct,缺登录态直接抛错。
 *
 * 端点实测配方见 docs/bilibili-api.md §11:
 * - 列表: /x/v3/fav/folder/created/list-all?up_mid= (自建) / collected/list (订阅)
 * - 内容: /x/v3/fav/resource/list?media_id=&pn=&ps= (medias[].id 即 aid)
 * - 写:   /x/v3/fav/folder/add|edit|del、/x/v3/fav/resource/deal(加+移合并)、batch-del
 */
class FavoriteRepository(private val auth: dev.avsong.data.auth.AuthStore) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writeMutex = Mutex() // 串行化写操作,避免 csrf 竞态

    data class Folder(
        val id: Long,
        val title: String,
        val cover: String?,
        val mediaCount: Int,
        val isPrivate: Boolean,
        val ownerMid: Long,
        val ownerName: String?,
    )

    data class FolderContents(
        val songs: List<Song>,
        val hasMore: Boolean,
    )

    private val mid: Long
        get() = auth.state.value.dedeUserId?.toLongOrNull()
            ?: throw IOException("请先登录 B 站账号")

    // ---------- 读 ----------

    /**
     * 自建收藏夹。用分页版 created/list 而不是 list-all:
     * list-all 是「选择收藏夹」弹窗的轻量端点,**实测不回封面**(卡片网格会全空),
     * 网页端展示收藏夹列表用的就是 created/list。ps=20 一页,has_more 续拉。
     */
    suspend fun myFolders(): List<Folder> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<Folder>()
        var page = 1
        while (page <= 5) { // 上限 100 个夹,防异常 has_more 死循环
            val resp = json.decodeFromString<FavFolderListResponse>(
                get("$api/x/v3/fav/folder/created/list?up_mid=$mid&type=2&pn=$page&ps=20"),
            )
            if (resp.code != 0) throw IOException("收藏夹接口 code=${resp.code}")
            val data = resp.data ?: break
            folders += data.list.map { f ->
                Folder(
                    id = f.id,
                    title = f.title,
                    cover = f.cover?.fixProtocol(),
                    mediaCount = f.mediaCount,
                    isPrivate = f.attr != 0,
                    ownerMid = f.mid,
                    ownerName = f.upper?.name,
                )
            }
            if (!data.hasMore) break
            page++
        }
        folders
    }

    suspend fun subscribedFolders(): List<Folder> = withContext(Dispatchers.IO) {
        val url = "$api/x/v3/fav/folder/collected/list?up_mid=$mid&pn=1&ps=20"
        decodeFolders(get(url))
    }

    private fun decodeFolders(body: String): List<Folder> {
        val resp = json.decodeFromString<FavFolderListResponse>(body)
        if (resp.code != 0) throw IOException("收藏夹接口 code=${resp.code}")
        return resp.data?.list.orEmpty().map { f ->
            Folder(
                id = f.id,
                title = f.title,
                cover = f.cover?.fixProtocol(),
                mediaCount = f.mediaCount,
                isPrivate = f.attr != 0,
                ownerMid = f.mid,
                ownerName = f.upper?.name,
            )
        }
    }

    suspend fun folderContents(folderId: Long, page: Int = 1): FolderContents =
        withContext(Dispatchers.IO) {
            val url = (api + "/x/v3/fav/resource/list").toHttpUrl().newBuilder()
                .addQueryParameter("media_id", folderId.toString())
                .addQueryParameter("pn", page.toString())
                .addQueryParameter("ps", "20")
                .addQueryParameter("keyword", "")
                .build()
            val body = get(url.toString())
            val resp = json.decodeFromString<FavResourceListResponse>(body)
            if (resp.code != 0) throw IOException("收藏夹内容 code=${resp.code}")
            val d = resp.data
            FolderContents(
                songs = d?.medias.orEmpty().filter { it.bvid.isNotBlank() }.map { m ->
                    Song(
                        bvid = m.bvid,
                        aid = m.id, // resource/list 的 id 字段就是 aid,收藏操作要用
                        title = m.title,
                        artist = m.upper.name,
                        coverUrl = m.cover.fixProtocol(),
                        durationSec = m.duration,
                    )
                },
                hasMore = d?.has_more ?: false,
            )
        }

    /** 某视频已加入哪些收藏夹(fav_state: 1=已收藏 2=私密未收藏 0=未收藏)。 */
    suspend fun checkFavorite(aid: Long): List<Long> = withContext(Dispatchers.IO) {
        val url = "$api/x/v3/fav/folder/created/list-all?up_mid=$mid&type=2&rid=$aid&biz_type=2"
        val resp = json.decodeFromString<FavFolderListResponse>(get(url))
        resp.data?.list.orEmpty().filter { it.favState == 1 }.map { it.id }
    }

    /** 收藏操作要 aid;搜索/榜单/收藏夹结果尽量带上,缺失时经 view 补。 */
    suspend fun fetchAid(bvid: String): Long = withContext(Dispatchers.IO) {
        val url = "$api/x/web-interface/view?bvid=$bvid"
        runCatching {
            json.decodeFromString<dev.avsong.data.remote.ViewResponse>(get(url)).data?.aid ?: 0L
        }.getOrDefault(0L)
    }

    // ---------- 写 ----------

    suspend fun createFolder(title: String, isPrivate: Boolean): Long = write {
        val body = postForm(
            "$api/x/v3/fav/folder/add",
            "title" to title,
            "intro" to "",
            "privilege" to if (isPrivate) "1" else "0",
        )
        val resp = json.decodeFromString<FavWriteResponse>(body)
        if (resp.code != 0) throw IOException("创建失败 code=${resp.code} ${resp.message}")
        (resp.data as? kotlinx.serialization.json.JsonObject)
            ?.get("id")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() }
            ?: 0L
    }

    suspend fun renameFolder(folderId: Long, title: String) = write {
        val body = postForm(
            "$api/x/v3/fav/folder/update?ruaming=0",
            "pubid" to folderId.toString(),
            "title" to title,
            "intro" to "",
        )
        val resp = json.decodeFromString<FavWriteResponse>(body)
        if (resp.code != 0) throw IOException("重命名失败 code=${resp.code}")
    }

    suspend fun deleteFolder(folderId: Long) = write {
        val body = postForm(
            "$api/x/v3/fav/folder/del",
            "pubid" to folderId.toString(),
        )
        val resp = json.decodeFromString<FavWriteResponse>(body)
        if (resp.code != 0) throw IOException("删除失败 code=${resp.code}")
    }

    /**
     * 收藏/取消收藏。addIds/delIds 是 aid 列表(逗号分隔字符串由调用方拼好传入)。
     * resource/deal 一次请求可同时完成加入多个夹 + 移出多个夹。
     */
    suspend fun modify(aid: Long, addIds: String = "", delIds: String = "") = write {
        val body = postForm(
            "$api/x/v3/fav/resource/deal",
            "rid" to aid.toString(),
            "type" to "2",
            "add_media_ids" to addIds,
            "del_media_ids" to delIds,
        )
        val resp = json.decodeFromString<FavWriteResponse>(body)
        if (resp.code != 0) throw IOException("收藏操作失败 code=${resp.code} ${resp.message}")
    }

    // ---------- HTTP ----------

    private suspend fun <T> write(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { writeMutex.withLock { block() } }

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            .header("Referer", "$api/")
            .get().build()
        BiliHttp.client.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code} $url")
            return it.body?.string() ?: throw IOException("空响应")
        }
    }

    private fun postForm(url: String, vararg fields: Pair<String, String>): String {
        val csrf = auth.state.value.biliJct
            ?: throw IOException("缺少 bili_jct,请重新登录")
        val form = FormBody.Builder()
            .add("csrf", csrf)
        fields.forEach { (k, v) -> form.add(k, v) }
        val request = Request.Builder().url(url)
            .header("Referer", "$api/")
            .header("Origin", "https://www.bilibili.com")
            .post(form.build())
            .build()
        BiliHttp.client.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code} $url")
            return it.body?.string() ?: throw IOException("空响应")
        }
    }

    private companion object {
        const val api = BiliConstants.HOST_API
    }
}

private fun String?.fixProtocol(): String? = when {
    this.isNullOrBlank() -> null
    startsWith("//") -> "https:$this"
    startsWith("http://") -> replaceFirst("http://", "https://")
    else -> this
}
