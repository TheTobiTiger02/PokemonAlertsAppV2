package com.example.pokemonalertsv2.data.counters

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The user's default counter options, plus the state of their Poke Genie import. */
data class RaidCounterSettings(
    val options: RaidCounterOptions = RaidCounterOptions(),
    val source: CounterSourceId = CounterSourceId.ALL_POKEMON,
    val ownedOnly: Boolean = false,
    val pokeGenieCount: Int = 0,
    val pokeGenieMatchedCount: Int = 0,
    val pokeGenieFileName: String? = null,
    val pokeGenieImportedAtMillis: Long = 0L
)

/**
 * Feature-scoped preferences over the app's existing DataStore.
 *
 * Follows the `GoDexPreferences` pattern rather than extending `AlertPreferencesStore`,
 * which would force every implementer of that interface (including the test fake) to grow
 * a dozen members for settings only this screen uses.
 */
class RaidCounterPreferences(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<RaidCounterSettings> = dataStore.data.map { prefs ->
        RaidCounterSettings(
            options = RaidCounterOptions(
                attackerLevel = prefs[LEVEL_KEY] ?: RaidCounterOptions.DEFAULT_ATTACKER_LEVEL,
                weather = prefs[WEATHER_KEY].toEnum(PokebattlerWeather.EXTREME),
                friendship = prefs[FRIENDSHIP_KEY].toEnum(PokebattlerFriendship.NONE),
                dodge = prefs[DODGE_KEY].toEnum(PokebattlerDodge.NONE),
                sort = prefs[SORT_KEY].toEnum(PokebattlerSort.OVERALL),
                attackStrategy = prefs[STRATEGY_KEY].toEnum(PokebattlerAttackStrategy.CINEMATIC),
                includeMegas = prefs[MEGAS_KEY] ?: true,
                includeShadow = prefs[SHADOW_KEY] ?: true,
                includeLegendary = prefs[LEGENDARY_KEY] ?: true
            ),
            source = prefs[SOURCE_KEY].toEnum(CounterSourceId.ALL_POKEMON),
            ownedOnly = prefs[OWNED_ONLY_KEY] ?: false,
            pokeGenieCount = prefs[PG_COUNT_KEY] ?: 0,
            pokeGenieMatchedCount = prefs[PG_MATCHED_KEY] ?: 0,
            pokeGenieFileName = prefs[PG_FILE_KEY],
            pokeGenieImportedAtMillis = prefs[PG_IMPORTED_AT_KEY] ?: 0L
        )
    }

    suspend fun updateDefaults(options: RaidCounterOptions) {
        dataStore.edit { prefs ->
            prefs[LEVEL_KEY] = options.attackerLevel
            prefs[WEATHER_KEY] = options.weather.name
            prefs[FRIENDSHIP_KEY] = options.friendship.name
            prefs[DODGE_KEY] = options.dodge.name
            prefs[SORT_KEY] = options.sort.name
            prefs[STRATEGY_KEY] = options.attackStrategy.name
            prefs[MEGAS_KEY] = options.includeMegas
            prefs[SHADOW_KEY] = options.includeShadow
            prefs[LEGENDARY_KEY] = options.includeLegendary
        }
    }

    suspend fun setSource(source: CounterSourceId) {
        dataStore.edit { it[SOURCE_KEY] = source.name }
    }

    suspend fun setOwnedOnly(ownedOnly: Boolean) {
        dataStore.edit { it[OWNED_ONLY_KEY] = ownedOnly }
    }

    suspend fun recordPokeGenieImport(
        fileName: String?,
        rowCount: Int,
        matchedCount: Int,
        timestamp: Long
    ) {
        dataStore.edit { prefs ->
            prefs[PG_COUNT_KEY] = rowCount
            prefs[PG_MATCHED_KEY] = matchedCount
            prefs[PG_IMPORTED_AT_KEY] = timestamp
            if (fileName != null) prefs[PG_FILE_KEY] = fileName else prefs.remove(PG_FILE_KEY)
        }
    }

    suspend fun clearPokeGenie() {
        dataStore.edit { prefs ->
            prefs.remove(PG_COUNT_KEY)
            prefs.remove(PG_MATCHED_KEY)
            prefs.remove(PG_IMPORTED_AT_KEY)
            prefs.remove(PG_FILE_KEY)
            prefs[OWNED_ONLY_KEY] = false
            // Nothing left to annotate with, so fall back to the generic ranking.
            prefs[SOURCE_KEY] = CounterSourceId.ALL_POKEMON.name
        }
    }

    /** Unknown or removed enum names fall back to the default instead of throwing. */
    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private companion object {
        val LEVEL_KEY = intPreferencesKey("raid_counters_attacker_level")
        val WEATHER_KEY = stringPreferencesKey("raid_counters_weather")
        val FRIENDSHIP_KEY = stringPreferencesKey("raid_counters_friendship")
        val DODGE_KEY = stringPreferencesKey("raid_counters_dodge")
        val SORT_KEY = stringPreferencesKey("raid_counters_sort")
        val STRATEGY_KEY = stringPreferencesKey("raid_counters_attack_strategy")
        val MEGAS_KEY = booleanPreferencesKey("raid_counters_include_megas")
        val SHADOW_KEY = booleanPreferencesKey("raid_counters_include_shadow")
        val LEGENDARY_KEY = booleanPreferencesKey("raid_counters_include_legendary")
        val SOURCE_KEY = stringPreferencesKey("raid_counters_source")
        val OWNED_ONLY_KEY = booleanPreferencesKey("raid_counters_owned_only")
        val PG_COUNT_KEY = intPreferencesKey("poke_genie_count")
        val PG_MATCHED_KEY = intPreferencesKey("poke_genie_matched_count")
        val PG_FILE_KEY = stringPreferencesKey("poke_genie_file_name")
        val PG_IMPORTED_AT_KEY = longPreferencesKey("poke_genie_imported_at")
    }
}
