package com.starkboard.control.toggles

import android.content.Context
import android.provider.Settings

object RotationToggle {
    /** Returns true when rotation is LOCKED (portrait lock on) */
    fun isLocked(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0

    fun toggle(context: Context): Boolean {
        val locked = isLocked(context)
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (locked) 1 else 0
            )
            true
        } catch (e: Exception) { false }
    }
}
