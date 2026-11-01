package com.example.pokemonalertsv2.health

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Heart rate, steps and calories recorded during one hunt or catch route, read from Health
 * Connect after the fact (a Galaxy Watch syncs to Samsung Health, which shares with Health
 * Connect). Kept with the summary so it is read once and then shown offline.
 */
@Serializable
data class WatchStats(
    val heartRateSum: Long = 0L,
    val heartRateCount: Int = 0,
    val minHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val steps: Long = 0L,
    val calories: Double = 0.0,
    /** Average heart rate per minute of the session; 0 where the watch recorded nothing. */
    val heartRatePerMinute: List<Int> = emptyList(),
    val firstSampleMillis: Long? = null,
    val lastSampleMillis: Long = 0L,
    /** Where the data came from, e.g. "Galaxy Watch6" or "Samsung Health". */
    val deviceName: String? = null,
    /** When Health Connect was last read for this session; see [needsHealthRead]. */
    val fetchedAtMillis: Long = 0L,
) {
    val averageHeartRate: Int? get() = if (heartRateCount == 0) null else (heartRateSum.toDouble() / heartRateCount).roundToInt()
    val hasData: Boolean get() = heartRateCount > 0 || steps > 0 || calories > 0.0

    companion object {
        /** Ten hours of per-minute heart rate, a few kilobytes at most. */
        const val MAX_MINUTES = 600

        /**
         * Builds the stats for a session from [startMillis] to [endMillis] out of heart-rate
         * samples (time, bpm) and totals. Samples outside the window or implausible are ignored.
         * Null when there is nothing to show, so the summary can say the watch has not synced yet.
         */
        fun fromSamples(
            startMillis: Long,
            endMillis: Long,
            heartRates: List<Pair<Long, Int>>,
            steps: Long,
            calories: Double,
            deviceName: String?,
            fetchedAtMillis: Long,
        ): WatchStats? {
            val inWindow = heartRates
                .filter { (at, bpm) -> at in startMillis..endMillis && bpm in 25..250 }
                .sortedBy { it.first }
            if (inWindow.isEmpty() && steps <= 0 && calories <= 0.0) return null
            val minutes = (((endMillis - startMillis) / 60_000L).toInt() + 1).coerceIn(1, MAX_MINUTES)
            val sums = LongArray(minutes)
            val counts = IntArray(minutes)
            inWindow.forEach { (at, bpm) ->
                val minute = ((at - startMillis) / 60_000L).toInt().coerceIn(0, minutes - 1)
                sums[minute] += bpm.toLong()
                counts[minute]++
            }
            val perMinute = List(minutes) { i -> if (counts[i] == 0) 0 else (sums[i].toDouble() / counts[i]).roundToInt() }
                .dropLastWhile { it == 0 }
            return WatchStats(
                heartRateSum = inWindow.sumOf { it.second.toLong() },
                heartRateCount = inWindow.size,
                minHeartRate = inWindow.minOfOrNull { it.second } ?: 0,
                maxHeartRate = inWindow.maxOfOrNull { it.second } ?: 0,
                steps = steps.coerceAtLeast(0L),
                calories = calories.coerceAtLeast(0.0),
                heartRatePerMinute = if (perMinute.count { it > 0 } >= 2) perMinute else emptyList(),
                firstSampleMillis = inWindow.firstOrNull()?.first,
                lastSampleMillis = inWindow.lastOrNull()?.first ?: 0L,
                deviceName = deviceName,
                fetchedAtMillis = fetchedAtMillis,
            )
        }
    }
}

/** How long after a session a watch may still be syncing it, so an earlier read is not final. */
internal const val HEALTH_SYNC_SETTLE_MILLIS = 2 * 60 * 60_000L
/** Sessions older than this are not read again; Health Connect only keeps 30 days readable by default. */
internal const val HEALTH_READ_MAX_AGE_MILLIS = 7 * 24 * 60 * 60_000L

/**
 * Whether a session that ended at [endedAtMillis] should be read from Health Connect now: it is
 * recent, and either nothing has been found yet or the last read came before the watch had
 * surely synced.
 */
internal fun needsHealthRead(endedAtMillis: Long, stats: WatchStats?, nowMillis: Long): Boolean {
    if (endedAtMillis <= 0L || nowMillis - endedAtMillis > HEALTH_READ_MAX_AGE_MILLIS) return false
    return stats == null || stats.fetchedAtMillis < endedAtMillis + HEALTH_SYNC_SETTLE_MILLIS
}
