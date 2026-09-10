package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.ui.alerts.mapPipDistanceMeters
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import kotlin.math.ceil

/**
 * Routed walking costs for one hunt, already resolved and frozen.
 *
 * Hunt ordering runs in two places that cannot suspend -- inside a `remember` in the
 * map's composition, and on the tracking service's location-fix path -- so the walk
 * costs have to be readable synchronously. Everything that can block, fail or wait
 * lives in [HuntRouteMatrixCache]; this is only ever a map lookup.
 */
internal interface HuntLegCosts {

    /** When the snapshot was assembled. Identity for `remember` keys, and for logs. */
    val calculatedAtMillis: Long

    /**
     * Walked metres from [fromId] to [toId], where a null [fromId] means the trainer.
     *
     * Null whenever the pair was never routed, came back unreachable, has aged out,
     * or -- for legs from the trainer -- the trainer has since walked far enough that
     * the row no longer describes where they are standing.
     */
    fun walkedMetersOrNull(fromId: String?, toId: String): Double?

    /** Seconds to walk from the trainer to [toId]. See [huntWalkSeconds]. */
    fun walkSecondsFromOriginOrNull(toId: String): Long?

    /**
     * The same costs judged from where the trainer is now, and at [nowMillis].
     *
     * Cheap by contract: callers use it on the location-fix path, so it may allocate
     * a small wrapper but must not copy or recompute the legs.
     */
    fun forOrigin(latitude: Double, longitude: Double, nowMillis: Long): HuntLegCosts

    /** Nothing routed: exactly today's behaviour. The default at every call site. */
    object None : HuntLegCosts {
        override val calculatedAtMillis: Long = 0L
        override fun walkedMetersOrNull(fromId: String?, toId: String): Double? = null
        override fun walkSecondsFromOriginOrNull(toId: String): Long? = null
        override fun forOrigin(latitude: Double, longitude: Double, nowMillis: Long): HuntLegCosts = this
    }
}

/**
 * Whether to believe the server's own duration instead of deriving one.
 *
 * Off, deliberately. The app estimates every unrouted leg at
 * [WalkingRouteUtils.AVERAGE_WALKING_SPEED_MPS], and reachability compares routed and
 * estimated targets in one list -- so a second speed model would let a target change
 * classification purely by which source happened to answer for it. Deriving seconds
 * from the routed *distance* keeps one model and still takes the real improvement,
 * which is the distance, not the speed.
 *
 * Turn on once field data says the server's durations and this estimate agree; the
 * provider value is carried in [HuntLeg] either way, because it encodes stairs and
 * grade that a flat speed cannot.
 */
internal const val HUNT_USE_PROVIDER_DURATIONS = false

/** Past this, a leg from the trainer describes somewhere they no longer are. */
internal const val HUNT_ORIGIN_DRIFT_METERS = 75.0

/** Matches WalkingRouteRepository, and the server's own matrix cache. */
internal const val HUNT_LEG_TTL_MILLIS = 10 * 60 * 1000L

/**
 * The endpoint takes at most 30 points *including* the trainer, so only 29 targets
 * can be routed while [HUNT_CHAIN_POOL] chains 30. The 30th always falls back to a
 * straight line, which is the right way round: it is the least likely to be walked.
 */
internal const val HUNT_MATRIX_MAX_TARGETS = 29

/** One routed leg. */
internal data class HuntLeg(val distanceMeters: Int, val durationSeconds: Int)

/** A point offered to the matrix: an alert, or the trainer. */
internal data class HuntRoutePoint(val id: String, val latitude: Double, val longitude: Double)

/**
 * A coordinate rounded to about 11 m.
 *
 * Legs are keyed on position rather than on alert id, matching the server's own cache
 * key, so a leg survives an alert expiring and returning, two alerts on one PokeStop
 * share it, and an id reused at new coordinates can never read a stale leg.
 */
internal data class HuntLegNode(val latitudeE4: Int, val longitudeE4: Int)

internal fun huntLegNode(latitude: Double, longitude: Double): HuntLegNode =
    HuntLegNode(Math.round(latitude * 10_000.0).toInt(), Math.round(longitude * 10_000.0).toInt())

internal data class HuntLegKey(val from: HuntLegNode, val to: HuntLegNode)

/** Seconds to walk [meters], on the app's one speed model. */
internal fun huntWalkSeconds(meters: Int): Long =
    ceil(meters / WalkingRouteUtils.AVERAGE_WALKING_SPEED_MPS.toDouble()).toLong().coerceAtLeast(1L)

/**
 * The snapshot [HuntRouteMatrixCache] publishes.
 *
 * Immutable and shared: readers hold it without a lock, and [forOrigin] re-judges it
 * against a new position without copying the leg map.
 */
internal class ResolvedHuntLegCosts(
    private val legs: Map<HuntLegKey, HuntLeg>,
    private val nodes: Map<String, HuntLegNode>,
    private val originNode: HuntLegNode,
    private val originLatitude: Double,
    private val originLongitude: Double,
    override val calculatedAtMillis: Long,
    private val expiresAtMillis: Long,
    private val readLatitude: Double = originLatitude,
    private val readLongitude: Double = originLongitude,
    private val readAtMillis: Long = calculatedAtMillis
) : HuntLegCosts {

    private val expired: Boolean get() = readAtMillis >= expiresAtMillis

    /**
     * True once the trainer has walked far enough for the origin row to be stale.
     *
     * Only the origin row: a leg between two alerts is the same walk wherever the
     * trainer happens to be standing, so those stay usable for the whole TTL.
     */
    private val originDrifted: Boolean by lazy {
        mapPipDistanceMeters(originLatitude, originLongitude, readLatitude, readLongitude) >
            HUNT_ORIGIN_DRIFT_METERS
    }

    override fun forOrigin(latitude: Double, longitude: Double, nowMillis: Long): HuntLegCosts =
        ResolvedHuntLegCosts(
            legs = legs,
            nodes = nodes,
            originNode = originNode,
            originLatitude = originLatitude,
            originLongitude = originLongitude,
            calculatedAtMillis = calculatedAtMillis,
            expiresAtMillis = expiresAtMillis,
            readLatitude = latitude,
            readLongitude = longitude,
            readAtMillis = nowMillis
        )

    private fun legOrNull(fromId: String?, toId: String): HuntLeg? {
        if (expired) return null
        if (fromId == null && originDrifted) return null
        val from = if (fromId == null) originNode else nodes[fromId] ?: return null
        val to = nodes[toId] ?: return null
        if (from == to) return null
        return legs[HuntLegKey(from, to)]
    }

    override fun walkedMetersOrNull(fromId: String?, toId: String): Double? =
        legOrNull(fromId, toId)?.distanceMeters?.toDouble()

    override fun walkSecondsFromOriginOrNull(toId: String): Long? {
        val leg = legOrNull(null, toId) ?: return null
        return if (HUNT_USE_PROVIDER_DURATIONS) leg.durationSeconds.toLong()
        else huntWalkSeconds(leg.distanceMeters)
    }
}
