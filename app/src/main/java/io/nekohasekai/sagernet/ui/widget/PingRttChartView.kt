package io.nekohasekai.sagernet.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.utils.toolbox.PingSample
import kotlin.math.max

class PingRttChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var samples: List<PingSample> = emptyList()
    private val linePath = Path()
    private val areaPath = Path()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val failPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val avgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.displayMetrics.scaledDensity * 10.5f
    }

    fun setSamples(newSamples: List<PingSample>) {
        samples = newSamples.takeLast(60)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val accent = ExpressiveSettingsHelper.resolveAccentColor(context)
        val textSecondary = context.getColorAttr(android.R.attr.textColorSecondary)
        val failColor = context.getColor(R.color.material_red_500)

        linePaint.color = accent
        linePaint.strokeWidth = 2.4f * density
        dotPaint.color = accent
        failPaint.color = failColor
        gridPaint.color = ColorUtils.setAlphaComponent(textSecondary, 36)
        gridPaint.strokeWidth = 1f * density
        avgPaint.color = ColorUtils.setAlphaComponent(accent, 140)
        avgPaint.strokeWidth = 1.2f * density
        avgPaint.pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
        labelPaint.color = ColorUtils.setAlphaComponent(textSecondary, 185)

        val left = 8f * density
        val top = 14f * density
        val right = width - 8f * density
        val bottom = height - 12f * density
        val chartW = (right - left).coerceAtLeast(1f)
        val chartH = (bottom - top).coerceAtLeast(1f)

        // Subtle horizontal guidelines
        for (i in 0..2) {
            val y = top + (chartH * i / 2f)
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        if (samples.isEmpty()) {
            canvas.drawText("0 ms", left + 4f * density, bottom - 4f * density, labelPaint)
            return
        }

        val okValues = samples.filter { it.success }.map { it.rttMs }
        val maxY = max(10.0, (okValues.maxOrNull() ?: 50.0) * 1.18)
        val avg = if (okValues.isNotEmpty()) okValues.average() else 0.0

        canvas.drawText(String.format("%.0f ms", maxY), left + 2f * density, top + 10f * density, labelPaint)
        if (avg > 0.0) {
            val avgY = (bottom - (avg / maxY) * chartH).toFloat().coerceIn(top, bottom)
            canvas.drawLine(left, avgY, right, avgY, avgPaint)
        }

        val n = samples.size
        val stepX = if (n <= 1) chartW else chartW / (n - 1).toFloat()

        linePath.reset()
        areaPath.reset()
        var started = false
        var lastOkX = left

        samples.forEachIndexed { idx, s ->
            val x = if (n == 1) left + chartW / 2f else left + idx * stepX
            if (s.success) {
                val y = (bottom - (s.rttMs / maxY) * chartH).toFloat().coerceIn(top, bottom)
                if (!started) {
                    linePath.moveTo(x, y)
                    areaPath.moveTo(x, bottom)
                    areaPath.lineTo(x, y)
                    started = true
                } else {
                    linePath.lineTo(x, y)
                    areaPath.lineTo(x, y)
                }
                lastOkX = x
                canvas.drawCircle(x, y, 2.8f * density, dotPaint)
            } else {
                canvas.drawCircle(x, bottom - 2f * density, 3f * density, failPaint)
            }
        }

        if (started) {
            areaPath.lineTo(lastOkX, bottom)
            areaPath.close()
            areaPaint.shader = LinearGradient(
                0f,
                top,
                0f,
                bottom,
                ColorUtils.setAlphaComponent(accent, 64),
                ColorUtils.setAlphaComponent(accent, 4),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(areaPath, areaPaint)
            canvas.drawPath(linePath, linePaint)
        }
    }
}
