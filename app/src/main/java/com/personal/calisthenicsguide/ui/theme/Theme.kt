package com.personal.calisthenicsguide.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** High-contrast dark palette meant to stay readable in direct outdoor sunlight. */
object AppColors {
    val Background = Color(0xFF0A0D12)
    val Surface = Color(0xFF151B24)
    val SurfaceHigh = Color(0xFF1F2733)
    val Outline = Color(0xFF5B6878)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFC3CCD8)
    val Accent = Color(0xFFFFC400)
    val AccentOn = Color(0xFF1A1300)
    val Cyan = Color(0xFF4DD9F0)
    val Good = Color(0xFF5CE08A)
    val Warn = Color(0xFFFFB74D)
    val Danger = Color(0xFFFF6B6B)

    // Anatomical highlight colours from the spec.
    val Muscle = Color(0xFFFF4D4D)
    val Tendon = Color(0xFF4D8DFF)
    val Joint = Color(0xFFFFE04D)
}

private val Scheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.AccentOn,
    secondary = AppColors.Cyan,
    onSecondary = Color.Black,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceHigh,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Outline,
    error = AppColors.Danger,
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

/** The app is dark only, whatever the system setting says. */
@Composable
fun CalisthenicsTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val unused = isSystemInDarkTheme()
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
