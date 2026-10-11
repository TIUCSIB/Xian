package dev.avsong

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
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
            val dark by app.themeStore.dark.collectAsState()
            // 状态栏图标颜色跟随 app 主题(app 不随系统,enableEdgeToEdge 的默认判定不够用)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            SideEffect { insetsController.isAppearanceLightStatusBars = !dark }
            AvSongTheme(darkTheme = dark) {
                val navController = rememberNavController()
                val viewModel = rememberMainViewModel()
                AvSongApp(navController = navController, viewModel = viewModel)
            }
        }
    }
}
