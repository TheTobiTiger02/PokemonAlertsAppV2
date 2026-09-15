package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.tracking.isEligibleArrivalDestination
import com.example.pokemonalertsv2.ui.alerts.mapCoordinatesOrNull
import com.example.pokemonalertsv2.ui.alerts.mapPipDistanceMeters
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Replays a recorded alert feed and walks the hunt's own suggestions, the way a trainer would.
 *
 * Opt-in and offline: it needs a directory of feed snapshots (`feed/<epochMillis>.json`, each a
 * JSON array of alerts) and `legs.json` holding real pedestrian distances between alert nodes
 * (`"lat4,lon4|lat4,lon4": meters`). Without `-Dhunt.replay.dir` it is skipped, so it never runs
 * in CI. It measures what the planner does to a walk -- catches, detours, doubling back, how much
 * the route moves when a new alert lands, and how long a plan takes -- rather than asserting,
 * because "a good route" is judged by comparing runs.
 *
 * Options (system properties):
 * - `hunt.replay.dir` -- snapshots + legs.
 * - `hunt.replay.startLat` / `hunt.replay.startLon` -- where the trainer starts.
 * - `hunt.replay.endShiftMinutes` -- pull every end time earlier, to replay a feed recorded in
 *   the afternoon as if it were the last hour before the alerts end.
 * - `hunt.replay.label` -- names the output files.
 */
class HuntReplaySimulationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false; coerceInputValues = true }

    @Test
    fun `replay a recorded hunt`() {
        val dir = System.getProperty("hunt.replay.dir")?.let(::File)
        assumeTrue("set -Dhunt.replay.dir to run the replay", dir != null && dir.isDirectory)
        dir!!
        val startLat = System.getProperty("hunt.replay.startLat")?.toDouble() ?: 49.7418
        val startLon = System.getProperty("hunt.replay.startLon")?.toDouble() ?: 8.6048
        val shiftMillis = (System.getProperty("hunt.replay.endShiftMinutes")?.toLong() ?: 0L) * 60_000L
        val label = System.getProperty("hunt.replay.label") ?: "run"

        val snapshots = File(dir, "feed").listFiles { f -> f.extension == "json" }!!
            .sortedBy { it.nameWithoutExtension.toLong() }
            .map { file -> file.nameWithoutExtension.toLong() to loadAlerts(file, shiftMillis) }
        val legs = loadLegs(File(dir, "legs.json"))
        val minutes = System.getProperty("hunt.replay.minutes")?.toLong() ?: 0L
        val holdBack = System.getProperty("hunt.replay.holdBackPercent")?.toInt() ?: 0
        val result = Simulation(snapshots, legs, startLat, startLon, CurrentPlanner, minutes, holdBack).run()

        val out = File(dir, "out").apply { mkdirs() }
        File(out, "$label-summary.txt").writeText(result.summary)
        File(out, "$label-walk.geojson").writeText(result.walkGeoJson)
        File(out, "$label-plans.txt").writeText(result.planLog)
        println(result.summary)
    }

    private fun loadAlerts(file: File, shiftMillis: Long): List<PokemonAlert> =
        json.decodeFromString<List<PokemonAlert>>(file.readText()).map { alert ->
            val end = TimeUtils.parseEndTimeToMillis(alert.endTime)
            if (end == null) alert else alert.copy(endTime = Instant.ofEpochMilli(end - shiftMillis).toString())
        }

    private fun loadLegs(file: File): Map<HuntLegKey, Double> {
        val root = json.parseToJsonElement(file.readText()) as JsonObject
        val legs = HashMap<HuntLegKey, Double>(root.size)
        for ((key, value) in root) {
            if (value is JsonNull) continue
            val (from, to) = key.split('|').map { part ->
                val (lat, lon) = part.split(',').map(String::toInt)
                HuntLegNode(lat, lon)
            }
            legs[HuntLegKey(from, to)] = value.jsonPrimitive.int.toDouble()
        }
        return legs
    }
}

/** What the planner under test hands back: the numbered walk, and where to go now. */
internal class SimPlan(val route: List<PokemonAlert>, val target: PokemonAlert?)

