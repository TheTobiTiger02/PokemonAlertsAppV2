package com.example.pokemonalertsv2.sync.live

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.hunt.HuntCatch
import com.example.pokemonalertsv2.hunt.HuntSession
import com.example.pokemonalertsv2.hunt.HuntSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a running session looks like to the group's other devices (the `live` collection,
 * DEVICE_SYNC.md). The device running it, the owner, writes these items from its own stores;
 * the others only read them, and act on the session by writing a [LiveCommand] to the owner.
 *
 * Every item changes only on a discrete event (a target, a catch, a pause): the position and
 * the walked distance, which change with every fix, travel as presence instead.
 */
internal object LiveKeys {
    /** Who runs the hunt. Written only when a hunt starts and when another device takes it over. */
    const val HUNT_OWNER = "hunt/owner"
    const val HUNT_SESSION = "hunt/session"
    const val HUNT_STATE = "hunt/state"
    const val HUNT_STATS = "hunt/stats"
    const val HUNT_SUMMARY = "hunt/summary"
    const val CATCH_OWNER = "catch/owner"
    const val CATCH_SESSION = "catch/session"
    const val JOURNEY = "journey/destination"
    const val RAID_WATCH = "raidwatch/state"
    const val COMMAND_PREFIX = "cmd/"

    /** Items describing the running hunt (the summary outlives it). */
    val HUNT_ITEMS = listOf(HUNT_OWNER, HUNT_SESSION, HUNT_STATE, HUNT_STATS)
    val CATCH_ITEMS = listOf(CATCH_OWNER, CATCH_SESSION)
}

/**
 * Who runs a session. [startedAtMillis] names the session (a new hunt is a new claim);
 * [claimedAtMillis] orders claims on the same session: the latest take-over wins, and the
 * device it was taken from stops without ending the session.
 */
@Serializable
data class LiveClaim(val owner: LiveOwner, val startedAtMillis: Long, val claimedAtMillis: Long)

internal val liveJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Which device runs a session, as the other devices name it. */
@Serializable
data class LiveOwner(val deviceId: String, val deviceName: String = "")

@Serializable
data class LiveHuntSession(val session: HuntSession)

/**
 * The owner's plan and target. [route] is the numbered walk, [others] further matches;
 * both carry the alerts themselves so a device whose feed differs still draws them.
 */
@Serializable
data class LiveHuntState(
    val target: PokemonAlert? = null,
    val targetId: String? = null,
    val pinnedTargetId: String? = null,
    val paused: Boolean = false,
    val route: List<PokemonAlert> = emptyList(),
    val others: List<PokemonAlert> = emptyList(),
    val outside: List<PokemonAlert> = emptyList(),
    /** The undo offer of the last catch, while it lasts. */
    val undo: LiveUndo? = null,
    /** "Mewtwo raid ended · next: …" while it shows on the owner. */
    val endedNote: String? = null
)

@Serializable
data class LiveUndo(val id: String, val displayName: String, val caughtAtMillis: Long)

/**
 * The hunt's catches and pause time, plus distance and the walked track as of the last catch,
 * pause or [LiveSessions] refresh (every couple of minutes), so a take-over keeps them. The
 * live distance travels as presence.
 */
@Serializable
data class LiveHuntStats(
    val catches: List<HuntCatch> = emptyList(),
    val pausedSinceMillis: Long? = null,
    val pausedTotalMillis: Long = 0,
    val distanceMeters: Double = 0.0,
    val track: String = ""
)

@Serializable
data class LiveHuntSummary(val owner: LiveOwner, val summary: HuntSummary)

/** The running catch route (`CatchSession`), refreshed on each visit, pause or replan. */
@Serializable
data class LiveCatchSession(val session: com.example.pokemonalertsv2.catchroutes.CatchSession)

@Serializable
data class LiveJourney(val owner: LiveOwner, val alert: PokemonAlert, val startedAtMillis: Long)

@Serializable
data class LiveRaidWatch(val owner: LiveOwner, val alert: PokemonAlert, val startedAtMillis: Long, val endAtMillis: Long)

/**
 * A device asks the owner to act on a running session. Deleted by the owner once done.
 *
 * [sessionStartedAtMillis] and [expectedTargetId] are what the sender saw: the owner drops a
 * command meant for another session, and a Got it / Next / Previous whose target has moved on
 * (a double tap, or one that arrived late), so a catch is never recorded for the wrong target.
 */
@Serializable
data class LiveCommand(
    val id: String,
    val to: String,
    val from: String,
    val session: String,
    val action: String,
    val alertId: String? = null,
    val sessionStartedAtMillis: Long = 0,
    val expectedTargetId: String? = null,
    /** [LiveAction.UPDATE_TARGETS]: the new [LiveTargets]. */
    val targets: LiveTargets? = null,
    val sentAtMillis: Long
) {
    companion object {
        const val SESSION_HUNT = "hunt"
        const val SESSION_CATCH = "catch"
        const val SESSION_JOURNEY = "journey"
        const val SESSION_RAID_WATCH = "raidwatch"
    }
}

/** What a hunt looks for, as "Edit targets" on another device sends it. */
@Serializable
data class LiveTargets(
    val name: String,
    val definition: com.example.pokemonalertsv2.data.FilterDefinition,
    val savedHuntId: String? = null,
    val area: List<com.example.pokemonalertsv2.catchroutes.CatchPoint> = emptyList()
)

/** Every action another device can ask the owner for. */
enum class LiveAction {
    GOT_IT, UNDO, NEXT, PREVIOUS, RETARGET, PAUSE, RESUME, RECALCULATE, STOP, UPDATE_TARGETS,
    // Catch route
    SKIP, SPACIAL_REND_ON, SPACIAL_REND_OFF
}

/**
 * Where the owner is and what its readout says, every few seconds (presence, never stored).
 * [walkedMeters] and [activeMillis] are the hunt's or route's own figures.
 */
@Serializable
data class LivePresence(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val bearing: Float? = null,
    val targetId: String? = null,
    val distanceMeters: Float? = null,
    val inRange: Boolean = false,
    val waiting: Boolean = false,
    val walkedMeters: Double = 0.0,
    val atMillis: Long
)
