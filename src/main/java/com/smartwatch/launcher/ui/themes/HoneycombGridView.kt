package com.smartwatch.launcher.ui.themes

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import com.smartwatch.launcher.model.AppInfo
import kotlin.math.hypot
import kotlin.math.min

class HoneycombGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), AppThemeView {

    private var apps: List<AppInfo> = emptyList()
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var baseRadius = 0f

    private var offsetX = 0f
    private var offsetY = 0f

    private val scroller = OverScroller(context)
    private val gestureDetector = GestureDetector(context, GestureListener())
    private val screenClipPath = Path()

    private val nodePositions = ArrayList<PointF>()
    private val iconDstRect = Rect()
    private val iconSrcRect = Rect()
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        isClickable = true
        isFocusable = true
    }

    override fun setApps(apps: List<AppInfo>) {
        this.apps = apps
        calculateGrid()
        invalidate()
    }

    override fun resetToCenter(animated: Boolean) {
        offsetX = 0f
        offsetY = 0f
        scroller.forceFinished(true)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(cx, cy)
        baseRadius = radius * 0.24f

        screenClipPath.reset()
        screenClipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        calculateGrid()
    }

    private fun calculateGrid() {
        nodePositions.clear()
        val colSpacing = baseRadius * 2.2f
        val rowSpacing = baseRadius * 1.90f
        val cols = 4

        for (i in apps.indices) {
            val row = i / cols
            val col = i % cols
            val xOffset = if (row % 2 != 0) colSpacing * 0.5f else 0f
            val gx = (col - (cols / 2f)) * colSpacing + xOffset
            val gy = (row - (apps.size / (cols * 2f))) * rowSpacing
            nodePositions.add(PointF(gx, gy))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(screenClipPath)

        for (i in apps.indices) {
            if (i >= nodePositions.size) break
            val pos = nodePositions[i]
            val x = cx + pos.x + offsetX
            val y = cy + pos.y + offsetY

            val dist = hypot(x - cx, y - cy)
            val rho = dist / radius

            if (rho > 1.05f) continue

            val scale = when {
                rho < 0.40f -> 1.15f
                rho <= 0.88f -> 1.0f - (rho - 0.40f) * 0.8f
                else -> (0.60f * (1.05f - rho) / 0.17f).coerceAtLeast(0f)
            }
            if (scale <= 0.05f) continue

            val curRadius = baseRadius * scale
            val bmp = apps[i].iconBitmap
            iconSrcRect.set(0, 0, bmp.width, bmp.height)
            iconDstRect.set(
                (x - curRadius).toInt(), (y - curRadius).toInt(),
                (x + curRadius).toInt(), (y + curRadius).toInt()
            )
            canvas.drawBitmap(bmp, iconSrcRect, iconDstRect, iconPaint)
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.currX.toFloat()
            offsetY = scroller.currY.toFloat()
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            offsetX -= distanceX
            offsetY -= distanceY
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            scroller.fling(
                offsetX.toInt(), offsetY.toInt(),
                (velocityX * 0.7f).toInt(), (velocityY * 0.7f).toInt(),
                -2000, 2000, -3000, 3000
            )
            postInvalidateOnAnimation()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            for (i in apps.indices) {
                if (i >= nodePositions.size) break
                val pos = nodePositions[i]
                val x = cx + pos.x + offsetX
                val y = cy + pos.y + offsetY
                val dist = hypot(x - cx, y - cy)
                val rho = dist / radius

                val scale = if (rho < 0.40f) 1.15f else (1.0f - (rho - 0.40f) * 0.8f).coerceAtLeast(0.4f)
                val curRadius = baseRadius * scale

                if (hypot(e.x - x, e.y - y) <= curRadius * 1.15f) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    context.packageManager.getLaunchIntentForPackage(apps[i].packageName)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(it)
                    }
                    return true
                }
            }
            return false
        }
    }
}