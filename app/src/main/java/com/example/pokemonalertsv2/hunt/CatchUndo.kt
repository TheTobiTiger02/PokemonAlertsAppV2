package com.example.pokemonalertsv2.hunt

import android.content.Context
import android.util.Log
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.CaughtAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
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
        retargetHunt(applicationContext, record.id, nowMillis)
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
private suspend fun retargetHunt(context: Context, alertId: String, nowMillis: Long) {
    val huntRepository = HuntRepository.getInstance(context)
    if (!huntRepository.isHunting()) return
    val alert = PokemonAlertsRepository.create(context).alerts.first()
        .firstOrNull { it.uniqueId == alertId }
        ?.takeIf { it.isEligibleArrivalDestination(nowMillis) }
        ?: return
    ArrivalTrackingRepository.getInstance(context).startTracking(alert, nowMillis)
    huntRepository.setTarget(alert.uniqueId)
}
