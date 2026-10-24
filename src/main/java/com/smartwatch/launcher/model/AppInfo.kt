package com.smartwatch.launcher.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable

/**
 * Encapsulates app metadata and pre-rendered circular icon.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val iconBitmap: Bitmap
)

object BitmapUtils {

    /**
     * Converts any Drawable (AdaptiveIconDrawable, VectorDrawable, BitmapDrawable)
     * into a high-performance, pre-clipped circular Bitmap.
     */
    fun drawableToCircularBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clip circular path
        val clipPath = Path().apply {
            addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        // Dark circular backing plate to make transparent/vector icons pop
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF24242A.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bgPaint)

        // Render the drawable
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)

        return bitmap
    }
}