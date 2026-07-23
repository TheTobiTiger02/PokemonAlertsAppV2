package com.example.pokemonalertsv2.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "godex_pending_updates")
data class GoDexPendingUpdateEntity(
    @PrimaryKey val entryKey: String,
    val caught: Boolean,
    val revision: Long,
    val timestamp: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null
)
