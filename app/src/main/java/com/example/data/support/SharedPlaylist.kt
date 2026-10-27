package com.example.data.support

import com.example.data.model.Song
import java.security.SecureRandom
import java.util.Locale

/**
 * One song inside a collaborative ("kolaboratif") playlist — only the fields
 * every install needs to rebuild a playable [Song]: the namespaced id carries
 * the provider, so `withDerivedProviderFields()` restores source & remote id
 * on the receiving device without shipping stream URLs (which expire anyway).
 */
data class SharedPlaylistSong(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String,
    val durationMs: Long
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        artworkUrl = artwork,
        durationMs = durationMs
    ).withDerivedProviderFields()

    companion object {
        fun fromSong(song: Song): SharedPlaylistSong = SharedPlaylistSong(
            id = song.id,
            title = song.title,
            artist = song.artist,
            artwork = song.artworkUrl,
            durationMs = song.durationMs
        )
    }
}

/**
 * A playlist that lives in the shared backend document: everyone holding the
 * [code] can read it, and writers publish the whole list back (last writer
 * wins, guarded by [SharedPlaylistSync] so two phones don't silently clobber
 * each other).
 */
data class SharedPlaylist(
    val code: String,
    val name: String,
    val ownerUid: String,
    val ownerName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songs: List<SharedPlaylistSong>
)

/**
 * The 6-character collaborative-playlist code and the text munging around it.
 *
 * The alphabet drops `0/O/1/I` (same trick as Dengar Bareng room codes) so a
 * code read off a chat message is never mistyped into a different one.
 */
object SharedPlaylistCodes {

    /** No 0/O/1/I confusion — copied from the room-code alphabet. */
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    const val LENGTH = 6

    private val random = SecureRandom()

    /** A fresh, unclaimed-looking code: 6 chars from [ALPHABET]. */
    fun newCode(): String = buildString {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }

    /**
     * Pulls the code out of whatever the user pasted: the bare code itself,
     * a code with dashes/spaces, or a full share message. Returns "" when no
     * plausible code is found (the caller shows an honest error).
     */
    fun normalize(input: String): String {
        val upper = input.trim().uppercase(Locale.ROOT)
        if (upper.isEmpty()) return ""

        // 1. The whole paste is the code (spaces/dashes allowed between chars).
        val stripped = upper.filterNot { it.isWhitespace() || it == '-' || it == '_' }
        if (isValid(stripped)) return stripped

        // 2. Embedded after a marker our share text uses: kode/code/playlist.
        //    The alphabet class is concatenated (never a `$` template inside a
        //    regex literal) so the parser and the regex can't disagree.
        val codeClass = "[" + ALPHABET + "]"
        val marker = Regex(
            "(?:KODE|CODE|PLAYLIST|NOXA)[\\s:/\\-]*(" + codeClass + "{6})"
        )
        marker.find(upper)?.groupValues?.get(1)?.let { candidate ->
            if (isValid(candidate)) return candidate
        }

        // 3. Any standalone 6-char token bounded by non-code characters —
        //    catches links and foreign formats without grabbing syllables out
        //    of longer words (the boundary check keeps "SAYANG" out only when
        //    it is glued to more code-alphabet characters; our own share copy
        //    never produces that).
        val loose = Regex("(?<![A-Z0-9])(" + codeClass + "{6})(?![A-Z0-9])")
        loose.find(upper)?.groupValues?.get(1)?.let { candidate ->
            if (isValid(candidate)) return candidate
        }

        return ""
    }
}

/**
 * The one rule that keeps two collaborating devices honest without a server:
 * push local changes only while nobody else has written since our last sync.
 * When the remote moved under us, the caller shows "pull to sync" instead of
 * overwriting a friend's edit.
 */
object SharedPlaylistSync {
    fun canPush(baseUpdatedAt: Long, remoteUpdatedAt: Long): Boolean =
        baseUpdatedAt != 0L && baseUpdatedAt == remoteUpdatedAt
}
