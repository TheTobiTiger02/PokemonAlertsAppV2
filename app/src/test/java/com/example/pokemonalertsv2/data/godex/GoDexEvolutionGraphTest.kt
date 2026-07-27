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

    @Test
    fun regionalEventOnlyPathsAreNotNormallyAvailable() {
        val text = File("src/main/assets/godex/evolution_paths_v1.json").readText()
        val graph = GoDexEvolutionGraph.parse(text)
        val eventOnlyPaths = listOf(
            GoDexEvolutionEdge(25, 26, toForm = "alola"),
            GoDexEvolutionEdge(102, 103, toForm = "alola"),
            GoDexEvolutionEdge(104, 105, toForm = "alola"),
            GoDexEvolutionEdge(109, 110, toForm = "galar"),
            GoDexEvolutionEdge(156, 157, toForm = "hisui"),
            GoDexEvolutionEdge(502, 503, toForm = "hisui"),
            GoDexEvolutionEdge(548, 549, toForm = "hisui"),
            GoDexEvolutionEdge(627, 628, toForm = "hisui"),
            GoDexEvolutionEdge(723, 724, toForm = "hisui")
        )

        eventOnlyPaths.forEach { edge ->
            assertTrue(edge !in graph.edges)
            assertTrue(edge !in graph.outgoing(edge.from))
        }
        assertTrue(
            GoDexEvolutionEdge(211, 904, fromForm = "hisui") in
                graph.outgoing(211)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun validationRejectsRegionalTargetWithoutRegionalSource() {
        GoDexEvolutionGraph.forTests(
            listOf(GoDexEvolutionEdge(25, 26, toForm = "alola"))
        )
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
