package moe.matsuri.nb4a.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.card.MaterialCardView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.utils.Theme

/**
 * Modern Material 3 Expressive appearance studio at the top of Appearance Settings:
 * 1) Live miniature mockup showing active background, card surface tones, stroke weight, & seed accent.
 * 2) Visual surface-mode selector strip (Pure AMOLED, AMOLED, Dark, Light, System).
 * 3) Monochrome special theme chips (Black, White, Light Gray) with conflict-free switching.
 */
class ThemeStudioPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
) : Preference(context, attrs, defStyleAttr) {

    init {
        layoutResource = R.layout.layout_pref_theme_studio
        isSelectable = false
    }

    private data class ModeSpec(
        val nightMode: Int,
        val labelRes: Int,
        val winBg: Int,
        val boxBg: Int,
        val boxBg2: Int,
        val boxStroke: Int,
        val boxStrokeWidthDp: Float,
        val isSplit: Boolean = false,
    )

    private fun recreateHost() {
        notifyChanged()
        callChangeListener(DataStore.nightTheme)
        val host = context as? Activity ?: return
        host.window?.decorView?.post {
            if (!host.isFinishing && !host.isDestroyed) {
                ActivityCompat.recreate(host)
            }
        }
    }

    private fun selectSurfaceMode(mode: Int) {
        DataStore.nightTheme = mode
        Theme.currentNightMode = mode
        DataStore.amoledTheme = (mode == Theme.NIGHT_PURE_AMOLED)

        val currentTheme = DataStore.appTheme
        val restoreSeedNeeded = currentTheme == Theme.BLACK ||
            (mode in setOf(Theme.NIGHT_DARK, Theme.NIGHT_AMOLED, Theme.NIGHT_PURE_AMOLED, Theme.NIGHT_SYSTEM) &&
                currentTheme in setOf(Theme.WHITE, Theme.LIGHT_GRAY))
        if (restoreSeedNeeded) {
            val restored = Theme.resolveSeedRestoreTheme()
            DataStore.appTheme = restored
            DataStore.useSystemTheme = (restored == Theme.MONET && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        }

        Theme.applyNightTheme()
        recreateHost()
    }

    private fun selectSpecialTheme(themeId: Int) {
        if (Theme.isColorfulSeed(DataStore.appTheme)) {
            Theme.rememberSeedTheme(DataStore.appTheme)
        }
        DataStore.appTheme = themeId
        DataStore.useSystemTheme = false
        when (themeId) {
            Theme.BLACK -> {
                if (DataStore.nightTheme !in setOf(Theme.NIGHT_AMOLED, Theme.NIGHT_PURE_AMOLED)) {
                    DataStore.nightTheme = Theme.NIGHT_PURE_AMOLED
                }
                DataStore.amoledTheme = (DataStore.nightTheme == Theme.NIGHT_PURE_AMOLED)
                Theme.currentNightMode = DataStore.nightTheme
            }
            Theme.WHITE, Theme.LIGHT_GRAY -> {
                DataStore.nightTheme = Theme.NIGHT_LIGHT
                DataStore.amoledTheme = false
                Theme.currentNightMode = Theme.NIGHT_LIGHT
            }
        }
        Theme.applyNightTheme()
        recreateHost()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val root = holder.itemView as? ViewGroup ?: return
        root.removeAllViews()

        val activeTheme = Theme.activeThemeId()
        val seedColor = resolveEffectiveAccent(activeTheme)
        val isNight = Theme.usingNightMode(context)
        val textPrimary = context.getColorAttr(android.R.attr.textColorPrimary)
        val textSecondary = context.getColorAttr(android.R.attr.textColorSecondary)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(0, dp2px(4), 0, dp2px(6))
        }
        root.addView(container)

        // 1. Live Interactive Preview Card
        container.addView(buildLivePreviewSection(activeTheme, seedColor, isNight, textSecondary))

        // 2. Surface Mode Cards Strip
        container.addView(sectionHeader(context.getString(R.string.theme_studio_mode), textSecondary))
        container.addView(buildModeStrip(activeTheme, seedColor, textPrimary, textSecondary))

        // 3. Monochrome Special Themes (Black / White / Light Gray)
        container.addView(sectionHeader(context.getString(R.string.theme_studio_special), textSecondary))
        container.addView(buildSpecialThemeRow(activeTheme, seedColor, textPrimary, isNight))
    }

    private fun resolveEffectiveAccent(activeTheme: Int): Int {
        return when (activeTheme) {
            Theme.BLACK -> Color.WHITE
            Theme.WHITE -> Color.parseColor("#2563EB")
            Theme.LIGHT_GRAY -> Color.parseColor("#475569")
            else -> Theme.getSeedPrimaryColor(context)
        }
    }

    private fun sectionHeader(title: String, color: Int): TextView {
        return TextView(context).apply {
            text = title
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.04f
            setTextColor(color)
            setPadding(dp2px(4), dp2px(12), dp2px(4), dp2px(6))
        }
    }

    private fun buildLivePreviewSection(
        activeTheme: Int,
        seedColor: Int,
        isNight: Boolean,
        textSecondary: Int,
    ): View {
        val isMonoBlack = Theme.isMonochromeBlack(context)
        val isPure = Theme.isPureAmoled(context)
        val isTinted = Theme.isTintedAmoled(context)
        val isWhite = Theme.isWhiteTheme(context)
        val isLightGray = Theme.isLightGrayTheme(context)

        val canvasBg = when {
            isMonoBlack || isPure || isTinted -> Color.BLACK
            isWhite -> Color.parseColor("#FFFFFF")
            isLightGray -> Color.parseColor("#F5F5F7")
            else -> context.getColorAttr(android.R.attr.colorBackground).takeIf { it != 0 }
                ?: if (isNight) 0xFF121413.toInt() else 0xFFF4F6F5.toInt()
        }
        val boxBg = Theme.resolveCardBackground(context)
        val nestedBg = Theme.resolveNestedBoxBackground(context)
        val boxStrokeCol = Theme.resolveCardStrokeColor(context, selected = false)
        val selectedStrokeCol = Theme.resolveCardStrokeColor(context, selected = true)
        val thinStrokePx = Theme.resolveCardStrokeWidthPx(context, selected = false).toInt().coerceAtLeast(1)
        val selectedStrokePx = Theme.resolveCardStrokeWidthPx(context, selected = true).toInt().coerceAtLeast(1)

        val onCanvasText = if (ColorUtils.calculateLuminance(canvasBg) > 0.45) {
            Color.parseColor("#111827")
        } else {
            Color.WHITE
        }
        val mutedLine = ColorUtils.setAlphaComponent(onCanvasText, if (ColorUtils.calculateLuminance(canvasBg) > 0.45) 38 else 46)
        val modeBadgeText = when {
            activeTheme == Theme.BLACK -> context.getString(R.string.theme_special_black)
            isWhite -> context.getString(R.string.theme_special_white)
            isLightGray -> context.getString(R.string.theme_special_light_gray)
            isPure -> context.getString(R.string.night_mode_pure_amoled)
            isTinted -> context.getString(R.string.night_mode_amoled)
            isNight -> context.getString(R.string.night_mode_dark)
            else -> context.getString(R.string.night_mode_light)
        }

        val outerCard = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            radius = dp2px(22).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(canvasBg)
            strokeWidth = thinStrokePx
            strokeColor = if (isPure) selectedStrokeCol else boxStrokeCol
        }

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(14), dp2px(12), dp2px(14), dp2px(12))
        }
        outerCard.addView(inner)

        // Top header bar inside mockup
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp2px(2), 0, dp2px(2), dp2px(10))
        }
        topBar.addView(TextView(context).apply {
            text = context.getString(R.string.app_name)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(onCanvasText)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val badgeBg = if (isMonoBlack) {
            ColorUtils.setAlphaComponent(Color.WHITE, 28)
        } else {
            ColorUtils.setAlphaComponent(seedColor, if (ColorUtils.calculateLuminance(canvasBg) > 0.45) 32 else 45)
        }
        val badgeTextColor = if (isMonoBlack) Color.WHITE else seedColor
        topBar.addView(TextView(context).apply {
            text = modeBadgeText
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(badgeTextColor)
            setPadding(dp2px(9), dp2px(3), dp2px(9), dp2px(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(99).toFloat()
                setColor(badgeBg)
                setStroke(thinStrokePx, ColorUtils.setAlphaComponent(badgeTextColor, 110))
            }
        })
        inner.addView(topBar)

        // Two miniature profile/box cards side-by-side in preview
        val cardsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        // Selected Box (Card 1)
        cardsRow.addView(buildMockupProfileCard(
            bgColor = if (isPure) ColorUtils.blendARGB(Color.BLACK, seedColor, 0.07f) else nestedBg,
            cardStrokeColor = selectedStrokeCol,
            strokePx = selectedStrokePx,
            accentColor = seedColor,
            lineColor = mutedLine,
            selected = true,
            marginEndPx = dp2px(8),
        ))

        // Unselected Box (Card 2)
        cardsRow.addView(buildMockupProfileCard(
            bgColor = boxBg,
            cardStrokeColor = boxStrokeCol,
            strokePx = thinStrokePx,
            accentColor = seedColor,
            lineColor = mutedLine,
            selected = false,
            marginEndPx = 0,
        ))
        inner.addView(cardsRow)

        // Bottom StatsBar + FAB mockup row
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp2px(10), 0, 0)
        }
        val statsPillBg = when {
            isMonoBlack || isPure -> Color.BLACK
            isTinted -> boxBg
            !isNight && !isWhite && !isLightGray -> seedColor
            else -> boxBg
        }
        val statsTextCol = if (ColorUtils.calculateLuminance(statsPillBg) > 0.45) Color.parseColor("#1E293B") else Color.WHITE
        val statsPill = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp2px(28), 1f).apply {
                marginEnd = dp2px(10)
            }
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp2px(10), 0, dp2px(10), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(14).toFloat()
                setColor(statsPillBg)
                if (isMonoBlack || isPure || isTinted) {
                    setStroke(thinStrokePx, boxStrokeCol)
                }
            }
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp2px(7), dp2px(7)).apply { marginEnd = dp2px(7) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (statsPillBg == seedColor) Color.WHITE else seedColor)
                }
            })
            addView(TextView(context).apply {
                text = "↑ 1.8 MB/s  •  ↓ 24.6 MB/s"
                textSize = 10.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(statsTextCol)
            })
        }
        bottomRow.addView(statsPill)

        val fabBg = when {
            isMonoBlack -> Color.parseColor("#242424")
            isWhite || isLightGray -> Color.parseColor("#1F2937")
            ColorUtils.calculateLuminance(seedColor) > 0.85 -> Color.parseColor("#242424")
            else -> seedColor
        }
        val fabCard = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp2px(30), dp2px(30))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fabBg)
            }
            val iconCol = if (ColorUtils.calculateLuminance(fabBg) > 0.45) Color.BLACK else Color.WHITE
            addView(ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp2px(15), dp2px(15), Gravity.CENTER)
                val d = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_service_connected, null)?.mutate()
                if (d != null) {
                    DrawableCompat.setTint(d, iconCol)
                    setImageDrawable(d)
                }
            })
        }
        bottomRow.addView(fabCard)
        inner.addView(bottomRow)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = context.getString(R.string.theme_studio_preview)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.04f
                setTextColor(textSecondary)
                setPadding(dp2px(4), 0, dp2px(4), dp2px(6))
            })
            addView(outerCard)
        }
    }

    private fun buildMockupProfileCard(
        bgColor: Int,
        cardStrokeColor: Int,
        strokePx: Int,
        accentColor: Int,
        lineColor: Int,
        selected: Boolean,
        marginEndPx: Int,
    ): MaterialCardView {
        val isDark = ColorUtils.calculateLuminance(bgColor) < 0.45
        val primaryBarCol = ColorUtils.setAlphaComponent(if (isDark) Color.WHITE else Color.BLACK, if (selected) 215 else 155)
        return MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = marginEndPx
            }
            radius = dp2px(12).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(bgColor)
            strokeColor = cardStrokeColor
            strokeWidth = strokePx

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp2px(8), dp2px(8), dp2px(8), dp2px(8))
            }
            if (selected) {
                row.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp2px(3.5f), dp2px(26)).apply {
                        marginEnd = dp2px(7)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp2px(4).toFloat()
                        setColor(accentColor)
                    }
                })
            }
            val lines = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            lines.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp2px(64), dp2px(7)).apply {
                    bottomMargin = dp2px(5)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dp2px(4).toFloat()
                    setColor(primaryBarCol)
                }
            })
            lines.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp2px(42), dp2px(5))
                background = GradientDrawable().apply {
                    cornerRadius = dp2px(4).toFloat()
                    setColor(lineColor)
                }
            })
            row.addView(lines)

            row.addView(TextView(context).apply {
                text = if (selected) "38 ms" else "64 ms"
                textSize = 9.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (selected) accentColor else ColorUtils.setAlphaComponent(primaryBarCol, 150))
            })
            addView(row)
        }
    }

    private fun buildModeStrip(
        activeTheme: Int,
        seedColor: Int,
        textPrimary: Int,
        textSecondary: Int,
    ): View {
        val seedForSample = if (activeTheme in setOf(Theme.BLACK, Theme.WHITE, Theme.LIGHT_GRAY)) {
            Color.parseColor("#00B96B")
        } else {
            seedColor
        }
        val tintedCardSample = ColorUtils.blendARGB(Color.BLACK, seedForSample, 0.16f)
        val tintedInnerSample = ColorUtils.blendARGB(Color.BLACK, seedForSample, 0.28f)
        val activeNight = if (Theme.currentNightMode != -1) Theme.currentNightMode else DataStore.nightTheme
        val isSpecialLight = activeTheme in setOf(Theme.WHITE, Theme.LIGHT_GRAY)
        val isSpecialBlack = activeTheme == Theme.BLACK

        val modes = listOf(
            ModeSpec(
                nightMode = Theme.NIGHT_PURE_AMOLED,
                labelRes = R.string.night_mode_pure_amoled,
                winBg = Color.BLACK,
                boxBg = Color.BLACK,
                boxBg2 = ColorUtils.blendARGB(Color.BLACK, seedForSample, 0.08f),
                boxStroke = ColorUtils.setAlphaComponent(seedForSample, 190),
                boxStrokeWidthDp = 0.6f,
            ),
            ModeSpec(
                nightMode = Theme.NIGHT_AMOLED,
                labelRes = R.string.night_mode_amoled,
                winBg = Color.BLACK,
                boxBg = tintedCardSample,
                boxBg2 = tintedInnerSample,
                boxStroke = Color.parseColor("#29FFFFFF"),
                boxStrokeWidthDp = 0.5f,
            ),
            ModeSpec(
                nightMode = Theme.NIGHT_DARK,
                labelRes = R.string.night_mode_dark,
                winBg = Color.parseColor("#141619"),
                boxBg = Color.parseColor("#23272C"),
                boxBg2 = Color.parseColor("#2D3238"),
                boxStroke = Color.parseColor("#26FFFFFF"),
                boxStrokeWidthDp = 0.5f,
            ),
            ModeSpec(
                nightMode = Theme.NIGHT_LIGHT,
                labelRes = R.string.night_mode_light,
                winBg = Color.parseColor("#F1F5F9"),
                boxBg = Color.WHITE,
                boxBg2 = Color.parseColor("#E2E8F0"),
                boxStroke = Color.parseColor("#22000000"),
                boxStrokeWidthDp = 0.5f,
            ),
            ModeSpec(
                nightMode = Theme.NIGHT_SYSTEM,
                labelRes = R.string.theme_mode_system,
                winBg = Color.parseColor("#141619"),
                boxBg = Color.parseColor("#24282D"),
                boxBg2 = Color.WHITE,
                boxStroke = Color.parseColor("#2E888888"),
                boxStrokeWidthDp = 0.5f,
                isSplit = true,
            ),
        )

        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val strip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp2px(1), dp2px(2), dp2px(1), dp2px(2))
        }
        scroll.addView(strip)

        val ringAccent = if (isSpecialBlack) Color.WHITE else if (isSpecialLight) Color.parseColor("#1F2937") else seedColor
        for (spec in modes) {
            val isSelected = !isSpecialBlack && !isSpecialLight && activeNight == spec.nightMode
            val card = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp2px(92), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp2px(8)
                }
                radius = dp2px(16).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(
                    if (isSelected) ColorUtils.setAlphaComponent(ringAccent, 22)
                    else Theme.resolveCardBackground(context),
                )
                strokeColor = if (isSelected) ringAccent else Theme.resolveCardStrokeColor(context, selected = false)
                strokeWidth = if (isSelected) dp2px(1.5f) else Theme.resolveCardStrokeWidthPx(context, selected = false).toInt().coerceAtLeast(1)
                isClickable = true
                isFocusable = true
                setOnClickListener { selectSurfaceMode(spec.nightMode) }
            }

            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp2px(7), dp2px(7), dp2px(7), dp2px(8))
            }

            // Miniature phone-canvas thumbnail inside mode card
            val thumb = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp2px(52),
                ).apply { bottomMargin = dp2px(6) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp2px(10).toFloat()
                    setColor(spec.winBg)
                    setStroke(dp2px(0.5f).coerceAtLeast(1), Color.parseColor("#24888888"))
                }
                clipToOutline = true
            }

            if (spec.isSplit) {
                // Left half light, right half dark for System mode
                val halves = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                }
                halves.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    setBackgroundColor(Color.parseColor("#F1F5F9"))
                })
                halves.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    setBackgroundColor(Color.parseColor("#141619"))
                })
                thumb.addView(halves)
            }

            // Two mini preview boxes inside the thumbnail
            val miniBoxes = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
                setPadding(dp2px(7), dp2px(7), dp2px(7), dp2px(7))
            }
            val miniStrokePx = (spec.boxStrokeWidthDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            miniBoxes.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp2px(17),
                ).apply { bottomMargin = dp2px(4) }
                background = GradientDrawable().apply {
                    cornerRadius = dp2px(5).toFloat()
                    setColor(spec.boxBg)
                    setStroke(miniStrokePx, spec.boxStroke)
                }
            })
            miniBoxes.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp2px(15),
                )
                background = GradientDrawable().apply {
                    cornerRadius = dp2px(5).toFloat()
                    setColor(spec.boxBg2)
                    setStroke(miniStrokePx, spec.boxStroke)
                }
            })
            thumb.addView(miniBoxes)

            // Active accent dot in thumbnail corner
            thumb.addView(View(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp2px(9), dp2px(9), Gravity.BOTTOM or Gravity.END).apply {
                    setMargins(0, 0, dp2px(5), dp2px(5))
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(seedForSample)
                }
            })
            col.addView(thumb)

            col.addView(TextView(context).apply {
                text = context.getString(spec.labelRes)
                textSize = 11.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isSelected) textPrimary else textSecondary)
            })

            card.addView(col)
            strip.addView(card)
        }
        return scroll
    }

    private fun buildSpecialThemeRow(
        activeTheme: Int,
        seedColor: Int,
        textPrimary: Int,
        isNight: Boolean,
    ): LinearLayout {
        data class SpecialSpec(
            val themeId: Int,
            val label: String,
            val dotColor: Int,
            val dotBorder: Int,
        )

        val specials = listOf(
            SpecialSpec(Theme.BLACK, context.getString(R.string.theme_special_black), Color.BLACK, Color.parseColor("#64748B")),
            SpecialSpec(Theme.WHITE, context.getString(R.string.theme_special_white), Color.WHITE, Color.parseColor("#CBD5E1")),
            SpecialSpec(Theme.LIGHT_GRAY, context.getString(R.string.theme_special_light_gray), Color.parseColor("#E5E7EB"), Color.parseColor("#94A3B8")),
        )

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val activeRing = when {
            activeTheme == Theme.BLACK -> Color.WHITE
            activeTheme in setOf(Theme.WHITE, Theme.LIGHT_GRAY) -> Color.parseColor("#1F2937")
            else -> seedColor
        }

        specials.forEachIndexed { idx, spec ->
            val selected = activeTheme == spec.themeId &&
                (spec.themeId == Theme.BLACK || !isNight)
            val card = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < specials.lastIndex) marginEnd = dp2px(8)
                }
                radius = dp2px(14).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(
                    if (selected) ColorUtils.setAlphaComponent(activeRing, if (isNight) 28 else 18)
                    else Theme.resolveCardBackground(context),
                )
                strokeColor = if (selected) activeRing else Theme.resolveCardStrokeColor(context, selected = false)
                strokeWidth = if (selected) dp2px(1.4f) else Theme.resolveCardStrokeWidthPx(context, selected = false).toInt().coerceAtLeast(1)
                isClickable = true
                isFocusable = true
                foreground = RippleDrawable(
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(activeRing, 38)),
                    null,
                    null,
                )
                setOnClickListener { selectSpecialTheme(spec.themeId) }
            }

            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp2px(8), dp2px(10), dp2px(8), dp2px(10))
            }
            inner.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp2px(16), dp2px(16)).apply { marginEnd = dp2px(7) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(spec.dotColor)
                    setStroke(dp2px(1), spec.dotBorder)
                }
            })
            inner.addView(TextView(context).apply {
                text = spec.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(textPrimary)
            })
            card.addView(inner)
            row.addView(card)
        }
        return row
    }

    private fun dp2px(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
}
