package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.RemoteException
import android.os.SystemClock
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
import androidx.core.view.isVisible
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
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.RouteManager
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.hotspotshare.HotspotShareStateStore
import io.nekohasekai.sagernet.hotspotshare.ShareMethod
import io.nekohasekai.sagernet.hotspotshare.ShareState
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
import kotlin.math.roundToInt

/** Compact Home dashboard screen: Status banner, C1 (Speed, Traffic, Routing), C2 (Network, Core Runtime, Hotspot Share). */
class HomeFragment : ToolbarFragment(R.layout.layout_home) {

    private companion object {
        const val LIVE_POLL_MS = 1000L
        const val CHART_WINDOW_MS = 60_000L
        const val CHART_TICK_MS = 15_000L
        const val STALE_MS = 3000L
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

    private var cardNetworkEgress: MaterialCardView? = null
    private var networkLatencyText: TextView? = null
    private var networkIpText: TextView? = null
    private var networkMetaText: TextView? = null

    private var cardCoreRuntime: MaterialCardView? = null
    private var coreConnectionsText: TextView? = null
    private var coreMemoryText: TextView? = null
    private var bytesAxis: LiveChartView.Axis? = null

    private var cardHotspotShare: MaterialCardView? = null
    private var hotspotIconBox: FrameLayout? = null
    private var hotspotIcon: ImageView? = null
    private var hotspotSubtitle: TextView? = null
    private var hotspotBadge: TextView? = null

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

        bytesAxis = LiveChartView.bytesAxis(ctx)
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
        cardNetworkEgress = null
        networkLatencyText = null
        networkIpText = null
        networkMetaText = null
        cardCoreRuntime = null
        coreConnectionsText = null
        coreMemoryText = null
        bytesAxis = null
        cardHotspotShare = null
        hotspotIconBox = null
        hotspotIcon = null
        hotspotSubtitle = null
        hotspotBadge = null
        super.onDestroyView()
    }

