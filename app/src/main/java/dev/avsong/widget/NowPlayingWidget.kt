package dev.avsong.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.avsong.player.MusicService

/**
 * 4x1 桌面小组件:封面 + 歌名/歌手 + 播放暂停/下一首。
 *
 * 数据与指令都直接走 MediaController(与通知栏同源,状态天然一致):
 * - 更新:onUpdate + MusicService 在播放状态变化时调 [NowPlayingWidget.updateAll] 广播;
 * - 指令:actionRunCallback(PlayPauseAction/NextAction)连 controller 后下发;
 * - 封面用 MediaMetadata.artworkData(通知栏同源字节数组,不额外请求)。
 */
class NowPlayingWidget : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = NowPlayingGlanceWidget

    companion object {
        /**
         * 播放状态变化时由 MusicService 调用:发 APPWIDGET_UPDATE 广播,
         * receiver 的 onUpdate(内部协程)里再调挂起的 update。这里不能直接调挂起函数。
         */
        fun updateAll(context: Context) {
            context.sendBroadcast(
                Intent(context, NowPlayingWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE),
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        CoroutineScope(Dispatchers.Default).launch { NowPlayingGlanceWidget.refresh(context) }
    }
}

object NowPlayingGlanceWidget : GlanceAppWidget() {

    /** 重渲染所有实例(封面 artworkData 是二进制,Glance 不感知变化,必须显式 update)。 */
    suspend fun refresh(context: Context) {
        val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
        for (id in manager.getGlanceIds(NowPlayingGlanceWidget::class.java)) {
            update(context, id)
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // composition 每次重建都连 controller 读快照;播放/切歌的即时刷新靠 updateAll 广播
        val snapshot = controller(context)?.let { c ->
            val item: MediaItem? = c.currentMediaItem
            Snapshot(
                title = item?.mediaMetadata?.title?.toString(),
                artist = item?.mediaMetadata?.artist?.toString(),
                artData = item?.mediaMetadata?.artworkData,
                isPlaying = c.isPlaying,
            )
        } ?: Snapshot()
        provideContent {
            GlanceTheme {
                WidgetContent(snapshot)
            }
        }
    }

    data class Snapshot(
        val title: String? = null,
        val artist: String? = null,
        val artData: ByteArray? = null,
        val isPlaying: Boolean = false,
    )
}

@Composable
private fun WidgetContent(snapshot: NowPlayingGlanceWidget.Snapshot) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        // 主体(封面+文字)点击 → 打开 app
        Row(
            modifier = GlanceModifier.defaultWeight().clickable(androidx.glance.action.NoRippleOverride) { actionStartActivity<dev.avsong.MainActivity>() },
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier.size(44.dp).background(GlanceTheme.colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                snapshot.artData?.let { bytes ->
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        androidx.glance.Image(
                            provider = androidx.glance.BitmapImageProvider(bmp),
                            contentDescription = null,
                            modifier = GlanceModifier.size(44.dp),
                        )
                    }
                }
            }
            Column(modifier = GlanceModifier.padding(start = 10.dp)) {
                Text(
                    snapshot.title ?: "AvSong",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    snapshot.artist ?: "点按打开播放器",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
        // 控制钮:文字符号(RemoteViews 里矢量图标处理繁琐,符号在两套主题下都清晰)
        androidx.glance.text.Text(
            if (snapshot.isPlaying) "⏸" else "▶",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp),
            modifier = GlanceModifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clickable(androidx.glance.action.NoRippleOverride) { actionRunCallback<PlayPauseAction>() },
        )
        androidx.glance.text.Text(
            "⏭",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp),
            modifier = GlanceModifier
                .padding(end = 4.dp)
                .clickable(androidx.glance.action.NoRippleOverride) { actionRunCallback<NextAction>() },
        )
    }
}

private suspend fun controller(context: Context): MediaController? {
    var ctrl: MediaController? = null
    runCatching {
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ ctrl = future.get() }, MoreExecutors.directExecutor())
        // 阻塞等待在 Glance 的挂起环境不可取,这里用同步轮询短等待(实测绑定 <100ms)
        var waited = 0
        while (ctrl == null && waited < 1500) {
            Thread.sleep(25)
            waited += 25
        }
    }
    return ctrl
}

/** 播放/暂停:连 controller 后 toggle;完成顺手触发一次 widget 刷新。 */
class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        controller(context)?.let { c -> if (c.isPlaying) c.pause() else c.play() }
        NowPlayingWidget.updateAll(context)
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        controller(context)?.seekToNext()
        NowPlayingWidget.updateAll(context)
    }
}
