package com.example.data.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import kotlin.math.roundToInt

/**
 * **Sistem Pintar NOXA** — a one-time device profile taken on first launch.
 *
 * The app measures the phone once (total RAM, CPU cores, chipset/SoC, Android
 * version) and picks a performance tier. Every tier knob in the app — how many
 * upcoming streams get warmed, the preload window before a track ends, the
 * crossfade availability, the artwork crossfade length — reads from the tier
 * instead of guessing per call. A 1 GB phone and a 12 GB phone therefore both
 * run the configuration they can actually afford, with no runtime branching
 * soup scattered through the codebase.
 *
 * [from] is pure so the tier rules are unit-testable without a device.
 */
data class PerfProfile(
    val tier: Tier,
    val totalMemBytes: Long,
    val cores: Int,
    val chipName: String,
    val sdkInt: Int
) {
    enum class Tier { HEMAT, SEIMBANG, NGEBUT }

    /** How many upcoming tracks get their full audio warmed ahead of time. */
    val warmTrackLimit: Int
        get() = when (tier) {
            Tier.HEMAT -> 1
            Tier.SEIMBANG -> 2
            Tier.NGEBUT -> 4
        }

    /**
     * How far before the end of the current track the next one's stream is
     * resolved in the background (the seamless-transition window).
     */
    val preloadWindowMs: Long
        get() = when (tier) {
            Tier.HEMAT -> 30_000L
            Tier.SEIMBANG -> 45_000L
            Tier.NGEBUT -> 60_000L
        }

    /** Crossfade needs a little spare headroom; the weakest phones skip it. */
    val crossfadeAllowed: Boolean
        get() = tier != Tier.HEMAT

    /** Artwork fade length — heavier devices get the slower, softer fade. */
    val artworkCrossfadeMs: Int
        get() = when (tier) {
            Tier.HEMAT -> 120
            Tier.SEIMBANG -> 180
            Tier.NGEBUT -> 240
        }

    /** Human label for the settings card. */
    val tierLabel: String
        get() = when (tier) {
            Tier.HEMAT -> "Hemat"
            Tier.SEIMBANG -> "Seimbang"
            Tier.NGEBUT -> "Ngebut"
        }

    val totalMemGb: Double
        get() = (totalMemBytes / 1_073_741_824.0 * 10).roundToInt() / 10.0

    companion object {
        /** At or below this RAM (or the OS low-RAM flag) → HEMAT. */
        const val HEMAT_MEM_BYTES = 2L * 1024 * 1024 * 1024

        /** At or below this RAM → SEIMBANG; above → NGEBUT. */
        const val SEIMBANG_MEM_BYTES = 6L * 1024 * 1024 * 1024

        /** Pure tier rule: RAM first, core count as the tiebreaker. */
        fun from(
            totalMemBytes: Long,
            cores: Int,
            isLowRamDevice: Boolean,
            sdkInt: Int = Build.VERSION.SDK_INT,
            chipName: String = ""
        ): PerfProfile {
            val safeCores = cores.coerceAtLeast(1)
            val tier = when {
                isLowRamDevice || totalMemBytes in 1..HEMAT_MEM_BYTES ->
                    if (safeCores <= 4) Tier.HEMAT else Tier.SEIMBANG

                totalMemBytes in (HEMAT_MEM_BYTES + 1)..SEIMBANG_MEM_BYTES -> Tier.SEIMBANG
                else -> Tier.NGEBUT
            }
            return PerfProfile(
                tier = tier,
                totalMemBytes = totalMemBytes.coerceAtLeast(0L),
                cores = safeCores,
                chipName = chipName,
                sdkInt = sdkInt
            )
        }
    }
}

/**
 * Persists the [PerfProfile] taken on first launch and answers
 * "has the scan run yet?". Kept in plain SharedPreferences so the value is
 * readable before any Compose state exists (the app shell needs it on the very
 * first frame).
 */
class PerfProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** True once a profile has been measured and persisted. */
    val isReady: Boolean get() = prefs.getBoolean(KEY_READY, false)

    fun load(): PerfProfile? {
        if (!isReady) return null
        return PerfProfile(
            tier = runCatching {
                PerfProfile.Tier.valueOf(prefs.getString(KEY_TIER, PerfProfile.Tier.SEIMBANG.name)!!)
            }.getOrDefault(PerfProfile.Tier.SEIMBANG),
            totalMemBytes = prefs.getLong(KEY_MEM, 0L),
            cores = prefs.getInt(KEY_CORES, 4),
            chipName = prefs.getString(KEY_CHIP, "").orEmpty(),
            sdkInt = prefs.getInt(KEY_SDK, Build.VERSION.SDK_INT)
        )
    }

    fun save(profile: PerfProfile) {
        prefs.edit()
            .putBoolean(KEY_READY, true)
            .putString(KEY_TIER, profile.tier.name)
            .putLong(KEY_MEM, profile.totalMemBytes)
            .putInt(KEY_CORES, profile.cores)
            .putString(KEY_CHIP, profile.chipName)
            .putInt(KEY_SDK, profile.sdkInt)
            .apply()
    }

    companion object {
        private const val NAME = "noxa_perf_profile"
        private const val KEY_READY = "ready"
        private const val KEY_TIER = "tier"
        private const val KEY_MEM = "total_mem"
        private const val KEY_CORES = "cores"
        private const val KEY_CHIP = "chip"
        private const val KEY_SDK = "sdk"

        /**
         * Measures the device. Every field is best-effort: a failed read yields
         * a neutral default instead of throwing — the scan must never be the
         * thing that stops the app from opening.
         */
        fun measure(context: Context): PerfProfile {
            val memInfo = ActivityManager.MemoryInfo()
            var totalMem = 0L
            var lowRam = false
            try {
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                    .getMemoryInfo(memInfo)
                totalMem = memInfo.totalMem
                lowRam = memInfo.lowMemory && memInfo.totalMem <= 2L * 1024 * 1024 * 1024
            } catch (e: Exception) {
                Log.w(TAG, "Memory probe failed: ${e.message}")
            }
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            // SoC name: SOC_MODEL on modern devices, hardware codename otherwise.
            val chip = when {
                Build.VERSION.SDK_INT >= 31 && !Build.SOC_MODEL.isNullOrBlank() -> Build.SOC_MODEL
                !Build.HARDWARE.isNullOrBlank() -> Build.HARDWARE
                else -> "unknown"
            }
            return PerfProfile.from(
                totalMemBytes = totalMem,
                cores = cores,
                isLowRamDevice = lowRam,
                chipName = chip
            )
        }

        private const val TAG = "PerfProfile"
    }
}
