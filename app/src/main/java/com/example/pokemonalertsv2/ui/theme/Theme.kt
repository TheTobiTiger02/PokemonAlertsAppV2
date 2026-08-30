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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The three accent roles that have no Material 3 counterpart.
 *
 * Everything else the app used to keep in a parallel `LinearModernColors` palette was a
 * one-for-one duplicate of [MaterialTheme.colorScheme] under a second set of names, which
 * meant the same pixel had two spellings and half the codebase reached for each. These are
 * what was actually missing: alpha-derived accents that M3 does not define.
 */
object AppAccents {
    @Composable
    fun accentGlow(): Color = MaterialTheme.colorScheme.primary.copy(
        alpha = if (LocalAppDarkTheme.current) 0.14f else 0.10f
    )

    @Composable
    fun borderHover(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
        alpha = if (LocalAppDarkTheme.current) 0.45f else 0.32f
    )

    @Composable
    fun borderAccent(): Color = MaterialTheme.colorScheme.primary.copy(
        alpha = if (LocalAppDarkTheme.current) 0.55f else 0.45f
    )
}

val LocalAppDarkTheme = staticCompositionLocalOf { false }

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
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    // The bars themselves are made transparent by enableEdgeToEdge() in the hosting
    // Activity; only the icon appearance has to follow the app theme, because the app can
    // be in dark mode while the system is not.
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalAppDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = PokemonShapes,
            content = content
        )
    }
}
