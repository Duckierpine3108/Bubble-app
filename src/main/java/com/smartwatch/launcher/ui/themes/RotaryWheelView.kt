package com.smartwatch.launcher.ui.themes

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Premium Rotary Wheel Rim Menu:
 * - Fixed 38° angular spacing with strict 5–7 visible item culling to prevent clustering.
 * - Smooth cubic Hermite scaling and opacity falloff.
 * - Magnetic detent snap-to-item physics on drag release.
 * - Back-to-front rendering so the centered active app always sits above neighbors.
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

    // 180° corresponds to the 9 o'clock active selection position
    private var rotationAngle = 180f
    private val angleStep = 38f

    private val scroller = OverScroller(context)
    private val gestureDetector = GestureDetector(context, GestureListener())
    private var snapAnimator: ValueAnimator? = null

    private val screenClipPath = Path()
    private val rimTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(40, 0, 229, 255)
        strokeWidth = 3f
    }
    private val activeDetentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val centerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val centerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val centerHudBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 14, 18, 24)
        style = Paint.Style.FILL
    }
    private val centerHudBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(70, 0, 229, 255)
    }

    private val iconDstRect = Rect()
    private val iconSrcRect = Rect()
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val hudRect = RectF()

    private data class RenderNode(
        val index: Int,
        val app: AppInfo,
        val angleDeg: Float,
        val absDiff: Float,
        val scale: Float,
        val alpha: Int,
        val x: Float,
        val y: Float
    )

    private val renderList = ArrayList<RenderNode>()

    init {
        isClickable = true
        isFocusable = true
    }

    override fun setApps(apps: List<AppInfo>) {
        this.apps = apps
        rotationAngle = 180f
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
        baseIconRadius = radius * 0.13f

        screenClipPath.reset()
        screenClipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        centerTitlePaint.textSize = radius * 0.13f
        centerSubPaint.textSize = radius * 0.08f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(screenClipPath)

        // 1. Draw subtle background guide rim and active detent at 9 o'clock
        canvas.drawCircle(cx, cy, wheelRadius, rimTrackPaint)
        canvas.drawArc(
            cx - wheelRadius, cy - wheelRadius, cx + wheelRadius, cy + wheelRadius,
            165f, 30f, false, activeDetentPaint
        )

        if (apps.isEmpty()) return

        // 2. Identify the center-most item and cull items beyond a 7-node window
        val centerIndex = ((180f - rotationAngle) / angleStep).roundToInt()
        val minIdx = (centerIndex - 3).coerceAtLeast(0)
        val maxIdx = (centerIndex + 3).coerceAtMost(apps.size - 1)

        renderList.clear()
        var closestApp: AppInfo? = null
        var minDiff = Float.MAX_VALUE

        for (i in minIdx..maxIdx) {
            val itemAngle = rotationAngle + (i * angleStep)
            val diff = normalizeDegrees(itemAngle - 180f)
            val absDiff = abs(diff)

            // Strict angular horizon: cull anything beyond 85° to prevent rim bunching
            if (absDiff > 85f) continue

            // Cubic smooth-step falloff
            val norm = (absDiff / 85f).coerceIn(0f, 1f)
            val smooth = 1.0f - (norm * norm * (3f - 2f * norm))

            val scale = 0.35f + (1.20f - 0.35f) * smooth
            val alpha = (255 * (smooth * smooth)).toInt().coerceIn(0, 255)

            val rad = Math.toRadians(itemAngle.toDouble())
            val x = (cx + cos(rad) * wheelRadius).toFloat()
            val y = (cy + sin(rad) * wheelRadius).toFloat()

            if (absDiff < minDiff) {
                minDiff = absDiff
                closestApp = apps[i]
            }

            renderList.add(RenderNode(i, apps[i], itemAngle, absDiff, scale, alpha, x, y))
        }

        // Draw outer items first; center item drawn last (on top of all others)
        renderList.sortByDescending { it.absDiff }

        for (node in renderList) {
            val curRadius = baseIconRadius * node.scale
            val bmp = node.app.iconBitmap

            iconSrcRect.set(0, 0, bmp.width, bmp.height)
            iconDstRect.set(
                (node.x - curRadius).toInt(), (node.y - curRadius).toInt(),
                (node.x + curRadius).toInt(), (node.y + curRadius).toInt()
            )

            iconPaint.alpha = node.alpha
            canvas.drawBitmap(bmp, iconSrcRect, iconDstRect, iconPaint)
        }

        // 3. Center HUD Display for active app
        closestApp?.let { app ->
            val hudWidth = radius * 1.08f
            val hudHeight = radius * 0.46f
            hudRect.set(
                cx - (hudWidth / 2f), cy - (hudHeight / 2f),
                cx + (hudWidth / 2f), cy + (hudHeight / 2f)
            )
            canvas.drawRoundRect(hudRect, hudHeight / 2f, hudHeight / 2f, centerHudBg)
            canvas.drawRoundRect(hudRect, hudHeight / 2f, hudHeight / 2f, centerHudBorder)

            val textY = cy - ((centerTitlePaint.descent() + centerTitlePaint.ascent()) / 2f) - (radius * 0.045f)
            canvas.drawText(app.label, cx, textY, centerTitlePaint)
            canvas.drawText("TAP TO OPEN", cx, textY + (radius * 0.13f), centerSubPaint)
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
            duration = 240
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
            rotationAngle += (distanceY * 0.24f)
            val minAngle = 180f - ((apps.size - 1) * angleStep) - 20f
            val maxAngle = 180f + 20f
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
            // Tap on Center HUD Card
            if (hudRect.contains(e.x, e.y)) {
                val centerIndex = ((180f - rotationAngle) / angleStep).roundToInt().coerceIn(0, apps.size - 1)
                if (centerIndex in apps.indices) {
                    launchApp(apps[centerIndex])
                    return true
                }
            }

            // Tap on any visible rim icon
            for (node in renderList) {
                val curRadius = baseIconRadius * node.scale
                if (hypot(e.x - node.x, e.y - node.y) <= curRadius * 1.3f) {
                    launchApp(node.app)
                    return true
                }
            }
            return false
        }
    }
}