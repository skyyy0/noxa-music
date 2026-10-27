package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.data.model.MusicSource
import com.example.data.model.PinnedTracks
import com.example.data.model.Song
import com.example.data.model.TrackLyrics
import com.example.media.PlaybackProgress
import com.example.media.PlayerUiState
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
import com.example.ui.util.FormatUtils
import com.example.ui.viewmodel.LyricsUiState
import com.example.ui.viewmodel.VideoUiState
import kotlinx.coroutines.flow.StateFlow

/** 16:9 frame for the music-video view (photos stay square). */
private const val VIDEO_ASPECT_RATIO = 16f / 9f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    visible: Boolean,
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    lyricsState: LyricsUiState,
    videoUiState: VideoUiState,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    /** Arms (`true`) / disarms (`false`) the "stop at song end" timer mode. */
    onSetSleepTimerEndOfTrack: ((Boolean) -> Unit)? = null,
    onOpenEqualizer: () -> Unit,
    onSelectQueueItem: (Song, Int) -> Unit,
    onShare: () -> Unit,
    /** Opens the per-song comment sheet (null hides the icon — callers without comments). */
    onOpenComments: (() -> Unit)? = null,
    onRetryLyrics: () -> Unit,
    onSetVideoMode: (Boolean) -> Unit,
    onAttachVideoSurface: (SurfaceView?) -> Unit,
    /** Karaoke-style synced lyric line over the artwork (setting toggle). */
    karaokeOverlayEnabled: Boolean = true,
    /** Opens the artist profile from the now-playing artist line (nullable). */
    onOpenArtist: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    // ------------------------------------------------ spectrum visualizer
    // One icon in the top bar arms it; the style chips only surface while
    // it is on (iOS-ish: controls appear on demand, never clutter the bar).
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var spectrumOn by rememberSaveable { mutableStateOf(false) }
    var spectrumStyle by rememberSaveable { mutableStateOf(SpectrumStyle.BARS) }
    var spectrumNotice by remember { mutableStateOf<String?>(null) }
    val spectrumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            spectrumNotice = null
            spectrumOn = true
        } else {
            spectrumOn = false
            spectrumNotice =
                "Izin mikrofon dipakai Android untuk spektrum audio — NOXA tidak pernah " +
                "mereka apapun. Tanpa izin, visualizer dimatikan."
        }
    }

    fun toggleSpectrum() {
        if (spectrumOn) {
            spectrumOn = false
            com.example.media.SpectrumCapture.stop()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            spectrumOn = true
        } else {
            spectrumLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Bind/unbind capture to the player's audio session with the sheet.
    LaunchedEffect(spectrumOn, visible) {
        if (spectrumOn && visible) {
            val sessionId = com.example.NoxaApplication.instance.player.exoPlayer.audioSessionId
            if (sessionId <= 0) {
                spectrumOn = false
                spectrumNotice = "Putar lagu dulu — spektrum butuh audio yang sedang berjalan."
            } else if (!com.example.media.SpectrumCapture.start(sessionId)) {
                spectrumOn = false
                spectrumNotice = "Spektrum tidak tersedia di perangkat ini."
            }
        } else {
            com.example.media.SpectrumCapture.stop()
        }
    }
    DisposableEffect(Unit) {
        onDispose { com.example.media.SpectrumCapture.stop() }
    }

    // Foto vs Video MV: the video view exists while the stream is resolving
    // (spinner) or ready (surface).
    val isVideoActive = videoUiState is VideoUiState.Loading || videoUiState is VideoUiState.Ready

    // ------------------------------------------- v2.0.0: tema dinamis artwork
    // The background glow takes the dominant colour of the current cover so
    // the player breathes with the album; falls back to the lime accent.
    var artworkTint by remember { mutableStateOf(NoxaLime) }
    val animatedTint by animateColorAsState(
        targetValue = artworkTint,
        animationSpec = tween(600),
        label = "artwork_tint"
    )
    LaunchedEffect(playerState.currentSong?.artworkUrl) {
        val url = playerState.currentSong?.artworkUrl
        if (url.isNullOrBlank()) {
            artworkTint = NoxaLime
        } else {
            artworkTint = com.example.ui.util.ArtworkPalette.dominantColorOf(
                url,
                coil.Coil.imageLoader(context),
                context
            ) ?: NoxaLime
        }
    }

    AnimatedVisibility(
        visible = visible && playerState.currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val song = playerState.currentSong ?: return@AnimatedVisibility

        // Tracks that can show a music video: native YouTube, or a Deezer track
        // that was matched to a YouTube video when its full audio was resolved.
        val canHaveMv = song.source == MusicSource.YOUTUBE || song.matchedVideoId.isNotBlank()

        // --------------------------------------- v2.0.0: swipe-down to close
        // Dragging the handle down slides the whole sheet (and dims it);
        // past the threshold it snaps closed, otherwise it springs back.
        val dragOffset = remember { Animatable(0f) }
        val dragScope = rememberCoroutineScope()
        // Reference height for the dim curve — sheet-level, not density-math
        // inside graphicsLayer (where `size` is not a Float source).
        val dimReferencePx = with(androidx.compose.ui.platform.LocalDensity.current) {
            700.dp.toPx()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NoxaBlack)
                .statusBarsPadding()
                .graphicsLayer {
                    translationY = dragOffset.value
                    val fraction = (dragOffset.value / dimReferencePx).coerceIn(0f, 1f)
                    alpha = 1f - fraction * 0.35f
                }
                .testTag("full_player_sheet")
        ) {
            // Ambient subtle radial gradient glow from top — tinted by the
            // current artwork's dominant colour (tema dinamis).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                animatedTint.copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Drag handle — the swipe-down affordance (iOS-style).
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, bottom = 2.dp)
                        .size(width = 42.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(animatedTint.copy(alpha = 0.5f))
                        .pointerInput(song.id) {
                            // ~140 dp of downward travel dismisses the sheet.
                            val closeThresholdPx = 140.dp.toPx()
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragScope.launch {
                                        dragOffset.snapTo(
                                            (dragOffset.value + dragAmount).coerceAtLeast(0f)
                                        )
                                    }
                                },
                                onDragEnd = {
                                    if (dragOffset.value > closeThresholdPx) {
                                        onClose()
                                    } else {
                                        dragScope.launch {
                                            dragOffset.animateTo(
                                                0f,
                                                spring(stiffness = Spring.StiffnessMediumLow)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        .testTag("full_player_drag_handle")
                )

                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("full_player_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = NoxaTextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PLAYING FROM",
                            color = NoxaTextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = song.album,
                            color = NoxaTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { toggleSpectrum() },
                            modifier = Modifier.testTag("full_player_spectrum_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Visualizer spektrum",
                                tint = if (spectrumOn) NoxaLime else NoxaTextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (onOpenComments != null) {
                            IconButton(
                                onClick = onOpenComments,
                                modifier = Modifier.testTag("full_player_comments_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ModeComment,
                                    contentDescription = "Komentar lagu",
                                    tint = NoxaTextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.testTag("full_player_share_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Bagikan lagu",
                                tint = NoxaTextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenEqualizer,
                            modifier = Modifier.testTag("full_player_eq_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = "Equalizer",
                                tint = NoxaTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }                // Artwork Card — or the music video when "Video MV" is selected.
                // MV-capable tracks get a vertical Foto/Video rail beside the art.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = if (canHaveMv) 0.dp else 14.dp)
                            .aspectRatio(if (isVideoActive) VIDEO_ASPECT_RATIO else 1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(NoxaSurfaceElevated)
                            .border(1.dp, NoxaBorder, RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isVideoActive && videoUiState is VideoUiState.Ready -> {
                                // A separate muted player renders here; the main audio
                                // (and the notification controls) never pause.
                                AndroidView(
                                    factory = { context -> SurfaceView(context) },
                                    update = { surface -> onAttachVideoSurface(surface) },
                                    onRelease = { onAttachVideoSurface(null) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            isVideoActive -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(34.dp),
                                    color = NoxaLime,
                                    strokeWidth = 3.dp
                                )
                            }

                            else -> {
                                AsyncImage(
                                    model = song.artworkUrl,
                                    contentDescription = "Full player artwork",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        // Breathing loop while audio runs — the
                                        // artwork swells almost imperceptibly
                                        // so the player feels alive, and holds
                                        // still the moment it pauses.
                                        .noxaBreathe(active = playerState.isPlaying)
                                )
                            }
                        }

                        // Karaoke: the current (and next) synced line rides over
                        // the artwork, following the playback position.
                        if (!isVideoActive && karaokeOverlayEnabled &&
                            lyricsState is LyricsUiState.Ready
                        ) {
                            KaraokeOverlay(
                                lyrics = (lyricsState as LyricsUiState.Ready).lyrics,
                                progressFlow = progressFlow
                            )
                        }
                    }

                    if (canHaveMv) {
                        Spacer(modifier = Modifier.width(14.dp))
                        MediaModeRail(
                            videoSelected = isVideoActive,
                            onSelectVideo = { onSetVideoMode(true) },
                            onSelectPhoto = { onSetVideoMode(false) }
                        )
                    }
                }

                if (videoUiState is VideoUiState.Unavailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = videoUiState.message,
                        color = NoxaTextTertiary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(if (canHaveMv) 14.dp else 28.dp))

                // Title & Artist & Favorite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = NoxaTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Long titles scroll like the iOS now-playing
                            // screen instead of being quietly cut off.
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.artist,
                                color = NoxaTextSecondary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (onOpenArtist != null) {
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .noxaTap { onOpenArtist(song.artistId, song.artist) }
                                        .padding(vertical = 2.dp, horizontal = 2.dp)
                                } else {
                                    Modifier
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AudioQualityBadge(song = song)
                            if (PinnedTracks.isPinned(song)) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Lagu pin Sky",
                                    tint = NoxaLime,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .testTag("pinned_star")
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            // A heavier tick marks the heart, iOS-style.
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleFavorite()
                        },
                        modifier = Modifier.testTag("full_player_favorite_btn")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) NoxaLime else NoxaTextSecondary,
                            modifier = Modifier
                                .size(26.dp)
                                // The heart pops when it fills (and settles
                                // when it empties) instead of snapping.
                                .noxaPop(trigger = isFavorite)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Spectrum strip — appears only when armed, with its style
                // chips inline. A one-line hint replaces it when capture is
                // unavailable (permission missing / no signal yet).
                AnimatedVisibility(visible = spectrumOn) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SpectrumVisualizer(
                            style = spectrumStyle,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            SpectrumStyle.entries.forEach { entry ->
                                val selected = entry == spectrumStyle
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (selected) NoxaLime.copy(alpha = 0.16f)
                                            else NoxaSurfaceSecondary
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) NoxaLime.copy(alpha = 0.55f)
                                            else NoxaBorder,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .noxaTap { spectrumStyle = entry }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        entry.label,
                                        color = if (selected) NoxaLime else NoxaTextTertiary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                spectrumNotice?.let { notice ->
                    Text(
                        text = notice,
                        color = NoxaTextTertiary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Progress. Only this section reads the fast position flow.
                PlayerProgressSection(
                    progressFlow = progressFlow,
                    fallbackDurationMs = song.durationMs,
                    songId = song.id,
                    onSeekTo = onSeekTo
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Main Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleShuffle()
                        },
                        modifier = Modifier.testTag("full_player_shuffle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.isShuffle) NoxaLime else NoxaTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            // Tap dips the glyph and ticks under the finger,
                            // the way the transport row feels on iOS.
                            .noxaTap { onPrevious() }
                            .testTag("full_player_prev_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = NoxaTextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(NoxaLime)
                            .noxaTap { onTogglePlayPause() }
                            .testTag("full_player_play_pause_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        // The glyph pops softly on every toggle — the small,
                        // alive feeling iOS transport buttons have.
                        val iconPop = remember { Animatable(1f) }
                        LaunchedEffect(playerState.isPlaying) {
                            iconPop.snapTo(0.72f)
                            iconPop.animateTo(
                                1f,
                                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessHigh)
                            )
                        }
                        if (playerState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                color = Color.Black,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier
                                    .size(36.dp)
                                    .graphicsLayer {
                                        scaleX = iconPop.value
                                        scaleY = iconPop.value
                                    }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .noxaTap { onNext() }
                            .testTag("full_player_next_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = NoxaTextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleRepeat()
                        },
                        modifier = Modifier.testTag("full_player_repeat_btn")
                    ) {
                        val isOne = playerState.repeatMode == Player.REPEAT_MODE_ONE
                        val isAll = playerState.repeatMode == Player.REPEAT_MODE_ALL
                        Icon(
                            imageVector = if (isOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (isOne || isAll) NoxaLime else NoxaTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Secondary Actions (Sleep Timer, Lyrics, Queue)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showSleepTimerDialog = true },
                        modifier = Modifier.testTag("full_player_sleep_timer_btn")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val sleepActive = playerState.sleepTimerMinutes != null ||
                                playerState.sleepTimerEndOfTrack
                            Icon(
                                imageVector = Icons.Default.NightsStay,
                                contentDescription = "Sleep Timer",
                                tint = if (sleepActive) NoxaLime else NoxaTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            when {
                                playerState.sleepTimerEndOfTrack -> {
                                    // No clock to count: the promise is the song itself.
                                    Text(
                                        text = "Akhir",
                                        color = NoxaLime,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                playerState.sleepTimerMinutes != null -> {
                                    SleepTimerLabel(progressFlow)
                                }
                                else -> {
                                    Text(
                                        text = "Tidur",
                                        color = NoxaTextTertiary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { showLyricsSheet = true },
                        modifier = Modifier.testTag("full_player_lyrics_btn")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Subject,
                                contentDescription = "Lyrics",
                                tint = if (lyricsState is LyricsUiState.Ready) NoxaLime else NoxaTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Lirik",
                                color = NoxaTextTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = { showQueueSheet = true },
                        modifier = Modifier.testTag("full_player_queue_btn")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = "Queue",
                                tint = NoxaTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Antrian",
                                color = NoxaTextTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Sky's words for a pinned track — the "why", under the controls.
                PinnedQuoteBlock(song = song)
            }
        }

        // Lyrics Bottom Sheet — synchronised with the real playback position.
        if (showLyricsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLyricsSheet = false },
                containerColor = NoxaSurface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Lirik",
                        color = NoxaTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${song.title} — ${song.artist}",
                        color = NoxaTextTertiary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LyricsPanel(
                        lyricsState = lyricsState,
                        progressFlow = progressFlow,
                        onSeekTo = onSeekTo,
                        onRetry = onRetryLyrics,
                        song = playerState.currentSong
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Queue Bottom Sheet
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                containerColor = NoxaSurface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Antrian (${playerState.queue.size})",
                        color = NoxaTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        itemsIndexed(playerState.queue, key = { _, item -> item.id }) { index, queueSong ->
                            val isCurrent = index == playerState.queueIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) NoxaSurfaceSecondary else Color.Transparent)
                                    .noxaTap {
                                        onSelectQueueItem(queueSong, index)
                                        showQueueSheet = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = if (isCurrent) NoxaLime else NoxaTextTertiary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = queueSong.title,
                                        color = if (isCurrent) NoxaLime else NoxaTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = queueSong.artist,
                                        color = NoxaTextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (PinnedTracks.isPinned(queueSong)) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Lagu pin Sky",
                                        tint = NoxaLime,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sleep Timer Bottom Sheet — a live countdown ring, chip grid and a
        // gentle fade-out instead of an abrupt cut (modern, iOS-flavoured).
        if (showSleepTimerDialog) {
            ModalBottomSheet(
                onDismissRequest = { showSleepTimerDialog = false },
                containerColor = NoxaSurface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                SleepTimerSheet(
                    playerState = playerState,
                    progressFlow = progressFlow,
                    onSelectMinutes = { mins ->
                        onSetSleepTimer(mins)
                        showSleepTimerDialog = false
                    },
                    onSetEndOfTrack = { enabled ->
                        onSetSleepTimerEndOfTrack?.invoke(enabled)
                        showSleepTimerDialog = false
                    },
                    onCancel = {
                        onSetSleepTimer(null)
                        onSetSleepTimerEndOfTrack?.invoke(false)
                        showSleepTimerDialog = false
                    }
                )
            }
        }
    }
}

/**
 * The sleep-timer sheet body: header, a live countdown ring while armed, a
 * grid of duration chips (plus "end of track") and an honest note about the
 * fade-out. Split from [FullPlayerSheet] so the countdown can recompose on its
 * own ticks.
 */
@Composable
private fun SleepTimerSheet(
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    onSelectMinutes: (Int) -> Unit,
    onSetEndOfTrack: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val progress by progressFlow.collectAsState()
    val minutesActive = playerState.sleepTimerMinutes
    val endOfTrack = playerState.sleepTimerEndOfTrack
    val active = minutesActive != null || endOfTrack

    // Remaining seconds of the running timer: the minutes countdown lives in
    // progress; end-of-track counts the real song tail so the ring hugs the
    // actual track length.
    val totalMs = if (progress.durationMs > 0) {
        progress.durationMs
    } else {
        playerState.durationMs
    }
    val remainingSeconds: Long? = when {
        endOfTrack && totalMs > 0L -> ((totalMs - progress.positionMs).coerceAtLeast(0L)) / 1000L
        endOfTrack -> null // duration unknown: wait, never fire blind
        else -> progress.sleepTimerRemainingSeconds
    }
    val totalSeconds: Long = when {
        endOfTrack -> totalMs / 1000L
        minutesActive != null -> minutesActive * 60L
        else -> 0L
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header: moon badge + title + one honest line about the behaviour.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NoxaLime.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NightsStay,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Sleep Timer",
                    color = NoxaTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (active) "Timer aktif — suara meredup sebelum berhenti"
                    else "Pilih kapan musik harus berhenti",
                    color = NoxaTextTertiary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (active) {
            // Live countdown ring — the small detail that makes it feel alive.
            SleepCountdownRing(
                remainingSeconds = remainingSeconds,
                totalSeconds = totalSeconds,
                endOfTrack = endOfTrack
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Duration chips, two per row.
        val options = listOf(10, 15, 30, 45, 60)
        options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowOptions.forEach { mins ->
                    SleepOptionChip(
                        modifier = Modifier.weight(1f),
                        title = "$mins menit",
                        subtitle = null,
                        selected = minutesActive == mins,
                        onClick = { onSelectMinutes(mins) }
                    )
                }
                // Keep the grid even when the row is short (5 options → 2/2/1).
                if (rowOptions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        SleepOptionChip(
            modifier = Modifier.fillMaxWidth(),
            title = "Berakhir di akhir lagu",
            subtitle = "Musik meredup lalu berhenti di penghujung lagu ini",
            selected = endOfTrack,
            leadingIcon = Icons.Default.MusicNote,
            onClick = { onSetEndOfTrack(!endOfTrack) }
        )

        if (active) {
            Spacer(modifier = Modifier.height(16.dp))
            SleepOptionChip(
                modifier = Modifier.fillMaxWidth(),
                title = "Matikan timer",
                subtitle = null,
                selected = false,
                destructive = true,
                onClick = onCancel
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Volume diredupkan pelan ±10 detik sebelum berhenti — tidak ada " +
                "dentang tiba-tiba di tengah tidur.",
            color = NoxaTextTertiary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** One tappable timer option: press-dip, lime selection, optional icon. */
@Composable
private fun SleepOptionChip(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    destructive: Boolean = false
) {
    val bg by animateColorAsState(
        targetValue = when {
            selected -> NoxaLime
            destructive -> NoxaError.copy(alpha = 0.12f)
            else -> NoxaSurfaceElevated
        },
        animationSpec = tween(durationMillis = 220),
        label = "sleepChipBg"
    )
    val borderColor = when {
        selected -> NoxaLime
        destructive -> NoxaError.copy(alpha = 0.5f)
        else -> NoxaBorder
    }
    val contentColor = if (selected) NoxaBlack else NoxaTextPrimary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .noxaTap(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = if (selected) NoxaBlack.copy(alpha = 0.65f) else NoxaTextTertiary,
                    fontSize = 11.sp
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Terpilih",
                tint = if (destructive) NoxaTextPrimary else NoxaBlack,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Animated circular countdown shown while a sleep timer is armed. */
@Composable
private fun SleepCountdownRing(
    remainingSeconds: Long?,
    totalSeconds: Long,
    endOfTrack: Boolean
) {
    val targetFraction = if (totalSeconds > 0L && remainingSeconds != null) {
        (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 600),
        label = "sleepRing"
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(168.dp)) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = NoxaSurfaceSecondary,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = NoxaLime,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (remainingSeconds != null) {
                        FormatUtils.formatDuration(remainingSeconds * 1000L)
                    } else {
                        "—:—"
                    },
                    color = NoxaTextPrimary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (endOfTrack) "sampai lagu selesai" else "lagi",
                    color = NoxaTextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * The seek bar and its two labels.
 *
 * Deliberately its own composable: the playback position changes twice a second,
 * and only this subtree re-reads it while the rest of the sheet stays still.
 *
 * The bar itself is a **waveform** ([WaveformSeekbar]) instead of the stock
 * Material slider: lime bars for the part already heard, quiet ones ahead.
 */
@Composable
private fun PlayerProgressSection(
    progressFlow: StateFlow<PlaybackProgress>,
    fallbackDurationMs: Long,
    songId: String,
    onSeekTo: (Long) -> Unit
) {
    val progress by progressFlow.collectAsState()

    val totalDuration = when {
        progress.durationMs > 0 -> progress.durationMs
        else -> fallbackDurationMs
    }
    val position = progress.positionMs.coerceIn(0L, totalDuration.coerceAtLeast(0L))

    Column(modifier = Modifier.fillMaxWidth()) {
        WaveformSeekbar(
            songId = songId,
            positionMs = position,
            durationMs = totalDuration,
            onSeekTo = onSeekTo,
            modifier = Modifier.testTag("full_player_slider")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = FormatUtils.formatDuration(position),
                color = NoxaTextTertiary,
                fontSize = 12.sp
            )
            Text(
                text = FormatUtils.formatDuration(totalDuration),
                color = NoxaTextTertiary,
                fontSize = 12.sp
            )
        }
    }
}

/** How many bars the waveform draws — dense enough to read as an envelope. */
private const val WAVE_BAR_COUNT = 56

/**
 * The waveform shape for one song: a smoothed pseudo-random envelope seeded by
 * the song id, so a track always draws the same silhouette (no flicker on
 * recomposition) while different tracks look different. A real decoded
 * waveform would mean decoding every file just to draw a scrubber — this is
 * the honest stylised stand-in, not a claim about the actual audio.
 */
private fun waveformBars(seed: String, count: Int): FloatArray {
    val rnd = Random(seed.hashCode())
    val raw = FloatArray(count) { 0.28f + rnd.nextFloat() * 0.72f }
    return FloatArray(count) { i ->
        val prev = raw[(i - 1).coerceAtLeast(0)]
        val next = raw[(i + 1).coerceAtMost(count - 1)]
        ((prev + raw[i] * 2f + next) / 4f).coerceIn(0.18f, 1f)
    }
}

/**
 * Draggable waveform scrubber: tap anywhere to jump, drag to scrub with a live
 * preview, release to seek. Reports its position as a progress bar to
 * accessibility services.
 */
@Composable
private fun WaveformSeekbar(
    songId: String,
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val bars = remember(songId) { waveformBars(songId, WAVE_BAR_COUNT) }
    var previewFraction by remember(songId) { mutableStateOf<Float?>(null) }
    // One light tick per 5% of track crossed while scrubbing: the waveform
    // gets physical detents under the finger instead of sliding on glass.
    val scrubTick = rememberScrubTicker()

    val total = durationMs.coerceAtLeast(1L)
    val fraction = previewFraction
        ?: (positionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
            .pointerInput(songId, durationMs) {
                detectTapGestures { offset ->
                    if (durationMs <= 0L) return@detectTapGestures
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    scrubTick(f)
                    onSeekTo((f * durationMs).toLong())
                }
            }
            .pointerInput(songId, durationMs) {
                detectDragGestures(
                    onDragStart = { pos ->
                        if (durationMs > 0L) {
                            previewFraction = (pos.x / size.width).coerceIn(0f, 1f)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (durationMs > 0L) {
                            val f = (change.position.x / size.width).coerceIn(0f, 1f)
                            previewFraction = f
                            scrubTick(f)
                        }
                    },
                    onDragEnd = {
                        previewFraction?.let { onSeekTo((it * durationMs).toLong()) }
                        previewFraction = null
                    },
                    onDragCancel = { previewFraction = null }
                )
            }
    ) {
        val gap = size.width / (bars.size * 3f)
        val barWidth = (size.width / bars.size) - gap
        if (barWidth <= 0f) return@Canvas

        bars.forEachIndexed { i, amplitude ->
            val played = ((i + 1f) / bars.size) <= fraction
            val barHeight = (size.height * amplitude).coerceAtLeast(barWidth)
            drawRoundRect(
                color = if (played) NoxaLime else NoxaSurfaceSecondary,
                topLeft = Offset(
                    x = i * (barWidth + gap),
                    y = (size.height - barHeight) / 2f
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun SleepTimerLabel(progressFlow: StateFlow<PlaybackProgress>) {
    val progress by progressFlow.collectAsState()
    val remaining = progress.sleepTimerRemainingSeconds ?: return
    Text(
        text = "${remaining / 60}m",
        color = NoxaLime,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
    )
}

/** Foto / Video MV as a vertical rail beside the artwork (MV-capable tracks). */
@Composable
private fun MediaModeRail(
    videoSelected: Boolean,
    onSelectVideo: () -> Unit,
    onSelectPhoto: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MediaModeChip(
            label = "Foto",
            icon = Icons.Default.Image,
            selected = !videoSelected,
            onClick = onSelectPhoto,
            tag = "player_mode_Foto"
        )
        MediaModeChip(
            label = "MV",
            icon = Icons.Default.Videocam,
            selected = videoSelected,
            onClick = onSelectVideo,
            tag = "player_mode_Video_MV"
        )
    }
}

@Composable
private fun MediaModeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) NoxaLime else NoxaSurfaceElevated)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = NoxaBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .noxaTap { onClick() }
            .padding(horizontal = 9.dp, vertical = 9.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) NoxaBlack else NoxaTextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (selected) NoxaBlack else NoxaTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Karaoke overlay: the synced line being sung rides over the artwork in lime,
 * with the next line fading in below — so eyes can follow without a sheet.
 */
@Composable
private fun KaraokeOverlay(
    lyrics: TrackLyrics,
    progressFlow: StateFlow<PlaybackProgress>
) {
    if (!lyrics.isSynced) return
    val progress by progressFlow.collectAsState()
    val index = lyrics.indexAt(progress.positionMs)
    val current = lyrics.lines.getOrNull(index) ?: return
    val next = lyrics.lines.getOrNull(index + 1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                )
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = current.text.ifBlank { "♪" },
                color = NoxaLime,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (next != null && next.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = next.text,
                    color = NoxaTextSecondary.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Sky's quote for a pinned track, shown beneath the transport controls — the
 * reason the song sits on the pin shelf in the first place.
 */
@Composable
private fun PinnedQuoteBlock(song: Song) {
    val pin = PinnedTracks.find(song) ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(NoxaLime.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .testTag("pinned_quote_badge")
        ) {
            Text(
                text = "PINNED BY SKY",
                color = NoxaLime,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "\u201C${pin.quote}\u201D",
            color = NoxaTextSecondary,
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.testTag("pinned_quote_text")
        )
    }
}

@Composable
private fun AudioQualityBadge(song: Song) {
    // Audio always resolves to full length at play time (YouTube matcher +
    // on-device extractor). The badge is kept neutral — no provider names —
    // so NOXA reads as a standalone music app rather than a front for other
    // services.
    val label = "FULL"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(NoxaLime.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = NoxaLime,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
