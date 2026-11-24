package com.example.pokemonalertsv2.data.dextracker

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One Pokémon of Dex Tracker's catalog (its src/data/catalog.json): [id] is the PokéAPI Pokémon
 * id (alternate forms above 10000), [speciesId] the national dex number, [family] the evolution
 * line it starts, [types] lower-case type names.
 */
@Serializable
data class DexTrackerPokemon(val id: Int, val speciesId: Int, val name: String, val family: List<Int>, val types: List<String>)

/** Dex Tracker's catalog, bundled as assets/dextracker/catalog_v1.json (scripts/android-fixtures.mjs there). */
class DexTrackerCatalog private constructor(val pokemon: List<DexTrackerPokemon>) {
    val byId: Map<Int, DexTrackerPokemon> = pokemon.associateBy { it.id }

    fun name(id: Int): String = byId[id]?.name ?: "#$id"

    /** The national dex number of a catalog id; ids not in the catalog are taken as dex numbers. */
    fun speciesOf(id: Int): Int = byId[id]?.speciesId ?: id

    fun primaryType(id: Int): String? = byId[id]?.types?.firstOrNull()

    companion object {
        @Volatile private var instance: DexTrackerCatalog? = null

        internal fun forTests(pokemon: List<DexTrackerPokemon>) = DexTrackerCatalog(pokemon)

        fun get(context: Context): DexTrackerCatalog = instance ?: synchronized(this) {
            instance ?: DexTrackerCatalog(
                Json { ignoreUnknownKeys = true }.decodeFromString(
                    context.assets.open("dextracker/catalog_v1.json").bufferedReader().use { it.readText() }
                )
            ).also { instance = it }
        }
    }
}
