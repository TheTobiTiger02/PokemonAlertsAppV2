package com.example.pokemonalertsv2.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "godex_pending_updates")
data class GoDexPendingUpdateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryKey: String,
    val caught: Boolean,
    val timestamp: Long
)
