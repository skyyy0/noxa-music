package com.example.data.support

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Role attached to an account. `DEVELOPER` is Sky's own account and is the
 * only role that can open the NOXA Admin console.
 */
enum class AccountRole(val label: String) {
    DEVELOPER("Developer"),
    ADMIN("Admin"),
    PARTNER("Partner"),
    MODERATOR("Moderator"),
    USER("User");

    val isStaff: Boolean get() = this != USER

    companion object {
        fun from(raw: String?): AccountRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: USER
    }
}

/** One message inside a support thread. `from` is "user" or "support". */
data class SupportMessage(
    val from: String,
    val text: String,
    val at: Long
)

/**
 * The support conversation between one user and NOXA Support.
 *
 * Rule requested by Sky: a user may only send **one message at a time** —
 * until Support replies, the composer stays locked. [canUserSend] encodes it.
 */
data class SupportThread(
    val uid: String,
    val username: String,
    val messages: List<SupportMessage>,
    val lastUserAt: Long,
    val lastSupportAt: Long,
    val crashHash: String = "",
    val resolved: Boolean = false
) {
    /** True when the user has nothing pending with Support. */
    val canUserSend: Boolean get() = lastUserAt <= lastSupportAt

    /** For the dev console: the last message came from a user. */
    val unreadForSupport: Boolean
        get() = messages.lastOrNull()?.from == "user"

    val lastMessage: SupportMessage? get() = messages.lastOrNull()
}

/** A registered account as stored by the backend. */
data class BackendLogin(
    val uid: String,
    val email: String,
    val username: String,
    val role: AccountRole,
    val createdAt: Long = 0L,
    /** Presence stamp — drives "aktif hari ini" / "online" in the console. */
    val lastSeenAt: Long = 0L
)

/** Outcome of a remote authentication attempt. */
sealed interface RemoteAuthResult {
    data class Ok(val login: BackendLogin) : RemoteAuthResult

    /** Credentials do not match any account. */
    data object Rejected : RemoteAuthResult

    /** Network/parse failure — the caller falls back to the local store. */
    data object Unreachable : RemoteAuthResult
}

/**
 * The keyless NOXA backend: accounts, roles and support chat.
 *
 * **Why Telegraph?** The release APK carries no `google-services.json`, so
 * Firebase (and every key-value store that needs an account, e.g. kvdb.io)
 * is unavailable. Telegraph's `api.telegra.ph` needs **no key and no signup**:
 * a single article acts as a JSON document —
 *
 *  * `createPage` once (the document now exists, path embedded below),
 *  * `getPage?return_content=true` to read the state,
 *  * `editPage` (with the page's short-name `access_token`) to write it back.
 *
 * The whole app state is one JSON object (`v`, `seq`, `accounts`, `threads`)
 * rendered as the page content, so every install reads and writes the same
 * registry. Optimistic concurrency: every write bumps `seq` after re-reading,
 * and a stale read is retried before it can clobber a newer one.
 *
 * Honest limits: no server-side authorization (any reader of the page could
 * technically edit it with the token, which ships in the APK), and writes are
 * last-writer-wins under heavy concurrency. For a personal app distributed by
 * direct APK that is a deliberate, documented tradeoff — passwords are never
 * stored (PBKDF2-HMAC-SHA256, 60k iterations), only salted hashes.
 */
class NoxaBackend {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------------ read

