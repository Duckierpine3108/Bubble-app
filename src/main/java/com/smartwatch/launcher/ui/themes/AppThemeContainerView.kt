package com.smartwatch.launcher.ui.themes

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.smartwatch.launcher.model.AppInfo
import com.smartwatch.launcher.ui.BubbleCanvasView

/**
 * Host View for Active App Theme with integrated ThemeSelectorOverlayView.
 */
class AppThemeContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), AppThemeView {

    private val themeManager = AppThemeManager(context)
    private var appsList: List<AppInfo> = emptyList()

    private val activeThemeHolder = FrameLayout(context)
    private var activeThemeView: View? = null

    private val selectorOverlay = ThemeSelectorOverlayView(context)

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            if (!selectorOverlay.isOpen) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                openThemeSelector()
            }
        }
    })

    init {
        // 1. Add Active Theme Container
        addView(activeThemeHolder, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        // 2. Add Dedicated Theme Selector Overlay On Top
        addView(selectorOverlay, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        selectorOverlay.onThemeSelectedListener = { selectedTheme ->
            applyTheme(selectedTheme, save = true)
        }

        selectorOverlay.onDismissListener = {
            restoreActiveViewScale()
        }

        applyTheme(themeManager.currentTheme, save = false)
    }

    override fun setApps(apps: List<AppInfo>) {
        this.appsList = apps
        notifyAppsToChild(activeThemeView)
    }

    override fun resetToCenter(animated: Boolean) {
        if (selectorOverlay.isOpen) {
            selectorOverlay.close()
            return
        }
        when (val v = activeThemeView) {
            is AppThemeView -> v.resetToCenter(animated)
            is BubbleCanvasView -> v.resetToCenter(animated)
        }
    }

    private fun applyTheme(theme: AppTheme, save: Boolean) {
        if (save) themeManager.currentTheme = theme
        activeThemeHolder.removeAllViews()

        val newView: View = when (theme) {
            AppTheme.BUBBLE_CLOUD -> BubbleCanvasView(context)
            AppTheme.ROTARY_WHEEL -> RotaryWheelView(context)
            AppTheme.VORTEX_TUNNEL -> VortexTunnelView(context)
            AppTheme.HONEYCOMB_MATRIX -> HoneycombGridView(context)
            AppTheme.CURVED_MAGNIFIER -> CurvedListView(context)
            AppTheme.CARD_DECK -> CardDeckView(context)
        }

        activeThemeView = newView
        activeThemeHolder.addView(newView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        notifyAppsToChild(newView)
        restoreActiveViewScale()
    }

    private fun notifyAppsToChild(view: View?) {
        when (view) {
            is AppThemeView -> view.setApps(appsList)
            is BubbleCanvasView -> view.setApps(appsList)
        }
    }

    private fun openThemeSelector() {
        ValueAnimator.ofFloat(1.0f, 0.82f).apply {
            duration = 240
            interpolator = OvershootInterpolator(1.15f)
            addUpdateListener {
                val s = it.animatedValue as Float
                activeThemeHolder.scaleX = s
                activeThemeHolder.scaleY = s
            }
            start()
        }
        selectorOverlay.open(themeManager.currentTheme)
    }

    private fun restoreActiveViewScale() {
        activeThemeHolder.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(220)
            .start()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Two-Finger Double Tap Shortcut
        if (ev.pointerCount == 2 && ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            if (!selectorOverlay.isOpen) {
                openThemeSelector()
                return true
            }
        }

        if (selectorOverlay.isOpen) {
            return selectorOverlay.dispatchTouchEvent(ev)
        }

        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}