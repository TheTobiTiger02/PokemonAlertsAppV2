package com.example.pokemonalertsv2.data.insights

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.TimeUtils
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What one insights query read back, and whether it saw everything. */
data class InsightsHistory(
    val alerts: List<PokemonAlert> = emptyList(),
    /** True when the row cap stopped the read before the server ran out. */
    val hitCap: Boolean = false
)

data class AreaCount(val area: String, val count: Int)

/**
 * When and where something actually turned up, over a window of history.
 *
 * Reports observations, and nothing more — it does not predict the next spawn.
 * A busy hour here means "this is when they have been appearing", which is the
 * question worth answering before deciding where to walk.
 */
data class SpawnInsights(
    val totalSightings: Int = 0,
    val daysCovered: Int = 0,
    val perDayAverage: Float = 0f,
    /** 24 buckets, in the reader's own time zone rather than the server's. */
    val byHour: List<Int> = List(24) { 0 },
    /** 7 buckets, Monday first. */
    val byWeekday: List<Int> = List(7) { 0 },
    val byArea: List<AreaCount> = emptyList(),
    val hundoCount: Int = 0,
    val truncated: Boolean = false
) {
    val busiestHour: Int? get() = byHour
        .withIndex()
        .filter { it.value > 0 }
        .maxByOrNull { it.value }
        ?.index

    val topArea: AreaCount? get() = byArea.firstOrNull()

    val isEmpty: Boolean get() = totalSightings == 0
}

/**
 * Buckets [history] by local hour, weekday and area.
 *
 * Timestamp preference is `createdAt` — when the alert was posted, which is when
 * the thing appeared. `endTime` is the fallback and is deliberately second: it
 * is the despawn, roughly half an hour later, which would smear an 18:00 spawn
 * into the 18:30 bucket.
 */
fun buildSpawnInsights(
    history: InsightsHistory,
    zone: ZoneId = ZoneId.systemDefault()
): SpawnInsights {
    val byHour = IntArray(24)
    val byWeekday = IntArray(7)
    val byArea = mutableMapOf<String, Int>()
    val days = mutableSetOf<LocalDate>()
    var counted = 0
    var hundos = 0

    history.alerts.forEach { alert ->
        val millis = TimeUtils.parseEndTimeToMillis(alert.createdAt)
            ?: TimeUtils.parseEndTimeToMillis(alert.endTime)
            // A row whose time will not parse is dropped rather than guessed at:
            // a wrong bucket is worse than a slightly smaller sample.
            ?: return@forEach

        val moment = Instant.ofEpochMilli(millis).atZone(zone)
        byHour[moment.hour]++
        byWeekday[moment.dayOfWeek.toIndex()]++
        days += moment.toLocalDate()
        counted++
        if (alert.isPerfect) hundos++
        alert.area?.trim()?.takeIf { it.isNotEmpty() }?.let { area ->
            byArea[area] = (byArea[area] ?: 0) + 1
        }
    }

    val daysCovered = days.size
    return SpawnInsights(
        totalSightings = counted,
        daysCovered = daysCovered,
        perDayAverage = if (daysCovered == 0) 0f else counted.toFloat() / daysCovered,
        byHour = byHour.toList(),
        byWeekday = byWeekday.toList(),
        byArea = byArea.entries
            .map { AreaCount(it.key, it.value) }
            // Count first, then name, so equal areas do not swap between reads.
            .sortedWith(compareByDescending<AreaCount> { it.count }.thenBy { it.area }),
        hundoCount = hundos,
        truncated = history.hitCap
    )
}

/** Monday-first, matching how the week reads in the UI. */
private fun DayOfWeek.toIndex(): Int = ordinal
