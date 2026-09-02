package com.example.pokemonalertsv2.data.counters

/** A special move suffix, independent of which species or moves have been released. */
internal data class PlusMove(val baseName: String, val count: Int)

private val plusMoveIdSuffix = Regex("_PLUS(?:_([1-9][0-9]*))?$", RegexOption.IGNORE_CASE)

internal fun parsePlusMove(raw: String?): PlusMove? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val literalCount = value.takeLastWhile { it == '+' }.length
    if (literalCount > 0) {
        val base = value.dropLast(literalCount).trimEnd()
        return base.takeIf { it.isNotEmpty() }?.let { PlusMove(it, literalCount) }
    }
    val suffix = plusMoveIdSuffix.find(value) ?: return null
    val base = value.take(suffix.range.first).takeIf { it.isNotBlank() } ?: return null
    val count = if (suffix.groupValues[1].isEmpty()) 1 else suffix.groupValues[1].toIntOrNull() ?: return null
    return PlusMove(base, count)
}
