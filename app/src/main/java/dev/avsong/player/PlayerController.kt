package dev.avsong.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI 与 MusicService 之间的桥接层。
 *
 * 连接 MediaController 是异步的(在 IO 线程等待),绝不在主线程阻塞;
 * 控制指令通过内部 scope 异步下发;状态经 [playerState] 流推送给 Compose。
 */
class PlayerController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var controller: MediaController? = null

    /** 挂起直到 controller 就绪(首次会绑定服务)。不阻塞调用线程。 */
    private suspend fun controller(): MediaController = withContext(Dispatchers.IO) {
        controller?.let { if (it.isConnected) return@withContext it }
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val ctrl = MediaController.Builder(context, token).buildAsync().get()
        controller = ctrl
        ctrl
    }

    /** 已连接时同步执行,未连接则异步排队执行。 */
    private inline fun withController(crossinline block: (MediaController) -> Unit) {
        val ctrl = controller
        if (ctrl != null && ctrl.isConnected) {
            block(ctrl)
        } else {
            scope.launch { block(controller()) }
        }
    }

    /**
     * 整队入列并播放。[startPositionMs] 用于「最近播放」续播:
     * setMediaItems 第三参就是起播位置,比 prepare 后再 seek 更稳(不会先闪一下 0 位置)。
     */
    fun playQueue(
        items: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
    ) = withController { ctrl ->
        ctrl.setMediaItems(items, startIndex, startPositionMs)
        ctrl.prepare()
        ctrl.play()
    }

    fun playPause() = withController { ctrl ->
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }
    fun skipToNext() = withController { it.seekToNext() }
    fun skipToPrevious() = withController { it.seekToPrevious() }

    /** 出错后重试(重新 prepare,会再次走 bili:// resolver 取新直链)。 */
    fun retry() = withController { ctrl ->
        ctrl.prepare()
        ctrl.play()
    }

    /** 跳到队列第 index 首并播放。 */
    fun playAt(index: Int) = withController { ctrl ->
        if (index in 0 until ctrl.mediaItemCount) {
            ctrl.seekTo(index, 0L)
            ctrl.play()
        }
    }

    /** 从队列移除第 index 首;移除当前播放项时 ExoPlayer 自动前移。 */
    fun removeAt(index: Int) = withController { ctrl ->
        if (index in 0 until ctrl.mediaItemCount) {
            ctrl.removeMediaItem(index)
        }
    }

    /**
     * 把第 from 首移动到 to 位置。
     * Media3 的 moveMediaItem 会自动保持"当前播放项"不变(内部调整索引),
     * 因此无需手动 seekTo——额外 seek 反而会导致跳歌(实测踩过)。
     */
    fun moveItem(from: Int, to: Int) = withController { ctrl ->
        val count = ctrl.mediaItemCount
        if (from in 0 until count && to in 0 until count && from != to) {
            ctrl.moveMediaItem(from, to)
        }
    }

    /**
     * 把当前播放项追加到队尾(不改动播放位置,只让这首歌稍后再来一遍)。
     * MediaItem 是不可变值,直接复制同一实例入队即可。
     */
    fun appendCurrentToQueue() = withController { ctrl ->
        ctrl.currentMediaItem?.let { ctrl.addMediaItem(it) }
    }

    /**
     * 「下一首播放」:插到当前曲后面,不打断播放、不动队列其余部分。
     * 队列为空时退化为单曲入列起播(与 playFromHistory 空队分支一致)。
     */
    fun playNext(item: MediaItem) = withController { ctrl ->
        if (ctrl.mediaItemCount == 0) {
            ctrl.setMediaItems(listOf(item), 0, 0L)
            ctrl.prepare()
            ctrl.play()
        } else {
            ctrl.addMediaItem(ctrl.currentMediaItemIndex + 1, item)
        }
    }

    /** 「加入队列」:追加到队尾,不改动播放位置。 */
    fun addToQueue(item: MediaItem) = withController { ctrl ->
        ctrl.addMediaItem(ctrl.mediaItemCount, item)
    }

    /**
     * 历史回放:曲子已在队列则跳播(不重复入列);队列空则单曲入列;
     * 否则追加到队尾起播 —— 不打乱现有队列。
     */
    fun playFromHistory(item: MediaItem) = withController { ctrl ->
        val idx = (0 until ctrl.mediaItemCount).firstOrNull {
            ctrl.getMediaItemAt(it).mediaId == item.mediaId
        }
        when {
            idx != null -> {
                ctrl.seekTo(idx, 0L)
                ctrl.play()
            }
            ctrl.mediaItemCount == 0 -> {
                ctrl.setMediaItems(listOf(item), 0, 0L)
                ctrl.prepare()
                ctrl.play()
            }
            else -> {
                ctrl.addMediaItem(ctrl.mediaItemCount, item)
                ctrl.seekTo(ctrl.mediaItemCount - 1, 0L)
                ctrl.play()
            }
        }
    }

    /** 抽屉模式条的三态显式设置(循环/单曲/随机),不走 toggle。 */
    fun setRepeatMode(mode: Int) = withController { it.repeatMode = mode }
    fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

    /** 一键清空播放队列:同时停止播放(mini 条随 currentMediaId 置空消失)。 */
    fun clearQueue() = withController { it.clearMediaItems() }

    /** 批量追加到队尾(合集后台补全队列用;不打断当前播放)。 */
    fun appendSongsToQueue(items: List<MediaItem>) = withController { ctrl ->
        if (items.isNotEmpty()) ctrl.addMediaItems(ctrl.mediaItemCount, items)
    }

    /** 同步采样进度:仅在已连接时有值,否则 0(供 UI ticker 使用)。 */
    fun currentPosition(): Long =
        controller?.takeIf { it.isConnected }?.currentPosition?.coerceAtLeast(0) ?: 0L

    /** 当前媒体总时长(ms);未连接或未知返回 0。 */
    fun duration(): Long =
        controller?.takeIf { it.isConnected }?.duration?.coerceAtLeast(0) ?: 0L

    fun release() {
        controller?.release()
        controller = null
    }

    /**
     * 持续监听播放器事件,以 [PlayerState] 流推送给 UI。
     * 协程作用域消亡时自动移除 listener。
     */
    fun playerState(): Flow<PlayerState> = callbackFlow {
        val ctrl = controller()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                trySend(ctrl.toState())
            }
        }
        ctrl.addListener(listener)
        trySend(ctrl.toState())
        awaitClose { ctrl.removeListener(listener) }
    }
}

