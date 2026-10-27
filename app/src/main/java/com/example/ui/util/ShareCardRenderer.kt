package com.example.ui.util

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import coil.imageLoader
import coil.request.ImageRequest
import com.example.data.model.PinnedTracks
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import androidx.core.content.FileProvider

/**
 * The Spotify-Canvas-style share card: artwork, song title, artist and NOXA
 * branding on one square image. Rendering is plain Bitmap/Canvas so it works
 * anywhere (including from a bottom-sheet coroutine) without a composable
 * host.
 *
 * Sharing flow: the card is rendered, best-effort saved into the gallery
 * (`Pictures/NOXA`) so the user owns the image, and then handed to the system
 * share sheet through the FileProvider.
 */
object ShareCardRenderer {

    /**
     * Everything the Kartu Profil NOXA shows — assembled by the profile screen
     * from the signed-in user and the on-device Wrapped stats.
     */
    data class ProfileCardData(
        val displayName: String,
        val subtitle: String?,
        val photoUrl: String?,
        val isDeveloper: Boolean,
        val totalPlays: Int,
        val distinctArtists: Int,
        val topArtist: String?,
        val topSong: String?,
        val longestStreakDays: Long
    )

    private const val CARD_SIZE = 1080
    /** Edge length, in pixels, the QR modules are encoded at before scaling. */
    private const val QR_PX = 560
    private const val LYRIC_CARD_HEIGHT = 1350
    private const val LYRIC_CARD_MAX_LINES = 7
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    private const val TAG = "ShareCard"

    /** Colors tuned to the NOXA theme (near-black with lime accents). */
    private val BG_TOP = Color.rgb(24, 30, 12)
    private val BG_BOTTOM = Color.rgb(9, 10, 7)
    private val LIME = Color.rgb(199, 255, 0)
    private val TEXT_PRIMARY = Color.rgb(244, 246, 238)
    private val TEXT_SECONDARY = Color.rgb(168, 174, 156)

