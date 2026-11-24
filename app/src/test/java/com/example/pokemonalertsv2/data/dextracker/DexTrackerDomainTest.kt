package com.example.pokemonalertsv2.data.dextracker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/**
 * Checks the port against Dex Tracker's own rules: domain_fixtures.json is src/domain.ts of
 * PokemonGoChecklist worked through its cases (`node scripts/android-fixtures.mjs` there).
 */
class DexTrackerDomainTest {
    private val fixtures: JsonObject = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("dextracker/domain_fixtures.json")!!.readText()
    ).jsonObject

    /** domain.ts keeps a stage at 0 after a decrement; the app (and Firestore reads here) drop it. */
    private fun counts(element: JsonElement?): Map<String, Int> =
        (element as? JsonObject).orEmpty().mapValues { it.value.jsonPrimitive.int }.filterValues { it != 0 }

    private fun entry(json: JsonElement): DexTrackerEntry {
        val o = json.jsonObject
        return DexTrackerEntry(
            id = o.getValue("id").jsonPrimitive.content,
            pokemonId = o.getValue("pokemonId").jsonPrimitive.int,
            name = o.getValue("name").jsonPrimitive.content,
            label = o.getValue("label").jsonPrimitive.content,
            target = o.getValue("target").jsonPrimitive.int,
            targets = o.getValue("targets").jsonArray.map { it.jsonPrimitive.int },
            owned = counts(o["owned"]),
            unassigned = o.getValue("unassigned").jsonPrimitive.int,
            extras = o.getValue("extras").jsonPrimitive.int,
            alts = counts(o["alts"]),
            order = o.getValue("order").jsonPrimitive.int,
            revision = o.getValue("revision").jsonPrimitive.long
        )
    }

    private fun action(json: JsonElement): DexTrackerAction {
        val o = json.jsonObject
        val account = o["account"]?.takeIf { it != JsonNull }?.jsonPrimitive?.content
        return when (o.getValue("type").jsonPrimitive.content) {
            "add" -> DexTrackerAction.Add(main = o.getValue("pool").jsonPrimitive.content == "main", delta = o.getValue("delta").jsonPrimitive.int, account = account)
            "trade" -> DexTrackerAction.Trade(o.getValue("count").jsonPrimitive.int, account)
            "remove" -> DexTrackerAction.Remove(o.getValue("pokemonId").jsonPrimitive.int)
            "assign" -> {
                val source = o.getValue("source").jsonPrimitive
                DexTrackerAction.Assign(
                    o.getValue("pokemonId").jsonPrimitive.int,
                    when {
                        source.longOrNull != null -> DexTrackerSource.Stage(source.int)
                        source.content == "unassigned" -> DexTrackerSource.Unassigned
                        source.content == "extras" -> DexTrackerSource.Spare(account)
                        else -> DexTrackerSource.NewCatch
                    }
                )
            }
            else -> error("Unknown action")
        }
    }

    private fun increments(json: JsonElement?): Map<String, Long> = json!!.jsonObject.mapValues { it.value.jsonPrimitive.long }

    @Test
    fun quickActionsMatchDexTracker() {
        val cases = fixtures.getValue("actions").jsonArray
        check(cases.size >= 20)
        cases.forEach { case ->
            val o = case.jsonObject
            val name = o.getValue("name").jsonPrimitive.content
            val before = entry(o.getValue("entry"))
            val action = action(o.getValue("action"))
            if ("error" in o) {
                assertThrows(name, DexTrackerException::class.java) { DexTrackerDomain.actionPatch(before, action) }
                return@forEach
            }
            val patch = DexTrackerDomain.actionPatch(before, action)
            assertEquals(name, entry(o.getValue("next")), patch.next)
            assertEquals(name, increments(o["increments"]), patch.increments)
            assertEquals(name, o.getValue("gained").jsonPrimitive.int, patch.gained)

            val expected = o.getValue("event").jsonObject
            val event = DexTrackerDomain.describeChange(before, patch.next, at = 1_000, id = "ev")!!.toMap()
            assertEquals(name, expected.keys, event.keys)
            expected.forEach { (key, value) ->
                val actual = event[key]
                if (value == JsonNull) assertNull("$name $key", actual)
                else assertEquals("$name $key", value.jsonPrimitive.content, actual.toString())
            }
        }
    }

    @Test
    fun undoMatchesDexTracker() {
        fixtures.getValue("reverts").jsonArray.forEach { case ->
            val o = case.jsonObject
            val name = o.getValue("name").jsonPrimitive.content
            val current = entry(o.getValue("current"))
            val before = entry(o.getValue("before"))
            val after = entry(o.getValue("after"))
            if ("error" in o) {
                assertThrows(name, DexTrackerException::class.java) { DexTrackerDomain.revertPatch(current, before, after) }
                return@forEach
            }
            val patch = DexTrackerDomain.revertPatch(current, before, after)
            assertEquals(name, entry(o.getValue("next")), patch.next)
            assertEquals(name, increments(o["increments"]), patch.increments)
            assertEquals(name, o.getValue("gained").jsonPrimitive.int, patch.gained)
        }
    }

    @Test
    fun progressStagesAndFiltersMatchDexTracker() {
        val entries = mutableListOf<DexTrackerEntry>()
        fixtures.getValue("states").jsonArray.forEach { case ->
            val o = case.jsonObject
            val e = entry(o.getValue("entry")).also(entries::add)
            val p = o.getValue("progress").jsonObject
            assertEquals(
                e.id,
                DexTrackerProgress(
                    p.getValue("owned").jsonPrimitive.int, p.getValue("missing").jsonPrimitive.int,
                    p.getValue("toCatch").jsonPrimitive.int, p.getValue("tradable").jsonPrimitive.int,
                    p.getValue("complete").jsonPrimitive.boolean, p.getValue("evolved").jsonPrimitive.int
                ),
                DexTrackerDomain.progress(e)
            )
            assertEquals(
                e.id,
                o.getValue("stages").jsonObject.map { (id, state) -> id.toInt() to DexTrackerStage.valueOf(state.jsonPrimitive.content.uppercase()) }.toMap(),
                DexTrackerDomain.stageStatus(e)
            )
            o.getValue("filters").jsonObject.forEach { (name, expected) ->
                val filter = if (name.startsWith("alt:")) DexTrackerFilter(DexTrackerFilterKind.ALT, name.removePrefix("alt:"))
                else DexTrackerFilter(DexTrackerFilterKind.valueOf(name.uppercase()))
                assertEquals("${e.id} $name", expected.jsonPrimitive.boolean, DexTrackerDomain.matchesFilter(e, filter))
            }
        }
        val t = fixtures.getValue("totals").jsonObject
        val totals = DexTrackerDomain.totals(entries)
        assertEquals(t.getValue("credited").jsonPrimitive.int, totals.credited)
        assertEquals(t.getValue("target").jsonPrimitive.int, totals.target)
        assertEquals(t.getValue("tradable").jsonPrimitive.int, totals.tradable)
        assertEquals(t.getValue("toCatch").jsonPrimitive.int, totals.toCatch)
        assertEquals(t.getValue("complete").jsonPrimitive.int, totals.complete)
        assertEquals(t.getValue("evolved").jsonPrimitive.int, totals.evolved)
    }

    @Test
    fun aStageTapPicksTheSourceTheWebAppWould() {
        val base = DexTrackerEntry(id = "e", pokemonId = 1, name = "Bulbasaur", target = 3, targets = listOf(1, 2, 3))
        assertEquals(DexTrackerAction.Remove(1), DexTrackerDomain.stageTap(base.copy(owned = mapOf("1" to 1)), 1))
        assertEquals(DexTrackerAction.Assign(2, DexTrackerSource.Unassigned), DexTrackerDomain.stageTap(base.copy(unassigned = 1), 2))
        assertEquals(DexTrackerAction.Assign(2, DexTrackerSource.Spare("alt1")), DexTrackerDomain.stageTap(base.copy(alts = mapOf("alt1" to 1)), 2))
        assertNull("two pools: the user picks", DexTrackerDomain.stageTap(base.copy(extras = 1, alts = mapOf("alt1" to 1)), 2))
        assertEquals(DexTrackerAction.Assign(2, DexTrackerSource.NewCatch), DexTrackerDomain.stageTap(base, 2))
        assertEquals(false, DexTrackerDomain.canRemoveFromMain(base.copy(owned = mapOf("1" to 1, "2" to 1))))
        assertEquals("2026-03-07", DexTrackerDomain.dayKey(LocalDate.of(2026, 3, 7)))
    }
}
