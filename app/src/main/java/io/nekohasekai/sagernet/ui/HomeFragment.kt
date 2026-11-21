package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.RemoteException
import android.text.format.Formatter
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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

/** Compact Home dashboard screen: top status banner, Network Speed, Traffic Usage, and Routing cards. */
class HomeFragment : ToolbarFragment(R.layout.layout_home) {

    private companion object {
        const val LIVE_POLL_MS = 1000L
        const val CHART_WINDOW_MS = 60_000L
        const val CHART_TICK_MS = 15_000L
    }

    private var statusBannerCard: MaterialCardView? = null
    private var statusIconBox: FrameLayout? = null
    private var statusIcon: ImageView? = null
    private var statusTitle: TextView? = null
    private var statusSubtitle: TextView? = null
    private var statusModeTag: TextView? = null

    private var speedRateText: TextView? = null
    private var speedChart: LiveChartView? = null

    private var trafficRing: TrafficRingView? = null
    private var trafficUpIcon: ImageView? = null
    private var trafficDownIcon: ImageView? = null
    private var trafficUpValue: TextView? = null
    private var trafficUpUnit: TextView? = null
    private var trafficDownValue: TextView? = null
    private var trafficDownUnit: TextView? = null

    private var cardRouting: MaterialCardView? = null
    private var pillRule: LinearLayout? = null
    private var pillRuleIcon: ImageView? = null
    private var pillRuleText: TextView? = null
    private var routeSummaryText: TextView? = null

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
        ExpressiveSettingsHelper.attachFloatingPillHeader(appbar, ctx)?.update(
            context = ctx,
            title = getString(R.string.menu_home),
            iconRes = R.drawable.ic_navigation_menu,
            contentDescription = getString(R.string.navigation_open_drawer),
        ) {
            (activity as? MainActivity)?.binding?.drawerLayout?.openDrawer(GravityCompat.START)
        }

        bindViews(view)
        styleCards(view)
        setupSpeedChart()
        wireClicks(view)

        refreshAllCards()
        startLivePollLoop()

        val scroll = view.findViewById<NestedScrollView>(R.id.home_scroll)
        scroll?.clipToPadding = false
        scroll?.let { target ->
            ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
                v.updatePadding(bottom = bars.bottom + dp2px(100))
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
        statusBannerCard = null
        statusIconBox = null
        statusIcon = null
        statusTitle = null
        statusSubtitle = null
        statusModeTag = null
        speedRateText = null
        speedChart = null
        trafficRing = null
        trafficUpIcon = null
        trafficDownIcon = null
        trafficUpValue = null
        trafficUpUnit = null
        trafficDownValue = null
        trafficDownUnit = null
        cardRouting = null
        pillRule = null
        pillRuleIcon = null
        pillRuleText = null
        routeSummaryText = null
        super.onDestroyView()
    }

    fun onServiceStateChanged(state: BaseService.State) {
        currentState = state
        if (view == null) return
        updateStatusBanner()
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
        statusBannerCard = view.findViewById(R.id.home_card_status_banner)
        statusIconBox = view.findViewById(R.id.home_status_icon_box)
        statusIcon = view.findViewById(R.id.home_status_icon)
        statusTitle = view.findViewById(R.id.home_status_title)
        statusSubtitle = view.findViewById(R.id.home_status_subtitle)
        statusModeTag = view.findViewById(R.id.home_status_mode_tag)

        speedRateText = view.findViewById(R.id.home_speed_rate_text)
        speedChart = view.findViewById(R.id.home_speed_chart)

        trafficRing = view.findViewById(R.id.home_traffic_ring)
        trafficUpIcon = view.findViewById(R.id.home_traffic_up_icon)
        trafficDownIcon = view.findViewById(R.id.home_traffic_down_icon)
        trafficUpValue = view.findViewById(R.id.home_traffic_up_value)
        trafficUpUnit = view.findViewById(R.id.home_traffic_up_unit)
        trafficDownValue = view.findViewById(R.id.home_traffic_down_value)
        trafficDownUnit = view.findViewById(R.id.home_traffic_down_unit)

        cardRouting = view.findViewById(R.id.home_card_running_mode)
        pillRule = view.findViewById(R.id.home_mode_pill_rule)
        pillRuleIcon = view.findViewById(R.id.home_mode_pill_rule_icon)
        pillRuleText = view.findViewById(R.id.home_mode_pill_rule_text)
        routeSummaryText = view.findViewById(R.id.home_route_summary_text)
    }

