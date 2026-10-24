package com.smartwatch.launcher.ui.watchfaces

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Common interface for all 60 FPS modular watch face renderers.
 */
interface WatchFaceRenderer {
    fun getName(): String
    fun onSizeChanged(width: Int, height: Int, cx: Float, cy: Float, radius: Float)
    fun draw(canvas: Canvas, timeMillis: Long, batteryPercent: Float)
}

/**
 * Manages active watch face state, persistence, and collection of renderers.
 */
class WatchFaceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smartwatch_prefs", Context.MODE_PRIVATE)

    val renderers: List<WatchFaceRenderer> = listOf(
        FrostedNeonRenderer(),
        PixelArcRenderer(),
        ChronographRenderer(),
        CyberNebulaRenderer(),
        RetroGeometricRenderer()
    )

    var currentFaceIndex: Int
        get() = prefs.getInt("active_face_index", 0).coerceIn(0, renderers.size - 1)
        set(value) {
            prefs.edit().putInt("active_face_index", value.coerceIn(0, renderers.size - 1)).apply()
        }

    fun getActiveRenderer(): WatchFaceRenderer = renderers[currentFaceIndex]
}

// =========================================================================
// DIAL 1: FROSTED GLASS NEON GLOW (Enhanced Halo + Particle Ambience)
// =========================================================================
class FrostedNeonRenderer : WatchFaceRenderer {
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassReflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val hourTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val minuteTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 200, 230, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val cardinalTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val hourHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF39FF14.toInt() // Neon Lime
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(14f, 0f, 0f, 0xFF39FF14.toInt())
    }
    private val minHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt() // Neon Cyan
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(14f, 0f, 0f, 0xFF00E5FF.toInt())
    }
    private val secHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF0055.toInt() // Hot Neon Pink
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(8f, 0f, 0f, 0xFFFF0055.toInt())
    }

    private val centerCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00E5FF.toInt() }

    private val glassArcRect = RectF()
    private val calendar = Calendar.getInstance()

    // 14 Ambient Halo Particles
    private val particleAngles = FloatArray(14) { it * (360f / 14f) }
    private val particleSpeeds = floatArrayOf(
        0.04f, -0.03f, 0.05f, -0.06f, 0.03f, -0.04f, 0.07f,
        -0.05f, 0.04f, -0.03f, 0.06f, -0.04f, 0.05f, -0.03f
    )

    override fun getName(): String = "Frosted Neon"

    override fun onSizeChanged(width: Int, height: Int, cx: Float, cy: Float, radius: Float) {
        this.cx = cx
        this.cy = cy
        this.radius = radius

        bgPaint.shader = RadialGradient(
            cx, cy * 0.75f, radius * 1.15f,
            intArrayOf(0xDD1B2936.toInt(), 0xF00D141C.toInt(), 0xFF040608.toInt()),
            floatArrayOf(0.0f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )

        glassReflectionPaint.shader = LinearGradient(
            cx - radius, cy - radius, cx + radius * 0.5f, cy + radius * 0.5f,
            intArrayOf(Color.argb(40, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        glassArcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        hourTickPaint.strokeWidth = radius * 0.022f
        minuteTickPaint.strokeWidth = radius * 0.010f
        cardinalTickPaint.strokeWidth = radius * 0.028f

        hourHandPaint.strokeWidth = radius * 0.036f
        minHandPaint.strokeWidth = radius * 0.024f
        secHandPaint.strokeWidth = radius * 0.012f
        haloPaint.strokeWidth = radius * 0.018f
    }

    override fun draw(canvas: Canvas, timeMillis: Long, batteryPercent: Float) {
        calendar.timeInMillis = timeMillis
        val h = calendar.get(Calendar.HOUR)
        val m = calendar.get(Calendar.MINUTE)
        val s = calendar.get(Calendar.SECOND)
        val ms = calendar.get(Calendar.MILLISECOND)

        val secAngle = ((s + ms / 1000f) / 60f) * 360f
        val minAngle = ((m + s / 60f) / 60f) * 360f
        val hourAngle = (((h % 12) + m / 60f) / 12f) * 360f

        // 1. Background & Specular Reflection
        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawArc(glassArcRect, 180f, 90f, true, glassReflectionPaint)

        // 2. Halo Glow & Ambient Floating Particles
        val pulse = (sin(timeMillis * 0.003) * 0.5 + 0.5).toFloat()
        haloPaint.color = Color.argb((40 + (pulse * 70)).toInt(), 0, 229, 255)
        canvas.drawCircle(cx, cy, radius * 0.94f, haloPaint)

        for (i in particleAngles.indices) {
            particleAngles[i] = (particleAngles[i] + particleSpeeds[i]) % 360f
            val rad = Math.toRadians(particleAngles[i].toDouble())
            val pDist = radius * (0.45f + 0.35f * sin((timeMillis * 0.001) + i).toFloat())
            val px = cx + (cos(rad) * pDist).toFloat()
            val py = cy + (sin(rad) * pDist).toFloat()
            val pAlpha = (50 + 60 * sin((timeMillis * 0.002) + i * 0.7).toFloat()).toInt().coerceIn(0, 255)
            particlePaint.color = Color.argb(pAlpha, 57, 255, 20)
            canvas.drawCircle(px, py, radius * 0.016f, particlePaint)
        }

        // 3. Ticks
        for (i in 0 until 60) {
            val rad = Math.toRadians((i * 6.0))
            val sinVal = sin(rad).toFloat()
            val cosVal = cos(rad).toFloat()
            val startR = radius * 0.90f

            when {
                i % 15 == 0 -> {
                    val stopR = radius * 0.77f
                    canvas.drawLine(cx + sinVal * startR, cy - cosVal * startR, cx + sinVal * stopR, cy - cosVal * stopR, cardinalTickPaint)
                }
                i % 5 == 0 -> {
                    val stopR = radius * 0.81f
                    canvas.drawLine(cx + sinVal * startR, cy - cosVal * startR, cx + sinVal * stopR, cy - cosVal * stopR, hourTickPaint)
                }
                else -> {
                    val stopR = radius * 0.86f
                    canvas.drawLine(cx + sinVal * startR, cy - cosVal * startR, cx + sinVal * stopR, cy - cosVal * stopR, minuteTickPaint)
                }
            }
        }

        // 4. Hands
        drawRotatedHand(canvas, hourAngle, radius * 0.48f, hourHandPaint, -radius * 0.08f)
        drawRotatedHand(canvas, minAngle, radius * 0.72f, minHandPaint, -radius * 0.10f)
        drawRotatedHand(canvas, secAngle, radius * 0.84f, secHandPaint, -radius * 0.18f)

        canvas.drawCircle(cx, cy, radius * 0.045f, centerCapPaint)
        canvas.drawCircle(cx, cy, radius * 0.022f, centerDotPaint)
    }

    private fun drawRotatedHand(canvas: Canvas, deg: Float, length: Float, paint: Paint, tail: Float) {
        canvas.save()
        canvas.rotate(deg, cx, cy)
        canvas.drawLine(cx, cy - tail, cx, cy - length, paint)
        canvas.restore()
    }
}

// =========================================================================
// DIAL 2: PIXEL ARC MINIMALIST (Clean Google Pixel Watch Style)
// =========================================================================
class PixelArcRenderer : WatchFaceRenderer {
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private val bgPaint = Paint().apply { color = 0xFF000000.toInt() }
    private val timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val dateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAAAAAA.toInt()
        textAlign = Paint.Align.CENTER
    }

    private val arcTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(35, 255, 255, 255)
        strokeCap = Paint.Cap.ROUND
    }
    private val secArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val batArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF00E676.toInt() // Emerald Green
        strokeCap = Paint.Cap.ROUND
    }

    private val outerArcRect = RectF()
    private val innerArcRect = RectF()
    private val calendar = Calendar.getInstance()

    override fun getName(): String = "Pixel Arc"

    override fun onSizeChanged(width: Int, height: Int, cx: Float, cy: Float, radius: Float) {
        this.cx = cx
        this.cy = cy
        this.radius = radius

        timeTextPaint.textSize = radius * 0.44f
        dateTextPaint.textSize = radius * 0.11f

        val stroke = radius * 0.045f
        arcTrackPaint.strokeWidth = stroke
        secArcPaint.strokeWidth = stroke
        batArcPaint.strokeWidth = stroke * 0.85f

        val padOuter = stroke * 1.5f
        outerArcRect.set(cx - radius + padOuter, cy - radius + padOuter, cx + radius - padOuter, cy + radius - padOuter)

        val padInner = padOuter + stroke * 1.8f
        innerArcRect.set(cx - radius + padInner, cy - radius + padInner, cx + radius - padInner, cy + radius - padInner)

        secArcPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(0xFF7C4DFF.toInt(), 0xFF00E5FF.toInt(), 0xFF00E676.toInt(), 0xFF7C4DFF.toInt()),
            floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f)
        )
    }

    override fun draw(canvas: Canvas, timeMillis: Long, batteryPercent: Float) {
        calendar.timeInMillis = timeMillis
        val h = calendar.get(Calendar.HOUR_OF_DAY)
        val m = calendar.get(Calendar.MINUTE)
        val s = calendar.get(Calendar.SECOND)
        val ms = calendar.get(Calendar.MILLISECOND)

        // 1. OLED Pure Black Background
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // 2. Outer Smooth Sweeping Second Arc
        val secSweep = ((s + ms / 1000f) / 60f) * 360f
        canvas.drawCircle(cx, cy, radius - arcTrackPaint.strokeWidth * 1.5f, arcTrackPaint)

        canvas.save()
        canvas.rotate(-90f, cx, cy)
        canvas.drawArc(outerArcRect, 0f, secSweep, false, secArcPaint)
        canvas.restore()

        // 3. Inner Battery Arc
        val batSweep = (batteryPercent / 100f).coerceIn(0f, 1f) * 140f
        canvas.drawArc(innerArcRect, 110f, 140f, false, arcTrackPaint)
        canvas.drawArc(innerArcRect, 110f, batSweep, false, batArcPaint)

        // 4. Center Bold Typography
        val timeStr = String.format("%02d:%02d", h, m)
        val yOffset = (timeTextPaint.descent() + timeTextPaint.ascent()) / 2f
        canvas.drawText(timeStr, cx, cy - yOffset - (radius * 0.05f), timeTextPaint)

        val days = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        val dayStr = "${days[calendar.get(Calendar.DAY_OF_WEEK) - 1]}  ${calendar.get(Calendar.DAY_OF_MONTH)}  •  ${batteryPercent.toInt()}%"
        canvas.drawText(dayStr, cx, cy + (radius * 0.28f), dateTextPaint)
    }
}

