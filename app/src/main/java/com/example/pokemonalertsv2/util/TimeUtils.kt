package com.example.pokemonalertsv2.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.FormatStyle
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object TimeUtils {
    private val parsers: List<(String) -> Long?> = listOf(
        // Epoch millis as number
        { s -> s.toLongOrNull() },
        // ISO_INSTANT (e.g., 2025-10-10T12:34:56Z)
        { s -> runCatching { Instant.parse(s).toEpochMilli() }.getOrNull() },
        // ISO_OFFSET_DATE_TIME (e.g., 2025-10-10T12:34:56+02:00)
        { s -> runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull() },
        // Common patterns without timezone - assume device default timezone
        { s -> parseWithFormatter(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) },
        { s -> parseWithFormatter(s, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")) },
        { s -> parseWithFormatter(s, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) },
        { s -> parseWithFormatter(s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) }
    )

    private fun parseWithFormatter(s: String, formatter: DateTimeFormatter): Long? {
        return try {
            val ldt = LocalDateTime.parse(s, formatter)
            ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) { null }
    }

    /**
     * Parses an endTime string into epoch millis. Returns null if unknown/invalid.
     */
    fun parseEndTimeToMillis(endTime: String?): Long? {
        val trimmed = endTime?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        for (p in parsers) {
            val v = runCatching { p(trimmed) }.getOrNull()
            if (v != null && v > 0) return v
        }
        return null
    }

    /**
     * Formats a remaining duration (millis) as a short string, e.g. "5m 12s" or "1h 03m".
     */
    fun formatDurationShort(remainingMs: Long): String {
        val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format("%dh %02dm", hours, minutes)
            minutes > 0 -> String.format("%dm %02ds", minutes, seconds)
            else -> String.format("%ds", seconds)
        }
    }

    /**
     * Formats how long ago something expired, e.g. "5 mins ago", "2 hours ago", "3 days ago".
     */
    fun formatTimeAgo(pastMs: Long): String {
        val elapsedMs = System.currentTimeMillis() - pastMs
        val totalSeconds = (elapsedMs / 1000).coerceAtLeast(0)
        
        val years = totalSeconds / (365 * 24 * 3600)
        val months = totalSeconds / (30 * 24 * 3600)
        val days = totalSeconds / (24 * 3600)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return when {
            years > 0 -> if (years == 1L) "1 year ago" else "$years years ago"
            months > 0 -> if (months == 1L) "1 month ago" else "$months months ago"
            days > 0 -> if (days == 1L) "1 day ago" else "$days days ago"
            hours > 0 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            minutes > 0 -> if (minutes == 1L) "1 min ago" else "$minutes mins ago"
            else -> if (seconds <= 5) "just now" else "$seconds secs ago"
        }
    }

    /** Formats an alert deadline in the device locale without exposing raw server timestamps. */
    fun formatAlertEndTime(endMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val zone = ZoneId.systemDefault()
        val end = Instant.ofEpochMilli(endMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val time = end.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        return when (end.toLocalDate()) {
            today -> "Ends today at $time"
            today.plusDays(1) -> "Ends tomorrow at $time"
            else -> "Ends ${end.format(DateTimeFormatter.ofPattern("EEE, d MMM"))} at $time"
        }
    }

    fun formatPostedTime(rawTimestamp: String?): String? =
        parseEndTimeToMillis(rawTimestamp)?.let { "Posted ${formatTimeAgo(it)}" }
}
