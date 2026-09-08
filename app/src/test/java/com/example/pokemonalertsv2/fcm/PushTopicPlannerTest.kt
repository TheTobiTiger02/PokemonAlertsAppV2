package com.example.pokemonalertsv2.fcm

import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.PushTopicArea
import com.example.pokemonalertsv2.data.PushTopicCatalog
import com.example.pokemonalertsv2.data.PushTopicType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision table behind the subscription.
 *
 * The catalog here mirrors the live one: nine server types with no `Spawn` among them, and a
 * `Darmstadt` group split into two zones alongside two ungrouped areas.
 */
class PushTopicPlannerTest {

    private val types = listOf(
        "Hundo", "Nundo", "PvP", "Raid", "Rocket", "Quest", "Kecleon", "Rare", "WeatherChange"
    ).map { PushTopicType(it, "alerts-t-${it.lowercase()}") }

    private val areas = listOf(
        PushTopicArea("Alsbach", "Alsbach", "alerts-a-alsbach"),
        PushTopicArea("Darmstadt", "Darmstadt", "alerts-a-darmstadt"),
        PushTopicArea("Darmstadt-North", "Darmstadt", "alerts-a-darmstadt-north", "alerts-a-darmstadt"),
        PushTopicArea("Darmstadt-South", "Darmstadt", "alerts-a-darmstadt-south", "alerts-a-darmstadt"),
        PushTopicArea("Unknown", null, "alerts-a-unknown")
    )

    private val catalog = PushTopicCatalog(
        schemaVersion = 1,
        baseTopic = "alerts",
        legacyTopic = "alerts",
        fanoutEnabled = true,
        maxTopicsPerCondition = 5,
        types = types,
        areas = areas,
        areaTypes = areas.flatMap { area ->
            types.map { type ->
                com.example.pokemonalertsv2.data.PushTopicAreaType(
                    area = area.value,
                    type = type.value,
                    topic = "${area.topic}-t-${type.value.lowercase()}"
                )
            }
        }
    )

    private fun typesOnly(vararg selected: FilterAlertType) =
        FilterDefinition(alertTypes = FilterSelection.only(selected.map { it.name }))

    // --- falling back to the legacy topic -------------------------------------------------

    @Test
    fun plan_withoutACatalogUsesTheCompiledLegacyTopic() {
        assertEquals(setOf("alerts"), PushTopicPlanner.plan(null, typesOnly(FilterAlertType.RAID)))
    }

    @Test
    fun plan_withFanoutDisabledUsesTheLegacyTopic() {
        val disabled = catalog.copy(fanoutEnabled = false)

        assertEquals(setOf("alerts"), PushTopicPlanner.plan(disabled, typesOnly(FilterAlertType.RAID)))
    }

    /** An unrecognised scheme is a reason to fall back, never to guess at topic names. */
    @Test
    fun plan_withAnUnknownSchemaVersionUsesTheLegacyTopic() {
        val future = catalog.copy(schemaVersion = 2)

        assertEquals(setOf("alerts"), PushTopicPlanner.plan(future, typesOnly(FilterAlertType.RAID)))
    }

    @Test
    fun plan_followsARenamedBaseTopic() {
        val staging = catalog.copy(legacyTopic = "alerts-staging", fanoutEnabled = false)

        assertEquals(setOf("alerts-staging"), PushTopicPlanner.plan(staging, FilterDefinition()))
    }

    @Test
    fun plan_withNothingNarrowedUsesTheLegacyTopic() {
        assertEquals(setOf("alerts"), PushTopicPlanner.plan(catalog, FilterDefinition()))
    }

    @Test
    fun plan_withEveryTypeSelectedUsesTheLegacyTopic() {
        val everything = FilterDefinition(
            alertTypes = FilterSelection.only(FilterAlertType.entries.map { it.name })
        )

        assertEquals(setOf("alerts"), PushTopicPlanner.plan(catalog, everything))
    }

    /** OTHER is the app's catch-all, and an unclassifiable alert is on no derived topic. */
    @Test
    fun plan_withOtherSelectedUsesTheLegacyTopic() {
        val definition = typesOnly(FilterAlertType.RAID, FilterAlertType.OTHER)

        assertEquals(setOf("alerts"), PushTopicPlanner.plan(catalog, definition))
    }

    @Test
    fun plan_withAnAreaTheCatalogDoesNotKnowUsesTheLegacyTopic() {
        val definition = FilterDefinition(areas = FilterSelection.only(listOf("Bensheim")))

        assertEquals(setOf("alerts"), PushTopicPlanner.plan(catalog, definition))
    }