    /**
     * Reads the full backend state, or `null` when unreachable/corrupt.
     *
     * Telegraph occasionally answers slowly or rate-limits an anonymous call;
     * a single failed read used to surface as "Email atau password salah."
     * during sign-in. Every read now retries with a short backoff before it
     * gives up honestly.
     */
    suspend fun readState(): BackendState? = withContext(Dispatchers.IO) {
        repeat(READ_ATTEMPTS) { attempt ->
            val raw = httpGet("$API_BASE/getPage/$PAGE_PATH?return_content=true")
            if (raw != null) {
                val parsed = try {
                    val root = JSONObject(raw)
                    if (!root.optBoolean("ok")) null else {
                        val content = root.getJSONObject("result").optJSONArray("content")
                        var payload: String? = null
                        if (content != null) {
                            for (i in 0 until content.length()) {
                                val node = content.optJSONObject(i) ?: continue
                                val children = node.optJSONArray("children") ?: continue
                                for (j in 0 until children.length()) {
                                    val text = children.optString(j)
                                    if (text.startsWith("{")) {
                                        payload = text
                                        break
                                    }
                                }
                                if (payload != null) break
                            }
                        }
                        payload?.let { parseState(JSONObject(it)) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "State parse failed: ${e.message}")
                    null
                }
                if (parsed != null) return@withContext parsed
            }
            if (attempt < READ_ATTEMPTS - 1) delay(350L * (attempt + 1))
        }
        null
    }

    // ----------------------------------------------------------------- write

    /**
     * Reads, mutates and writes the state back. Returns the block's result,
     * or `null` when the backend could not be reached or the write failed.
     * The read happens inside the write window so `seq` ordering stays honest.
     */
    suspend fun <T> mutate(block: (BackendState) -> T): T? = withContext(Dispatchers.IO) {
        val state = readState() ?: return@withContext null
        val result = block(state)
        // Every write passes through here, so document-size discipline for the
        // collaborative playlists lives here too — no single feature can push
        // the page over Telegraph's limit.
        pruneSharedPlaylists(state)
        if (writeState(state)) result else null
    }

    private suspend fun writeState(state: BackendState): Boolean {
        state.seq += 1
        val json = serializeState(state)
        val children = JSONArray().put(json.toString())
        val content = JSONArray().put(JSONObject().put("tag", "p").put("children", children))
        val body = FormBody.Builder()
            .add("access_token", PAGE_TOKEN)
            .add("title", PAGE_TITLE)
            .add("content", content.toString())
            .build()
        val attempts = 3
        repeat(attempts) { attempt ->
            val raw = httpPost("$API_BASE/editPage/$PAGE_PATH", body) ?: return false
            val ok = try {
                JSONObject(raw).optBoolean("ok")
            } catch (e: Exception) {
                false
            }
            if (ok) return true
            if (attempt < attempts - 1) delay(400L * (attempt + 1))
        }
        return false
    }

    // ------------------------------------------------------------ auth: user

    /**
     * Verifies email + password against the remote registry.
     * Distinguishes "wrong credentials" from "backend offline" so the caller
     * can still fall back to the built-in local account store.
     */
    suspend fun authenticate(email: String, password: String): RemoteAuthResult {
        val state = readState() ?: return RemoteAuthResult.Unreachable
        val account = state.accounts[email.trim().lowercase(Locale.ROOT)]
            ?: return RemoteAuthResult.Rejected
        if (!verifyPassword(account.saltHex, account.hashHex, password)) {
            return RemoteAuthResult.Rejected
        }
        return RemoteAuthResult.Ok(
            BackendLogin(
                uid = account.uid,
                email = account.email,
                username = account.username,
                role = account.role,
                createdAt = account.createdAt
            )
        )
    }

    /**
     * Creates a remote account. Returns the new login, or a failure message
     * (`null` first element means "backend unreachable").
     */
    suspend fun register(
        uid: String,
        email: String,
        username: String,
        password: String,
        role: AccountRole = AccountRole.USER
    ): Pair<BackendLogin?, String?> {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val salt = randomSalt()
        var conflict: String? = null
        val login = mutate { state ->
            val existing = state.accounts[cleanEmail]
            if (existing != null && existing.hashHex.isNotBlank()) {
                conflict = "Email sudah terdaftar. Gunakan Masuk."
                return@mutate null
            }
            // Username uniqueness, ignoring the very account being claimed
            // (a passwordless stub carries its own name).
            if (state.accounts.values.any {
                    it.email != cleanEmail && it.username.equals(username, ignoreCase = true)
                }
            ) {
                conflict = "Username \"$username\" sudah dipakai. Pilih yang lain."
                return@mutate null
            }
            val account = if (existing != null) {
                // Claiming a passwordless stub (created by [upsertAccount]
                // from a Google/phone sign-in): keep uid, role, createdAt and
                // the support thread, only add real credentials.
                existing.copy(
                    uid = uid,
                    username = username,
                    saltHex = toHex(salt),
                    hashHex = toHex(pbkdf2(password.toByteArray(Charsets.UTF_8), salt))
                )
            } else {
                RemoteAccountInternal(
                    uid = uid,
                    email = cleanEmail,
                    username = username,
                    saltHex = toHex(salt),
                    hashHex = toHex(pbkdf2(password.toByteArray(Charsets.UTF_8), salt)),
                    role = role,
                    createdAt = System.currentTimeMillis()
                )
            }
            state.accounts[cleanEmail] = account
            BackendLogin(account.uid, cleanEmail, username, account.role, account.createdAt)
        }
        return when {
            login != null -> login to null
            conflict != null -> null to conflict
            else -> null to UNREACHABLE
        }
    }

    // -------------------------------------------------------- account updates

    /** Renames an account everywhere (registry + its support thread).
     *  Returns `null` on success or a user-facing reason on refusal — the
     *  7-day cooldown lives here so it holds across every device. */
    suspend fun updateUsername(uid: String, newUsername: String): String? {
        val now = System.currentTimeMillis()
        var refusal: String? = null
        val changed = mutate { state ->
            val account = state.accounts.values.firstOrNull { it.uid == uid } ?: return@mutate false
            if (state.accounts.values.any { it !== account && it.username.equals(newUsername, ignoreCase = true) }) {
                refusal = "Username \"$newUsername\" sudah dipakai. Pilih yang lain."
                return@mutate false
            }
            val wait = USERNAME_COOLDOWN_MS - (now - account.lastUsernameChangeAt)
            if (account.lastUsernameChangeAt > 0L && wait > 0L) {
                refusal = cooldownMessage("username", wait)
                return@mutate false
            }
            state.accounts[account.email] = account.copy(
                username = newUsername,
                lastUsernameChangeAt = now
            )
            state.threads[uid]?.let { thread ->
                state.threads[uid] = thread.copy(username = newUsername)
            }
            true
        }
        return if (changed == true) null else refusal ?: "Ganti username gagal. Coba lagi."
    }

    /**
     * Changes the account's password (hash only — the plaintext never travels
     * back). Returns `null` on success or a user-facing reason; the 14-day
     * cooldown holds across every device.
     */
    suspend fun updatePassword(uid: String, newPassword: String): String? {
        val salt = randomSalt()
        val hashHex = toHex(pbkdf2(newPassword.toByteArray(Charsets.UTF_8), salt))
        val now = System.currentTimeMillis()
        var refusal: String? = null
        val changed = mutate { state ->
            val account = state.accounts.values.firstOrNull { it.uid == uid } ?: return@mutate false
            val wait = PASSWORD_COOLDOWN_MS - (now - account.lastPasswordChangeAt)
            if (account.lastPasswordChangeAt > 0L && wait > 0L) {
                refusal = cooldownMessage("password", wait)
                return@mutate false
            }
            state.accounts[account.email] = account.copy(
                saltHex = toHex(salt),
                hashHex = hashHex,
                lastPasswordChangeAt = now
            )
            true
        }
        return if (changed == true) null else refusal ?: "Ganti password gagal. Coba lagi."
    }

    /** Permanently removes an account and its support thread. */
    suspend fun deleteAccount(email: String): Boolean {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val removed = mutate { state ->
            val account = state.accounts.remove(cleanEmail) ?: return@mutate false
            state.threads.remove(account.uid)
            true
        }
        return removed == true
    }

    /** Publishes a new in-app update; every client below it must update. */
    suspend fun publishUpdate(info: UpdateInfo): Boolean =
        mutate { state ->
            state.update = info
            true
        } == true

    /**
     * Presence heartbeat: stamps [uid]'s `lastSeenAt` so the dev console can
     * answer "siapa yang aktif hari ini / sedang online". Deliberately
     * throttled — the write is skipped entirely unless the stored stamp is
     * older than [PRESENCE_THROTTLE_MS], so an open app writes the shared
     * document at most once every few minutes no matter how often the
     * caller ticks. Returns true when a write actually happened.
     */
    suspend fun heartbeat(uid: String): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val state = readState() ?: return@withContext false
        val account = state.accounts.values.firstOrNull { it.uid == uid }
            ?: return@withContext false
        if (now - account.lastSeenAt < PRESENCE_THROTTLE_MS) return@withContext false
        state.accounts[account.email] = account.copy(lastSeenAt = now)
        writeState(state)
    }

