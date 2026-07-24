package com.starkboard.control.toggles

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build

object BluetoothToggle {
    private fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Suppress("MissingPermission")
    fun isEnabled(context: Context): Boolean = try {
        adapter(context)?.isEnabled == true
    } catch (_: Exception) { false }

    @Suppress("DEPRECATION", "MissingPermission")
    fun toggle(context: Context): Boolean {
        val bt = adapter(context) ?: return false
        return try {
            if (bt.isEnabled) bt.disable() else bt.enable()
            true
        } catch (e: Exception) {
            false
        }
    }
}
