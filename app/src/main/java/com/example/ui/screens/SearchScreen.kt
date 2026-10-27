package com.example.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Song
import com.example.ui.components.SongListItem
import com.example.ui.components.noxaClickable
import com.example.ui.components.noxaTap
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary

data class BrowseGenre(
    val name: String,
    val color: Color
)

@Composable
fun SearchScreen(
    searchQuery: String,
    searchResults: List<Song>,
    isSearching: Boolean,
    currentPlayingSongId: String?,
    onQueryChange: (String) -> Unit,
    onPerformSearch: (String) -> Unit,
    onPlaySong: (Song, List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    favoriteIds: Set<String>,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShare: (Song) -> Unit,
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    /** Card name → artwork URL (artist photos, chart covers) for the browse grid. */
    genreImages: Map<String, String> = emptyMap(),
    /** v2.0.0 — hasil "Cari pakai lirik" (reverse lookup via LRCLIB). */
    lyricSearch: com.example.ui.viewmodel.LyricSearchUi = com.example.ui.viewmodel.LyricSearchUi.EMPTY,
    onSearchByLyrics: (String) -> Unit = {},
    onClearLyricSearch: () -> Unit = {},
    /** v2.0.0 — rating bintang & "jangan putar lagi". */
    songRatings: Map<String, Int> = emptyMap(),
    onRateSong: (Song, Int) -> Unit = { _, _ -> },
    onMuteSong: (Song) -> Unit = {},
    onMuteArtist: (Song) -> Unit = {},
    // ------------------------------------------- v2.1: radio & unduhan
    downloadedIds: Set<String> = emptySet(),
    downloadingIds: Set<String> = emptySet(),
    onToggleDownload: (Song) -> Unit = {},
    onStartRadio: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // "Cari pakai lirik" mode: type a line of lyrics, get the songs back.
    var lyricMode by remember { mutableStateOf(false) }

    // -------------------------------------------------- v2.1: pencarian suara
    // The system speech recognizer does the listening (and holds the mic
    // permission itself), so this app never records anything — it just gets
    // the transcript back and runs the normal search with it.
    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (spoken.isNotBlank()) {
            onQueryChange(spoken)
            if (lyricMode) onSearchByLyrics(spoken) else onPerformSearch(spoken)
        }
    }

    fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Sebutkan judul atau artis lagu")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Pencarian suara tidak tersedia di perangkat ini.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val genres = remember {
        listOf(
            BrowseGenre("Dewa 19", Color(0xFFE50914)),
            BrowseGenre("Sheila On 7", Color(0xFF1DB954)),
            BrowseGenre("Iwan Fals", Color(0xFFF59E0B)),
            BrowseGenre("NOAH", Color(0xFF8B5CF6)),
            BrowseGenre("Tulus", Color(0xFF06B6D4)),
            BrowseGenre("Justin Bieber", Color(0xFFEC4899)),
            BrowseGenre("Global Top 50", Color(0xFFEF4444)),
            BrowseGenre("Rock & Metal", Color(0xFF10B981))
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
            .statusBarsPadding()
            .testTag("search_screen")
    ) {
        // Search Header
        Text(
            text = "Search",
            color = NoxaTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
        )

        // Search Input Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { text ->
                    if (lyricMode) {
                        // In lyric mode every keystroke goes to the reverse
                        // lookup instead of the catalogue search.
                        onQueryChange(text)
                        onSearchByLyrics(text)
                    } else {
                        onQueryChange(text)
                    }
                },
                placeholder = {
                    Text(
                        text = if (lyricMode) {
                            "Ketik sepenggal lirik yang kamu ingat..."
                        } else {
                            "Songs, artists, podcasts..."
                        },
                        color = NoxaTextTertiary,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = NoxaTextSecondary
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { startVoiceSearch() },
                            modifier = Modifier.testTag("voice_search_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Cari dengan suara",
                                tint = NoxaLime
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    onQueryChange("")
                                    if (lyricMode) onClearLyricSearch()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = NoxaTextSecondary
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = NoxaSurfaceElevated,
                    unfocusedContainerColor = NoxaSurfaceSecondary,
                    focusedIndicatorColor = NoxaLime,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = NoxaLime,
                    focusedTextColor = NoxaTextPrimary,
                    unfocusedTextColor = NoxaTextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input")
            )
        }

        // Mode chips: normal title/artist search vs "Cari pakai lirik".
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchModeChip(
                label = "Judul & artis",
                selected = !lyricMode,
                onClick = {
                    if (lyricMode) {
                        lyricMode = false
                        onClearLyricSearch()
                    }
                },
                testTag = "search_mode_titles"
            )
            SearchModeChip(
                label = "Cari pakai lirik",
                selected = lyricMode,
                onClick = { lyricMode = true },
                testTag = "search_mode_lyrics"
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Provider filter chips were removed on purpose: showing "YouTube",
        // "Deezer" or "Audius" made NOXA look like a front for other music
        // apps. The merged result list now stands on its own, provider-neutral.
        val filteredResults = searchResults

        // Body: lyric mode shows the reverse-lookup results with the
        // matched line quoted above each group of hits.
        if (lyricMode && isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = NoxaLime,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Mencari lirik...",
                        color = NoxaTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (lyricMode) {
            if (lyricSearch.songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Subject,
                            contentDescription = null,
                            tint = NoxaTextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (lyricSearch.query.isEmpty()) {
                                "Ketik sepenggal lirik — misalnya\n\"kuingin kau tahu perasaanku\""
                            } else {
                                "Belum ada lagu yang cocok dengan lirik itu.\nCoba potongan lain yang lebih panjang."
                            },
                            color = NoxaTextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp, start = 8.dp, end = 8.dp)
                ) {
                    item {
                        // The matched lyric line, quoted — so the user sees
                        // *why* each result came back.
                        Text(
                            text = "\"${lyricSearch.matchedLine}\"",
                            color = NoxaLime,
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                    itemsIndexed(lyricSearch.songs, key = { _, song -> song.id }) { index, song ->
                        SongListItem(
                            song = song,
                            isPlaying = song.id == currentPlayingSongId,
                            isFavorite = favoriteIds.contains(song.id),
                            onPlay = { onPlaySong(song, lyricSearch.songs, index) },
                            onToggleFavorite = { onToggleFavorite(song) },
                            onPlayNext = { onPlayNext(song) },
                            onOpenArtist = onOpenArtist,
                            onAddToQueue = { onAddToQueue(song) },
                            onAddToPlaylist = { onAddToPlaylist(song) },
                            onShare = { onShare(song) },
                            rating = songRatings[song.id] ?: 0,
                            onRate = onRateSong,
                            onMuteSong = { onMuteSong(song) },
                            onMuteArtist = { onMuteArtist(song) },
                            onStartRadio = { onStartRadio(song) },
                            onToggleDownload = { onToggleDownload(song) },
                            isDownloaded = downloadedIds.contains(song.id),
                            isDownloading = downloadingIds.contains(song.id),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        } else if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = NoxaLime,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Mencari musik...",
                        color = NoxaTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (searchQuery.isNotEmpty() && searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "No results",
                        tint = NoxaTextTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No results found for \"$searchQuery\"",
                        color = NoxaTextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Check spelling or search for popular artists",
                        color = NoxaTextTertiary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (filteredResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp, start = 8.dp, end = 8.dp)
            ) {
                itemsIndexed(filteredResults, key = { _, song -> song.id }) { index, song ->
                    SongListItem(
                        song = song,
                        isPlaying = song.id == currentPlayingSongId,
                        isFavorite = favoriteIds.contains(song.id),
                        onPlay = { onPlaySong(song, filteredResults, index) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { onPlayNext(song) },
                        onOpenArtist = onOpenArtist,
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onShare = { onShare(song) },
                        rating = songRatings[song.id] ?: 0,
                        onRate = onRateSong,
                        onMuteSong = { onMuteSong(song) },
                        onMuteArtist = { onMuteArtist(song) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        } else {
            // Browse Genres Grid
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Explore Genres",
                    color = NoxaTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(genres) { genre ->
                        val imageUrl = genreImages[genre.name]
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(genre.color.copy(alpha = 0.25f))
                                .border(1.dp, genre.color.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .noxaClickable {
                                    onPerformSearch(genre.name)
                                }
                                .testTag("genre_card_${genre.name}"),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            if (imageUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = genre.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Scrim so the title stays readable over any photo —
                                // the Spotify browse-card treatment.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.75f)
                                                )
                                            )
                                        )
                                )
                            }
                            Text(
                                text = genre.name,
                                color = NoxaTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One pill in the search-mode row (Judul & artis / Cari pakai lirik).
 * Lime-filled when selected; hairline border when not.
 */
@Composable
private fun SearchModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) NoxaLime else NoxaSurfaceElevated)
            .border(
                1.dp,
                if (selected) NoxaLime else NoxaBorder,
                RoundedCornerShape(20.dp)
            )
            .noxaTap { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            color = if (selected) NoxaBlack else NoxaTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
