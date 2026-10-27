package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.NoxaUser
import com.example.ui.components.PasswordGuardRobot
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary

/**
 * Single entry point for every sign-in method.
 *
 * The home guest card and the Profile card both lead here instead of showing
 * raw auth forms inline, so the first decision a new user makes is simply
 * "how do you want to get in?":
 *
 *  * **Google** — one tap, Firebase sign-in with the Google provider.
 *  * **Email** — classic email + password (Firebase Email/Password, or the
 *    built-in local account when the build carries no Firebase config).
 *    No OTP: email auth never involves a one-time code.
 *  * **Phone** — SMS one-time code (OTP) verified through Firebase phone auth.
 */
@Composable
fun GetStartedScreen(
    currentUser: NoxaUser?,
    isSigningIn: Boolean,
    onBack: () -> Unit,
    onSignInWithGoogle: (Activity, (String) -> Unit) -> Unit,
    onSignInWithEmail: (String, String, (String) -> Unit) -> Unit,
    onRegisterWithEmail: (String, String, String, (String) -> Unit) -> Unit,
    onSendPhoneOtp: (Activity, String, (String) -> Unit, (String) -> Unit) -> Unit,
    onVerifyPhoneOtp: (String, String, (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Which method's form is open. Google needs no form at all.
    var selectedMethod by remember { mutableStateOf<AuthMethod?>(null) }

    // Email / password form state.
    var registerMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var passwordFocused by remember { mutableStateOf(false) }

    // Phone OTP form state.
    var phone by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var otpCode by remember { mutableStateOf("") }

    var authError by remember { mutableStateOf<String?>(null) }

    // Success: the moment a session appears, leave the hub. A user who was
    // already signed in when opening this screen never gets bounced.
    val signedInOnEntry = remember { currentUser != null }
    LaunchedEffect(currentUser) {
        if (!signedInOnEntry && currentUser != null) onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp)
            .testTag("get_started_screen")
    ) {
        // ---- Top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = NoxaTextPrimary
                )
            }
            Text(
                text = "Get Started",
                color = NoxaTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ---- Hero copy ----
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(
                text = "Join NOXA",
                color = NoxaTextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pick how you want in — favorites, playlists and your library follow you everywhere.",
                color = NoxaTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Option 1: Google ----
        SignInOptionCard(
            title = "Continue with Google",
            subtitle = "One tap — use your Google account",
            leading = {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = GoogleGIcon,
                        contentDescription = "Google",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            selected = false,
            enabled = !isSigningIn,
            onClick = {
                authError = null
                if (activity != null) {
                    onSignInWithGoogle(activity) { authError = it }
                } else {
                    authError = "Google Sign-In butuh Activity — coba lagi dari layar utama."
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Option 2: Email / password ----
        SignInOptionCard(
            title = "Continue with Email",
            subtitle = "Sign in or create an account with email & password",
            leading = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(26.dp)
                )
            },
            selected = selectedMethod == AuthMethod.Email,
            enabled = !isSigningIn,
            onClick = {
                authError = null
                selectedMethod =
                    if (selectedMethod == AuthMethod.Email) null else AuthMethod.Email
            }
        )

        if (selectedMethod == AuthMethod.Email) {
            Spacer(modifier = Modifier.height(12.dp))
            EmailAuthForm(
                registerMode = registerMode,
                email = email,
                password = password,
                username = username,
                passwordFocused = passwordFocused,
                isSigningIn = isSigningIn,
                authError = authError,
                onRegisterModeChange = {
                    registerMode = it
                    authError = null
                },
                onEmailChange = { email = it; authError = null },
                onPasswordChange = { password = it; authError = null },
                onUsernameChange = { username = it; authError = null },
                onFocusChange = { passwordFocused = it },
                onSubmit = {
                    val onError: (String) -> Unit = { authError = it }
                    if (registerMode) {
                        onRegisterWithEmail(email.trim(), password, username.trim(), onError)
                    } else {
                        onSignInWithEmail(email.trim(), password, onError)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Option 3: Phone (SMS OTP) ----
        SignInOptionCard(
            title = "Continue with Phone",
            subtitle = "Verify with a one-time code (OTP) sent by SMS",
            leading = {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(26.dp)
                )
            },
            selected = selectedMethod == AuthMethod.Phone,
            enabled = !isSigningIn,
            onClick = {
                authError = null
                selectedMethod =
                    if (selectedMethod == AuthMethod.Phone) null else AuthMethod.Phone
            }
        )

        if (selectedMethod == AuthMethod.Phone) {
            Spacer(modifier = Modifier.height(12.dp))
            PhoneAuthForm(
                phone = phone,
                verificationId = verificationId,
                otpCode = otpCode,
                isSigningIn = isSigningIn,
                authError = authError,
                onPhoneChange = { phone = it; authError = null },
                onOtpChange = { otpCode = it; authError = null },
                onSendCode = {
                    authError = null
                    if (activity != null) {
                        onSendPhoneOtp(activity, phone.trim(), { id ->
                            verificationId = id
                            otpCode = ""
                        }, { authError = it })
                    } else {
                        authError = "OTP butuh Activity — coba lagi dari layar utama."
                    }
                },
                onChangeNumber = {
                    verificationId = null
                    otpCode = ""
                    authError = null
                },
                onVerify = {
                    val id = verificationId
                    if (id != null) {
                        authError = null
                        onVerifyPhoneOtp(id, otpCode.trim()) { authError = it }
                    }
                }
            )
        }

        authError?.let { message ->
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                color = NoxaLime,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .testTag("auth_error_text")
            )
        }

        if (isSigningIn) {
            Spacer(modifier = Modifier.height(18.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NoxaLime, strokeWidth = 2.dp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Guest is always a valid choice — nothing here is forced.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Maybe later — continue as guest",
                color = NoxaTextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("guest_continue_link")
            )
        }
    }
}

private enum class AuthMethod { Email, Phone }

/** One tappable row on the hub: icon, title, subtitle and selection state. */
@Composable
private fun SignInOptionCard(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) NoxaSurfaceElevated else NoxaSurfaceSecondary)
            .border(
                1.dp,
                if (selected) NoxaLime else NoxaBorder,
                RoundedCornerShape(18.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = NoxaTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = NoxaTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/** Email + password form (sign-in / register toggle). No OTP — by design. */
@Composable
private fun EmailAuthForm(
    registerMode: Boolean,
    email: String,
    password: String,
    username: String,
    passwordFocused: Boolean,
    isSigningIn: Boolean,
    authError: String?,
    onRegisterModeChange: (Boolean) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NoxaSurfaceSecondary)
            .border(1.dp, NoxaBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(
            text = if (registerMode) "Create your NOXA account" else "Sign in with email",
            color = NoxaTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (registerMode) {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username unik") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_username_field"),
                colors = hubFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_email_field"),
            colors = hubFieldColors()
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (registerMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PasswordGuardRobot(covering = passwordFocused && password.isNotEmpty())
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onFocusChange(it.isFocused) }
                        .testTag("auth_password_field"),
                    colors = hubFieldColors()
                )
            }
            if (password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                PasswordStrengthBar(
                    password = password,
                    modifier = Modifier.padding(start = 64.dp)
                )
            }
        } else {
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_field"),
                colors = hubFieldColors()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        androidx.compose.material3.Button(
            onClick = onSubmit,
            enabled = !isSigningIn && email.isNotBlank() && password.isNotBlank() &&
                (!registerMode || username.isNotBlank()),
            shape = RoundedCornerShape(20.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = NoxaLime,
                contentColor = NoxaBlack
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_submit_btn")
        ) {
            Text(
                text = if (registerMode) "Create account" else "Sign in",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (registerMode) "Already have an account? Sign in" else "New here? Create account",
            color = NoxaLime,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onRegisterModeChange(!registerMode) }
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("auth_mode_toggle")
        )
        if (!registerMode) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Email login uses a password — no one-time code needed.",
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

/** Phone form: number -> SMS OTP -> verify. */
@Composable
private fun PhoneAuthForm(
    phone: String,
    verificationId: String?,
    otpCode: String,
    isSigningIn: Boolean,
    authError: String?,
    onPhoneChange: (String) -> Unit,
    onOtpChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onChangeNumber: () -> Unit,
    onVerify: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NoxaSurfaceSecondary)
            .border(1.dp, NoxaBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        if (verificationId == null) {
            Text(
                text = "Verify your number",
                color = NoxaTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "We'll text you a 6-digit code. Use country code, e.g. +62812xxxxxxx.",
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = onPhoneChange,
                label = { Text("Phone number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_phone_field"),
                colors = hubFieldColors()
            )
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.Button(
                onClick = onSendCode,
                enabled = !isSigningIn && phone.trim().length >= 8,
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = NoxaLime,
                    contentColor = NoxaBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_send_otp_btn")
            ) {
                Text("Send OTP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        } else {
            Text(
                text = "Enter the code",
                color = NoxaTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Code sent to $phone via SMS.",
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = otpCode,
                onValueChange = onOtpChange,
                label = { Text("One-time code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_otp_field"),
                colors = hubFieldColors()
            )
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.Button(
                onClick = onVerify,
                enabled = !isSigningIn && otpCode.trim().length >= 4,
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = NoxaLime,
                    contentColor = NoxaBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_verify_otp_btn")
            ) {
                Text("Verify & Continue", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Wrong number? Change it",
                color = NoxaLime,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isSigningIn) { onChangeNumber() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("auth_change_number")
            )
        }
    }
}

/**
 * The official four-colour Google "G" — the brand asset paths on the 24dp
 * grid (red/yellow/green/blue), not a text glyph. `tint` must be
 * [Color.Unspecified] wherever this is used or Compose would flatten it.
 */
private val GoogleGIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GoogleG",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        listOf(
            Color(0xFFEA4335) to "M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z",
            Color(0xFFFBBC05) to "M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z",
            Color(0xFF34A853) to "M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z",
            Color(0xFF4285F4) to "M23.06 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
        ).forEach { (color, pathData) ->
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(color)
            )
        }
    }.build()
}

@Composable
private fun hubFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NoxaLime,
    unfocusedBorderColor = NoxaBorder,
    focusedLabelColor = NoxaLime,
    unfocusedLabelColor = NoxaTextSecondary,
    focusedTextColor = NoxaTextPrimary,
    unfocusedTextColor = NoxaTextPrimary,
    cursorColor = NoxaLime
)
