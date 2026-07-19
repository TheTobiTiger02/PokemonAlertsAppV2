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
        evolutionGraph: GoDexEvolutionGraph? = null,
        formChangeGraph: GoDexFormChangeGraph? = null
    ): GoDexMatchResult {
        if (!configured) return GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED)
        val pokedexId = alert.pokedexId ?: return GoDexMatchResult(GoDexMatchStatus.UNKNOWN)
        val explicitGender = normalizedGender(alert.gender)
        val formGender = normalizedFormGender(alert.pokemonForm)
        if (explicitGender != null && formGender != null && explicitGender != formGender) {
            return GoDexMatchResult(GoDexMatchStatus.UNKNOWN)
        }
        val alertGender = explicitGender ?: formGender
        val resolved = resolveDirect(pokedexId, alert.pokemonForm, alertGender, entries)
            ?: return GoDexMatchResult(GoDexMatchStatus.UNKNOWN)

        if (resolved.needed) return GoDexMatchResult(GoDexMatchStatus.NEEDED, resolved.entryKey)

        val targets = evolutionGraph?.let {
            findNeededDescendants(
                sourceId = pokedexId,
                sourceForm = resolved.formSlug,
                sourceGender = alertGender,
                entries = entries,
                graph = it
            )
        }.orEmpty()
        val formChangeTargets = formChangeGraph?.let {
            findNeededFormChanges(
                pokedexId = pokedexId,
                sourceForm = resolved.formSlug,
                sourceGender = alertGender,
                entries = entries,
                graph = it
            )
        }.orEmpty()
        val status = when {
            targets.isNotEmpty() && formChangeTargets.isNotEmpty() ->
                GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED
            targets.isNotEmpty() -> GoDexMatchStatus.EVOLUTION_NEEDED
            formChangeTargets.isNotEmpty() -> GoDexMatchStatus.FORM_CHANGE_NEEDED
            else -> GoDexMatchStatus.COLLECTED
        }
        return GoDexMatchResult(
            status = status,
            matchedEntryKey = resolved.entryKey,
            evolutionTargets = targets,
            formChangeTargets = formChangeTargets
        )
    }

    private fun resolveDirect(
        pokedexId: Int,
        alertForm: String?,
        alertGender: String?,
        entries: List<GoDexEntryEntity>
    ): GoDexEntryEntity? {
        val speciesEntries = entries.filter { it.pokedexId == pokedexId }
        if (speciesEntries.isEmpty()) return null
        val normalizedForm = normalizedAlertForm(alertForm)
        val exactCandidates = speciesEntries.filter { it.formSlug == normalizedForm }
        val candidates = when {
            exactCandidates.isNotEmpty() -> exactCandidates
            alertForm == null -> speciesEntries.filter { it.formSlug == null }
            else -> {
                val alertKeys = canonicalFormKeys(alertForm)
                val equivalentEntries = speciesEntries.filter { entry ->
                    entry.formSlug?.let(::canonicalFormKeys)?.any(alertKeys::contains) == true
                }
                when {
                    equivalentEntries.isNotEmpty() -> equivalentEntries
                    isExplicitBaseFormLabel(alertForm) -> speciesEntries.filter { it.formSlug == null }
                    normalizedFormGender(alertForm) != null && normalizedFormGender(alertForm) == alertGender ->
                        speciesEntries.filter { it.formSlug == null }
                    else -> return null
                }
            }
        }
        return resolveGender(candidates, alertGender)
    }

    private fun resolveReachableTarget(
        pokedexId: Int,
        form: String?,
        gender: String?,
        entries: List<GoDexEntryEntity>
    ): GoDexEntryEntity? {
        val speciesEntries = entries.filter { it.pokedexId == pokedexId }
        val exactCandidates = speciesEntries.filter { it.formSlug == form }
        val candidates = exactCandidates.ifEmpty {
            speciesEntries.filter { formsEquivalent(it.formSlug, form) }
        }
        return resolveGender(candidates, gender)
    }

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
                .filter { formsEquivalent(it.fromForm, current.form) }
                .filter { it.sourceGender == null || it.sourceGender == current.gender }
                .forEach { edge ->
                    val next = EvolutionState(edge.to, edge.toForm, current.gender, current.distance + 1)
                    val visitKey = Triple(next.pokedexId, next.form, next.gender)
                    if (!visited.add(visitKey)) return@forEach
                    resolveReachableTarget(next.pokedexId, next.form, next.gender, entries)
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

    private data class FormChangeState(
        val form: String?,
        val distance: Int
    )

    private fun findNeededFormChanges(
        pokedexId: Int,
        sourceForm: String?,
        sourceGender: String?,
        entries: List<GoDexEntryEntity>,
        graph: GoDexFormChangeGraph
    ): List<GoDexFormChangeTarget> {
        val queue = ArrayDeque<FormChangeState>()
        queue.add(FormChangeState(sourceForm, 0))
        val visited = hashSetOf(sourceForm)
        val targets = linkedMapOf<String, GoDexFormChangeTarget>()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            graph.outgoing(pokedexId)
                .asSequence()
                .filter { formsEquivalent(it.fromForm, current.form) }
                .forEach { edge ->
                    if (!visited.add(edge.toForm)) return@forEach
                    val next = FormChangeState(edge.toForm, current.distance + 1)
                    resolveReachableTarget(pokedexId, next.form, sourceGender, entries)
                        ?.takeIf { it.needed }
                        ?.let { entry ->
                            targets.putIfAbsent(
                                entry.entryKey,
                                GoDexFormChangeTarget(
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
        return targets.values.sortedWith(compareBy(GoDexFormChangeTarget::distance, GoDexFormChangeTarget::entryKey))
    }

    internal fun normalizedAlertForm(value: String?): String? {
        val normalized = value.normalizedToken() ?: return null
        FORM_ALIASES[normalized]?.let { return it }
        Regex("(?:spinda )?(\\d{1,2})").matchEntire(normalized)?.let {
            return it.groupValues[1].padStart(2, '0')
        }
        Regex("(?:unown )?([a-z])").matchEntire(normalized)?.let { return it.groupValues[1] }
        return normalized.replace(" ", "_")
    }

    private fun canonicalFormKeys(value: String): Set<String> {
        val normalized = value.normalizedToken() ?: return emptySet()
        val phrases = linkedSetOf(normalized)
        var words = normalized.split(' ')
        while (words.lastOrNull() in DESCRIPTIVE_SUFFIXES) {
            words = words.dropLast(1)
            if (words.isNotEmpty()) phrases += words.joinToString(" ")
        }

        val keys = linkedSetOf<String>()
        phrases.forEach { phrase ->
            val aliased = FORM_ALIASES[phrase] ?: phrase.replace(" ", "_")
            keys += aliased
            keys += aliased.replace("_", "")
        }
        normalizedAlertForm(value)?.let { primary ->
            keys += primary
            keys += primary.replace("_", "")
        }
        return keys
    }

    private fun formsEquivalent(first: String?, second: String?): Boolean {
        if (first == null || second == null) return first == second
        val firstKeys = canonicalFormKeys(first)
        return canonicalFormKeys(second).any(firstKeys::contains)
    }

    fun getCaughtEntries(
        alert: PokemonAlert,
        entries: List<GoDexEntryEntity>,
        evolutionGraph: GoDexEvolutionGraph? = null,
        formChangeGraph: GoDexFormChangeGraph? = null
    ): List<GoDexEntryEntity> {
        val pokedexId = alert.pokedexId ?: return emptyList()
        val alertGender = normalizedGender(alert.gender) ?: normalizedFormGender(alert.pokemonForm)
        val resolved = resolveDirect(pokedexId, alert.pokemonForm, alertGender, entries) ?: return emptyList()
        
        val list = mutableListOf<GoDexEntryEntity>()
        if (!resolved.needed) {
            list.add(resolved)
        }
        
        evolutionGraph?.let { graph ->
            val queue = ArrayDeque<EvolutionState>()
            queue.add(EvolutionState(pokedexId, resolved.formSlug, alertGender, 0))
            val visited = hashSetOf(Triple(pokedexId, resolved.formSlug, alertGender))
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                graph.outgoing(current.pokedexId)
                    .asSequence()
                    .filter { formsEquivalent(it.fromForm, current.form) }
                    .filter { it.sourceGender == null || it.sourceGender == current.gender }
                    .forEach { edge ->
                        val next = EvolutionState(edge.to, edge.toForm, current.gender, current.distance + 1)
                        val visitKey = Triple(next.pokedexId, next.form, next.gender)
                        if (!visited.add(visitKey)) return@forEach
                        resolveReachableTarget(next.pokedexId, next.form, next.gender, entries)
                            ?.let { entry ->
                                if (!entry.needed) {
                                    list.add(entry)
                                }
                            }
                        queue.add(next)
                    }
            }
        }
        
        formChangeGraph?.let { graph ->
            val queue = ArrayDeque<FormChangeState>()
            queue.add(FormChangeState(resolved.formSlug, 0))
            val visited = hashSetOf(resolved.formSlug)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                graph.outgoing(pokedexId)
                    .asSequence()
                    .filter { formsEquivalent(it.fromForm, current.form) }
                    .forEach { edge ->
                        if (!visited.add(edge.toForm)) return@forEach
                        val next = FormChangeState(edge.toForm, current.distance + 1)
                        resolveReachableTarget(pokedexId, next.form, alertGender, entries)
                            ?.let { entry ->
                                if (!entry.needed) {
                                    list.add(entry)
                                }
                            }
                        queue.add(next)
                    }
            }
        }
        return list.distinctBy { it.entryKey }
    }

    private fun isExplicitBaseFormLabel(value: String): Boolean =
        value.normalizedToken() in BASE_FORM_LABELS

    private fun normalizedFormGender(value: String?): String? = when (value.normalizedToken()) {
        "male", "male form" -> "male"
        "female", "female form" -> "female"
        else -> null
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

    private val DESCRIPTIVE_SUFFIXES = setOf(
        "form", "forme", "style", "trim", "flower", "striped", "cloak", "sea", "size"
    )

    private val BASE_FORM_LABELS = setOf(
        "natural", "natural form", "normal", "normal form",
        "default", "default form", "base", "base form"
    )

    private val FORM_ALIASES = mapOf(
        "alola" to "alola", "alolan" to "alola",
        "galar" to "galar", "galarian" to "galar",
        "hisui" to "hisui", "hisuian" to "hisui",
        "paldea" to "paldea", "paldean" to "paldea",
        "baile style" to "baile", "pau style" to "pau",
        "pom pom" to "pom", "pom pom style" to "pom", "sensu style" to "sensu",
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
}