// =========================================================================
// DIAL 3: CHRONOGRAPH DARK METALLIC (Swiss Luxury + Tourbillon Escapement)
// =========================================================================
class ChronographRenderer : WatchFaceRenderer {
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subDialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55111418.toInt() }
    private val subDialBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(70, 200, 200, 210)
        strokeWidth = 2f
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD8D8E0.toInt()
        style = Paint.Style.STROKE
    }
    private val mainHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF0F0F5.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(8f, 0f, 4f, Color.BLACK)
    }
    private val subHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5252.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val gearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD4AF37.toInt() // Metallic Gold Tourbillon
        style = Paint.Style.STROKE
    }

    private val calendar = Calendar.getInstance()

    override fun getName(): String = "Swiss Chrono"

    override fun onSizeChanged(width: Int, height: Int, cx: Float, cy: Float, radius: Float) {
        this.cx = cx
        this.cy = cy
        this.radius = radius

        bgPaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(0xFF282D36.toInt(), 0xFF14171C.toInt(), 0xFF08090C.toInt()),
            floatArrayOf(0.0f, 0.7f, 1.0f),
            Shader.TileMode.CLAMP
        )

        tickPaint.strokeWidth = radius * 0.016f
        mainHandPaint.strokeWidth = radius * 0.034f
        subHandPaint.strokeWidth = radius * 0.014f
        gearPaint.strokeWidth = radius * 0.012f
    }

    override fun draw(canvas: Canvas, timeMillis: Long, batteryPercent: Float) {
        calendar.timeInMillis = timeMillis
        val h = calendar.get(Calendar.HOUR)
        val m = calendar.get(Calendar.MINUTE)
        val s = calendar.get(Calendar.SECOND)
        val ms = calendar.get(Calendar.MILLISECOND)

        // 1. Brushed Titanium Base
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // 2. Chronograph Outer Ticks
        for (i in 0 until 60) {
            val rad = Math.toRadians((i * 6.0))
            val sinVal = sin(rad).toFloat()
            val cosVal = cos(rad).toFloat()
            val r1 = radius * 0.92f
            val r2 = if (i % 5 == 0) radius * 0.82f else radius * 0.88f
            canvas.drawLine(cx + sinVal * r1, cy - cosVal * r1, cx + sinVal * r2, cy - cosVal * r2, tickPaint)
        }

        // 3. Top Sub-Dial: Battery Meter
        val topSubCy = cy - radius * 0.38f
        val subRadius = radius * 0.22f
        canvas.drawCircle(cx, topSubCy, subRadius, subDialBgPaint)
        canvas.drawCircle(cx, topSubCy, subRadius, subDialBorderPaint)
        val batAngle = -60f + (batteryPercent / 100f) * 120f
        drawMiniHand(canvas, cx, topSubCy, batAngle, subRadius * 0.75f, subHandPaint)

        // 4. Bottom Sub-Dial: Tourbillon Mechanical Escapement Gear
        val bottomSubCy = cy + radius * 0.40f
        canvas.drawCircle(cx, bottomSubCy, subRadius * 1.05f, subDialBgPaint)
        canvas.drawCircle(cx, bottomSubCy, subRadius * 1.05f, subDialBorderPaint)

        val balanceAngle = (sin(timeMillis * 0.018) * 60f).toFloat()
        canvas.save()
        canvas.rotate(balanceAngle, cx, bottomSubCy)
        for (a in 0 until 6) {
            val rRad = Math.toRadians((a * 60.0))
            canvas.drawLine(
                cx, bottomSubCy,
                cx + (cos(rRad) * subRadius * 0.85f).toFloat(),
                bottomSubCy + (sin(rRad) * subRadius * 0.85f).toFloat(),
                gearPaint
            )
        }
        canvas.drawCircle(cx, bottomSubCy, subRadius * 0.45f, gearPaint)
        canvas.restore()

        // 5. Main Chronograph Hands
        val hourAngle = (((h % 12) + m / 60f) / 12f) * 360f
        val minAngle = ((m + s / 60f) / 60f) * 360f
        val secAngle = ((s + ms / 1000f) / 60f) * 360f

        drawRotatedHand(canvas, hourAngle, radius * 0.48f, mainHandPaint)
        drawRotatedHand(canvas, minAngle, radius * 0.74f, mainHandPaint)
        drawRotatedHand(canvas, secAngle, radius * 0.85f, subHandPaint)

        canvas.drawCircle(cx, cy, radius * 0.04f, tickPaint)
    }

    private fun drawMiniHand(canvas: Canvas, x: Float, y: Float, deg: Float, len: Float, paint: Paint) {
        canvas.save()
        canvas.rotate(deg, x, y)
        canvas.drawLine(x, y, x, y - len, paint)
        canvas.restore()
    }

    private fun drawRotatedHand(canvas: Canvas, deg: Float, len: Float, paint: Paint) {
        canvas.save()
        canvas.rotate(deg, cx, cy)
        canvas.drawLine(cx, cy + (radius * 0.1f), cx, cy - len, paint)
        canvas.restore()
    }
}

