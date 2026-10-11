package dev.avsong.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** /x/frontend/finger/spi */
@Serializable
data class SpiResponse(
    val code: Int = 0,
    val data: SpiData? = null,
)

@Serializable
data class SpiData(
    @SerialName("b_3") val b3: String = "",
    @SerialName("b_4") val b4: String = "",
)

/** /x/web-interface/search/type  (search_type=video) */
@Serializable
data class SearchResponse(
    val code: Int = 0,
    val data: SearchData? = null,
)

@Serializable
data class SearchData(
    val result: List<SearchItem> = emptyList(),
    @SerialName("numResults") val numResults: Int = 0,
    @SerialName("numPages") val numPages: Int = 0,
)

@Serializable
data class SearchItem(
    val bvid: String = "",
    val aid: Long = 0,
    val title: String = "",
    val author: String = "",
    val pic: String = "",
    val duration: String = "",
    val play: Long = 0,
)

/** /x/web-interface/view */
@Serializable
data class ViewResponse(
    val code: Int = 0,
    val data: ViewData? = null,
)

@Serializable
data class ViewData(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val duration: Long = 0,
    val owner: ViewOwner? = null,
    val pages: List<ViewPage> = emptyList(),
)

@Serializable
data class ViewOwner(val mid: Long = 0, val name: String = "")

@Serializable
data class ViewPage(
    val cid: Long = 0,
    val page: Int = 1,
)

/** /x/player/playurl?fnval=16 */
@Serializable
data class PlayUrlResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PlayUrlData? = null,
)

@Serializable
data class PlayUrlData(
    val dash: Dash? = null,
)

@Serializable
data class Dash(
    val audio: List<AudioStream> = emptyList(),
    val flac: Flac? = null,
    val dolby: Dolby? = null,
)

@Serializable
data class Flac(
    val audio: AudioStream? = null,
)

@Serializable
data class Dolby(
    val type: Int = 0,
    val audio: List<AudioStream>? = null, // ⚠ 实测常为 JSON null,必须可空
)

@Serializable
data class AudioStream(
    val id: Int = 0,
    @SerialName("baseUrl") val baseUrl: String = "",
    @SerialName("backupUrl") val backupUrl: List<String> = emptyList(),
    val bandwidth: Long = 0,
    val codecs: String = "",
)

/** /x/centralization/interface/music/comprehensive/web/rank */
@Serializable
data class RankResponse(
    val code: Int = 0,
    val data: RankData? = null,
)

@Serializable
data class RankData(
    val list: List<RankItem> = emptyList(),
)

@Serializable
data class RankItem(
    val bvid: String = "",
    val cid: String = "",
    val aid: String = "",
    @SerialName("music_title") val musicTitle: String = "",
    val author: String = "",
    val cover: String = "",
    // 顶层无时长,时长挂在关联稿件上(秒);可能缺整个对象
    val related_archive: RankRelatedArchive? = null,
)

@Serializable
data class RankRelatedArchive(
    val duration: Long = 0,
)

/** /x/passport-login/web/qrcode/generate */
@Serializable
data class QrGenerateResponse(
    val code: Int = 0,
    val data: QrGenerateData? = null,
)

@Serializable
data class QrGenerateData(
    val url: String = "",
    @SerialName("qrcode_key") val qrcodeKey: String = "",
)

/** /x/passport-login/web/qrcode/poll */
@Serializable
data class QrPollResponse(
    val code: Int = 0,
    val data: QrPollData? = null,
)

@Serializable
data class QrPollData(
    val code: Int = -1,
    val message: String = "",
    val url: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("timestamp") val timestamp: Long = 0,
)

/** /x/web-interface/nav(登录态) */
@Serializable
data class NavResponse(
    val code: Int = 0,
    val data: NavData? = null,
)

@Serializable
data class NavData(
    val isLogin: Boolean = false,
    val uname: String = "",
    val face: String = "",
    val vip: NavVip? = null,
    @SerialName("vipStatus") val vipStatus: Int = 0,
)

@Serializable
data class NavVip(
    @SerialName("vipStatus") val vipStatus: Int = 0,
    val label: NavVipLabel? = null,
)

@Serializable
data class NavVipLabel(
    val text: String = "",
)
