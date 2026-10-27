package com.example

import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.model.ArtistProfile
import com.example.data.model.MusicSource
import com.example.data.model.Song
import com.example.data.remote.HomeMusicSection
import com.example.media.PlaybackProgress
import com.example.ui.components.FullPlayerSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.components.SongListItem
import com.example.ui.screens.GetStartedScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.viewmodel.LyricsUiState
import com.example.ui.viewmodel.VideoUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Renders the UI that appears exactly while a song is loading — mini player,
 * full player sheet, home feed shelves and song rows — so a render-time
 * exception shows up here instead of as a force-close on a device.
 */
@RunWith(AndroidJUnit4::class)
// A plain Application: NoxaApplication binds a media3 MediaController in
// onCreate, which Robolectric's shadowed services cannot satisfy.
@Config(sdk = [36], application = android.app.Application::class)
class UiRenderSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun song(id: String, title: String, artist: String) = Song(
        id = id,
        title = title,
        artist = artist,
        artworkUrl = "https://e-cdns-images.dzcdn.net/images/cover/x/500x500-000000-80-0-0.jpg",
        source = MusicSource.DEEZER,
        isPreview = true
    )

    @Test
    fun miniPlayerRendersWhileSongLoads() {
        val progress = MutableStateFlow(PlaybackProgress(positionMs = 1_000, durationMs = 30_000))
        compose.setContent {
            MiniPlayer(
                currentSong = song("deezer:1", "Kangen", "Dewa 19"),
                isPlaying = true,
                isBuffering = true, // "memuat lagu" state
                progressFlow = progress,
                isFavorite = false,
                onTogglePlayPause = {},
                onNext = {},
                onToggleFavorite = {},
                onClick = {}
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun fullPlayerSheetRendersWithPinnedQueue() {
        val current = song("deezer:1", "Kangen", "Dewa 19")
        val queue = listOf(
            current,
            song("deezer:2", "Terlalu Manis", "Slank"),
            song("deezer:3", "Dan", "Sheila On 7")
        )
        val progress = MutableStateFlow(PlaybackProgress())
        compose.setContent {
            FullPlayerSheet(
                visible = true,
                playerState = com.example.media.PlayerUiState(
                    currentSong = current,
                    isPlaying = true,
                    isBuffering = true,
                    queue = queue,
                    queueIndex = 0
                ),
                progressFlow = progress,
                lyricsState = LyricsUiState.Loading,
                videoUiState = VideoUiState.Idle,
                isFavorite = false,
                onClose = {},
                onTogglePlayPause = {},
                onSeekTo = {},
                onNext = {},
                onPrevious = {},
                onToggleShuffle = {},
                onToggleRepeat = {},
                onToggleFavorite = {},
                onSetSleepTimer = {},
                onOpenEqualizer = {},
                onSelectQueueItem = { _, _ -> },
                onShare = {},
                onRetryLyrics = {},
                onSetVideoMode = {},
                onAttachVideoSurface = {}
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun homeScreenRendersFeedWithPinnedAndRecent() {
        val sections = listOf(
            HomeMusicSection(
                title = "Dewa 19",
                subtitle = "Balada",
                songs = listOf(song("deezer:11", "Kangen", "Dewa 19"), song("deezer:12", "Pupus", "Dewa 19"))
            )
        )
        val pinned = listOf(
            song("deezer:1", "Kangen", "Dewa 19"),
            song("deezer:2", "Pupus", "Dewa 19"),
            song("deezer:3", "Terlalu Manis", "Slank")
        )
        val recent = listOf(song("deezer:4", "Dan", "Sheila On 7"))
        val artists = listOf(
            ArtistProfile(
                id = 1L, name = "Dewa 19", pictureUrl = "", fanCount = 100L,
                albumCount = 5, link = ""
            )
        )
        compose.setContent {
            HomeScreen(
                // Hero included: this path was never rendered in tests before.
                heroSong = song("deezer:99", "Hate Me", "Ellie Goulding, Juice WRLD"),
                sections = sections,
                pinnedSongs = pinned,
                recentSongs = recent,
                popularArtists = artists,
                resumeSong = null,
                isLoading = false,
                errorMessage = null,
                currentUser = null,
                isGuestMode = true,
                isSigningIn = false,
                currentPlayingSongId = "deezer:1",
                onPlaySong = { _, _, _ -> },
                onResume = {},
                onDismissResume = {},
                onSignInWithGoogle = {},
                onRetry = {},
                onPlaySkyMix = {}
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun songListItemRendersWithLongPressSupport() {
        compose.setContent {
            val s = remember { song("deezer:9", "Sepia", "Sheila On 7") }
            SongListItem(
                song = s,
                isPlaying = false,
                isFavorite = true,
                onPlay = {},
                onToggleFavorite = {},
                onPlayNext = {},
                onAddToQueue = {},
                onAddToPlaylist = {},
                onShare = {}
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun getStartedScreenRendersAllSignInOptions() {
        compose.setContent {
            GetStartedScreen(
                currentUser = null,
                isSigningIn = false,
                onBack = {},
                onSignInWithGoogle = { _, _ -> },
                onSignInWithEmail = { _, _, _ -> },
                onRegisterWithEmail = { _, _, _, _ -> },
                onSendPhoneOtp = { _, _, _, _ -> },
                onVerifyPhoneOtp = { _, _, _ -> }
            )
        }
        compose.waitForIdle()
    }
}
