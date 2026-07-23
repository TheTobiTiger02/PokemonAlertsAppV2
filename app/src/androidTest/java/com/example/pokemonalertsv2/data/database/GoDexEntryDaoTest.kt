package com.example.pokemonalertsv2.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoDexEntryDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    ).build()
    private val dao = database.goDexEntryDao()

    @After
    fun closeDatabase() = database.close()

    @Test
    fun replaceAllRemovesOldCollectionAndPublishesNewCollection() = runBlocking {
        dao.replaceAll(listOf(entry("0001-none", 1, needed = false)))
        dao.replaceAll(
            listOf(
                entry("0025-none", 25, needed = true),
                entry("0026_alola-none", 26, form = "alola", needed = false)
            )
        )

        assertEquals(listOf("0025-none", "0026_alola-none"), dao.getAll().map { it.entryKey })
        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun latestDesiredStateCoalescesAndSurvivesInboundReplacement() = runBlocking {
        dao.replaceAll(listOf(entry("0025-none", 25, needed = true)))

        dao.setDesiredState("0025-none", caught = true, now = 100L)
        val firstRevision = dao.getPendingUpdate("0025-none")!!.revision
        dao.setDesiredState("0025-none", caught = false, now = 100L)

        val pending = dao.getPendingUpdates()
        assertEquals(1, pending.size)
        assertEquals(false, pending.single().caught)
        assertEquals(firstRevision + 1L, pending.single().revision)

        dao.replaceAllPreservingPending(listOf(entry("0025-none", 25, needed = false)))
        assertEquals(true, dao.getAll().single().needed)
        assertEquals(1, dao.getPendingUpdates().size)
    }

    @Test
    fun acknowledgementOnlyDeletesMatchingRevision() = runBlocking {
        dao.replaceAll(listOf(entry("0025-none", 25, needed = true)))
        dao.setDesiredState("0025-none", caught = true, now = 100L)
        val oldRevision = dao.getPendingUpdate("0025-none")!!.revision
        dao.setDesiredState("0025-none", caught = false, now = 101L)

        assertEquals(0, dao.deletePendingUpdateIfRevision("0025-none", oldRevision))
        assertEquals(false, dao.getPendingUpdate("0025-none")!!.caught)
    }

    private fun entry(
        key: String,
        dex: Int,
        form: String? = null,
        needed: Boolean
    ) = GoDexEntryEntity(key, dex, form, "none", "Pokemon", needed)
}
