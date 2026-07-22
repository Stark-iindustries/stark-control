package com.starkboard.control.toggles

import android.app.NotificationManager
import android.content.Context

object DndToggle {
    fun isEnabled(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
               nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
               nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
    }

    fun toggle(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return false
        return try {
            val newFilter = if (isEnabled(context))
                NotificationManager.INTERRUPTION_FILTER_ALL
            else
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            nm.setInterruptionFilter(newFilter)
            true
        } catch (e: Exception) { false }
    }
}
