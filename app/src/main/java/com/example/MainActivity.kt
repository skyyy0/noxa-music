package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ui.NoxaApp
import com.example.ui.theme.NoxaTheme
import com.example.ui.util.NoxaLinks
import com.example.ui.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    /** Shared across the activity and the composable tree (deep links included). */
    private val musicViewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            NoxaTheme {
                NoxaApp(viewModel = musicViewModel)
            }
        }
        handleShareLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareLink(intent)
    }

    /**
     * Shared NOXA links (`https://skyvibes.my.id/track/<id>` or
     * `noxa://play/<id>`) resolve into a real song and start playing — the
     * recipient lands straight in the player.
     */
    private fun handleShareLink(intent: Intent?) {
        val songId = NoxaLinks.songIdFrom(intent?.data) ?: return
        musicViewModel.playSharedSong(songId)
    }

    /**
     * The media notification / foreground playback service needs this permission
     * on Android 13+. Without it the notification is silently dropped.
     *
     * The platform permission API is used deliberately (rather than the Activity
     * Result API) because the result is not needed here, and the Activity Result
     * API requires an androidx.fragment version >= 1.3.0 which this project does
     * not declare directly.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}
