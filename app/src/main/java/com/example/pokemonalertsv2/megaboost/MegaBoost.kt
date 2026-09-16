package com.example.pokemonalertsv2.megaboost

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

/** Live wild Pokémon per species and area, as the backend's live collector sees them. */
interface LiveSpeciesService {
    @GET("api/spawnpoints/live-species") suspend fun liveSpecies(): Response<LiveSpeciesResponse>
}

@Serializable data class LiveSpeciesResponse(val generatedAt: String? = null, val areas: List<LiveArea> = emptyList())
@Serializable data class LiveArea(val area: String, val total: Int = 0, val species: List<LiveSpeciesCount> = emptyList())
/** [formId] is the game form (e.g. 50 for Alolan Raichu), null when the collector did not record one. */
@Serializable data class LiveSpeciesCount(val pokemonId: Int, val formId: Int? = null, val count: Int)

/** A live species in one form; [formId] null is the species as such. */
data class LiveSpecies(val dex: Int, val formId: Int? = null)

/** The types of [species]: its own form's when known, else the species' ordinary types. */
fun Map<LiveSpecies, List<String>>.typesOf(species: LiveSpecies): List<String>? =
    this[species]?.takeIf { it.isNotEmpty() } ?: this[species.copy(formId = null)]?.takeIf { it.isNotEmpty() }

/** The label that sums every area. */
const val ALL_AREAS = "All"

/** Live Pokémon per species and form in [area], or across every area for [ALL_AREAS]. */
fun liveCounts(response: LiveSpeciesResponse, area: String): Map<LiveSpecies, Int> =
    response.areas.filter { area == ALL_AREAS || it.area == area }
        .flatMap { it.species }
        .filter { it.pokemonId > 0 && it.count > 0 }
        .groupingBy { LiveSpecies(it.pokemonId, it.formId?.takeIf { form -> form > 0 }) }
        .fold(0) { total, entry -> total + entry.count }

/**
 * Live Pokémon per type, most common first. A dual-type Pokémon adds to both of its types, so the
 * totals can add up to more than the Pokémon counted. Species without known types are left out.
 */
fun typeTotals(counts: Map<LiveSpecies, Int>, types: Map<LiveSpecies, List<String>>): List<Pair<String, Int>> =
    counts.flatMap { (species, count) -> types.typesOf(species).orEmpty().distinct().map { it to count } }
        .groupingBy { it.first }
        .fold(0) { total, entry -> total + entry.second }
        .toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

/** A mega that could be made active: its Pokebattler id, name, types, and whether the roster has its base species. */
data class MegaCandidate(
    val pokemonId: String,
    val name: String,
    val types: List<String>,
    val owned: Boolean,
) {
    /** The types its Mega Boost pays out on: its own, except for the weather trio, which boosts its whole weather. */
    val boostTypes: List<String> get() = WEATHER_BOOST_TYPES[pokemonId] ?: types
}

/**
 * Primal Groudon, Primal Kyogre and Mega Rayquaza boost every type their weather boosts rather than their
 * own types: Sunny (Fire, Grass, Ground), Rainy (Water, Electric, Bug) and Windy (Dragon, Flying, Psychic).
 */
private val WEATHER_BOOST_TYPES = mapOf(
    "GROUDON_PRIMAL" to listOf("POKEMON_TYPE_FIRE", "POKEMON_TYPE_GRASS", "POKEMON_TYPE_GROUND"),
    "KYOGRE_PRIMAL" to listOf("POKEMON_TYPE_WATER", "POKEMON_TYPE_ELECTRIC", "POKEMON_TYPE_BUG"),
    "RAYQUAZA_MEGA" to listOf("POKEMON_TYPE_DRAGON", "POKEMON_TYPE_FLYING", "POKEMON_TYPE_PSYCHIC"),
)

/** How much of the live spawn one mega would boost. */
data class MegaBoostRow(val mega: MegaCandidate, val boosted: Int, val total: Int) {
    val share: Double get() = if (total == 0) 0.0 else boosted.toDouble() / total
}

/**
 * Megas ranked by the live Pokémon they boost: a Pokémon sharing at least one type with the mega
 * counts once, since that is what the Mega Boost candy bonus pays out on. Megas from the trainer's
 * roster come first; within each group the most boosted leads, then the name.
 */
fun rankMegaBoost(counts: Map<LiveSpecies, Int>, types: Map<LiveSpecies, List<String>>, megas: List<MegaCandidate>): List<MegaBoostRow> {
    val typed = counts.mapNotNull { (species, count) -> types.typesOf(species)?.let { it.toSet() to count } }
    val total = typed.sumOf { it.second }
    return megas.map { mega ->
        val types = mega.boostTypes.toSet()
        MegaBoostRow(mega, typed.filter { (speciesTypes, _) -> speciesTypes.any { it in types } }.sumOf { it.second }, total)
    }.sortedWith(
        compareByDescending<MegaBoostRow> { it.mega.owned }
            .thenByDescending { it.boosted }
            .thenBy { it.mega.name }
    )
}
