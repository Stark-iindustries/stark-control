package com.starkboard.control

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.starkboard.control.ui.ControlCenterView
import com.starkboard.control.ui.StatusBarView
import com.starkboard.control.ui.TriggerPillView

class OverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private var statusBarView: StatusBarView? = null
    private var triggerPillView: TriggerPillView? = null
    private var controlCenterView: ControlCenterView? = null
    private var ccVisible = false

    companion object {
        const val CHANNEL_ID = "stark_control_overlay"
        const val NOTIF_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // FIX: Android 14+ requires service type in startForeground()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        addStatusBarOverlay()
        addTriggerPill()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeStatusBar()
        removeTriggerPill()
        hideControlCenter()
    }

    // ── Status Bar Overlay ────────────────────────────────────────────

    private fun addStatusBarOverlay() {
        val sbh = getStatusBarHeight()
        val view = StatusBarView(this)
        statusBarView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            sbh,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or  // pass through touches — pill handles them
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
        }

        windowManager.addView(view, params)
    }

    private fun removeStatusBar() {
        statusBarView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            statusBarView = null
        }
    }

    // ── Trigger Pill (top-right, always visible, opens CC) ───────────
    // FIX: Android 12+ intercepts swipe-from-top before tiny overlays receive it.
    // A tappable/swipeable pill at the top-right corner is the reliable trigger.

    private fun addTriggerPill() {
        val sbh = getStatusBarHeight()
        val pill = TriggerPillView(this) { showControlCenter() }
        triggerPillView = pill

        val pillW = dpToPx(56)
        val pillH = dpToPx(36)

        val params = WindowManager.LayoutParams(
            pillW,
            pillH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = sbh - pillH / 2   // sits half in status bar, half below — reachable
        }

        windowManager.addView(pill, params)
    }

    private fun removeTriggerPill() {
        triggerPillView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            triggerPillView = null
        }
    }

    // ── Control Center Overlay ────────────────────────────────────────

    fun showControlCenter() {
        if (ccVisible) return
        ccVisible = true

        val cc = ControlCenterView(this) { hideControlCenter() }
        controlCenterView = cc

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(cc, params)
        // FIX: wait for layout so height != 0 before animating
        cc.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                cc.viewTreeObserver.removeOnGlobalLayoutListener(this)
                cc.animateIn()
            }
        })
    }

    fun hideControlCenter() {
        controlCenterView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            controlCenterView = null
        }
        ccVisible = false
        statusBarView?.refreshNetwork()
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun getStatusBarHeight(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else dpToPx(44)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_running))
            .setContentText(getString(R.string.overlay_stop))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
