package dev.avsong.data.auth

import dev.avsong.data.BiliConstants
import dev.avsong.data.BiliHttp
import dev.avsong.data.remote.NavResponse
import dev.avsong.data.remote.QrGenerateResponse
import dev.avsong.data.remote.QrPollResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.Request
import java.io.IOException

/**
 * B 站网页扫码登录。
 *
 * 流程(端点见 docs/bilibili-api.md §9):
 * 1. generate → data.url(二维码内容)+ data.qrcode_key
 * 2. UI 把 url 画成二维码,用户 B 站 App 扫码确认
 * 3. poll 轮询:code 0 成功 / 86038 过期 / 86090 已扫码待确认 / 86101 未扫码
 *    成功时从 Set-Cookie 提取 SESSDATA / bili_jct / DedeUserID
 * 4. nav 拉用户名与会员状态
 */
object QrLogin {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Qr(val url: String, val key: String)

    sealed interface PollResult {
        data object Waiting : PollResult
        data object Scanned : PollResult
        data object Expired : PollResult
        data class Success(
            val sessData: String,
            val biliJct: String,
            val dedeUserId: String,
        ) : PollResult
    }

    suspend fun generate(): Qr = withContext(Dispatchers.IO) {
        val body = get("$api/x/passport-login/web/qrcode/generate")
        val resp = json.decodeFromString<QrGenerateResponse>(body)
        val d = resp.data ?: throw IOException("二维码生成失败 code=${resp.code}")
        Qr(d.url, d.qrcodeKey)
    }

    suspend fun poll(key: String): PollResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$api/x/passport-login/web/qrcode/poll?qrcode_key=$key&source=main-fe-header")
            .header("User-Agent", BiliConstants.UA)
            .header("Referer", "https://www.bilibili.com/")
            .get()
            .build()
        BiliHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("空响应")
            val resp = json.decodeFromString<QrPollResponse>(body)
            when (resp.data?.code ?: resp.code) {
                0 -> {
                    // 成功:登录 Cookie 在 Set-Cookie 头里
                    val cookies = Cookie.parseAll(response.request.url, response.headers)
                    fun find(name: String) = cookies.firstOrNull { it.name == name }?.value
                    val sess = find("SESSDATA") ?: throw IOException("响应缺少 SESSDATA")
                    PollResult.Success(
                        sessData = sess,
                        biliJct = find("bili_jct").orEmpty(),
                        dedeUserId = find("DedeUserID").orEmpty(),
                    )
                }
                86038 -> PollResult.Expired
                86090 -> PollResult.Scanned
                else -> PollResult.Waiting // 86101 未扫码及其他
            }
        }
    }

    /** 登录后拉资料;失败返回 null(不阻塞登录成功)。 */
    suspend fun fetchProfile(): Profile? = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("${BiliConstants.HOST_API}/x/web-interface/nav")
            val nav = json.decodeFromString<NavResponse>(body).data ?: return@withContext null
            Profile(
                uname = nav.uname,
                face = nav.face,
                vipLabel = nav.vip?.label?.text?.takeIf { it.isNotBlank() && nav.vip?.vipStatus == 1 },
            )
        }.getOrNull()
    }

    data class Profile(val uname: String?, val face: String?, val vipLabel: String?)

    private const val api = "https://passport.bilibili.com"

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", BiliConstants.UA)
            .header("Referer", BiliConstants.REFERER)
            .get().build()
        BiliHttp.client.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code} $url")
            return it.body?.string() ?: throw IOException("空响应体")
        }
    }
}
