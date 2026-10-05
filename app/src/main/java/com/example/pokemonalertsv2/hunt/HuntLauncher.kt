package com.example.pokemonalertsv2.hunt

import android.content.Context
import com.example.pokemonalertsv2.catchroutes.CatchPoint
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.tracking.ArrivalTrackingService

/**
 * Starts a hunt from the target picker, wherever the picker was opened from: the map's
 * tools, the Tools tab, or the floating map's "Edit targets".
 */
internal suspend fun startHunt(
    context: Context,
    name: String,
    definition: FilterDefinition,
    savedHuntId: String?,
    area: List<CatchPoint>
) {
    val huntRepository = HuntRepository.getInstance(context)
    // Remembered before the hunt starts, so the row exists to point the session at.
    // Re-running an identical hunt touches that row rather than leaving a second copy.
    val saved = huntRepository.recordStart(
        name = name,
        definition = definition,
        replacingId = savedHuntId,
        area = area
    )
    // The hunt is written first, then the old journey is cleared. The other order leaves
    // a moment with neither a destination nor a hunt, and a service running for the old
    // journey reads that as "nothing to do" and stops itself mid-start.
    huntRepository.start(
        name = name,
        definition = definition,
        savedHuntId = saved.id,
        area = area
    )
    // A new hunt supersedes whatever you were walking to. Without this the old journey
    // simply carries on under the new hunt's name, which is how a raid hunt ended up
    // pointing at a spawn.
    ArrivalTrackingRepository.getInstance(context).stopTracking()
    // Start the service even with nothing to walk to yet: it is what waits for the first
    // match to arrive.
    ArrivalTrackingService.startHunt(context)
}

/**
 * Changes what the running hunt is looking for, without ending it.
 *
 * The saved-hunt row is updated the same way editing it before a start would, and the
 * session keeps its start time, catches and distance. If the hunt ended while the picker
 * was open, this starts it instead -- the trainer pressed a button that says "hunt".
 */
internal suspend fun updateHunt(
    context: Context,
    name: String,
    definition: FilterDefinition,
    savedHuntId: String?,
    area: List<CatchPoint>
) {
    val huntRepository = HuntRepository.getInstance(context)
    if (!huntRepository.isHunting()) {
        startHunt(context, name, definition, savedHuntId, area)
        return
    }
    val saved = huntRepository.recordStart(
        name = name,
        definition = definition,
        replacingId = savedHuntId,
        area = area
    )
    huntRepository.updateTargets(name, definition, saved.id, area)
}
