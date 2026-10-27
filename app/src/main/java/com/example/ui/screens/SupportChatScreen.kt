package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.support.SupportMessage
import com.example.data.support.SupportThread
import com.example.ui.components.SupportIdentityRow
import com.example.ui.components.VerifiedBadgePurple
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaPurple
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the support screen is currently showing. */
sealed interface SupportUiState {
    data object Loading : SupportUiState

    /** Backend unreachable — chat cannot load or send. */
    data object Offline : SupportUiState

    /** Signed-out visitors are asked to log in first. */
    data object NeedsLogin : SupportUiState

    data class Ready(
        val thread: SupportThread?,
        val sending: Boolean = false,
        val error: String? = null
    ) : SupportUiState
}

/**
 * "Chat dengan NOXA Support" — the in-app bug report / complaint chat.
 *
 * Sky's rule: a user can send **one message until Support replies**; the
 * composer locks after each send and unlocks the moment a support message
 * lands. Support (the developer account) answers from the NOXA Admin console.
 */
@Composable
fun SupportChatScreen(
    state: SupportUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSend: (String) -> Unit,
    onLoginHint: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Centered, width-capped column: on tablets and wide phones the chat
    // stays a comfortable reading width instead of stretching edge to edge.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack),
        contentAlignment = Alignment.Center
    ) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .background(NoxaBlack)
            // Without statusBarsPadding the back button slid under the clock
            // on tall-status-bar devices.
            .statusBarsPadding()
            // Keyboard and nav bar share the bottom edge — pad by the larger
            // of the two so the composer never floats above the keyboard.
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    ) {
        SupportChatHeader(onBack = onBack)

        when (val s = state) {
            SupportUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NoxaLime, strokeWidth = 2.5.dp)
            }

            SupportUiState.NeedsLogin -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                VerifiedBadgePurple(size = 56.dp)
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Masuk dulu untuk chat Support",
                    color = NoxaTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Chat NOXA Support terikat ke akun kamu, supaya balasan tidak hilang saat ganti perangkat.",
                    color = NoxaTextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (onLoginHint != null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(NoxaLime)
                            .clickable { onLoginHint() }
                            .padding(horizontal = 24.dp, vertical = 11.dp)
                    ) {
                        Text(
                            text = "Ke halaman masuk",
                            color = NoxaBlack,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            SupportUiState.Offline -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Server NOXA tidak terjangkau",
                    color = NoxaTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Chat support butuh koneksi internet. Coba lagi sebentar lagi.",
                    color = NoxaTextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, NoxaBorder, RoundedCornerShape(20.dp))
                        .clickable { onRefresh() }
                        .padding(horizontal = 24.dp, vertical = 11.dp)
                ) {
                    Text("Coba lagi", color = NoxaLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            is SupportUiState.Ready -> ReadyChatBody(s, onSend)
        }
    }
    }
}

@Composable
private fun SupportChatHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = NoxaTextPrimary
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(NoxaPurple),
            contentAlignment = Alignment.Center
        ) {
            Text("N", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            SupportIdentityRow(name = "NOXA Support")
            Text(
                text = "Balasan biasanya dalam hitungan menit",
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
        }
        // Badge lives inside SupportIdentityRow only — the old second badge
        // on the right edge duplicated it.
        Spacer(modifier = Modifier.width(10.dp))
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NoxaBorder))
}

