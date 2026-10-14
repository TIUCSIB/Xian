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
            /**
             * Cookie 续期令牌。⚠ 不在 Set-Cookie 里,也不在 data.url 查询参数里,
             * 而是轮询响应的 **data.refresh_token** 字段(权威实现 bilibili-api login_v2 确认)。
             * 不取出来就没法续期,只能等过期后重新扫码。
             */
            val acTimeValue: String?,
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
                        // 续期令牌:data.refresh_token(不是 url 参数,Set-Cookie 也没有)
                        acTimeValue = resp.data?.refreshToken?.takeIf { it.isNotBlank() },
                    )
                }
                86038 -> PollResult.Expired
                86090 -> PollResult.Scanned
                else -> PollResult.Waiting // 86101 未扫码及其他
            }
        }
    }

    /**
     * nav 结果三分:有效资料 / 服务端明确未登录 / 状态未知(网络失败)。
     * 调用方据此决定「更新资料」还是「清本地会话」——
     * ⚠ 网络失败绝不能当未登录处理(会把还能用的登录误踢掉)。
     */
    sealed interface ProfileResult {
        data class Ok(val profile: Profile) : ProfileResult
        data object Invalid : ProfileResult
        data object Error : ProfileResult
    }

    /** 拉资料并区分结果;登录后与「我的」页静默刷新共用。 */
    suspend fun fetchProfileResult(): ProfileResult = withContext(Dispatchers.IO) {
        try {
            val body = get("${BiliConstants.HOST_API}/x/web-interface/nav")
            val resp = json.decodeFromString<NavResponse>(body)
            val nav = resp.data
            when {
                // -101=账号未登录(会话已失效);code 0 但 isLogin=false 同义。
                // 其它非 0(如 -412 风控)归 Error:不清会话,避免误踢
                resp.code == -101 || (resp.code == 0 && (nav == null || !nav.isLogin)) ->
                    ProfileResult.Invalid

                resp.code != 0 || nav == null -> ProfileResult.Error
                // isLogin=true 但昵称为空属异常响应,保留旧资料不覆盖
                nav.uname.isBlank() -> ProfileResult.Error

                else -> ProfileResult.Ok(
                    Profile(
                        uname = nav.uname,
                        face = nav.face.takeIf { it.isNotBlank() }?.let(::httpsUrl),
                        vipLabel = nav.vip?.label?.text?.takeIf { it.isNotBlank() && nav.vip?.vipStatus == 1 },
                        level = nav.levelInfo?.currentLevel ?: 0,
                        coin = nav.coin,
                    ),
                )
            }
        } catch (e: Exception) {
            ProfileResult.Error
        }
    }

    /** 登录后拉资料;失败/未登录返回 null(不阻塞登录成功)。 */
    suspend fun fetchProfile(): Profile? =
        (fetchProfileResult() as? ProfileResult.Ok)?.profile

    data class Profile(
        val uname: String?,
        val face: String?,
        val vipLabel: String?,
        val level: Int = 0,
        val coin: Double = 0.0,
    )

    /** B 站图片 URL 可能是 `//` 或 `http://` 协议头,统一补成 https(docs 常见坑表)。 */
    private fun httpsUrl(url: String) = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> "https://${url.removePrefix("http://")}"
        else -> url
    }

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
