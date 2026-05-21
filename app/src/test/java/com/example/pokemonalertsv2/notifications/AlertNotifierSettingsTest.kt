package com.example.pokemonalertsv2.notifications

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertNotifierSettingsTest {

    @Test
    fun shouldNotify_appliesAllowedSpeciesSnapshotCaseInsensitively() {
        val settings = notificationSettings(allowedHundoSpecies = setOf("pikachu"))

        assertTrue(settings.shouldNotify(sampleAlert(name = "Pikachu", type = listOf("Hundo"))))
        assertFalse(settings.shouldNotify(sampleAlert(name = "Eevee", type = listOf("Hundo"))))
    }

    @Test
    fun shouldNotify_appliesExcludedRaidTierSnapshotCaseInsensitively() {
        val settings = notificationSettings(excludedRaidTiers = setOf("mega"))

        assertFalse(settings.shouldNotify(sampleAlert(name = "Mega Raid", type = listOf("Raid", "Mega"))))
        assertTrue(settings.shouldNotify(sampleAlert(name = "Tier 5 Raid", type = listOf("Raid", "5"))))
    }

    @Test
    fun shouldNotify_blocksWhenNotificationsDisabledOrSilenced() {
        assertFalse(notificationSettings(notificationsEnabled = false).shouldNotify(sampleAlert()))
        assertFalse(notificationSettings(silenceUntil = 2_000L, nowMillis = 1_000L).shouldNotify(sampleAlert()))
    }



    @Test
    fun shouldNotify_appliesTypeToggles() {
        val settings = notificationSettings(rocketEnabled = false)

        assertFalse(settings.shouldNotify(sampleAlert(type = listOf("Rocket"))))
        assertTrue(settings.shouldNotify(sampleAlert(type = listOf("Quest"))))
    }

    private fun notificationSettings(
        notificationsEnabled: Boolean = true,
        rocketEnabled: Boolean = true,
        silenceUntil: Long = 0L,
        selectedArea: String = "All",
        maxDistance: Int = 0,
        allowedHundoSpecies: Set<String> = emptySet(),
        excludedRaidTiers: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis()
    ) = AlertNotifier.NotificationSettings(
        notificationsEnabled = notificationsEnabled,
        raidsEnabled = true,
        spawnsEnabled = true,
        questsEnabled = true,
        hundosEnabled = true,
        pvpEnabled = true,
        nundosEnabled = true,
        kecleonEnabled = true,
        rocketEnabled = rocketEnabled,
        vibrateEnabled = true,
        silenceUntil = silenceUntil,
        selectedArea = selectedArea,
        maxDistance = maxDistance,
        excludedHundoTypes = emptySet(),
        excludedNundoTypes = emptySet(),
        excludedPvpTypes = emptySet(),
        excludedSpawnTypes = emptySet(),
        excludedRocketTypes = emptySet(),
        excludedRaidTiers = excludedRaidTiers,
        allowedHundoSpecies = allowedHundoSpecies,
        allowedNundoSpecies = emptySet(),
        allowedPvpSpecies = emptySet(),
        allowedSpawnSpecies = emptySet(),
        nowMillis = nowMillis
    )

    private fun sampleAlert(
        name: String = "Pikachu",
        type: List<String> = listOf("Hundo"),
        area: String? = null
    ) = PokemonAlert(
        name = name,
        type = type,
        area = area
    )
}