// =========================================================================
// DIAL 4: CYBER MOTION NEBULA (Sci-Fi Vector Waves & Particle Fields)
// =========================================================================
class CyberNebulaRenderer : WatchFaceRenderer {
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private val bgPaint = Paint().apply { color = 0xFF05050A.toInt() }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 229, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(10f, 0f, 0f, 0xFF00E5FF.toInt())
    }
    private val secDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.FILL
    }

    private val wavePath = Path()
    private val calendar = Calendar.getInstance()

    override fun getName(): String = "Cyber Nebula"

    override fun onSizeChanged(width: Int, height: Int, cx: Float, cy: Float, radius: Float) {
        this.cx = cx
        this.cy = cy
        this.radius = radius
        handPaint.strokeWidth = radius * 0.026f
        wavePaint.strokeWidth = radius * 0.018f
    }

    override fun draw(canvas: Canvas, timeMillis: Long, batteryPercent: Float) {
        calendar.timeInMillis = timeMillis
        val h = calendar.get(Calendar.HOUR)
        val m = calendar.get(Calendar.MINUTE)
        val s = calendar.get(Calendar.SECOND)
        val ms = calendar.get(Calendar.MILLISECOND)

        canvas.drawCircle(cx, cy, radius, bgPaint)

        // 1. Procedural Kinetic Nebula Rings
        val timeSec = timeMillis * 0.0015
        for (w in 1..3) {
            wavePath.reset()
            val baseR = radius * (0.28f * w)
            val color = if (w % 2 == 0) Color.argb(70, 124, 77, 255) else Color.argb(60, 0, 229, 255)
            wavePaint.color = color

            for (deg in 0..360 step 8) {
                val rRad = Math.toRadians(deg.toDouble())
                val morph = sin(rRad * 4.0 + timeSec * (w * 0.8)) * (radius * 0.06f)
                val rFinal = baseR + morph.toFloat()
                val px = cx + (cos(rRad) * rFinal).toFloat()
                val py = cy + (sin(rRad) * rFinal).toFloat()
                if (deg == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
            }
            wavePath.close()
            canvas.drawPath(wavePath, wavePaint)
        }

        // 2. HUD Coordinate Reticles
        canvas.drawCircle(cx, cy, radius * 0.88f, crosshairPaint)
        canvas.drawLine(cx, cy - radius * 0.93f, cx, cy - radius * 0.83f, crosshairPaint)
        canvas.drawLine(cx, cy + radius * 0.93f, cx, cy + radius * 0.83f, crosshairPaint)
        canvas.drawLine(cx - radius * 0.93f, cy, cx - radius * 0.83f, cy, crosshairPaint)
        canvas.drawLine(cx + radius * 0.93f, cy, cx + radius * 0.83f, cy, crosshairPaint)

        // 3. Crisp Geometric Laser Hands
        val hourAngle = (((h % 12) + m / 60f) / 12f) * 360f
        val minAngle = ((m + s / 60f) / 60f) * 360f
        val secAngle = ((s + ms / 1000f) / 60f) * 360f

        drawLaserHand(canvas, hourAngle, radius * 0.45f)
        drawLaserHand(canvas, minAngle, radius * 0.70f)

        // Sweeping Orbital Satellite Particle (Seconds)
        val secRad = Math.toRadians((secAngle - 90.0))
        val sX = cx + (cos(secRad) * radius * 0.88f).toFloat()
        val sY = cy + (sin(secRad) * radius * 0.88f).toFloat()
        canvas.drawCircle(sX, sY, radius * 0.035f, secDotPaint)

        canvas.drawCircle(cx, cy, radius * 0.04f, handPaint)
    }

    private fun drawLaserHand(canvas: Canvas, deg: Float, len: Float) {
        canvas.save()
        canvas.rotate(deg, cx, cy)
        canvas.drawLine(cx, cy, cx, cy - len, handPaint)
        canvas.restore()
    }
}

