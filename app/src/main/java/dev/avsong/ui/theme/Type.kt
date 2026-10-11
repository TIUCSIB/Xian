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
 * Inter 变量字体(设计稿指定,2026-10-11 接入)。
 * 单个 TTF 覆盖 wght 轴,按字重取对应实例(minSdk 26 起支持 variation settings)。
 */
private fun inter(weight: FontWeight) = Font(
    resId = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val InterFamily = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
)

/** 显式调过的槽位:标题加重、正文收紧,配合品牌色拉开层级。 */
private val Base = Typography(
    headlineSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.2.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    ),
)

/** 未显式定义的槽位也统一挂 Inter,避免混入 FontFamily.Default。 */
val AvTypography = Base.copy(
    displayLarge = Base.displayLarge.copy(fontFamily = InterFamily),
    displayMedium = Base.displayMedium.copy(fontFamily = InterFamily),
    displaySmall = Base.displaySmall.copy(fontFamily = InterFamily),
    headlineLarge = Base.headlineLarge.copy(fontFamily = InterFamily),
    headlineMedium = Base.headlineMedium.copy(fontFamily = InterFamily),
    titleSmall = Base.titleSmall.copy(fontFamily = InterFamily),
    bodyMedium = Base.bodyMedium.copy(fontFamily = InterFamily),
    bodySmall = Base.bodySmall.copy(fontFamily = InterFamily),
    labelMedium = Base.labelMedium.copy(fontFamily = InterFamily),
)
