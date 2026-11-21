package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.RemoteException
import android.text.TextUtils
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.card.MaterialCardView
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.LiveStatsBatch
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.RouteManager
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.route.OutboundIds
import io.nekohasekai.sagernet.ui.route.RouteQuickSwitch
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.ui.widget.TrafficRingView
import io.nekohasekai.sagernet.utils.HapticHelper
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.LiveChartView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/** Home dashboard screen: top status pill and Stage C1 cards (Running Mode, Network Speed, Traffic Usage). */
class HomeFragment : ToolbarFragment(R.layout.layout_home) {

    private companion object {
        const val LIVE_POLL_MS = 1000L
        const val CHART_WINDOW_MS = 60_000L
        const val CHART_TICK_MS = 15_000L
    }

    private var statusPillContainer: LinearLayout? = null
    private var statusPillIcon: ImageView? = null
    private var statusPillText: TextView? = null

    private var chipModeVpn: LinearLayout? = null
    private var chipModeVpnText: TextView? = null
    private var chipModeProxy: LinearLayout? = null
    private var chipModeProxyText: TextView? = null
    private var routeRow: LinearLayout? = null
    private var pillRule: LinearLayout? = null
    private var pillRuleIcon: ImageView? = null
    private var pillRuleText: TextView? = null
    private var pillOutboundIcon: ImageView? = null
    private var pillOutboundText: TextView? = null
    private var pillBypassIcon: ImageView? = null
    private var pillBypassText: TextView? = null

    private var speedRateText: TextView? = null
    private var speedChart: LiveChartView? = null

    private var trafficRing: TrafficRingView? = null
    private var trafficUpIcon: ImageView? = null
    private var trafficDownIcon: ImageView? = null
    private var trafficUpValue: TextView? = null
    private var trafficUpUnit: TextView? = null
    private var trafficDownValue: TextView? = null
    private var trafficDownUnit: TextView? = null

    private var currentState: BaseService.State = DataStore.serviceState
    private var latestSpeed: SpeedDisplayData = SpeedDisplayData()
    private var recordedRx: Long = 0L
    private var recordedTx: Long = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        val isAmoled = Theme.isBlackTheme(ctx)
        val screenBg = if (isAmoled) Color.BLACK else ctx.getColorAttr(android.R.attr.colorBackground)
        view.setBackgroundColor(screenBg)

        val appbar = view.findViewById<AppBarLayout>(R.id.appbar)
        appbar?.setBackgroundColor(screenBg)
        toolbar?.visibility = View.GONE
        val header = ExpressiveSettingsHelper.attachFloatingPillHeader(appbar, ctx)?.apply {
            update(
                context = ctx,
                title = getString(R.string.menu_home),
                iconRes = R.drawable.ic_navigation_menu,
                contentDescription = getString(R.string.navigation_open_drawer),
            ) {
                (activity as? MainActivity)?.binding?.drawerLayout?.openDrawer(GravityCompat.START)
            }
        }
        header?.let { attachStatusPill(it.root) }

        bindViews(view)
        styleCards(view)
        setupSpeedChart()
        wireClicks()

        refreshAllCards()
        startLivePollLoop()

