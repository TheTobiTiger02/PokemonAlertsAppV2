package com.example.pokemonalertsv2.fcm

import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PushTopicArea
import com.example.pokemonalertsv2.data.PushTopicCatalog
import com.example.pokemonalertsv2.data.PushTopicType
import com.example.pokemonalertsv2.data.filterAlertTypes
import com.example.pokemonalertsv2.data.normalizeFilterToken

/**
 * Turns the union of the app's filters into the set of FCM topics to subscribe to.
 *
 * Pure and Android-free so the whole decision table is unit-testable. Two rules govern every
 * branch below:
 *
 * 1. **Over-delivery is safe, under-delivery is a bug.** Where the app cannot *prove* a topic is
 *    unnecessary, it subscribes. Every failure — a missing catalog, an unrecognised schema, an
 *    area the catalog does not describe — falls back to the legacy broadcast topic, which is
 *    exactly today's behaviour.
 * 2. **Never both.** The result is either the legacy topic alone or derived topics alone; a
 *    device on both receives some alerts twice.
 */
object PushTopicPlanner {

    /** Well beyond any plausible plan against this catalog; a guard, not a budget. */
    const val MAX_CLIENT_TOPICS = 50

    /** Used only when the catalog has never been fetched, matching the pre-fanout build. */
    const val DEFAULT_LEGACY_TOPIC = "alerts"

    fun plan(catalog: PushTopicCatalog?, definition: FilterDefinition): Set<String> {
        val legacy = setOf(catalog?.legacyTopic?.takeIf { it.isNotBlank() } ?: DEFAULT_LEGACY_TOPIC)
        if (catalog == null || !catalog.isUsable) return legacy

        val selectedTypes = selectedCatalogTypes(catalog, definition) ?: emptySet()
        val selectedAreas = selectedCatalogAreas(catalog, definition) ?: emptySet()
        val typeNarrows = selectedTypes.isNotEmpty()
        val areaNarrows = selectedAreas.isNotEmpty()

        val candidates = buildList {
            if (typeNarrows && areaNarrows) {
                crossTopics(catalog, selectedAreas, selectedTypes)?.let(::add)
            }
            val typeRatio = selectedTypes.size.toDouble() / catalog.types.size
            val areaRatio = areaClosure(catalog, selectedAreas).size.toDouble() / catalog.areas.size
            val singles = buildList {
                if (typeNarrows) add(typeRatio to selectedTypes.map { it.topic }.toSet())
                if (areaNarrows) add(areaRatio to selectedAreas.map(PushTopicArea::coveringTopic).toSet())
            }
            addAll(singles.sortedBy { it.first }.map { it.second })
        }

        return candidates.firstOrNull { it.isNotEmpty() && it.size <= MAX_CLIENT_TOPICS } ?: legacy
    }

