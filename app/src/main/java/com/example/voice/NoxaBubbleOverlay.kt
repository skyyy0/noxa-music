package com.example.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Bubble NOXA Wake — orb lingkaran dengan gradien yang berputar yang muncul
 * di atas aplikasi lain saat kata bangun "Hi NOXA" terdengar.
 *
 * Sengaja dibangun dengan View + WindowManager biasa (bukan ComposeView):
 * window overlay milik service tidak punya LifecycleOwner/BackDispatcher milik
 * activity, dan View programatik paling tidak bergantung pada hal itu. Semua
 * pemanggilan dari service di main thread — aman untuk View.
 *
 * Butuh izin `SYSTEM_ALERT_WINDOW` (dicek service sebelum show; kalau izin
 * dicabut di tengah jalan, [show] diam-diam gagal dan service tetap jalan
 * dengan feedback lewat notifikasi).
 */
class NoxaBubbleOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var container: LinearLayout? = null
    private var captionView: TextView? = null
    private var orbView: NoxaOrbView? = null

    val isShowing: Boolean get() = container != null

    /** Tampilkan orb (sekali); kalau sudah tampil tidak ditumpuk. */
    fun show() {
        val existing = container
        if (existing != null) {
            if (existing.isAttachedToWindow) return
            // Window basi: view sudah dilepas sistem (task ditutup / OEM
            // membuang overlay saat app exit) tapi referensinya masih ada —
            // check “sudah tampil” lama membuat bubble tidak pernah muncul
            // lagi walaupun wake word terdeteksi. Buang dulu, pasang ulang.
            hide()
        }
        try {
            val metrics = context.resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            // Lebar kontainer mengikuti layar (dibatasi 400dp): caption panjang
            // membungkus ke maksimal 3 baris DI DALAM layar, bukan melebar
            // keluar tepi seperti sebelumnya (WRAP_CONTENT tanpa batas).
            val containerWidth = min(screenWidth - dp(24), dp(400))
            val orbSize = min(dp(140), (screenWidth * 0.4f).toInt())

            val orb = NoxaOrbView(context)
            val caption = TextView(context).apply {
                visibility = TextView.GONE
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 3
                maxWidth = containerWidth - dp(16)
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(16), dp(9), dp(16), dp(9))
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(0xE60C0C0F.toInt())
                    setStroke(dp(1), 0x40C6FF00)
                }
            }
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(orb, LinearLayout.LayoutParams(orbSize, orbSize))
                addView(
                    caption,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(14) }
                )
            }
            windowManager.addView(root, layoutParams(containerWidth, screenHeight))
            container = root
            orbView = orb
            captionView = caption
        } catch (e: Exception) {
            // Izin overlay dicabut / token window tidak valid: fitur tetap
            // jalan tanpa bubble (service memberi lewat notifikasi) — dan
            // kegagalannya dicatat, bukan ditelan diam-diam.
            Log.w(TAG, "bubble gagal tampil: ${e.javaClass.simpleName}: ${e.message}")
            container = null
            orbView = null
            captionView = null
        }
    }

    /** Teks di bawah orb — transcript sementara atau pesan hasil perintah. */
    fun setCaption(text: String) {
        val view = captionView ?: return
        view.text = text
        view.visibility = if (text.isBlank()) TextView.GONE else TextView.VISIBLE
    }

    /**
     * Amplitudo ucapan (dari `onRmsChanged`, rmsdB −2…+10) → bar di dalam orb.
     * Dipanggil dari main thread; orbid sendiri animasi ~60fps via animator.
     */
    fun setLevel(rmsDb: Float) {
        orbView?.setLevel(((rmsDb + 2f) / 12f).coerceIn(0f, 1f))
    }

    fun hide() {
        val root = container ?: return
        container = null
        orbView = null
        captionView = null
        try {
            windowManager.removeView(root)
        } catch (_: Exception) {
            // Sudah lepas sendiri — tidak ada yang perlu dilakukan.
        }
    }

    private fun layoutParams(widthPx: Int, screenHeightPx: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
        return WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // NOT_TOUCH_MODAL: ketukan di LUAR bubble diteruskan ke app di
            // belakang (main game/WhatsApp tetap responsif); bubble sendiri
            // masih bisa disentuh untuk menutup.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            // Posisi mengikuti tinggi layar: dasar bubble 25% dari bawah —
            // di atas kontrol game / navigation bar, tidak menutupi tengah
            // layar seperti penempatan CENTER sebelumnya.
            gravity = Gravity.BOTTOM
            x = 0
            y = (screenHeightPx * 0.25f).toInt()
            // Tetap terbaca saat layar terkunci (Hi NOXA sering diucapkan
            // dengan layar menyala/terkunci) — flag system, bukan overlay tiruan.
            @Suppress("DEPRECATION")
            val showWhenLocked = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            flags = flags or showWhenLocked
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt()

    private companion object {
        const val TAG = "NoxaBubbleOverlay"
    }
}

