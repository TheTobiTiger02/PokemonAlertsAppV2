package com.example.pokemonalertsv2.data.godex

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class GoDexEvolutionEdge(
    val from: Int,
    val to: Int,
    val fromForm: String? = null,
    val toForm: String? = null,
    val sourceGender: String? = null
)

@Serializable
private data class GoDexEvolutionAsset(
    val version: Int,
    val speciesMaxId: Int,
    val edges: List<GoDexEvolutionEdge>
)

class GoDexEvolutionGraph private constructor(
    val version: Int,
    val speciesMaxId: Int,
    val edges: List<GoDexEvolutionEdge>
) {
    private val edgesBySource = edges.groupBy { it.from }

    fun outgoing(pokedexId: Int): List<GoDexEvolutionEdge> = edgesBySource[pokedexId].orEmpty()

    fun validate() {
        require(version > 0) { "Evolution graph version must be positive" }
        require(speciesMaxId > 0) { "Evolution graph speciesMaxId must be positive" }
        require(edges.isNotEmpty()) { "Evolution graph is empty" }
        require(edges.distinct().size == edges.size) { "Evolution graph contains duplicate edges" }
        edges.forEach { edge ->
            require(edge.from in 1..speciesMaxId && edge.to in 1..speciesMaxId) {
                "Evolution graph contains invalid species id: ${edge.from} -> ${edge.to}"
            }
            require(edge.from != edge.to) { "Evolution graph contains a self-cycle for ${edge.from}" }
            require(edge.sourceGender == null || edge.sourceGender in SUPPORTED_GENDERS) {
                "Evolution graph contains unsupported gender: ${edge.sourceGender}"
            }
            require(edge.fromForm == null || edge.fromForm in SUPPORTED_FORMS) {
                "Evolution graph contains unsupported source form: ${edge.fromForm}"
            }
            require(edge.toForm == null || edge.toForm in SUPPORTED_FORMS) {
                "Evolution graph contains unsupported target form: ${edge.toForm}"
            }
        }
        val visiting = HashSet<Pair<Int, String?>>()
        val visited = HashSet<Pair<Int, String?>>()
        fun visit(node: Pair<Int, String?>) {
            if (node in visited) return
            require(visiting.add(node)) { "Evolution graph contains a cycle at ${node.first}" }
            outgoing(node.first)
                .filter { it.fromForm == node.second }
                .forEach { visit(it.to to it.toForm) }
            visiting.remove(node)
            visited.add(node)
        }
        edges.forEach { visit(it.from to it.fromForm) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }
        private val SUPPORTED_GENDERS = setOf("male", "female")
        private val SUPPORTED_FORMS = setOf(
            "alola", "galar", "hisui", "paldea", "white", "red", "blue",
            "east", "west", "plant", "sandy", "trash", "spring", "summer",
            "autumn", "winter", "red_flower", "orange_flower", "yellow_flower",
            "white_flower", "blue_flower", "small", "average", "large", "super"
        )

        fun load(context: Context): GoDexEvolutionGraph = context.assets
            .open("godex/evolution_paths_v1.json")
            .bufferedReader()
            .use { parse(it.readText()) }

        internal fun parse(text: String): GoDexEvolutionGraph {
            val asset = json.decodeFromString<GoDexEvolutionAsset>(text)
            return GoDexEvolutionGraph(asset.version, asset.speciesMaxId, asset.edges).also { it.validate() }
        }

        internal fun forTests(edges: List<GoDexEvolutionEdge>, speciesMaxId: Int = 1100) =
            GoDexEvolutionGraph(1, speciesMaxId, edges).also { it.validate() }
    }
}
