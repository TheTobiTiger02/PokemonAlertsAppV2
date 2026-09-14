package com.example.pokemonalertsv2.data.counters

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PartyPowerTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun verifiedProviderToggleAndBothCacheIdentities() {
        val off = RaidCounterOptions()
        val on = off.copy(partyPower = true)
        assertFalse(off.partyPower)
        assertEquals("1", PokebattlerUrls.queryParams(off)["numParty"])
        assertEquals("2", PokebattlerUrls.queryParams(on)["numParty"])
        for (attacker in listOf(AttackerSpec.Level(40), AttackerSpec.ExactLevel(37.5), AttackerSpec.PokebattlerUser("test"))) {
            assertNotEquals(raidCounterCacheKey("MEWTWO", "RAID_LEVEL_5", attacker, off), raidCounterCacheKey("MEWTWO", "RAID_LEVEL_5", attacker, on))
            assertNotEquals(personalResponseCacheKey("MEWTWO", "RAID_LEVEL_5", attacker, off), personalResponseCacheKey("MEWTWO", "RAID_LEVEL_5", attacker, on))
        }
        // A provider fallback must never silently substitute non-Party results.
        assertTrue(on.precomputedBaseline().partyPower)
    }

    @Test fun defaultsPersistPartyPowerWithoutChangingOtherOptions() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("raid.preferences_pb") }
        try {
            val prefs = RaidCounterPreferences(store)
            assertFalse(prefs.settings.first().options.partyPower)
            val options = RaidCounterOptions(partyPower = true, attackerLevel = 35, friendship = PokebattlerFriendship.BEST)
            prefs.updateDefaults(options)
            assertEquals(options, RaidCounterPreferences(store).settings.first().options)
            prefs.updateDefaults(options.copy(partyPower = false))
            assertFalse(prefs.settings.first().options.partyPower)
        } finally { scope.cancel() }
    }
}
