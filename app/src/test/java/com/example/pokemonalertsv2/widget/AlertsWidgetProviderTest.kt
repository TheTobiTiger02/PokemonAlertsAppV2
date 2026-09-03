package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            alerts = listOf(sampleAlert(endTime = "120000000000000"))
        )
        val nowMillis = 1_000_000_000_000L
        val nextExpirationMillis = WidgetAlertSnapshotStore.nextExpirationMillis(nowMillis)

        val delay = AlertsWidgetProvider.calculateNextUpdateDelay(
            nowMillis = nowMillis,
            hasActiveAlerts = nextExpirationMillis != null,
            nextExpirationMillis = nextExpirationMillis
        )

        assertEquals(TimeUnit.MINUTES.toMillis(1), delay)
    }

    @Test
    fun calculateNextUpdateDelay_refreshesAtNextExpiryWithoutBuffer() {
        val delay = AlertsWidgetProvider.calculateNextUpdateDelay(
            nowMillis = 1_000L,
            hasActiveAlerts = true,
            nextExpirationMillis = 6_000L
        )

        assertEquals(TimeUnit.SECONDS.toMillis(5), delay)
    }

    @Test
    fun calculateNextUpdateDelay_usesMinimumDelayForAlreadyPassedExpiry() {
        val delay = AlertsWidgetProvider.calculateNextUpdateDelay(
            nowMillis = 6_000L,
            hasActiveAlerts = true,
            nextExpirationMillis = 5_000L
        )

        assertEquals(1L, delay)
    }

    @Test
    fun exactAlarmIsUsedOnlyForActiveAlertsWhenAccessIsGranted() {
        assertTrue(
            shouldScheduleExactWidgetAlarm(
                hasActiveAlerts = true,
                canScheduleExactAlarms = true
            )
        )
        assertFalse(
            shouldScheduleExactWidgetAlarm(
                hasActiveAlerts = true,
                canScheduleExactAlarms = false
            )
        )
        assertFalse(
            shouldScheduleExactWidgetAlarm(
                hasActiveAlerts = false,
                canScheduleExactAlarms = true
            )
        )
    }

    /**
     * This used to assert the opposite - that the URI changes with every snapshot generation -
     * which is exactly what leaked. A differing intent makes the framework build another
     * RemoteViewsService connection and another factory, each holding its own copy of the alert
     * list, and they accumulated until the process ran out of memory. Refreshes go through
     * notifyAppWidgetViewDataChanged now, so this must stay stable.
     */
    @Test
    fun adapterDataUriIsStablePerWidgetSoOneFactoryIsReused() {
        val first = widgetAdapterDataKey("com.example.test", appWidgetId = 7)
        val second = widgetAdapterDataKey("com.example.test", appWidgetId = 7)
        val other = widgetAdapterDataKey("com.example.test", appWidgetId = 8)

        assertEquals(first, second)
        assertTrue(first != other)
        assertTrue(first.endsWith("/7"))
    }

    @Test
    fun questWidgetCopyShowsTaskAndReward() {
        val alert = PokemonAlert(
            name = "Quest",
            type = listOf("Quest"),
            questTask = "Spin 5 PokéStops",
            questReward = "Pikachu"
        )

        assertEquals(
            "Task: Spin 5 PokéStops • Reward: Pikachu",
            buildWidgetAlertDescription(alert, fallback = "Quest")
        )
        assertEquals(
            "Task: Spin 5 PokéStops",
            buildCompactWidgetAlertMeta(alert, fallback = "Quest")
        )
    }

    @Test
    fun widgetCopyFallsBackWhenQuestTaskIsMissing() {
        val alert = PokemonAlert(name = "Quest", type = listOf("Quest"), questTask = " ")

        assertEquals("PokéStop", buildWidgetAlertDescription(alert, fallback = "PokéStop"))
        assertEquals("Quest", buildCompactWidgetAlertMeta(alert, fallback = "Quest"))
    }

    private fun sampleAlert(endTime: String) = PokemonAlert(
        name = "Out Of Range",
        endTime = endTime,
        latitude = 49.74,
        longitude = 8.62,
        type = listOf("Quest")
    )
}
