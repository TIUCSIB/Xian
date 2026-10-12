@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package dev.avsong.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.avsong.R

/**
 * Noto Sans SC 变量字体(OFL 许可,单文件覆盖 100–900 字重轴)。
 *
 * 2026-10-12 从 Inter 换过来:Inter 没有中日文字形,中文/日文标题走系统回退字体,
 * 而回退不认变量字体的 wght 设置——「正在播放」的加粗高亮对中文完全无效(用户实测),
 * 换成一款自带 CJK + 假名 + 拉丁的字体后,字重在两种文字上都真实生效。
 * 附带覆盖:拉丁(含重音)、希腊、西里尔、平假名/片假名、汉字(简繁+生僻)、全角标点。
 */
private fun noto(weight: FontWeight) = Font(
    resId = R.font.noto_sans_sc_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val NotoFamily = FontFamily(
    noto(FontWeight.Normal),
    noto(FontWeight.Medium),
    noto(FontWeight.SemiBold),
    noto(FontWeight.Bold),
)

/** 显式调过的槽位:标题加重、正文收紧,配合品牌色拉开层级。 */
private val Base = Typography(
    headlineSmall = TextStyle(
        fontFamily = NotoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = NotoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.2.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = NotoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = NotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = NotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = NotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    ),
)

/** 未显式定义的槽位也统一挂 Noto,避免混入 FontFamily.Default。 */
val AvTypography = Base.copy(
    displayLarge = Base.displayLarge.copy(fontFamily = NotoFamily),
    displayMedium = Base.displayMedium.copy(fontFamily = NotoFamily),
    displaySmall = Base.displaySmall.copy(fontFamily = NotoFamily),
    headlineLarge = Base.headlineLarge.copy(fontFamily = NotoFamily),
    headlineMedium = Base.headlineMedium.copy(fontFamily = NotoFamily),
    titleSmall = Base.titleSmall.copy(fontFamily = NotoFamily),
    bodyMedium = Base.bodyMedium.copy(fontFamily = NotoFamily),
    bodySmall = Base.bodySmall.copy(fontFamily = NotoFamily),
    labelMedium = Base.labelMedium.copy(fontFamily = NotoFamily),
)
