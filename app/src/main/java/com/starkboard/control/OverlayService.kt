package com.starkboard.control

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.starkboard.control.model.NotificationItem
import com.starkboard.control.ui.*

class OverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private val vmStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = vmStore

    private val ssrc = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry

    private lateinit var wm: WindowManager
    private var statusBarView: StatusBarView? = null
    private var dynamicIslandView: DynamicIslandView? = null
    private var leftEdge: EdgeGestureView? = null
    private var rightEdge: EdgeGestureView? = null
    private var controlCenter: ControlCenterView? = null
    private var notifCenter: NotificationCenterView? = null
    private var scrim: View? = null

    private val notifications = mutableListOf<NotificationItem>()

    private val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                StarkNotificationListenerService.ACTION_NOTIF_POSTED -> {
                    val item: NotificationItem? = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(StarkNotificationListenerService.EXTRA_NOTIF, NotificationItem::class.java)
                    else
                        @Suppress("DEPRECATION") intent.getParcelableExtra(StarkNotificationListenerService.EXTRA_NOTIF)
                    item?.let {
                        notifications.removeAll { n -> n.key == it.key }
                        notifications.add(0, it)
                        if (notifications.size > 50) notifications.removeLast()
                        dynamicIslandView?.showNotification(it)
                        notifCenter?.updateNotifications(notifications.toList())
                    }
                }
                StarkNotificationListenerService.ACTION_NOTIF_REMOVED -> {
                    val key = intent.getStringExtra(StarkNotificationListenerService.EXTRA_KEY)
                    notifications.removeAll { n -> n.key == key }
                    notifCenter?.updateNotifications(notifications.toList())
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "stark_overlay"
        const val NOTIF_ID = 1
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, OverlayService::class.java))
    }

    override fun onCreate() {
        ssrc.performRestore(null)
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            startForeground(NOTIF_ID, buildNotif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else
            startForeground(NOTIF_ID, buildNotif())

        val filter = IntentFilter().apply {
            addAction(StarkNotificationListenerService.ACTION_NOTIF_POSTED)
            addAction(StarkNotificationListenerService.ACTION_NOTIF_REMOVED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(notifReceiver, filter, RECEIVER_NOT_EXPORTED)
        else
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(notifReceiver, filter)

        hideSystemStatusBar()
        addStatusBar()
        addDynamicIsland()
        addEdgeGestures()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? { super.onBind(intent); return null }

    override fun onDestroy() {
        vmStore.clear()
        super.onDestroy()
        try { unregisterReceiver(notifReceiver) } catch (_: Exception) {}
        restoreSystemStatusBar()
        listOf(statusBarView, dynamicIslandView, leftEdge, rightEdge)
            .forEach { v -> v?.let { safeRemove(it) } }
        closeControlCenter()
        closeNotificationCenter()
    }

    // ── System Status Bar ─────────────────────────────────────────────

    private fun hideSystemStatusBar() {
        try {
            Settings.Global.putString(contentResolver, "policy_control", "immersive.status=*")
        } catch (_: SecurityException) { /* needs WRITE_SECURE_SETTINGS via setup.sh */ }
    }

    private fun restoreSystemStatusBar() {
        try { Settings.Global.putString(contentResolver, "policy_control", null) }
        catch (_: Exception) {}
    }

    // ── Status Bar ────────────────────────────────────────────────────

    private fun addStatusBar() {
        val v = StatusBarView(this,
            onLeftSwipe = { openNotificationCenter() },
            onRightSwipe = { openControlCenter() }
        ).also { statusBarView = it }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            sbHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // Cover the display cutout/notch area on Android P+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        wm.addView(v, lp)
    }

    // ── Dynamic Island ────────────────────────────────────────────────

    private fun addDynamicIsland() {
        val v = DynamicIslandView(this).also { dynamicIslandView = it }
        val pillW = dp(120)
        val sw = screenW()
        wm.addView(v, WindowManager.LayoutParams(
            pillW, sbHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (sw - pillW) / 2
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        })
    }

    // ── Edge Gestures ─────────────────────────────────────────────────

    private fun addEdgeGestures() {
        // Edge gesture strips extend FROM the top of screen (y=0) covering the
        // status bar + a generous area below, so swipe-down always registers.
        // Width is intentionally narrow so normal app touches pass through.
        val stripW = dp(56)
        val stripH = sbHeight() + dp(160)   // status bar + reach area

        leftEdge = EdgeGestureView(this) { openNotificationCenter() }.also { v ->
            wm.addView(v, WindowManager.LayoutParams(
                stripW, stripH,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0; y = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            })
        }

        rightEdge = EdgeGestureView(this) { openControlCenter() }.also { v ->
            wm.addView(v, WindowManager.LayoutParams(
                stripW, stripH,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 0; y = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            })
        }
    }

    // ── Control Center ────────────────────────────────────────────────

    private fun openControlCenter() {
        if (controlCenter != null) return
        try {
            closeNotificationCenter()
            addScrim { closeControlCenter() }
            val v = ControlCenterView(this, this) { closeControlCenter() }.also { controlCenter = it }
            wm.addView(v, fullScreenParams(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            ))
            v.animateIn()
        } catch (_: Exception) {
            controlCenter = null
            removeScrim()
        }
    }

    private fun closeControlCenter() {
        controlCenter?.animateOut { safeRemove(controlCenter); controlCenter = null }
        removeScrim()
    }

    // ── Notification Center ───────────────────────────────────────────

    private fun openNotificationCenter() {
        if (notifCenter != null) return
        try {
            closeControlCenter()
            addScrim { closeNotificationCenter() }
            val v = NotificationCenterView(this, this, notifications.toList()) { closeNotificationCenter() }
                .also { notifCenter = it }
            wm.addView(v, fullScreenParams(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            ))
            v.animateIn()
        } catch (_: Exception) {
            notifCenter = null
            removeScrim()
        }
    }

    private fun closeNotificationCenter() {
        notifCenter?.animateOut { safeRemove(notifCenter); notifCenter = null }
        removeScrim()
    }

    // ── Scrim ─────────────────────────────────────────────────────────

    private fun addScrim(onTap: () -> Unit) {
        val v = View(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener { onTap() }
        }.also { scrim = it }
        wm.addView(v, fullScreenParams(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
    }

    private fun removeScrim() {
        scrim?.let { safeRemove(it); scrim = null }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun safeRemove(v: View?) {
        v ?: return
        try { wm.removeView(v) } catch (_: Exception) {}
    }

    private fun fullScreenParams(flags: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags, PixelFormat.TRANSLUCENT
    )

    fun sbHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(28)
    }

    private fun screenW(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        (getSystemService(WINDOW_SERVICE) as WindowManager).currentWindowMetrics.bounds.width()
    else {
        val dm = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        dm.widthPixels
    }

    fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Stark Control", NotificationManager.IMPORTANCE_LOW)
            .apply { setShowBadge(false) }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildNotif() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Stark Control active")
        .setContentText("Swipe top-left: notifications • top-right: controls")
        .setSmallIcon(android.R.drawable.ic_menu_manage)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()
}
