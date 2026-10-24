package com.smartwatch.launcher.ui.themes

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 3D Radial Card Deck Stack:
 * - Continuous interpolation across fractional drag offsets.
 * - Native 3D perspective exit transition using Android Camera matrix (backward tilt + fade).
 * - Underlying cards ascend up an exponential curve with zero popping or sudden snapping.
 */
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
    private var snapAnimator: ValueAnimator? = null

    private val screenClipPath = Path()
    private val camera3d = Camera()
    private val matrix3d = Matrix()

    private val cardRect = RectF()
    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
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
        currentPosition = 0f
        invalidate()
    }

    override fun resetToCenter(animated: Boolean) {
        snapAnimator?.cancel()
        if (!animated) {
            currentPosition = 0f
            invalidate()
            return
        }
        animateToPosition(0f)
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
        val minIdx = (baseIndex - 1).coerceAtLeast(0)
        val maxIdx = (baseIndex + 4).coerceAtMost(apps.size - 1)

        // Draw in strict back-to-front order (descending delta)
        val indices = (minIdx..maxIdx).sortedByDescending { it - currentPosition }

        for (i in indices) {
            val delta = i - currentPosition

            // Cull cards fully outside the interaction frame
            if (delta < -1.15f || delta > 4.2f) continue

            canvas.save()

            val cardW = radius * 1.56f
            val cardH = radius * 0.74f

            if (delta < 0f) {
                // EXIT ANIMATION: Card glides upward, tilts back into 3D depth, and fades out
                val t = -delta // 0.0f to 1.15f
                val ease = t * t
                val yOffset = cy - (ease * radius * 1.25f)
                val scale = (1.0f - (t * 0.15f)).coerceAtLeast(0.80f)
                val alpha = ((1.0f - (t / 1.0f).coerceIn(0f, 1f)) * 255).toInt().coerceIn(0, 255)

                // 3D Backward Tilt
                val tiltAngle = -t * 26f
                camera3d.save()
                camera3d.rotateX(tiltAngle)
                camera3d.getMatrix(matrix3d)
                camera3d.restore()
                matrix3d.preTranslate(-cx, -yOffset)
                matrix3d.postTranslate(cx, yOffset)
                canvas.concat(matrix3d)

                canvas.scale(scale, scale, cx, yOffset)
                drawSingleCard(canvas, apps[i], cx, yOffset, cardW, cardH, alpha, isLead = false)

            } else {
                // STACK UNDERNEATH: Cards elevate upward along a smooth curve
                val t = delta // 0.0f to 4.0f
                val yOffset = cy + (t * radius * 0.27f)
                val scale = (1.0f - (t * 0.085f)).coerceIn(0.65f, 1.0f)
                val alpha = ((1.0f - (t * 0.22f)) * 255).toInt().coerceIn(35, 255)

                canvas.scale(scale, scale, cx, yOffset)
                drawSingleCard(canvas, apps[i], cx, yOffset, cardW, cardH, alpha, isLead = (delta < 0.45f))
            }

            canvas.restore()
        }
    }

    private fun drawSingleCard(
        canvas: Canvas,
        app: AppInfo,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        alpha: Int,
        isLead: Boolean
    ) {
        cardRect.set(x - (w / 2f), y - (h / 2f), x + (w / 2f), y + (h / 2f))

        // Background Glass Fill
        cardBgPaint.color = Color.argb(alpha.coerceAtMost(245), 18, 24, 34)
        canvas.drawRoundRect(cardRect, radius * 0.16f, radius * 0.16f, cardBgPaint)

        // Accent Border
        val borderAlpha = if (isLead) (alpha * 0.85f).toInt() else (alpha * 0.28f).toInt()
        cardBorderPaint.color = Color.argb(borderAlpha, 0, 229, 255)
        canvas.drawRoundRect(cardRect, radius * 0.16f, radius * 0.16f, cardBorderPaint)

        // Icon
        val iconSize = h * 0.64f
        val iconX = cardRect.left + (h * 0.20f)
        val iconY = y - (iconSize / 2f)
        val bmp = app.iconBitmap

        iconSrcRect.set(0, 0, bmp.width, bmp.height)
        iconDstRect.set(
            iconX.toInt(), iconY.toInt(),
            (iconX + iconSize).toInt(), (iconY + iconSize).toInt()
        )
        iconPaint.alpha = alpha
        canvas.drawBitmap(bmp, iconSrcRect, iconDstRect, iconPaint)

        // Text Content
        val textX = iconX + iconSize + (h * 0.18f)
        titlePaint.alpha = alpha
        tagPaint.alpha = alpha
        canvas.drawText(app.label, textX, y - (radius * 0.03f), titlePaint)
        canvas.drawText("TAP TO LAUNCH", textX, y + (radius * 0.095f), tagPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            snapToClosestCard()
        }
        return handled || super.onTouchEvent(event)
    }

    private fun snapToClosestCard() {
        val target = currentPosition.roundToInt().coerceIn(0, apps.size - 1).toFloat()
        animateToPosition(target)
    }

    private fun animateToPosition(target: Float) {
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(currentPosition, target).apply {
            duration = 260
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                currentPosition = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            snapAnimator?.cancel()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            currentPosition += (distanceY / (radius * 0.72f))
            currentPosition = currentPosition.coerceIn(0f, (apps.size - 1).toFloat())
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val target = if (velocityY < -450) {
                ceil(currentPosition)
            } else if (velocityY > 450) {
                floor(currentPosition)
            } else {
                currentPosition.roundToInt().toFloat()
            }.coerceIn(0f, (apps.size - 1).toFloat())

            animateToPosition(target)
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val activeIndex = currentPosition.roundToInt().coerceIn(0, apps.size - 1)
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