    // --- the type axis --------------------------------------------------------------------

    @Test
    fun plan_narrowsToTheSelectedTypes() {
        val definition = typesOnly(FilterAlertType.RAID, FilterAlertType.QUEST)

        assertEquals(setOf("alerts-t-raid", "alerts-t-quest"), PushTopicPlanner.plan(catalog, definition))
    }

    /**
     * The server has no `Spawn` type: a plain spawn is published as Hundo, Nundo, PvP or Rare,
     * all of which this app also calls a spawn.
     */
    @Test
    fun plan_resolvesSpawnToTheServerSpawnFamily() {
        assertEquals(
            setOf("alerts-t-hundo", "alerts-t-nundo", "alerts-t-pvp", "alerts-t-rare"),
            PushTopicPlanner.plan(catalog, typesOnly(FilterAlertType.SPAWN))
        )
    }

    /**
     * HUNDO is derived from the IV fields, not the type list, so a server-side PvP alert with
     * 15/15/15 is a hundo to this app while carrying only the PvP topic.
     */
    @Test
    fun plan_widensHundoAcrossTheSpawnFamily() {
        assertEquals(
            setOf("alerts-t-hundo", "alerts-t-nundo", "alerts-t-pvp", "alerts-t-rare"),
            PushTopicPlanner.plan(catalog, typesOnly(FilterAlertType.HUNDO))
        )
    }

    @Test
    fun plan_mapsWeatherOntoTheWeatherChangeType() {
        assertEquals(
            setOf("alerts-t-weatherchange"),
            PushTopicPlanner.plan(catalog, typesOnly(FilterAlertType.WEATHER))
        )
    }

    // --- the area axis --------------------------------------------------------------------

    /**
     * A zone alert reaches the group topic too, but an older alert carrying only the bare group
     * label reaches nothing else — so the group topic is the only choice that cannot miss one.
     */
    @Test
    fun plan_subscribesToTheGroupRatherThanTheZone() {
        val definition = FilterDefinition(areas = FilterSelection.only(listOf("Darmstadt-North")))

        assertEquals(setOf("alerts-a-darmstadt"), PushTopicPlanner.plan(catalog, definition))
    }

    @Test
    fun plan_usesTheAreaTopicWhenThereIsNoGroup() {
        val definition = FilterDefinition(areas = FilterSelection.only(listOf("Alsbach")))

        assertEquals(setOf("alerts-a-alsbach"), PushTopicPlanner.plan(catalog, definition))
    }

    // --- the cross product ----------------------------------------------------------------

    /**
     * The zone-to-group rollup is documented for the area axis only, so the plan covers the
     * group's own topic and each of its zones rather than assuming a rollup that may not exist.
     */
    @Test
    fun plan_crossesAreaAndTypeAcrossTheWholeGroup() {
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.RAID.name)),
            areas = FilterSelection.only(listOf("Darmstadt-North"))
        )

        assertEquals(
            setOf(
                "alerts-a-darmstadt-t-raid",
                "alerts-a-darmstadt-north-t-raid",
                "alerts-a-darmstadt-south-t-raid"
            ),
            PushTopicPlanner.plan(catalog, definition)
        )
    }

    @Test
    fun plan_fallsBackToOneAxisWhenAPairIsMissing() {
        val gapped = catalog.copy(
            areaTypes = catalog.areaTypes.filterNot { it.area == "Alsbach" && it.type == "Raid" }
        )
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.RAID.name)),
            areas = FilterSelection.only(listOf("Alsbach"))
        )

        // One raid topic beats one area topic: the type axis is the more selective of the two.
        assertEquals(setOf("alerts-t-raid"), PushTopicPlanner.plan(gapped, definition))
    }

    @Test
    fun plan_neverMixesTheLegacyTopicIntoADerivedPlan() {
        val plan = PushTopicPlanner.plan(catalog, typesOnly(FilterAlertType.RAID))

        assertTrue(plan.isNotEmpty())
        assertTrue("alerts" !in plan)
    }

    @Test
    fun plan_staysUnderTheClientCap() {
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(
                listOf(FilterAlertType.RAID.name, FilterAlertType.QUEST.name, FilterAlertType.ROCKET.name)
            ),
            areas = FilterSelection.only(listOf("Darmstadt-North", "Alsbach"))
        )

        assertTrue(PushTopicPlanner.plan(catalog, definition).size <= PushTopicPlanner.MAX_CLIENT_TOPICS)
    }
}
