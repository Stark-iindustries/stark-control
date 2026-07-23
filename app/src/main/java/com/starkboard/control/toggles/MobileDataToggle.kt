package com.starkboard.control.toggles

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager

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

    fun toggle(context: Context): Boolean {
        val newState = !isEnabled(context)
        // Approach 1: TelephonyManager.setDataEnabled (needs MODIFY_PHONE_STATE via ADB)
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.isDataEnabled = newState
            true
        } catch (_: Exception) {
            // Approach 2: Settings.Global (needs WRITE_SECURE_SETTINGS via ADB)
            try {
                Settings.Global.putInt(context.contentResolver, "mobile_data", if (newState) 1 else 0)
                true
            } catch (_: Exception) { false }
        }
    }
}
