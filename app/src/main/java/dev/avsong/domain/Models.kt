package dev.avsong.domain

import java.util.concurrent.TimeUnit

/**
 * 播放队列里的一首歌。
 *
 * B 站音频直链约 3 小时过期,因此不存 audioUrl:
 * 播放用 [playUri] 得到 bili://<bvid> 自定义 scheme,交给 ExoPlayer 的
 * ResolvingDataSource 在真正加载时现取直链(见 player/BiliDataSources.kt)。
 * 测试用的直链歌曲(fake)可自带 audioUrl,走 https 原样播放。
 */
data class Song(
    val bvid: String,
    /** 搜索结果不含 cid;榜单带但不可信。播放时经 bili:// 现取真实 cid。 */
    val cid: Long? = null,
    /** 收藏操作要 aid(bvid 不行)。搜索/榜单/收藏夹结果尽量带上,缺失时经 view 补。 */
    val aid: Long = 0,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val durationSec: Long = 0,
    /** 仅 fake/直链歌曲携带;真实 B 站歌曲为 null → 走 bili://。 */
    val audioUrl: String? = null,
    /** 从这首开始播放时的起播位置(最近播放用);0 = 从头。 */
    val resumePositionMs: Long = 0,
    /**
     * 列表行的附加提示(如「听到 00:26」),只在列表里拼在歌手名后面显示。
     * ⚠ 不进 artist/meta —— 否则播放器和通知栏也会挂着这个提示。
     */
    val progressHint: String? = null,
) {
    val mediaId: String get() = bvid

    /** 给 ExoPlayer 的播放地址。 */
    fun playUri(): String = audioUrl ?: "bili://$bvid"
}

/** 搜索/歌单结果的分页包装。 */
data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val hasMore: Boolean,
)

fun Long.formatClock(): String {
    if (this <= 0) return "00:00"
    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) - TimeUnit.HOURS.toMinutes(hours)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) - TimeUnit.MINUTES.toSeconds(hours * 60 + minutes)
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
