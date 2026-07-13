package com.example.pokemonalertsv2.notifications

import com.example.pokemonalertsv2.data.PokemonAlert
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationImageResolverTest {

    @Test
    fun thumbnailLessAlertAwaitsGeneratedMap() = runTest {
        val generatedMap = CompletableDeferred<String>()
        val result = async {
            resolveAlertNotificationImage(
                alert = alertWithCoordinates(),
                loadRemoteImage = { null },
                generateMapFallback = { _, thumbnailUrl, _ ->
                    assertNull(thumbnailUrl)
                    generatedMap.await()
                }
            )
        }

        runCurrent()
        assertFalse(result.isCompleted)

        generatedMap.complete("generated-map")
        assertEquals("generated-map", result.await())
    }

    @Test
    fun failedHeroImageContinuesToMapFallback() = runTest {
        val attempts = mutableListOf<String>()
        val result = resolveAlertNotificationImage(
            alert = alertWithCoordinates(imageUrl = "https://example.test/hero.png"),
            loadRemoteImage = { url ->
                attempts += "remote:$url"
                null
            },
            generateMapFallback = { _, _, _ ->
                attempts += "map"
                "generated-map"
            }
        )

        assertEquals("generated-map", result)
        assertEquals(
            listOf("remote:https://example.test/hero.png", "map"),
            attempts
        )
    }

    @Test
    fun invalidCoordinatesSkipMapGeneration() = runTest {
        var mapWasRequested = false
        val result = resolveAlertNotificationImage(
            alert = PokemonAlert(
                name = "Invalid location",
                latitude = 0.0,
                longitude = 0.0,
                thumbnailUrl = "https://example.test/pokemon.png"
            ),
            loadRemoteImage = { "thumbnail" },
            generateMapFallback = { _, _, _ ->
                mapWasRequested = true
                "unexpected-map"
            }
        )

        assertEquals("thumbnail", result)
        assertFalse(mapWasRequested)
    }

    @Test
    fun timedOutMapReturnsTextOnlyFallback() = runTest {
        val result = resolveAlertNotificationImage<String>(
            alert = alertWithCoordinates(),
            timeoutMillis = 100L,
            loadRemoteImage = { null },
            generateMapFallback = { _, _, _ -> awaitCancellation() }
        )

        assertNull(result)
    }

    @Test
    fun failedMapContinuesToAvailableThumbnail() = runTest {
        val attempts = mutableListOf<String>()
        val result = resolveAlertNotificationImage(
            alert = alertWithCoordinates(
                thumbnailUrl = "https://example.test/pokemon.png"
            ),
            loadRemoteImage = { url ->
                attempts += "remote:$url"
                "thumbnail"
            },
            generateMapFallback = { _, thumbnailUrl, _ ->
                attempts += "map:$thumbnailUrl"
                null
            }
        )

        assertEquals("thumbnail", result)
        assertEquals(
            listOf(
                "map:https://example.test/pokemon.png",
                "remote:https://example.test/pokemon.png"
            ),
            attempts
        )
    }

    @Test
    fun imagePreparationExceptionFallsBackToTextOnlyNotification() = runTest {
        val result = resolveAlertNotificationImage<String>(
            alert = alertWithCoordinates(),
            loadRemoteImage = { null },
            generateMapFallback = { _, _, _ -> error("tile request failed") }
        )

        assertNull(result)
    }

    private fun alertWithCoordinates(
        imageUrl: String? = null,
        thumbnailUrl: String? = null
    ) = PokemonAlert(
        name = "Darmstadt alert",
        latitude = 49.88146124315846,
        longitude = 8.64371495798815,
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
        pokemonLocation = "Wilhelminenstraße Darmstadt"
    )
}
