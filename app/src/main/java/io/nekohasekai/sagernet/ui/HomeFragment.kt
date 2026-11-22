package io.nekohasekai.sagernet.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
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
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.LiveStatsBatch
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RouteManager
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SettingsMapper
import io.nekohasekai.sagernet.group.SubscriptionClient
import io.nekohasekai.sagernet.hotspotshare.HotspotShareStateStore
import io.nekohasekai.sagernet.hotspotshare.ShareMethod
import io.nekohasekai.sagernet.hotspotshare.SharePreference
import io.nekohasekai.sagernet.hotspotshare.ShareState
import io.nekohasekai.sagernet.hotspotshare.TetherWatchService
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.appUserAgent
import io.nekohasekai.sagernet.ktx.confirmAction
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.newHttpClient
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.outbound.Outbound
import io.nekohasekai.sagernet.outbound.`import`.ImportReport
import io.nekohasekai.sagernet.outbound.`import`.ProfileImport
import io.nekohasekai.sagernet.route.OutboundIds
import io.nekohasekai.sagernet.ui.profile.AnyTLSSettingsActivity
import io.nekohasekai.sagernet.ui.profile.AutoSelectorSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ChainSettingsActivity
import io.nekohasekai.sagernet.ui.profile.CustomSettingsActivity
import io.nekohasekai.sagernet.ui.profile.DirectSettingsActivity
import io.nekohasekai.sagernet.ui.profile.DnsttSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HttpSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HysteriaSettingsActivity
import io.nekohasekai.sagernet.ui.profile.JuicitySettingsActivity
import io.nekohasekai.sagernet.ui.profile.MasqueSettingsActivity
import io.nekohasekai.sagernet.ui.profile.MasterDnsSettingsActivity
import io.nekohasekai.sagernet.ui.profile.MieruSettingsActivity
import io.nekohasekai.sagernet.ui.profile.NaiveSettingsActivity
import io.nekohasekai.sagernet.ui.profile.OpenConnectSettingsActivity
import io.nekohasekai.sagernet.ui.profile.OpenVpnSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SSHSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowTLSSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowsocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SnellSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TrojanSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TrustTunnelSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TuicSettingsActivity
import io.nekohasekai.sagernet.ui.profile.VMessSettingsActivity
import io.nekohasekai.sagernet.ui.profile.VlessSettingsActivity
import io.nekohasekai.sagernet.ui.profile.WireGuardSettingsActivity
import io.nekohasekai.sagernet.ui.profile.XrayVlessSettingsActivity
import io.nekohasekai.sagernet.ui.profile.profileSettingsIntent
import io.nekohasekai.sagernet.ui.profiles.SubscriptionInfoCard
import io.nekohasekai.sagernet.ui.route.RouteQuickSwitch
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.ui.settings.ExpressiveShape
import io.nekohasekai.sagernet.ui.test.TestFormat
import io.nekohasekai.sagernet.ui.widget.TrafficRingView
import io.nekohasekai.sagernet.utils.HapticHelper
import io.nekohasekai.sagernet.utils.HotspotGatewayState
import io.nekohasekai.sagernet.utils.HotspotGatewayStatus
import io.nekohasekai.sagernet.utils.HotspotKind
import io.nekohasekai.sagernet.utils.InterfaceIp
import io.nekohasekai.sagernet.utils.InterfaceKind
import io.nekohasekai.sagernet.utils.LanSharingLogics
import io.nekohasekai.sagernet.utils.NetworkInterfaceInfo
import io.nekohasekai.sagernet.utils.NetworkInterfacesRepository
import io.nekohasekai.sagernet.utils.NetworkInterfacesSnapshot
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.utils.stun.DefaultStunSocketFactory
import io.nekohasekai.sagernet.utils.stun.StunAssessmentKind
import io.nekohasekai.sagernet.utils.stun.StunAssessmentPolicy
import io.nekohasekai.sagernet.utils.stun.StunNatTester
import io.nekohasekai.sagernet.utils.stun.StunPreset
import io.nekohasekai.sagernet.utils.stun.StunProtocolCodes
import io.nekohasekai.sagernet.utils.toolbox.ConnectivityChecker
import io.nekohasekai.sagernet.utils.toolbox.ConnectivityStatus
import io.nekohasekai.sagernet.utils.toolbox.ServiceProbeResult
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
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.util.zip.ZipInputStream
import kotlin.math.hypot

/**
 * Ultra-compact Bento Home dashboard: Interactive Status Hero + URL test strip, Active Group /
 * Subscription + Quick Switcher, Network Speed (2x4), Traffic Usage + Network (2x2),
 * compact 2x1 CPU & RAM sparklines, compact Routing + Apps VPN Mode, and Hotspot & Proxy Share.
 */
class HomeFragment : ToolbarFragment(R.layout.layout_home) {

    private companion object {
        const val LIVE_POLL_MS = 1000L
        const val CHART_WINDOW_MS = 60_000L
        const val CHART_TICK_MS = 15_000L
        const val STALE_MS = 3000L

        val MANUAL_PROFILE_OPTIONS: List<Triple<Int, Int, Class<*>>> = listOf(
            Triple(R.id.action_new_auto_selector, R.string.action_auto_selector, AutoSelectorSettingsActivity::class.java),
            Triple(R.id.action_new_socks, R.string.action_socks, SocksSettingsActivity::class.java),
            Triple(R.id.action_new_http, R.string.action_http, HttpSettingsActivity::class.java),
            Triple(R.id.action_new_ss, R.string.action_shadowsocks, ShadowsocksSettingsActivity::class.java),
            Triple(R.id.action_new_vmess, R.string.action_vmess, VMessSettingsActivity::class.java),
            Triple(R.id.action_new_vless, R.string.action_vless, VlessSettingsActivity::class.java),
            Triple(R.id.action_new_xray_vless, R.string.action_vless_xray, XrayVlessSettingsActivity::class.java),
            Triple(R.id.action_new_trojan, R.string.action_trojan, TrojanSettingsActivity::class.java),
            Triple(R.id.action_new_mieru, R.string.action_mieru, MieruSettingsActivity::class.java),
            Triple(R.id.action_new_naive, R.string.action_naive, NaiveSettingsActivity::class.java),
            Triple(R.id.action_new_hysteria, R.string.action_hysteria, HysteriaSettingsActivity::class.java),
            Triple(R.id.action_new_tuic, R.string.action_tuic, TuicSettingsActivity::class.java),
            Triple(R.id.action_new_juicity, R.string.action_juicity, JuicitySettingsActivity::class.java),
            Triple(R.id.action_new_shadowtls, R.string.action_shadowtls, ShadowTLSSettingsActivity::class.java),
            Triple(R.id.action_new_anytls, R.string.action_anytls, AnyTLSSettingsActivity::class.java),
            Triple(R.id.action_new_trusttunnel, R.string.action_trusttunnel, TrustTunnelSettingsActivity::class.java),
            Triple(R.id.action_new_snell, R.string.action_snell, SnellSettingsActivity::class.java),
            Triple(R.id.action_new_ssh, R.string.action_ssh, SSHSettingsActivity::class.java),
            Triple(R.id.action_new_wg, R.string.action_wireguard, WireGuardSettingsActivity::class.java),
            Triple(R.id.action_new_masque, R.string.action_masque, MasqueSettingsActivity::class.java),
            Triple(R.id.action_new_openvpn, R.string.action_openvpn, OpenVpnSettingsActivity::class.java),
            Triple(R.id.action_new_openconnect, R.string.action_openconnect, OpenConnectSettingsActivity::class.java),
            Triple(R.id.action_new_dnstt, R.string.action_dnstt, DnsttSettingsActivity::class.java),
            Triple(R.id.action_new_masterdns, R.string.action_masterdns, MasterDnsSettingsActivity::class.java),
            Triple(R.id.action_new_direct, R.string.action_direct, DirectSettingsActivity::class.java),
            Triple(R.id.action_new_config, R.string.custom_config, CustomSettingsActivity::class.java),
            Triple(R.id.action_new_chain, R.string.proxy_chain, ChainSettingsActivity::class.java),
        )
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
    private var statusEditBtn: FrameLayout? = null
    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importFileUri(uri)
    }
    private var statusTestBar: LinearLayout? = null
    private var statusTestIcon: ImageView? = null
    private var statusTestProgress: CircularProgressIndicator? = null
    private var statusTestText: TextView? = null
    private var statusExitCountry: TextView? = null

    private var cardNetworkSpeed: MaterialCardView? = null
    private var speedConnBadge: TextView? = null
    private var speedRateText: TextView? = null
    private var speedDotVpn: View? = null
    private var speedDotUp: View? = null
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
    private var networkExtraText: TextView? = null

    private var cardCpuChart: MaterialCardView? = null
    private var cpuValueText: TextView? = null
    private var cpuChart: LiveChartView? = null

    private var cardRamChart: MaterialCardView? = null
    private var ramValueText: TextView? = null
    private var ramChart: LiveChartView? = null

    private var cardConnChart: MaterialCardView? = null
    private var connValueText: TextView? = null
    private var connChart: LiveChartView? = null

    private var cardRouting: MaterialCardView? = null
    private var routeIcon: ImageView? = null
    private var routeModeBadge: TextView? = null
    private var pillRuleText: TextView? = null
    private var routeSummaryText: TextView? = null

    private var headerStatusDot: View? = null
    private var headerStatusText: TextView? = null

    private var cardAppProxy: MaterialCardView? = null
    private var appProxyBadge: TextView? = null
    private var appProxyModeText: TextView? = null
    private var appProxyMetaText: TextView? = null

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