    /**
     * Renders the card, saves it to the gallery and opens the share sheet.
     * Returns true when the share sheet was handed the image successfully.
     */
    suspend fun share(context: Context, song: Song): Boolean {
        val file = try {
            val bitmap = render(context, song) ?: return false
            saveToGallery(context, bitmap, safeName(song.title, "share"))
            writeToCache(context, bitmap, "song-${song.id.hashCode()}")
        } catch (e: Exception) {
            Log.e(TAG, "Rendering/saving the share card failed", e)
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + FILE_PROVIDER_SUFFIX,
                file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, NoxaLinks.shareText(song))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Many share targets (and several OEM pickers) silently drop
                // the URI grant carried only by EXTRA_STREAM; putting the uri
                // into ClipData is what actually propagates the read grant.
                clipData = ClipData.newUri(context.contentResolver, "NOXA share card", uri)
            }
            val chooser = Intent.createChooser(send, "Bagikan \"${song.title}\"").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Opening the share sheet failed", e)
            false
        }
    }

    /**
     * **Bagikan QR** — the NOXA deep link as a scannable code on a themed
     * card, so a friend only has to point a camera at it. Same flow as the
     * canvas card: render → gallery → system share sheet.
     */
    suspend fun shareQr(context: Context, song: Song): Boolean {
        val file = try {
            val bitmap = renderQrCard(song) ?: return false
            saveToGallery(context, bitmap, safeName(song.title, "qr"))
            writeToCache(context, bitmap, "qr-${song.id.hashCode()}")
        } catch (e: Exception) {
            Log.e(TAG, "Rendering/saving the QR card failed", e)
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + FILE_PROVIDER_SUFFIX,
                file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, NoxaLinks.shareText(song))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "NOXA QR", uri)
            }
            val chooser = Intent.createChooser(send, "Bagikan QR \"${song.title}\"").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Opening the QR share sheet failed", e)
            false
        }
    }

    /**
     * Draws the 1080×1080 QR card: title and artist up top, the code on a
     * white quiet-zone plate, and the scan hint below.
     *
     * The modules are painted near-black on white rather than lime-on-black:
     * camera pipelines are far less forgiving than the theme is, and a code
     * that only sometimes scans is worse than no code at all. The lime lives
     * in the surrounding chrome.
     */
    internal fun renderQrCard(song: Song): Bitmap? {
        val link = NoxaLinks.songLink(song)
        if (link.isBlank()) return null

        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )
        val matrix = try {
            QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, QR_PX, QR_PX, hints)
        } catch (e: Exception) {
            Log.e(TAG, "QR encoding failed", e)
            return null
        }

        val modules = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            val x = i % matrix.width
            val y = i / matrix.width
            if (matrix.get(x, y)) Color.rgb(10, 11, 7) else Color.WHITE
        }
        modules.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)

        val bitmap = Bitmap.createBitmap(CARD_SIZE, CARD_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_SIZE.toFloat(),
                BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_SIZE.toFloat(), CARD_SIZE.toFloat(), bgPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 58f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val title = TextUtils.ellipsize(song.title, titlePaint, CARD_SIZE - 160f, TextUtils.TruncateAt.END)
        canvas.drawText(title, 0, title.length, CARD_SIZE / 2f, 175f, titlePaint)

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        val artist = TextUtils.ellipsize(song.artist, artistPaint, CARD_SIZE - 200f, TextUtils.TruncateAt.END)
        canvas.drawText(artist, 0, artist.length, CARD_SIZE / 2f, 238f, artistPaint)

        // White quiet-zone plate so the code has the contrast it needs.
        val plate = QR_PX + 96f
        val plateLeft = (CARD_SIZE - plate) / 2f
        val plateTop = 330f
        val plateRect = RectF(plateLeft, plateTop, plateLeft + plate, plateTop + plate)
        canvas.drawRoundRect(plateRect, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        })
        canvas.drawBitmap(
            modules,
            null,
            RectF(
                plateLeft + 48f,
                plateTop + 48f,
                plateLeft + 48f + QR_PX,
                plateTop + 48f + QR_PX
            ),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 38f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Pindai untuk buka di NOXA",
            CARD_SIZE / 2f,
            plateTop + plate + 90f,
            hintPaint
        )

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LIME
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }
        canvas.drawText("NOXA MUSIC", CARD_SIZE / 2f, CARD_SIZE - 90f, brandPaint)

        return bitmap
    }

    /**
     * **Bagikan lirik jadi gambar** — a tall story card with the verse around
     * the line being sung; the highlighted line lands in lime. Same flow as
     * the canvas card: render → gallery → system share sheet.
     * [highlightIndex] < 0 means "no highlight" (plain, unsynced lyrics).
     */
    suspend fun shareLyrics(
        context: Context,
        song: Song?,
        lines: List<String>,
        highlightIndex: Int
    ): Boolean {
        val file = try {
            val bitmap = renderLyricsCard(context, song, lines, highlightIndex) ?: return false
            saveToGallery(context, bitmap, safeName(song?.title ?: "lirik", "lyrics"))
            writeToCache(context, bitmap, "lyrics-${(song?.id ?: "card").hashCode()}")
        } catch (e: Exception) {
            Log.e(TAG, "Rendering/saving the lyric card failed", e)
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + FILE_PROVIDER_SUFFIX,
                file
            )
            val text = buildString {
                if (song != null) {
                    append('"').append(song.title).append('"').append(" — ").append(song.artist)
                    append('\n')
                    append(NoxaLinks.songLink(song))
                    append("\n\n")
                }
                append(lines.joinToString("\n"))
                append("\n\nDidengarkan lewat NOXA Music")
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "NOXA lyric card", uri)
            }
            val chooser = Intent.createChooser(send, "Bagikan lirik").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Opening the lyric share sheet failed", e)
            false
        }
    }

    /** Draws the 1080×1080 card. Returns null when nothing could be drawn. */
    internal suspend fun render(context: Context, song: Song): Bitmap? {
        val artwork = loadArtwork(context, song.artworkUrl)
        val bitmap = Bitmap.createBitmap(CARD_SIZE, CARD_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background: vertical theme gradient.
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_SIZE.toFloat(),
                BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_SIZE.toFloat(), CARD_SIZE.toFloat(), bgPaint)

        // Artwork: rounded square, centered.
        val artSize = 700f
        val artLeft = (CARD_SIZE - artSize) / 2f
        val artTop = 130f
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
        if (artwork != null) {
            val scaled = Bitmap.createScaledBitmap(artwork, artSize.toInt(), artSize.toInt(), true)
            val path = Path().apply { addRoundRect(artRect, 44f, 44f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(scaled, artLeft, artTop, Paint(Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG_TOP }
            canvas.drawRoundRect(artRect, 44f, 44f, placeholder)
        }

        // Vector "PINNED" badge for pinned tracks, top-left of the artwork:
        // a drawn five-point star + label — no emoji glyphs on the card.
        if (PinnedTracks.isPinned(song)) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIME }
            val badge = RectF(artLeft + 18f, artTop + 18f, artLeft + 172f, artTop + 88f)
            canvas.drawRoundRect(badge, 20f, 20f, badgePaint)
            drawStar(
                canvas = canvas,
                cx = badge.left + 35f,
                cy = badge.centerY(),
                outerRadius = 21f,
                paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
            )
            val starPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 38f
                textAlign = Paint.Align.LEFT
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("PINNED", badge.left + 64f, badge.centerY() + 13f, starPaint)
        }

        // Title (single line, ellipsized) and artist below the artwork.
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 62f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val title = TextUtils.ellipsize(song.title, titlePaint, CARD_SIZE - 160f, TextUtils.TruncateAt.END)
        canvas.drawText(title, 0, title.length, CARD_SIZE / 2f, 930f, titlePaint)

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 44f
            textAlign = Paint.Align.CENTER
        }
        val artist = TextUtils.ellipsize(song.artist, artistPaint, CARD_SIZE - 200f, TextUtils.TruncateAt.END)
        canvas.drawText(artist, 0, artist.length, CARD_SIZE / 2f, 1000f, artistPaint)

        // Lime divider + NOXA branding footer.
        val linePaint = Paint().apply { color = LIME }
        canvas.drawRect(CARD_SIZE / 2f - 60f, 1042f, CARD_SIZE / 2f + 60f, 1048f, linePaint)

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }
        canvas.drawText("NOXA MUSIC", CARD_SIZE / 2f, 1105f, brandPaint)

        val linkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("skyvibes.my.id", CARD_SIZE / 2f, 1150f, linkPaint)

        return bitmap
    }

    /**
     * Draws the 1080×1350 lyric card: compact song header, the verse in the
     * middle (highlighted line in lime and bold), NOXA footer. Every line is
     * ellipsized to one row — lyric lines are short by nature, and a wrapped
     * wall of text kills the card.
     */
    internal suspend fun renderLyricsCard(
        context: Context,
        song: Song?,
        lines: List<String>,
        highlightIndex: Int
    ): Bitmap? {
        val visible = lines.map { it.trim() }.filter { it.isNotBlank() }.take(LYRIC_CARD_MAX_LINES)
        if (visible.isEmpty()) return null
        val artwork = loadArtwork(context, song?.artworkUrl)
        val bitmap = Bitmap.createBitmap(CARD_SIZE, LYRIC_CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, LYRIC_CARD_HEIGHT.toFloat(),
                BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_SIZE.toFloat(), LYRIC_CARD_HEIGHT.toFloat(), bgPaint)

        // Header: small rounded artwork + title/artist to its right.
        val artRect = RectF(64f, 64f, 214f, 214f)
        if (artwork != null) {
            val side = artRect.width().toInt()
            val scaled = Bitmap.createScaledBitmap(artwork, side, side, true)
            val path = Path().apply { addRoundRect(artRect, 26f, 26f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(scaled, artRect.left, artRect.top, Paint(Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            canvas.drawRoundRect(artRect, 26f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG_TOP })
        }

        val maxTextWidth = CARD_SIZE - artRect.right - 128f
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 52f
            textAlign = Paint.Align.LEFT
            typeface = Typeface.DEFAULT_BOLD
        }
        val title = TextUtils.ellipsize(
            song?.title ?: "Lirik", titlePaint, maxTextWidth, TextUtils.TruncateAt.END
        )
        canvas.drawText(title, 0, title.length, artRect.right + 40f, 150f, titlePaint)

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 36f
            textAlign = Paint.Align.LEFT
        }
        val artist = TextUtils.ellipsize(
            song?.artist?.takeIf { it.isNotBlank() } ?: "NOXA Music",
            artistPaint,
            maxTextWidth,
            TextUtils.TruncateAt.END
        )
        canvas.drawText(artist, 0, artist.length, artRect.right + 40f, 206f, artistPaint)

        // The verse: highlighted line in lime & bold, the rest calm white.
        val highlight = if (highlightIndex in visible.indices) highlightIndex else -1
        var y = 430f
        for ((index, raw) in visible.withIndex()) {
            val isHighlight = index == highlight
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isHighlight) LIME else TEXT_PRIMARY
                if (!isHighlight) alpha = 190
                textSize = if (isHighlight) 58f else 42f
                textAlign = Paint.Align.CENTER
                typeface = if (isHighlight) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val line = TextUtils.ellipsize(raw, paint, CARD_SIZE - 140f, TextUtils.TruncateAt.END)
            canvas.drawText(line, 0, line.length, CARD_SIZE / 2f, y, paint)
            y += if (isHighlight) 98f else 80f
        }

        // Lime divider + NOXA branding footer (same language as the canvas card).
        val footerTop = LYRIC_CARD_HEIGHT - 190f
        val linePaint = Paint().apply { color = LIME }
        canvas.drawRect(CARD_SIZE / 2f - 60f, footerTop, CARD_SIZE / 2f + 60f, footerTop + 6f, linePaint)

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }
        canvas.drawText("NOXA MUSIC", CARD_SIZE / 2f, footerTop + 64f, brandPaint)

        val linkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("skyvibes.my.id", CARD_SIZE / 2f, footerTop + 110f, linkPaint)

        return bitmap
    }

    /**
     * **Kartu Profil NOXA** — the shareable identity card: avatar (photo or
     * drawn initial), name, role badge and the year's on-device listening
     * stats. Same render → gallery → share-sheet flow as the other cards.
     */
    suspend fun shareProfileCard(context: Context, data: ProfileCardData): Boolean {
        val file = try {
            val bitmap = renderProfileCard(context, data) ?: return false
            saveToGallery(context, bitmap, safeName(data.displayName, "profil"))
            writeToCache(context, bitmap, "profile-${data.displayName.hashCode()}")
        } catch (e: Exception) {
            Log.e(TAG, "Rendering/saving the profile card failed", e)
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + FILE_PROVIDER_SUFFIX,
                file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Kartu profil NOXA ${data.displayName} — ${data.totalPlays} lagu diputar. " +
                        "Didengarkan lewat NOXA Music"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "NOXA profile card", uri)
            }
            val chooser = Intent.createChooser(send, "Bagikan kartu profil").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Opening the profile share sheet failed", e)
            false
        }
    }

    /** Draws the 1080×1350 profile card. Avatar photo degrades to an initial. */
    internal suspend fun renderProfileCard(context: Context, data: ProfileCardData): Bitmap? {
        if (data.displayName.isBlank()) return null
        val photo: Bitmap? = if (data.photoUrl.isNullOrBlank()) {
            null
        } else {
            loadArtwork(context, data.photoUrl)
        }
        val bitmap = Bitmap.createBitmap(CARD_SIZE, LYRIC_CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, LYRIC_CARD_HEIGHT.toFloat(),
                BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_SIZE.toFloat(), LYRIC_CARD_HEIGHT.toFloat(), bgPaint)

        // Avatar: lime-ringed circle with the photo, or a drawn initial.
        val avatarCx = CARD_SIZE / 2f
        val avatarTop = 150f
        val avatarRadius = 150f
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LIME
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        val avatarCenterY = avatarTop + avatarRadius
        if (photo != null) {
            val scaled = Bitmap.createScaledBitmap(
                photo, (avatarRadius * 2).toInt(), (avatarRadius * 2).toInt(), true
            )
            val path = Path().apply {
                addCircle(avatarCx, avatarCenterY, avatarRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(scaled, avatarCx - avatarRadius, avatarCenterY - avatarRadius, Paint(Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG_TOP }
            canvas.drawCircle(avatarCx, avatarCenterY, avatarRadius, fallbackPaint)
            val initialPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = LIME
                textSize = 150f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            val initial = data.displayName.trim().take(1).uppercase()
            canvas.drawText(initial, avatarCx, avatarCenterY + 52f, initialPaint)
        }
        canvas.drawCircle(avatarCx, avatarCenterY, avatarRadius, ringPaint)

        // Name + subtitle.
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 60f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val name = TextUtils.ellipsize(data.displayName, namePaint, CARD_SIZE - 160f, TextUtils.TruncateAt.END)
        canvas.drawText(name, 0, name.length, avatarCx, avatarTop + avatarRadius * 2 + 92f, namePaint)

        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 34f
            textAlign = Paint.Align.CENTER
        }
        if (!data.subtitle.isNullOrBlank()) {
            val subtitle = TextUtils.ellipsize(
                data.subtitle, subtitlePaint, CARD_SIZE - 200f, TextUtils.TruncateAt.END
            )
            canvas.drawText(subtitle, 0, subtitle.length, avatarCx, avatarTop + avatarRadius * 2 + 148f, subtitlePaint)
        }

        // DEVELOPER pill (vector-only, lime on black).
        var statsTop = avatarTop + avatarRadius * 2 + 210f
        if (data.isDeveloper) {
            val pillWidth = 320f
            val pillHeight = 66f
            val pill = RectF(
                avatarCx - pillWidth / 2f, statsTop,
                avatarCx + pillWidth / 2f, statsTop + pillHeight
            )
            canvas.drawRoundRect(pill, 33f, 33f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIME })
            val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 34f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.1f
            }
            canvas.drawText("DEVELOPER", avatarCx, pill.centerY() + 12f, pillTextPaint)
            statsTop += pillHeight + 56f
        }

        // Stats rows — only what actually exists; empty stats shrink the card
        // content instead of showing zeros.
        val rowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        val accentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LIME
            textSize = 44f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        var y = statsTop
        fun drawRow(text: String, accent: Boolean) {
            val paint = if (accent) accentPaint else rowPaint
            val line = TextUtils.ellipsize(text, paint, CARD_SIZE - 140f, TextUtils.TruncateAt.END)
            canvas.drawText(line, 0, line.length, avatarCx, y, paint)
            y += 84f
        }
        if (data.totalPlays > 0) drawRow("${data.totalPlays} lagu diputar tahun ini", accent = true)
        data.topArtist?.let { drawRow("Artis teratas: $it", accent = false) }
        data.topSong?.let { drawRow("Lagu paling sering: $it", accent = false) }
        if (data.distinctArtists > 0) drawRow("${data.distinctArtists} artis berbeda", accent = false)
        if (data.longestStreakDays > 0) {
            drawRow("Streak terpanjang: ${data.longestStreakDays} hari", accent = false)
        }

        // Lime divider + NOXA branding footer (shared language with the cards).
        val footerTop = LYRIC_CARD_HEIGHT - 190f
        val linePaint = Paint().apply { color = LIME }
        canvas.drawRect(CARD_SIZE / 2f - 60f, footerTop, CARD_SIZE / 2f + 60f, footerTop + 6f, linePaint)

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }
        canvas.drawText("NOXA MUSIC", CARD_SIZE / 2f, footerTop + 64f, brandPaint)

        val linkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("skyvibes.my.id", CARD_SIZE / 2f, footerTop + 110f, linkPaint)

        return bitmap
    }

    /** Artwork via Coil; any failure degrades to the drawn placeholder. */
    private suspend fun loadArtwork(context: Context, url: String?): Bitmap? = try {
        val loader: ImageLoader = context.imageLoader
        val drawable = loader.execute(
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(false)
                .build()
        ).drawable
        drawable?.toBitmap()
    } catch (e: Exception) {
        null
    }

    /** Draws a clean five-point star (vector-style, not an emoji glyph). */
    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, outerRadius: Float, paint: Paint) {
        val path = Path()
        val innerRadius = outerRadius * 0.42f
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val angle = Math.toRadians((-90 + i * 36).toDouble())
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    /**
     * Best-effort gallery save (`Pictures/NOXA`). On Android 10+ this needs no
     * permission; below that it needs WRITE_EXTERNAL_STORAGE, so failures are
     * swallowed — the share sheet still works through the FileProvider copy.
     */
    private fun saveToGallery(context: Context, bitmap: Bitmap, baseName: String): Uri? {
        val name = "NOXA-$baseName.png"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NOXA")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "NOXA"
                )
                dir.mkdirs()
                val file = File(dir, name)
                file.outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.insertImage(context.contentResolver, file.absolutePath, name, null)
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** The FileProvider copy the share sheet actually hands out. */
    private fun writeToCache(context: Context, bitmap: Bitmap, key: String): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "noxa-share-$key.png")
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return file
    }

    /** Filesystem-safe display name for a card title. */
    private fun safeName(title: String, fallback: String): String = title
        .replace(Regex("[^A-Za-z0-9 _-]"), "")
        .trim()
        .replace(' ', '-')
        .take(40)
        .ifBlank { fallback }
}
