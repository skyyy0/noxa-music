package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.model.AlbumInfo
import com.example.data.model.ArtistProfile
import com.example.data.model.MusicSource
import com.example.data.model.Song
import com.example.data.remote.ArtistInsights
import com.example.data.remote.HomeMusicSection
import com.example.ui.components.NoxAmbientBackdrop
import com.example.ui.screens.ArtistScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.NoxaBlack
import com.example.ui.viewmodel.ArtistUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Writes real PNG screenshots of the two screens this change touches so the
 * visual result (ambient background glow, per-row listener count) can be
 * inspected rather than guessed at. Output: `app/build/screenshots/` as PNG.
 */
@RunWith(AndroidJUnit4::class)
// NATIVE graphics: real Skia rasterisation, so gradients/bitmap compress work.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun song(id: String, title: String, artist: String, durationMs: Long) = Song(
        id = id,
        title = title,
        artist = artist,
        artworkUrl = "https://example.invalid/cover.jpg",
        durationMs = durationMs,
        source = MusicSource.DEEZER,
        isPreview = true,
        artistId = "1",
        viewCount = "92"
    )

    @Test
    fun homeScreenScreenshot() {
        val sections = listOf(
            HomeMusicSection(
                title = "Dewa 19",
                subtitle = "Balada",
                songs = listOf(
                    song("deezer:11", "Kangen", "Dewa 19", 300_000),
                    song("deezer:12", "Pupus", "Dewa 19", 280_000)
                )
            )
        )
        renderAndCapture("home_screen") {
            HomeScreen(
                heroSong = song("deezer:99", "Hate Me", "Ellie Goulding, Juice WRLD", 186_000),
                sections = sections,
                pinnedSongs = listOf(song("deezer:1", "Kangen", "Dewa 19", 300_000)),
                recentSongs = listOf(song("deezer:4", "Dan", "Sheila On 7", 250_000)),
                popularArtists = listOf(
                    ArtistProfile(
                        id = 1L, name = "Dewa 19", pictureUrl = "",
                        fanCount = 100L, albumCount = 5, link = ""
                    )
                ),
                resumeSong = null,
                isLoading = false,
                errorMessage = null,
                currentUser = null,
                isGuestMode = true,
                isSigningIn = false,
                currentPlayingSongId = "deezer:11",
                onPlaySong = { _, _, _ -> },
                onResume = {},
                onDismissResume = {},
                onSignInWithGoogle = {},
                onRetry = {},
                onPlaySkyMix = {}
            )
        }
    }

    @Test
    fun artistScreenScreenshot() {
        val topSongs = listOf(
            song("deezer:1", "Tak Kan Ada Cinta Yang Lain", "Dewa 19", 270_000),
            song("deezer:2", "Kangen", "Dewa 19", 300_000),
            song("deezer:3", "Selimut Hati", "Dewa 19", 279_000)
        )
        renderAndCapture("artist_screen") {
            ArtistScreen(
                state = ArtistUiState.Ready(
                    profile = ArtistProfile(
                        id = 1L,
                        name = "Dewa 19",
                        pictureUrl = "https://example.invalid/artist.jpg",
                        fanCount = 2_500_000L,
                        albumCount = 12,
                        link = ""
                    ),
                    topSongs = topSongs,
                    albums = listOf(
                        AlbumInfo(
                            id = 5L, title = "Bintang Lima", coverUrl = "",
                            releaseYear = "2001", trackCount = 10,
                            artistName = "Dewa 19", artistId = 1L, type = "album",
                            releaseDate = "2001-01-01"
                        )
                    ),
                    insights = ArtistInsights(
                        spotifyMonthlyListeners = 10_600_000L,
                        spotifyFollowers = 14_300_000L,
                        genres = listOf("rock", "alternatif")
                    )
                ),
                favoriteIds = setOf("deezer:2"),
                currentPlayingSongId = "deezer:2",
                isFollowing = false,
                onToggleFollow = {},
                onBack = {},
                onPlaySong = { _, _, _ -> },
                onToggleFavorite = {},
                onPlayNext = {},
                onAddToQueue = {},
                onAddToPlaylist = {},
                onShare = {},
                onOpenAlbum = {},
                onOpenArtist = { _, _ -> },
                onRetry = {}
            )
        }

        // Every Populer row must carry the artist's audience line — this is the
        // per-song listener count the feature added (3 top songs -> 3 lines).
        // Exact match: the header fact-chip contains the same words but is a
        // different (longer) string, so only the per-row lines match here.
        compose.onAllNodesWithText("10.6 jt pendengar bulanan")
            .assertCountEquals(topSongs.size)
    }

    /**
     * Isolates the ambient backdrop on pure black and asserts, by pixels, that
     * it paints a visible lime glow (top area) and warm ember (bottom area) —
     * i.e. the background is NOT flat black.
     */
    @Test
    fun ambientBackdropPaintsGlowOnBlack() {
        renderAndCapture("ambient_backdrop") {
            Box(modifier = Modifier.fillMaxSize().background(NoxaBlack)) {
                NoxAmbientBackdrop()
            }
        }

        val bmp = checkNotNull(lastBitmap) { "screenshot bitmap missing" }
        // Sample the radial CENTRES (the gradient sub-boxes sit TopStart and
        // BottomEnd, so corners are legitimately outside the glow radius).
        val lime = bmp.getPixel((bmp.width * 475) / 1000, bmp.height * 275 / 1000)
        val warm = bmp.getPixel((bmp.width * 575) / 1000, bmp.height * 75 / 100)
        val limeR = android.graphics.Color.red(lime)
        val limeG = android.graphics.Color.green(lime)
        val warmR = android.graphics.Color.red(warm)
        val warmG = android.graphics.Color.green(warm)
        // Glow must be a faint but real tint over black (not flat 0, not opaque).
        check(limeG in 4..120 && limeG >= limeR) {
            "lime ambient glow missing at upper zone: rgb($limeR,$limeG,${android.graphics.Color.blue(lime)})"
        }
        check(warmR in 4..120 && warmR > warmG) {
            "warm ambient glow missing at lower zone: rgb($warmR,$warmG,${android.graphics.Color.blue(warm)})"
        }
    }

    private var lastBitmap: Bitmap? = null

    private fun renderAndCapture(name: String, content: @Composable () -> Unit) {
        compose.setContent { content() }
        compose.waitForIdle()

        var bitmap: Bitmap? = null
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            if (view.width == 0 || view.height == 0) {
                val dm = compose.activity.resources.displayMetrics
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            }
            val w = view.width.coerceAtLeast(1)
            val h = view.height.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            bitmap = bmp
        }

        val outDir = File("build/screenshots").apply { mkdirs() }
        val outFile = File(outDir, "$name.png")
        outFile.outputStream().use { stream ->
            check(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "PNG compression failed for $name"
            }
        }
        lastBitmap = bitmap
        println("SCREENSHOT_SAVED=${outFile.absolutePath} (${bitmap!!.width}x${bitmap!!.height})")
    }
}
