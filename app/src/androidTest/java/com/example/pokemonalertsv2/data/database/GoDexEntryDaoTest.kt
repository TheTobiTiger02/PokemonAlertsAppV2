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

    private fun entry(
        key: String,
        dex: Int,
        form: String? = null,
        needed: Boolean
    ) = GoDexEntryEntity(key, dex, form, "none", "Pokemon", needed)
}
