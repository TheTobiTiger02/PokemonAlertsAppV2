package com.example.pokemonalertsv2.sync

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.pokemonalertsv2.catchroutes.CatchAreaEntity
import com.example.pokemonalertsv2.catchroutes.CatchHistoryEntity
import com.example.pokemonalertsv2.catchroutes.CatchRouteDao
import com.example.pokemonalertsv2.catchroutes.CatchSessionEntity
import com.example.pokemonalertsv2.catchroutes.CatchSetupEntity
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.database.PokeGenieDao
import com.example.pokemonalertsv2.data.database.PokeGenieMonEntity
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.hunt.HuntSummary
import com.example.pokemonalertsv2.hunt.SavedHunt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Per adapter: snapshot -> apply on an empty target -> snapshot is identical; device-local data never appears. */
class SyncAdaptersTest {
    @get:Rule
    val folder = TemporaryFolder()

    private suspend fun roundTrip(source: SyncAdapter, target: SyncAdapter): Map<String, String> {
        val snapshot = source.snapshot()
        target.apply(snapshot, emptySet())
        assertEquals(snapshot, target.snapshot())
        return snapshot
    }

    @Test
    fun settings() = runBlocking {
        val source = testDataStore(folder.root, "settings-a")
        source.edit {
            it[stringPreferencesKey("selected_area")] = "Alsbach"
            it[intPreferencesKey("max_distance")] = 1500
            it[longPreferencesKey("quiet_hours_start_minute")] = 1320L
            it[booleanPreferencesKey("quiet_hours_enabled")] = true
            it[floatPreferencesKey("some_float")] = 0.5f
            it[stringSetPreferencesKey("dismissed_alert_ids")] = setOf("a", "b")
            // Device-local or travelling elsewhere: never in the settings snapshot.
            it[stringPreferencesKey("godex_session_cookies")] = "cookie"
            it[longPreferencesKey("silence_until")] = 99L
            it[intPreferencesKey("floating_map_x")] = 12
            it[booleanPreferencesKey("onboarding_completed")] = true
            it[stringSetPreferencesKey("seen_alert_ids")] = setOf("s")
            it[intPreferencesKey("poke_genie_count")] = 3
        }
        // A real store always holds the unified filter document; apply would migrate one otherwise.
        com.example.pokemonalertsv2.data.AlertPreferences(source).updateFilterStateDocument { it }
        val targetStore = testDataStore(folder.root, "settings-b")
        targetStore.edit { it[intPreferencesKey("floating_map_x")] = 400 }
        val snapshot = roundTrip(SettingsSyncAdapter(source), SettingsSyncAdapter(targetStore))
        assertTrue("filters travel", "filter_state_v1" in snapshot)
        listOf("godex_session_cookies", "silence_until", "floating_map_x", "onboarding_completed", "seen_alert_ids", "poke_genie_count")
            .forEach { assertFalse("$it must not sync", it in snapshot) }
        val target = targetStore.data.first()
        assertEquals(1500, target[intPreferencesKey("max_distance")])
        assertEquals(1320L, target[longPreferencesKey("quiet_hours_start_minute")])
        assertEquals(setOf("a", "b"), target[stringSetPreferencesKey("dismissed_alert_ids")])
        assertEquals("the target's own window position stays", 400, target[intPreferencesKey("floating_map_x")])

        SettingsSyncAdapter(targetStore).apply(emptyMap(), setOf("selected_area", "floating_map_x"))
        assertNull(targetStore.data.first()[stringPreferencesKey("selected_area")])
        assertEquals("a local-only key is never deleted by sync", 400, targetStore.data.first()[intPreferencesKey("floating_map_x")])
    }

