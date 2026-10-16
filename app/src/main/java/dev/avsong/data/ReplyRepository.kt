package dev.avsong.data

import dev.avsong.data.remote.ReplyResponse
import dev.avsong.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.IOException

/**
 * 视频热评(B 站评论区的热门一条 + 高赞排序)。
 * 端点:`/x/v2/reply/main?type=1&oid=<aid>&mode=3&next=0`(mode=3 按热度;匿名可用,实测 code:0)。
 * ⚠ 只取第一页(20 条):实测翻页到第 3 页起混入大量 0-3 赞的低赞评论,
 * 「热评」语义不成立(用户实测后拍板不翻页);弹幕与列表抽屉都吃这 20 条足够。
 * ⚠ 要 aid 不是 bvid(与收藏同坑);Song.aid 缺失时经 fetchAid 补。
 */
object ReplyRepository {

    data class HotReply(
        val uname: String,
        val message: String,
        val likeCount: Int,
        val rpid: Long = 0,
        /** 本账号点赞态(0/1/2);匿名恒 0。 */
        val action: Int = 0,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** 拉第一页热评(最多 20 条);失败抛 IOException(UI 侧给空态即可)。 */
    suspend fun hotReplies(aid: Long): List<HotReply> = withContext(Dispatchers.IO) {
        if (aid <= 0) throw IOException("无 aid")
        val url = "https://api.bilibili.com/x/v2/reply/main?type=1&oid=$aid&mode=3&next=0"
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://www.bilibili.com")
            .get()
            .build()
        dev.avsong.data.BiliHttp.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw IOException("空响应体")
            val parsed = json.decodeFromString(ReplyResponse.serializer(), body)
            if (parsed.code != 0) throw IOException("reply code=${parsed.code}")
            parsed.data?.replies.orEmpty().map {
                HotReply(
                    uname = it.member.uname,
                    message = it.content.message,
                    likeCount = it.like,
                    rpid = it.rpid,
                    action = it.action,
                )
            }
        }
    }

    /** 便捷:对 Song 拉(补 aid);失败返回空列表(UI 显示「暂无评论」)。 */
    suspend fun hotRepliesFor(song: Song, fetchAid: suspend (String) -> Long): List<HotReply> {
        val aid = if (song.aid > 0) song.aid else runCatching { fetchAid(song.bvid) }.getOrDefault(0L)
        return runCatching { hotReplies(aid) }.getOrDefault(emptyList())
    }

    /**
     * 评论点赞([ReplyRepository] 内唯一写操作):POST /x/v2/reply/action。
     * [like] true=点赞 false=取消;需登录(csrf=bili_jct),匿名调用会返回 -101 由上层提示。
     * 成功后 B 站不回新的赞数,UI 侧按 action 翻转本地 +1/-1 即可。
     */
    suspend fun likeReply(oid: Long, rpid: Long, like: Boolean, csrf: String) = withContext(Dispatchers.IO) {
        val form = okhttp3.FormBody.Builder()
            .add("oid", oid.toString())
            .add("type", "1") // 1=视频评论
            .add("rpid", rpid.toString())
            .add("action", if (like) "1" else "0")
            .add("csrf", csrf)
            .build()
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/v2/reply/action")
            .header("Referer", "https://www.bilibili.com")
            .post(form)
            .build()
        dev.avsong.data.BiliHttp.client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val code = Regex(""""code":(-?\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            if (!resp.isSuccessful || code != 0) throw IOException("点赞失败 code=$code")
        }
    }
}
