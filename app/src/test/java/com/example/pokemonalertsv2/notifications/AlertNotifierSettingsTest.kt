package com.example.pokemonalertsv2.notifications

import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.hunt.isHuntTarget
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import com.example.pokemonalertsv2.data.DEFAULT_QUIET_HOURS_START
import com.example.pokemonalertsv2.data.DEFAULT_QUIET_HOURS_END

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
    fun shouldNotify_normalizesIdentifiersIndependentlyOfDeviceLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val settings = notificationSettings(allowedHundoSpecies = setOf("ivysaur"))

            assertTrue(settings.shouldNotify(sampleAlert(name = "Ivysaur", type = listOf("Hundo"))))
        } finally {
            Locale.setDefault(previousLocale)
        }
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

    @Test fun rareFlamigoDistanceLimitIsIndependentOfHuntMatching() {
        val flamigo = PokemonAlert(name = "Flamigo", pokemon = "Flamigo", type = listOf("Rare"), latitude = 49.7, longitude = 8.6)
        val hunt = FilterDefinition(alertTypes = FilterSelection.only(listOf("Rare")), rareSpecies = FilterSelection.only(listOf("Flamigo")))
        val notifications = hunt.copy(distanceOverrides = DistanceOverrides(perType = mapOf(FilterAlertType.RARE.name to 300)))
        val settings = notificationSettings().copy(filterDefinition = notifications)
        assertTrue(isHuntTarget(flamigo, hunt))
        assertTrue(settings.shouldNotify(flamigo, matchContext = FilterMatchContext(effectiveDistanceMeters = 299f)))
        assertTrue(settings.shouldNotify(flamigo, matchContext = FilterMatchContext(effectiveDistanceMeters = 300f)))
        assertFalse(settings.shouldNotify(flamigo, matchContext = FilterMatchContext(effectiveDistanceMeters = 301f)))
        assertFalse(settings.shouldNotify(flamigo, matchContext = FilterMatchContext(effectiveDistanceMeters = 3000f)))
        // User explicitly retains notification delivery when distance cannot be checked.
        assertTrue(settings.shouldNotify(flamigo, matchContext = FilterMatchContext()))
        val dualType = flamigo.copy(type = listOf("Rare", "Hundo"), iv = "100")
        val overlapping = settings.copy(filterDefinition = notifications.copy(alertTypes = FilterSelection.only(listOf("Rare", "Hundo"))))
        assertTrue(overlapping.shouldNotify(dualType, matchContext = FilterMatchContext(effectiveDistanceMeters = 3000f)))
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
        goDexFilterEnabled: Boolean = false,
        maxWalkingMinutes: Int = 0,
        quietHoursEnabled: Boolean = false,
        quietHoursStartMinute: Int = DEFAULT_QUIET_HOURS_START,
        quietHoursEndMinute: Int = DEFAULT_QUIET_HOURS_END
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
        maxWalkingMinutes = maxWalkingMinutes,
        quietHoursEnabled = quietHoursEnabled,
        quietHoursStartMinute = quietHoursStartMinute,
        quietHoursEndMinute = quietHoursEndMinute,
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
