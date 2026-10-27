package com.example.data.model

/** One timed lyric line. [timeMs] is when the line starts, on the track timeline. */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

/**
 * Lyrics for a track.
 *
 * [lines] is empty when the provider only had unsynchronised text, in which case
 * [plainText] still holds the whole song so the UI can show something useful.
 */
data class TrackLyrics(
    val lines: List<LyricLine> = emptyList(),
    val plainText: String? = null,
    val source: String = "LRCLIB"
) {
    val isSynced: Boolean get() = lines.isNotEmpty()

    /** Index of the line that should be highlighted at [positionMs], or -1. */
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }
}
