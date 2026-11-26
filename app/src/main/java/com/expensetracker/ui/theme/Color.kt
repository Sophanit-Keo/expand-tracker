package com.expensetracker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette: indigo primary with a fuchsia accent.
val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E0FF),
    onPrimaryContainer = Color(0xFF16105E),
    inversePrimary = Color(0xFFC2C1FF),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E0F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFFB0189E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F2),
    onTertiaryContainer = Color(0xFF3B0036),
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF1A1B23),
    surface = Color(0xFFF7F7FB),
    onSurface = Color(0xFF1A1B23),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF5A5A66),
    surfaceTint = Color(0xFF4F46E5),
    inverseSurface = Color(0xFF2F3038),
    inverseOnSurface = Color(0xFFF2F0F7),
    outline = Color(0xFF7A7984),
    outlineVariant = Color(0xFFCBC9D4),
    surfaceBright = Color(0xFFF7F7FB),
    surfaceDim = Color(0xFFD9D9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F1F7),
    surfaceContainer = Color(0xFFEBEBF2),
    surfaceContainerHigh = Color(0xFFE5E5ED),
    surfaceContainerHighest = Color(0xFFDFDFE7),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFC2C1FF),
    onPrimary = Color(0xFF2A2399),
    primaryContainer = Color(0xFF4038C8),
    onPrimaryContainer = Color(0xFFE2E0FF),
    inversePrimary = Color(0xFF4F46E5),
    secondary = Color(0xFFC4C4DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE0E0F9),
    tertiary = Color(0xFFFFAAEE),
    onTertiary = Color(0xFF5B0057),
    tertiaryContainer = Color(0xFF8A1680),
    onTertiaryContainer = Color(0xFFFFD7F2),
    background = Color(0xFF111218),
    onBackground = Color(0xFFE3E1EA),
    surface = Color(0xFF111218),
    onSurface = Color(0xFFE3E1EA),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceTint = Color(0xFFC2C1FF),
    inverseSurface = Color(0xFFE3E1EA),
    inverseOnSurface = Color(0xFF2F3038),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF46464F),
    surfaceBright = Color(0xFF37383F),
    surfaceDim = Color(0xFF111218),
    surfaceContainerLowest = Color(0xFF0C0D12),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F26),
    surfaceContainerHigh = Color(0xFF292A31),
    surfaceContainerHighest = Color(0xFF34353C),
)

/** Spending going up is "bad", going down is "good" — used for the change chips. */
val Increase = Color(0xFFE5484D)
val Decrease = Color(0xFF2EA66F)

/**
 * Cash flow series colors (a validated categorical pair): money in vs money out. The light-mode
 * steps sit below 3:1 on white, so charts always carry a legend and readable numbers next to them.
 */
val CashInLight = Color(0xFF00B894)
val CashInDark = Color(0xFF00A888)
val CashOutLight = Color(0xFFF47230)
val CashOutDark = Color(0xFFE76624)

/** Text color for "+$" amounts: a darker green that passes text contrast in both themes. */
val CashInText: Color
    @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (LocalDarkTheme.current) Color(0xFF53C556) else Color(0xFF006C10)