/**
 * Orb wake: halo berdenyut, cincin dengan sweep-gradient yang berputar terus,
 * cakram gelap di tengah, dan lima bar level suara yang bergerak mengikuti
 * rms ucapan. Murni digambar di Canvas (tanpa resource), jalan di semua API 24+.
 */
class NoxaOrbView(context: Context) : View(context) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpf(9f)
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF20B0B0D.toInt() }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpf(4f)
        strokeCap = Paint.Cap.ROUND
        color = COLOR_LIME
    }

    /** Derajat rotasi gradien; maju terus selama view terpasang. */
    private var phase = 0f

    /** Level suara 0..1 (dari recognizer). */
    private var level = 0.3f

    private var sweep: SweepGradient? = null
    private var sweepRadius = 0f
    private var animator: ValueAnimator? = null

    fun setLevel(value: Float) {
        level = value.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 5_000
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                phase = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
        }.also { it.start() }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val ringRadius = min(cx, cy) - dpf(8f)
        val pulse = ((sin(phase * PI / 180.0 * 2.0) + 1.0) / 2.0).toFloat()

        // Halo lime yang membesar-mengecil pelan di belakang cincin.
        val glowRadius = ringRadius * (1.18f + 0.14f * pulse)
        if (glowPaint.shader == null || glowRadius != glowRadiusCached) {
            glowRadiusCached = glowRadius
            glowPaint.shader = RadialGradient(
                cx, cy, glowRadius,
                intArrayOf(0x66C6FF00, 0x22C6FF00, 0x00C6FF00),
                floatArrayOf(0.55f, 0.8f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        glowPaint.alpha = (140 + 90 * pulse).toInt()
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        // Cincin gradien muter (lime → cyan → ungu → lime).
        if (sweep == null || ringRadius != sweepRadius) {
            sweepRadius = ringRadius
            sweep = SweepGradient(
                cx, cy,
                intArrayOf(COLOR_LIME, COLOR_CYAN, COLOR_PURPLE, COLOR_LIME),
                floatArrayOf(0f, 0.35f, 0.7f, 1f)
            )
        }
        val matrix = android.graphics.Matrix()
        matrix.postRotate(phase, cx, cy)
        sweep?.setLocalMatrix(matrix)
        ringPaint.shader = sweep
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)

        // Cakram gelap di tengah — latar untuk bar suara.
        canvas.drawCircle(cx, cy, ringRadius - ringPaint.strokeWidth / 2f, discPaint)

        // Lima bar level suara yang bergoyang mengikuti phase & rms.
        val bars = 5
        val span = (ringRadius - ringPaint.strokeWidth) * 1.1f
        val step = span / bars
        val startX = cx - span / 2f + step / 2f
        for (i in 0 until bars) {
            val wave = (sin(phase * PI / 180.0 * 6.0 + i * 1.1) + 1.0) / 2.0
            val h = (ringRadius * 0.16f + ringRadius * 0.42f * level * wave.toFloat())
            val x = startX + step * i
            canvas.drawLine(x, cy - h, x, cy + h, barPaint)
        }
    }

    private var glowRadiusCached = 0f

    private fun dpf(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
    )

    private companion object {
        // Palet identitas NOXA — sengaja sama dengan Compose theme.
        const val COLOR_LIME = 0xFFC6FF00.toInt()
        const val COLOR_CYAN = 0xFF22D3EE.toInt()
        const val COLOR_PURPLE = 0xFFA855F7.toInt()
    }
}
