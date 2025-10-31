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
    primary = PokeRedDark,
    onPrimary = Color.White,
    primaryContainer = PokeRedDarkVariant,
    onPrimaryContainer = Color.White,
    
    secondary = PokeBlueDark,
    onSecondary = Color.White,
    secondaryContainer = PokeBlueDarkVariant,
    onSecondaryContainer = PokeBlack,
    
    tertiary = PokeYellowDark,
    onTertiary = PokeBlack,
    tertiaryContainer = PokeYellowDarkVariant,
    onTertiaryContainer = PokeBlack,
    
    background = Color(0xFF121212),
    onBackground = Color(0xFFE1E1E1),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE1E1E1),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFCACACA),
    
    error = Color(0xFFCF6679),
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = PokeRed,
    onPrimary = Color.White,
    primaryContainer = PokeRedLight,
    onPrimaryContainer = Color.White,
    
    secondary = PokeBlue,
    onSecondary = Color.White,
    secondaryContainer = PokeBlueLight,
    onSecondaryContainer = Color.White,
    
    tertiary = PokeYellow,
    onTertiary = PokeBlack,
    tertiaryContainer = PokeYellowLight,
    onTertiaryContainer = PokeBlack,
    
    background = Color(0xFFFDFDFD),
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF444444),
    
    error = Color(0xFFB00020),
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
        content = content
    )
}