data class PlayerState(
    val currentMediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** 播放错误(如 bili:// 取链失败);null 表示无错误。 */
    val error: String? = null,
    /** 当前队列快照(含当前项);供队列页渲染。 */
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
)

/** 队列里的一项(仅展示字段)。 */
data class QueueEntry(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
)

internal fun MediaController.toState(): PlayerState {
    val item: MediaItem? = currentMediaItem
    val meta: MediaMetadata = item?.mediaMetadata ?: MediaMetadata.EMPTY
    val queue = (0 until mediaItemCount).map { i ->
        val m = getMediaItemAt(i)
        val md = m.mediaMetadata
        QueueEntry(
            mediaId = m.mediaId ?: "",
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            artworkUri = md.artworkUri?.toString(),
        )
    }
    return PlayerState(
        currentMediaId = item?.mediaId,
        title = meta.title?.toString(),
        artist = meta.artist?.toString(),
        artworkUri = meta.artworkUri?.toString(),
        isPlaying = isPlaying,
        positionMs = currentPosition.coerceAtLeast(0),
        durationMs = if (duration > 0) duration else 0,
        shuffleEnabled = shuffleModeEnabled,
        repeatMode = repeatMode,
        error = playerError?.cause?.message ?: playerError?.message,
        queue = queue,
        currentIndex = currentMediaItemIndex,
    )
}
