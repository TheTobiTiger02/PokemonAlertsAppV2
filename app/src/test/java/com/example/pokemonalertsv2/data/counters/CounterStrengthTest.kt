package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list-relative bar and the "needs a TM" badge.
 *
 * Both are pure and both are easy to get backwards — the estimator runs the opposite way to
 * every other metric, and a second charged move means a mismatch in slot one is not one.
 */
class CounterStrengthTest {

    @Test
    fun `only cost metrics count down`() {
        assertTrue(CounterMetric.ESTIMATOR.isLowerBetter())
        assertTrue(CounterMetric.TIME.isLowerBetter())
        assertFalse(CounterMetric.OVERALL.isLowerBetter())
        assertFalse(CounterMetric.POWER.isLowerBetter())
        assertFalse(CounterMetric.TDO.isLowerBetter())
    }

    @Test
    fun `the winner is the smallest estimator and the largest damage`() {
        val estimators = listOf(2.5, 1.4, 3.0)
        assertEquals(1.4, CounterMetric.ESTIMATOR.bestOf(estimators)!!, 1e-9)
        assertEquals(3.0, CounterMetric.TDO.bestOf(estimators)!!, 1e-9)
    }

    @Test
    fun `nulls and non-positive values are not candidates`() {
        assertNull(CounterMetric.ESTIMATOR.bestOf(listOf(null, null)))
        assertNull(CounterMetric.ESTIMATOR.bestOf(emptyList()))
        // A zero estimator is missing data, not an infinitely good counter.
        assertEquals(1.5, CounterMetric.ESTIMATOR.bestOf(listOf(0.0, 1.5))!!, 1e-9)
    }

    @Test
    fun `the best row always fills the bar whichever way the metric runs`() {
        assertEquals(1f, CounterMetric.ESTIMATOR.strengthRatio(1.4, 1.4), 1e-6f)
        assertEquals(1f, CounterMetric.TDO.strengthRatio(900.0, 900.0), 1e-6f)
    }

    @Test
    fun `a worse row is a ratio to the winner in both directions`() {
        // Twice the estimator is half as good.
        assertEquals(0.5f, CounterMetric.ESTIMATOR.strengthRatio(2.8, 1.4), 1e-6f)
        // Half the damage is half as good.
        assertEquals(0.5f, CounterMetric.TDO.strengthRatio(450.0, 900.0), 1e-6f)
    }

    @Test
    fun `missing values collapse to an empty bar rather than dividing by zero`() {
        assertEquals(0f, CounterMetric.ESTIMATOR.strengthRatio(null, 1.4), 1e-6f)
        assertEquals(0f, CounterMetric.ESTIMATOR.strengthRatio(1.4, null), 1e-6f)
        assertEquals(0f, CounterMetric.ESTIMATOR.strengthRatio(0.0, 1.4), 1e-6f)
    }

    @Test
    fun `a value better than the stated best is clamped instead of overflowing the bar`() {
        assertEquals(1f, CounterMetric.ESTIMATOR.strengthRatio(1.0, 1.4), 1e-6f)
        assertEquals(1f, CounterMetric.TDO.strengthRatio(1200.0, 900.0), 1e-6f)
    }

    // ── missingMoves ─────────────────────────────────────────────────────────

    private fun counter() = RaidCounter(
        rank = 1,
        pokemonId = "MACHAMP",
        displayName = "Machamp",
        fastMove = "Counter",
        chargedMove = "Dynamic Punch"
    )

    private fun owned(quick: String?, charge: String?, charge2: String? = null) = OwnedPokemon(
        displayName = "Machamp",
        form = null,
        level = 40.0,
        atkIv = 15,
        defIv = 15,
        staIv = 15,
        cp = 3056,
        quickMove = quick,
        chargeMove = charge,
        chargeMove2 = charge2,
        shadow = false,
        lucky = false,
        matchKeys = listOf("MACHAMP")
    )

    @Test
    fun `a matching copy needs no TM`() {
        val entry = DecoratedCounter(counter(), owned("Counter", "Dynamic Punch"))
        assertEquals(emptyList<String>(), entry.missingMoves)
        assertFalse(entry.movesetDiffers)
    }

    @Test
    fun `a second charged move counts as knowing it`() {
        val entry = DecoratedCounter(counter(), owned("Counter", "Cross Chop", "Dynamic Punch"))
        assertEquals(emptyList<String>(), entry.missingMoves)
    }

    @Test
    fun `both slots are reported when both are wrong`() {
        val entry = DecoratedCounter(counter(), owned("Karate Chop", "Cross Chop"))
        assertEquals(listOf("Counter", "Dynamic Punch"), entry.missingMoves)
        assertTrue(entry.movesetDiffers)
    }

    @Test
    fun `a move the import never recorded is not a mismatch`() {
        val entry = DecoratedCounter(counter(), owned("Counter", null))
        assertEquals(emptyList<String>(), entry.missingMoves)
    }

    @Test
    fun `a counter the user does not own is never flagged`() {
        assertFalse(DecoratedCounter(counter()).movesetDiffers)
    }
}
