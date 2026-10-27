package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.noxaStrongTap
import com.example.ui.components.noxaTap
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaPurple
import com.example.ui.theme.NoxaPurpleDeep
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.util.FormatUtils

/** One member chip shown in the room roster. */
data class RoomMemberUi(
    val uid: String,
    val name: String,
    val isSelf: Boolean
)

/** What the Dengar Bareng screen is showing. */
sealed interface RoomUiState {
    data object Idle : RoomUiState
    data class Joining(val code: String) : RoomUiState

    data class InRoom(
        val code: String,
        val songId: String = "",
        val songTitle: String = "",
        val songArtist: String = "",
        val songArtwork: String = "",
        val playing: Boolean = false,
        val positionMs: Long = 0L,
        val members: List<RoomMemberUi> = emptyList(),
        /** Vote Skip: distinct ballots from current members. */
        val voteCount: Int = 0,
        /** Strict-majority threshold for the current roster size. */
        val votesNeeded: Int = 1,
        /** Whether *this* member currently has a ballot in. */
        val myVote: Boolean = false
    ) : RoomUiState

    data class Failed(val code: String, val message: String) : RoomUiState
}

/**
 * **Dengar Bareng** — one shared room, everyone hears the same song.
 *
 * iOS-flavoured: the screen starts almost empty (one hero card, one code
 * field, two actions); while in a room the controls are progressive — the
 * big play button is always there, seek + prev/next only unfold when the
 * transport is tapped, and the roster lives in a compact avatar stack.
 */
@Composable
fun RoomScreen(
    state: RoomUiState,
    onBack: () -> Unit,
    onJoin: (String) -> Unit,
    onCreate: () -> Unit,
    onLeave: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    /** Vote Skip — casts/withdraws this member's ballot for the current song. */
    onVoteSkip: () -> Unit,
    /** Smooth local progress (ms) — used when the room plays our own song. */
    localPositionMs: Long,
    localDurationMs: Long,
    modifier: Modifier = Modifier
) {
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
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            RoomHeader(onBack = onBack)

            when (val s = state) {
                RoomUiState.Idle -> RoomIdleBody(onJoin = onJoin, onCreate = onCreate)
                is RoomUiState.Joining -> RoomJoiningBody(code = s.code)
                is RoomUiState.Failed -> RoomFailedBody(
                    code = s.code,
                    message = s.message,
                    onRetry = { onJoin(s.code) }
                )

                is RoomUiState.InRoom -> RoomInRoomBody(
                    state = s,
                    onLeave = onLeave,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onVoteSkip = onVoteSkip,
                    localPositionMs = localPositionMs,
                    localDurationMs = localDurationMs
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RoomHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = NoxaTextPrimary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Dengar Bareng",
                color = NoxaTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Satu room, satu lagu — semua sinkron",
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NoxaBorder)
    )
}

// ------------------------------------------------------------------ idle

