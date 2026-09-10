package com.example.pokemonalertsv2.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalCadenceTest {

    private val spawnRadius = 40
    private val gymRadius = 80

    @Test
    fun `each band answers for its own distance`() {
        assertEquals(ArrivalCadence.Arriving, arrivalCadenceFor(50f, spawnRadius))
        assertEquals(ArrivalCadence.Approach, arrivalCadenceFor(200f, spawnRadius))
        assertEquals(ArrivalCadence.Walking, arrivalCadenceFor(500f, spawnRadius))
        assertEquals(ArrivalCadence.Far, arrivalCadenceFor(2_000f, spawnRadius))
    }

    @Test
    fun `the close band scales with the destination's own radius`() {
        // 100 m out: already arriving at a gym, still only approaching a spawn.
        assertEquals(ArrivalCadence.Approach, arrivalCadenceFor(100f, spawnRadius))
        assertEquals(ArrivalCadence.Arriving, arrivalCadenceFor(100f, gymRadius))
    }

    @Test
    fun `the last stretch is faster than the old fixed rate, not merely equal to it`() {
        val arriving = arrivalCadenceFor(30f, spawnRadius)

        assertEquals(ArrivalCadence.Arriving, arriving)
        // The evaluator needs two qualifying fixes 2 s apart; at 1 s that is quick.
        assertEquals(1_000L, arriving.intervalMillis)
        assertEquals(2f, arriving.minDisplacementMeters, 0.001f)
    }

    @Test
    fun `no fix yet asks for the fastest rate`() {
        // Never slow on an unknown: an indeterminate Live Update falls out of the chip.
        assertEquals(ArrivalCadence.Arriving, arrivalCadenceFor(null, spawnRadius))
        assertEquals(ArrivalCadence.Arriving, arrivalCadenceFor(Float.NaN, spawnRadius))
        assertEquals(ArrivalCadence.Arriving, arrivalCadenceFor(-1f, spawnRadius))
    }

    @Test
    fun `slowing down has to clear the boundary properly`() {
        // Arriving ends at 80 m for a spawn. Fifteen metres past is not enough...
        assertEquals(
            ArrivalCadence.Arriving,
            arrivalCadenceFor(95f, spawnRadius, current = ArrivalCadence.Arriving)
        )
        // ...twenty-five is.
        assertEquals(
            ArrivalCadence.Approach,
            arrivalCadenceFor(105f, spawnRadius, current = ArrivalCadence.Arriving)
        )
    }

    @Test
    fun `speeding up is immediate`() {
        // Crossing inward needs no margin at all: err toward more fixes.
        assertEquals(
            ArrivalCadence.Arriving,
            arrivalCadenceFor(79f, spawnRadius, current = ArrivalCadence.Far)
        )
        assertEquals(
            ArrivalCadence.Walking,
            arrivalCadenceFor(599f, spawnRadius, current = ArrivalCadence.Far)
        )
    }

    @Test
    fun `hysteresis does not strand a band once it is well past`() {
        assertEquals(
            ArrivalCadence.Far,
            arrivalCadenceFor(5_000f, spawnRadius, current = ArrivalCadence.Arriving)
        )
    }
}
