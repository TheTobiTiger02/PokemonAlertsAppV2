package com.example.pokemonalertsv2.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NebulaPurple,
    onPrimary = Color.White,
    primaryContainer = AuroraViolet,
    onPrimaryContainer = Color.White,

    secondary = StardustBlue,
    onSecondary = Color(0xFF021221),
    secondaryContainer = ElectricCyan,
    onSecondaryContainer = Color(0xFF031B1B),

    tertiary = LuminousAmber,
    onTertiary = Color(0xFF2D1600),
    tertiaryContainer = EmberOrange,
    onTertiaryContainer = Color(0xFF2B0B00),

    background = SurfaceBase,
    onBackground = Color(0xFFE3E8FF),
    surface = SurfaceElevated,
    onSurface = Color(0xFFE8EAFF),
    surfaceVariant = SurfaceHighest,
    onSurfaceVariant = Color(0xFFB8C0FF),
    outline = SurfaceOutline,
    outlineVariant = Color(0xFF3F4C75),

    error = DangerRed,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = NebulaPurple,
    onPrimaryContainer = Color.White,

    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = StardustBlue,
    onSecondaryContainer = Color(0xFF00152E),

    tertiary = LuminousAmber,
    onTertiary = Color(0xFF3A1A00),
    tertiaryContainer = EmberOrange,
    onTertiaryContainer = Color(0xFF2D0B00),

    background = LightSky,
    onBackground = Color(0xFF0B1026),
    surface = LightSurface,
    onSurface = Color(0xFF060A1A),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF303B63),
    outline = LightOutline,
    outlineVariant = Color(0xFFC8CEFF),

    error = DangerRed,
    onError = Color.White,
)

@Composable
fun PokemonAlertsV2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled by default to use Pokémon colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PokemonShapes,
        content = content
    )
}