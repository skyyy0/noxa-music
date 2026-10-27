package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ai.AiChatMessage
import com.example.ui.components.noxaTap
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaLimeDark
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary

private val AiSuggestions = listOf(
    "Putar Dewa 19 Kangen",
    "Rekomendasi lagu pop Indonesia",
    "Buat playlist fokus kerja",
    "Lagu santai untuk perjalanan"
)

/**
 * **NOXA AI** — the playful music-assistant chat.
 *
 * While the model is "thinking", a lime gradient shimmer sweeps a skeleton
 * row and the avatar pulses: the modern AI-thinking feel, in NOXA's own
 * lime-on-black identity (the reference screenshots were purple).
 */
@Composable
fun AiChatScreen(
    messages: List<AiChatMessage>,
    isThinking: Boolean,
    error: String?,
    playingSongId: String?,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onPlaySongMessage: (AiChatMessage) -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(messages.size, isThinking) {
        val last = messages.size + (if (isThinking) 1 else 0)
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    // Centered + width-capped: comfortable on tablets and tall/wide phones.
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
            .statusBarsPadding()
            // Keyboard and navigation bar share the bottom edge: taking the
            // LARGER of the two (instead of padding both) keeps the composer
            // exactly above the keyboard on 3-button nav phones too, where
            // the old double padding shoved the input row up under the header.
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    ) {
        AiHeader(onBack = onBack, onClear = onClearChat)

        error?.let { message ->
            Text(
                text = message,
                color = Color(0xFFFF6B61),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && !isThinking) {
                item(key = "welcome") {
                    AiWelcomeCard(
                        onSuggestion = { draft = it }
                    )
                }
            }
            // Positional key: two identical AI replies used to share a key
            // ("hash-text") and crash the list with "Key was already used".
            itemsIndexed(messages, key = { index, message -> "$index-${message.fromUser}" }) { index, message ->
                AiMessageRow(
                    message = message,
                    isPlaying = message.songId != null && message.songId == playingSongId,
                    // Only the freshest reply types itself out; the rest of
                    // the transcript renders instantly (and stays that way
                    // when the item is scrolled off and back on).
                    typewriter = !message.fromUser && index == messages.lastIndex,
                    onPlaySong = { onPlaySongMessage(message) }
                )
            }
            if (isThinking) {
                item(key = "thinking") {
                    AiThinkingRow()
                }
            }
        }

        AiComposer(
            value = draft,
            onValueChange = { draft = it },
            enabled = !isThinking,
            onSend = {
                val text = draft.trim()
                if (text.isNotEmpty() && !isThinking) {
                    draft = ""
                    onSend(text)
                }
            }
        )
    }
    }
}

@Composable
private fun AiHeader(onBack: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = NoxaTextPrimary
            )
        }
        AiAvatar(size = 40.dp)
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "NOXA AI",
                color = NoxaTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Asisten musik pribadi",
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
        }
        IconButton(onClick = onClear) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = "Bersihkan chat",
                tint = NoxaTextSecondary
            )
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NoxaBorder))
}

/** The lime-gradient robot avatar with the AutoAwesome spark. */
@Composable
fun AiAvatar(
    size: Dp,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false
) {
    val infinite = rememberInfiniteTransition(label = "ai_avatar")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "ai_pulse"
    )
    val scale = if (pulsing) 1f + 0.08f * pulse else 1f
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .clip(RoundedCornerShape(size * 0.32f))
                .background(
                    Brush.linearGradient(
                        listOf(NoxaLime, NoxaLimeDark)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = NoxaBlack,
                modifier = Modifier.size(size * 0.56f)
            )
        }
    }
}

