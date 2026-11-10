package com.muddassir.squadgym.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = Color(0xFF10160A),
    primaryContainer = Color(0xFF26330F),
    onPrimaryContainer = Lime,
    secondary = Mint,
    onSecondary = Color(0xFF04221B),
    secondaryContainer = Color(0xFF123028),
    onSecondaryContainer = Mint,
    tertiary = Amber,
    onTertiary = Color(0xFF241A02),
    background = Obsidian,
    onBackground = TextPrimaryDark,
    surface = Charcoal,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceRaisedDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceRaisedDark,
    surfaceContainerHighest = Color(0xFF232E29),
    surfaceContainerLow = Color(0xFF0D1311),
    surfaceContainerLowest = Obsidian,
    outline = OutlineDark,
    outlineVariant = Color(0xFF202B26),
    error = Coral,
    onError = Color(0xFF2B0B07),
    errorContainer = DangerTintDark,
    onErrorContainer = Color(0xFFFFB4AA),
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = Charcoal,
)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8EBC8),
    onPrimaryContainer = ForestDeep,
    secondary = Color(0xFF1F6F5A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F0E7),
    onSecondaryContainer = Color(0xFF0B3A2E),
    tertiary = Color(0xFF8A6400),
    onTertiary = Color.White,
    background = CanvasLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceRaisedLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = Color(0xFFF0F3E9),
    surfaceContainerHighest = SurfaceRaisedLight,
    surfaceContainerLow = Color(0xFFF9FBF4),
    surfaceContainerLowest = Color.White,
    outline = OutlineLight,
    outlineVariant = Color(0xFFE7ECE0),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = DangerTintLight,
    onErrorContainer = Color(0xFF5F1410),
    inverseSurface = TextPrimaryLight,
    inverseOnSurface = Color.White,
)

/** Colours Material 3 does not model: tints, chart ink, semantic states. */
@Immutable
data class SquadColors(
    val isDark: Boolean,
    val accent: Color,
    val accentInk: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val successTint: Color,
    val warningTint: Color,
    val dangerTint: Color,
    val neutralTint: Color,
    val border: Color,
    val chartBar: Color,
    val chartBarMuted: Color,
    val chartGrid: Color,
    val overlay: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
)

private val DarkExtras = SquadColors(
    isDark = true,
    accent = Lime,
    accentInk = Color(0xFF10160A),
    success = Color(0xFF6FE3A0),
    warning = Amber,
    danger = Coral,
    successTint = SuccessTintDark,
    warningTint = WarningTintDark,
    dangerTint = DangerTintDark,
    neutralTint = NeutralTintDark,
    border = Color(0xFF243029),
    chartBar = Lime,
    chartBarMuted = Color(0xFF2F3D36),
    chartGrid = Color(0xFF1F2A25),
    overlay = Color(0xCC0A0F0D),
    gradientTop = Color(0xFF16211B),
    gradientBottom = Color(0xFF080B0A),
)

private val LightExtras = SquadColors(
    isDark = false,
    accent = Color(0xFF3F7D20),
    accentInk = Color.White,
    success = Color(0xFF1E7C46),
    warning = Color(0xFF9A6B00),
    danger = Color(0xFFB3261E),
    successTint = SuccessTintLight,
    warningTint = WarningTintLight,
    dangerTint = DangerTintLight,
    neutralTint = NeutralTintLight,
    border = Color(0xFFE2E8DA),
    chartBar = Color(0xFF3F7D20),
    chartBarMuted = Color(0xFFDCE5D2),
    chartGrid = Color(0xFFE7ECE0),
    overlay = Color(0xCCF4F6EF),
    gradientTop = Color(0xFFEDF3E1),
    gradientBottom = Color(0xFFF4F6EF),
)

val LocalSquadColors = staticCompositionLocalOf { DarkExtras }

object SquadGymTheme {
    val colors: SquadColors
        @Composable get() = LocalSquadColors.current
}

val SquadShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 28.dp
    val xxl = 36.dp
    val screen = 20.dp
}

@Composable
fun SquadGymTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extras = if (darkTheme) DarkExtras else LightExtras
    androidx.compose.runtime.CompositionLocalProvider(LocalSquadColors provides extras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = SquadTypography,
            shapes = SquadShapes,
            content = content,
        )
    }
}