    fun onServiceStateChanged(state: BaseService.State) {
        currentState = state
        if (view == null) return
        updateStatusBanner()
        refreshNetworkEgressCard()
        updateSpeedChart(DataStore.serviceState == BaseService.State.Connected)
        updateCoreRuntimeUi()
        refreshHotspotShareCard()
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

    fun onProfileSelected() {
        if (view == null) return
        updateStatusBanner()
        refreshNetworkEgressCard()
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

        cardNetworkEgress = view.findViewById(R.id.home_card_network_egress)
        networkLatencyText = view.findViewById(R.id.home_network_latency_text)
        networkIpText = view.findViewById(R.id.home_network_ip_text)
        networkMetaText = view.findViewById(R.id.home_network_meta_text)

        cardCoreRuntime = view.findViewById(R.id.home_card_core_runtime)
        coreConnectionsText = view.findViewById(R.id.home_core_connections_text)
        coreMemoryText = view.findViewById(R.id.home_core_memory_text)

        cardHotspotShare = view.findViewById(R.id.home_card_hotspot_share)
        hotspotIconBox = view.findViewById(R.id.home_hotspot_icon_box)
        hotspotIcon = view.findViewById(R.id.home_hotspot_icon)
        hotspotSubtitle = view.findViewById(R.id.home_hotspot_subtitle)
        hotspotBadge = view.findViewById(R.id.home_hotspot_badge)
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
        cardNetworkEgress?.let { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 18f) }
        cardCoreRuntime?.let { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 18f) }
        cardHotspotShare?.let { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 18f) }

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
        cardNetworkEgress?.setOnClickListener { v ->
            val ctx = context ?: return@setOnClickListener
            HapticHelper.perform(ctx, v, HapticHelper.HapticKind.TAP)
            startActivity(Intent(ctx, IpLookupActivity::class.java))
        }
        cardCoreRuntime?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.TAP) }
            (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_live_graphs)
        }
        cardHotspotShare?.setOnClickListener { v ->
            val ctx = context ?: return@setOnClickListener
            HapticHelper.perform(ctx, v, HapticHelper.HapticKind.TAP)
            startActivity(Intent(ctx, LanSharingActivity::class.java))
        }
    }

    private fun refreshAllCards() {
        updateStatusBanner()
        refreshRunningModeCard()
        refreshNetworkEgressCard()
        updateSpeedReadout()
        updateSpeedChart(DataStore.serviceState == BaseService.State.Connected)
        updateCoreRuntimeUi()
        refreshTrafficTotals()
        refreshHotspotShareCard()
    }

    private fun activeTargetProfileId(): Long = if (currentState.started) {
        DataStore.currentProfile.takeIf { it > 0L } ?: DataStore.selectedProxy
    } else {
        DataStore.selectedProxy
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

        val targetId = activeTargetProfileId()
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

    private fun refreshNetworkEgressCard() {
        val targetId = activeTargetProfileId()
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val entity = if (targetId > 0L) {
                    SagerDatabase.proxyDao.getById(targetId)
                } else {
                    SagerDatabase.proxyDao.getAll().firstOrNull()
                }
                val group = entity?.groupId?.takeIf { it > 0L }?.let { SagerDatabase.groupDao.getById(it) }
                entity to group
            }
            val ctx = context ?: return@launch
            val entity = loaded.first
            val group = loaded.second
            val secondary = ctx.getColorAttr(android.R.attr.textColorSecondary)

            if (entity == null) {
                networkLatencyText?.isVisible = false
                networkIpText?.text = getString(R.string.not_connected)
                networkMetaText?.text = getString(R.string.profile_empty)
                return@launch
            }

            val country = entity.testCountry?.trim().orEmpty()
            val flagPrefix = if (country.length == 2 && country.all { it in 'A'..'Z' || it in 'a'..'z' }) {
                "${ProxyEntity.countryFlag(country)} "
            } else if (country.isNotEmpty()) {
                "$country "
            } else {
                ""
            }
            val endpoint = entity.ipOut?.trim()?.takeIf { it.isNotEmpty() }
                ?: entity.displayAddress().trim().takeIf { it.isNotEmpty() }
                ?: entity.displayName()
            networkIpText?.text = "$flagPrefix$endpoint"

            val latency = entity.latency
            if (latency > 0) {
                networkLatencyText?.isVisible = true
                networkLatencyText?.text = "$latency ms"
                networkLatencyText?.setTextColor(entity.latencyColor().takeIf { it != 0 } ?: secondary)
            } else if (latency < 0) {
                networkLatencyText?.isVisible = true
                networkLatencyText?.text = getString(R.string.unavailable)
                networkLatencyText?.setTextColor(entity.latencyColor().takeIf { it != 0 } ?: secondary)
            } else {
                networkLatencyText?.isVisible = false
            }

            val dlSpeed = entity.dlSpeed?.trim()?.takeIf { it.isNotEmpty() && it != ProxyEntity.SPEED_NA }
            val detail = dlSpeed?.let { "↓$it" }
                ?: group?.displayName()?.takeIf { it.isNotBlank() }
            networkMetaText?.text = if (detail != null) {
                "${entity.displayType()} · $detail"
            } else {
                entity.displayType()
            }
        }
    }

    private fun refreshHotspotShareCard() {
        val ctx = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val (state, port) = withContext(Dispatchers.IO) {
                HotspotShareStateStore.snapshot() to DataStore.inboundSocksPort
            }
            val currentCtx = context ?: return@launch
            val iconBox = hotspotIconBox ?: return@launch
            val icon = hotspotIcon ?: return@launch
            val subtitleTv = hotspotSubtitle ?: return@launch
            val badgeTv = hotspotBadge ?: return@launch

            val isNight = Theme.usingNightMode(currentCtx)
            val isAnyAmoled = Theme.isAnyAmoled(currentCtx) || Theme.isMonochromeBlack(currentCtx)
            val accent = ExpressiveSettingsHelper.resolveAccentColor(currentCtx)
            val primaryInk = currentCtx.getColorAttr(android.R.attr.textColorPrimary)
            val secondaryInk = currentCtx.getColorAttr(android.R.attr.textColorSecondary)

            val isActive = state is ShareState.Active
            val isStarting = state is ShareState.Starting
            val methodLabel = when (state) {
                is ShareState.Active -> when (state.method) {
                    ShareMethod.SHIZUKU -> "Shizuku"
                    ShareMethod.ROOT -> "Root"
                    ShareMethod.LAN_PROXY -> "LAN Proxy"
                }
                else -> "SOCKS5 / HTTP"
            }
            subtitleTv.text = "$methodLabel · :$port"
            badgeTv.text = when {
                isActive -> getString(R.string.home_hotspot_active)
                isStarting -> getString(R.string.connecting)
                else -> getString(R.string.home_hotspot_standby)
            }

            val badgeFill = when {
                isActive -> if (Theme.isMonochromeBlack(currentCtx)) {
                    ColorUtils.setAlphaComponent(Color.WHITE, 36)
                } else {
                    ColorUtils.setAlphaComponent(accent, if (isNight) 44 else 28)
                }
                else -> Theme.resolveNestedBoxBackground(currentCtx)
            }
            val badgeInk = when {
                isActive -> if (Theme.isMonochromeBlack(currentCtx)) Color.WHITE else accent
                else -> secondaryInk
            }

            iconBox.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(badgeFill)
                if (isActive || isAnyAmoled) {
                    setStroke(dp2px(1), ColorUtils.setAlphaComponent(if (isActive) badgeInk else primaryInk, if (isActive) 120 else 48))
                }
            }
            val d = ResourcesCompat.getDrawable(resources, R.drawable.ic_hardware_router, null)?.mutate()
            if (d != null) {
                DrawableCompat.setTint(d, if (isActive) badgeInk else primaryInk)
                icon.setImageDrawable(d)
            }

            badgeTv.setTextColor(badgeInk)
            badgeTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(8).toFloat()
                setColor(badgeFill)
                if (isActive) {
                    setStroke(dp2px(1), ColorUtils.setAlphaComponent(badgeInk, if (isAnyAmoled) 150 else 96))
                }
            }
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

    private fun updateCoreRuntimeUi() {
        val connTv = coreConnectionsText ?: return
        val memTv = coreMemoryText ?: return
        val series = LiveHistory.series
        val connected = DataStore.serviceState == BaseService.State.Connected
        val last = if (connected && series.size > 0 && SystemClock.elapsedRealtime() - series.latestTime <= STALE_MS) {
            series.size - 1
        } else {
            -1
        }
        if (last < 0) {
            connTv.text = "0"
            memTv.text = "0 B"
            return
        }
        val conns = series.value(LiveStatsBatch.CONNECTIONS, last)
        connTv.text = if (conns.isNaN()) "0" else conns.roundToInt().coerceAtLeast(0).toString()

        val mem = series.value(LiveStatsBatch.MEMORY, last)
        memTv.text = if (mem.isNaN()) "0 B" else (bytesAxis?.format(mem.toDouble()) ?: "0 B")
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
                    updateCoreRuntimeUi()
                    delay(LIVE_POLL_MS)
                }
            }
        }
    }
}
