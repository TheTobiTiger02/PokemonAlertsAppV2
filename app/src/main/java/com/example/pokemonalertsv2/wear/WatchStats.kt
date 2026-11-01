package com.example.pokemonalertsv2.wear

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * What a connected watch measured during one hunt or catch route: heart rate, steps and calories.
 * The watch sends cumulative steps and calories for its own exercise, so the latest reading is the
 * session total; heart rate is summed for the average and bucketed per minute for the chart.
 */
@Serializable
data class WatchStats(
    val heartRateSum: Long = 0L,
    val heartRateCount: Int = 0,
    val minHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val steps: Long = 0L,
    val calories: Double = 0.0,
    /** Average heart rate per minute since the first sample; 0 where no sample arrived. */
    val heartRatePerMinute: List<Int> = emptyList(),
    val firstSampleMillis: Long? = null,
    val lastSampleMillis: Long = 0L,
    /** The watch's name, as the phone sees it, for the summary. */
    val deviceName: String? = null,
) {
    val averageHeartRate: Int? get() = if (heartRateCount == 0) null else (heartRateSum.toDouble() / heartRateCount).roundToInt()
    val hasData: Boolean get() = heartRateCount > 0 || steps > 0 || calories > 0.0

    fun withSample(sample: WatchSample, deviceName: String? = this.deviceName): WatchStats {
        val first = firstSampleMillis ?: sample.atMillis
        var next = copy(
            steps = maxOf(steps, sample.stepsTotal),
            calories = maxOf(calories, sample.caloriesTotal),
            firstSampleMillis = first,
            lastSampleMillis = maxOf(lastSampleMillis, sample.atMillis),
            deviceName = deviceName ?: this.deviceName,
        )
        val hr = sample.heartRate
        if (hr != null && hr in 25..250) {
            val minute = ((sample.atMillis - first) / 60_000L).toInt().coerceIn(0, MAX_MINUTES - 1)
            val perMinute = heartRatePerMinute.toMutableList()
            while (perMinute.size <= minute) perMinute += 0
            // A running average within the minute; the first sample of a minute just takes the slot.
            perMinute[minute] = if (perMinute[minute] == 0) hr else (perMinute[minute] + hr) / 2
            next = next.copy(
                heartRateSum = heartRateSum + hr,
                heartRateCount = heartRateCount + 1,
                minHeartRate = if (heartRateCount == 0) hr else minOf(minHeartRate, hr),
                maxHeartRate = maxOf(maxHeartRate, hr),
                heartRatePerMinute = perMinute,
            )
        }
        return next
    }

    companion object {
        /** Ten hours of per-minute heart rate, a few kilobytes at most. */
        const val MAX_MINUTES = 600
    }
}

/** One reading from the watch. Steps and calories are totals since the watch's exercise started. */
@Serializable
data class WatchSample(
    val atMillis: Long,
    val heartRate: Int? = null,
    val stepsTotal: Long = 0L,
    val caloriesTotal: Double = 0.0,
)
