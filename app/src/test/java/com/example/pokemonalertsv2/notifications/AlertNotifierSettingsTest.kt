package com.example.pokemonalertsv2.notifications

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertNotifierSettingsTest {

    @Test
    fun notificationContent_placesExactWildCpFirst() {
        val alert = PokemonAlert(name = "Trubbish", cp = 457, type = listOf("Hundo"))

        assertEquals(
            "CP 457 • 15.5 km • 192 min walk • Hundo",
            AlertNotifier.buildNotificationContentText(
                alert = alert,
                distanceText = "15.5 km",
                walkingText = "192 min walk"
            )
        )
    }

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

    @Test
    fun goDexFilterSuppressesOnlyConfirmedCollectedHundos() {
        val settings = notificationSettings(
            allowedHundoSpecies = setOf("different-species"),
            goDexFilterEnabled = true
        )
        val hundo = sampleAlert(type = listOf("Hundo"))

        assertFalse(settings.shouldNotify(hundo, GoDexMatchStatus.COLLECTED))
        assertTrue(settings.shouldNotify(hundo, GoDexMatchStatus.NEEDED))
        assertTrue(settings.shouldNotify(hundo, GoDexMatchStatus.EVOLUTION_NEEDED))
        assertTrue(settings.shouldNotify(hundo, GoDexMatchStatus.FORM_CHANGE_NEEDED))
        assertTrue(settings.shouldNotify(hundo, GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED))
        assertTrue(settings.shouldNotify(hundo, GoDexMatchStatus.UNKNOWN))
        assertTrue(settings.shouldNotify(hundo, GoDexMatchStatus.NOT_CONFIGURED))
    }

    @Test
    fun goDexFilterDoesNotChangeNonHundoOrOtherFilters() {
        val settings = notificationSettings(goDexFilterEnabled = true, rocketEnabled = false)

        assertTrue(settings.shouldNotify(sampleAlert(type = listOf("Quest")), GoDexMatchStatus.COLLECTED))
        assertFalse(settings.shouldNotify(sampleAlert(type = listOf("Rocket")), GoDexMatchStatus.NEEDED))
    }

    private fun notificationSettings(
        notificationsEnabled: Boolean = true,
        rocketEnabled: Boolean = true,
        silenceUntil: Long = 0L,
        selectedArea: String = "All",
        maxDistance: Int = 0,
        allowedHundoSpecies: Set<String> = emptySet(),
        excludedRaidTiers: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
        goDexFilterEnabled: Boolean = false
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
        nowMillis = nowMillis,
        goDexFilterEnabled = goDexFilterEnabled
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
