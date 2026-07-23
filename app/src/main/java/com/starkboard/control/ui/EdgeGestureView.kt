package com.starkboard.control.ui

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Thin transparent strip placed at either the top-left or top-right corner
 * of the screen, just below the status bar. Detects a downward swipe
 * and fires [onSwipeDown]. Invisible — does not draw anything.
 */
class EdgeGestureView(
    context: Context,
    private val onSwipeDown: () -> Unit
) : View(context) {

    private val gd = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (e1 == null) return false
            val dy = e2.y - e1.y
            val dx = e2.x - e1.x
            if (dy > 60 && vy > 250f && Math.abs(dy) > Math.abs(dx)) {
                onSwipeDown()
                return true
            }
            return false
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Single tap also triggers (for accessibility)
            return false
        }
    })

    init {
        setWillNotDraw(true)         // fully transparent
        isClickable = true
        isFocusable = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gd.onTouchEvent(event)
    }
}
