package com.smartwatch.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Self-contained translucent frosted glass analog watch dial.
 * - Continuous 60 FPS sweep second hand with millisecond precision.
 * - Glassmorphism radial/linear depth gradients with ambient pulsing bezel.
 * - Dual swipe-left / tap gesture handling to slide open the Bubble Launcher.
 * - Zero battery draw when hidden (stops invalidation loop when GONE).
 */
class FrostedGlassClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var cx = 0f
    private var cy = 0f
    private var dialRadius = 0f

    // Paints
    private val dialBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bezelPulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(90, 255, 255, 255)
    }

    // Tick Paints
    private val hourTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val minuteTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 200, 220, 240)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val accentTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt() // Vibrant neon cyan
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // Hands Paints
    private val hourHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(8f, 0f, 4f, Color.argb(160, 0, 0, 0))
    }
    private val minuteHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0F7FA.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(8f, 0f, 4f, Color.argb(160, 0, 0, 0))
    }
    private val secondHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(10f, 0f, 0f, 0xFF00E5FF.toInt())
    }
    private val centerCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF00E5FF.toInt()
    }

    private val screenClipPath = Path()
    private val glassReflectionRect = RectF()

    // Gestures
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            revealApps()
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val startX = e1?.x ?: return false
            val diffX = e2.x - startX
            val diffY = e2.y - (e1.y)

            // Swipe left (Right to Left across the dial)
            if (diffX < -70f && abs(diffX) > abs(diffY)) {
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        dialRadius = min(cx, cy)

        screenClipPath.reset()
        screenClipPath.addCircle(cx, cy, dialRadius, Path.Direction.CW)

        // Frosted dark glassmorphism radial gradient
        dialBackgroundPaint.shader = RadialGradient(
            cx, cy * 0.75f, dialRadius * 1.1f,
            intArrayOf(
                Color.argb(215, 28, 38, 48),  // Specular top highlight
                Color.argb(235, 12, 16, 22),  // Deep frosted body
                Color.argb(250, 4, 6, 8)      // Dark AMOLED perimeter
            ),
            floatArrayOf(0.0f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )

        // Frosted diagonal glass reflection
        glassHighlightPaint.shader = LinearGradient(
            cx - dialRadius, cy - dialRadius,
            cx + dialRadius * 0.5f, cy + dialRadius * 0.5f,
            intArrayOf(
                Color.argb(45, 255, 255, 255),
                Color.argb(12, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0.0f, 0.35f, 1.0f),
            Shader.TileMode.CLAMP
        )
        glassReflectionRect.set(cx - dialRadius, cy - dialRadius, cx + dialRadius, cy + dialRadius)

        // Scale stroke widths relative to display resolution
        hourTickPaint.strokeWidth = dialRadius * 0.022f
        minuteTickPaint.strokeWidth = dialRadius * 0.010f
        accentTickPaint.strokeWidth = dialRadius * 0.026f

        hourHandPaint.strokeWidth = dialRadius * 0.038f
        minuteHandPaint.strokeWidth = dialRadius * 0.024f
        secondHandPaint.strokeWidth = dialRadius * 0.012f
        bezelPulsePaint.strokeWidth = dialRadius * 0.020f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(screenClipPath)

        // 1. Draw Frosted Glass Dial Face
        canvas.drawCircle(cx, cy, dialRadius, dialBackgroundPaint)
        canvas.drawArc(glassReflectionRect, 180f, 90f, true, glassHighlightPaint)

        // 2. Ambient Bezel Breathing Ring
        val timeNow = SystemClock.uptimeMillis()
        val pulse = (sin(timeNow * 0.0028) * 0.5 + 0.5).toFloat() // 0.0 to 1.0 cycle
        val alphaPulse = (35 + (pulse * 75)).toInt()
        bezelPulsePaint.color = Color.argb(alphaPulse, 0, 229, 255)
        canvas.drawCircle(cx, cy, dialRadius - (bezelPulsePaint.strokeWidth / 2f), bezelPulsePaint)
        canvas.drawCircle(cx, cy, dialRadius - bezelPulsePaint.strokeWidth, innerBorderPaint)

        // 3. Draw Dial Tick Marks
        drawTicks(canvas)

        // 4. Calculate Time & Render Hands
        val calendar = Calendar.getInstance()
        val hours = calendar.get(Calendar.HOUR)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        val millis = calendar.get(Calendar.MILLISECOND)

        // Continuous smooth angles (no discrete ticking jumps)
        val secAngle = ((seconds + millis / 1000f) / 60f) * 360f
        val minAngle = ((minutes + seconds / 60f) / 60f) * 360f
        val hourAngle = (((hours % 12) + minutes / 60f) / 12f) * 360f

        drawHand(canvas, hourAngle, dialRadius * 0.48f, hourHandPaint, -dialRadius * 0.08f)
        drawHand(canvas, minAngle, dialRadius * 0.72f, minuteHandPaint, -dialRadius * 0.10f)
        drawHand(canvas, secAngle, dialRadius * 0.84f, secondHandPaint, -dialRadius * 0.18f)

        // 5. Center Pivots
        canvas.drawCircle(cx, cy, dialRadius * 0.045f, centerCapPaint)
        canvas.drawCircle(cx, cy, dialRadius * 0.022f, centerDotPaint)

        // Continuous 60 FPS loop (only active while visible to protect battery)
        if (visibility == VISIBLE && isAttachedToWindow) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawTicks(canvas: Canvas) {
        val outerR = dialRadius * 0.90f
        val hourInnerR = dialRadius * 0.80f
        val minInnerR = dialRadius * 0.85f

        for (i in 0 until 60) {
            val angleDeg = i * 6.0
            val angleRad = Math.toRadians(angleDeg)
            val cosVal = cos(angleRad).toFloat()
            val sinVal = sin(angleRad).toFloat()

            val startX = cx + sinVal * outerR
            val startY = cy - cosVal * outerR

            when {
                i % 15 == 0 -> { // 12, 3, 6, 9 Cardinal Ticks
                    val stopX = cx + sinVal * (hourInnerR - (dialRadius * 0.03f))
                    val stopY = cy - cosVal * (hourInnerR - (dialRadius * 0.03f))
                    canvas.drawLine(startX, startY, stopX, stopY, accentTickPaint)
                }
                i % 5 == 0 -> { // Standard Hour Ticks
                    val stopX = cx + sinVal * hourInnerR
                    val stopY = cy - cosVal * hourInnerR
                    canvas.drawLine(startX, startY, stopX, stopY, hourTickPaint)
                }
                else -> { // Minor Minute Ticks
                    val stopX = cx + sinVal * minInnerR
                    val stopY = cy - cosVal * minInnerR
                    canvas.drawLine(startX, startY, stopX, stopY, minuteTickPaint)
                }
            }
        }
    }

    private fun drawHand(
        canvas: Canvas,
        angleDeg: Float,
        length: Float,
        paint: Paint,
        tailLength: Float
    ) {
        canvas.save()
        canvas.rotate(angleDeg, cx, cy)
        canvas.drawLine(cx, cy - tailLength, cx, cy - length, paint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    /**
     * Slides dial left and fades out to reveal underlying Bubble Launcher.
     */
    fun revealApps(animated: Boolean = true) {
        if (!animated) {
            visibility = GONE
            return
        }
        animate()
            .translationX(-width.toFloat())
            .alpha(0f)
            .setDuration(360)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .withEndAction {
                visibility = GONE
            }
            .start()
    }

    /**
     * Slides dial back into view from the left over the Bubble Launcher.
     */
    fun showClock(animated: Boolean = true) {
        visibility = VISIBLE
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
            .setDuration(360)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
        postInvalidateOnAnimation()
    }
}