    /** Changes an account's role (used by the dev console). */
    suspend fun updateRole(uid: String, role: AccountRole): Boolean {
        val changed = mutate { state ->
            val account = state.accounts.values.firstOrNull { it.uid == uid } ?: return@mutate false
            state.accounts[account.email] = RemoteAccountInternal(
                uid = account.uid,
                email = account.email,
                username = account.username,
                saltHex = account.saltHex,
                hashHex = account.hashHex,
                role = role,
                createdAt = account.createdAt
            )
            true
        }
        return changed == true
    }

    /** True when the email is already registered remotely. */
    suspend fun isEmailTaken(email: String): Boolean? {
        val state = readState() ?: return null
        val account = state.accounts[email.trim().lowercase(Locale.ROOT)] ?: return false
        // Passwordless stubs (Google/phone sign-ins) can still be claimed
        // by a later email+password registration.
        return account.hashHex.isNotBlank()
    }

    /**
     * Makes any signed-in account visible in the shared registry.
     *
     * Google, phone and Firebase sign-ins never go through [register], so
     * those users used to be invisible in the dev console's "Daftar Akun"
     * count — only email+password registrations showed up. Every sign-in
     * path calls this with its identity; the account is created as a
     * **passwordless stub** (empty hash, claimable later by a real
     * registration) or refreshed in place.
     *
     * Role handling is conservative: an existing role is only ever upgraded
     * (by enum rank — DEVELOPER first), never downgraded, so a seeded
     * developer or staff account can't be demoted by a plain sign-in.
     */
    suspend fun upsertAccount(
        uid: String,
        email: String,
        username: String,
        role: AccountRole
    ): BackendLogin? {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        if (cleanEmail.isBlank()) return null
        val cleanName = username.ifBlank { cleanEmail.substringBefore('@') }

        fun toLogin(account: RemoteAccountInternal) =
            BackendLogin(account.uid, account.email, account.username, account.role, account.createdAt)

        // Cheap path: already fully visible and nothing to upgrade — no write.
        val current = readState() ?: return null
        val existing = current.accounts[cleanEmail]
        if (existing != null && existing.uid.isNotBlank() &&
            existing.username.isNotBlank() && role.ordinal >= existing.role.ordinal
        ) {
            return toLogin(existing)
        }

        return mutate { state ->
            val found = state.accounts[cleanEmail]
            val account = if (found == null) {
                RemoteAccountInternal(
                    uid = uid,
                    email = cleanEmail,
                    username = cleanName,
                    saltHex = "",
                    hashHex = "",
                    role = role,
                    createdAt = System.currentTimeMillis()
                ).also { state.accounts[cleanEmail] = it }
            } else {
                val merged = found.copy(
                    uid = found.uid.ifBlank { uid },
                    username = if (found.username.isNotBlank()) found.username else cleanName,
                    role = if (role.ordinal < found.role.ordinal) role else found.role
                )
                state.accounts[cleanEmail] = merged
                merged
            }
            toLogin(account)
        }
    }

