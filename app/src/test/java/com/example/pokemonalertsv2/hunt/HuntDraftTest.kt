package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.QuestFilterRules
import com.example.pokemonalertsv2.data.QuestPairRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuntDraftTest {

    @Test
    fun `untouched sections become any`() {
        val draft = EMPTY_HUNT_DRAFT.copy(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.ROCKET.name))
        )

        val hunt = draft.forHunt()

        assertEquals(FilterSelectionMode.ALL, hunt.rocketTypes.mode)
        assertEquals(FilterSelectionMode.ALL, hunt.spawnSpecies.mode)
        assertEquals(FilterSelectionMode.ALL, hunt.raidTiers.mode)
        // The types are the one thing the trainer really did have to choose.
        assertEquals(FilterSelectionMode.ONLY, hunt.alertTypes.mode)
    }

    @Test
    fun `a narrowed section survives the trip out`() {
        val draft = EMPTY_HUNT_DRAFT.copy(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.ROCKET.name)),
            rocketTypes = FilterSelection.only(listOf("Dragon"))
        )

        val hunt = draft.forHunt()

        assertEquals(FilterSelectionMode.ONLY, hunt.rocketTypes.mode)
        assertTrue(hunt.rocketTypes.contains("Dragon"))
    }

    @Test
    fun `a rocket hunt round trips back into the sheet`() {
        val hunt = EMPTY_HUNT_DRAFT.copy(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.ROCKET.name)),
            rocketTypes = FilterSelection.only(listOf("Dragon"))
        ).forHunt()

        assertEquals(hunt, hunt.forHuntDraft().forHunt())
    }

    @Test
    fun `a species hunt round trips back into the sheet`() {
        val hunt = EMPTY_HUNT_DRAFT.copy(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.SPAWN.name)),
            spawnSpecies = FilterSelection.only(listOf("Larvitar", "Dratini"))
        ).forHunt()

        assertEquals(hunt, hunt.forHuntDraft().forHunt())
    }

    @Test
    fun `an exact quest hunt round trips with its rules untouched`() {
        val quests = QuestFilterRules(
            exactPairs = setOf(QuestPairRule("spin-5-stops", "spinda"))
        )
        val hunt = EMPTY_HUNT_DRAFT.copy(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.QUEST.name)),
            quests = quests
        ).forHunt()

        val back = hunt.forHuntDraft()

        // Quests are not one of the eight narrowable selections and must not be
        // touched by either direction.
        assertEquals(quests, back.quests)
        assertEquals(hunt, back.forHunt())
    }

    @Test
    fun `a hunt with nothing narrowed round trips`() {
        val hunt = EMPTY_HUNT_DRAFT.copy(
            alertTypes = FilterSelection.only(FilterAlertType.entries.map { it.name })
        ).forHunt()

        assertEquals(hunt, hunt.forHuntDraft().forHunt())
    }

    @Test
    fun `a definition with every type allowed is still startable as a draft`() {
        // A saved hunt that reached ALL some other way would otherwise load with
        // no chips ticked and a dead Start button.
        val draft = FilterDefinition().forHuntDraft()

        assertEquals(FilterAlertType.entries, draft.chosenTypes())
    }

    @Test
    fun `chosen types are empty until something is picked`() {
        assertTrue(EMPTY_HUNT_DRAFT.chosenTypes().isEmpty())
    }
}
