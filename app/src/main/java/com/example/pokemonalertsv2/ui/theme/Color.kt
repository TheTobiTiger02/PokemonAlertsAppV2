package com.example.pokemonalertsv2.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Static Material 3 palettes for Pokemon Alerts.
 *
 * The product deliberately keeps an electric-blue primary across devices instead of
 * inheriting dynamic wallpaper colours. Secondary and tertiary roles are neutral so
 * alert type is communicated by copy and iconography, not a rainbow of card colours.
 */

// Light palette
val LuminousBackground = Color(0xFFFAF9FD)
val LuminousSurface = Color(0xFFFAF9FD)
val LuminousSurfaceDim = Color(0xFFDBD9DE)
val LuminousSurfaceBright = Color(0xFFFAF9FD)
val LuminousSurfaceContainerLowest = Color(0xFFFFFFFF)
val LuminousSurfaceContainerLow = Color(0xFFF4F3F7)
val LuminousSurfaceContainer = Color(0xFFEFEFF3)
val LuminousSurfaceContainerHigh = Color(0xFFE9E8EC)
val LuminousSurfaceContainerHighest = Color(0xFFE3E2E6)
val LuminousOnSurface = Color(0xFF1A1B20)
val LuminousOnSurfaceVariant = Color(0xFF44474E)
val LuminousInverseSurface = Color(0xFF2F3036)
val LuminousInverseOnSurface = Color(0xFFF2F0F4)
val LuminousOutline = Color(0xFF74777F)
val LuminousOutlineVariant = Color(0xFFC4C6CF)

val LuminousPrimary = Color(0xFF0058BE)
val LuminousOnPrimary = Color.White
val LuminousPrimaryContainer = Color(0xFFD7E2FF)
val LuminousOnPrimaryContainer = Color(0xFF001B3F)
val LuminousInversePrimary = Color(0xFFADC6FF)
val LuminousSecondary = Color(0xFF565E70)
val LuminousOnSecondary = Color.White
val LuminousSecondaryContainer = Color(0xFFDAE2F8)
val LuminousOnSecondaryContainer = Color(0xFF131C2B)
val LuminousTertiary = Color(0xFF4F5F78)
val LuminousOnTertiary = Color.White
val LuminousTertiaryContainer = Color(0xFFD7E3FF)
val LuminousOnTertiaryContainer = Color(0xFF0A1C35)
val LuminousError = Color(0xFFBA1A1A)
val LuminousOnError = Color.White
val LuminousErrorContainer = Color(0xFFFFDAD6)
val LuminousOnErrorContainer = Color(0xFF410002)

// Dark palette
val MidnightBackground = Color(0xFF121318)
val MidnightSurface = Color(0xFF121318)
val MidnightSurfaceDim = Color(0xFF121318)
val MidnightSurfaceBright = Color(0xFF38393F)
val MidnightSurfaceContainerLowest = Color(0xFF0D0E13)
val MidnightSurfaceContainerLow = Color(0xFF1A1B20)
val MidnightSurfaceContainer = Color(0xFF1E1F24)
val MidnightSurfaceContainerHigh = Color(0xFF292A2F)
val MidnightSurfaceContainerHighest = Color(0xFF33343A)
val MidnightOnSurface = Color(0xFFE3E2E6)
val MidnightOnSurfaceVariant = Color(0xFFC4C6CF)
val MidnightInverseSurface = Color(0xFFE3E2E6)
val MidnightInverseOnSurface = Color(0xFF2F3036)
val MidnightOutline = Color(0xFF8E9099)
val MidnightOutlineVariant = Color(0xFF44474E)

val MidnightPrimary = Color(0xFFADC6FF)
val MidnightOnPrimary = Color(0xFF002E6A)
val MidnightPrimaryContainer = Color(0xFF00458F)
val MidnightOnPrimaryContainer = Color(0xFFD7E2FF)
val MidnightInversePrimary = Color(0xFF0058BE)
val MidnightSecondary = Color(0xFFBEC6DA)
val MidnightOnSecondary = Color(0xFF283142)
val MidnightSecondaryContainer = Color(0xFF3E4758)
val MidnightOnSecondaryContainer = Color(0xFFDAE2F8)
val MidnightTertiary = Color(0xFFB9C7E4)
val MidnightOnTertiary = Color(0xFF223149)
val MidnightTertiaryContainer = Color(0xFF39495F)
val MidnightOnTertiaryContainer = Color(0xFFD7E3FF)
val MidnightError = Color(0xFFFFB4AB)
val MidnightOnError = Color(0xFF690005)
val MidnightErrorContainer = Color(0xFF93000A)
val MidnightOnErrorContainer = Color(0xFFFFDAD6)

// Semantic feedback colours. These never identify an alert category.
val SuccessGreen = Color(0xFF2E7D32)
val WarningAmber = Color(0xFF8A5A00)
val DangerRed = LuminousError
