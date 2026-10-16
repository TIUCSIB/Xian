package dev.avsong

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import dev.avsong.R
import dev.avsong.ui.AvSongApp
import dev.avsong.ui.rememberMainViewModel
import dev.avsong.ui.theme.AvSongTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝也继续播放 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+ 需要通知权限才能显示播放通知(前台服务本身不需要)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val app = applicationContext as AvSongApp
            val theme by app.themeStore.state.collectAsState()
            // 状态栏图标颜色跟随 app 主题(app 不随系统,enableEdgeToEdge 的默认判定不够用)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            SideEffect { insetsController.isAppearanceLightStatusBars = !theme.dark }
            AvSongTheme(dark = theme.dark, seed = theme.seed, customHue = theme.customHue) {
                // 锁定字体缩放:用户系统 fontScale=1.15 时账户头/卡片行全被 sp 文字撑高,
                // 每页顶部多出几十 dp(真机实测);app 排版按 1.0 设计,不随系统放大
                val sysDensity = androidx.compose.ui.platform.LocalDensity.current
                val fixedDensity = androidx.compose.ui.unit.Density(sysDensity.density, 1.0f)
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides fixedDensity
                ) {
                val navController = rememberNavController()
                val viewModel = rememberMainViewModel()
                AvSongApp(navController = navController, viewModel = viewModel)
                    // 启动过渡:屋顶霓虹月夜图盖在内容上,短暂停留后淡出。
                    // 系统启动页(Android 12+)时长=冷启动耗时,压不掉但可被首帧后的本层接管——
                    // 场景图在此显示,把「系统页→首帧」之间可能的空白也盖住(仅冷启动进程首帧起一次)
                    var splashVisible by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(900)
                        splashVisible = false
                    }
                    val splashAlpha by animateFloatAsState(
                        targetValue = if (splashVisible) 1f else 0f,
                        animationSpec = tween(400),
                        label = "splashFade",
                    )
                    if (splashAlpha > 0.01f) {
                        Box(Modifier.fillMaxSize().alpha(splashAlpha)) {
                            Image(
                                painter = painterResource(R.drawable.splash_bg),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                }
            }
        }
    }
