package com.example.pokemonalertsv2.data

/**
 * Combining the filters of every surface into the one question push subscription can ask:
 * *could any part of this app want this alert?*
 *
 * FCM is the only path that wakes the app in the background —
 * [com.example.pokemonalertsv2.work.AlertWorker] runs on demand, never on a schedule — so every
 * subscribed topic costs a device wakeup per alert. Only what acts while the app is closed earns
 * one: see [backgroundDeliveryDefinitions]. Feed and Map poll while visible and delta-sync when
 * opened, so they do not need pushes of their own.
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
            commonSpecies = left.commonSpecies.union(right.commonSpecies),
            hundoSpecies = left.hundoSpecies.union(right.hundoSpecies),
            nundoSpecies = left.nundoSpecies.union(right.nundoSpecies),
            pvpSpecies = left.pvpSpecies.union(right.pvpSpecies),
            raidSpecies = left.raidSpecies.union(right.raidSpecies),
            raidTiers = left.raidTiers.union(right.raidTiers),
            rocketTypes = left.rocketTypes.union(right.rocketTypes),
            tappableSpecies = left.tappableSpecies.union(right.tappableSpecies),
            quests = QuestFilterRules()
        )
    }
}

/** The Feed, Map and Notifications definitions this document currently resolves to. */
fun FilterStateDocument.surfaceDefinitions(): List<FilterDefinition> =
    FilterSurface.entries.map { surface -> assignment(surface).resolve(this) }

/**
 * The surfaces that need alerts delivered while the app is in the background.
 *
 * Notifications always do. A hunt or journey also reads the local cache from its foreground
 * service with the app closed, for new targets and for alerts that get invalidated, so while one
 * is running ([trackingActive]) every surface counts. Placed widgets are added by the caller.
 */
fun FilterStateDocument.backgroundDeliveryDefinitions(trackingActive: Boolean): List<FilterDefinition> =
    if (trackingActive) surfaceDefinitions() else listOf(assignment(FilterSurface.NOTIFICATIONS).resolve(this))
