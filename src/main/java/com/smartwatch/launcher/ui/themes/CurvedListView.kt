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
import kotlin.math.min

class CurvedListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), AppThemeView {

    private var apps: List<AppInfo> = emptyList()
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var itemHeight = 0f

    private var scrollY = 0f
    private val scroller = OverScroller(context)
    private val gestureDetector = GestureDetector(context, GestureListener())

    private val screenClipPath = Path()
    private val pillRect = RectF()
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 24, 28, 36)
        style = Paint.Style.FILL
    }
    private val pillActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val iconDstRect = Rect()
    private val iconSrcRect = Rect()
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        isClickable = true
        isFocusable = true
    }

    override fun setApps(apps: List<AppInfo>) {
        this.apps = apps
        invalidate()
    }

    override fun resetToCenter(animated: Boolean) {
        scrollY = 0f
        scroller.forceFinished(true)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(cx, cy)
        itemHeight = radius * 0.34f

        screenClipPath.reset()
        screenClipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        labelPaint.textSize = radius * 0.11f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(screenClipPath)

        for (i in apps.indices) {
            val yPos = cy + (i * itemHeight) - scrollY
            val diffY = yPos - cy

            if (abs(diffY) > radius * 1.05f) continue

            val normalized = (abs(diffY) / radius).coerceIn(0f, 1f)
            val curveX = cx - (normalized * normalized * (radius * 0.35f))
            val scale = (1.18f - normalized * 0.45f).coerceIn(0.65f, 1.18f)

            val pillW = radius * 1.45f * scale
            val pillH = itemHeight * 0.82f * scale
            val isFocused = abs(diffY) < itemHeight * 0.45f

            pillRect.set(
                curveX - (pillW / 2f), yPos - (pillH / 2f),
                curveX + (pillW / 2f), yPos + (pillH / 2f)
            )
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint)
            if (isFocused) {
                canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillActivePaint)
            }

            val iconSize = (pillH * 0.70f)
            val iconX = pillRect.left + (pillH * 0.20f)
            val iconY = yPos - (iconSize / 2f)
            val bmp = apps[i].iconBitmap

            iconSrcRect.set(0, 0, bmp.width, bmp.height)
            iconDstRect.set(
                iconX.toInt(), iconY.toInt(),
                (iconX + iconSize).toInt(), (iconY + iconSize).toInt()
            )
            canvas.drawBitmap(bmp, iconSrcRect, iconDstRect, iconPaint)

            val textBaseline = yPos - ((labelPaint.descent() + labelPaint.ascent()) / 2f)
            canvas.drawText(apps[i].label, iconX + iconSize + (pillH * 0.25f), textBaseline, labelPaint)
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = scroller.currY.toFloat()
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
            scrollY += distanceY
            scrollY = scrollY.coerceIn(0f, (apps.size - 1) * itemHeight)
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            scroller.fling(
                0, scrollY.toInt(),
                0, -velocityY.toInt(),
                0, 0, 0, ((apps.size - 1) * itemHeight).toInt()
            )
            postInvalidateOnAnimation()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            for (i in apps.indices) {
                val yPos = cy + (i * itemHeight) - scrollY
                if (abs(e.y - yPos) <= itemHeight * 0.45f) {
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