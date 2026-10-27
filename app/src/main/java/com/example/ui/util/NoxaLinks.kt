package com.example.ui.util

import android.net.Uri
import com.example.data.model.Song

/**
 * NOXA's own share links — instead of sending people to Deezer, sharing sends
 * `https://skyvibes.my.id/track/<songId>`, which the manifest's intent-filters
 * open straight in the app's player (the `noxa://play/<songId>` scheme works
 * too and is handy for testing).
 */
object NoxaLinks {

    /** Public base of the web link. The `www.` host is registered as well. */
    const val WEB_BASE = "https://skyvibes.my.id/track"

    /** Canonical share link for a song, e.g. `…/track/deezer%3A76056788`. */
    fun songLink(song: Song): String = "$WEB_BASE/${Uri.encode(song.id)}"

    /** The familiar share message, now pointing at the NOXA link. */
    fun shareText(song: Song): String = buildString {
        append(song.title)
        append(" — ")
        append(song.artist)
        append('\n')
        append(songLink(song))
        append("\n\nDidengarkan lewat NOXA Music")
    }

    /**
     * The song id carried by an incoming VIEW intent, or `null` when the URI is
     * not a NOXA link. Accepts `noxa://play/<id>` and
     * `https://skyvibes.my.id/track/<id>` (with or without `www.`).
     */
    fun songIdFrom(uri: Uri?): String? {
        if (uri == null) return null
        val id = when (uri.scheme?.lowercase()) {
            "noxa" -> uri.pathSegments.firstOrNull()

            "https", "http" -> {
                val host = uri.host?.removePrefix("www.").orEmpty()
                if (host == "skyvibes.my.id" && uri.pathSegments.firstOrNull() == "track") {
                    uri.pathSegments.getOrNull(1)
                } else {
                    null
                }
            }

            else -> null
        }
        return id?.takeIf { it.isNotBlank() }
    }
}