internal fun interface SimPlanner {
    fun plan(
        alive: List<PokemonAlert>,
        latitude: Double,
        longitude: Double,
        nowMillis: Long,
        costs: HuntLegCosts,
        currentTarget: PokemonAlert?,
        previousRouteIds: List<String>
    ): SimPlan
}

/** The planner as the service drives it: the previous route carried forward, its head walked to. */
internal val CurrentPlanner = SimPlanner { alive, latitude, longitude, nowMillis, costs, _, previousRouteIds ->
    val plan = huntPlan(alive, latitude, longitude, nowMillis, costs, previousRouteIds)
    SimPlan(plan.route, plan.route.firstOrNull())
}

/** Routed legs between alert nodes, plus the trainer's row whenever they stand on a node. */
private class SimCosts(
    private val legs: Map<HuntLegKey, Double>,
    private val nodes: Map<String, HuntLegNode>,
    private val originNode: HuntLegNode?
) : HuntLegCosts {
    override val calculatedAtMillis: Long = 0L
    override fun walkedMetersOrNull(fromId: String?, toId: String): Double? {
        val from = if (fromId == null) originNode else nodes[fromId]
        val to = nodes[toId]
        if (from == null || to == null || from == to) return null
        return legs[HuntLegKey(from, to)]
    }
    override fun walkSecondsFromOriginOrNull(toId: String, slackMeters: Double): Long? =
        walkedMetersOrNull(null, toId)?.let { huntWalkSeconds((it - slackMeters).coerceAtLeast(0.0).toInt()) }
    override fun forOrigin(latitude: Double, longitude: Double, nowMillis: Long): HuntLegCosts = this
}

internal class SimResult(val summary: String, val walkGeoJson: String, val planLog: String)

