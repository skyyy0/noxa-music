package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HeatmapStats
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaPurpleDeep
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.viewmodel.YearListeningUi
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * **NOXA Wrapped Tahunan** — the listener's own year, told story-style.
 *
 * Every number is the same on-device truth as the Profile heatmap
 * (`Room.play_records`, last 365 days): nothing is estimated, nothing leaves
 * the phone. Sections fade in one after another like a story, and an empty
 * year says so honestly instead of inventing a zero-hero.
 */
@Composable
fun WrappedScreen(
    yearListening: YearListeningUi,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = yearListening.stats

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NoxaPurpleDeep.copy(alpha = 0.45f), NoxaBlack, NoxaBlack)
                )
            )
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = NoxaTextPrimary
                    )
                }
                Text(
                    text = "NOXA Wrapped",
                    color = NoxaTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (stats == null || stats.totalPlays == 0) {
                EmptyState()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 40.dp)
                ) {
                    // ------------------------------------------------ hero
                    Section(index = 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "TAHUN MENDENGARMU",
                                color = NoxaLime,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "${stats.totalPlays}",
                                color = NoxaTextPrimary,
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 66.sp
                            )
                            Text(
                                text = "lagu diputar di NOXA",
                                color = NoxaTextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                HeroStat(
                                    value = "${stats.activeDays}",
                                    label = "hari mendengar",
                                    modifier = Modifier.weight(1f)
                                )
                                HeroStat(
                                    value = "${yearListening.distinctArtists}",
                                    label = "artis berbeda",
                                    modifier = Modifier.weight(1f)
                                )
                                HeroStat(
                                    value = "${stats.bestDayPlays}",
                                    label = "terbanyak sehari",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // ------------------------------------- artis jiwamu
                    yearListening.topArtists.firstOrNull()?.let { soulmate ->
                        Section(index = 1) {
                            StatCard(
                                icon = Icons.Default.EmojiEvents,
                                kicker = "ARTIS JIWAMU",
                                title = soulmate.name,
                                body = "Diputar ${soulmate.plays}× sepanjang setahun terakhir."
                            )
                        }
                    }

                    if (yearListening.topArtists.size > 1) {
                        Section(index = 2) {
                            RankCard(
                                kicker = "5 ARTIS PALING SERING",
                                rows = yearListening.topArtists.take(5).mapIndexed { i, taste ->
                                    Triple("#${i + 1}", taste.name, "${taste.plays}×")
                                }
                            )
                        }
                    }

                    // --------------------------------- lagu paling sering
                    if (yearListening.topSongs.isNotEmpty()) {
                        Section(index = 3) {
                            RankCard(
                                kicker = "LAGU PALING SERING",
                                icon = Icons.Default.MusicNote,
                                rows = yearListening.topSongs.take(5).mapIndexed { i, song ->
                                    Triple("#${i + 1}", song.title, "${song.plays}×")
                                }
                            )
                        }
                    }

                    // ------------------------------- kebiasaan mendengar
                    Section(index = 4) {
                        StatCard(
                            icon = Icons.Default.Schedule,
                            kicker = "KEBIASAAN MENDENGAR",
                            title = if (yearListening.busiestHour in 0..23) {
                                String.format(
                                    Locale.getDefault(),
                                    "Jam %02d.00",
                                    yearListening.busiestHour
                                )
                            } else {
                                "Sepanjang hari"
                            },
                            body = buildString {
                                append("Jam paling rajin kamu dengar musik. ")
                                append("Streak terpanjang: ${stats.longestStreak} hari berturut-turut")
                                if (stats.currentStreak > 0) {
                                    append(", dan sekarang kamu di streak ${stats.currentStreak} hari")
                                }
                                append(".")
                            }
                        )
                    }

                    // ------------------------------------------ heatmap
                    Section(index = 5) {
                        HeatmapSection(stats = stats)
                    }

                    // ---------------------------------------- penutup
                    Section(index = 6) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = NoxaLime,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Semua angka ini berasal dari HP-mu sendiri.\n" +
                                    "Tidak ada yang dikirim ke mana pun.",
                                color = NoxaTextTertiary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------- primitives

/**
 * Story-style entrance: section [index] fades in and rises a little, each
 * later section a beat after the one before it.
 */
@Composable
private fun Section(index: Int, content: @Composable () -> Unit) {
    var shown by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay(80L * index)
        shown = true
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (shown) 1f else 0f)
    ) {
        content()
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, NoxaBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = NoxaLime, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Text(label, color = NoxaTextTertiary, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    kicker: String,
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaBorder, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Icon(icon, contentDescription = null, tint = NoxaLime, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = kicker,
            color = NoxaTextTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, color = NoxaTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = body, color = NoxaTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun RankCard(
    kicker: String,
    rows: List<Triple<String, String, String>>,
    icon: ImageVector = Icons.Default.Star
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaBorder, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = NoxaLime, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = kicker,
                color = NoxaTextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        rows.forEach { (rank, title, meta) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rank,
                    color = NoxaLime,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(32.dp)
                )
                Text(
                    text = title,
                    color = NoxaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = meta, color = NoxaTextTertiary, fontSize = 12.sp)
            }
        }
    }
}

/** Compact 365-day heatmap: weekday rows × week columns, lime intensity. */
@Composable
private fun HeatmapSection(stats: HeatmapStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = NoxaLime,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "PETA PANAS 365 HARI",
                color = NoxaTextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        val cell = 9.dp
        val gap = 2.dp
        // Consecutive cells: cell i sits at weekday (firstWeekday + i) % 7 and
        // week column floor((firstWeekday + i) / 7) — rows stay aligned.
        val firstWeekday = stats.cells.firstOrNull()?.weekday ?: 0
        val columns = (stats.cells.size + firstWeekday + 6) / 7
        Column {
            for (weekday in 0..6) {
                Row(modifier = Modifier.padding(bottom = gap)) {
                    repeat(columns) { col ->
                        val idx = col * 7 + weekday - firstWeekday
                        val plays = stats.cells.getOrNull(idx)?.plays ?: 0
                        Box(
                            modifier = Modifier
                                .padding(end = gap)
                                .size(cell)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (idx !in stats.cells.indices || plays <= 0) {
                                        Color.White.copy(alpha = 0.05f)
                                    } else {
                                        NoxaLime.copy(alpha = intensity(plays, stats.bestDayPlays))
                                    }
                                )
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "${stats.activeDays} hari aktif • rekor ${stats.longestStreak} hari " +
                "berturut-turut",
            color = NoxaTextSecondary,
            fontSize = 12.sp
        )
    }
}

/** 0.35 → 1.0 lime alpha across a day's play count. */
private fun intensity(plays: Int, best: Int): Float {
    if (plays <= 0) return 0f
    val max = best.coerceAtLeast(1)
    return (0.35f + 0.65f * (plays.toFloat() / max)).coerceIn(0.35f, 1f)
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = NoxaTextTertiary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Wrapped-mu belum terbentuk",
            color = NoxaTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Putar lagu dulu di NOXA — setelah cukup data, tahun mendengarmu " +
                "akan muncul di sini lengkap dengan peta panas dan lagu teratas.",
            color = NoxaTextTertiary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}