    private var cardSubscription: View? = null
    private var subIcon: ImageView? = null
    private var subTitleText: TextView? = null
    private var subExpiryText: TextView? = null
    private var subPortalBtn: MaterialButton? = null
    private var subSwitchBtn: MaterialButton? = null
    private var subQuotaContainer: LinearLayout? = null
    private var subProgressBar: LinearProgressIndicator? = null
    private var subQuotaText: TextView? = null
    private var subRemainingText: TextView? = null

    private var cardDiagStun: MaterialCardView? = null
    private var diagStunProgress: CircularProgressIndicator? = null
    private var diagStunOpenBtn: FrameLayout? = null
    private var diagStunValue: TextView? = null
    private var diagStunMeta: TextView? = null
    private var cardDiagDnsLeak: MaterialCardView? = null
    private var diagDnsProgress: CircularProgressIndicator? = null
    private var diagDnsOpenBtn: FrameLayout? = null
    private var diagDnsValue: TextView? = null
    private var diagDnsMeta: TextView? = null
    private var cardDiagConnectivity: MaterialCardView? = null
    private var diagConnProgress: CircularProgressIndicator? = null
    private var diagConnOpenBtn: FrameLayout? = null
    private var diagConnValue: TextView? = null
    private var diagConnMeta: TextView? = null

