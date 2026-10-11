package dev.avsong.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 参考设计稿配色:近黑底 + 柠檬黄强调 + 深灰卡片。
// 用户明确要求:纯色扁平,禁止渐变/阴影/边框;品牌粉蓝已弃用。
private val Lemon = Color(0xFFF7F051)
private val Ink = Color(0xFF070707)

private val AvColors = darkColorScheme(
    primary = Lemon,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3A3A1F),
    onPrimaryContainer = Lemon,
    secondary = Color(0xFFB9B9B9),
    onSecondary = Ink,
    background = Ink,
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF232323),
    onSurfaceVariant = Color(0xFFB4B4B4),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF242424),
    outline = Color(0xFF6E6E6E),
    outlineVariant = Color(0xFF333333),
    error = Color(0xFFFF8A80),
)

/**
 * App 主题:固定深色(参考稿即深色设计),锁定柠檬黄强调。
 * 不用 dynamicColor:Material You 取色会破坏这套配色;不用浅色方案:
 * 用户嫌粉蓝品牌色单调后选了这套黑黄,单一方案即可。
 */
@Composable
fun AvSongTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AvColors,
        typography = AvTypography,
        content = content,
    )
}
