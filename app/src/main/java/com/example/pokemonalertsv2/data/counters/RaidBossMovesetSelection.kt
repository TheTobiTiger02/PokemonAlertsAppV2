package com.example.pokemonalertsv2.data.counters

import androidx.compose.runtime.Immutable

/** Explicit selection state for the average result versus one concrete boss moveset. */
@Immutable
sealed interface RaidBossMovesetSelection {
    @Immutable
    data object Average : RaidBossMovesetSelection

    @Immutable
    data class Exact(val moveset: RaidBossMoveset) : RaidBossMovesetSelection
}

fun RaidBossMovesetSelection.toMovesetOrNull(): RaidBossMoveset? = when (this) {
    RaidBossMovesetSelection.Average -> null
    is RaidBossMovesetSelection.Exact -> moveset
}

fun RaidBossMoveset?.toSelection(): RaidBossMovesetSelection =
    if (this == null || isRandom) RaidBossMovesetSelection.Average
    else RaidBossMovesetSelection.Exact(this)
