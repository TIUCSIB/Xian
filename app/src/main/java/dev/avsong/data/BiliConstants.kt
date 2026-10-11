package dev.avsong.data

/**
 * B 站接口相关常量。防盗链 Referer 是所有音源/图片请求的硬性要求。
 */
object BiliConstants {
    const val REFERER = "https://www.bilibili.com"
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    const val HOST_API = "https://api.bilibili.com"
    const val HOST_MUSIC = "https://music.bilibili.com"
}
