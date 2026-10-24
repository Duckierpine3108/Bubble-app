package com.smartwatch.launcher.ui.themes

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.OverScroller
import com.smartwatch.launcher.model.AppInfo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Premium Smartwatch Rotary Rim Menu:
 * - 27.5° optimal angular pacing ensuring 5 to 7 visible icons without crowding or sparse voids.
 * - Dynamic glowing cyan bezel arc track with luxury orbital watch gauge tick marks.
 * - Active center pop-out (1.35x magnification) with glowing neon halo and drop shadow.
 * - Frosted glass center launcher card with elastic spring-scale bounce upon item snapping.
 * - Mechanical rotary crown momentum and magnetic detent snapping.
 */
class RotaryWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), AppThemeView {

    private var apps: List<AppInfo> = emptyList()
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var wheelRadius = 0f
    private var baseIconRadius = 0f

    // 180° = 9 o'clock active selection position
    private var rotationAngle = 180f
    private val angleStep = 27.5f

    // Track active item changes to trigger spring pop
    private var lastFocusedIndex = -1
    private var cardSpringScale = 1.0f

    private val scroller = OverScroller(context)
    private val gestureDetector = GestureDetector(context, GestureListener())
    private var snapAnimator: ValueAnimator? = null
    private var cardBounceAnimator: ValueAnimator? = null

    private val screenClipPath = Path()

