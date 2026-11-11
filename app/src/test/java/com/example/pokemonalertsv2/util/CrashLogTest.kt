package com.example.pokemonalertsv2.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CrashLogTest {
    @Test
    fun keepsTheFiveNewestReportsWithTheStackTrace() {
        val directory = Files.createTempDirectory("crashes").toFile()
        try {
            repeat(7) { index ->
                CrashLog.write(directory, Thread.currentThread(), IllegalStateException("boom $index"), 1_700_000_000_000L + index * 1_000L)
            }
            val files = directory.listFiles()!!.sortedBy { it.name }
            assertEquals(5, files.size)
            assertTrue(files.last().readText().contains("IllegalStateException: boom 6"))
            assertTrue(files.first().readText().contains("boom 2"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
