package moe.matsuri.nb4a.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.TypedArrayUtils
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.ui.settings.ExpressiveShape
import io.nekohasekai.sagernet.utils.Theme

/**
 * Collapsible Material 3 Expressive seed-color palette box.
 * Header carries an M3 Expressive geometric badge, active seed chip, compact Custom HEX button,
 * and expand/collapse toggle; body expands/collapses the 6-column color swatch grid smoothly.
 */
class ColorPickerPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = TypedArrayUtils.getAttr(
        context,
        androidx.preference.R.attr.preferenceStyle,
        android.R.attr.preferenceStyle,
    ),
) : Preference(context, attrs, defStyle) {

    private data class PresetTheme(
        val id: Int,
        val name: String,
        val color: Int,
    )

    init {
        layoutResource = R.layout.layout_pref_color_palette
        isSelectable = false
    }

    private fun resolveSwatchColor(themeId: Int): Int {
        return when (themeId) {
            Theme.BLACK -> Color.BLACK
            Theme.WHITE -> Color.WHITE
            Theme.LIGHT_GRAY -> Color.parseColor("#F5F5F7")
            Theme.MONET -> Theme.getSystemWallpaperColor(context) ?: Color.parseColor("#3664E8")
            Theme.VERDANT_MINT -> Color.parseColor("#00B96B")
            Theme.BLUE -> Color.parseColor("#2196F3")
            Theme.INDIGO -> Color.parseColor("#3F51B5")
            Theme.LIGHT_BLUE -> Color.parseColor("#03A9F4")
            Theme.CYAN -> Color.parseColor("#00BCD4")
            Theme.TEAL -> Color.parseColor("#009688")
            Theme.GREEN -> Color.parseColor("#4CAF50")
            Theme.LIGHT_GREEN -> Color.parseColor("#8BC34A")
            Theme.LIME -> Color.parseColor("#CDDC39")
            Theme.YELLOW -> Color.parseColor("#FFEB3B")
            Theme.AMBER -> Color.parseColor("#FFC107")
            Theme.ORANGE -> Color.parseColor("#FF9800")
            Theme.DEEP_ORANGE -> Color.parseColor("#FF5722")
            Theme.RED -> Color.parseColor("#F44336")
            Theme.PINK_SSR -> Color.parseColor("#FB7299")
            Theme.PINK -> Color.parseColor("#E91E63")
            Theme.PURPLE -> Color.parseColor("#9C27B0")
            Theme.DEEP_PURPLE -> Color.parseColor("#673AB7")
            Theme.BROWN -> Color.parseColor("#795548")
            Theme.BLUE_GREY -> Color.parseColor("#607D8B")
            Theme.GREY -> Color.parseColor("#9E9E9E")
            Theme.CUSTOM -> 0xFF000000.toInt() or DataStore.customThemeColor
            else -> context.getColorAttr(R.attr.colorPrimary)
        }
    }

    private fun getThemeName(themeId: Int, isTr: Boolean): String {
        return when (themeId) {
            Theme.BLACK -> context.getString(R.string.theme_special_black)
            Theme.WHITE -> context.getString(R.string.theme_special_white)
            Theme.LIGHT_GRAY -> context.getString(R.string.theme_special_light_gray)
            Theme.MONET -> "Material You"
            Theme.VERDANT_MINT -> if (isTr) "Nane Yeşili" else "Verdant Mint"
            Theme.BLUE -> if (isTr) "Klasik Mavi" else "Classic Blue"
            Theme.RED -> if (isTr) "Alev Kırmızısı" else "Flame Red"
            Theme.DEEP_PURPLE -> if (isTr) "Koyu Mor" else "Deep Purple"
            Theme.PURPLE -> if (isTr) "Lavanta Moru" else "Purple"
            Theme.DEEP_ORANGE -> if (isTr) "Mercan" else "Coral Orange"
            Theme.ORANGE -> if (isTr) "Canlı Turuncu" else "Vivid Orange"
            Theme.AMBER -> if (isTr) "Kehribar" else "Amber"
            Theme.CYAN -> if (isTr) "Turkuaz" else "Cyan"
            Theme.TEAL -> if (isTr) "Su Yeşili" else "Teal"
            Theme.GREEN -> if (isTr) "Orman Yeşili" else "Forest Green"
            Theme.INDIGO -> if (isTr) "İndigo" else "Indigo"
            Theme.LIGHT_BLUE -> if (isTr) "Gök Mavisi" else "Sky Blue"
            Theme.LIGHT_GREEN -> if (isTr) "Açık Yeşil" else "Light Green"
            Theme.LIME -> if (isTr) "Limon Yeşili" else "Lime"
            Theme.YELLOW -> if (isTr) "Sarı" else "Yellow"
            Theme.PINK_SSR -> if (isTr) "Kiraz Çiçeği" else "Sakura Pink"
            Theme.PINK -> if (isTr) "Neon Pembe" else "Neon Pink"
            Theme.BROWN -> if (isTr) "Mocha Kahve" else "Mocha Brown"
            Theme.BLUE_GREY -> if (isTr) "Mavi Gri" else "Blue Grey"
            Theme.GREY -> if (isTr) "Antrasit Gri" else "Graphite Grey"
            Theme.CUSTOM -> String.format("#%06X", 0xFFFFFF and DataStore.customThemeColor)
            else -> if (isTr) "Özel" else "Custom"
        }
    }

    override fun getSummary(): CharSequence {
        val activeThemeId = Theme.activeThemeId()
        val isTr = context.resources.configuration.locales[0].language == "tr"
        return getThemeName(activeThemeId, isTr)
    }

    private fun buildPresets(isTr: Boolean): List<PresetTheme> {
        val list = mutableListOf<PresetTheme>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(PresetTheme(Theme.MONET, "Material You", resolveSwatchColor(Theme.MONET)))
        }
        val seedIds = listOf(
            Theme.VERDANT_MINT,
            Theme.BLUE,
            Theme.INDIGO,
            Theme.LIGHT_BLUE,
            Theme.CYAN,
            Theme.TEAL,
            Theme.GREEN,
            Theme.LIGHT_GREEN,
            Theme.LIME,
            Theme.YELLOW,
            Theme.AMBER,
            Theme.ORANGE,
            Theme.DEEP_ORANGE,
            Theme.RED,
            Theme.PINK_SSR,
            Theme.PINK,
            Theme.PURPLE,
            Theme.DEEP_PURPLE,
            Theme.BROWN,
            Theme.BLUE_GREY,
            Theme.GREY,
        )
        for (id in seedIds) {
            list.add(PresetTheme(id, getThemeName(id, isTr), resolveSwatchColor(id)))
        }
        list.add(
            PresetTheme(
                Theme.CUSTOM,
                context.getString(R.string.theme_custom_color),
                0xFF000000.toInt() or DataStore.customThemeColor,
            ),
        )
        return list
    }

    private var recreateScheduled = false

    private fun applyTheme(themeId: Int) {
        if (themeId != Theme.CUSTOM && themeId == Theme.activeThemeId()) return
        if (recreateScheduled) return
        recreateScheduled = true
        if (Theme.isColorfulSeed(themeId)) {
            Theme.rememberSeedTheme(themeId)
        }
        DataStore.appTheme = themeId
        DataStore.useSystemTheme = (themeId == Theme.MONET)
        Theme.applyNightTheme()
        notifyChanged()
        callChangeListener(themeId)
        val host = context as? Activity ?: return
        host.window?.decorView?.post {
            if (!host.isFinishing && !host.isDestroyed) {
                ActivityCompat.recreate(host)
            }
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val root = holder.itemView as? ViewGroup ?: return
        root.removeAllViews()

        val activeThemeId = Theme.activeThemeId()
        val isTr = context.resources.configuration.locales[0].language == "tr"
        val isNight = Theme.usingNightMode(context)
        val isAmoled = Theme.isBlackTheme(context)
        val isAmoledBorder = Theme.isAnyAmoled(context) || Theme.isMonochromeBlack(context)
        val textPrimary = context.getColorAttr(android.R.attr.textColorPrimary)
        val textSecondary = context.getColorAttr(android.R.attr.textColorSecondary)
        val activeColor = resolveSwatchColor(activeThemeId)
        val effectiveRing = Theme.getSeedPrimaryColor(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        // Collapsible swatch grid container
        val gridContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(0, dp2px(12), 0, 0)
            visibility = if (DataStore.appearancePaletteExpanded) View.VISIBLE else View.GONE
        }

        val presets = buildPresets(isTr)
        val columns = 6
        val rows = presets.chunked(columns)
        rows.forEachIndexed { rowIdx, chunk ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (rowIdx < rows.lastIndex) bottomMargin = dp2px(10)
                }
            }
            for (colIdx in 0 until columns) {
                val cell = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp2px(46), 1f)
                }
                val preset = chunk.getOrNull(colIdx)
                if (preset != null) {
                    val isSelected = activeThemeId == preset.id
                    cell.addView(buildSwatchItem(preset, isSelected, isNight, textSecondary))
                }
                row.addView(cell)
            }
            gridContainer.addView(row)
        }

        val chevronIcon = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp2px(18), dp2px(18), Gravity.CENTER)
        }
        fun updateChevron(expanded: Boolean) {
            val resId = if (expanded) R.drawable.ic_baseline_expand_less_24 else R.drawable.ic_baseline_expand_more_24
            val d = ResourcesCompat.getDrawable(context.resources, resId, null)?.mutate()
            if (d != null) {
                DrawableCompat.setTint(d, textSecondary)
                chevronIcon.setImageDrawable(d)
            }
        }
        updateChevron(DataStore.appearancePaletteExpanded)

        // Header row: Expressive Icon Badge + Title/Active Seed + Custom pill + Chevron toggle
        var isExpanded = DataStore.appearancePaletteExpanded
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(14).toFloat()
                setColor(Color.WHITE)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(effectiveRing, if (isNight) 34 else 22)),
                null,
                mask,
            )
            setOnClickListener {
                isExpanded = !isExpanded
                DataStore.appearancePaletteExpanded = isExpanded
                updateChevron(isExpanded)
                TransitionManager.endTransitions(container)
                if (container.isAttachedToWindow && container.isLaidOut) {
                    TransitionManager.beginDelayedTransition(
                        container,
                        AutoTransition().apply { duration = 150L },
                    )
                }
                gridContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
            }
        }

        val (badgeContainer, badgeIconCol) = ExpressiveSettingsHelper.resolveBadgeColors(context, isNight, isAmoled)
        val paletteDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_baseline_color_lens_24, null)
        if (paletteDrawable != null) {
            val badge = ExpressiveSettingsHelper.createMaskedIcon(
                context = context,
                originalDrawable = paletteDrawable,
                containerColor = badgeContainer,
                iconColor = badgeIconCol,
                sizeDp = 42,
                iconSizeDp = 22,
                shape = ExpressiveShape.SQUIRCLE,
            )
            header.addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp2px(42), dp2px(42)).apply {
                    marginEnd = dp2px(14)
                }
                setImageDrawable(badge)
            })
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp2px(8)
            }
        }
        titleCol.addView(TextView(context).apply {
            text = title ?: context.getString(R.string.theme_studio_seed_color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(textPrimary)
        })

        val activeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp2px(2), 0, 0)
        }
        activeRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp2px(9), dp2px(9)).apply {
                marginEnd = dp2px(6)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(activeColor)
                setStroke(dp2px(0.8f).coerceAtLeast(1), ColorUtils.setAlphaComponent(textSecondary, 90))
            }
        })
        activeRow.addView(TextView(context).apply {
            text = getThemeName(activeThemeId, isTr)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(textSecondary)
        })
        titleCol.addView(activeRow)
        header.addView(titleCol)

        // Compact Custom HEX button pill
        val customPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dp2px(8)
            }
            setPadding(dp2px(10), dp2px(6), dp2px(10), dp2px(6))
            isClickable = true
            isFocusable = true
            val pillStroke = if (activeThemeId == Theme.CUSTOM) effectiveRing
            else Theme.resolveCardStrokeColor(context, selected = false)
            val pillBg = if (activeThemeId == Theme.CUSTOM) {
                ColorUtils.setAlphaComponent(effectiveRing, if (isNight) 38 else 24)
            } else {
                Theme.resolveNestedBoxBackground(context)
            }
            val bgShape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(99).toFloat()
                setColor(pillBg)
                setStroke(dp2px(1), pillStroke)
            }
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(99).toFloat()
                setColor(Color.WHITE)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(effectiveRing, 40)),
                bgShape,
                mask,
            )
            setOnClickListener { showCustomColorDialog() }
        }
        customPill.addView(TextView(context).apply {
            text = context.getString(R.string.theme_custom_color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(textPrimary)
        })
        header.addView(customPill)

        header.addView(FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp2px(30), dp2px(30))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Theme.resolveNestedBoxBackground(context))
                if (isAmoledBorder) {
                    setStroke(
                        Theme.resolveCardStrokeWidthPx(context, selected = false).toInt().coerceAtLeast(1),
                        Theme.resolveCardStrokeColor(context, selected = false),
                    )
                }
            }
            addView(chevronIcon)
        })

        container.addView(header)
        container.addView(gridContainer)
        root.addView(container)
    }

    private fun buildSwatchItem(
        preset: PresetTheme,
        isSelected: Boolean,
        isNight: Boolean,
        ringFallback: Int,
    ): FrameLayout {
        val swatchSize = dp2px(38)
        val item = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp2px(44), dp2px(44), Gravity.CENTER)
            contentDescription = preset.name
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(preset.color, 55)),
                null,
                GradientDrawable().apply { shape = GradientDrawable.OVAL },
            )
            setOnClickListener {
                if (preset.id == Theme.CUSTOM) {
                    showCustomColorDialog()
                } else {
                    applyTheme(preset.id)
                }
            }
        }

        if (isSelected) {
            item.addView(View(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp2px(43), dp2px(43), Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(dp2px(2), preset.color)
                }
            })
        }

        val innerCircleSize = if (isSelected) dp2px(33) else swatchSize
        val circle = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(innerCircleSize, innerCircleSize, Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (preset.id == Theme.MONET) {
                    colors = intArrayOf(
                        preset.color,
                        ColorUtils.blendARGB(preset.color, Color.WHITE, 0.25f),
                    )
                    orientation = GradientDrawable.Orientation.TL_BR
                } else {
                    setColor(preset.color)
                }
                if (!isSelected) {
                    setStroke(
                        dp2px(0.8f).coerceAtLeast(1),
                        ColorUtils.setAlphaComponent(ringFallback, if (isNight) 55 else 40),
                    )
                }
            }
        }
        item.addView(circle)

        val checkIconColor = if (ColorUtils.calculateLuminance(preset.color) > 0.52) Color.BLACK else Color.WHITE
        if (isSelected) {
            item.addView(ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp2px(18), dp2px(18), Gravity.CENTER)
                val checkDrawable = ResourcesCompat.getDrawable(
                    context.resources,
                    R.drawable.ic_baseline_check_circle_24,
                    null,
                )?.mutate()
                if (checkDrawable != null) {
                    DrawableCompat.setTint(checkDrawable, checkIconColor)
                    setImageDrawable(checkDrawable)
                }
            })
        } else if (preset.id == Theme.CUSTOM) {
            item.addView(TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                )
                text = "+"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(checkIconColor)
                includeFontPadding = false
            })
        }
        return item
    }

    private fun showCustomColorDialog() {
        val initialColor = 0xFF000000.toInt() or DataStore.customThemeColor
        var red = Color.red(initialColor)
        var green = Color.green(initialColor)
        var blue = Color.blue(initialColor)
        var updatingFromSliders = false
        var updatingFromText = false

        val isNight = Theme.usingNightMode(context)
        val isAmoledBorder = Theme.isAnyAmoled(context) || Theme.isMonochromeBlack(context)
        val cardBg = Theme.resolveCardBackground(context)
        val nestedBg = Theme.resolveNestedBoxBackground(context)
        val strokeCol = Theme.resolveCardStrokeColor(context, selected = false)
        val strokePx = Theme.resolveCardStrokeWidthPx(context, selected = false).toInt().coerceAtLeast(1)
        val textPrimary = context.getColorAttr(android.R.attr.textColorPrimary)
        val textSecondary = context.getColorAttr(android.R.attr.textColorSecondary)
        val accentColor = when {
            Theme.isMonochromeBlack(context) -> Color.WHITE
            Theme.isWhiteTheme(context) -> Color.parseColor("#212121")
            Theme.isLightGrayTheme(context) -> Color.parseColor("#2563EB")
            else -> Theme.getSeedPrimaryColor(context)
        }

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(20), dp2px(12), dp2px(20), dp2px(4))
        }

        // Theme-harmonized preview + HEX box
        val previewCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp2px(12)
            }
            setPadding(dp2px(14), dp2px(12), dp2px(14), dp2px(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(18).toFloat()
                setColor(nestedBg)
                setStroke(
                    strokePx,
                    if (isAmoledBorder) strokeCol else ColorUtils.setAlphaComponent(textSecondary, 45),
                )
            }
        }
        val previewDot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp2px(44), dp2px(44)).apply {
                marginEnd = dp2px(14)
            }
        }
        previewCard.addView(previewDot)

        val inputCorner = dp2px(14).toFloat()
        val focusedStroke = accentColor
        val unfocusedStroke = ColorUtils.setAlphaComponent(textSecondary, if (isNight) 125 else 95)
        val strokeStates = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(focusedStroke, unfocusedStroke),
        )
        val hexLayout = TextInputLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            hint = context.getString(R.string.custom_color_hex_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxBackgroundColor = cardBg
            setBoxCornerRadii(inputCorner, inputCorner, inputCorner, inputCorner)
            setBoxStrokeColorStateList(strokeStates)
            defaultHintTextColor = ColorStateList.valueOf(textSecondary)
            hintTextColor = ColorStateList.valueOf(accentColor)
        }
        val hexInput = TextInputEditText(hexLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            maxLines = 1
            typeface = Typeface.MONOSPACE
            setTextColor(textPrimary)
            setHintTextColor(textSecondary)
            setText(String.format("#%06X", 0xFFFFFF and initialColor))
        }
        hexLayout.addView(hexInput)
        previewCard.addView(hexLayout)
        dialogLayout.addView(previewCard)

        fun updatePreviewDot(color: Int) {
            previewDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(dp2px(1.5f).coerceAtLeast(1), ColorUtils.setAlphaComponent(textPrimary, 110))
            }
        }
        updatePreviewDot(initialColor)

        val slidersCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(dp2px(14), dp2px(10), dp2px(14), dp2px(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(18).toFloat()
                setColor(nestedBg)
                setStroke(
                    strokePx,
                    if (isAmoledBorder) strokeCol else ColorUtils.setAlphaComponent(textSecondary, 45),
                )
            }
        }

        val trackBgTint = ColorStateList.valueOf(ColorUtils.setAlphaComponent(textSecondary, 80))

        fun buildChannelSlider(label: String, tint: Int, initial: Int, onChange: (Int) -> Unit): SeekBar {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp2px(5), 0, dp2px(5))
            }
            val nameView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp2px(26), dp2px(26)).apply {
                    marginEnd = dp2px(8)
                }
                gravity = Gravity.CENTER
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tint)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorUtils.setAlphaComponent(tint, if (isNight) 38 else 26))
                }
            }
            val valueView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp2px(36), ViewGroup.LayoutParams.WRAP_CONTENT)
                text = initial.toString()
                gravity = Gravity.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.MONOSPACE
                setTextColor(textSecondary)
            }
            val seek = SeekBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                max = 255
                progress = initial
                progressBackgroundTintList = trackBgTint
                progressTintList = ColorStateList.valueOf(tint)
                thumbTintList = ColorStateList.valueOf(tint)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        valueView.text = progress.toString()
                        if (fromUser && !updatingFromText) {
                            onChange(progress)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }
            row.addView(nameView)
            row.addView(seek)
            row.addView(valueView)
            slidersCard.addView(row)
            return seek
        }
        dialogLayout.addView(slidersCard)

        lateinit var redSeek: SeekBar
        lateinit var greenSeek: SeekBar
        lateinit var blueSeek: SeekBar

        fun syncHexFromRgb() {
            val col = Color.rgb(red, green, blue)
            updatePreviewDot(col)
            updatingFromSliders = true
            hexInput.setText(String.format("#%06X", 0xFFFFFF and col))
            hexInput.setSelection(hexInput.text?.length ?: 0)
            updatingFromSliders = false
        }

        redSeek = buildChannelSlider("R", Color.parseColor("#EF4444"), red) { v ->
            red = v
            syncHexFromRgb()
        }
        greenSeek = buildChannelSlider("G", Color.parseColor("#22C55E"), green) { v ->
            green = v
            syncHexFromRgb()
        }
        blueSeek = buildChannelSlider("B", Color.parseColor("#3B82F6"), blue) { v ->
            blue = v
            syncHexFromRgb()
        }

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingFromSliders) return
                val raw = s?.toString()?.trim().orEmpty()
                val normalized = if (raw.startsWith("#")) raw else "#$raw"
                if (normalized.length == 7 || normalized.length == 9) {
                    runCatching { Color.parseColor(normalized) }.getOrNull()?.let { parsed ->
                        updatingFromText = true
                        red = Color.red(parsed)
                        green = Color.green(parsed)
                        blue = Color.blue(parsed)
                        redSeek.progress = red
                        greenSeek.progress = green
                        blueSeek.progress = blue
                        updatePreviewDot(0xFF000000.toInt() or parsed)
                        updatingFromText = false
                    }
                }
            }
        })

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.custom_color_title))
            .setView(dialogLayout)
            .setPositiveButton(context.getString(R.string.custom_color_apply)) { _, _ ->
                val raw = hexInput.text?.toString()?.trim().orEmpty()
                val parsed = if (raw.isNotEmpty()) {
                    runCatching {
                        Color.parseColor(if (raw.startsWith("#")) raw else "#$raw")
                    }.getOrNull() ?: Color.rgb(red, green, blue)
                } else {
                    Color.rgb(red, green, blue)
                }
                DataStore.customThemeColor = 0xFF000000.toInt() or parsed
                applyTheme(Theme.CUSTOM)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val inset = context.resources.getDimensionPixelSize(R.dimen.dialog_background_inset)
            val surfaceBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.resources.getDimension(R.dimen.dialog_corner_radius)
                setColor(cardBg)
                setStroke(
                    dp2px(0.8f).coerceAtLeast(1),
                    if (isAmoledBorder) strokeCol else ColorUtils.setAlphaComponent(textSecondary, 55),
                )
            }
            dialog.window?.setBackgroundDrawable(InsetDrawable(surfaceBg, inset))
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.apply {
                isAllCaps = false
                setTextColor(accentColor)
            }
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
                isAllCaps = false
                setTextColor(textSecondary)
            }
        }
        dialog.show()
    }

    private fun dp2px(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
}
