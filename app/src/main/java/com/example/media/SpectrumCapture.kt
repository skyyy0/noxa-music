package com.example.media

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live FFT capture for the player's spectrum visualizer.
 *
 * Android treats [Visualizer] as audio *monitoring*, so it only works with
 * `RECORD_AUDIO` granted (even though NOXA never records anything) and only
 * when the audio session actually plays — the same session [EqualizerManager]
 * binds to. Every failure mode (no permission, dead session, a device that
 * simply lacks the effect) is swallowed into "no data": the visualizer then
 * shows a calm flat line instead of crashing or lying about signal it does
 * not have.
 */
object SpectrumCapture {

    private const val TAG = "SpectrumCapture"

    private var visualizer: Visualizer? = null
    private var boundSessionId: Int = -1

    /** Raw FFT bytes (interleaved real/imag), emptied when capture stops. */
    private val _fft = MutableStateFlow(ByteArray(0))
    val fft: StateFlow<ByteArray> = _fft

    /**
     * Binds capture to [audioSessionId]. Returns false when the platform
     * refuses (missing permission, invalid session, unsupported device) —
     * the caller surfaces that honestly.
     */
    @Synchronized
    fun start(audioSessionId: Int): Boolean {
        if (audioSessionId <= 0) return false
        if (boundSessionId == audioSessionId && visualizer != null) return true
        stop()
        return try {
            val viz = Visualizer(audioSessionId)
            viz.enabled = false
            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) = Unit

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft != null) _fft.value = fft.copyOf()
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                false, /* waveform */
                true   /* fft */
            )
            viz.enabled = true
            visualizer = viz
            boundSessionId = audioSessionId
            true
        } catch (e: Exception) {
            // Expected without RECORD_AUDIO or on devices without the effect.
            Log.w(TAG, "Visualizer unavailable: ${e.message}")
            _fft.value = ByteArray(0)
            false
        }
    }

    @Synchronized
    fun stop() {
        try {
            visualizer?.let { viz ->
                viz.enabled = false
                viz.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer release failed: ${e.message}")
        }
        visualizer = null
        boundSessionId = -1
        _fft.value = ByteArray(0)
    }
}
