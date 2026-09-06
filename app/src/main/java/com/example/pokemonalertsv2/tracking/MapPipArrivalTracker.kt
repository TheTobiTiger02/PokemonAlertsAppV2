package com.example.pokemonalertsv2.tracking

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.pokemonalertsv2.data.PokemonAlert

/**
 * How the floating map hands a destination to arrival tracking.
 *
 * The picture-in-picture window never receives touch events, so it cannot go through
 * [rememberArrivalTrackingUiController]: that path can raise the "Switch destination?" dialog, a
 * permission request or a settings prompt, none of which the user could answer from the window.
 * This seam drives the repository directly behind a silent preflight instead, and reports failure
 * by returning false rather than by saying anything on screen. The window reads that as "keep the
 * label chip up", which is the only feedback channel it has.
 */
interface MapPipArrivalTracker {
    /** @return true when tracking is running for [alert]; false when the preflight refused. */
    suspend fun start(alert: PokemonAlert): Boolean

    suspend fun stop()
}

@Composable
fun rememberMapPipArrivalTracker(): MapPipArrivalTracker {
    val context = LocalContext.current.applicationContext
    return remember(context) { DefaultMapPipArrivalTracker(context) }
}

private class DefaultMapPipArrivalTracker(
    private val context: Context
) : MapPipArrivalTracker {

    private val repository = ArrivalTrackingRepository.getInstance(context)

    override suspend fun start(alert: PokemonAlert): Boolean {
        if (!hasFineLocationPermission(context)) return false
        // Not required to start the service, but without it the ongoing notification is dropped
        // and the user would get a foreground service telling them nothing. The chip is better.
        if (!hasNotificationPermission(context)) return false
        if (!areLocationServicesEnabled(context)) return false
        // Re-checked here, not just when the intent was resolved: the alert may have expired
        // while the browse cursor was settling, and startTracking rejects it with an exception.
        if (!alert.isEligibleArrivalDestination()) return false
        return runCatching {
            repository.startTracking(alert)
            ArrivalTrackingService.start(context)
        }.onFailure {
            // A refused foreground start would otherwise leave a stored destination with nothing
            // servicing it: tracking would look active, so the chip would hide, and nothing would
            // ever report progress. Roll back so the window falls back to the chip.
            runCatching { repository.stopTracking() }
        }.isSuccess
    }

    override suspend fun stop() {
        repository.stopTracking()
    }
}
