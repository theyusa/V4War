package io.nekohasekai.sagernet.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.utils.Theme
import kotlin.math.min

/**
 * Rounded two-segment ring indicator comparing uploaded vs downloaded traffic totals.
 */
class TrafficRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        /** Returns `(trackColor, downloadColor, uploadColor)` derived strictly from active theme roles. */
        fun resolveSegmentColors(context: Context): Triple<Int, Int, Int> {
            val textSecondary = context.getColorAttr(android.R.attr.textColorSecondary)
            val accent = ExpressiveSettingsHelper.resolveAccentColor(context)
            val surfaceContrast = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSecondaryContainer,
                Theme.resolveCardBackground(context),
            )
            val uploadColor = if (Theme.isMonochromeBlack(context)) {
                ColorUtils.setAlphaComponent(accent, 145)
            } else {
                val tertiary = MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorTertiary,
                    surfaceContrast,
                )
                val blended = ColorUtils.blendARGB(accent, tertiary, 0.42f)
                if (ColorUtils.calculateContrast(blended, accent) < 1.25) {
                    ColorUtils.blendARGB(accent, surfaceContrast, 0.38f)
                } else {
                    blended
                }
            }
            val trackColor = ColorUtils.setAlphaComponent(textSecondary, 44)
            return Triple(trackColor, accent, uploadColor)
        }
    }

    private var downloadBytes: Long = 0L
    private var uploadBytes: Long = 0L

    private val strokePx = dp2px(12).toFloat()
    private val rect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    }
    private val downloadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
    }
    private val uploadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
    }

    fun setTraffic(download: Long, upload: Long) {
        val nextDl = download.coerceAtLeast(0L)
        val nextUl = upload.coerceAtLeast(0L)
        if (nextDl != downloadBytes || nextUl != uploadBytes) {
            downloadBytes = nextDl
            uploadBytes = nextUl
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (side <= strokePx) return

        val cx = paddingLeft + (width - paddingLeft - paddingRight) * 0.5f
        val cy = paddingTop + (height - paddingTop - paddingBottom) * 0.5f
        val radius = (side - strokePx) * 0.5f
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val (trackColor, downloadColor, uploadColor) = resolveSegmentColors(context)
        trackPaint.color = trackColor
        downloadPaint.color = downloadColor
        uploadPaint.color = uploadColor

        canvas.drawOval(rect, trackPaint)

        val total = downloadBytes + uploadBytes
        if (total <= 0L) return

        if (downloadBytes > 0L && uploadBytes > 0L) {
            val gapDeg = 36f
            val availableDeg = 360f - gapDeg * 2f
            val dlSweep = (availableDeg * (downloadBytes.toFloat() / total.toFloat())).coerceIn(14f, availableDeg - 14f)
            val ulSweep = availableDeg - dlSweep
            val ulStart = -90f + gapDeg * 0.5f
            val dlStart = ulStart + ulSweep + gapDeg
            canvas.drawArc(rect, ulStart, ulSweep, false, uploadPaint)
            canvas.drawArc(rect, dlStart, dlSweep, false, downloadPaint)
        } else if (downloadBytes > 0L) {
            canvas.drawArc(rect, -90f, 360f, false, downloadPaint)
        } else {
            canvas.drawArc(rect, -90f, 360f, false, uploadPaint)
        }
    }
}
