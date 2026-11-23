package com.example.pokemonalertsv2.sync

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What the group's live stream says (DEVICE_SYNC.md, "Live events"). */
sealed interface SyncEvent {
    /** Sent first: the server's collection versions and the presence it remembers. */
    data class Hello(val versions: Map<String, Long>, val presence: List<Presence>) : SyncEvent

    /** Another device stored [version] of [collection]; [payload] is the sealed blob when small. */
    data class Change(val collection: String, val version: Long, val origin: String, val payload: ByteArray?) : SyncEvent

    /** A device running a session says where it is; [payload] is sealed. */
    data class Presence(val origin: String, val payload: ByteArray, val atMillis: Long) : SyncEvent

    /** The group was deleted on one of its devices. */
    data object Deleted : SyncEvent
}

@Serializable
private data class HelloWire(val collections: List<SyncCollectionInfo> = emptyList(), val presence: List<PresenceWire> = emptyList())

@Serializable
private data class ChangeWire(val collection: String, val version: Long, val origin: String = "", val payload: String? = null)

@Serializable
private data class PresenceWire(val origin: String, val payload: String, val at: Long = 0)

/** Parses one event frame; null for anything this app does not know (a newer server). */
internal fun parseSyncEvent(type: String, data: String, decodeBase64: (String) -> ByteArray): SyncEvent? = runCatching {
    val json = SyncMerge.json
    when (type) {
        "hello" -> json.decodeFromString(HelloWire.serializer(), data).let { hello ->
            SyncEvent.Hello(
                versions = hello.collections.associate { it.name to it.version },
                presence = hello.presence.map { SyncEvent.Presence(it.origin, decodeBase64(it.payload), it.at) }
            )
        }
        "change" -> json.decodeFromString(ChangeWire.serializer(), data).let {
            SyncEvent.Change(it.collection, it.version, it.origin, it.payload?.let(decodeBase64))
        }
        "presence" -> json.decodeFromString(PresenceWire.serializer(), data).let {
            SyncEvent.Presence(it.origin, decodeBase64(it.payload), it.at)
        }
        "deleted" -> SyncEvent.Deleted
        else -> null
    }
}.getOrNull()

/**
 * Splits a server-sent event stream into (event, data) frames. Comment lines (the
 * heartbeat) and `retry:` are skipped; a frame ends at an empty line.
 */
internal class SseFrameReader {
    private var event = "message"
    private val data = StringBuilder()

    /** Feeds one line; returns a finished frame at its empty line. */
    fun line(line: String): Pair<String, String>? {
        if (line.isEmpty()) {
            val frame = if (data.isNotEmpty()) event to data.toString() else null
            event = "message"
            data.setLength(0)
            return frame
        }
        when {
            line.startsWith(":") -> Unit
            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").removePrefix(" "))
            }
        }
        return null
    }
}

/**
 * The app's end of the live stream. [events] runs one connection and ends (or throws) when it
 * does; [DeviceSync] reconnects with backoff while something holds the stream open.
 */
class SyncEventStream(client: OkHttpClient, private val baseUrl: String) {
    // No call timeout: the stream is meant to stay open. The read timeout is above the
    // server's 20 s heartbeat, so a dead connection is noticed within a minute.
    private val client = client.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Cancelling the collector cancels the call at once, which unblocks the read: the socket
     * closes when the app goes to the background, not at the next heartbeat.
     */
    fun events(group: SyncGroup, deviceId: String): Flow<SyncEvent> = callbackFlow {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/sync/${group.groupId}/events")
            .header("Accept", "text/event-stream")
            .header("X-Sync-Auth", group.authToken)
            .header("X-Sync-Device", deviceId)
            .apply { appKey()?.let { header("X-App-Key", it) } }
            .build()
        val call = client.newCall(request)
        launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (response.code == 404) throw SyncGroupGoneException()
                    if (!response.isSuccessful) {
                        throw SyncAccountException.fromResponse(response.code, runCatching { response.body?.string() }.getOrNull())
                            ?: SyncTransportException(response.code, "HTTP ${response.code}")
                    }
                    val source = response.body?.source() ?: throw IOException("empty stream")
                    val frames = SseFrameReader()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        val (type, data) = frames.line(line) ?: continue
                        parseSyncEvent(type, data) { Base64.decode(it, Base64.DEFAULT) }?.let { send(it) }
                    }
                }
                close()
            } catch (error: Throwable) {
                close(error)
            }
        }
        awaitClose { call.cancel() }
    }
}
