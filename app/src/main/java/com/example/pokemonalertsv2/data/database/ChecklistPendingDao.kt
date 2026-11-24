package com.example.pokemonalertsv2.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Marks on the account's goDex checklists waiting to be written back, with the editor's
 * set and undo, as [GoDexEntryDao] does them for the Hundo collection.
 */
@Dao
interface ChecklistPendingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(update: ChecklistPendingEntity)

    @Query("SELECT * FROM checklist_pending WHERE checklistId = :checklistId AND entryKey = :entryKey")
    suspend fun get(checklistId: String, entryKey: String): ChecklistPendingEntity?

    @Query("SELECT * FROM checklist_pending ORDER BY timestamp ASC, checklistId, entryKey LIMIT 1")
    suspend fun next(): ChecklistPendingEntity?

    @Query("SELECT * FROM checklist_pending WHERE checklistId = :checklistId")
    suspend fun forChecklist(checklistId: String): List<ChecklistPendingEntity>

    /** Device sync and the editor: every waiting mark. */
    @Query("SELECT * FROM checklist_pending ORDER BY checklistId, entryKey")
    fun observeAll(): Flow<List<ChecklistPendingEntity>>

    @Query("SELECT * FROM checklist_pending ORDER BY checklistId, entryKey")
    suspend fun all(): List<ChecklistPendingEntity>

    @Query("DELETE FROM checklist_pending WHERE checklistId = :checklistId AND entryKey = :entryKey AND revision = :revision")
    suspend fun deleteIfRevision(checklistId: String, entryKey: String, revision: Long): Int

    @Query("DELETE FROM checklist_pending WHERE checklistId = :checklistId AND entryKey = :entryKey")
    suspend fun delete(checklistId: String, entryKey: String)

    @Query("DELETE FROM checklist_pending WHERE checklistId = :checklistId")
    suspend fun clear(checklistId: String)

    @Query(
        """
        UPDATE checklist_pending SET attemptCount = attemptCount + 1, lastError = :error
        WHERE checklistId = :checklistId AND entryKey = :entryKey AND revision = :revision
        """
    )
    suspend fun recordFailure(checklistId: String, entryKey: String, revision: Long, error: String): Int

    @Query("SELECT needed FROM checklist_entries WHERE checklistId = :checklistId AND entryKey = :entryKey")
    suspend fun neededOf(checklistId: String, entryKey: String): Boolean?

    @Query("UPDATE checklist_entries SET needed = :needed, updatedAt = :now WHERE checklistId = :checklistId AND entryKey = :entryKey")
    suspend fun updateNeeded(checklistId: String, entryKey: String, needed: Boolean, now: Long)

    @Query("DELETE FROM checklist_entries WHERE checklistId = :checklistId")
    suspend fun clearEntries(checklistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<ChecklistEntryEntity>)

    @Transaction
    suspend fun setDesiredState(checklistId: String, entryKey: String, caught: Boolean, now: Long) {
        val revision = maxOf(now, (get(checklistId, entryKey)?.revision ?: 0L) + 1L)
        updateNeeded(checklistId, entryKey, !caught, now)
        insert(ChecklistPendingEntity(checklistId, entryKey, caught, revision, now))
    }

    /** Marks entries caught or needed; returns what each was before, for [undoDesiredStates]. */
    @Transaction
    suspend fun setDesiredStates(checklistId: String, entryKeys: Collection<String>, caught: Boolean, now: Long): List<GoDexMarkUndo> =
        entryKeys.mapNotNull { entryKey ->
            val neededBefore = neededOf(checklistId, entryKey) ?: return@mapNotNull null
            if (neededBefore == !caught) return@mapNotNull null
            val previous = get(checklistId, entryKey)?.asGoDexPending()
            setDesiredState(checklistId, entryKey, caught, now)
            GoDexMarkUndo(entryKey, neededBefore, previous)
        }

    /** A mark not sent yet is dropped (or the earlier one restored); one already sent is reversed. */
    @Transaction
    suspend fun undoDesiredStates(checklistId: String, changes: List<GoDexMarkUndo>, now: Long) {
        changes.forEach { change ->
            if (get(checklistId, change.entryKey) == null) {
                setDesiredState(checklistId, change.entryKey, caught = !change.neededBefore, now = now)
            } else {
                updateNeeded(checklistId, change.entryKey, change.neededBefore, now)
                val previous = change.previous
                if (previous == null) delete(checklistId, change.entryKey)
                else insert(ChecklistPendingEntity(checklistId, previous.entryKey, previous.caught, previous.revision, previous.timestamp, previous.attemptCount, previous.lastError))
            }
        }
    }

    /** Device sync: a mark made on another device, shown here at once and sent from either. */
    @Transaction
    suspend fun applySynced(update: ChecklistPendingEntity) {
        updateNeeded(update.checklistId, update.entryKey, !update.caught, update.timestamp)
        insert(update.copy(attemptCount = 0, lastError = null))
    }

    /** A fresh import, with marks not yet written back kept on top. */
    @Transaction
    suspend fun replaceEntriesPreservingPending(checklistId: String, entries: List<ChecklistEntryEntity>) {
        val desired = forChecklist(checklistId).associate { it.entryKey to it.caught }
        clearEntries(checklistId)
        insertEntries(entries.map { entry -> desired[entry.entryKey]?.let { entry.copy(needed = !it) } ?: entry })
    }
}
