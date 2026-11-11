package com.example.pokemonalertsv2.data.counters

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** A concrete raid boss moveset returned by Pokebattler. */
@Serializable
@Immutable
data class RaidBossMoveset(
    val move1: String? = null,
    val move2: String? = null
) {
    val isRandom: Boolean
        get() = move1.isNullOrBlank() && move2.isNullOrBlank()

    val displayName: String
        get() = if (isRandom) {
            "Average moveset"
        } else {
            listOfNotNull(prettifyMoveName(move1), prettifyMoveName(move2)).joinToString(" / ")
        }

    fun cacheToken(): String = if (isRandom) "AVERAGE" else "${move1.orEmpty()}|${move2.orEmpty()}"
}

