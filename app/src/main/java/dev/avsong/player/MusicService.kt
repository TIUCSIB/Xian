package dev.avsong.player

import android.app.PendingIntent
import android.content.Intent
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放服务:Media3 托管通知栏、锁屏、蓝牙/线控,无需手写 MediaStyle 通知。
 * 额外职责:播放现场持久化(队列 bvid + 进度 + 模式),进程重启后恢复。
 */
class MusicService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var store: PlaybackPersistenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        mediaSession?.run {
            release()
            mediaSession = null
        }
        player?.release()
        player = null
        scope.cancel()
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
            .setUri("bili://$bvid")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .apply { cover?.let { setArtworkUri(android.net.Uri.parse(it)) } }
                    .build(),
            )
            .build()

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
}
