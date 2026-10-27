package com.example

import com.example.data.auth.PasswordVault
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PBKDF2 hashing behind the built-in (keyless) account store. */
class PasswordVaultTest {

    @Test
    fun pbkdf2MatchesKnownTestVectors() {
        // Official PBKDF2-HMAC-SHA256 vectors (RFC 6070 companions for SHA-256).
        fun vector(password: String, salt: String, iterations: Int): String {
            val derived = PasswordVault.pbkdf2(
                password.toCharArray(),
                salt.toByteArray(Charsets.UTF_8),
                iterations,
                dkLen = 32
            )
            return PasswordVault.toHex(derived)
        }

        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            vector("password", "salt", 1)
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            vector("password", "salt", 2)
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            vector("password", "salt", 4096)
        )
    }

    @Test
    fun hashAndVerifyRoundTrip() {
        val salt = PasswordVault.newSalt()
        val hash = PasswordVault.hash("rahasia123", salt)
        assertTrue(PasswordVault.verify("rahasia123", salt, hash))
        assertFalse(PasswordVault.verify("rahasia124", salt, hash))
        assertFalse(PasswordVault.verify("", salt, hash))
    }

    @Test
    fun samePasswordDifferentSaltsProduceDifferentHashes() {
        val first = PasswordVault.hash("rahasia123", PasswordVault.newSalt())
        val second = PasswordVault.hash("rahasia123", PasswordVault.newSalt())
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun hexEncodingRoundTrips() {
        val bytes = ByteArray(32) { it.toByte() }
        assertArrayEquals(bytes, PasswordVault.fromHex(PasswordVault.toHex(bytes)))
    }
}
