package dev.avsong.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 发现类接口 DTO(推荐/热门/排行/最近播放)。
 *
 * 前三个源的条目结构与 view 系完全同构(bvid/cid/pic/title/duration/owner),
 * 差异只在「列表挂在 data.list 还是 data.item」,故共用一个 [VideoItem]。
 */

/** `/x/web-interface/ranking/v2`、`/x/web-interface/popular` */
@Serializable
data class VideoListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: VideoListData? = null,
)

@Serializable
data class VideoListData(
    val list: List<VideoItem> = emptyList(),
    /** 仅 popular 有;true 表示没有下一页。 */
    @SerialName("no_more") val noMore: Boolean = false,
)

/** `/x/web-interface/wbi/index/top/feed/rcmd` —— ⚠ 列表字段名是 item,不是 list */
@Serializable
data class RcmdResponse(
    val code: Int = 0,
    val data: RcmdData? = null,
)

@Serializable
data class RcmdData(
    val item: List<VideoItem> = emptyList(),
)

@Serializable
data class VideoItem(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    /** 秒(不是 "3:46" 那种字符串,与搜索接口不同)。 */
    val duration: Long = 0,
    val owner: VideoOwner? = null,
)

@Serializable
data class VideoOwner(val mid: Long = 0, val name: String = "")

/** `/x/web-interface/history/cursor`(需登录;ps 上限 30,50 会 -400) */
@Serializable
data class HistoryResponse(
    val code: Int = 0,
    val message: String = "",
    val data: HistoryData? = null,
)

@Serializable
data class HistoryData(
    val list: List<HistoryItem> = emptyList(),
)

@Serializable
data class HistoryItem(
    val title: String = "",
    val cover: String = "",
    val history: HistoryInner? = null,
    /** 已观看秒数;`-1` 表示没有进度(刚点开就退出,或已看完)。 */
    val progress: Long = 0,
    val duration: Long = 0,
    @SerialName("author_name") val authorName: String = "",
)

@Serializable
data class HistoryInner(
    val bvid: String = "",
    val cid: Long = 0,
)
