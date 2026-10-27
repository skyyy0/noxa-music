package com.example.voice

import com.example.NoxaApplication
import com.example.data.model.MusicSource
import com.example.data.model.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Jembatan antara [NoxaWakeService] (yang berjalan di background, di atas app
 * lain) dan dunia pemutaran musik.
 *
 * Dua jalur, dipilih otomatis:
 *
 *  * **[handler]** — diisi [com.example.ui.viewmodel.MusicViewModel] saat UI
 *    hidup. Semua keputusan (kebijakan YouTube, salinan offline, ladder
 *    resolusi audio) dijalankan persis seperti ketukan manual.
 *  * **[WakeCommandEngine]** — fallback saat aplikasi sudah ditutup user dan
 *    ViewModel tidak ada. Service tetap bisa memutar lagu lewat singleton di
 *    [NoxaApplication].
 *
 * Kenapa ini penting: fitur utama NOXA Wake memang dipakai saat NOXA sedang di
 * background/ditutup — jalur fallback itu bukan pelengkap, itu jalur utama.
 */
object NoxaWakeBus {

    /**
     * Dipasang MusicViewModel di `init` dan dilepas di `onCleared`. Tipe
     * suspend (bukan `SharedFlow`) supaya service tahu kapan perintah sudah
     * benar-benar dieksekusi dan bisa menampilkan hasilnya di bubble.
     */
    @Volatile
    var handler: (suspend (String) -> String)? = null

    /** Jalankan satu transcript → pesan hasil (untuk bubble/notifikasi). */
    suspend fun dispatch(transcript: String): String {
        val h = handler
        if (h != null) {
            runCatching { h(transcript) }.getOrNull()?.let { return it }
            // handler gagal/throw: jangan diam — jatuh ke engine lokal.
        }
        return WakeCommandEngine.execute(transcript)
    }
}

/**
 * Eksekusi perintah suara tanpa ViewModel (aplikasi ditutup, proses masih
 * hidup karena foreground service). Meniru ladder di MusicViewModel:
 * salinan offline → resolver milik sendiri → audio YouTube penuh (hanya
 * setelah kebijakan YouTube diterima).
 */
object WakeCommandEngine {

    suspend fun execute(transcript: String): String {
        val app = NoxaApplication.instance
        return when (val cmd = WakeCommandParser.parse(transcript)) {
            is WakeCommand.Play -> play(app, cmd.query)
            WakeCommand.Pause -> pause(app)
            WakeCommand.Resume -> resume(app)
            WakeCommand.Next -> {
                app.player.next()
                "Lagu berikutnya."
            }
            WakeCommand.Previous -> {
                app.player.previous()
                "Lagu sebelumnya."
            }
            is WakeCommand.Unknown ->
                if (cmd.text.isBlank()) "" else WakeCommandParser.HINT
        }
    }

    private fun pause(app: NoxaApplication): String =
        if (app.player.uiState.value.isPlaying) {
            app.player.togglePlayPause()
            "Dijeda."
        } else {
            "Sudah dalam keadaan jeda."
        }

    private fun resume(app: NoxaApplication): String =
        if (app.player.uiState.value.isPlaying) {
            "Sudah diputar."
        } else {
            app.player.togglePlayPause()
            "Dilanjutkan."
        }

    private suspend fun play(app: NoxaApplication, rawQuery: String): String {
        val query = rawQuery.trim()
        if (query.isEmpty()) return resume(app)

        // Katalog (Deezer + Audius) lebih dulu karena langsung punya URL audio;
        // YouTube ditambah sebagai cadangan penuh.
        val results = coroutineScope {
            val catalog = async {
                runCatching { app.musicRepository.search(query) }.getOrDefault(emptyList())
            }
            val youtube = async {
                runCatching { app.youtubeSourceRepository.search(query) }.getOrDefault(emptyList())
            }
            (catalog.await() + youtube.await()).distinctBy { it.id }
        }
        if (results.isEmpty()) {
            return "Lagu \u201c$query\u201d tidak ditemukan."
        }

        // Salinan offline menang telak: bisa diputar tanpa internet.
        val offline = runCatching { app.database.noxaDao().offlineTracksOnce() }
            .getOrDefault(emptyList())
            .filter { it.audioExists() }
            .associateBy { it.songId }
        val playable = results
            .filter { it.streamUrl.isNotBlank() }
            .map { offline[it.id]?.toSong() ?: it }
            .ifEmpty { emptyList() }

        var chosen = playable.firstOrNull()
        if (chosen == null) {
            // Hanya hasil YouTube (URL audio belum ada): butuh kebijakan +
            // resolusi instance. Kalau gagal, jujur — jangan diam.
            val yt = results.firstOrNull { it.source == MusicSource.YOUTUBE }
                ?: return denialOrMissing(app, results, query)
            if (!app.usagePolicyStore.isYouTubePolicyAccepted) {
                return "Perlu sekali persetujuan kebijakan YouTube di dalam NOXA " +
                    "sebelum hasil ini bisa diputar."
            }
            chosen = runCatching { app.youtubeSourceRepository.resolveAudio(yt) }.getOrNull()
                ?: return "Audio \u201c$query\u201d belum bisa diambil sekarang — coba lagi nanti."
        }

        // Ladder resolusi (sama dengan MusicViewModel): preview Deezer yang
        // sudah berkebijakan dicocokkan ke audio penuh.
        val resolved = resolveFullAudio(app, chosen) ?: chosen
        val queue = playable.map { if (it.id == resolved.id) resolved else it }
            .ifEmpty { listOf(resolved) }
        val index = queue.indexOfFirst { it.id == resolved.id }.coerceAtLeast(0)

        app.player.playSong(resolved, queue, index)
        return "Memutar \u201c${resolved.title}\u201d \u2014 ${resolved.artist}"
    }

    private fun denialOrMissing(app: NoxaApplication, results: List<Song>, query: String): String =
        if (results.isEmpty()) "Lagu \u201c$query\u201d tidak ditemukan."
        else "Sumber audio untuk \u201c$query\u201d belum tersedia."

    private suspend fun resolveFullAudio(app: NoxaApplication, song: Song): Song? {
        if (song.source == MusicSource.DEEZER && song.isPreview &&
            app.usagePolicyStore.isYouTubePolicyAccepted
        ) {
            runCatching { app.youtubeSourceRepository.findFullAudio(song) }
                .getOrNull()?.let { return it }
        }
        runCatching { app.streamResolver.resolve(song) }.getOrNull()?.let { return it }
        return null
    }
}
