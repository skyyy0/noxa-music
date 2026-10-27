package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PerfProfile
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import kotlinx.coroutines.delay

/**
 * **Sistem Pintar — layar pemindaian perangkat.**
 *
 * Shown exactly once, on the first launch after install: NOXA measures RAM,
 * CPU cores and the chipset, then announces the profile it will run with
 * (Hemat / Seimbang / Ngebut). The staggered check-in rows are real state —
 * each row lights up as that probe completes, and the app continues the
 * moment [isScanning] flips to false (the view model drives both).
 */
@Composable
fun DeviceScanScreen(
    profile: PerfProfile,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    // Staggered checklist: which probe rows have "completed" so far.
    var revealed by remember { mutableIntStateOf(0) }
    LaunchedEffect(isScanning) {
        if (!isScanning) {
            // Everything checks in quickly once the scan has finished.
            while (revealed < SCAN_ROWS) {
                delay(90)
                revealed++
            }
        } else {
            while (revealed < SCAN_ROWS) {
                delay(420)
                revealed++
            }
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (isScanning) 1f else revealed / SCAN_ROWS.toFloat(),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "scan_progress"
    )

    // A slow-spinning ring around the chip glyph — the "thinking" tell.
    val infinite = rememberInfiniteTransition(label = "scan_ring")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_rotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Chip glyph with its rotating ring.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .rotate(if (isScanning) rotation else 0f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(NoxaLime.copy(alpha = 0.08f))
                        .align(Alignment.Center)
                )
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = if (isScanning) "Sistem Pintar sedang bekerja..." else "Konfigurasi siap!",
                color = NoxaTextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isScanning) {
                    "NOXA mendeteksi perangkatmu agar performanya pas —\nringan di HP biasa, ngebut di HP kencang."
                } else {
                    "Profil performa disimpan. Selamat mendengar!"
                },
                color = NoxaTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Probe checklist.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NoxaSurfaceElevated)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ScanRow(
                    label = "Memori (RAM)",
                    value = "${profile.totalMemGb} GB",
                    done = revealed >= 1
                )
                ScanRow(
                    label = "Prosesor",
                    value = "${profile.cores} inti",
                    done = revealed >= 2
                )
                ChipsetRow(
                    chipName = profile.chipName,
                    done = revealed >= 3
                )
                ScanRow(
                    label = "Sistem",
                    value = "Android ${profile.sdkInt}",
                    done = revealed >= 4
                )
                ScanRow(
                    label = "Konfigurasi",
                    value = profile.tierLabel,
                    done = revealed >= 5,
                    highlight = true
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Thin progress line under the panel.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(NoxaSurfaceElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(NoxaLime)
                )
            }
        }
    }
}

@Composable
private fun ScanRow(
    label: String,
    value: String,
    done: Boolean,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (done) NoxaTextPrimary else NoxaTextTertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (done) value else "· · ·",
                color = if (done) {
                    if (highlight) NoxaLime else NoxaTextSecondary
                } else {
                    NoxaTextTertiary
                },
                fontSize = 13.sp,
                fontWeight = if (highlight && done) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (done) NoxaLime.copy(alpha = 0.18f)
                        else NoxaSurfaceElevated
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selesai",
                        tint = NoxaLime,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/** Chipsets report a hardware codename; keep it lowercase-neutral. */
@Composable
private fun ChipsetRow(chipName: String, done: Boolean) {
    ScanRow(
        label = "Chipset",
        value = chipName.ifBlank { "unknown" },
        done = done
    )
}

private const val SCAN_ROWS = 5
