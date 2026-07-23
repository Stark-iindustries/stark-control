package com.starkboard.control.ui

import android.animation.*
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.starkboard.control.model.NotificationItem

/**
 * Overlay that sits on top of the status bar center.
 * In idle state it is fully transparent (the status bar draws the pill).
 * When a notification arrives it expands to show app name + text,
 * stays for 4 seconds, then collapses and becomes invisible again.
 */
class DynamicIslandView(context: Context) : View(context) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A84FF")
        style = Paint.Style.FILL
    }

    private var expandFraction = 0f   // 0 = collapsed pill, 1 = fully expanded
    private var currentNotif: NotificationItem? = null
    private val handler = Handler(Looper.getMainLooper())
    private var collapseRunnable: Runnable? = null

    fun showNotification(item: NotificationItem) {
        currentNotif = item
        cancelCollapse()
        animateExpand {
            scheduleCollapse()
        }
    }

    private fun animateExpand(onEnd: () -> Unit) {
        val anim = ValueAnimator.ofFloat(expandFraction, 1f).apply {
            duration = 420
            interpolator = OvershootInterpolator(1.3f)
            addUpdateListener {
                expandFraction = it.animatedValue as Float
                invalidate()
                requestLayoutOnParent()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onEnd() }
            })
        }
        anim.start()
    }

    private fun animateCollapse() {
        val anim = ValueAnimator.ofFloat(expandFraction, 0f).apply {
            duration = 340
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                expandFraction = it.animatedValue as Float
                invalidate()
                requestLayoutOnParent()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentNotif = null
                }
            })
        }
        anim.start()
    }

    private fun scheduleCollapse() {
        collapseRunnable = Runnable { animateCollapse() }
        handler.postDelayed(collapseRunnable!!, 4_000)
    }

    private fun cancelCollapse() {
        collapseRunnable?.let { handler.removeCallbacks(it) }
        collapseRunnable = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelCollapse()
    }

    private fun requestLayoutOnParent() {
        (parent as? android.view.ViewGroup)?.let { parent ->
            val lp = layoutParams as? android.view.WindowManager.LayoutParams ?: return
            val d = resources.displayMetrics.density
            val baseW = 120 * d
            val expandW = resources.displayMetrics.widthPixels * 0.88f
            val baseH = targetHeight()
            val expandH = baseH + 52 * d

            lp.width = (baseW + (expandW - baseW) * expandFraction).toInt()
            lp.height = (baseH + (expandH - baseH) * expandFraction).toInt()
            lp.x = ((resources.displayMetrics.widthPixels - lp.width) / 2)

            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                wm.updateViewLayout(this, lp)
            } catch (_: Exception) {}
        }
    }

    private fun targetHeight(): Float {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id).toFloat()
        else 28 * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        if (expandFraction <= 0.01f) { setBackgroundColor(Color.TRANSPARENT); return }

        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f + (12f * resources.displayMetrics.density * expandFraction)
        val d = resources.displayMetrics.density

        // Background pill
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r.coerceAtMost(h / 2f + 4 * d), r.coerceAtMost(h / 2f + 4 * d), bgPaint)

        if (expandFraction < 0.7f) return

        val alpha = ((expandFraction - 0.7f) / 0.3f).coerceIn(0f, 1f)
        val notif = currentNotif ?: return

        val pad = 14f * d
        val dotR = 5f * d

        // Coloured dot (app colour placeholder)
        dotPaint.alpha = (alpha * 255).toInt()
        canvas.drawCircle(pad + dotR, h / 2f, dotR, dotPaint)

        // Title
        titlePaint.textSize = 13f * d
        titlePaint.alpha = (alpha * 255).toInt()
        canvas.drawText(
            notif.appName.take(22),
            pad + dotR * 2 + 6 * d,
            h / 2f - 2 * d,
            titlePaint
        )

        // Body text
        bodyPaint.textSize = 12f * d
        bodyPaint.alpha = (alpha * 200).toInt()
        canvas.drawText(
            notif.text.take(40),
            pad + dotR * 2 + 6 * d,
            h / 2f + bodyPaint.textSize,
            bodyPaint
        )
    }
}
