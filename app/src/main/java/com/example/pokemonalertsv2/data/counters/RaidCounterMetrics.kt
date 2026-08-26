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

/**
 * True when a smaller number is the better counter.
 *
 * The estimator ("how many trainers") and the time to win are costs; overall, power and total
 * damage are scores. Every list-relative comparison has to know which way the metric runs.
 */
fun CounterMetric.isLowerBetter(): Boolean =
    this == CounterMetric.ESTIMATOR || this == CounterMetric.TIME

/**
 * How strong [value] is against [best], as 0f..1f.
 *
 * Deliberately a ratio to the best row rather than a position between best and worst: "this
 * counter is 78% as good as the top one" survives filtering the list, whereas a
 * worst-to-best span would rescale every bar whenever the tail changes.
 */
fun CounterMetric.strengthRatio(value: Double?, best: Double?): Float {
    if (value == null || best == null || value <= 0.0 || best <= 0.0) return 0f
    val ratio = if (isLowerBetter()) best / value else value / best
    return ratio.toFloat().coerceIn(0f, 1f)
}

/** The winning value for [metric] across [values], honouring the metric's direction. */
fun CounterMetric.bestOf(values: Iterable<Double?>): Double? {
    val present = values.filterNotNull().filter { it > 0.0 }
    if (present.isEmpty()) return null
    return if (isLowerBetter()) present.min() else present.max()
}
