package com.example.pokemonalertsv2.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class UpdateChecksumTest {
    private val hash = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

    @Test
    fun readsTheHashFromSha256sumOutput() {
        assertEquals(hash, parseSha256("$hash  app/build/outputs/apk/release/app-release.apk\n"))
        assertEquals(hash, parseSha256(hash.uppercase()))
    }

    @Test
    fun rejectsMissingOrMalformedHashes() {
        assertNull(parseSha256(""))
        assertNull(parseSha256("not a hash"))
        assertNull(parseSha256(hash.dropLast(1)))
        assertNull(parseSha256(hash + "0"))
    }

    @Test
    fun hashesFileContents() {
        val file = File.createTempFile("update", ".apk")
        try {
            file.writeText("test")
            assertEquals(hash, sha256Hex(file))
        } finally {
            file.delete()
        }
    }
}
