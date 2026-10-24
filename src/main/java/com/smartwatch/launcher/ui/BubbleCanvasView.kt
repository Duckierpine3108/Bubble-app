package com.smartwatch.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import com.smartwatch.launcher.model.AppInfo
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance Custom Canvas Launcher tailored for circular smartwatches.
 * Features:
 * - Apple Watch-style Hexagonal spiral grid layout.
 * - Spherical compression lens & center magnification.
 * - Physics momentum panning with rubber-band bounds.
 * - Edge culling to prevent circular bezel clipping.
 */
class BubbleCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class BubbleNode(
        val appInfo: AppInfo,
        val gridX: Float,
        val gridY: Float
    )

    private var bubbles: List<BubbleNode> = emptyList()

    // Pan offsets
    private var offsetX = 0f
    private var offsetY = 0f

    // Center coordinates & screen radius
    private var cx = 0f
    private var cy = 0f
    private var screenRadius = 0f

    // Dimensional settings
    private var baseRadius = 0f
    private var baseSpacing = 0f

    // Physics & Gesture engines
    private val scroller = OverScroller(context)
    private val gestureDetector = GestureDetector(context, GestureListener())
    private var recenterAnimator: ValueAnimator? = null

    // Paints
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bubbleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(45, 255, 255, 255)
    }
    private val screenClipPath = Path()
    private val iconDstRect = Rect()
    private val iconSrcRect = Rect()

    // Center Focus HUD Text
    private var focusedApp: AppInfo? = null
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }
    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 20, 20, 24)
        style = Paint.Style.FILL
    }
    private val hudRect = RectF()

    init {
        isClickable = true
        isFocusable = true
    }

    fun setApps(apps: List<AppInfo>) {
        if (width > 0 && height > 0) {
            setupLayout(apps)
        } else {
            post { setupLayout(apps) }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        screenRadius = minOf(cx, cy)

        // Dynamically compute bubble sizes relative to round display diameter
        baseRadius = screenRadius * 0.22f
        baseSpacing = baseRadius * 2.30f
        hudTextPaint.textSize = screenRadius * 0.12f

        screenClipPath.reset()
        screenClipPath.addCircle(cx, cy, screenRadius, Path.Direction.CW)

        if (bubbles.isNotEmpty()) {
            setupLayout(bubbles.map { it.appInfo })
        }
    }

    /**
     * Places apps in an isotropic hexagonal spiral coordinate system.
     */
    private fun setupLayout(apps: List<AppInfo>) {
        if (apps.isEmpty()) {
            bubbles = emptyList()
            invalidate()
            return
        }

        val positions = computeHexagonalSpiral(apps.size, baseSpacing)
        bubbles = apps.mapIndexed { index, appInfo ->
            val pos = positions[index]
            BubbleNode(appInfo, pos.x, pos.y)
        }
        invalidate()
    }

    /**
     * Hexagonal coordinates (q, r) mapped to Cartesian (x, y) with isotropic distance.
     */
    private fun computeHexagonalSpiral(count: Int, spacing: Float): List<PointF> {
        val list = ArrayList<PointF>(count)
        list.add(PointF(0f, 0f)) // Center node (Ring 0)
        if (count == 1) return list

        val directions = arrayOf(
            Pair(1, 0),
            Pair(0, 1),
            Pair(-1, 1),
            Pair(-1, 0),
            Pair(0, -1),
            Pair(1, -1)
        )

        var ring = 1
        while (list.size < count) {
            var q = 0
            var r = -ring

            for (dir in directions) {
                for (step in 0 until ring) {
                    val x = spacing * (q + r * 0.5f)
                    val y = spacing * (r * 0.8660254f) // sqrt(3)/2
                    list.add(PointF(x, y))

                    if (list.size == count) return list

                    q += dir.first
                    r += dir.second
                }
            }
            ring++
        }
        return list
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Strict circular bezel clipping
        canvas.clipPath(screenClipPath)

        var closestApp: AppInfo? = null
        var closestDist = Float.MAX_VALUE

        // Iterate through all bubbles
        for (node in bubbles) {
            val rx = node.gridX + offsetX
            val ry = node.gridY + offsetY
            val d = hypot(rx, ry)
            val rho = if (screenRadius > 0f) d / screenRadius else 0f

            // Cull nodes far outside view boundary
            if (rho > 1.25f) continue

            // Spherical lens compression
            val compressedRho = rho / (1f + 0.32f * rho * rho)
            val screenDist = compressedRho * screenRadius
            val angle = atan2(ry, rx)
            val finalX = cx + cos(angle) * screenDist
            val finalY = cy + sin(angle) * screenDist

            val scale = calculateFisheyeScale(rho)
            if (scale <= 0.02f) continue

            val drawRadius = baseRadius * scale

            // Check closest bubble to screen center for HUD focus text
            if (d < closestDist) {
                closestDist = d
                closestApp = node.appInfo
            }

            // Draw bubble icon
            val left = (finalX - drawRadius).toInt()
            val top = (finalY - drawRadius).toInt()
            val right = (finalX + drawRadius).toInt()
            val bottom = (finalY + drawRadius).toInt()

            val bmp = node.appInfo.iconBitmap
            iconSrcRect.set(0, 0, bmp.width, bmp.height)
            iconDstRect.set(left, top, right, bottom)

            canvas.drawBitmap(bmp, iconSrcRect, iconDstRect, iconPaint)
            canvas.drawCircle(finalX, finalY, drawRadius, bubbleBorderPaint)
        }

        // Update HUD label for centered app
        focusedApp = if (closestDist < baseRadius * 1.1f) closestApp else null
        drawFocusedAppHud(canvas)
    }

    /**
     * Apple Watch-style fisheye curve:
     * - Center magnifying factor (1.25x)
     * - Quadratic drop toward bezel perimeter
     * - Vanishes gracefully right before hardware edge
     */
    private fun calculateFisheyeScale(rho: Float): Float {
        return when {
            rho < 0.35f -> {
                1.25f - (rho / 0.35f) * 0.25f
            }
            rho <= 1.0f -> {
                val t = (rho - 0.35f) / 0.65f
                1.0f - (t * t) * 0.70f
            }
            rho <= 1.20f -> {
                val t = (rho - 1.0f) / 0.20f
                (0.30f * (1f - t)).coerceAtLeast(0f)
            }
            else -> 0f
        }
    }

    /**
     * Draws an elegant pill HUD at the bottom of the circular display for the currently focused app.
     */
    private fun drawFocusedAppHud(canvas: Canvas) {
        val app = focusedApp ?: return
        val text = app.label
        val textWidth = hudTextPaint.measureText(text)
        val pillPaddingX = 22f
        val pillHeight = hudTextPaint.textSize * 1.5f
        val pillY = height * 0.85f

        hudRect.set(
            cx - (textWidth / 2f) - pillPaddingX,
            pillY - (pillHeight / 2f),
            cx + (textWidth / 2f) + pillPaddingX,
            pillY + (pillHeight / 2f)
        )

        canvas.drawRoundRect(hudRect, pillHeight / 2f, pillHeight / 2f, hudBgPaint)
        val textBaseline = pillY - ((hudTextPaint.descent() + hudTextPaint.ascent()) / 2f)
        canvas.drawText(text, cx, textBaseline, hudTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            enforceRubberBandBounds()
        }
        return handled || super.onTouchEvent(event)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.currX.toFloat()
            offsetY = scroller.currY.toFloat()
            postInvalidateOnAnimation()
        }
    }

    private fun getMaxDragDistance(): Float {
        if (bubbles.isEmpty()) return screenRadius
        val ringCount = ceil(sqrt(bubbles.size.toDouble() / 3.0)).toFloat().coerceAtLeast(1f)
        return ringCount * baseSpacing * 1.15f
    }

    private fun enforceRubberBandBounds() {
        val maxDist = getMaxDragDistance()
        val currentDist = hypot(offsetX, offsetY)
        if (currentDist > maxDist) {
            val targetX = (offsetX / currentDist) * maxDist
            val targetY = (offsetY / currentDist) * maxDist
            animateOffsets(targetX, targetY)
        }
    }

    /**
     * Resets bubble grid smoothly to center (called on Back/Home key).
     */
    fun resetToCenter(animated: Boolean = true) {
        if (!animated) {
            offsetX = 0f
            offsetY = 0f
            invalidate()
            return
        }
        scroller.forceFinished(true)
        animateOffsets(0f, 0f)
    }

    private fun animateOffsets(targetX: Float, targetY: Float) {
        recenterAnimator?.cancel()
        val startX = offsetX
        val startY = offsetY
        recenterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { animator ->
                val f = animator.animatedFraction
                offsetX = startX + (targetX - startX) * f
                offsetY = startY + (targetY - startY) * f
                invalidate()
            }
            start()
        }
    }

    private fun handleTap(touchX: Float, touchY: Float) {
        val tappedApp = findTappedApp(touchX, touchY) ?: return
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(tappedApp.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun findTappedApp(touchX: Float, touchY: Float): AppInfo? {
        var selected: AppInfo? = null
        var minTouchDist = Float.MAX_VALUE

        for (node in bubbles) {
            val rx = node.gridX + offsetX
            val ry = node.gridY + offsetY
            val d = hypot(rx, ry)
            val rho = if (screenRadius > 0f) d / screenRadius else 0f
            if (rho > 1.15f) continue

            val compressedRho = rho / (1f + 0.32f * rho * rho)
            val screenDist = compressedRho * screenRadius
            val angle = atan2(ry, rx)
            val finalX = cx + cos(angle) * screenDist
            val finalY = cy + sin(angle) * screenDist

            val scale = calculateFisheyeScale(rho)
            val effectiveRadius = baseRadius * scale

            val distFromTouch = hypot(touchX - finalX, touchY - finalY)
            if (distFromTouch <= effectiveRadius * 1.15f && distFromTouch < minTouchDist) {
                minTouchDist = distFromTouch
                selected = node.appInfo
            }
        }
        return selected
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            if (!scroller.isFinished) {
                scroller.forceFinished(true)
            }
            recenterAnimator?.cancel()
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            offsetX -= distanceX
            offsetY -= distanceY
            invalidate()
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val maxBound = getMaxDragDistance().toInt()
            scroller.fling(
                offsetX.toInt(),
                offsetY.toInt(),
                (velocityX * 0.85f).toInt(),
                (velocityY * 0.85f).toInt(),
                -maxBound,
                maxBound,
                -maxBound,
                maxBound
            )
            postInvalidateOnAnimation()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            handleTap(e.x, e.y)
            return true
        }
    }
}