package com.example.pokemonalertsv2.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

data class LinearModernColors(
    val bgDeep: Color,
    val bgBase: Color,
    val bgElevated: Color,
    val surfaceTranslucent: Color,
    val surfaceTranslucentHover: Color,
    val foreground: Color,
    val foregroundMuted: Color,
    val foregroundSubtle: Color,
    val accent: Color,
    val accentBright: Color,
    val accentGlow: Color,
    val borderDefault: Color,
    val borderHover: Color,
    val borderAccent: Color
)

val DarkLinearModernColors = LinearModernColors(
    bgDeep = LinearDarkBgDeep,
    bgBase = LinearDarkBgBase,
    bgElevated = LinearDarkBgElevated,
    surfaceTranslucent = LinearDarkSurface,
    surfaceTranslucentHover = LinearDarkSurfaceHover,
    foreground = LinearDarkForeground,
    foregroundMuted = LinearDarkForegroundMuted,
    foregroundSubtle = LinearDarkForegroundSubtle,
    accent = LinearDarkAccent,
    accentBright = LinearDarkAccentBright,
    accentGlow = LinearDarkAccentGlow,
    borderDefault = LinearDarkBorderDefault,
    borderHover = LinearDarkBorderHover,
    borderAccent = LinearDarkBorderAccent
)

val LightLinearModernColors = LinearModernColors(
    bgDeep = LinearLightBgDeep,
    bgBase = LinearLightBgBase,
    bgElevated = LinearLightBgElevated,
    surfaceTranslucent = LinearLightSurface,
    surfaceTranslucentHover = LinearLightSurfaceHover,
    foreground = LinearLightForeground,
    foregroundMuted = LinearLightForegroundMuted,
    foregroundSubtle = LinearLightForegroundSubtle,
    accent = LinearLightAccent,
    accentBright = LinearLightAccentBright,
    accentGlow = LinearLightAccentGlow,
    borderDefault = LinearLightBorderDefault,
    borderHover = LinearLightBorderHover,
    borderAccent = LinearLightBorderAccent
)

val LocalLinearModernColors = staticCompositionLocalOf<LinearModernColors> {
    error("No LinearModernColors provided")
}

private val DarkColorScheme = darkColorScheme(
    primary = LinearDarkAccent,
    onPrimary = LinearDarkForeground,
    primaryContainer = LinearDarkBgElevated,
    onPrimaryContainer = LinearDarkForeground,
    secondary = LinearDarkAccentBright,
    onSecondary = LinearDarkForeground,
    secondaryContainer = LinearDarkSurface,
    onSecondaryContainer = LinearDarkForeground,
    tertiary = LinearDarkAccent,
    onTertiary = LinearDarkForeground,
    tertiaryContainer = LinearDarkSurface,
    onTertiaryContainer = LinearDarkForeground,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRed.copy(alpha = 0.2f),
    onErrorContainer = Color.White,
    background = LinearDarkBgBase,
    onBackground = LinearDarkForeground,
    surface = LinearDarkBgElevated,
    onSurface = LinearDarkForeground,
    surfaceVariant = LinearDarkSurface,
    onSurfaceVariant = LinearDarkForegroundMuted,
    surfaceTint = LinearDarkAccent,
    inverseSurface = LinearLightBgElevated,
    inverseOnSurface = LinearLightForeground,
    inversePrimary = LinearLightAccent,
    outline = LinearDarkBorderDefault,
    outlineVariant = LinearDarkBorderHover,
    scrim = Color.Black,
    surfaceBright = LinearDarkBgElevated,
    surfaceDim = LinearDarkBgDeep,
    surfaceContainer = LinearDarkBgElevated,
    surfaceContainerHigh = LinearDarkBgElevated,
    surfaceContainerHighest = LinearDarkBgElevated,
    surfaceContainerLow = LinearDarkBgDeep,
    surfaceContainerLowest = LinearDarkBgDeep
)

private val LightColorScheme = lightColorScheme(
    primary = LinearLightAccent,
    onPrimary = Color.White,
    primaryContainer = LinearLightBgElevated,
    onPrimaryContainer = LinearLightForeground,
    secondary = LinearLightAccentBright,
    onSecondary = Color.White,
    secondaryContainer = LinearLightSurface,
    onSecondaryContainer = LinearLightForeground,
    tertiary = LinearLightAccent,
    onTertiary = Color.White,
    tertiaryContainer = LinearLightSurface,
    onTertiaryContainer = LinearLightForeground,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRed.copy(alpha = 0.1f),
    onErrorContainer = DangerRed,
    background = LinearLightBgBase,
    onBackground = LinearLightForeground,
    surface = LinearLightBgElevated,
    onSurface = LinearLightForeground,
    surfaceVariant = LinearLightSurface,
    onSurfaceVariant = LinearLightForegroundMuted,
    surfaceTint = LinearLightAccent,
    inverseSurface = LinearDarkBgElevated,
    inverseOnSurface = LinearDarkForeground,
    inversePrimary = LinearDarkAccent,
    outline = LinearLightBorderDefault,
    outlineVariant = LinearLightBorderHover,
    scrim = Color.Black,
    surfaceBright = LinearLightBgElevated,
    surfaceDim = LinearLightBgDeep,
    surfaceContainer = LinearLightBgElevated,
    surfaceContainerHigh = LinearLightBgElevated,
    surfaceContainerHighest = LinearLightBgElevated,
    surfaceContainerLow = LinearLightBgDeep,
    surfaceContainerLowest = LinearLightBgDeep
)

@Composable
fun PokemonAlertsV2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER")
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val linearColors = if (darkTheme) DarkLinearModernColors else LightLinearModernColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalLinearModernColors provides linearColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = PokemonShapes,
            content = content
        )
    }
}
