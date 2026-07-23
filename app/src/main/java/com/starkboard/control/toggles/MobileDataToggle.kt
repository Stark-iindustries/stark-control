package com.starkboard.control.toggles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Toast

object MobileDataToggle {

    fun isEnabled(context: Context): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.isDataEnabled
        } catch (_: Exception) {
            try {
                Settings.Global.getInt(context.contentResolver, "mobile_data", 1) == 1
            } catch (_: Exception) { false }
        }
    }

    /**
     * Three-tier toggle for Android 11 without MODIFY_PHONE_STATE:
     *
     * 1. TelephonyManager.isDataEnabled setter — works if MODIFY_PHONE_STATE was granted (unlikely without root)
     * 2. Settings.Global "mobile_data" write — works on Android 11 AOSP/Infinix XOS once
     *    WRITE_SECURE_SETTINGS is granted via the Termux ADB setup script
     * 3. Graceful fallback — opens the SIM card settings page so the user can tap it themselves;
     *    shows a toast so they know what happened
     */
    fun toggle(context: Context): Boolean {
        val newState = !isEnabled(context)

        // Tier 1: direct API (works if granted via root / privileged)
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.isDataEnabled = newState
            return true
        } catch (_: Exception) {}

        // Tier 2: Settings.Global write (works on Android 11 with WRITE_SECURE_SETTINGS via ADB)
        try {
            val ok = Settings.Global.putInt(
                context.contentResolver, "mobile_data", if (newState) 1 else 0
            )
            if (ok) {
                // Some OEMs need an explicit broadcast to pick up the change
                try {
                    context.sendBroadcast(
                        Intent("android.intent.action.SIM_STATE_CHANGED")
                    )
                } catch (_: Exception) {}
                return true
            }
        } catch (_: Exception) {}

        // Tier 3: open Settings so the user can toggle manually
        try {
            Toast.makeText(
                context,
                "Tap to toggle Mobile Data in Settings",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
        } catch (_: Exception) {}

        return false
    }
}
