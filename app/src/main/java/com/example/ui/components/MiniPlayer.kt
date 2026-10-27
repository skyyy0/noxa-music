package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Song
import com.example.media.PlaybackProgress
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState

/**
 * The panel's silhouette — shared by the surface, the sheen and the edge so
 * the three layers never disagree about where the corners are.
 */
private val MiniPlayerShape = RoundedCornerShape(22.dp)

@Composable
fun MiniPlayer(
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progressFlow: StateFlow<PlaybackProgress>,
    isFavorite: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    AnimatedVisibility(
        visible = currentSong != null,
        // Soft spring entrance and a quicker tweened exit: the bar glides
        // into place instead of snapping, iOS-style.
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = { it }
        ),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 200),
            targetOffsetY = { it }
        ),
        modifier = modifier
    ) {
        if (currentSong == null) return@AnimatedVisibility

        Box(
            modifier = Modifier
                .fillMaxWidth()
                // A floating glass panel of its own: soft elevation, a sheet
                // that lets the canvas read through it, light pooling along the
                // top edge, and a hairline that fades before it reaches the
                // bottom. Deliberately NOT welded to the navigation below —
                // the gap between the two is what makes the stack feel light.
                .shadow(14.dp, MiniPlayerShape, clip = false)
                .clip(MiniPlayerShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            NoxaSurface.copy(alpha = 0.97f),
                            NoxaBlack.copy(alpha = 0.93f)
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    ),
                    MiniPlayerShape
                )
                .noxaTap { onClick() }
                .testTag("mini_player")
        ) {
            Column {
                // The playhead is the dock's top seam: edge to edge, so playback
                // and navigation are literally joined by the same line.
                MiniPlayerProgress(progressFlow)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Artwork Thumbnail
                    AsyncImage(
                        model = currentSong.artworkUrl,
                        contentDescription = "Mini player artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            // Sleeve radius: album art is nearly square everywhere
                            // in NOXA so artwork reads as artwork, not as a chip.
                            .clip(RoundedCornerShape(10.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Artist
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = currentSong.title,
                            color = NoxaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Scrolls when the title is too long for the strip.
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = currentSong.artist,
                            color = NoxaTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Favorite Button — a heavier tick marks the heart.
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleFavorite()
                        },
                        modifier = Modifier.size(36.dp).testTag("mini_player_fav_btn")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) NoxaLime else NoxaTextSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                // Pops when the heart fills, settles when it empties.
                                .noxaPop(trigger = isFavorite)
                        )
                    }

                    // Play/Pause Button
                    Box(
                        modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NoxaLime)
                        .noxaTap { onTogglePlayPause() }
                        .testTag("mini_player_play_pause_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Skip Next Button
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(36.dp).testTag("mini_player_next_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = NoxaTextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The playhead hairline that caps the dock.
 *
 * This is the only part of the mini player that watches the fast position flow,
 * so the surrounding artwork, title and buttons are not rebuilt every 500 ms.
 * The track stays faintly visible so the seam doubles as the dock's top edge
 * even before playback has moved.
 */
@Composable
private fun MiniPlayerProgress(progressFlow: StateFlow<PlaybackProgress>) {
    val progress by progressFlow.collectAsState()
    // The position jumps twice a second; easing each step keeps the hairline
    // gliding forward instead of hopping in visible chunks.
    val smoothFraction by animateFloatAsState(
        targetValue = progress.fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 420, easing = LinearEasing),
        label = "mini_progress"
    )
    LinearProgressIndicator(
        progress = { smoothFraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp),
        color = NoxaLime,
        trackColor = NoxaBorder
    )
}
