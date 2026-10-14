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
    private const val RETRY_WINDOW_MS = 1500L

    /** bvid → 上次解析时间(用于判断是否错误重试)。 */
    private val lastResolveAt = ConcurrentHashMap<String, Long>()
    /** bvid → 当前尝试次数(错误重试递增;换曲/新加载重置)。 */
    private val attempts = ConcurrentHashMap<String, Int>()
    @Volatile
    private var currentBvid: String? = null

    fun create(context: Context): ResolvingDataSource.Factory {
        val http = BiliHttpDataSourceFactory.create()
        // DefaultDataSource 按 scheme 分发:file://(已下载离线播)/content:// → 各自原生源,
        // http(s)(bili:// 解析后的直链)→ 带 Referer 的 BiliHttp。裸挂 http 源会让本地文件打不开(实测踩过)。
        val delegate = androidx.media3.datasource.DefaultDataSource.Factory(context, http)

        return ResolvingDataSource.Factory(delegate,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
                    if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return dataSpec

                    val bvid = uri.host ?: throw IOException("bili:// 缺少 bvid")
                    // 切到新曲目:重置计数
                    if (bvid != currentBvid) {
                        currentBvid?.let { attempts.remove(it); lastResolveAt.remove(it) }
                        currentBvid = bvid
                        attempts[bvid] = 0
                    }

                    val now = SystemClock.elapsedRealtime()
                    val prev = lastResolveAt[bvid]
                    val isRetry = prev != null && now - prev < RETRY_WINDOW_MS
                    lastResolveAt[bvid] = now
                    if (isRetry) {
                        attempts[bvid] = (attempts[bvid] ?: 0) + 1
                    } else {
                        attempts[bvid] = 0 // 正常加载/seek:回到主 CDN
                    }

                    val urls = BiliSongRepository.audioUrlsBlocking(bvid)
                    val attempt = attempts[bvid] ?: 0
                    val url = urls[minOf(attempt, urls.lastIndex)]

                    return dataSpec.buildUpon().setUri(Uri.parse(url)).build()
                }
            },
        )
    }
}
