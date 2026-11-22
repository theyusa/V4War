package moe.matsuri.nb4a.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.utils.PaletteKeyColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure Android View recreation of aShellYou's Material 3 Expressive PaletteWheel:
 * - Outlined tonal container card (16dp radius)
 * - 3-segment tonal wheel (top = primary, bottom-left = secondary, bottom-right = tertiary)
 * - Unselected: circular clip; Selected: rotated rounded square (diamond) + 12-sided scalloped
 *   cookie badge in primaryContainer with onPrimaryContainer checkmark.
 */
class PaletteWheelView(context: Context) : View(context) {

    var keyColors: PaletteKeyColors = PaletteKeyColors(0xFF30A370.toInt(), 0xFF4D6357.toInt(), 0xFF3D6373.toInt())
        set(value) {
            field = value
            invalidate()
        }

    var isCheckedWheel: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isDynamicWallpaperBadge: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp2px(1).toFloat()
    }
    private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val cardRect = RectF()
    private val wheelRect = RectF()
    private val clipPath = Path()
    private val scallopPath = Path()
    private val checkPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val surfaceCol = context.getColorAttr(com.google.android.material.R.attr.colorSurfaceContainerLowest)
            .takeIf { it != 0 }
            ?: context.getColorAttr(com.google.android.material.R.attr.colorSurface)
        val outlineVar = context.getColorAttr(com.google.android.material.R.attr.colorOutlineVariant)
            .takeIf { it != 0 }
            ?: Color.GRAY
        val primaryContainer = context.getColorAttr(com.google.android.material.R.attr.colorPrimaryContainer)
            .takeIf { it != 0 }
            ?: keyColors.primary
        val onPrimaryContainer = context.getColorAttr(com.google.android.material.R.attr.colorOnPrimaryContainer)
            .takeIf { it != 0 }
            ?: Color.WHITE
        val primary = context.getColorAttr(androidx.appcompat.R.attr.colorPrimary)
            .takeIf { it != 0 }
            ?: keyColors.primary

        val inset = dp2px(1).toFloat()
        cardRect.set(inset, inset, w - inset, h - inset)
        val cardRadius = dp2px(14).toFloat()

        cardPaint.color = surfaceCol
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardPaint)

        borderPaint.color = if (isCheckedWheel) primary else outlineVar
        borderPaint.strokeWidth = dp2px(if (isCheckedWheel) 2 else 1).toFloat()
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, borderPaint)

        // Inner 3-segment wheel
        val pad = min(w, h) * 0.17f
        val wheelSize = min(w, h) - pad * 2f
        val cx = w * 0.5f
        val cy = h * 0.5f
        val half = wheelSize * 0.5f
        wheelRect.set(cx - half, cy - half, cx + half, cy + half)

        canvas.save()
        clipPath.reset()
        if (isCheckedWheel) {
            // aShellYou rotated rounded square (diamond)
            val sqHalf = half * 0.84f
            val r = sqHalf * 0.42f
            canvas.rotate(-45f, cx, cy)
            clipPath.addRoundRect(cx - sqHalf, cy - sqHalf, cx + sqHalf, cy + sqHalf, r, r, Path.Direction.CW)
            canvas.clipPath(clipPath)
            canvas.rotate(45f, cx, cy)
        } else {
            clipPath.addCircle(cx, cy, half, Path.Direction.CW)
            canvas.clipPath(clipPath)
        }

        // Top half: Primary
        segPaint.color = keyColors.primary
        canvas.drawRect(cx - half * 1.2f, cy - half * 1.2f, cx + half * 1.2f, cy, segPaint)

        // Bottom-left: Secondary
        segPaint.color = keyColors.secondary
        canvas.drawRect(cx - half * 1.2f, cy, cx, cy + half * 1.2f, segPaint)

        // Bottom-right: Tertiary
        segPaint.color = keyColors.tertiary
        canvas.drawRect(cx, cy, cx + half * 1.2f, cy + half * 1.2f, segPaint)
        canvas.restore()

        // Selected 12-sided scalloped cookie badge + check icon
        if (isCheckedWheel) {
            val badgeRadius = wheelSize * 0.27f
            buildScallopPath(scallopPath, cx, cy, badgeRadius, badgeRadius * 0.13f, lobes = 12)
            badgePaint.color = primaryContainer
            canvas.drawPath(scallopPath, badgePaint)

            checkPaint.color = onPrimaryContainer
            checkPaint.strokeWidth = dp2px(2).toFloat()
            val cr = badgeRadius * 0.46f
            checkPath.reset()
            checkPath.moveTo(cx - cr * 0.95f, cy + cr * 0.05f)
            checkPath.lineTo(cx - cr * 0.22f, cy + cr * 0.75f)
            checkPath.lineTo(cx + cr * 0.98f, cy - cr * 0.65f)
            canvas.drawPath(checkPath, checkPaint)
        }
    }

    private fun buildScallopPath(
        out: Path,
        cx: Float,
        cy: Float,
        baseRadius: Float,
        amplitude: Float,
        lobes: Int,
    ) {
        out.reset()
        val steps = lobes * 10
        for (i in 0..steps) {
            val theta = (2.0 * PI * i) / steps
            val r = baseRadius + amplitude * cos(lobes * theta).toFloat()
            val x = cx + r * cos(theta).toFloat()
            val y = cy + r * sin(theta).toFloat()
            if (i == 0) out.moveTo(x, y) else out.lineTo(x, y)
        }
        out.close()
    }
}
