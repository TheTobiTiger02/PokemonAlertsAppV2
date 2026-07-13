package com.example.pokemonalertsv2.notifications

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.AlertCoordinates
import com.example.pokemonalertsv2.util.AlertImageSource
import com.example.pokemonalertsv2.util.orderedAlertImageSources
import com.example.pokemonalertsv2.util.validAlertCoordinates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal const val NOTIFICATION_IMAGE_PREPARATION_TIMEOUT_MS = 6_000L

/**
 * Resolves the complete notification image before the notification is posted.
 * The generic result keeps the ordering and timeout policy independently testable
 * without requiring Android bitmap instances in local unit tests.
 */
internal suspend fun <T> resolveAlertNotificationImage(
    alert: PokemonAlert,
    timeoutMillis: Long = NOTIFICATION_IMAGE_PREPARATION_TIMEOUT_MS,
    loadRemoteImage: suspend (String) -> T?,
    generateMapFallback: suspend (AlertCoordinates, String?) -> T?
): T? = withTimeoutOrNull(timeoutMillis) {
    for (source in orderedAlertImageSources(alert)) {
        val image = when (source) {
            AlertImageSource.REMOTE_IMAGE -> alert.imageUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { url -> attemptImageOperation { loadRemoteImage(url) } }

            AlertImageSource.MAP_FALLBACK -> validAlertCoordinates(alert)
                ?.let { coordinates ->
                    attemptImageOperation {
                        generateMapFallback(
                            coordinates,
                            alert.thumbnailUrl?.takeIf { it.isNotBlank() }
                        )
                    }
                }

            AlertImageSource.THUMBNAIL -> alert.thumbnailUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { url -> attemptImageOperation { loadRemoteImage(url) } }

            AlertImageSource.PLACEHOLDER -> null
        }
        if (image != null) return@withTimeoutOrNull image
    }
    null
}

private suspend fun <T> attemptImageOperation(block: suspend () -> T?): T? = try {
    block()
} catch (exception: CancellationException) {
    throw exception
} catch (_: Exception) {
    null
}
