package com.example.pokemonalertsv2.data.godex

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class GoDexFormChangeEdge(
    val pokedexId: Int,
    val fromForm: String? = null,
    val toForm: String? = null
)

@Serializable
private data class GoDexFormChangeAsset(
    val version: Int,
    val speciesMaxId: Int,
    val edges: List<GoDexFormChangeEdge>
)

class GoDexFormChangeGraph private constructor(
    val version: Int,
    val speciesMaxId: Int,
    val edges: List<GoDexFormChangeEdge>
) {
    private val edgesBySpecies = edges.groupBy { it.pokedexId }

    fun outgoing(pokedexId: Int): List<GoDexFormChangeEdge> =
        edgesBySpecies[pokedexId].orEmpty()

    fun validate() {
        require(version > 0) { "Form-change graph version must be positive" }
        require(speciesMaxId > 0) { "Form-change graph speciesMaxId must be positive" }
        require(edges.isNotEmpty()) { "Form-change graph is empty" }
        require(edges.distinct().size == edges.size) { "Form-change graph contains duplicate edges" }
        edges.forEach { edge ->
            require(edge.pokedexId in 1..speciesMaxId) {
                "Form-change graph contains invalid species id: ${edge.pokedexId}"
            }
            require(edge.fromForm != edge.toForm) {
                "Form-change graph contains an identity edge for ${edge.pokedexId}"
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        fun load(context: Context): GoDexFormChangeGraph = context.assets
            .open("godex/form_change_paths_v1.json")
            .bufferedReader()
            .use { parse(it.readText()) }

        internal fun parse(text: String): GoDexFormChangeGraph {
            val asset = json.decodeFromString<GoDexFormChangeAsset>(text)
            return GoDexFormChangeGraph(asset.version, asset.speciesMaxId, asset.edges).also { it.validate() }
        }

        internal fun forTests(edges: List<GoDexFormChangeEdge>, speciesMaxId: Int = 1100) =
            GoDexFormChangeGraph(1, speciesMaxId, edges).also { it.validate() }
    }
}
