package com.example.pokemonalertsv2.catchroutes

import kotlin.math.*

/** Display geometry only. Never used by scoring, distance, or visit tracking. */
internal data class CatchDrawSegment(val from: CatchPoint, val to: CatchPoint, val stage: Int)
internal data class CatchDrawMarker(val point: CatchPoint, val label: String, val bearing: Double = 0.0)
internal data class CatchRouteDrawing(val segments: List<CatchDrawSegment>, val arrows: List<CatchDrawMarker>, val stages: List<CatchDrawMarker>)
internal fun catchRouteDrawing(path: List<CatchPathPosition>): CatchRouteDrawing {
    if (path.isEmpty()) return CatchRouteDrawing(emptyList(), emptyList(), emptyList())
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
        lanes[entry.index] = (lane - (group.size - 1) / 2.0) * 8.0 * if (key(a.point) < key(b.point)) 1 else -1
    }
    val segments = mutableListOf<CatchDrawSegment>()
    val arrows = mutableListOf<CatchDrawMarker>()
    val stages = mutableListOf(CatchDrawMarker(path.first().point, "Start"))
    var nextArrow = 35.0
    var nextStage = 180.0
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
        if (previous != null && catchDistance(previous.to,from)>0.1) {
            segments += CatchDrawSegment(previous.to,from,floor(a.meters/180).toInt())
            val pdx=(previous.to.longitude-previous.from.longitude)*scale
            val pdy=(previous.to.latitude-previous.from.latitude)*111195.0
            if ((pdx*dx+pdy*dy)/(hypot(pdx,pdy)*length).coerceAtLeast(0.001)< -0.95)
                stages += CatchDrawMarker(CatchPoint((previous.to.latitude+from.latitude)/2,(previous.to.longitude+from.longitude)/2),"Turn back")
        }
        segments += CatchDrawSegment(from,to,floor(a.meters/180).toInt())
        while(nextArrow <= b.meters) {
            if(nextArrow >= a.meters) arrows += CatchDrawMarker(at(nextArrow), "", Math.toDegrees(atan2(dx,dy)))
            nextArrow += 65
        }
        while(nextStage <= b.meters) {
            if(nextStage >= a.meters) stages += CatchDrawMarker(at(nextStage),(nextStage/180).toInt().toString())
            nextStage += 180
        }
    }
    val closed = catchDistance(path.first().point,path.last().point)<10
    if(closed) stages[0]=stages[0].copy(label="Start / Finish") else stages += CatchDrawMarker(path.last().point,"Finish")
    return CatchRouteDrawing(segments,arrows,stages)
}
