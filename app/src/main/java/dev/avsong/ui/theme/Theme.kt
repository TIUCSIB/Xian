package dev.avsong.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 用户明确要求:纯色扁平,禁止渐变/阴影/边框;品牌粉蓝已弃用。
private val Ink = Color(0xFF070707)

/**
 * 主题颜色 seed。深浅由功能栏图标切换;这里改的是"主题色是什么"——
 * 夜间和浅色两套方案都由 seed 色相派生(夜间经 [darkSeedScheme],浅色走手调方案
 * 或轮盘自定义的 [customColorScheme])。
 * hue 供夜间/自定义派生用;预览色随枚举携带,主题弹窗的色板直接取用。
 */
enum class ThemeSeed(
    val label: String,
    val hue: Float,
    val previewBg: Color,
    val previewAccent: Color,
) {
    YELLOW("柠檬", 57f, Color(0xFFFCFAF0), Color(0xFFF7F051)),
    CREAM("奶油", 24f, Color(0xFFFAF7F2), Color(0xFF8A5A3E)),
    ROSE("玫瑰", 340f, Color(0xFFFCF5F7), Color(0xFFA6375A)),
    CRIMSON("绯红", 5f, Color(0xFFFDF5F3), Color(0xFFA64034)),
    INDIGO("靛蓝", 231f, Color(0xFFF7F8FB), Color(0xFF3A4A9F)),
    SKY("天蓝", 205f, Color(0xFFF5F9FC), Color(0xFF29679E)),
    GREEN("墨绿", 155f, Color(0xFFF5F7F3), Color(0xFF2E5943)),
    VIOLET("紫罗兰", 275f, Color(0xFFF8F6FC), Color(0xFF6B4BA1)),
}

/**
 * 夜间方案:整套由 seed 色相生成。曲线以原手调黑黄方案为基准校准——
 * hue=57(柠檬)时 primary≈#F5EE51,与品牌柠檬黄 #F7F0551 几乎一致。
 */
private fun darkSeedScheme(hue: Float) = darkColorScheme(
    primary = Color.hsl(hue, 0.88f, 0.64f),
    onPrimary = Color.hsl(hue, 0.30f, 0.05f),
    primaryContainer = Color.hsl(hue, 0.30f, 0.17f),
    onPrimaryContainer = Color.hsl(hue, 0.88f, 0.64f),
    secondary = Color.hsl(hue, 0.04f, 0.73f),
    onSecondary = Color.hsl(hue, 0.30f, 0.05f),
    background = Color.hsl(hue, 0.10f, 0.027f),
    onBackground = Color.hsl(hue, 0.06f, 0.95f),
    surface = Color.hsl(hue, 0.09f, 0.07f),
    onSurface = Color.hsl(hue, 0.06f, 0.95f),
    surfaceVariant = Color.hsl(hue, 0.07f, 0.135f),
    onSurfaceVariant = Color.hsl(hue, 0.05f, 0.72f),
    surfaceContainer = Color.hsl(hue, 0.08f, 0.10f),
    surfaceContainerHigh = Color.hsl(hue, 0.07f, 0.14f),
    outline = Color.hsl(hue, 0.03f, 0.43f),
    outlineVariant = Color.hsl(hue, 0.05f, 0.20f),
    error = Color(0xFFFF8A80),
)

/** 浅色·柠檬黄:黄在白底上要压深成金才够对比(原柠檬黄只适合夜间做强调)。 */
private val AvYellowColors = lightColorScheme(
    primary = Color(0xFF7E7000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6F0C2),
    onPrimaryContainer = Color(0xFF3B3500),
    secondary = Color(0xFF6E6740),
    onSecondary = Color.White,
    background = Color(0xFFFCFBF0),
    onBackground = Color(0xFF1D1C14),
    surface = Color(0xFFFEFDF4),
    onSurface = Color(0xFF1D1C14),
    surfaceVariant = Color(0xFFF2F0DC),
    onSurfaceVariant = Color(0xFF6E6740),
    surfaceContainer = Color(0xFFF8F6E6),
    surfaceContainerHigh = Color(0xFFF2F0DC),
    outline = Color(0xFFCFC9A6),
    outlineVariant = Color(0xFFE9E6D2),
    error = Color(0xFFB3261E),
)

