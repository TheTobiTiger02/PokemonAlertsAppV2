package com.example.pokemonalertsv2.events

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.pokemonalertsv2.catchroutes.catchBody
import com.example.pokemonalertsv2.data.PokemonAlertsApi
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The event calendar with an on-disk copy of the last good answer, so the tab and reminders still have
 * something to show without a connection.
 */
class EventsRepository(context: Context, private val service: EventsService = PokemonAlertsApi.eventsService) {
    private val cacheFile = File(context.applicationContext.filesDir, "events.json")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val pageDir = File(context.applicationContext.filesDir, "event-pages")
    private val pages = java.util.concurrent.ConcurrentHashMap<String, EventPage>()

    /** The event's LeekDuck page: fresh when the backend answers, else the copy kept from last time, else null. */
    suspend fun page(eventId: String): EventPage? {
        val file = File(pageDir, "${eventId.hashCode().toUInt()}.json")
        return try {
            val response = service.event(eventId)
            val page = response.catchBody()
            pages[eventId] = page
            withContext(Dispatchers.IO) { runCatching { pageDir.mkdirs(); file.writeText(json.encodeToString(EventPage.serializer(), page)) } }
            page
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            pages[eventId] ?: withContext(Dispatchers.IO) { runCatching { json.decodeFromString<EventPage>(file.readText()) }.getOrNull() }
        }
    }

    /** A page already in memory, so reopening an event shows it at once. */
    fun remembered(eventId: String): EventPage? = pages[eventId]

    suspend fun cached(): List<GameEvent> = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<GameEventsResponse>(cacheFile.readText()).data }.getOrDefault(emptyList())
    }

    /** Fresh events, stored for later; throws when the backend cannot answer. */
    suspend fun refresh(): List<GameEvent> {
        val response = service.events()
        if (response.code() == 404) throw IllegalStateException("The backend does not serve events yet. Deploy the latest backend.")
        val body = response.catchBody()
        withContext(Dispatchers.IO) {
            runCatching { cacheFile.writeText(json.encodeToString(GameEventsResponse.serializer(), body)) }
        }
        return body.data
    }
}

/** What the trainer chose in the Events tab: hidden types, reminder types, starred events and lead time. */
data class EventSettings(
    val hiddenTypes: Set<String> = DEFAULT_HIDDEN_EVENT_TYPES,
    val reminderTypes: Set<String> = DEFAULT_REMINDER_EVENT_TYPES,
    val starredIds: Set<String> = emptySet(),
    val leadMinutes: Int = DEFAULT_REMINDER_LEAD_MINUTES,
)

class EventPreferences(context: Context) {
    private val dataStore = context.applicationContext.alertPreferencesDataStore

    // An absent key means "never changed" and gives the default; an empty set is a real choice.
    val settings: Flow<EventSettings> = dataStore.data.map { prefs ->
        EventSettings(
            hiddenTypes = prefs[HIDDEN_TYPES] ?: DEFAULT_HIDDEN_EVENT_TYPES,
            reminderTypes = prefs[REMINDER_TYPES] ?: DEFAULT_REMINDER_EVENT_TYPES,
            starredIds = prefs[STARRED] ?: emptySet(),
            leadMinutes = prefs[LEAD_MINUTES] ?: DEFAULT_REMINDER_LEAD_MINUTES,
        )
    }

    suspend fun current(): EventSettings = settings.first()

    suspend fun toggleHidden(type: String) = dataStore.edit { prefs ->
        val current = prefs[HIDDEN_TYPES] ?: DEFAULT_HIDDEN_EVENT_TYPES
        prefs[HIDDEN_TYPES] = if (type in current) current - type else current + type
    }

    suspend fun toggleReminderType(type: String) = dataStore.edit { prefs ->
        val current = prefs[REMINDER_TYPES] ?: DEFAULT_REMINDER_EVENT_TYPES
        prefs[REMINDER_TYPES] = if (type in current) current - type else current + type
    }

    suspend fun toggleStar(eventId: String) = dataStore.edit { prefs ->
        val current = prefs[STARRED] ?: emptySet()
        prefs[STARRED] = if (eventId in current) current - eventId else current + eventId
    }

    suspend fun setLeadMinutes(minutes: Int) = dataStore.edit { it[LEAD_MINUTES] = minutes }

    private companion object {
        val HIDDEN_TYPES = stringSetPreferencesKey("events_hidden_types")
        val REMINDER_TYPES = stringSetPreferencesKey("events_reminder_types")
        val STARRED = stringSetPreferencesKey("events_starred_ids")
        val LEAD_MINUTES = intPreferencesKey("events_reminder_lead_minutes")
    }
}
