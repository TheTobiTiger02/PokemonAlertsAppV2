package com.example.pokemonalertsv2.sync.live

import com.example.pokemonalertsv2.sync.live.LiveRules.ClaimVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRulesTest {
    private val a = LiveOwner("A", "Pixel")
    private val b = LiveOwner("B", "Galaxy")

    @Test
    fun claimVerdicts() {
        assertEquals("nothing claimed yet", ClaimVerdict.KEEP, LiveRules.claimVerdict(null, "A", 100, 100))
        assertEquals("our own claim", ClaimVerdict.KEEP, LiveRules.claimVerdict(LiveClaim(a, 100, 100), "A", 100, 100))
        assertEquals("B took this hunt over", ClaimVerdict.RELEASE, LiveRules.claimVerdict(LiveClaim(b, 100, 500), "A", 100, 100))
        assertEquals("B started a hunt of its own since", ClaimVerdict.REPLACE, LiveRules.claimVerdict(LiveClaim(b, 400, 400), "A", 100, 100))
        assertEquals("B's claim is older than ours: we took it back", ClaimVerdict.KEEP, LiveRules.claimVerdict(LiveClaim(b, 100, 500), "A", 100, 600))
        assertEquals("a hunt B ended before ours started", ClaimVerdict.KEEP, LiveRules.claimVerdict(LiveClaim(b, 50, 50), "A", 100, 100))
    }

    @Test
    fun takeOverAlwaysOrdersAfterTheClaimItReplaces() {
        assertEquals(1_000L, LiveRules.takeOverClaimedAt(1_000, LiveClaim(a, 100, 500)))
        assertEquals("a clock behind the other device", 5_001L, LiveRules.takeOverClaimedAt(1_000, LiveClaim(a, 100, 5_000)))
        assertEquals(1_000L, LiveRules.takeOverClaimedAt(1_000, null))
    }

    private fun command(action: LiveAction, expected: String? = "t1", started: Long = 100, sent: Long = 10_000, to: String = "A") =
        LiveCommand("id", to, "B", LiveCommand.SESSION_HUNT, action.name, null, started, expected, null, sent)

    @Test
    fun commandsAreCheckedAgainstWhatTheSenderSaw() {
        fun accepts(c: LiveCommand, target: String? = "t1", started: Long? = 100) = LiveRules.accepts(c, "A", started, target, 12_000, 60_000)
        assertTrue(accepts(command(LiveAction.GOT_IT)))
        assertFalse("the target moved on: no catch for the wrong one", accepts(command(LiveAction.GOT_IT), target = "t2"))
        assertFalse(accepts(command(LiveAction.NEXT), target = "t2"))
        assertTrue("pause does not depend on the target", accepts(command(LiveAction.PAUSE), target = "t2"))
        assertFalse("another hunt", accepts(command(LiveAction.PAUSE, started = 99)))
        assertFalse("no hunt here", accepts(command(LiveAction.PAUSE), started = null))
        assertFalse("for another device", accepts(command(LiveAction.PAUSE, to = "C")))
        assertFalse("too old", LiveRules.accepts(command(LiveAction.PAUSE, sent = 0), "A", 100, "t1", 120_000, 60_000))
    }

    @Test
    fun ownerOfflineOnlyAfterTheStreamHasBeenOpenAWhile() {
        assertFalse("stream closed: nothing to judge", LiveRules.ownerOffline(null, null, 100_000, 45_000))
        assertFalse("stream just opened, presence not in yet", LiveRules.ownerOffline(null, 90_000, 100_000, 45_000))
        assertTrue("open long enough, never heard", LiveRules.ownerOffline(null, 10_000, 100_000, 45_000))
        assertFalse("heard recently", LiveRules.ownerOffline(80_000, 10_000, 100_000, 45_000))
        assertTrue("quiet too long", LiveRules.ownerOffline(40_000, 10_000, 100_000, 45_000))
    }
}
