package com.example.pokemonalertsv2.data.checklists

import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.database.ChecklistEntity
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.filterAlertTypes
import com.example.pokemonalertsv2.data.godex.GoDexEvolutionGraph
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexMatcher

/**
 * A checklist that still needs the alert's Pokémon ([viaEvolution]: what it evolves into).
 * [shinySeen]: a Shiny checklist and an alert that says the Pokémon is shiny.
 */
data class ChecklistBadge(
    val checklistId: String,
    val title: String,
    val kind: ChecklistKind,
    val viaEvolution: Boolean = false,
    val shinySeen: Boolean = false
)

/**
 * Which checklists badge an alert. Only checklists switched on for alerts take part, and only
 * for the alert types they badge: the user's choice, or else their kind's
 * [defaultAlertTypes]. On top, a kind may need something of the alert itself ([kindCondition]):
 * a Hundo checklist a hundo, Shadow a shadow raid, Dynamax a Max Battle, XXL and XXS that size.
 * The scanner cannot tell whether a spawn is lucky or a costume, so those badges mean "you could
 * still use one".
 */
object ChecklistMatcher {
    private val ANY_POKEMON = setOf(
        FilterAlertType.SPAWN, FilterAlertType.COMMON, FilterAlertType.RAID, FilterAlertType.QUEST, FilterAlertType.MAX_BATTLE
    )
    private val WILD_SPAWNS = setOf(
        FilterAlertType.SPAWN, FilterAlertType.HUNDO, FilterAlertType.NUNDO, FilterAlertType.PVP, FilterAlertType.COMMON
    )

    /** The alert types a checklist of [kind] badges until the user picks others. */
    fun defaultAlertTypes(kind: ChecklistKind): Set<FilterAlertType> = when (kind) {
        ChecklistKind.HUNDO -> setOf(FilterAlertType.HUNDO)
        // Size only shows on encountered wild spawns.
        ChecklistKind.XXL, ChecklistKind.XXS -> WILD_SPAWNS
        ChecklistKind.SHADOW -> setOf(FilterAlertType.RAID)
        ChecklistKind.DYNAMAX -> setOf(FilterAlertType.MAX_BATTLE)
        ChecklistKind.SHINY, ChecklistKind.LUCKY, ChecklistKind.COSTUME, ChecklistKind.ANY -> ANY_POKEMON
    }

    /** The alert types that can badge at all: those that are a Pokémon to catch. */
    val selectableAlertTypes: List<FilterAlertType> = listOf(
        FilterAlertType.SPAWN, FilterAlertType.HUNDO, FilterAlertType.NUNDO, FilterAlertType.PVP, FilterAlertType.COMMON,
        FilterAlertType.RAID, FilterAlertType.MAX_BATTLE, FilterAlertType.QUEST, FilterAlertType.ROCKET, FilterAlertType.TAPPABLE
    )

    /** The types [checklist] badges: its own choice, or its kind's defaults. */
    fun alertTypes(checklist: ChecklistEntity): Set<FilterAlertType> =
        parseAlertTypes(checklist.alertTypes) ?: defaultAlertTypes(ChecklistKind.fromName(checklist.kind))

    /** Null for no choice of its own (also when nothing readable is stored). */
    fun parseAlertTypes(stored: String?): Set<FilterAlertType>? {
        if (stored == null) return null
        return stored.split(',').mapNotNullTo(LinkedHashSet()) { name -> FilterAlertType.entries.firstOrNull { it.name == name.trim() } }
    }

    /** What [alertTypes] stores; null when they are the kind's defaults, so a later default change applies. */
    fun storeAlertTypes(kind: ChecklistKind, types: Set<FilterAlertType>): String? =
        if (types == defaultAlertTypes(kind)) null else selectableAlertTypes.filter { it in types }.joinToString(",")

    /** What a kind needs of the alert besides its type. */
    fun kindCondition(kind: ChecklistKind, alert: PokemonAlert): Boolean = when (kind) {
        ChecklistKind.HUNDO -> alert.hasType("hundo") || alert.isPerfect
        ChecklistKind.SHADOW -> isShadow(alert)
        ChecklistKind.DYNAMAX -> alert.isMaxBattle
        ChecklistKind.XXL -> alert.size.equals("XXL", ignoreCase = true)
        ChecklistKind.XXS -> alert.size.equals("XXS", ignoreCase = true)
        else -> true
    }

    fun fits(checklist: ChecklistEntity, alert: PokemonAlert): Boolean {
        val types = alertTypes(checklist)
        return alert.filterAlertTypes().any { it in types } && kindCondition(ChecklistKind.fromName(checklist.kind), alert)
    }

    private fun isShadow(alert: PokemonAlert): Boolean =
        alert.hasType("shadow") || listOfNotNull(alert.pokemon, alert.name).any { it.trim().startsWith("Shadow ", ignoreCase = true) }

    fun badges(
        alert: PokemonAlert,
        checklists: List<ChecklistEntity>,
        entries: Map<String, List<GoDexEntryEntity>>,
        evolutionGraph: GoDexEvolutionGraph?
    ): List<ChecklistBadge> {
        if (alert.pokedexId == null) return emptyList()
        return checklists.mapNotNull { checklist ->
            if (!checklist.useForAlerts || !fits(checklist, alert)) return@mapNotNull null
            val kind = ChecklistKind.fromName(checklist.kind)
            val list = entries[checklist.id].orEmpty()
            if (list.isEmpty()) return@mapNotNull null
            val result = GoDexMatcher.match(
                alert = alert,
                entries = list,
                configured = true,
                evolutionGraph = evolutionGraph.takeIf { checklist.includeEvolutions }
            )
            val badge = ChecklistBadge(checklist.id, checklist.title, kind, shinySeen = kind == ChecklistKind.SHINY && alert.isShiny == true)
            when (result.status) {
                GoDexMatchStatus.NEEDED -> badge
                GoDexMatchStatus.EVOLUTION_NEEDED,
                GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED -> badge.copy(viaEvolution = true).takeIf { checklist.includeEvolutions }
                else -> null
            }
        }
    }
}
