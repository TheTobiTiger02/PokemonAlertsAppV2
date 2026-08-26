package com.example.pokemonalertsv2.data.counters

import androidx.compose.runtime.Immutable
import java.util.Locale

/** The metric the counter list is currently ordered by. */
enum class CounterMetric(val label: String) {
    OVERALL("Overall"),
    ESTIMATOR("Estimator"),
    TIME("Fastest"),
    POWER("Power"),
    TDO("Total damage")
}

/** User-facing, normalized counter metrics. */
@Immutable
data class CounterMetrics(
    val estimator: Double? = null,
    val overallPercent: Double? = null,
    val powerPercent: Double? = null,
    val tdo: Double? = null,
    val deaths: Double? = null,
    val timeToWinSeconds: Double? = null
) {
    fun valueFor(metric: CounterMetric): Double? = when (metric) {
        CounterMetric.OVERALL -> overallPercent
        CounterMetric.ESTIMATOR -> estimator
        CounterMetric.TIME -> timeToWinSeconds
        CounterMetric.POWER -> powerPercent
        CounterMetric.TDO -> tdo
    }

    fun headline(metric: CounterMetric): String = when (metric) {
        CounterMetric.OVERALL -> overallPercent?.let { "%.0f%% overall".format(Locale.US, it) }
        CounterMetric.ESTIMATOR -> estimator?.let { "%.2f trainers".format(Locale.US, it) }
        CounterMetric.TIME -> timeToWinSeconds?.let { "%.1fs".format(Locale.US, it) }
        CounterMetric.POWER -> powerPercent?.let { "%.0f%% power".format(Locale.US, it) }
        CounterMetric.TDO -> tdo?.let { "%.0f damage".format(Locale.US, it) }
    }.orEmpty()
}

/** Converts a raw Pokebattler cost into the percentage shown on the website. */
internal fun reciprocalPercent(rawCost: Double?): Double? =
    rawCost?.takeIf { it > 0.0 }?.let { 100.0 / it }

