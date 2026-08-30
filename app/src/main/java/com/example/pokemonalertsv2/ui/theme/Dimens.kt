package com.example.pokemonalertsv2.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The app's spacing scale.
 *
 * Card internals had drifted into one-off values (9.dp, 14.dp, 7.dp) that read as
 * deliberate but were not, so the same visual gap was spelled three different ways in
 * three files. Reach for the nearest step rather than adding a new literal.
 */
object Spacing {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

/**
 * Layout constants that more than one composable has to agree on.
 *
 * [AlertCardHeroHeight] in particular is shared by the real alert card and its shimmer
 * placeholder; when it lived as a literal in both, the skeleton could silently stop
 * matching the content it stands in for.
 */
object Dimens {
    val AlertCardHeroHeight = 112.dp
    val AlertDetailHeroHeight = 240.dp

    /** Bottom bar below this width, navigation rail at or above it. */
    val NavigationRailBreakpoint = 600.dp

    /** Single-column feed below this width, two columns at or above it. */
    val TwoColumnBreakpoint = 840.dp
}

/**
 * Named alpha steps, so a surface's translucency is a decision recorded once rather than a
 * float repeated at each call site.
 */
object Alphas {
    const val Scrim = 0.92f
    const val Elevated = 0.85f
    const val Muted = 0.72f
    const val Disabled = 0.45f
    const val Track = 0.36f
    const val Tint = 0.16f
    const val Hairline = 0.05f
}
