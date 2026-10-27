package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.media.SpectrumCapture
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaPurple
import com.example.ui.theme.NoxaSurfaceSecondary
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Visualizer styles — cycled from the player with one compact control. */
enum class SpectrumStyle(val label: String) {
    BARS("Bar"),
    WAVE("Gelombang"),
    RING("Cincin")
}

/**
 * The full-player spectrum visualizer.
 *
 * Reads [SpectrumCapture]'s live FFT, smooths it at frame rate (exponential
 * approach, so bars glide instead of jittering) and draws one of three
 * styles in NOXA's lime→purple identity. With no signal — capture off, no
 * RECORD_AUDIO permission, or a device without the effect — the levels
 * simply rest at zero: a flat, calm baseline instead of fake movement.
 */
@Composable
fun SpectrumVisualizer(
    style: SpectrumStyle,
    modifier: Modifier = Modifier,
    height: Dp = 76.dp
) {
    val bandCount = 44
    var levels by remember { mutableStateOf(FloatArray(bandCount)) }

    // Frame-rate smoothing loop: pull the latest FFT, ease toward it.
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val data = SpectrumCapture.fft.value
            val target = computeBands(data, bandCount)
            val current = levels
            val next = FloatArray(bandCount) { i ->
                current[i] + (target[i] - current[i]) * 0.34f
            }
            levels = next
        }
    }

    // A slow shimmer keeps the idle line alive without faking signal.
    val idle = rememberInfiniteTransition(label = "spectrum_idle")
    val shimmer by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "spectrum_shimmer"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height
        val gradient = Brush.horizontalGradient(listOf(NoxaLime, NoxaPurple))

        when (style) {
            SpectrumStyle.BARS -> {
                val gap = 3.dp.toPx()
                val barWidth = (w - gap * (bandCount + 1)) / bandCount
                val radius = min(barWidth / 2f, 5.dp.toPx())
                levels.forEachIndexed { i, level ->
                    val boosted = (level * 1.35f).coerceAtMost(1f)
                    val barHeight = max(2.dp.toPx(), boosted * h * 0.92f)
                    val x = gap + i * (barWidth + gap)
                    drawRoundRect(
                        brush = gradient,
                        topLeft = Offset(x, h - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
                    )
                }
            }

            SpectrumStyle.WAVE -> {
                val step = w / (bandCount - 1)
                fun wavePath(mirror: Boolean): Path {
                    val path = Path()
                    levels.forEachIndexed { i, level ->
                        val x = i * step
                        val amp = (level * 0.9f + 0.04f) * h * 0.42f
                        val y = if (mirror) h / 2f + amp else h / 2f - amp
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    return path
                }
                // Idle baseline keeps a whisper of motion when silent.
                drawLine(
                    color = NoxaSurfaceSecondary,
                    start = Offset(0f, h / 2f),
                    end = Offset(w, h / 2f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawPath(
                    wavePath(mirror = false),
                    brush = gradient,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    wavePath(mirror = true),
                    brush = gradient,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            SpectrumStyle.RING -> {
                val center = Offset(w / 2f, h / 2f)
                val baseRadius = min(w, h) * 0.22f
                val maxOut = min(w, h) * 0.24f
                // Faint guide circle so the ring reads even at rest.
                drawCircle(
                    color = NoxaSurfaceSecondary,
                    radius = baseRadius,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                levels.forEachIndexed { i, level ->
                    val angle = Math.PI * 2 * i / bandCount - Math.PI / 2
                    val boosted = (level * 1.5f).coerceAtMost(1f)
                    val length = 3.dp.toPx() + boosted * maxOut
                    val sx = center.x + (Math.cos(angle) * baseRadius).toFloat()
                    val sy = center.y + (Math.sin(angle) * baseRadius).toFloat()
                    val ex = center.x + (Math.cos(angle) * (baseRadius + length)).toFloat()
                    val ey = center.y + (Math.sin(angle) * (baseRadius + length)).toFloat()
                    drawLine(
                        brush = gradient,
                        start = Offset(sx, sy),
                        end = Offset(ex, ey),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * Maps raw FFT bytes onto [bands] smoothed magnitudes with logarithmic
 * frequency spacing (bass on the left, treble on the right — how a spectrum
 * is supposed to read). Returns zeros for empty input.
 */
internal fun computeBands(fft: ByteArray, bands: Int): FloatArray {
    if (fft.size < 4) return FloatArray(bands)
    val bins = fft.size / 2
    val maxBin = min(bins - 1, 96)
    val out = FloatArray(bands)
    for (b in 0 until bands) {
        // Linear spacing across the musically useful low/mid bins.
        val start = 1 + ((maxBin - 1) * b) / bands
        val end = max(start + 1, 1 + ((maxBin - 1) * (b + 1)) / bands)
        var peak = 0f
        for (k in start until min(end, bins)) {
            val re = fft[2 * k].toInt()
            val im = fft[2 * k + 1].toInt()
            val mag = sqrt((re * re + im * im).toDouble()) / 128.0
            if (mag > peak) peak = mag.toFloat()
        }
        // Gentle log scaling: quiet music still moves a little.
        out[b] = (kotlin.math.ln(1f + peak * 6f) / kotlin.math.ln(7f)).coerceIn(0f, 1f)
    }
    return out
}
