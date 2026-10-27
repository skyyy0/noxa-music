package com.example.data.offline

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** One file written to the offline directory. */
data class StoredFile(val path: String, val sizeBytes: Long)

/**
 * **Mode offline** — the byte-level half of the download feature: streams a
 * URL into `filesDir/offline/` and hands back the path.
 *
 * Design notes:
 *  * Everything lands as `*.part` first and is renamed on success, so a killed
 *    download never leaves a half-file that the player would try to play.
 *  * The extension comes from the response's `Content-Type` (falling back to
 *    `.bin`): ExoPlayer sniffs the container anyway, but a matching suffix
 *    keeps the file sane when a user browses the app's private storage.
 *  * One OkHttpClient is reused — a fresh pool per download is the classic
 *    way to exhaust sockets on a long download session.
 */
object OfflineFileStore {

    private const val TAG = "OfflineFileStore"
    private const val DIR_NAME = "offline"
    private const val MAX_BYTES = 64L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    /** The offline directory, created on first use. */
    fun dir(context: android.content.Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** Filesystem-safe stem for a song id (`deezer:123` → `deezer_123`). */
    fun baseNameFor(songId: String): String =
        songId.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /**
     * Downloads [url] into the offline directory under [baseName].
     * Returns null when the body is empty or oversized; throws [IOException]
     * on transport/HTTP failure so the caller can report an honest error.
     */
    suspend fun fetch(
        context: android.content.Context,
        url: String,
        baseName: String
    ): StoredFile? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NOXA")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("body kosong")
            val ext = extensionFor(response.header("Content-Type"))
            val target = File(dir(context), baseName + ext)
            val part = File(dir(context), baseName + ".part")

            var written = 0L
            part.outputStream().use { out ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > MAX_BYTES) {
                            throw IOException("file melebihi batas 64 MB")
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }

            if (written == 0L) {
                part.delete()
                return@use null
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.delete()
                throw IOException("gagal menyimpan file")
            }
            StoredFile(target.absolutePath, written)
        }
    }

    /** Best-effort removal of previously stored files. Never throws. */
    fun delete(paths: Collection<String>) {
        paths.filter { it.isNotBlank() }.forEach { path ->
            runCatching {
                val file = File(path)
                if (file.exists() && !file.delete()) {
                    Log.w(TAG, "Could not delete $path")
                }
            }
        }
    }

    private fun extensionFor(contentType: String?): String = when {
        contentType == null -> ".bin"
        contentType.contains("mpeg", ignoreCase = true) -> ".mp3"
        contentType.contains("mp4", ignoreCase = true) -> ".m4a"
        contentType.contains("webm", ignoreCase = true) -> ".webm"
        contentType.contains("ogg", ignoreCase = true) ||
            contentType.contains("opus", ignoreCase = true) -> ".ogg"
        contentType.startsWith("image/", ignoreCase = true) -> {
            when {
                contentType.contains("png") -> ".png"
                contentType.contains("webp") -> ".webp"
                contentType.contains("gif") -> ".gif"
                else -> ".jpg"
            }
        }
        else -> ".bin"
    }
}
