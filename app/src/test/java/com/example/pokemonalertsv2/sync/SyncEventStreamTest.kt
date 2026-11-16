package com.example.pokemonalertsv2.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class SyncEventStreamTest {
    private val decode: (String) -> ByteArray = { Base64.getDecoder().decode(it) }

    private fun frames(text: String): List<Pair<String, String>> {
        val reader = SseFrameReader()
        return text.lines().mapNotNull { reader.line(it) }
    }

    @Test
    fun framesSkipCommentsAndRetry() {
        val parsed = frames("retry: 2000\n\n: ping 1\n\nevent: hello\ndata: {\"collections\":[]}\n\nevent: change\ndata: {\"collection\":\"live\",\"version\":3}\n\n")
        assertEquals(listOf("hello" to "{\"collections\":[]}", "change" to "{\"collection\":\"live\",\"version\":3}"), parsed)
    }

    @Test
    fun eventsParse() {
        val hello = parseSyncEvent(
            "hello",
            "{\"collections\":[{\"name\":\"settings\",\"version\":4}],\"presence\":[{\"origin\":\"a\",\"payload\":\"AQI=\",\"at\":9}],\"at\":1}",
            decode
        ) as SyncEvent.Hello
        assertEquals(mapOf("settings" to 4L), hello.versions)
        assertEquals("a", hello.presence.single().origin)
        assertArrayEquals(byteArrayOf(1, 2), hello.presence.single().payload)

        val change = parseSyncEvent("change", "{\"collection\":\"live\",\"version\":7,\"origin\":\"b\",\"payload\":\"AQI=\"}", decode) as SyncEvent.Change
        assertEquals("live", change.collection)
        assertEquals(7L, change.version)
        assertArrayEquals(byteArrayOf(1, 2), change.payload)
        val large = parseSyncEvent("change", "{\"collection\":\"godex\",\"version\":2,\"origin\":\"b\"}", decode) as SyncEvent.Change
        assertNull("large blobs come without payload", large.payload)

        assertEquals(SyncEvent.Deleted, parseSyncEvent("deleted", "{\"at\":1}", decode))
        assertNull("unknown events are ignored", parseSyncEvent("future", "{}", decode))
        assertNull("broken data is ignored", parseSyncEvent("change", "{nope", decode))
    }
}
