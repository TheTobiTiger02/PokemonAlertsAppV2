package com.example.pokemonalertsv2.hunt

import android.content.Context
import android.util.Log
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.CaughtAlert
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.raidwatch.RaidWatchController
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.tracking.isEligibleArrivalDestination
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider
import kotlinx.coroutines.flow.first

private const val TAG = "CatchUndo"

/**
 * Long enough to notice a mis-tap, short enough that the offer is never stale.
 *
 * The tick that retires a target sits between two step arrows in a 30dp bar on a
 * map being read while walking, so it gets hit by accident — but an undo still
 * being offered ten minutes and three catches later is no longer about that tap.
 */
const val CATCH_UNDO_WINDOW_MILLIS = 120_000L

/** Whether the offer is still worth showing. A record with no timestamp never is. */
internal fun isUndoOfferLive(
    caught: CaughtAlert?,
    nowMillis: Long,
    windowMillis: Long = CATCH_UNDO_WINDOW_MILLIS
): Boolean {
    val record = caught ?: return false
    val age = nowMillis - record.caughtAtMillis
    return age in 0..windowMillis
}

internal fun undoOfferLabel(caught: CaughtAlert): String = "Undo catching ${caught.displayName}"

/**
 * The single way a target is retired with "Got it", wherever the tick was pressed.
 *
 * Dismisses it like a swipe on the feed would, remembers it for [undoLastCatch], adds it
 * to the hunt's catch list, and ends its Raid Watch: the hundo CP and counters belong to
 * the raid you were standing at, and once it is caught the chip has to make way for the
 * next target rather than keep counting down a raid you have finished with.
 */
suspend fun recordHuntCatch(
    context: Context,
    alert: PokemonAlert,
    displayName: String,
    nowMillis: Long = System.currentTimeMillis()
) {
    val applicationContext = context.applicationContext
    val raidWatched = runCatching { RaidWatchController.stopIfWatching(applicationContext, alert.uniqueId) }
        .onFailure { Log.w(TAG, "Could not end the caught raid's watch", it) }
        .getOrDefault(false)
    runCatching {
        val preferences = AlertPreferences(applicationContext.alertPreferencesDataStore)
        preferences.addDismissedAlert(alert.uniqueId)
        preferences.rememberCaughtAlert(alert.uniqueId, displayName, nowMillis, raidWatched)
        AlertsWidgetProvider.requestUpdate(applicationContext)
    }.onFailure { Log.w(TAG, "Could not record the caught target", it) }
    runCatching {
        HuntRepository.getInstance(applicationContext).recordCatch(
            HuntCatch(
                id = alert.uniqueId,
                name = displayName,
                imageUrl = alert.thumbnailUrl?.takeIf { it.isNotBlank() } ?: alert.imageUrl?.takeIf { it.isNotBlank() },
                atMillis = nowMillis
            )
        )
    }.onFailure { Log.w(TAG, "Could not add the catch to the hunt", it) }
}

/**
 * The single way back from a catch, wherever the tick was pressed.
 *
 * There are four of those — the notification action, the floating window, the
 * picture-in-picture window and the panel — and before this they un-did different
 * amounts of the catch, or nothing at all. Modelled on
 * [com.example.pokemonalertsv2.tracking.ArrivalTrackingService.stopEverything]:
 * one path, so they cannot drift.
 *
 * Returns true when something was actually put back.
 */
suspend fun undoLastCatch(
    context: Context,
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    val applicationContext = context.applicationContext
    val preferences = AlertPreferences(applicationContext.alertPreferencesDataStore)
    val caught = preferences.lastCaughtAlert.first()
    if (!isUndoOfferLive(caught, nowMillis)) return false
    val record = caught ?: return false

    return runCatching {
        preferences.removeDismissedAlert(record.id)
        // Cleared before anything else can fail: the offer is for the last catch,
        // and a second tap arriving from another surface must find nothing to do.
        preferences.forgetCaughtAlert()
        AlertsWidgetProvider.requestUpdate(applicationContext)
        HuntRepository.getInstance(applicationContext).forgetCatch(record.id)
        val alert = PokemonAlertsRepository.create(applicationContext).alerts.first()
            .firstOrNull { it.uniqueId == record.id }
        // The catch took the raid's hundo CP and counters away; taking it back returns them.
        // start() refuses a raid that has ended in the meantime.
        if (record.raidWatched && alert != null) RaidWatchController.start(applicationContext, alert, nowMillis)
        retargetHunt(applicationContext, alert, nowMillis)
        true
    }.getOrElse {
        Log.w(TAG, "Could not undo the last catch", it)
        false
    }
}

/**
 * Points the journey back at the restored alert, when a hunt is still running.
 *
 * Undoing a catch mid-hunt has already advanced to the next target, so without
 * this the alert comes back to the feed and the map while the walk carries on
 * somewhere else. Silently skipped once the alert has expired — restoring it to
 * the list is still right, walking to it is not.
 */
private suspend fun retargetHunt(context: Context, restored: PokemonAlert?, nowMillis: Long) {
    val huntRepository = HuntRepository.getInstance(context)
    if (!huntRepository.isHunting()) return
    val alert = restored?.takeIf { it.isEligibleArrivalDestination(nowMillis) } ?: return
    ArrivalTrackingRepository.getInstance(context).startTracking(alert, nowMillis)
    huntRepository.setTarget(alert.uniqueId)
}
