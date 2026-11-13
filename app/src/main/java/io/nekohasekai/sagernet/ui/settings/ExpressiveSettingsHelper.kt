package io.nekohasekai.sagernet.ui.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.tabs.TabLayout
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.ExpressivePreferenceDecoration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Material 3 Expressive geometric container shapes for icon badges and appearance studio cards.
 */
enum class ExpressiveShape {
    CIRCLE,
    SCALLOP_12,
    SQUIRCLE,
    CLOVER_8,
}

/**
 * Vector-crisp M3 Expressive geometric badge background supporting [ExpressiveShape] variants.
 */
class ExpressiveShapeDrawable(
    var shapeType: ExpressiveShape = ExpressiveShape.CIRCLE,
    color: Int = Color.TRANSPARENT,
    private val fixedSizePx: Int = 0,
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }
    private val path = Path()
    private val rectF = RectF()
    private var lastWidth = -1
    private var lastHeight = -1
    private var lastShape: ExpressiveShape? = null

    fun setFillColor(color: Int) {
        if (paint.color != color) {
            paint.color = color
            invalidateSelf()
        }
    }

    override fun getIntrinsicWidth(): Int = if (fixedSizePx > 0) fixedSizePx else super.getIntrinsicWidth()

    override fun getIntrinsicHeight(): Int = if (fixedSizePx > 0) fixedSizePx else super.getIntrinsicHeight()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rebuildPath(bounds.width(), bounds.height())
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        if (b.width() != lastWidth || b.height() != lastHeight || shapeType != lastShape) {
            rebuildPath(b.width(), b.height())
        }
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun rebuildPath(w: Int, h: Int) {
        lastWidth = w
        lastHeight = h
        lastShape = shapeType
        path.reset()
        if (w <= 0 || h <= 0) return

        val cx = w * 0.5f
        val cy = h * 0.5f
        val radius = min(w, h) * 0.5f

        when (shapeType) {
            ExpressiveShape.CIRCLE -> {
                path.addCircle(cx, cy, radius, Path.Direction.CW)
            }

            ExpressiveShape.SQUIRCLE -> {
                val r = radius * 0.56f
                rectF.set(cx - radius * 0.94f, cy - radius * 0.94f, cx + radius * 0.94f, cy + radius * 0.94f)
                path.addRoundRect(rectF, r, r, Path.Direction.CW)
            }

            ExpressiveShape.SCALLOP_12 -> {
                buildWavePolygonPath(cx, cy, radius * 0.90f, radius * 0.11f, lobes = 12)
            }

            ExpressiveShape.CLOVER_8 -> {
                buildWavePolygonPath(cx, cy, radius * 0.88f, radius * 0.13f, lobes = 8)
            }
        }
    }

    private fun buildWavePolygonPath(cx: Float, cy: Float, baseR: Float, amp: Float, lobes: Int) {
        val steps = 120
        for (i in 0..steps) {
            val theta = (2.0 * PI * i) / steps
            val r = baseR + amp * cos(lobes * theta).toFloat()
            val x = cx + r * cos(theta).toFloat()
            val y = cy + r * sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Material 3 Expressive design helper.
 * Generates 42dp (settings) and 36dp (drawer) tonal masked icon badges across M3 Expressive shapes,
 * fully harmonized with V4War's 3-tone seed theme system.
 */
object ExpressiveSettingsHelper {

    /**
     * Unwraps any existing LayerDrawable layers to extract the original core vector drawable.
     * Prevents nested layer wrapping, accumulated insets, and disappearing icons.
     */
    fun unwrapOriginalIcon(drawable: Drawable): Drawable {
        var cur = drawable
        while (cur is LayerDrawable && cur.numberOfLayers >= 2) {
            cur = cur.getDrawable(1)
        }
        return cur
    }

    fun createMaskedIcon(
        context: Context,
        originalDrawable: Drawable,
        containerColor: Int,
        iconColor: Int,
        sizeDp: Int = 42,
        iconSizeDp: Int = 22,
        shape: ExpressiveShape = ExpressiveShape.SQUIRCLE,
    ): Drawable {
        val size = dp2px(sizeDp)
        val iconSize = dp2px(iconSizeDp)
        val inset = (size - iconSize) / 2

        // Fast-path: update existing LayerDrawable badge in place without re-wrapping
        if (originalDrawable is LayerDrawable && originalDrawable.numberOfLayers == 2) {
            val bg = originalDrawable.getDrawable(0)
            val icon = originalDrawable.getDrawable(1)
            if (icon != null) {
                when (bg) {
                    is ExpressiveShapeDrawable -> {
                        bg.shapeType = shape
                        bg.setFillColor(containerColor)
                    }
                    is GradientDrawable -> {
                        bg.setColor(containerColor)
                    }
                }
                val iconMut = icon.mutate()
                iconMut.clearColorFilter()
                DrawableCompat.setTint(iconMut, iconColor)
                return originalDrawable
            }
        }

        val rawIcon = unwrapOriginalIcon(originalDrawable)
        val background = ExpressiveShapeDrawable(shape, containerColor, size)

        val iconCopy = (rawIcon.constantState?.newDrawable(context.resources) ?: rawIcon).mutate()
        iconCopy.clearColorFilter()
        DrawableCompat.setTint(iconCopy, iconColor)

        return LayerDrawable(arrayOf(background, iconCopy)).apply {
            setLayerInset(1, inset, inset, inset, inset)
            setBounds(0, 0, size, size)
        }
    }

    internal fun resolveBadgeColors(context: Context, isDark: Boolean, isAmoled: Boolean): Pair<Int, Int> {
        val tintIcons = DataStore.tintPreferenceIcons
        if (isAmoled && Theme.isMonochromeBlack(context)) {
            val containerColor = Color.parseColor("#1EFFFFFF")
            val iconColor = if (tintIcons) Color.WHITE else Color.parseColor("#C4C7C5")
            return containerColor to iconColor
        }

        val themeAccent = Theme.getSeedPrimaryColor(context).takeIf { it != 0 && it != Color.WHITE }
            ?: context.getColorAttr(R.attr.colorAccent).takeIf { it != 0 && it != Color.WHITE }
            ?: ContextCompat.getColor(context, R.color.material_blue_accent_200)

        val containerColor = when {
            isAmoled -> ColorUtils.setAlphaComponent(themeAccent, (255 * 0.20f).toInt())
            isDark -> ColorUtils.setAlphaComponent(themeAccent, (255 * 0.16f).toInt())
            else -> ColorUtils.blendARGB(themeAccent, Color.WHITE, 0.88f)
        }

        val iconColor = if (tintIcons) {
            if (isDark) {
                ColorUtils.blendARGB(themeAccent, Color.WHITE, 0.15f)
            } else {
                themeAccent
            }
        } else {
            if (isDark) Color.parseColor("#C4C7C5") else Color.parseColor("#444746")
        }

        return containerColor to iconColor
    }

    fun applyDrawerMaskedIcons(navigation: NavigationView, context: Context) {
        val isDark = Theme.usingNightMode(context)
        val isAmoled = Theme.isBlackTheme(context)
        val (containerColor, iconColor) = resolveBadgeColors(context, isDark, isAmoled)

        navigation.itemIconTintList = null

        if (navigation.headerCount > 0) {
            val headerView = navigation.getHeaderView(0)
            val headerCard = headerView?.findViewById<MaterialCardView>(R.id.drawerHeaderCard)
            headerCard?.apply {
                setCardBackgroundColor(Theme.resolveCardBackground(context))
                strokeColor = Theme.resolveCardStrokeColor(context, selected = false)
                strokeWidth = Theme.resolveCardStrokeWidthPx(context, selected = false).toInt().coerceAtLeast(1)
            }
        }

        val menu = navigation.menu
        fun processItem(item: MenuItem) {
            val d = item.icon ?: return
            item.icon = createMaskedIcon(
                context,
                d,
                containerColor,
                iconColor,
                sizeDp = 36,
                iconSizeDp = 20,
                shape = ExpressiveShape.SQUIRCLE,
            )
        }

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.hasSubMenu()) {
                val subMenu = item.subMenu ?: continue
                for (j in 0 until subMenu.size()) {
                    processItem(subMenu.getItem(j))
                }
            } else {
                processItem(item)
            }
        }
    }

    /**
     * Compact `36dp` M3 Expressive floating header bar (`[navButton] [titlePill]`) shared across Settings and Tools.
     */
    class FloatingPillHeader(
        val root: LinearLayout,
        private val navButton: FrameLayout,
        private val navIcon: ImageView,
        private val titleView: TextView,
    ) {
        fun update(
            context: Context,
            title: CharSequence,
            iconRes: Int,
            contentDescription: CharSequence,
            onNavClick: () -> Unit,
        ) {
            val textPrimary = context.getColorAttr(android.R.attr.textColorPrimary)
            root.visibility = View.VISIBLE
            titleView.text = title
            navButton.contentDescription = contentDescription
            navButton.setOnClickListener { onNavClick() }
            val drawable = ResourcesCompat.getDrawable(context.resources, iconRes, null)?.mutate()
            if (drawable != null) {
                DrawableCompat.setTint(drawable, textPrimary)
                navIcon.setImageDrawable(drawable)
            }
        }
    }

    fun attachFloatingPillHeader(appbar: AppBarLayout?, context: Context): FloatingPillHeader? {
        appbar ?: return null
        val isNight = Theme.usingNightMode(context)
        val pillBg = Theme.resolveCardBackground(context)
        val pillStroke = Theme.resolveCardStrokeColor(context, selected = false)
        val strokePx = Theme.resolveCardStrokeWidthPx(context, selected = false).toInt().coerceAtLeast(1)
        val textPrimary = context.getColorAttr(android.R.attr.textColorPrimary)
        val rippleColor = ColorUtils.setAlphaComponent(textPrimary, if (isNight) 38 else 28)

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(dp2px(16), dp2px(6), dp2px(16), dp2px(6))
            visibility = View.VISIBLE
        }

        val pillHeight = dp2px(36)
        val navButton = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(pillHeight, pillHeight).apply {
                marginEnd = dp2px(8)
            }
            isClickable = true
            isFocusable = true
            val bgShape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pillBg)
                setStroke(strokePx, pillStroke)
            }
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            background = RippleDrawable(ColorStateList.valueOf(rippleColor), bgShape, mask)
        }
        val navIcon = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp2px(18), dp2px(18), Gravity.CENTER)
        }
        navButton.addView(navIcon)
        headerRow.addView(navButton)

        val titlePill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = pillHeight
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                pillHeight,
            )
            setPadding(dp2px(14), 0, dp2px(14), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(18).toFloat()
                setColor(pillBg)
                setStroke(strokePx, pillStroke)
            }
        }
        val titleText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0f
            includeFontPadding = false
            setTextColor(textPrimary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        titlePill.addView(titleText)
        headerRow.addView(titlePill)

        appbar.addView(headerRow)
        return FloatingPillHeader(headerRow, navButton, navIcon, titleText)
    }

    fun styleExpressiveCard(card: MaterialCardView, cornerRadiusDp: Float = 20f) {
        val ctx = card.context
        card.radius = dp2px(cornerRadiusDp.toInt()).toFloat()
        card.cardElevation = 0f
        card.setCardBackgroundColor(Theme.resolveCardBackground(ctx))
        card.strokeColor = Theme.resolveCardStrokeColor(ctx, selected = false)
        card.strokeWidth = Theme.resolveCardStrokeWidthPx(ctx, selected = false).toInt().coerceAtLeast(1)
    }

    fun resolveAccentColor(context: Context): Int = when {
        Theme.isMonochromeBlack(context) -> Color.WHITE
        Theme.isWhiteTheme(context) -> Color.parseColor("#18181B")
        Theme.isLightGrayTheme(context) -> Color.parseColor("#2563EB")
        else -> Theme.getSeedPrimaryColor(context)
    }

    fun styleExpressiveTabs(
        container: View,
        tabLayout: TabLayout,
        cornerRadiusDp: Float = 14f,
    ) {
        val ctx = container.context
        val isNight = Theme.usingNightMode(ctx)
        val seedPrimary = Theme.getSeedPrimaryColor(ctx)
        val containerBg = Theme.resolveCardBackground(ctx)
        val strokeColor = Theme.resolveCardStrokeColor(ctx, selected = false)
        val strokeWidth = Theme.resolveCardStrokeWidthPx(ctx, selected = false).toInt().coerceAtLeast(1)

        container.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(cornerRadiusDp.toInt()).toFloat()
            setColor(containerBg)
            setStroke(strokeWidth, strokeColor)
        }

        val indicatorColor = when {
            Theme.isMonochromeBlack(ctx) -> ColorUtils.setAlphaComponent(Color.WHITE, 42)
            Theme.isWhiteTheme(ctx) -> ColorUtils.setAlphaComponent(Color.BLACK, 24)
            Theme.isLightGrayTheme(ctx) -> ColorUtils.setAlphaComponent(Color.parseColor("#2563EB"), 32)
            else -> ColorUtils.setAlphaComponent(seedPrimary, if (isNight) 54 else 32)
        }
        val selectedTextColor = when {
            Theme.isMonochromeBlack(ctx) -> Color.WHITE
            Theme.isWhiteTheme(ctx) -> Color.BLACK
            Theme.isLightGrayTheme(ctx) -> Color.parseColor("#1D4ED8")
            else -> if (isNight) Color.WHITE else seedPrimary
        }
        val unselectedTextColor = ctx.getColorAttr(android.R.attr.textColorSecondary)

        val tabColorStates = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(selectedTextColor, unselectedTextColor),
        )
        tabLayout.setSelectedTabIndicatorColor(indicatorColor)
        tabLayout.setTabTextColors(unselectedTextColor, selectedTextColor)
        tabLayout.tabIconTint = tabColorStates
        applyTabTypography(tabLayout)
    }

    fun applyTabTypography(tabLayout: TabLayout) {
        val mediumFace = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return
        for (i in 0 until tabStrip.childCount) {
            val tabView = tabStrip.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until tabView.childCount) {
                val child = tabView.getChildAt(j)
                if (child is TextView) {
                    child.typeface = mediumFace
                    child.letterSpacing = 0f
                    child.includeFontPadding = false
                    child.isAllCaps = false
                }
            }
        }
    }

    fun fitTabsToContainer(tabLayout: TabLayout) {
        applyTabTypography(tabLayout)
        if (tabLayout.tabCount <= 0) return
        tabLayout.post {
            applyTabTypography(tabLayout)
            val parentView = tabLayout.parent as? View
            val availableWidth = if (parentView != null && parentView.width > 0) {
                parentView.width - parentView.paddingLeft - parentView.paddingRight
            } else {
                tabLayout.width
            }
            if (availableWidth <= 0) return@post

            val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return@post
            var totalNaturalWidth = 0
            val minExtraPx = dp2px(8)
            for (i in 0 until tabStrip.childCount) {
                val tabView = tabStrip.getChildAt(i) as? ViewGroup ?: continue
                var textWidth = 0f
                var iconWidth = 0
                for (j in 0 until tabView.childCount) {
                    when (val child = tabView.getChildAt(j)) {
                        is TextView -> {
                            val label = child.text?.toString().orEmpty()
                            textWidth += child.paint.measureText(label)
                        }
                        is ImageView -> {
                            if (child.visibility == View.VISIBLE && child.drawable != null) {
                                iconWidth += child.drawable.intrinsicWidth.coerceAtLeast(dp2px(18)) + dp2px(6)
                            }
                        }
                    }
                }
                val horizontalPad = tabView.paddingLeft + tabView.paddingRight
                totalNaturalWidth += kotlin.math.ceil((textWidth + iconWidth + horizontalPad + minExtraPx).toDouble()).toInt()
            }

            val targetMode = if (totalNaturalWidth <= availableWidth) {
                TabLayout.MODE_FIXED
            } else {
                TabLayout.MODE_SCROLLABLE
            }
            val targetGravity = if (targetMode == TabLayout.MODE_FIXED) {
                TabLayout.GRAVITY_FILL
            } else {
                TabLayout.GRAVITY_START
            }
            if (tabLayout.tabMode != targetMode) {
                tabLayout.tabMode = targetMode
            }
            if (tabLayout.tabGravity != targetGravity) {
                tabLayout.tabGravity = targetGravity
            }
        }
    }

    fun attachActivityFloatingHeader(activity: AppCompatActivity, root: View, title: CharSequence) {
        val isAmoled = Theme.isBlackTheme(activity)
        val screenBg = if (isAmoled) Color.BLACK else activity.getColorAttr(android.R.attr.colorBackground)
        root.setBackgroundColor(screenBg)

        val appbar = root.findViewById<AppBarLayout>(R.id.appbar)
        val toolbar = root.findViewById<Toolbar>(R.id.toolbar)
        appbar?.setBackgroundColor(screenBg)
        toolbar?.visibility = View.GONE

        attachFloatingPillHeader(appbar, activity)?.update(
            context = activity,
            title = title,
            iconRes = R.drawable.baseline_arrow_back_24,
            contentDescription = activity.getString(R.string.navigation_back),
        ) {
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    fun applySquircleIcon(
        imageView: ImageView,
        iconRes: Int,
        sizeDp: Int = 40,
        iconSizeDp: Int = 20,
        customContainerColor: Int? = null,
        customIconColor: Int? = null,
    ) {
        val ctx = imageView.context
        val isNight = Theme.usingNightMode(ctx)
        val isAmoled = Theme.isBlackTheme(ctx)
        val (defaultContainer, defaultIcon) = resolveBadgeColors(ctx, isNight, isAmoled)
        val raw = ResourcesCompat.getDrawable(ctx.resources, iconRes, null) ?: return
        imageView.setImageDrawable(
            createMaskedIcon(
                ctx,
                raw,
                customContainerColor ?: defaultContainer,
                customIconColor ?: defaultIcon,
                sizeDp = sizeDp,
                iconSizeDp = iconSizeDp,
                shape = ExpressiveShape.SQUIRCLE,
            ),
        )
    }

    fun styleExpressiveNestedBox(
        view: View,
        cornerRadiusDp: Float = 14f,
        strokeAlpha: Int = 255,
    ) {
        val ctx = view.context
        val isBorderTheme = Theme.isAnyAmoled(ctx) || Theme.isMonochromeBlack(ctx)
        val nestedBg = Theme.resolveNestedBoxBackground(ctx)
        val baseStroke = Theme.resolveCardStrokeColor(ctx, selected = false)
        val strokeColor = if (strokeAlpha < 255) {
            ColorUtils.setAlphaComponent(baseStroke, strokeAlpha)
        } else {
            baseStroke
        }
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(cornerRadiusDp.toInt()).toFloat()
            setColor(nestedBg)
            if (isBorderTheme) {
                setStroke(dp2px(1), strokeColor)
            }
        }
    }

    fun styleExpressiveBadge(
        textView: TextView,
        accentColor: Int? = null,
        cornerRadiusDp: Float = 10f,
    ) {
        val ctx = textView.context
        val isNight = Theme.usingNightMode(ctx)
        val accent = accentColor ?: resolveAccentColor(ctx)
        val isMonochromeAccent = accent == Color.WHITE || accent == Color.BLACK
        val fill = if (Theme.isMonochromeBlack(ctx) && isMonochromeAccent) {
            ColorUtils.setAlphaComponent(Color.WHITE, 28)
        } else {
            ColorUtils.setAlphaComponent(accent, if (isNight) 38 else 26)
        }
        val textCol = if (Theme.isMonochromeBlack(ctx) && isMonochromeAccent) {
            Color.WHITE
        } else if (isNight && ColorUtils.calculateLuminance(accent) < 0.35) {
            ColorUtils.blendARGB(accent, Color.WHITE, 0.35f)
        } else {
            accent
        }
        textView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(cornerRadiusDp.toInt()).toFloat()
            setColor(fill)
            if (Theme.isAnyAmoled(ctx) || Theme.isMonochromeBlack(ctx)) {
                setStroke(dp2px(1), ColorUtils.setAlphaComponent(accent, 90))
            }
        }
        textView.setTextColor(textCol)
    }

    fun contrastOnColor(backgroundColor: Int): Int {
        val darkInk = Color.parseColor("#111827")
        val whiteContrast = ColorUtils.calculateContrast(Color.WHITE, backgroundColor)
        val darkContrast = ColorUtils.calculateContrast(darkInk, backgroundColor)
        return if (darkContrast > whiteContrast * 1.15) darkInk else Color.WHITE
    }

    private fun applyUnifiedButtonTypography(button: MaterialButton) {
        button.isAllCaps = false
        button.letterSpacing = 0f
        button.includeFontPadding = false
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun stylePrimaryButton(button: MaterialButton) {
        val ctx = button.context
        val accent = resolveAccentColor(ctx)
        val onAccent = when {
            Theme.isMonochromeBlack(ctx) -> Color.BLACK
            else -> contrastOnColor(accent)
        }
        applyUnifiedButtonTypography(button)
        button.cornerRadius = dp2px(16)
        button.backgroundTintList = ColorStateList.valueOf(accent)
        button.setTextColor(onAccent)
        button.iconTint = ColorStateList.valueOf(onAccent)
    }

    fun styleOutlinedButton(button: MaterialButton) {
        val ctx = button.context
        val isNight = Theme.usingNightMode(ctx)
        val isBorderTheme = Theme.isAnyAmoled(ctx) || Theme.isMonochromeBlack(ctx)
        val accent = resolveAccentColor(ctx)
        val strokeColor = if (isBorderTheme) {
            Theme.resolveCardStrokeColor(ctx, selected = false)
        } else {
            ColorUtils.setAlphaComponent(accent, if (isNight) 150 else 120)
        }
        val textColor = when {
            Theme.isMonochromeBlack(ctx) -> Color.WHITE
            Theme.isWhiteTheme(ctx) -> Color.parseColor("#18181B")
            Theme.isLightGrayTheme(ctx) -> Color.parseColor("#2563EB")
            else -> accent
        }
        applyUnifiedButtonTypography(button)
        button.cornerRadius = dp2px(16)
        button.strokeColor = ColorStateList.valueOf(strokeColor)
        button.strokeWidth = dp2px(1)
        button.setTextColor(textColor)
        button.iconTint = ColorStateList.valueOf(textColor)
        button.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 38))
    }

    fun styleExpressiveChip(chip: Chip, cornerRadiusDp: Float = 12f) {
        val ctx = chip.context
        val isNight = Theme.usingNightMode(ctx)
        val accent = resolveAccentColor(ctx)
        val isMonoBlack = Theme.isMonochromeBlack(ctx)
        val isWhite = Theme.isWhiteTheme(ctx)
        val isLightGray = Theme.isLightGrayTheme(ctx)

        val checkedBg = when {
            isMonoBlack -> ColorUtils.setAlphaComponent(Color.WHITE, 44)
            isWhite || isLightGray -> ColorUtils.setAlphaComponent(accent, 34)
            isNight -> ColorUtils.setAlphaComponent(accent, 52)
            else -> ColorUtils.setAlphaComponent(accent, 34)
        }
        val uncheckedBg = Theme.resolveNestedBoxBackground(ctx)
        val checkedStroke = when {
            isMonoBlack -> Color.WHITE
            isWhite -> Color.parseColor("#18181B")
            else -> accent
        }
        val uncheckedStroke = Theme.resolveCardStrokeColor(ctx, selected = false)
        val checkedText = when {
            isMonoBlack -> Color.WHITE
            isWhite -> Color.parseColor("#18181B")
            isLightGray -> Color.parseColor("#1D4ED8")
            isNight && ColorUtils.calculateLuminance(accent) < 0.35 ->
                ColorUtils.blendARGB(accent, Color.WHITE, 0.35f)
            else -> accent
        }
        val uncheckedText = ctx.getColorAttr(android.R.attr.textColorPrimary)

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        )
        chip.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, dp2px(cornerRadiusDp.toInt()).toFloat())
            .build()
        chip.chipBackgroundColor = ColorStateList(states, intArrayOf(checkedBg, uncheckedBg))
        chip.chipStrokeColor = ColorStateList(states, intArrayOf(checkedStroke, uncheckedStroke))
        chip.chipStrokeWidth = dp2px(1).toFloat()
        chip.setTextColor(ColorStateList(states, intArrayOf(checkedText, uncheckedText)))
        chip.checkedIconTint = ColorStateList.valueOf(checkedText)
        chip.chipIconTint = ColorStateList(states, intArrayOf(checkedText, uncheckedText))
        chip.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 38))

        chip.isAllCaps = false
        chip.letterSpacing = 0f
        chip.includeFontPadding = false
        chip.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        chip.textSize = 13f
        chip.isSingleLine = true
        chip.maxLines = 1
        chip.ellipsize = null
        chip.chipMinHeight = dp2px(36).toFloat()
        chip.minHeight = dp2px(36)
        chip.setEnsureMinTouchTargetSize(false)
        chip.chipStartPadding = dp2px(10).toFloat()
        chip.chipEndPadding = dp2px(10).toFloat()
        chip.textStartPadding = dp2px(4).toFloat()
        chip.textEndPadding = dp2px(4).toFloat()
        chip.iconStartPadding = dp2px(2).toFloat()
        chip.iconEndPadding = dp2px(2).toFloat()
    }

    fun styleExpressiveChips(root: View) {
        if (root is Chip) {
            styleExpressiveChip(root)
        } else if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                styleExpressiveChips(root.getChildAt(i))
            }
        }
    }
}

