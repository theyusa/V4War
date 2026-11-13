package io.nekohasekai.sagernet.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper

class TrafficBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var downloadBytes: Long = 0L
    private var uploadBytes: Long = 0L
    private var referenceMax: Long = 0L

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ulPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    fun setTraffic(download: Long, upload: Long, maxTotal: Long = download + upload) {
        downloadBytes = download.coerceAtLeast(0L)
        uploadBytes = upload.coerceAtLeast(0L)
        referenceMax = maxTotal.coerceAtLeast(downloadBytes + uploadBytes).coerceAtLeast(1L)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = h / 2f
        val accent = ExpressiveSettingsHelper.resolveAccentColor(context)
        val textSecondary = context.getColorAttr(android.R.attr.textColorSecondary)
        val ulColor = ColorUtils.blendARGB(accent, context.getColor(R.color.material_amber_900), 0.55f)

        trackPaint.color = ColorUtils.setAlphaComponent(textSecondary, 32)
        dlPaint.color = accent
        ulPaint.color = ulColor

        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        val total = downloadBytes + uploadBytes
        if (total <= 0L) return

        val fillW = (w * (total.toFloat() / referenceMax.toFloat())).coerceIn(h, w)
        val dlW = fillW * (downloadBytes.toFloat() / total.toFloat())

        if (dlW > 0f) {
            rect.set(0f, 0f, dlW.coerceAtLeast(h.coerceAtMost(fillW)), h)
            canvas.drawRoundRect(rect, radius, radius, dlPaint)
        }
        if (uploadBytes > 0L && fillW > dlW) {
            val ulStart = (dlW - radius * 0.4f).coerceAtLeast(0f)
            rect.set(ulStart, 0f, fillW, h)
            canvas.drawRoundRect(rect, radius, radius, ulPaint)
        }
    }
}