    private fun styleCards(view: View) {
        statusBannerCard?.let { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 18f) }
        view.findViewById<MaterialCardView>(R.id.home_card_network_speed)?.let {
            ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 18f)
        }
        view.findViewById<MaterialCardView>(R.id.home_card_traffic_usage)?.let {
            ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 18f)
        }
        cardRouting?.let { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 18f) }

        val ctx = requireContext()
        val (_, downloadColor, uploadColor) = TrafficRingView.resolveSegmentColors(ctx)
        trafficUpIcon?.setColorFilter(uploadColor)
        trafficDownIcon?.setColorFilter(downloadColor)

        pillRule?.let { ExpressiveSettingsHelper.styleExpressiveNestedBox(it, cornerRadiusDp = 11f, strokeAlpha = 140) }
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

    private fun wireClicks(view: View) {
        val cardNetworkSpeed = view.findViewById<View>(R.id.home_card_network_speed)
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
        view.findViewById<View>(R.id.home_card_traffic_usage)?.setOnClickListener { v ->
            val ctx = context ?: return@setOnClickListener
            HapticHelper.perform(ctx, v, HapticHelper.HapticKind.TAP)
            startActivity(Intent(ctx, TrafficUsageActivity::class.java))
        }
        cardRouting?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.SELECTION) }
            RouteQuickSwitch.show(this) {
                refreshRunningModeCard()
            }
        }
    }

    private fun refreshAllCards() {
        updateStatusBanner()
        refreshRunningModeCard()
        updateSpeedReadout()
        updateSpeedChart(DataStore.serviceState == BaseService.State.Connected)
        refreshTrafficTotals()
    }

    private fun updateStatusBanner() {
        val ctx = context ?: return
        val card = statusBannerCard ?: return
        val iconBox = statusIconBox ?: return
        val icon = statusIcon ?: return
        val titleTv = statusTitle ?: return
        val subtitleTv = statusSubtitle ?: return
        val modeTv = statusModeTag ?: return

        val isNight = Theme.usingNightMode(ctx)
        val isAnyAmoled = Theme.isAnyAmoled(ctx) || Theme.isMonochromeBlack(ctx)
        val accent = ExpressiveSettingsHelper.resolveAccentColor(ctx)
        val cardBg = Theme.resolveCardBackground(ctx)
        val strokeBase = Theme.resolveCardStrokeColor(ctx, selected = false)
        val strokeWidth = Theme.resolveCardStrokeWidthPx(ctx, selected = false).toInt().coerceAtLeast(1)
        val primaryInk = ctx.getColorAttr(android.R.attr.textColorPrimary)
        val secondaryInk = ctx.getColorAttr(android.R.attr.textColorSecondary)

        val stateLabel = when (currentState) {
            BaseService.State.Connected -> getString(R.string.home_status_enabled)
            BaseService.State.Connecting -> getString(R.string.connecting)
            BaseService.State.Stopping -> getString(R.string.stopping)
            else -> getString(R.string.home_status_disabled)
        }
        val active = currentState == BaseService.State.Connected
        val transitioning = currentState == BaseService.State.Connecting || currentState == BaseService.State.Stopping

        val bannerFill = when {
            active && isAnyAmoled -> ColorUtils.blendARGB(cardBg, accent, 0.10f)
            active -> ColorUtils.blendARGB(cardBg, accent, if (isNight) 0.14f else 0.08f)
            else -> cardBg
        }
        val bannerStroke = when {
            active -> ColorUtils.setAlphaComponent(accent, if (isAnyAmoled) 150 else 104)
            else -> strokeBase
        }
        card.setCardBackgroundColor(bannerFill)
        card.strokeColor = bannerStroke
        card.strokeWidth = strokeWidth

        val badgeFill = when {
            active -> if (Theme.isMonochromeBlack(ctx)) {
                ColorUtils.setAlphaComponent(Color.WHITE, 36)
            } else {
                ColorUtils.setAlphaComponent(accent, if (isNight) 44 else 28)
            }
            transitioning -> Theme.resolveNestedBoxBackground(ctx)
            else -> Theme.resolveNestedBoxBackground(ctx)
        }
        val iconInk = when {
            active -> if (Theme.isMonochromeBlack(ctx)) Color.WHITE else accent
            else -> primaryInk
        }

        iconBox.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(badgeFill)
            if (active || isAnyAmoled) {
                setStroke(dp2px(1), ColorUtils.setAlphaComponent(iconInk, if (active) 120 else 48))
            }
        }
        val d = ResourcesCompat.getDrawable(resources, R.drawable.ic_power_settings_new_24, null)?.mutate()
        if (d != null) {
            DrawableCompat.setTint(d, iconInk)
            icon.setImageDrawable(d)
        }

        titleTv.text = stateLabel
        titleTv.setTextColor(if (active) iconInk else primaryInk)

        val modeLabel = if (DataStore.serviceMode == Key.MODE_VPN) {
            getString(R.string.service_mode_vpn)
        } else {
            getString(R.string.route_proxy)
        }
        modeTv.text = modeLabel
        modeTv.setTextColor(secondaryInk)
        modeTv.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(8).toFloat()
            setColor(Theme.resolveNestedBoxBackground(ctx))
        }

        val targetId = if (currentState.started) {
            DataStore.currentProfile.takeIf { it > 0L } ?: DataStore.selectedProxy
        } else {
            DataStore.selectedProxy
        }
        if (targetId <= 0L) {
            subtitleTv.text = getString(R.string.not_connected)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) {
                SagerDatabase.proxyDao.getById(targetId)?.displayName()
            }
            if (view == null) return@launch
            subtitleTv.text = name?.takeIf { it.isNotBlank() } ?: getString(R.string.not_connected)
        }
    }

    private fun refreshRunningModeCard() {
        val ctx = context ?: return
        val isBypass = DataStore.bypass
        val accent = ExpressiveSettingsHelper.resolveAccentColor(ctx)

        viewLifecycleOwner.lifecycleScope.launch {
            val route = withContext(Dispatchers.IO) {
                RouteManager.get(RouteManager.activeRouteId())?.takeIf { !it.is_raw }
                    ?: RouteManager.current()
            }
            val currentCtx = context ?: return@launch
            val routeTitle = route.name.trim().ifEmpty { getString(R.string.home_route_rule) }
            pillRuleText?.text = routeTitle

            val outboundLabel = when (route.default_outbound_id) {
                OutboundIds.DIRECT -> getString(R.string.home_route_direct)
                OutboundIds.BLOCK -> getString(R.string.home_route_block)
                else -> getString(R.string.route_proxy)
            }
            routeSummaryText?.text = if (isBypass) {
                "$outboundLabel · ${getString(R.string.home_route_bypass)}"
            } else {
                outboundLabel
            }
            pillRuleIcon?.setColorFilter(accent)
            pillRuleText?.setTextColor(currentCtx.getColorAttr(android.R.attr.textColorPrimary))
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