    /** All registered accounts, newest first (dev console). */
    suspend fun listAccounts(): List<BackendLogin>? =
        readState()?.accounts?.values
            ?.map { BackendLogin(it.uid, it.email, it.username, it.role, it.createdAt) }
            ?.sortedByDescending { it.createdAt }

    /** All support threads, most recently active first (dev console). */
    suspend fun listThreads(): List<SupportThread>? =
        readState()?.threads?.values?.sortedByDescending { it.lastUserAt.coerceAtLeast(it.lastSupportAt) }

    // ------------------------------------------------- rooms (dengar bareng)

    private fun normalizeRoomCode(code: String): String =
        code.trim().uppercase(Locale.ROOT)

    /** Members seen in the last 10 minutes stay on the roster. */
    private fun List<RoomMember>.fresh(now: Long): List<RoomMember> =
        filter { now - it.lastSeen < 10 * 60 * 1000L }

    /** Reads one listening room, or null when it does not exist / offline. */
    suspend fun readRoom(code: String): RoomState? {
        val normalized = normalizeRoomCode(code)
        val room = readState()?.rooms?.get(normalized) ?: return null
        return room.copy(members = room.members.fresh(System.currentTimeMillis()))
    }

    /**
     * Joins [code], creating the room on first join, and refreshes this
     * member's presence. Returns the room, or null when the backend is
     * unreachable (the caller reports that honestly).
     */
    suspend fun joinRoom(code: String, uid: String, name: String): RoomState? {
        val normalized = normalizeRoomCode(code)
        if (normalized.isBlank()) return null
        return mutate { state ->
            val now = System.currentTimeMillis()
            val room = state.rooms[normalized]
                ?: RoomState(code = normalized, updatedAt = now)
            val member = RoomMember(
                uid = uid,
                name = name.trim().ifBlank { "NOXA User" },
                lastSeen = now
            )
            val joined = room.copy(
                members = (room.members.filterNot { it.uid == uid } + member)
                    .fresh(now)
            )
            state.rooms[normalized] = joined
            pruneRooms(state)
            joined
        }
    }

    // ------------------------------------------- collaborative playlists

    /** Reads one collaborative playlist, or null when missing / offline. */
    suspend fun readSharedPlaylist(code: String): SharedPlaylist? {
        val normalized = SharedPlaylistCodes.normalize(code)
        if (!SharedPlaylistCodes.isValid(normalized)) return null
        return readState()?.sharedPlaylists?.get(normalized)
    }

    /**
     * Creates or replaces [playlist] under its code and stamps a fresh
     * `updatedAt` (the version every collaborator compares against). Returns
     * the stored copy, or null when the backend could not be reached.
     */
    suspend fun publishSharedPlaylist(playlist: SharedPlaylist): SharedPlaylist? {
        val normalized = SharedPlaylistCodes.normalize(playlist.code)
        if (!SharedPlaylistCodes.isValid(normalized)) return null
        val stored = playlist.copy(code = normalized, updatedAt = System.currentTimeMillis())
        return mutate { state ->
            val existing = state.sharedPlaylists[normalized]
            state.sharedPlaylists[normalized] = if (existing == null) {
                stored
            } else {
                // Keep the original creation stamp; songs and name are the
                // publisher's current truth (guarded client-side by sync).
                stored.copy(createdAt = existing.createdAt)
            }
            state.sharedPlaylists.getValue(normalized)
        }
    }

    /** Drops this member; the room dies with its last member. */
    suspend fun leaveRoom(code: String, uid: String) {
        val normalized = normalizeRoomCode(code)
        mutate { state ->
            val room = state.rooms[normalized] ?: return@mutate false
            val members = room.members.filterNot { it.uid == uid }
            if (members.isEmpty()) {
                state.rooms.remove(normalized)
            } else {
                state.rooms[normalized] = room.copy(members = members)
            }
            true
        }
    }

    /**
     * Publishes a content change (new song / play-pause / seek). [content]
     * receives the room with this member refreshed and returns the updated
     * room; [updatedAt] is stamped here so followers can tell a real change
     * from a heartbeat. Null = unreachable.
     */
    suspend fun publishRoom(
        code: String,
        uid: String,
        name: String,
        content: (RoomState) -> RoomState
    ): RoomState? {
        val normalized = normalizeRoomCode(code)
        return mutate { state ->
            val now = System.currentTimeMillis()
            val room = state.rooms[normalized]
                ?: RoomState(code = normalized, updatedAt = now)
            val member = RoomMember(
                uid = uid,
                name = name.trim().ifBlank { "NOXA User" },
                lastSeen = now
            )
            val withMember = room.copy(
                members = (room.members.filterNot { it.uid == uid } + member)
                    .fresh(now)
            )
            val updated = content(withMember).copy(updatedAt = now)
            state.rooms[normalized] = updated
            pruneRooms(state)
            updated
        }
    }

