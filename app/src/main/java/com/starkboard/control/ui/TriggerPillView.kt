package com.starkboard.control.ui

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * A small pill-shaped handle that sits at the top-right of the screen.
 * Tap or swipe down → opens Control Center.
 * Works on ALL Android versions because it is a standard touchable overlay view —
 * unlike swipe-from-the-very-top which Android 12+ intercepts before overlays receive it.
 */
class TriggerPillView(context: Context, private val onOpen: () -> Unit) : View(context) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC1C1C1E")
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFFFFF")
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    private val gd = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { onOpen(); return true }
        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent, velX: Float, velY: Float
        ): Boolean {
            if (velY > 200f) { onOpen(); return true }
            return false
        }
    })

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rx = h / 2f

        // Pill background
        canvas.drawRoundRect(RectF(0f, 0f, w, h), rx, rx, bgPaint)

        // Three horizontal grip lines — iOS-style indicator
        val lineW = w * 0.38f
        val cx = w / 2f
        val spacing = h * 0.18f
        val midY = h / 2f
        for (i in -1..1) {
            canvas.drawLine(cx - lineW / 2f, midY + i * spacing,
                cx + lineW / 2f, midY + i * spacing, linePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent) = gd.onTouchEvent(event)
}
