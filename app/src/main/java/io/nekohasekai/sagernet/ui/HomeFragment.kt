package io.nekohasekai.sagernet.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
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
import io.nekohasekai.sagernet.hotspotshare.SharePreference
import io.nekohasekai.sagernet.hotspotshare.ShareState
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.route.OutboundIds
import io.nekohasekai.sagernet.ui.profiles.SubscriptionInfoCard
import io.nekohasekai.sagernet.ui.route.RouteQuickSwitch
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.ui.settings.ExpressiveShape
import io.nekohasekai.sagernet.ui.test.TestFormat
import io.nekohasekai.sagernet.ui.widget.SquareMaterialCardView
import io.nekohasekai.sagernet.ui.widget.TrafficRingView
import io.nekohasekai.sagernet.utils.HapticHelper
import io.nekohasekai.sagernet.utils.HotspotKind
import io.nekohasekai.sagernet.utils.InterfaceIp
import io.nekohasekai.sagernet.utils.InterfaceKind
import io.nekohasekai.sagernet.utils.NetworkInterfaceInfo
import io.nekohasekai.sagernet.utils.NetworkInterfacesRepository
import io.nekohasekai.sagernet.utils.NetworkInterfacesSnapshot
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.ExitIp
import io.nekohasekai.sagernet.widget.LiveChartView
import io.nekohasekai.sagernet.widget.StatsBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Compact Bento Home dashboard: Interactive Status Hero + URL test strip, Network Speed (2x4),
 * Traffic Usage + Network (2x2), 1:1 square CPU & RAM sparklines, compact Route + Local IP,
 * upgraded Hotspot & Proxy Share, and Subscription + Online Portal.
 */
class HomeFragment : ToolbarFragment(R.layout.layout_home) {

    private companion object {
        const val LIVE_POLL_MS = 1000L
        const val CHART_WINDOW_MS = 60_000L
        const val CHART_TICK_MS = 15_000L
        const val STALE_MS = 3000L
    }

    private var statusBannerCard: MaterialCardView? = null
    private var statusPowerBtn: FrameLayout? = null
    private var statusIconBox: FrameLayout? = null
    private var statusIcon: ImageView? = null
    private var statusProgress: CircularProgressIndicator? = null
    private var statusDot: View? = null
    private var statusTitle: TextView? = null
    private var statusTypeChip: TextView? = null
    private var statusSubtitle: TextView? = null
    private var statusModeTag: TextView? = null
    private var statusTestBar: LinearLayout? = null
    private var statusTestIcon: ImageView? = null
    private var statusTestProgress: CircularProgressIndicator? = null
    private var statusTestText: TextView? = null
    private var statusExitCountry: TextView? = null

    private var cardNetworkSpeed: MaterialCardView? = null
    private var speedRateText: TextView? = null
    private var speedDotVpn: View? = null
    private var speedDotDirect: View? = null
    private var speedChart: LiveChartView? = null

    private var cardTrafficUsage: MaterialCardView? = null
    private var trafficRing: TrafficRingView? = null
    private var trafficUpIcon: ImageView? = null
    private var trafficDownIcon: ImageView? = null
    private var trafficUpValue: TextView? = null
    private var trafficUpUnit: TextView? = null
    private var trafficDownValue: TextView? = null
    private var trafficDownUnit: TextView? = null
    private var trafficTotalText: TextView? = null

    private var cardNetworkEgress: MaterialCardView? = null
    private var networkLatencyText: TextView? = null
    private var networkIpText: TextView? = null
    private var networkMetaText: TextView? = null

    private var cardCpuChart: SquareMaterialCardView? = null
    private var cpuValueText: TextView? = null
    private var cpuChart: LiveChartView? = null

    private var cardRamChart: SquareMaterialCardView? = null
    private var ramValueText: TextView? = null
    private var ramChart: LiveChartView? = null

    private var cardRouting: MaterialCardView? = null
    private var routeIcon: ImageView? = null
    private var pillRuleText: TextView? = null
    private var routeSummaryText: TextView? = null

    private var cardLocalIp: MaterialCardView? = null
    private var localIpIfaceText: TextView? = null
    private var localIpValueText: TextView? = null
    private var localIpMetaText: TextView? = null

    private var cardHotspotShare: MaterialCardView? = null
    private var hotspotIcon: ImageView? = null
    private var hotspotSubtitle: TextView? = null
    private var hotspotBadge: TextView? = null
    private var hotspotChevron: ImageView? = null
    private var hotspotChipMode: LinearLayout? = null
    private var hotspotChipModeValue: TextView? = null
    private var hotspotChipPort: LinearLayout? = null
    private var hotspotChipPortValue: TextView? = null
    private var hotspotChipLan: LinearLayout? = null
    private var hotspotChipLanValue: TextView? = null

    private var cardSubscription: MaterialCardView? = null
    private var subTitleText: TextView? = null
    private var subExpiryText: TextView? = null
    private var subPortalBtn: MaterialButton? = null
    private var subProgressBar: LinearProgressIndicator? = null
    private var subQuotaText: TextView? = null
    private var subRemainingText: TextView? = null

