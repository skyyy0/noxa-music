package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Song
import com.example.media.PlaybackProgress
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.util.ShareCardRenderer
import com.example.ui.viewmodel.LyricsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Synchronised lyrics.
 *
 * The highlighted line is derived from the player's real position, so it tracks
 * the audio exactly instead of drifting. Because some lyric entries (and Deezer's
 * 30 second previews) are not aligned to the same second, the panel also offers a
 * ±0.5s nudge that is applied on top of the timestamps — that is the honest fix
 * for "liriknya terlalu cepat / terlalu lambat" rather than an artificial delay.
 */
@Composable
fun LyricsPanel(
    lyricsState: LyricsUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    onSeekTo: (Long) -> Unit,
    onRetry: () -> Unit,
    /** Now-playing song — powers "Bagikan lirik" (null hides nothing: card still renders). */
    song: Song? = null,
    modifier: Modifier = Modifier
) {
    var offsetMs by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    var sharingLyrics by remember { mutableStateOf(false) }

    /** Renders the verse as a story card and hands it to the system sheet. */
    fun shareLines(lines: List<String>, highlight: Int) {
        if (sharingLyrics || lines.isEmpty()) return
        sharingLyrics = true
        shareScope.launch {
            val ok = withContext(Dispatchers.IO) {
                ShareCardRenderer.shareLyrics(context, song, lines, highlight)
            }
            sharingLyrics = false
            if (!ok) {
                android.widget.Toast.makeText(
                    context,
                    "Gagal membuat gambar lirik. Coba lagi.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    when (val state = lyricsState) {
        LyricsUiState.Idle -> LyricsMessage("Pilih lagu untuk melihat liriknya.", null, null)
        LyricsUiState.Loading -> LyricsMessage("Mencari lirik...", null, null, loading = true)
        is LyricsUiState.Unavailable -> LyricsMessage(state.message, "Coba lagi", onRetry)

        is LyricsUiState.Ready -> {
            val lyrics = state.lyrics
            if (!lyrics.isSynced) {
                // Plain (unsynchronised) lyrics — still useful, clearly labelled.
                LazyColumn(
                    modifier = modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item {
                        Text(
                            text = "Lirik ini tidak punya penanda waktu, jadi tidak bisa disinkronkan.",
                            color = NoxaTextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    item {
                        LyricShareButton(
                            sharing = sharingLyrics,
                            onClick = {
                                val lines = lyrics.plainText.orEmpty().lineSequence()
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .take(LYRIC_SHARE_MAX_LINES)
                                    .toList()
                                shareLines(lines, highlight = -1)
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    item {
                        Text(
                            text = lyrics.plainText.orEmpty(),
                            color = NoxaTextSecondary,
                            fontSize = 16.sp,
                            lineHeight = 28.sp
                        )
                    }
                }
                return
            }

            val progress by progressFlow.collectAsStateWithLifecycle()
            val activeIndex = remember(progress.positionMs, offsetMs, lyrics) {
                lyrics.indexAt(progress.positionMs + offsetMs)
            }
            val listState = rememberLazyListState()

            // Follow the song. The offset keeps the active line roughly a third of
            // the way down the panel instead of glued to the bottom edge.
            LaunchedEffect(activeIndex) {
                if (activeIndex >= 0) {
                    listState.animateScrollToItem(
                        index = (activeIndex - 2).coerceAtLeast(0),
                        scrollOffset = 0
                    )
                }
            }

            Column(modifier = modifier.fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .testTag("lyrics_list"),
                    contentPadding = PaddingValues(vertical = 120.dp)
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val isActive = index == activeIndex
                        val color by animateColorAsState(
                            targetValue = if (isActive) NoxaLime else NoxaTextSecondary,
                            label = "lyricColor"
                        )
                        Text(
                            text = line.text,
                            color = color,
                            fontSize = if (isActive) 19.sp else 16.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            lineHeight = 26.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Jump the player to this line (respecting the offset).
                                    onSeekTo((line.timeMs - offsetMs).coerceAtLeast(0L))
                                }
                                .padding(vertical = 6.dp)
                                .testTag("lyric_line_$index")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Geser lirik: ${if (offsetMs >= 0) "+" else ""}${offsetMs / 1000.0}s",
                        color = NoxaTextTertiary,
                        fontSize = 11.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { offsetMs -= OFFSET_STEP_MS }) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Lirik lebih cepat",
                                tint = NoxaTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Lebih cepat", color = NoxaTextPrimary, fontSize = 11.sp)
                        }
                        TextButton(onClick = { offsetMs += OFFSET_STEP_MS }) {
                            Text("Lebih lambat", color = NoxaTextPrimary, fontSize = 11.sp)
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Lirik lebih lambat",
                                tint = NoxaTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (offsetMs != 0L) {
                            TextButton(onClick = { offsetMs = 0L }) {
                                Text("Reset", color = NoxaLime, fontSize = 11.sp)
                            }
                        }
                    }
                }

                LyricShareButton(
                    sharing = sharingLyrics,
                    onClick = {
                        val center = activeIndex.coerceAtLeast(0)
                        val from = (center - 2).coerceAtLeast(0)
                        val window = lyrics.lines
                            .subList(from, minOf(from + LYRIC_SHARE_MAX_LINES, lyrics.lines.size))
                        shareLines(window.map { it.text }, highlight = center - from)
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Sumber lirik: ${lyrics.source}",
                    color = NoxaTextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * "Bagikan lirik" chip — renders the verse around the current line into a
 * story card (see [ShareCardRenderer.shareLyrics]) and opens the share sheet.
 */
@Composable
private fun LyricShareButton(
    sharing: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NoxaSurfaceSecondary)
            .clickable(enabled = !sharing) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("share_lyrics"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (sharing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = NoxaLime,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Bagikan lirik",
                tint = NoxaLime,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (sharing) "Membuat gambar..." else "Bagikan lirik",
            color = NoxaLime,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private const val OFFSET_STEP_MS = 500L

/** How many lyric lines fit on one share card. */
private const val LYRIC_SHARE_MAX_LINES = 7

@Composable
private fun LyricsMessage(
    message: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    loading: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = NoxaLime,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = message,
                color = NoxaTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(NoxaSurfaceSecondary)
                        .clickable { onAction() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(actionLabel, color = NoxaLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
