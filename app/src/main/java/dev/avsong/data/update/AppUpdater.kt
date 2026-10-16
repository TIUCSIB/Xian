package dev.avsong.data.update

import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内自更新(纯客户端,更新源 = GitHub Releases):
 * 1. [checkLatest] 拉 releases/latest 的 tag 与本地版本比对(返回版本号与 APK 直链)
 * 2. [downloadApk] 把 release 资产下载到 files/update/(进度经 StateFlow 回显 UI)
 * 3. [install] 经 FileProvider 拉起系统安装器(REQUEST_INSTALL_PACKAGES,首次会引导授权)
 *
 * 网络 HttpURLConnection + IO 线程:不带 B 站头(BiliHttp 不适用),也无 JSON 依赖
 * (tag 与资产 URL 用正则抠)。GitHub API 限流 60/h/IP,检查更新频度足够。
 */
object AppUpdater {

    data class Latest(
        val version: String,     // 去 v 前缀,如 "0.2.1"
        val apkUrl: String,      // release 资产里 .apk 的 browser_download_url
    )

    /** 下载态:idle=未开始;running(progress 0-100);done=完成待安装;failed=失败(带原因) */
    data class DownloadState(val status: String, val progress: Int = 0, val message: String? = null)

    private val _download = MutableStateFlow(DownloadState("idle"))
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    private var downloading = false

    /** 查最新版;返回 null = 查不到(网络/限流)。 */
    suspend fun checkLatest(): Latest? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://api.github.com/repos/TIUCSIB/Xian/releases/latest")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val body = conn.inputStream.use { it.bufferedReader().readText() }
            val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: return@runCatching null
            val apk = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"").find(body)
                ?.groupValues?.get(1)
                ?: return@runCatching null
            Latest(tag.removePrefix("v"), apk)
        }.getOrNull()
    }

    /**
     * 下载最新 APK 到 files/update/(覆盖旧文件)。成功后状态转 done,等 UI 调 [install]。
     * 已在下载中则忽略重复触发(检查更新行被连点)。
     */
    fun downloadApk(context: Context, url: String) {
        if (downloading) return
        downloading = true
        _download.value = DownloadState("running", 0)
        val ctx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dir = File(ctx.filesDir, "update").apply { mkdirs() }
                val file = File(dir, "update.apk")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            // 整百分点节流:StateFlow equals 去重已够,这里少造对象
                            if (total > 0) {
                                val pct = (read * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    _download.value = DownloadState("running", pct)
                                }
                            }
                        }
                    }
                }
                if (total <= 0) _download.value = DownloadState("running", 100)
                _download.value = DownloadState("done")
            } catch (e: Exception) {
                _download.value = DownloadState("failed", message = e.message ?: "下载失败")
            } finally {
                downloading = false
            }
        }
    }

    /** 拉起系统安装器(files/update/update.apk 经 FileProvider 授权)。 */
    fun install(context: Context) {
        val file = File(context.filesDir, "update/update.apk")
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
