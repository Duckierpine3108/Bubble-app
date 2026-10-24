package com.smartwatch.launcher.ui.themes

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
import android.widget.OverScroller
import com.smartwatch.launcher.model.AppInfo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

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

    private var rotationAngle = 180f
    private val itemAngleStep = 32f

    private val scroller = OverScroller(context)
    private val gestureDetector = GestureDetector(context, GestureListener())

    private val screenClipPath = Path()
    private val rimTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(45, 0, 229, 255)
        strokeWidth = 4f
    }
    private val focusMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
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
        color = Color.argb(190, 16, 20, 26)
        style = Paint.Style.FILL
    }
    private val iconDstRect = Rect()
    private val iconSrcRect = Rect()
    private val hudRect = RectF()

    init {
        isClickable = true
        isFocusable = true
    }

    override fun setApps(apps: List<AppInfo>) {
        this.apps = apps
        invalidate()
    }

    override fun resetToCenter(animated: Boolean) {
        rotationAngle = 180f
        scroller.forceFinished(true)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(cx, cy)
        wheelRadius = radius * 0.76f
        baseIconRadius = radius * 0.16f

        screenClipPath.reset()
        screenClipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        centerTitlePaint.textSize = radius * 0.13f
        centerSubPaint.textSize = radius * 0.08f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(screenClipPath)

        canvas.drawCircle(cx, cy, wheelRadius, rimTrackPaint)
        canvas.drawArc(
            cx - wheelRadius, cy - wheelRadius, cx + wheelRadius, cy + wheelRadius,
            165f, 30f, false, focusMarkerPaint
        )

        var closestApp: AppInfo? = null
        var minAngleDiff = Float.MAX_VALUE

        for (i in apps.indices) {
            val angleDeg = rotationAngle + (i * itemAngleStep)
            val normalizedDiff = normalizeDegrees(angleDeg - 180f)

            if (abs(normalizedDiff) > 95f) continue

            val rad = Math.toRadians(angleDeg.toDouble())
            val x = (cx + cos(rad) * wheelRadius).toFloat()
            val y = (cy + sin(rad) * wheelRadius).toFloat()

            val dist = abs(normalizedDiff)
            val scale = (1.35f - (dist / 85f) * 0.75f).coerceIn(0.5f, 1.35f)
            val alpha = ((1f - (dist / 95f)) * 255).toInt().coerceIn(0, 255)

            if (dist < minAngleDiff) {
                minAngleDiff = dist
                closestApp = apps[i]
            }

            val curRadius = baseIconRadius * scale
            val bmp = apps[i].iconBitmap
            iconSrcRect.set(0, 0, bmp.width, bmp.height)
            iconDstRect.set(
                (x - curRadius).toInt(), (y - curRadius).toInt(),
                (x + curRadius).toInt(), (y + curRadius).toInt()
            )

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.alpha = alpha
            }
            canvas.drawBitmap(bmp, iconSrcRect, iconDstRect, paint)
        }

        closestApp?.let { app ->
            val hudWidth = radius * 1.05f
            val hudHeight = radius * 0.44f
            hudRect.set(cx - (hudWidth / 2f), cy - (hudHeight / 2f), cx + (hudWidth / 2f), cy + (hudHeight / 2f))
            canvas.drawRoundRect(hudRect, hudHeight / 2f, hudHeight / 2f, centerHudBg)

            val textY = cy - ((centerTitlePaint.descent() + centerTitlePaint.ascent()) / 2f) - (radius * 0.05f)
            canvas.drawText(app.label, cx, textY, centerTitlePaint)
            canvas.drawText("TAP TO OPEN", cx, textY + (radius * 0.14f), centerSubPaint)
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
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun launchApp(app: AppInfo) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            rotationAngle += (distanceY * 0.28f)
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            scroller.fling(
                0, rotationAngle.toInt(),
                0, (velocityY * 0.35f).toInt(),
                0, 0, -50000, 50000
            )
            postInvalidateOnAnimation()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (hypot(e.x - cx, e.y - cy) < radius * 0.45f) {
                val focusedIndex = getFocusedIndex()
                if (focusedIndex in apps.indices) {
                    launchApp(apps[focusedIndex])
                    return true
                }
            }

            for (i in apps.indices) {
                val angleDeg = rotationAngle + (i * itemAngleStep)
                if (abs(normalizeDegrees(angleDeg - 180f)) > 90f) continue

                val rad = Math.toRadians(angleDeg.toDouble())
                val x = (cx + cos(rad) * wheelRadius).toFloat()
                val y = (cy + sin(rad) * wheelRadius).toFloat()
                if (hypot(e.x - x, e.y - y) <= baseIconRadius * 1.3f) {
                    launchApp(apps[i])
                    return true
                }
            }
            return false
        }
    }

    private fun getFocusedIndex(): Int {
        var minDiff = Float.MAX_VALUE
        var bestIndex = -1
        for (i in apps.indices) {
            val diff = abs(normalizeDegrees(rotationAngle + (i * itemAngleStep) - 180f))
            if (diff < minDiff) {
                minDiff = diff
                bestIndex = i
            }
        }
        return bestIndex
    }
}