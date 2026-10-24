package com.smartwatch.launcher.ui.themes

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
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.smartwatch.launcher.model.AppInfo
import kotlin.math.min

class AppThemeContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), AppThemeView {

    private val themeManager = AppThemeManager(context)
    private var appsList: List<AppInfo> = emptyList()

    private var activeThemeView: View? = null
    private var isSwitcherMode = false
    private var switcherScale = 1.0f
    private var previewThemeIndex = themeManager.currentTheme.id

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private val screenClipPath = Path()

    private val overlayPaint = Paint().apply { color = Color.argb(190, 0, 0, 0) }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 20, 24, 32)
        style = Paint.Style.FILL
    }
    private val activeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00E5FF.toInt() }
    private val inactiveDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(100, 255, 255, 255) }
    private val badgeRect = RectF()

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            openSwitcher()
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isSwitcherMode) {
                applyTheme(AppTheme.fromId(previewThemeIndex))
                return true
            }
            return false
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (isSwitcherMode) {
                val diffX = (e2.x) - (e1?.x ?: 0f)
                if (diffX < -40f) cycleTheme(1)
                else if (diffX > 40f) cycleTheme(-1)
                return true
            }
            return false
        }
    })

    init {
        setWillNotDraw(false)
        applyTheme(themeManager.currentTheme, save = false)
    }

    override fun setApps(apps: List<AppInfo>) {
        this.appsList = apps
        (activeThemeView as? AppThemeView)?.setApps(apps)
    }

    override fun resetToCenter(animated: Boolean) {
        if (isSwitcherMode) closeSwitcher()
        (activeThemeView as? AppThemeView)?.resetToCenter(animated)
    }

    private fun applyTheme(theme: AppTheme, save: Boolean = true) {
        if (save) themeManager.currentTheme = theme
        removeAllViews()

        val newView: View = when (theme) {
            AppTheme.ROTARY_WHEEL -> RotaryWheelView(context)
            AppTheme.VORTEX_TUNNEL -> VortexTunnelView(context)
            AppTheme.HONEYCOMB_MATRIX -> HoneycombGridView(context)
            AppTheme.CURVED_MAGNIFIER -> CurvedListView(context)
            AppTheme.CARD_DECK -> CardDeckView(context)
        }

        activeThemeView = newView
        addView(newView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        (newView as? AppThemeView)?.setApps(appsList)

        if (isSwitcherMode) closeSwitcher()
    }

    private fun openSwitcher() {
        isSwitcherMode = true
        previewThemeIndex = themeManager.currentTheme.id
        ValueAnimator.ofFloat(1.0f, 0.78f).apply {
            duration = 280
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                switcherScale = it.animatedValue as Float
                activeThemeView?.scaleX = switcherScale
                activeThemeView?.scaleY = switcherScale
                invalidate()
            }
            start()
        }
    }

    private fun closeSwitcher() {
        ValueAnimator.ofFloat(switcherScale, 1.0f).apply {
            duration = 240
            addUpdateListener {
                switcherScale = it.animatedValue as Float
                activeThemeView?.scaleX = switcherScale
                activeThemeView?.scaleY = switcherScale
                invalidate()
            }
            withEndAction {
                isSwitcherMode = false
                invalidate()
            }
            start()
        }
    }

    private fun cycleTheme(dir: Int) {
        val total = AppTheme.entries.size
        previewThemeIndex = (previewThemeIndex + dir + total) % total
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount == 2 && ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            openSwitcher()
            return true
        }
        if (isSwitcherMode) {
            gestureDetector.onTouchEvent(ev)
            return true
        }
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(cx, cy)
        screenClipPath.reset()
        screenClipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        titlePaint.textSize = radius * 0.12f
        subPaint.textSize = radius * 0.08f
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isSwitcherMode) {
            canvas.clipPath(screenClipPath)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

            val current = AppTheme.fromId(previewThemeIndex)
            val badgeW = radius * 1.35f
            val badgeH = radius * 0.38f
            val badgeY = height * 0.80f

            badgeRect.set(cx - (badgeW / 2f), badgeY - (badgeH / 2f), cx + (badgeW / 2f), badgeY + (badgeH / 2f))
            canvas.drawRoundRect(badgeRect, badgeH / 2f, badgeH / 2f, badgePaint)
            canvas.drawText(current.title, cx, badgeY - (radius * 0.03f), titlePaint)
            canvas.drawText("TAP TO SELECT", cx, badgeY + (radius * 0.09f), subPaint)

            val total = AppTheme.entries.size
            val spacing = radius * 0.10f
            val startX = cx - ((total - 1) * spacing) / 2f
            for (i in 0 until total) {
                val p = if (i == previewThemeIndex) activeDotPaint else inactiveDotPaint
                canvas.drawCircle(startX + (i * spacing), height * 0.14f, radius * 0.024f, p)
            }
        }
    }
}