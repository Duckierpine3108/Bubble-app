package com.smartwatch.launcher.ui.themes

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * High-performance, round-dial optimized Theme Selector Carousel Overlay:
 * - Dims underlying view and renders frosted glass cards with neon cyan borders.
 * - Procedural vector icons for each theme layout.
 * - Elastic horizontal drag physics with magnetic snap.
 * - Bezel navigation arrows and pagination dots.
 */
class ThemeSelectorOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onThemeSelectedListener: ((AppTheme) -> Unit)? = null
    var onDismissListener: (() -> Unit)? = null

    var isOpen: Boolean = false
        private set

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private val screenClipPath = Path()

    private var carouselPosition = 0f
    private var snapAnimator: ValueAnimator? = null

    // Background Dim
    private val dimPaint = Paint().apply { color = Color.argb(215, 0, 0, 0) }

    // Card Paints
    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB0BEC5.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val actionButtonBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.FILL
    }
    private val actionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A0E14.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Vector Icon Paints
    private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF00E5FF.toInt()
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF00E5FF.toInt()
    }

    // Bezel Indicator Paints
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00E5FF.toInt() }
    private val dotInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 255, 255) }

    private val cardRect = RectF()
    private val actionPillRect = RectF()
    private val vectorPath = Path()

    private val gestureDetector = GestureDetector(context, GestureListener())

    init {
        visibility = GONE
        isClickable = true
        isFocusable = true
    }

    fun open(initialTheme: AppTheme) {
        isOpen = true
        carouselPosition = initialTheme.id.toFloat()
        visibility = VISIBLE
        alpha = 0f
        scaleX = 0.9f
        scaleY = 0.9f
        bringToFront()

        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .setListener(null)
            .start()

        invalidate()
    }

    fun close(onClosed: (() -> Unit)? = null) {
        animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isOpen = false
                    visibility = GONE
                    onClosed?.invoke()
                    onDismissListener?.invoke()
                }
            })
            .start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(cx, cy)

        screenClipPath.reset()
        screenClipPath.addCircle(cx, cy, radius, Path.Direction.CW)

        titlePaint.textSize = radius * 0.12f
        subTitlePaint.textSize = radius * 0.075f
        actionTextPaint.textSize = radius * 0.078f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(screenClipPath)

        // 1. Dim Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        val themes = AppTheme.values()

        // 2. Render Cards in Carousel
        for (i in themes.indices) {
            val delta = i - carouselPosition
            if (abs(delta) > 1.6f) continue

            val scale = (1.0f - abs(delta) * 0.22f).coerceIn(0.65f, 1.0f)
            val alpha = ((1.0f - abs(delta) * 0.55f) * 255).toInt().coerceIn(0, 255)
            val cardX = cx + (delta * radius * 1.35f)

            val cardW = radius * 1.34f * scale
            val cardH = radius * 1.18f * scale

            cardRect.set(
                cardX - (cardW / 2f), cy - (cardH / 2f),
                cardX + (cardW / 2f), cy + (cardH / 2f)
            )

            // Frosted Glass Card Body
            cardBgPaint.color = Color.argb(alpha.coerceAtMost(235), 20, 26, 36)
            canvas.drawRoundRect(cardRect, radius * 0.18f, radius * 0.18f, cardBgPaint)

            // Glowing Cyan Border
            val borderAlpha = if (abs(delta) < 0.35f) alpha else (alpha * 0.35f).toInt()
            cardBorderPaint.color = Color.argb(borderAlpha, 0, 229, 255)
            cardBorderPaint.strokeWidth = if (abs(delta) < 0.35f) 3.5f else 2f
            canvas.drawRoundRect(cardRect, radius * 0.18f, radius * 0.18f, cardBorderPaint)

            // Vector Badge Icon
            drawThemeBadge(canvas, themes[i], cardX, cy - (cardH * 0.22f), radius * 0.20f * scale, alpha)

            // Theme Title
            titlePaint.alpha = alpha
            canvas.drawText("${themes[i].id + 1}. ${themes[i].title}", cardX, cy + (cardH * 0.10f), titlePaint)

            // Theme Subtitle
            subTitlePaint.alpha = (alpha * 0.85f).toInt()
            canvas.drawText(themes[i].subtitle, cardX, cy + (cardH * 0.22f), subTitlePaint)

            // Action Button Pill (only active card)
            if (abs(delta) < 0.40f) {
                val btnW = cardW * 0.70f
                val btnH = cardH * 0.20f
                val btnY = cy + (cardH * 0.36f)
                actionPillRect.set(cardX - (btnW / 2f), btnY - (btnH / 2f), cardX + (btnW / 2f), btnY + (btnH / 2f))

                actionButtonBg.alpha = alpha
                canvas.drawRoundRect(actionPillRect, btnH / 2f, btnH / 2f, actionButtonBg)

                val textBase = btnY - ((actionTextPaint.descent() + actionTextPaint.ascent()) / 2f)
                actionTextPaint.alpha = alpha
                canvas.drawText("TAP TO APPLY", cardX, textBase, actionTextPaint)
            }
        }

        // 3. Left / Right Bezel Navigation Arrows
        drawNavigationArrows(canvas)

        // 4. Top Pagination Dots
        drawPaginationDots(canvas, themes.size)
    }

    private fun drawThemeBadge(canvas: Canvas, theme: AppTheme, x: Float, y: Float, size: Float, alpha: Int) {
        iconStrokePaint.alpha = alpha
        iconFillPaint.alpha = alpha

        when (theme) {
            AppTheme.BUBBLE_CLOUD -> {
                canvas.drawCircle(x, y, size * 0.32f, iconFillPaint)
                for (a in 0 until 6) {
                    val rad = Math.toRadians((a * 60.0))
                    val bx = (x + cos(rad) * size * 0.65f).toFloat()
                    val by = (y + sin(rad) * size * 0.65f).toFloat()
                    canvas.drawCircle(bx, by, size * 0.20f, iconStrokePaint)
                }
            }
            AppTheme.ROTARY_WHEEL -> {
                canvas.drawCircle(x, y, size * 0.65f, iconStrokePaint)
                canvas.drawCircle(x - (size * 0.65f), y, size * 0.24f, iconFillPaint)
                canvas.drawCircle(x, y, size * 0.22f, iconStrokePaint)
            }
            AppTheme.VORTEX_TUNNEL -> {
                canvas.drawCircle(x, y, size * 0.70f, iconStrokePaint)
                canvas.drawCircle(x, y, size * 0.45f, iconStrokePaint)
                canvas.drawCircle(x, y, size * 0.22f, iconFillPaint)
            }
            AppTheme.HONEYCOMB_MATRIX -> {
                drawHexagon(canvas, x, y, size * 0.45f)
                drawHexagon(canvas, x - (size * 0.48f), y, size * 0.25f)
                drawHexagon(canvas, x + (size * 0.48f), y, size * 0.25f)
            }
            AppTheme.CURVED_MAGNIFIER -> {
                val pillH = size * 0.20f
                canvas.drawRoundRect(x - (size * 0.50f), y - (size * 0.40f), x + (size * 0.50f), y - (size * 0.40f) + pillH, pillH / 2f, pillH / 2f, iconStrokePaint)
                canvas.drawRoundRect(x - (size * 0.65f), y - (pillH / 2f), x + (size * 0.65f), y + (pillH / 2f), pillH / 2f, pillH / 2f, iconFillPaint)
                canvas.drawRoundRect(x - (size * 0.50f), y + (size * 0.40f) - pillH, x + (size * 0.50f), y + (size * 0.40f), pillH / 2f, pillH / 2f, iconStrokePaint)
            }
            AppTheme.CARD_DECK -> {
                canvas.save()
                canvas.rotate(-8f, x, y)
                canvas.drawRoundRect(x - (size * 0.55f), y - (size * 0.35f), x + (size * 0.55f), y + (size * 0.35f), size * 0.10f, size * 0.10f, iconStrokePaint)
                canvas.restore()
                canvas.drawRoundRect(x - (size * 0.50f), y - (size * 0.30f), x + (size * 0.50f), y + (size * 0.30f), size * 0.10f, size * 0.10f, iconFillPaint)
            }
        }
    }

    private fun drawHexagon(canvas: Canvas, x: Float, y: Float, r: Float) {
        vectorPath.reset()
        for (i in 0 until 6) {
            val rad = Math.toRadians((i * 60.0))
            val px = (x + cos(rad) * r).toFloat()
            val py = (y + sin(rad) * r).toFloat()
            if (i == 0) vectorPath.moveTo(px, py) else vectorPath.lineTo(px, py)
        }
        vectorPath.close()
        canvas.drawPath(vectorPath, iconStrokePaint)
    }

    private fun drawNavigationArrows(canvas: Canvas) {
        val arrowPad = radius * 0.08f
        val arrowSize = radius * 0.06f

        // Left Arrow (<)
        if (carouselPosition > 0.1f) {
            vectorPath.reset()
            vectorPath.moveTo(arrowPad + arrowSize, cy - arrowSize)
            vectorPath.lineTo(arrowPad, cy)
            vectorPath.lineTo(arrowPad + arrowSize, cy + arrowSize)
            canvas.drawPath(vectorPath, arrowPaint)
        }

        // Right Arrow (>)
        if (carouselPosition < AppTheme.values().size - 1.1f) {
            val rightX = width - arrowPad
            vectorPath.reset()
            vectorPath.moveTo(rightX - arrowSize, cy - arrowSize)
            vectorPath.lineTo(rightX, cy)
            vectorPath.lineTo(rightX - arrowSize, cy + arrowSize)
            canvas.drawPath(vectorPath, arrowPaint)
        }
    }

    private fun drawPaginationDots(canvas: Canvas, total: Int) {
        val dotR = radius * 0.022f
        val spacing = radius * 0.085f
        val startX = cx - ((total - 1) * spacing) / 2f
        val dotY = radius * 0.18f

        val activeIndex = carouselPosition.roundToInt().coerceIn(0, total - 1)

        for (i in 0 until total) {
            val paint = if (i == activeIndex) dotActivePaint else dotInactivePaint
            canvas.drawCircle(startX + (i * spacing), dotY, dotR, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isOpen) return false
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            snapToNearest()
        }
        return handled || true
    }

    private fun snapToNearest() {
        val target = carouselPosition.roundToInt().coerceIn(0, AppTheme.values().size - 1).toFloat()
        snapTo(target)
    }

    private fun snapTo(target: Float) {
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(carouselPosition, target).apply {
            duration = 240
            interpolator = OvershootInterpolator(1.15f)
            addUpdateListener {
                carouselPosition = it.animatedValue as Float
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
            carouselPosition += (distanceX / (radius * 1.1f))
            carouselPosition = carouselPosition.coerceIn(0f, (AppTheme.values().size - 1).toFloat())
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val target = if (velocityX < -400) {
                ceil(carouselPosition)
            } else if (velocityX > 400) {
                floor(carouselPosition)
            } else {
                carouselPosition.roundToInt().toFloat()
            }.coerceIn(0f, (AppTheme.values().size - 1).toFloat())

            snapTo(target)
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Check Bezel Arrow Clicks
            val arrowPad = radius * 0.16f
            if (e.x < arrowPad && carouselPosition > 0.1f) {
                snapTo((carouselPosition.roundToInt() - 1).coerceAtLeast(0).toFloat())
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                return true
            }
            if (e.x > width - arrowPad && carouselPosition < AppTheme.values().size - 1.1f) {
                snapTo((carouselPosition.roundToInt() + 1).coerceAtMost(AppTheme.values().size - 1).toFloat())
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                return true
            }

            // Check Active Card Tap (Apply Theme)
            val activeIndex = carouselPosition.roundToInt().coerceIn(0, AppTheme.values().size - 1)
            val selectedTheme = AppTheme.values()[activeIndex]

            val cardW = radius * 1.34f
            val cardH = radius * 1.18f
            val clickRect = RectF(cx - (cardW / 2f), cy - (cardH / 2f), cx + (cardW / 2f), cy + (cardH / 2f))

            if (clickRect.contains(e.x, e.y)) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                close {
                    onThemeSelectedListener?.invoke(selectedTheme)
                }
                return true
            }

            // Tap Outside Closes Without Selection
            close()
            return true
        }
    }
}