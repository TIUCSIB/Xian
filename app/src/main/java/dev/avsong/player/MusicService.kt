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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
            .setMediaSourceFactory(biliMediaSourceFactory(this))
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
            .setCallback(
                object : MediaSession.Callback {
                    // 声明自定义命令:controller 侧 sendCustomCommand 只放行这里列出的。
                    // ⚠ 用 AcceptedResultBuilder(playerCommands 走默认全集);手写 accept()
                    // 覆盖 availablePlayerCommands 曾把 session actions 压到只剩 128(播放键失灵)。
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                    ): androidx.media3.session.MediaSession.ConnectionResult {
                        val result = super.onConnect(session, controller)
                        return androidx.media3.session.MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            // 在默认 session 命令之上叠加自定义命令;playerCommands 保持 builder 默认全集
                            .setAvailableSessionCommands(
                                result.availableSessionCommands.buildUpon()
                                    .add(SessionCommand(COMMAND_TOGGLE_VIDEO, android.os.Bundle.EMPTY))
                                    .build(),
                            )
                            .build()
                    }

                    override fun onCustomCommand(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        command: SessionCommand,
                        args: android.os.Bundle,
                    ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
                        if (command.customAction == COMMAND_TOGGLE_VIDEO) {
                            val isVideo = toggleVideoSource()
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                SessionResult(
                                    SessionResult.RESULT_SUCCESS,
                                    android.os.Bundle(1).apply { putBoolean(EXTRA_IS_VIDEO, isVideo) },
                                ),
                            )
                        }
                        return super.onCustomCommand(session, controller, command, args)
                    }
                },
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
                    .setExtras(
                        android.os.Bundle().apply {
                            if (durationSec > 0) putLong(dev.avsong.ui.META_DURATION_SEC, durationSec)
                            // 恢复现场按 uri 还原 MV 标记:biliMediaSourceFactory 依它合成音视频源,
                            // UI 依它画画面层;不还原会出现「service 在解视频、UI 以为是纯音频」
                            if (uri?.startsWith("${BiliDataSources.MV_SCHEME}:") == true) {
                                putBoolean(EXTRA_IS_VIDEO, true)
                            }
                        },
                    )
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
                durationSec = md.extras?.getLong(dev.avsong.ui.META_DURATION_SEC, 0L) ?: 0L,
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

    // ---------- MV 画面切换(自定义 session command,service 侧执行) ----------
    //
    // controller 侧 MediaItem 不带 localConfiguration(binder 安全裁剪),拿不到 uri;
    // 只能在这里(service 端)对当前项做原位替换。返回切换后的 MV 态。

    /**
     * MV 画面开/关(逐曲):翻转当前曲 uri 在 bili:// 与 bili-mv:// 之间。
     * ⚠ 实现必须走「移除+原位插回+bvid 定位 seek」,不能 replaceMediaItem:
     * replace 在 shuffle 下会重排 shuffle order 甩飞当前项(实测 Father Figure→Call your name)。
     * remove 瞬间 ExoPlayer 自动前移播下一首(1~2s),插回后按 bvid 找回并 seek 回原进度续播。
     * 返回是否切到了 MV。
     */
    private fun toggleVideoSource(): Boolean {
        val p = player ?: return false
        val cur = p.currentMediaItem ?: return false
        val curUri = cur.localConfiguration?.uri ?: return false
        val bvid = curUri.host ?: return false
        // ⚠ MV 事实源是 metadata.extras 的 is_video 标记,**不是 uri**:
        // biliMediaSourceFactory 对 MV 曲返回 MergingMediaSource,其 getMediaItem() 是第一个
        // 子源(音频轨)的 item —— 播放列表里的 uri 恒为 bili://(实测);只按 scheme 判断会
        // 永远 toVideo=true,表现为「只能开、关不掉」。scheme 只作旧 item 的兜底。
        val isVideoNow = cur.mediaMetadata.extras?.getBoolean(EXTRA_IS_VIDEO) == true ||
            BiliDataSources.MV_SCHEME.equals(curUri.scheme ?: "", ignoreCase = true)
        val toVideo = !isVideoNow
        val resumeMs = p.currentPosition.coerceAtLeast(0)
        val wasPlaying = p.isPlaying

        // MV 态写进 extras:MediaMetadata 跨 binder 可读,controller 侧可据此画勾选态
        val newItem = cur.buildUpon()
            .setUri(if (toVideo) "${BiliDataSources.MV_SCHEME}://$bvid" else "bili://$bvid")
            .setMediaMetadata(
                cur.mediaMetadata.buildUpon().setExtras(
                    android.os.Bundle().apply {
                        putBoolean(EXTRA_IS_VIDEO, toVideo)
                        cur.mediaMetadata.extras?.getLong(dev.avsong.ui.META_DURATION_SEC, 0L)
                            ?.takeIf { it > 0 }
                            ?.let { putLong(dev.avsong.ui.META_DURATION_SEC, it) }
                    },
                ).build(),
            )
            .build()

        val oldIndex = p.currentMediaItemIndex
        // 暂停再动:避免 remove 瞬间真的把下一首播起来(闪播)
        p.pause()
        p.removeMediaItem(oldIndex)
        p.addMediaItem(oldIndex, newItem)
        // 按 mediaId(bvid)找回新位置:shuffle order 变化后 index 不可信
        val newIndex = (0 until p.mediaItemCount).firstOrNull { p.getMediaItemAt(it).mediaId == bvid } ?: -1
        if (newIndex >= 0) {
            p.seekTo(newIndex, resumeMs)
        }
        if (wasPlaying) p.play()
        return toVideo
    }

    companion object {
        /** 曲末渐弱窗口(与下一首渐强一起构成 6s 级的交叉过渡)。 */
        private const val FADE_OUT_MS = 6_000L
        /** 自动切歌后新曲的渐强时长。 */
        private const val FADE_IN_MS = 2_500L
        /** 自定义命令:MV 画面开/关(controller 发起,service 执行 replaceMediaItem)。 */
        const val COMMAND_TOGGLE_VIDEO = "avsong.TOGGLE_VIDEO"
        /** toggle 结果 bundle key:切换后当前曲是否 MV 态。 */
        const val EXTRA_IS_VIDEO = "is_video"
    }
}

