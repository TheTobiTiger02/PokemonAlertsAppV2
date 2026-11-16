package com.example.pokemonalertsv2.sync.live

/**
 * The decisions of the owner/mirror model that end or move a session, kept free of Android so
 * they can be tested on their own: a wrong answer here stops a hunt.
 */
internal object LiveRules {

    /** What a device running a session does about the group's current claim on it. */
    enum class ClaimVerdict {
        /** This device runs it: publish (and claim it if the group does not know yet). */
        KEEP,
        /** Another device took this same session over: stop here without ending it. */
        RELEASE,
        /** Another device started a session of its own since: end this one, as on one device. */
        REPLACE
    }

    /**
     * [claim] is the group's claim, [selfId] this device, [sessionStartedAtMillis] the session
     * running here and [localClaimedAtMillis] when this device claimed it.
     */
    fun claimVerdict(claim: LiveClaim?, selfId: String, sessionStartedAtMillis: Long, localClaimedAtMillis: Long): ClaimVerdict = when {
        claim == null || claim.owner.deviceId == selfId -> ClaimVerdict.KEEP
        // An older claim (this device took over or started since): it is overwritten by ours.
        claim.claimedAtMillis <= localClaimedAtMillis -> ClaimVerdict.KEEP
        claim.startedAtMillis == sessionStartedAtMillis -> ClaimVerdict.RELEASE
        else -> ClaimVerdict.REPLACE
    }

    /** A take-over's claim time: after the claim it replaces even when this device's clock is behind. */
    fun takeOverClaimedAt(nowMillis: Long, current: LiveClaim?): Long =
        maxOf(nowMillis, (current?.claimedAtMillis ?: 0L) + 1)

    private val TARGETED = setOf(LiveAction.GOT_IT, LiveAction.NEXT, LiveAction.PREVIOUS)

    /**
     * Whether the owner carries out [command] for the session it runs: meant for this device,
     * recent, for this session, and -- for Got it, Next and Previous -- for the target the sender
     * saw, so a double tap or a late command never retires the wrong one.
     */
    fun accepts(
        command: LiveCommand,
        selfId: String,
        sessionStartedAtMillis: Long?,
        currentTargetId: String?,
        nowMillis: Long,
        maxAgeMillis: Long
    ): Boolean {
        if (command.to != selfId) return false
        if (kotlin.math.abs(nowMillis - command.sentAtMillis) > maxAgeMillis) return false
        if (sessionStartedAtMillis == null) return false
        if (command.sessionStartedAtMillis != 0L && command.sessionStartedAtMillis != sessionStartedAtMillis) return false
        val action = runCatching { LiveAction.valueOf(command.action) }.getOrNull() ?: return false
        if (action in TARGETED && command.expectedTargetId != currentTargetId) return false
        return true
    }

    /**
     * Whether the device running a session has gone quiet: the stream has been open for
     * [quietMillis] and nothing came from it for as long (by this device's clock).
     */
    fun ownerOffline(heardAtMillis: Long?, streamConnectedAtMillis: Long?, nowMillis: Long, quietMillis: Long): Boolean {
        val connectedAt = streamConnectedAtMillis ?: return false
        val lastSign = maxOf(heardAtMillis ?: 0L, connectedAt)
        return nowMillis - lastSign > quietMillis
    }
}
