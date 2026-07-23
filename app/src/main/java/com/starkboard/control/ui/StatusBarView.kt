package com.starkboard.control.ui

import android.content.*
import android.graphics.*
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.*

/**
 * iPhone 16-style status bar.
 * Left: time | Center: Dynamic Island pill | Right: signal + wifi + battery-bar-with-%-inside
 * Handles left/right swipe gestures to open notification / control center.
 */
class StatusBarView(
    context: Context,
    private val onLeftSwipe: () -> Unit,
    private val onRightSwipe: () -> Unit
) : View(context) {

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }
    private val islandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }
    private val battBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
    }
    private val battFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val battTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())
    private var batteryLevel = 100
    private var isCharging = false
    private var wifiLevel = -1   // -1=off, 0-3 bars
    private var cellLevel = 2    // 0-4

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (Math.abs(dy) > Math.abs(dx) && vy > 300f) {
                    // Swipe down — pick left or right half
                    if (e1.x < width / 2f) onLeftSwipe() else onRightSwipe()
                    return true
                }
                return false
            }
        })

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateNetwork()
            invalidate()
            handler.postDelayed(this, 30_000)
        }
    }

    private val battReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            invalidate()
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(battReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        handler.post(tickRunnable)
        updateNetwork()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { context.unregisterReceiver(battReceiver) } catch (_: Exception) {}
        handler.removeCallbacks(tickRunnable)
    }

    fun refreshNetwork() { updateNetwork(); invalidate() }

    private fun updateNetwork() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLevel = if (wm.isWifiEnabled) {
                val info = wm.connectionInfo
                if (info != null && info.networkId != -1)
                    WifiManager.calculateSignalLevel(info.rssi, 4).coerceIn(1, 3)
                else 0
            } else -1
        } catch (_: Exception) { wifiLevel = -1 }
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            cellLevel = tm.signalStrength?.level ?: 2
        } catch (_: Exception) { cellLevel = 2 }
    }

    override fun onTouchEvent(event: MotionEvent) = gestureDetector.onTouchEvent(event)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        val pad = 14f * d       // left/right padding
        val midY = h / 2f

        // ── Time (left) ───────────────────────────────────────────────
        val timeStr = timeFormat.format(Date())
        timePaint.textSize = h * 0.52f
        val timeY = midY + timePaint.textSize * 0.36f
        canvas.drawText(timeStr, pad, timeY, timePaint)

        // ── Dynamic Island pill (center) ──────────────────────────────
        val pillW = w * 0.28f
        val pillH = h * 0.72f
        val pillLeft = (w - pillW) / 2f
        val pillTop = (h - pillH) / 2f
        canvas.drawRoundRect(
            RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH),
            pillH / 2f, pillH / 2f, islandPaint
        )

        // ── Right-side icons ──────────────────────────────────────────
        val rightPad = pad
        var rx = w - rightPad

        // Battery bar (rightmost)
        val battW = 28f * d
        val battH = 14f * d
        val nubW = 2.5f * d
        val nubH = 6f * d
        val battTop = midY - battH / 2f
        val battLeft = rx - battW - nubW
        val cornerR = 3.5f * d

        // Nub (positive terminal)
        val nubTop = midY - nubH / 2f
        canvas.drawRoundRect(
            RectF(battLeft + battW, nubTop, battLeft + battW + nubW, nubTop + nubH),
            1.5f * d, 1.5f * d, battBorderPaint
        )

        // Outline
        battBorderPaint.strokeWidth = 1.8f
        canvas.drawRoundRect(
            RectF(battLeft, battTop, battLeft + battW, battTop + battH),
            cornerR, cornerR, battBorderPaint
        )

        // Fill
        val border = 2.5f * d
        val maxFillW = battW - border * 2
        val fillW = maxFillW * (batteryLevel / 100f)
        battFillPaint.color = when {
            isCharging -> Color.parseColor("#32D74B")
            batteryLevel <= 20 -> Color.parseColor("#FF453A")
            batteryLevel <= 40 -> Color.parseColor("#FF9F0A")
            else -> Color.WHITE
        }
        if (fillW > cornerR) {
            canvas.drawRoundRect(
                RectF(battLeft + border, battTop + border,
                    battLeft + border + fillW, battTop + battH - border),
                cornerR - border, cornerR - border, battFillPaint
            )
        }

        // Percentage text inside battery
        battTextPaint.textSize = battH * 0.62f
        battTextPaint.color = if (batteryLevel > 30) Color.BLACK else Color.WHITE
        canvas.drawText(
            "$batteryLevel",
            battLeft + battW / 2f,
            battTop + battH / 2f + battTextPaint.textSize * 0.36f,
            battTextPaint
        )

        rx = battLeft - 8f * d

        // WiFi icon
        if (wifiLevel >= 0) {
            rx = drawWifi(canvas, rx, midY, h * 0.42f, d, wifiLevel)
            rx -= 6f * d
        }

        // Signal bars (cell)
        rx = drawSignalBars(canvas, rx, midY, h * 0.46f, d, cellLevel)
    }

    /** Draws 4 rising bars for cellular signal. Returns new left edge. */
    private fun drawSignalBars(canvas: Canvas, rightX: Float, midY: Float, totalH: Float, d: Float, level: Int): Float {
        val barCount = 4
        val barW = 3f * d
        val gap = 2f * d
        val totalW = barCount * barW + (barCount - 1) * gap
        val baseY = midY + totalH / 2f

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        for (i in 0 until barCount) {
            val barH = totalH * ((i + 1).toFloat() / barCount)
            val left = rightX - totalW + i * (barW + gap)
            p.color = if (i < level) Color.WHITE else Color.argb(80, 255, 255, 255)
            canvas.drawRoundRect(
                RectF(left, baseY - barH, left + barW, baseY),
                1.5f * d, 1.5f * d, p
            )
        }
        return rightX - totalW - 0f
    }

    /** Draws iOS-style WiFi arcs. Returns new left edge. */
    private fun drawWifi(canvas: Canvas, rightX: Float, midY: Float, size: Float, d: Float, level: Int): Float {
        val cx = rightX - size / 2f
        val baseY = midY + size * 0.3f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        // 3 arcs + dot
        val arcCount = 3
        for (i in 0 until arcCount) {
            val r = size * (i + 1) / (arcCount + 1)
            p.strokeWidth = 1.8f * d
            p.color = if ((arcCount - i) <= level) Color.WHITE else Color.argb(80, 255, 255, 255)
            canvas.drawArc(
                RectF(cx - r, baseY - r, cx + r, baseY + r),
                200f, 140f, false, p
            )
        }
        // Dot
        val dotR = 2f * d
        p.style = Paint.Style.FILL
        p.color = if (level > 0) Color.WHITE else Color.argb(80, 255, 255, 255)
        canvas.drawCircle(cx, baseY + dotR * 0.5f, dotR, p)
        return rightX - size
    }
}