fun Preference.applyMaskedIconBadges(
    context: Context,
    containerColor: Int,
    iconColor: Int,
) {
    if (this is PreferenceGroup) {
        for (i in 0 until preferenceCount) {
            getPreference(i).applyMaskedIconBadges(context, containerColor, iconColor)
        }
        return
    }
    if (this is SwitchPreference || this is SwitchPreferenceCompat) {
        widgetLayoutResource = R.layout.preference_widget_expressive_switch
    }
    icon?.let { d ->
        icon = ExpressiveSettingsHelper.createMaskedIcon(
            context,
            d,
            containerColor,
            iconColor,
            shape = ExpressiveShape.SQUIRCLE,
        )
    }
}

fun PreferenceFragmentCompat.applyExpressiveSettingsStyle() {
    val ctx = context ?: return
    val list = listView ?: return

    if (list.layoutManager !is FixedLinearLayoutManager) {
        list.layoutManager = FixedLinearLayoutManager(list)
    }

    // Obliterate default divider lines completely
    setDivider(null)
    setDividerHeight(0)

    for (i in list.itemDecorationCount - 1 downTo 0) {
        val deco = list.getItemDecorationAt(i)
        if (deco !is ExpressivePreferenceDecoration) {
            list.removeItemDecoration(deco)
        }
    }

    val isNight = Theme.usingNightMode(ctx)
    val isAmoled = Theme.isBlackTheme(ctx)

    // 3-Tone Dynamic Theme Harmony: Deep background (Derin zemin)
    val screenBg = when {
        isAmoled -> 0xFF000000.toInt()
        else -> {
            val bg = ctx.getColorAttr(android.R.attr.colorBackground)
            if (bg != 0) bg else if (isNight) 0xFF121413.toInt() else 0xFFF0F4F1.toInt()
        }
    }
    list.setBackgroundColor(screenBg)

    var hasExpressiveDeco = false
    for (i in 0 until list.itemDecorationCount) {
        if (list.getItemDecorationAt(i) is ExpressivePreferenceDecoration) {
            hasExpressiveDeco = true
            break
        }
    }
    if (!hasExpressiveDeco) {
        list.addItemDecoration(ExpressivePreferenceDecoration())
    }

    val (containerColor, iconColor) = ExpressiveSettingsHelper.resolveBadgeColors(ctx, isNight, isAmoled)
    preferenceScreen?.applyMaskedIconBadges(ctx, containerColor, iconColor)
}
