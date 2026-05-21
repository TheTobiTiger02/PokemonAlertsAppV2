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

    private fun notificationSettings(
        allowedHundoSpecies: Set<String> = emptySet(),
        excludedRaidTiers: Set<String> = emptySet()
    ) = AlertNotifier.NotificationSettings(
        notificationsEnabled = true,
        raidsEnabled = true,
        spawnsEnabled = true,
        questsEnabled = true,
        hundosEnabled = true,
        pvpEnabled = true,
        nundosEnabled = true,
        kecleonEnabled = true,
        rocketEnabled = true,
        vibrateEnabled = true,
        silenceUntil = 0L,
        selectedArea = "All",
        maxDistance = 0,
        excludedHundoTypes = emptySet(),
        excludedNundoTypes = emptySet(),
        excludedPvpTypes = emptySet(),
        excludedSpawnTypes = emptySet(),
        excludedRocketTypes = emptySet(),
        excludedRaidTiers = excludedRaidTiers,
        allowedHundoSpecies = allowedHundoSpecies,
        allowedNundoSpecies = emptySet(),
        allowedPvpSpecies = emptySet(),
        allowedSpawnSpecies = emptySet()
    )

    private fun sampleAlert(name: String, type: List<String>) = PokemonAlert(
        name = name,
        type = type
    )
}
