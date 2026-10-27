package com.example.data.model

/**
 * Tracks Sky pinned for this release.
 *
 * A pinned track gets a badge everywhere it appears (home shelf, search,
 * library, mini player) and its own shelf at the top of Home. In the full
 * player, the pin also surfaces the quote [SKY_QUOTE] beneath the transport
 * controls — the words that explain *why* the song is pinned.
 */
object PinnedTracks {

    /** Sky's words for this drop, shown in the full player under the controls. */
    const val SKY_QUOTE =
        "ada lagu yang hanya berdurasi beberapa menit, " +
            "tapi bisa membawa kita kembali ke masa yang sudah tidak mungkin dapat diulang."

    /** A pinned pick: matched against songs by title + artist (case-insensitive). */
    data class PinnedTrack(val title: String, val artist: String, val quote: String)

    val ALL: List<PinnedTrack> = listOf(
        PinnedTrack(title = "Kangen", artist = "Dewa 19", quote = SKY_QUOTE),
        PinnedTrack(
            title = "Pupus",
            artist = "Dewa 19",
            quote = "tidak semua yang kita perjuangkan ditakdirkan untuk kita miliki."
        ),
        PinnedTrack(
            title = "Dan",
            artist = "Sheila On 7",
            quote = "kadang, merelakan seseorang berarti menerima bahwa kebahagiaannya " +
                "mungkin tidak lagi bersama kita."
        ),
        PinnedTrack(
            title = "Terlalu Manis",
            artist = "Slank",
            quote = "beberapa kenangan memang lebih indah dibiarkan menjadi bagian dari masa lalu."
        )
    )

    /**
     * The pinned entry for this song, or null when it is not pinned.
     *
     * Strict catalogue matching first, then the relaxed rule as a fallback:
     * a pin the catalogue cannot serve is resolved from YouTube, where the
     * artist may sit inside the title and the uploader is the label's channel.
     * A track that earned its place on the shelf must still get Sky's quote
     * and the star in the player — so the lookup the UI uses is exactly as
     * forgiving as the shelf's own resolver, while still requiring the
     * artist's name.
     */
    fun find(song: Song): PinnedTrack? =
        ALL.firstOrNull { pin -> matches(pin, song.title, song.artist) }
            ?: ALL.firstOrNull { pin -> matchesRelaxed(pin, song.title, song.artist) }

    fun isPinned(song: Song): Boolean = find(song) != null

    /**
     * Whether a catalogue result is the pinned recording: titles travel with
     * remaster/bracket/live suffixes across providers, so the pinned title must
     * still match the *start* of the result's title. One rule, used both when
     * matching live catalogue results and when looking up the quote for a song
     * that is already in the UI — so a track that gets the badge always gets
     * its quote too.
     */
    fun matchesResult(title: String, artist: String): Boolean = ALL.any { pin ->
        matches(pin, title, artist)
    }

    /**
     * Whether a catalogue result is THIS specific pinned recording (not just
     * any pin's). The home feed uses it while resolving each pin so two pins
     * of the same artist (Kangen & Pupus are both Dewa 19) can never grab
     * the same track — that duplicated song ids and crashed the shelf with
     * "Key \"deezer:…\" was already used".
     */
    fun matches(pin: PinnedTrack, title: String, artist: String): Boolean {
        val t = normalizedTitle(title)
        return titleFitsPin(t, pin) && pin.artist.equals(artist.trim(), ignoreCase = true)
    }

    /**
     * Relaxed match for **user-generated sources** (YouTube), used only when
     * the catalogue cannot serve a pin:
     *
     *  * the video title may carry the artist inside it, because the uploader
     *    is the label's channel, not the artist — so the artist may appear
     *    in the title;
     *  * the pin title may sit anywhere in the title, as long as it is a
     *    whole word (so "Dan" never matches "dan" mid-word).
     *
     * Catalogue matching ([matches]) stays strict — a badge must never leak
     * onto a wrong track just because a word overlaps.
     */
    fun matchesRelaxed(pin: PinnedTrack, title: String, artist: String): Boolean {
        val a = artist.trim()
        val t = normalizedTitle(title)
        val artistHit = pin.artist.equals(a, ignoreCase = true) ||
            a.contains(pin.artist, ignoreCase = true) ||
            t.contains(pin.artist, ignoreCase = true)
        if (!artistHit) return false
        if (titleFitsPin(t, pin)) return true
        // Short pins are case-sensitive here: "Dan" must never match the
        // Indonesian word "dan" (="and") inside an unrelated title, while
        // long pins like "Terlalu Manis" keep the forgiving case fold.
        val options = if (pin.title.length <= 3) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex(
            "(?<![A-Za-z0-9])${Regex.escape(pin.title)}(?![A-Za-z0-9])",
            options
        ).containsMatchIn(t)
    }

    /**
     * Deezer serves Sheila On 7's pick as "Dan…" (Ellipsis in the catalogue),
     * so trailing dots/ellipsis/exclamations are not part of the title.
     */
    private fun normalizedTitle(title: String): String =
        title.trim().trimEnd('.', '…', '!', '?', ' ', '-').trim()

    /** The pinned title leads the (normalised) result title. */
    private fun titleFitsPin(t: String, pin: PinnedTrack): Boolean =
        t.equals(pin.title, ignoreCase = true) ||
            t.startsWith("${pin.title} ", ignoreCase = true) ||
            t.startsWith("${pin.title}(", ignoreCase = true) ||
            t.startsWith("${pin.title} -", ignoreCase = true)
}
