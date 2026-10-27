package com.example.data.auth

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Unique usernames, backed by **Firestore**.
 *
 * Every name is claimed as the document id of `usernames/{lowercased}` inside
 * a transaction, so two people can never register the same name at the same
 * moment — document-id uniqueness does the enforcing, which works even with
 * test-mode security rules (rules still recommended, see ENVIRONMENT.md).
 *
 * When Firestore is unreachable or not configured, claims degrade gracefully:
 * the call reports [ClaimResult.UNAVAILABLE] and the caller keeps the account
 * working (name stored as the display name) instead of blocking registration.
 */
object UsernameRegistry {

    enum class ClaimResult { AVAILABLE, TAKEN, INVALID, UNAVAILABLE }

    private const val COLLECTION = "usernames"

    /** 3–20 chars: letters, digits, underscore — @ and . kept for readability. */
    private val USERNAME_REGEX = Regex("^[A-Za-z0-9_.]{3,20}$")

    /** Returns an Indonesian error message, or null when the name is valid. */
    fun validate(username: String): String? =
        if (!USERNAME_REGEX.matches(username)) {
            "Username 3–20 karakter: huruf, angka, titik, atau underscore."
        } else {
            null
        }

    private fun firestore(): FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        null
    }

    private fun docId(username: String): String =
        username.trim().lowercase(Locale.ROOT)

    /**
     * Quick availability probe for a friendly pre-creation check.
     * `null` means "unknown" (Firestore unreachable) — not "available".
     */
    suspend fun isTaken(username: String): Boolean? {
        if (validate(username) != null) return null
        val db = firestore() ?: return null
        return try {
            val snapshot = db.collection(COLLECTION).document(docId(username)).get().await()
            snapshot.getString("uid") != null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Atomically claims [username] for [uid]. Returns [ClaimResult.TAKEN] when
     * another user already holds the name (race-proof: check and write happen
     * in one transaction).
     */
    suspend fun claim(uid: String, username: String): ClaimResult {
        val normalized = username.trim()
        validate(normalized)?.let { return ClaimResult.INVALID }
        val db = firestore() ?: return ClaimResult.UNAVAILABLE

        return try {
            db.runTransaction { transaction ->
                val doc = db.collection(COLLECTION).document(docId(normalized))
                val existing = transaction.get(doc)
                val holder = existing.getString("uid")
                if (holder != null && holder != uid) {
                    ClaimResult.TAKEN
                } else {
                    transaction.set(
                        doc,
                        mapOf(
                            "uid" to uid,
                            "username" to normalized,
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                    ClaimResult.AVAILABLE
                }
            }.await()
        } catch (e: Exception) {
            ClaimResult.UNAVAILABLE
        }
    }
}
