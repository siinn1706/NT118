package vn.uit.healthos.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import vn.uit.healthos.R

val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
)

private fun style(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = BeVietnamPro,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

/*
 * Scale modelled on Apple's text styles (Large Title 34, Title 1 28, Title 2 22, Title 3 20, Headline 17, Body,
 * Subheadline 15, Footnote 13, Caption 12/11) with the tight negative tracking of SF at display sizes. Body text
 * stays at 16sp because Be Vietnam Pro is wider than SF and Vietnamese diacritics need the extra line height.
 */
internal val HealthOsTypography = Typography(
    displayLarge = style(48, 56, FontWeight.Bold, -1.2f),
    displayMedium = style(40, 48, FontWeight.Bold, -1f),
    displaySmall = style(34, 41, FontWeight.Bold, -0.8f),
    headlineLarge = style(30, 37, FontWeight.Bold, -0.6f),
    headlineMedium = style(28, 34, FontWeight.Bold, -0.5f),
    headlineSmall = style(22, 28, FontWeight.Bold, -0.35f),
    titleLarge = style(20, 26, FontWeight.SemiBold, -0.3f),
    titleMedium = style(17, 22, FontWeight.SemiBold, -0.25f),
    titleSmall = style(15, 20, FontWeight.SemiBold, -0.15f),
    bodyLarge = style(16, 23, FontWeight.Normal, -0.2f),
    bodyMedium = style(15, 21, FontWeight.Normal, -0.15f),
    bodySmall = style(13, 18, FontWeight.Normal, -0.05f),
    labelLarge = style(15, 20, FontWeight.Medium, -0.15f),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium, 0.05f),
)

/** Tabular figures so metric values do not jitter when they update. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