/** 浅色·奶油焦糖(2026-10-12 用户选定)。 */
private val AvCreamColors = lightColorScheme(
    primary = Color(0xFF8A5A3E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2E9E1),
    onPrimaryContainer = Color(0xFF3D2B20),
    secondary = Color(0xFF7A7468),
    onSecondary = Color.White,
    background = Color(0xFFFAF7F2),
    onBackground = Color(0xFF2D2A26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2D2A26),
    surfaceVariant = Color(0xFFF1ECE4),
    onSurfaceVariant = Color(0xFF7A7468),
    surfaceContainer = Color(0xFFF5F1EB),
    surfaceContainerHigh = Color(0xFFF1ECE4),
    outline = Color(0xFFD6CFC4),
    outlineVariant = Color(0xFFE9E4DB),
    error = Color(0xFFB3261E),
)

private val AvIndigoColors = lightColorScheme(
    primary = Color(0xFF3A4A9F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E8F5),
    onPrimaryContainer = Color(0xFF1A1F3D),
    secondary = Color(0xFF6E7686),
    onSecondary = Color.White,
    background = Color(0xFFF7F8FB),
    onBackground = Color(0xFF1A1D26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1D26),
    surfaceVariant = Color(0xFFECEEF4),
    onSurfaceVariant = Color(0xFF6E7686),
    surfaceContainer = Color(0xFFF1F3F8),
    surfaceContainerHigh = Color(0xFFEAEDF3),
    outline = Color(0xFFC9CEDC),
    outlineVariant = Color(0xFFE2E5EE),
    error = Color(0xFFB3261E),
)

private val AvGreenColors = lightColorScheme(
    primary = Color(0xFF2E5943),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFEAE2),
    onPrimaryContainer = Color(0xFF14301F),
    secondary = Color(0xFF6E7A68),
    onSecondary = Color.White,
    background = Color(0xFFF5F7F3),
    onBackground = Color(0xFF1C2420),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C2420),
    surfaceVariant = Color(0xFFE9EFE7),
    onSurfaceVariant = Color(0xFF6E7A68),
    surfaceContainer = Color(0xFFEFF3ED),
    surfaceContainerHigh = Color(0xFFE7EDE5),
    outline = Color(0xFFC5CFC2),
    outlineVariant = Color(0xFFDFE6DC),
    error = Color(0xFFB3261E),
)

private val AvRoseColors = lightColorScheme(
    primary = Color(0xFFA6375A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF8E4EC),
    onPrimaryContainer = Color(0xFF451426),
    secondary = Color(0xFF756069),
    onSecondary = Color.White,
    background = Color(0xFFFCF5F7),
    onBackground = Color(0xFF26171C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF26171C),
    surfaceVariant = Color(0xFFF4E7EB),
    onSurfaceVariant = Color(0xFF7A5F68),
    surfaceContainer = Color(0xFFF9EDF0),
    surfaceContainerHigh = Color(0xFFF4E7EB),
    outline = Color(0xFFD8BFC8),
    outlineVariant = Color(0xFFECDAE0),
    error = Color(0xFFB3261E),
)

private val AvCrimsonColors = lightColorScheme(
    primary = Color(0xFFA64034),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF8E3DF),
    onPrimaryContainer = Color(0xFF451912),
    secondary = Color(0xFF75605B),
    onSecondary = Color.White,
    background = Color(0xFFFDF5F3),
    onBackground = Color(0xFF241613),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF241613),
    surfaceVariant = Color(0xFFF4E5E1),
    onSurfaceVariant = Color(0xFF7A5F5A),
    surfaceContainer = Color(0xFFF9ECE9),
    surfaceContainerHigh = Color(0xFFF4E5E1),
    outline = Color(0xFFD8C0BB),
    outlineVariant = Color(0xFFECDAD6),
    error = Color(0xFFB3261E),
)

