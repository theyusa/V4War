/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package moe.matsuri.nb4a.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.Spinner
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceViewHolder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.utils.Theme

/**
 * Bend [DropDownPreference] to support
 * [Simple Menus](https://material.google.com/components/menus.html#menus-behavior).
 */


open class SimpleMenuPreference
@JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dropdownPreferenceStyle,
    defStyleRes: Int = 0
) : DropDownPreference(context!!, attrs, defStyleAttr, defStyleRes) {

    private lateinit var mAdapter: SimpleMenuAdapter

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val mSpinner = holder.itemView.findViewById<Spinner>(R.id.spinner)
        mSpinner.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
        mSpinner.dropDownVerticalOffset = dp2px(4)
        mSpinner.setPopupBackgroundResource(R.drawable.bg_popup_menu)
    }

    override fun createAdapter(): ArrayAdapter<CharSequence?> {
        mAdapter = SimpleMenuAdapter(getContext(), R.layout.simple_menu_dropdown_item)
        return mAdapter
    }

    override fun setValue(value: String?) {
        super.setValue(value)
        if (::mAdapter.isInitialized) {
            // Null while a preference whose entries are set in code is inflated with a stored value.
            mAdapter.currentPosition = entryValues?.indexOf(value) ?: -1
            mAdapter.notifyDataSetChanged()
        }
    }

    private class SimpleMenuAdapter(context: Context, resource: Int) :
        ArrayAdapter<CharSequence?>(context, resource) {

        var currentPosition = -1

        private val pillRadius = dp2px(14).toFloat()
        private val selectedColor = selectedFill(context)
        private val textPrimary = context.getColorAttr(android.R.attr.textColorPrimary)
        private val accentColor = Theme.resolveCardStrokeColor(context, selected = true)
        private val isAmoledBorder = Theme.isAnyAmoled(context) || Theme.isMonochromeBlack(context)
        private val strokePx = dp2px(1)

        private val selectedPillDrawable = GradientDrawable().apply {
            setColor(selectedColor)
            cornerRadius = pillRadius
            if (isAmoledBorder) {
                setStroke(strokePx, ColorUtils.setAlphaComponent(accentColor, 140))
            }
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View = super.getDropDownView(position, convertView, parent)
            val checkedView = view as? CheckedTextView

            if (position == currentPosition) {
                view.background = selectedPillDrawable
                checkedView?.apply {
                    isChecked = true
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(textPrimary)
                    val checkIcon = ResourcesCompat.getDrawable(
                        context.resources,
                        R.drawable.ic_baseline_check_circle_24,
                        null,
                    )?.mutate()
                    if (checkIcon != null) {
                        val size = dp2px(18)
                        checkIcon.setBounds(0, 0, size, size)
                        DrawableCompat.setTint(checkIcon, accentColor)
                        setCompoundDrawablesRelative(null, null, checkIcon, null)
                        compoundDrawablePadding = dp2px(10)
                    } else {
                        checkMarkDrawable = null
                    }
                }
            } else {
                view.background = null
                checkedView?.apply {
                    isChecked = false
                    setTypeface(null, Typeface.NORMAL)
                    setTextColor(textPrimary)
                    checkMarkDrawable = null
                    setCompoundDrawablesRelative(null, null, null, null)
                }
            }
            return view
        }
    }
}

/**
 * The selected row's fill over the popup's surface: the theme's primary, stronger on a dark surface, or the text
 * colour where the primary would not show (the white theme by day, the black one at night), faded until the text keeps
 * 4.5:1. It stays translucent, so the popup's border still shows along the row.
 */
private fun selectedFill(context: Context): Int {
    val elevated = context.getColorAttr(R.attr.cardElevatedSurfaceColor)
    val baseSurface = if (elevated != 0) elevated else context.getColorAttr(R.attr.colorSurface)
    val surface = ColorUtils.setAlphaComponent(baseSurface, 0xFF)
    val text = context.getColorAttr(android.R.attr.textColorPrimary)
    val dark = ColorUtils.calculateLuminance(surface) < 0.5
    var fill = ColorUtils.setAlphaComponent(context.getColorAttr(R.attr.colorPrimary), if (dark) 0x66 else 0x48)
    // CIE76 ΔE under 6: the tint reads as the surface itself
    if (labDistance(ColorUtils.compositeColors(fill, surface), surface) < 6) {
        fill = ColorUtils.setAlphaComponent(text, 0x1F)
    }
    while (Color.alpha(fill) > 0x0A &&
        ColorUtils.calculateContrast(text, ColorUtils.compositeColors(fill, surface)) < 4.5
    ) {
        fill = ColorUtils.setAlphaComponent(fill, Color.alpha(fill) - 0x0A)
    }
    return fill
}

private fun labDistance(a: Int, b: Int): Double {
    val labA = DoubleArray(3).also { ColorUtils.colorToLAB(a, it) }
    val labB = DoubleArray(3).also { ColorUtils.colorToLAB(b, it) }
    return ColorUtils.distanceEuclidean(labA, labB)
}