@Composable
private fun ReadyChatBody(state: SupportUiState.Ready, onSend: (String) -> Unit) {
    val thread = state.thread
    val listState = rememberLazyListState()
    // saveable: survives rotation / process recreation instead of wiping a
    // half-typed complaint.
    var draft by rememberSaveable { mutableStateOf("") }
    // Text handed to a send that has not resolved yet — restored on failure
    // so a dropped connection never costs the user their typing.
    var pendingSend by rememberSaveable { mutableStateOf<String?>(null) }

    // Sky's rule — locked while the previous user message is unanswered.
    val canSend = thread?.canUserSend ?: true

    // Resolve the in-flight send: keep the draft on failure, drop it on ack.
    // Keyed on the whole state object — every emission is a new instance, so
    // a repeat failure with an identical message still triggers resolution.
    LaunchedEffect(state) {
        if (!state.sending && pendingSend != null) {
            if (state.error != null) {
                if (draft.isBlank()) draft = pendingSend.orEmpty()
            }
            pendingSend = null
        }
    }

    LaunchedEffect(thread?.messages?.size) {
        val count = thread?.messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // First-time welcome card.
            if (thread == null || thread.messages.isEmpty()) {
                item(key = "welcome") {
                    WelcomeSupportCard()
                }
            }
            // Positional key: "${at}-${from}" can collide when two messages
            // land in the same millisecond, which crashed LazyColumn with
            // "Key was already used".
            itemsIndexed(
                thread?.messages.orEmpty(),
                key = { index, _ -> "msg-$index" }
            ) { _, message ->
                SupportBubble(message = message, isSupport = message.from == "support")
            }
            if (state.sending) {
                item(key = "sending") {
                    Text(
                        text = "Mengirim...",
                        color = NoxaTextTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        state.error?.let { error ->
            Text(
                text = error,
                color = NoxaErrorMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        if (!canSend) {
            LockedNotice()
        }

        Composer(
            value = draft,
            onValueChange = { draft = it },
            enabled = canSend && !state.sending,
            sending = state.sending,
            onSend = {
                val text = draft.trim()
                if (text.isNotEmpty()) {
                    pendingSend = text
                    draft = ""
                    onSend(text)
                }
            }
        )
    }
}

@Composable
private fun WelcomeSupportCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        SupportIdentityRow(
            name = "NOXA Support",
            subtitle = "resmi"
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Halo! Ada bug atau keluhan tentang NOXA? Ceritakan di sini — laporkan langsung dari aplikasi, balasannya muncul di chat ini.",
            color = NoxaTextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
internal fun SupportBubble(message: SupportMessage, isSupport: Boolean) {
    val alignment = if (isSupport) Alignment.Start else Alignment.End
    val background = if (isSupport) NoxaSurfaceElevated else NoxaLime.copy(alpha = 0.16f)
    val textColor = if (isSupport) NoxaTextPrimary else NoxaTextPrimary

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (isSupport) {
            SupportIdentityRow(name = "NOXA Support", badgeSize = 13.dp)
            Spacer(modifier = Modifier.height(4.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    if (isSupport) RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
                    else RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
                )
                .background(background)
                .border(
                    1.dp,
                    if (isSupport) NoxaBorder else NoxaLime.copy(alpha = 0.35f),
                    if (isSupport) RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
                    else RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
                )
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = TIME_FORMAT.format(Date(message.at)),
            color = NoxaTextTertiary,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun LockedNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = NoxaPurple,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = "Pesan terkirim — tunggu balasan NOXA Support. Setelah dibalas, kamu bisa chat bebas lagi.",
            color = NoxaTextTertiary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    sending: Boolean,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (enabled) onValueChange(it) },
            enabled = enabled,
            placeholder = {
                Text(
                    text = if (enabled) "Tulis laporan / keluhan..." else "Menunggu balasan Support",
                    color = NoxaTextTertiary,
                    fontSize = 13.sp
                )
            },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NoxaLime,
                unfocusedBorderColor = NoxaBorder,
                focusedTextColor = NoxaTextPrimary,
                unfocusedTextColor = NoxaTextPrimary,
                disabledTextColor = NoxaTextTertiary,
                disabledBorderColor = NoxaBorder,
                cursorColor = NoxaLime,
                focusedContainerColor = NoxaSurfaceSecondary,
                unfocusedContainerColor = NoxaSurfaceSecondary,
                disabledContainerColor = NoxaSurfaceSecondary
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank() && !sending,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (enabled && value.isNotBlank() && !sending) NoxaLime
                    else NoxaSurfaceSecondary
                )
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = NoxaBlack,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Kirim",
                    tint = if (enabled && value.isNotBlank()) NoxaBlack else NoxaTextTertiary
                )
            }
        }
    }
}

private val NoxaErrorMuted = Color(0xFFFF6B61)

internal val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