    /**
     * The catalog types whose alerts the definition could want, or null when the type axis
     * cannot narrow at all.
     *
     * The server's vocabulary has no `Spawn` value: a plain spawn is published as Hundo, Nundo,
     * PvP or Rare, all of which the app also classifies as [FilterAlertType.SPAWN], so selecting
     * SPAWN resolves to that family rather than to nothing. This relies on the server giving
     * every alert at least one type from the catalog — an alert with an empty or unlisted type
     * carries no type topic at all, which `PushTopicCoverageTest` checks against a real snapshot.
     */
    internal fun selectedCatalogTypes(
        catalog: PushTopicCatalog,
        definition: FilterDefinition
    ): Set<PushTopicType>? {
        if (definition.alertTypes.mode != FilterSelectionMode.ONLY) return null
        // OTHER is the app's catch-all for an alert it could not classify, and an unclassifiable
        // alert is on no derived topic by definition.
        if (definition.alertTypes.contains(FilterAlertType.OTHER.name)) return null

        val classified = catalog.types.associateWith { classify(it.value) }
        val wanted = classified.filterValues { categories ->
            categories.any { definition.alertTypes.contains(it.name) }
        }.keys.toMutableSet()

        // HUNDO and NUNDO are derived from the IV fields, not from the type list, so a server-side
        // "PvP" alert with 15/15/15 would be a hundo to this app while carrying only the PvP
        // topic. Selecting either therefore pulls in the whole spawn family.
        //
        // Today the server does tag every applicable type at once — the snapshot behind
        // PushTopicCoverageTest holds a ["Hundo", "PvP"] alert and no perfect-IV alert missing
        // its Hundo tag — so this widening is insurance rather than a live fix. It is kept
        // because the failure it prevents is silent: the spawn family is around 1% of the feed,
        // and an alert that never arrives leaves no trace to debug. (ROCKET is promoted the same
        // way, from gruntType, but that field is structurally Rocket-only.)
        val ivDerived = listOf(FilterAlertType.HUNDO, FilterAlertType.NUNDO)
        if (ivDerived.any { definition.alertTypes.contains(it.name) }) {
            wanted += classified.filterValues { FilterAlertType.SPAWN in it }.keys
        }

        // A selected category with no catalog type behind it cannot be served by this axis.
        val covered = wanted.flatMap { classified.getValue(it) }.toSet()
        val unservable = FilterAlertType.entries.filter {
            definition.alertTypes.contains(it.name) && it !in covered
        }
        if (unservable.isNotEmpty()) return null
        return wanted.takeIf { it.isNotEmpty() && it.size < catalog.types.size }
    }

    /**
     * The catalog areas the definition could want, or null when the area axis cannot narrow.
     *
     * Each resolves to its [PushTopicArea.coveringTopic] — the group, never the bare zone. A
     * zone alert reaches both, but an older alert carrying only the group label reaches only the
     * group topic, so a zone-only subscriber would miss it.
     */
    internal fun selectedCatalogAreas(
        catalog: PushTopicCatalog,
        definition: FilterDefinition
    ): Set<PushTopicArea>? {
        if (definition.areas.mode != FilterSelectionMode.ONLY) return null
        val byToken = catalog.areas.associateBy { normalizeFilterToken(it.value) }
        val selected = definition.areas.normalizedValues.map { byToken[it] ?: return null }.toSet()
        return selected.takeIf { it.isNotEmpty() && it.size < catalog.areas.size }
    }

    /**
     * Every catalog area whose alerts land under the selected areas' covering topics — the
     * selection plus its group siblings.
     */
    private fun areaClosure(catalog: PushTopicCatalog, selected: Set<PushTopicArea>): Set<PushTopicArea> {
        val groups = selected.mapTo(mutableSetOf()) { it.group ?: it.value }
        return catalog.areas.filterTo(mutableSetOf()) { (it.group ?: it.value) in groups }
    }

    /**
     * The area×type topics, or null when the catalog does not list every pair.
     *
     * The pairs are taken over the group closure rather than the selected zones alone: the
     * backend documents the zone-to-group rollup for the area axis only, so whether a
     * `Darmstadt-North` hundo also reaches `alerts-a-darmstadt-t-hundo` is not something the
     * client may assume. Subscribing to both costs a few topics and cannot miss.
     */
    private fun crossTopics(
        catalog: PushTopicCatalog,
        selectedAreas: Set<PushTopicArea>,
        selectedTypes: Set<PushTopicType>
    ): Set<String>? {
        val topics = mutableSetOf<String>()
        areaClosure(catalog, selectedAreas).forEach { area ->
            selectedTypes.forEach { type ->
                val pair = catalog.areaType(area.value, type.value) ?: return null
                if (pair.topic.isBlank()) return null
                topics += pair.topic
            }
        }
        return topics.takeIf { it.isNotEmpty() }
    }

    /**
     * What this app would call an alert carrying only this server type, using the app's own
     * classifier rather than a parallel string table that would drift from it.
     */
    private fun classify(typeValue: String): Set<FilterAlertType> =
        PokemonAlert(name = "", type = listOf(typeValue)).filterAlertTypes()
}
