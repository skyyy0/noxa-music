package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.util.ArtworkPalette
import com.example.data.model.ArtistProfile
import com.example.data.model.PinnedTracks
import com.example.data.model.Song
import com.example.data.remote.HomeMusicSection
import com.example.ui.components.HeroSpotlightCard
import com.example.ui.components.NowPlayingBars
import com.example.ui.components.NoxAmbientBackdrop
import com.example.ui.components.NoxaCountUp
import com.example.ui.components.NoxaShimmerBox
import com.example.ui.components.noxaAppear
import com.example.ui.components.noxaClickable
import com.example.ui.components.noxaTap
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaEmber
import com.example.ui.theme.NoxaHaze
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.data.auth.NoxaUser
import com.example.ui.viewmodel.ListeningStats
import java.util.Calendar

@Composable
fun HomeScreen(
    heroSong: Song?,
    sections: List<HomeMusicSection>,
    pinnedSongs: List<Song> = emptyList(),
    /** Recently played songs — rendered as their own shelf. */
    recentSongs: List<Song> = emptyList(),
    /** Most-played songs of the week — the Spotify-style quick-play grid. */
    quickPlaySongs: List<Song> = emptyList(),
    /** Taste-derived recommendations for the "Buat kamu" shelf. */
    forYouSongs: List<Song> = emptyList(),
    /** "Karena kamu sering dengar …" — the subtitle above forYouSongs. */
    forYouLabel: String = "",
    /** Weekly listening stats card; null (or zero plays) hides it. */
    listeningStats: ListeningStats? = null,
    popularArtists: List<ArtistProfile> = emptyList(),
    /** New releases from followed artists — the "Radar Rilis" shelf. */
    releaseRadar: List<com.example.data.model.AlbumInfo> = emptyList(),
    /** Opens an album page from the radar shelf. */
    onOpenAlbum: (Long) -> Unit = {},
    resumeSong: Song?,
    isLoading: Boolean,
    errorMessage: String?,
    currentUser: NoxaUser?,
    isGuestMode: Boolean,
    isSigningIn: Boolean,
    currentPlayingSongId: String?,
    onPlaySong: (Song, List<Song>, Int) -> Unit,
    onResume: () -> Unit,
    onDismissResume: () -> Unit,
    onSignInWithGoogle: (Activity) -> Unit,
    /** Opens the "Get Started" sign-in hub (Google / Email / Phone OTP). */
    onOpenGetStarted: () -> Unit = {},
    onRetry: () -> Unit,
    /** Plays the shuffled Mix Sky queue (pinned + favorites + recent). */
    onPlaySkyMix: () -> Unit = {},
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Selamat pagi"
            in 12..16 -> "Selamat siang"
            else -> "Selamat malam"
        }
    }

    // Flattened once per feed load instead of on every hero tap.
    val heroQueue = remember(sections) { sections.firstOrNull()?.songs.orEmpty() }

    Box(modifier = modifier.fillMaxSize().background(NoxaBlack)) {
        // The hero cover sets the room's light: sample its dominant colour once
        // per artwork and let the backdrop breathe in it, so Home changes mood
        // with the featured cover instead of sitting under a fixed lime wash.
        var ambientTint by remember { mutableStateOf<Color?>(null) }
        LaunchedEffect(heroSong?.artworkUrl) {
            val artworkUrl = heroSong?.artworkUrl
            ambientTint = if (artworkUrl.isNullOrBlank()) {
                null
            } else {
                ArtworkPalette.dominantColorOf(
                    artworkUrl,
                    coil.Coil.imageLoader(context),
                    context
                )
            }
        }
        NoxAmbientBackdrop(
            modifier = Modifier.matchParentSize(),
            tint = ambientTint
        )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            // The screens below the status bar start here; without this the header
            // slid under the clock and the guest card looked cut off.
            .statusBarsPadding()
            .testTag("home_screen"),
        // Extra room at the end of the feed: the floating mini player plus
        // the navigation pill stack taller than the old fused dock, and the
        // last shelf should scroll clear of them instead of stopping short.
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        // Top Header
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noxaAppear()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    // Eyebrow, not a shout: a tracked-out micro label sitting
                    // above the name gives the header hierarchy without size.
                    Text(
                        text = greeting,
                        color = NoxaTextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = if (currentUser != null) (currentUser.displayName ?: "NOXA Music") else "NOXA Music",
                        color = NoxaTextPrimary,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (currentUser?.photoUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentUser.photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Profile Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            // A neutral edge, not a brand ring: the avatar is a
                            // control, not an accent.
                            .border(1.dp, NoxaBorder, CircleShape)
                    )
                } else {                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(NoxaSurfaceSecondary)
                                .border(1.dp, NoxaBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "N",
                            color = NoxaTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Continue listening — the queue restored after the app (or device) restarted.
        if (resumeSong != null) {
            item(key = "resume") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        // Tone, not outline: the resume row separates from the
                        // canvas by a tonal step alone. The lime border it used
                        // to carry fought with the hero for attention.
                        .clip(RoundedCornerShape(14.dp))
                        .background(NoxaSurfaceElevated)
                        .noxaTap { onResume() }
                        .padding(12.dp)
                        .testTag("resume_card"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resumeSong.artworkUrl)
                            .size(128, 128)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lanjutkan mendengarkan",
                            color = NoxaTextTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = resumeSong.title,
                            color = NoxaTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = resumeSong.artist,
                            color = NoxaTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Glass, not brand fill: the hero below owns the single
                    // lime focal point on this screen.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Lanjutkan",
                            tint = NoxaTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onDismissResume) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = NoxaTextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Recommended Google Sign-in Banner for Guests
        if (currentUser == null) {
            item(key = "guest_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NoxaSurfaceElevated)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Guest",
                                tint = NoxaTextSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Mode Tamu Aktif",
                                    color = NoxaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Sign in to sync your library everywhere",
                                    color = NoxaTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }
                        }

                        // One entry point: the hub offers Google, Email and
                        // Phone (OTP) instead of firing Google directly.
                        Button(
                            onClick = onOpenGetStarted,
                            enabled = !isSigningIn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NoxaLime,
                                contentColor = NoxaBlack
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("get_started_btn")
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = NoxaBlack,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Real loading / error state for the API-backed home feed.
        if (isLoading || errorMessage != null) {
            item(key = "state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isLoading) {
                            // Shimmer skeleton shaped like the feed that is
                            // about to land — heading, shelf cards, list rows.
                            Column(modifier = Modifier.fillMaxWidth()) {
                                NoxaShimmerBox(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .height(20.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    repeat(3) {
                                        NoxaShimmerBox(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(150.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                repeat(3) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NoxaShimmerBox(
                                            modifier = Modifier.size(48.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            NoxaShimmerBox(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.6f)
                                                    .height(14.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            NoxaShimmerBox(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.35f)
                                                    .height(12.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Memuat musik...",
                                    color = NoxaTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Text(
                                text = errorMessage ?: "",
                                color = NoxaTextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NoxaLime,
                                    contentColor = NoxaBlack
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Coba lagi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // A. Hero Spotlight — "Hate Me" (Ellie Goulding with Juice WRLD),
        // resolved live from the catalogue so the cover and the audio are real.
        if (heroSong != null) {
            item(key = "hero") {
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    HomeSectionHeader(title = "Sorotan hari ini", subtitle = "")
                    Spacer(modifier = Modifier.height(10.dp))
                    HeroSpotlightCard(
                        song = heroSong,
                        // Full bleed on purpose: the artwork runs edge to edge and
                        // dissolves into the page instead of sitting in a card.
                        modifier = Modifier,
                        onPlay = {
                            val queue = if (heroQueue.isEmpty()) listOf(heroSong) else heroQueue
                            val index = queue.indexOfFirst { it.id == heroSong.id }.coerceAtLeast(0)
                            onPlaySong(heroSong, queue, index)
                        }
                    )
                }
            }
        }

        // A2. Putar Cepat — the Spotify-style grid of this week's top songs.
        val gridSongs = if (quickPlaySongs.isNotEmpty()) quickPlaySongs else recentSongs.take(8)
        if (gridSongs.isNotEmpty()) {
            item(key = "quick_grid") {
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    HomeSectionHeader(
                        title = "Putar cepat",
                        subtitle = "Lagu yang paling sering kamu putar",
                        leadingIcon = Icons.Default.LocalFireDepartment,
                        iconTint = NoxaEmber
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    gridSongs.chunked(2).forEach { rowSongs ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSongs.forEach { song ->
                                val index = gridSongs.indexOfFirst { it.id == song.id }
                                val tilePlaying = song.id == currentPlayingSongId
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        // No outline and no "playing" border: the
                                        // tile is a tonal block, and playback state
                                        // is shown by the bars over its artwork.
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NoxaSurfaceElevated)
                                        .noxaAppear(delayMillis = index * 40)
                                        .noxaClickable { onPlaySong(song, gridSongs, index) }
                                        .padding(7.dp)
                                        .testTag("quick_tile_${song.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(song.artworkUrl)
                                                .size(96, 96)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                        if (tilePlaying) {
                                            NowPlayingBars(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(3.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(9.dp))
                                    Text(
                                        text = song.title,
                                        color = if (tilePlaying) NoxaLime else NoxaTextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (rowSongs.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // A3. Statistik kamu — what you actually played this week (on-device).
        if (listeningStats != null && listeningStats.playsWeek > 0) {
            item(key = "listen_stats") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            // Warm tonal wash instead of a lime one: this card is
                            // about *your* listening, not about playback state.
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        NoxaSurfaceElevated,
                                        NoxaEmber.copy(alpha = 0.08f)
                                    )
                                )
                            )
                            .padding(14.dp)
                            .testTag("listen_stats_card")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = NoxaEmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "Statistik kamu",
                                color = NoxaTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            NoxaCountUp(
                                target = listeningStats.playsWeek,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = NoxaTextPrimary,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.8).sp
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "lagu diputar\n7 hari terakhir",
                                color = NoxaTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        if (listeningStats.topArtists.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(
                                    items = listeningStats.topArtists,
                                    key = { _, artist -> "stat_${artist.name}" }
                                ) { position, artist ->                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(NoxaSurfaceSecondary)
                                                .noxaTap { onOpenArtist("", artist.name) }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "#${position + 1} ${artist.name}",
                                                color = NoxaTextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "${artist.plays}x",
                                                color = NoxaTextTertiary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
            }
        }

        // A4. Buat kamu — recommendations grown from the user's own taste.
        if (forYouSongs.isNotEmpty()) {
            item(key = "for_you") {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    HomeSectionHeader(
                        title = "Buat kamu",
                        subtitle = forYouLabel.ifBlank {
                            "Rekomendasi dari selera mendengarmu"
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(
                            items = forYouSongs,
                            key = { _, song -> "foryou_${song.id}" },
                            contentType = { _, _ -> "foryou_card" }
                        ) { index, song ->
                            val forYouPlaying = song.id == currentPlayingSongId
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .noxaAppear(delayMillis = index * 50)
                                    .noxaClickable { onPlaySong(song, forYouSongs, index) }
                                    .testTag("foryou_song_${song.id}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .shadow(6.dp, RoundedCornerShape(14.dp))
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(NoxaSurfaceElevated)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(song.artworkUrl)
                                            .size(280, 280)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = song.title,
                                    color = if (forYouPlaying) NoxaLime else NoxaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = song.artist,
                                    color = NoxaTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // B. Artis Populer — avatar shelf; each avatar opens the artist page.
        if (popularArtists.isNotEmpty()) {
            item(key = "popular_artists") {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    HomeSectionHeader(
                        title = "Artis Populer",
                        subtitle = "Ketuk untuk membuka profil artis"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            items = popularArtists,
                            key = { _, artist -> artist.id },
                            contentType = { _, _ -> "artist_avatar" }
                        ) { _, artist ->
                            Column(
                                modifier = Modifier
                                    .width(72.dp)
                                    .noxaTap {
                                        onOpenArtist(artist.id.toString(), artist.name)
                                    }
                                    .testTag("popular_artist_${artist.id}"),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(artist.pictureUrl)
                                        .size(144, 144)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = artist.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .shadow(6.dp, CircleShape)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = artist.name,
                                    color = NoxaTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // C0. Radar Rilis — new albums from artists you follow (v2.0.0).
        if (releaseRadar.isNotEmpty()) {
            item(key = "release_radar_shelf") {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    HomeSectionHeader(
                        title = "Radar Rilis",
                        subtitle = "Album baru dari artis yang kamu ikuti",
                        leadingIcon = Icons.Default.NewReleases,
                        iconTint = NoxaHaze
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = releaseRadar,
                            key = { _, album -> "radar_${album.id}" },
                            contentType = { _, _ -> "radar_album" }
                        ) { index, album ->
                            Column(
                                modifier = Modifier
                                    .width(132.dp)
                                    .noxaAppear(delayMillis = index * 60)
                                    .noxaClickable { onOpenAlbum(album.id) }
                                    .testTag("radar_album_${album.id}")
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(album.coverUrl)
                                        .size(300, 300)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = album.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(132.dp)
                                        .shadow(6.dp, RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(7.dp))
                                Text(
                                    text = album.title,
                                    color = NoxaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = album.artistName.ifBlank { album.releaseYear },
                                    color = NoxaTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // C. Pinned by Sky — the dedicated pin shelf with the quote.
        if (pinnedSongs.isNotEmpty()) {
            item(key = "pinned_shelf") {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    HomeSectionHeader(
                        title = "Pinned by Sky",
                        subtitle = "Lagu khusus pilihan Sky",
                        leadingIcon = Icons.Rounded.PushPin,
                        trailing = {
                            // Mix Sky: one shuffled queue from Sky's pins, the
                            // user's favorites and their recent listens.
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(NoxaSurfaceSecondary)
                                    .noxaClickable { onPlaySkyMix() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("mix_sky_btn"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = null,
                                    tint = NoxaTextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Mix Sky",
                                    color = NoxaTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = pinnedSongs,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "pinned_card" }
                        ) { index, song ->
                            PinnedShelfCard(
                                song = song,
                                quote = PinnedTracks.find(song)?.quote.orEmpty(),
                                isPlaying = song.id == currentPlayingSongId,
                                onClick = { onPlaySong(song, pinnedSongs, index) },
                                // Cards materialise one after another instead of
                                // popping in together — the iOS shelf feel.
                                modifier = Modifier.noxaAppear(delayMillis = index * 70)
                            )
                        }
                    }
                }
            }
        }

        // C2. Dengar Terakhir — quick jumps back into recent listens.
        if (recentSongs.isNotEmpty()) {
            item(key = "recent_shelf") {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    HomeSectionHeader(
                        title = "Dengar Terakhir",
                        subtitle = "Lanjutkan dari mana kamu berhenti"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(
                            items = recentSongs,
                            key = { _, song -> "recent_${song.id}" },
                            contentType = { _, _ -> "recent_card" }
                        ) { index, song ->
                            val recentPlaying = song.id == currentPlayingSongId
                            Column(
                                modifier = Modifier
                                    .width(96.dp)
                                    .noxaAppear(delayMillis = index * 40)
                                    .noxaClickable {
                                        onPlaySong(song, recentSongs, index)
                                    }
                                    .testTag("recent_card_${song.id}"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .shadow(6.dp, RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NoxaSurfaceElevated)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(song.artworkUrl)
                                            .size(192, 192)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Text(
                                    text = song.title,
                                    color = if (recentPlaying) NoxaLime else NoxaTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // D. Curated shelves: Dewa 19, Sheila On 7, Justin Bieber, and more.
        sections.forEach { section ->
            item(key = "section_${section.title}") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                ) {
                    HomeSectionHeader(
                        title = section.title,
                        subtitle = section.subtitle
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(
                            items = section.songs,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "track_card" }
                        ) { index, song ->
                            val isPlaying = song.id == currentPlayingSongId
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .noxaAppear(delayMillis = index * 50)
                                    .noxaClickable {
                                        onPlaySong(song, section.songs, index)
                                    }
                                    .testTag("section_song_${song.id}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .shadow(6.dp, RoundedCornerShape(14.dp))
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(NoxaSurfaceElevated)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(song.artworkUrl)
                                            .size(280, 280)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (isPlaying) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(NoxaLime),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Playing",
                                                    tint = NoxaBlack,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (PinnedTracks.isPinned(song)) {
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                // Status over artwork needs its own
                                                // scrim to stay legible on light
                                                // covers — not a solid brand slab.
                                                .background(Color.Black.copy(alpha = 0.62f))
                                                .padding(horizontal = 5.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = NoxaBlack,
                                                modifier = Modifier.size(9.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Pinned",
                                                color = NoxaBlack,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = song.title,
                                    color = if (isPlaying) NoxaLime else NoxaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = song.artist,
                                    color = NoxaTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    // Shelf cards are narrow, so the artist name
                                    // itself is the entry point to their profile.
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .noxaTap { onOpenArtist(song.artistId, song.artist) }
                                        .padding(vertical = 1.dp, horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * A card on Sky's pin shelf: artwork with a vector star badge, the track — and the quote
 * that explains why it was pinned. Fixed width so several pins scroll in a row.
 */
@Composable
private fun PinnedShelfCard(
    song: Song,
    quote: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .width(280.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(NoxaSurfaceElevated)
            .noxaClickable { onClick() }
            .padding(12.dp)
            .testTag("pinned_card_${song.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(song.artworkUrl)
                        .size(144, 144)
                        .crossfade(true)
                        .build(),
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, NoxaBorder, RoundedCornerShape(10.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Pinned by Sky",
                        tint = NoxaBlack,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isPlaying) NoxaLime else NoxaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = NoxaTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(NoxaLime),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Putar lagu pinned",
                    tint = NoxaBlack,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (quote.isNotBlank()) {
            Text(
                text = "\u201C$quote\u201D",
                color = NoxaTextTertiary,
                fontSize = 11.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
        }
    }
}

/**
 * One header style for every shelf on Home — the rhythm that makes the feed
 * read as a designed surface instead of stacked widgets: an optional lime
 * badge for the shelf's icon, a large iOS-flavoured title, a quiet subtitle
 * and an optional trailing control (e.g. the Mix Sky chip). Content and
 * navigation are untouched; only the presentation is unified.
 */
@Composable
private fun HomeSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    /**
     * Wayfinder tint for [leadingIcon]. Shelves use a support hue (ember for
     * heat, haze for releases) instead of routing every icon through the
     * brand lime, which is reserved for playback state.
     */
    iconTint: Color = NoxaTextTertiary,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                // No chip container — a bare wayfinder icon keeps the header
                // quiet so the shelf title, not its decoration, carries weight.
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                color = NoxaTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp
            )
            Spacer(modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = NoxaTextTertiary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}
