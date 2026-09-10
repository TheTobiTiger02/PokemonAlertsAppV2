package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode

/**
 * The two vocabularies a hunt is expressed in, and the way between them.
 *
 * [HuntTargetSheet] edits a *draft*, where a section the trainer never opened is
 * None — so the shared chip controls start with nothing ticked and the first tap
 * adds rather than removes. A *hunt* is the same definition with those Nones
 * turned back into All, because None matches nothing and a Rocket hunt with no
 * grunt chosen would find zero targets.
 *
 * Kept out of the sheet so both directions can be tested without Compose.
 */

/** Nothing chosen yet, in every dimension. */
internal val EMPTY_HUNT_DRAFT = FilterDefinition(
    alertTypes = FilterSelection.None,
    spawnSpecies = FilterSelection.None,
    rareSpecies = FilterSelection.None,
    hundoSpecies = FilterSelection.None,
    nundoSpecies = FilterSelection.None,
    pvpSpecies = FilterSelection.None,
    raidSpecies = FilterSelection.None,
    raidTiers = FilterSelection.None,
    rocketTypes = FilterSelection.None
)

/**
 * Turns "not picked" back into "any" before the hunt runs.
 *
 * The alert types are exempt: those really do have to be chosen.
 */
internal fun FilterDefinition.forHunt(): FilterDefinition = copy(
    spawnSpecies = spawnSpecies.orAny(),
    rareSpecies = rareSpecies.orAny(),
    hundoSpecies = hundoSpecies.orAny(),
    nundoSpecies = nundoSpecies.orAny(),
    pvpSpecies = pvpSpecies.orAny(),
    raidSpecies = raidSpecies.orAny(),
    raidTiers = raidTiers.orAny(),
    rocketTypes = rocketTypes.orAny()
)

/**
 * A saved hunt, back in the sheet's None-first vocabulary.
 *
 * The inverse of [forHunt]: All becomes None so an untouched section shows as
 * untouched, and a narrowed one is kept verbatim. The alert types are normalized
 * to an explicit ONLY of everything rather than left at All, because
 * [chosenTypes] reads anything but ONLY as "nothing chosen yet" and the Start
 * button would be dead on a hunt that had in fact been started before.
 */
internal fun FilterDefinition.forHuntDraft(): FilterDefinition = copy(
    alertTypes = alertTypes.orAllTypes(),
    spawnSpecies = spawnSpecies.orNone(),
    rareSpecies = rareSpecies.orNone(),
    hundoSpecies = hundoSpecies.orNone(),
    nundoSpecies = nundoSpecies.orNone(),
    pvpSpecies = pvpSpecies.orNone(),
    raidSpecies = raidSpecies.orNone(),
    raidTiers = raidTiers.orNone(),
    rocketTypes = rocketTypes.orNone()
)

private fun FilterSelection.orAny(): FilterSelection =
    if (mode == FilterSelectionMode.NONE) FilterSelection.All else this

private fun FilterSelection.orNone(): FilterSelection =
    if (mode == FilterSelectionMode.ALL) FilterSelection.None else this

private fun FilterSelection.orAllTypes(): FilterSelection =
    if (mode == FilterSelectionMode.ONLY) this
    else FilterSelection.only(FilterAlertType.entries.map { it.name })

/** The alert types actually narrowed to; empty means nothing has been chosen yet. */
internal fun FilterDefinition.chosenTypes(): List<FilterAlertType> =
    if (alertTypes.mode != FilterSelectionMode.ONLY) {
        emptyList()
    } else {
        FilterAlertType.entries.filter { alertTypes.contains(it.name) }
    }
