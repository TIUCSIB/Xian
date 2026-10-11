package dev.avsong.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
 * 浅色方案:用户指定的黑白灰设计稿色卡(2026-10-11)——
 * #171C26 墨黑做主强调、#6C7689 次级文字、#A4AAB7 描边/弱化、白面;
 * 柠檬黄在浅色下对比度不够,黄色仅深色模式使用。
 */
private val AvLightColors = lightColorScheme(
    primary = Color(0xFF171C26),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAEDF1),
    onPrimaryContainer = Color(0xFF171C26),
    secondary = Color(0xFF6C7689),
    onSecondary = Color.White,
    background = Color(0xFFF3F4F6),
    onBackground = Color(0xFF171C26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171C26),
    surfaceVariant = Color(0xFFECF0F3),
    onSurfaceVariant = Color(0xFF6C7689),
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerHigh = Color(0xFFEAEDF1),
    outline = Color(0xFFA4AAB7),
    outlineVariant = Color(0xFFE1E5E9),
    error = Color(0xFFB3261E),
)

/**
 * App 主题:柠檬黄强调,日/夜间两套纯色方案。
 * 不用 dynamicColor:Material You 取色会破坏这套配色。
 */
@Composable
fun AvSongTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AvColors else AvLightColors,
        typography = AvTypography,
        content = content,
    )
}
