package com.voltwise.powermonitor.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.voltwise.powermonitor.R
import com.voltwise.powermonitor.battery.BatteryStatus

class BatteryGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var level: Int = 75
    private var animatedLevel: Float = 75f
    private var status: BatteryStatus = BatteryStatus.DISCHARGING
    private var powerMw: Float = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val arcRect = RectF()
    private var strokeWidthPx = 32f

    private var animator: ValueAnimator? = null

    init {
        strokeWidthPx = resources.displayMetrics.density * 14f
        trackPaint.strokeWidth = strokeWidthPx
        progressPaint.strokeWidth = strokeWidthPx
    }

    fun setBatteryData(newLevel: Int, newStatus: BatteryStatus, newPowerMw: Float) {
        val oldLevel = this.level
        this.level = newLevel.coerceIn(0, 100)
        this.status = newStatus
        this.powerMw = newPowerMw

        animator?.cancel()
        animator = ValueAnimator.ofFloat(animatedLevel, level.toFloat()).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animatedLevel = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = strokeWidthPx * 1.5f
        arcRect.set(padding, padding, w - padding, h - padding)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0 || height <= 0) return

        val centerX = width / 2f
        val centerY = height / 2f

        // Track background
        trackPaint.color = ContextCompat.getColor(context, R.color.surface_variant_dark)
        canvas.drawArc(arcRect, 135f, 270f, false, trackPaint)

        // Progress arc
        val sweepAngle = (animatedLevel / 100f) * 270f
        progressPaint.color = resolveBatteryColor(animatedLevel.toInt(), status)
        canvas.drawArc(arcRect, 135f, sweepAngle, false, progressPaint)

        // Percentage Text
        textPaint.textSize = height * 0.18f
        textPaint.color = ContextCompat.getColor(context, R.color.text_primary_dark)
        canvas.drawText("${animatedLevel.toInt()}%", centerX, centerY + (textPaint.textSize * 0.25f), textPaint)

        // Status text
        subTextPaint.textSize = height * 0.065f
        subTextPaint.color = resolveBatteryColor(animatedLevel.toInt(), status)
        val statusText = when (status) {
            BatteryStatus.CHARGING -> "⚡ CHARGING"
            BatteryStatus.DISCHARGING -> "DISCHARGING"
            BatteryStatus.FULL -> "FULL"
            else -> status.name
        }
        canvas.drawText(statusText, centerX, centerY - (textPaint.textSize * 0.55f), subTextPaint)

        // Power Draw Text
        subTextPaint.textSize = height * 0.06f
        subTextPaint.color = ContextCompat.getColor(context, R.color.text_secondary_dark)
        val powerText = "${String.format("%.0f", powerMw)} mW"
        canvas.drawText(powerText, centerX, centerY + (textPaint.textSize * 0.75f), subTextPaint)
    }

    private fun resolveBatteryColor(lvl: Int, stat: BatteryStatus): Int {
        if (stat == BatteryStatus.CHARGING) {
            return ContextCompat.getColor(context, R.color.battery_charging)
        }
        return when {
            lvl > 50 -> ContextCompat.getColor(context, R.color.battery_good)
            lvl > 20 -> ContextCompat.getColor(context, R.color.battery_moderate)
            lvl > 10 -> ContextCompat.getColor(context, R.color.battery_warning)
            else -> ContextCompat.getColor(context, R.color.battery_critical)
        }
    }
}
