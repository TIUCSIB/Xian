package dev.avsong.data.remote

import java.security.MessageDigest

/**
 * B 站 WBI 签名。
 * 参考 https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/misc/sign/wbi.md
 *
 * 用法:调用方把公共参数(含 wts)放入 params,调用 [encWbi] 得到带 w_rid 的完整参数表。
 */
object WbiSign {

    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 54, 34, 20, 51, 36, 52, 22, 25, 57, 4, 59, 56, 6, 30,
    )

    /** 从 img_key + sub_key 派生 32 位 mixin key(可缓存,按 nav 返回的 keys 计算)。 */
    fun getMixinKey(imgKey: String, subKey: String): String {
        val original = imgKey + subKey
        val sb = StringBuilder()
        for (idx in mixinKeyEncTab) {
            if (idx < original.length) sb.append(original[idx])
            if (sb.length == 32) break
        }
        return sb.toString()
    }

    /**
     * 对参数表做 WBI 签名,返回新的参数表(含 wts 与 w_rid)。
     */
    fun encWbi(params: Map<String, String>, mixinKey: String): Map<String, String> {
        val wts = (System.currentTimeMillis() / 1000).toString()
        val sorted = params.toSortedMap() + ("wts" to wts)
        val filtered = sorted.mapValues { (_, v) ->
            v.filterNot { it in "!'()*" }
        }
        val query = filtered.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
        val wRid = md5Hex(query + mixinKey)
        return filtered + ("w_rid" to wRid)
    }

    private fun md5Hex(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
}
