package com.example.pokemonalertsv2.tracking

/**
 * How often to ask for a location fix, as a function of how far there is left to walk.
 *
 * A hunt used to hold one rate for its whole length. Nine hundred metres out that is
 * spending battery on precision nobody can use, and in the last few metres -- where
 * the arrival actually gets decided -- it was no better than anywhere else.
 *
 * Kept as a value and computed by a pure function, the way
 * [com.example.pokemonalertsv2.ui.alerts.MapPoseCadence] and
 * [com.example.pokemonalertsv2.raidwatch.RaidWatchTiming] are, so the bands can be
 * tested without a device.
 */
internal data class ArrivalCadence(
    val intervalMillis: Long,
    val minIntervalMillis: Long,
    val minDisplacementMeters: Float
) {
    companion object {
        /**
         * The last stretch, and the only band that is *faster* than the old fixed rate.
         *
         * [ArrivalFixEvaluator] needs two qualifying fixes at least two seconds apart
         * before it will call an arrival, so this is where the fixes have to be cheap.
         */
        val Arriving = ArrivalCadence(1_000L, 500L, 2f)

        /** What every hunt used to run at, start to finish. */
        val Approach = ArrivalCadence(3_000L, 2_000L, 5f)

        val Walking = ArrivalCadence(8_000L, 5_000L, 10f)

        /** Far enough that the next fix cannot change any decision being made. */
        val Far = ArrivalCadence(20_000L, 10_000L, 25f)

        /** Keep a usable origin for new matches even while the trainer is stationary. */
        val Standby = ArrivalCadence(20_000L, 10_000L, 0f)
    }
}

/**
 * How much room to leave in front of the arrival radius before slowing down.
 *
 * Forty metres is about thirty seconds of walking, which at [ArrivalCadence.Arriving]
 * is dozens of fixes -- far more than the evaluator's two-fix dwell needs. Being a
 * margin *on top of* the destination's own radius, it scales: 80 m for a 40 m spawn,
 * 120 m for an 80 m gym or a Spacial Rend spawn.
 */
private const val ARRIVING_MARGIN_METERS = 40f

private const val APPROACH_MAX_METERS = 250f
private const val WALKING_MAX_METERS = 600f

/**
 * How far past a boundary the trainer must be before the rate is allowed to drop.
 *
 * One-directional on purpose: slowing down needs the margin, speeding up is
 * immediate. Pacing back and forth across a boundary would otherwise tear the request
 * down and rebuild it on every fix, and every ambiguous case should resolve toward
 * more fixes rather than fewer.
 */
private const val CADENCE_HYSTERESIS_METERS = 20f

/**
 * The cadence for a walk with [distanceMeters] left to a destination of [radiusMeters].
 *
 * A null distance means the service has no fix yet, which answers
 * [ArrivalCadence.Arriving] -- fail fast, never slow. That also protects the status
 * bar chip: the Live Update goes indeterminate without a distance, and an
 * indeterminate one is dropped out of the chip into the shade.
 */
internal fun arrivalCadenceFor(
    distanceMeters: Float?,
    radiusMeters: Int,
    current: ArrivalCadence? = null
): ArrivalCadence {
    val distance = distanceMeters?.takeIf { it.isFinite() && it >= 0f } ?: return ArrivalCadence.Arriving
    val arrivingMax = radiusMeters.toFloat() + ARRIVING_MARGIN_METERS

    // Slowing down has to clear the boundary by the hysteresis margin; speeding up
    // does not, so the comparison is against a boundary pushed out only when the
    // trainer is currently on the faster side of it.
    fun boundary(edge: Float, faster: ArrivalCadence): Float =
        if (current != null && current.intervalMillis <= faster.intervalMillis) {
            edge + CADENCE_HYSTERESIS_METERS
        } else {
            edge
        }

    return when {
        distance <= boundary(arrivingMax, ArrivalCadence.Arriving) -> ArrivalCadence.Arriving
        distance <= boundary(APPROACH_MAX_METERS, ArrivalCadence.Approach) -> ArrivalCadence.Approach
        distance <= boundary(WALKING_MAX_METERS, ArrivalCadence.Walking) -> ArrivalCadence.Walking
        else -> ArrivalCadence.Far
    }
}
