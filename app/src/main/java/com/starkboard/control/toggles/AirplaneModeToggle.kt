package com.starkboard.control.toggles

import android.content.Context
import android.content.Intent
import android.provider.Settings

object AirplaneModeToggle {
    fun isEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    fun toggle(context: Context): Boolean {
        val newState = !isEnabled(context)
        return try {
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                if (newState) 1 else 0
            )
            // Broadcast the change so the system picks it up
            context.sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
                putExtra("state", newState)
            })
            true
        } catch (e: Exception) {
            // Fallback: root shell
            try {
                val val_ = if (newState) 1 else 0
                val p = Runtime.getRuntime().exec(arrayOf(
                    "su", "-c",
                    "settings put global airplane_mode_on $val_ && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state ${newState}"
                ))
                p.waitFor()
                p.exitValue() == 0
            } catch (e2: Exception) { false }
        }
    }
}
