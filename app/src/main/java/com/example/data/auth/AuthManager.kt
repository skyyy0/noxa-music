package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Sign-in for NOXA.
 *
 * Two backends, one interface:
 *
 *  * **Firebase** — used whenever the build actually carries Firebase config
 *    (`google-services.json`), including the unique-username registry in
 *    Firestore. Unchanged behaviour on configured builds.
 *  * **Built-in local accounts** — the always-available fallback. The release
 *    APK on GitHub has no `google-services.json`, so Firebase sign-up used to
 *    fail with "Firebase belum dikonfigurasi". With the local store, email +
 *    password registration and sign-in work on every install, with zero keys
 *    and zero setup; names stay unique per device.
 */
class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }

    private val localStore = LocalAccountStore(context)
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    /** Keyless shared registry (accounts, roles, support chat). */
    private val backend = com.example.data.support.NoxaBackend()

    // ------------------------------------------- sign-in throttle (brute force)

    /** Per-device ledger of recent failed sign-ins, keyed by email. */
    private val throttlePrefs =
        context.getSharedPreferences("noxa_login_throttle", Context.MODE_PRIVATE)

    /** How long this email must wait after its recent failures (0 = none). */
    private fun loginWaitMs(email: String): Long {
        val key = email.trim().lowercase(java.util.Locale.ROOT)
        return LoginThrottle.remainingMs(
            failures = throttlePrefs.getInt("fail_$key", 0),
            lastFailureAt = throttlePrefs.getLong("last_$key", 0L),
            now = System.currentTimeMillis()
        )
    }

    private fun recordLoginFailure(email: String) {
        val key = email.trim().lowercase(java.util.Locale.ROOT)
        throttlePrefs.edit()
            .putInt("fail_$key", throttlePrefs.getInt("fail_$key", 0) + 1)
            .putLong("last_$key", System.currentTimeMillis())
            .apply()
    }

    /** A correct sign-in forgives every earlier failure for this email. */
    private fun clearLoginFailures(email: String) {
        val key = email.trim().lowercase(java.util.Locale.ROOT)
        throttlePrefs.edit().remove("fail_$key").remove("last_$key").apply()
    }

    private fun humanWait(ms: Long): String =
        if (ms < 60_000L) "${(ms + 999) / 1000} detik lagi"
        else "${(ms + 59_999) / 60_000} menit lagi"

    /**
     * Fire-and-forget scope for pushing a completed sign-in's identity into
     * the shared registry. Google, phone and Firebase sign-ins never go
     * through `backend.register`, so without this those users were invisible
     * in the dev console's "Daftar Akun" count — only email+password
     * registrations showed up. Sign-in never waits on it; an offline
     * registry simply skips the write (the next sign-in retries).
     */
    private val registrySyncScope = CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    private fun syncToRegistry(user: NoxaUser) {
        val email = user.email?.trim()?.lowercase(java.util.Locale.ROOT) ?: return
        registrySyncScope.launch {
            try {
                backend.upsertAccount(
                    uid = user.uid,
                    email = email,
                    username = user.displayName ?: email.substringBefore('@'),
                    role = user.role
                )
            } catch (e: Exception) {
                Log.w("AuthManager", "Registry sync skipped: ${e.message}")
            }
        }
    }

    private val remotePrefs =
        context.getSharedPreferences("noxa_remote_session", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<NoxaUser?>(auth?.currentUser?.let { toNoxaUser(it) }
        ?: restoreRemoteSession()
        ?: localStore.restoreSessionUid()?.let { uid ->
            localStore.accountForUid(uid)?.let { account ->
                NoxaUser(
                    uid = account.uid,
                    displayName = account.username,
                    email = account.email,
                    photoUrl = null
                )
            }
        })
    val currentUser: StateFlow<NoxaUser?> = _currentUser.asStateFlow()

    private val _isGuestMode = MutableStateFlow(_currentUser.value == null)
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    init {
        auth?.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                _currentUser.value = toNoxaUser(firebaseAuth.currentUser!!)
                _isGuestMode.value = false
            }
        }
    }

    private fun toNoxaUser(user: com.google.firebase.auth.FirebaseUser): NoxaUser = NoxaUser(
        uid = user.uid,
        displayName = user.displayName,
        email = user.email,
        photoUrl = user.photoUrl?.toString()
    )

    // -------------------------------------------------- remote session (keyless)

    /** Rebuilds the session created by a successful remote sign-in. */
    private fun restoreRemoteSession(): NoxaUser? {
        val uid = remotePrefs.getString(KEY_REMOTE_UID, null) ?: return null
        return NoxaUser(
            uid = uid,
            displayName = remotePrefs.getString(KEY_REMOTE_USERNAME, null),
            email = remotePrefs.getString(KEY_REMOTE_EMAIL, null),
            photoUrl = null,
            role = com.example.data.support.AccountRole.from(
                remotePrefs.getString(KEY_REMOTE_ROLE, null)
            )
        )
    }

    private fun storeRemoteSession(user: NoxaUser) {
        remotePrefs.edit()
            .putString(KEY_REMOTE_UID, user.uid)
            .putString(KEY_REMOTE_USERNAME, user.displayName)
            .putString(KEY_REMOTE_EMAIL, user.email)
            .putString(KEY_REMOTE_ROLE, user.role.name)
            .apply()
    }

    private fun clearRemoteSession() {
        remotePrefs.edit().clear().apply()
    }

    /**
     * Mirrors a remote account into the on-device store so a sign-in still
     * works offline afterwards (same uid on both sides).
     */
    private suspend fun mirrorRemoteLocally(
        login: com.example.data.support.BackendLogin,
        password: String
    ) {
        try {
            if (localStore.accountForUid(login.uid) != null) {
                localStore.storeSessionUid(login.uid)
                return
            }
            val result = localStore.createAccount(
                email = login.email,
                username = login.username,
                password = password,
                uid = login.uid
            )
            if (result is LocalAccountStore.CreateResult.Created) {
                localStore.storeSessionUid(login.uid)
            }
        } catch (e: Exception) {
            Log.w("AuthManager", "Local mirror failed: ${e.message}")
        }
    }

    fun continueAsGuest() {
        _isGuestMode.value = true
    }

    /**
     * Google Sign-In through Credential Manager, verified WITHOUT Firebase
     * Auth: the ID token is checked directly against Google's public signing
     * keys ([GoogleIdTokenVerifier]), so login works even when the Google (or
     * Email/Password) provider is disabled in the Firebase console — which
     * used to make the picker return to NOXA and silently do nothing. A
     * Firebase hand-off remains as a fallback when the token cannot be
     * verified locally (e.g. Google's key endpoint unreachable).
     */
    fun signInWithGoogle(
        activity: Activity,
        scope: CoroutineScope,
        onSuccess: (NoxaUser) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val serverClientId = webClientId()
        if (serverClientId == null) {
            Log.e("AuthManager", "default_web_client_id missing from google-services.json")
            onError("Google Sign-In belum dikonfigurasi pada build ini.")
            return
        }

        _isSigningIn.value = true

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    Log.w("AuthManager", "Google picker returned a token; verifying locally")

                    // 1) Verify the token against Google's own signing keys —
                    //    no Firebase Auth (and no console setup) required.
                    val identity = GoogleIdTokenVerifier.verify(googleIdToken, serverClientId)
                    if (identity != null) {
                        Log.w("AuthManager", "Local Google verification OK for sub=${identity.subject}")
                        val user = NoxaUser(
                            uid = "google-" + identity.subject,
                            displayName = identity.name ?: identity.email?.substringBefore('@'),
                            email = identity.email,
                            photoUrl = identity.pictureUrl,
                            // Sky's Google account is a developer account by
                            // definition — auto-detected on every sign-in.
                            role = roleForEmail(identity.email)
                        )
                        // Persisted in the same store as remote sessions, so
                        // the Google account survives an app restart.
                        storeRemoteSession(user)
                        _currentUser.value = user
                        _isGuestMode.value = false
                        syncToRegistry(user)
                        onSuccess(user)
                        return@launch
                    }

                    // 2) Fallback: hand the token to Firebase — that path
                    //    works on projects where the Google provider is on.
                    Log.w("AuthManager", "Local Google verification failed; trying Firebase hand-off")
                    val firebaseAuth = auth
                    if (firebaseAuth != null) {
                        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                        val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                        val user = authResult.user
                        if (user != null) {
                                val noxaUser = toNoxaUser(user).copy(role = roleForEmail(user.email))
                                _currentUser.value = noxaUser
                                _isGuestMode.value = false
                                syncToRegistry(noxaUser)
                                onSuccess(noxaUser)
                            } else {
                                onError("Masuk dengan Google gagal. Coba lagi.")
                            }
                    } else {
                        onError("Verifikasi akun Google gagal. Periksa koneksi internet lalu coba lagi.")
                    }
                } else {
                    onError("Masuk dengan Google dibatalkan. Coba lagi.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("AuthManager", "Google Sign-In cancelled: ${e.message}")
            } catch (e: Exception) {
                Log.e("AuthManager", "Google Sign-In failed", e)
                onError(humanizeAuthError(e))
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    // ------------------------------------------------------- phone sign-in

    /**
     * Sends an SMS one-time code to [phoneNumber] (E.164, e.g. `+62812…`).
     *
     * Uses Firebase phone auth — the only keyless-registry-independent method
     * here, since SMS OTP has no public keyless equivalent. Two console steps
     * are required for real delivery: the Blaze plan (phone auth is billed per
     * SMS) and Indonesia allowed in the SMS region policy. On eligible
     * devices Firebase verifies instantly and [onSuccess] fires without the
     * user ever typing the code.
     */
    fun sendPhoneOtp(
        activity: Activity,
        phoneNumber: String,
        scope: CoroutineScope,
        onCodeSent: (verificationId: String) -> Unit,
        onSuccess: (NoxaUser) -> Unit = {},
        onError: (String) -> Unit
    ) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            onError("Masuk dengan nomor telepon belum tersedia pada build ini.")
            return
        }
        val e164 = phoneNumber.trim()
        if (!e164.startsWith("+") || e164.length < 8) {
            onError("Nomor telepon tidak valid. Awali dengan + dan kode negara, contoh +62812xxxxxxx.")
            return
        }

        _isSigningIn.value = true
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto/instant verification on this device — sign in without
                // waiting for the user to type the SMS code.
                scope.launch {
                    signInWithPhoneCredential(firebaseAuth, credential, onSuccess, onError)
                }
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                _isSigningIn.value = false
                Log.w("AuthManager", "Phone verification failed: ${e.message}")
                onError(humanizePhoneError(e))
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                _isSigningIn.value = false
                onCodeSent(verificationId)
            }
        }
        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(e164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /** Verifies the six-digit SMS code the user received. */
    fun verifyPhoneOtp(
        verificationId: String,
        code: String,
        scope: CoroutineScope,
        onSuccess: (NoxaUser) -> Unit = {},
        onError: (String) -> Unit
    ) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            onError("Masuk dengan nomor telepon belum tersedia pada build ini.")
            return
        }
        val trimmed = code.trim()
        if (trimmed.length < 4) {
            onError("Kode OTP tidak valid.")
            return
        }
        _isSigningIn.value = true
        scope.launch {
            val credential = PhoneAuthProvider.getCredential(verificationId, trimmed)
            signInWithPhoneCredential(firebaseAuth, credential, onSuccess, onError)
        }
    }

    /** Firebase hand-off shared by the instant and manual phone paths. */
    private suspend fun signInWithPhoneCredential(
        firebaseAuth: FirebaseAuth,
        credential: PhoneAuthCredential,
        onSuccess: (NoxaUser) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user
            if (user != null) {
            val noxaUser = toNoxaUser(user).copy(role = roleForEmail(user.email))
            storeRemoteSession(noxaUser)
            _currentUser.value = noxaUser
            _isGuestMode.value = false
            syncToRegistry(noxaUser)
            onSuccess(noxaUser)
            } else {
                onError("Masuk dengan nomor telepon gagal. Coba lagi.")
            }
        } catch (e: Exception) {
            Log.e("AuthManager", "Phone sign-in failed", e)
            onError(humanizePhoneError(e))
        } finally {
            _isSigningIn.value = false
        }
    }

    /** Maps phone-auth failures to honest Indonesian messages. */
    private fun humanizePhoneError(e: Exception): String {
        val code = (e as? FirebaseAuthException)?.errorCode.orEmpty().lowercase()
        val msg = e.message.orEmpty().lowercase()
        return when {
            "invalid-phone-number" in code || "missing-phone-number" in code ->
                "Nomor telepon tidak valid. Awali dengan + dan kode negara, contoh +62812xxxxxxx."
            "invalid-verification-code" in code || "code-expired" in code ->
                "Kode OTP salah atau kedaluwarsa. Periksa SMS lalu coba lagi."
            "quota-exceeded" in code || "too-many-requests" in code ->
                "Terlalu banyak permintaan kode OTP. Coba lagi beberapa menit lagi."
            "operation-not-allowed" in code || "provider is disabled" in msg ->
                "Masuk nomor telepon belum diaktifkan di proyek Firebase."
            "billing" in msg ->
                "Layanan SMS OTP butuh penagihan (Blaze) aktif di proyek Firebase."
            "region" in msg ->
                "Wilayah SMS belum diizinkan — aktifkan Indonesia di pengaturan SMS region Firebase."
            else -> humanizeAuthError(e)
        }
    }

    /**
     * Email/password sign-in — the Google-free path, so an account works on any
     * device without a Google account on it.
     *
     * Order matters: the shared keyless registry (Telegraph) is consulted
     * FIRST on every build — that is where cross-device accounts, including
     * the developer account, actually live — followed by the on-device mirror
     * for offline sign-in. A dead registry reports a connection problem
     * instead of blaming the password. Firebase's email/password provider is
     * deliberately NOT part of this path: it is disabled in the Firebase
     * project (it surfaced raw English errors) and would hold no accounts.
     */
    fun signInWithEmail(
        email: String,
        password: String,
        scope: CoroutineScope,
        onSuccess: (NoxaUser) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Brute-force backoff: a device that keeps guessing is asked to wait
        // (LoginThrottle — first failures are free, then it doubles).
        val pendingWait = loginWaitMs(email)
        if (pendingWait > 0) {
            onError("Terlalu banyak percobaan gagal. Coba lagi dalam ${humanWait(pendingWait)}.")
            return
        }
        _isSigningIn.value = true
        scope.launch {
            var registryUnreachable = false
            try {
                // 1) Shared registry first — that is where the developer
                    //    account (and every account across devices) lives.
                    when (val remote = backend.authenticate(email, password)) {
                        is com.example.data.support.RemoteAuthResult.Ok -> {
                            val login = remote.login
                            mirrorRemoteLocally(login, password)
                            val user = NoxaUser(
                                uid = login.uid,
                                displayName = login.username,
                                email = login.email,
                                photoUrl = null,
                                role = login.role
                            )
                            storeRemoteSession(user)
                            _currentUser.value = user
                            _isGuestMode.value = false
                            clearLoginFailures(email)
                            onSuccess(user)
                            return@launch
                        }

                        com.example.data.support.RemoteAuthResult.Rejected -> Unit

                        com.example.data.support.RemoteAuthResult.Unreachable -> {
                            registryUnreachable = true
                        }
                    }

                    // 2) Firebase Email/Password — the provider is enabled in
                    //    the project, so sign-in also works when the keyless
                    //    registry is unreachable. A wrong password here is
                    //    expected on registry-only accounts; fall through.
                    val firebaseAuth = auth
                    if (firebaseAuth != null) {
                        try {
                            val fbResult = firebaseAuth
                                .signInWithEmailAndPassword(email.trim(), password)
                                .await()
                            val fbUser = fbResult.user
                            if (fbUser != null) {
                                mirrorRemoteLocally(
                                    com.example.data.support.BackendLogin(
                                        uid = fbUser.uid,
                                        email = fbUser.email ?: email.trim(),
                                        username = fbUser.displayName
                                            ?: fbUser.email?.substringBefore('@')
                                            ?: "NOXA User",
                                        role = com.example.data.support.AccountRole.USER
                                    ),
                                    password
                                )
                                val user = NoxaUser(
                                    uid = fbUser.uid,
                                    displayName = fbUser.displayName
                                        ?: fbUser.email?.substringBefore('@'),
                                    email = fbUser.email,
                                    photoUrl = fbUser.photoUrl?.toString(),
                                    role = roleForEmail(fbUser.email)
                                )
                                storeRemoteSession(user)
                                _currentUser.value = user
                                _isGuestMode.value = false
                                syncToRegistry(user)
                                clearLoginFailures(email)
                                onSuccess(user)
                                return@launch
                            }
                        } catch (e: Exception) {
                            Log.w("AuthManager", "Firebase email sign-in failed: ${e.message}")
                        }
                    }

                    // 3) The on-device mirror (offline sign-in).
                    val account = localStore.authenticate(email, password)
                    if (account != null) {
                        val user = NoxaUser(
                            uid = account.uid,
                            displayName = account.username,
                            email = account.email,
                            photoUrl = null,
                            role = roleForEmail(account.email)
                        )
                        _currentUser.value = user
                        _isGuestMode.value = false
                        syncToRegistry(user)
                        clearLoginFailures(email)
                        onSuccess(user)
                        return@launch
                    }

                    recordLoginFailure(email)
                    onError(
                        if (registryUnreachable) {
                            "Server akun tidak terjangkau. Periksa koneksi internet lalu coba lagi."
                        } else {
                            "Email atau password salah."
                        }
                    )
                } catch (e: Exception) {
                    onError("Masuk gagal. Coba lagi.")
                } finally {
                    _isSigningIn.value = false
                }
            }
        }

    /**
     * Email/password registration with a **unique username**. On Firebase
     * builds the name is claimed through [UsernameRegistry] (Firestore); on
     * plain builds it is claimed in the built-in store. Either way the user
     * never sees a configuration error.
     */
    fun registerWithEmail(
        email: String,
        password: String,
        username: String,
        scope: CoroutineScope,
        onSuccess: (NoxaUser) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val trimmedName = username.trim()
        UsernameRegistry.validate(trimmedName)?.let { onError(it); return }
        if (password.length < 6) {
            onError("Password minimal 6 karakter.")
            return
        }
        if (!LocalAccountStore.isEmailFormatValid(email)) {
            onError("Format email tidak valid.")
            return
        }

        _isSigningIn.value = true

        // The shared keyless registry is consulted on EVERY build — it is the
        // app's real account store (roles, support chat, admin console).
        scope.launch {
            try {
                // One uid for both registries, generated up front.
                    val uid = "noxa-" + System.currentTimeMillis().toString(36) +
                        "-" + (1000..9999).random()
                    val (remoteLogin, remoteFailure) =
                        backend.register(uid, email.trim(), trimmedName, password)

                    when {
                        // Real conflict (email/username already registered
                        // globally) — refuse before creating anything.
                        remoteFailure != null &&
                            remoteFailure != com.example.data.support.NoxaBackend.UNREACHABLE -> {
                            onError(remoteFailure)
                        }

                        remoteLogin != null -> {
                            // Registered in the shared registry + mirrored
                            // locally under the same uid (offline sign-in).
                            localStore.createAccount(email, trimmedName, password, uid)
                            localStore.storeSessionUid(uid)
                            val user = NoxaUser(
                                uid = uid,
                                displayName = trimmedName,
                                email = email.trim().lowercase(java.util.Locale.ROOT),
                                photoUrl = null,
                                role = remoteLogin.role
                            )
                            storeRemoteSession(user)
                            _currentUser.value = user
                            _isGuestMode.value = false
                            onSuccess(user)
                        }

                        // Registry offline: Firebase Email/Password when the
                        // project has it enabled, else the built-in local
                        // store — registration never dead-ends.
                        else -> {
                            try {
                                val registered = registerWithFirebase(
                                    email.trim(), trimmedName, password
                                )
                                if (!registered) {
                                    registerWithoutRegistry(
                                        email, trimmedName, password, onSuccess, onError
                                    )
                                }
                            } catch (e: Exception) {
                                onError(humanizeAuthError(e))
                            }
                        }
                    }
                } catch (e: Exception) {
                    onError("Pendaftaran gagal. Coba lagi.")
                } finally {
                    _isSigningIn.value = false
                }
            }
        }    /**
     * Registration while the shared registry is unreachable: the built-in
     * local store keeps sign-up working offline. Firebase's email/password
     * provider is disabled in this project (it surfaced raw English errors
     * like "operation-not-allowed"), so it is deliberately not used.
     */
    private suspend fun registerWithoutRegistry(
        email: String,
        username: String,
        password: String,
        onSuccess: (NoxaUser) -> Unit,
        onError: (String) -> Unit
    ) {
        when (val result = localStore.createAccount(email, username, password)) {
            is LocalAccountStore.CreateResult.Failed -> onError(result.message)
            is LocalAccountStore.CreateResult.Created -> {
                val account = result.account
                localStore.storeSessionUid(account.uid)
                val user = NoxaUser(
                    uid = account.uid,
                    displayName = account.username,
                    email = account.email,
                    photoUrl = null,
                    role = roleForEmail(account.email)
                )
                _currentUser.value = user
                _isGuestMode.value = false
                // Offline-created accounts still join the shared registry as
                // soon as the network is back — the dev console counts them.
                syncToRegistry(user)
                onSuccess(user)
            }
        }
    }

    /**
     * Registration (or account recovery) through Firebase Email/Password.
     * The provider is enabled in the project and needs no SHA registration,
     * so sign-up keeps working even when the keyless registry is
     * unreachable. Returns true when a session was established.
     */
    private suspend fun registerWithFirebase(
        email: String,
        username: String,
        password: String
    ): Boolean {
        val firebaseAuth = auth ?: return false
        val cleanEmail = email.trim().lowercase(java.util.Locale.ROOT)
        val result = try {
            firebaseAuth.createUserWithEmailAndPassword(cleanEmail, password).await()
        } catch (e: Exception) {
            val lower = e.message.orEmpty().lowercase()
            if ("email already" in lower || "email_exists" in lower) {
                // The address already lives in Firebase from an earlier
                // attempt — recover the session with the same password.
                try {
                    firebaseAuth.signInWithEmailAndPassword(cleanEmail, password).await()
                } catch (e2: Exception) {
                    Log.w("AuthManager", "Firebase email recover failed: ${e2.message}")
                    return false
                }
            } else {
                Log.w("AuthManager", "Firebase email register failed: ${e.message}")
                return false
            }
        }
        val fbUser = result.user ?: return false
        try {
            val profile = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build()
            fbUser.updateProfile(profile).await()
        } catch (e: Exception) {
            Log.w("AuthManager", "Firebase profile update failed: ${e.message}")
        }
        // Mirror locally so the next sign-in also works offline.
        localStore.createAccount(cleanEmail, username, password, fbUser.uid)
        localStore.storeSessionUid(fbUser.uid)
        val user = NoxaUser(
            uid = fbUser.uid,
            displayName = username,
            email = cleanEmail,
            photoUrl = null,
            role = roleForEmail(cleanEmail)
        )
        storeRemoteSession(user)
        _currentUser.value = user
        _isGuestMode.value = false
        syncToRegistry(user)
        return true
    }

    /**
     * Sky's Google account doubles as the developer account, so signing in
     * with it always opens the NOXA Admin console — no email+password
     * round-trip needed.
     */
    private fun roleForEmail(email: String?): com.example.data.support.AccountRole =
        if (email.equals(DEVELOPER_GOOGLE_EMAIL, ignoreCase = true)) {
            com.example.data.support.AccountRole.DEVELOPER
        } else {
            com.example.data.support.AccountRole.USER
        }

    /** Maps provider errors to Indonesian, human messages (never raw codes). */
    private fun humanizeAuthError(e: Exception): String {
        val msg = e.message.orEmpty()
        val lower = msg.lowercase()
        return when {
            "email_exists" in lower || "email already" in lower ->
                "Email sudah terdaftar. Gunakan Masuk."
            "operation-not-allowed" in lower || "sign-in provider is disabled" in lower ->
                "Metode masuk ini belum diaktifkan di proyek auth. " +
                    "Gunakan masuk dengan Google, atau coba lagi nanti."
            "missing_android_integration" in lower || "android integration" in lower ->
                "Login Google ditolak Firebase: aplikasi (package com.noxa.music) " +
                    "belum terdaftar di konsol Firebase — daftarkan dengan SHA-1 " +
                    "keystore rilis, lalu unggah google-services.json baru."
            "invalid_email" in lower || "malformed" in lower || "badly formatted" in lower ->
                "Format email tidak valid."
            "weak_password" in lower -> "Password minimal 6 karakter."
            "invalid_login_credentials" in lower || "password is invalid" in lower ||
                "no user record" in lower || "user-not-found" in lower ->
                "Email atau password salah."
            "network" in lower -> "Koneksi internet bermasalah. Coba lagi."
            else -> msg.ifBlank { "Autentikasi gagal. Coba lagi." }
        }
    }

    fun attemptSilentAutoSignIn(scope: CoroutineScope) {
        if (auth?.currentUser != null) return
        val serverClientId = webClientId() ?: return

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth?.signInWithCredential(authCredential)?.await()
                }
            } catch (e: Exception) {
                // Silent check failure is expected on non-configured accounts; continue gracefully
            }
        }
    }

    /**
     * The OAuth web client id is generated by the Google Services plugin from
     * `google-services.json`. It is resolved by name so that the app still
     * compiles and runs (in guest mode) when that file is not present.
     */
    private fun webClientId(): String? {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resId == 0) return null
        return try {
            context.getString(resId).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------- account settings

    /**
     * Changes the signed-in account's username (Pengaturan akun). Updates the
     * shared registry when reachable; the local mirror always follows, so the
     * rename survives offline. Returns through [onError]/[onSuccess].
     */
    fun changeUsername(
        newUsername: String,
        scope: CoroutineScope,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val user = _currentUser.value
        if (user == null) {
            onError("Belum masuk ke akun.")
            return
        }
        UsernameRegistry.validate(newUsername)?.let { onError(it); return }
        val trimmed = newUsername.trim()
        if (trimmed.equals(user.displayName, ignoreCase = true)) {
            onError("Username sama dengan yang sekarang.")
            return
        }
        scope.launch {
            try {
                // Per-device cooldown; registry accounts are additionally
                // checked server-side so the 7-day rule holds everywhere.
                val localWait = localStore.cooldownRemaining(
                    user.uid, "username", com.example.data.support.NoxaBackend.USERNAME_COOLDOWN_MS
                )
                if (localWait > 0L) {
                    onError(com.example.data.support.NoxaBackend.cooldownMessage("username", localWait))
                    return@launch
                }
                val reachable = runCatching { backend.readState() != null }.getOrDefault(false)
                if (reachable) {
                    val refusal = backend.updateUsername(user.uid, trimmed)
                    if (refusal != null) {
                        onError(refusal)
                        return@launch
                    }
                }
                localStore.updateUsername(user.uid, trimmed)
                localStore.markChanged(user.uid, "username")
                val updated = user.copy(displayName = trimmed)
                if (reachable) storeRemoteSession(updated)
                _currentUser.value = updated
                onSuccess(trimmed)
            } catch (e: Exception) {
                onError("Ganti username gagal. Coba lagi.")
            }
        }
    }

    /**
     * Changes the password after re-verifying the current one (remote first,
     * local fallback). Both stores are updated so either sign-in path works.
     */
    fun changePassword(
        currentPassword: String,
        newPassword: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val user = _currentUser.value
        if (user == null) {
            onError("Belum masuk ke akun.")
            return
        }
        if (newPassword.length < 6) {
            onError("Password baru minimal 6 karakter.")
            return
        }
        if (currentPassword == newPassword) {
            onError("Password baru harus berbeda dari yang lama.")
            return
        }
        scope.launch {
            try {
                // Per-device cooldown for the 14-day password rule.
                val localWait = localStore.cooldownRemaining(
                    user.uid, "password", com.example.data.support.NoxaBackend.PASSWORD_COOLDOWN_MS
                )
                if (localWait > 0L) {
                    onError(com.example.data.support.NoxaBackend.cooldownMessage("password", localWait))
                    return@launch
                }
                val email = user.email
                if (email.isNullOrBlank()) {
                    onError("Akun ini tidak punya email untuk verifikasi.")
                    return@launch
                }
                // Verify the current password wherever the account lives.
                var verified = false
                var remoteReachable = false
                when (val remote = backend.authenticate(email, currentPassword)) {
                    is com.example.data.support.RemoteAuthResult.Ok -> {
                        verified = true
                        remoteReachable = true
                    }

                    com.example.data.support.RemoteAuthResult.Rejected -> {
                        remoteReachable = true
                    }

                    com.example.data.support.RemoteAuthResult.Unreachable -> {
                        val account = localStore.authenticate(email, currentPassword)
                        verified = account != null
                    }
                }
                if (!verified) {
                    onError(
                        if (remoteReachable) "Password lama salah."
                        else "Password lama salah."
                    )
                    return@launch
                }
                var refusal: String? = null
                if (remoteReachable) {
                    refusal = backend.updatePassword(user.uid, newPassword)
                }
                if (remoteReachable && refusal != null) {
                    onError(refusal)
                    return@launch
                }
                localStore.updatePassword(user.uid, newPassword)
                localStore.markChanged(user.uid, "password")
                onSuccess()
            } catch (e: Exception) {
                onError("Ganti password gagal. Coba lagi.")
            }
        }
    }

    /**
     * Permanently deletes the signed-in account after the caller re-confirms
     * the email. The registry account (and its support thread), the Firebase
     * user when this session came from it, and the on-device mirror are all
     * cleared before the session ends.
     */
    fun deleteAccount(
        confirmEmail: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val user = _currentUser.value
        if (user == null) {
            onError("Belum masuk ke akun.")
            return
        }
        val email = user.email
        if (email.isNullOrBlank()) {
            onError("Akun ini tidak punya email untuk verifikasi.")
            return
        }
        if (!email.equals(confirmEmail.trim(), ignoreCase = true)) {
            onError("Email tidak sesuai. Ketik email akun kamu: $email")
            return
        }
        scope.launch {
            try {
                // 1) Global registry (account + support thread).
                runCatching { backend.deleteAccount(email) }
                // 2) Firebase user, when the session lives there.
                val fbUser = auth?.currentUser
                if (fbUser != null && fbUser.email.equals(email, ignoreCase = true)) {
                    runCatching { fbUser.delete().await() }
                }
                // 3) On-device mirror.
                runCatching { localStore.deleteAccount(email) }
                // 4) End the session everywhere.
                auth?.signOut()
                localStore.clearSession()
                clearRemoteSession()
                _currentUser.value = null
                _isGuestMode.value = true
                onSuccess()
            } catch (e: Exception) {
                Log.e("AuthManager", "Delete account failed", e)
                onError("Hapus akun gagal. Coba lagi.")
            }
        }
    }

    fun signOut(scope: CoroutineScope, onComplete: () -> Unit = {}) {
        auth?.signOut()
        localStore.clearSession()
        clearRemoteSession()
        _currentUser.value = null
        _isGuestMode.value = true
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e("AuthManager", "Failed to clear credential state", e)
            } finally {
                onComplete()
            }
        }
    }

    private companion object {
        /** Sky's Google account — auto-promoted to DEVELOPER everywhere. */
        const val DEVELOPER_GOOGLE_EMAIL = "sekay77771@gmail.com"

        const val KEY_REMOTE_UID = "uid"
        const val KEY_REMOTE_USERNAME = "username"
        const val KEY_REMOTE_EMAIL = "email"
        const val KEY_REMOTE_ROLE = "role"
    }
}
