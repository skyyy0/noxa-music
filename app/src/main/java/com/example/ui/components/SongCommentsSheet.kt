package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.support.SongComment
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.viewmodel.MusicViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * **Komentar per Lagu** — the shared comment thread under the current song.
 *
 * Read-only thread + a one-line composer; posting is guarded twice (here for
 * UX, in the view model for truth) so a signed-out listener sees an honest
 * prompt instead of writing a nameless comment to the shared document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongCommentsSheet(
    ui: MusicViewModel.SongCommentsUi,
    isSignedIn: Boolean,
    onDismiss: () -> Unit,
    onPost: (String) -> Unit,
    /** Tapping the signed-out prompt (opens auth). */
    onSignIn: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(ui.song.id) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NoxaSurface,
        contentColor = NoxaTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.72f)
                .imePadding()
                .navigationBarsPadding()
        ) {
            // ------------------------------------------------------- header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Komentar",
                            color = NoxaTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Live count chip — honest even while loading (shows
                        // only what has actually arrived).
                        val count = ui.comments.size
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(NoxaLime.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (ui.loading && count == 0) "…" else count.toString(),
                                color = NoxaLime,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "${ui.song.title} • ${ui.song.artist}",
                        color = NoxaTextTertiary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup komentar",
                        tint = NoxaTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    ui.loading -> CircularProgressIndicator(
                        color = NoxaLime,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(26.dp)
                    )

                    ui.comments.isEmpty() -> Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ModeComment,
                            contentDescription = null,
                            tint = NoxaTextTertiary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Belum ada komentar",
                            color = NoxaTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Jadilah yang pertama meninggalkan pesan di lagu ini.",
                            color = NoxaTextTertiary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp, end = 20.dp, bottom = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(ui.comments, key = { it.id }) { comment ->
                            CommentRow(comment)
                        }
                    }
                }
            }

            // Inline failure — the draft stays in the composer untouched.
            ui.error?.let { message ->
                Text(
                    text = message,
                    color = com.example.ui.theme.NoxaError,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            // ---------------------------------------------------- composer
            if (isSignedIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = draft,
                        onValueChange = { input ->
                            // Hard cap mirrors the backend's 400-char limit.
                            draft = input.take(400)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus),
                        placeholder = {
                            Text("Tulis komentar…", color = NoxaTextTertiary, fontSize = 13.sp)
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = NoxaSurfaceSecondary,
                            unfocusedContainerColor = NoxaSurfaceSecondary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = NoxaLime
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val text = draft.trim()
                            if (text.isNotEmpty() && !ui.posting) {
                                onPost(text)
                                draft = ""
                                keyboard?.hide()
                            }
                        })
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (draft.isNotBlank() && !ui.posting) NoxaLime
                                else NoxaSurfaceElevated
                            )
                            .border(
                                1.dp,
                                if (draft.isNotBlank() && !ui.posting) NoxaLime else NoxaBorder,
                                CircleShape
                            )
                            .clickable(enabled = draft.isNotBlank() && !ui.posting) {
                                val text = draft.trim()
                                if (text.isNotEmpty()) {
                                    onPost(text)
                                    draft = ""
                                    keyboard?.hide()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (ui.posting) {
                            CircularProgressIndicator(
                                color = NoxaLime,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Kirim komentar",
                                tint = if (draft.isNotBlank()) NoxaSurface else NoxaTextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NoxaSurfaceSecondary)
                        .clickable { onSignIn() }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Masuk untuk berkomentar",
                        color = NoxaLime,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: SongComment) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(NoxaLime.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = comment.name.take(1).uppercase(Locale.ROOT),
                color = NoxaLime,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.name,
                    color = NoxaTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = relativeTime(comment.at),
                    color = NoxaTextTertiary,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = comment.text,
                color = NoxaTextSecondary,
                fontSize = 13.sp,
                lineHeight = 17.sp
            )
        }
    }
}

/** Indonesian relative time: "baru saja", "5 menit lalu", "2 jam lalu", then a date. */
private fun relativeTime(at: Long): String {
    if (at <= 0L) return ""
    val diff = System.currentTimeMillis() - at
    if (diff < 60_000L) return "baru saja"
    val minutes = diff / 60_000L
    if (minutes < 60) return "$minutes menit lalu"
    val hours = diff / 3_600_000L
    if (hours < 24) return "$hours jam lalu"
    val days = diff / 86_400_000L
    if (days < 7) return "$days hari lalu"
    val sameYear = Calendar.getInstance().get(Calendar.YEAR) ==
        Calendar.getInstance().apply { timeInMillis = at }.get(Calendar.YEAR)
    val pattern = if (sameYear) "d MMM" else "d MMM yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(java.util.Date(at))
}
