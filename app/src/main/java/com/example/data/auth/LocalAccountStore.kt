package com.example.data.auth

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.Locale

/**
 * A built-in, keyless account database.
 *
 * Firebase Auth only works when the build carries `google-services.json`,
 * which a fresh install of the release APK does not have — sign-up would fail
 * with "Firebase belum dikonfigurasi". This store makes accounts work on
 * every install with **zero configuration**: accounts live on-device in
 * SharedPreferences, passwords are stored as salted PBKDF2 hashes (see
 * [PasswordVault]) and a session survives app restarts.
 *
 * Honest tradeoff: names are unique per device, not globally — cross-device
 * accounts return automatically on builds that carry Firebase.
 */
class LocalAccountStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("noxa_local_accounts", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /** Result of a registration attempt; failure carries a user-facing message. */
    sealed class CreateResult {
        data class Created(val account: LocalAccount) : CreateResult()
        data class Failed(val message: String) : CreateResult()
    }

    suspend fun createAccount(
        email: String,
        username: String,
        password: String,
        /** Shared uid with the remote registry, so both sides agree on identity. */
        uid: String = "local-" + System.currentTimeMillis() + "-" + (0..9999).random()
    ): CreateResult = mutex.withLock {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val cleanName = username.trim()
        val accounts = load().toMutableMap()

        if (accounts.values.any { it.email == cleanEmail }) {
            return@withLock CreateResult.Failed("Email sudah terdaftar. Gunakan Masuk.")
        }
        if (accounts.values.any { it.username.equals(cleanName, ignoreCase = true) }) {
            return@withLock CreateResult.Failed("Username \"$cleanName\" sudah dipakai. Pilih yang lain.")
        }

        val salt = PasswordVault.newSalt()
        val account = LocalAccount(
            uid = uid,
            email = cleanEmail,
            username = cleanName,
            saltHex = PasswordVault.toHex(salt),
            hashHex = PasswordVault.toHex(PasswordVault.hash(password, salt)),
            createdAt = System.currentTimeMillis()
        )
        accounts[cleanEmail] = account
        save(accounts)
        CreateResult.Created(account)
    }

    /** Renames a local account (mirrors a remote username change). */
    fun updateUsername(uid: String, newUsername: String) {
        val accounts = load().toMutableMap()
        val email = accounts.entries.firstOrNull { it.value.uid == uid }?.key ?: return
        val account = accounts.getValue(email)
        accounts[email] = account.copy(username = newUsername.trim())
        save(accounts)
    }

    /** Changes a local account's password (fresh salt + hash). */
    fun updatePassword(uid: String, newPassword: String) {
        val accounts = load().toMutableMap()
        val email = accounts.entries.firstOrNull { it.value.uid == uid }?.key ?: return
        val account = accounts.getValue(email)
        val salt = PasswordVault.newSalt()
        accounts[email] = account.copy(
            saltHex = PasswordVault.toHex(salt),
            hashHex = PasswordVault.toHex(PasswordVault.hash(newPassword, salt))
        )
        save(accounts)
    }

    /**
     * Permanently deletes an account from the device store. Clears the
     * session when the deleted account is the signed-in one. Returns false
     * when the email matched nothing.
     */
    fun deleteAccount(email: String): Boolean {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val accounts = load().toMutableMap()
        val removed = accounts.remove(cleanEmail) ?: return false
        save(accounts)
        if (sessionUid == removed.uid) clearSession()
        return true
    }

    /** Remaining cooldown (ms) before the account may change username/password again. */
    fun cooldownRemaining(uid: String, kind: String, periodMs: Long): Long {
        val last = prefs.getLong(cooldownKey(uid, kind), 0L)
        if (last == 0L) return 0L
        val wait = last + periodMs - System.currentTimeMillis()
        return if (wait > 0L) wait else 0L
    }

    /** Marks "username"/"password" changed now, starting its cooldown. */
    fun markChanged(uid: String, kind: String) {
        prefs.edit().putLong(cooldownKey(uid, kind), System.currentTimeMillis()).apply()
    }

    private fun cooldownKey(uid: String, kind: String) = "cooldown_${kind}_$uid"

    /** Returns the matching account, or null when the credentials are wrong. */
    suspend fun authenticate(email: String, password: String): LocalAccount? = mutex.withLock {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val account = load()[cleanEmail] ?: return@withLock null
        val salt = PasswordVault.fromHex(account.saltHex)
        val hash = PasswordVault.fromHex(account.hashHex)
        if (PasswordVault.verify(password, salt, hash)) {
            sessionUid = account.uid
            account
        } else {
            null
        }
    }

    /** The account behind a stored session uid, if it still exists. */
    fun accountForUid(uid: String): LocalAccount? =
        load().values.firstOrNull { it.uid == uid }

    fun clearSession() {
        prefs.edit().remove(KEY_SESSION_UID).apply()
    }

    private var sessionUid: String?
        get() = prefs.getString(KEY_SESSION_UID, null)
        set(value) {
            prefs.edit().putString(KEY_SESSION_UID, value).apply()
        }

    fun restoreSessionUid(): String? = sessionUid

    fun storeSessionUid(uid: String) {
        sessionUid = uid
    }

    private fun load(): Map<String, LocalAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = mutableMapOf<String, LocalAccount>()
            for (key in json.keys()) {
                val entry = json.optJSONObject(key) ?: continue
                result[key] = LocalAccount(
                    uid = entry.optString("uid"),
                    email = entry.optString("email"),
                    username = entry.optString("username"),
                    saltHex = entry.optString("saltHex"),
                    hashHex = entry.optString("hashHex"),
                    createdAt = entry.optLong("createdAt")
                )
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun save(accounts: Map<String, LocalAccount>) {
        val json = JSONObject()
        for ((key, account) in accounts) {
            json.put(
                key,
                JSONObject()
                    .put("uid", account.uid)
                    .put("email", account.email)
                    .put("username", account.username)
                    .put("saltHex", account.saltHex)
                    .put("hashHex", account.hashHex)
                    .put("createdAt", account.createdAt)
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS, json.toString()).apply()
    }

    companion object {
        private const val KEY_ACCOUNTS = "accounts_json"
        private const val KEY_SESSION_UID = "session_uid"

        /** Minimal but honest email shape check (no perfect regex needed here). */
        fun isEmailFormatValid(email: String): Boolean =
            Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())
    }
}

/** One locally stored account (never holds the password itself). */
data class LocalAccount(
    val uid: String,
    val email: String,
    val username: String,
    val saltHex: String,
    val hashHex: String,
    val createdAt: Long
)
