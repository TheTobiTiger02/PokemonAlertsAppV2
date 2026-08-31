package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.MegaBaseExpander
import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieMatcher
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieRow
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Mega Malamar report, end to end, against a real captured response.
 *
 * A level 50 Kartana holding Razor Leaf / Leaf Blade outranked Pinsir, which is wrong twice
 * over: Pokébattler scores that Kartana at estimator 4.93 while both Pinsir forms sit near
 * 2.2-2.7. The cause was not the scoring but the roster — the level 50 base Pinsir was never
 * synthesized, because the export also held a level 28 plain Pinsir, and that copy is then
 * dropped for being under [MIN_PERSONAL_LEVEL].
 *
 * The fixture is `attackers/levels/50` for `MALAMAR_MEGA`, trimmed to the three attackers
 * this test names.
 */
class PersonalRankingMalamarTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun response(): PokebattlerCountersResponse {
        val text = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("malamar_mega_level50.json")
        ).bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }

    /** The four rows this trainer's Poké Genie export actually contains. */
    private fun exportedRows() = listOf(
        PokeGenieRow(
            scanIndex = 21,
            name = "Pinsir",
            form = "Mega",
            cp = 4728,
            atkIv = 15,
            defIv = 15,
            staIv = 15,
            level = 50.0,
            quickMove = "Fury Cutter",
            chargeMove = "X-Scissor"
        ),
        PokeGenieRow(
            scanIndex = 2297,
            name = "Pinsir",
            cp = 2203,
            atkIv = 8,
            defIv = 6,
            staIv = 8,
            level = 28.0,
            quickMove = "Bug Bite",
            chargeMove = "X-Scissor"
        ),
        PokeGenieRow(
            scanIndex = 2305,
            name = "Kartana",
            cp = 4156,
            atkIv = 15,
            defIv = 15,
            staIv = 15,
            level = 50.0,
            quickMove = "Razor Leaf",
            chargeMove = "Leaf Blade"
        ),
        PokeGenieRow(
            scanIndex = 2306,
            name = "Kartana",
            cp = 3665,
            atkIv = 15,
            defIv = 15,
            staIv = 14,
            level = 40.0,
            quickMove = "Razor Leaf",
            chargeMove = "Leaf Blade"
        )
    )

    private fun PokeGenieRow.toOwned(): OwnedPokemon = OwnedPokemon(
        displayName = name,
        form = form,
        level = level,
        atkIv = atkIv,
        defIv = defIv,
        staIv = staIv,
        cp = cp,
        quickMove = quickMove,
        chargeMove = chargeMove,
        chargeMove2 = chargeMove2,
        shadow = false,
        lucky = false,
        matchKeys = PokeGenieMatcher.matchKeysFor(this)
    )

    /** The level 50 bucket, ranked exactly as `loadPokeGeniePersonal` would rank it. */
    private fun rankLevel50(): List<PersonalCounter> {
        val expanded = MegaBaseExpander.expand(exportedRows())
        val plan = planPersonalLevels(expanded.rows.map { it.toOwned() })
        val owned = plan.byLevel.getValue(MAX_PERSONAL_LEVEL)
        return rankPersonalLevel(
            level = MAX_PERSONAL_LEVEL,
            owned = owned,
            response = response(),
            movesMode = PersonalMovesMode.CURRENT,
            metric = CounterMetric.ESTIMATOR,
            bossMoveset = null
        ).sortedWith(personalComparator(CounterMetric.ESTIMATOR))
    }

    @Test
    fun `the level 50 base Pinsir is in the roster`() {
        val ranked = rankLevel50()
        val pinsir = ranked.firstOrNull { it.pokemonId == "PINSIR" }

        assertNotNull("no non-mega Pinsir was ranked", pinsir)
        assertEquals(50.0, pinsir!!.evaluatedLevel!!, 1e-9)
        // Synthesized from the mega scan, so it carries the mega's moves.
        assertEquals("FURY_CUTTER_FAST", pinsir.fastMove.moveId)
        assertEquals("X_SCISSOR", pinsir.chargedMove.moveId)
    }

    @Test
    fun `Pinsir outranks a Kartana that only knows Leaf Blade`() {
        val ranked = rankLevel50()
        val order = ranked.map { it.pokemonId }

        assertEquals(listOf("PINSIR_MEGA", "PINSIR", "KARTANA"), order)
    }

    @Test
    fun `each copy is scored on the moveset it actually knows`() {
        val ranked = rankLevel50()
        val kartana = ranked.single { it.pokemonId == "KARTANA" }

        assertEquals("RAZOR_LEAF_FAST", kartana.fastMove.moveId)
        assertEquals("LEAF_BLADE", kartana.chargedMove.moveId)
        // Not the 2.489 its best moveset would score.
        assertEquals(4.932179, kartana.metrics.estimator!!, 1e-4)
        assertTrue(!kartana.movesetAssumed && !kartana.movesetUnlisted)
    }

    @Test
    fun `the level 28 Pinsir is excluded rather than ranked`() {
        val expanded = MegaBaseExpander.expand(exportedRows())
        val plan = planPersonalLevels(expanded.rows.map { it.toOwned() })

        assertEquals(1, plan.skippedBelowMinimum)
        assertEquals(listOf(MIN_PERSONAL_LEVEL, MAX_PERSONAL_LEVEL), plan.levels)
    }

    @Test
    fun `a moveset Pokebattler does not list is floored, not dropped`() {
        // Kartana's Air Slash / Sacred Sword is legal but outside the ten movesets the
        // response carries, so the row must survive at the worst listed score.
        val defender = response().attackers.first().randomMove!!.defenders
            .single { it.pokemonId == "KARTANA" }
        val mine = OwnedPokemon(
            displayName = "Kartana",
            form = null,
            level = 50.0,
            atkIv = 15,
            defIv = 15,
            staIv = 15,
            cp = 4156,
            quickMove = "Air Slash",
            chargeMove = "Sacred Sword",
            shadow = false,
            lucky = false,
            matchKeys = listOf("KARTANA")
        )

        val choice = selectOwnedMoves(defender, mine, PersonalMovesMode.CURRENT, CounterMetric.ESTIMATOR)

        assertNotNull(choice)
        assertTrue(choice!!.unlisted)
        assertEquals(5.432771, choice.move.result!!.estimator!!, 1e-4)
    }
}
