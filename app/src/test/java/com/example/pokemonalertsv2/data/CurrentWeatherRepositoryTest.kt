package com.example.pokemonalertsv2.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentWeatherRepositoryTest {

    private var now = 0L
    private val requests = mutableListOf<String>()
    private var response: CurrentWeatherResponse? =
        CurrentWeatherResponse(area = "Alsbach", currentWeather = "Rain", isCurrentHour = true)
    private var failure: Throwable? = null

    private val repository = CurrentWeatherRepository(
        fetch = { area ->
            requests += area
            failure?.let { throw it }
            response ?: error("no response configured")
        },
        elapsedRealtime = { now }
    )

    @Test
    fun trimsAreaAndCachesWithinTheTtl() = runTest {
        assertEquals("Rain", repository.weatherFor("  Alsbach  ")?.currentWeather)
        now += 60_000
        assertEquals("Rain", repository.weatherFor("Alsbach")?.currentWeather)

        assertEquals(listOf("Alsbach"), requests)
    }

    @Test
    fun refetchesOnceTheCacheHasExpired() = runTest {
        repository.weatherFor("Alsbach")
        now += 6 * 60_000
        repository.weatherFor("Alsbach")

        assertEquals(listOf("Alsbach", "Alsbach"), requests)
    }

    @Test
    fun skipsTheEveryAreaPseudoValue() = runTest {
        assertNull(repository.weatherFor("All"))
        assertNull(repository.weatherFor(null))
        assertNull(repository.weatherFor("  "))

        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun failureKeepsServingTheLastKnownValue() = runTest {
        repository.weatherFor("Alsbach")
        now += 6 * 60_000
        failure = IllegalStateException("offline")

        // Weather is a hint, never a reason to fail a screen.
        assertEquals("Rain", repository.weatherFor("Alsbach")?.currentWeather)
    }

    @Test
    fun firstFailureReturnsNothingRatherThanThrowing() = runTest {
        failure = IllegalStateException("offline")

        assertNull(repository.weatherFor("Alsbach"))
    }

    @Test
    fun ignoresAResponseWithNoWeatherInIt() = runTest {
        response = CurrentWeatherResponse(area = "Alsbach", currentWeather = null)

        assertNull(repository.weatherFor("Alsbach"))
    }
}
