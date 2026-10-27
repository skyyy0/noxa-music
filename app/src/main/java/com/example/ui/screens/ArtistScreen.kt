package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AlbumInfo
import com.example.data.model.ArtistProfile
import com.example.data.model.Song
import com.example.ui.components.SongListItem
import com.example.ui.i18n.string
import com.example.ui.components.noxaAppear
import com.example.ui.components.noxaTap
import com.example.ui.components.NoxAmbientBackdrop
import com.example.ui.components.NoxaCountUp
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.util.ArtworkPalette
import com.example.ui.util.FormatUtils
import com.example.ui.viewmodel.ArtistUiState

/** Biru = artis terverifikasi biasa, merah = artis besar (listener ≥ 1 jt). */
@Composable
fun VerifiedArtistBadge(
    isBigArtist: Boolean,
    modifier: Modifier = Modifier,
    sizeDp: Int = 18
) {
    Icon(
        imageVector = Icons.Default.Verified,
        contentDescription = if (isBigArtist) "Artis besar terverifikasi" else "Terverifikasi",
        tint = if (isBigArtist) Color(0xFFE53935) else Color(0xFF1D9BF0),
        modifier = modifier.size(sizeDp.dp)
    )
}

/**
 * v2.0.0 artist page — built to be richer than a plain list:
 *
 *  * Full-bleed **hero photo with scroll parallax** (the photo drifts slower
 *    than the content, iOS album-page feel) + gradient scrim + oversized name.
 *  * **Stats row**: monthly listeners, release count — big numbers, tap-free.
 *  * **Ikuti** button with live state, feeding the Home release radar.
 *  * **Bio** (Wikipedia) with expand/collapse — hides itself when unavailable.
 *  * **Populer** list where every row carries a popularity bar (Deezer rank,
 *    honest label) instead of an invented play count.
 *  * **Discography with filter chips** (Semua / Album / Single / Kompilasi).
 *  * **Penggemar juga mendengar** — related-artist avatars that open their
 *    pages, so browsing "one club" of artists never dead-ends.
 */
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    favoriteIds: Set<String>,
    currentPlayingSongId: String?,
    /** Live playback state — "Putar semua" toggles to Jeda/Lanjutkan. */
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShare: (Song) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String, String) -> Unit,
    onRetry: () -> Unit,
    /** v2.0.0 — rating bintang & "jangan putar lagi" di daftar populer. */
    songRatings: Map<String, Int> = emptyMap(),
    onRateSong: (Song, Int) -> Unit = { _, _ -> },
    onMuteSong: (Song) -> Unit = {},
    onMuteArtist: (Song) -> Unit = {},
    // ------------------------------------------- v2.1: radio & unduhan
    downloadedIds: Set<String> = emptySet(),
    downloadingIds: Set<String> = emptySet(),
    onToggleDownload: (Song) -> Unit = {},
    onStartRadio: (Song) -> Unit = {},
    /** True while the radio queue for this artist is being built. */
    isBuildingRadio: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
    ) {
        // Ambient depth behind the page, keyed to the artist's own photo: the
        // hero, stats and rows sit in the light of this artist's imagery rather
        // than under a fixed brand wash.
        val context = LocalContext.current
        var ambientTint by remember { mutableStateOf<Color?>(null) }
        val artistArtwork = (state as? ArtistUiState.Ready)?.profile?.pictureUrl
        LaunchedEffect(artistArtwork) {
            ambientTint = if (artistArtwork.isNullOrBlank()) {
                null
            } else {
                ArtworkPalette.dominantColorOf(
                    artistArtwork,
                    coil.Coil.imageLoader(context),
                    context
                )
            }
        }
        NoxAmbientBackdrop(
            modifier = Modifier.matchParentSize(),
            tint = ambientTint
        )
        when (state) {
            is ArtistUiState.Loading -> {
                CircularProgressIndicator(
                    color = NoxaLime,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                )
            }

            is ArtistUiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        color = NoxaTextSecondary,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Coba lagi",
                        color = NoxaLime,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, NoxaLime, RoundedCornerShape(20.dp))
                            .noxaTap { onRetry() }
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    )
                }
            }

            is ArtistUiState.Ready -> {
                ArtistPageContent(
                    isPlaying = isPlaying,
                    onTogglePlayPause = onTogglePlayPause,
                    state = state,
                    favoriteIds = favoriteIds,
                    currentPlayingSongId = currentPlayingSongId,
                    isFollowing = isFollowing,
                    onToggleFollow = onToggleFollow,
                    onBack = onBack,
                    onPlaySong = onPlaySong,
                    onToggleFavorite = onToggleFavorite,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShare = onShare,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                    songRatings = songRatings,
                    onRateSong = onRateSong,
                    onMuteSong = onMuteSong,
                    onMuteArtist = onMuteArtist,
                    downloadedIds = downloadedIds,
                    downloadingIds = downloadingIds,
                    onToggleDownload = onToggleDownload,
                    onStartRadio = onStartRadio,
                    isBuildingRadio = isBuildingRadio
                )
            }
        }
    }
}

