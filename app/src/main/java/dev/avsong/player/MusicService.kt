package dev.avsong.player

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import dev.avsong.widget.NowPlayingWidget
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放服务:Media3 托管通知栏、锁屏、蓝牙/线控,无需手写 MediaStyle 通知。
 * 额外职责:播放现场持久化(队列 bvid + 进度 + 模式),进程重启后恢复。
 *
 * 交叉淡入淡出:Media3 1.11.1 没有可用的 crossfade API(全量扫过 aar 确认),
 * 这里手写——曲末 [FADE_OUT_MS] 起音量渐弱,自动切到下一首后 [FADE_IN_MS] 渐强;
 * 手动切歌/暂停/拖动进度一律立刻回满音量,不做淡入淡出(用户主动操作要即时反馈)。
 */
class MusicService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var store: PlaybackPersistenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fadeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 淡入淡出:monitor 每次开播重启,靠这几条状态串联「渐弱→切歌→渐强」
    private var fadeJob: Job? = null
    private var fadeInJob: Job? = null
    private var crossfadeFrom: MediaItem? = null

    // 节流:两次保存至少间隔 3s,避免频繁写盘
    private var lastSaveElapsed = 0L

    override fun onCreate() {
        super.onCreate()
        store = PlaybackPersistenceStore(this)

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                ProgressiveMediaSource.Factory(BiliDataSources.create(this)),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val session = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, dev.avsong.MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

        player = exoPlayer
        mediaSession = session

        exoPlayer.addListener(persistenceListener)
        exoPlayer.addListener(crossfadeListener)
        scope.launch { restore(exoPlayer) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player ?: return
        if (!p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        player?.let { saveSnapshot(it) } // 退出前落盘一次
        player?.removeListener(persistenceListener)
        player?.removeListener(crossfadeListener)
        fadeJob?.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        player?.release()
        player = null
        scope.cancel()
        fadeScope.cancel()
        super.onDestroy()
    }

    // ---------- 恢复 ----------

    private suspend fun restore(exoPlayer: ExoPlayer) {
        // 读盘走 IO;ExoPlayer 只能在主线程操作,切回 Main 再恢复
        val snapshot = withContext(Dispatchers.IO) { store.load() }
        if (snapshot.isEmpty) return
        withContext(Dispatchers.Main) {
            val items = snapshot.items.map { it.toMediaItem() }
            exoPlayer.setMediaItems(items, snapshot.startIndex.coerceIn(0, items.size - 1), snapshot.positionMs)
            exoPlayer.shuffleModeEnabled = snapshot.shuffle
            exoPlayer.repeatMode = snapshot.repeatMode
            exoPlayer.prepare()
            // 只在"上次正在播放"时自动续播,否则静默恢复现场
            if (snapshot.playWhenReady) exoPlayer.play()
        }
    }

    private fun PlaybackPersistenceStore.PersistedItem.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(bvid)
            // 已下载曲带 file://,直接离线还原;旧快照/在线曲回退 bili:// 现取
            .setUri(uri?.takeIf { it.isNotBlank() } ?: "bili://$bvid")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .apply { cover?.let { setArtworkUri(android.net.Uri.parse(it)) } }
                    .build(),
            )
            .build()

    // ---------- 交叉淡入淡出 ----------
    //
    // 结构:monitor(协程)在曲末把音量渐弱到 0 → ExoPlayer 自动切下一首 →
    // onMediaItemTransition 记录「该淡入了」→ onPlayWhenReadyChanged 把音量渐强回来。
    // 三条状态只在主线程读写,没有并发问题。

    private val crossfadeListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val p = player ?: return
            if (!playWhenReady) {
                // 暂停/被打断:取消在跑的渐变并回满音量,再播时不做淡入
                fadeJob?.cancel()
                fadeInJob?.cancel()
                p.volume = 1f
                return
            }
            p.volume = 1f
            startCrossfadeMonitor()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = player ?: return
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // 手动切歌/跳播:静默保住满音量(上一首可能刚被渐弱过)
                p.volume = 1f
                startCrossfadeMonitor()
                return
            }
            // 单曲循环时同一首回到开头,不算切歌(否则会越播越轻)
            if (mediaItem != null && mediaItem == crossfadeFrom) {
                p.volume = 1f
                startCrossfadeMonitor()
                return
            }
            // 自动切歌:新曲从静音渐强上来(曲末渐弱已把音量压到 0)
            p.volume = 0f
            fadeInJob?.cancel()
            fadeInJob = fadeScope.launch {
                val steps = 25
                for (i in 1..steps) {
                    player?.volume = i / steps.toFloat()
                    delay((FADE_IN_MS / steps).coerceAtLeast(1))
                }
                player?.volume = 1f
            }
            startCrossfadeMonitor()
        }
    }

    private fun startCrossfadeMonitor() {
        fadeJob?.cancel()
        val p = player ?: return
        if (p.mediaItemCount == 0) return
        fadeJob = fadeScope.launch {
            while (isActive) {
                val pl = player ?: return@launch
                val dur = pl.duration
                if (pl.isPlaying && dur != C.TIME_UNSET && dur > 0) {
                    val remain = dur - pl.currentPosition
                    if (remain in 1..FADE_OUT_MS) {
                        android.util.Log.i(
                            "BiliSongRepository",
                            "crossfade: fade-out start ${pl.currentMediaItem?.mediaId} remain=${remain}ms",
                        )
                        crossfadeFrom = pl.currentMediaItem
                        val ok = fadeOut(pl, remain)
                        if (ok) return@launch
                    }
                }
                delay(200)
            }
        }
    }

    /** 曲末渐弱:按剩余时长等比例把音量降到 0;被暂停/手动切歌打断时恢复音量并退出。 */
    private suspend fun fadeOut(player: ExoPlayer, remainMs: Long): Boolean {
        val startAt = SystemClock.elapsedRealtime()
        while (true) {
            val p = player
            val left = remainMs - (SystemClock.elapsedRealtime() - startAt)
            if (left <= 0) break
            if (!p.isPlaying || p.currentMediaItem != crossfadeFrom) {
                fadeScope.launch { p.volume = 1f }
                return true
            }
            p.volume = (left.toFloat() / remainMs).coerceIn(0f, 1f)
            delay(50)
        }
        // 队列到头没有下一首:别把音量留在 0,否则下一轮起播是哑的
        if (!player.hasNextMediaItem()) {
            player.volume = 1f
            return false
        }
        player.volume = 0f
        return true
    }

    // ---------- 保存 ----------

    private val persistenceListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                saveSnapshot(player)
                // 桌面小组件同步刷新(与快照同一套事件+节流;广播触发 receiver 协程更新)
                NowPlayingWidget.updateAll(this@MusicService)
            }
        }
    }

    private fun saveSnapshot(p: Player) {
        val now = android.os.SystemClock.elapsedRealtime()
        // 3s 节流;但暂停/切歌(非播放态变化)立即存
        val important = !p.isPlaying
        if (!important && now - lastSaveElapsed < 3_000) return
        lastSaveElapsed = now

        if (p.mediaItemCount == 0) {
            scope.launch { store.clear() }
            return
        }
        val items = (0 until p.mediaItemCount).map { i ->
            val m = p.getMediaItemAt(i)
            val md = m.mediaMetadata
            PlaybackPersistenceStore.PersistedItem(
                bvid = m.mediaId ?: return@map PlaybackPersistenceStore.PersistedItem("", "", "", null),
                title = md.title?.toString().orEmpty(),
                artist = md.artist?.toString().orEmpty(),
                cover = md.artworkUri?.toString(),
                // Media3 1.x:uri 挪进了 localConfiguration(裸 m.uri 不存在)
                uri = m.localConfiguration?.uri?.toString(),
            )
        }.filter { it.bvid.isNotBlank() }

        val snapshot = PlaybackPersistenceStore.Snapshot(
            items = items,
            startIndex = p.currentMediaItemIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
            positionMs = p.currentPosition.coerceAtLeast(0),
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            playWhenReady = p.playWhenReady,
        )
        scope.launch { store.save(snapshot) }
    }

    private companion object {
        /** 曲末渐弱窗口(与下一首渐强一起构成 6s 级的交叉过渡)。 */
        const val FADE_OUT_MS = 6_000L
        /** 自动切歌后新曲的渐强时长。 */
        const val FADE_IN_MS = 2_500L
    }
}
