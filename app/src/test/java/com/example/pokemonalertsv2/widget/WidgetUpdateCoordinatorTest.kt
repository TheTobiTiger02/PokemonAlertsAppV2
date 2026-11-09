package com.example.pokemonalertsv2.widget

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetUpdateCoordinatorTest {
    private val gap = 5_000L

    @Test
    fun aSteadyStreamRefreshesOncePerGapAndKeepsTheLastRequest() = runTest {
        val requests = Channel<Unit>(capacity = Channel.CONFLATED)
        var refreshes = 0
        val loop = launch {
            WidgetUpdateCoordinator.runRefreshLoop(requests, isScreenOn = { true }, gapMillis = gap) { refreshes++ }
        }

        requests.trySend(Unit)
        runCurrent()
        assertEquals(1, refreshes)

        repeat(10) {
            advanceTimeBy(100)
            requests.trySend(Unit)
        }
        runCurrent()
        assertEquals(1, refreshes)

        advanceTimeBy(gap)
        runCurrent()
        assertEquals(2, refreshes)
        loop.cancel()
    }

    @Test
    fun requestsWithTheScreenOffAreDropped() = runTest {
        val requests = Channel<Unit>(capacity = Channel.CONFLATED)
        var screenOn = false
        var refreshes = 0
        val loop = launch {
            WidgetUpdateCoordinator.runRefreshLoop(requests, isScreenOn = { screenOn }, gapMillis = gap) { refreshes++ }
        }

        requests.trySend(Unit)
        runCurrent()
        assertEquals(0, refreshes)

        screenOn = true
        requests.trySend(Unit)
        runCurrent()
        assertEquals(1, refreshes)
        loop.cancel()
    }
}
