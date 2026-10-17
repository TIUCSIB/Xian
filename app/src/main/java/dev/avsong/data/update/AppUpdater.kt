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

    /** 清掉目录里所有旧包(版本号对不上就重下,不能装错版本)。 */
    private fun purgeStale(dir: File, keepVersion: String?) {
        dir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".apk") && f.name != "update-$keepVersion.apk") f.delete()
        }
    }

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
     * 下载指定版本的 APK 到 files/update/。成功后状态转 done,等 UI 调 [install]。
     * 已在下载中则忽略重复触发(检查更新行被连点)。
     *
     * ⚠ 文件名必须带版本号:`update.apk` 固定名会让上一次下载的旧包被当成本次的包,
     * 装完系统报「已安装相同版本」(实测踩过)。旧版本的包在下载前一并清掉。
     */
    fun downloadApk(context: Context, url: String, version: String) {
        if (downloading) return
        downloading = true
        _download.value = DownloadState("running", 0)
        val ctx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dir = File(ctx.filesDir, "update").apply { mkdirs() }
                // 只保留本次要装的版本,其余旧包删除
                purgeStale(dir, keepVersion = version)
                val file = File(dir, "update-$version.apk")
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

    /**
     * 拉起系统安装器。只装 [version] 对应的包——版本对不上就不装(返回 false),
     * 否则会装成磁盘上残留的旧版本,系统提示「已安装相同版本」。
     */
    fun install(context: Context, version: String): Boolean {
        val dir = File(context.filesDir, "update")
        val file = File(dir, "update-$version.apk")
        if (!file.exists()) {
            // 缓存里是别的版本:清掉并让调用方重新下载
            purgeStale(dir, keepVersion = null)
            _download.value = DownloadState("idle")
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        // 安装器已拉起,状态复位:否则下次检查更新会直接复用这次的 done 状态
        _download.value = DownloadState("idle")
        return true
    }
}
