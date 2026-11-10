package com.example.pokemonalertsv2.util

/**
 * How long to stay away from a routing endpoint after [consecutiveFailures]
 * failures in a row: [baseMillis] doubled per further failure (at most
 * [maxDoublings] times), never more than [maxMillis].
 */
internal fun exponentialBackoffMillis(
    consecutiveFailures: Int,
    baseMillis: Long,
    maxMillis: Long,
    maxDoublings: Int = 5,
): Long {
    if (consecutiveFailures <= 0) return 0L
    val doublings = (consecutiveFailures - 1).coerceAtMost(maxDoublings)
    return (baseMillis shl doublings).coerceAtMost(maxMillis)
}
