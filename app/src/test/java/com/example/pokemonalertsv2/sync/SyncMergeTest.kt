package com.example.pokemonalertsv2.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private fun doc(vararg entries: Pair<String, SyncEntry>) = SyncDocument(entries = mapOf(*entries))
    private fun value(t: Long, d: String, v: String) = SyncEntry(t = t, d = d, value = v)
    private fun tomb(t: Long, d: String) = SyncEntry(t = t, d = d, x = true)

    @Test
    fun newerWins() {
        val merged = SyncMerge.merge(doc("a" to value(10, "A", "old")), doc("a" to value(20, "B", "new")))
        assertEquals("new", merged.entries.getValue("a").value)
        val reversed = SyncMerge.merge(doc("a" to value(30, "A", "mine")), doc("a" to value(20, "B", "theirs")))
        assertEquals("mine", reversed.entries.getValue("a").value)
    }

    @Test
    fun tieIsBrokenByDeviceId() {
        val merged = SyncMerge.merge(doc("a" to value(10, "A", "from A")), doc("a" to value(10, "B", "from B")))
        assertEquals("from B", merged.entries.getValue("a").value)
        val other = SyncMerge.merge(doc("a" to value(10, "B", "from B")), doc("a" to value(10, "A", "from A")))
        assertEquals("the same answer on both devices", "from B", other.entries.getValue("a").value)
    }

    @Test
    fun tombstoneBeatsOlderValueAndLosesToNewer() {
        assertTrue(SyncMerge.merge(doc("a" to tomb(20, "A")), doc("a" to value(10, "B", "v"))).entries.getValue("a").isTombstone)
        assertEquals("v", SyncMerge.merge(doc("a" to tomb(20, "A")), doc("a" to value(30, "B", "v"))).entries.getValue("a").value)
    }

    @Test
    fun itemsOnOneSideOnlyAreKept() {
        val merged = SyncMerge.merge(doc("mine" to value(1, "A", "1")), doc("theirs" to value(1, "B", "2")))
        assertEquals(setOf("mine", "theirs"), merged.entries.keys)
    }

    @Test
    fun diffStampsChangesAndKeepsUnchangedOwnership() {
        val shadow = SyncMerge.shadowOf(doc("same" to value(5, "B", "s"), "changed" to value(5, "B", "old"), "gone" to value(5, "B", "g")), 3, false)
        val diff = SyncMerge.diffAgainstShadow(mapOf("same" to "s", "changed" to "new", "added" to "n"), shadow, "A") { 100 }
        assertTrue(diff.changed)
        val e = diff.document.entries
        assertEquals(SyncEntry(5, "B", value = "s"), e["same"])
        assertEquals(SyncEntry(100, "A", value = "new"), e["changed"])
        assertEquals(SyncEntry(100, "A", value = "n"), e["added"])
        assertTrue(e.getValue("gone").isTombstone)
        assertEquals(100L, e.getValue("gone").t)

        val unchanged = SyncMerge.diffAgainstShadow(mapOf("same" to "s"), SyncMerge.shadowOf(doc("same" to value(5, "B", "s")), 1, false), "A") { 100 }
        assertFalse(unchanged.changed)
    }

    @Test
    fun joinBaselineLosesToGroupAndKeepsLocalOnlyItems() {
        val local = mapOf("shared" to "joiner value", "history/1" to "joiner hunt")
        val baseline = SyncMerge.joinBaseline(local, "J")
        val diff = SyncMerge.diffAgainstShadow(local, baseline, "J") { 500 }
        assertFalse("joining is not a local change", diff.changed)
        val group = doc("shared" to value(1, "A", "group value"), "history/2" to value(1, "A", "group hunt"))
        val merged = SyncMerge.merge(diff.document, group)
        assertEquals("group value", merged.entries.getValue("shared").value)
        assertEquals("joiner hunt", merged.entries.getValue("history/1").value)
        assertEquals("group hunt", merged.entries.getValue("history/2").value)
        val (upserts, deletes) = SyncMerge.changesToApply(local, merged)
        assertEquals(mapOf("shared" to "group value", "history/2" to "group hunt"), upserts)
        assertTrue(deletes.isEmpty())
        assertFalse("the joiner's own item must reach the group", SyncMerge.sameContent(merged, group))
    }

    @Test
    fun clockBehindDeviceStillOrdersAfterWhatItHasSeen() {
        // Device B's clock says 1_000, but it has already merged a change stamped 5_000.
        val highestSeen = 5_000L
        val stamp = maxOf(1_000L, highestSeen + 1)
        val seen = doc("a" to value(5_000, "A", "from A"))
        val diff = SyncMerge.diffAgainstShadow(mapOf("a" to "from B"), SyncMerge.shadowOf(seen, 1, false), "B") { stamp }
        val merged = SyncMerge.merge(diff.document, seen)
        assertEquals("from B", merged.entries.getValue("a").value)
    }

    @Test
    fun oldTombstonesArePruned() {
        val now = 1_000L * 24 * 60 * 60 * 1000
        val pruned = SyncMerge.pruneTombstones(
            doc(
                "old" to tomb(now - SyncMerge.TOMBSTONE_TTL_MILLIS - 1, "A"),
                "recent" to tomb(now - 1000, "A"),
                "oldValue" to value(1, "A", "kept")
            ),
            now
        )
        assertEquals(setOf("recent", "oldValue"), pruned.entries.keys)
    }

    @Test
    fun documentRoundTripsThroughJson() {
        val document = doc("a" to value(1, "A", "x"), "b" to tomb(2, "B"))
        val decoded = SyncMerge.decode(SyncMerge.encode(document))
        assertTrue(SyncMerge.sameContent(document, decoded))
        assertTrue("wire format names", SyncMerge.encode(document).decodeToString().contains("\"val\":\"x\""))
        assertTrue(SyncMerge.encode(document).decodeToString().contains("\"v\":1"))
    }
}