        val scroll = view.findViewById<NestedScrollView>(R.id.home_scroll)
        scroll?.clipToPadding = false
        scroll?.let { target ->
            ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
                v.updatePadding(bottom = bars.bottom + dp2px(108))
                insets
            }
            target.setOnScrollChangeListener(
                NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    val dy = scrollY - oldScrollY
                    if (dy != 0) (activity as? MainActivity)?.driveBottomBar(dy)
                },
            )
            ViewCompat.requestApplyInsets(target)
        }
    }

    override fun onResume() {
        super.onResume()
        currentState = DataStore.serviceState
        refreshAllCards()
    }

    override fun onDestroyView() {
        statusPillContainer = null
        statusPillIcon = null
        statusPillText = null
        chipModeVpn = null
        chipModeVpnText = null
        chipModeProxy = null
        chipModeProxyText = null
        routeRow = null
        pillRule = null
        pillRuleIcon = null
        pillRuleText = null
        pillOutboundIcon = null
        pillOutboundText = null
        pillBypassIcon = null
        pillBypassText = null
        speedRateText = null
        speedChart = null
        trafficRing = null
        trafficUpIcon = null
        trafficDownIcon = null
        trafficUpValue = null
        trafficUpUnit = null
        trafficDownValue = null
        trafficDownUnit = null
        super.onDestroyView()
    }

    fun onServiceStateChanged(state: BaseService.State) {
        currentState = state
        if (view == null) return
        updateStatusPill()
        updateSpeedChart(DataStore.serviceState == BaseService.State.Connected)
        if (!state.connected) {
            latestSpeed = SpeedDisplayData()
            updateSpeedReadout()
            updateTrafficTotalsUi()
        }
    }

    fun onSpeedUpdate(stats: SpeedDisplayData) {
        latestSpeed = stats
        if (view == null) return
        updateSpeedReadout()
        updateTrafficTotalsUi()
    }

    fun onTrafficUpdated() {
        if (view == null) return
        refreshTrafficTotals()
    }

    private fun bindViews(view: View) {
        chipModeVpn = view.findViewById(R.id.home_chip_mode_vpn)
        chipModeVpnText = view.findViewById(R.id.home_chip_mode_vpn_text)
        chipModeProxy = view.findViewById(R.id.home_chip_mode_proxy)
        chipModeProxyText = view.findViewById(R.id.home_chip_mode_proxy_text)
        routeRow = view.findViewById(R.id.home_running_route_row)
        pillRule = view.findViewById(R.id.home_mode_pill_rule)
        pillRuleIcon = view.findViewById(R.id.home_mode_pill_rule_icon)
        pillRuleText = view.findViewById(R.id.home_mode_pill_rule_text)
        pillOutboundIcon = view.findViewById(R.id.home_mode_pill_outbound_icon)
        pillOutboundText = view.findViewById(R.id.home_mode_pill_outbound_text)
        pillBypassIcon = view.findViewById(R.id.home_mode_pill_bypass_icon)
        pillBypassText = view.findViewById(R.id.home_mode_pill_bypass_text)

        speedRateText = view.findViewById(R.id.home_speed_rate_text)
        speedChart = view.findViewById(R.id.home_speed_chart)

        trafficRing = view.findViewById(R.id.home_traffic_ring)
        trafficUpIcon = view.findViewById(R.id.home_traffic_up_icon)
        trafficDownIcon = view.findViewById(R.id.home_traffic_down_icon)
        trafficUpValue = view.findViewById(R.id.home_traffic_up_value)
        trafficUpUnit = view.findViewById(R.id.home_traffic_up_unit)
        trafficDownValue = view.findViewById(R.id.home_traffic_down_value)
        trafficDownUnit = view.findViewById(R.id.home_traffic_down_unit)
    }

    private fun styleCards(view: View) {
        view.findViewById<MaterialCardView>(R.id.home_card_running_mode)?.let {
            ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 22f)
        }
        view.findViewById<MaterialCardView>(R.id.home_card_network_speed)?.let {
            ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 22f)
        }
        view.findViewById<MaterialCardView>(R.id.home_card_traffic_usage)?.let {
            ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 22f)
        }

        val ctx = requireContext()
        val (_, downloadColor, uploadColor) = TrafficRingView.resolveSegmentColors(ctx)
        trafficUpIcon?.setColorFilter(uploadColor)
        trafficDownIcon?.setColorFilter(downloadColor)

        routeRow?.let { ExpressiveSettingsHelper.styleExpressiveNestedBox(it, cornerRadiusDp = 14f, strokeAlpha = 150) }
    }

    private fun attachStatusPill(headerRow: LinearLayout) {
        val ctx = headerRow.context
        headerRow.addView(
            Space(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        val pillHeight = dp2px(36)
        val pill = LinearLayout(ctx).apply {
            id = R.id.home_header_status_pill
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = pillHeight
            isClickable = false
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                pillHeight,
            )
            setPadding(dp2px(12), 0, dp2px(13), 0)
        }
        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp2px(15), dp2px(15)).apply {
                marginEnd = dp2px(6)
            }
            contentDescription = null
        }
        val label = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        pill.addView(icon)
        pill.addView(label)
        headerRow.addView(pill)

        statusPillContainer = pill
        statusPillIcon = icon
        statusPillText = label
    }

    private fun setupSpeedChart() {
        val ctx = context ?: return
        val chart = speedChart ?: return
        val rows = listOf(
            LiveChartView.Row(
                key = "proxy",
                label = getString(R.string.live_graph_proxy),
                color = LiveChartView.proxyColor(ctx),
                wash = true,
                fields = intArrayOf(LiveStatsBatch.PROXY_DOWN, LiveStatsBatch.PROXY_UP),
            ),
        )
        val columns = listOf(
            LiveChartView.Column(key = "d", arrow = "↓", dashed = false, wash = true),
            LiveChartView.Column(key = "u", arrow = "↑", dashed = true, wash = false),
        )
        chart.setup(rows, columns, LiveChartView.rateAxis(ctx), compact = true, events = false)
        chart.windowMs = CHART_WINDOW_MS
        chart.tickMs = CHART_TICK_MS
        chart.data = LiveHistory.series
    }

    private fun wireClicks() {
        routeRow?.setOnClickListener {
            val v = it
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.SELECTION) }
            RouteQuickSwitch.show(this) {
                refreshRunningModeCard()
            }
        }
        val cardNetworkSpeed = view?.findViewById<View>(R.id.home_card_network_speed)
        cardNetworkSpeed?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.TAP) }
            (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_live_graphs)
        }
        // Forward taps on the compact sparkline preview straight to the parent card instead of entering chart scrub mode.
        val chart = speedChart
        if (chart != null && cardNetworkSpeed != null) {
            val touchSlop = ViewConfiguration.get(chart.context).scaledTouchSlop
            var downX = 0f
            var downY = 0f
            var moved = false
            chart.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        moved = false
                        cardNetworkSpeed.isPressed = true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > touchSlop) {
                            moved = true
                            cardNetworkSpeed.isPressed = false
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        cardNetworkSpeed.isPressed = false
                        if (!moved) cardNetworkSpeed.performClick()
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        cardNetworkSpeed.isPressed = false
                    }
                }
                true
            }
        }
        view?.findViewById<View>(R.id.home_card_traffic_usage)?.setOnClickListener { v ->
            val ctx = context ?: return@setOnClickListener
            HapticHelper.perform(ctx, v, HapticHelper.HapticKind.TAP)
            startActivity(Intent(ctx, TrafficUsageActivity::class.java))
        }
    }

    private fun refreshAllCards() {
        updateStatusPill()
        refreshRunningModeCard()
        updateSpeedReadout()
        updateSpeedChart(DataStore.serviceState == BaseService.State.Connected)
        refreshTrafficTotals()
    }

    private fun updateStatusPill() {
        val ctx = context ?: return
        val pill = statusPillContainer ?: return
        val icon = statusPillIcon ?: return
        val label = statusPillText ?: return

        val isNight = Theme.usingNightMode(ctx)
        val isAnyAmoled = Theme.isAnyAmoled(ctx) || Theme.isMonochromeBlack(ctx)
        val accent = ExpressiveSettingsHelper.resolveAccentColor(ctx)
        val cardBg = Theme.resolveCardBackground(ctx)
        val strokeBase = Theme.resolveCardStrokeColor(ctx, selected = false)
        val strokeWidth = Theme.resolveCardStrokeWidthPx(ctx, selected = false).toInt().coerceAtLeast(1)
        val primaryInk = ctx.getColorAttr(android.R.attr.textColorPrimary)

        val stateLabel = when (currentState) {
            BaseService.State.Connected -> getString(R.string.home_status_enabled)
            BaseService.State.Connecting -> getString(R.string.connecting)
            BaseService.State.Stopping -> getString(R.string.stopping)
            else -> getString(R.string.home_status_disabled)
        }
        val active = currentState == BaseService.State.Connected
        val transitioning = currentState == BaseService.State.Connecting || currentState == BaseService.State.Stopping

        val fillColor = when {
            active -> if (Theme.isMonochromeBlack(ctx)) {
                ColorUtils.setAlphaComponent(Color.WHITE, 34)
            } else {
                ColorUtils.setAlphaComponent(accent, if (isNight) 44 else 28)
            }
            transitioning -> Theme.resolveNestedBoxBackground(ctx)
            else -> cardBg
        }
        val borderColor = when {
            active -> ColorUtils.setAlphaComponent(accent, if (isAnyAmoled) 150 else 104)
            else -> strokeBase
        }
        val contentInk = when {
            active -> if (Theme.isMonochromeBlack(ctx)) Color.WHITE else accent
            else -> primaryInk
        }

        pill.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(18).toFloat()
            setColor(fillColor)
            setStroke(strokeWidth, borderColor)
        }
        label.text = stateLabel
        label.setTextColor(contentInk)
        val d = ResourcesCompat.getDrawable(resources, R.drawable.ic_power_settings_new_24, null)?.mutate()
        if (d != null) {
            DrawableCompat.setTint(d, contentInk)
            icon.setImageDrawable(d)
        }
    }

    private fun refreshRunningModeCard() {
        val ctx = context ?: return
        val isVpn = DataStore.serviceMode == Key.MODE_VPN
        val isBypass = DataStore.bypass
        val accent = ExpressiveSettingsHelper.resolveAccentColor(ctx)
        val primaryText = ctx.getColorAttr(android.R.attr.textColorPrimary)
        val secondaryText = ctx.getColorAttr(android.R.attr.textColorSecondary)
        val nestedBg = Theme.resolveNestedBoxBackground(ctx)
        val strokeColor = Theme.resolveCardStrokeColor(ctx, selected = false)

        fun styleModeChip(box: LinearLayout?, tv: TextView?, label: String, selected: Boolean) {
            if (box == null || tv == null) return
            val chipFill = if (selected) nestedBg else Color.TRANSPARENT
            val chipStroke = if (selected) ColorUtils.setAlphaComponent(accent, 110) else strokeColor
            box.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(13).toFloat()
                setColor(chipFill)
                setStroke(dp2px(1), chipStroke)
            }
            tv.text = if (selected) "● $label" else "○ $label"
            tv.setTextColor(if (selected) primaryText else secondaryText)
        }

        styleModeChip(chipModeVpn, chipModeVpnText, getString(R.string.service_mode_vpn), isVpn)
        styleModeChip(chipModeProxy, chipModeProxyText, getString(R.string.home_mode_system_proxy), !isVpn)

        viewLifecycleOwner.lifecycleScope.launch {
            val route = withContext(Dispatchers.IO) {
                RouteManager.get(RouteManager.activeRouteId())?.takeIf { !it.is_raw }
                    ?: RouteManager.current()
            }
            val currentCtx = context ?: return@launch
            val routeTitle = route.name.trim().ifEmpty { getString(R.string.home_route_rule) }
            pillRuleText?.text = if (routeTitle.equals(getString(R.string.home_route_rule), ignoreCase = true)) {
                routeTitle
            } else {
                routeTitle
            }

            pillOutboundText?.text = when (route.default_outbound_id) {
                OutboundIds.DIRECT -> getString(R.string.home_route_direct)
                OutboundIds.BLOCK -> getString(R.string.home_route_block)
                else -> getString(R.string.route_proxy)
            }
            pillBypassText?.text = getString(R.string.home_route_bypass)

            val selectedCapsuleColor = Theme.resolveCardBackground(currentCtx)
            val selectedStroke = Theme.resolveCardStrokeColor(currentCtx, selected = false)
            pillRule?.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(11).toFloat()
                setColor(selectedCapsuleColor)
                setStroke(dp2px(1), selectedStroke)
            }
            val inkPrimary = currentCtx.getColorAttr(android.R.attr.textColorPrimary)
            val inkSecondary = currentCtx.getColorAttr(android.R.attr.textColorSecondary)
            pillRuleText?.setTextColor(inkPrimary)
            pillRuleIcon?.setColorFilter(ExpressiveSettingsHelper.resolveAccentColor(currentCtx))
            pillOutboundIcon?.setColorFilter(inkSecondary)
            pillOutboundText?.setTextColor(inkSecondary)
            pillBypassIcon?.setColorFilter(if (isBypass) ExpressiveSettingsHelper.resolveAccentColor(currentCtx) else inkSecondary)
            pillBypassText?.setTextColor(if (isBypass) inkPrimary else inkSecondary)
            pillBypassText?.alpha = if (isBypass) 1f else 0.55f
            pillBypassIcon?.alpha = if (isBypass) 1f else 0.55f
        }
    }

    private fun refreshTrafficTotals() {
        viewLifecycleOwner.lifecycleScope.launch {
            val (rxSum, txSum) = withContext(Dispatchers.IO) {
                val all = SagerDatabase.proxyDao.getAll()
                var dl = 0L
                var ul = 0L
                for (p in all) {
                    dl += p.rx.coerceAtLeast(0L)
                    ul += p.tx.coerceAtLeast(0L)
                }
                dl to ul
            }
            if (view == null) return@launch
            recordedRx = rxSum
            recordedTx = txSum
            updateTrafficTotalsUi()
        }
    }

    private fun updateSpeedReadout() {
        val ctx = context ?: return
        val upRate = latestSpeed.txRateProxy.coerceAtLeast(0L) + latestSpeed.txRateDirect.coerceAtLeast(0L)
        val downRate = latestSpeed.rxRateProxy.coerceAtLeast(0L) + latestSpeed.rxRateDirect.coerceAtLeast(0L)
        val upStr = Formatter.formatFileSize(ctx, upRate)
        val downStr = Formatter.formatFileSize(ctx, downRate)
        speedRateText?.text = "↑ $upStr/s   ↓ $downStr/s"
    }

    private fun updateTrafficTotalsUi() {
        val ctx = context ?: return
        val sessionDl = if (DataStore.serviceState.connected) latestSpeed.rxTotal.coerceAtLeast(0L) else 0L
        val sessionUl = if (DataStore.serviceState.connected) latestSpeed.txTotal.coerceAtLeast(0L) else 0L
        val totalDl = maxOf(recordedRx, sessionDl)
        val totalUl = maxOf(recordedTx, sessionUl)

        trafficRing?.setTraffic(totalDl, totalUl)

        val (ulVal, ulUnit) = splitFormattedSize(ctx, totalUl)
        val (dlVal, dlUnit) = splitFormattedSize(ctx, totalDl)
        trafficUpValue?.text = ulVal
        trafficUpUnit?.text = ulUnit
        trafficDownValue?.text = dlVal
        trafficDownUnit?.text = dlUnit
    }

    private fun splitFormattedSize(context: android.content.Context, bytes: Long): Pair<String, String> {
        val formatted = Formatter.formatFileSize(context, bytes.coerceAtLeast(0L)).trim()
        val idx = formatted.lastIndexOf(' ')
        return if (idx in 1 until formatted.lastIndex) {
            formatted.substring(0, idx).trim() to formatted.substring(idx + 1).trim()
        } else {
            formatted to ""
        }
    }

    private fun updateSpeedChart(running: Boolean) {
        val chart = speedChart ?: return
        chart.running = running
        chart.invalidate()
    }

    private fun startLivePollLoop() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val service = (activity as? MainActivity)?.connection?.service
                    if (service != null) {
                        val batch = withContext(Dispatchers.IO) {
                            try {
                                service.liveStats(LiveHistory.session, LiveHistory.serial)
                            } catch (_: RemoteException) {
                                null
                            } catch (e: Exception) {
                                Logs.w(e)
                                null
                            }
                        }
                        if (batch != null) LiveHistory.merge(batch)
                    }
                    updateSpeedChart(DataStore.serviceState == BaseService.State.Connected)
                    delay(LIVE_POLL_MS)
                }
            }
        }
    }
}
