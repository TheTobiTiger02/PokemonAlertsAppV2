package com.example.pokemonalertsv2.navigation

import com.example.pokemonalertsv2.ui.settings.SettingsDestination

/**
 * Where a `pokemonalerts://` link wants to land.
 *
 * Notifications and widgets navigate with an `EXTRA_INITIAL_TAB` int, which works but is
 * only reachable from inside the app's own PendingIntents. These links are an additional
 * entry path onto the same state, so nothing about the existing extras changes.
 */
internal sealed interface DeepLinkTarget {
    /** A root tab, by its index in `NAV_DESTINATIONS`. */
    data class RootTab(val tabIndex: Int) : DeepLinkTarget

    /** A settings sub-page. Also selects the Settings tab. */
    data class Settings(val destination: SettingsDestination) : DeepLinkTarget

    /** A single alert, resolved against the repository before it can be shown. */
    data class Alert(val alertId: String) : DeepLinkTarget
}

internal const val DEEP_LINK_SCHEME = "pokemonalerts"

private const val TAB_ALERTS = 0
private const val TAB_HISTORY = 1
private const val TAB_MAP = 2
private const val TAB_SETTINGS = 3

/**
 * Parses a deep link into a target, or null if it is not one of ours.
 *
 * Kept as a pure String -> target function rather than taking a [android.net.Uri] so it can
 * be unit-tested on the JVM without Robolectric; the Activity does the Uri.toString().
 *
 * Accepted:
 *  - `pokemonalerts://alerts`, `://history`, `://map`, `://settings`
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
        "alerts" -> DeepLinkTarget.RootTab(TAB_ALERTS)
        "history" -> DeepLinkTarget.RootTab(TAB_HISTORY)
        "map" -> DeepLinkTarget.RootTab(TAB_MAP)
        "settings" -> {
            val name = tail.firstOrNull()
                ?: return DeepLinkTarget.RootTab(TAB_SETTINGS)
            val destination = SettingsDestination.entries
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: return DeepLinkTarget.RootTab(TAB_SETTINGS)
            DeepLinkTarget.Settings(destination)
        }
        // The id is the rest of the path, so an id containing a slash still round-trips.
        "alert" -> tail.joinToString("/").takeIf { it.isNotBlank() }?.let(DeepLinkTarget::Alert)
        else -> null
    }
}
