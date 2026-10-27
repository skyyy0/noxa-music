package com.example.data.model

/** Where a track (and its audio) came from. */
enum class MusicSource {
    /** Audius public API — full-length, artist-uploaded audio. */
    AUDIUS,

    /** Deezer public API — 30 second, label-approved preview clips. */
    DEEZER,

    /**
     * YouTube (metadata via search API or Invidious/Piped, audio & MV resolved
     * through an Invidious/Piped instance at play time). Full-length tracks.
     */
    YOUTUBE
}

data class Song(
    /**
     * Globally unique id, namespaced by provider (`deezer:123`, `audius:abc`,
     * `youtube:<videoId>`). Two providers can return the same song, so the
     * source prefix keeps favourites, history and the playback queue from
     * colliding.
     *
     * Because the prefix encodes the provider, [source] and [remoteId] can be
     * reconstructed from [id] alone via [withDerivedProviderFields] — that is
     * how a favourite stored before this metadata existed is still playable.
     */
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "Single",
    val artworkUrl: String,
    val streamUrl: String = "",
    val durationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val lyrics: String? = null,
    val channelId: String? = null,
    val viewCount: String? = null,
    val source: MusicSource = MusicSource.AUDIUS,
    /** The provider's own id, used when calling that provider's API again. */
    val remoteId: String = "",
    /** True when the audio is a short, provider-limited clip (e.g. Deezer). */
    val isPreview: Boolean = false,
    /** Public web page for this track, used by the share button. */
    val shareUrl: String = "",
    /**
     * The artist's id in the catalogue that carries full artist pages (Deezer
     * today). Blank for sources without one — the UI then resolves the artist
     * by name at open time.
     */
    val artistId: String = "",
    /**
     * YouTube video id matched for a non-YouTube track (a Deezer preview whose
     * full audio was found on YouTube). Lets the full player offer the MV
     * toggle for those tracks too, even though [remoteId] belongs to Deezer.
     */
    val matchedVideoId: String = ""
) {
    /**
     * Returns a copy whose [source] and [remoteId] are consistent with the
     * provider prefix baked into [id]. Songs loaded from Room or from the
     * persisted playback session predate those fields, so every consumer that
     * reads them from disk runs this first.
     */
    fun withDerivedProviderFields(): Song {
        val prefix = ID_PREFIXES.entries.firstOrNull { id.startsWith(it.value) }
            ?: return this
        return copy(
            source = prefix.key,
            remoteId = remoteId.ifBlank { id.removePrefix(prefix.value) }
        )
    }

    companion object {
        /** The id prefix used for each provider, e.g. `youtube:dQw4w9WgXcQ`. */
        val ID_PREFIXES: Map<MusicSource, String> = mapOf(
            MusicSource.AUDIUS to "audius:",
            MusicSource.DEEZER to "deezer:",
            MusicSource.YOUTUBE to "youtube:"
        )

        fun idFor(source: MusicSource, remoteId: String): String =
            (ID_PREFIXES[source] ?: "") + remoteId
    }
}
