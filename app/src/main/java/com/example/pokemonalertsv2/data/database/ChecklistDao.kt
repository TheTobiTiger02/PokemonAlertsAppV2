package com.example.pokemonalertsv2.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklists ORDER BY sortOrder, createdAt")
    fun observeChecklists(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklists ORDER BY sortOrder, createdAt")
    suspend fun checklists(): List<ChecklistEntity>

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun checklist(id: String): ChecklistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklist(checklist: ChecklistEntity)

    @Query("SELECT * FROM checklist_entries WHERE checklistId = :checklistId ORDER BY pokedexId, entryKey")
    fun observeEntries(checklistId: String): Flow<List<ChecklistEntryEntity>>

    @Query("SELECT * FROM checklist_entries WHERE checklistId = :checklistId ORDER BY pokedexId, entryKey")
    suspend fun entries(checklistId: String): List<ChecklistEntryEntity>

    /** Every entry of every checklist that badges alerts. */
    @Query(
        """
        SELECT e.* FROM checklist_entries e JOIN checklists c ON c.id = e.checklistId
        WHERE c.useForAlerts = 1 ORDER BY e.checklistId, e.pokedexId, e.entryKey
        """
    )
    fun observeAlertEntries(): Flow<List<ChecklistEntryEntity>>

    /** Entries of checklists made in the app: device sync carries them. */
    @Query(
        """
        SELECT e.* FROM checklist_entries e JOIN checklists c ON c.id = e.checklistId
        WHERE c.source = 'LOCAL' ORDER BY e.checklistId, e.entryKey
        """
    )
    fun observeLocalEntries(): Flow<List<ChecklistEntryEntity>>

    /** Needed and total per checklist, for the list of checklists. */
    @Query("SELECT checklistId, SUM(needed) AS needed, COUNT(*) AS total FROM checklist_entries GROUP BY checklistId")
    fun observeProgress(): Flow<List<ChecklistProgressRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<ChecklistEntryEntity>)

    @Query("DELETE FROM checklist_entries WHERE checklistId = :checklistId")
    suspend fun clearEntries(checklistId: String)

    @Query("DELETE FROM checklist_entries WHERE checklistId = :checklistId AND entryKey IN (:entryKeys)")
    suspend fun deleteEntries(checklistId: String, entryKeys: Collection<String>)

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun deleteChecklistRow(id: String)

    @Transaction
    suspend fun replaceEntries(checklistId: String, entries: List<ChecklistEntryEntity>) {
        clearEntries(checklistId)
        upsertEntries(entries)
    }

    @Transaction
    suspend fun deleteChecklist(id: String) {
        clearEntries(id)
        deleteChecklistRow(id)
    }

    @Query("UPDATE checklist_entries SET needed = :needed, updatedAt = :now WHERE checklistId = :checklistId AND entryKey IN (:entryKeys) AND needed != :needed")
    suspend fun setNeeded(checklistId: String, entryKeys: Collection<String>, needed: Boolean, now: Long): Int
}

data class ChecklistProgressRow(val checklistId: String, val needed: Int, val total: Int)
