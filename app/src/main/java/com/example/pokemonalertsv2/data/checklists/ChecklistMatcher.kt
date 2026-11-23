package com.example.pokemonalertsv2.data.checklists

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.database.ChecklistEntity
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexEvolutionGraph
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexMatcher

/** A checklist that still needs the alert's Pokémon ([viaEvolution]: what it evolves into). */
data class ChecklistBadge(
    val checklistId: String,
    val title: String,
    val kind: ChecklistKind,
    val viaEvolution: Boolean = false
)

/**
 * Which checklists badge an alert. Only checklists switched on for alerts take part, and only
 * when their kind fits the alert: Hundo checklists hundo alerts, Shadow checklists shadow raids,
 * Dynamax checklists Max Battles, every other kind any alert of the species (the scanner cannot
 * tell whether a spawn is shiny or lucky, so those mean "you could still use one").
 */
object ChecklistMatcher {
    fun kindFits(kind: ChecklistKind, alert: PokemonAlert): Boolean = when (kind) {
        ChecklistKind.HUNDO -> alert.hasType("hundo")
        ChecklistKind.SHADOW -> isShadow(alert)
        ChecklistKind.DYNAMAX -> alert.isMaxBattle
        else -> true
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
            if (!checklist.useForAlerts) return@mapNotNull null
            val kind = ChecklistKind.fromName(checklist.kind)
            if (!kindFits(kind, alert)) return@mapNotNull null
            val list = entries[checklist.id].orEmpty()
            if (list.isEmpty()) return@mapNotNull null
            val result = GoDexMatcher.match(
                alert = alert,
                entries = list,
                configured = true,
                evolutionGraph = evolutionGraph.takeIf { checklist.includeEvolutions }
            )
            when (result.status) {
                GoDexMatchStatus.NEEDED -> ChecklistBadge(checklist.id, checklist.title, kind)
                GoDexMatchStatus.EVOLUTION_NEEDED,
                GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED ->
                    ChecklistBadge(checklist.id, checklist.title, kind, viaEvolution = true).takeIf { checklist.includeEvolutions }
                else -> null
            }
        }
    }
}