private class Simulation(
    private val snapshots: List<Pair<Long, List<PokemonAlert>>>,
    private val legs: Map<HuntLegKey, Double>,
    startLat: Double,
    startLon: Double,
    private val planner: SimPlanner,
    /** Holds the last snapshot this long past the first, for recordings shorter than a walk. */
    private val minimumMinutes: Long,
    /**
     * Share of alerts withheld from the start and released at a fixed pseudo-random moment of the
     * run. A recorded Rocket feed barely changes in an hour, so this is what exercises the planner
     * against alerts arriving mid-walk.
     */
    private val holdBackPercent: Int
) {
    private var lat = startLat
    private var lon = startLon
    private var standingOn: HuntLegNode? = null

    private var target: PokemonAlert? = null
    private var legFromLat = lat
    private var legFromLon = lon
    private var legStartMillis = 0L
    private var legSeconds = 0L
    private var legMeters = 0.0

    private val caught = HashSet<String>()
    private var walkedMeters = 0.0
    private var missedOnArrival = 0
    private var switches = 0
    private var plannedLate = 0
    private var numberedStops = 0
    private var backtracks = 0
    private var churnEvents = 0
    private var churnMoves = 0
    private var newcomerJumps = 0
    private var routeChanges = 0
    private val planNanos = ArrayList<Long>()
    private val walk = ArrayList<Pair<Double, Double>>()
    private val catches = ArrayList<Triple<Long, String, Pair<Double, Double>>>()
    private val log = StringBuilder()

    fun run(): SimResult {
        val start = snapshots.first().first
        val end = maxOf(snapshots.last().first, start + minimumMinutes * 60_000L)
        var previousRoute = emptyList<String>()
        var previousIds = emptySet<String>()
        walk += lat to lon
        val releaseAt = HashMap<String, Long>()
        snapshots.first().second.forEach { alert ->
            val h = (alert.uniqueId.hashCode().toLong() and 0x7fffffff)
            if (h % 100 < holdBackPercent) releaseAt[alert.uniqueId] = start + (h / 100) % (end - start)
        }
        var now = start
        while (now <= end) {
            val feed = snapshots.last { it.first <= now }.second
            val alive = feed.filter {
                it.uniqueId !in caught && it.isEligibleArrivalDestination(now) && (releaseAt[it.uniqueId] ?: 0L) <= now
            }
            val nodes = alive.mapNotNull { a -> a.mapCoordinatesOrNull()?.let { a.uniqueId to huntLegNode(it.latitude, it.longitude) } }.toMap()
            val costs = SimCosts(legs, nodes, standingOn)

            val began = System.nanoTime()
            val plan = planner.plan(alive, lat, lon, now, costs, target?.takeIf { t -> alive.any { it.uniqueId == t.uniqueId } }, previousRoute)
            planNanos += System.nanoTime() - began

            val routeIds = plan.route.map { it.uniqueId }
            // The map numbers nine; that is the route the trainer actually sees.
            val visible = routeIds.take(9)
            val previousVisible = previousRoute.take(9)
            val ids = alive.map { it.uniqueId }.toSet()
            val newcomers = ids - previousIds
            if (previousIds.isNotEmpty() && newcomers.isNotEmpty()) {
                val before = previousRoute.filter { it in ids }.take(5)
                val after = routeIds.filter { it !in newcomers }.take(5)
                if (before != after) {
                    churnEvents++
                    churnMoves += before.zip(after).count { (a, b) -> a != b } + abs(before.size - after.size)
                }
                if (routeIds.take(2).any { it in newcomers }) newcomerJumps++
            }
            if (visible != previousVisible) {
                // Progress along the route (the head caught) is not a change the trainer notices.
                if (visible != previousRoute.filter { it in ids }.take(9)) routeChanges++
                scoreRoute(plan.route.take(9), now, costs)
            }
            previousIds = ids
            previousRoute = routeIds

            val next = plan.target
            if (next?.uniqueId != target?.uniqueId) {
                if (target != null && target!!.uniqueId in ids) switches++
                beginLeg(next, now, costs)
                log.append("${Instant.ofEpochMilli(now)} target=${next?.name} legM=${legMeters.toInt()} route=${plan.route.take(9).joinToString(" > ") { it.name.take(24) }}")
                // Machine-readable tail for plotting: trainer, numbered stops, then every live alert.
                log.append(" |at=$lat,$lon|route=${plan.route.take(9).joinToString(";") { "${it.latitude},${it.longitude}" }}")
                log.append("|alive=${alive.joinToString(";") { "${it.latitude},${it.longitude}" }}\n")
            }
            advance(now)
            now += TICK_MILLIS
        }

        val hours = (end - start) / 3_600_000.0
        val sorted = planNanos.sorted()
        val summary = buildString {
            appendLine("simulated ${"%.2f".format(hours)} h, ${snapshots.size} snapshots")
            appendLine("catches=${caught.size} (${"%.1f".format(caught.size / hours)}/h) walked=${walkedMeters.toInt()} m, m/catch=${if (caught.isEmpty()) "-" else (walkedMeters / caught.size).toInt()}")
            appendLine("missedOnArrival=$missedOnArrival targetSwitches=$switches")
            appendLine("distinct plans scored: numberedStops=$numberedStops numberedButLate=$plannedLate backtracks=$backtracks")
            appendLine("routeChanges=$routeChanges newAlertEvents with top5 churn=$churnEvents moves=$churnMoves newcomerInTop2=$newcomerJumps")
            appendLine("plan ms p50=${"%.2f".format(sorted[sorted.size / 2] / 1e6)} p99=${"%.2f".format(sorted[(sorted.size * 99) / 100] / 1e6)} max=${"%.2f".format(sorted.last() / 1e6)}")
        }
        return SimResult(summary, geoJson(), log.toString())
    }

    private fun beginLeg(next: PokemonAlert?, now: Long, costs: HuntLegCosts) {
        target = next
        legFromLat = lat
        legFromLon = lon
        legStartMillis = now
        val coordinates = next?.mapCoordinatesOrNull() ?: return
        val toPin = costs.walkedMetersOrNull(null, next.uniqueId)
            ?: (mapPipDistanceMeters(lat, lon, coordinates.latitude, coordinates.longitude) * WalkingRouteUtils.DETOUR_FACTOR)
        legMeters = (toPin - huntInteractionRadiusMeters(next)).coerceAtLeast(0.0)
        legSeconds = huntWalkSeconds(legMeters.toInt())
    }

    private fun advance(now: Long) {
        val current = target ?: return
        val coordinates = current.mapCoordinatesOrNull() ?: return
        val elapsed = (now + TICK_MILLIS - legStartMillis) / 1000.0
        if (elapsed >= legSeconds) {
            walkedMeters += legMeters
            val endMillis = TimeUtils.parseEndTimeToMillis(current.endTime)
            val arrival = legStartMillis + legSeconds * 1000
            if (endMillis != null && arrival > endMillis) missedOnArrival++ else {
                caught += current.uniqueId
                catches += Triple(arrival, current.name, coordinates.latitude to coordinates.longitude)
            }
            lat = coordinates.latitude
            lon = coordinates.longitude
            standingOn = huntLegNode(lat, lon)
            walk += lat to lon
            target = null
            return
        }
        // Mid-leg the walk is a straight line in space but on the routed clock.
        val share = elapsed / legSeconds
        val wasOn = standingOn
        lat = legFromLat + (coordinates.latitude - legFromLat) * share
        lon = legFromLon + (coordinates.longitude - legFromLon) * share
        if (wasOn != null && mapPipDistanceMeters(wasOn.latitudeE4 / 1e4, wasOn.longitudeE4 / 1e4, lat, lon) > HUNT_ORIGIN_DRIFT_METERS) {
            standingOn = null
        }
    }

    /**
     * Looks at a plan the way the user did at the screen: is every numbered stop one the walk
     * reaches in time, and does the line double back on itself.
     */
    private fun scoreRoute(route: List<PokemonAlert>, now: Long, costs: HuntLegCosts) {
        var fromId: String? = null
        var fromLat = lat
        var fromLon = lon
        var clock = 0L
        val points = ArrayList<Pair<Double, Double>>()
        points += lat to lon
        for (alert in route) {
            val c = alert.mapCoordinatesOrNull() ?: continue
            val pin = costs.walkedMetersOrNull(fromId, alert.uniqueId)
                ?: (mapPipDistanceMeters(fromLat, fromLon, c.latitude, c.longitude) * WalkingRouteUtils.DETOUR_FACTOR)
            clock += huntWalkSeconds((pin - huntInteractionRadiusMeters(alert)).coerceAtLeast(0.0).toInt())
            val end = TimeUtils.parseEndTimeToMillis(alert.endTime)
            numberedStops++
            if (end != null && now + clock * 1000 > end) plannedLate++
            fromId = alert.uniqueId
            fromLat = c.latitude
            fromLon = c.longitude
            points += fromLat to fromLon
        }
        for (i in 1 until points.size - 1) {
            val a = bearing(points[i - 1], points[i])
            val b = bearing(points[i], points[i + 1])
            val turn = abs(((b - a + 540) % 360) - 180)
            val hop = mapPipDistanceMeters(points[i].first, points[i].second, points[i + 1].first, points[i + 1].second)
            if (turn > 135 && hop > 150) backtracks++
        }
    }

    private fun bearing(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val y = sin(Math.toRadians(b.second - a.second)) * cos(Math.toRadians(b.first))
        val x = cos(Math.toRadians(a.first)) * sin(Math.toRadians(b.first)) -
            sin(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * cos(Math.toRadians(b.second - a.second))
        return Math.toDegrees(atan2(y, x))
    }

    private fun geoJson(): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[")
        append("{\"type\":\"Feature\",\"properties\":{\"kind\":\"walk\"},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        append(walk.joinToString(",") { "[${it.second},${it.first}]" })
        append("]}}")
        catches.forEachIndexed { index, (at, name, point) ->
            append(",{\"type\":\"Feature\",\"properties\":{\"n\":${index + 1},\"at\":\"${Instant.ofEpochMilli(at)}\",\"name\":\"${name.replace("\"", "'")}\"},")
            append("\"geometry\":{\"type\":\"Point\",\"coordinates\":[${point.second},${point.first}]}}")
        }
        append("]}")
    }

    private companion object {
        const val TICK_MILLIS = 10_000L
    }
}