    private val percentAxis = LiveChartView.percentAxis()
    private var bytesAxis: LiveChartView.Axis? = null
    private var portalUri: Uri? = null
    private var latestConnections: Int = 0
    private var connectionTestJob: Job? = null
    private var statusProfileLoadJob: Job? = null
    private var hasTestStatus: Boolean = false
    private var lastTestStatusText: CharSequence? = null
    private val exitIpListener: () -> Unit = { updateExitCountryUi() }

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
        styleCards(ctx)
        setupCharts(ctx)
        wireClicks()
        ExitIp.listener = exitIpListener

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
        ExitIp.listener = exitIpListener
        refreshAllCards()
    }

    override fun onDestroyView() {
        connectionTestJob?.cancel()
        connectionTestJob = null
        statusProfileLoadJob?.cancel()
        statusProfileLoadJob = null
        if (ExitIp.listener === exitIpListener) ExitIp.listener = null
        hasTestStatus = false
        lastTestStatusText = null

        statusBannerCard = null
        statusPowerBtn = null
        statusIconBox = null
        statusIcon = null
        statusProgress = null
        statusDot = null
        statusTitle = null
        statusTypeChip = null
        statusSubtitle = null
        statusModeTag = null
        statusTestBar = null
        statusTestIcon = null
        statusTestProgress = null
        statusTestText = null
        statusExitCountry = null
        cardNetworkSpeed = null
        speedRateText = null
        speedDotVpn = null
        speedDotDirect = null
        speedChart = null
        cardTrafficUsage = null
        trafficRing = null
        trafficUpIcon = null
        trafficDownIcon = null
        trafficUpValue = null
        trafficUpUnit = null
        trafficDownValue = null
        trafficDownUnit = null
        trafficTotalText = null
        cardNetworkEgress = null
        networkLatencyText = null
        networkIpText = null
        networkMetaText = null
        cardCpuChart = null
        cpuValueText = null
        cpuChart = null
        cardRamChart = null
        ramValueText = null
        ramChart = null
        cardRouting = null
        routeIcon = null
        pillRuleText = null
        routeSummaryText = null
        cardLocalIp = null
        localIpIfaceText = null
        localIpValueText = null
        localIpMetaText = null
        cardHotspotShare = null
        hotspotIcon = null
        hotspotSubtitle = null
        hotspotBadge = null
        hotspotChevron = null
        hotspotChipMode = null
        hotspotChipModeValue = null
        hotspotChipPort = null
        hotspotChipPortValue = null
        hotspotChipLan = null
        hotspotChipLanValue = null
        cardSubscription = null
        subTitleText = null
        subExpiryText = null
        subPortalBtn = null
        subProgressBar = null
        subQuotaText = null
        subRemainingText = null
        bytesAxis = null
        portalUri = null
        super.onDestroyView()
    }

    fun onServiceStateChanged(state: BaseService.State) {
        val wasConnected = currentState == BaseService.State.Connected
        currentState = state
        if (state == BaseService.State.Connected) {
            ExitIp.ensure()
        } else {
            connectionTestJob?.cancel()
            connectionTestJob = null
            hasTestStatus = false
            lastTestStatusText = null
            ExitIp.clear()
        }
        if (view == null) return
        val running = DataStore.serviceState == BaseService.State.Connected
        if (!state.connected) latestConnections = 0
        if (state == BaseService.State.Connected && !wasConnected && !hasTestStatus) {
            lastTestStatusText = getString(R.string.vpn_connected)
        }
        updateStatusBanner()
        updateTestBarUi()
        updateExitCountryUi()
        refreshNetworkEgressCard()
        refreshLocalIpCard()
        updateSpeedChart(running)
        updateCpuRamUi(running)
        refreshHotspotShareCard()
        refreshSubscriptionCard()
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
        refreshSubscriptionCard()
    }

    private fun bindViews(view: View) {
        statusBannerCard = view.findViewById(R.id.home_card_status_banner)
        statusPowerBtn = view.findViewById(R.id.home_status_power_btn)
        statusIconBox = view.findViewById(R.id.home_status_icon_box)
        statusIcon = view.findViewById(R.id.home_status_icon)
        statusProgress = view.findViewById(R.id.home_status_progress)
        statusDot = view.findViewById(R.id.home_status_dot)
        statusTitle = view.findViewById(R.id.home_status_title)
        statusTypeChip = view.findViewById(R.id.home_status_type_chip)
        statusSubtitle = view.findViewById(R.id.home_status_subtitle)
        statusModeTag = view.findViewById(R.id.home_status_mode_tag)
        statusTestBar = view.findViewById(R.id.home_status_test_bar)
        statusTestIcon = view.findViewById(R.id.home_status_test_icon)
        statusTestProgress = view.findViewById(R.id.home_status_test_progress)
        statusTestText = view.findViewById(R.id.home_status_test_text)
        statusExitCountry = view.findViewById(R.id.home_status_exit_country)

        cardNetworkSpeed = view.findViewById(R.id.home_card_network_speed)
        speedRateText = view.findViewById(R.id.home_speed_rate_text)
        speedDotVpn = view.findViewById(R.id.home_speed_dot_vpn)
        speedDotDirect = view.findViewById(R.id.home_speed_dot_direct)
        speedChart = view.findViewById(R.id.home_speed_chart)

        cardTrafficUsage = view.findViewById(R.id.home_card_traffic_usage)
        trafficRing = view.findViewById(R.id.home_traffic_ring)
        trafficUpIcon = view.findViewById(R.id.home_traffic_up_icon)
        trafficDownIcon = view.findViewById(R.id.home_traffic_down_icon)
        trafficUpValue = view.findViewById(R.id.home_traffic_up_value)
        trafficUpUnit = view.findViewById(R.id.home_traffic_up_unit)
        trafficDownValue = view.findViewById(R.id.home_traffic_down_value)
        trafficDownUnit = view.findViewById(R.id.home_traffic_down_unit)
        trafficTotalText = view.findViewById(R.id.home_traffic_total_text)

        cardNetworkEgress = view.findViewById(R.id.home_card_network_egress)
        networkLatencyText = view.findViewById(R.id.home_network_latency_text)
        networkIpText = view.findViewById(R.id.home_network_ip_text)
        networkMetaText = view.findViewById(R.id.home_network_meta_text)

        cardCpuChart = view.findViewById(R.id.home_card_cpu_chart)
        cpuValueText = view.findViewById(R.id.home_cpu_value_text)
        cpuChart = view.findViewById(R.id.home_cpu_chart)

        cardRamChart = view.findViewById(R.id.home_card_ram_chart)
        ramValueText = view.findViewById(R.id.home_ram_value_text)
        ramChart = view.findViewById(R.id.home_ram_chart)

        cardRouting = view.findViewById(R.id.home_card_running_mode)
        routeIcon = view.findViewById(R.id.home_icon_running_mode)
        pillRuleText = view.findViewById(R.id.home_mode_pill_rule_text)
        routeSummaryText = view.findViewById(R.id.home_route_summary_text)

        cardLocalIp = view.findViewById(R.id.home_card_local_ip)
        localIpIfaceText = view.findViewById(R.id.home_local_ip_iface)
        localIpValueText = view.findViewById(R.id.home_local_ip_value)
        localIpMetaText = view.findViewById(R.id.home_local_ip_meta)

        cardHotspotShare = view.findViewById(R.id.home_card_hotspot_share)
        hotspotIcon = view.findViewById(R.id.home_hotspot_icon)
        hotspotSubtitle = view.findViewById(R.id.home_hotspot_subtitle)
        hotspotBadge = view.findViewById(R.id.home_hotspot_badge)
        hotspotChevron = view.findViewById(R.id.home_hotspot_chevron)
        hotspotChipMode = view.findViewById(R.id.home_hotspot_chip_mode)
        hotspotChipModeValue = view.findViewById(R.id.home_hotspot_chip_mode_value)
        hotspotChipPort = view.findViewById(R.id.home_hotspot_chip_port)
        hotspotChipPortValue = view.findViewById(R.id.home_hotspot_chip_port_value)
        hotspotChipLan = view.findViewById(R.id.home_hotspot_chip_lan)
        hotspotChipLanValue = view.findViewById(R.id.home_hotspot_chip_lan_value)

        cardSubscription = view.findViewById(R.id.home_card_subscription)
        subTitleText = view.findViewById(R.id.home_sub_title)
        subExpiryText = view.findViewById(R.id.home_sub_expiry)
        subPortalBtn = view.findViewById(R.id.home_sub_portal_btn)
        subProgressBar = view.findViewById(R.id.home_sub_progress)
        subQuotaText = view.findViewById(R.id.home_sub_quota_text)
        subRemainingText = view.findViewById(R.id.home_sub_remaining_text)
    }

    private fun styleCards(ctx: Context) {
        listOfNotNull(
            statusBannerCard,
            cardNetworkSpeed,
            cardTrafficUsage,
            cardNetworkEgress,
            cardCpuChart,
            cardRamChart,
            cardRouting,
            cardLocalIp,
            cardHotspotShare,
            cardSubscription,
        ).forEach { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 18f) }

        val (_, downloadColor, uploadColor) = TrafficRingView.resolveSegmentColors(ctx)
        trafficUpIcon?.setColorFilter(uploadColor)
        trafficDownIcon?.setColorFilter(downloadColor)

        listOfNotNull(hotspotChipMode, hotspotChipPort, hotspotChipLan).forEach { chipBox ->
            ExpressiveSettingsHelper.styleExpressiveNestedBox(chipBox, cornerRadiusDp = 10f, strokeAlpha = 110)
        }

        val nestedBg = Theme.resolveNestedBoxBackground(ctx)
        val primaryInk = ctx.getColorAttr(android.R.attr.textColorPrimary)
        val accent = ExpressiveSettingsHelper.resolveAccentColor(ctx)
        val strokeColor = Theme.resolveCardStrokeColor(ctx, selected = false)
        val isAnyAmoled = Theme.isAnyAmoled(ctx) || Theme.isMonochromeBlack(ctx)

        subPortalBtn?.let { button ->
            styleTonalActionButton(button, nestedBg, primaryInk, accent, strokeColor, isAnyAmoled)
        }
    }

    private fun styleTonalActionButton(
        button: MaterialButton,
        nestedBg: Int,
        primaryInk: Int,
        accent: Int,
        strokeColor: Int,
        isAnyAmoled: Boolean,
    ) {
        button.backgroundTintList = ColorStateList.valueOf(nestedBg)
        button.setTextColor(primaryInk)
        button.iconTint = ColorStateList.valueOf(accent)
        if (isAnyAmoled) {
            button.strokeWidth = dp2px(1)
            button.strokeColor = ColorStateList.valueOf(strokeColor)
        } else {
            button.strokeWidth = 0
        }
    }

    private fun resolveDistinctDirectColor(ctx: Context, proxyColor: Int): Int {
        if (Theme.isMonochromeBlack(ctx)) return LiveChartView.directColor(ctx)
        val surface = ColorUtils.setAlphaComponent(
            MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface, Color.WHITE),
            255,
        )
        val isNight = Theme.usingNightMode(ctx)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(proxyColor, hsl)
        hsl[0] = (hsl[0] + 205f) % 360f
        hsl[1] = hsl[1].coerceIn(0.55f, 0.78f)
        hsl[2] = if (isNight) 0.66f else 0.44f
        val darker = ColorUtils.calculateLuminance(surface) > 0.5
        var c = ColorUtils.HSLToColor(hsl)
        repeat(24) {
            if (ColorUtils.calculateContrast(c, surface) >= 3.2) return c
            val next = (hsl[2] + if (darker) -10f / 255f else 10f / 255f).coerceIn(0f, 1f)
            if (next == hsl[2]) return c
            hsl[2] = next
            c = ColorUtils.HSLToColor(hsl)
        }
        return c
    }

    private fun setupCharts(ctx: Context) {
        val proxyColor = LiveChartView.proxyColor(ctx)
        val directColor = resolveDistinctDirectColor(ctx, proxyColor)
        speedDotVpn?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(proxyColor)
        }
        speedDotDirect?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(directColor)
        }

        speedChart?.let { chart ->
            val rows = listOf(
                LiveChartView.Row(
                    key = "proxy",
                    label = getString(R.string.live_graph_proxy),
                    color = proxyColor,
                    wash = true,
                    fields = intArrayOf(LiveStatsBatch.PROXY_DOWN, LiveStatsBatch.PROXY_UP),
                ),
                LiveChartView.Row(
                    key = "direct",
                    label = getString(R.string.live_graph_direct),
                    color = directColor,
                    wash = false,
                    fields = intArrayOf(LiveStatsBatch.DIRECT_DOWN, LiveStatsBatch.DIRECT_UP),
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

        val memoryAxis = bytesAxis ?: LiveChartView.bytesAxis(ctx).also { bytesAxis = it }
        setupMetricChart(cpuChart, LiveChartView.coreColor(ctx), LiveStatsBatch.CPU, percentAxis)
        setupMetricChart(ramChart, proxyColor, LiveStatsBatch.MEMORY, memoryAxis)
    }

    private fun setupMetricChart(
        chart: LiveChartView?,
        color: Int,
        field: Int,
        axis: LiveChartView.Axis,
    ) {
        val target = chart ?: return
        val rows = listOf(
            LiveChartView.Row(
                key = "core",
                label = getString(R.string.live_graph_core),
                color = color,
                wash = true,
                fields = intArrayOf(field),
            ),
        )
        val columns = listOf(LiveChartView.Column(key = "v", arrow = "", dashed = false, wash = true))
        target.setup(rows, columns, axis, compact = true, events = false)
        target.windowMs = CHART_WINDOW_MS
        target.tickMs = CHART_TICK_MS
        target.data = LiveHistory.series
    }

    private fun wireClicks() {
        statusPowerBtn?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.HEAVY) }
            (activity as? MainActivity)?.toggleService()
        }
        statusBannerCard?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.TAP) }
            (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_configuration)
        }
        statusTestBar?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.TAP) }
            if (currentState == BaseService.State.Connected) {
                runConnectionTest()
            } else {
                (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_configuration)
            }
        }

        val openLiveGraphs = View.OnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.TAP) }
            (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_live_graphs)
        }
        cardNetworkSpeed?.setOnClickListener(openLiveGraphs)
        cardCpuChart?.setOnClickListener(openLiveGraphs)
        cardRamChart?.setOnClickListener(openLiveGraphs)

        attachCardChartTapForwarder(speedChart, cardNetworkSpeed)
        attachCardChartTapForwarder(cpuChart, cardCpuChart)
        attachCardChartTapForwarder(ramChart, cardRamChart)

        cardTrafficUsage?.setOnClickListener { v -> launchActivity(v, TrafficUsageActivity::class.java) }
        cardRouting?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.SELECTION) }
            RouteQuickSwitch.show(this) {
                refreshRunningModeCard()
            }
        }
        cardNetworkEgress?.setOnClickListener { v -> launchActivity(v, IpLookupActivity::class.java) }
        cardLocalIp?.setOnClickListener { v -> launchActivity(v, NetworkInterfacesActivity::class.java) }
        cardHotspotShare?.setOnClickListener { v -> launchActivity(v, LanSharingActivity::class.java) }

        cardSubscription?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.TAP) }
            (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_configuration)
        }
        subPortalBtn?.setOnClickListener { v ->
            val ctx = context ?: return@setOnClickListener
            val uri = portalUri ?: return@setOnClickListener
            HapticHelper.perform(ctx, v, HapticHelper.HapticKind.TAP)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(ctx, getString(R.string.subinfo_no_app, uri.toString()), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchActivity(view: View, destination: Class<*>) {
        val ctx = context ?: return
        HapticHelper.perform(ctx, view, HapticHelper.HapticKind.TAP)
        startActivity(Intent(ctx, destination))
    }

    // Forward taps on compact sparklines straight to the parent card instead of entering crosshair scrub mode.
    private fun attachCardChartTapForwarder(chart: LiveChartView?, card: View?) {
        if (chart == null || card == null) return
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
                    card.isPressed = true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > touchSlop) {
                        moved = true
                        card.isPressed = false
                    }
                }

                MotionEvent.ACTION_UP -> {
                    card.isPressed = false
                    if (!moved) card.performClick()
                }

                MotionEvent.ACTION_CANCEL -> {
                    card.isPressed = false
                }
            }
            true
        }
    }

    private fun refreshAllCards() {
        val running = DataStore.serviceState == BaseService.State.Connected
        if (running) ExitIp.ensure() else ExitIp.clear()
        updateStatusBanner()
        updateTestBarUi()
        updateExitCountryUi()
        refreshRunningModeCard()
        refreshNetworkEgressCard()
        refreshLocalIpCard()
        updateSpeedReadout()
        updateSpeedChart(running)
        updateCpuRamUi(running)
        refreshTrafficTotals()
        refreshHotspotShareCard()
        refreshSubscriptionCard()
    }

    private fun activeTargetProfileId(): Long = if (currentState.started) {
        DataStore.currentProfile.takeIf { it > 0L } ?: DataStore.selectedProxy
    } else {
        DataStore.selectedProxy
    }

    private fun resolveErrorColor(ctx: Context, fallback: Int): Int =
        ctx.getColorAttr(androidx.appcompat.R.attr.colorError).takeIf { it != 0 } ?: fallback

    private fun updateStatusBanner() {
        val ctx = context ?: return
        val card = statusBannerCard ?: return
        val iconBox = statusIconBox ?: return
        val icon = statusIcon ?: return
        val progress = statusProgress
        val dot = statusDot
        val titleTv = statusTitle ?: return
        val typeChip = statusTypeChip
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
        val transitional = currentState == BaseService.State.Connecting ||
            currentState == BaseService.State.Stopping

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
            active || transitional -> if (Theme.isMonochromeBlack(ctx)) {
                ColorUtils.setAlphaComponent(Color.WHITE, 36)
            } else {
                ColorUtils.setAlphaComponent(accent, if (isNight) 44 else 28)
            }
            else -> Theme.resolveNestedBoxBackground(ctx)
        }
        val iconInk = when {
            active || transitional -> if (Theme.isMonochromeBlack(ctx)) Color.WHITE else accent
            else -> primaryInk
        }

        statusPowerBtn?.contentDescription = getString(
            if (currentState.canStop) R.string.disconnect else R.string.connect,
        )
        iconBox.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(badgeFill)
            if (active || transitional || isAnyAmoled) {
                setStroke(
                    dp2px(1),
                    ColorUtils.setAlphaComponent(iconInk, if (active || transitional) 120 else 48),
                )
            }
        }
        val d = ResourcesCompat.getDrawable(resources, R.drawable.ic_power_settings_new_24, null)?.mutate()
        if (d != null) {
            DrawableCompat.setTint(d, iconInk)
            icon.setImageDrawable(d)
        }
        progress?.apply {
            visibility = if (transitional) View.VISIBLE else View.INVISIBLE
            setIndicatorColor(iconInk)
        }

        dot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active || transitional) iconInk else ColorUtils.setAlphaComponent(secondaryInk, 140))
        }

        titleTv.text = stateLabel
        titleTv.setTextColor(if (active || transitional) iconInk else primaryInk)

        modeTv.setTextColor(secondaryInk)
        modeTv.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(8).toFloat()
            setColor(Theme.resolveNestedBoxBackground(ctx))
        }
        updateStatusModeTag()

        statusProfileLoadJob?.cancel()
        val targetId = activeTargetProfileId()
        if (targetId <= 0L) {
            typeChip?.isVisible = false
            subtitleTv.text = getString(R.string.home_status_tap_to_select)
            return
        }
        statusProfileLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val entity = withContext(Dispatchers.IO) {
                SagerDatabase.proxyDao.getById(targetId)
            }
            val currentCtx = context ?: return@launch
            if (entity == null) {
                typeChip?.isVisible = false
                subtitleTv.text = getString(R.string.home_status_tap_to_select)
                return@launch
            }
            subtitleTv.text = entity.displayName()
            val typeLabel = entity.displayType().trim()
            if (typeLabel.isNotEmpty()) {
                val chipInk = if (active || transitional) iconInk else secondaryInk
                val chipStrokeAlpha = when {
                    active || transitional -> 96
                    Theme.isAnyAmoled(currentCtx) || Theme.isMonochromeBlack(currentCtx) -> 72
                    else -> 44
                }
                typeChip?.isVisible = true
                typeChip?.text = typeLabel
                typeChip?.setTextColor(chipInk)
                typeChip?.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp2px(6).toFloat()
                    setColor(Theme.resolveNestedBoxBackground(currentCtx))
                    setStroke(dp2px(1), ColorUtils.setAlphaComponent(chipInk, chipStrokeAlpha))
                }
            } else {
                typeChip?.isVisible = false
            }
        }
    }

    private fun updateTestBarUi() {
        val ctx = context ?: return
        val bar = statusTestBar ?: return
        val testIcon = statusTestIcon
        val testProgress = statusTestProgress
        val testText = statusTestText

        val isAnyAmoled = Theme.isAnyAmoled(ctx) || Theme.isMonochromeBlack(ctx)
        val accent = ExpressiveSettingsHelper.resolveAccentColor(ctx)
        val activeInk = if (Theme.isMonochromeBlack(ctx)) Color.WHITE else accent
        val primaryInk = ctx.getColorAttr(android.R.attr.textColorPrimary)
        val secondaryInk = ctx.getColorAttr(android.R.attr.textColorSecondary)
        val nestedBg = Theme.resolveNestedBoxBackground(ctx)
        val connected = currentState == BaseService.State.Connected

        bar.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(12).toFloat()
            setColor(nestedBg)
            if (isAnyAmoled) {
                setStroke(
                    dp2px(1),
                    ColorUtils.setAlphaComponent(
                        if (connected) activeInk else Theme.resolveCardStrokeColor(ctx, selected = false),
                        if (connected) 90 else 160,
                    ),
                )
            }
        }

        val testing = connectionTestJob?.isActive == true
        testIcon?.isVisible = !testing
        testProgress?.isVisible = testing
        testIcon?.setColorFilter(if (connected) activeInk else secondaryInk)
        testProgress?.setIndicatorColor(activeInk)

        val message: CharSequence = when (currentState) {
            BaseService.State.Connected -> when {
                testing -> getString(R.string.connection_test_testing)
                hasTestStatus && !lastTestStatusText.isNullOrBlank() -> lastTestStatusText!!
                else -> getString(R.string.vpn_connected)
            }
            BaseService.State.Connecting -> getString(R.string.connecting)
            BaseService.State.Stopping -> getString(R.string.stopping)
            else -> getString(R.string.home_status_tap_to_select)
        }
        testText?.text = message
        testText?.setTextColor(if (connected) primaryInk else secondaryInk)
    }

    private fun updateExitCountryUi() {
        val exitTv = statusExitCountry ?: return
        val connected = currentState == BaseService.State.Connected
        val info = ExitIp.info?.takeIf { connected }
        val refreshing = connected && ExitIp.refreshing
        exitTv.alpha = if (refreshing) 0.5f else 1f
        if (info == null) {
            exitTv.text = "…"
            exitTv.contentDescription = null
            exitTv.isVisible = refreshing
        } else {
            val country = if (TestFormat.isCountryCode(info.countryCode)) {
                TestFormat.countryName(info.countryCode)
            } else {
                info.country
            }
            val flag = TestFormat.flag(info.countryCode)
            exitTv.text = if (flag.isEmpty()) country else "$flag\u00A0$country"
            exitTv.contentDescription = country
            exitTv.isVisible = true
        }
    }

    private fun runConnectionTest() {
        if (connectionTestJob?.isActive == true || currentState != BaseService.State.Connected) return
        val mainActivity = activity as? MainActivity ?: return
        hasTestStatus = true
        lastTestStatusText = getString(R.string.connection_test_testing)
        val clientTimeoutMs = StatsBar.computeClientUrlTestTimeoutMs(DataStore.urlTestTimeoutMs)
        connectionTestJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main.immediate) {
            try {
                val elapsed = withTimeout(clientTimeoutMs) {
                    mainActivity.urlTest()
                }
                hasTestStatus = true
                lastTestStatusText = if (elapsed == ProxyEntity.LATENCY_CONNECT_ONLY) {
                    getString(R.string.test_connect_ok)
                } else {
                    getString(
                        if (DataStore.testUrl.startsWith("https://")) {
                            R.string.connection_test_available
                        } else {
                            R.string.connection_test_available_http
                        },
                        elapsed,
                    )
                }
                ExitIp.refresh()
            } catch (_: TimeoutCancellationException) {
                hasTestStatus = true
                val timeoutText = getString(R.string.connection_test_timeout_error)
                val status = getString(R.string.connection_test_error, timeoutText)
                lastTestStatusText = status
                if (isAdded) snackbar(status).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w(e)
                hasTestStatus = true
                val reason = e.readableMessage.ifBlank {
                    getString(R.string.connection_test_timeout_error)
                }
                val status = getString(R.string.connection_test_error, reason)
                lastTestStatusText = status
                if (isAdded) snackbar(status).show()
            } finally {
                connectionTestJob = null
                if (view != null) updateTestBarUi()
            }
        }
        updateTestBarUi()
    }

    private fun updateStatusModeTag() {
        val modeTv = statusModeTag ?: return
        val baseMode = if (DataStore.serviceMode == Key.MODE_VPN) {
            getString(R.string.service_mode_vpn)
        } else {
            getString(R.string.route_proxy)
        }
        val nextText = if (currentState == BaseService.State.Connected && latestConnections > 0) {
            "$baseMode · $latestConnections"
        } else {
            baseMode
        }
        if (modeTv.text != nextText) modeTv.text = nextText
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
            routeIcon?.setColorFilter(accent)
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

    private fun refreshLocalIpCard() {
        val appCtx = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val cm = appCtx.getSystemService(ConnectivityManager::class.java)
                    ?: return@withContext null
                runCatching { NetworkInterfacesRepository(cm).collect() }.getOrNull()
            }
            if (view == null) return@launch
            val picked = snapshot?.let(::selectPrimaryLocalIp)
            if (picked == null) {
                localIpIfaceText?.isVisible = false
                localIpValueText?.text = getString(R.string.home_local_ip_none)
                localIpMetaText?.isVisible = false
                return@launch
            }
            val (iface, ip) = picked
            localIpIfaceText?.isVisible = true
            localIpIfaceText?.text = iface.name
            localIpValueText?.text = ip.address.substringBefore('%')

            val kindLabel = when (iface.kind) {
                InterfaceKind.WIFI -> getString(R.string.home_iface_wifi)
                InterfaceKind.ETHERNET -> getString(R.string.home_iface_ethernet)
                InterfaceKind.CELLULAR -> getString(R.string.home_iface_cellular)
                InterfaceKind.VPN -> getString(R.string.home_iface_vpn)
                InterfaceKind.OTHER -> getString(R.string.home_iface_other)
            }
            localIpMetaText?.isVisible = true
            localIpMetaText?.text = ip.prefixLength?.let { "$kindLabel · /$it" } ?: kindLabel
        }
    }

    private fun selectPrimaryLocalIp(snapshot: NetworkInterfacesSnapshot): Pair<NetworkInterfaceInfo, InterfaceIp>? {
        val activeName = snapshot.activeNetwork?.interfaceName
        fun usableIp(iface: NetworkInterfaceInfo, wantIpv4: Boolean): InterfaceIp? =
            iface.addresses.firstOrNull { ip ->
                val raw = ip.address.substringBefore('%')
                val isV4 = !ip.ipv6
                isV4 == wantIpv4 &&
                    !raw.startsWith("127.") &&
                    !raw.startsWith("169.254.") &&
                    !raw.startsWith("fe80:", ignoreCase = true) &&
                    raw != "::1"
            }

        fun rank(iface: NetworkInterfaceInfo): Int = when {
            !iface.isVpn && iface.name == activeName -> 0
            !iface.isVpn && iface.kind == InterfaceKind.WIFI -> 1
            !iface.isVpn && iface.kind == InterfaceKind.ETHERNET -> 2
            !iface.isVpn && iface.hotspot == HotspotKind.CONFIRMED -> 3
            !iface.isVpn && iface.kind == InterfaceKind.CELLULAR -> 4
            !iface.isVpn && iface.isDefaultRoute -> 5
            !iface.isVpn -> 6
            else -> 7
        }

        val candidates = snapshot.interfaces
            .filter { it.up != false && !it.loopback }
            .sortedWith(compareBy<NetworkInterfaceInfo> { rank(it) }.thenBy { it.name })

        for (iface in candidates) {
            usableIp(iface, wantIpv4 = true)?.let { return iface to it }
        }
        for (iface in candidates) {
            usableIp(iface, wantIpv4 = false)?.let { return iface to it }
        }
        return null
    }

    private fun refreshHotspotShareCard() {
        context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Triple(
                    HotspotShareStateStore.snapshot(),
                    SharePreference.fromCode(DataStore.hotspotShareMethod),
                    DataStore.inboundSocksPort to DataStore.allowLanAccess,
                )
            }
            val currentCtx = context ?: return@launch
            val state = loaded.first
            val preference = loaded.second
            val port = loaded.third.first
            val lanOpen = loaded.third.second

            val iconView = hotspotIcon ?: return@launch
            val subtitleTv = hotspotSubtitle ?: return@launch
            val badgeTv = hotspotBadge ?: return@launch
            val chevron = hotspotChevron

            val isNight = Theme.usingNightMode(currentCtx)
            val isAnyAmoled = Theme.isAnyAmoled(currentCtx) || Theme.isMonochromeBlack(currentCtx)
            val accent = ExpressiveSettingsHelper.resolveAccentColor(currentCtx)
            val primaryInk = currentCtx.getColorAttr(android.R.attr.textColorPrimary)
            val secondaryInk = currentCtx.getColorAttr(android.R.attr.textColorSecondary)
            val errorColor = resolveErrorColor(currentCtx, accent)

            val isActive = state is ShareState.Active
            val isStarting = state is ShareState.Starting
            val isFailure = state is ShareState.Error || state is ShareState.Stopped

            fun methodLabel(method: ShareMethod): String = when (method) {
                ShareMethod.SHIZUKU -> getString(R.string.hotspot_share_tile_method_shizuku)
                ShareMethod.ROOT -> getString(R.string.hotspot_share_tile_method_root)
                ShareMethod.LAN_PROXY -> getString(R.string.hotspot_share_tile_method_proxy)
            }

            val configuredMode = when (preference) {
                SharePreference.AUTO -> getString(R.string.home_hotspot_method_auto)
                SharePreference.SHIZUKU -> getString(R.string.hotspot_share_tile_method_shizuku)
                SharePreference.ROOT -> getString(R.string.hotspot_share_tile_method_root)
                SharePreference.LAN_PROXY -> getString(R.string.hotspot_share_tile_method_proxy)
            }

            subtitleTv.text = when (state) {
                is ShareState.Active -> getString(R.string.home_hotspot_sharing_via, methodLabel(state.method))
                is ShareState.Starting -> getString(R.string.connecting)
                else -> getString(R.string.home_hotspot_ready_subtitle)
            }
            badgeTv.text = when (state) {
                is ShareState.Active -> getString(R.string.home_hotspot_active)
                is ShareState.Starting -> getString(R.string.connecting)
                is ShareState.Error -> getString(R.string.home_hotspot_error)
                is ShareState.Stopped -> getString(R.string.home_hotspot_stopped)
                ShareState.Off -> getString(R.string.home_hotspot_standby)
            }

            hotspotChipModeValue?.text = if (state is ShareState.Active) methodLabel(state.method) else configuredMode
            hotspotChipPortValue?.text = ":$port"
            hotspotChipLanValue?.text = if (lanOpen) {
                getString(R.string.home_hotspot_lan_open)
            } else {
                getString(R.string.home_hotspot_lan_local)
            }

            val highlightColor = when {
                isFailure -> errorColor
                isActive -> if (Theme.isMonochromeBlack(currentCtx)) Color.WHITE else accent
                else -> secondaryInk
            }
            val badgeFill = when {
                isActive || isFailure -> ColorUtils.setAlphaComponent(highlightColor, if (isNight) 42 else 28)
                else -> Theme.resolveNestedBoxBackground(currentCtx)
            }
            val iconInk = when {
                isActive || isFailure -> highlightColor
                else -> primaryInk
            }

            ResourcesCompat.getDrawable(resources, R.drawable.ic_hardware_router, null)?.let { raw ->
                iconView.setImageDrawable(
                    ExpressiveSettingsHelper.createMaskedIcon(
                        currentCtx,
                        raw,
                        badgeFill,
                        iconInk,
                        sizeDp = 36,
                        iconSizeDp = 18,
                        shape = ExpressiveShape.SQUIRCLE,
                    ),
                )
            }

            badgeTv.setTextColor(highlightColor)
            badgeTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(8).toFloat()
                setColor(badgeFill)
                if (isActive || isFailure) {
                    setStroke(dp2px(1), ColorUtils.setAlphaComponent(highlightColor, if (isAnyAmoled) 150 else 96))
                }
            }

            chevron?.drawable?.mutate()?.let { d ->
                DrawableCompat.setTint(d, ColorUtils.setAlphaComponent(secondaryInk, 185))
                chevron.setImageDrawable(d)
            }
        }
    }

    private fun refreshSubscriptionCard() {
        val targetId = activeTargetProfileId()
        viewLifecycleOwner.lifecycleScope.launch {
            val subGroup = withContext(Dispatchers.IO) {
                val activeGroup = if (targetId > 0L) {
                    SagerDatabase.proxyDao.getById(targetId)?.groupId
                        ?.takeIf { it > 0L }
                        ?.let { SagerDatabase.groupDao.getById(it) }
                } else {
                    null
                }
                activeGroup?.takeIf { it.isSubscription && it.subInfo.valid }
                    ?: SagerDatabase.groupDao.allGroups().firstOrNull { it.isSubscription && it.subInfo.valid }
            }
            val ctx = context ?: return@launch
            val card = cardSubscription ?: return@launch
            if (subGroup == null) {
                portalUri = null
                card.isVisible = false
                return@launch
            }

            val info = subGroup.subInfo
            val now = System.currentTimeMillis() / 1000L
            val accent = ExpressiveSettingsHelper.resolveAccentColor(ctx)
            val primaryInk = ctx.getColorAttr(android.R.attr.textColorPrimary)
            val secondaryInk = ctx.getColorAttr(android.R.attr.textColorSecondary)
            val errorColor = resolveErrorColor(ctx, accent)

            val groupName = subGroup.displayName()
            val provider = info.title.trim().takeIf { it.isNotEmpty() && it != groupName }
            subTitleText?.text = if (provider == null) {
                groupName
            } else {
                SpannableStringBuilder(groupName).append(" · ")
                    .append(provider, ForegroundColorSpan(secondaryInk), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (info.expire > 0L) {
                val urgent = (info.expire - now) / 86400L <= 3L
                subExpiryText?.isVisible = true
                subExpiryText?.text = SubscriptionInfoCard.expiryText(ctx, info.expire, now)
                subExpiryText?.setTextColor(if (urgent) errorColor else secondaryInk)
            } else {
                subExpiryText?.isVisible = false
            }

            portalUri = parseHttpUri(info.webUrl)
            subPortalBtn?.isVisible = portalUri != null

            subProgressBar?.isVisible = info.hasQuota
            subQuotaText?.isVisible = info.hasQuota
            subRemainingText?.isVisible = info.hasQuota
            if (info.hasQuota) {
                val percent = info.percentUsed.toInt().coerceIn(0, 100)
                val barProgress = if (info.total > 0L) {
                    if (info.used > 0L && percent == 0) 2 else percent
                } else {
                    100
                }
                subProgressBar?.setProgressCompat(barProgress, false)
                subProgressBar?.setIndicatorColor(
                    if (info.isExpired(now) || (info.total > 0L && percent >= 90)) errorColor else accent,
                )
                subProgressBar?.trackColor = ColorUtils.setAlphaComponent(primaryInk, 0x1F)

                val usedStr = SubscriptionInfoCard.readableSize(info.used)
                subQuotaText?.text = if (info.total > 0L) {
                    getString(R.string.subinfo_quota, usedStr, SubscriptionInfoCard.readableSize(info.total), percent)
                } else {
                    getString(R.string.subinfo_quota_unlimited, usedStr)
                }

                val remStr = if (info.total > 0L) SubscriptionInfoCard.readableSize(info.remaining) else "∞"
                subRemainingText?.text = getString(R.string.home_sub_remaining, remStr)
            }

            card.isVisible = true
        }
    }

    private fun parseHttpUri(raw: String): Uri? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val uri = Uri.parse(text)
        return uri.takeIf {
            val scheme = it.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !it.host.isNullOrEmpty()
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
        val upStr = ctx.getString(R.string.speed, Formatter.formatFileSize(ctx, upRate))
        val downStr = ctx.getString(R.string.speed, Formatter.formatFileSize(ctx, downRate))
        speedRateText?.text = "↑ $upStr   ↓ $downStr"
    }

    private fun updateCpuRamUi(running: Boolean) {
        cpuChart?.let {
            it.running = running
            it.invalidate()
        }
        ramChart?.let {
            it.running = running
            it.invalidate()
        }

        val cpuTv = cpuValueText
        val ramTv = ramValueText
        val zeroPct = percentAxis.format(0.0)
        val zeroBytes = context?.let { Formatter.formatFileSize(it, 0L) } ?: (bytesAxis?.format(0.0) ?: "0 B")
        val series = LiveHistory.series
        val last = if (running && series.size > 0 && SystemClock.elapsedRealtime() - series.latestTime <= STALE_MS) {
            series.size - 1
        } else {
            -1
        }
        if (last < 0) {
            cpuTv?.text = zeroPct
            ramTv?.text = zeroBytes
            if (latestConnections != 0) {
                latestConnections = 0
                updateStatusModeTag()
            }
            return
        }

        val cpu = series.value(LiveStatsBatch.CPU, last)
        cpuTv?.text = if (cpu.isNaN()) zeroPct else percentAxis.format(cpu.toDouble())

        val mem = series.value(LiveStatsBatch.MEMORY, last)
        ramTv?.text = if (mem.isNaN()) zeroBytes else (bytesAxis?.format(mem.toDouble()) ?: zeroBytes)

        val conns = series.value(LiveStatsBatch.CONNECTIONS, last)
        val count = if (conns.isNaN()) 0 else conns.roundToInt().coerceAtLeast(0)
        if (latestConnections != count) {
            latestConnections = count
            updateStatusModeTag()
        }
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
        trafficTotalText?.text = getString(
            R.string.home_traffic_total,
            Formatter.formatShortFileSize(ctx, totalDl + totalUl),
        )
    }

    private fun splitFormattedSize(context: Context, bytes: Long): Pair<String, String> {
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
                    val running = DataStore.serviceState == BaseService.State.Connected
                    updateSpeedChart(running)
                    updateCpuRamUi(running)
                    delay(LIVE_POLL_MS)
                }
            }
        }
    }
}
