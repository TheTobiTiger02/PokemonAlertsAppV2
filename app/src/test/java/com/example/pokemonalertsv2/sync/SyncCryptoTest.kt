package com.example.pokemonalertsv2.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncCryptoTest {
    private val group = SyncGroup(ByteArray(32) { it.toByte() })

    @Test
    fun derivedValuesAreStableAndDistinct() {
        assertEquals(32, group.groupId.length)
        assertTrue(group.groupId.matches(Regex("[0-9a-f]{32}")))
        assertEquals(group.groupId, SyncGroup(ByteArray(32) { it.toByte() }).groupId)
        assertNotEquals(group.groupId, SyncGroup(ByteArray(32) { (it + 1).toByte() }).groupId)
        assertEquals(43, group.authToken.length)
        assertEquals("devsync-${group.groupId}", group.topic)
    }

    @Test
    fun hkdfMatchesRfc5869TestCase3() {
        // RFC 5869 A.3: IKM = 22 x 0x0b, no salt, no info.
        val okm = SyncCrypto.hkdf(ByteArray(22) { 0x0b }, "", 42)
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            okm.toHex()
        )
    }

    @Test
    fun sealOpenRoundTrip() {
        val plain = "{\"v\":1,\"entries\":{}}".repeat(50).toByteArray()
        val aad = SyncCrypto.aad(group.groupId, "settings")
        val sealed = SyncCrypto.seal(plain, group.encKey, aad)
        assertArrayEquals(plain, SyncCrypto.open(sealed, group.encKey, aad))
        assertTrue("gzip shrinks repetitive JSON", sealed.size < plain.size)
    }

    @Test
    fun wrongKeyFails() {
        val aad = SyncCrypto.aad(group.groupId, "settings")
        val sealed = SyncCrypto.seal("secret".toByteArray(), group.encKey, aad)
        val other = SyncGroup(ByteArray(32) { 7 })
        expectFailure { SyncCrypto.open(sealed, other.encKey, aad) }
    }

    @Test
    fun aadMismatchFails() {
        val sealed = SyncCrypto.seal("secret".toByteArray(), group.encKey, SyncCrypto.aad(group.groupId, "settings"))
        expectFailure { SyncCrypto.open(sealed, group.encKey, SyncCrypto.aad(group.groupId, "signins")) }
    }

    @Test
    fun tamperedBlobFails() {
        val aad = SyncCrypto.aad(group.groupId, "settings")
        val sealed = SyncCrypto.seal("secret".toByteArray(), group.encKey, aad)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()
        expectFailure { SyncCrypto.open(sealed, group.encKey, aad) }
    }

    /** The same vector is asserted by the backend (src/sync_crypto.test.js), which derives these for accounts. */
    @Test
    fun derivationMatchesTheBackend() {
        val fixed = SyncGroup(ByteArray(32) { it.toByte() })
        assertEquals("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8", SyncCrypto.base64Url(fixed.secret))
        assertEquals("b51013189306b062e3359f4ea6e69b76", fixed.groupId)
        assertEquals("1uL66wwD7ge0nIloifOOuUQ6eOju0WG-GjWIUm-Vy9g", fixed.authToken)
    }

    /** The stored form of the secret (SyncStateStore), unchanged so existing installs keep their group. */
    @Test
    fun storedCodeRoundTripsAndCatchesTypos() {
        val code = group.pairingCode
        assertTrue(code.startsWith("P1"))
        assertArrayEquals(group.secret, SyncCrypto.decodePairingCode(code))
        assertArrayEquals("whitespace is ignored", group.secret, SyncCrypto.decodePairingCode("  ${code.chunked(8).joinToString(" ")}\n"))

        val typo = code.substring(0, 10) + (if (code[10] == 'A') 'B' else 'A') + code.substring(11)
        assertNull("a changed character fails the checksum", SyncCrypto.decodePairingCode(typo))
        assertNull("truncated", SyncCrypto.decodePairingCode(code.dropLast(1)))
        assertNull("wrong version prefix", SyncCrypto.decodePairingCode("P2" + code.drop(2)))
        assertNull(SyncCrypto.decodePairingCode(""))
        assertNull(SyncCrypto.decodePairingCode("hello"))
    }

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("expected a failure")
        } catch (expected: Exception) {
            // GCM authentication failed, as it should
        }
    }
}
