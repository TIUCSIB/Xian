package dev.avsong.data.remote

import kotlinx.serialization.Serializable

/** /main/suggest 搜索联想(s.search.bilibili.com,匿名可用)。 */
@Serializable
data class SearchSuggestResponse(
    val code: Int = 0,
    val result: SuggestResult? = null,
)

@Serializable
data class SuggestResult(
    val tag: List<SuggestTag> = emptyList(),
)

@Serializable
data class SuggestTag(
    /** 纯文本联想词(name 字段含 <em> 高亮标签,勿直接展示)。 */
    val value: String = "",
)
