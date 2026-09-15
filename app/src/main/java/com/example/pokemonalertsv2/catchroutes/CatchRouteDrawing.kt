package com.example.pokemonalertsv2.catchroutes

import kotlin.math.*

/** Display geometry only. Never used by scoring, distance, or visit tracking. */
internal data class CatchDrawSegment(val from: CatchPoint, val to: CatchPoint, val startMeters: Double, val endMeters: Double)
internal data class CatchDrawMarker(val point: CatchPoint, val label: String, val bearing: Double = 0.0, val meters: Double = 0.0)

/**
 * What the route map draws: the line in lane-separated [segments], direction [arrows] (dense; the map
 * thins them to a fixed on-screen spacing), numbered [stops] where catches happen, and the two [endpoints].
 */
internal data class CatchRouteDrawing(
    val segments: List<CatchDrawSegment>,
    val arrows: List<CatchDrawMarker>,
    val stops: List<CatchDrawMarker>,
    val endpoints: List<CatchDrawMarker>,
)

/** Arrow candidates along the line; the map keeps one per ~90 dp on screen, so zooming in shows more. */
internal const val CATCH_ARROW_SPACING_METERS = 30.0

/**
 * Turns a routed [path] into drawing geometry. [stopMeters] are the positions along the path of the
 * places where Pokémon are caught, in walking order; they become the numbered stops. Out-and-back legs
 * over the same street are drawn in two lanes so both directions stay visible.
 */
internal fun catchRouteDrawing(path: List<CatchPathPosition>, stopMeters: List<Double> = emptyList()): CatchRouteDrawing {
    if (path.isEmpty()) return CatchRouteDrawing(emptyList(), emptyList(), emptyList(), emptyList())
    fun key(p: CatchPoint) = "${round(p.latitude * 100000).toLong()}:${round(p.longitude * 100000).toLong()}"
    val vertices = CatchSpatialIndex(path) { it.point }
    // Split at shared vertices so different upstream segmentation still gets separate lanes.
    val legs = path.zipWithNext().flatMap { (a,b) ->
        val sx=111195.0*cos(Math.toRadians(a.point.latitude)).coerceAtLeast(0.01)
        val dx=(b.point.longitude-a.point.longitude)*sx
        val dy=(b.point.latitude-a.point.latitude)*111195.0
        val length2=dx*dx+dy*dy
        if(length2<0.01) emptyList() else {
            val splits=vertices.near(a.point,b.point,1.5).mapNotNull { v ->
                val x=(v.point.longitude-a.point.longitude)*sx
                val y=(v.point.latitude-a.point.latitude)*111195.0
                val t=(x*dx+y*dy)/length2
                if(t>0.000001 && t<0.999999 && abs(x*dy-y*dx)/sqrt(length2)<1.5)
                    CatchPathPosition(v.point,a.meters+(b.meters-a.meters)*t) else null
            }.distinctBy { key(it.point) }.sortedBy { it.meters }
            (listOf(a)+splits+listOf(b)).zipWithNext().filter { catchDistance(it.first.point,it.second.point)>0.1 }
        }
    }
    fun edge(a: CatchPoint, b: CatchPoint) = listOf(key(a), key(b)).sorted().joinToString("/")
    val repeats = legs.withIndex().groupBy { edge(it.value.first.point, it.value.second.point) }
    val lanes = mutableMapOf<Int, Double>()
    for (group in repeats.values) group.forEachIndexed { lane, entry ->
        val (a,b) = entry.value
        lanes[entry.index] = (lane - (group.size - 1) / 2.0) * 6.0 * if (key(a.point) < key(b.point)) 1 else -1
    }
    val segments = mutableListOf<CatchDrawSegment>()
    val arrows = mutableListOf<CatchDrawMarker>()
    val stops = mutableListOf<CatchDrawMarker>()
    val pendingStops = stopMeters.sorted().toMutableList()
    var nextArrow = CATCH_ARROW_SPACING_METERS
    for ((i, leg) in legs.withIndex()) {
        val (a,b) = leg
        val scale = 111195.0 * cos(Math.toRadians((a.point.latitude+b.point.latitude)/2)).coerceAtLeast(0.01)
        val dx = (b.point.longitude-a.point.longitude)*scale
        val dy = (b.point.latitude-a.point.latitude)*111195.0
        val length = hypot(dx,dy)
        val offset = lanes.getValue(i)
        fun shift(p: CatchPoint) = CatchPoint(p.latitude + dx/length*offset/111195.0, p.longitude - dy/length*offset/scale)
        val from=shift(a.point); val to=shift(b.point)
        fun at(meters: Double): CatchPoint {
            val t=((meters-a.meters)/(b.meters-a.meters).coerceAtLeast(0.001)).coerceIn(0.0,1.0)
            return CatchPoint(from.latitude+(to.latitude-from.latitude)*t,from.longitude+(to.longitude-from.longitude)*t)
        }
        val previous = segments.lastOrNull()
        // Bridge the small lane jump so the line reads as one continuous stroke.
        if (previous != null && catchDistance(previous.to,from)>0.1) segments += CatchDrawSegment(previous.to,from,a.meters,a.meters)
        segments += CatchDrawSegment(from,to,a.meters,b.meters)
        val bearing = Math.toDegrees(atan2(dx,dy))
        while(nextArrow <= b.meters) {
            if(nextArrow >= a.meters) arrows += CatchDrawMarker(at(nextArrow), "", bearing, nextArrow)
            nextArrow += CATCH_ARROW_SPACING_METERS
        }
        while (pendingStops.isNotEmpty() && pendingStops.first() <= b.meters + 0.01) {
            val meters = pendingStops.removeAt(0)
            stops += CatchDrawMarker(at(meters), (stops.size + 1).toString(), bearing, meters)
        }
    }
    // Stops at or beyond the end (a zero-length path, or rounding) sit on the last point.
    pendingStops.forEach { stops += CatchDrawMarker(path.last().point, (stops.size + 1).toString(), 0.0, it) }
    val closed = catchDistance(path.first().point,path.last().point)<10
    val endpoints = if (closed) listOf(CatchDrawMarker(path.first().point, "Start / Finish"))
        else listOf(CatchDrawMarker(path.first().point, "Start"), CatchDrawMarker(path.last().point, "Finish", meters = path.last().meters))
    return CatchRouteDrawing(segments, arrows, stops, endpoints)
}

