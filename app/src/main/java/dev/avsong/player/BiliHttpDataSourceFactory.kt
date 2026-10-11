package dev.avsong.player

import androidx.media3.datasource.DefaultHttpDataSource
import dev.avsong.data.BiliConstants

/**
 * 带 Referer 的 HTTP DataSource。B 站音频直链必须带 Referer,否则 403。
 * resolver 层已把 bili:// 换成 https 直链,这里只会收到真实 http(s) 请求。
 */
object BiliHttpDataSourceFactory {
    fun create(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(BiliConstants.UA)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to BiliConstants.REFERER,
                    "Origin" to "https://www.bilibili.com",
                ),
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
}
