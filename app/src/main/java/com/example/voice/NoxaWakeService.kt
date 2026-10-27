package com.example.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * **NOXA Wake (v3.0.0)** — layanan mikrofon yang tetap hidup walau NOXA
 * ditutup/di-background, mendengarkan kata bangun **"Hi NOXA" / "Hai NOXA"**.
 *
 * Alurnya:
 *  1. Mode [Mode.WAKE]: loop [SpeechRecognizer] (bahasa `id-ID`, partial
 *     results aktif) berjalan terus-menerus. Saat "hi noxa" terdeteksi →
 *     bunyi pendek + bubble orb gradien muter muncul di atas aplikasi lain,
 *     lalu masuk mode [Mode.COMMAND].
 *  2. Mode [Mode.COMMAND]: ucapan berikutnya adalah perintah
 *     ("putarkan dewa 19 aku milikmu"). Transcript dikirim ke [NoxaWakeBus],
 *     hasilnya tampil di bubble dan di notifikasi. Jeda 12 detik tanpa
 *     perintah → bubble hilang, kembali ke mode wake.
 *
 * Batasan yang diungkapkan jujur (dari riset platform Android 12–16):
 *  * **Layar menyala, NOXA di background** — jalur yang didukung. Layanan FGS
 *    tipe `microphone` boleh merekam, dan recognizer sistem umumnya tetap
 *    jalan selama aktivitas di atasnya hidup.
 *  * **Layar mati** — dicoba (CPU ditahan wake lock saat layar mati, maksimal
 *    4 jam per penahanan), tapi bergantung pada recognizer perangkat; banyak
 *    OEM menghentikannya. Bila gagal, layanan otomatis bangun lagi begitu
 *    layar menyala.
 *  * Bubble butuh izin `SYSTEM_ALERT_WINDOW`. Tanpa izin, perintah tetap
 *    dieksekusi dan hasilnya disampaikan lewat notifikasi.
 *
 * Izin `RECORD_AUDIO` diperiksa sebelum service start (UI Pengaturan yang
 * memintanya); kehilangan izin di tengah jalan menghentikan layanan dengan
 * jujur, bukan diam-diam gagal.
 */
class NoxaWakeService : Service() {

    private enum class Mode { WAKE, COMMAND }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var recognizer: SpeechRecognizer? = null
    private var mode: Mode = Mode.WAKE
    private var alive = true

    /** Sesi sekarang sudah mendengar wake word (untuk path final/timeout). */
    private var sessionWakeHeard = false

    /** Cancel yang kita sendiri yang memicu onError — jangan dihitung gagal. */
    private var expectCancelError = false

    private var consecutiveErrors = 0
    private var pendingRestart: Runnable? = null
    private var commandTimeout: Runnable? = null
    private var hideBubble: Runnable? = null

    /**
     * Ekor ucapan perintah tidak boleh membangunkan layanan lagi: setelah
     * dispatch, wake word diabaikan selama [WAKE_QUIET_MS] (v3.1.0 — mencegah
     * bubble langsung muncul kedua kali oleh gema/lanjutan kalimat yang sama).
     */
    private var wakeQuietUntil = 0L

    /** Izin overlay hilang — peringatan sudah dikirim, jangan spam notifikasi. */
    private var overlayWarned = false

