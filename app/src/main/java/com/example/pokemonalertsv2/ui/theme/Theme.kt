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

private val DarkColorScheme = darkColorScheme(
    primary = MidnightPrimary,
    onPrimary = MidnightOnPrimary,
    primaryContainer = MidnightPrimaryContainer,
    onPrimaryContainer = MidnightOnPrimaryContainer,
    secondary = MidnightSecondary,
    onSecondary = MidnightOnSecondary,
    secondaryContainer = MidnightSecondaryContainer,
    onSecondaryContainer = MidnightOnSecondaryContainer,
    tertiary = MidnightTertiary,
    onTertiary = MidnightOnTertiary,
    tertiaryContainer = MidnightTertiaryContainer,
    onTertiaryContainer = MidnightOnTertiaryContainer,
    background = MidnightBackground,
    onBackground = MidnightOnSurface,
    surface = MidnightSurface,
    onSurface = MidnightOnSurface,
    surfaceVariant = MidnightSurfaceContainerHighest,
    onSurfaceVariant = MidnightOnSurfaceVariant,
    outline = MidnightOutline,
    outlineVariant = MidnightOutlineVariant,
    error = MidnightError,
    onError = MidnightOnError,
    errorContainer = MidnightErrorContainer,
    onErrorContainer = MidnightOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = LuminousPrimary,
    onPrimary = LuminousOnPrimary,
    primaryContainer = LuminousPrimaryContainer,
    onPrimaryContainer = LuminousOnPrimaryContainer,
    secondary = LuminousSecondary,
    onSecondary = LuminousOnSecondary,
    secondaryContainer = LuminousSecondaryContainer,
    onSecondaryContainer = LuminousOnSecondaryContainer,
    tertiary = LuminousTertiary,
    onTertiary = LuminousOnTertiary,
    tertiaryContainer = LuminousTertiaryContainer,
    onTertiaryContainer = LuminousOnTertiaryContainer,
    background = LuminousBackground,
    onBackground = LuminousOnSurface,
    surface = LuminousSurface,
    onSurface = LuminousOnSurface,
    surfaceVariant = LuminousSurfaceContainer,
    onSurfaceVariant = LuminousOnSurfaceVariant,
    outline = LuminousOutline,
    outlineVariant = LuminousOutlineVariant,
    error = LuminousError,
    onError = LuminousOnError,
    errorContainer = LuminousErrorContainer,
    onErrorContainer = LuminousOnErrorContainer
)

@Composable
fun PokemonAlertsV2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER")
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Keep the approved electric-blue identity stable across devices. The parameter
    // remains for source compatibility with existing callers.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PokemonShapes,
        content = content
    )
}
