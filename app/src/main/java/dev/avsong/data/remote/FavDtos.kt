package dev.avsong.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** /x/v3/fav/folder/created/list + collected/list + list-all 共用结构(list-all 无 has_more,取默认值) */
@Serializable
data class FavFolderListResponse(
    val code: Int = 0,
    val data: FavFolderListData? = null,
)

@Serializable
data class FavFolderListData(
    val count: Int = 0,
    val list: List<FavFolder> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class FavFolder(
    val id: Long = 0,
    val fid: Long = 0,
    val mid: Long = 0,
    /** attr==0 公开;非 0 私密。 */
    val attr: Int = 0,
    /**
     * ⚠ 只有 collected/list(「我追的合集/收藏夹」)带这个字段:
     * 11=收藏夹,21=合集(追合集)。合集条目的 id 是 season_id、mid/upper 是 UP 主。
     * 自建夹(created/list、list-all)不带该字段,默认 0。
     */
    val type: Int = 0,
    val title: String = "",
    val cover: String? = null,
    /** 收藏夹简介(created/list 返回;list-all 轻量版无)。 */
    val intro: String = "",
    @SerialName("media_count") val mediaCount: Int = 0,
    /** 带 rid+biz_type 查询时返回:1=已收藏 2=在私密夹未收藏 0=未收藏。 */
    @SerialName("fav_state") val favState: Int = 0,
    val upper: FavUpper? = null, // 订阅收藏夹/合集的创建者
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
    // ⚠ 收藏夹被清空后本接口会回 "medias":null(不是 []),非空声明会让 kotlinx 解析整个响应抛异常
    // → 详情页报 "Expected start of the array '[' but had 'n'"(实测:收藏=1 首时取消收藏后复现)
    val medias: List<FavMedia>? = null,
    val has_more: Boolean = false,
    /** 收藏夹元数据(每页都带):详情页头部简介从这里取。 */
    val info: FavResourceInfo? = null,
)

@Serializable
data class FavResourceInfo(
    val id: Long = 0,
    val title: String = "",
    /** 收藏夹简介(网页端创建时填的 intro)。 */
    val intro: String = "",
    @SerialName("media_count") val mediaCount: Int = 0,
    val cover: String? = null,
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