    // 1. Arc Track & Gauge Tick Paints
    private val trackGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = Color.argb(32, 0, 229, 255)
    }
    private val trackCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.argb(90, 0, 229, 255)
    }
    private val trackDetentGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        color = 0xFF00E5FF.toInt()
        setShadowLayer(10f, 0f, 0f, 0xFF00E5FF.toInt())
    }
    private val gaugeTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // 2. Center Card Paints
    private val cardShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }
    private val centerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val centerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // 3. Icon & Halo Paints
    private val activeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFF00E5FF.toInt()
        setShadowLayer(12f, 0f, 0f, 0xFF00E5FF.toInt())
    }
    private val activeHaloDisc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(50, 0, 229, 255)
    }
    private val iconDstRect = Rect()
    private val iconSrcRect = Rect()
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val hudRect = RectF()
    private val hudShadowRect = RectF()

    private data class WheelNode(
        val index: Int,
        val app: AppInfo,
        val angleDeg: Float,
        val absDiff: Float,
        val scale: Float,
        val alpha: Int,
        val x: Float,
        val y: Float
    )

    private val renderList = ArrayList<WheelNode>()

    init {
        isClickable = true
        isFocusable = true
    }

    override fun setApps(apps: List<AppInfo>) {
        this.apps = apps
        rotationAngle = 180f
        lastFocusedIndex = -1
        invalidate()
    }

    override fun resetToCenter(animated: Boolean) {
        snapAnimator?.cancel()
        scroller.forceFinished(true)
        if (!animated) {
            rotationAngle = 180f
            invalidate()
            return
        }
        animateToAngle(180f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(cx, cy)
        wheelRadius = radius * 0.78f
        baseIconRadius = radius * 0.135f

        screenClipPath.reset()
        screenClipPath.addCircle(cx, cy, radius, Path.Direction.CW)

        centerTitlePaint.textSize = radius * 0.125f
        centerSubPaint.textSize = radius * 0.078f

        // Center frosted glass card background gradient
        cardBgPaint.shader = LinearGradient(
            cx - (radius * 0.5f), cy - (radius * 0.25f),
            cx + (radius * 0.5f), cy + (radius * 0.25f),
            intArrayOf(0xEE1E2836.toInt(), 0xF5101620.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        cardBorderPaint.shader = LinearGradient(
            cx - (radius * 0.5f), cy - (radius * 0.25f),
            cx + (radius * 0.5f), cy + (radius * 0.25f),
            intArrayOf(0xFF00E5FF.toInt(), 0x3300E5FF.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(screenClipPath)

        // 1. Draw Left Bezel Glow Arc and Luxury Gauge Ticks
        drawGlowingRim(canvas)

        if (apps.isEmpty()) return

        // 2. Collect visible icons in balanced 7-node window (180° ± 3 items)
        val centerIndex = ((180f - rotationAngle) / angleStep).roundToInt().coerceIn(0, apps.size - 1)
        val minIdx = (centerIndex - 3).coerceAtLeast(0)
        val maxIdx = (centerIndex + 3).coerceAtMost(apps.size - 1)

        renderList.clear()
        var closestApp: AppInfo? = null
        var minDiff = Float.MAX_VALUE

        for (i in minIdx..maxIdx) {
            val itemAngle = rotationAngle + (i * angleStep)
            val diff = normalizeDegrees(itemAngle - 180f)
            val absDiff = abs(diff)

            // Graceful horizon: items transition smoothly up to 88°
            if (absDiff > 88f) continue

            val norm = (absDiff / 88f).coerceIn(0f, 1f)
            // Cosine continuous ease: 1.0 at center down to 0.0 at edge
            val factor = (cos(norm * Math.PI) * 0.5 + 0.5).toFloat()

            val scale = 0.52f + (1.35f - 0.52f) * factor
            val alpha = (45 + (210 * factor)).toInt().coerceIn(0, 255)

            val rad = Math.toRadians(itemAngle.toDouble())
            val x = (cx + cos(rad) * wheelRadius).toFloat()
            val y = (cy + sin(rad) * wheelRadius).toFloat()

            if (absDiff < minDiff) {
                minDiff = absDiff
                closestApp = apps[i]
            }

            renderList.add(WheelNode(i, apps[i], itemAngle, absDiff, scale, alpha, x, y))
        }

        // Trigger spring bounce if focus snapped to a new app
        if (centerIndex != lastFocusedIndex && minDiff < 10f) {
            lastFocusedIndex = centerIndex
            triggerCardBounce()
        }

        // 3. Render icons from back to front (center item on top)
        renderList.sortByDescending { it.absDiff }

        for (node in renderList) {
            val curRadius = baseIconRadius * node.scale
            val isCenter = node.absDiff < 14f

            // Center active item glowing halo surround
            if (isCenter) {
                canvas.drawCircle(node.x, node.y, curRadius * 1.18f, activeHaloDisc)
                canvas.drawCircle(node.x, node.y, curRadius * 1.10f, activeGlowPaint)
            }

            val bmp = node.app.iconBitmap
            iconSrcRect.set(0, 0, bmp.width, bmp.height)
            iconDstRect.set(
                (node.x - curRadius).toInt(), (node.y - curRadius).toInt(),
                (node.x + curRadius).toInt(), (node.y + curRadius).toInt()
            )

            iconPaint.alpha = node.alpha
            canvas.drawBitmap(bmp, iconSrcRect, iconDstRect, iconPaint)
        }

        // 4. Render Center Frosted Glass Card with Spring Bounce
        closestApp?.let { app ->
            drawCenterCard(canvas, app)
        }

        // Subtle subtext breathing animation
        postInvalidateOnAnimation()
    }

    private fun drawGlowingRim(canvas: Canvas) {
        // Outer glow arc
        canvas.drawArc(
            cx - wheelRadius, cy - wheelRadius,
            cx + wheelRadius, cy + wheelRadius,
            96f, 168f, false, trackGlowPaint
        )
        // Sharp core track
        canvas.drawArc(
            cx - wheelRadius, cy - wheelRadius,
            cx + wheelRadius, cy + wheelRadius,
            96f, 168f, false, trackCorePaint
        )
        // Active 9 o'clock notch detent
        canvas.drawArc(
            cx - wheelRadius, cy - wheelRadius,
            cx + wheelRadius, cy + wheelRadius,
            167f, 26f, false, trackDetentGlow
        )

        // Luxury mechanical gauge ticks along left bezel
        for (deg in 100..260 step 6) {
            val rad = Math.toRadians(deg.toDouble())
            val cosVal = cos(rad).toFloat()
            val sinVal = sin(rad).toFloat()

            val isMajor = (deg % 18 == 0)
            val len1 = if (isMajor) radius * 0.035f else radius * 0.018f
            val len2 = if (isMajor) radius * 0.015f else 0f

            val rOuter = wheelRadius + len1
            val rInner = wheelRadius - len2

            val x1 = cx + cosVal * rOuter
            val y1 = cy + sinVal * rOuter
            val x2 = cx + cosVal * rInner
            val y2 = cy + sinVal * rInner

            gaugeTickPaint.strokeWidth = if (isMajor) 2.5f else 1.5f
            gaugeTickPaint.color = if (isMajor) Color.argb(120, 0, 229, 255) else Color.argb(45, 255, 255, 255)
            canvas.drawLine(x1, y1, x2, y2, gaugeTickPaint)
        }
    }

    private fun drawCenterCard(canvas: Canvas, app: AppInfo) {
        val cardW = radius * 1.10f * cardSpringScale
        val cardH = radius * 0.48f * cardSpringScale
        val cardX = cx + (radius * 0.06f)
        val cardY = cy

        canvas.save()

        // Card Drop Shadow
        hudShadowRect.set(
            cardX - (cardW / 2f), cardY - (cardH / 2f) + (radius * 0.04f),
            cardX + (cardW / 2f), cardY + (cardH / 2f) + (radius * 0.04f)
        )
        canvas.drawRoundRect(hudShadowRect, cardH / 2f, cardH / 2f, cardShadowPaint)

        // Frosted Card Body
        hudRect.set(
            cardX - (cardW / 2f), cardY - (cardH / 2f),
            cardX + (cardW / 2f), cardY + (cardH / 2f)
        )
        canvas.drawRoundRect(hudRect, cardH / 2f, cardH / 2f, cardBgPaint)
        canvas.drawRoundRect(hudRect, cardH / 2f, cardH / 2f, cardBorderPaint)

        // Title
        val textY = cardY - ((centerTitlePaint.descent() + centerTitlePaint.ascent()) / 2f) - (radius * 0.045f)
        canvas.drawText(app.label, cardX, textY, centerTitlePaint)

        // Pulsing Neon Subtext
        val pulse = (sin(System.currentTimeMillis() * 0.0036) * 0.5 + 0.5).toFloat()
        val subAlpha = (170 + (pulse * 85)).toInt().coerceIn(0, 255)
        centerSubPaint.color = Color.argb(subAlpha, 0, 229, 255)
        canvas.drawText("TAP TO OPEN", cardX, textY + (radius * 0.135f), centerSubPaint)

        canvas.restore()
    }

    private fun triggerCardBounce() {
        cardBounceAnimator?.cancel()
        cardBounceAnimator = ValueAnimator.ofFloat(0.91f, 1.0f).apply {
            duration = 260
            interpolator = OvershootInterpolator(2.2f)
            addUpdateListener {
                cardSpringScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun normalizeDegrees(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            rotationAngle = scroller.currY.toFloat()
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (scroller.isFinished) {
                snapToNearestDetent()
            }
        }
        return handled || super.onTouchEvent(event)
    }

    private fun snapToNearestDetent() {
        if (apps.isEmpty()) return
        val targetIndex = ((180f - rotationAngle) / angleStep).roundToInt().coerceIn(0, apps.size - 1)
        val targetAngle = 180f - (targetIndex * angleStep)
        animateToAngle(targetAngle)
    }

    private fun animateToAngle(targetAngle: Float) {
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(rotationAngle, targetAngle).apply {
            duration = 230
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun launchApp(app: AppInfo) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            snapAnimator?.cancel()
            scroller.forceFinished(true)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            rotationAngle += (distanceY * 0.25f)
            val minAngle = 180f - ((apps.size - 1) * angleStep) - 15f
            val maxAngle = 180f + 15f
            rotationAngle = rotationAngle.coerceIn(minAngle, maxAngle)
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val minAngle = (180f - ((apps.size - 1) * angleStep)).toInt()
            scroller.fling(
                0, rotationAngle.toInt(),
                0, (velocityY * 0.32f).toInt(),
                0, 0, minAngle - 10, 190
            )
            postInvalidateOnAnimation()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // 1. Check Center Frosted Card Tap
            if (hudRect.contains(e.x, e.y)) {
                val centerIndex = ((180f - rotationAngle) / angleStep).roundToInt().coerceIn(0, apps.size - 1)
                if (centerIndex in apps.indices) {
                    launchApp(apps[centerIndex])
                    return true
                }
            }

            // 2. Check Visible Rim Icon Tap
            for (node in renderList) {
                val curRadius = baseIconRadius * node.scale
                if (hypot(e.x - node.x, e.y - node.y) <= curRadius * 1.35f) {
                    launchApp(node.app)
                    return true
                }
            }
            return false
        }
    }
}