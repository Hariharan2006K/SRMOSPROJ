package com.voltwise.powermonitor.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.voltwise.powermonitor.R
import com.voltwise.powermonitor.data.entity.BatteryLogEntity
import com.voltwise.powermonitor.util.FormatUtils
import kotlin.math.max
import kotlin.math.min

class BatteryTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val logs = mutableListOf<BatteryLogEntity>()
    private var selectedIndex: Int = -1

    var onPointSelected: ((BatteryLogEntity?) -> Unit)? = null

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.displayMetrics.scaledDensity * 10f
        textAlign = Paint.Align.RIGHT
    }

    private val levelLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val levelFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tempLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val scrubberLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }

    private val tooltipBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.surface_dark)
    }

    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.displayMetrics.scaledDensity * 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val linePath = Path()
    private val fillPath = Path()
    private val tempPath = Path()

    init {
        gridPaint.color = ContextCompat.getColor(context, R.color.divider_dark)
        axisTextPaint.color = ContextCompat.getColor(context, R.color.text_tertiary_dark)
        levelLinePaint.color = ContextCompat.getColor(context, R.color.battery_good)
        tempLinePaint.color = ContextCompat.getColor(context, R.color.temp_warm)
    }

    fun submitLogs(newLogs: List<BatteryLogEntity>) {
        logs.clear()
        logs.addAll(newLogs)
        selectedIndex = -1
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (logs.isEmpty()) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val paddingLeft = 70f
                val paddingRight = 40f
                val chartWidth = width - paddingLeft - paddingRight

                val touchX = event.x.coerceIn(paddingLeft, width - paddingRight)
                val fraction = (touchX - paddingLeft) / chartWidth
                val index = (fraction * (logs.size - 1)).toInt().coerceIn(0, logs.size - 1)

                if (index != selectedIndex) {
                    selectedIndex = index
                    onPointSelected?.invoke(logs[index])
                    invalidate()
                }
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selectedIndex = -1
                onPointSelected?.invoke(null)
                invalidate()
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft = 70f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 60f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        if (chartWidth <= 0 || chartHeight <= 0) return

        // 1. Draw horizontal grid lines & percentage labels (0%, 25%, 50%, 75%, 100%)
        val levels = listOf(100, 75, 50, 25, 0)
        for (lvl in levels) {
            val y = paddingTop + (1f - (lvl / 100f)) * chartHeight
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)
            canvas.drawText("${lvl}%", paddingLeft - 12f, y + 8f, axisTextPaint)
        }

        if (logs.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.text_secondary_dark)
                textSize = resources.displayMetrics.scaledDensity * 12f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Waiting for telemetry logs…", width / 2f, height / 2f, emptyPaint)
            return
        }

        // 2. Prepare Path points for Battery % and Temperature
        linePath.reset()
        fillPath.reset()
        tempPath.reset()

        val count = logs.size
        var firstX = 0f
        var lastX = 0f

        for (i in 0 until count) {
            val fraction = if (count > 1) i.toFloat() / (count - 1) else 0.5f
            val x = paddingLeft + fraction * chartWidth
            val yLevel = paddingTop + (1f - (logs[i].level / 100f)) * chartHeight

            // Temperature scaled between 15°C and 55°C
            val tempC = logs[i].temperatureCelsius.coerceIn(15f, 55f)
            val tempFraction = (tempC - 15f) / (55f - 15f)
            val yTemp = paddingTop + (1f - tempFraction) * chartHeight

            if (i == 0) {
                firstX = x
                linePath.moveTo(x, yLevel)
                fillPath.moveTo(x, paddingTop + chartHeight)
                fillPath.lineTo(x, yLevel)
                tempPath.moveTo(x, yTemp)
            } else {
                linePath.lineTo(x, yLevel)
                fillPath.lineTo(x, yLevel)
                tempPath.lineTo(x, yTemp)
            }
            lastX = x
        }

        fillPath.lineTo(lastX, paddingTop + chartHeight)
        fillPath.close()

        // 3. Draw gradient fill for battery level
        val gradient = LinearGradient(
            0f, paddingTop, 0f, paddingTop + chartHeight,
            ContextCompat.getColor(context, R.color.battery_good),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        levelFillPaint.shader = gradient
        levelFillPaint.alpha = 50
        canvas.drawPath(fillPath, levelFillPaint)

        // 4. Draw Battery level line & Temperature line
        canvas.drawPath(linePath, levelLinePaint)
        canvas.drawPath(tempPath, tempLinePaint)

        // 5. Draw Time axis stamps
        val startTimeStr = FormatUtils.formatTime(logs.first().timestamp)
        val endTimeStr = FormatUtils.formatTime(logs.last().timestamp)
        axisTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(startTimeStr, paddingLeft, height - 15f, axisTextPaint)
        axisTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(endTimeStr, width - paddingRight, height - 15f, axisTextPaint)

        // 6. Draw Scrubber Tooltip if active
        if (selectedIndex in logs.indices) {
            val fraction = if (count > 1) selectedIndex.toFloat() / (count - 1) else 0.5f
            val scrubX = paddingLeft + fraction * chartWidth
            val selected = logs[selectedIndex]

            // Vertical line
            canvas.drawLine(scrubX, paddingTop, scrubX, paddingTop + chartHeight, scrubberLinePaint)

            // Point dots
            val yLevel = paddingTop + (1f - (selected.level / 100f)) * chartHeight
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(scrubX, yLevel, 7f, dotPaint)

            // Tooltip badge
            val tooltipText = "${selected.level}% • ${String.format("%.1f", selected.temperatureCelsius)}°C • ${FormatUtils.formatTime(selected.timestamp)}"
            val textWidth = tooltipTextPaint.measureText(tooltipText)
            val boxWidth = textWidth + 36f
            val boxHeight = 44f
            val boxX = (scrubX - boxWidth / 2f).coerceIn(paddingLeft, width - paddingRight - boxWidth)
            val boxY = paddingTop - 30f

            val boxRect = RectF(boxX, boxY, boxX + boxWidth, boxY + boxHeight)
            canvas.drawRoundRect(boxRect, 10f, 10f, tooltipBoxPaint)
            canvas.drawText(tooltipText, boxRect.centerX(), boxRect.centerY() + 10f, tooltipTextPaint)
        }
    }
}
