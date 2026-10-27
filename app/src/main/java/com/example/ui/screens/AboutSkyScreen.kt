package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Song
import com.example.media.PlayerUiState
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary

@Composable
fun AboutSkyScreen(
    playerState: PlayerUiState,
    onPlaySong: (Song) -> Unit,
    onTogglePlayPause: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current

    // Specific pinned track required by user
    val pinnedAudioTrack = remember {
        Song(
            id = "about_sky_dj_haning",
            title = "DJ Haning - Mejikuhibiniu X Kasih Aba Aba",
            artist = "Sky Official Remix",
            album = "Sky Vibes Showcase",
            artworkUrl = "https://res.cloudinary.com/dnxjhd4ii/image/upload/v1791629123/1783606840288_2_yt5d35.jpg",
            streamUrl = "https://res.cloudinary.com/dnxjhd4ii/video/upload/v1783607799/DJ_Haning_-_Mejikuhibiniu_X_Kasih_Aba_Aba_SPOTISAVER_pgnytv.mp3",
            durationMs = 214000L,
            lyrics = "Mejikuhibiniu warna warni senja...\nKasih aba-aba di lantai dansa NOXA\nAlunan musik mengalun malam\nSky Vibes mengguncang panggung utama."
        )
    }

    // Auto-play the pinned audio upon entering About Sky if not already playing it
    LaunchedEffect(Unit) {
        if (playerState.currentSong?.id != pinnedAudioTrack.id) {
            onPlaySong(pinnedAudioTrack)
        }
    }

    val isThisTrackPlaying = playerState.currentSong?.id == pinnedAudioTrack.id && playerState.isPlaying

    // Animated equalizing bars
    val infiniteTransition = rememberInfiniteTransition(label = "eq_bars")
    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse),
        label = "bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 18f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(tween(280), RepeatMode.Reverse),
        label = "bar3"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp)
            .testTag("about_sky_screen")
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("about_sky_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NoxaTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "About Sky",
                color = NoxaTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Hero Image — Exact Cloudinary asset
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(20.dp))
                .background(NoxaSurfaceElevated)
                .border(1.dp, NoxaBorder, RoundedCornerShape(20.dp))
        ) {
            AsyncImage(
                model = "https://res.cloudinary.com/dnxjhd4ii/image/upload/v1791629123/1783606840288_2_yt5d35.jpg",
                contentDescription = "Sky Hero Showcase",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle dark overlay at bottom of hero
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 200f
                        )
                    )
            )

            // Lime Tag
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NoxaLime)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "SKY • LEAD ARCHITECT",
                    color = NoxaBlack,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pinned Music Showcase Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isThisTrackPlaying) NoxaLime else NoxaBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PINNED TRACK",
                        color = NoxaLime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // Waveform equalizer bars indicator
                    if (isThisTrackPlaying) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.height(26.dp)
                        ) {
                            Box(modifier = Modifier.width(3.dp).height(bar1Height.dp).background(NoxaLime, RoundedCornerShape(2.dp)))
                            Box(modifier = Modifier.width(3.dp).height(bar2Height.dp).background(NoxaLime, RoundedCornerShape(2.dp)))
                            Box(modifier = Modifier.width(3.dp).height(bar3Height.dp).background(NoxaLime, RoundedCornerShape(2.dp)))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pinnedAudioTrack.title,
                            color = NoxaTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${pinnedAudioTrack.artist} • ${pinnedAudioTrack.album}",
                            color = NoxaTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Play/Pause button for pinned track
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NoxaLime)
                            .clickable {
                                if (playerState.currentSong?.id == pinnedAudioTrack.id) {
                                    onTogglePlayPause()
                                } else {
                                    onPlaySong(pinnedAudioTrack)
                                }
                            }
                            .testTag("about_sky_play_pinned_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isThisTrackPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isThisTrackPlaying) "Pause" else "Play",
                            tint = NoxaBlack,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Biography & Philosophy Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoxaBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Crafting Acoustic Excellence",
                    color = NoxaTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "NOXA was born from a singular passion: creating a music platform that respects pure sound, fluid design, and uncompromising performance. Built natively with modern Android engineering, NOXA delivers deep AMOLED black aesthetics, instant playback response, and hardware-accelerated equalizers.",
                    color = NoxaTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "The Vision",
                    color = NoxaTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Seamless streaming without clutter. Every transition, every control curve, and every sound profile is calibrated to make music listening an immersive, luxurious experience.",
                    color = NoxaTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Links & Ecosystem Section
        Text(
            text = "Ecosystem & Contact",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoxaBorder)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // Official Sky Vibes Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://skyvibes.my.id"))
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = "Website", tint = NoxaLime, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Official Website", color = NoxaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("skyvibes.my.id", color = NoxaTextSecondary, fontSize = 12.sp)
                        }
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = NoxaTextTertiary, modifier = Modifier.size(16.dp))
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NoxaBorder))

                // GitHub
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/skyyy0"))
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = "GitHub", tint = NoxaLime, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("GitHub Repository & Code", color = NoxaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("github.com/skyyy0", color = NoxaTextSecondary, fontSize = 12.sp)
                        }
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = NoxaTextTertiary, modifier = Modifier.size(16.dp))
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NoxaBorder))

                // Direct WhatsApp / Bug Report
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/6289510146933?text=Hello%20Sky,%20reporting%20an%20inquiry%20regarding%20NOXA%20Android"))
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, contentDescription = "Contact", tint = NoxaLime, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Contact Sky / Report Issues", color = NoxaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("+62 895 1014 6933", color = NoxaTextSecondary, fontSize = 12.sp)
                        }
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = NoxaTextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Footer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "NOXA • Sky Edition",
                    color = NoxaTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Built with precision and passion.",
                    color = NoxaTextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
