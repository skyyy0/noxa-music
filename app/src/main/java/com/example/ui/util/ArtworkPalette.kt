package com.example.ui.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * **Tema dinamis** — pulls a dominant color out of the current artwork so the
 * player background can breathe with the album instead of staying flat black.
 *
 * The bitmap is decoded tiny (64 px) through the app's own Coil loader, then
 * the most frequent non-grey bucket wins. No extra palette library: a 64 px
 * downscale plus bucketing is cheaper and good enough for a background tint.
 * Every failure returns `null` and the player keeps its default theme.
 */
object ArtworkPalette {

    /** Samples this many pixels per side when measuring. */
    private const val SAMPLE_SIZE = 64

    /** Colours closer than this to grey are ignored (they make dull tints). */
    private const val MIN_SATURATION = 0.12f

    /** Colours darker than this are ignored (they disappear on a dark UI). */
    private const val MIN_LUMINANCE = 0.10f

    suspend fun dominantColorOf(
        url: String,
        loader: ImageLoader,
        context: android.content.Context
    ): Color? =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext null
            val bitmap = try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(SAMPLE_SIZE, SAMPLE_SIZE)
                    .allowHardware(false)
                    .build()
                val drawable = loader.execute(request).drawable ?: return@withContext null
                when (drawable) {
                    is BitmapDrawable -> drawable.bitmap
                    else -> {
                        val bmp = Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtMost(SAMPLE_SIZE),
                            drawable.intrinsicHeight.coerceAtMost(SAMPLE_SIZE),
                            Bitmap.Config.ARGB_8888
                        )
                        drawable.setBounds(0, 0, bmp.width, bmp.height)
                        drawable.draw(Canvas(bmp))
                        bmp
                    }
                }
            } catch (e: Exception) {
                return@withContext null
            }
            if (bitmap.width == 0 || bitmap.height == 0) return@withContext null
            pickDominant(bitmap)
        }

    /**
     * Bucketing: each pixel collapses to a 4-bit-per-channel key; the most
     * popular bucket that is neither grey nor too dark wins, then its bucket
     * centre is returned as a Compose [Color].
     */
    internal fun pickDominant(bitmap: Bitmap): Color? {
        val counts = HashMap<Int, Int>()
        val colors = HashMap<Int, Int>()
        val stepX = (bitmap.width / SAMPLE_SIZE + 1).coerceAtLeast(1)
        val stepY = (bitmap.height / SAMPLE_SIZE + 1).coerceAtLeast(1)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if ((pixel ushr 24) > 128) {
                    val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                    counts[key] = (counts[key] ?: 0) + 1
                    colors[key] = pixel
                }
                x += stepX
            }
            y += stepY
        }
        var bestKey = -1
        var bestCount = 0
        for ((key, count) in counts) {
            val pixel = colors.getValue(key)
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val luminance = (maxC + minC) / 2f
            val saturation = if (maxC == minC) 0f else (maxC - minC) / (1f - kotlin.math.abs(2f * luminance - 1f))
            if (saturation < MIN_SATURATION || luminance < MIN_LUMINANCE) continue
            if (count > bestCount) {
                bestCount = count
                bestKey = key
            }
        }
        if (bestKey < 0) return null
        val pixel = colors.getValue(bestKey)
        return Color(
            red = ((pixel shr 16) and 0xFF) / 255f,
            green = ((pixel shr 8) and 0xFF) / 255f,
            blue = (pixel and 0xFF) / 255f
        )
    }

    /**
     * Tints a base colour toward [dominant]: enough personality to feel alive
     * with the artwork, still dark enough to sit under player text.
     */
    fun playerBackgroundTint(dominant: Color?): Color {
        if (dominant == null) return androidx.compose.ui.graphics.Color(0xFF0A0A0B)
        return Color(
            red = dominant.red * 0.24f,
            green = dominant.green * 0.24f,
            blue = dominant.blue * 0.24f
        )
    }

    /** A mid-strength accent from the artwork, for progress bars and glows. */
    fun accentOf(dominant: Color?): Color? {
        if (dominant == null) return null
        return Color(
            red = (dominant.red * 0.65f + 0.35f).coerceAtMost(1f),
            green = (dominant.green * 0.65f + 0.35f).coerceAtMost(1f),
            blue = (dominant.blue * 0.65f + 0.35f).coerceAtMost(1f)
        )
    }
}
