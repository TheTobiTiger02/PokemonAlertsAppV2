package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AlertsWidgetProviderTest {

    @Before
    fun setUp() {
        WidgetAlertSnapshotStore.clearForTesting()
    }

    @Test
    fun calculateNextUpdateDelay_usesIdleCadenceWhenNoAlertsAreActive() {
        val delay = AlertsWidgetProvider.calculateNextUpdateDelay(
            nowMillis = 1_000L,
            hasActiveAlerts = false,
            nextExpirationMillis = 2_000L
        )

        assertEquals(TimeUnit.MINUTES.toMillis(15), delay)
    }

    @Test
    fun calculateNextUpdateDelay_usesOneMinuteCadenceWhenNoExpiryIsSooner() {
        val delay = AlertsWidgetProvider.calculateNextUpdateDelay(
            nowMillis = 1_000L,
            hasActiveAlerts = true,
            nextExpirationMillis = 120_000L
        )

        assertEquals(TimeUnit.MINUTES.toMillis(1), delay)
    }

    @Test
    fun calculateNextUpdateDelay_usesOneMinuteCadenceForDistanceFilteredAlert() {
        WidgetAlertSnapshotStore.updateCadence(
            appWidgetId = 7,
            alerts = listOf(sampleAlert(endTime = "120000"))
        )
        val nowMillis = 1_000L
        val nextExpirationMillis = WidgetAlertSnapshotStore.nextExpirationMillis(nowMillis)

        val delay = AlertsWidgetProvider.calculateNextUpdateDelay(
            nowMillis = nowMillis,
            hasActiveAlerts = nextExpirationMillis != null,
            nextExpirationMillis = nextExpirationMillis
        )

        assertEquals(TimeUnit.MINUTES.toMillis(1), delay)
    }

    @Test
    fun calculateNextUpdateDelay_refreshesJustAfterNextExpiryWhenSoonerThanCadence() {
        val delay = AlertsWidgetProvider.calculateNextUpdateDelay(
            nowMillis = 1_000L,
            hasActiveAlerts = true,
            nextExpirationMillis = 6_000L
        )

        assertEquals(TimeUnit.SECONDS.toMillis(6), delay)
    }

    @Test
    fun calculateNextUpdateDelay_usesMinimumDelayForAlreadyPassedExpiry() {
        val delay = AlertsWidgetProvider.calculateNextUpdateDelay(
            nowMillis = 6_000L,
            hasActiveAlerts = true,
            nextExpirationMillis = 5_000L
        )

        assertEquals(TimeUnit.SECONDS.toMillis(1), delay)
    }

    private fun sampleAlert(endTime: String) = PokemonAlert(
        name = "Out Of Range",
        endTime = endTime,
        latitude = 49.74,
        longitude = 8.62,
        type = listOf("Quest")
    )
}
