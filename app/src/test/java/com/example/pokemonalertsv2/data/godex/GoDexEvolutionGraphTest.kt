package com.example.pokemonalertsv2.data.godex

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoDexEvolutionGraphTest {
    @Test
    fun bundledGraphIsValidAndContainsRequiredOverrides() {
        val text = File("src/main/assets/godex/evolution_paths_v1.json").readText()
        val graph = GoDexEvolutionGraph.parse(text)

        assertEquals(1, graph.version)
        assertTrue(graph.edges.size > 500)
        assertTrue(graph.edges.contains(GoDexEvolutionEdge(211, 904, fromForm = "hisui")))
        assertTrue(graph.edges.contains(GoDexEvolutionEdge(264, 862, fromForm = "galar")))
        assertTrue(graph.edges.contains(GoDexEvolutionEdge(415, 416, sourceGender = "female")))
        assertTrue(graph.edges.contains(GoDexEvolutionEdge(710, 711, fromForm = "super", toForm = "super")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validationRejectsDuplicateEdges() {
        val edge = GoDexEvolutionEdge(1, 2)
        GoDexEvolutionGraph.forTests(listOf(edge, edge))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validationRejectsCycles() {
        GoDexEvolutionGraph.forTests(listOf(GoDexEvolutionEdge(1, 2), GoDexEvolutionEdge(2, 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validationRejectsUnsupportedForms() {
        GoDexEvolutionGraph.forTests(listOf(GoDexEvolutionEdge(1, 2, fromForm = "mystery")))
    }
}