    @Test
    fun hunts() = runBlocking {
        val json = Json { encodeDefaults = true }
        val saved = SavedHunt(id = "h1", name = "Dragons", definition = FilterDefinition(), savedAtMillis = 10)
        val summary = HuntSummary("Walk", startedAtMillis = 1000, endedAtMillis = 2000, activeMillis = 900, distanceMeters = 1200.0, catches = emptyList())
        val source = testDataStore(folder.root, "hunts-a")
        source.edit {
            HuntRepository.applyHuntSync(
                it,
                mapOf(
                    "saved/h1" to json.encodeToString(SavedHunt.serializer(), saved),
                    "history/1000" to json.encodeToString(HuntSummary.serializer(), summary),
                    "pref/battery_saver" to "true"
                ),
                emptySet()
            )
            it[stringPreferencesKey("active_hunt")] = "{\"running\":true}"
            it[stringPreferencesKey("active_hunt_stats")] = "{}"
        }
        val target = testDataStore(folder.root, "hunts-b")
        fun adapter(store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) = FunctionSyncAdapter(
            DeviceSync.HUNTS,
            { HuntRepository.huntSyncSnapshot(store.data.first()) },
            { u, d -> store.edit { HuntRepository.applyHuntSync(it, u, d) } },
            emptyFlow()
        )
        val snapshot = roundTrip(adapter(source), adapter(target))
        assertEquals(setOf("saved/h1", "history/1000", "pref/battery_saver"), snapshot.keys)
        assertFalse(snapshot.keys.any { "active" in it })
        assertNull("the running hunt stays on its device", target.data.first()[stringPreferencesKey("active_hunt")])

        adapter(target).apply(emptyMap(), setOf("history/1000"))
        assertEquals(setOf("saved/h1", "pref/battery_saver"), adapter(target).snapshot().keys)
    }

    @Test
    fun routes() = runBlocking {
        val source = FakeCatchRouteDao().apply {
            save(CatchSetupEntity("s1", "Morning", "{}", 1))
            saveArea(CatchAreaEntity("a1", "Park", "[]", 2))
            addHistory(CatchHistoryEntity("r1", 3, "{\"id\":\"r1\"}"))
            writeSession(CatchSessionEntity(payload = "{\"running\":true}"))
        }
        val target = FakeCatchRouteDao()
        val snapshot = roundTrip(RoutesSyncAdapter(source), RoutesSyncAdapter(target))
        assertEquals(setOf("setup/s1", "area/a1", "history/r1"), snapshot.keys)
        assertNull("the active session never travels", target.session)

        RoutesSyncAdapter(target).apply(emptyMap(), setOf("area/a1"))
        assertTrue(target.areas.isEmpty())
    }

    @Test
    fun pokeGenie() = runBlocking {
        val sourceDao = FakePokeGenieDao().apply { insertAll(listOf(mon(1, "Mewtwo"), mon(2, "Rayquaza"))) }
        val sourceStore = testDataStore(folder.root, "pg-a")
        sourceStore.edit {
            it[RaidCounterPreferences.PG_COUNT_KEY] = 2
            it[RaidCounterPreferences.PG_FILE_KEY] = "export.csv"
            it[RaidCounterPreferences.PG_IMPORTED_AT_KEY] = 77L
        }
        val targetDao = FakePokeGenieDao()
        val targetStore = testDataStore(folder.root, "pg-b")
        roundTrip(PokeGenieSyncAdapter(sourceDao, sourceStore), PokeGenieSyncAdapter(targetDao, targetStore))
        assertEquals(listOf("Mewtwo", "Rayquaza"), targetDao.getAll().map { it.displayName })
        assertEquals(2, targetStore.data.first()[RaidCounterPreferences.PG_COUNT_KEY])

        PokeGenieSyncAdapter(targetDao, targetStore).apply(emptyMap(), setOf("roster", "summary"))
        assertTrue(targetDao.getAll().isEmpty())
        assertNull(targetStore.data.first()[RaidCounterPreferences.PG_COUNT_KEY])
    }

    @Test
    fun signIns() = runBlocking {
        var goDexA: GoDexSignIn? = GoDexSignIn("session=1", "https://godex.site/x", "AUTHENTICATED")
        var pbA: PokebattlerSignIn? = PokebattlerSignIn("jwt", "42", "Ash")
        var goDexB: GoDexSignIn? = null
        var pbB: PokebattlerSignIn? = null
        val a = SignInsSyncAdapter({ goDexA }, { goDexA = it }, { pbA }, { pbA = it }, emptyFlow())
        val b = SignInsSyncAdapter({ goDexB }, { goDexB = it }, { pbB }, { pbB = it }, emptyFlow())
        roundTrip(a, b)
        assertEquals(goDexA, goDexB)
        assertEquals(pbA, pbB)

        b.apply(emptyMap(), setOf("pokebattler"))
        assertNull("a sign-out elsewhere signs this device out", pbB)
        assertEquals(setOf("godex"), b.snapshot().keys)
    }

