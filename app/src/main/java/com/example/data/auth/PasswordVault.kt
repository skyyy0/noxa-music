package com.example.data.auth

import java.security.Key
import java.security.SecureRandom
import javax.crypto.Mac

/**
 * Password hashing for the built-in account store.
 *
 * PBKDF2-HMAC-SHA256 implemented directly on top of [Mac] so it works on every
 * supported API level (java.util.Base64 and PBKDF2WithHmacSHA256 via
 * SecretKeyFactory both require API 26, while the app's minSdk is 24).
 * Passwords are never stored — only salted hashes.
 */
object PasswordVault {

    private const val ITERATIONS = 60_000
    private const val SALT_BYTES = 16
    private const val KEY_BYTES = 32

    fun newSalt(): ByteArray =
        ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(password: String, salt: ByteArray): ByteArray =
        pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_BYTES)

    /** Constant-time comparison of a fresh hash against the stored one. */
    fun verify(password: String, salt: ByteArray, expectedHash: ByteArray): Boolean {
        val actual = hash(password, salt)
        return java.security.MessageDigest.isEqual(actual, expectedHash)
    }

    /** Standard PBKDF2 (RFC 2898) over HMAC-SHA256. Internal for unit tests. */
    internal fun pbkdf2(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        dkLen: Int
    ): ByteArray {
        // PBKDF2 (RFC 2898): the password is the HMAC *key*, the salt travels
        // in the message: U1 = PRF(P, S || INT(block)), U_i = PRF(P, U_{i-1}).
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(RawHmacKey(String(password).toByteArray(Charsets.UTF_8)))
        val out = ByteArray(dkLen)
        var offset = 0
        var block = 1
        while (offset < dkLen) {
            mac.reset()
            var u = mac.doFinal(salt + intToBytes(block))
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) {
                    t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
                }
            }
            val take = minOf(t.size, dkLen - offset)
            System.arraycopy(t, 0, out, offset, take)
            offset += take
            block++
        }
        return out
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    /**
     * A bare key holding raw HMAC material. Equivalent to SecretKeySpec for
     * this use, but without depending on the javax.crypto.spec class name.
     */
    private class RawHmacKey(private val bytes: ByteArray) : Key {
        override fun getAlgorithm(): String = "HmacSHA256"
        override fun getEncoded(): ByteArray = bytes
        override fun getFormat(): String = "RAW"
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray =
        ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
}