/** How much route one numbered stop covers: catches within this stretch share a number. */
internal const val CATCH_STOP_SPAN_METERS = 150.0

/**
 * The route's numbered stops: catches grouped by walking order into stretches of at most
 * [CATCH_STOP_SPAN_METERS]. Few enough to read on the map, and the same list the stop timeline shows.
 */
internal fun catchStops(encounters: List<CatchEncounter>): List<List<CatchEncounter>> {
    val stops = mutableListOf<MutableList<CatchEncounter>>()
    for (e in encounters.sortedBy { it.meters }) {
        val stop = stops.lastOrNull()
        if (stop == null || e.meters - stop.first().meters > CATCH_STOP_SPAN_METERS) stops += mutableListOf(e) else stop += e
    }
    return stops
}

/** The middle of each stop's stretch: where its numbered badge sits. */
internal fun catchStopMeters(encounters: List<CatchEncounter>): List<Double> = catchStops(encounters).map { (it.first().meters + it.last().meters) / 2 }

/** Route colour at [fraction] of the way, from blue at the start to violet at the finish. */
internal fun catchRouteColor(fraction: Double): Int {
    val t = fraction.coerceIn(0.0, 1.0)
    val from = intArrayOf(0x1E, 0x63, 0xF0)
    val to = intArrayOf(0x8B, 0x3F, 0xE0)
    val c = IntArray(3) { (from[it] + (to[it] - from[it]) * t).roundToInt() }
    return (0xFF shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2]
}
