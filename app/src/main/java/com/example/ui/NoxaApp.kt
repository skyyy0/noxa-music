package com.example.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.Song
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.FullPlayerSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.components.ShareSheetDialog
import com.example.ui.components.SongCommentsSheet
import com.example.ui.i18n.string
import com.example.ui.components.YouTubePolicyDialog
import com.example.ui.components.NoxaMotion
import com.example.ui.components.noxaTap
import com.example.ui.screens.AboutSkyScreen
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.AlbumScreen
import com.example.ui.screens.ArtistScreen
import com.example.ui.screens.DeviceScanScreen
import com.example.ui.screens.DevPanelScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.GetStartedScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SupportChatScreen
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.viewmodel.ArtistUiState
import com.example.ui.viewmodel.MusicViewModel
import com.example.ui.viewmodel.VideoUiState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    object Equalizer : Screen("equalizer", "Equalizer", Icons.Default.Tune)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
    object AboutSky : Screen("about_sky", "About Sky", Icons.Default.Person)

    /** Playlist detail: `playlist/{id}`. */
    object Playlist : Screen("playlist", "Playlist", Icons.Default.LibraryMusic) {
        const val ARG_ID = "playlistId"
        const val ROUTE = "playlist/{$ARG_ID}"
        fun routeFor(id: Long) = "playlist/$id"
    }

    /** Artist profile: `artist/{artistId}` (Deezer artist id). */
    object Artist : Screen("artist", "Artist", Icons.Default.Person) {
        const val ARG_ID = "artistId"
        const val ROUTE = "artist/{$ARG_ID}"
        fun routeFor(id: Long) = "artist/$id"
    }

    /** Album detail: `album/{albumId}`. */
    object Album : Screen("album", "Album", Icons.Default.LibraryMusic) {
        const val ARG_ID = "albumId"
        const val ROUTE = "album/{$ARG_ID}"
        fun routeFor(id: Long) = "album/$id"
    }

    /** NOXA AI chat — asisten musik yang bisa kontrol pemutar. */
    object AiChat : Screen("ai_chat", "AI", Icons.Default.Person)

    /** NOXA Support chat (lapor bug & keluhan). */
    object SupportChat : Screen("support_chat", "Support", Icons.Default.Person)

    /** Dengar Bareng — shared listening room. */
    object Room : Screen("room", "Dengar Bareng", Icons.Default.Group)

    /** NOXA Admin console — developer role only. */
    object DevPanel : Screen("dev_panel", "Admin", Icons.Default.Person)

    /** Sign-in hub: Google / Email / Phone (OTP). */
    object GetStarted : Screen("get_started", "Get Started", Icons.Default.Person)

    /** NOXA Wrapped tahunan — the year in listening, story-style. */
    object Wrapped : Screen("wrapped", "Wrapped", Icons.Default.BarChart)
}

@Composable
fun NoxaApp(
    viewModel: MusicViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    // Pengaturan → Bahasa: the whole tree reads translated strings through
    // LocalStrings; Indonesia (default) serves the original hardcoded copy.
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    androidx.compose.runtime.CompositionLocalProvider(
        com.example.ui.i18n.LocalStrings provides
            com.example.ui.i18n.Strings.forLanguage(appLanguage)
    ) {
        NoxaAppContent(viewModel = viewModel, navController = navController)
    }
}

