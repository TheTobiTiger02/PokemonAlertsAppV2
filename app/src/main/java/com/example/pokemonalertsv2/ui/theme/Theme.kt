package com.example.pokemonalertsv2.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    bgDeep = AppDarkBackground,
    bgBase = AppDarkBackground,
    bgElevated = AppDarkSurface,
    surfaceTranslucent = AppDarkSurfaceContainer,
    surfaceTranslucentHover = AppDarkSurfaceContainerHigh,
    foreground = AppDarkOnSurface,
    foregroundMuted = AppDarkOnSurfaceVariant,
    foregroundSubtle = AppDarkOnSurfaceVariant,
    accent = AppDarkPrimary,
    accentBright = AppDarkPrimary,
    accentGlow = AppDarkPrimary.copy(alpha = 0.14f),
    borderDefault = AppDarkOutline,
    borderHover = AppDarkOnSurfaceVariant.copy(alpha = 0.45f),
    borderAccent = AppDarkPrimary.copy(alpha = 0.55f)
)

val LightLinearModernColors = LinearModernColors(
    bgDeep = AppLightBackground,
    bgBase = AppLightBackground,
    bgElevated = AppLightSurface,
    surfaceTranslucent = AppLightSurfaceContainer,
    surfaceTranslucentHover = AppLightSurfaceContainerHigh,
    foreground = AppLightOnSurface,
    foregroundMuted = AppLightOnSurfaceVariant,
    foregroundSubtle = AppLightOnSurfaceVariant,
    accent = AppLightPrimary,
    accentBright = AppLightPrimary,
    accentGlow = AppLightPrimary.copy(alpha = 0.10f),
    borderDefault = AppLightOutline,
    borderHover = AppLightOnSurfaceVariant.copy(alpha = 0.32f),
    borderAccent = AppLightPrimary.copy(alpha = 0.45f)
)

val LocalLinearModernColors = staticCompositionLocalOf { DarkLinearModernColors }

private val DarkColorScheme = darkColorScheme(
    primary = AppDarkPrimary,
    onPrimary = AppDarkOnPrimary,
    primaryContainer = AppDarkPrimaryContainer,
    onPrimaryContainer = AppDarkOnPrimaryContainer,
    secondary = AppDarkPrimary,
    onSecondary = AppDarkOnPrimary,
    secondaryContainer = AppDarkSurfaceContainerHigh,
    onSecondaryContainer = AppDarkOnSurface,
    tertiary = AppDarkPrimary,
    onTertiary = AppDarkOnPrimary,
    tertiaryContainer = AppDarkSurfaceContainer,
    onTertiaryContainer = AppDarkOnSurface,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = AppDarkBackground,
    onBackground = AppDarkOnSurface,
    surface = AppDarkSurface,
    onSurface = AppDarkOnSurface,
    surfaceVariant = AppDarkSurfaceContainer,
    onSurfaceVariant = AppDarkOnSurfaceVariant,
    outline = AppDarkOutline,
    outlineVariant = AppDarkOutlineVariant,
    surfaceBright = AppDarkSurfaceContainerHigh,
    surfaceDim = AppDarkBackground,
    surfaceContainerLowest = AppDarkBackground,
    surfaceContainerLow = AppDarkSurface,
    surfaceContainer = AppDarkSurfaceContainer,
    surfaceContainerHigh = AppDarkSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF28303C),
    scrim = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = AppLightPrimary,
    onPrimary = AppLightOnPrimary,
    primaryContainer = AppLightPrimaryContainer,
    onPrimaryContainer = AppLightOnPrimaryContainer,
    secondary = AppLightPrimary,
    onSecondary = AppLightOnPrimary,
    secondaryContainer = AppLightSurfaceContainerHigh,
    onSecondaryContainer = AppLightOnSurface,
    tertiary = AppLightPrimary,
    onTertiary = AppLightOnPrimary,
    tertiaryContainer = AppLightSurfaceContainer,
    onTertiaryContainer = AppLightOnSurface,
    error = DangerRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = AppLightBackground,
    onBackground = AppLightOnSurface,
    surface = AppLightSurface,
    onSurface = AppLightOnSurface,
    surfaceVariant = AppLightSurfaceContainer,
    onSurfaceVariant = AppLightOnSurfaceVariant,
    outline = AppLightOutline,
    outlineVariant = AppLightOutlineVariant,
    surfaceBright = AppLightSurface,
    surfaceDim = AppLightSurfaceContainerHigh,
    surfaceContainerLowest = AppLightSurface,
    surfaceContainerLow = AppLightBackground,
    surfaceContainer = AppLightSurfaceContainer,
    surfaceContainerHigh = AppLightSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFFDCE1E9),
    scrim = Color.Black
)

@Composable
fun PokemonAlertsV2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors = if (darkTheme) DarkLinearModernColors else LightLinearModernColors
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

    CompositionLocalProvider(LocalLinearModernColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = PokemonShapes,
            content = content
        )
    }
}
