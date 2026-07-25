package com.juanpablo0612.sickreshapex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Colors that don't map cleanly onto Material3's ColorScheme roles but are used
 * throughout the app (pipeline status, gradients). Brand color stays fixed across
 * light/dark so the SICK identity never shifts with dynamic color.
 */
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val scanCyan: Color,
    val scanBlue: Color,
    val heroGradient: Brush,
    val scanGradient: Brush,
    val cardBorder: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color
)

private val DarkExtendedColors = ExtendedColors(
    success = SuccessGreen,
    onSuccess = Ink950,
    successContainer = SuccessGreenDark,
    warning = WarningAmber,
    onWarning = Ink950,
    warningContainer = WarningAmberDark,
    scanCyan = ScanCyan,
    scanBlue = ScanBlue,
    heroGradient = Brush.linearGradient(listOf(Ink900, Ink800, SickOrangeDark.copy(alpha = 0.35f))),
    scanGradient = Brush.linearGradient(listOf(ScanCyan, ScanBlue)),
    cardBorder = Color.White.copy(alpha = 0.08f),
    shimmerBase = Ink700,
    shimmerHighlight = Ink500
)

private val LightExtendedColors = ExtendedColors(
    success = Color(0xFF1A7A50),
    onSuccess = Color.White,
    successContainer = Color(0xFFCDF3E1),
    warning = Color(0xFF8A5A00),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFE6B8),
    scanCyan = ScanCyanDark,
    scanBlue = ScanBlue,
    heroGradient = Brush.linearGradient(listOf(Paper0, Paper50, SickOrangeLight.copy(alpha = 0.25f))),
    scanGradient = Brush.linearGradient(listOf(ScanCyanDark, ScanBlue)),
    cardBorder = Color.Black.copy(alpha = 0.06f),
    shimmerBase = Paper200,
    shimmerHighlight = Paper0
)

private val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current

private val DarkColorScheme = darkColorScheme(
    primary = SickOrange,
    onPrimary = Ink950,
    primaryContainer = SickOrangeContainerDark,
    onPrimaryContainer = SickOrangeLight,
    secondary = ScanCyan,
    onSecondary = Ink950,
    secondaryContainer = Ink700,
    onSecondaryContainer = ScanCyan,
    tertiary = ScanBlue,
    onTertiary = Ink950,
    background = Ink900,
    onBackground = TextHighDark,
    surface = Ink800,
    onSurface = TextHighDark,
    surfaceVariant = Ink700,
    onSurfaceVariant = TextMedDark,
    surfaceContainer = Ink800,
    surfaceContainerLow = Ink900,
    surfaceContainerLowest = Ink950,
    surfaceContainerHigh = Ink700,
    surfaceContainerHighest = Ink600,
    outline = Ink500,
    outlineVariant = Ink600,
    error = ErrorRed,
    onError = Ink950,
    errorContainer = ErrorRedDark,
    onErrorContainer = Color(0xFFFFD9D9)
)

private val LightColorScheme = lightColorScheme(
    primary = SickOrangeDark,
    onPrimary = Color.White,
    primaryContainer = SickOrangeContainerLight,
    onPrimaryContainer = SickOrangeDark,
    secondary = ScanCyanDark,
    onSecondary = Color.White,
    secondaryContainer = Paper200,
    onSecondaryContainer = ScanCyanDark,
    tertiary = ScanBlue,
    onTertiary = Color.White,
    background = Paper50,
    onBackground = TextHighLight,
    surface = Paper0,
    onSurface = TextHighLight,
    surfaceVariant = Paper100,
    onSurfaceVariant = TextMedLight,
    surfaceContainer = Paper50,
    surfaceContainerLow = Paper0,
    surfaceContainerLowest = Paper0,
    surfaceContainerHigh = Paper100,
    surfaceContainerHighest = Paper200,
    outline = Paper300,
    outlineVariant = Paper200,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

/**
 * App theme. Dynamic color is intentionally NOT offered: the demo relies on a fixed
 * SICK-orange + scan-cyan identity that must stay consistent regardless of wallpaper.
 */
@Composable
fun SickReshapeXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
