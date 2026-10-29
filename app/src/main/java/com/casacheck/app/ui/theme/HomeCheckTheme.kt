package com.casacheck.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.casacheck.app.R

internal val Navy = Color(0xFF052E56)
internal val Blue = Color(0xFF1B67B2)
internal val Pale = Color(0xFFF6F7F9)
internal val Ink = Color(0xFF172536)
internal val Muted = Color(0xFF6B7C8F)
internal val Green = Color(0xFF16845B)
internal val Orange = Color(0xFFB76B16)


private val HomeFont = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.inter_regular))
private val BaseTypography = androidx.compose.material3.Typography()
internal val HomeTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = HomeFont),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = HomeFont),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = HomeFont),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = HomeFont),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = HomeFont),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = HomeFont),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = HomeFont),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = HomeFont),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = HomeFont),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = HomeFont),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = HomeFont),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = HomeFont),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = HomeFont),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = HomeFont),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = HomeFont)
)
