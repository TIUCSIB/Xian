package dev.avsong.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import dev.avsong.data.BiliSongRepository
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * bili://<bvid> 自定义 scheme 的解析层。
 *
 * ExoPlayer 每次打开数据源都会回调 resolveDataSpec,现取 B 站音频直链(~3h 过期):
 * - 队列放很久再点 / 直链中途失效 → 重新解析拿新 deadline,天然自愈
 * - 主 CDN 打开失败 → ExoPlayer 快速重试(间隔 < RETRY_WINDOW_MS)时轮换到 backupUrl(备用 CDN)
 *
 * 用"两次解析的时间间隔"区分错误重试(极短)与正常加载/seek(较长):
 * 只有前者才递增尝试次数换源,避免 seek 误用 backup。
 */
object BiliDataSources {

    const val SCHEME = "bili"
    /** MV 合成 scheme:`bili-mv://<bvid>` = 音频流+视频流 MergingMediaSource(逐曲 toggle 改写)。 */
    const val MV_SCHEME = "bili-mv"
    private const val RETRY_WINDOW_MS = 1500L

    /** bvid → 上次解析时间(用于判断是否错误重试)。 */
    private val lastResolveAt = ConcurrentHashMap<String, Long>()
    /** bvid → 当前尝试次数(错误重试递增;换曲/新加载重置)。 */
    private val attempts = ConcurrentHashMap<String, Int>()
    @Volatile
    private var currentBvid: String? = null

    /**
     * resolver 通用逻辑:按 blocking 取流函数解析直链并做重试换源。
     * [lane] 区分音/视频:MV 的 MergingMediaSource 两路几乎同时打开,
     * 共用计数会把第二路误判成错误重试、直接落到备用 CDN(实测)。
     */
    private fun resolveTo(
        uri: android.net.Uri,
        dataSpec: DataSpec,
        lane: String,
        fetch: (String) -> List<String>,
    ): DataSpec {
        val bvid = uri.host ?: throw IOException("$SCHEME:// 缺少 bvid")
        val key = "$lane:$bvid"
        // 切到新曲目:重置计数(音/视频任意一路先到都算切换)
        if (bvid != currentBvid) {
            currentBvid?.let { attempts.clear(); lastResolveAt.clear() }
            currentBvid = bvid
        }

        val now = SystemClock.elapsedRealtime()
        val prev = lastResolveAt[key]
        val isRetry = prev != null && now - prev < RETRY_WINDOW_MS
        lastResolveAt[key] = now
        if (isRetry) {
            attempts[key] = (attempts[key] ?: 0) + 1
        } else {
            attempts[key] = 0 // 正常加载/seek:回到主 CDN
        }

        val urls = fetch(bvid)
        val attempt = attempts[key] ?: 0
        val url = urls[minOf(attempt, urls.lastIndex)]

        return dataSpec.buildUpon().setUri(android.net.Uri.parse(url)).build()
    }

    fun create(context: Context): ResolvingDataSource.Factory {
        val http = BiliHttpDataSourceFactory.create()
        // DefaultDataSource 按 scheme 分发:file://(已下载离线播)/content:// → 各自原生源,
        // http(s)(bili:// 解析后的直链)→ 带 Referer 的 BiliHttp。裸挂 http 源会让本地文件打不开(实测踩过)。
        val delegate = androidx.media3.datasource.DefaultDataSource.Factory(context, http)

        return ResolvingDataSource.Factory(delegate,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
                    val scheme = uri.scheme ?: return dataSpec
                    return when {
                        SCHEME.equals(scheme, ignoreCase = true) ->
                            resolveTo(uri, dataSpec, lane = "audio") { BiliSongRepository.audioUrlsBlocking(it) }
                        MV_SCHEME.equals(scheme, ignoreCase = true) ->
                            resolveTo(uri, dataSpec, lane = "video") { bvid ->
                                val cid = BiliSongRepository.viewCidBlocking(bvid)
                                BiliSongRepository.videoUrlsBlocking(bvid, cid)
                            }
                        else -> dataSpec
                    }
                }
            },
        )
    }
}
