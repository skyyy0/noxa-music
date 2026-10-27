package com.example.media

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelMilliBels: Int,
    val maxLevelMilliBels: Int,
    val currentLevelMilliBels: Int
)

data class EqualizerState(
    val isEnabled: Boolean = true,
    val currentPreset: String = "Normal",
    val presets: List<String> = listOf("Flat", "Bass Boost", "Vocal Booster", "Electronic", "Rock", "Pop", "Classical"),
    val bands: List<EqualizerBand> = emptyList(),
    /** 0..1000, mirroring the platform BassBoost scale. */
    val bassBoostStrength: Int = 0,
    /** 0..1000, mirroring the platform Virtualizer scale. */
    val virtualizerStrength: Int = 0,
    val supportsBassBoost: Boolean = false,
    val supportsVirtualizer: Boolean = false
)

class EqualizerManager {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    var currentState: EqualizerState = EqualizerState()
        private set

    fun init(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()

            val eq = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            equalizer = eq

            // BassBoost and Virtualizer are separate platform effects and are not
            // available on every device, so each is probed independently.
            val bb = try {
                BassBoost(0, audioSessionId).apply { enabled = true }
            } catch (e: Exception) {
                Log.w("EqualizerManager", "BassBoost unavailable: ${e.message}")
                null
            }
            bassBoost = bb

            val vc = try {
                Virtualizer(0, audioSessionId).apply { enabled = true }
            } catch (e: Exception) {
                Log.w("EqualizerManager", "Virtualizer unavailable: ${e.message}")
                null
            }
            virtualizer = vc

            // `getStrengthSupported()` is the platform's own answer for whether the
            // effect can do anything on this device; a constructor succeeding is
            // not enough.
            val bassSupported = bb != null && runCatching { bb.getStrengthSupported() }.getOrDefault(false)
            val virtualSupported = vc != null && runCatching { vc.getStrengthSupported() }.getOrDefault(false)

            currentState = currentState.copy(
                supportsBassBoost = bassSupported,
                supportsVirtualizer = virtualSupported,
                bassBoostStrength = if (bassSupported) {
                    runCatching { bb!!.getRoundedStrength().toInt() }.getOrDefault(0)
                } else {
                    0
                },
                virtualizerStrength = if (virtualSupported) {
                    runCatching { vc!!.getRoundedStrength().toInt() }.getOrDefault(0)
                } else {
                    0
                }
            )
            readCurrentBands()
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Failed to initialize Equalizer with session ID: $audioSessionId", e)
        }
    }

    fun setBassBoost(strength: Int) {
        val bb = bassBoost ?: return
        val clamped = strength.coerceIn(0, 1000)
        try {
            bb.setStrength(clamped.toShort())
            currentState = currentState.copy(bassBoostStrength = clamped)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting bass boost", e)
        }
    }

    fun setVirtualizer(strength: Int) {
        val vc = virtualizer ?: return
        val clamped = strength.coerceIn(0, 1000)
        try {
            vc.setStrength(clamped.toShort())
            currentState = currentState.copy(virtualizerStrength = clamped)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting virtualizer", e)
        }
    }

    private fun readCurrentBands() {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()

            val bandList = mutableListOf<EqualizerBand>()
            for (i in 0 until numBands) {
                val centerFreq = eq.getCenterFreq(i.toShort()) / 1000 // Convert mHz to Hz
                val level = eq.getBandLevel(i.toShort()).toInt()
                bandList.add(
                    EqualizerBand(
                        index = i,
                        centerFreqHz = centerFreq,
                        minLevelMilliBels = minLevel,
                        maxLevelMilliBels = maxLevel,
                        currentLevelMilliBels = level
                    )
                )
            }
            currentState = currentState.copy(bands = bandList)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error reading bands", e)
        }
    }

    fun setBandLevel(bandIndex: Int, levelMilliBels: Int) {
        val eq = equalizer ?: return
        try {
            eq.setBandLevel(bandIndex.toShort(), levelMilliBels.toShort())
            val updatedBands = currentState.bands.map {
                if (it.index == bandIndex) it.copy(currentLevelMilliBels = levelMilliBels) else it
            }
            currentState = currentState.copy(bands = updatedBands, currentPreset = "Custom")
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting band level", e)
        }
    }

    fun applyPreset(presetName: String) {
        val eq = equalizer ?: return
        try {
            when (presetName) {
                "Flat" -> {
                    for (i in 0 until (eq.numberOfBands)) {
                        eq.setBandLevel(i.toShort(), 0)
                    }
                }
                "Bass Boost" -> {
                    val range = eq.bandLevelRange
                    val max = range[1].toInt()
                    if (eq.numberOfBands > 0) eq.setBandLevel(0, (max * 0.8).toInt().toShort())
                    if (eq.numberOfBands > 1) eq.setBandLevel(1, (max * 0.4).toInt().toShort())
                }
                "Vocal Booster" -> {
                    val range = eq.bandLevelRange
                    val max = range[1].toInt()
                    if (eq.numberOfBands > 2) eq.setBandLevel(2, (max * 0.6).toInt().toShort())
                    if (eq.numberOfBands > 3) eq.setBandLevel(3, (max * 0.5).toInt().toShort())
                }
                "Electronic" -> {
                    val range = eq.bandLevelRange
                    val max = range[1].toInt()
                    if (eq.numberOfBands > 0) eq.setBandLevel(0, (max * 0.6).toInt().toShort())
                    if (eq.numberOfBands > 4) eq.setBandLevel(4, (max * 0.7).toInt().toShort())
                }
                "Rock" -> {
                    val range = eq.bandLevelRange
                    val max = range[1].toInt()
                    if (eq.numberOfBands > 0) eq.setBandLevel(0, (max * 0.7).toInt().toShort())
                    if (eq.numberOfBands > 2) eq.setBandLevel(2, (-max * 0.2).toInt().toShort())
                    if (eq.numberOfBands > 4) eq.setBandLevel(4, (max * 0.8).toInt().toShort())
                }
                else -> {
                    for (i in 0 until (eq.numberOfBands)) {
                        eq.setBandLevel(i.toShort(), 0)
                    }
                }
            }
            readCurrentBands()
            currentState = currentState.copy(currentPreset = presetName)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error applying preset: $presetName", e)
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            currentState = currentState.copy(isEnabled = enabled)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting enabled state", e)
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            equalizer = null
            bassBoost = null
            virtualizer = null
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error releasing equalizer", e)
        }
    }
}
