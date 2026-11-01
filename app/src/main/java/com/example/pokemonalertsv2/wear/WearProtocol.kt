package com.example.pokemonalertsv2.wear

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the phone and the watch app say to each other over the Wearable Data Layer (Bluetooth,
 * or Wi-Fi via the phone; never mobile data). The watch module keeps a copy of this file with
 * the same paths and JSON shape; `WearProtocolTest` pins the wire format.
 */
object WearProtocol {
    /** Data item the phone keeps current with the running session; the watch observes it. */
    const val PATH_SESSION = "/pokealerts/session"
    const val KEY_STATE = "state"
    /** Message from the watch: one of the [Command] wire names as UTF-8. */
    const val PATH_COMMAND = "/pokealerts/command"
    /** Message from the watch: a [WatchSample] as JSON. */
    const val PATH_SAMPLE = "/pokealerts/sample"
    /** Capability the watch app advertises, so the phone can tell whether one is installed. */
    const val CAPABILITY_WATCH = "pokealerts_watch"

    enum class Command(val wire: String) {
        PAUSE("pause"), RESUME("resume"), STOP("stop"), CAUGHT("caught"), SKIP("skip");

        companion object {
            fun fromWire(value: String): Command? = entries.firstOrNull { it.wire == value.trim() }
        }
    }

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encodeState(state: WearSessionState): String = json.encodeToString(WearSessionState.serializer(), state)
    fun decodeState(text: String): WearSessionState? = runCatching { json.decodeFromString(WearSessionState.serializer(), text) }.getOrNull()
    fun encodeSample(sample: WatchSample): ByteArray = json.encodeToString(WatchSample.serializer(), sample).toByteArray()
    fun decodeSample(bytes: ByteArray): WatchSample? = runCatching { json.decodeFromString(WatchSample.serializer(), bytes.decodeToString()) }.getOrNull()
}

/**
 * The running session as the watch shows it. [kind] is [KIND_NONE] when nothing runs, which is
 * the watch's cue to end its exercise. [activeMillis] was measured at [snapshotAtMillis]; the
 * watch keeps counting from there while not [paused], so the phone need not send every second.
 */
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
    /** Hunt: the Pokémon being walked to. Route: "Next stop" with its distance. */
    val target: String? = null,
    /** Route: how many stops remain. */
    val remaining: Int? = null,
) {
    val active: Boolean get() = kind != KIND_NONE

    companion object {
        const val KIND_NONE = "none"
        const val KIND_HUNT = "hunt"
        const val KIND_ROUTE = "route"
    }
}
