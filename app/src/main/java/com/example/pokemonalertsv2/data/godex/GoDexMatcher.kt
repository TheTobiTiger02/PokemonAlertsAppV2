package com.example.pokemonalertsv2.data.godex

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import java.text.Normalizer
import java.util.ArrayDeque
import java.util.Locale

object GoDexMatcher {
    fun match(
        alert: PokemonAlert,
        entries: List<GoDexEntryEntity>,
        configured: Boolean,
        evolutionGraph: GoDexEvolutionGraph? = null
    ): GoDexMatchResult {
        if (!configured) return GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED)
        val pokedexId = alert.pokedexId ?: return GoDexMatchResult(GoDexMatchStatus.UNKNOWN)
        val alertForm = normalizedAlertForm(alert.pokemonForm)
        val alertGender = normalizedGender(alert.gender)
        val resolved = resolveDirect(pokedexId, alertForm, alertGender, entries)
            ?: return GoDexMatchResult(GoDexMatchStatus.UNKNOWN)

        if (resolved.needed) return GoDexMatchResult(GoDexMatchStatus.NEEDED)
        // A base-only GoDex entry may match an unrepresented costume. Never assume that
        // such a costume can evolve unless the graph explicitly contains that form.
        if (alertForm != null && resolved.formSlug == null) {
            return GoDexMatchResult(GoDexMatchStatus.COLLECTED)
        }

        val targets = evolutionGraph?.let {
            findNeededDescendants(
                sourceId = pokedexId,
                sourceForm = resolved.formSlug,
                sourceGender = alertGender,
                entries = entries,
                graph = it
            )
        }.orEmpty()
        return if (targets.isEmpty()) {
            GoDexMatchResult(GoDexMatchStatus.COLLECTED)
        } else {
            GoDexMatchResult(GoDexMatchStatus.EVOLUTION_NEEDED, targets)
        }
    }

    private fun resolveDirect(
        pokedexId: Int,
        alertForm: String?,
        alertGender: String?,
        entries: List<GoDexEntryEntity>
    ): GoDexEntryEntity? {
        val speciesEntries = entries.filter { it.pokedexId == pokedexId }
        if (speciesEntries.isEmpty()) return null
        val hasExplicitForms = speciesEntries.any { it.formSlug != null }
        val formCandidates = if (alertForm != null) {
            speciesEntries.filter { it.formSlug == alertForm }
        } else {
            speciesEntries.filter { it.formSlug == null }
        }
        val candidates = when {
            formCandidates.isNotEmpty() -> formCandidates
            !hasExplicitForms -> speciesEntries.filter { it.formSlug == null }
            else -> return null
        }
        return resolveGender(candidates, alertGender)
    }

    private fun resolveExactEvolutionTarget(
        pokedexId: Int,
        form: String?,
        gender: String?,
        entries: List<GoDexEntryEntity>
    ): GoDexEntryEntity? = resolveGender(
        entries.filter { it.pokedexId == pokedexId && it.formSlug == form },
        gender
    )

    private fun resolveGender(candidates: List<GoDexEntryEntity>, gender: String?): GoDexEntryEntity? {
        if (candidates.isEmpty()) return null
        val gendered = candidates.filter { it.gender != "none" }
        return if (gendered.isEmpty()) {
            candidates.firstOrNull { it.gender == "none" } ?: candidates.singleOrNull()
        } else {
            gender?.let { expected -> gendered.singleOrNull { it.gender == expected } }
        }
    }

    private data class EvolutionState(
        val pokedexId: Int,
        val form: String?,
        val gender: String?,
        val distance: Int
    )

    private fun findNeededDescendants(
        sourceId: Int,
        sourceForm: String?,
        sourceGender: String?,
        entries: List<GoDexEntryEntity>,
        graph: GoDexEvolutionGraph
    ): List<GoDexEvolutionTarget> {
        val queue = ArrayDeque<EvolutionState>()
        queue.add(EvolutionState(sourceId, sourceForm, sourceGender, 0))
        val visited = hashSetOf(Triple(sourceId, sourceForm, sourceGender))
        val targets = linkedMapOf<String, GoDexEvolutionTarget>()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            graph.outgoing(current.pokedexId)
                .asSequence()
                .filter { it.fromForm == current.form }
                .filter { it.sourceGender == null || it.sourceGender == current.gender }
                .forEach { edge ->
                    val next = EvolutionState(edge.to, edge.toForm, current.gender, current.distance + 1)
                    val visitKey = Triple(next.pokedexId, next.form, next.gender)
                    if (!visited.add(visitKey)) return@forEach
                    resolveExactEvolutionTarget(next.pokedexId, next.form, next.gender, entries)
                        ?.takeIf { it.needed }
                        ?.let { entry ->
                            targets.putIfAbsent(
                                entry.entryKey,
                                GoDexEvolutionTarget(
                                    entryKey = entry.entryKey,
                                    pokedexId = entry.pokedexId,
                                    displayName = entry.displayName,
                                    distance = next.distance
                                )
                            )
                        }
                    queue.add(next)
                }
        }
        return targets.values.sortedWith(compareBy(GoDexEvolutionTarget::distance, GoDexEvolutionTarget::pokedexId))
    }

    internal fun normalizedAlertForm(value: String?): String? {
        val normalized = value.normalizedToken() ?: return null
        val aliases = mapOf(
            "alola" to "alola", "alolan" to "alola",
            "galar" to "galar", "galarian" to "galar",
            "hisui" to "hisui", "hisuian" to "hisui",
            "paldea" to "paldea", "paldean" to "paldea",
            "baile style" to "baile", "pau style" to "pau",
            "pom pom style" to "pom", "sensu style" to "sensu",
            "east sea" to "east", "west sea" to "west",
            "blue striped" to "blue", "red striped" to "red", "white striped" to "white",
            "plant cloak" to "plant", "sandy cloak" to "sandy", "trash cloak" to "trash",
            "spring form" to "spring", "summer form" to "summer",
            "autumn form" to "autumn", "winter form" to "winter",
            "red flower" to "red_flower", "orange flower" to "orange_flower",
            "yellow flower" to "yellow_flower", "white flower" to "white_flower",
            "blue flower" to "blue_flower",
            "exclamation" to "zem", "question" to "zim"
        )
        aliases[normalized]?.let { return it }
        Regex("(?:spinda )?(\\d{1,2})").matchEntire(normalized)?.let {
            return it.groupValues[1].padStart(2, '0')
        }
        Regex("(?:unown )?([a-z])").matchEntire(normalized)?.let { return it.groupValues[1] }
        return normalized.replace(" ", "_")
    }

    private fun normalizedGender(value: String?): String? = value?.trim()?.lowercase(Locale.ROOT)?.let {
        when {
            it.startsWith("m") -> "male"
            it.startsWith("f") -> "female"
            else -> null
        }
    }

    private fun String?.normalizedToken(): String? = this
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
        ?.replace(Regex("\\p{M}+"), "")
        ?.lowercase(Locale.ROOT)
        ?.replace("'", "")
        ?.replace(Regex("[^a-z0-9]+"), " ")
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotEmpty() }
}
