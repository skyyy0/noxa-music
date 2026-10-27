package com.example.ui.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.data.model.Song

/**
 * Shares a track through the system share sheet.
 *
 * The link is NOXA's own deep link ([NoxaLinks.songLink]) so recipients land
 * straight in the app's player when NOXA is installed.
 */
object ShareUtils {

    fun shareSong(context: Context, song: Song) {
        val body = NoxaLinks.shareText(song)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${song.title} — ${song.artist}")
            putExtra(Intent.EXTRA_TEXT, body)
        }

        val chooser = Intent.createChooser(send, "Bagikan \"${song.title}\"")
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            // No app can handle the share; nothing sensible to do.
        }
    }
}
