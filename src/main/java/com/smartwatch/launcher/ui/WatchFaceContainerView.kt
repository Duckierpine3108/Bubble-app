package com.smartwatch.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.min

/**
 * Master watch face container:
 * - Detects Swipe Right-to-Left to reveal underlying Bubble App Launcher.
 * - Detects Long-Press to enter Dial Switcher/Customizer Mode.
 * - Renders active watch face at 60 FPS with zero overhead when hidden.
 */
class WatchFaceContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val watchFaceManager = WatchFaceManager(context)

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private val screenClipPath = Path()

    // Battery percentage caching
    private var batteryPercent: Float = 100f
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                batteryPercent = (level * 100f) / scale
            }
        }
    }

    // Customizer Carousel State
    var isCustomizerMode = false
        private set
    private var customizerScale = 1.0f
    private var previewIndex = watchFaceManager.currentFaceIndex
    private var horizontalSwipeAccumulator = 0f

    // Customizer UI Overlay Paints
    private val dimOverlayPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 20, 24, 30)
        style = Paint.Style.FILL
    }
    private val dotActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00E5FF.toInt() }
    private val dotInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(100, 255, 255, 255) }
    private val badgeRect = RectF()

    // Gestures
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            horizontalSwipeAccumulator = 0f
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (!isCustomizerMode) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                enterCustomizerMode()
            }
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isCustomizerMode) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                exitCustomizerMode(save = true)
                return true
            }
            return false
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (isCustomizerMode) {
                horizontalSwipeAccumulator += distanceX
                val threshold = width * 0.18f
                if (horizontalSwipeAccumulator > threshold) {
                    cyclePreview(1)
                    horizontalSwipeAccumulator = 0f
                } else if (horizontalSwipeAccumulator < -threshold) {
                    cyclePreview(-1)
                    horizontalSwipeAccumulator = 0f
                }
                return true
            }
            return false
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (isCustomizerMode) {
                if (diffX < -50f) cyclePreview(1)
                else if (diffX > 50f) cyclePreview(-1)
                return true
            }

            // Normal Mode: Swipe Right-to-Left opens Bubble Launcher
            if (diffX < -80f && abs(diffX) > abs(diffY)) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                revealApps()
                return true
            }
            return false
        }
    })

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(batteryReceiver)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(cx, cy)

        screenClipPath.reset()
        screenClipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        titleTextPaint.textSize = radius * 0.11f

        watchFaceManager.renderers.forEach {
            it.onSizeChanged(w, h, cx, cy, radius)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(screenClipPath)

        val timeNow = System.currentTimeMillis()

        if (!isCustomizerMode) {
            watchFaceManager.getActiveRenderer().draw(canvas, timeNow, batteryPercent)
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimOverlayPaint)

            canvas.save()
            canvas.scale(customizerScale, customizerScale, cx, cy)
            watchFaceManager.renderers[previewIndex].draw(canvas, timeNow, batteryPercent)
            canvas.restore()

            drawCustomizerOverlay(canvas)
        }

        if (visibility == VISIBLE && isAttachedToWindow) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawCustomizerOverlay(canvas: Canvas) {
        val currentName = watchFaceManager.renderers[previewIndex].getName()
        val textWidth = titleTextPaint.measureText(currentName)
        val badgeHeight = radius * 0.22f
        val badgeY = height * 0.82f

        badgeRect.set(
            cx - (textWidth / 2f) - (radius * 0.12f),
            badgeY - (badgeHeight / 2f),
            cx + (textWidth / 2f) + (radius * 0.12f),
            badgeY + (badgeHeight / 2f)
        )
        canvas.drawRoundRect(badgeRect, badgeHeight / 2f, badgeHeight / 2f, badgeBgPaint)

        val textBaseline = badgeY - ((titleTextPaint.descent() + titleTextPaint.ascent()) / 2f)
        canvas.drawText(currentName, cx, textBaseline, titleTextPaint)

        val total = watchFaceManager.renderers.size
        val dotRadius = radius * 0.022f
        val dotSpacing = radius * 0.08f
        val startDotX = cx - ((total - 1) * dotSpacing) / 2f
        val dotY = height * 0.12f

        for (i in 0 until total) {
            val paint = if (i == previewIndex) dotActivePaint else dotInactivePaint
            canvas.drawCircle(startDotX + (i * dotSpacing), dotY, dotRadius, paint)
        }
    }

    private fun enterCustomizerMode() {
        isCustomizerMode = true
        previewIndex = watchFaceManager.currentFaceIndex
        ValueAnimator.ofFloat(1.0f, 0.76f).apply {
            duration = 320
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                customizerScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun exitCustomizerMode(save: Boolean) {
        if (!isCustomizerMode) return
        if (save) {
            watchFaceManager.currentFaceIndex = previewIndex
        }
        ValueAnimator.ofFloat(customizerScale, 1.0f).apply {
            duration = 280
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                customizerScale = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isCustomizerMode = false
                    invalidate()
                }
            })
            start()
        }
    }

    private fun cyclePreview(direction: Int) {
        val count = watchFaceManager.renderers.size
        previewIndex = (previewIndex + direction + count) % count
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    fun revealApps(animated: Boolean = true) {
        if (isCustomizerMode) exitCustomizerMode(save = false)
        if (!animated) {
            visibility = GONE
            return
        }
        animate()
            .translationX(-width.toFloat())
            .alpha(0f)
            .setDuration(340)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .withEndAction {
                visibility = GONE
            }
            .start()
    }

    fun showClock(animated: Boolean = true) {
        visibility = VISIBLE
        bringToFront()
        if (!animated) {
            translationX = 0f
            alpha = 1f
            invalidate()
            return
        }
        translationX = -width.toFloat()
        alpha = 0f
        animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(340)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
        postInvalidateOnAnimation()
    }
}