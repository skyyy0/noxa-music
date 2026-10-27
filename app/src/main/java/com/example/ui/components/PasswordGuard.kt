package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaError
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaPurple
import com.example.ui.theme.NoxaSuccess
import com.example.ui.theme.NoxaSurfaceTertiary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary

/** How strong a typed password is (0 = empty, 4 = excellent). */
data class PasswordStrength(
    val score: Int,
    val label: String,
    val color: Color
) {
    companion object {
        val EMPTY = PasswordStrength(0, "", Color.Transparent)
    }
}

/**
 * Classic length + character-class heuristic, presented honestly as a hint
 * (never as a guarantee): weak when short or one-class, strong at 12+
 * characters mixing upper, lower, digits and symbols.
 */
fun assessPasswordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) return PasswordStrength.EMPTY
    if (password.length < 6) return PasswordStrength(1, "Lemah", NoxaError)

    val classes = listOf(
        password.any { it.isLowerCase() },
        password.any { it.isUpperCase() },
        password.any { it.isDigit() },
        password.any { !it.isLetterOrDigit() }
    ).count { it }

    val score = when {
        password.length >= 12 && classes >= 3 -> 4
        password.length >= 10 && classes >= 2 -> 3
        password.length >= 8 && classes >= 2 -> 2
        else -> 1
    }
    return when (score) {
        4 -> PasswordStrength(4, "Sangat Kuat", NoxaSuccess)
        3 -> PasswordStrength(3, "Kuat", NoxaLime)
        2 -> PasswordStrength(2, "Sedang", Color(0xFFFFB340))
        else -> PasswordStrength(1, "Lemah", NoxaError)
    }
}

/**
 * The segmented strength bar shown while a password is being typed:
 * four segments that light up from red → orange → lime → green with the
 * level's name beside them.
 */
@Composable
fun PasswordStrengthBar(
    password: String,
    modifier: Modifier = Modifier
) {
    val strength = assessPasswordStrength(password)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(4) { index ->
                val lit = index < strength.score
                val target = if (lit) strength.color else NoxaSurfaceTertiary
                val animated by animateFloatAsState(
                    targetValue = if (lit) 1f else 0f,
                    animationSpec = tween(280),
                    label = "strength_segment"
                )
                val segmentColor = if (lit) {
                    target.copy(alpha = 0.35f + 0.65f * animated)
                } else {
                    NoxaSurfaceTertiary
                }
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(segmentColor)
                )
            }
        }
        if (strength.label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Kekuatan password: ",
                    color = NoxaTextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = strength.label,
                    color = strength.color,
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }
}

/**
 * **NOXA Bot** — the little robot that covers its eyes while you type your
 * password, so the mascot "promises" not to peek. Hands slide up over the
 * eyes as soon as the field is focused with content, and drop back down
 * (eyes reappearing) when you stop.
 */
@Composable
fun PasswordGuardRobot(
    covering: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp
) {
    val progress by animateFloatAsState(
        targetValue = if (covering) 1f else 0f,
        animationSpec = tween(320),
        label = "robot_cover"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Antenna.
        drawLine(
            color = NoxaTextSecondary,
            start = Offset(w * 0.50f, h * 0.16f),
            end = Offset(w * 0.50f, h * 0.07f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = NoxaLime,
            radius = w * 0.045f * (0.8f + 0.2f * progress),
            center = Offset(w * 0.50f, h * 0.05f)
        )

        // Head plate with a soft metal gradient.
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(NoxaSurfaceTertiary, NoxaSurfaceTertiary.copy(alpha = 0.75f))
            ),
            topLeft = Offset(w * 0.12f, h * 0.16f),
            size = Size(w * 0.76f, h * 0.70f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.20f, h * 0.20f)
        )
        drawRoundRect(
            color = NoxaBorder,
            topLeft = Offset(w * 0.12f, h * 0.16f),
            size = Size(w * 0.76f, h * 0.70f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.20f, h * 0.20f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.03f)
        )

        // Eyes: round while open, squashed to happy closed lines as the
        // hands rise (the robot refuses to look at your password).
        val eyeScale = 1f - 0.75f * progress
        listOf(0.365f, 0.635f).forEach { cx ->
            drawOval(
                color = NoxaLime,
                topLeft = Offset(w * (cx - 0.075f), h * (0.42f - 0.06f * eyeScale)),
                size = Size(w * 0.15f, h * 0.12f * eyeScale + h * 0.02f)
            )
        }

        // Smile.
        drawLine(
            color = NoxaTextSecondary,
            start = Offset(w * 0.42f, h * 0.65f),
            end = Offset(w * 0.58f, h * 0.65f),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round
        )

        // Hands: slide from resting at the sides up over the eyes.
        val handY = 0.78f - 0.36f * progress
        val handColor = NoxaPurple
        // Left hand.
        drawRoundRect(
            color = handColor,
            topLeft = Offset(w * (0.06f + 0.06f * progress), h * handY),
            size = Size(w * 0.36f, h * 0.15f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.07f, h * 0.07f)
        )
        // Right hand.
        drawRoundRect(
            color = handColor,
            topLeft = Offset(w * (0.58f - 0.06f * progress), h * handY),
            size = Size(w * 0.36f, h * 0.15f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.07f, h * 0.07f)
        )
        // Fingers hint.
        if (progress > 0.4f) {
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(w * 0.18f, h * (handY + 0.075f)),
                end = Offset(w * 0.34f, h * (handY + 0.075f)),
                strokeWidth = w * 0.02f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(w * 0.66f, h * (handY + 0.075f)),
                end = Offset(w * 0.82f, h * (handY + 0.075f)),
                strokeWidth = w * 0.02f,
                cap = StrokeCap.Round
            )
        }
    }
}
