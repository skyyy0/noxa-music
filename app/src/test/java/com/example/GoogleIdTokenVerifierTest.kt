package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.auth.GoogleIdTokenVerifier
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * Proves the keyless Google Sign-In path end to end.
 *
 * Google's JWKS (`/oauth2/v3/certs`) hands out bare RSA keys with **no**
 * `x5c` chain — the exact shape built here — so this test guards the
 * `n`/`e` modulus parsing, the RS256 signature check and every claim check
 * (iss/aud/exp) with a self-made key. A regression here is exactly the
 * "picker returns and nothing happens" bug.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class GoogleIdTokenVerifierTest {

    private val audience = "421601545057-r3rtjg045701q19dbab8md91v5t00ktp.apps.googleusercontent.com"

    private fun b64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** A Google-shaped JWKS: `kty`/`kid`/`n`/`e`, deliberately no `x5c`. */
    private fun jwksFor(publicKey: RSAPublicKey, kid: String): String {
        val jwk = JSONObject()
            .put("kty", "RSA")
            .put("use", "sig")
            .put("alg", "RS256")
            .put("kid", kid)
            .put("n", b64Url(publicKey.modulus.toByteArray()))
            .put("e", b64Url(publicKey.publicExponent.toByteArray()))
        return JSONObject().put("keys", JSONArray().put(jwk)).toString()
    }

    /** A properly signed Google-style ID token (RS256 over header.payload). */
    private fun signedToken(
        privateKey: java.security.PrivateKey,
        kid: String,
        audience: String,
        issuer: String = "accounts.google.com",
        expOffsetSeconds: Long = 3600L,
        tamperSignature: Boolean = false
    ): String {
        val header = JSONObject().put("alg", "RS256").put("typ", "JWT").put("kid", kid)
        val now = System.currentTimeMillis() / 1000
        val payload = JSONObject()
            .put("iss", issuer)
            .put("aud", audience)
            .put("sub", "118947235908473680291")
            .put("email", "sky@noxa.com")
            .put("name", "Sky")
            .put("picture", "https://example.com/photo.jpg")
            .put("iat", now)
            .put("exp", now + expOffsetSeconds)
        val signingInput = b64Url(header.toString().toByteArray(Charsets.UTF_8)) +
            "." + b64Url(payload.toString().toByteArray(Charsets.UTF_8))
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(signingInput.toByteArray(Charsets.US_ASCII))
        val signature = signer.sign()
        val encoded = if (tamperSignature) {
            b64Url(signature.copyOf().also { it[0] = (it[0].toInt() xor 0x5A).toByte() })
        } else {
            b64Url(signature)
        }
        return "$signingInput.$encoded"
    }

    private fun keyPair() = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.generateKeyPair()

    @Test
    fun googleStyleJwksWithoutX5cVerifies() {
        val pair = keyPair()
        val public = pair.public as RSAPublicKey
        val keys = GoogleIdTokenVerifier.parseJwks(jwksFor(public, "test-key-1"))
        assertEquals("n/e JWK must parse into a key", setOf("test-key-1"), keys.keys)

        val identity = GoogleIdTokenVerifier.verifyWithKeys(
            signedToken(pair.private, "test-key-1", audience = audience),
            audience,
            keys
        )
        assertNotNull("token signed by the parsed key must verify", identity)
        assertEquals("118947235908473680291", identity!!.subject)
        assertEquals("sky@noxa.com", identity.email)
        assertEquals("Sky", identity.name)
        assertEquals("https://example.com/photo.jpg", identity.pictureUrl)
    }

    @Test
    fun unknownKidFallsBackToSoleKey() {
        val pair = keyPair()
        val keys = GoogleIdTokenVerifier.parseJwks(
            jwksFor(pair.public as RSAPublicKey, "rotated-kid")
        )
        // Token without a kid: a one-key set is still unambiguous.
        val token = signedToken(pair.private, kid = "", audience = audience)
        val identity = GoogleIdTokenVerifier.verifyWithKeys(token, audience, keys)
        assertNotNull(identity)
    }

    @Test
    fun tamperedSignatureIsRejected() {
        val pair = keyPair()
        val keys = GoogleIdTokenVerifier.parseJwks(
            jwksFor(pair.public as RSAPublicKey, "test-key-1")
        )
        val token = signedToken(pair.private, "test-key-1", audience = audience, tamperSignature = true)
        assertNull(GoogleIdTokenVerifier.verifyWithKeys(token, audience, keys))
    }

    @Test
    fun wrongAudienceIsRejected() {
        val pair = keyPair()
        val keys = GoogleIdTokenVerifier.parseJwks(
            jwksFor(pair.public as RSAPublicKey, "test-key-1")
        )
        val token = signedToken(pair.private, "test-key-1", audience = "someone-elses-client-id")
        assertNull(GoogleIdTokenVerifier.verifyWithKeys(token, audience, keys))
    }

    @Test
    fun foreignIssuerIsRejected() {
        val pair = keyPair()
        val keys = GoogleIdTokenVerifier.parseJwks(
            jwksFor(pair.public as RSAPublicKey, "test-key-1")
        )
        val token = signedToken(pair.private, "test-key-1", audience = audience, issuer = "evil.example.com")
        assertNull(GoogleIdTokenVerifier.verifyWithKeys(token, audience, keys))
    }

    @Test
    fun expiredTokenIsRejected() {
        val pair = keyPair()
        val keys = GoogleIdTokenVerifier.parseJwks(
            jwksFor(pair.public as RSAPublicKey, "test-key-1")
        )
        val token = signedToken(pair.private, "test-key-1", audience = audience, expOffsetSeconds = -3600L)
        assertNull(GoogleIdTokenVerifier.verifyWithKeys(token, audience, keys))
    }

    @Test
    fun keySignedByAnotherKeyIsRejected() {
        val genuine = keyPair()
        val impostor = keyPair()
        // Key set holds the impostor's key but the token was signed by another.
        val keys = GoogleIdTokenVerifier.parseJwks(
            jwksFor(impostor.public as RSAPublicKey, "test-key-1")
        )
        val token = signedToken(genuine.private, "test-key-1", audience = audience)
        assertNull(GoogleIdTokenVerifier.verifyWithKeys(token, audience, keys))
    }

    @Test
    fun x5cCertificateStillParsesWhenPresent() {
        // x5c entries are base64 DER certificates; parsing must not crash and
        // an unusable entry must simply produce no key (n/e is the primary
        // path, x5c only a fallback).
        val jwk = JSONObject()
            .put("kty", "RSA")
            .put("kid", "cert-key")
            .put("x5c", JSONArray().put("bm90LWEtY2VydA=="))
        val body = JSONObject().put("keys", JSONArray().put(jwk)).toString()
        val keys = GoogleIdTokenVerifier.parseJwks(body)
        assertTrue("unusable x5c must degrade to an empty set, not crash", keys.isEmpty())
    }
}