/**
 * AvSong 媒体源工厂:按 uri scheme 产三种源 ——
 * - `bili://<bvid>` 纯音频(默认,离线曲走 file:// 也由 DefaultDataSource 分发)
 * - `bili-mv://<bvid>` 音频+视频合并(MV 画面播放;视频流经同一 resolver 现取)
 * - 其它(file:// 直链快照等)原样 ProgressiveMediaSource。
 * MV 用 MergingMediaSource(音轨来自 playurl audio 最高档,画面来自 video 档)保持
 * 时间轴一致,单 ExoPlayer 实例渲染,无需音画同步逻辑。
 */
private fun biliMediaSourceFactory(context: android.content.Context): androidx.media3.exoplayer.source.MediaSource.Factory {
    val resolverFactory = BiliDataSources.create(context)
    val progressive = ProgressiveMediaSource.Factory(resolverFactory)
    return object : androidx.media3.exoplayer.source.MediaSource.Factory {
        override fun setDrmSessionManagerProvider(
            provider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider,
        ): androidx.media3.exoplayer.source.MediaSource.Factory = this

        override fun setLoadErrorHandlingPolicy(
            policy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy,
        ): androidx.media3.exoplayer.source.MediaSource.Factory = this

        override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER)

        override fun createMediaSource(mediaItem: MediaItem): androidx.media3.exoplayer.source.MediaSource {
            val uri = mediaItem.localConfiguration?.uri ?: return progressive.createMediaSource(mediaItem)
            val bvid = uri.host ?: mediaItem.mediaId
            // MV 条件:显式 bili-mv://(toggle 时改写的当前曲);file://(离线曲)不合成
            val mvScheme = BiliDataSources.MV_SCHEME.equals(uri.scheme ?: "", ignoreCase = true)
            if (mvScheme) {
                // 音频流沿用默认音质选路;视频流单独走 bili-mv://(resolver 内区分取链)
                val audio = progressive.createMediaSource(
                    mediaItem.buildUpon().setUri("bili://$bvid").build(),
                )
                val video = progressive.createMediaSource(
                    mediaItem.buildUpon().setUri("bili-mv://$bvid").build(),
                )
                return androidx.media3.exoplayer.source.MergingMediaSource(audio, video)
            }
            return progressive.createMediaSource(mediaItem)
        }
    }
}
