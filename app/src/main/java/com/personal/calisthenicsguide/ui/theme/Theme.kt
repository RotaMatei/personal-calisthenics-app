package com.personal.calisthenicsguide.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One spacing scale for every screen: gaps between cards, padding inside them, screen gutters. */
object Space {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** Side gutter of every screen. */
    val screen: Dp = lg
    /** Corner radius of cards, buttons and clip frames. */
    val corner: Dp = 16.dp
    /** Smallest tap target. */
    val tap: Dp = 48.dp
}

/**
 * Calm, low-contrast dark palette: soft charcoal surfaces, off-white text and muted accents (sage, slate, sand, rose,
 * mauve). Nothing is saturated, so the app is easy on the eyes; text and buttons still keep a comfortable contrast.
 */
object AppColors {
    val Background = Color(0xFF15181C)
    val Surface = Color(0xFF1C2025)
    val SurfaceHigh = Color(0xFF262B32)
    val Outline = Color(0xFF3B424B)
    val TextPrimary = Color(0xFFE2E5E9)
    val TextSecondary = Color(0xFF99A2AD)

    /** Sage green: the main accent, primary buttons and progress. */
    val Accent = Color(0xFF9DBBA8)
    val AccentOn = Color(0xFF14201A)
    val AccentSoft = Color(0xFF26332B)

    /** Slate: neutral information and the "lower" phase. */
    val Info = Color(0xFF9FAEC0)
    val Good = Color(0xFF93B89E)
    val Warn = Color(0xFFCBB085)
    val Danger = Color(0xFFCB8F8B)

    // Anatomical highlight colours: soft rose muscle, soft teal tendon, soft sand joint.
    val Muscle = Color(0xFFD9707F)
    val Tendon = Color(0xFF4FB8AC)
    val Joint = Color(0xFFDCC48E)

    // Workout days on the heatmap and in charts.
    val DayA = Color(0xFF9DBBA8)
    val DayB = Color(0xFF9FAEC0)
    val DayC = Color(0xFFB7A3BF)

    /** Video-style backdrop behind the 3D clips. */
    val ClipBackdrop = Color(0xFF111418)
}

private val Scheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.AccentOn,
    primaryContainer = AppColors.AccentSoft,
    onPrimaryContainer = AppColors.TextPrimary,
    secondary = AppColors.Info,
    onSecondary = Color(0xFF161C23),
    secondaryContainer = AppColors.AccentSoft,
    onSecondaryContainer = AppColors.TextPrimary,
    tertiary = AppColors.Warn,
    onTertiary = Color(0xFF241C0E),
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceHigh,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Outline,
    outlineVariant = Color(0xFF2E343B),
    error = AppColors.Danger,
    onError = Color(0xFF2A1514),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** The app is dark only, whatever the system setting says. */
@Composable
fun CalisthenicsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
