package com.starkboard.control

import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.starkboard.control.model.NotificationItem

class StarkNotificationListenerService : NotificationListenerService() {

    companion object {
        const val ACTION_NOTIF_POSTED = "com.starkboard.control.NOTIF_POSTED"
        const val ACTION_NOTIF_REMOVED = "com.starkboard.control.NOTIF_REMOVED"
        const val EXTRA_NOTIF = "notif_item"
        const val EXTRA_KEY = "notif_key"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val title = extras.getCharSequence("android.title")?.toString() ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        if (title.isBlank()) return

        // Suppress our own service notification
        if (sbn.packageName == packageName) return

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (_: Exception) { sbn.packageName }

        val item = NotificationItem(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timeMs = sbn.postTime,
            smallIconRes = 0
        )

        val intent = Intent(ACTION_NOTIF_POSTED).apply {
            putExtra(EXTRA_NOTIF, item)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val intent = Intent(ACTION_NOTIF_REMOVED).apply {
            putExtra(EXTRA_KEY, sbn.key)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
