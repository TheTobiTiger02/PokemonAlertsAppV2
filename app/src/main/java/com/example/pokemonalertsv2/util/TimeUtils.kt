package com.example.pokemonalertsv2.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
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
}
