package dev.avsong.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * /x/player/v2(非-wbi,实测可用)只取歌词相关字段。
 * subtitle.subtitles 常为空(多数视频无字幕);bgm_info 对音乐/翻唱视频有用。
 */
@Serializable
data class PlayerV2Response(
    val code: Int = 0,
    val data: PlayerV2Data? = null,
)

@Serializable
data class PlayerV2Data(
    val subtitle: PlayerSubtitle? = null,
    @SerialName("bgm_info") val bgmInfo: PlayerBgm? = null,
)

@Serializable
data class PlayerSubtitle(
    val subtitles: List<SubtitleTrack> = emptyList(),
)

@Serializable
data class SubtitleTrack(
    val id: Long = 0,
    val lan: String = "",        // 如 "zh-CN" / "ai-zh"
    @SerialName("lan_doc") val lanDoc: String = "",
    @SerialName("is_ai") val isAi: Boolean = false,
    @SerialName("subtitle_url") val subtitleUrl: String = "",
)

@Serializable
data class PlayerBgm(
    @SerialName("music_id") val musicId: String = "",
    @SerialName("music_title") val musicTitle: String = "", // "发现《XXX》" 需清洗
    @SerialName("music_singer") val musicSinger: String = "",
    val jump_url: String = "",
)

/** subtitle_url 指向的 JSON:body[].from/to/content(秒,浮点)。 */
@Serializable
data class SubtitleBody(
    val body: List<SubtitleLine> = emptyList(),
)

@Serializable
data class SubtitleLine(
    val from: Double = 0.0,
    val to: Double = 0.0,
    val content: String = "",
)