// =========================================================================
// DIAL 5: RETRO GEOMETRIC VECTOR (Pure AMOLED High-Efficiency Geometry)
// =========================================================================
class RetroGeometricRenderer : WatchFaceRenderer {
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private val bgPaint = Paint().apply { color = 0xFF000000.toInt() }
    private val polygonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF263238.toInt()
        strokeWidth = 2f
    }
    private val hourTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 4f
        strokeCap = Paint.Cap.SQUARE
    }
    private val polyHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
    }
    private val secAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFF6D00.toInt() // Radar Safety Orange
        strokeCap = Paint.Cap.ROUND
    }

    private val handPath = Path()
    private val polyPath = Path()
    private val calendar = Calendar.getInstance()

    override fun getName(): String = "Retro Vector"

    override fun onSizeChanged(width: Int, height: Int, cx: Float, cy: Float, radius: Float) {
        this.cx = cx
        this.cy = cy
        this.radius = radius
        secAccentPaint.strokeWidth = radius * 0.015f
    }

    override fun draw(canvas: Canvas, timeMillis: Long, batteryPercent: Float) {
        calendar.timeInMillis = timeMillis
        val h = calendar.get(Calendar.HOUR)
        val m = calendar.get(Calendar.MINUTE)
        val s = calendar.get(Calendar.SECOND)
        val ms = calendar.get(Calendar.MILLISECOND)

        // 1. OLED Pure Black
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // 2. Concentric Hexagonal Radar Geometry
        val pulse = (sin(timeMillis * 0.002) * (radius * 0.03f)).toFloat()
        drawPolygon(canvas, cx, cy, radius * 0.85f + pulse, 6, polygonPaint)
        drawPolygon(canvas, cx, cy, radius * 0.55f, 6, polygonPaint)
        drawPolygon(canvas, cx, cy, radius * 0.28f, 6, polygonPaint)

        // 3. 12 Vector Triangle Ticks
        for (i in 0 until 12) {
            val rad = Math.toRadians((i * 30.0))
            val sinVal = sin(rad).toFloat()
            val cosVal = cos(rad).toFloat()
            val r1 = radius * 0.88f
            val r2 = radius * 0.79f
            canvas.drawLine(cx + sinVal * r1, cy - cosVal * r1, cx + sinVal * r2, cy - cosVal * r2, hourTickPaint)
        }

        // 4. Hollow Geometric Vector Hands
        val hourAngle = (((h % 12) + m / 60f) / 12f) * 360f
        val minAngle = ((m + s / 60f) / 60f) * 360f
        val secAngle = ((s + ms / 1000f) / 60f) * 360f

        drawHollowHand(canvas, hourAngle, radius * 0.48f, radius * 0.065f)
        drawHollowHand(canvas, minAngle, radius * 0.74f, radius * 0.050f)

        // Thin Vibrant Orange Second Vector
        canvas.save()
        canvas.rotate(secAngle, cx, cy)
        canvas.drawLine(cx, cy + (radius * 0.15f), cx, cy - (radius * 0.85f), secAccentPaint)
        canvas.drawCircle(cx, cy - (radius * 0.65f), radius * 0.03f, secAccentPaint)
        canvas.restore()

        canvas.drawCircle(cx, cy, radius * 0.035f, bgPaint)
        canvas.drawCircle(cx, cy, radius * 0.035f, hourTickPaint)
    }

    private fun drawPolygon(canvas: Canvas, x: Float, y: Float, r: Float, sides: Int, paint: Paint) {
        polyPath.reset()
        for (i in 0 until sides) {
            val angle = Math.toRadians((i * (360.0 / sides)))
            val px = x + (cos(angle) * r).toFloat()
            val py = y + (sin(angle) * r).toFloat()
            if (i == 0) polyPath.moveTo(px, py) else polyPath.lineTo(px, py)
        }
        polyPath.close()
        canvas.drawPath(polyPath, paint)
    }

    private fun drawHollowHand(canvas: Canvas, deg: Float, len: Float, halfWidth: Float) {
        canvas.save()
        canvas.rotate(deg, cx, cy)
        handPath.reset()
        handPath.moveTo(cx, cy + (len * 0.15f))
        handPath.lineTo(cx - halfWidth, cy)
        handPath.lineTo(cx, cy - len)
        handPath.lineTo(cx + halfWidth, cy)
        handPath.close()
        canvas.drawPath(handPath, polyHandPaint)
        canvas.restore()
    }
}