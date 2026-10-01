package com.example.pokemonalertsv2.data.backup

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.pokemonalertsv2.data.ALERT_SYNC_ETAG_KEY
import com.example.pokemonalertsv2.data.ALERT_SYNC_REVISION_KEY
import com.example.pokemonalertsv2.data.LAST_PUSH_RECEIVED_KEY
import com.example.pokemonalertsv2.data.LAST_SUCCESSFUL_ALERT_SYNC_KEY
import com.example.pokemonalertsv2.data.SEEN_ALERTS_KEY
import com.example.pokemonalertsv2.data.SILENCE_UNTIL_KEY
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.godex.GoDexPreferences
import com.example.pokemonalertsv2.raidwatch.RaidWatchStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Export and import of the app's settings.
 *
 * The app keeps roughly forty preference keys plus several imported datasets, and a
 * reinstall currently loses all of it. Rather than enumerate the keys -- a list that would
 * silently fall behind every time one is added -- this walks the whole preference store and
 * excludes a small, explicit denylist. New settings are therefore backed up by default,
 * which is the safer direction to be wrong in.
 */
object SettingsBackup {

    const val FORMAT_VERSION = 1
    const val MIME_TYPE = "application/json"

    /**
     * Keys deliberately left out of the export.
     *
     * Two reasons. Session credentials: an export is a file the user may well email to
     * themselves or drop in cloud storage, and a scraped-site session cookie in plaintext
     * is not something to hand out with a settings backup. The Pokebattler bearer token is
     * not listed because it never enters this store at all -- it lives encrypted in its own
     * SharedPreferences (see [com.example.pokemonalertsv2.data.counters.PokebattlerAuth]).
     *
     * And per-device state, which describes one install rather than the user's choices:
     * restoring another device's alert-sync cursor would make this device skip changes it
     * never received, its push receipt time would report delivery that never happened here,
     * and a restored Raid Watch or silence would resurrect something that ended long ago.
     * The Poke Genie roster itself lives in the database and is not exported, so its import
     * summary would claim Pokemon the restored install does not have. The Pokebattler
     * trainer number belongs to a sign-in, and sign-ins are not part of a backup.
     *
     * Names come from the key constants themselves: these keys were once renamed and the
     * literal copies here silently stopped excluding anything.
     */
    val EXCLUDED_KEYS: Set<String> = setOf(
        GoDexPreferences.COOKIES_KEY,
        GoDexPreferences.SESSION_STATE_KEY,
        GoDexPreferences.WRITE_BACK_URL_KEY,
        ALERT_SYNC_REVISION_KEY,
        ALERT_SYNC_ETAG_KEY,
        LAST_SUCCESSFUL_ALERT_SYNC_KEY,
        LAST_PUSH_RECEIVED_KEY,
        SEEN_ALERTS_KEY,
        SILENCE_UNTIL_KEY,
        RaidWatchStore.KEY_ALERT_JSON,
        RaidWatchStore.KEY_STARTED_AT,
        RaidWatchStore.KEY_END_AT,
        RaidWatchStore.KEY_TEAM_JSON,
        RaidCounterPreferences.PG_COUNT_KEY,
        RaidCounterPreferences.PG_MATCHED_KEY,
        RaidCounterPreferences.PG_FILE_KEY,
        RaidCounterPreferences.PG_IMPORTED_AT_KEY,
        RaidCounterPreferences.PG_EXPANSION_KEY,
        RaidCounterPreferences.PB_TRAINER_KEY
    ).mapTo(mutableSetOf()) { it.name }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Serializable
    data class Backup(
        val version: Int = FORMAT_VERSION,
        val exportedAtMillis: Long,
        val appVersion: String? = null,
        val entries: Map<String, Entry>
    )

    /**
     * A preference value plus the type needed to put it back.
     *
     * DataStore keys are typed, so a bare JSON value is not enough to restore one: an
     * integer and a long look identical once serialized.
     */
    @Serializable
    data class Entry(
        val type: String,
        val value: String? = null,
        val values: List<String>? = null
    )

    fun export(
        preferences: Preferences,
        exportedAtMillis: Long,
        appVersion: String? = null
    ): String = json.encodeToString(
        Backup.serializer(),
        Backup(
            exportedAtMillis = exportedAtMillis,
            appVersion = appVersion,
            entries = preferences.asMap()
                .filterKeys { it.name !in EXCLUDED_KEYS }
                .mapNotNull { (key, value) -> encodeEntry(key.name, value) }
                .toMap()
        )
    )

    fun parse(text: String): Result<Backup> = runCatching {
        val backup = json.decodeFromString(Backup.serializer(), text)
        require(backup.version <= FORMAT_VERSION) {
            "This backup was written by a newer version of the app."
        }
        backup
    }

    /**
     * Applies [backup] onto [target].
     *
     * Excluded keys are re-checked here, not just on export: a hand-edited or older file
     * could carry a session cookie, and importing one would be exactly the leak the export
     * side is trying to avoid. Unrecognised types are skipped rather than failing the whole
     * import, so one bad entry cannot cost the user the other thirty-nine.
     */
    fun apply(backup: Backup, target: androidx.datastore.preferences.core.MutablePreferences): Int {
        var applied = 0
        backup.entries.forEach { (name, entry) ->
            if (name in EXCLUDED_KEYS) return@forEach
            if (decodeInto(target, name, entry)) applied++
        }
        return applied
    }

    private fun encodeEntry(name: String, value: Any?): Pair<String, Entry>? = when (value) {
        is Boolean -> name to Entry("boolean", value.toString())
        is Int -> name to Entry("int", value.toString())
        is Long -> name to Entry("long", value.toString())
        is Float -> name to Entry("float", value.toString())
        is Double -> name to Entry("double", value.toString())
        is String -> name to Entry("string", value)
        is Set<*> -> name to Entry(
            type = "stringSet",
            values = value.filterIsInstance<String>()
        )
        else -> null
    }

    private fun decodeInto(
        target: androidx.datastore.preferences.core.MutablePreferences,
        name: String,
        entry: Entry
    ): Boolean = runCatching {
        when (entry.type) {
            "boolean" -> target[booleanPreferencesKey(name)] = entry.value!!.toBooleanStrict()
            "int" -> target[intPreferencesKey(name)] = entry.value!!.toInt()
            "long" -> target[longPreferencesKey(name)] = entry.value!!.toLong()
            "float" -> target[floatPreferencesKey(name)] = entry.value!!.toFloat()
            "double" -> target[doublePreferencesKey(name)] = entry.value!!.toDouble()
            "string" -> target[stringPreferencesKey(name)] = entry.value!!
            "stringSet" -> target[stringSetPreferencesKey(name)] = entry.values!!.toSet()
            else -> return false
        }
        true
    }.getOrDefault(false)
}
