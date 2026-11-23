package com.example.pokemonalertsv2.sync

import kotlinx.coroutines.flow.first
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
        b.engine.join(group.secret)
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
        b.engine.join(group.secret)

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
        c.engine.join(group.secret)
        racingA.engine.join(group.secret)
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
        b.engine.join(group.secret)

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
        b.engine.join(group.secret)
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
        b.engine.join(group.secret)

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
        b.engine.join(group.secret)

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
        val reason = runCatching { b.engine.join(unknown.secret) }.exceptionOrNull() as? SyncJoinException
        assertEquals(SyncJoinException.Reason.NOT_FOUND, reason?.reason)
        assertNull(b.state.group())
    }

    @Test
    fun pingWithKnownVersionSkipsTheListingAndPullsOnlyThatCollection() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["x"] = "1"
        val group = a.engine.create()
        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.engine.join(group.secret)

        a.now = 5_000
        a.adapter.data["x"] = "2"
        a.engine.syncNow(listOf(DeviceSync.SETTINGS))
        val version = server.blobs.getValue(DeviceSync.SETTINGS).first
        val lists = server.lists.get()
        assertTrue(b.engine.syncNow(listOf(DeviceSync.SETTINGS), knownVersions = mapOf(DeviceSync.SETTINGS to version)))
        assertEquals("applied from the ping", "2", b.adapter.data["x"])
        assertEquals("no listing request", lists, server.lists.get())

        // A ping for a version this device already has (its own write, or a repeat) costs nothing.
        val fetches = server.fetches.get()
        b.engine.syncNow(listOf(DeviceSync.SETTINGS), knownVersions = mapOf(DeviceSync.SETTINGS to version))
        assertEquals(fetches, server.fetches.get())
        assertEquals(lists, server.lists.get())
    }

    @Test
    fun pingForADeletedGroupDisconnects() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["x"] = "1"
        val group = a.engine.create()
        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.engine.join(group.secret)
        a.engine.deleteEverywhere()
        assertFalse(b.engine.syncNow(listOf(DeviceSync.SETTINGS), knownVersions = mapOf(DeviceSync.SETTINGS to 9L)))
        assertNull(b.state.group())
        assertTrue("nothing was recreated", server.blobs.isEmpty())
    }

    @Test
    fun pingActionBranchesOnOrigin() {
        assertEquals(DeviceSync.PingAction.NOT_A_PING, DeviceSync.pingAction(mapOf("pushKey" to "1"), "me"))
        assertEquals(DeviceSync.PingAction.OWN_WRITE, DeviceSync.pingAction(mapOf("devsync" to "1", "origin" to "me"), "me"))
        assertEquals(DeviceSync.PingAction.SYNC, DeviceSync.pingAction(mapOf("devsync" to "1", "origin" to "other"), "me"))
        assertEquals(DeviceSync.PingAction.SYNC, DeviceSync.pingAction(mapOf("devsync" to "1"), "me"))
        assertEquals(DeviceSync.PingAction.DELETED, DeviceSync.pingAction(mapOf("devsync" to "1", "deleted" to "1", "origin" to "x"), "me"))
    }

    @Test
    fun anItemThisDeviceCannotReadIsKeptForTheOthers() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["saved/1"] = "v1"
        val group = a.engine.create()
        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.adapter.unreadable += "saved/2"
        b.engine.join(group.secret)

        a.now = 3_000
        a.adapter.data["saved/2"] = "written by a newer app"
        a.engine.syncNow()
        b.engine.syncNow()
        assertFalse("B could not apply it", b.adapter.data.containsKey("saved/2"))

        // A later change on B must not read the missing item as a deletion.
        b.now = 4_000
        b.adapter.data["other"] = "b"
        b.engine.syncNow()
        val stored = server.document(group, DeviceSync.SETTINGS).entries
        assertEquals("written by a newer app", stored.getValue("saved/2").value)
        a.engine.syncNow()
        assertEquals("written by a newer app", a.adapter.data["saved/2"])

        // Once B can read it (an app update), it applies on the next sync.
        b.adapter.unreadable.clear()
        val fetches = server.fetches.get()
        b.engine.syncNow()
        assertEquals("the same app version does not fetch again", fetches, server.fetches.get())
        b.appVersion = 2
        b.engine.syncNow()
        assertEquals("written by a newer app", b.adapter.data["saved/2"])
    }

    @Test
    fun anItemInANewerSchemaIsLeftAlone() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.schemas["filters"] = 2
        a.adapter.data["filters"] = "{new fields}"
        val group = a.engine.create()
        assertEquals(2, server.document(group, DeviceSync.SETTINGS).entries.getValue("filters").s)

        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.adapter.data["filters"] = "{old}"
        b.engine.join(group.secret)
        assertEquals("not applied by the older schema", "{old}", b.adapter.data["filters"])

        b.now = 3_000
        b.adapter.data["other"] = "x"
        b.engine.syncNow()
        val stored = server.document(group, DeviceSync.SETTINGS).entries.getValue("filters")
        assertEquals("never re-encoded by the older device", "{new fields}", stored.value)
        assertEquals(2, stored.s)
    }

    @Test
    fun aSettingKeptToThisDeviceNeitherTravelsNorIsOverwritten() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["notifications"] = "on"
        val group = a.engine.create()
        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.engine.join(group.secret)
        assertEquals("on", b.adapter.data["notifications"])

        // B keeps notifications to itself and turns them off.
        b.adapter.local += "notifications"
        b.now = 3_000
        b.adapter.data["notifications"] = "off"
        b.engine.syncNow()
        assertEquals("A's value stays the group's", "on", server.document(group, DeviceSync.SETTINGS).entries.getValue("notifications").value)

        // Another change on B uploads without fetching: B keeps the group's copy of what it keeps local.
        b.now = 3_500
        b.adapter.data["theme"] = "dark"
        val fetchesBefore = server.fetches.get()
        b.engine.syncLocalChange(DeviceSync.SETTINGS)
        assertEquals(fetchesBefore, server.fetches.get())
        assertEquals("on", server.document(group, DeviceSync.SETTINGS).entries.getValue("notifications").value)
        a.engine.syncNow()
        assertEquals("on", a.adapter.data["notifications"])

        // A change on A does not reach B.
        a.now = 4_000
        a.adapter.data["notifications"] = "on-again"
        a.engine.syncNow()
        b.engine.syncNow()
        assertEquals("off", b.adapter.data["notifications"])

        // Handing it back adopts the group's value.
        b.adapter.local.clear()
        b.engine.adoptGroupValue(DeviceSync.SETTINGS, "notifications")
        assertEquals("on-again", b.adapter.data["notifications"])
        assertEquals("on-again", server.document(group, DeviceSync.SETTINGS).entries.getValue("notifications").value)
    }

    @Test
    fun aLocalChangeUploadsWithoutListingAndCostsNothingWhenUnchanged() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["x"] = "1"
        val group = a.engine.create()
        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.engine.join(group.secret)

        val lists = server.lists.get()
        val fetches = server.fetches.get()
        val puts = server.puts.get()
        a.engine.syncLocalChange(DeviceSync.SETTINGS)
        assertEquals("unchanged: no request", fetches, server.fetches.get())
        assertEquals(puts, server.puts.get())

        a.now = 5_000
        a.adapter.data["x"] = "2"
        a.engine.syncLocalChange(DeviceSync.SETTINGS)
        assertEquals("no listing", lists, server.lists.get())
        assertEquals("one upload", puts + 1, server.puts.get())
        assertEquals("2", server.document(group, DeviceSync.SETTINGS).entries.getValue("x").value)
    }

    @Test
    fun anInlinePayloadNeedsNoFetch() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["x"] = "1"
        val group = a.engine.create()
        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.engine.join(group.secret)

        a.now = 5_000
        a.adapter.data["x"] = "2"
        a.engine.syncNow(listOf(DeviceSync.SETTINGS))
        val (version, blob) = server.blobs.getValue(DeviceSync.SETTINGS)
        val fetches = server.fetches.get()
        b.engine.syncNow(listOf(DeviceSync.SETTINGS), mapOf(DeviceSync.SETTINGS to version), mapOf(DeviceSync.SETTINGS to FetchResult.Blob(version, blob)))
        assertEquals("2", b.adapter.data["x"])
        assertEquals("the event carried the blob", fetches, server.fetches.get())
    }

    @Test
    fun aBlobThatDoesNotAuthenticateIsAPermanentFailure() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["x"] = "1"
        a.engine.create()
        server.blobs[DeviceSync.SETTINGS] = 9L to ByteArray(40) { 7 }
        val error = runCatching { a.engine.syncNow() }.exceptionOrNull()
        assertTrue("got $error", error is SyncPermanentException)
    }

    @Test
    fun aGroupNotUploadedYetIsNotDeletedByANotFound() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        server.deleted = true // every upload fails: the group never reaches the server
        runCatching { a.engine.create() }
        a.engine.onDeletedElsewhere(onlyIfConfirmed = true)
        assertTrue("still connected", a.state.group() != null)
    }

    @Test
    fun deletedElsewhereDisconnectsWithTheNotice() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data["x"] = "1"
        a.engine.create()
        a.engine.onDeletedElsewhere()
        assertNull(a.state.group())
        assertEquals(DeviceSync.NOTICE_DELETED_ELSEWHERE, a.state.snapshots.first().notice)
    }

    @Test
    fun differentCollectionsSyncSideBySide() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000, collections = listOf(DeviceSync.SETTINGS, DeviceSync.LIVE))
        a.adapters[0].data["x"] = "1"
        a.adapters[1].data["hunt/session"] = "s"
        val group = a.engine.create()
        assertEquals("s", server.document(group, DeviceSync.LIVE).entries.getValue("hunt/session").value)
        assertEquals("1", server.document(group, DeviceSync.SETTINGS).entries.getValue("x").value)
    }
}
