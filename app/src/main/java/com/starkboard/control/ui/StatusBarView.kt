package com.starkboard.control.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.view.View
import java.text.SimpleDateFormat
import java.util.*

class StatusBarView(context: Context) : View(context) {

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val batteryBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val batteryLowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val batteryChargingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30D158")
        style = Paint.Style.FILL
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())

    private var batteryLevel = 100
    private var isCharging = false
    private var wifiLevel = -1       // 0-4, -1 = off
    private var cellLevel = -1       // 0-4, -1 = no service
    private var cellType = ""        // "5G", "4G", "3G", "LTE", etc.

    private val tickRunnable = object : Runnable {
        override fun run() {
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

    init {
        setBackgroundColor(Color.BLACK)
    }

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

    fun refreshNetwork() {
        updateNetworkStatus()
        invalidate()
    }

    private fun updateNetworkStatus() {
        // WiFi
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLevel = if (wm.isWifiEnabled) {
            val info = wm.connectionInfo
            if (info != null && info.networkId != -1) {
                WifiManager.calculateSignalLevel(info.rssi, 5)
            } else -1
        } else -1

        // Cellular
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        @Suppress("DEPRECATION")
        val sig = tm.signalStrength
        cellLevel = if (sig != null) {
            val level = sig.level  // 0-4
            level
        } else 0

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = if (net != null) cm.getNetworkCapabilities(net) else null
        cellType = when {
            caps == null -> ""
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ""
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> "5G"
            else -> {
                @Suppress("DEPRECATION")
                when (tm.networkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_GPRS -> "E"
                    else -> "4G"
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val w = width.toFloat()

        // ── Time (left side, iOS style) ──────────────────────────────
        val timeStr = timeFormat.format(Date())
        val timeX = 24f
        val timeY = h / 2f + timePaint.textSize * 0.35f
        canvas.drawText(timeStr, timeX, timeY, timePaint)

        // ── Right side icons ─────────────────────────────────────────
        var rightX = w - 18f

        // Battery pill
        rightX = drawBattery(canvas, rightX, h)
        rightX -= 10f

        // WiFi bars OR Cell bars
        if (wifiLevel >= 0) {
            rightX = drawWifiBars(canvas, rightX, h, wifiLevel)
            rightX -= 8f
        } else {
            rightX = drawCellBars(canvas, rightX, h, cellLevel)
            rightX -= 4f
            if (cellType.isNotEmpty()) {
                val tPaint = Paint(iconPaint).apply { textSize = 24f }
                val tw = tPaint.measureText(cellType)
                rightX -= tw
                canvas.drawText(cellType, rightX, h / 2f + 8f, tPaint)
                rightX -= 6f
            }
        }
    }

    /** Draws battery pill, returns new rightX after drawing */
    private fun drawBattery(canvas: Canvas, rightX: Float, h: Float): Float {
        val pillW = 50f
        val pillH = 24f
        val capW = 4f
        val capH = 10f
        val cx = rightX - pillW - capW
        val cy = (h - pillH) / 2f

        // Pill border
        val borderRect = RectF(cx, cy, cx + pillW, cy + pillH)
        canvas.drawRoundRect(borderRect, 6f, 6f, batteryBorderPaint)

        // Nub
        val nubRect = RectF(cx + pillW, cy + (pillH - capH) / 2f,
                            cx + pillW + capW, cy + (pillH + capH) / 2f)
        canvas.drawRoundRect(nubRect, 2f, 2f, iconPaint)

        // Fill
        val pad = 3.5f
        val fillW = (pillW - pad * 2) * (batteryLevel / 100f)
        val fillRect = RectF(cx + pad, cy + pad, cx + pad + fillW, cy + pillH - pad)
        val fillPaint = when {
            isCharging -> batteryChargingPaint
            batteryLevel <= 20 -> batteryLowPaint
            else -> iconPaint
        }
        if (fillW > 0) canvas.drawRoundRect(fillRect, 3f, 3f, fillPaint)

        return cx
    }

    /** WiFi arc bars, returns new rightX */
    private fun drawWifiBars(canvas: Canvas, rightX: Float, h: Float, level: Int): Float {
        val size = 36f
        val left = rightX - size
        val top = (h - size) / 2f
        val cx = left + size / 2f
        val cy = top + size - 4f
        val barPaint = Paint(iconPaint).apply { strokeWidth = 3f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        // 3 arcs
        val steps = 3
        for (i in 0 until steps) {
            val radius = 6f + i * 9f
            val arc = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            barPaint.alpha = if (i < level) 255 else 80
            canvas.drawArc(arc, 210f, 120f, false, barPaint)
        }
        // Dot
        val dotPaint = Paint(iconPaint).apply { alpha = 255 }
        canvas.drawCircle(cx, cy, 3f, dotPaint)
        return left
    }

    /** Cell signal bars, returns new rightX */
    private fun drawCellBars(canvas: Canvas, rightX: Float, h: Float, level: Int): Float {
        val barCount = 4
        val barW = 5f
        val spacing = 3f
        val maxH = 22f
        val totalW = barCount * barW + (barCount - 1) * spacing
        val left = rightX - totalW
        val bottom = (h + maxH) / 2f

        for (i in 0 until barCount) {
            val barH = maxH * (i + 1) / barCount
            val bx = left + i * (barW + spacing)
            val rect = RectF(bx, bottom - barH, bx + barW, bottom)
            val p = Paint(iconPaint).apply { alpha = if (i < level) 255 else 80 }
            canvas.drawRoundRect(rect, 2f, 2f, p)
        }
        return left
    }
}
