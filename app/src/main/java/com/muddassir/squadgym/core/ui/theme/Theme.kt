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
    onPrimary = Color(0xFF0B1A12),
    primaryContainer = Color(0xFF22302A),
    onPrimaryContainer = Lime,
    secondary = Mint,
    onSecondary = Color(0xFF08201C),
    secondaryContainer = Color(0xFF18302B),
    onSecondaryContainer = Mint,
    tertiary = Amber,
    onTertiary = Color(0xFF231A05),
    background = Obsidian,
    onBackground = TextPrimaryDark,
    surface = Charcoal,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceRaisedDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceRaisedDark,
    surfaceContainerHighest = Color(0xFF232725),
    surfaceContainerLow = Color(0xFF0F1110),
    surfaceContainerLowest = Obsidian,
    outline = OutlineDark,
    outlineVariant = Color(0xFF202322),
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
    primaryContainer = Color(0xFFDDEBE2),
    onPrimaryContainer = ForestDeep,
    secondary = Color(0xFF3F6F63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EDE8),
    onSecondaryContainer = Color(0xFF17352C),
    tertiary = Color(0xFF7A6240),
    onTertiary = Color.White,
    background = CanvasLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceRaisedLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = Color(0xFFFBFBF8),
    surfaceContainerHighest = SurfaceRaisedLight,
    surfaceContainerLow = Color(0xFFFAFAF7),
    surfaceContainerLowest = Color.White,
    outline = OutlineLight,
    outlineVariant = Color(0xFFEDEDE7),
    error = Color(0xFFA33A2E),
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
    accentInk = Color(0xFF0B1A12),
    success = Color(0xFF8FD3A6),
    warning = Amber,
    danger = Coral,
    successTint = SuccessTintDark,
    warningTint = WarningTintDark,
    dangerTint = DangerTintDark,
    neutralTint = NeutralTintDark,
    border = Color(0xFF272A28),
    chartBar = Color(0xFFD7DCD4),
    chartBarMuted = Color(0xFF2A2E2B),
    chartGrid = Color(0xFF202322),
    overlay = Color(0xCC0B0C0B),
    gradientTop = Obsidian,
    gradientBottom = Obsidian,
)

private val LightExtras = SquadColors(
    isDark = false,
    accent = Forest,
    accentInk = Color.White,
    success = Color(0xFF2E6B45),
    warning = Color(0xFF8A6A2F),
    danger = Color(0xFFA33A2E),
    successTint = SuccessTintLight,
    warningTint = WarningTintLight,
    dangerTint = DangerTintLight,
    neutralTint = NeutralTintLight,
    border = OutlineLight,
    chartBar = Color(0xFF242826),
    chartBarMuted = Color(0xFFE9E9E4),
    chartGrid = Color(0xFFEFEFEA),
    overlay = Color(0xCCF7F7F4),
    gradientTop = CanvasLight,
    gradientBottom = CanvasLight,
)

val LocalSquadColors = staticCompositionLocalOf { DarkExtras }

object SquadGymTheme {
    val colors: SquadColors
        @Composable get() = LocalSquadColors.current
}

val SquadShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 44.dp
    val screen = 24.dp
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
