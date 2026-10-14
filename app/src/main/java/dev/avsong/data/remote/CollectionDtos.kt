package dev.avsong.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * UP 主合集(歌单化)相关 DTO。
 *
 * `/x/polymer/web-space/seasons_archives_list` 一次请求同时给 meta(合集名/封面/总数)
 * 和当前页曲目,翻页只是换 page_num。
 * ⚠ archives 条目实测**没有 owner 字段**(cid 也可能为 null)——UP 名用 card 接口按 mid 单独取,
 * cid 不进 DTO(播放永远经 bili:// 现取)。
 */
@Serializable
data class SeasonArchivesResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SeasonArchivesData? = null,
)

@Serializable
data class SeasonArchivesData(
    val meta: SeasonMeta? = null,
    val archives: List<SeasonArchive> = emptyList(),
    val page: SeasonPage? = null,
)

@Serializable
data class SeasonMeta(
    @SerialName("season_id") val seasonId: Long = 0,
    val mid: Long = 0,
    val name: String = "",
    /** 合集显示名(实测 title 是去掉「合集·」前缀的短名,name 带「合集·」前缀)。 */
    val title: String = "",
    val total: Int = 0,
    val cover: String = "",
)

@Serializable
data class SeasonArchive(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    /** 秒。 */
    val duration: Long = 0,
)

@Serializable
data class SeasonPage(
    @SerialName("page_size") val pageSize: Int = 0,
    val total: Int = 0,
)

/** `/x/web-interface/card?mid=` —— 匿名可用,取 UP 名(合集条目没有 owner)。 */
@Serializable
data class CardResponse(
    val code: Int = 0,
    val data: CardData? = null,
)

@Serializable
data class CardData(
    val name: String = "",
)

/**
 * `/x/space/fav/season/list?season_id=` —— 网页端「我追的合集」内容接口(匿名可用)。
 * 本 app 只用它取 info.upper.mid:追合集条目可能不带 UP mid,
 * 而 seasons_archives_list 需要 mid 才能取完整 meta + 曲目。
 */
@Serializable
data class FavSeasonInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: FavSeasonInfoData? = null,
)

@Serializable
data class FavSeasonInfoData(
    val info: FavSeasonInfo? = null,
)

@Serializable
data class FavSeasonInfo(
    val id: Long = 0,
    val title: String = "",
    val upper: FavUpper? = null,
)
