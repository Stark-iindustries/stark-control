package com.starkboard.control.toggles

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

object FlashlightToggle {
    private var isOn = false
    private var cameraId: String? = null

    private fun getCameraId(context: Context): String? {
        if (cameraId != null) return cameraId
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in cm.cameraIdList) {
            val chars = cm.getCameraCharacteristics(id)
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                cameraId = id
                return id
            }
        }
        return null
    }

    fun isEnabled(): Boolean = isOn

    fun toggle(context: Context): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = getCameraId(context) ?: return false
        return try {
            val newState = !isOn
            cm.setTorchMode(id, newState)
            isOn = newState
            true
        } catch (e: Exception) { false }
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = getCameraId(context) ?: return false
        return try {
            cm.setTorchMode(id, enabled)
            isOn = enabled
            true
        } catch (e: Exception) { false }
    }

    fun cleanup(context: Context) {
        if (isOn) setEnabled(context, false)
    }
}
