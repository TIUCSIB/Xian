package dev.avsong.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** /x/v3/fav/folder/created/list-all + collected/list 共用结构 */
@Serializable
data class FavFolderListResponse(
    val code: Int = 0,
    val data: FavFolderListData? = null,
)

@Serializable
data class FavFolderListData(
    val count: Int = 0,
    val list: List<FavFolder> = emptyList(),
)

@Serializable
data class FavFolder(
    val id: Long = 0,
    val fid: Long = 0,
    val mid: Long = 0,
    /** attr==0 公开;非 0 私密。 */
    val attr: Int = 0,
    val title: String = "",
    val cover: String? = null,
    @SerialName("media_count") val mediaCount: Int = 0,
    /** 带 rid+biz_type 查询时返回:1=已收藏 2=在私密夹未收藏 0=未收藏。 */
    @SerialName("fav_state") val favState: Int = 0,
    val upper: FavUpper? = null, // 订阅收藏夹的创建者
)

@Serializable
data class FavUpper(val mid: Long = 0, val name: String = "")

/** /x/v3/fav/resource/list */
@Serializable
data class FavResourceListResponse(
    val code: Int = 0,
    val data: FavResourceListData? = null,
)

@Serializable
data class FavResourceListData(
    val medias: List<FavMedia> = emptyList(),
    val has_more: Boolean = false,
)

@Serializable
data class FavMedia(
    val id: Long = 0, // aid(收藏/取消收藏要用)
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val duration: Long = 0,
    val upper: FavUpper = FavUpper(),
    val cnt_info: FavCntInfo? = null,
)

@Serializable
data class FavCntInfo(val play: Long = 0)

/** 写接口统一响应(add/deal/edit/del)。 */
@Serializable
data class FavWriteResponse(
    val code: Int = 0,
    val message: String = "",
    val data: kotlinx.serialization.json.JsonElement? = null,
)
