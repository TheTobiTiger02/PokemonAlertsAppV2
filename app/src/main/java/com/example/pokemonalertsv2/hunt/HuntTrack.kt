package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.catchroutes.CatchPoint
import kotlin.math.roundToLong

/**
 * A hunt's walked path, stored as an encoded polyline (the format map services use: 1e-5 degree
 * precision, about a metre, delta-encoded). A point costs 4-8 characters instead of ~45 as JSON, so
 * a two-hour walk is a few kilobytes and the DataStore write on every fix stays small. Nothing is
 * sent anywhere; the map tiles behind it are only fetched when a hunt's summary is opened.
 */
internal object HuntTrack {
    /** Points closer than this to the last kept one are skipped. */
    const val MIN_SPACING_METERS = 8.0
    /** Past this many points, every other one is dropped, so very long hunts keep their shape at half the detail. */
    const val MAX_POINTS = 2_500

    fun encode(points: List<CatchPoint>): String {
        val out = StringBuilder(points.size * 6)
        var lastLat = 0L
        var lastLon = 0L
        for (p in points) {
            val lat = (p.latitude * 1e5).roundToLong()
            val lon = (p.longitude * 1e5).roundToLong()
            encodeValue(lat - lastLat, out)
            encodeValue(lon - lastLon, out)
            lastLat = lat
            lastLon = lon
        }
        return out.toString()
    }

    fun decode(encoded: String): List<CatchPoint> {
        val points = ArrayList<CatchPoint>(encoded.length / 6)
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val dLat = decodeValue(encoded, index) ?: break
            index = dLat.second
            val dLon = decodeValue(encoded, index) ?: break
            index = dLon.second
            lat += dLat.first
            lon += dLon.first
            points += CatchPoint(lat / 1e5, lon / 1e5)
        }
        return points
    }

    /** [encoded] with [point] added, unless it is within [MIN_SPACING_METERS] of the last point. */
    fun append(encoded: String, point: CatchPoint): String {
        if (!point.valid) return encoded
        val points = decode(encoded)
        val last = points.lastOrNull()
        if (last != null && huntPathDistance(last, point) < MIN_SPACING_METERS) return encoded
        val next = points + point
        val kept = if (next.size > MAX_POINTS) next.filterIndexed { i, _ -> i % 2 == 0 || i == next.lastIndex } else next
        return encode(kept)
    }

    private fun encodeValue(value: Long, out: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            out.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        out.append((v + 63).toInt().toChar())
    }

    private fun decodeValue(encoded: String, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var index = start
        while (true) {
            if (index >= encoded.length) return null
            val b = encoded[index++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to index
    }
}

internal fun huntPathDistance(a: CatchPoint, b: CatchPoint): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = kotlin.math.sin(dLat / 2).let { it * it } +
        kotlin.math.cos(Math.toRadians(a.latitude)) * kotlin.math.cos(Math.toRadians(b.latitude)) *
        kotlin.math.sin(dLon / 2).let { it * it }
    return earthRadius * 2 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
}
