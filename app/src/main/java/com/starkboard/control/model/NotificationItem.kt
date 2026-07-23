package com.starkboard.control.model

import android.graphics.drawable.Icon
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timeMs: Long,
    val smallIconRes: Int = 0
) : Parcelable {
    fun timeLabel(): String {
        val diff = System.currentTimeMillis() - timeMs
        return when {
            diff < 60_000 -> "now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> {
                val h = diff / 3_600_000
                if (h == 1L) "1h ago" else "${h}h ago"
            }
            else -> {
                val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                fmt.format(java.util.Date(timeMs))
            }
        }
    }
}
