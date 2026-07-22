package com.starkboard.control.toggles

import android.content.Context
import android.net.wifi.WifiManager

object WifiToggle {
    fun isEnabled(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wm.isWifiEnabled
    }

    @Suppress("DEPRECATION")
    fun toggle(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val newState = !wm.isWifiEnabled
        return try {
            wm.isWifiEnabled = newState
            true
        } catch (e: Exception) {
            false
        }
    }
}
