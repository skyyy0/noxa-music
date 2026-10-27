package com.example

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.data.local.AppSettingsStore
import com.example.data.local.NoxaDatabase
import com.example.data.local.PerfProfileStore
import com.example.data.local.UsagePolicyStore
import com.example.data.remote.ArtistBioRepository
import com.example.data.remote.LyricsRepository
import com.example.data.remote.MusicRepository
import com.example.data.remote.StreamResolver
import com.example.data.remote.YouTubeSourceRepository
import com.example.media.MediaPlaybackService
import com.example.media.NoxaAudioPlayer
import com.google.common.util.concurrent.ListenableFuture
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NoxaApplication : Application(), ImageLoaderFactory {

    lateinit var database: NoxaDatabase
        private set

    lateinit var musicRepository: MusicRepository
        private set

    lateinit var lyricsRepository: LyricsRepository
        private set

    lateinit var streamResolver: StreamResolver
        private set

    lateinit var youtubeSourceRepository: YouTubeSourceRepository
        private set

    lateinit var usagePolicyStore: UsagePolicyStore
        private set

    lateinit var appSettingsStore: AppSettingsStore
        private set

    lateinit var perfProfileStore: PerfProfileStore
        private set

    /** Pengaturan → Bahasa (default Indonesia — salinan app hari ini). */
    lateinit var languageStore: com.example.data.local.LanguageStore
        private set

    lateinit var artistBioRepository: ArtistBioRepository
        private set

    /**
     * **Halaman artis lebih lengkap**: subscriber YouTube (angka pendengar
     * yang benar-benar dikenali orang) + genre/asal/tahun dari MusicBrainz,
     * dua sumber publik tanpa kunci.
     */
    lateinit var artistInsightsScraper: com.example.data.remote.ArtistInsightsScraper
        private set

    /**
     * **Sistem Pintar**: the one-time device profile (RAM, cores, chipset).
     * Measured on first launch, then loaded from storage on every launch —
     * [perfProfileStore] answers `isReady` synchronously so the app shell can
     * decide whether to show the first-run scan before any coroutine exists.
     */
    val perfProfile: com.example.data.local.PerfProfile
        get() = cachedPerfProfile ?: com.example.data.local.PerfProfile.from(
            totalMemBytes = 0L,
            cores = Runtime.getRuntime().availableProcessors(),
            isLowRamDevice = isLowRamDevice
        ).also { cachedPerfProfile = it }

    @Volatile
    private var cachedPerfProfile: com.example.data.local.PerfProfile? = null

    lateinit var player: NoxaAudioPlayer
        private set

    lateinit var authManager: com.example.data.auth.AuthManager
        private set

    /**
     * Connecting a [MediaController] to [MediaPlaybackService] is what keeps the
     * media session (and therefore the notification, the lock screen controls and
     * background playback) alive, and what lets the system offer to resume the
     * last session after a reboot.
     */
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLogger()
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Handled gracefully in test environment
        }
        database = NoxaDatabase.getDatabase(this)
        musicRepository = MusicRepository()
        lyricsRepository = LyricsRepository()
        streamResolver = StreamResolver()
        youtubeSourceRepository = YouTubeSourceRepository()
        usagePolicyStore = UsagePolicyStore(this)
        appSettingsStore = AppSettingsStore(this)
        perfProfileStore = PerfProfileStore(this)
        languageStore = com.example.data.local.LanguageStore(this)
        cachedPerfProfile = perfProfileStore.load()
        artistBioRepository = ArtistBioRepository()
        artistInsightsScraper = com.example.data.remote.ArtistInsightsScraper()
        player = NoxaAudioPlayer(this)
        authManager = com.example.data.auth.AuthManager(this)
        connectMediaSession()
    }

    /**
     * Keeps the last crash stack trace on disk (`files/crash/latest-crash.txt`).
     * The exception is still delivered to the previous handler (the app behaves
     * exactly as before), but the NOXA support chat can now attach the real
     * stack trace to a bug report instead of guessing.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = java.io.File(filesDir, "crash").apply { mkdirs() }
                java.io.File(dir, "latest-crash.txt").writeText(
                    buildString {
                        appendLine("time=${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                        appendLine("thread=${thread.name}")
                        appendLine(throwable.toString())
                        throwable.stackTrace.forEach { appendLine("\tat $it") }
                    }
                )
            } catch (_: Exception) {
                // Never let the logger itself cause a second failure.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun connectMediaSession() {
        try {
            val token = SessionToken(this, ComponentName(this, MediaPlaybackService::class.java))
            val future = MediaController.Builder(this, token).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    try {
                        future.get()
                    } catch (e: Exception) {
                        Log.w(TAG, "Media controller connection failed: ${e.message}")
                    }
                },
                ContextCompat.getMainExecutor(this)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not connect to the media session: ${e.message}")
        }
    }

    override fun onTerminate() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onTerminate()
    }

    override fun newImageLoader(): ImageLoader {
        // 2 GB devices get a leaner image pipeline: a smaller memory cache
        // and RGB_565 artwork (covers have no alpha — this halves bitmap
        // memory, the single largest consumer during scroll on low-RAM
        // phones) while normal devices keep full quality.
        val lowRam = isLowRamDevice
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (lowRam) 0.12 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            }
            // Track/artist artwork is immutable, so honour the cache instead of
            // refetching it: this is the single biggest win for scroll smoothness.
            .respectCacheHeaders(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .apply {
                if (lowRam) bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            }
            .crossfade(true)
            .crossfade(if (lowRam) 120 else 200)
            .build()
    }

    /**
     * True on low-RAM devices (the 2 GB class Android flags itself). Used to
     * trim artwork caches, audio warming and animations instead of guessing
     * from the total heap — the OS knows better than we do.
     */
    val isLowRamDevice: Boolean by lazy {
        try {
            @Suppress("DEPRECATION")
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "NoxaApplication"

        lateinit var instance: NoxaApplication
            private set
    }
}
