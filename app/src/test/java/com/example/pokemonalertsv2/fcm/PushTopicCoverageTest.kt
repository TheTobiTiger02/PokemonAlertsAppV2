package com.example.pokemonalertsv2.fcm

import com.example.pokemonalertsv2.data.AlertFilterMatcher
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PushTopicCatalog
import com.example.pokemonalertsv2.data.filterAlertTypes
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The invariant the whole feature rests on: **an alert the app would show must arrive.**
 *
 * FCM is the only thing that wakes this app in the background, so an alert on no subscribed
 * topic does not merely skip a notification — the feed, map, widgets and history never learn
 * about it. Every other test here checks a rule in isolation; this one replays a real
 * `/api/pokemon` snapshot against the real `/api/push-topics` catalog and checks the rules
 * compose correctly over data nobody wrote for the test.
 *
 * It is also the tripwire for a vocabulary drift: the app derives its own categories from IV
 * and CP fields the server's type list does not carry, so a category the backend stops
 * publishing under the expected type shows up here as an uncovered alert rather than as a
 * silently missing push months later.
 */
class PushTopicCoverageTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val catalog: PushTopicCatalog = json.decodeFromString(
        PushTopicCatalog.serializer(),
        readFixture("push-topics.json")
    )

    private val alerts: List<PokemonAlert> = json.decodeFromString(
        ListSerializer(PokemonAlert.serializer()),
        readFixture("alerts-snapshot.json")
    )

    @Test
    fun snapshotStillMatchesTheAssumptionsThePlannerMakes() {
        assertTrue("the fixture should carry real alerts", alerts.size > 50)
        assertTrue("the catalog fixture should be usable", catalog.isUsable)

        val known = catalog.types.map { it.value.lowercase() }.toSet()
        val unlisted = alerts.filter { alert ->
            alert.type.orEmpty().isEmpty() || alert.type.orEmpty().none { it.lowercase() in known }
        }
        assertTrue(
            "every alert must carry a type the catalog publishes, or it lands on no type topic " +
                "at all: ${unlisted.take(3).map { it.name to it.type }}",
            unlisted.isEmpty()
        )
    }

    @Test
    fun everyWantedAlertIsAddressedByThePlan() {
        val failures = mutableListOf<String>()
        var checked = 0

        definitionMatrix().forEach { (label, definition) ->
            val plan = PushTopicPlanner.plan(catalog, definition)
            alerts.filter { AlertFilterMatcher.matches(it, definition) }.forEach { alert ->
                checked++
                if (serverTopicsFor(alert).none(plan::contains)) {
                    failures += "$label wants ${alert.name} (${alert.type}, ${alert.area}) " +
                        "but it is published to ${serverTopicsFor(alert)} and the plan is $plan"
                }
            }
        }

        // Not every cross has data — the bare "Darmstadt" label only ever carries the alerts that
        // lack coordinates — so the guard against a vacuous pass is the total, not per case.
        assertTrue("only $checked alert/filter pairs were checked", checked > 500)

        if (failures.isNotEmpty()) fail(failures.take(5).joinToString("\n"))
    }

    /**
     * Coverage alone would pass if the planner never narrowed anything: the legacy topic carries
     * every alert, so falling back is always "covered". These are the cases that have to come out
     * narrowed for the feature to be doing any work at all.
     */
    @Test
    fun theCasesWorthNarrowingActuallyNarrow() {
        val narrowed = definitionMatrix().filter { (_, definition) ->
            PushTopicPlanner.plan(catalog, definition) != setOf(catalog.legacyTopic)
        }.map { it.first }.toSet()

        listOf(
            "only RAID", "only HUNDO", "only SPAWN", "only QUEST",
            "only Alsbach", "only Darmstadt-North",
            "Alsbach + RAID", "Darmstadt-North + HUNDO",
            "everything but quests", "high value"
        ).forEach { case ->
            assertTrue("$case should have narrowed but fell back to the legacy topic", case in narrowed)
        }

        assertTrue(
            "an unfiltered app must stay on the legacy broadcast topic",
            PushTopicPlanner.plan(catalog, FilterDefinition()) == setOf(catalog.legacyTopic)
        )
    }

    /**
     * The test's own tripwire. If [serverTopicsFor] and the planner ever agreed by accident —
     * because both derived their names the same wrong way, say — coverage would pass for a plan
     * that addresses nothing. A deliberately wrong plan must be caught.
     */
    @Test
    fun aWrongPlanWouldBeCaught() {
        val definition = FilterDefinition(alertTypes = FilterSelection.only(listOf(FilterAlertType.RAID.name)))
        val wrongPlan = setOf("alerts-t-raids")

        val wanted = alerts.filter { AlertFilterMatcher.matches(it, definition) }
        assertTrue("the snapshot needs raids for this to mean anything", wanted.isNotEmpty())
        assertTrue(wanted.none { alert -> serverTopicsFor(alert).any(wrongPlan::contains) })
    }

    /**
     * The topics the backend addresses an alert to, per its documented rules. Reproduced here
     * and nowhere in production code: the app subscribes to names the catalog hands it, and
     * deriving them locally would break the day the operator renames the base topic.
     *
     * The area×type topic is modelled as zone-only, which is the pessimistic reading — the
     * rollup to the group is documented for the plain area axis alone.
     */
    private fun serverTopicsFor(alert: PokemonAlert): Set<String> = buildSet {
        add(catalog.legacyTopic)
        val types = catalog.types.filter { type -> alert.type.orEmpty().any { it.equals(type.value, true) } }
        types.forEach { add(it.topic) }
        val area = catalog.areas.firstOrNull { it.value.equals(alert.area, ignoreCase = true) }
        if (area != null) {
            add(area.topic)
            area.groupTopic?.let(::add)
            types.forEach { type -> catalog.areaType(area.value, type.value)?.let { add(it.topic) } }
        }
        alert.pokedexId?.let { dex ->
            catalog.species.firstOrNull { it.pokedexId == dex }?.let { add(it.topic) }
        }
    }

    /**
     * What a filter UI can actually select. OTHER is absent from `FILTERABLE_ALERT_CATEGORIES`,
     * so no surface ever puts it in a selection; that it forces a legacy fallback when it somehow
     * appears is pinned in [PushTopicPlannerTest] instead.
     */
    private val selectableTypes = FilterAlertType.entries - FilterAlertType.OTHER

    private fun definitionMatrix(): List<Pair<String, FilterDefinition>> {
        val areas = listOf("Alsbach", "Darmstadt", "Darmstadt-North", "Darmstadt-South")
        val typeCases = selectableTypes.map { type ->
            "only ${type.name}" to FilterDefinition(alertTypes = FilterSelection.only(listOf(type.name)))
        }
        val areaCases = areas.map { area ->
            "only $area" to FilterDefinition(areas = FilterSelection.only(listOf(area)))
        }
        val crossCases = areas.flatMap { area ->
            listOf(FilterAlertType.RAID, FilterAlertType.HUNDO, FilterAlertType.QUEST, FilterAlertType.SPAWN)
                .map { type ->
                    "$area + ${type.name}" to FilterDefinition(
                        alertTypes = FilterSelection.only(listOf(type.name)),
                        areas = FilterSelection.only(listOf(area))
                    )
                }
        }
        val realistic = listOf(
            // Muting quests is the most common narrowing there is.
            "everything but quests" to FilterDefinition(
                alertTypes = FilterSelection.only(
                    (selectableTypes - FilterAlertType.QUEST).map { it.name }
                )
            ),
            "high value" to FilterDefinition(
                alertTypes = FilterSelection.only(
                    listOf(
                        FilterAlertType.HUNDO.name, FilterAlertType.NUNDO.name,
                        FilterAlertType.PVP.name, FilterAlertType.RARE.name
                    )
                )
            ),
            "unfiltered" to FilterDefinition()
        )
        return typeCases + areaCases + crossCases + realistic
    }

    private fun readFixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }
}