    private val percentAxis = LiveChartView.percentAxis()
    private var bytesAxis: LiveChartView.Axis? = null
    private var portalUri: Uri? = null
    private var currentSubGroupId: Long = 0L
    private var lastUrlTestLatencyMs: Int = 0
    private var connectionTestJob: Job? = null
    private var statusProfileLoadJob: Job? = null
    private var stunDiagJob: Job? = null
    private var dnsDiagJob: Job? = null
    private var connDiagJob: Job? = null
    private var hasTestStatus: Boolean = false
    private var lastTestStatusText: CharSequence? = null
    private val exitIpListener: () -> Unit = {
        updateExitCountryUi()
        refreshNetworkEgressCard()
    }

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
        val pillHeader = ExpressiveSettingsHelper.attachFloatingPillHeader(appbar, ctx)?.apply {
            update(
                context = ctx,
                title = getString(R.string.app_name),
                iconRes = R.drawable.ic_navigation_menu,
                contentDescription = getString(R.string.navigation_open_drawer),
            ) {
                (activity as? MainActivity)?.binding?.drawerLayout?.openDrawer(GravityCompat.START)
            }
        }
        attachHeaderActions(pillHeader?.root, ctx)

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
                v.updatePadding(bottom = bars.bottom + dp2px(60))
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
        stunDiagJob?.cancel()
        stunDiagJob = null
        dnsDiagJob?.cancel()
        dnsDiagJob = null
        connDiagJob?.cancel()
        connDiagJob = null
        if (ExitIp.listener === exitIpListener) ExitIp.listener = null
        hasTestStatus = false
        lastTestStatusText = null
        lastUrlTestLatencyMs = 0
        currentSubGroupId = 0L

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
        statusEditBtn = null
        statusTestBar = null
        statusTestIcon = null
        statusTestProgress = null
        statusTestText = null
        statusExitCountry = null
        cardNetworkSpeed = null
        speedConnBadge = null
        speedRateText = null
        speedDotVpn = null
        speedDotUp = null
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
        networkExtraText = null
        cardCpuChart = null
        cpuValueText = null
        cpuChart = null
        cardRamChart = null
        ramValueText = null
        ramChart = null
        cardConnChart = null
        connValueText = null
        connChart = null
        cardRouting = null
        routeIcon = null
        routeModeBadge = null
        pillRuleText = null
        routeSummaryText = null
        headerStatusDot = null
        headerStatusText = null
        cardAppProxy = null
        appProxyBadge = null
        appProxyModeText = null
        appProxyMetaText = null
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
        subIcon = null
        subTitleText = null
        subExpiryText = null
        subPortalBtn = null
        subSwitchBtn = null
        subQuotaContainer = null
        subProgressBar = null
        subQuotaText = null
        subRemainingText = null
        cardDiagStun = null
        diagStunProgress = null
        diagStunOpenBtn = null
        diagStunValue = null
        diagStunMeta = null
        cardDiagDnsLeak = null
        diagDnsProgress = null
        diagDnsOpenBtn = null
        diagDnsValue = null
        diagDnsMeta = null
        cardDiagConnectivity = null
        diagConnProgress = null
        diagConnOpenBtn = null
        diagConnValue = null
        diagConnMeta = null
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
            lastUrlTestLatencyMs = 0
            ExitIp.clear()
        }
        if (view == null) return
        val running = DataStore.serviceState == BaseService.State.Connected
        if (state == BaseService.State.Connected && !wasConnected && !hasTestStatus) {
            lastTestStatusText = getString(R.string.vpn_connected)
        }
        updateStatusBanner()
        updateTestBarUi()
        updateExitCountryUi()
        refreshRunningModeCard()
        refreshNetworkEgressCard()
        refreshAppProxyCard()
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
        lastUrlTestLatencyMs = 0
        if (view == null) return
        updateStatusBanner()
        updateTestBarUi()
        refreshNetworkEgressCard()
        refreshRunningModeCard()
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
        statusEditBtn = view.findViewById(R.id.home_status_edit_btn)
        statusTestBar = view.findViewById(R.id.home_status_test_bar)
        statusTestIcon = view.findViewById(R.id.home_status_test_icon)
        statusTestProgress = view.findViewById(R.id.home_status_test_progress)
        statusTestText = view.findViewById(R.id.home_status_test_text)
        statusExitCountry = view.findViewById(R.id.home_status_exit_country)

        cardNetworkSpeed = view.findViewById(R.id.home_card_network_speed)
        speedConnBadge = view.findViewById(R.id.home_speed_conn_badge)
        speedRateText = view.findViewById(R.id.home_speed_rate_text)
        speedDotVpn = view.findViewById(R.id.home_speed_dot_vpn)
        speedDotUp = view.findViewById(R.id.home_speed_dot_up)
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
        networkExtraText = view.findViewById(R.id.home_network_extra_text)

        cardCpuChart = view.findViewById(R.id.home_card_cpu_chart)
        cpuValueText = view.findViewById(R.id.home_cpu_value_text)
        cpuChart = view.findViewById(R.id.home_cpu_chart)

        cardRamChart = view.findViewById(R.id.home_card_ram_chart)
        ramValueText = view.findViewById(R.id.home_ram_value_text)
        ramChart = view.findViewById(R.id.home_ram_chart)

        cardConnChart = view.findViewById(R.id.home_card_conn_chart)
        connValueText = view.findViewById(R.id.home_conn_value_text)
        connChart = view.findViewById(R.id.home_conn_chart)

        cardRouting = view.findViewById(R.id.home_card_running_mode)
        routeIcon = view.findViewById(R.id.home_icon_running_mode)
        routeModeBadge = view.findViewById(R.id.home_route_mode_badge)
        pillRuleText = view.findViewById(R.id.home_mode_pill_rule_text)
        routeSummaryText = view.findViewById(R.id.home_route_summary_text)

        cardAppProxy = view.findViewById(R.id.home_card_app_proxy)
        appProxyBadge = view.findViewById(R.id.home_app_proxy_badge)
        appProxyModeText = view.findViewById(R.id.home_app_proxy_mode_text)
        appProxyMetaText = view.findViewById(R.id.home_app_proxy_meta_text)

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
        subIcon = view.findViewById(R.id.home_sub_icon)
        subTitleText = view.findViewById(R.id.home_sub_title)
        subExpiryText = view.findViewById(R.id.home_sub_expiry)
        subPortalBtn = view.findViewById(R.id.home_sub_portal_btn)
        subSwitchBtn = view.findViewById(R.id.home_sub_switch_btn)
        subQuotaContainer = view.findViewById(R.id.home_sub_quota_container)
        subProgressBar = view.findViewById(R.id.home_sub_progress)
        subQuotaText = view.findViewById(R.id.home_sub_quota_text)
        subRemainingText = view.findViewById(R.id.home_sub_remaining_text)

        cardDiagStun = view.findViewById(R.id.home_card_diag_stun)
        diagStunProgress = view.findViewById(R.id.home_diag_stun_progress)
        diagStunOpenBtn = view.findViewById(R.id.home_diag_stun_open_btn)
        diagStunValue = view.findViewById(R.id.home_diag_stun_value)
        diagStunMeta = view.findViewById(R.id.home_diag_stun_meta)

        cardDiagDnsLeak = view.findViewById(R.id.home_card_diag_dns_leak)
        diagDnsProgress = view.findViewById(R.id.home_diag_dns_progress)
        diagDnsOpenBtn = view.findViewById(R.id.home_diag_dns_open_btn)
        diagDnsValue = view.findViewById(R.id.home_diag_dns_value)
        diagDnsMeta = view.findViewById(R.id.home_diag_dns_meta)

        cardDiagConnectivity = view.findViewById(R.id.home_card_diag_connectivity)
        diagConnProgress = view.findViewById(R.id.home_diag_conn_progress)
        diagConnOpenBtn = view.findViewById(R.id.home_diag_conn_open_btn)
        diagConnValue = view.findViewById(R.id.home_diag_conn_value)
        diagConnMeta = view.findViewById(R.id.home_diag_conn_meta)
    }

    private fun styleCards(ctx: Context) {
        listOfNotNull(
            statusBannerCard,
            cardNetworkSpeed,
            cardTrafficUsage,
            cardNetworkEgress,
            cardCpuChart,
            cardRamChart,
            cardConnChart,
            cardRouting,
            cardAppProxy,
            cardHotspotShare,
            cardDiagStun,
            cardDiagDnsLeak,
            cardDiagConnectivity,
        ).forEach { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 16f) }

        cardSubscription?.let {
            ExpressiveSettingsHelper.styleExpressiveNestedBox(it, cornerRadiusDp = 11f, strokeAlpha = 115)
        }

        val (_, downloadColor, uploadColor) = TrafficRingView.resolveSegmentColors(ctx)
        trafficUpIcon?.setColorFilter(uploadColor)
        trafficDownIcon?.setColorFilter(downloadColor)

        listOfNotNull(hotspotChipMode, hotspotChipPort, hotspotChipLan).forEach { chipBox ->
            ExpressiveSettingsHelper.styleExpressiveNestedBox(chipBox, cornerRadiusDp = 10f, strokeAlpha = 110)
        }

        val nestedBg = Theme.resolveNestedBoxBackground(ctx)
        val primaryInk = ctx.getColorAttr(android.R.attr.textColorPrimary)
        val accent = ExpressiveSettingsHelper.resolveAccentColor(ctx)
        val iconAccent = if (Theme.isMonochromeBlack(ctx)) Color.WHITE else accent
        val strokeColor = Theme.resolveCardStrokeColor(ctx, selected = false)
        val isAnyAmoled = Theme.isAnyAmoled(ctx) || Theme.isMonochromeBlack(ctx)

        listOfNotNull(
            view?.findViewById<ImageView>(R.id.home_icon_network_speed),
            view?.findViewById<ImageView>(R.id.home_icon_traffic_usage),
            view?.findViewById<ImageView>(R.id.home_icon_network_egress),
            view?.findViewById<ImageView>(R.id.home_icon_cpu),
            view?.findViewById<ImageView>(R.id.home_icon_ram),
            view?.findViewById<ImageView>(R.id.home_icon_conn),
            routeIcon,
            view?.findViewById<ImageView>(R.id.home_icon_app_proxy),
            view?.findViewById<ImageView>(R.id.home_icon_diag_stun),
            view?.findViewById<ImageView>(R.id.home_icon_diag_dns),
            view?.findViewById<ImageView>(R.id.home_icon_diag_conn),
        ).forEach { it.setColorFilter(iconAccent) }

        speedConnBadge?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(7).toFloat()
            setColor(nestedBg)
        }

        listOfNotNull(subPortalBtn, subSwitchBtn).forEach { button ->
            styleTonalActionButton(button, nestedBg, primaryInk, accent, strokeColor, isAnyAmoled)
        }
        listOfNotNull(statusEditBtn, diagStunOpenBtn, diagDnsOpenBtn, diagConnOpenBtn).forEach { btn ->
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(nestedBg)
                if (isAnyAmoled) {
                    setStroke(dp2px(1), strokeColor)
                }
            }
        }
        listOfNotNull(diagStunProgress, diagDnsProgress, diagConnProgress).forEach { progress ->
            progress.setIndicatorColor(accent)
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
        val (_, downloadColor, uploadColor) = TrafficRingView.resolveSegmentColors(ctx)
        val directColor = resolveDistinctDirectColor(ctx, downloadColor)
        speedDotVpn?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(downloadColor)
        }
        speedDotUp?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(uploadColor)
        }
        speedDotDirect?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(directColor)
        }

        speedChart?.let { chart ->
            val rows = listOf(
                LiveChartView.Row(
                    key = "down",
                    label = getString(R.string.live_graph_down),
                    color = downloadColor,
                    wash = true,
                    fields = intArrayOf(LiveStatsBatch.PROXY_DOWN),
                ),
                LiveChartView.Row(
                    key = "up",
                    label = getString(R.string.live_graph_up),
                    color = uploadColor,
                    wash = false,
                    fields = intArrayOf(LiveStatsBatch.PROXY_UP),
                ),
                LiveChartView.Row(
                    key = "direct",
                    label = getString(R.string.live_graph_direct),
                    color = directColor,
                    wash = false,
                    fields = intArrayOf(LiveStatsBatch.DIRECT_DOWN),
                ),
            )
            val columns = listOf(
                LiveChartView.Column(key = "v", arrow = "", dashed = false, wash = true),
            )
            chart.setup(rows, columns, LiveChartView.rateAxis(ctx), compact = true, events = false)
            chart.windowMs = CHART_WINDOW_MS
            chart.tickMs = CHART_TICK_MS
            chart.data = LiveHistory.series
        }

        val memoryAxis = bytesAxis ?: LiveChartView.bytesAxis(ctx).also { bytesAxis = it }
        setupMetricChart(cpuChart, LiveChartView.coreColor(ctx), LiveStatsBatch.CPU, percentAxis)
        setupMetricChart(ramChart, downloadColor, LiveStatsBatch.MEMORY, memoryAxis)
        setupMetricChart(connChart, directColor, LiveStatsBatch.CONNECTIONS, LiveChartView.countAxis())
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
        statusEditBtn?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.TAP) }
            openActiveProfileEditor()
        }
        statusBannerCard?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.TAP) }
            (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_configuration)
        }
        statusTestBar?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.TAP) }
            when {
                currentState == BaseService.State.Connected -> runConnectionTest()
                activeTargetProfileId() > 0L -> (activity as? MainActivity)?.toggleService()
                else -> (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_configuration)
            }
        }

        val openLiveGraphs = View.OnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.TAP) }
            (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_live_graphs)
        }
        cardNetworkSpeed?.setOnClickListener(openLiveGraphs)
        cardCpuChart?.setOnClickListener(openLiveGraphs)
        cardRamChart?.setOnClickListener(openLiveGraphs)
        cardConnChart?.setOnClickListener(openLiveGraphs)

        attachCardChartTapForwarder(speedChart, cardNetworkSpeed)
        attachCardChartTapForwarder(cpuChart, cardCpuChart)
        attachCardChartTapForwarder(ramChart, cardRamChart)
        attachCardChartTapForwarder(connChart, cardConnChart)

        cardTrafficUsage?.setOnClickListener { v -> launchActivity(v, TrafficUsageActivity::class.java) }
        cardRouting?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.SELECTION) }
            RouteQuickSwitch.show(this) {
                refreshRunningModeCard()
            }
        }
        cardNetworkEgress?.setOnClickListener { v -> launchActivity(v, IpLookupActivity::class.java) }
        cardAppProxy?.setOnClickListener { v -> launchActivity(v, AppManagerActivity::class.java) }
        cardHotspotShare?.setOnClickListener { v -> launchActivity(v, LanSharingActivity::class.java) }

        val openProfiles = View.OnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.TAP) }
            val mainActivity = activity as? MainActivity ?: return@OnClickListener
            mainActivity.displayFragmentWithId(R.id.nav_configuration)
            val groupId = currentSubGroupId
            if (groupId > 0L) {
                mainActivity.showGroup(groupId)
                v.post { mainActivity.showGroup(groupId) }
            }
        }
        cardSubscription?.setOnClickListener(openProfiles)
        subSwitchBtn?.setOnClickListener(openProfiles)
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

        wireDiagTile(cardDiagStun, diagStunOpenBtn, StunActivity::class.java) { runQuickStunTest() }
        wireDiagTile(cardDiagDnsLeak, diagDnsOpenBtn, DnsLeakActivity::class.java) { runQuickDnsLeakTest() }
        wireDiagTile(cardDiagConnectivity, diagConnOpenBtn, ConnectivityTestActivity::class.java) { runQuickConnectivityTest() }
    }

    private fun wireDiagTile(
        card: View?,
        openBtn: View?,
        destination: Class<*>,
        onRunInline: () -> Unit,
    ) {
        card?.setOnClickListener { v ->
            context?.let { c -> HapticHelper.perform(c, v, HapticHelper.HapticKind.TAP) }
            onRunInline()
        }
        card?.setOnLongClickListener { v ->
            launchActivity(v, destination)
            true
        }
        openBtn?.setOnClickListener { v -> launchActivity(v, destination) }
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
        refreshSubscriptionCard()
        refreshRunningModeCard()
        refreshAppProxyCard()
        refreshNetworkEgressCard()
        updateSpeedReadout()
        updateSpeedChart(running)
        updateCpuRamUi(running)
        refreshTrafficTotals()
        refreshHotspotShareCard()
    }

    private fun activeTargetProfileId(): Long =
        DataStore.selectedProxy.takeIf { it > 0L } ?: DataStore.currentProfile

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

        val statusColor = ContextCompat.getColor(
            ctx,
            when {
                active -> R.color.material_green_500
                transitional -> R.color.material_amber_500
                else -> R.color.material_red_500
            },
        )

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

        val badgeFill = ColorUtils.setAlphaComponent(statusColor, if (isNight) 54 else 38)
        val iconInk = statusColor

        statusPowerBtn?.contentDescription = getString(
            if (currentState.canStop) R.string.disconnect else R.string.connect,
        )
        iconBox.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(badgeFill)
            setStroke(
                dp2px(1),
                ColorUtils.setAlphaComponent(statusColor, if (active || transitional) 190 else 145),
            )
        }
        val d = ResourcesCompat.getDrawable(resources, R.drawable.ic_power_settings_new_24, null)?.mutate()
        if (d != null) {
            DrawableCompat.setTint(d, iconInk)
            icon.setImageDrawable(d)
        }
        progress?.apply {
            visibility = if (transitional) View.VISIBLE else View.INVISIBLE
            setIndicatorColor(statusColor)
        }

        dot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(statusColor)
        }
        headerStatusDot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(statusColor)
        }
        if (!active) {
            headerStatusText?.text = stateLabel
        }

        titleTv.text = stateLabel
        titleTv.setTextColor(statusColor)

        statusProfileLoadJob?.cancel()
        val targetId = activeTargetProfileId()
        if (targetId <= 0L) {
            typeChip?.isVisible = false
            statusEditBtn?.isVisible = false
            subtitleTv.text = getString(R.string.home_status_tap_to_select)
            updateLatencyBadge(ctx, null)
            return
        }
        statusProfileLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val entity = withContext(Dispatchers.IO) {
                SagerDatabase.proxyDao.getById(targetId)
            }
            val currentCtx = context ?: return@launch
            if (entity == null) {
                typeChip?.isVisible = false
                statusEditBtn?.isVisible = false
                subtitleTv.text = getString(R.string.home_status_tap_to_select)
                updateLatencyBadge(currentCtx, null)
                return@launch
            }
            statusEditBtn?.isVisible = true
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
            updateLatencyBadge(currentCtx, entity)
        }
    }

    private fun updateLatencyBadge(ctx: Context, entity: ProxyEntity?) {
        val modeTv = statusModeTag ?: return
        val secondaryInk = ctx.getColorAttr(android.R.attr.textColorSecondary)
        val displayLatency = if (currentState == BaseService.State.Connected && lastUrlTestLatencyMs > 0) {
            lastUrlTestLatencyMs
        } else {
            entity?.latency ?: 0
        }
        if (displayLatency == 0) {
            modeTv.isVisible = false
            return
        }
        val colorRes = when {
            displayLatency < 0 -> R.color.material_red_500
            displayLatency < 300 -> R.color.material_green_500
            displayLatency < 800 -> R.color.material_amber_500
            else -> R.color.material_red_500
        }
        val badgeInk = ContextCompat.getColor(ctx, colorRes)
        modeTv.isVisible = true
        modeTv.text = if (displayLatency > 0) {
            "$displayLatency ms"
        } else {
            getString(R.string.unavailable)
        }
        modeTv.setTextColor(badgeInk.takeIf { it != 0 } ?: secondaryInk)
        modeTv.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(8).toFloat()
            setColor(Theme.resolveNestedBoxBackground(ctx))
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
            else -> if (activeTargetProfileId() > 0L) {
                getString(R.string.home_status_ready_to_connect)
            } else {
                getString(R.string.home_status_tap_to_select)
            }
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
                if (elapsed > 0) {
                    lastUrlTestLatencyMs = elapsed
                    updateStatusBanner()
                }
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

    private fun refreshRunningModeCard() {
        context ?: return
        val targetId = activeTargetProfileId()
        viewLifecycleOwner.lifecycleScope.launch {
            val (route, warpEnabled) = withContext(Dispatchers.IO) {
                val runningRouteId = RouteManager.runningRouteId()
                val resolvedRoute = if (runningRouteId > 0L) {
                    RouteManager.get(runningRouteId)?.takeIf { !it.is_raw }
                } else {
                    val gid = if (targetId > 0L) SagerDatabase.proxyDao.getById(targetId)?.groupId ?: 0L else 0L
                    if (gid > 0L) RouteManager.routeFor(gid) else null
                } ?: RouteManager.current()
                resolvedRoute to DataStore.enableWarp
            }
            val currentCtx = context ?: return@launch
            val accent = ExpressiveSettingsHelper.resolveAccentColor(currentCtx)
            val activeInk = if (Theme.isMonochromeBlack(currentCtx)) Color.WHITE else accent

            val routeTitle = route.name.trim().ifEmpty { getString(R.string.group_default_name) }
            pillRuleText?.text = routeTitle
            pillRuleText?.setTextColor(currentCtx.getColorAttr(android.R.attr.textColorPrimary))
            routeIcon?.setColorFilter(activeInk)

            val outboundLabel = when (route.default_outbound_id) {
                OutboundIds.DIRECT -> getString(R.string.home_route_direct)
                OutboundIds.BLOCK -> getString(R.string.home_route_block)
                else -> getString(R.string.route_proxy)
            }
            routeModeBadge?.apply {
                text = outboundLabel
                setTextColor(currentCtx.getColorAttr(android.R.attr.textColorSecondary))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp2px(7).toFloat()
                    setColor(Theme.resolveNestedBoxBackground(currentCtx))
                }
            }

            val activeRuleCount = route.rules.count { !it.disabled && !it.isEmpty() }
            val ruleSummary = if (activeRuleCount > 0) {
                getString(R.string.home_route_rules_active, activeRuleCount)
            } else {
                getString(R.string.home_route_default_only)
            }
            routeSummaryText?.text = if (warpEnabled) "$ruleSummary · WARP" else ruleSummary
        }
    }

    private fun refreshNetworkEgressCard() {
        val appCtx = context?.applicationContext ?: return
        val targetId = activeTargetProfileId()
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val entity = if (targetId > 0L) {
                    SagerDatabase.proxyDao.getById(targetId)
                } else {
                    null
                }
                val cm = appCtx.getSystemService(ConnectivityManager::class.java)
                val snapshot = cm?.let { runCatching { NetworkInterfacesRepository(it).collect() }.getOrNull() }
                entity to snapshot
            }
            val ctx = context ?: return@launch
            val entity = loaded.first
            val snapshot = loaded.second
            val picked = snapshot?.let(::selectPrimaryLocalIp)
            val secondary = ctx.getColorAttr(android.R.attr.textColorSecondary)

            if (picked != null) {
                val iface = picked.first
                val kindLabel = when (iface.kind) {
                    InterfaceKind.WIFI -> getString(R.string.home_iface_wifi)
                    InterfaceKind.ETHERNET -> getString(R.string.home_iface_ethernet)
                    InterfaceKind.CELLULAR -> getString(R.string.home_iface_cellular)
                    InterfaceKind.VPN -> getString(R.string.home_iface_vpn)
                    InterfaceKind.OTHER -> getString(R.string.home_iface_other)
                }
                networkLatencyText?.isVisible = true
                networkLatencyText?.text = kindLabel
                networkLatencyText?.setTextColor(secondary)
                networkLatencyText?.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp2px(6).toFloat()
                    setColor(Theme.resolveNestedBoxBackground(ctx))
                }
            } else {
                networkLatencyText?.isVisible = false
            }

            val exitInfo = ExitIp.info?.takeIf { currentState == BaseService.State.Connected }
            val localIp = picked?.second?.address?.substringBefore('%')

            if (exitInfo != null) {
                val flag = TestFormat.flag(exitInfo.countryCode)
                val exitIpStr = exitInfo.ip.ifBlank { exitInfo.country }
                networkIpText?.text = if (flag.isEmpty()) exitIpStr else "$flag\u00A0$exitIpStr"
                val countryName = if (TestFormat.isCountryCode(exitInfo.countryCode)) {
                    TestFormat.countryName(exitInfo.countryCode)
                } else {
                    exitInfo.country
                }
                networkMetaText?.text = when {
                    picked != null && localIp != null && countryName.isNotEmpty() ->
                        "$countryName · ${picked.first.name} ($localIp)"
                    countryName.isNotEmpty() -> countryName
                    picked != null && localIp != null -> "${picked.first.name} ($localIp)"
                    else -> getString(R.string.home_network)
                }
                networkExtraText?.text = buildNetworkExtraLine(ctx, entity, picked)
                return@launch
            }

            val storedIpOut = entity?.ipOut?.trim()?.takeIf {
                currentState == BaseService.State.Connected && it.isNotEmpty()
            }
            if (storedIpOut != null) {
                val country = entity.testCountry?.trim().orEmpty()
                val flagPrefix = if (country.length == 2 && country.all { it in 'A'..'Z' || it in 'a'..'z' }) {
                    "${ProxyEntity.countryFlag(country)}\u00A0"
                } else {
                    ""
                }
                networkIpText?.text = "$flagPrefix$storedIpOut"
                networkMetaText?.text = if (picked != null && localIp != null) {
                    "${picked.first.name} · $localIp"
                } else {
                    country.ifEmpty { getString(R.string.home_network) }
                }
                networkExtraText?.text = buildNetworkExtraLine(ctx, entity, picked)
                return@launch
            }

            if (picked != null && localIp != null) {
                networkIpText?.text = localIp
                networkMetaText?.text = picked.second.prefixLength?.let {
                    "${picked.first.name} · /$it"
                } ?: picked.first.name
                networkExtraText?.text = buildNetworkExtraLine(ctx, entity, picked)
            } else {
                networkIpText?.text = getString(R.string.not_connected)
                networkMetaText?.text = getString(R.string.home_local_ip_none)
                networkExtraText?.text = entity?.displayType()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: getString(R.string.home_status_disabled)
            }
        }
    }

    private fun buildNetworkExtraLine(
        ctx: Context,
        entity: ProxyEntity?,
        picked: Pair<NetworkInterfaceInfo, InterfaceIp>?,
    ): String {
        val type = entity?.displayType()?.trim().orEmpty()
        val latency = if (currentState == BaseService.State.Connected && lastUrlTestLatencyMs > 0) {
            lastUrlTestLatencyMs
        } else {
            entity?.latency ?: 0
        }
        if (type.isNotEmpty() && latency > 0) return "$type · $latency ms"
        if (type.isNotEmpty()) return type
        val prefix = picked?.second?.prefixLength
        return if (picked != null && prefix != null) {
            "${picked.first.name} · IPv4 /$prefix"
        } else {
            ctx.getString(R.string.home_network)
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

    private fun refreshAppProxyCard() {
        context ?: return
        val badgeTv = appProxyBadge
        val modeTv = appProxyModeText
        val metaTv = appProxyMetaText
        viewLifecycleOwner.lifecycleScope.launch {
            val (enabled, bypass, count) = withContext(Dispatchers.IO) {
                val on = DataStore.proxyApps
                val bp = DataStore.bypass
                val pkgs = if (on) {
                    DataStore.individual.lineSequence().count { it.isNotBlank() }
                } else {
                    0
                }
                Triple(on, bp, pkgs)
            }
            val currentCtx = context ?: return@launch
            val isAnyAmoled = Theme.isAnyAmoled(currentCtx) || Theme.isMonochromeBlack(currentCtx)
            val accent = ExpressiveSettingsHelper.resolveAccentColor(currentCtx)
            val primaryInk = currentCtx.getColorAttr(android.R.attr.textColorPrimary)
            val secondaryInk = currentCtx.getColorAttr(android.R.attr.textColorSecondary)
            val activeInk = if (Theme.isMonochromeBlack(currentCtx)) Color.WHITE else accent

            badgeTv?.text = getString(
                if (enabled) R.string.home_hotspot_active else R.string.hotspot_share_status_off,
            )
            badgeTv?.setTextColor(if (enabled) activeInk else secondaryInk)
            badgeTv?.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(6).toFloat()
                setColor(
                    if (enabled) {
                        ColorUtils.setAlphaComponent(activeInk, if (Theme.usingNightMode(currentCtx)) 42 else 28)
                    } else {
                        Theme.resolveNestedBoxBackground(currentCtx)
                    },
                )
                if (enabled) {
                    setStroke(dp2px(1), ColorUtils.setAlphaComponent(activeInk, if (isAnyAmoled) 140 else 88))
                }
            }

            modeTv?.text = when {
                !enabled -> getString(R.string.home_app_proxy_all)
                bypass -> getString(R.string.home_app_proxy_bypass)
                else -> getString(R.string.home_app_proxy_only)
            }
            modeTv?.setTextColor(primaryInk)

            metaTv?.text = if (enabled) {
                getString(R.string.home_app_proxy_count, count)
            } else {
                getString(R.string.home_app_proxy_all_sub)
            }
        }
    }

    private data class HotspotCardSnapshot(
        val shareState: ShareState,
        val hotspotEnabled: Boolean,
        val preference: SharePreference,
        val lanOpen: Boolean,
        val port: Int,
        val hasAuth: Boolean,
        val inboundUser: String,
        val gatewayEnabled: Boolean,
        val gatewayStatus: HotspotGatewayStatus,
        val gatewayPort: Int,
    )

    private fun refreshHotspotShareCard() {
        context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                HotspotShareStateStore.reconcileIfServiceDead(TetherWatchService.isRunningOrStarting())
                val gw = HotspotGatewayState.snapshot()
                HotspotCardSnapshot(
                    shareState = HotspotShareStateStore.snapshot(),
                    hotspotEnabled = DataStore.hotspotShareEnabled,
                    preference = SharePreference.fromCode(DataStore.hotspotShareMethod),
                    lanOpen = DataStore.allowLanAccess,
                    port = DataStore.inboundSocksPort,
                    hasAuth = DataStore.inboundAuth,
                    inboundUser = DataStore.inboundUser,
                    gatewayEnabled = DataStore.hotspotGatewayEnabled,
                    gatewayStatus = gw.status,
                    gatewayPort = gw.port,
                )
            }
            val currentCtx = context ?: return@launch
            val state = loaded.shareState
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

            fun methodLabel(method: ShareMethod): String = when (method) {
                ShareMethod.SHIZUKU -> getString(R.string.hotspot_share_tile_method_shizuku)
                ShareMethod.ROOT -> getString(R.string.hotspot_share_tile_method_root)
                ShareMethod.LAN_PROXY -> getString(R.string.hotspot_share_tile_method_proxy)
            }

            val configuredMode = when (loaded.preference) {
                SharePreference.AUTO -> getString(R.string.home_hotspot_method_auto)
                SharePreference.SHIZUKU -> getString(R.string.hotspot_share_tile_method_shizuku)
                SharePreference.ROOT -> getString(R.string.hotspot_share_tile_method_root)
                SharePreference.LAN_PROXY -> getString(R.string.hotspot_share_tile_method_proxy)
            }

            val activeMethod = (state as? ShareState.Active)?.method
            val heroMode = LanSharingLogics.resolveHeroBannerMode(loaded.lanOpen, activeMethod)
            val anySharingActive = heroMode != LanSharingLogics.HeroBannerMode.INACTIVE
            val isStarting = state is ShareState.Starting
            val isFailure = state is ShareState.Error || state is ShareState.Stopped

            val authShort = if (loaded.hasAuth) {
                getString(R.string.lan_sharing_auth_enabled, loaded.inboundUser.ifBlank { "***" })
            } else {
                getString(R.string.lan_sharing_auth_none)
            }

            subtitleTv.text = when {
                state is ShareState.Active && !loaded.lanOpen ->
                    getString(R.string.home_hotspot_sharing_via, methodLabel(state.method))
                isStarting -> getString(R.string.hotspot_share_status_starting)
                else -> getString(R.string.lan_sharing_status_sub, loaded.port, authShort)
            }

            badgeTv.text = when {
                heroMode == LanSharingLogics.HeroBannerMode.BOTH_ACTIVE && activeMethod != null ->
                    getString(R.string.lan_hero_both_active_badge, activeMethod.code.uppercase())
                state is ShareState.Active ->
                    getString(R.string.hotspot_share_status_active, methodLabel(state.method))
                isStarting -> getString(R.string.hotspot_share_status_starting)
                state is ShareState.Error -> getString(R.string.home_hotspot_error)
                state is ShareState.Stopped -> getString(R.string.home_hotspot_stopped)
                loaded.lanOpen -> getString(R.string.lan_sharing_status_running)
                loaded.hotspotEnabled || loaded.gatewayEnabled -> getString(R.string.home_hotspot_standby)
                else -> getString(R.string.hotspot_share_status_off)
            }

            val offLabel = getString(R.string.hotspot_share_status_off)
            hotspotChipModeValue?.text = when {
                state is ShareState.Active -> methodLabel(state.method)
                loaded.hotspotEnabled -> configuredMode
                else -> offLabel
            }
            hotspotChipPortValue?.text = if (loaded.lanOpen) ":${loaded.port}" else offLabel
            hotspotChipLanValue?.text = if (loaded.gatewayEnabled) {
                if (loaded.gatewayStatus == HotspotGatewayStatus.FOUND && loaded.gatewayPort > 0) {
                    ":${loaded.gatewayPort}"
                } else {
                    getString(R.string.home_hotspot_active)
                }
            } else {
                offLabel
            }

            val highlightColor = when {
                isFailure -> errorColor
                anySharingActive -> if (Theme.isMonochromeBlack(currentCtx)) Color.WHITE else accent
                else -> secondaryInk
            }
            val badgeFill = when {
                anySharingActive || isFailure ->
                    ColorUtils.setAlphaComponent(highlightColor, if (isNight) 42 else 28)
                else -> Theme.resolveNestedBoxBackground(currentCtx)
            }
            val iconInk = when {
                anySharingActive || isFailure -> highlightColor
                else -> primaryInk
            }

            ResourcesCompat.getDrawable(resources, R.drawable.ic_hardware_router, null)?.let { raw ->
                iconView.setImageDrawable(
                    ExpressiveSettingsHelper.createMaskedIcon(
                        currentCtx,
                        raw,
                        badgeFill,
                        iconInk,
                        sizeDp = 34,
                        iconSizeDp = 17,
                        shape = ExpressiveShape.SQUIRCLE,
                    ),
                )
            }

            badgeTv.setTextColor(highlightColor)
            badgeTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(7).toFloat()
                setColor(badgeFill)
                if (anySharingActive || isFailure) {
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
            val loaded = withContext(Dispatchers.IO) {
                val group = if (targetId > 0L) {
                    SagerDatabase.proxyDao.getById(targetId)?.groupId
                        ?.takeIf { it > 0L }
                        ?.let { SagerDatabase.groupDao.getById(it) }
                } else {
                    null
                } ?: SagerDatabase.groupDao.getById(DataStore.currentGroupId())
                    ?: SagerDatabase.groupDao.allGroups().firstOrNull()
                val memberCount = group?.let { SagerDatabase.proxyDao.countByGroup(it.id).toInt() } ?: 0
                group to memberCount
            }
            val ctx = context ?: return@launch
            val card = cardSubscription ?: return@launch
            val subGroup = loaded.first
            val memberCount = loaded.second
            currentSubGroupId = subGroup?.id ?: 0L
            if (subGroup == null) {
                portalUri = null
                subPortalBtn?.isVisible = false
                subQuotaContainer?.isVisible = false
                card.isVisible = false
                return@launch
            }

            val info = subGroup.subInfo
            val hasSubMeta = subGroup.isSubscription && info.valid
            val now = System.currentTimeMillis() / 1000L
            val accent = ExpressiveSettingsHelper.resolveAccentColor(ctx)
            val primaryInk = ctx.getColorAttr(android.R.attr.textColorPrimary)
            val secondaryInk = ctx.getColorAttr(android.R.attr.textColorSecondary)
            val errorColor = resolveErrorColor(ctx, accent)

            subIcon?.setImageResource(
                if (subGroup.isSubscription) {
                    R.drawable.ic_baseline_link_24
                } else {
                    R.drawable.ic_baseline_folder_open_24
                },
            )
            subIcon?.setColorFilter(if (Theme.isMonochromeBlack(ctx)) Color.WHITE else accent)

            val groupName = subGroup.displayName()
            val provider = if (hasSubMeta) {
                info.title.trim().takeIf { it.isNotEmpty() && it != groupName }
            } else {
                null
            }
            subTitleText?.text = if (provider == null) {
                groupName
            } else {
                SpannableStringBuilder(groupName).append(" · ")
                    .append(provider, ForegroundColorSpan(secondaryInk), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val countLabel = getString(R.string.home_group_profiles_count, memberCount)
            subExpiryText?.isVisible = true
            when {
                hasSubMeta && info.expire > 0L -> {
                    val urgent = (info.expire - now) / 86400L <= 3L
                    subExpiryText?.text = "${SubscriptionInfoCard.expiryText(ctx, info.expire, now)} · $countLabel"
                    subExpiryText?.setTextColor(if (urgent) errorColor else secondaryInk)
                }
                subGroup.isSubscription -> {
                    subExpiryText?.text = "${getString(R.string.home_group_label_subscription)} · $countLabel"
                    subExpiryText?.setTextColor(secondaryInk)
                }
                else -> {
                    subExpiryText?.text = "${getString(R.string.home_group_label_local)} · $countLabel"
                    subExpiryText?.setTextColor(secondaryInk)
                }
            }

            portalUri = if (hasSubMeta) parseHttpUri(info.webUrl) else null
            subPortalBtn?.isVisible = portalUri != null

            val showQuota = hasSubMeta && info.hasQuota
            subQuotaContainer?.isVisible = showQuota
            if (showQuota) {
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
        connChart?.let {
            it.running = running
            it.invalidate()
        }

        val cpuTv = cpuValueText
        val ramTv = ramValueText
        val connTv = connValueText
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
            connTv?.text = "0"
            speedConnBadge?.text = getString(R.string.home_connections_count, 0)
            if (running) {
                headerStatusText?.text = getString(R.string.home_connections_count, 0)
            }
            return
        }

        val cpu = series.value(LiveStatsBatch.CPU, last)
        cpuTv?.text = if (cpu.isNaN()) zeroPct else percentAxis.format(cpu.toDouble())

        val mem = series.value(LiveStatsBatch.MEMORY, last)
        ramTv?.text = if (mem.isNaN()) zeroBytes else (bytesAxis?.format(mem.toDouble()) ?: zeroBytes)

        val connRaw = series.value(LiveStatsBatch.CONNECTIONS, last)
        val conns = if (connRaw.isNaN()) 0 else connRaw.toInt().coerceAtLeast(0)
        connTv?.text = conns.toString()
        val connStr = getString(R.string.home_connections_count, conns)
        speedConnBadge?.text = connStr
        if (running) {
            headerStatusText?.text = connStr
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

    private fun attachHeaderActions(headerRoot: LinearLayout?, ctx: Context) {
        headerRoot ?: return
        if (headerRoot.findViewById<View>(R.id.home_header_add_btn) != null) return

        val isNight = Theme.usingNightMode(ctx)
        val pillSize = dp2px(36)
        val pillBg = Theme.resolveCardBackground(ctx)
        val pillStroke = Theme.resolveCardStrokeColor(ctx, selected = false)
        val strokePx = Theme.resolveCardStrokeWidthPx(ctx, selected = false).toInt().coerceAtLeast(1)
        val textPrimary = ctx.getColorAttr(android.R.attr.textColorPrimary)
        val rippleColor = ColorUtils.setAlphaComponent(textPrimary, if (isNight) 38 else 28)

        val statusShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = pillSize / 2f
            setColor(pillBg)
            setStroke(strokePx, pillStroke)
        }
        val statusMask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = pillSize / 2f
            setColor(textPrimary)
        }
        val statusDotView = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp2px(7), dp2px(7)).apply {
                marginEnd = dp2px(6)
            }
        }
        val statusTextView = TextView(ctx).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            maxLines = 1
            setTextColor(textPrimary)
        }
        headerStatusDot = statusDotView
        headerStatusText = statusTextView
        val statusPill = LinearLayout(ctx).apply {
            id = R.id.home_header_status_pill
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = pillSize
            setPadding(dp2px(11), 0, dp2px(11), 0)
            layoutParams = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                pillSize,
            ).apply {
                marginStart = dp2px(8)
            }
            isClickable = true
            isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(rippleColor), statusShape, statusMask)
            addView(statusDotView)
            addView(statusTextView)
            setOnClickListener { v ->
                context?.let { HapticHelper.perform(it, v, HapticHelper.HapticKind.TAP) }
                (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_live_graphs)
            }
        }
        headerRoot.addView(statusPill)

        headerRoot.addView(
            View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            },
        )

        val addBtn = createHeaderPillButton(
            ctx = ctx,
            id = R.id.home_header_add_btn,
            iconRes = R.drawable.ic_action_note_add,
            descRes = R.string.add_profile,
            pillSize = pillSize,
            pillBg = pillBg,
            pillStroke = pillStroke,
            strokePx = strokePx,
            iconTint = textPrimary,
            rippleColor = rippleColor,
            marginStartPx = 0,
        ).apply {
            setOnClickListener { v ->
                context?.let { HapticHelper.perform(it, v, HapticHelper.HapticKind.TAP) }
                showAddConfigMenu(v)
            }
        }

        val moreBtn = createHeaderPillButton(
            ctx = ctx,
            id = R.id.home_header_more_btn,
            iconRes = R.drawable.ic_baseline_more_vert_24,
            descRes = R.string.profiles_more_options,
            pillSize = pillSize,
            pillBg = pillBg,
            pillStroke = pillStroke,
            strokePx = strokePx,
            iconTint = textPrimary,
            rippleColor = rippleColor,
            marginStartPx = dp2px(8),
        ).apply {
            setOnClickListener { v ->
                context?.let { HapticHelper.perform(it, v, HapticHelper.HapticKind.TAP) }
                showHomeMoreMenu(v)
            }
        }

        headerRoot.addView(addBtn)
        headerRoot.addView(moreBtn)
    }

    private fun createHeaderPillButton(
        ctx: Context,
        id: Int,
        iconRes: Int,
        descRes: Int,
        pillSize: Int,
        pillBg: Int,
        pillStroke: Int,
        strokePx: Int,
        iconTint: Int,
        rippleColor: Int,
        marginStartPx: Int,
    ): FrameLayout {
        val bgShape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(pillBg)
            setStroke(strokePx, pillStroke)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(iconTint)
        }
        return FrameLayout(ctx).apply {
            this.id = id
            layoutParams = LinearLayout.LayoutParams(pillSize, pillSize).apply {
                marginStart = marginStartPx
            }
            isClickable = true
            isFocusable = true
            contentDescription = ctx.getString(descRes)
            background = RippleDrawable(ColorStateList.valueOf(rippleColor), bgShape, mask)
            addView(
                ImageView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(dp2px(18), dp2px(18), Gravity.CENTER)
                    setImageResource(iconRes)
                    imageTintList = ColorStateList.valueOf(iconTint)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
            )
        }
    }

    private fun enableMenuIcons(menu: Menu) {
        try {
            val method = menu.javaClass.getMethod("setOptionalIconsVisible", Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(menu, true)
        } catch (_: Exception) {}
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.hasSubMenu()) {
                item.subMenu?.let { enableMenuIcons(it) }
            }
        }
    }

    private fun resolveHomeTargetGroupId(): Long =
        currentSubGroupId.takeIf { it > 0L } ?: DataStore.currentGroupId()

    private suspend fun resolveActiveGroup(): ProxyGroup? {
        val targetId = activeTargetProfileId()
        return if (targetId > 0L) {
            SagerDatabase.proxyDao.getById(targetId)?.groupId
                ?.takeIf { it > 0L }
                ?.let { SagerDatabase.groupDao.getById(it) }
        } else {
            null
        } ?: currentSubGroupId.takeIf { it > 0L }?.let { SagerDatabase.groupDao.getById(it) }
            ?: SagerDatabase.groupDao.getById(DataStore.currentGroupId())
            ?: SagerDatabase.groupDao.allGroups().firstOrNull()
    }

    private fun showAddConfigMenu(anchor: View) {
        val ctx = context ?: return
        val popup = PopupMenu(ctx, anchor, Gravity.END)
        val scanItem = popup.menu.add(
            Menu.NONE,
            R.id.action_scan_qr_code,
            0,
            R.string.add_profile_methods_scan_qr_code,
        ).setIcon(R.drawable.ic_baseline_camera_24)
        val cachedHasCamera = SagerNet.cachedHasCamera()
        if (cachedHasCamera != null) {
            scanItem.isVisible = cachedHasCamera && !SagerNet.isTv
        } else {
            scanItem.isVisible = !SagerNet.isTv
            viewLifecycleOwner.lifecycleScope.launch {
                val available = SagerNet.hasCamera()
                if (view != null) {
                    scanItem.isVisible = available && !SagerNet.isTv
                }
            }
        }
        popup.menu.add(
            Menu.NONE,
            R.id.action_import_clipboard,
            1,
            R.string.action_import,
        ).setIcon(R.drawable.ic_content_copy)
        popup.menu.add(
            Menu.NONE,
            R.id.action_import_file,
            2,
            R.string.action_import_file,
        ).setIcon(R.drawable.ic_baseline_folder_open_24)

        val manualSub = popup.menu.addSubMenu(
            Menu.NONE,
            Menu.NONE,
            3,
            R.string.add_profile_methods_manual_settings,
        ).setIcon(R.drawable.ic_baseline_tune_24)
        MANUAL_PROFILE_OPTIONS.forEachIndexed { index, (itemId, titleRes, _) ->
            manualSub.add(Menu.NONE, itemId, index, titleRes)
        }

        enableMenuIcons(popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_scan_qr_code -> {
                    startActivity(Intent(ctx, ScannerActivity::class.java))
                    true
                }

                R.id.action_import_clipboard -> {
                    importClipboardText()
                    true
                }

                R.id.action_import_file -> {
                    startFilesForResult(importFileLauncher, "*/*")
                    true
                }

                else -> {
                    val option = MANUAL_PROFILE_OPTIONS.firstOrNull { it.first == item.itemId } ?: return@setOnMenuItemClickListener false
                    startActivity(Intent(ctx, option.third))
                    true
                }
            }
        }
        popup.show()
    }

    private fun showHomeMoreMenu(anchor: View) {
        val ctx = context ?: return
        val popup = PopupMenu(ctx, anchor, Gravity.END)
        val groupOps = 1
        val navOps = 2

        popup.menu.add(
            groupOps,
            R.id.action_update_subscription,
            0,
            R.string.profiles_update_subscription,
        ).setIcon(R.drawable.ic_baseline_refresh_24)
        popup.menu.add(
            groupOps,
            R.id.action_test_url,
            1,
            R.string.profiles_test_url,
        ).setIcon(R.drawable.ic_test_latency_24)
        popup.menu.add(
            groupOps,
            R.id.action_edit,
            2,
            R.string.home_menu_edit_active_profile,
        ).setIcon(R.drawable.ic_image_edit)
        popup.menu.add(
            groupOps,
            R.id.action_clear_traffic_statistics,
            3,
            R.string.clear_traffic_statistics,
        ).setIcon(R.drawable.ic_device_data_usage)

        popup.menu.add(
            navOps,
            R.id.nav_route,
            4,
            R.string.menu_route,
        ).setIcon(R.drawable.ic_maps_directions)
        popup.menu.add(
            navOps,
            R.id.nav_group,
            5,
            R.string.menu_group,
        ).setIcon(R.drawable.ic_baseline_view_list_24)

        MenuCompat.setGroupDividerEnabled(popup.menu, true)
        enableMenuIcons(popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_update_subscription -> {
                    updateActiveSubscription()
                    true
                }

                R.id.action_test_url -> {
                    if (currentState == BaseService.State.Connected) {
                        runConnectionTest()
                    } else {
                        snackbar(R.string.not_connected).show()
                    }
                    true
                }

                R.id.action_edit -> {
                    openActiveProfileEditor()
                    true
                }

                R.id.action_clear_traffic_statistics -> {
                    clearActiveGroupTraffic()
                    true
                }

                R.id.nav_route, R.id.nav_group -> {
                    (activity as? MainActivity)?.displayFragmentWithId(item.itemId)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun importClipboardText() {
        val act = activity ?: return
        val text = SagerNet.getClipboardText()
        if (text.isBlank()) {
            snackbar(R.string.clipboard_empty).show()
        } else {
            SubscribeFlows.importText(act, text, resolveHomeTargetGroupId())
        }
    }

    private fun importFileUri(uri: Uri) {
        val act = activity ?: return
        val ctx = context ?: return
        val groupId = resolveHomeTargetGroupId()
        val resolver = ctx.contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val handledNonZip = withContext(Dispatchers.IO) {
                    val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                    fileName?.endsWith(".zip", ignoreCase = true) != true
                }
                if (handledNonZip) {
                    SubscribeFlows.importUri(act, uri, groupId)
                    return@launch
                }

                val (outbounds, report) = withContext(Dispatchers.IO) {
                    val list = mutableListOf<Outbound>()
                    val impReport = ImportReport()
                    val preference = SettingsMapper.xrayVlessPreference()
                    val importCtx = SettingsMapper.buildContext()
                    ZipInputStream(resolver.openInputStream(uri)!!).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.isDirectory) continue
                            val result = ProfileImport.parse(zip.bufferedReader().readText(), preference, importCtx)
                            for (message in result.messages) Logs.w(message)
                            impReport.addAll(result.reports)
                            list.addAll(result.outbounds)
                            zip.closeEntry()
                        }
                    }
                    if (list.isNotEmpty()) ProfileManager.addProfileBatch(list, groupId)
                    impReport.setImported(list.size)
                    list to impReport
                }
                if (view == null) return@launch
                val currentCtx = context ?: return@launch
                when {
                    report.hasProblems() ->
                        SubscriptionReportDialog.show(currentCtx, getString(R.string.validation_import_title), report.text())

                    outbounds.isEmpty() -> snackbar(R.string.no_proxies_found_in_file).show()
                    else -> snackbar(resources.getQuantityString(R.plurals.added, outbounds.size, outbounds.size)).show()
                }
                refreshAllCards()
            } catch (e: Exception) {
                Logs.w(e)
                if (view != null) snackbar(e.readableMessage).show()
            }
        }
    }

    private fun openActiveProfileEditor() {
        val targetId = activeTargetProfileId()
        if (targetId <= 0L) {
            snackbar(R.string.home_status_tap_to_select).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val entity = SagerDatabase.proxyDao.getById(targetId) ?: return@withContext null
                val isSub = SagerDatabase.groupDao.getById(entity.groupId)?.isSubscription == true
                entity to isSub
            }
            val ctx = context ?: return@launch
            if (loaded == null) {
                snackbar(R.string.home_status_tap_to_select).show()
                return@launch
            }
            startActivity(loaded.first.profileSettingsIntent(ctx, loaded.second))
        }
    }

    private fun updateActiveSubscription() {
        viewLifecycleOwner.lifecycleScope.launch {
            val group = withContext(Dispatchers.IO) { resolveActiveGroup() }
            if (group != null && group.isSubscription) {
                SubscriptionClient.refreshGroup(group.id, showDiff = true)
            } else {
                snackbar(R.string.grp_no_subscription_url).show()
            }
        }
    }

    private fun clearActiveGroupTraffic() {
        val ctx = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val group = withContext(Dispatchers.IO) { resolveActiveGroup() } ?: return@launch
            if (view == null) return@launch
            ctx.confirmAction(
                getString(R.string.confirm_clear_traffic),
                group.displayName(),
                R.string.confirm_clear,
            ) {
                val service = (activity as? MainActivity)?.connection?.service
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val ids = ProfileManager.memberIds(group.id).toLongArray()
                        try {
                            service?.resetTraffic(ids)
                        } catch (e: Exception) {
                            Logs.w(e)
                        }
                        ProfileManager.resetTraffic(ids)
                    }
                    refreshTrafficTotals()
                }
            }
        }
    }

    private fun runQuickStunTest() {
        val ctx = context ?: return
        stunDiagJob?.cancel()
        diagStunProgress?.isVisible = true
        diagStunValue?.text = getString(R.string.connection_test_testing)
        diagStunValue?.setTextColor(ctx.getColorAttr(android.R.attr.textColorPrimary))
        diagStunMeta?.text = getString(R.string.home_diag_stun_idle_meta)

        val tester = StunNatTester(DefaultStunSocketFactory())
        stunDiagJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val results = tester.test(
                    servers = StunPreset.FAST.servers,
                    ipv6Mode = if (DataStore.vpnIpv6) 1 else 0,
                    enableQualityBurst = false,
                    enableCrossServerFallback = true,
                )
                if (view == null) return@launch
                val currentCtx = context ?: return@launch
                val assessment = StunAssessmentPolicy.assess(results)
                val rep = assessment.representative ?: results.firstOrNull { it.bindingSuccess }
                val errLabel = getString(R.string.home_diag_error)
                val code = when (rep?.natType) {
                    StunProtocolCodes.NAT_NONE -> "OPEN"
                    StunProtocolCodes.NAT_FULL -> "NAT1"
                    StunProtocolCodes.NAT_RESTRICTED -> "NAT2"
                    StunProtocolCodes.NAT_PORT_RESTRICTED -> "NAT3"
                    StunProtocolCodes.NAT_SYMMETRIC -> "NAT4"
                    StunProtocolCodes.NAT_CONE -> "NAT1-3"
                    StunProtocolCodes.NAT_BLOCKED -> "BLOCKED"
                    StunProtocolCodes.NAT_SYMMETRIC_UDP_FIREWALL -> "FW"
                    else -> when (assessment.kind) {
                        StunAssessmentKind.INCONSISTENT -> "MIXED"
                        StunAssessmentKind.BASIC_ONLY -> "UDP"
                        else -> errLabel
                    }
                }
                val rtt = when {
                    rep != null && rep.medianRttMs >= 0.0 -> rep.medianRttMs.toInt().coerceAtLeast(1)
                    rep != null && rep.rttMs > 0L -> rep.rttMs.toInt()
                    else -> 0
                }
                diagStunValue?.text = if (rtt > 0 && code != errLabel) "$code · ${rtt}ms" else code
                val role = when (rep?.natType) {
                    StunProtocolCodes.NAT_NONE,
                    StunProtocolCodes.NAT_FULL,
                    StunProtocolCodes.NAT_RESTRICTED,
                    -> ExpressiveSettingsHelper.SemanticRole.SUCCESS

                    StunProtocolCodes.NAT_PORT_RESTRICTED,
                    StunProtocolCodes.NAT_CONE,
                    -> ExpressiveSettingsHelper.SemanticRole.WARNING

                    StunProtocolCodes.NAT_SYMMETRIC,
                    StunProtocolCodes.NAT_BLOCKED,
                    StunProtocolCodes.NAT_SYMMETRIC_UDP_FIREWALL,
                    -> ExpressiveSettingsHelper.SemanticRole.ERROR

                    else -> when (assessment.kind) {
                        StunAssessmentKind.BASIC_ONLY,
                        StunAssessmentKind.INCONSISTENT,
                        -> ExpressiveSettingsHelper.SemanticRole.WARNING

                        else -> ExpressiveSettingsHelper.SemanticRole.ERROR
                    }
                }
                diagStunValue?.setTextColor(ExpressiveSettingsHelper.resolveSemanticColor(currentCtx, role))
                diagStunMeta?.text = rep?.externalAddress?.takeIf { it.isNotEmpty() }
                    ?: getString(stunAssessmentLabelRes(assessment.kind))
            } catch (e: CancellationException) {
                tester.cancel()
                throw e
            } catch (_: Throwable) {
                if (view != null) {
                    context?.let { currentCtx ->
                        diagStunValue?.text = getString(R.string.home_diag_error)
                        diagStunValue?.setTextColor(
                            ExpressiveSettingsHelper.resolveSemanticColor(
                                currentCtx,
                                ExpressiveSettingsHelper.SemanticRole.ERROR,
                            ),
                        )
                    }
                }
            } finally {
                if (view != null) diagStunProgress?.isVisible = false
            }
        }
    }

    private fun stunAssessmentLabelRes(kind: StunAssessmentKind): Int = when (kind) {
        StunAssessmentKind.FAVORABLE -> R.string.stun_assessment_favorable
        StunAssessmentKind.MODERATE -> R.string.stun_assessment_moderate
        StunAssessmentKind.RESTRICTIVE -> R.string.stun_assessment_restrictive
        StunAssessmentKind.OPEN -> R.string.stun_assessment_open
        StunAssessmentKind.INCONSISTENT -> R.string.stun_assessment_inconsistent
        StunAssessmentKind.BASIC_ONLY -> R.string.stun_assessment_basic
        StunAssessmentKind.FAILED -> R.string.stun_assessment_failed
        StunAssessmentKind.CANCELLED -> R.string.stun_assessment_cancelled
    }

    private fun runQuickDnsLeakTest() {
        val ctx = context ?: return
        val appCtx = ctx.applicationContext
        dnsDiagJob?.cancel()
        diagDnsProgress?.isVisible = true
        diagDnsValue?.text = getString(R.string.connection_test_testing)
        diagDnsValue?.setTextColor(ctx.getColorAttr(android.R.attr.textColorPrimary))
        diagDnsMeta?.text = getString(R.string.home_diag_dns_idle_meta)

        dnsDiagJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val probe = withContext(Dispatchers.IO) {
                    val cm = appCtx.getSystemService(ConnectivityManager::class.java)
                    val activeNet = cm?.activeNetwork
                    val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
                    val link = activeNet?.let { cm.getLinkProperties(it) }
                    val isVpnConnected = DataStore.serviceState.connected &&
                        (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true ||
                            link?.interfaceName?.startsWith("tun") == true)
                    val activeIface = link?.interfaceName ?: if (isVpnConnected) "tun0" else "wlan0"

                    val physicalDns = mutableListOf<String>()
                    cm?.allNetworks?.forEach { net ->
                        val c = cm.getNetworkCapabilities(net)
                        if (c != null && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                            cm.getLinkProperties(net)?.dnsServers?.forEach { dns ->
                                val host = dns.hostAddress.orEmpty()
                                if (host.isNotBlank()) physicalDns.add(host)
                            }
                        }
                    }

                    val sampleIp = runCatching {
                        InetAddress.getAllByName("www.google.com").firstOrNull()?.hostAddress
                    }.getOrNull()
                    val fakeIpDetected = sampleIp != null &&
                        (sampleIp.startsWith("198.18.") || sampleIp.startsWith("198.19."))

                    var resolverIp: String? = null
                    var resolverGeo: String? = null
                    runCatching {
                        val req = Request.Builder()
                            .url("https://edns.ip-api.com/json")
                            .header("User-Agent", appUserAgent())
                            .build()
                        val client = newHttpClient(viaProxy = false, timeoutSeconds = 8)
                            .newBuilder()
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .build()
                        client.newCall(req).execute().use { resp ->
                            val json = JSONObject(resp.body?.string().orEmpty())
                            val dnsObj = json.optJSONObject("dns")
                            if (dnsObj != null) {
                                resolverIp = dnsObj.optString("ip").takeIf { it.isNotBlank() }
                                resolverGeo = dnsObj.optString("geo").takeIf { it.isNotBlank() }
                            }
                        }
                    }

                    arrayOf<Any?>(
                        isVpnConnected,
                        activeIface,
                        physicalDns,
                        fakeIpDetected,
                        sampleIp,
                        resolverIp,
                        resolverGeo,
                    )
                }

                if (view == null) return@launch
                val currentCtx = context ?: return@launch
                val isVpnConnected = probe[0] as Boolean
                val activeIface = probe[1] as String
                @Suppress("UNCHECKED_CAST")
                val physicalDns = probe[2] as List<String>
                val fakeIpDetected = probe[3] as Boolean
                val sampleIp = probe[4] as String?
                val resolverIp = probe[5] as String?
                val rawGeo = probe[6] as String?
                val resolverGeo = rawGeo
                    ?.substringAfter(" - ", rawGeo)
                    ?.replace(", Inc.", "")
                    ?.replace(", LLC", "")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

                val (labelRes, role, metaText) = when {
                    fakeIpDetected -> Triple(
                        R.string.home_diag_dns_fakeip,
                        ExpressiveSettingsHelper.SemanticRole.SUCCESS,
                        "198.18.0.0/15",
                    )

                    !isVpnConnected -> Triple(
                        R.string.home_diag_dns_direct,
                        ExpressiveSettingsHelper.SemanticRole.INFO,
                        resolverGeo ?: resolverIp ?: activeIface,
                    )

                    resolverIp != null && physicalDns.contains(resolverIp) -> Triple(
                        R.string.home_diag_dns_leak,
                        ExpressiveSettingsHelper.SemanticRole.ERROR,
                        resolverGeo ?: resolverIp,
                    )

                    isVpnConnected && (resolverIp != null || sampleIp != null) -> Triple(
                        R.string.home_diag_dns_safe,
                        ExpressiveSettingsHelper.SemanticRole.SUCCESS,
                        resolverGeo ?: resolverIp ?: activeIface,
                    )

                    else -> Triple(
                        R.string.home_diag_error,
                        ExpressiveSettingsHelper.SemanticRole.ERROR,
                        activeIface,
                    )
                }

                diagDnsValue?.text = getString(labelRes)
                diagDnsValue?.setTextColor(ExpressiveSettingsHelper.resolveSemanticColor(currentCtx, role))
                diagDnsMeta?.text = metaText
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                if (view != null) {
                    context?.let { currentCtx ->
                        diagDnsValue?.text = getString(R.string.home_diag_error)
                        diagDnsValue?.setTextColor(
                            ExpressiveSettingsHelper.resolveSemanticColor(
                                currentCtx,
                                ExpressiveSettingsHelper.SemanticRole.ERROR,
                            ),
                        )
                    }
                }
            } finally {
                if (view != null) diagDnsProgress?.isVisible = false
            }
        }
    }

    private fun runQuickConnectivityTest() {
        val ctx = context ?: return
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
        val quickIds = setOf("openai", "youtube", "netflix", "telegram", "google", "github")
        val targets = ConnectivityChecker.SERVICES.filter { it.id in quickIds }
        if (targets.isEmpty()) return

        connDiagJob?.cancel()
        diagConnProgress?.isVisible = true
        diagConnValue?.text = getString(R.string.connection_test_testing)
        diagConnValue?.setTextColor(ctx.getColorAttr(android.R.attr.textColorPrimary))
        diagConnMeta?.text = "0/${targets.size}"

        connDiagJob = viewLifecycleOwner.lifecycleScope.launch {
            val resultsMap = LinkedHashMap<String, ServiceProbeResult>()
            try {
                ConnectivityChecker.runChecks(
                    targets,
                    viaProxy = DataStore.serviceState.connected,
                    cm,
                ).collect { res ->
                    resultsMap[res.spec.id] = res
                    if (view != null) {
                        val doneCount = resultsMap.values.count {
                            it.status != ConnectivityStatus.PENDING && it.status != ConnectivityStatus.TESTING
                        }
                        diagConnMeta?.text = "$doneCount/${targets.size}"
                    }
                }
                if (view == null) return@launch
                val currentCtx = context ?: return@launch
                val okResults = resultsMap.values.filter {
                    it.status == ConnectivityStatus.AVAILABLE || it.status == ConnectivityStatus.PARTIAL
                }
                val okCount = okResults.size
                val avgRtt = okResults.map { it.rttMs }.filter { it > 0L }
                    .takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L

                diagConnValue?.text = getString(R.string.home_diag_conn_ok, okCount, targets.size)
                val role = when {
                    okCount == targets.size -> ExpressiveSettingsHelper.SemanticRole.SUCCESS
                    okCount > 0 -> ExpressiveSettingsHelper.SemanticRole.WARNING
                    else -> ExpressiveSettingsHelper.SemanticRole.ERROR
                }
                diagConnValue?.setTextColor(ExpressiveSettingsHelper.resolveSemanticColor(currentCtx, role))
                diagConnMeta?.text = if (avgRtt > 0L) {
                    "~${avgRtt} ms"
                } else {
                    getString(R.string.home_diag_conn_idle_meta)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                if (view != null) {
                    context?.let { currentCtx ->
                        diagConnValue?.text = getString(R.string.home_diag_error)
                        diagConnValue?.setTextColor(
                            ExpressiveSettingsHelper.resolveSemanticColor(
                                currentCtx,
                                ExpressiveSettingsHelper.SemanticRole.ERROR,
                            ),
                        )
                    }
                }
            } finally {
                if (view != null) diagConnProgress?.isVisible = false
            }
        }
    }
}
