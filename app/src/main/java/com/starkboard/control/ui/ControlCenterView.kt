package com.starkboard.control.ui

import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.starkboard.control.toggles.*

class ControlCenterView(context: Context, private val onDismiss: () -> Unit) : FrameLayout(context) {

    // ── Colors ─────────────────────────────────────────────────────────
    private val BG_COLOR = Color.parseColor("#E01C1C1E")
    private val TILE_OFF = Color.parseColor("#FF2C2C2E")
    private val TILE_ON = Color.parseColor("#FFFFFFFF")
    private val TILE_ACCENT_ON = Color.parseColor("#FF1C74E8")   // iOS blue
    private val TEXT_OFF = Color.parseColor("#FFFFFFFF")
    private val TEXT_ON = Color.parseColor("#FF000000")
    private val TEXT_ON_ACCENT = Color.parseColor("#FFFFFFFF")
    private val SLIDER_BG = Color.parseColor("#FF3A3A3C")
    private val SLIDER_FG = Color.parseColor("#FFFFFFFF")

    // ── Paints ─────────────────────────────────────────────────────────
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG_COLOR }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val sliderBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SLIDER_BG }
    private val sliderFgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SLIDER_FG }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // ── State ──────────────────────────────────────────────────────────
    private var wifiOn = false
    private var dataOn = false
    private var btOn = false
    private var airplaneOn = false
    private var flashOn = false
    private var rotLocked = false
    private var dndOn = false
    private var brightness = 0.5f
    private var volume = 0.5f

    // ── Gesture ────────────────────────────────────────────────────────
    private var touchTileIndex = -1
    private var touchDown = PointF()
    private var translationY_ = 0f

    // ── Layout ─────────────────────────────────────────────────────────
    // Calculated in onLayout
    private var panelLeft = 0f
    private var panelTop = 0f
    private var panelRight = 0f
    private var panelBottom = 0f
    private var panelRadius = 0f
    private val tiles = mutableListOf<TileInfo>()
    private var brightRect = RectF()
    private var volRect = RectF()
    private var draggingBrightness = false
    private var draggingVolume = false

    data class TileInfo(
        val index: Int,
        val rect: RectF,
        val label: String,
        var isOn: Boolean,
        var useAccent: Boolean = false
    )

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)
        refreshState()
    }

    fun refreshState() {
        wifiOn = try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.isWifiEnabled
        } catch (e: Exception) { false }
        dataOn = MobileDataToggle.isEnabled(context)
        btOn = BluetoothToggle.isEnabled(context)
        airplaneOn = AirplaneModeToggle.isEnabled(context)
        flashOn = FlashlightToggle.isEnabled()
        rotLocked = RotationToggle.isLocked(context)
        dndOn = DndToggle.isEnabled(context)

        // Brightness
        brightness = try {
            val b = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            b / 255f
        } catch (e: Exception) { 0.5f }

        // Volume
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volume = am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()

        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        buildLayout(w.toFloat(), h.toFloat())
    }

    private fun buildLayout(w: Float, h: Float) {
        val margin = w * 0.04f
        val panelW = w - margin * 2f
        panelRadius = 32f

        // Panel floats about 1/3 from top
        panelLeft = margin
        panelRight = w - margin
        panelTop = h * 0.02f
        panelBottom = h * 0.68f

        val innerL = panelLeft + 18f
        val innerR = panelRight - 18f
        val innerT = panelTop + 22f

        val gap = 12f

        // ── Big 2x2 connectivity block ──────────────────────────────
        val bigBlockW = (innerR - innerL) * 0.52f
        val bigTileW = (bigBlockW - gap) / 2f
        val bigTileH = bigTileW

        tiles.clear()
        // WiFi
        tiles.add(TileInfo(0, RectF(innerL, innerT, innerL + bigTileW, innerT + bigTileH), "Wi-Fi", wifiOn))
        // Cellular
        tiles.add(TileInfo(1, RectF(innerL + bigTileW + gap, innerT, innerL + bigBlockW, innerT + bigTileH), "Cellular", dataOn, useAccent = false))
        // Bluetooth
        tiles.add(TileInfo(2, RectF(innerL, innerT + bigTileH + gap, innerL + bigTileW, innerT + bigTileH * 2 + gap), "Bluetooth", btOn))
        // Airplane
        tiles.add(TileInfo(3, RectF(innerL + bigTileW + gap, innerT + bigTileH + gap, innerL + bigBlockW, innerT + bigTileH * 2 + gap), "Airplane", airplaneOn))

        // ── Small tiles column ──────────────────────────────────────
        val smallX = innerL + bigBlockW + gap
        val smallW = innerR - smallX
        val bigBlock2H = bigTileH * 2 + gap
        val smallTileH = (bigBlock2H - gap * 2) / 3f
        // Flashlight
        tiles.add(TileInfo(4, RectF(smallX, innerT, innerR, innerT + smallTileH), "Flash", flashOn))
        // Rotation Lock
        tiles.add(TileInfo(5, RectF(smallX, innerT + smallTileH + gap, innerR, innerT + smallTileH * 2 + gap), "Rotate", rotLocked))
        // DND
        tiles.add(TileInfo(6, RectF(smallX, innerT + (smallTileH + gap) * 2, innerR, innerT + bigBlock2H), "Focus", dndOn))

        val sliderT = innerT + bigTileH * 2 + gap + 18f
        val sliderH = 52f

        // Brightness slider
        brightRect = RectF(innerL, sliderT, innerR, sliderT + sliderH)
        // Volume slider
        volRect = RectF(innerL, sliderT + sliderH + gap, innerR, sliderT + sliderH * 2 + gap)

        panelBottom = volRect.bottom + 22f
    }

    override fun onDraw(canvas: Canvas) {
        // Dim scrim
        canvas.drawColor(Color.argb(160, 0, 0, 0))

        // Panel background
        val panelRect = RectF(panelLeft, panelTop + translationY_, panelRight, panelBottom + translationY_)
        canvas.drawRoundRect(panelRect, panelRadius, panelRadius, bgPaint)

        val dy = translationY_

        // Tiles
        for (tile in tiles) {
            val r = RectF(tile.rect).apply { offset(0f, dy) }
            drawTile(canvas, r, tile)
        }

        // Sliders
        drawSlider(canvas, RectF(brightRect).apply { offset(0f, dy) }, brightness, "Brightness", isBrightness = true)
        drawSlider(canvas, RectF(volRect).apply { offset(0f, dy) }, volume, "Volume", isBrightness = false)
    }

    private fun drawTile(canvas: Canvas, r: RectF, tile: TileInfo) {
        // Background
        val bgColor = when {
            tile.isOn && tile.useAccent -> TILE_ACCENT_ON
            tile.isOn -> TILE_ON
            else -> TILE_OFF
        }
        tilePaint.color = bgColor
        canvas.drawRoundRect(r, 20f, 20f, tilePaint)

        // Icon
        val cx = r.centerX()
        val cy = r.centerY() - 8f
        val iconColor = when {
            tile.isOn && tile.useAccent -> TEXT_ON_ACCENT
            tile.isOn -> TEXT_ON
            else -> TEXT_OFF
        }
        iconPaint.color = iconColor
        drawTileIcon(canvas, tile.index, tile.isOn, cx, cy, r.width() * 0.22f)

        // Label
        textPaint.color = iconColor
        textPaint.textSize = r.height() * 0.16f
        canvas.drawText(tile.label, r.centerX(), r.bottom - r.height() * 0.12f, textPaint)
    }

    private fun drawTileIcon(canvas: Canvas, index: Int, on: Boolean, cx: Float, cy: Float, size: Float) {
        val p = Paint(iconPaint).apply { strokeWidth = size * 0.15f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val fp = Paint(iconPaint).apply { style = Paint.Style.FILL }
        when (index) {
            0 -> { // WiFi arcs
                for (i in 0..2) {
                    val r = size * 0.35f + i * size * 0.3f
                    val arc = RectF(cx - r, cy - r, cx + r, cy + r)
                    p.alpha = if (on) 255 else 160
                    canvas.drawArc(arc, 210f, 120f, false, p)
                }
                canvas.drawCircle(cx, cy + size * 0.35f, size * 0.12f, fp)
            }
            1 -> { // Cell bars
                val barCount = 4
                val totalW = size * 0.9f
                val bw = totalW / (barCount * 2 - 1)
                val startX = cx - totalW / 2f
                val bottom = cy + size * 0.4f
                for (i in 0 until barCount) {
                    val bh = size * 0.2f + i * size * 0.15f
                    val bx = startX + i * bw * 2
                    fp.alpha = if (on) 255 else 160
                    canvas.drawRoundRect(RectF(bx, bottom - bh, bx + bw, bottom), 2f, 2f, fp)
                }
            }
            2 -> { // Bluetooth B shape
                val bPath = Path().apply {
                    moveTo(cx - size * 0.15f, cy - size * 0.45f)
                    lineTo(cx + size * 0.2f, cy - size * 0.2f)
                    lineTo(cx - size * 0.1f, cy)
                    lineTo(cx + size * 0.2f, cy + size * 0.2f)
                    lineTo(cx - size * 0.15f, cy + size * 0.45f)
                    moveTo(cx - size * 0.15f, cy)
                    lineTo(cx + size * 0.2f, cy)
                }
                canvas.drawPath(bPath, p)
            }
            3 -> { // Airplane
                val aPath = Path().apply {
                    moveTo(cx, cy - size * 0.5f)
                    lineTo(cx + size * 0.5f, cy + size * 0.2f)
                    lineTo(cx, cy)
                    lineTo(cx - size * 0.5f, cy + size * 0.2f)
                    close()
                    moveTo(cx - size * 0.25f, cy + size * 0.3f)
                    lineTo(cx + size * 0.25f, cy + size * 0.3f)
                }
                canvas.drawPath(aPath, fp)
            }
            4 -> { // Flashlight
                canvas.drawRoundRect(RectF(cx - size*0.15f, cy - size*0.5f, cx + size*0.15f, cy + size*0.35f), 6f, 6f, fp)
                canvas.drawRoundRect(RectF(cx - size*0.25f, cy + size*0.25f, cx + size*0.25f, cy + size*0.5f), 4f, 4f, fp)
            }
            5 -> { // Rotation lock
                val circRect = RectF(cx - size*0.4f, cy - size*0.4f, cx + size*0.4f, cy + size*0.4f)
                canvas.drawArc(circRect, 30f, 270f, false, p)
                // Arrow head
                canvas.drawCircle(cx + size*0.4f, cy - size*0.05f, size*0.12f, fp)
                // Lock
                canvas.drawRoundRect(RectF(cx - size*0.15f, cy - size*0.1f, cx + size*0.15f, cy + size*0.2f), 4f, 4f, fp)
            }
            6 -> { // Moon (DND)
                val moonPath = Path().apply {
                    addArc(RectF(cx - size*0.4f, cy - size*0.45f, cx + size*0.4f, cy + size*0.45f), 40f, 280f)
                    lineTo(cx, cy - size*0.45f)
                    addArc(RectF(cx - size*0.15f, cy - size*0.35f, cx + size*0.3f, cy + size*0.15f), 320f, -230f)
                    close()
                }
                canvas.drawPath(moonPath, fp)
            }
        }
    }

    private fun drawSlider(canvas: Canvas, r: RectF, value: Float, label: String, isBrightness: Boolean) {
        // Track
        canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f, sliderBgPaint)
        // Fill
        val fillW = r.width() * value.coerceIn(0f, 1f)
        val fillRect = RectF(r.left, r.top, r.left + fillW, r.bottom)
        canvas.drawRoundRect(fillRect, r.height() / 2f, r.height() / 2f, sliderFgPaint)
        // Icon + label
        val iconP = Paint(iconPaint).apply { color = if (value > 0.15f) Color.BLACK else Color.WHITE; style = Paint.Style.FILL }
        val cx = r.left + r.height() / 2f
        val cy = r.centerY()
        val iSize = r.height() * 0.3f
        if (isBrightness) {
            // Sun icon
            canvas.drawCircle(cx, cy, iSize * 0.5f, iconP)
        } else {
            // Speaker icon
            val sp = Path().apply {
                moveTo(cx - iSize * 0.5f, cy - iSize * 0.4f)
                lineTo(cx, cy - iSize * 0.4f)
                lineTo(cx + iSize * 0.5f, cy - iSize * 0.8f)
                lineTo(cx + iSize * 0.5f, cy + iSize * 0.8f)
                lineTo(cx, cy + iSize * 0.4f)
                lineTo(cx - iSize * 0.5f, cy + iSize * 0.4f)
                close()
            }
            canvas.drawPath(sp, iconP)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDown.set(event.x, event.y)
                touchTileIndex = hitTile(event.x, event.y - translationY_)
                draggingBrightness = hitRect(event.x, event.y - translationY_, brightRect)
                draggingVolume = hitRect(event.x, event.y - translationY_, volRect)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - touchDown.y
                if (draggingBrightness) {
                    val pct = (event.x - brightRect.left) / brightRect.width()
                    brightness = pct.coerceIn(0f, 1f)
                    applyBrightness()
                    invalidate()
                    return true
                }
                if (draggingVolume) {
                    val pct = (event.x - volRect.left) / volRect.width()
                    volume = pct.coerceIn(0f, 1f)
                    applyVolume()
                    invalidate()
                    return true
                }
                if (!draggingBrightness && !draggingVolume && dy > 40f) {
                    translationY_ = (dy - 40f).coerceAtMost(height * 0.3f)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = event.y - touchDown.y
                if (!draggingBrightness && !draggingVolume) {
                    if (dy > height * 0.15f) {
                        animateDismiss()
                    } else {
                        // Check tap on tile
                        val tileIdx = hitTile(event.x, event.y - translationY_)
                        if (tileIdx >= 0 && kotlin.math.abs(dy) < 20f) {
                            handleTileTap(tileIdx)
                        } else if (tileIdx < 0 && !hitPanel(event.x, event.y)) {
                            animateDismiss()
                        } else {
                            snapBack()
                        }
                    }
                }
                draggingBrightness = false
                draggingVolume = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitRect(x: Float, y: Float, r: RectF): Boolean = r.contains(x, y)

    private fun hitPanel(x: Float, y: Float): Boolean =
        x >= panelLeft && x <= panelRight && y >= panelTop && y <= panelBottom

    private fun hitTile(x: Float, y: Float): Int {
        for (tile in tiles) {
            if (tile.rect.contains(x, y)) return tile.index
        }
        return -1
    }

    private fun handleTileTap(index: Int) {
        when (index) {
            0 -> { WifiToggle.toggle(context); wifiOn = WifiToggle.isEnabled(context) }
            1 -> { MobileDataToggle.toggle(context); dataOn = MobileDataToggle.isEnabled(context) }
            2 -> { BluetoothToggle.toggle(context); btOn = BluetoothToggle.isEnabled(context) }
            3 -> { AirplaneModeToggle.toggle(context); airplaneOn = AirplaneModeToggle.isEnabled(context) }
            4 -> { FlashlightToggle.toggle(context); flashOn = FlashlightToggle.isEnabled() }
            5 -> { RotationToggle.toggle(context); rotLocked = RotationToggle.isLocked(context) }
            6 -> { DndToggle.toggle(context); dndOn = DndToggle.isEnabled(context) }
        }
        tiles.find { it.index == index }?.isOn = when (index) {
            0 -> wifiOn; 1 -> dataOn; 2 -> btOn; 3 -> airplaneOn
            4 -> flashOn; 5 -> rotLocked; 6 -> dndOn; else -> false
        }
        invalidate()
    }

    private fun applyBrightness() {
        try {
            val b = (brightness * 255).toInt().coerceIn(1, 255)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, b)
        } catch (e: Exception) { /* needs WRITE_SETTINGS */ }
    }

    private fun applyVolume() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).toInt(), 0)
    }

    private fun animateDismiss() {
        val start = translationY_
        val end = height.toFloat()
        ValueAnimator.ofFloat(start, end).apply {
            duration = 280
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                translationY_ = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    onDismiss()
                }
            })
            start()
        }
    }

    private fun snapBack() {
        ValueAnimator.ofFloat(translationY_, 0f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                translationY_ = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun animateIn() {
        translationY_ = -height.toFloat() * 0.5f
        ValueAnimator.ofFloat(translationY_, 0f).apply {
            duration = 320
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                translationY_ = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