    @Test
    fun devices() = runBlocking {
        val stateA = SyncStateStore(testDataStore(folder.root, "dev-a"), PlainSealer)
        val stateB = SyncStateStore(testDataStore(folder.root, "dev-b"), PlainSealer)
        val rowA = DeviceRow("Pixel A", "Google Pixel A", "1.31.0", 5)
        var leaving = false
        val a = DevicesSyncAdapter("A", stateA) { if (leaving) null else rowA }
        val b = DevicesSyncAdapter("B", stateB) { DeviceRow("Pixel B", seenAtMillis = 6) }
        b.apply(a.snapshot(), emptySet())
        assertEquals(setOf("A", "B"), b.snapshot().keys)
        assertEquals(rowA, stateB.devices()["A"])
        // The own row is never taken from the group.
        a.apply(mapOf("A" to SyncMerge.json.encodeToString(DeviceRow.serializer(), DeviceRow("forged"))), emptySet())
        assertFalse("A" in stateA.devices())
        leaving = true
        assertTrue("leaving removes the own row", a.snapshot().isEmpty())
    }

    private fun mon(id: Long, name: String) = PokeGenieMonEntity(
        id = id, scanIndex = id.toInt(), displayName = name, form = null, pokedexNumber = 150, matchKey = name.uppercase(),
        altMatchKeys = "", cp = 4000, hp = 200, atkIv = 15, defIv = 15, staIv = 15, levelMin = 40.0, levelMax = 40.0,
        level = 40.0, quickMove = "Confusion", chargeMove = "Psystrike", chargeMove2 = null, gender = null,
        shadowState = "NONE", lucky = false, favorite = true, importedAt = 77
    )
}

private class FakeCatchRouteDao : CatchRouteDao {
    val setups = LinkedHashMap<String, CatchSetupEntity>()
    val areas = LinkedHashMap<String, CatchAreaEntity>()
    val history = LinkedHashMap<String, CatchHistoryEntity>()
    var session: CatchSessionEntity? = null
    private val tick = MutableStateFlow(0)

    override fun setups(): Flow<List<CatchSetupEntity>> = tick.map { setups.values.toList() }
    override suspend fun save(entity: CatchSetupEntity) { setups[entity.id] = entity; tick.value++ }
    override suspend fun delete(id: String) { setups.remove(id); tick.value++ }
    override fun session(): Flow<CatchSessionEntity?> = tick.map { session }
    override suspend fun currentSession(): CatchSessionEntity? = session
    override suspend fun writeSession(entity: CatchSessionEntity) { session = entity }
    override suspend fun clearSession() { session = null }
    override fun areas(): Flow<List<CatchAreaEntity>> = tick.map { areas.values.toList() }
    override suspend fun saveArea(entity: CatchAreaEntity) { areas[entity.id] = entity; tick.value++ }
    override suspend fun deleteArea(id: String) { areas.remove(id); tick.value++ }
    override fun history(): Flow<List<CatchHistoryEntity>> = tick.map { history.values.toList() }
    override suspend fun addHistory(entity: CatchHistoryEntity) { history[entity.id] = entity; tick.value++ }
    override suspend fun trimHistory(keep: Int) {
        history.values.sortedByDescending { it.endedAt }.drop(keep).forEach { history.remove(it.id) }
    }
    override suspend fun deleteHistory(id: String) { history.remove(id); tick.value++ }
    override suspend fun clearHistory() { history.clear(); tick.value++ }
    override suspend fun allSetups(): List<CatchSetupEntity> = setups.values.toList()
    override suspend fun allAreas(): List<CatchAreaEntity> = areas.values.toList()
    override suspend fun allHistory(): List<CatchHistoryEntity> = history.values.toList()
}

private class FakePokeGenieDao : PokeGenieDao {
    private val rows = MutableStateFlow<List<PokeGenieMonEntity>>(emptyList())
    override suspend fun getAll(): List<PokeGenieMonEntity> = rows.value
    override suspend fun count(): Int = rows.value.size
    override fun countFlow(): Flow<Int> = rows.map { it.size }
    override suspend fun insertAll(mons: List<PokeGenieMonEntity>) { rows.value = rows.value + mons }
    override suspend fun clear() { rows.value = emptyList() }
}
