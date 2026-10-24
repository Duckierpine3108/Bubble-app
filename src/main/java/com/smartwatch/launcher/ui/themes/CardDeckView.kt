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
import com.smartwatch.launcher.model.AppInfo
import kotlin.math.min
import kotlin.math.roundToInt

class CardDeckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), AppThemeView {

    private var apps: List<AppInfo> = emptyList()
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private var currentPosition = 0f
    private val gestureDetector = GestureDetector(context, GestureListener())
    private val screenClipPath = Path()

    private val cardRect = RectF()
    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(120, 0, 229, 255)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
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
        currentPosition = 0f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(cx, cy)
        screenClipPath.reset()
        screenClipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        titlePaint.textSize = radius * 0.12f
        tagPaint.textSize = radius * 0.08f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(screenClipPath)

        if (apps.isEmpty()) return

        val baseIndex = currentPosition.toInt()
        val maxDeck = min(apps.size - 1, baseIndex + 4)

        for (i in maxDeck downTo baseIndex.coerceAtLeast(0)) {
            val delta = i - currentPosition
            if (delta < -1f) continue

            val yOffset = cy + (delta * radius * 0.28f)
            val scale = (1.0f - (delta * 0.09f)).coerceIn(0.5f, 1.0f)
            val alpha = ((1.0f - (delta * 0.22f)) * 255).toInt().coerceIn(40, 255)

            val cardW = radius * 1.55f * scale
            val cardH = radius * 0.74f * scale

            cardRect.set(cx - (cardW / 2f), yOffset - (cardH / 2f), cx + (cardW / 2f), yOffset + (cardH / 2f))

            cardBgPaint.color = Color.argb(alpha.coerceAtMost(240), 20, 26, 36)
            canvas.drawRoundRect(cardRect, radius * 0.14f, radius * 0.14f, cardBgPaint)
            canvas.drawRoundRect(cardRect, radius * 0.14f, radius * 0.14f, cardBorderPaint)

            val iconSize = cardH * 0.65f
            val iconX = cardRect.left + (cardH * 0.18f)
            val iconY = yOffset - (iconSize / 2f)
            val bmp = apps[i].iconBitmap

            iconSrcRect.set(0, 0, bmp.width, bmp.height)
            iconDstRect.set(
                iconX.toInt(), iconY.toInt(),
                (iconX + iconSize).toInt(), (iconY + iconSize).toInt()
            )
            iconPaint.alpha = alpha
            canvas.drawBitmap(bmp, iconSrcRect, iconDstRect, iconPaint)

            val textX = iconX + iconSize + (cardH * 0.18f)
            canvas.drawText(apps[i].label, textX, yOffset - (radius * 0.03f), titlePaint)
            canvas.drawText("TAP TO LAUNCH", textX, yOffset + (radius * 0.09f), tagPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            snapToClosest()
        }
        return handled || super.onTouchEvent(event)
    }

    private fun snapToClosest() {
        val target = currentPosition.roundToInt().coerceIn(0, apps.size - 1).toFloat()
        ValueAnimator.ofFloat(currentPosition, target).apply {
            duration = 240
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                currentPosition = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            currentPosition += (distanceY / (radius * 0.6f))
            currentPosition = currentPosition.coerceIn(0f, (apps.size - 1).toFloat())
            invalidate()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val activeIndex = currentPosition.roundToInt()
            if (activeIndex in apps.indices) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                context.packageManager.getLaunchIntentForPackage(apps[activeIndex].packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
                return true
            }
            return false
        }
    }
}