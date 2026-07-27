package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.godex.GoDexEvolutionTarget
import com.example.pokemonalertsv2.data.godex.GoDexFormChangeTarget
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GoDexCatchOptionsTest {
    @Test
    fun directNeededEntryIsTheOnlyDirectCatchOption() {
        val result = GoDexMatchResult(
            status = GoDexMatchStatus.NEEDED,
            matchedEntryKey = "0025-none"
        )

        assertEquals(
            listOf(GoDexCatchOption("0025-none", "Pikachu", GoDexCatchTargetKind.DIRECT)),
            goDexCatchOptions("Pikachu", result)
        )
    }

    @Test
    fun directNeededEntryAndNeededEvolutionAreBothCatchOptions() {
        val result = GoDexMatchResult(
            status = GoDexMatchStatus.NEEDED,
            matchedEntryKey = "0813-none",
            evolutionTargets = listOf(
                GoDexEvolutionTarget("0814-none", 814, "Raboot", 1)
            )
        )

        val options = goDexCatchOptions("Scorbunny", result)

        assertEquals(
            listOf(
                GoDexCatchOption("0813-none", "Scorbunny", GoDexCatchTargetKind.DIRECT),
                GoDexCatchOption("0814-none", "Raboot", GoDexCatchTargetKind.EVOLUTION, 1)
            ),
            options
        )
        assertNull(initialGoDexTargetSelection(options.map(GoDexCatchOption::entryKey)))
    }

    @Test
    fun descendantChooserNeverOffersAlreadyCaughtBaseEntry() {
        val result = GoDexMatchResult(
            status = GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED,
            matchedEntryKey = "0133-none",
            evolutionTargets = listOf(
                GoDexEvolutionTarget("0134-none", 134, "Vaporeon", 1)
            ),
            formChangeTargets = listOf(
                GoDexFormChangeTarget("0133_special-none", 133, "Special Eevee", 1)
            )
        )

        val options = goDexCatchOptions("Eevee", result)

        assertEquals(listOf("0134-none", "0133_special-none"), options.map { it.entryKey })
        assertFalse(options.any { it.entryKey == result.matchedEntryKey })
    }

    @Test
    fun duplicateExactTargetKeyIsOnlyShownOnce() {
        val result = GoDexMatchResult(
            status = GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED,
            matchedEntryKey = "0001-none",
            evolutionTargets = listOf(
                GoDexEvolutionTarget("0002-none", 2, "Target", 1)
            ),
            formChangeTargets = listOf(
                GoDexFormChangeTarget("0002-none", 2, "Target", 1)
            )
        )

        assertEquals(1, goDexCatchOptions("Source", result).size)
    }

    @Test
    fun singleTargetIsPreselectedButMultipleTargetsRequireSelection() {
        assertEquals("0025-none", initialGoDexTargetSelection(listOf("0025-none")))
        assertNull(initialGoDexTargetSelection(listOf("0134-none", "0135-none")))
        assertNull(initialGoDexTargetSelection(emptyList()))
    }
}