@Composable
private fun NoxaAppContent(
    viewModel: MusicViewModel,
    navController: NavHostController
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val heroSong by viewModel.heroSong.collectAsStateWithLifecycle()
    val homeSections by viewModel.homeSections.collectAsStateWithLifecycle()
    val isHomeLoading by viewModel.isHomeLoading.collectAsStateWithLifecycle()
    val homeError by viewModel.homeError.collectAsStateWithLifecycle()
    val resumableSong by viewModel.resumableSong.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isGuestMode by viewModel.isGuestMode.collectAsStateWithLifecycle()
    // Komentar per Lagu: the shared thread under the current song (null = closed).
    val songComments by viewModel.songComments.collectAsStateWithLifecycle()
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    // Collecting the favourite *set* once here is what makes the heart icons
    // update immediately everywhere after a tap.
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val historySongs by viewModel.historySongs.collectAsStateWithLifecycle()
    val playlistSummaries by viewModel.playlistSummaries.collectAsStateWithLifecycle()
    val pinnedSongs by viewModel.pinnedSongs.collectAsStateWithLifecycle()
    val popularArtists by viewModel.popularArtists.collectAsStateWithLifecycle()
    val weeklyRecap by viewModel.weeklyRecap.collectAsStateWithLifecycle()
    val yearListening by viewModel.yearListening.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

    // Collaborative playlists: one flow for the sheets, one for banners,
    // and the playlistId -> code map for chips.
    // ------------------------------------------- v2.0.0: Sistem Pintar & radar
    val needsPerfScan by viewModel.needsPerfScan.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val devicePerf by viewModel.devicePerf.collectAsStateWithLifecycle()
    val releaseRadar by viewModel.releaseRadar.collectAsStateWithLifecycle()
    val followedArtistIds by viewModel.followedArtistIds.collectAsStateWithLifecycle()
    val lyricSearch by viewModel.lyricSearch.collectAsStateWithLifecycle()
    val mutedSongs by viewModel.mutedSongs.collectAsStateWithLifecycle()
    val mutedArtists by viewModel.mutedArtists.collectAsStateWithLifecycle()
    val songRatings by viewModel.songRatings.collectAsStateWithLifecycle()

    // ------------------------------------------- v2.1: radio & mode offline
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val downloadingIds by viewModel.downloadingIds.collectAsStateWithLifecycle()
    val offlineSongs by viewModel.offlineSongs.collectAsStateWithLifecycle()
    val isBuildingRadio by viewModel.isBuildingRadio.collectAsStateWithLifecycle()

    val sharedPlaylistUi by viewModel.sharedPlaylistUi.collectAsStateWithLifecycle()
    val sharedNotice by viewModel.sharedNotice.collectAsStateWithLifecycle()
    val sharedLinks by viewModel.sharedLinks.collectAsStateWithLifecycle()

    // A successful import opens the fresh playlist (and consumes the event).
    LaunchedEffect(sharedPlaylistUi) {
        val imported = sharedPlaylistUi as? com.example.ui.viewmodel.SharedPlaylistUi.Imported
            ?: return@LaunchedEffect
        navController.navigate(Screen.Playlist.routeFor(imported.playlistId))
        viewModel.consumeSharedPlaylistUi()
    }

    // Home taste: quick-play grid, stats card and "Buat kamu" shelf.
    val quickPlaySongs by viewModel.quickPlaySongs.collectAsStateWithLifecycle()
    val forYouSongs by viewModel.forYouSongs.collectAsStateWithLifecycle()
    val forYouLabel by viewModel.forYouLabel.collectAsStateWithLifecycle()
    val listeningStats by viewModel.listeningStats.collectAsStateWithLifecycle()

    // In-app update published by the developer account (wajib / opsional).
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val updateDialogVisible by viewModel.updateDialogVisible.collectAsStateWithLifecycle()
    val updatePhase by viewModel.updatePhase.collectAsStateWithLifecycle()
    val updateError by viewModel.updateError.collectAsStateWithLifecycle()

    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsStateWithLifecycle()
    val videoUiState by viewModel.videoUiState.collectAsStateWithLifecycle()
    val pendingYouTubePlay by viewModel.pendingYouTubePlay.collectAsStateWithLifecycle()
    val artistUiState by viewModel.artistUiState.collectAsStateWithLifecycle()
    val albumUiState by viewModel.albumUiState.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Song awaiting "which playlist?".
    var songPendingPlaylist by remember { mutableStateOf<Song?>(null) }

    // Song awaiting NOXA's share sheet (canvas image / copy link).
    var songPendingShare by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(playerState.errorMessage) {
        val message = playerState.errorMessage
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearPlayerError()
        }
    }

    // Mode offline: one toast per download/remove result, consumed right away.
    val downloadNotice by viewModel.downloadNotice.collectAsStateWithLifecycle()
    LaunchedEffect(downloadNotice) {
        val notice = downloadNotice ?: return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        viewModel.consumeDownloadNotice()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    // Tapping an artist name anywhere: resolve id (or name) -> open the page.
    val openArtistProfile: (String, String) -> Unit = { artistId, artistName ->
        viewModel.openArtistProfile(artistId, artistName) { resolvedId ->
            navController.navigate(Screen.Artist.routeFor(resolvedId)) {
                launchSingleTop = true
            }
        }
    }

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Search,
        Screen.Library,
        Screen.Equalizer,
        Screen.Profile
    )

    val isCurrentFavorite = playerState.currentSong?.id?.let { favoriteIds.contains(it) } ?: false

    // Collected again here (cheap state read) so Pengaturan shows the active
    // language; the tree itself is already wrapped in LocalStrings upstream.
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    // Hoisted tab navigation: tapping any bottom-nav tab always runs this,
    // from every route (home, search, library, equalizer, profile, artist,
    // album, playlist). Popping to the start destination keeps the stack flat.
    val navigateToTab: (Screen) -> Unit = { screen ->
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(NoxaBlack)) {
        Scaffold(
            bottomBar = {
                // ── Two surfaces, one system ──────────────────────────
                // The player and the navigation are deliberately separate: a
                // floating glass mini player hovering above its own light
                // navigation pill, with a real gap between them. Both wear the
                // same material — translucent sheet, top sheen, hairline edge,
                // soft shadow — so they read as siblings instead of as one
                // heavy slab. Each keeps its own boundary, its own shape and
                // its own inset, which is what keeps the stack feeling light.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    MiniPlayer(
                        currentSong = playerState.currentSong,
                        isPlaying = playerState.isPlaying,
                        isBuffering = playerState.isBuffering,
                        progressFlow = viewModel.playbackProgress,
                        isFavorite = isCurrentFavorite,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.next() },
                        onToggleFavorite = {
                            playerState.currentSong?.let { viewModel.toggleFavorite(it) }
                        },
                        onClick = { viewModel.openFullPlayer() },
                        // Inset from both edges: the panel floats, it is not a
                        // full-width strip welded to the screen.
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )

                    // The gap only exists while a song is loaded — a standing
                    // spacer would lift the bar for no reason.
                    if (playerState.currentSong != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    val navShape = RoundedCornerShape(26.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .shadow(10.dp, navShape, clip = false)
                            .clip(navShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        NoxaSurface.copy(alpha = 0.97f),
                                        NoxaSurface.copy(alpha = 0.90f)
                                    )
                                )
                            )
                            // Light gathers along the top edge and is gone by
                            // the time it reaches the icons: the glass cue,
                            // kept cheap enough to run every frame.
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                                )
                            )
                            // Edge highlight instead of an outline — a drawn
                            // top, a disappearing bottom.
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.15f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                ),
                                navShape
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bottomNavItems.forEach { screen ->
                                val isSelected = currentRoute == screen.route
                                // The selected icon settles a touch larger on a
                                // spring — a living detail without any flashing.
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.08f else 1f,
                                    animationSpec = NoxaMotion.snappySpring,
                                    label = "nav_icon_scale"
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .noxaTap { navigateToTab(screen) }
                                        .padding(vertical = 9.dp)
                                        .testTag("nav_item_${screen.route}"),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.title,
                                        tint = if (isSelected) NoxaTextPrimary else NoxaTextTertiary,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                            }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // Labels are always present: an evenly weighted,
                                    // quiet bar instead of a label that expands and
                                    // shoves its neighbours around.
                                    Text(
                                        text = when (screen.route) {
                                            Screen.Home.route -> string("nav.home")
                                            Screen.Search.route -> string("nav.search")
                                            Screen.Library.route -> string("nav.library")
                                            Screen.Profile.route -> string("nav.profile")
                                            else -> screen.title
                                        },
                                        color = if (isSelected) NoxaTextPrimary else NoxaTextTertiary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // The one lime mark in the bar: a 4dp dot that
                                    // says "you are here" without shouting.
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) NoxaLime else Color.Transparent
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // Breathing room under the pill so it never sits glued to
                    // the gesture inset.
                    Spacer(modifier = Modifier.height(8.dp))
                }
            },
            containerColor = NoxaBlack
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                composable(Screen.Home.route) {
                    // One-time first launch: the device scan must finish
                    // before the feed is allowed to hit the network.
                    if (needsPerfScan) {
                        LaunchedEffect(Unit) { viewModel.runPerfScan() }
                        DeviceScanScreen(profile = devicePerf, isScanning = isScanning)
                        return@composable
                    }
                    HomeScreen(
                        heroSong = heroSong,
                        sections = homeSections,
                        pinnedSongs = pinnedSongs,
                        recentSongs = historySongs.take(12),
                        quickPlaySongs = quickPlaySongs,
                        forYouSongs = forYouSongs,
                        forYouLabel = forYouLabel,
                        listeningStats = listeningStats,
                        onPlaySkyMix = { viewModel.playSkyMix() },
                        popularArtists = popularArtists,
                        resumeSong = resumableSong,
                        isLoading = isHomeLoading,
                        errorMessage = homeError,
                        currentUser = currentUser,
                        isGuestMode = isGuestMode,
                        isSigningIn = isSigningIn,
                        currentPlayingSongId = playerState.currentSong?.id,
                        onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                        onResume = { viewModel.resumeLastSession() },
                        onDismissResume = { viewModel.dismissResume() },
                        onSignInWithGoogle = { activity -> viewModel.signInWithGoogle(activity) },
                        onOpenGetStarted = { navController.navigate(Screen.GetStarted.route) },
                        onRetry = { viewModel.loadHome() },
                        onOpenArtist = openArtistProfile,
                        releaseRadar = releaseRadar,
                        onOpenAlbum = { albumId ->
                            navController.navigate(Screen.Album.routeFor(albumId))
                        }
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        currentPlayingSongId = playerState.currentSong?.id,
                        favoriteIds = favoriteIds,
                        // Artwork for the browse grid: real artist photos from
                        // Deezer plus the Global Top 50 cover, Spotify-style.
                        genreImages = remember(homeSections, popularArtists) {
                            buildMap {
                                popularArtists.forEach { artist ->
                                    if (artist.pictureUrl.isNotBlank()) {
                                        put(artist.name, artist.pictureUrl)
                                    }
                                }
                                homeSections
                                    .firstOrNull { it.title == "Global Top 50" }
                                    ?.songs
                                    ?.firstOrNull()
                                    ?.artworkUrl
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { put("Global Top 50", it) }
                            }
                        },
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        onPerformSearch = { viewModel.performSearch(it) },
                        onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onAddToPlaylist = { songPendingPlaylist = it },
                        onShare = { songPendingShare = it },
                        onOpenArtist = openArtistProfile,
                        lyricSearch = lyricSearch,
                        onSearchByLyrics = { viewModel.searchByLyrics(it) },
                        onClearLyricSearch = { viewModel.clearLyricSearch() },
                        songRatings = songRatings,
                        onRateSong = { song, stars -> viewModel.rateSong(song, stars) },
                        onMuteSong = { viewModel.muteSong(it) },
                        onMuteArtist = { viewModel.muteArtist(it) },
                        downloadedIds = downloadedIds,
                        downloadingIds = downloadingIds,
                        onToggleDownload = { viewModel.toggleDownload(it) },
                        onStartRadio = { viewModel.startSongRadio(it) }
                    )
                }

                composable(Screen.Library.route) {
                    LibraryScreen(
                        favorites = favoriteSongs,
                        history = historySongs,
                        playlistSummaries = playlistSummaries,
                        currentPlayingSongId = playerState.currentSong?.id,
                        onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        favoriteIds = favoriteIds,
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onAddToPlaylist = { songPendingPlaylist = it },
                        onShare = { songPendingShare = it },
                        onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onOpenPlaylist = { navController.navigate(Screen.Playlist.routeFor(it)) },
                        onClearHistory = { viewModel.clearHistory() },
                        onOpenArtist = openArtistProfile,
                        sharedPlaylistUi = sharedPlaylistUi,
                        onCheckSharedPlaylist = { viewModel.previewSharedPlaylist(it) },
                        onImportSharedPlaylist = { viewModel.importSharedPlaylist() },
                        onConsumeSharedPlaylist = { viewModel.consumeSharedPlaylistUi() },
                        sharedLinks = sharedLinks,
                        songRatings = songRatings,
                        onRateSong = { song, stars -> viewModel.rateSong(song, stars) },
                        onMuteSong = { viewModel.muteSong(it) },
                        onMuteArtist = { viewModel.muteArtist(it) },
                        offlineSongs = offlineSongs,
                        downloadedIds = downloadedIds,
                        downloadingIds = downloadingIds,
                        onToggleDownload = { viewModel.toggleDownload(it) },
                        onStartRadio = { viewModel.startSongRadio(it) }
                    )
                }

                composable(Screen.Playlist.ROUTE) { entry ->
                    val playlistId = entry.arguments?.getString(Screen.Playlist.ARG_ID)?.toLongOrNull()
                    if (playlistId == null) {
                        navController.popBackStack()
                    } else {
                        val songsFlow = remember(playlistId) { viewModel.playlistSongs(playlistId) }
                        PlaylistDetailScreen(
                            playlistId = playlistId,
                            playlistName = playlistSummaries
                                .firstOrNull { it.playlistId == playlistId }?.name
                                ?: "Playlist",
                            songsFlow = songsFlow,
                            favoriteIds = favoriteIds,
                            currentPlayingSongId = playerState.currentSong?.id,
                            isPlaying = playerState.isPlaying,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onBack = { navController.popBackStack() },
                            onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onRemoveSong = { songId -> viewModel.removeSongFromPlaylist(playlistId, songId) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onShare = { songPendingShare = it },
                            onOpenArtist = openArtistProfile,
                            sharedCode = sharedLinks[playlistId.toString()],
                            sharedPlaylistUi = sharedPlaylistUi,
                            sharedNotice = sharedNotice,
                            onSharePlaylist = { viewModel.sharePlaylist(playlistId) },
                            onSyncPlaylist = { viewModel.syncSharedPlaylist(playlistId) },
                            onDismissNotice = { viewModel.dismissSharedNotice() },
                            onConsumeSharedPlaylist = { viewModel.consumeSharedPlaylistUi() },
                            downloadedIds = downloadedIds,
                            downloadingIds = downloadingIds,
                            onToggleDownload = { viewModel.toggleDownload(it) },
                            onStartRadio = { viewModel.startSongRadio(it) }
                        )
                    }
                }

                composable(Screen.Artist.ROUTE) { entry ->
                    val artistId = entry.arguments?.getString(Screen.Artist.ARG_ID)?.toLongOrNull()
                    if (artistId == null) {
                        navController.popBackStack()
                    } else {
                        LaunchedEffect(artistId) { viewModel.loadArtist(artistId) }
                        ArtistScreen(
                            state = artistUiState,
                            favoriteIds = favoriteIds,
                            currentPlayingSongId = playerState.currentSong?.id,
                            isPlaying = playerState.isPlaying,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onBack = { navController.popBackStack() },
                            onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songPendingPlaylist = it },
                            onShare = { songPendingShare = it },
                            onOpenAlbum = { albumId ->
                                navController.navigate(Screen.Album.routeFor(albumId))
                            },
                            onOpenArtist = openArtistProfile,
                            onRetry = { viewModel.loadArtist(artistId) },
                            isFollowing = followedArtistIds.contains(artistId),
                            onToggleFollow = {
                                (artistUiState as? ArtistUiState.Ready)?.let { ready ->
                                    viewModel.toggleFollowArtist(ready.profile)
                                }
                            },
                            songRatings = songRatings,
                            onRateSong = { song, stars -> viewModel.rateSong(song, stars) },
                            onMuteSong = { viewModel.muteSong(it) },
                            onMuteArtist = { viewModel.muteArtist(it) },
                            downloadedIds = downloadedIds,
                            downloadingIds = downloadingIds,
                            onToggleDownload = { viewModel.toggleDownload(it) },
                            onStartRadio = { viewModel.startSongRadio(it) },
                            isBuildingRadio = isBuildingRadio
                        )
                    }
                }

                composable(Screen.Album.ROUTE) { entry ->
                    val albumId = entry.arguments?.getString(Screen.Album.ARG_ID)?.toLongOrNull()
                    if (albumId == null) {
                        navController.popBackStack()
                    } else {
                        LaunchedEffect(albumId) { viewModel.loadAlbum(albumId) }
                        AlbumScreen(
                            state = albumUiState,
                            favoriteIds = favoriteIds,
                            currentPlayingSongId = playerState.currentSong?.id,
                            isPlaying = playerState.isPlaying,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onBack = { navController.popBackStack() },
                            onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songPendingPlaylist = it },
                            onShare = { songPendingShare = it },
                            onOpenArtist = openArtistProfile,
                            onRetry = { viewModel.loadAlbum(albumId) },
                            downloadedIds = downloadedIds,
                            downloadingIds = downloadingIds,
                            onToggleDownload = { viewModel.toggleDownload(it) },
                            onStartRadio = { viewModel.startSongRadio(it) }
                        )
                    }
                }

                composable(Screen.Equalizer.route) {
                    EqualizerScreen(
                        equalizerState = playerState.equalizerState,
                        onSetEnabled = { viewModel.setEqualizerEnabled(it) },
                        onSelectPreset = { viewModel.setEqualizerPreset(it) },
                        onSetBandLevel = { band, level -> viewModel.setEqualizerBand(band, level) },
                        onSetBassBoost = { viewModel.setBassBoost(it) },
                        onSetVirtualizer = { viewModel.setVirtualizer(it) }
                    )
                }

                composable(Screen.Profile.route) {
                    ProfileScreen(
                        currentUser = currentUser,
                        isSigningIn = isSigningIn,
                        weeklyRecap = weeklyRecap,
                        yearListening = yearListening,
                        appSettings = appSettings,
                        onSignInWithGoogle = { activity: Activity, onError: (String) -> Unit ->
                            viewModel.signInWithGoogle(activity, onError = onError)
                        },
                        onSignInWithEmail = { email, password, onError ->
                            viewModel.signInWithEmail(email, password, onError = onError)
                        },
                        onRegisterWithEmail = { email, password, username, onError ->
                            viewModel.registerWithEmail(
                                email,
                                password,
                                username,
                                onError = onError
                            )
                        },
                        onSetMvQuality = { viewModel.setMvQualityHeight(it) },
                        onSetAutoplaySimilar = { viewModel.setAutoplaySimilar(it) },
                        onSetKaraokeOverlay = { viewModel.setKaraokeOverlay(it) },
                        onSetAutoResumeOnOpen = { viewModel.setAutoResumeOnOpen(it) },
                        onSignOut = { viewModel.signOut() },
                        onNavigateToAboutSky = {
                            navController.navigate(Screen.AboutSky.route)
                        },
                        onOpenAiChat = {
                            navController.navigate(Screen.AiChat.route)
                        },
                        onOpenSupportChat = {
                            navController.navigate(Screen.SupportChat.route)
                        },
                        onOpenDevPanel = {
                            navController.navigate(Screen.DevPanel.route)
                        },
                        onOpenRoom = {
                            navController.navigate(Screen.Room.route)
                        },
                        onOpenGetStarted = { navController.navigate(Screen.GetStarted.route) },
                        onOpenWrapped = {
                            navController.navigate(Screen.Wrapped.route) {
                                launchSingleTop = true
                            }
                        },
                        onChangeUsername = { name, onResult ->
                            viewModel.changeUsername(name, onResult)
                        },
                        onChangePassword = { current, fresh, onResult ->
                            viewModel.changePassword(current, fresh, onResult)
                        },
                        onDeleteAccount = { email, onResult ->
                            viewModel.deleteAccount(email, onResult)
                        },
                        availableUpdate = updateInfo,
                        onOpenUpdate = { viewModel.openUpdateFromSettings() },
                        language = appLanguage,
                        onSetLanguage = { viewModel.setLanguage(it) },
                        onSetCrossfade = { viewModel.setCrossfadeEnabled(it) },
                        perfProfile = devicePerf,
                        mutedArtists = mutedArtists,
                        mutedSongs = mutedSongs,
                        onUnmute = { kind, value -> viewModel.unmute(kind, value) }
                    )
                }

                composable(Screen.GetStarted.route) {
                    GetStartedScreen(
                        currentUser = currentUser,
                        isSigningIn = isSigningIn,
                        onBack = { navController.popBackStack() },
                        onSignInWithGoogle = { activity, onError ->
                            viewModel.signInWithGoogle(activity, onError = onError)
                        },
                        onSignInWithEmail = { email, password, onError ->
                            viewModel.signInWithEmail(email, password, onError = onError)
                        },
                        onRegisterWithEmail = { email, password, username, onError ->
                            viewModel.registerWithEmail(
                                email,
                                password,
                                username,
                                onError = onError
                            )
                        },
                        onSendPhoneOtp = { activity, phone, onCodeSent, onError ->
                            viewModel.sendPhoneOtp(activity, phone, onCodeSent, onError)
                        },
                        onVerifyPhoneOtp = { verificationId, code, onError ->
                            viewModel.verifyPhoneOtp(verificationId, code, onError)
                        }
                    )
                }

                composable(Screen.AboutSky.route) {
                    AboutSkyScreen(
                        playerState = playerState,
                        onPlaySong = { song -> viewModel.playSong(song) },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.AiChat.route) {
                    val aiMessages by viewModel.aiMessages.collectAsStateWithLifecycle()
                    val isAiThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
                    val aiError by viewModel.aiError.collectAsStateWithLifecycle()
                    AiChatScreen(
                        messages = aiMessages,
                        isThinking = isAiThinking,
                        error = aiError,
                        playingSongId = playerState.currentSong?.id,
                        onBack = { navController.popBackStack() },
                        onSend = { viewModel.sendAiMessage(it) },
                        onPlaySongMessage = { viewModel.playAiSongMessage(it) },
                        onClearChat = { viewModel.clearAiChat() }
                    )
                }

                composable(Screen.Room.route) {
                    val roomState by viewModel.roomState.collectAsStateWithLifecycle()
                    val roomProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
                    val roomSongId = roomState.let { s ->
                        (s as? com.example.ui.screens.RoomUiState.InRoom)?.songId.orEmpty()
                    }
                    val sameSong = playerState.currentSong?.id == roomSongId && roomSongId.isNotEmpty()
                    DisposableEffect(Unit) {
                        onDispose { viewModel.leaveRoom() }
                    }
                    com.example.ui.screens.RoomScreen(
                        state = roomState,
                        onBack = { navController.popBackStack() },
                        onJoin = { code -> viewModel.joinRoom(code) },
                        onCreate = { viewModel.createRoom() },
                        onLeave = {
                            viewModel.leaveRoom()
                            navController.popBackStack()
                        },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSeekTo = { pos -> viewModel.seekTo(pos) },
                        onPrevious = { viewModel.previous() },
                        onNext = { viewModel.next() },
                        onVoteSkip = { viewModel.toggleSkipVote() },
                        localPositionMs = if (sameSong) roomProgress.positionMs else 0L,
                        localDurationMs = if (sameSong) {
                            roomProgress.durationMs.takeIf { it > 0 }
                                ?: playerState.currentSong?.durationMs ?: 0L
                        } else {
                            0L
                        }
                    )
                }

                composable(Screen.SupportChat.route) {
                    val supportState by viewModel.supportState.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { viewModel.openSupportChat() }
                    SupportChatScreen(
                        state = supportState,
                        onBack = { navController.popBackStack() },
                        onRefresh = { viewModel.refreshSupportChat() },
                        onSend = { viewModel.sendSupportMessage(it) },
                        onLoginHint = { navigateToTab(Screen.Profile) }
                    )
                }

                composable(Screen.Wrapped.route) {
                    com.example.ui.screens.WrappedScreen(
                        yearListening = yearListening,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.DevPanel.route) {
                    val devState by viewModel.devState.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { viewModel.openDevPanel() }
                    DevPanelScreen(
                        state = devState,
                        onBack = { navController.popBackStack() },
                        onRefresh = { viewModel.refreshDevPanel() },
                        onReply = { uid, text -> viewModel.sendSupportReply(uid, text) },
                        onCreateAccount = { email, username, password, role, onResult ->
                            viewModel.createAccountAsDev(email, username, password, role, onResult)
                        },
                        onChangeRole = { uid, role -> viewModel.changeAccountRole(uid, role) },
                        currentVersionCode = com.example.BuildConfig.VERSION_CODE.toLong(),
                        currentVersionName = com.example.BuildConfig.VERSION_NAME,
                        onPushUpdate = { url, name, code, note, mandatory, onResult ->
                            viewModel.pushUpdate(url, name, code, note, mandatory, onResult)
                        }
                    )
                }
            }
        }

        // In-app update — sits above every screen (nav, player sheet,
        // dialogs) until the user is on the published version. The developer
        // account never sees it: publishing an update must never lock the
        // publisher out of the very console she pushes it from.
        val pendingUpdate = updateInfo
        if (updateDialogVisible && pendingUpdate != null &&
            // Render-site guard: even if stale state slipped through, a build
            // already on/past the published versionCode never draws the popup.
            com.example.data.remote.UpdateGate.isNewer(
                com.example.BuildConfig.VERSION_CODE.toLong(), pendingUpdate.versionCode
            ) &&
            currentUser?.role != com.example.data.support.AccountRole.DEVELOPER
        ) {
            com.example.ui.components.UpdateDialog(
                info = pendingUpdate,
                phase = updatePhase,
                error = updateError,
                mandatory = pendingUpdate.mandatory,
                onUpdate = { viewModel.downloadUpdate() },
                onDismiss = { viewModel.dismissOptionalUpdate() }
            )
        }

        // Full Screen Player Overlay Sheet
        FullPlayerSheet(
            visible = isFullPlayerVisible,
            playerState = playerState,
            progressFlow = viewModel.playbackProgress,
            lyricsState = lyricsState,
            videoUiState = videoUiState,
            isFavorite = isCurrentFavorite,
            onClose = { viewModel.closeFullPlayer() },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSeekTo = { viewModel.seekTo(it) },
            onNext = { viewModel.next() },
            onPrevious = { viewModel.previous() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onToggleFavorite = {
                playerState.currentSong?.let { viewModel.toggleFavorite(it) }
            },                onSetSleepTimer = { viewModel.setSleepTimer(it) },
                onSetSleepTimerEndOfTrack = { enabled ->
                    viewModel.setSleepTimerEndOfTrack(enabled)
                },
            onOpenEqualizer = {
                viewModel.closeFullPlayer()
                navController.navigate(Screen.Equalizer.route)
            },
            onSelectQueueItem = { song, idx ->
                viewModel.playSong(song, playerState.queue, idx)
            },
            onShare = {
                playerState.currentSong?.let { songPendingShare = it }
            },
            onOpenComments = {
                playerState.currentSong?.let { viewModel.openSongComments(it) }
            },
            onRetryLyrics = { viewModel.retryLyrics() },
            onSetVideoMode = { enabled -> viewModel.setVideoMode(enabled) },
            onAttachVideoSurface = { surface -> viewModel.attachMvSurface(surface) },
            karaokeOverlayEnabled = appSettings.karaokeOverlay,
            onOpenArtist = { artistId, artistName ->
                viewModel.openArtistProfile(artistId, artistName) { resolvedId ->
                    viewModel.closeFullPlayer()
                    navController.navigate(Screen.Artist.routeFor(resolvedId)) {
                        launchSingleTop = true
                    }
                }
            }
        )

        // One-time YouTube usage policy, shown before the first YouTube play.
        if (pendingYouTubePlay != null) {
            YouTubePolicyDialog(
                onAccept = { viewModel.acceptYouTubePolicy() },
                onDecline = { viewModel.declineYouTubePolicy() }
            )
        }

        // "Which playlist?" picker, triggered from any song's overflow menu.
        songPendingPlaylist?.let { song ->
            AddToPlaylistDialog(
                song = song,
                playlists = playlistSummaries,
                onDismiss = { songPendingPlaylist = null },
                onSelect = { playlistId ->
                    viewModel.addSongToPlaylist(playlistId, song)
                    songPendingPlaylist = null
                    Toast.makeText(context, "Ditambahkan ke playlist", Toast.LENGTH_SHORT).show()
                },
                onCreatePlaylist = { name ->
                    viewModel.createPlaylist(name)
                    Toast.makeText(context, "Playlist \"$name\" dibuat", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // NOXA share sheet — canvas image or copy the deep link.
        songPendingShare?.let { song ->
            ShareSheetDialog(
                song = song,
                onDismiss = { songPendingShare = null }
            )
        }

        // Komentar per Lagu — shared thread keyed by the song id.
        songComments?.let { commentsUi ->
            SongCommentsSheet(
                ui = commentsUi,
                isSignedIn = currentUser != null && !isGuestMode,
                onDismiss = { viewModel.closeSongComments() },
                onPost = { text -> viewModel.postSongComment(text) },
                onSignIn = {
                    viewModel.closeSongComments()
                    viewModel.closeFullPlayer()
                    navController.navigate(Screen.GetStarted.route) { launchSingleTop = true }
                }
            )
        }
    }
}
