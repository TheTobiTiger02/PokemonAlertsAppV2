package com.example.pokemonalertsv2.notifications

/**
 * Central notification id / PendingIntent request-code derivation.
 *
 * Every role gets its own namespaced hash instead of a base hash plus fixed offsets,
 * so two alerts whose hashes happen to sit near each other can no longer collide across
 * roles — an offset overlap silently retargeted a tap (or a cancel) to the wrong alert.
 * Posting, re-posting (snooze) and cancelling must all go through here to stay in sync.
 */
internal object AlertNotificationIds {
    private fun hashOf(vararg parts: String): Int = parts.joinToString("\u0000").hashCode()

    /** The notification id for a single alert; also the id used to cancel it. */
    fun forAlert(uniqueId: String): Int = hashOf("alert", uniqueId)

    /** The group-summary notification id for a channel. */
    fun forSummary(channelId: String): Int = hashOf("summary", channelId)

    /** A stable PendingIntent request code for [role] on a single alert. */
    fun forAlertAction(role: String, uniqueId: String): Int =
        hashOf("action", role, uniqueId)

    /** A stable PendingIntent request code for [role] on a channel summary. */
    fun forSummaryAction(role: String, channelId: String): Int =
        hashOf("action-summary", role, channelId)
}
