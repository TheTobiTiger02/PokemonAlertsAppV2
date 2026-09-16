package com.example.pokemonalertsv2.events

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The backend's event calendar (LeekDuck via ScrapedDuck, plus hand-entered events). */
interface EventsService {
    @GET("api/events") suspend fun events(): Response<GameEventsResponse>
}

@Serializable data class GameEventsResponse(val data: List<GameEvent> = emptyList())

@Serializable data class GameEvent(
    val id: String,
    val name: String,
    val eventType: String,
    val heading: String? = null,
    val link: String? = null,
    val image: String? = null,
    val startAt: String,
    val endAt: String,
    /** The feed gave a wall-clock time, which Pokémon GO runs at that clock time in every time zone. */
    val localTime: Boolean? = null,
    val hasSpawns: Boolean? = null,
    val spawnRelevant: Boolean = false,
    val featured: List<EventPokemon> = emptyList(),
    val bonuses: List<EventBonus> = emptyList(),
    val bonusDisclaimers: List<String> = emptyList(),
    val shinies: List<EventPokemon> = emptyList(),
    val raidBosses: List<EventPokemon> = emptyList(),
    val source: String? = null,
) {
    val startMillis: Long get() = runCatching { Instant.parse(startAt).toEpochMilli() }.getOrDefault(0L)
    val endMillis: Long get() = runCatching { Instant.parse(endAt).toEpochMilli() }.getOrDefault(0L)
}

@Serializable data class EventPokemon(val name: String, val image: String? = null, val canBeShiny: Boolean = false, val pokemonId: Int? = null)
@Serializable data class EventBonus(val text: String, val image: String? = null)

/** Battle League weeks and far-away Wild Area events are noise for most trainers; one chip away. */
val DEFAULT_HIDDEN_EVENT_TYPES: Set<String> = setOf("go-battle-league", "wild-area")

/** Types a new install reminds about. */
val DEFAULT_REMINDER_EVENT_TYPES: Set<String> = setOf("community-day", "pokemon-spotlight-hour")

const val DEFAULT_REMINDER_LEAD_MINUTES = 15
val REMINDER_LEAD_CHOICES = listOf(5, 15, 30, 60)

/** A readable name for an event type, singular, e.g. `pokemon-spotlight-hour` -> "Spotlight Hour". */
fun eventTypeName(type: String): String = when (type) {
    "pokemon-spotlight-hour" -> "Spotlight Hour"
    "community-day" -> "Community Day"
    "raid-hour" -> "Raid Hour"
    "raid-day" -> "Raid Day"
    "raid-battles" -> "Raid Battles"
    "max-battles" -> "Max Battles"
    "max-mondays" -> "Max Monday"
    "go-battle-league" -> "GO Battle League"
    "go-pass" -> "GO Pass"
    "wild-area" -> "Wild Area"
    "choose-your-path" -> "Choose Your Path"
    "event" -> "Event"
    "season" -> "Season"
    else -> type.split('-').joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}

/** The zone an event's clock times are shown in: Berlin for wall-clock feed times, the phone's otherwise. */
fun GameEvent.displayZone(deviceZone: ZoneId = ZoneId.systemDefault()): ZoneId =
    if (localTime == true) BERLIN else deviceZone

private val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

/** What the Events tab lists: running events (ending soonest first) and upcoming ones by day. */
data class EventSections(val now: List<GameEvent>, val upcoming: List<Pair<LocalDate, List<GameEvent>>>)

fun groupEvents(events: List<GameEvent>, nowMillis: Long, hiddenTypes: Set<String>, zone: ZoneId = ZoneId.systemDefault()): EventSections {
    val visible = events.filter { it.eventType !in hiddenTypes && it.endMillis > nowMillis && it.endMillis > it.startMillis }
    val running = visible.filter { it.startMillis <= nowMillis }.sortedWith(compareBy<GameEvent> { it.endMillis }.thenBy { it.name })
    val upcoming = visible.filter { it.startMillis > nowMillis }
        .sortedWith(compareBy<GameEvent> { it.startMillis }.thenBy { it.name })
        .groupBy { Instant.ofEpochMilli(it.startMillis).atZone(it.displayZone(zone)).toLocalDate() }
        .toList()
    return EventSections(running, upcoming)
}

/** "2 h 15 min" style duration, rounded down to minutes; "less than a minute" below one. */
fun durationLabel(millis: Long): String {
    val minutes = millis / 60_000
    if (minutes < 1) return "less than a minute"
    val days = minutes / (24 * 60)
    val hours = (minutes % (24 * 60)) / 60
    val rest = minutes % 60
    return when {
        days > 0 -> if (hours > 0) "$days d $hours h" else "$days d"
        hours > 0 -> if (rest > 0) "$hours h $rest min" else "$hours h"
        else -> "$rest min"
    }
}

/** Share of a running event already past, 0-1. */
fun GameEvent.progress(nowMillis: Long): Float =
    if (endMillis <= startMillis) 1f else ((nowMillis - startMillis).toFloat() / (endMillis - startMillis)).coerceIn(0f, 1f)

/** An upcoming reminder: which event, and when to notify. */
data class EventReminder(val event: GameEvent, val notifyAtMillis: Long)

/**
 * The reminders to schedule: events starting within [horizonMillis] whose type is reminded about or
 * that were starred, notified [leadMinutes] before start. An event already past its notify time but not
 * yet started still gets a reminder right away; started ones get none.
 */
fun eventsToRemind(
    events: List<GameEvent>,
    reminderTypes: Set<String>,
    starredIds: Set<String>,
    leadMinutes: Int,
    nowMillis: Long,
    horizonMillis: Long = 48 * 3_600_000L,
): List<EventReminder> = events
    .filter { it.eventType in reminderTypes || it.id in starredIds }
    .filter { it.startMillis > nowMillis && it.startMillis - nowMillis <= horizonMillis }
    .map { EventReminder(it, maxOf(nowMillis, it.startMillis - leadMinutes * 60_000L)) }
    .sortedBy { it.notifyAtMillis }
