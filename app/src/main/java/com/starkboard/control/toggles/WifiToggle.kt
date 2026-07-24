package com.starkboard.control.toggles

import android.content.Context
import android.net.wifi.WifiManager

object WifiToggle {
    fun isEnabled(context: Context): Boolean = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wm.isWifiEnabled
    } catch (_: Exception) { false }

    @Suppress("DEPRECATION")
    fun toggle(context: Context): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.isWifiEnabled = !wm.isWifiEnabled
            true
        } catch (_: Exception) { false }
    }
}
