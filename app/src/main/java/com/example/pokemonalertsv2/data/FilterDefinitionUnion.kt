package com.example.pokemonalertsv2.data

/**
 * Combining the filters of every surface into the one question push subscription can ask:
 * *could any part of this app want this alert?*
 *
 * FCM is the only path that wakes the app in the background — [com.example.pokemonalertsv2.work.AlertWorker]
 * runs on demand, never on a schedule — so an alert on no subscribed topic does not merely
 * skip a notification: the feed, map, widget and history never learn about it until the next
 * foreground refresh. Narrowing therefore has to be driven by the union of Feed, Map,
 * Notifications and every configured widget, not by the notification filter alone.
 */

fun FilterSelection.union(other: FilterSelection): FilterSelection = when {
    mode == FilterSelectionMode.ALL || other.mode == FilterSelectionMode.ALL -> FilterSelection.All
    mode == FilterSelectionMode.NONE && other.mode == FilterSelectionMode.NONE -> FilterSelection.None
    mode == FilterSelectionMode.NONE -> other
    other.mode == FilterSelectionMode.NONE -> this
    else -> FilterSelection(FilterSelectionMode.ONLY, normalizedValues + other.normalizedValues)
}

/**
 * The axis-wise union of several definitions.
 *
 * A [FilterDefinition] is conjunctive across axes, so unioning each axis independently yields a
 * *superset* of the true union — "Hundos in Alsbach" ∪ "Raids in Darmstadt" becomes
 * "Hundos or Raids in Alsbach or Darmstadt". That is the safe direction: over-delivery costs a
 * wakeup, under-delivery loses an alert.
 *
 * Distance is dropped rather than unioned. It cannot be expressed as a topic without geohash
 * tiles and stays a local check in [com.example.pokemonalertsv2.notifications.AlertNotifier].
 */
fun unionOf(definitions: List<FilterDefinition>): FilterDefinition {
    if (definitions.isEmpty()) return FilterDefinition()
    return definitions.reduce { left, right ->
        FilterDefinition(
            alertTypes = left.alertTypes.union(right.alertTypes),
            areas = left.areas.union(right.areas),
            maxDistanceMeters = 0,
            maxWalkingMinutes = 0,
            spawnSpecies = left.spawnSpecies.union(right.spawnSpecies),
            rareSpecies = left.rareSpecies.union(right.rareSpecies),
            hundoSpecies = left.hundoSpecies.union(right.hundoSpecies),
            nundoSpecies = left.nundoSpecies.union(right.nundoSpecies),
            pvpSpecies = left.pvpSpecies.union(right.pvpSpecies),
            raidSpecies = left.raidSpecies.union(right.raidSpecies),
            raidTiers = left.raidTiers.union(right.raidTiers),
            rocketTypes = left.rocketTypes.union(right.rocketTypes),
            quests = QuestFilterRules()
        )
    }
}

/** The Feed, Map and Notifications definitions this document currently resolves to. */
fun FilterStateDocument.surfaceDefinitions(): List<FilterDefinition> =
    FilterSurface.entries.map { surface -> assignment(surface).resolve(this) }