private val AvSkyColors = lightColorScheme(
    primary = Color(0xFF29679E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEEBF6),
    onPrimaryContainer = Color(0xFF0F2D42),
    secondary = Color(0xFF5F6E7A),
    onSecondary = Color.White,
    background = Color(0xFFF5F9FC),
    onBackground = Color(0xFF141D24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141D24),
    surfaceVariant = Color(0xFFE5EEF5),
    onSurfaceVariant = Color(0xFF5C6E7C),
    surfaceContainer = Color(0xFFEDF3F8),
    surfaceContainerHigh = Color(0xFFE5EEF5),
    outline = Color(0xFFC2CFDA),
    outlineVariant = Color(0xFFDEE8EF),
    error = Color(0xFFB3261E),
)

private val AvVioletColors = lightColorScheme(
    primary = Color(0xFF6B4BA1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBE2F6),
    onPrimaryContainer = Color(0xFF2A1750),
    secondary = Color(0xFF69627A),
    onSecondary = Color.White,
    background = Color(0xFFF8F6FC),
    onBackground = Color(0xFF1B1626),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1626),
    surfaceVariant = Color(0xFFEDE8F5),
    onSurfaceVariant = Color(0xFF6C6480),
    surfaceContainer = Color(0xFFF2EEF9),
    surfaceContainerHigh = Color(0xFFEDE8F5),
    outline = Color(0xFFCCC5DB),
    outlineVariant = Color(0xFFE3DDEC),
    error = Color(0xFFB3261E),
)

/** 轮盘拖出的自定义色相生成整套浅色方案:固定饱和/明度曲线,只随色相变。 */
fun customColorScheme(hue: Float): ColorScheme = lightColorScheme(
    primary = Color.hsl(hue, 0.52f, 0.38f),
    onPrimary = Color.White,
    primaryContainer = Color.hsl(hue, 0.45f, 0.91f),
    onPrimaryContainer = Color.hsl(hue, 0.55f, 0.16f),
    secondary = Color.hsl(hue, 0.10f, 0.40f),
    onSecondary = Color.White,
    background = Color.hsl(hue, 0.30f, 0.975f),
    onBackground = Color.hsl(hue, 0.28f, 0.12f),
    surface = Color.White,
    onSurface = Color.hsl(hue, 0.28f, 0.12f),
    surfaceVariant = Color.hsl(hue, 0.22f, 0.93f),
    onSurfaceVariant = Color.hsl(hue, 0.10f, 0.42f),
    surfaceContainer = Color.hsl(hue, 0.26f, 0.955f),
    surfaceContainerHigh = Color.hsl(hue, 0.22f, 0.92f),
    outline = Color.hsl(hue, 0.14f, 0.80f),
    outlineVariant = Color.hsl(hue, 0.18f, 0.89f),
    error = Color(0xFFB3261E),
)

/**
 * App 主题:深浅两态,颜色都随 seed(预设色相或轮盘自定义色相)派生。
 * 不用 dynamicColor:Material You 取色会破坏这套配色。
 */
@Composable
fun AvSongTheme(
    dark: Boolean = true,
    seed: ThemeSeed = ThemeSeed.YELLOW,
    customHue: Float? = null,
    content: @Composable () -> Unit,
) {
    val hue = customHue ?: seed.hue
    MaterialTheme(
        colorScheme = if (dark) {
            darkSeedScheme(hue)
        } else if (customHue != null) {
            customColorScheme(customHue)
        } else {
            when (seed) {
                ThemeSeed.YELLOW -> AvYellowColors
                ThemeSeed.CREAM -> AvCreamColors
                ThemeSeed.ROSE -> AvRoseColors
                ThemeSeed.CRIMSON -> AvCrimsonColors
                ThemeSeed.INDIGO -> AvIndigoColors
                ThemeSeed.SKY -> AvSkyColors
                ThemeSeed.GREEN -> AvGreenColors
                ThemeSeed.VIOLET -> AvVioletColors
            }
        },
        typography = AvTypography,
        content = content,
    )
}
