package io.nekohasekai.sagernet.ui.route

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.RouteManager
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.route.OutboundIds
import io.nekohasekai.sagernet.route.RouteProfile
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.ui.settings.WarpSettingsFragment
import io.nekohasekai.sagernet.ui.warp.WarpClient
import io.nekohasekai.sagernet.utils.HapticHelper
import io.nekohasekai.sagernet.utils.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The routing-profile quick switch (the desktop Routing menu's profile list and its "Enable Warp" toggle). */
object RouteQuickSwitch {

    /** A scrollable vertical list of route profiles where each row can be selected or edited individually. */
    fun show(fragment: Fragment, onSwitched: ((RouteProfile) -> Unit)? = null) {
        fragment.lifecycleScope.launch {
            val (profiles, currentId, warp) = withContext(Dispatchers.IO) {
                Triple(RouteManager.usable(), RouteManager.current().id, DataStore.enableWarp)
            }
            val context = fragment.context ?: return@launch
            val isNight = Theme.usingNightMode(context)
            val accent = ExpressiveSettingsHelper.resolveAccentColor(context)
            val textPrimary = context.getColorAttr(android.R.attr.textColorPrimary)
            val textSecondary = context.getColorAttr(android.R.attr.textColorSecondary)
            val nestedBg = Theme.resolveNestedBoxBackground(context)
            val editPillBg = Theme.resolveCardBackground(context)
            val rippleColor = ColorUtils.setAlphaComponent(textPrimary, if (isNight) 38 else 28)
            val mediumFace = Typeface.create("sans-serif-medium", Typeface.NORMAL)

            val listContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp2px(16), dp2px(8), dp2px(16), dp2px(4))
            }
            val scrollView = ScrollView(context).apply {
                isVerticalScrollBarEnabled = true
                addView(
                    listContainer,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }

            var dialog: AlertDialog? = null

            profiles.forEachIndexed { index, profile ->
                val selected = profile.id == currentId
                val displayName = profile.name.trim().ifBlank { "#" + profile.id }
                val defaultLabel = when (profile.default_outbound_id) {
                    OutboundIds.DIRECT -> context.getString(R.string.home_route_direct)
                    OutboundIds.BLOCK -> context.getString(R.string.home_route_block)
                    else -> context.getString(R.string.route_proxy)
                }
                val activeRules = profile.rules.count { !it.disabled && !it.isEmpty() }
                val subtitle = if (activeRules > 0) {
                    "$defaultLabel · ${context.getString(R.string.home_route_rules_active, activeRules)}"
                } else {
                    defaultLabel
                }

                val rowShape = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp2px(14).toFloat()
                    setColor(
                        if (selected) {
                            ColorUtils.setAlphaComponent(accent, if (isNight) 40 else 26)
                        } else {
                            nestedBg
                        },
                    )
                    setStroke(dp2px(1), Theme.resolveCardStrokeColor(context, selected = selected))
                }
                val rowMask = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp2px(14).toFloat()
                    setColor(textPrimary)
                }

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp2px(52)
                    setPadding(dp2px(10), dp2px(8), dp2px(8), dp2px(8))
                    background = RippleDrawable(ColorStateList.valueOf(rippleColor), rowShape, rowMask)
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (index < profiles.lastIndex) bottomMargin = dp2px(6)
                    }
                }

                val radio = RadioButton(context).apply {
                    isClickable = false
                    isFocusable = false
                    isChecked = selected
                    minimumWidth = 0
                    minimumHeight = 0
                    if (selected) {
                        buttonTintList = ColorStateList.valueOf(accent)
                    }
                }
                row.addView(
                    radio,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )

                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        marginStart = dp2px(6)
                        marginEnd = dp2px(6)
                    }
                }
                textCol.addView(
                    TextView(context).apply {
                        text = displayName
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        typeface = mediumFace
                        setTextColor(textPrimary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    },
                )
                textCol.addView(
                    TextView(context).apply {
                        text = subtitle
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                        setTextColor(textSecondary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    },
                )
                row.addView(textCol)

                val editShape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(editPillBg)
                    setStroke(dp2px(1), Theme.resolveCardStrokeColor(context, selected = false))
                }
                val editMask = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(textPrimary)
                }
                val editBtn = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp2px(38), dp2px(38))
                    background = RippleDrawable(ColorStateList.valueOf(rippleColor), editShape, editMask)
                    isClickable = true
                    isFocusable = true
                    contentDescription = context.getString(R.string.edit)
                }
                val editIcon = ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(dp2px(18), dp2px(18), Gravity.CENTER)
                    setImageResource(R.drawable.ic_image_edit)
                    imageTintList = ColorStateList.valueOf(if (selected) accent else textSecondary)
                }
                editBtn.addView(editIcon)
                row.addView(editBtn)

                row.setOnClickListener { v ->
                    HapticHelper.perform(context, v, HapticHelper.HapticKind.TAP)
                    dialog?.dismiss()
                    if (profile.id == currentId) return@setOnClickListener
                    fragment.lifecycleScope.launch {
                        val kept = switchTo(profile.id)
                        fragment.snackbar(switchedMessage(fragment, displayName, kept)).show()
                        onSwitched?.invoke(profile)
                    }
                }
                editBtn.setOnClickListener { v ->
                    HapticHelper.perform(context, v, HapticHelper.HapticKind.TAP)
                    dialog?.dismiss()
                    context.startActivity(
                        Intent(context, RouteProfileActivity::class.java)
                            .putExtra(RouteProfileActivity.EXTRA_PROFILE_ID, profile.id),
                    )
                }

                listContainer.addView(row)
            }

            dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.route_profile_quick_switch)
                .setView(scrollView)
                .setPositiveButton(R.string.route_new) { _, _ ->
                    context.startActivity(
                        Intent(context, RouteProfileActivity::class.java)
                            .putExtra(RouteProfileActivity.EXTRA_REMOTE, false),
                    )
                }
                .setNeutralButton(if (warp) R.string.warp_disable else R.string.warp_enable) { _, _ ->
                    setWarpEnabled(fragment, !warp)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            dialog.show()
        }
    }

    /**
     * D15: current_route_id is global; a running service reloads at once, as the desktop restarts the profile, unless
     * the running profile's group keeps its own routing profile (ConnectionModes.cpp:220-226). Returns that group's
     * name, null when it does not apply.
     */
    suspend fun switchTo(id: Long): String? = withContext(Dispatchers.IO) {
        DataStore.currentRouteId = id
        if (!DataStore.serviceState.started) return@withContext null
        RouteManager.groupKeepingRunningRoute()?.let { return@withContext it.displayName() }
        SagerNet.reloadService()
        null
    }

    /** route_switched, followed by why the running profile did not follow when a group [kept] its own. */
    fun switchedMessage(fragment: Fragment, name: CharSequence, kept: String?): String {
        val switched = fragment.getString(R.string.route_switched, name)
        return if (kept == null) switched else switched + "\n" + fragment.getString(R.string.route_group_keeps_own, kept)
    }

    /**
     * "Enable Warp" (mainwindow_setup.cpp:893-903): saves enable_warp (turning on also turns the desktop's enable_tor
     * off, as WarpService::Apply does) and reloads a running service.
     */
    fun setWarpEnabled(fragment: Fragment, enabled: Boolean) {
        DataStore.enableWarp = enabled
        if (enabled) DataStore.enableTor = false
        if (DataStore.serviceState.started) SagerNet.reloadService()
        val activity = fragment.activity as? MainActivity
        if (enabled && !WarpClient.isGenerated()) {
            fragment.snackbar(fragment.getString(R.string.warp_not_generated)).apply {
                if (activity != null) setAction(R.string.settings) {
                    val screen = WarpSettingsFragment::class.java.name
                    activity.openSettingsScreen(screen, activity.getString(R.string.warp_settings))
                }
            }.show()
        } else {
            fragment.snackbar(fragment.getString(if (enabled) R.string.warp_enabled else R.string.warp_disabled)).show()
        }
    }
}