@Composable
private fun ArtistPageContent(
    state: ArtistUiState.Ready,
    favoriteIds: Set<String>,
    currentPlayingSongId: String?,
    /** Live playback state — "Putar semua" toggles to Jeda/Lanjutkan. */
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShare: (Song) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String, String) -> Unit,
    /** v2.0.0 — rating bintang & "jangan putar lagi" di daftar populer. */
    songRatings: Map<String, Int> = emptyMap(),
    onRateSong: (Song, Int) -> Unit = { _, _ -> },
    onMuteSong: (Song) -> Unit = {},
    onMuteArtist: (Song) -> Unit = {},
    // ------------------------------------------- v2.1: radio & unduhan
    downloadedIds: Set<String> = emptySet(),
    downloadingIds: Set<String> = emptySet(),
    onToggleDownload: (Song) -> Unit = {},
    onStartRadio: (Song) -> Unit = {},
    /** True while the radio queue for this artist is being built. */
    isBuildingRadio: Boolean = false
) {
    val profile = state.profile
    val topSongs = state.topSongs
    val albums = state.albums
    val relatedArtists = state.relatedArtists
    val bio = state.bio
    val insights = state.insights

    val scroll = rememberScrollState()
    // The hero photo drifts at half scroll speed — the parallax anchor.
    val parallax by remember {
        derivedStateOf { scroll.value * 0.5f }
    }

    var bioExpanded by remember { mutableStateOf(false) }
    var discographyFilter by remember { mutableStateOf("Semua") }

    val filteredAlbums = remember(albums, discographyFilter) {
        when (discographyFilter) {
            "Album" -> albums.filter { it.type.equals("album", ignoreCase = true) }
            "Single" -> albums.filter { it.type.equals("single", ignoreCase = true) }
            "Kompilasi" -> albums.filter { it.type.equals("compile", ignoreCase = true) }
            else -> albums
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .navigationBarsPadding()
        ) {
            // ------------------------------------------------ hero + parallax
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(profile.pictureUrl)
                        .size(800, 800)
                        .crossfade(true)
                        .build(),
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .graphicsLayer { translationY = -parallax }
                )
                // Scrim: transparent at the photo's top, black at the content
                // edge — the name always sits on readable darkness.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent,
                                    NoxaBlack
                                ),
                                startY = 0f
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            color = NoxaTextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        VerifiedArtistBadge(
                            isBigArtist = profile.isBigArtist,
                            sizeDp = 22
                        )
                    }
                }
            }

            // ------------------------------------------------------- stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Scraped YouTube subscribers beat Deezer's nb_fan as the
                // headline "listeners" figure; Deezer fans stay as fallback.
                val audience = insights?.youtubeSubscribers ?: profile.fanCount
                StatBlock(
                    value = FormatUtils.formatCount(audience),
                    count = audience,
                    label = if (insights?.youtubeSubscribers != null) {
                        "subscriber YouTube"
                    } else {
                        string("artist.fans")
                    },
                    modifier = Modifier.weight(1f)
                )
                if (profile.albumCount > 0) {
                    StatBlock(
                        value = profile.albumCount.toString(),
                        count = profile.albumCount.toLong(),
                        label = string("artist.releases"),
                        modifier = Modifier.weight(1f)
                    )
                }
                StatBlock(
                    value = topSongs.size.toString(),
                    count = topSongs.size.toLong(),
                    label = string("artist.topTracks"),
                    modifier = Modifier.weight(1f)
                )
            }

            // Scraped facts: genre • origin • years active. Only what the
            // sources actually answered — empty chips never render.
            val factChips = remember(insights) {
                buildList {
                    // Spotify's own numbers: monthly listeners + followers.
                    val spotify = buildList {
                        insights?.spotifyMonthlyListeners
                            ?.let { add("${FormatUtils.formatCount(it)} pendengar bulanan") }
                        insights?.spotifyFollowers
                            ?.let { add("${FormatUtils.formatCount(it)} followers") }
                    }
                    if (spotify.isNotEmpty()) {
                        add("Spotify: ${spotify.joinToString(" • ")}")
                    }
                    insights?.genres?.takeIf { it.isNotEmpty() }
                        ?.let { add(it.joinToString(" • ")) }
                    listOfNotNull(
                        insights?.country,
                        insights?.activeSince?.let { "aktif sejak $it" }
                    ).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" • ")) }
                }
            }
            if (factChips.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    factChips.forEach { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(NoxaSurfaceSecondary)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = chip,
                                color = NoxaTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // ------------------------------------------- action row + follow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (topSongs.isNotEmpty()) {
                    // Live toggle: while a top song of THIS artist is the
                    // current song, the button pauses/resumes instead of
                    // restarting the shelf from the top.
                    val topActive = currentPlayingSongId != null &&
                        topSongs.any { it.id == currentPlayingSongId }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(NoxaLime)
                            .noxaTap {
                                if (topActive) onTogglePlayPause()
                                else onPlaySong(topSongs[0], topSongs, 0)
                            }
                            .padding(horizontal = 24.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (topActive && isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = null,
                            tint = NoxaBlack,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                topActive && isPlaying -> "Jeda"
                                topActive -> "Lanjutkan"
                                else -> "Putar semua"
                            },
                            color = NoxaBlack,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // Ikuti: filled when following, ghost when not — the state is
                // carried by fill and a check mark, not by painting a second
                // lime button next to the lime play.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (isFollowing) NoxaSurfaceSecondary else NoxaSurfaceElevated
                        )
                        .noxaTap { onToggleFollow() }
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFollowing) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = NoxaTextPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = if (isFollowing) string("artist.following") else string("artist.follow"),
                        color = if (isFollowing) NoxaTextPrimary else NoxaTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Radio: an endless queue seeded by this artist (their top
                // tracks plus the artists Deezer calls related).
                if (topSongs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Transparent)
                            .border(1.dp, NoxaBorder, RoundedCornerShape(24.dp))
                            .noxaTap { if (!isBuildingRadio) onStartRadio(topSongs[0]) }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isBuildingRadio) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = NoxaTextSecondary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = NoxaTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Radio",
                            color = NoxaTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ------------------------------------------------------------ bio
            if (!bio.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NoxaSurfaceElevated)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Tentang",
                        color = NoxaTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = bio,
                        color = NoxaTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = if (bioExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (bioExpanded) "Tutup" else "Baca selengkapnya",
                        color = NoxaTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .noxaTap { bioExpanded = !bioExpanded }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    )
                }
            }

            // --------------------------------------------------------- populer
            if (topSongs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionTitle("Populer")
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    // The one honest "total audience" figure we can show per
                    // row: the artist's own monthly listeners, repeated as a
                    // quiet lime line. It is the artist-level number (not a
                    // per-track play count), so it reads as context, not a
                    // fabricated per-song stat.
                    val audienceLine = insights?.spotifyMonthlyListeners?.let {
                        "${FormatUtils.formatCount(it)} pendengar bulanan"
                    } ?: if (profile.fanCount > 0L) {
                        "${FormatUtils.formatCount(profile.fanCount)} pendengar"
                    } else {
                        null
                    }
                    topSongs.forEachIndexed { index, song ->
                        SongListItem(
                            song = song,
                            rank = index + 1,
                            isPlaying = song.id == currentPlayingSongId,
                            isFavorite = favoriteIds.contains(song.id),
                            onPlay = { onPlaySong(song, topSongs, index) },
                            onToggleFavorite = { onToggleFavorite(song) },
                            onPlayNext = { onPlayNext(song) },
                            onAddToQueue = { onAddToQueue(song) },
                            onAddToPlaylist = { onAddToPlaylist(song) },
                            onShare = { onShare(song) },
                            onOpenArtist = onOpenArtist,
                            rating = songRatings[song.id] ?: 0,
                            onRate = onRateSong,
                            onMuteSong = { onMuteSong(song) },
                            onMuteArtist = { onMuteArtist(song) },
                            onStartRadio = { onStartRadio(song) },
                            onToggleDownload = { onToggleDownload(song) },
                            isDownloaded = downloadedIds.contains(song.id),
                            isDownloading = downloadingIds.contains(song.id),
                            metaText = audienceLine,
                            metaHighlight = index == 0
                        )
                        // Popularity bar (Deezer rank 0–100) — the honest
                        // per-track signal; no invented play counts.
                        PopularityBar(rank = song.viewCount?.toLongOrNull() ?: 0L)
                    }
                }
            }

            // ---------------------------------------------- related artists
            if (relatedArtists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                SectionTitle("Penggemar juga mendengar")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(items = relatedArtists, key = { it.id }) { related ->
                        RelatedArtistCard(
                            artist = related,
                            onClick = { onOpenArtist(related.id.toString(), related.name) }
                        )
                    }
                }
            }

            // ---------------------------------------------------- discography
            if (albums.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionTitle("Diskografi")
                DiscographyFilterRow(
                    selected = discographyFilter,
                    onSelect = { discographyFilter = it },
                    available = albums.map { it.type }.distinct()
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredAlbums,
                        key = { it.id }
                    ) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onOpenAlbum(album.id) },
                            modifier = Modifier.noxaAppear(delayMillis = 0)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Floating back button — always reachable over the hero photo.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Kembali",
                tint = NoxaTextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    /** When set, the number ticks up to this value on first appearance. */
    count: Long? = null
) {
    Column(modifier = modifier) {
        if (count != null && count in 0..Int.MAX_VALUE) {
            NoxaCountUp(
                target = count.toInt(),
                style = androidx.compose.ui.text.TextStyle(
                    color = NoxaTextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black
                )
            )
        } else {
            Text(
                text = value,
                color = NoxaTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = label,
            color = NoxaTextTertiary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Thin popularity meter under a Populer row (rank 0–100). */
@Composable
private fun PopularityBar(rank: Long) {
    if (rank <= 0L) return
    val fraction = (rank.coerceIn(0L, 100L) / 100f)
    Box(
        modifier = Modifier
            .padding(start = 92.dp, end = 20.dp, top = 2.dp, bottom = 4.dp)
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(NoxaSurfaceSecondary)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NoxaLime.copy(alpha = 0.75f))
        )
    }
}

@Composable
private fun RelatedArtistCard(artist: ArtistProfile, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .noxaTap { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.pictureUrl)
                .size(240, 240)
                .crossfade(true)
                .build(),
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .border(1.dp, NoxaBorder, CircleShape)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            color = NoxaTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** Chips: Semua + every record type the discography actually contains. */
@Composable
private fun DiscographyFilterRow(
    selected: String,
    onSelect: (String) -> Unit,
    available: List<String>
) {
    val labels = remember(available) {
        buildList {
            add("Semua")
            available.forEach { type ->
                when {
                    type.equals("album", ignoreCase = true) -> add("Album")
                    type.equals("single", ignoreCase = true) -> add("Single")
                    type.equals("compile", ignoreCase = true) -> add("Kompilasi")
                }
            }
        }.distinct()
    }
    if (labels.size <= 1) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(labels) { label ->
            val isSelected = label == selected
            Text(
                text = label,
                // Selected tab goes neutral-white instead of lime: an in-page
                // filter is wayfinding, not playback.
                color = if (isSelected) NoxaBlack else NoxaTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) NoxaTextPrimary else NoxaSurfaceSecondary)
                    .noxaTap { onSelect(label) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = NoxaTextPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
private fun AlbumCard(
    album: AlbumInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .noxaTap { onClick() }
            .padding(4.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(album.coverUrl)
                .size(320, 320)
                .crossfade(true)
                .build(),
            contentDescription = album.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, NoxaBorder, RoundedCornerShape(10.dp))
                .background(NoxaSurfaceSecondary)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            color = NoxaTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = buildString {
                if (album.releaseYear.isNotBlank()) {
                    append(album.releaseYear)
                    if (album.trackCount > 0) append(" • ")
                }
                if (album.trackCount > 0) {
                    append(album.trackCount)
                    append(" lagu")
                }
            }.ifEmpty { "Album" },
            color = NoxaTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
