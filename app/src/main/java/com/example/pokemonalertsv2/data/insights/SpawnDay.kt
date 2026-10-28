package com.example.pokemonalertsv2.data.insights

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One Berlin hour of a day: spawnpoints active in it and wild Pokémon first seen in it. */
@Serializable
data class SpawnHourSlot(
    /** Berlin clock hour; the autumn clock change repeats 2. */
    @SerialName("hour") val hour: Int,
    /** Epoch millis; what [SpawnHourResponse] is asked for by. */
    @SerialName("start") val start: Long,
    @SerialName("active") val active: Int = 0,
    @SerialName("live") val live: Int = 0,
    @SerialName("catalogueOnly") val catalogueOnly: Int = 0,
    @SerialName("sightings") val sightings: Int = 0
)

@Serializable
data class SpawnDayTotals(
    @SerialName("active") val active: Int = 0,
    @SerialName("live") val live: Int = 0,
    @SerialName("catalogueOnly") val catalogueOnly: Int = 0,
    @SerialName("sightings") val sightings: Int = 0
)

/** `GET api/spawnpoints/day`: one of the last 30 Berlin days hour by hour, today up to the current hour. */
@Serializable
data class SpawnDayResponse(
    @SerialName("day") val day: String,
    @SerialName("area") val area: String? = null,
    /** False while the day (or its last sightings) can still change. */
    @SerialName("complete") val complete: Boolean = false,
    @SerialName("hours") val hours: List<SpawnHourSlot> = emptyList(),
    @SerialName("totals") val totals: SpawnDayTotals = SpawnDayTotals()
)

@Serializable
data class SpawnHourPoint(
    @SerialName("id") val id: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("area") val area: String? = null,
    /** "live" when our scanner saw it, "catalogue" when only the catalogue did. */
    @SerialName("source") val source: String = "live"
)

/** `GET api/spawnpoints/hour`: the spawnpoints active in one hour and the species first seen in it, named. */
@Serializable
data class SpawnHourResponse(
    @SerialName("start") val start: Long,
    @SerialName("hour") val hour: Int = 0,
    @SerialName("sightings") val sightings: Int = 0,
    @SerialName("points") val points: List<SpawnHourPoint> = emptyList(),
    @SerialName("species") val species: List<SpawnSpeciesCount> = emptyList()
)

@Serializable
data class AlertHour(
    @SerialName("hour") val hour: Int,
    @SerialName("start") val start: Long,
    @SerialName("count") val count: Int = 0,
    @SerialName("byType") val byType: Map<String, Int> = emptyMap()
)

/** `GET api/alerts/hourly`: alerts received per Berlin hour and type, with the same hours as the spawn day. */
@Serializable
data class AlertHoursResponse(
    @SerialName("day") val day: String,
    @SerialName("total") val total: Int = 0,
    @SerialName("byType") val byType: Map<String, Int> = emptyMap(),
    @SerialName("hours") val hours: List<AlertHour> = emptyList()
)

/** A day's spawn hours and its alerts, loaded together when a day is opened. */
data class SpawnDayDetail(
    val spawns: SpawnDayResponse,
    /** Null when the alert counts could not be loaded; the spawn hours still show. */
    val alerts: AlertHoursResponse?
)

/**
 * Two-digit hour labels; the repeated hour of the autumn clock change gets a prime ("02′") so both hours
 * can be told apart.
 */
fun hourLabels(hours: List<Int>): List<String> {
    val seen = mutableSetOf<Int>()
    return hours.map { hour -> "%02d".format(hour) + if (seen.add(hour)) "" else "′" }
}

/** The hour a day opens on: the latest for today (still running), otherwise the busiest. */
fun defaultHour(day: SpawnDayResponse, isToday: Boolean): Long? =
    if (isToday) day.hours.lastOrNull()?.start else day.hours.maxByOrNull { it.active }?.start
