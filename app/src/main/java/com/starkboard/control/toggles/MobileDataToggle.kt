package com.starkboard.control.toggles

import android.content.Context
import android.net.ConnectivityManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Aggressive mobile data toggle.
 * Tries multiple strategies in order — at least one usually works on most ROMs.
 *
 * Strategy 1 – TelephonyManager hidden API (works on most OEM ROMs pre-Android 11)
 * Strategy 2 – ConnectivityManager hidden API (older Android fallback)
 * Strategy 3 – IConnectivityManager binder stub via reflection (deeper system access)
 * Strategy 4 – Root shell via `svc data` (works if device is rooted)
 * Strategy 5 – Settings.Global mobile_data key (some manufacturer ROMs honour this)
 */
object MobileDataToggle {
    private const val TAG = "MobileDataToggle"

    fun isEnabled(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val method = ConnectivityManager::class.java.getDeclaredMethod("getMobileDataEnabled")
            method.isAccessible = true
            method.invoke(cm) as Boolean
        } catch (e: Exception) {
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val method = TelephonyManager::class.java.getDeclaredMethod("getDataEnabled")
                method.isAccessible = true
                method.invoke(tm) as Boolean
            } catch (e2: Exception) {
                // fallback: assume on
                true
            }
        }
    }

    fun toggle(context: Context): Boolean {
        val target = !isEnabled(context)
        return setEnabled(context, target)
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        Log.d(TAG, "Attempting to set mobile data -> $enabled")

        // Strategy 1: TelephonyManager.setDataEnabled (hidden, works on many ROMs)
        if (tryTelephonyManager(context, enabled)) {
            Log.d(TAG, "Strategy 1 (TelephonyManager) succeeded")
            return true
        }

        // Strategy 2: TelephonyManager.setMobileDataEnabled (older hidden API)
        if (tryTelephonyManagerLegacy(context, enabled)) {
            Log.d(TAG, "Strategy 2 (TelephonyManager legacy) succeeded")
            return true
        }

        // Strategy 3: ConnectivityManager.setMobileDataEnabled (hidden)
        if (tryConnectivityManager(context, enabled)) {
            Log.d(TAG, "Strategy 3 (ConnectivityManager) succeeded")
            return true
        }

        // Strategy 4: IConnectivityManager binder via reflection
        if (tryIConnectivityManager(context, enabled)) {
            Log.d(TAG, "Strategy 4 (IConnectivityManager) succeeded")
            return true
        }

        // Strategy 5: Root shell `svc data enable/disable`
        if (tryRootShell(enabled)) {
            Log.d(TAG, "Strategy 5 (root shell) succeeded")
            return true
        }

        // Strategy 6: Settings.Global mobile_data (some OEM ROMs)
        if (trySettingsGlobal(context, enabled)) {
            Log.d(TAG, "Strategy 6 (Settings.Global) succeeded")
            return true
        }

        Log.w(TAG, "All strategies failed — device may require system-level privilege")
        return false
    }

    private fun tryTelephonyManager(context: Context, enabled: Boolean): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val m = TelephonyManager::class.java.getDeclaredMethod("setDataEnabled", Boolean::class.java)
            m.isAccessible = true
            m.invoke(tm, enabled)
            true
        } catch (e: Exception) { false }
    }

    private fun tryTelephonyManagerLegacy(context: Context, enabled: Boolean): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val m = TelephonyManager::class.java.getDeclaredMethod("setMobileDataEnabled", Boolean::class.java)
            m.isAccessible = true
            m.invoke(tm, enabled)
            true
        } catch (e: Exception) { false }
    }

    private fun tryConnectivityManager(context: Context, enabled: Boolean): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val m = ConnectivityManager::class.java.getDeclaredMethod("setMobileDataEnabled", Boolean::class.java)
            m.isAccessible = true
            m.invoke(cm, enabled)
            true
        } catch (e: Exception) { false }
    }

    private fun tryIConnectivityManager(context: Context, enabled: Boolean): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            // Get the internal IConnectivityManager service binder
            val serviceField = ConnectivityManager::class.java.getDeclaredField("mService")
            serviceField.isAccessible = true
            val iService = serviceField.get(cm) ?: return false
            val m = iService.javaClass.getDeclaredMethod("setMobileDataEnabled", Boolean::class.java)
            m.isAccessible = true
            m.invoke(iService, enabled)
            true
        } catch (e: Exception) {
            try {
                // Alternative field name on some ROMs
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val serviceField = ConnectivityManager::class.java.getDeclaredField("sService")
                serviceField.isAccessible = true
                val iService = serviceField.get(cm) ?: return false
                val m = iService.javaClass.getDeclaredMethod("setMobileDataEnabled", Boolean::class.java)
                m.isAccessible = true
                m.invoke(iService, enabled)
                true
            } catch (e2: Exception) { false }
        }
    }

    private fun tryRootShell(enabled: Boolean): Boolean {
        return try {
            val cmd = if (enabled) "svc data enable" else "svc data disable"
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            p.waitFor()
            p.exitValue() == 0
        } catch (e: Exception) { false }
    }

    private fun trySettingsGlobal(context: Context, enabled: Boolean): Boolean {
        return try {
            android.provider.Settings.Global.putInt(
                context.contentResolver,
                "mobile_data",
                if (enabled) 1 else 0
            )
            true
        } catch (e: Exception) { false }
    }
}
