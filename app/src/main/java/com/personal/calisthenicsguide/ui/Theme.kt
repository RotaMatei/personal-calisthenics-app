package com.personal.calisthenicsguide.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Semantic colours beyond the Material scheme. */
object Palette {
    val Background = Color(0xFF0A0D12)
    val Surface = Color(0xFF141A22)
    val SurfaceHigh = Color(0xFF1E2630)
    val Amber = Color(0xFFFFB020)
    val Cyan = Color(0xFF22D3EE)
    val Green = Color(0xFF4ADE80)
    val Red = Color(0xFFFF6B6B)
    val Muted = Color(0xFFB4BFCD)
}

private val Scheme = darkColorScheme(
    primary = Palette.Amber,
    onPrimary = Color(0xFF1A1100),
    primaryContainer = Color(0xFF3A2A00),
    onPrimaryContainer = Color(0xFFFFE3A8),
    secondary = Palette.Cyan,
    onSecondary = Color(0xFF00232A),
    tertiary = Palette.Green,
    onTertiary = Color(0xFF002010),
    background = Palette.Background,
    onBackground = Color.White,
    surface = Palette.Surface,
    onSurface = Color.White,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Color(0xFFD5DCE6),
    error = Palette.Red,
    onError = Color(0xFF2A0000),
    outline = Color(0xFF6B7788),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 84.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)

/** Deep dark theme with strong contrast for use in direct sunlight. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // The app is dark on purpose, whatever the system setting says.
    @Suppress("UNUSED_VARIABLE") val system = isSystemInDarkTheme()
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
