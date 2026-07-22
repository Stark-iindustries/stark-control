package com.starkboard.control.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.view.View
import java.text.SimpleDateFormat
import java.util.*

/**
 * iPhone 16-style status bar overlay.
 * Left side: time (bold). Right side: signal bars, wifi arcs, battery.
 * Center: Dynamic Island pill (purely cosmetic — matches the iPhone 16 notch shape).
 */
class StatusBarView(context: Context) : View(context) {

    // ── Paints ────────────────────────────────────────────────────────
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val islandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }
    private val battBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val battFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())

    private var batteryLevel = 100
    private var isCharging = false
    private var wifiLevel = -1   // 0-3 bars, -1 = off/disconnected
    private var cellLevel = -1   // 0-4 bars, -1 = no service
    private var cellType = "5G"

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateNetworkStatus()
            invalidate()
            handler.postDelayed(this, 30_000)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL
            invalidate()
        }
    }

    init { setBackgroundColor(Color.BLACK) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        handler.post(tickRunnable)
        updateNetworkStatus()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        handler.removeCallbacks(tickRunnable)
    }

    fun refreshNetwork() { updateNetworkStatus(); invalidate() }

    private fun updateNetworkStatus() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLevel = if (wm.isWifiEnabled) {
                val info = wm.connectionInfo
                if (info != null && info.networkId != -1)
                    WifiManager.calculateSignalLevel(info.rssi, 4).coerceIn(1, 3)
                else -1
            } else -1
        } catch (_: Exception) { wifiLevel = -1 }

        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            val sig = tm.signalStrength
            cellLevel = sig?.level ?: 0
            cellType = when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA -> "4G"
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_HSDPA -> "3G"
                else -> ""
            }
        } catch (_: Exception) { cellLevel = 0; cellType = "" }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // ── Dynamic Island pill (center) ───────────────────────────────
        val islandW = w * 0.28f
        val islandH = h * 0.72f
        val islandR = islandH / 2f
        val islandLeft = (w - islandW) / 2f
        val islandTop = (h - islandH) / 2f
        canvas.drawRoundRect(
            RectF(islandLeft, islandTop, islandLeft + islandW, islandTop + islandH),
            islandR, islandR, islandPaint
        )

        val padding = w * 0.04f
        val iconH = h * 0.55f
        val iconY = (h - iconH) / 2f + iconH  // baseline

        // ── Time (left side) ──────────────────────────────────────────
        timePaint.textSize = h * 0.52f
        val timeStr = timeFormat.format(Date())
        val timeX = padding + w * 0.02f
        canvas.drawText(timeStr, timeX, iconY, timePaint)

        // ── Right-side indicators (battery → wifi → cell → network type) ─
        var rightX = w - padding

        // Battery
        rightX = drawBattery(canvas, rightX, h) - w * 0.025f

        // WiFi
        if (wifiLevel >= 0) {
            rightX = drawWifi(canvas, rightX, h, wifiLevel) - w * 0.018f
        }

        // Cell bars + type label
        if (cellType.isNotEmpty()) {
            val typePaint = Paint(timePaint).apply {
                textSize = h * 0.36f
                textAlign = Paint.Align.RIGHT
                color = Color.WHITE
            }
            canvas.drawText(cellType, rightX, iconY, typePaint)
            rightX -= timePaint.measureText(cellType) * 0.36f / 0.52f + w * 0.01f
        }
        drawCellBars(canvas, rightX, h, cellLevel.coerceAtLeast(0))
    }

    /** Returns new rightX after drawing battery. */
    private fun drawBattery(canvas: Canvas, rightX: Float, h: Float): Float {
        val bW = h * 1.05f
        val bH = h * 0.45f
        val tipW = h * 0.06f
        val tipH = h * 0.22f
        val left = rightX - bW - tipW
        val top = (h - bH) / 2f
        val r = bH * 0.25f

        // Border
        canvas.drawRoundRect(RectF(left, top, left + bW, top + bH), r, r, battBorderPaint)

        // Tip
        val tipLeft = left + bW + 1f
        val tipTop = (h - tipH) / 2f
        canvas.drawRoundRect(RectF(tipLeft, tipTop, tipLeft + tipW, tipTop + tipH), 2f, 2f, battBorderPaint)

        // Fill
        battFillPaint.color = when {
            isCharging -> Color.parseColor("#30D158")
            batteryLevel <= 20 -> Color.RED
            else -> Color.WHITE
        }
        val fillW = ((bW - 4f) * batteryLevel / 100f).coerceAtLeast(0f)
        if (fillW > 0)
            canvas.drawRoundRect(RectF(left + 2f, top + 2f, left + 2f + fillW, top + bH - 2f),
                r * 0.6f, r * 0.6f, battFillPaint)

        return left
    }

    /** Returns new rightX after drawing wifi icon. */
    private fun drawWifi(canvas: Canvas, rightX: Float, h: Float, level: Int): Float {
        val size = h * 0.85f
        val left = rightX - size
        val top = (h - size) / 2f
        val cx = left + size / 2f
        val cy = top + size - size * 0.1f

        val p = Paint(iconPaint).apply {
            strokeWidth = size * 0.12f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        val steps = 3
        for (i in 0 until steps) {
            val rad = size * 0.18f + i * size * 0.24f
            val arc = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
            p.alpha = if (i < level) 255 else 70
            canvas.drawArc(arc, 210f, 120f, false, p)
        }
        val dotP = Paint(iconPaint).apply { alpha = 255 }
        canvas.drawCircle(cx, cy, size * 0.08f, dotP)
        return left
    }

    private fun drawCellBars(canvas: Canvas, rightX: Float, h: Float, level: Int) {
        val barCount = 4
        val barW = h * 0.13f
        val spacing = h * 0.08f
        val maxH = h * 0.52f
        val totalW = barCount * barW + (barCount - 1) * spacing
        val left = rightX - totalW
        val bottom = (h + maxH) / 2f

        for (i in 0 until barCount) {
            val barH = maxH * (i + 1) / barCount
            val bx = left + i * (barW + spacing)
            val p = Paint(iconPaint).apply { alpha = if (i < level) 255 else 70 }
            canvas.drawRoundRect(RectF(bx, bottom - barH, bx + barW, bottom), 1.5f, 1.5f, p)
        }
    }
}
