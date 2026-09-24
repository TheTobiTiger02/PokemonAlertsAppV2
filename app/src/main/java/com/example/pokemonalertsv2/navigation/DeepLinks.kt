package com.example.pokemonalertsv2.navigation

import com.example.pokemonalertsv2.ui.settings.SettingsDestination

/**
 * Where a `pokemonalerts://` link wants to land.
 *
 * Links and intents resolve to stable destinations. Old numeric extras remain readable.
 */
internal sealed interface DeepLinkTarget {
    data class RootTab(val request: AppNavigationRequest) : DeepLinkTarget

    /** A settings sub-page. Also selects the Settings tab. */
    data class Settings(val destination: SettingsDestination) : DeepLinkTarget

    /** A single alert, resolved against the repository before it can be shown. */
    data class Alert(val alertId: String) : DeepLinkTarget
}

internal const val DEEP_LINK_SCHEME = "pokemonalerts"

/**
 * Parses a deep link into a target, or null if it is not one of ours.
 *
 * Kept as a pure String -> target function rather than taking a [android.net.Uri] so it can
 * be unit-tested on the JVM without Robolectric; the Activity does the Uri.toString().
 *
 * Accepted:
 *  - `pokemonalerts://alerts`, `://history`, `://map`, `://events`, `://settings`
 *  - `pokemonalerts://settings/<destination-name>` (case-insensitive, e.g. `godex`)
 *  - `pokemonalerts://alert/<id>`
 */
internal fun parseDeepLink(url: String?): DeepLinkTarget? {
    val raw = url?.trim().orEmpty()
    val prefix = "$DEEP_LINK_SCHEME://"
    if (!raw.startsWith(prefix, ignoreCase = true)) return null

    // Strip any query/fragment: none of these targets take parameters, and leaving them on
    // would make "map?foo=1" miss.
    val path = raw.removeRange(0, prefix.length)
        .substringBefore('?')
        .substringBefore('#')
        .trim('/')
    if (path.isEmpty()) return null

    val segments = path.split('/').filter { it.isNotBlank() }
    val head = segments.firstOrNull()?.lowercase() ?: return null
    val tail = segments.drop(1)

    return when (head) {
        "alerts" -> DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.ALERTS))
        "history" -> DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.ALERTS, AlertsView.HISTORY))
        "map" -> DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.MAP))
        "events" -> DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.EVENTS))
        "settings" -> {
            val name = tail.firstOrNull()
                ?: return DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.SETTINGS))
            val destination = SettingsDestination.entries
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: return DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.SETTINGS))
            DeepLinkTarget.Settings(destination)
        }
        // The id is the rest of the path, so an id containing a slash still round-trips.
        "alert" -> tail.joinToString("/").takeIf { it.isNotBlank() }?.let(DeepLinkTarget::Alert)
        else -> null
    }
}
