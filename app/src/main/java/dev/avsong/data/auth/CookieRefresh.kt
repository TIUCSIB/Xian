package dev.avsong.data.auth

import android.util.Base64
import dev.avsong.data.BiliConstants
import dev.avsong.data.BiliHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * B 站 Cookie 续期(登录态保活)。SESSDATA 临近过期时用 refresh_token 换一套新 Cookie,
 * 不用重新扫码。链路(catalog §6.2;实测补正见下):
 *
 * 1. `cookie/info?csrf=<bili_jct>` → `data.refresh` 是否需要续期,**`data.timestamp` 是加密基准**
 * 2. RSA-OAEP(SHA-256) 加密 `refresh_<timestamp>` → 十六进制 = correspondPath
 * 3. GET `www.bilibili.com/correspond/1/<path>` → 响应是 **gzip**,解压后抠 `<div id="1-name">…</div>` = refresh_csrf
 * 4. POST `cookie/refresh`(csrf=bili_jct, refresh_csrf, refresh_token=ac_time_value, source=main_web)
 *    → Set-Cookie 给新 SESSDATA/bili_jct/DedeUserID,body.data.refresh_token 是新令牌
 * 5. POST `confirm/refresh`(csrf=**新** bili_jct, refresh_token=**旧** ac_time_value)使旧的失效
 *
 * ⚠ 两个实测要点(公开实现里没写清,踩过):
 * - **correspondPath 必须用服务端 `cookie/info` 返回的 timestamp 加密**,用本地时间戳一律 404
 *   (本地时钟与服务器有偏差;该字段就是为此提供的基准)
 * - correspond 页面响应带 `Content-Encoding: gzip`,不解压抠不到 refresh_csrf
 */
object CookieRefresh {

    private const val PASSPORT = "https://passport.bilibili.com"
    private const val WWW = "https://www.bilibili.com"

    /** 这是 B 站网页端固定的公钥(用于加密 refresh_{ts}),公开且不变。 */
    private const val PUBLIC_KEY_PEM = """MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDLgd2OAkcGVtoE3ThUREbio0Eg
Uc/prcajMKXvkCKFCWhJYJcLkcM2DKKcSeFpD/j6Boy538YXnR6VhcuUJOhH2x71
nzPjfdTcqMz7djHum0qSZA0AyCBDABUqCrfNgCiJ00Ra7GmRj+YCK1NJEuewlb40
JNrRuoEUXpabUzGB8QIDAQAB"""

    private val json = Json { ignoreUnknownKeys = true }

    /** 串行化续期(见 checkAndRefresh 注释);进程内只成功续一次。 */
    private val mutex = Mutex()
    private var refreshedThisProcess = false

    @Serializable
    private data class CookieInfoResponse(val code: Int = -1, val data: CookieInfoData? = null)

    @Serializable
    private data class CookieInfoData(
        val refresh: Boolean = false,
        val timestamp: Long = 0,
    )

    @Serializable
    private data class RefreshResponse(
        val code: Int = -1,
        val message: String = "",
        val data: RefreshData? = null,
    )

    @Serializable
    private data class RefreshData(@SerialName("refresh_token") val refreshToken: String = "")

    /** 续期结果:成功带新凭据;NotNeeded 表示服务端说还不用续;Failed 带原因(仅日志用)。 */
    sealed interface Result {
        data class Success(
            val sessData: String,
            val biliJct: String,
            val dedeUserId: String,
            val acTimeValue: String,
        ) : Result
        data object NotNeeded : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * 检查并(需要时)执行续期。调用方拿到 Success 后应落盘新凭据。
     * 任何一步失败都只返回 Failed,不改动本地状态 —— 续期是「锦上添花」,失败不该影响已有登录。
     *
     * ⚠ 串行化 + 进程内去重:启动检查和「我的」页检查可能并发触发,
     * 而续期会轮换 Cookie —— 并发跑两次会导致两套新 Cookie 互相覆盖(半新半旧 → 接口全 403)。
     */
    suspend fun checkAndRefresh(auth: AuthStore.AuthState): Result = mutex.withLock {
        if (refreshedThisProcess) return@withLock Result.NotNeeded
        if (!auth.canRefresh) return@withLock Result.Failed("缺少 ac_time_value,无法续期(需重新扫码)")
        val sess = auth.sessData ?: return@withLock Result.Failed("无 SESSDATA")
        val jct = auth.biliJct ?: return@withLock Result.Failed("无 bili_jct")
        val uid = auth.dedeUserId.orEmpty()
        val acTime = auth.acTimeValue ?: return@withLock Result.Failed("无 ac_time_value")

        try {
            val info = withContext(Dispatchers.IO) { fetchCookieInfo(sess, jct, uid) }
                ?: return@withLock Result.Failed("cookie/info 请求失败")
            if (info.code != 0) return@withLock Result.Failed("cookie/info code=${info.code}")
            val data = info.data ?: return@withLock Result.Failed("cookie/info 无数据")
            if (!data.refresh) return@withLock Result.NotNeeded

            val refreshCsrf = withContext(Dispatchers.IO) { fetchRefreshCsrf(sess, jct, uid, data.timestamp) }
                ?: return@withLock Result.Failed("获取 refresh_csrf 失败")

            val result = withContext(Dispatchers.IO) { refreshCookies(sess, jct, uid, acTime, refreshCsrf) }
            if (result is Result.Success) refreshedThisProcess = true
            result
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fetchCookieInfo(sess: String, jct: String, uid: String): CookieInfoResponse? {
        val url = "$PASSPORT/x/passport-login/web/cookie/info?csrf=$jct"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BiliConstants.UA)
            .header("Referer", "$WWW/")
            .header("Cookie", cookieHeader(sess, jct, uid))
            .get()
            .build()
        BiliHttp.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return json.decodeFromString(CookieInfoResponse.serializer(), resp.body?.string() ?: return null)
        }
    }