    private var overlay: NoxaBubbleOverlay? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
            // Android 14+: FGS tipe mikrofon butuh RECORD_AUDIO tergrant.
            Log.e(TAG, "startForeground mikrofon gagal: ${e.message}")
            stopSelf()
            return
        }

        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Layar sudah mati saat service (re)start → tahan CPU sekarang juga.
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) acquireWakeLock()

        beginListening(Mode.WAKE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------- mode utama

    /** Siapkan (bila perlu) dan jalankan satu sesi [SpeechRecognizer]. */
    private fun beginListening(target: Mode) {
        if (!alive) return
        mode = target
        try {
            val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(recognitionListener)
                recognizer = it
            }
            sessionWakeHeard = false
            engine.startListening(buildIntent())
        } catch (e: SecurityException) {
            Log.e(TAG, "izin mikrofon hilang — NOXA Wake berhenti")
            stopSelf()
        } catch (e: Exception) {
            Log.w(TAG, "startListening gagal: ${e.message}")
            scheduleRestart(backoffAfterError(), target)
        }
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Partial results adalah nyawa deteksi wake: "hi noxa" harus terbaca
        // sebelum utterance berikutnya diucapkan.
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
    }

    /** Batalkan sesi berjalan lalu jalankan ulang [target] setelah [delayMs]. */
    private fun scheduleRestart(delayMs: Long, target: Mode) {
        if (!alive) return
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        val job = Runnable {
            pendingRestart = null
            cancelCurrentSession()
            beginListening(target)
        }
        pendingRestart = job
        mainHandler.postDelayed(job, delayMs)
    }

    private fun cancelCurrentSession() {
        expectCancelError = true
        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }
        // Kalau cancel ternyata tidak menghasilkan error, jangan biarkan
        // flag menelan error berikutnya yang asli.
        mainHandler.postDelayed({ expectCancelError = false }, 1_000)
    }

    private fun recreateRecognizer() {
        consecutiveErrors = 0
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    private fun backoffAfterError(): Long {
        consecutiveErrors = min(consecutiveErrors + 1, 5)
        return min(1_000L shl (consecutiveErrors - 1), 15_000L)
    }

    // ------------------------------------------------------- deteksi & alur

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            consecutiveErrors = 0
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            // Level suara → bar di dalam orb (hanya saat bubble tampil).
            if (overlay?.isShowing == true) overlay?.setLevel(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults) ?: return
            when (mode) {
                // Wake terdeteksi lebih awal → bubble & chime langsung muncul,
                // sesi TIDAK dibatalkan: "hi noxa putar lagu x" dalam satu
                // napas harus utuh sampai hasil akhir.
                Mode.WAKE ->
                    if (!sessionWakeHeard && WakeCommandParser.containsWake(text) &&
                        SystemClock.elapsedRealtime() >= wakeQuietUntil
                    ) {
                        onWakeHeard(fromPartial = true)
                    }
                // Mode perintah: transcript berjalan real-time di bubble.
                Mode.COMMAND -> overlay?.setCaption(text)
            }
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results).orEmpty()
            when (mode) {
                Mode.WAKE -> {
                    if (WakeCommandParser.containsWake(text) &&
                        SystemClock.elapsedRealtime() >= wakeQuietUntil
                    ) {
                        onWakeHeard(fromPartial = false)
                    } else {
                        // Tidak ada wake word (atau masih periode tenang) — sesi
                        // berakhir sendiri, dengarkan lagi.
                        scheduleRestart(150, Mode.WAKE)
                    }
                }

                Mode.COMMAND -> {
                    val command = WakeCommandParser.stripWake(text) ?: text
                    if (command.isBlank()) {
                        // Hanya "hi noxa" → minta perintahnya dengan sesi baru.
                        overlay?.setCaption("Mendengarkan perintah\u2026")
                        scheduleRestart(300, Mode.COMMAND)
                    } else {
                        dispatchCommand(command)
                    }
                }
            }
        }

        override fun onError(errorCode: Int) = handleRecognitionError(errorCode)

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Kata bangun terdengar: tampilkan bubble + bunyi, lalu tunggu perintah.
     * Dari partial → jangan ganggu sesi yang sedang berjalan; dari hasil
     * akhir → sesi sudah selesai, langsung buka sesi perintah.
     */
    private fun onWakeHeard(fromPartial: Boolean) {
        sessionWakeHeard = true
        mode = Mode.COMMAND
        beep()
        val bubble = ensureOverlay()
        if (bubble != null) {
            overlayWarned = false
            bubble.show()
            bubble.setCaption("Mendengarkan\u2026")
        } else {
            // Bubble tidak bisa tampil (izin overlay dicabut / belum diberikan).
            // Jangan diam — tanpa pesan ini fitur terlihat \"mati\" padahal
            // mikrofon tetap mendengar. Satu notifikasi + jalan ke Pengaturan.
            notifyOverlayMissing()
        }
        scheduleCommandTimeout()
        if (!fromPartial) scheduleRestart(300, Mode.COMMAND)
    }

    /** Eksekusi perintah; bubble menampilkan hasil, lalu kembali mendengarkan. */
    private fun dispatchCommand(text: String) {
        sessionWakeHeard = false
        // Periode tenang: kalimat yang baru saja dieksekusi (atau gema
        // mikrofon) tidak boleh langsung memicu \"Hi NOXA\" palsu.
        wakeQuietUntil = SystemClock.elapsedRealtime() + WAKE_QUIET_MS
        commandTimeout?.let { mainHandler.removeCallbacks(it); commandTimeout = null }

        overlay?.setCaption("Memproses\u2026")
        mode = Mode.WAKE
        scheduleRestart(400, Mode.WAKE)

        scope.launch {
            val message = runCatching { NoxaWakeBus.dispatch(text) }
                .getOrElse { e ->
                    Log.w(TAG, "perintah gagal: ${e.message}")
                    "Perintah belum bisa diproses \u2014 coba lagi."
                }
            withContext(Dispatchers.Main) {
                if (!alive) return@withContext
                val shown = message.ifBlank { text }
                overlay?.setCaption(shown)
                notifyStatus(shown)
                scheduleHideBubble(4_000)
            }
        }
    }

    private fun scheduleCommandTimeout() {
        commandTimeout?.let { mainHandler.removeCallbacks(it) }
        val job = Runnable {
            commandTimeout = null
            if (alive && mode == Mode.COMMAND) {
                mode = Mode.WAKE
                sessionWakeHeard = false
                overlay?.setCaption("Tidak ada perintah \u2014 kembali mendengarkan.")
                scheduleHideBubble(1_500)
                scheduleRestart(200, Mode.WAKE)
            }
        }
        commandTimeout = job
        mainHandler.postDelayed(job, COMMAND_TIMEOUT_MS)
    }

    private fun scheduleHideBubble(delayMs: Long) {
        hideBubble?.let { mainHandler.removeCallbacks(it) }
        val job = Runnable {
            hideBubble = null
            overlay?.hide()
        }
        hideBubble = job
        mainHandler.postDelayed(job, delayMs)
    }

    private fun handleRecognitionError(code: Int) {
        if (!alive) return
        if (expectCancelError) {
            expectCancelError = false
            return
        }
        when (code) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                Log.w(TAG, "RECORD_AUDIO dicabut — NOXA Wake berhenti.")
                stopSelf()
            }

            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // Hening di mode perintah = user belum bicara; tetap dengarkan
                // sampai timeout yang mengakhiri.
                scheduleRestart(if (mode == Mode.COMMAND) 400 else 150, mode)
            }

            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                if (consecutiveErrors + 1 >= RECREATE_AFTER) recreateRecognizer()
                scheduleRestart(backoffAfterError(), mode)
            }

            else -> {
                // Termasuk bahasa yang tidak didukung perangkat: coba lagi dengan
                // backoff, kalau terus gagal loop-nya memang akan melambat sendiri.
                scheduleRestart(backoffAfterError(), mode)
            }
        }
    }

    private fun firstResult(bundle: Bundle?): String? = bundle
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()

    private fun ensureOverlay(): NoxaBubbleOverlay? {
        if (!Settings.canDrawOverlays(this)) {
            // Log jujur supaya kasus \"terdeteksi tapi bubble hilang\" bisa
            // dibedakan dari bug tampilan di lapangan.
            Log.w(TAG, "izin SYSTEM_ALERT_WINDOW tidak ada — bubble dilewati")
            return null
        }
        if (overlay == null) overlay = NoxaBubbleOverlay(this)
        return overlay
    }

    /**
     * Izin overlay hilang di tengah jalan → beri tahu user sekali, dengan
     * intent langsung ke layar izin \"Tampilkan di atas aplikasi lain\".
     */
    private fun notifyOverlayMissing() {
        if (overlayWarned) return
        overlayWarned = true
        runCatching {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID, "NOXA Wake — Izin bubble",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    "Bubble \"Hi NOXA\" butuh izin \"Tampilkan di atas aplikasi lain\""
            }
            notificationManager.createNotificationChannel(channel)
            val openOverlaySettings = PendingIntent.getActivity(
                this, 2,
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationManager.notify(
                ALERT_NOTIF_ID,
                NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_noxa_wake)
                    .setContentTitle("Bubble NOXA tidak bisa tampil")
                    .setContentText(
                        "Izin \"Tampilkan di atas aplikasi lain\" dibutuhkan — " +
                            "ketuk untuk membukanya."
                    )
                    .setContentIntent(openOverlaySettings)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    /** Nada pendek "terdeteksi" — ToneGenerator, tanpa file audio resource. */
    private fun beep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 180)
            mainHandler.postDelayed({ runCatching { tone.release() } }, 600)
        }
    }

    // ------------------------------------------------------- layar & wake lock

    /**
     * Layar mati → CPU bisa tidur dan koneksi recognizer ikut mati; tahan
     * ringan selama layar gelap (maks 4 jam, diperbarui tiap layar mati).
     * Layar hidup → lepas, baterai tidak dibakar sia-sia.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> acquireWakeLock()
                Intent.ACTION_SCREEN_ON -> {
                    releaseWakeLock()
                    // Bangunkan sesi yang mungkin mati saat layar gelap.
                    if (alive) scheduleRestart(250, mode)
                }
            }
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val lock = wakeLock ?: pm
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "noxa:wake")
                .apply { setReferenceCounted(false) }
                .also { wakeLock = it }
            if (!lock.isHeld) lock.acquire(WAKE_LOCK_MAX_MS)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
    }

    // -------------------------------------------------------------- notifikasi

    private fun buildNotification(status: String? = null): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "NOXA Wake", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Bilang \u201cHi NOXA\u201d untuk memberi perintah suara"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, NoxaWakeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noxa_wake)
            .setContentTitle("NOXA Wake aktif")
            .setContentText(
                status
                    ?: "Bilang \u201cHi NOXA\u201d lalu sebutkan lagu \u2014 mis. \u201cputarkan Dewa 19 Aku Milikmu\u201d"
            )
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Berhenti", stop)
            .build()
    }

    private fun notifyStatus(text: String) {
        runCatching {
            notificationManager.notify(NOTIF_ID, buildNotification(text.take(120)))
        }
    }

    // -------------------------------------------------------------- lifecycle

    override fun onDestroy() {
        alive = false
        isRunning.value = false
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        commandTimeout?.let { mainHandler.removeCallbacks(it) }
        hideBubble?.let { mainHandler.removeCallbacks(it) }
        mainHandler.removeCallbacksAndMessages(null)
        scope.cancel()
        cancelCurrentSession()
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
        overlay?.hide()
        overlay = null
        runCatching { unregisterReceiver(screenReceiver) }
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NoxaWakeService"
        private const val NOTIF_ID = 4711
        private const val ALERT_NOTIF_ID = 4712
        private const val CHANNEL_ID = "noxa_wake"
        private const val ALERT_CHANNEL_ID = "noxa_wake_alert"
        private const val ACTION_STOP = "com.example.voice.STOP"

        /** Pasca-perintah, wake word diabaikan selama ini (mencegah gema). */
        private const val WAKE_QUIET_MS = 2_500L

        /** 12 detik "hi noxa" tanpa perintah → bubble tutup sendiri. */
        private const val COMMAND_TIMEOUT_MS = 12_000L

        /** 4 error berturut-turut → buang recognizer, bikin yang baru. */
        private const val RECREATE_AFTER = 4

        /** Batas tahan CPU saat layar mati (diperbarui tiap layar mati). */
        private const val WAKE_LOCK_MAX_MS = 4L * 60 * 60 * 1000

        /**
         * Dinamis untuk Compose (Pengaturan → Fitur Pemutar): apakah layanan
         * sedang hidup. `false` setelah service mati, apa pun penyebabnya.
         */
        val isRunning = MutableStateFlow(false)
    }
}
