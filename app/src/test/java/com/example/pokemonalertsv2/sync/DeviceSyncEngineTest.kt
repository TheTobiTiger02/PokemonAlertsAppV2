package com.example.pokemonalertsv2.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceSyncEngineTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = FakeServer()

    @Test
    fun createUploadsAndJoinTakesTheGroupsValuesButKeepsLocalOnlyItems() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data += mapOf("theme" to "dark", "area" to "Alsbach")
        val group = a.engine.create()
        assertEquals(mapOf("theme" to "dark", "area" to "Alsbach"), server.document(group, DeviceSync.SETTINGS).entries.mapValues { it.value.value })

        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.adapter.data += mapOf("theme" to "light", "onlyOnB" to "kept")
        b.engine.join(group.pairingCode)
        assertEquals("the group's value wins", "dark", b.adapter.data["theme"])
        assertEquals("Alsbach", b.adapter.data["area"])
        assertEquals("B's own item survives", "kept", b.adapter.data["onlyOnB"])

        a.engine.syncNow()
        assertEquals("and reaches A", "kept", a.adapter.data["onlyOnB"])
        assertEquals("dark", a.adapter.data["theme"])
    }

    @Test
    fun conflictIsMergedAndRetried() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["x"] = "1"
        val group = a.engine.create()
        val b = FakeDevice(folder.root, "B", server, now = 1_500)
        b.engine.join(group.pairingCode)

        // C writes between A's fetch and A's upload, so A's first PUT gets a 412.
        var interfered = false
        lateinit var c: FakeDevice
        val racingA = FakeDevice(folder.root, "A2", server, now = 3_000, beforePut = {
            if (!interfered) {
                interfered = true
                c.adapter.data["fromC"] = "c"
                c.engine.syncNow()
            }
        })
        c = FakeDevice(folder.root, "C", server, now = 2_000)
        c.engine.join(group.pairingCode)
        racingA.engine.join(group.pairingCode)
        racingA.adapter.data["fromA"] = "a"
        val putsBefore = server.puts.get()
        racingA.engine.syncNow()

        assertTrue(interfered)
        val stored = server.document(group, DeviceSync.SETTINGS).entries.mapValues { it.value.value }
        assertEquals("a", stored["fromA"])
        assertEquals("c", stored["fromC"])
        assertEquals("C's write and A's merged retry", 2, server.puts.get() - putsBefore)
        assertEquals("c", racingA.adapter.data["fromC"])
    }

    @Test
    fun appliedRemoteValuesCauseNoSecondUpload() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["x"] = "1"
        val group = a.engine.create()
        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.engine.join(group.pairingCode)

        a.now = 5_000
        a.adapter.data["x"] = "2"
        a.engine.syncNow()
        b.engine.syncNow()
        assertEquals("2", b.adapter.data["x"])

        val puts = server.puts.get()
        b.engine.syncNow()
        b.engine.syncNow()
        a.engine.syncNow()
        assertEquals("nothing changed anywhere: no uploads", puts, server.puts.get())
    }

    @Test
    fun deletionsPropagateAsTombstones() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data += mapOf("area/1" to "home", "area/2" to "park")
        val group = a.engine.create()
        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.engine.join(group.pairingCode)
        assertEquals("park", b.adapter.data["area/2"])

        a.now = 3_000
        a.adapter.data.remove("area/2")
        a.engine.syncNow()
        b.engine.syncNow()
        assertFalse(b.adapter.data.containsKey("area/2"))
        assertTrue(server.document(group, DeviceSync.SETTINGS).entries.getValue("area/2").isTombstone)
    }

    @Test
    fun offlineEditsLastWriterWins() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["toggle"] = "off"
        val group = a.engine.create()
        val b = FakeDevice(folder.root, "B", server, now = 1_100)
        b.engine.join(group.pairingCode)

        a.now = 10_000; a.adapter.data["toggle"] = "on-A"
        b.now = 20_000; b.adapter.data["toggle"] = "on-B"
        a.engine.syncNow()
        b.engine.syncNow()
        a.engine.syncNow()
        assertEquals("on-B", a.adapter.data["toggle"])
        assertEquals("on-B", b.adapter.data["toggle"])
    }

    @Test
    fun unsupportedVersionIsNeverOverwritten() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["x"] = "1"
        val group = a.engine.create()
        val future = SyncMerge.json.encodeToString(SyncDocument.serializer(), SyncDocument(v = 2, entries = mapOf("x" to SyncEntry(9_000, "Z", value = "future"))))
        server.blobs[DeviceSync.SETTINGS] = 7L to SyncCrypto.seal(future.toByteArray(), group.encKey, SyncCrypto.aad(group.groupId, DeviceSync.SETTINGS))

        a.adapter.data["x"] = "2"
        val puts = server.puts.get()
        a.engine.syncNow()
        assertEquals("no upload over a newer format", puts, server.puts.get())
        assertEquals(7L, server.blobs.getValue(DeviceSync.SETTINGS).first)
        assertEquals("2", a.adapter.data["x"])
        assertTrue(a.state.status(DeviceSync.SETTINGS).unsupported)
    }

    @Test
    fun groupDeletedElsewhereDisconnectsInsteadOfRecreating() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["x"] = "1"
        val group = a.engine.create()
        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.engine.join(group.pairingCode)

        a.engine.deleteEverywhere()
        assertNull(a.state.group())
        assertFalse(b.engine.syncNow())
        assertNull(b.state.group())
        assertTrue("nothing was recreated", server.blobs.isEmpty())
        assertEquals("1", b.adapter.data["x"])
    }

    @Test
    fun joinWithUnknownOrInvalidCodeFails() = runBlocking {
        val b = FakeDevice(folder.root, "B", server, now = 1_000)
        val unknown = SyncCrypto.newGroup()
        val reason = runCatching { b.engine.join(unknown.pairingCode) }.exceptionOrNull() as? SyncJoinException
        assertEquals(SyncJoinException.Reason.NOT_FOUND, reason?.reason)
        val invalid = runCatching { b.engine.join("P1nope") }.exceptionOrNull() as? SyncJoinException
        assertEquals(SyncJoinException.Reason.INVALID_CODE, invalid?.reason)
        assertNull(b.state.group())
    }

    @Test
    fun pingActionBranchesOnOrigin() {
        assertEquals(DeviceSync.PingAction.NOT_A_PING, DeviceSync.pingAction(mapOf("pushKey" to "1"), "me"))
        assertEquals(DeviceSync.PingAction.OWN_WRITE, DeviceSync.pingAction(mapOf("devsync" to "1", "origin" to "me"), "me"))
        assertEquals(DeviceSync.PingAction.SYNC, DeviceSync.pingAction(mapOf("devsync" to "1", "origin" to "other"), "me"))
        assertEquals(DeviceSync.PingAction.SYNC, DeviceSync.pingAction(mapOf("devsync" to "1"), "me"))
    }
}
