package com.example.pokemonalertsv2.tracking

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pokemonalertsv2.data.PokemonAlert
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArrivalTrackingRepositoryInstrumentedTest {
    private val repository = ArrivalTrackingRepository.getInstance(
        ApplicationProvider.getApplicationContext()
    )

    @After
    fun cleanUp() = runBlocking {
        repository.stopTracking()
        repository.updateArrivalRadius(ArrivalTrackingRepository.DEFAULT_RADIUS_METERS)
    }

    @Test
    fun destinationAndLiveRadiusSurviveRepositoryReads() = runBlocking {
        repository.updateArrivalRadius(65)
        repository.startTracking(
            PokemonAlert(
                name = "Persisted Hundo",
                pokemon = "Pikachu",
                type = listOf("Hundo"),
                latitude = 49.86,
                longitude = 8.65
            ),
            nowMillis = 1234L
        )

        val stored = repository.currentDestination()
        assertEquals("Persisted Hundo", stored?.alert?.name)
        assertEquals(65, stored?.radiusMeters)

        repository.updateArrivalRadius(80)
        assertEquals(80, repository.currentDestination()?.radiusMeters)

        repository.stopTracking()
        assertNull(repository.currentDestination())
    }
}
