package com.example.pokemonalertsv2.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.FormatStyle
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object TimeUtils {
    // Bare numbers below these bounds are not real timestamps (e.g. a date encoded as
    // yyyymmdd would land in 1970 and read as instantly expired), so reject them.
    private const val MIN_EPOCH_MILLIS = 946_684_800_000L   // 2000-01-01
    private const val MAX_EPOCH_SECONDS = 32_503_680_000L    // 3000-01-01

    private val parsers: List<(String) -> Long?> = listOf(
        // Epoch millis (or seconds, scaled up) as a bare number
        { s ->
            s.toLongOrNull()?.let { n ->
                when {
                    n >= MIN_EPOCH_MILLIS -> n
                    n in MIN_EPOCH_MILLIS / 1000..MAX_EPOCH_SECONDS -> n * 1000
                    else -> null
                }
            }
        },
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
     *
     * Results are memoized per raw string: the same timestamps are re-parsed by every
     * pipeline pass (feed filter, distance decoration, category counts, map filter) on
     * each sync, which with 1000+ live alerts turned repeated parsing into real jank.
     */
    fun parseEndTimeToMillis(endTime: String?): Long? {
        val trimmed = endTime?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        synchronized(endTimeParseCache) {
            endTimeParseCache[trimmed]?.let { return it }
        }
        val parsed = parseEndTimeUncached(trimmed)
        synchronized(endTimeParseCache) {
            if (endTimeParseCache.size >= END_TIME_CACHE_LIMIT) {
                val iter = endTimeParseCache.entries.iterator()
                repeat(END_TIME_CACHE_LIMIT / 4) {
                    if (iter.hasNext()) {
                        iter.next()
                        iter.remove()
                    }
                }
            }
            endTimeParseCache[trimmed] = parsed
        }
        return parsed
    }

    private fun parseEndTimeUncached(trimmed: String): Long? {
        for (p in parsers) {
            val v = runCatching { p(trimmed) }.getOrNull()
            if (v != null && v > 0) return v
        }
        return null
    }

    /** LinkedHashMap in insertion order doubles as a simple FIFO-bounded memo. */
    private val endTimeParseCache = LinkedHashMap<String, Long?>()
    private const val END_TIME_CACHE_LIMIT = 4_096

    /**
     * Formats a remaining duration (millis) as a short string, e.g. "5m 12s" or "1h 03m".
     */
    fun formatDurationShort(remainingMs: Long): String {
        // Seconds round *up*, the way a countdown reads: with 400 ms left the label says
        // "1s" for that last real second instead of sitting on "0s" until the caller's own
        // `remaining <= 0` check swaps in "EXPIRED".
        val totalSeconds = if (remainingMs <= 0L) 0L else (remainingMs + 999) / 1000
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

    /** Formats a server timestamp as a localized date and time. */
    fun formatTimestamp(rawTimestamp: String?): String? {
        val timestamp = parseEndTimeToMillis(rawTimestamp) ?: return null
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(
                DateTimeFormatter.ofLocalizedDateTime(
                    FormatStyle.MEDIUM,
                    FormatStyle.SHORT
                )
            )
    }
}
