package com.example.pokemonalertsv2.events

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The backend's event calendar (LeekDuck via ScrapedDuck, plus hand-entered events). */
interface EventsService {
    @GET("api/events") suspend fun events(): Response<GameEventsResponse>
    @GET("api/events/{id}") suspend fun event(@Path("id") id: String): Response<EventPage>
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
    /** The backend holds the event's LeekDuck page; [sectionKeys] name its sections (spawns, raids, research, …). */
    val hasPage: Boolean = false,
    val sectionKeys: List<String> = emptyList(),
) {
    val startMillis: Long get() = runCatching { Instant.parse(startAt).toEpochMilli() }.getOrDefault(0L)
    val endMillis: Long get() = runCatching { Instant.parse(endAt).toEpochMilli() }.getOrDefault(0L)
}

/** An event's LeekDuck page: its sections as LeekDuck shows them, each a list of typed blocks. */
@Serializable data class EventPage(val sections: List<EventSection> = emptyList(), val pageFetchedAt: String? = null)

@Serializable data class EventSection(val key: String, val title: String, val icon: String? = null, val blocks: List<EventBlock> = emptyList())

/**
 * One piece of a page section. [type] says which fields apply: `heading` ([level], [text]), `text`, `list`
 * ([items] as strings), `pokemon` ([items] as [EventPokemon]), `bonuses` ([items] as [EventBonus]), `research`
 * ([tasks]), `specialResearch` ([steps]), `moves` ([items] as [EventMove]) and `image` ([url]). Unknown types
 * are skipped by the screen, so the backend can add more without breaking older apps.
 */
@Serializable data class EventBlock(
    val type: String,
    val level: Int? = null,
    val text: String? = null,
    val url: String? = null,
    val items: List<JsonElement> = emptyList(),
    val tasks: List<EventTask> = emptyList(),
    val steps: List<EventResearchStep> = emptyList(),
) {
    fun strings(): List<String> = items.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    fun pokemon(): List<EventPokemon> = decodeItems()
    fun bonuses(): List<EventBonus> = decodeItems()
    fun moves(): List<EventMove> = decodeItems()
    private inline fun <reified T> decodeItems(): List<T> = items.mapNotNull { runCatching { blockJson.decodeFromJsonElement<T>(it) }.getOrNull() }
}

private val blockJson = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

@Serializable data class EventTask(val task: String, val rewards: List<EventReward> = emptyList())
@Serializable data class EventResearchStep(val number: Int? = null, val name: String? = null, val tasks: List<EventTask> = emptyList(),
    val rewards: List<EventReward> = emptyList())
/** A research reward: a Pokémon (with its CP range at encounter level) or an item with a quantity. */
@Serializable data class EventReward(val name: String? = null, val image: String? = null, val shiny: Boolean = false, val type: String? = null,
    val pokemonId: Int? = null, val minCp: Int? = null, val maxCp: Int? = null, val quantity: Int? = null)
@Serializable data class EventMove(val pokemon: String? = null, val move: String? = null, val category: String? = null, val type: String? = null)

/** Readable names for the section keys LeekDuck uses, for the small badges on event cards. */
fun sectionLabel(key: String): String = when (key) {
    "about" -> "About"
    "go-pass" -> "GO Pass"
    else -> key.split('-').joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}

/** Sections worth a badge on the card: the ones that say what to do or catch. */
fun cardSectionBadges(keys: List<String>): List<String> =
    keys.filter { it in setOf("spawns", "raids", "eggs", "research", "bonuses", "moves") }.map(::sectionLabel)

@Serializable data class EventPokemon(val name: String, val image: String? = null, val canBeShiny: Boolean = false, val pokemonId: Int? = null,
    /** On page lists LeekDuck says `shiny` and the Pokémon's type; the feed says `canBeShiny`. */
    val shiny: Boolean = false, val type: String? = null) {
    val shinyAvailable: Boolean get() = canBeShiny || shiny
}
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