@Composable
private fun AiWelcomeCard(onSuggestion: (String) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            AiAvatar(size = 28.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                    .background(NoxaSurfaceElevated)
                    .border(1.dp, NoxaBorder, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    "Halo, saya NOXA AI — asisten musik Anda. Ingin mendengarkan lagu apa hari ini? Minta saya putar lagu, menyusun playlist, atau ceritakan suasana hati Anda.",
                    color = NoxaTextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "Coba:",
            color = NoxaTextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        AiSuggestions.forEach { suggestion ->
            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(NoxaSurfaceSecondary)
                        .border(1.dp, NoxaBorder, RoundedCornerShape(18.dp))
                        .noxaTap { onSuggestion(suggestion) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        suggestion,
                        color = NoxaLime,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AiMessageRow(
    message: AiChatMessage,
    isPlaying: Boolean,
    typewriter: Boolean = false,
    onPlaySong: () -> Unit
) {
    if (message.fromUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                    .background(NoxaLime.copy(alpha = 0.16f))
                    .border(
                        1.dp,
                        NoxaLime.copy(alpha = 0.35f),
                        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
                    )
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            ) {
                Text(message.text, color = NoxaTextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        return
    }

    // Typewriter reveal: the reply is uncovered a few characters at a time
    // with a blinking lime caret — the assistant feels like it is writing
    // instead of pasting. rememberSaveable keeps it finished when the item
    // scrolls off-screen and back.
    val fullText = message.text
    var revealedLength by rememberSaveable(message.text, typewriter) {
        mutableStateOf(if (typewriter) 0 else fullText.length)
    }
    val typing = revealedLength < fullText.length
    if (typing) {
        LaunchedEffect(fullText) {
            while (revealedLength < fullText.length) {
                kotlinx.coroutines.delay(16L)
                revealedLength = minOf(fullText.length, revealedLength + 2)
            }
        }
    }
    // One-shot orbit halo the first time an action-bearing reply appears.
    val hasAction = message.songTitle != null

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (hasAction) ActionHalo()
                AiAvatar(size = 28.dp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                    .background(NoxaSurfaceElevated)
                    .border(1.dp, NoxaBorder, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fullText.take(revealedLength),
                        color = NoxaTextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    if (typing) {
                        // Blinking caret while the answer is still writing.
                        val caretAlpha by rememberInfiniteTransition(
                            label = "ai_caret"
                        ).animateFloat(
                            initialValue = 0.15f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                tween(560), RepeatMode.Reverse
                            ),
                            label = "ai_caret_alpha"
                        )
                        Text(
                            "\u258c",
                            color = NoxaLime.copy(alpha = caretAlpha),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = message.songTitle != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
        ) {
            Column {
                message.songTitle?.let { title ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.padding(start = 36.dp)) {
                        AiSongCard(
                            title = title,
                            artist = message.songArtist.orEmpty(),
                            artwork = message.songArtwork,
                            note = message.songNote,
                            isPlaying = isPlaying,
                            onPlay = onPlaySong
                        )
                    }
                }
            }
        }
    }
}

/**
 * One-shot lime ring that expands and fades out of the avatar the first time
 * a reply that *did something* (queued a song, built a mix) appears — the
 * "wah" beat for AI actions. One ring, 900 ms, never loops.
 */
@Composable
private fun ActionHalo() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val p = progress.value
    Box(
        modifier = Modifier
            .size(28.dp * (1f + 1.1f * p))
            .graphicsLayer { alpha = (1f - p) * 0.8f }
            .border(1.5.dp, NoxaLime, CircleShape)
    )
}

/** The "Dipilih oleh NOXA AI" card — artwork, title, play control, note. */
@Composable
private fun AiSongCard(
    title: String,
    artist: String,
    artwork: String?,
    note: String?,
    isPlaying: Boolean,
    onPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaLime.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!artwork.isNullOrBlank()) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NoxaSurfaceSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NoxaLime,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "DIPILIH OLEH NOXA AI",
                    color = NoxaLime,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    title,
                    color = NoxaTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Start
                )
                Text(
                    artist,
                    color = NoxaTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NoxaLime)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Sedang diputar" else "Putar",
                    tint = NoxaBlack,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(NoxaSurfaceSecondary)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isPlaying) 1f else 0.35f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(NoxaLime)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isPlaying) "Sedang diputar" else "Ketuk untuk putar",
                color = NoxaTextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            if (note != null) {
                Text(
                    " · $note",
                    color = NoxaTextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * The thinking state: a pulsing avatar beside a skeleton row whose lime
 * gradient sweeps left to right — the "NOXA AI is working on it" effect.
 */
@Composable
private fun AiThinkingRow() {
    val infinite = rememberInfiniteTransition(label = "ai_thinking")
    val sweep by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300)),
        label = "ai_sweep"
    )

    Row(verticalAlignment = Alignment.Bottom) {
        AiAvatar(size = 28.dp, pulsing = true)
        Spacer(modifier = Modifier.width(8.dp))
        BoxWithConstraints(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .background(NoxaSurfaceElevated)
                .border(1.dp, NoxaBorder, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            val rowWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val shimmerBrush = remember(sweep, rowWidth) {
                Brush.linearGradient(
                    colors = listOf(
                        NoxaSurfaceSecondary,
                        NoxaLime.copy(alpha = 0.30f),
                        NoxaSurfaceSecondary
                    ),
                    start = Offset(sweep * rowWidth - rowWidth * 0.5f, 0f),
                    end = Offset(sweep * rowWidth, 0f)
                )
            }
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .padding(bottom = if (index == 0) 8.dp else 0.dp)
                        .height(11.dp)
                        .width(if (index == 0) 170.dp else 110.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "NOXA AI sedang memproses...",
                color = NoxaTextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AiComposer(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
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
                    "Tulis pesan ke NOXA...",
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
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (enabled && value.isNotBlank()) NoxaLime else NoxaSurfaceSecondary
                )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Kirim",
                tint = if (enabled && value.isNotBlank()) NoxaBlack else NoxaTextTertiary
            )
        }
    }
}