@Composable
private fun RoomIdleBody(onJoin: (String) -> Unit, onCreate: () -> Unit) {
    var code by remember { mutableStateOf("") }

    Spacer(modifier = Modifier.height(28.dp))

    // Hero card — the one thing on screen before you act.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(NoxaLime.copy(alpha = 0.12f), NoxaPurple.copy(alpha = 0.12f))
                )
            )
            .border(1.dp, NoxaBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(NoxaLime.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Group,
                contentDescription = null,
                tint = NoxaLime,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "Putar musik bareng teman,\nposisinya sama persis",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Buat room dan bagikan kodenya — siapa pun yang gabung mendengar lagu yang sama di waktu yang sama.",
            color = NoxaTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp
        )
    }

    Spacer(modifier = Modifier.height(26.dp))

    // Code entry: one big capsule, uppercase, monospaced — iOS input style.
    OutlinedRoomCodeField(
        value = code,
        onValueChange = { input ->
            code = input.filter { it.isLetterOrDigit() }.take(6).uppercase(java.util.Locale.ROOT)
        }
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Primary: create. Secondary: join with the typed code.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(NoxaLime)
            .noxaStrongTap { onCreate() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Buat Room Baru", color = NoxaBlack, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(10.dp))

    val canJoin = code.length in 4..6
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(if (canJoin) NoxaSurfaceElevated else NoxaSurfaceSecondary)
            .border(
                1.dp,
                if (canJoin) NoxaLime.copy(alpha = 0.6f) else NoxaBorder,
                RoundedCornerShape(26.dp)
            )
            .noxaTap(enabled = canJoin) { onJoin(code) }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Gabung dengan Kode",
            color = if (canJoin) NoxaLime else NoxaTextTertiary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text(
        "Kode terdiri dari 6 karakter, contoh: K7M2QX",
        color = NoxaTextTertiary,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun OutlinedRoomCodeField(value: String, onValueChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                "KODE ROOM",
                color = NoxaTextTertiary,
                fontSize = 16.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters
        ),
        textStyle = TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 7.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            color = NoxaTextPrimary
        ),
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NoxaLime,
            unfocusedBorderColor = NoxaBorder,
            cursorColor = NoxaLime,
            focusedContainerColor = NoxaSurfaceElevated,
            unfocusedContainerColor = NoxaSurfaceElevated
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ------------------------------------------------------------- joining / failed

@Composable
private fun RoomJoiningBody(code: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(70.dp))
        CircularProgressIndicator(color = NoxaLime, strokeWidth = 2.5.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "Menyambung ke room $code…",
            color = NoxaTextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun RoomFailedBody(code: String, message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        Text(
            "Room $code tidak bisa dimasuki",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            color = NoxaTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(NoxaLime)
                .clickable { onRetry() }
                .padding(horizontal = 26.dp, vertical = 11.dp)
        ) {
            Text("Coba Lagi", color = NoxaBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ------------------------------------------------------------------ in room

@Composable
private fun RoomInRoomBody(
    state: RoomUiState.InRoom,
    onLeave: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onVoteSkip: () -> Unit,
    localPositionMs: Long,
    localDurationMs: Long
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var controlsExpanded by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(16.dp))

    // --- code chip + roster + leave, in one compact row.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(NoxaSurfaceElevated)
                .border(1.dp, NoxaLime.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .noxaTap {
                    clipboard.setText(AnnotatedString(state.code))
                    Toast.makeText(context, "Kode room disalin: ${state.code}", Toast.LENGTH_SHORT)
                        .show()
                }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Salin kode room",
                tint = NoxaLime,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                state.code,
                color = NoxaLime,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Avatar stack: first three members, then "+N".
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            state.members.take(3).forEach { member ->
                RoomAvatar(name = member.name, isSelf = member.isSelf)
            }
            if (state.members.size > 3) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(NoxaPurpleDeep)
                        .border(2.dp, NoxaBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+${state.members.size - 3}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            "Keluar",
            color = NoxaTextTertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .noxaStrongTap { onLeave() }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // --- live sync status: pulsing dot + roster size.
    Row(verticalAlignment = Alignment.CenterVertically) {
        LiveDot()
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            "Sinkron langsung · ${state.members.size} pendengar",
            color = NoxaTextSecondary,
            fontSize = 12.sp
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (state.songId.isBlank()) {
        // Empty room: say what to do instead of showing a dead player.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(NoxaSurfaceElevated)
                .border(1.dp, NoxaBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 20.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = null,
                tint = NoxaTextTertiary,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Room siap — belum ada lagu",
                color = NoxaTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Putar lagu apa saja dari pemutar NOXA. Begitu ada yang memutar,\nsemua anggota room ikut mendengar lagu itu otomatis.",
                color = NoxaTextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
        }
        return
    }

    // --- now playing card (tap the transport below to unfold the extras).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaBorder, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.songArtwork.isNotBlank()) {
            AsyncImage(
                model = state.songArtwork,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NoxaSurfaceSecondary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "SEDANG DIPUTAR BERSAMA",
                color = NoxaLime,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                state.songTitle,
                color = NoxaTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                state.songArtist,
                color = NoxaTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // --- transport: play/pause always; prev/next + seek unfold on tap.
    // The caller zeroes local progress when the room plays another song,
    // so "has local duration" == "room plays our song".
    val duration = if (localDurationMs > 0) localDurationMs else 0L
    val position = if (localDurationMs > 0) localPositionMs else state.positionMs

    // Progress line (tap-free, iOS-style): whole bar is the seek surface.
    var seeking by remember { mutableStateOf<Float?>(null) }
    val shownPosition = seeking ?: position
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsExpanded = !controlsExpanded }
    ) {
        Slider(
            value = shownPosition.toFloat().coerceAtMost((duration.coerceAtLeast(1L)).toFloat()),
            onValueChange = { seeking = it },
            onValueChangeFinished = {
                seeking?.let { onSeekTo(it.toLong()) }
                seeking = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = NoxaLime,
                activeTrackColor = NoxaLime,
                inactiveTrackColor = NoxaSurfaceSecondary
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                FormatUtils.formatDuration(shownPosition.toLong()),
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
            Text(
                if (duration > 0) FormatUtils.formatDuration(duration) else "--:--",
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Prev/next exist only while the transport is unfolded.
        AnimatedVisibility(
            visible = controlsExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Sebelumnya",
                        tint = NoxaTextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.width(30.dp))
            }
        }

        // The one always-on control: big iOS-style play/pause.
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(NoxaLime)
                .clickable { onTogglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.playing) "Jeda" else "Putar",
                tint = NoxaBlack,
                modifier = Modifier.size(34.dp)
            )
        }

        AnimatedVisibility(
            visible = controlsExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(30.dp))
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Berikutnya",
                        tint = NoxaTextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        if (controlsExpanded) "Ketuk baris progres untuk tutup" else "Ketuk baris progres untuk kontrol lanjutan",
        color = NoxaTextTertiary,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(14.dp))

    // --- Vote Skip: the democracy strip under the transport. One ballot per
    // member; a strict majority makes the room's deterministic skipper
    // advance everyone at once. Any playback change resets the count.
    VoteSkipBar(
        state = state,
        onVoteSkip = onVoteSkip
    )
}

@Composable
private fun VoteSkipBar(
    state: RoomUiState.InRoom,
    onVoteSkip: () -> Unit
) {
    val needed = state.votesNeeded.coerceAtLeast(1)
    val count = state.voteCount.coerceIn(0, needed)
    val reached = state.voteCount >= needed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NoxaSurfaceElevated)
            .border(
                1.dp,
                if (state.myVote) NoxaLime.copy(alpha = 0.6f) else NoxaBorder,
                RoundedCornerShape(18.dp)
            )                .noxaTap { onVoteSkip() }
                .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("room_vote_skip")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Vote skip",
                tint = if (state.myVote || reached) NoxaLime else NoxaTextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.myVote) "Vote Skip — kamu ikut" else "Vote Skip",
                    color = if (state.myVote) NoxaLime else NoxaTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        reached -> "Mayoritas tercapai — lagu segera berikutnya!"
                        else -> "${state.voteCount}/$needed anggota setuju lanjut"
                    },
                    color = NoxaTextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            // Vote meter: filled pips, one per required vote.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(needed) { i ->
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (i < count) NoxaLime else NoxaSurfaceSecondary)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomAvatar(name: String, isSelf: Boolean) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (isSelf) NoxaLime else NoxaPurple)
            .border(2.dp, NoxaBlack, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(java.util.Locale.ROOT).ifBlank { "?" },
            color = if (isSelf) NoxaBlack else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/** Small pulsing lime dot — "this room is live". */
@Composable
private fun LiveDot() {
    val transition = rememberInfiniteTransition(label = "live_dot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "live_dot_alpha"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(NoxaLime.copy(alpha = alpha))
    )
}
