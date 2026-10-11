package dev.avsong.data

import dev.avsong.domain.PagedResult
import dev.avsong.domain.Song
import dev.avsong.domain.SongRepository

/**
 * 占位数据源:公开可访问的测试音频,用于在真实 B 站 API 出问题时回退验证 UI 链路。
 */
class FakeSongRepository : SongRepository {

    private val catalog = listOf(
        Song(
            bvid = "fake-1",
            title = "_demo 钢琴曲",
            artist = "Kevin MacLeod",
            coverUrl = null,
            durationSec = 38,
            audioUrl = "https://www.kozco.com/tech/piano2-CleanCut.mp3",
        ),
        Song(
            bvid = "fake-2",
            title = "demo 环境音",
            artist = "SoundHelix",
            coverUrl = null,
            durationSec = 430,
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
        ),
        Song(
            bvid = "fake-3",
            title = "示例歌曲",
            artist = "SoundHelix",
            coverUrl = null,
            durationSec = 460,
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
        ),
    )

    override suspend fun search(keyword: String, page: Int): PagedResult<Song> {
        kotlinx.coroutines.delay(300)
        val hits = if (keyword.isBlank()) catalog
        else catalog.filter {
            it.title.contains(keyword, true) || it.artist.contains(keyword, true)
        }
        return PagedResult(hits, page = 1, hasMore = false)
    }

    override suspend fun getRanking(): List<Song> = catalog
}