    /**
     * RSA-OAEP(SHA-256) 加密 `refresh_<serverTimestamp>` → hex。
     *
     * ⚠ Android/JDK 的 `OAEPWithSHA-256AndMGF1Padding` **默认 MGF1 用 SHA-1**(历史遗留),
     * 而 B 站按「OAEP-SHA256 + MGF1-SHA256」解密 —— 不显式指定 MGF1 参数会算出错误的密文,
     * 表现为 correspond 页面 404(和用错时间戳同一个症状,极易误判)。必须显式传 OAEPParameterSpec。
     */
    private fun correspondPath(timestamp: Long): String {
        val der = Base64.decode(PUBLIC_KEY_PEM.replace("\n", ""), Base64.DEFAULT)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        val spec = javax.crypto.spec.OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            java.security.spec.MGF1ParameterSpec.SHA256,
            javax.crypto.spec.PSource.PSpecified.DEFAULT,
        )
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val encrypted = cipher.doFinal("refresh_$timestamp".toByteArray(Charsets.UTF_8))
        return encrypted.joinToString("") { "%02x".format(it) }
    }

    /**
     * 取 refresh_csrf。⚠ 必须传服务端 timestamp(本地时间会 404);
     * 响应 gzip 由 OkHttp 自动解压(BiliHttp.client 默认带 Accept-Encoding 并透明解压)。
     */
    private fun fetchRefreshCsrf(sess: String, jct: String, uid: String, timestamp: Long): String? {
        val path = correspondPath(timestamp)
        val request = Request.Builder()
            .url("$WWW/correspond/1/$path")
            .header("User-Agent", BiliConstants.UA)
            .header("Referer", "$WWW/")
            .header("Cookie", cookieHeader(sess, jct, uid))
            .get()
            .build()
        BiliHttp.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val html = resp.body?.string() ?: return null
            return Regex("""<div id="1-name">(.+?)</div>""").find(html)?.groupValues?.get(1)
        }
    }

    /** 第 4+5 步:换新 Cookie,再确认(使旧 refresh_token 失效)。 */
    private fun refreshCookies(
        sess: String,
        jct: String,
        uid: String,
        acTime: String,
        refreshCsrf: String,
    ): Result {
        val body = FormBody.Builder()
            .add("csrf", jct)
            .add("refresh_csrf", refreshCsrf)
            .add("refresh_token", acTime)
            .add("source", "main_web")
            .build()
        val request = Request.Builder()
            .url("$PASSPORT/x/passport-login/web/cookie/refresh")
            .header("User-Agent", BiliConstants.UA)
            .header("Referer", "$WWW/")
            .header("Cookie", cookieHeader(sess, jct, uid))
            .post(body)
            .build()

        BiliHttp.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return Result.Failed("refresh HTTP ${resp.code}")
            val text = resp.body?.string().orEmpty()
            val parsed = json.decodeFromString(RefreshResponse.serializer(), text)
            if (parsed.code != 0) return Result.Failed("refresh code=${parsed.code} ${parsed.message}")

            // 新 Cookie 在 Set-Cookie 头里
            val newSess = resp.header("Set-Cookie")?.let { firstCookie(it, "SESSDATA") }
                ?: resp.headers.values("Set-Cookie").firstNotNullOfOrNull { firstCookie(it, "SESSDATA") }
                ?: return Result.Failed("refresh 响应缺少新 SESSDATA")
            val newJct = resp.headers.values("Set-Cookie").firstNotNullOfOrNull { firstCookie(it, "bili_jct") }
                ?: return Result.Failed("refresh 响应缺少新 bili_jct")
            val newUid = resp.headers.values("Set-Cookie").firstNotNullOfOrNull { firstCookie(it, "DedeUserID") }
                ?: uid
            val newToken = parsed.data?.refreshToken?.takeIf { it.isNotBlank() }
                ?: return Result.Failed("refresh 响应缺少新 refresh_token")

            // 确认续期(用新 csrf + 旧 refresh_token),失败不算致命:新 Cookie 已可用
            runCatching { confirmRefresh(newSess, newJct, newUid, acTime) }

            return Result.Success(newSess, newJct, newUid, newToken)
        }
    }

    private fun confirmRefresh(sess: String, newJct: String, uid: String, oldToken: String) {
        val body = FormBody.Builder()
            .add("csrf", newJct)
            .add("refresh_token", oldToken)
            .build()
        val request = Request.Builder()
            .url("$PASSPORT/x/passport-login/web/confirm/refresh")
            .header("User-Agent", BiliConstants.UA)
            .header("Referer", "$WWW/")
            .header("Cookie", cookieHeader(sess, newJct, uid))
            .post(body)
            .build()
        BiliHttp.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("confirm HTTP ${resp.code}")
        }
    }

    /** 从一条 Set-Cookie 里抠出指定 cookie 的值(值可能含 `%2C` 等,原样保留)。 */
    private fun firstCookie(setCookie: String, name: String): String? {
        val prefix = "$name="
        val start = setCookie.indexOf(prefix)
        if (start < 0) return null
        val from = start + prefix.length
        val end = setCookie.indexOf(';', from).let { if (it < 0) setCookie.length else it }
        return setCookie.substring(from, end).takeIf { it.isNotBlank() }
    }

    private fun cookieHeader(sess: String, jct: String, uid: String): String = buildString {
        append("SESSDATA=").append(sess)
        append("; bili_jct=").append(jct)
        if (uid.isNotBlank()) append("; DedeUserID=").append(uid)
    }
}
