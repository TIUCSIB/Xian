package dev.avsong.data

import dev.avsong.data.BiliConstants.REFERER
import dev.avsong.data.BiliConstants.UA
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

/**
 * B 站请求的公共 Header:Referer 防盗链 + 桌面 UA + 匿名 buvid Cookie。
 * 各端点 Referer 要求不同(search / video / music-center),
 * 拦截器只在请求未显式指定时注入默认值。
 */
object BiliHttp {

    @Volatile
    var buvid3: String? = null
    @Volatile
    var buvid4: String? = null

    /** 登录 Cookie(SESSDATA 等),由 AvSongApp 在启动时接线到 AuthStore。 */
    @Volatile
    var authCookie: String? = null

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(BiliHeaderInterceptor())
            .addNetworkInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
            .build()
    }

    class BiliHeaderInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", UA)
                .apply {
                    if (original.header("Referer") == null) header("Referer", REFERER)
                    if (original.header("Cookie") == null) {
                        val cookie = buildCookie()
                        if (cookie != null) header("Cookie", cookie)
                    }
                }
                .build()
            return chain.proceed(request)
        }

        private fun buildCookie(): String? = buildString {
            val b3 = buvid3
            if (b3 != null) append("buvid3=").append(b3)
            val b4 = buvid4
            if (b4 != null) { if (isNotEmpty()) append("; "); append("buvid4=").append(b4) }
            val auth = authCookie
            if (auth != null) { if (isNotEmpty()) append("; "); append(auth) }
        }.ifBlank { null }
    }
}
