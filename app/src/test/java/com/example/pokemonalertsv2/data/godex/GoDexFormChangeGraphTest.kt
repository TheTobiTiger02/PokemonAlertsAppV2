package com.example.pokemonalertsv2.data.godex

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoDexFormChangeGraphTest {
    @Test
    fun bundledGraphContainsEveryFurfrouTrim() {
        val text = File("src/main/assets/godex/form_change_paths_v1.json").readText()
        val graph = GoDexFormChangeGraph.parse(text)

        assertEquals(1, graph.version)
        assertEquals(9, graph.edges.size)
        assertEquals(setOf("heart", "star", "diamond", "debutante", "matron", "dandy", "la_reine", "kabuki", "pharaoh"),
            graph.edges.mapTo(mutableSetOf()) { it.toForm })
        assertTrue(graph.edges.all { it.pokedexId == 676 && it.fromForm == null })
    }

    @Test(expected = IllegalArgumentException::class)
    fun validationRejectsDuplicateEdges() {
        val edge = GoDexFormChangeEdge(676, null, "heart")
        GoDexFormChangeGraph.forTests(listOf(edge, edge))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validationRejectsIdentityEdges() {
        GoDexFormChangeGraph.forTests(listOf(GoDexFormChangeEdge(676, "heart", "heart")))
    }

    @Test
    fun validationAllowsCyclesBetweenDifferentForms() {
        GoDexFormChangeGraph.forTests(
            listOf(
                GoDexFormChangeEdge(676, null, "heart"),
                GoDexFormChangeEdge(676, "heart", null)
            )
        )
    }
}
