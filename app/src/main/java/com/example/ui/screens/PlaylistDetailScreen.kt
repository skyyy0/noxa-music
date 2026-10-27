package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Song
import com.example.ui.components.SongListItem
import com.example.ui.components.noxaAppear
import com.example.ui.components.noxaTap
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaError
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.viewmodel.SharedPlaylistUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * One playlist and the songs that were added to it. Songs can be played,
 * shuffled, removed, added to the queue or shared from here.
 *
 * A linked playlist also shows its collaborative identity: a `◆ Kolaboratif`
 * badge with the share code, a "Bagikan" action that publishes the list and
 * reveals the code, and a "Sinkronkan" pull that refreshes it from friends —
 * all rendered with a hero header, an ambient lime glow and gentle motion
 * instead of a flat title row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistName: String,
    songsFlow: Flow<List<Song>>,
    favoriteIds: Set<String>,
    currentPlayingSongId: String?,
    /** Live playback state — the play button toggles to Jeda/Lanjutkan. */
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onShare: (Song) -> Unit,
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    /** The share code when this playlist is linked, else null. */
    sharedCode: String? = null,
    sharedPlaylistUi: SharedPlaylistUi = SharedPlaylistUi.Idle,
    sharedNotice: String? = null,
    onSharePlaylist: () -> Unit = {},
    onSyncPlaylist: () -> Unit = {},
    onDismissNotice: () -> Unit = {},
    onConsumeSharedPlaylist: () -> Unit = {},
    // ------------------------------------------- v2.1: radio & unduhan
    downloadedIds: Set<String> = emptySet(),
    downloadingIds: Set<String> = emptySet(),
    onToggleDownload: (Song) -> Unit = {},
    onStartRadio: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val songs by songsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val haptic = LocalHapticFeedback.current
    var showShareResult by remember { mutableStateOf(false) }
    var justCopied by remember { mutableStateOf(false) }

    // A successful publish opens the code sheet; errors fall through to the
    // inline banner below (honest feedback for both paths).
    LaunchedEffect(sharedPlaylistUi) {
        if (sharedPlaylistUi is SharedPlaylistUi.Shared) {
            showShareResult = true
            justCopied = false
        }
    }

    // The banner text: a sync/push notice wins, a share error rides along.
    val bannerText = sharedNotice ?: (sharedPlaylistUi as? SharedPlaylistUi.Error)?.message
    LaunchedEffect(bannerText) {
        if (bannerText != null) {
            delay(5_000)
            if (sharedNotice != null) onDismissNotice() else onConsumeSharedPlaylist()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
            .testTag("playlist_detail_screen")
    ) {
        // Ambient lime glow behind the hero header — depth, not flat black.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(NoxaLime.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .noxaAppear(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = NoxaTextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlistName,
                        color = NoxaTextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${songs.size} lagu",
                            color = NoxaTextSecondary,
                            fontSize = 12.sp
                        )
                        if (sharedCode != null) {
                            // The collaborative identity chip.
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(NoxaLime.copy(alpha = 0.14f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "◆ Kolaboratif",
                                    color = NoxaLime,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = sharedCode,
                                    color = NoxaLime,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Inline banner: sync results and push conflicts slide in gently.
            AnimatedVisibility(
                visible = bannerText != null,
                enter = slideInVertically(tween(260)) { -it / 2 } + fadeIn(tween(260)),
                exit = slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(200)),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                val isError = sharedPlaylistUi is SharedPlaylistUi.Error
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isError) NoxaError.copy(alpha = 0.12f)
                            else NoxaLime.copy(alpha = 0.10f)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bannerText ?: "",
                        color = NoxaTextPrimary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = NoxaTextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Playlist ini masih kosong",
                            color = NoxaTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pakai menu ⋮ pada sebuah lagu lalu pilih \"Tambah ke playlist\"",
                            color = NoxaTextTertiary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                return
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp, start = 8.dp, end = 8.dp)
            ) {
                item(key = "actions") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .noxaAppear(60)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val playlistIsActive = currentPlayingSongId != null &&
                                songs.any { it.id == currentPlayingSongId }
                            Button(
                                onClick = {
                                    if (playlistIsActive) onTogglePlayPause()
                                    else onPlaySong(songs[0], songs, 0)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NoxaLime,
                                    contentColor = NoxaBlack
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (playlistIsActive && isPlaying) {
                                        Icons.Default.Pause
                                    } else {
                                        Icons.Default.PlayArrow
                                    },
                                    contentDescription = "Putar semua",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when {
                                        playlistIsActive && isPlaying -> "Jeda"
                                        playlistIsActive -> "Lanjutkan"
                                        else -> "Putar"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    val shuffled = songs.shuffled()
                                    onPlaySong(shuffled[0], shuffled, 0)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NoxaSurfaceSecondary,
                                    contentColor = NoxaTextPrimary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Acak",
                                    tint = NoxaLime,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Acak", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Collaborative actions: publish the code, or pull the
                        // latest from whoever last edited it.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SharePill(
                                modifier = Modifier.weight(1f),
                                label = if (sharedPlaylistUi is SharedPlaylistUi.Publishing) {
                                    "Menerbitkan…"
                                } else {
                                    "Bagikan playlist"
                                },
                                icon = Icons.Default.Link,
                                busy = sharedPlaylistUi is SharedPlaylistUi.Publishing,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSharePlaylist()
                                }
                            )
                            if (sharedCode != null) {
                                SharePill(
                                    modifier = Modifier.weight(1f),
                                    label = "Sinkronkan",
                                    icon = Icons.Default.Sync,
                                    busy = false,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSyncPlaylist()
                                    }
                                )
                            }
                        }
                    }
                }

                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SongListItem(
                            song = song,
                            isPlaying = song.id == currentPlayingSongId,
                            isFavorite = favoriteIds.contains(song.id),
                            onPlay = { onPlaySong(song, songs, index) },
                            onToggleFavorite = { onToggleFavorite(song) },
                            onPlayNext = { onPlayNext(song) },
                            onAddToQueue = { onAddToQueue(song) },
                            onShare = { onShare(song) },
                            onOpenArtist = onOpenArtist,
                            onStartRadio = { onStartRadio(song) },
                            onToggleDownload = { onToggleDownload(song) },
                            isDownloaded = downloadedIds.contains(song.id),
                            isDownloading = downloadingIds.contains(song.id),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onRemoveSong(song.id) },
                            modifier = Modifier.testTag("remove_from_playlist_${song.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Hapus dari playlist",
                                tint = NoxaTextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Share result: the code, big and copyable, in a modern sheet.
        if (showShareResult && sharedPlaylistUi is SharedPlaylistUi.Shared) {
            val shared = sharedPlaylistUi
            val context = LocalContext.current
            val clipboard = LocalClipboardManager.current
            val shareText = buildString {
                append("Playlist NOXA \"")
                append(shared.name)
                append("\" — gabung pakai kode: ")
                append(shared.code)
                append("\nBuka NOXA › Koleksi › Playlist › ikon tautan, lalu tempel kode ini.")
            }
            ModalBottomSheet(
                onDismissRequest = {
                    showShareResult = false
                    onConsumeSharedPlaylist()
                },
                containerColor = NoxaSurface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(NoxaLime.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = NoxaLime,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Playlist siap dibagikan",
                        color = NoxaTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = shared.name,
                        color = NoxaTextTertiary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    // The code itself — monospaced, letterspaced, unmissable.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(NoxaSurfaceElevated)
                            .border(1.dp, NoxaLime.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .padding(vertical = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = shared.code,
                            color = NoxaLime,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Temanmu: Koleksi → Playlist → ikon tautan, lalu tempel kode ini. " +
                            "Semua lagu yang kamu tambahkan di sini ikut terkirim.",
                        color = NoxaTextTertiary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SharePill(
                            modifier = Modifier.weight(1f),
                            label = if (justCopied) "Tersalin" else "Salin kode",
                            icon = if (justCopied) Icons.Default.Check else Icons.Default.Link,
                            busy = false,
                            onClick = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(shared.code))
                                justCopied = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                        SharePill(
                            modifier = Modifier.weight(1f),
                            label = "Bagikan",
                            icon = Icons.Default.Link,
                            busy = false,
                            onClick = {
                                sharePlaylistText(context, shareText, shared.name)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Outlined pill button used by the collaborative actions. */
@Composable
private fun SharePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaBorder, RoundedCornerShape(18.dp))
            .noxaTap(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = NoxaLime,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NoxaLime,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = NoxaTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Fires the system share sheet with the collaborative-playlist message. */
private fun sharePlaylistText(context: Context, text: String, name: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Playlist NOXA — $name")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, "Bagikan playlist")
    if (context !is android.app.Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(chooser)
    } catch (e: Exception) {
        // No share target available; the code stays visible to copy by hand.
    }
}
