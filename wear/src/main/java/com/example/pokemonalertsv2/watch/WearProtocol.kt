package com.example.pokemonalertsv2.watch

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The watch's copy of the phone app's `wear/WearProtocol.kt`: same paths, same JSON. Change
 * both together; the phone's `WatchStatsTest` pins the wire format.
 */
object WearProtocol {
    const val PATH_SESSION = "/pokealerts/session"
    const val KEY_STATE = "state"
    const val PATH_COMMAND = "/pokealerts/command"
    const val PATH_SAMPLE = "/pokealerts/sample"

    enum class Command(val wire: String) { PAUSE("pause"), RESUME("resume"), STOP("stop"), CAUGHT("caught"), SKIP("skip") }

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun decodeState(text: String?): WearSessionState? =
        text?.let { runCatching { json.decodeFromString(WearSessionState.serializer(), it) }.getOrNull() }

    fun encodeSample(sample: WatchSample): ByteArray = json.encodeToString(WatchSample.serializer(), sample).toByteArray()
}

@Serializable
data class WearSessionState(
    val kind: String = KIND_NONE,
    val name: String = "",
    val startedAtMillis: Long = 0L,
    val snapshotAtMillis: Long = 0L,
    val activeMillis: Long = 0L,
    val paused: Boolean = false,
    val caught: Int = 0,
    val distanceMeters: Double = 0.0,
    val target: String? = null,
    val remaining: Int? = null,
) {
    val active: Boolean get() = kind != KIND_NONE
    val isHunt: Boolean get() = kind == KIND_HUNT

    /** Active time now, counted on from the phone's last snapshot. */
    fun activeMillisAt(now: Long): Long =
        if (paused || snapshotAtMillis == 0L) activeMillis else activeMillis + (now - snapshotAtMillis).coerceAtLeast(0L)

    companion object {
        const val KIND_NONE = "none"
        const val KIND_HUNT = "hunt"
        const val KIND_ROUTE = "route"
    }
}

@Serializable
data class WatchSample(
    val atMillis: Long,
    val heartRate: Int? = null,
    val stepsTotal: Long = 0L,
    val caloriesTotal: Double = 0.0,
)
