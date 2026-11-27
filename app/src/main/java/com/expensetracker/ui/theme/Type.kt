package com.expensetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Base = Typography()

val AppTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Medium),
)

/**
 * Khmer stacks vowels and subscript consonants above and below the line, so the fixed Latin line
 * heights would clip them. Use generous, proportional line heights and no negative tracking instead.
 */
private fun TextStyle.forKhmer(): TextStyle = copy(lineHeight = 1.55.em, letterSpacing = TextUnit.Unspecified)

val KhmerTypography = Typography(
    displayLarge = AppTypography.displayLarge.forKhmer(),
    displayMedium = AppTypography.displayMedium.forKhmer(),
    displaySmall = AppTypography.displaySmall.forKhmer(),
    headlineLarge = AppTypography.headlineLarge.forKhmer(),
    headlineMedium = AppTypography.headlineMedium.forKhmer(),
    headlineSmall = AppTypography.headlineSmall.forKhmer(),
    titleLarge = AppTypography.titleLarge.forKhmer(),
    titleMedium = AppTypography.titleMedium.forKhmer(),
    titleSmall = AppTypography.titleSmall.forKhmer(),
    bodyLarge = AppTypography.bodyLarge.forKhmer(),
    bodyMedium = AppTypography.bodyMedium.forKhmer(),
    bodySmall = AppTypography.bodySmall.forKhmer(),
    labelLarge = AppTypography.labelLarge.forKhmer(),
    labelMedium = AppTypography.labelMedium.forKhmer(),
    labelSmall = AppTypography.labelSmall.forKhmer(),
)
