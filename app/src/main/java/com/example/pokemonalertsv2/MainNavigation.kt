package com.example.pokemonalertsv2

import com.example.pokemonalertsv2.ui.theme.Dimens
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/**
 * Bottom navigation destinations.
 * Onboarding is handled separately and is not part of the nav bar.
 */
internal data class NavDestination(
    /** Stable screen id, as carried by notification/widget extras and deep links. */
    val screen: Int,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

internal enum class NavigationLayoutMode { BOTTOM_BAR, RAIL }

internal fun navigationLayoutModeForWidth(width: Dp): NavigationLayoutMode =
    if (width >= Dimens.NavigationRailBreakpoint) NavigationLayoutMode.RAIL else NavigationLayoutMode.BOTTOM_BAR

// Screen ids. They predate History moving into the Alerts tab and stay stable because
// notification PendingIntents and widgets already on the home screen carry them.
internal const val ALERTS_TAB_INDEX = 0
internal const val HISTORY_SCREEN_INDEX = 1
internal const val MAP_TAB_INDEX = 2
internal const val EVENTS_TAB_INDEX = 3
/** Settings is a full-screen destination the gear opens, not a bottom-bar tab. */
internal const val SETTINGS_TAB_INDEX = 4
internal const val TOOLS_TAB_INDEX = 5

internal val NAV_DESTINATIONS = listOf(
    NavDestination(ALERTS_TAB_INDEX, R.string.navigation_alerts, Icons.Filled.Notifications, Icons.Outlined.Notifications),
    NavDestination(MAP_TAB_INDEX, R.string.navigation_map, Icons.Filled.LocationOn, Icons.Outlined.LocationOn),
    NavDestination(EVENTS_TAB_INDEX, R.string.navigation_events, Icons.Filled.Star, Icons.Outlined.Star),
    NavDestination(TOOLS_TAB_INDEX, R.string.navigation_tools, Icons.Filled.Build, Icons.Outlined.Build)
)

/** Everything MainScaffold can show as a root: the bottom-bar tabs plus Settings. */
internal val ROOT_ROUTE_INDICES = NAV_DESTINATIONS.map { it.screen } + SETTINGS_TAB_INDEX

internal fun rootTabIndexOrNull(index: Int): Int? =
    index.takeIf { it in ALERTS_TAB_INDEX..TOOLS_TAB_INDEX }

/**
 * Whether a bottom-bar item shows as selected. Settings is not a tab, so while it is open the
 * tab it was opened from stays highlighted.
 */
internal fun isNavTabSelected(tabScreen: Int, selectedScreen: Int, settingsOrigin: Int): Boolean =
    tabScreen == if (selectedScreen == SETTINGS_TAB_INDEX) settingsOrigin else selectedScreen

internal fun isNavDestinationSelected(destination: NavDestination, selectedScreen: Int, settingsOrigin: Int) =
    isNavTabSelected(destination.screen, selectedScreen, settingsOrigin)

/** The tab a screen id lives on: History is a section of the Alerts tab. */
internal fun tabForScreen(screen: Int): Int =
    if (screen == HISTORY_SCREEN_INDEX) ALERTS_TAB_INDEX else screen
