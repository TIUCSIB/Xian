package dev.avsong.ui.common

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay

/** 消失前的淡出时长;再往前的展示时长见 [NOTICE_HOLD_MS]。 */
private const val NOTICE_FADE_MS = 180L
private const val NOTICE_HOLD_MS = 2400L

/**
 * 应用内「主题化 Toast」:替代系统 Toast(样式自带安卓机器人图标、配色固定黑底,与主题脱节,用户点名换掉)。
 *
 * 用透明 Dialog 窗口承载——当初选 Toast 而不是 Snackbar 就是因为队列抽屉是独立窗口会把
 * Snackbar 盖住;Dialog 窗口后于抽屉创建,同层后到者在上,同样能盖住抽屉。
 * 窗口设 NOT_FOCUSABLE|NOT_TOUCHABLE,与系统 Toast 一样纯悬浮:不拦截任何触摸、不吃返回键,
 * 下层界面照常可操作。样式走 inverseSurface/inverseOnSurface(M3 为 snackbar 预设的对比色对),
 * 纯色药丸、无阴影无渐变,夜间/浅色随主题自动适配。
 */
@Composable
fun ThemedNoticeHost(
    message: String?,
    onConsumed: () -> Unit,
    /** 距屏幕底部的留白:默认抬到迷你条+底部 tab 上方。 */
    bottomPadding: Dp = 118.dp,
) {
    if (message == null) return
    var visible by remember(message) { mutableStateOf(false) }
    LaunchedEffect(message) { visible = true }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        // Compose Dialog 窗口默认可聚焦、带全屏压暗遮罩;改成系统 Toast 那样的纯悬浮窗口:
        // 不聚焦、不接收触摸、无遮罩(遮罩会压暗整个页面,用户点名去掉)
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            )
            window?.setDimAmount(0f)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                exit = fadeOut(),
            ) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = bottomPadding)
                        .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }

    // 展示期满先淡出再回收(message=null 时整个 Dialog 直接离场,没机会播放退出动画)
    LaunchedEffect(message) {
        delay(NOTICE_HOLD_MS)
        visible = false
        delay(NOTICE_FADE_MS)
        onConsumed()
    }
}