    /**
     * Keeps dormant rooms (and their members) out of the shared document.
     * A room lives while any member was seen recently — presence, not age,
     * is what keeps it alive (an idle room whose members all left dies here).
     */
    private fun pruneRooms(state: BackendState) {
        val now = System.currentTimeMillis()
        state.rooms.entries.retainAll { (_, room) ->
            room.members.any { now - it.lastSeen < 60 * 60 * 1000L }
        }
        state.rooms.keys.forEach { code ->
            val room = state.rooms[code] ?: return@forEach
            val fresh = room.members.fresh(now)
            if (fresh.size != room.members.size) {
                state.rooms[code] = room.copy(members = fresh)
            }
        }
    }

    // -------------------------------------------------------- support: user

    /**
     * Sends one message from a user. Enforces Sky's rule: while a previous
     * user message is still unanswered the send is refused (`false`).
     * The first message of each turn automatically attaches the latest crash
     * trace (if one exists), deduplicated by content hash.
     */
    suspend fun sendUserMessage(
        uid: String,
        username: String,
        text: String,
        crashTrace: String? = null
    ): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val sent = mutate { state ->
            val existing = state.threads[uid]
            if (existing != null && !existing.canUserSend) return@mutate false

            var body = trimmed
            var crashHash = existing?.crashHash ?: ""
            val freshCrash = crashTrace?.takeIf { it.isNotBlank() }
            val firstOfTurn = existing == null || existing.messages.lastOrNull()?.from == "support"
            if (freshCrash != null && firstOfTurn) {
                val hash = sha256(freshCrash)
                if (hash != crashHash) {
                    crashHash = hash
                    body += "\n\n---\nLaporan crash otomatis (baru saja):\n" +
                        freshCrash.take(CRASH_ATTACH_LIMIT)
                }
            }

            val message = SupportMessage("user", body, System.currentTimeMillis())
            val updated = SupportThread(
                uid = uid,
                username = username,
                messages = (existing?.messages ?: emptyList()) + message,
                lastUserAt = message.at,
                lastSupportAt = existing?.lastSupportAt ?: 0L,
                crashHash = crashHash,
                resolved = false
            )
            state.threads[uid] = updated
            pruneThreads(state)
            true
        }
        return sent == true
    }

    /** Reads one thread (the user's own chat). */
    suspend fun userThread(uid: String): SupportThread? = readState()?.threads?.get(uid)

    // ------------------------------------------------------- support: staff

    /**
     * Support/developer reply. Clears the user's pending flag, which unlocks
     * their composer — this is what re-arms the "one message at a time" rule.
     */
    suspend fun sendSupportMessage(threadUid: String, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val sent = mutate { state ->
            val existing = state.threads[threadUid] ?: SupportThread(
                uid = threadUid,
                username = "",
                messages = emptyList(),
                lastUserAt = 0L,
                lastSupportAt = 0L
            )
            val message = SupportMessage("support", trimmed, System.currentTimeMillis())
            state.threads[threadUid] = existing.copy(
                messages = existing.messages + message,
                lastSupportAt = message.at
            )
            pruneThreads(state)
            true
        }
        return sent == true
    }

    /** Staff-created account (role picker in the dev console). */
    suspend fun createAccountAsStaff(
        email: String,
        username: String,
        password: String,
        role: AccountRole
    ): Pair<BackendLogin?, String?> {
        val uid = "noxa-" + System.currentTimeMillis().toString(36) +
            "-" + (1000..9999).random()
        return register(uid, email, username, password, role)
    }

    // ------------------------------------------------------------- internals

    data class RemoteAccountInternal(
        val uid: String,
        val email: String,
        val username: String,
        val saltHex: String,
        val hashHex: String,
        val role: AccountRole,
        val createdAt: Long,
        /** Last username change (ms) — drives the 7-day cooldown. */
        val lastUsernameChangeAt: Long = 0L,
        /** Last password change (ms) — drives the 14-day cooldown. */
        val lastPasswordChangeAt: Long = 0L,
        /**
         * Presence stamp: the last time this account was seen using NOXA.
         * Written by the client heartbeat (throttled server-side so a busy
         * install can never flood the shared document with writes).
         */
        val lastSeenAt: Long = 0L
    )

    /**
     * One published in-app update (developer push).
     *
     * `mandatory` is Sky's rollout dial: `false` shows a soft dialog with a
     * "Nanti Saja" escape and an update entry in Settings (good for small
     * patches), `true` shows the undismissable popup (good for big changes).
     * Older documents without the field parse as `true` — the historical
     * behaviour was forced.
     */
    data class UpdateInfo(
        val versionCode: Long,
        val versionName: String,
        val url: String,
        val note: String,
        val at: Long,
        val mandatory: Boolean = true
    )

    /** One member of a listening room; [lastSeen] keeps the roster honest. */
    data class RoomMember(
        val uid: String,
        val name: String,
        val lastSeen: Long
    )

    /**
     * A live "Dengar Bareng" room: everyone hears the same song at roughly
     * the same position. Last-writer-wins by [updatedAt] — whoever acts
     * (song, play/pause, seek) publishes; every other member follows.
     */
    data class RoomState(
        val code: String,
        val songId: String = "",
        val songTitle: String = "",
        val songArtist: String = "",
        val songArtwork: String = "",
        val playing: Boolean = false,
        val positionMs: Long = 0L,
        /** Bumped ONLY by content changes — never by heartbeats. */
        val updatedAt: Long = 0L,
        val members: List<RoomMember> = emptyList(),
        /**
         * Vote Skip: uids of members currently voting to advance. Any local
         * playback change (song/play-pause) clears the ballot — the vote is
         * only ever about *this* song. Reaching a majority makes the room's
         * deterministic leader perform the skip (see `RoomVotes`).
         */
        val skipVotes: List<String> = emptyList()
    )

    /** Full backend document, mirrored 1:1 onto the Telegraph page. */
    class BackendState {
        var seq: Long = 0L
        val accounts = LinkedHashMap<String, RemoteAccountInternal>()
        val threads = LinkedHashMap<String, SupportThread>()

        /** Latest pushed update, or null when nothing was ever published. */
        var update: UpdateInfo? = null

        /** Live listening rooms keyed by room code. */
        val rooms = LinkedHashMap<String, RoomState>()

        /** Collaborative playlists keyed by their 6-character share code. */
        val sharedPlaylists = LinkedHashMap<String, SharedPlaylist>()
    }

    private fun parseState(json: JSONObject): BackendState {
        val state = BackendState()
        state.seq = json.optLong("seq")
        val accounts = json.optJSONArray("accounts") ?: JSONArray()
        for (i in 0 until accounts.length()) {
            val obj = accounts.optJSONObject(i) ?: continue
            val email = obj.optString("email").trim().lowercase(Locale.ROOT)
            if (email.isBlank()) continue
            state.accounts[email] = RemoteAccountInternal(
                uid = obj.optString("uid"),
                email = email,
                username = obj.optString("username"),
                saltHex = obj.optString("saltHex"),
                hashHex = obj.optString("hashHex"),
                role = AccountRole.from(obj.optString("role")),
                createdAt = obj.optLong("createdAt"),
                lastUsernameChangeAt = obj.optLong("lastUsernameChangeAt"),
                lastPasswordChangeAt = obj.optLong("lastPasswordChangeAt"),
                lastSeenAt = obj.optLong("lastSeenAt")
            )
        }
        val threads = json.optJSONObject("threads") ?: JSONObject()
        for (uid in threads.keys()) {
            val obj = threads.optJSONObject(uid) ?: continue
            val rawMessages = obj.optJSONArray("messages") ?: JSONArray()
            val messages = ArrayList<SupportMessage>(rawMessages.length())
            for (i in 0 until rawMessages.length()) {
                val m = rawMessages.optJSONObject(i) ?: continue
                messages.add(
                    SupportMessage(
                        from = m.optString("from"),
                        text = m.optString("text"),
                        at = m.optLong("at")
                    )
                )
            }
            state.threads[uid] = SupportThread(
                uid = uid,
                username = obj.optString("username"),
                messages = messages,
                lastUserAt = obj.optLong("lastUserAt"),
                lastSupportAt = obj.optLong("lastSupportAt"),
                crashHash = obj.optString("crashHash"),
                resolved = obj.optBoolean("resolved")
            )
        }
        val rooms = json.optJSONObject("rooms") ?: JSONObject()
        for (code in rooms.keys()) {
            val obj = rooms.optJSONObject(code) ?: continue
            val rawMembers = obj.optJSONArray("members") ?: JSONArray()
            val members = ArrayList<RoomMember>(rawMembers.length())
            for (i in 0 until rawMembers.length()) {
                val m = rawMembers.optJSONObject(i) ?: continue
                members.add(
                    RoomMember(
                        uid = m.optString("uid"),
                        name = m.optString("name"),
                        lastSeen = m.optLong("lastSeen")
                    )
                )
            }
            val rawVotes = obj.optJSONArray("skipVotes") ?: JSONArray()
            val skipVotes = ArrayList<String>(rawVotes.length())
            for (i in 0 until rawVotes.length()) {
                rawVotes.optString(i).takeIf { it.isNotBlank() }?.let { skipVotes += it }
            }
            state.rooms[code] = RoomState(
                code = code,
                songId = obj.optString("songId"),
                songTitle = obj.optString("songTitle"),
                songArtist = obj.optString("songArtist"),
                songArtwork = obj.optString("songArtwork"),
                playing = obj.optBoolean("playing"),
                positionMs = obj.optLong("positionMs"),
                updatedAt = obj.optLong("updatedAt"),
                members = members,
                skipVotes = skipVotes
            )
        }
        val shared = json.optJSONObject("sharedPlaylists") ?: JSONObject()
        for (code in shared.keys()) {
            val obj = shared.optJSONObject(code) ?: continue
            val rawSongs = obj.optJSONArray("songs") ?: JSONArray()
            val songs = ArrayList<SharedPlaylistSong>(rawSongs.length())
            for (i in 0 until rawSongs.length()) {
                val s = rawSongs.optJSONObject(i) ?: continue
                val id = s.optString("id")
                if (id.isBlank()) continue
                songs.add(
                    SharedPlaylistSong(
                        id = id,
                        title = s.optString("title"),
                        artist = s.optString("artist"),
                        artwork = s.optString("artwork"),
                        durationMs = s.optLong("durationMs")
                    )
                )
            }
            state.sharedPlaylists[code] = SharedPlaylist(
                code = code,
                name = obj.optString("name"),
                ownerUid = obj.optString("ownerUid"),
                ownerName = obj.optString("ownerName"),
                createdAt = obj.optLong("createdAt"),
                updatedAt = obj.optLong("updatedAt"),
                songs = songs
            )
        }
        state.update = json.optJSONObject("update")?.let { u ->
            val url = u.optString("url")
            if (url.isBlank()) null else UpdateInfo(
                versionCode = u.optLong("versionCode"),
                versionName = u.optString("versionName"),
                url = url,
                note = u.optString("note"),
                at = u.optLong("at"),
                // Absent field = old document = the old forced behaviour.
                mandatory = !u.has("mandatory") || u.optBoolean("mandatory")
            )
        }
        return state
    }

    private fun serializeState(state: BackendState): JSONObject {
        val accounts = JSONArray()
        state.accounts.values.forEach { account ->
            accounts.put(
                JSONObject()
                    .put("uid", account.uid)
                    .put("email", account.email)
                    .put("username", account.username)
                    .put("saltHex", account.saltHex)
                    .put("hashHex", account.hashHex)
                    .put("role", account.role.name)
                    .put("createdAt", account.createdAt)
                    .put("lastUsernameChangeAt", account.lastUsernameChangeAt)
                    .put("lastPasswordChangeAt", account.lastPasswordChangeAt)
                    .put("lastSeenAt", account.lastSeenAt)
            )
        }
        val threads = JSONObject()
        state.threads.forEach { (uid, thread) ->
            val messages = JSONArray()
            thread.messages.forEach { message ->
                messages.put(
                    JSONObject()
                        .put("from", message.from)
                        .put("text", message.text)
                        .put("at", message.at)
                )
            }
            threads.put(
                uid,
                JSONObject()
                    .put("username", thread.username)
                    .put("messages", messages)
                    .put("lastUserAt", thread.lastUserAt)
                    .put("lastSupportAt", thread.lastSupportAt)
                    .put("crashHash", thread.crashHash)
                    .put("resolved", thread.resolved)
            )
        }
        return JSONObject()
            .put("v", 1)
            .put("seq", state.seq)
            .put("accounts", accounts)
            .put("threads", threads)
            .also { root ->
                state.update?.let { u ->
                    root.put(
                        "update",
                        JSONObject()
                            .put("versionCode", u.versionCode)
                            .put("versionName", u.versionName)
                            .put("url", u.url)
                            .put("note", u.note)
                            .put("at", u.at)
                            .put("mandatory", u.mandatory)
                    )
                }
                if (state.rooms.isNotEmpty()) {
                    val rooms = JSONObject()
                    state.rooms.forEach { (code, room) ->
                        val members = JSONArray()
                        room.members.forEach { m ->
                            members.put(
                                JSONObject()
                                    .put("uid", m.uid)
                                    .put("name", m.name)
                                    .put("lastSeen", m.lastSeen)
                            )
                        }
                        val votes = JSONArray()
                        room.skipVotes.forEach { uid -> votes.put(uid) }
                        rooms.put(
                            code,
                            JSONObject()
                                .put("songId", room.songId)
                                .put("songTitle", room.songTitle)
                                .put("songArtist", room.songArtist)
                                .put("songArtwork", room.songArtwork)
                                .put("playing", room.playing)
                                .put("positionMs", room.positionMs)
                                .put("updatedAt", room.updatedAt)
                                .put("members", members)
                                .put("skipVotes", votes)
                        )
                    }
                    root.put("rooms", rooms)
                }
                if (state.sharedPlaylists.isNotEmpty()) {
                    val shared = JSONObject()
                    state.sharedPlaylists.forEach { (code, playlist) ->
                        val songs = JSONArray()
                        playlist.songs.forEach { s ->
                            songs.put(
                                JSONObject()
                                    .put("id", s.id)
                                    .put("title", s.title)
                                    .put("artist", s.artist)
                                    .put("artwork", s.artwork)
                                    .put("durationMs", s.durationMs)
                            )
                        }
                        shared.put(
                            code,
                            JSONObject()
                                .put("name", playlist.name)
                                .put("ownerUid", playlist.ownerUid)
                                .put("ownerName", playlist.ownerName)
                                .put("createdAt", playlist.createdAt)
                                .put("updatedAt", playlist.updatedAt)
                                .put("songs", songs)
                        )
                    }
                    root.put("sharedPlaylists", shared)
                }
            }
    }

    /**
     * Keeps collaborative playlists inside Telegraph's page-size limit: a
     * per-playlist song cap plus a global cap that keeps the freshest
     * playlists first. Runs on every write (see [mutate]).
     */
    private fun pruneSharedPlaylists(state: BackendState) {
        if (state.sharedPlaylists.isEmpty()) return
        val MAX_SONGS_PER_PLAYLIST = 200
        val MAX_PLAYLISTS = 40
        state.sharedPlaylists.forEach { (code, playlist) ->
            if (playlist.songs.size > MAX_SONGS_PER_PLAYLIST) {
                state.sharedPlaylists[code] = playlist.copy(
                    songs = playlist.songs.take(MAX_SONGS_PER_PLAYLIST)
                )
            }
        }
        if (state.sharedPlaylists.size > MAX_PLAYLISTS) {
            val keep = state.sharedPlaylists.values
                .sortedByDescending { it.updatedAt }
                .take(MAX_PLAYLISTS)
                .map { it.code }
                .toSet()
            state.sharedPlaylists.keys.retainAll(keep)
        }
    }

    /** Keeps the document inside Telegraph's page-size limit. */
    private fun pruneThreads(state: BackendState) {
        val MAX_MESSAGES_PER_THREAD = 200
        val MAX_THREADS = 120
        state.threads.values.forEach { thread ->
            if (thread.messages.size > MAX_MESSAGES_PER_THREAD) {
                state.threads[thread.uid] = thread.copy(
                    messages = thread.messages.takeLast(MAX_MESSAGES_PER_THREAD)
                )
            }
        }
        if (state.threads.size > MAX_THREADS) {
            val keep = state.threads.values
                .sortedByDescending { it.lastUserAt.coerceAtLeast(it.lastSupportAt) }
                .take(MAX_THREADS)
                .map { it.uid }
                .toSet()
            state.threads.keys.retainAll(keep)
        }
    }

    private fun httpGet(url: String): String? = try {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "NOXA/1.13.3")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "GET failed: ${e.message}")
        null
    }

    private fun httpPost(url: String, body: FormBody): String? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NOXA/1.13.3")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "POST failed: ${e.message}")
        null
    }

    // ------------------------------------------------------- password crypto

    /** Verifies a PBKDF2-HMAC-SHA256 hex hash, same parameters as local store. */
    private fun verifyPassword(saltHex: String, hashHex: String, password: String): Boolean = try {
        val actual = pbkdf2(password.toByteArray(Charsets.UTF_8), fromHex(saltHex))
        MessageDigest.isEqual(actual, fromHex(hashHex))
    } catch (e: Exception) {
        false
    }

    /**
     * PBKDF2 (RFC 2898) over HMAC-SHA256 — same implementation as
     * `PasswordVault`, kept dependency-free so the backend module compiles
     * standalone. 60k iterations / 32-byte key, matching seeded accounts.
     */
    private fun pbkdf2(password: ByteArray, salt: ByteArray): ByteArray {
        val iterations = 60_000
        val dkLen = 32
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(object : java.security.Key {
            override fun getAlgorithm(): String = "HmacSHA256"
            override fun getEncoded(): ByteArray = password
            override fun getFormat(): String = "RAW"
        })
        val out = ByteArray(dkLen)
        var offset = 0
        var block = 1
        while (offset < dkLen) {
            mac.reset()
            var u = mac.doFinal(salt + intToBytes(block))
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
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

    private fun randomSalt(): ByteArray =
        ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun fromHex(hex: String): ByteArray =
        ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }

    private fun sha256(text: String): String = try {
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        text.hashCode().toString()
    }

    companion object {
        private const val TAG = "NoxaBackend"
        private const val API_BASE = "https://api.telegra.ph"

        /** Username may change once every 7 days; password once every 14. */
        const val USERNAME_COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000
        const val PASSWORD_COOLDOWN_MS = 14L * 24 * 60 * 60 * 1000

        /** Human Indonesian cooldown message with a rounded-up day count. */
        fun cooldownMessage(kind: String, waitMs: Long): String {
            val days = (waitMs / (24 * 60 * 60 * 1000L)) + 1
            return if (kind == "username") {
                "Ganti username hanya bisa 1x dalam 7 hari. Bisa lagi dalam ~$days hari."
            } else {
                "Ganti password hanya bisa 1x dalam 14 hari. Bisa lagi dalam ~$days hari."
            }
        }

        /** The one state document shared by every install. */
        private const val PAGE_PATH = "NOXA-Backend-Core-10-13"
        private const val PAGE_TITLE = "NOXA Backend Core"
        private const val PAGE_TOKEN =
            "7941e33f1aeb16bf70aed150bdf81ef1f0700eee189f7ca79cdbf2a416bf"

        /** How often a read is retried before giving up (slow/rate-limited CDN). */
        private const val READ_ATTEMPTS = 3

        /**
         * Presence writes are coalesced to one per window — the heartbeat
         * exists only for the console's "active today / online" cards, so a
         * stamp that fresh is not worth a whole-document write.
         */
        private const val PRESENCE_THROTTLE_MS = 3L * 60 * 1000

        /** Returned to callers when the backend itself is unreachable. */
        const val UNREACHABLE = "server_noxa_tidak_tersedia"

        /** How much of a crash trace travels with a support message. */
        private const val CRASH_ATTACH_LIMIT = 6_000
    }
}
