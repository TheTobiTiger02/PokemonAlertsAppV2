package com.example.pokemonalertsv2.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class AlertsWidgetProviderTest {

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
    fun calculateNextUpdateDelay_usesThirtySecondCadenceWhenNoExpiryIsSooner() {
        val delay = AlertsWidgetProvider.calculateNextUpdateDelay(
            nowMillis = 1_000L,
            hasActiveAlerts = true,
            nextExpirationMillis = 60_000L
        )

        assertEquals(TimeUnit.SECONDS.toMillis(30), delay)
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
}
