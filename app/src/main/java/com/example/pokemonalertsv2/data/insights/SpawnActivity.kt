package com.example.pokemonalertsv2.data.insights

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** One Berlin day of `GET api/spawnpoints/activity-days`. */
@Serializable
data class SpawnActivityDay(
    @SerialName("day") val day: String,
    @SerialName("active") val active: Int = 0,
    @SerialName("live") val live: Int = 0,
    @SerialName("catalogueOnly") val catalogueOnly: Int = 0,
    @SerialName("idle") val idle: Int = 0,
    @SerialName("unscanned") val unscanned: Int = 0
)

/** An hour range the server flagged as an unexplained spawn surge. */
@Serializable
data class SpawnSurge(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String = "Unexplained spawn surge",
    @SerialName("start") val start: String,
    @SerialName("end") val end: String
)

/** Active spawnpoints per Berlin day, oldest first, today last (and still running). */
@Serializable
data class SpawnActivityDaysResponse(
    @SerialName("days") val days: List<String> = emptyList(),
    @SerialName("area") val area: String? = null,
    @SerialName("areas") val areas: List<String> = emptyList(),
    @SerialName("total") val total: Int = 0,
    @SerialName("summary") val summary: List<SpawnActivityDay> = emptyList(),
    @SerialName("surges") val surges: List<SpawnSurge> = emptyList()
)

@Serializable
data class SpawnSpeciesCount(
    @SerialName("pokemonId") val pokemonId: Int,
    @SerialName("formId") val formId: Int? = null,
    @SerialName("count") val count: Int = 0,
    @SerialName("name") val name: String? = null,
    @SerialName("thumbnailUrl") val thumbnailUrl: String? = null,
    /** Place in the full ranking for the range, 1 = most seen. */
    @SerialName("rank") val rank: Int? = null,
    /** Count per day, aligned with the response's `days`; only sent for search results. */
    @SerialName("daily") val daily: List<Int> = emptyList()
)

@Serializable
data class SpawnDayTotal(
    @SerialName("day") val day: String,
    @SerialName("total") val total: Int = 0
)

@Serializable
data class SpawnSpeciesTotals(
    @SerialName("sightings") val sightings: Int = 0,
    @SerialName("species") val species: Int = 0
)

/** Every wild Pokémon the live scanner saw over recent days, counted per species (`GET api/spawnpoints/species`). */
@Serializable
data class SpawnSpeciesResponse(
    @SerialName("days") val days: List<String> = emptyList(),
    @SerialName("area") val area: String? = null,
    @SerialName("areas") val areas: List<String> = emptyList(),
    @SerialName("perDay") val perDay: List<SpawnDayTotal> = emptyList(),
    @SerialName("totals") val totals: SpawnSpeciesTotals = SpawnSpeciesTotals(),
    @SerialName("species") val species: List<SpawnSpeciesCount> = emptyList(),
    /** The search this answers, or null for the plain ranking. */
    @SerialName("query") val query: String? = null
)

/** Both halves of the Spawns tab, fetched together for one range and area. */
data class SpawnActivity(
    val days: SpawnActivityDaysResponse,
    val species: SpawnSpeciesResponse
)

/**
 * What the daily counts say at a glance. Today is still running, so "usual" is the median of the
 * finished days only, and today is never flagged for being low.
 */
data class SpawnActivitySummary(
    val today: Int?,
    val usual: Int?,
    val busiest: Int?,
    /** Indices into the summary that are more than [UNUSUAL_SHARE] off usual, or hit by a surge. */
    val flagged: Set<Int>,
    /** Finished days without a single live sighting: the scanner was not running, so they say nothing. */
    val unscanned: Int = 0
)

const val UNUSUAL_SHARE = 0.5

fun summarizeSpawnActivity(
    response: SpawnActivityDaysResponse,
    zone: ZoneId = ZoneId.of("Europe/Berlin")
): SpawnActivitySummary {
    val summary = response.summary
    // A day with no live sighting at all is one the scanner did not run, not a quiet day.
    val scanned = summary.dropLast(1).filter { it.live > 0 }
    val finished = scanned.map { it.active }
    val usual = median(finished)
    val surgeDays = response.surges.flatMap { surgeDays(it, zone) }.toSet()
    val flagged = summary.indices.filter { index ->
        val day = summary[index]
        val isToday = index == summary.lastIndex
        val unusual = !isToday && day.live > 0 && usual != null && usual > 0 && abs(day.active - usual) > usual * UNUSUAL_SHARE
        unusual || day.day in surgeDays
    }.toSet()
    return SpawnActivitySummary(
        today = summary.lastOrNull()?.active,
        usual = usual,
        busiest = finished.maxOrNull(),
        flagged = flagged,
        unscanned = summary.size - 1 - scanned.size
    )
}

/** Berlin dates (yyyy-MM-dd) a surge touches. */
private fun surgeDays(surge: SpawnSurge, zone: ZoneId): List<String> {
    val start = runCatching { Instant.parse(surge.start) }.getOrNull() ?: return emptyList()
    val end = runCatching { Instant.parse(surge.end) }.getOrNull() ?: return emptyList()
    val days = mutableListOf<String>()
    var hour = start
    while (hour.isBefore(end)) {
        days += hour.atZone(zone).toLocalDate().toString()
        hour = hour.plus(1, ChronoUnit.HOURS)
    }
    return days.distinct()
}

private fun median(values: List<Int>): Int? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else Math.round((sorted[mid - 1] + sorted[mid]) / 2.0).toInt()
}
