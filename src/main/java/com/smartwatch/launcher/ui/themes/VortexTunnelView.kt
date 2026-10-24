package com.smartwatch.launcher.ui.themes

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import com.smartwatch.launcher.model.AppInfo
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class VortexTunnelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), AppThemeView {

    private var apps: List<AppInfo> = emptyList()
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private var scrollOffset = 0f
    private val scroller = OverScroller(context)
    private val gestureDetector = GestureDetector(context, GestureListener())

    private val screenClipPath = Path()
    private val spiralTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(35, 124, 77, 255)
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val iconDstRect = Rect()
    private val iconSrcRect = Rect()

    init {
        isClickable = true
        isFocusable = true
    }

    override fun setApps(apps: List<AppInfo>) {
        this.apps = apps
        invalidate()
    }

    override fun resetToCenter(animated: Boolean) {
        scrollOffset = 0f
        scroller.forceFinished(true)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(cx, cy)
        screenClipPath.reset()
        screenClipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        textPaint.textSize = radius * 0.10f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(screenClipPath)

        for (ring in 1..4) {
            canvas.drawCircle(cx, cy, radius * (ring * 0.23f), spiralTrackPaint)
        }

        if (apps.isEmpty()) return

        for (i in (apps.size - 1) downTo 0) {
            val virtualIndex = i - (scrollOffset * 0.12f)
            if (virtualIndex < -1f || virtualIndex > 9.5f) continue

            val depth = virtualIndex.coerceIn(0f, 9f) / 9f
            val distance = radius * (1.0f - depth * 0.82f)
            val angleRad = Math.toRadians((virtualIndex * 58.0 + (scrollOffset * 2.0)))

            val x = (cx + cos(angleRad) * distance).toFloat()
            val y = (cy + sin(angleRad) * distance).toFloat()

            val scale = (1.30f - depth * 0.90f).coerceIn(0.25f, 1.30f)
            val alpha = ((1.0f - (depth * 0.70f)) * 255).toInt().coerceIn(20, 255)
            val iconRadius = (radius * 0.18f) * scale

            val bmp = apps[i].iconBitmap
            iconSrcRect.set(0, 0, bmp.width, bmp.height)
            iconDstRect.set(
                (x - iconRadius).toInt(), (y - iconRadius).toInt(),
                (x + iconRadius).toInt(), (y + iconRadius).toInt()
            )

            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.alpha = alpha
            }
            canvas.drawBitmap(bmp, iconSrcRect, iconDstRect, p)

            if (scale > 0.95f) {
                canvas.drawText(apps[i].label, x, y + iconRadius + textPaint.textSize, textPaint)
            }
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currY.toFloat()
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
            scrollOffset += (distanceY * 0.22f)
            scrollOffset = scrollOffset.coerceIn(0f, apps.size * 8.3f)
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            scroller.fling(
                0, scrollOffset.toInt(),
                0, (velocityY * 0.4f).toInt(),
                0, 0, 0, (apps.size * 8.3f).toInt()
            )
            postInvalidateOnAnimation()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            for (i in apps.indices) {
                val virtualIndex = i - (scrollOffset * 0.12f)
                if (virtualIndex < -0.5f || virtualIndex > 7.5f) continue

                val depth = virtualIndex.coerceIn(0f, 9f) / 9f
                val distance = radius * (1.0f - depth * 0.82f)
                val angleRad = Math.toRadians((virtualIndex * 58.0 + (scrollOffset * 2.0)))

                val x = (cx + cos(angleRad) * distance).toFloat()
                val y = (cy + sin(angleRad) * distance).toFloat()
                val scale = (1.30f - depth * 0.90f).coerceIn(0.25f, 1.30f)
                val iconRadius = (radius * 0.18f) * scale

                if (hypot(e.x - x, e.y - y) <= iconRadius * 1.25f) {
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