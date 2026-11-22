package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import java.io.File
import java.net.InetAddress
import java.util.Collections
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import android.widget.AdapterView
import android.widget.ArrayAdapter
import io.nekohasekai.sagernet.hotspotshare.FailClosedAction
import io.nekohasekai.sagernet.hotspotshare.HotspotShareLogger
import io.nekohasekai.sagernet.hotspotshare.HotspotShareStateStore
import io.nekohasekai.sagernet.hotspotshare.HotspotTrafficMonitor
import io.nekohasekai.sagernet.hotspotshare.RootBackend
import io.nekohasekai.sagernet.hotspotshare.ShareFailure
import io.nekohasekai.sagernet.hotspotshare.ShareMethod
import io.nekohasekai.sagernet.hotspotshare.SharePreference
import io.nekohasekai.sagernet.hotspotshare.ShareState
import io.nekohasekai.sagernet.hotspotshare.ShizukuBackend
import io.nekohasekai.sagernet.hotspotshare.TetherWatchService
import io.nekohasekai.sagernet.hotspotshare.shizuku.ShizukuWatchdog
import rikka.shizuku.Shizuku
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SettingsRegistry
import io.nekohasekai.sagernet.databinding.ActivityLanSharingBinding
import io.nekohasekai.sagernet.utils.PlatformNotifications
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import kotlin.math.abs
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.widget.applyListInsets
import io.nekohasekai.sagernet.utils.ClientShareRoute
import io.nekohasekai.sagernet.utils.HotspotCandidate
import io.nekohasekai.sagernet.utils.HotspotGatewayManager
import io.nekohasekai.sagernet.utils.HotspotGatewaySnapshot
import io.nekohasekai.sagernet.utils.HotspotGatewayState
import io.nekohasekai.sagernet.utils.HotspotGatewayStatus
import io.nekohasekai.sagernet.utils.LanClientDevice
import io.nekohasekai.sagernet.utils.LanSharingLogics
import io.nekohasekai.sagernet.utils.LanSharingNetwork
import io.nekohasekai.sagernet.utils.ShizukuTunnelTraffic
import io.nekohasekai.sagernet.utils.TetherKind
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LanSharingActivity : ThemedActivity(), SagerConnection.Callback {

    private lateinit var binding: ActivityLanSharingBinding
    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
    private val clientHistory = ConcurrentHashMap<String, LanClientDevice>()
    private var statusReceiver: BroadcastReceiver? = null
    private var lanSharingToggleChecked = false
    private var gatewayToggleChecked = false
    private var hotspotShareToggleChecked = false
    private var suppressLogsSwitchCallback = false
    private var suppressMainTabCallback = false
    private var suppressSubTabCallback = false
    private var selectedTabIndex: Int = TAB_HOTSPOT_SHARE
    private var selectedLanProxySubTabIndex: Int = SUBTAB_LAN_MAIN
    private var lastLoopbackHealMs: Long = 0L
    private var touchDownInTabBar: Boolean = false
    private var touchDownInLogBox: Boolean = false
    private var swipeTriggeredThisGesture: Boolean = false
    private lateinit var horizontalSwipeDetector: GestureDetector
    private var currentRawShareLogs: String = ""
    private var logRefreshJob: Job? = null
    private var logRenderJob: Job? = null
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != SHIZUKU_PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        lifecycleScope.launch {
            cachedShizukuGranted = granted
            if (pendingStartAfterShizukuPermission) {
                pendingStartAfterShizukuPermission = false
                val shouldStart = withContext(Dispatchers.IO) {
                    if (granted) {
                        DataStore.hotspotShareEnabled
                    } else {
                        DataStore.hotspotShareEnabled = false
                        HotspotShareStateStore.publish(ShareState.Error(ShareFailure.SHIZUKU_PERMISSION_DENIED))
                        false
                    }
                }
                if (shouldStart && !isFinishing && !isDestroyed) {
                    TetherWatchService.start(this@LanSharingActivity)
                }
            }
            if (!isFinishing && !isDestroyed) {
                refreshNetworkAndState()
            }
        }
    }

    private var detectedHotspotCandidate: HotspotCandidate? = null
    @Volatile
    private var detectedHotspotCandidates: List<HotspotCandidate> = emptyList()
    private var detectedWifiIp: String? = null
    private var hotspotActive: Boolean = false
    @Volatile
    private var activeTetherKinds: Set<TetherKind> = emptySet()
    @Volatile
    private var activeTetherMask: Int = 0
    private val clientInterfaceMap = ConcurrentHashMap<String, String>()
    private var tetherToggleInFlight: Boolean = false

    @Volatile
    private var cachedShizukuRunning: Boolean = false

    @Volatile
    private var cachedShizukuGranted: Boolean = false

    @Volatile
    private var cachedRootAvailable: Boolean = false

    @Volatile
    private var rootCheckedOnce: Boolean = false

    @Volatile
    private var cachedShizukuClientCount: Int = 0

    // Confined to Dispatchers.Main; persisted across config changes via onSaveInstanceState.
    private var pendingStartAfterShizukuPermission: Boolean = false
    private var lastHeroBadgeState: String? = null
    private var lastHotspotBadgeActive: Boolean? = null
    private var lastWifiBadgeActive: Boolean? = null
    private var lastDevicesBadgeActive: Boolean? = null
    private var lastShareBadgeKey: String? = null
    private var lastLanProxyBadgeKey: String? = null
    private var lastActiveShareMethod: ShareMethod? = null
    private var lastRenderedDeviceKey: String? = null
    private var lastLanProxyTrafficSample: LanSharingLogics.LanProxyTrafficSample? = null
    private var lastLanProxyTrafficDisplay: LanSharingLogics.LanProxyTrafficDisplay? = null
    private var cachedAdb5555Open: Boolean = false
    private var cachedWatchdogFlagExists: Boolean = false
    private val hostnameLookupCache = ConcurrentHashMap<String, String>()
    private val hostnameLookupsInFlight: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private var currentLogSourceFilter: HotspotShareLogger.LogSource = HotspotShareLogger.LogSource.ALL
    private var currentLogLevelFilter: HotspotShareLogger.LogLevelFilter = HotspotShareLogger.LogLevelFilter.ALL
    private var currentLogSearchQuery: String = ""
    private var currentLogNewestFirst: Boolean = true
    private var suppressFilterChipCallbacks: Boolean = false
    private var lastRenderedLogEnabledState: Boolean? = null
    private var initialSettingsLoaded: Boolean = false
    private var lastAppliedShareMethod: String = "auto"
    private var lastAppliedFailClosed: String = "block"
    private val shareMethodCodes = listOf("auto", "shizuku", "root", "lan_proxy")
    private val failClosedCodes = listOf("block", "stop")

    private var netStateReceiver: BroadcastReceiver? = null
    private var refreshNetworkJob: Job? = null

    @Volatile
    private var pendingForceRootCheck: Boolean = false

    @Volatile
    private var lastMaskQueryElapsedMs: Long = 0L

    private val gatewayChangedListener: () -> Unit = {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) renderUiState()
        }
    }

    private companion object {
        // System broadcasts that must re-detect the hotspot IP: AP state flips and tethering changes
        // are sent by the OS, therefore the receiver is registered RECEIVER_EXPORTED.
        const val WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 5051
        const val STATE_PENDING_SHIZUKU_START = "state_pending_shizuku_start"
        const val STATE_SELECTED_TAB = "state_selected_tab"
        const val STATE_SELECTED_LAN_SUBTAB = "state_selected_lan_subtab"
        const val TAB_HOTSPOT_SHARE = 0
        const val TAB_LAN_PROXY = 1
        const val TAB_DEVICES_AUTO = 2
        const val TAB_LOGS = 3
        const val SUBTAB_LAN_MAIN = 0
        const val SUBTAB_AUTO_CONNECT = 1
        const val SUBTAB_SHARE_CARDS = 2
        const val LOOPBACK_HEAL_COOLDOWN_MS = 5000L
        const val POLL_INTERVAL_MS = 4500L
        const val MASK_DEDUP_WINDOW_MS = 1500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingStartAfterShizukuPermission = savedInstanceState?.getBoolean(STATE_PENDING_SHIZUKU_START, false) ?: false
        selectedTabIndex = savedInstanceState?.getInt(STATE_SELECTED_TAB, TAB_HOTSPOT_SHARE) ?: TAB_HOTSPOT_SHARE
        selectedLanProxySubTabIndex =
            savedInstanceState?.getInt(STATE_SELECTED_LAN_SUBTAB, SUBTAB_LAN_MAIN) ?: SUBTAB_LAN_MAIN
        binding = ActivityLanSharingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ExpressiveSettingsHelper.attachActivityFloatingHeader(
            this,
            binding.root,
            getString(R.string.lan_sharing)
        )
        binding.scrollLanSharing.applyListInsets()
        binding.scrollLanSharing.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (selectedTabIndex == TAB_LOGS && (bottom - top) != (oldBottom - oldTop)) {
                updateLogsBoxHeightToFillViewport()
            }
        }
        styleExpressiveViews()
        initHorizontalSwipeDetector()

        setupTabs()
        setupLanProxySubTabs()
        setupViews()
        setupHotspotShareViews()
        setupLogViews()
        loadInitialSettingsAsync()
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {}
        registerStatusUpdates()
        registerSystemNetReceivers()
        connection.connect(this, this)
        startPollingLoop()
    }

    override fun onResume() {
        super.onResume()
        TetherWatchService.setUiForeground(true)
        // Coming back to the screen re-detects: the hotspot subnet changes between openings.
        refreshNetworkAndState()
        ensureLanProxyBoundOnLoopbackMismatch()
        if (selectedTabIndex == TAB_LOGS) {
            updateLogsBoxHeightToFillViewport()
            refreshInlineLogs(scrollToBottom = false, forceRebind = true)
        }
        if (pendingStartAfterShizukuPermission) {
            lifecycleScope.launch {
                val granted = withContext(Dispatchers.IO) {
                    ShizukuBackend.isShizukuRunning() && ShizukuBackend.isShizukuGranted(this@LanSharingActivity)
                }
                if (granted && pendingStartAfterShizukuPermission) {
                    pendingStartAfterShizukuPermission = false
                    val enabled = withContext(Dispatchers.IO) { DataStore.hotspotShareEnabled }
                    if (enabled && !isFinishing && !isDestroyed) {
                        TetherWatchService.start(this@LanSharingActivity)
                    }
                }
            }
        }
    }

    override fun onPause() {
        TetherWatchService.setUiForeground(false)
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.lan_sharing_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_refresh -> {
                refreshNetworkAndState(forceRootCheck = true)
                lifecycleScope.launch { pollClientDevicesOnce(force = true) }
                true
            }
            R.id.action_config -> {
                showHotspotDialog { saved ->
                    if (saved) {
                        refreshNetworkAndState()
                        if (DataStore.hotspotGatewayEnabled) {
                            lifecycleScope.launch {
                                withContext(Dispatchers.Default) {
                                    HotspotGatewayManager.checkAndUpdate(forceReload = true)
                                }
                                if (!isFinishing && !isDestroyed) renderUiState()
                            }
                        }
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        DataStore.serviceState = state
        refreshNetworkAndState()
        lifecycleScope.launch { pollClientDevicesOnce() }
    }

    override fun onServiceConnected(service: ISagerNetService) {
        lifecycleScope.launch {
            val stateOrdinal = withContext(Dispatchers.IO) {
                runCatching { service.state }.getOrNull()
            }
            if (isFinishing || isDestroyed) return@launch
            stateOrdinal?.let { ordinal ->
                BaseService.State.entries.getOrNull(ordinal)?.let { DataStore.serviceState = it }
            }
            refreshNetworkAndState()
            ensureLanProxyBoundOnLoopbackMismatch()
            pollClientDevicesOnce()
        }
    }

    private fun setupTabs() {
        val tabLayout = binding.tabLanSections
        tabLayout.removeAllTabs()
        val tabTitles = listOf(
            R.string.lan_tab_hotspot_share,
            R.string.lan_tab_local_proxy,
            R.string.lan_tab_devices_auto,
            R.string.lan_tab_logs,
        )
        tabTitles.forEachIndexed { idx, titleRes ->
            tabLayout.addTab(
                tabLayout.newTab().setText(titleRes),
                idx == selectedTabIndex,
            )
        }
        ExpressiveSettingsHelper.styleExpressiveTabs(binding.lanTabContainer, tabLayout)
        ExpressiveSettingsHelper.fitTabsToContainer(tabLayout)
        selectTabSection(selectedTabIndex)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (suppressMainTabCallback) return
                val direction = if (tab.position >= selectedTabIndex) 1 else -1
                selectTabSection(tab.position, animate = true, swipeDirection = direction)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {
                if (tab.position == TAB_LOGS) {
                    refreshInlineLogs(scrollToBottom = true)
                }
            }
        })
    }

    private fun setupLanProxySubTabs() {
        val tabLayout = binding.tabLanProxySubSections
        tabLayout.removeAllTabs()
        val subTitles = listOf(
            R.string.lan_subtab_proxy,
            R.string.lan_subtab_auto_connect,
            R.string.lan_subtab_share_cards,
        )
        subTitles.forEachIndexed { idx, titleRes ->
            tabLayout.addTab(
                tabLayout.newTab().setText(titleRes),
                idx == selectedLanProxySubTabIndex,
            )
        }
        ExpressiveSettingsHelper.styleExpressiveTabs(binding.lanProxySubTabContainer, tabLayout, cornerRadiusDp = 12f)
        ExpressiveSettingsHelper.fitTabsToContainer(tabLayout)
        selectLanProxySubTabSection(selectedLanProxySubTabIndex)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (suppressSubTabCallback) return
                val direction = if (tab.position >= selectedLanProxySubTabIndex) 1 else -1
                selectLanProxySubTabSection(tab.position, animate = true, swipeDirection = direction)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun selectTabSection(index: Int, animate: Boolean = false, swipeDirection: Int = 0) {
        val clamped = index.coerceIn(TAB_HOTSPOT_SHARE, TAB_LOGS)
        selectedTabIndex = clamped
        binding.tabLanSections.getTabAt(clamped)?.let { tab ->
            if (!tab.isSelected) {
                suppressMainTabCallback = true
                tab.select()
                suppressMainTabCallback = false
            }
        }
        binding.containerTabHotspotShare.visibility = if (clamped == TAB_HOTSPOT_SHARE) View.VISIBLE else View.GONE
        binding.containerTabLanProxy.visibility = if (clamped == TAB_LAN_PROXY) View.VISIBLE else View.GONE
        binding.containerTabDevices.visibility = if (clamped == TAB_DEVICES_AUTO) View.VISIBLE else View.GONE
        binding.containerTabLogs.visibility = if (clamped == TAB_LOGS) View.VISIBLE else View.GONE

        val activeView = when (clamped) {
            TAB_HOTSPOT_SHARE -> binding.containerTabHotspotShare
            TAB_LAN_PROXY -> {
                ExpressiveSettingsHelper.fitTabsToContainer(binding.tabLanProxySubSections)
                binding.containerTabLanProxy
            }
            TAB_DEVICES_AUTO -> binding.containerTabDevices
            else -> binding.containerTabLogs
        }
        if (animate) {
            animateSectionTransition(activeView, swipeDirection)
        }
        if (clamped == TAB_LOGS) {
            updateLogsBoxHeightToFillViewport()
            refreshInlineLogs(scrollToBottom = true)
        }
    }

    private fun selectLanProxySubTabSection(index: Int, animate: Boolean = false, swipeDirection: Int = 0) {
        val clamped = index.coerceIn(SUBTAB_LAN_MAIN, SUBTAB_SHARE_CARDS)
        selectedLanProxySubTabIndex = clamped
        binding.tabLanProxySubSections.getTabAt(clamped)?.let { tab ->
            if (!tab.isSelected) {
                suppressSubTabCallback = true
                tab.select()
                suppressSubTabCallback = false
            }
        }
        binding.containerSubTabLanProxyMain.visibility = if (clamped == SUBTAB_LAN_MAIN) View.VISIBLE else View.GONE
        binding.containerSubTabAutoConnect.visibility = if (clamped == SUBTAB_AUTO_CONNECT) View.VISIBLE else View.GONE
        binding.containerSubTabShareCards.visibility = if (clamped == SUBTAB_SHARE_CARDS) View.VISIBLE else View.GONE

        if (animate) {
            val activeView = when (clamped) {
                SUBTAB_LAN_MAIN -> binding.containerSubTabLanProxyMain
                SUBTAB_AUTO_CONNECT -> binding.containerSubTabAutoConnect
                else -> binding.containerSubTabShareCards
            }
            animateSectionTransition(activeView, swipeDirection)
        }
    }

    private fun initHorizontalSwipeDetector() {
        horizontalSwipeDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (e1 == null || touchDownInTabBar) return false
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    val minDistance = dpToPx(if (touchDownInLogBox) 92 else 72).toFloat()
                    val minRatio = if (touchDownInLogBox) 2.2f else 1.6f
                    val minVelocity = dpToPx(220).toFloat()
                    if (abs(dx) < minDistance || abs(dx) <= abs(dy) * minRatio || abs(velocityX) < minVelocity) {
                        return false
                    }
                    return handleHorizontalSwipe(forward = dx < 0f)
                }
            },
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!::binding.isInitialized || !::horizontalSwipeDetector.isInitialized) {
            return super.dispatchTouchEvent(ev)
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            swipeTriggeredThisGesture = false
            touchDownInTabBar = isTouchInsideView(ev, binding.lanTabContainer) ||
                (binding.lanProxySubTabContainer.visibility == View.VISIBLE &&
                    isTouchInsideView(ev, binding.lanProxySubTabContainer))
            touchDownInLogBox = selectedTabIndex == TAB_LOGS && (
                isTouchInsideView(ev, binding.boxInlineShareLogs) ||
                    (binding.drawerLogFilters.visibility == View.VISIBLE &&
                        isTouchInsideView(ev, binding.editLogSearch))
                )
        }
        val detected = horizontalSwipeDetector.onTouchEvent(ev)
        if (detected && swipeTriggeredThisGesture && ev.actionMasked == MotionEvent.ACTION_UP) {
            val cancelEvent = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
            val handled = super.dispatchTouchEvent(cancelEvent)
            cancelEvent.recycle()
            return handled
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun handleHorizontalSwipe(forward: Boolean): Boolean {
        if (forward) {
            if (selectedTabIndex == TAB_LAN_PROXY && selectedLanProxySubTabIndex < SUBTAB_SHARE_CARDS) {
                selectLanProxySubTabSection(selectedLanProxySubTabIndex + 1, animate = true, swipeDirection = 1)
                swipeTriggeredThisGesture = true
                return true
            }
            if (selectedTabIndex < TAB_LOGS) {
                val nextMain = selectedTabIndex + 1
                if (nextMain == TAB_LAN_PROXY) {
                    selectLanProxySubTabSection(SUBTAB_LAN_MAIN, animate = false)
                }
                selectTabSection(nextMain, animate = true, swipeDirection = 1)
                swipeTriggeredThisGesture = true
                return true
            }
        } else {
            if (selectedTabIndex == TAB_LAN_PROXY && selectedLanProxySubTabIndex > SUBTAB_LAN_MAIN) {
                selectLanProxySubTabSection(selectedLanProxySubTabIndex - 1, animate = true, swipeDirection = -1)
                swipeTriggeredThisGesture = true
                return true
            }
            if (selectedTabIndex > TAB_HOTSPOT_SHARE) {
                val prevMain = selectedTabIndex - 1
                if (prevMain == TAB_LAN_PROXY) {
                    selectLanProxySubTabSection(SUBTAB_SHARE_CARDS, animate = false)
                }
                selectTabSection(prevMain, animate = true, swipeDirection = -1)
                swipeTriggeredThisGesture = true
                return true
            }
        }
        return false
    }

    private fun animateSectionTransition(target: View, swipeDirection: Int) {
        target.animate().cancel()
        val shiftPx = dpToPx(24).toFloat() * when {
            swipeDirection > 0 -> 1f
            swipeDirection < 0 -> -1f
            else -> 0f
        }
        target.alpha = 0f
        target.translationX = shiftPx
        target.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(180L)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    private fun isTouchInsideView(ev: MotionEvent, view: View): Boolean {
        if (view.visibility != View.VISIBLE || !view.isShown) return false
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val x = ev.rawX
        val y = ev.rawY
        return x >= loc[0] && x <= loc[0] + view.width && y >= loc[1] && y <= loc[1] + view.height
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun isLiveVpnConnected(): Boolean {
        if (DataStore.serviceState.started) return true
        val service = connection.service ?: return false
        return runCatching {
            val state = BaseService.State.entries.getOrNull(service.state)
            if (state != null) {
                DataStore.serviceState = state
                state.started
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun reloadRunningServiceIfActive() {
        if (isLiveVpnConnected()) {
            SagerNet.reloadService()
        }
    }

    private fun reloadRunningServiceIfActiveAsync() {
        lifecycleScope.launch(Dispatchers.IO) {
            reloadRunningServiceIfActive()
        }
    }

    private fun ensureLanProxyBoundOnLoopbackMismatch() {
        val now = System.currentTimeMillis()
        if (now - lastLoopbackHealMs < LOOPBACK_HEAL_COOLDOWN_MS) return
        lifecycleScope.launch {
            val reloaded = withContext(Dispatchers.IO) {
                if (!DataStore.allowLanAccess || DataStore.disableMixedInbound || DataStore.randomInboundPort) {
                    return@withContext false
                }
                val vpnRunning = DataStore.serviceState.connected || (connection.service?.let { svc ->
                    runCatching { BaseService.State.entries.getOrNull(svc.state)?.connected == true }.getOrDefault(false)
                } == true)
                if (!vpnRunning) return@withContext false

                val nonLoopbackIps = LanSharingNetwork.findLocalNonLoopbackIpv4Addresses()
                if (!LanSharingNetwork.isPortBoundLoopbackOnly(DataStore.inboundSocksPort, nonLoopbackIps)) {
                    return@withContext false
                }
                if (isFinishing || isDestroyed) return@withContext false
                HotspotShareLogger.warn(
                    "LanProxy",
                    "Inbound ${DataStore.inboundSocksPort} bound on loopback only; reloading service for LAN access",
                )
                SagerNet.reloadService()
                true
            }
            if (reloaded && !isFinishing && !isDestroyed) {
                lastLoopbackHealMs = System.currentTimeMillis()
            }
        }
    }

    private data class InitialLanSettingsState(
        val allowLanAccess: Boolean,
        val hotspotGatewayEnabled: Boolean,
        val hotspotShareEnabled: Boolean,
        val hotspotShareMethod: String,
        val hotspotShareFailClosed: String,
        val shareLogsEnabled: Boolean,
    )

    private fun loadInitialSettingsAsync() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                HotspotShareLogger.ensureDefaultOffMigration()
                val allowLan = DataStore.allowLanAccess
                var clashChanged = false
                if (allowLan) {
                    if (LanSharingLogics.clashApiSecretShouldBeGenerated(DataStore.coreBoxClashApiSecret)) {
                        DataStore.coreBoxClashApiSecret = LanSharingLogics.generateClashApiSecret()
                        clashChanged = true
                    }
                    val clashDecision = LanSharingLogics.ensureClashApiEnabledPort(DataStore.coreBoxClashApi)
                    if (clashDecision.changed) {
                        DataStore.coreBoxClashApi = clashDecision.newRawPort
                        clashChanged = true
                    }
                }
                if (clashChanged) {
                    reloadRunningServiceIfActive()
                }
                InitialLanSettingsState(
                    allowLanAccess = allowLan,
                    hotspotGatewayEnabled = DataStore.hotspotGatewayEnabled,
                    hotspotShareEnabled = DataStore.hotspotShareEnabled,
                    hotspotShareMethod = DataStore.hotspotShareMethod,
                    hotspotShareFailClosed = DataStore.hotspotShareFailClosed,
                    shareLogsEnabled = HotspotShareLogger.isLoggingEnabled(),
                )
            }
            if (isFinishing || isDestroyed) return@launch
            lastAppliedShareMethod = loaded.hotspotShareMethod
            lastAppliedFailClosed = loaded.hotspotShareFailClosed
            initialSettingsLoaded = true
            setShareSwitchCheckedSilent(loaded.allowLanAccess)
            setGatewaySwitchCheckedSilent(loaded.hotspotGatewayEnabled)
            setHotspotShareSwitchCheckedSilent(loaded.hotspotShareEnabled)
            updateShareMethodSelectorText(loaded.hotspotShareMethod)
            updateFailClosedSelectorText(loaded.hotspotShareFailClosed)
            suppressLogsSwitchCallback = true
            binding.switchShareLogsEnabled.isChecked = loaded.shareLogsEnabled
            suppressLogsSwitchCallback = false
            updateInlineLogBoxAppearance(loaded.shareLogsEnabled)
            renderUiState()
        }
    }

    private fun setupViews() {
        binding.btnRefreshLan.setOnClickListener {
            refreshNetworkAndState(forceRootCheck = true)
            lifecycleScope.launch { pollClientDevicesOnce(force = true) }
        }

        // Share (X): the switch is the real LAN inbound switch; turning it writes inbound_address to the LAN
        // or loopback address and reloads the service (InboundSettingsFragment allowLan listener,
        // NetworkSettingsScreens.kt:74-78). It works 100% independently of Shizuku/Root Hotspot Share.
        styleInteractiveToggleButton(binding.btnLanSharingToggle, lanSharingToggleChecked)
        val lanToggleClick = View.OnClickListener {
            io.nekohasekai.sagernet.utils.HapticHelper.perform(
                this,
                binding.btnLanSharingToggle,
                io.nekohasekai.sagernet.utils.HapticHelper.HapticKind.SELECTION,
            )
            val isChecked = !lanSharingToggleChecked
            lanSharingToggleChecked = isChecked
            styleInteractiveToggleButton(binding.btnLanSharingToggle, isChecked)
            DataStore.inboundAddress =
                if (isChecked) SettingsRegistry.LAN_ADDRESS else SettingsRegistry.LOOPBACK_ADDRESS
            if (isChecked) {
                if (DataStore.disableMixedInbound) {
                    DataStore.disableMixedInbound = false
                }
                // Clear stale stopped/error banner only when Hotspot Share is off and not active;
                // never disturb an active or enabled Shizuku / Root Hotspot Share session.
                if (!DataStore.hotspotShareEnabled && HotspotShareStateStore.snapshot() !is ShareState.Active) {
                    HotspotShareStateStore.publish(ShareState.Off)
                    PlatformNotifications.cancelHotspotShareDropped(this)
                }
                // The live client list reads the Clash API /connections endpoint: make sure the API
                // is switched on, and that a secret exists. An existing secret is never overwritten;
                // the reload below makes the core pick up these values immediately.
                if (LanSharingLogics.clashApiSecretShouldBeGenerated(DataStore.coreBoxClashApiSecret)) {
                    DataStore.coreBoxClashApiSecret = LanSharingLogics.generateClashApiSecret()
                }
                val clashDecision = LanSharingLogics.ensureClashApiEnabledPort(DataStore.coreBoxClashApi)
                if (clashDecision.changed) {
                    DataStore.coreBoxClashApi = clashDecision.newRawPort
                }
            } else {
                lastLanProxyTrafficSample = null
                lastLanProxyTrafficDisplay = null
                // Only if Hotspot Share is specifically running in LAN_PROXY mode does turning off
                // the LAN inbound stop that share mode. Shizuku and Root modes remain 100% untouched.
                val currentSnap = HotspotShareStateStore.snapshot()
                val activeMethod = (currentSnap as? ShareState.Active)?.method
                val pref = SharePreference.fromCode(DataStore.hotspotShareMethod)
                if (LanSharingLogics.hotspotShareDependsOnLanProxy(DataStore.hotspotShareEnabled, pref, activeMethod)) {
                    PlatformNotifications.cancelHotspotShareDropped(this)
                    DataStore.hotspotShareEnabled = false
                    setHotspotShareSwitchCheckedSilent(false)
                    if (TetherWatchService.isRunning ||
                        currentSnap is ShareState.Active ||
                        currentSnap is ShareState.Starting
                    ) {
                        TetherWatchService.stop(this)
                    } else {
                        HotspotShareStateStore.publish(ShareState.Off)
                    }
                }
            }
            reloadRunningServiceIfActiveAsync()
            HotspotShareLogger.info(
                "LanProxy",
                if (isChecked) {
                    "LAN Proxy enabled (port=${DataStore.inboundSocksPort}, auth=${DataStore.inboundAuth})"
                } else {
                    "LAN Proxy disabled"
                },
            )
            refreshNetworkAndState()
            lifecycleScope.launch { pollClientDevicesOnce(force = true) }
        }

        binding.btnLanSharingToggle.setOnClickListener(lanToggleClick)
        binding.rowSwitchLanSharing.setOnClickListener(lanToggleClick)

        binding.cardAuth.setOnClickListener {
            showLanProxyAuthDialog()
        }
        binding.btnAuthAction.setOnClickListener {
            showLanProxyAuthDialog()
        }

        // Auto connect (Y): the Wi-Fi gateway auto-connect feature, its dialog and its status.
        styleInteractiveToggleButton(binding.btnGatewayToggle, gatewayToggleChecked)
        val gatewayToggleClick = View.OnClickListener {
            io.nekohasekai.sagernet.utils.HapticHelper.perform(
                this,
                binding.btnGatewayToggle,
                io.nekohasekai.sagernet.utils.HapticHelper.HapticKind.SELECTION,
            )
            val isChecked = !gatewayToggleChecked
            if (isChecked) {
                showHotspotDialog { saved ->
                    if (saved) {
                        DataStore.hotspotGatewayEnabled = true
                        setGatewaySwitchCheckedSilent(true)
                        HotspotGatewayManager.onSwitchChanged(true)
                        HotspotShareLogger.info(
                            "AutoGateway",
                            "Auto-connect enabled (port=${DataStore.hotspotGatewayPort}, auth=${DataStore.hotspotGatewayUser.isNotBlank()})",
                        )
                    } else {
                        DataStore.hotspotGatewayEnabled = false
                        setGatewaySwitchCheckedSilent(false)
                    }
                    refreshNetworkAndState()
                }
            } else {
                gatewayToggleChecked = false
                styleInteractiveToggleButton(binding.btnGatewayToggle, false)
                DataStore.hotspotGatewayEnabled = false
                HotspotGatewayManager.onSwitchChanged(false)
                HotspotShareLogger.info("AutoGateway", "Auto-connect disabled")
                refreshNetworkAndState()
            }
        }

        binding.btnGatewayToggle.setOnClickListener(gatewayToggleClick)
        binding.rowGatewaySwitch.setOnClickListener(gatewayToggleClick)
        binding.btnGatewayConfigure.setOnClickListener {
            showHotspotDialog { saved ->
                if (saved) {
                    HotspotShareLogger.info(
                        "AutoGateway",
                        "Auto-connect configured (port=${DataStore.hotspotGatewayPort}, auth=${DataStore.hotspotGatewayUser.isNotBlank()})",
                    )
                    if (DataStore.hotspotGatewayEnabled) {
                        lifecycleScope.launch {
                            withContext(Dispatchers.Default) {
                                HotspotGatewayManager.checkAndUpdate(forceReload = true)
                            }
                            if (!isFinishing && !isDestroyed) renderUiState()
                        }
                    }
                    refreshNetworkAndState()
                }
            }
        }

        binding.btnCopyHotspotIp.setOnClickListener {
            currentHotspotHostIp()?.let { ip ->
                copyToClipboard(ip, getString(R.string.lan_sharing_copied_ip, ip))
            }
        }
        binding.btnCopyHotspotPort.setOnClickListener {
            val port = currentConfiguredPort()
            copyToClipboard(port.toString(), getString(R.string.lan_sharing_copied_port, port))
        }
        binding.btnCopyHotspotLink.setOnClickListener {
            currentHotspotHostIp()?.let { ip ->
                val link = buildSocksLinkForHost(ip)
                copyToClipboard(link, getString(R.string.lan_sharing_copied_link))
            }
        }
        binding.btnShareHotspotLink.setOnClickListener {
            currentHotspotHostIp()?.let { ip -> shareSocksLink(buildSocksLinkForHost(ip)) }
        }

        binding.btnCopyWifiIp.setOnClickListener {
            val ip = detectedWifiIp
            if (!ip.isNullOrBlank()) {
                copyToClipboard(ip, getString(R.string.lan_sharing_copied_ip, ip))
            }
        }
        binding.btnCopyWifiPort.setOnClickListener {
            val port = currentConfiguredPort()
            copyToClipboard(port.toString(), getString(R.string.lan_sharing_copied_port, port))
        }
        binding.btnCopyWifiLink.setOnClickListener {
            val ip = detectedWifiIp
            if (!ip.isNullOrBlank()) {
                val link = buildSocksLinkForHost(ip)
                copyToClipboard(link, getString(R.string.lan_sharing_copied_link))
            }
        }
        binding.btnShareWifiLink.setOnClickListener {
            val ip = detectedWifiIp
            if (!ip.isNullOrBlank()) {
                shareSocksLink(buildSocksLinkForHost(ip))
            }
        }

        binding.btnRefreshDevices.setOnClickListener {
            refreshNetworkAndState()
            lifecycleScope.launch { pollClientDevicesOnce(force = true) }
        }
    }

    private fun setShareSwitchCheckedSilent(checked: Boolean) {
        lanSharingToggleChecked = checked
        styleInteractiveToggleButton(binding.btnLanSharingToggle, checked)
    }

    private fun setGatewaySwitchCheckedSilent(checked: Boolean) {
        gatewayToggleChecked = checked
        styleInteractiveToggleButton(binding.btnGatewayToggle, checked)
    }

    private fun registerStatusUpdates() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                if (action == Action.HOTSPOT_SHARE_LOGS_UPDATED) {
                    if (selectedTabIndex == TAB_LOGS) {
                        refreshInlineLogs(scrollToBottom = false, forceRebind = true)
                    }
                    return
                }
                renderUiState()
                if (action == Action.HOTSPOT_SHARE_STATE) {
                    lifecycleScope.launch { pollClientDevicesOnce(force = true) }
                }
                if (selectedTabIndex == TAB_LOGS && lastRenderedLogEnabledState != false) {
                    refreshInlineLogs(scrollToBottom = false)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Action.HOTSPOT_GATEWAY_STATUS)
            addAction(Action.HOTSPOT_SHARE_STATE)
            addAction(Action.HOTSPOT_SHARE_LOGS_UPDATED)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onSuccess { statusReceiver = receiver }

        HotspotGatewayManager.addListener(gatewayChangedListener)
    }

    /**
     * The OS-level hotspot signals: AP state flips (`WIFI_AP_STATE_CHANGED`) and tethering
     * on/off (`TETHER_STATE_CHANGED`). They are system broadcasts, so the receiver must be
     * registered RECEIVER_EXPORTED, unlike the in-app status broadcast above.
     */
    private fun registerSystemNetReceivers() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshNetworkAndState()
            }
        }
        val filter = IntentFilter().apply {
            addAction(WIFI_AP_STATE_CHANGED)
            addAction(TETHER_STATE_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }.onSuccess { netStateReceiver = receiver }
    }

    private data class NetworkStateSnapshot(
        val hotspotActive: Boolean,
        val candidates: List<HotspotCandidate>,
        val wifiIp: String?,
        val tetherKinds: Set<TetherKind>,
        val tetherMask: Int,
    )

    private fun refreshNetworkAndState(forceRootCheck: Boolean = false) {
        if (forceRootCheck) pendingForceRootCheck = true
        refreshNetworkJob?.cancel()
        refreshNetworkJob = lifecycleScope.launch {
            if (isFinishing || isDestroyed) return@launch
            val prevRoot = cachedRootAvailable
            val checkRootNow = pendingForceRootCheck || !rootCheckedOnce
            val net = withContext(Dispatchers.IO) {
                val reconciledStaleState = HotspotShareStateStore.reconcileIfServiceDead(
                    TetherWatchService.isRunningOrStarting(),
                )
                cachedShizukuRunning = ShizukuBackend.isShizukuRunning()
                cachedShizukuGranted = if (cachedShizukuRunning) {
                    ShizukuBackend.isShizukuGranted(this@LanSharingActivity)
                } else {
                    false
                }
                if (reconciledStaleState) {
                    HotspotShareLogger.warn(
                        "HotspotShare",
                        "Cleared stale Active/Starting share state because TetherWatchService was not running",
                    )
                    if (cachedShizukuRunning && cachedShizukuGranted) {
                        ShizukuBackend.cleanupOrphanedServiceState(this@LanSharingActivity)
                    }
                }
                cachedAdb5555Open = ShizukuWatchdog.isLocalAdbPortOpen()
                cachedWatchdogFlagExists = runCatching {
                    File(ShizukuWatchdog.DEFAULT_KEEP_ALIVE_FLAG_PATH).exists()
                }.getOrDefault(false)
                if (checkRootNow) {
                    cachedRootAvailable = detectRootWithoutPopup()
                    rootCheckedOnce = true
                    pendingForceRootCheck = false
                }
                val tetherKinds = runCatching {
                    LanSharingNetwork.getActiveTetherKinds(this@LanSharingActivity)
                }.getOrDefault(emptySet())
                val useShizukuMask = cachedShizukuRunning && cachedShizukuGranted && !isRootShareModeActiveOrSelected()
                val tetherMask = if (useShizukuMask) {
                    runCatching {
                        ShizukuBackend.refreshActiveTetherMask(
                            this@LanSharingActivity,
                            force = forceRootCheck,
                        )
                    }.getOrDefault(0)
                } else {
                    0
                }
                lastMaskQueryElapsedMs = SystemClock.elapsedRealtime()
                NetworkStateSnapshot(
                    hotspotActive = LanSharingNetwork.isHotspotActive(this@LanSharingActivity),
                    candidates = LanSharingNetwork.findHotspotCandidates(this@LanSharingActivity),
                    wifiIp = LanSharingNetwork.findWifiIp(this@LanSharingActivity),
                    tetherKinds = tetherKinds,
                    tetherMask = tetherMask,
                )
            }
            if (isFinishing || isDestroyed) return@launch
            hotspotActive = net.hotspotActive
            detectedHotspotCandidates = net.candidates
            // Show the first candidate; all candidates are in the list for diagnostics.
            detectedHotspotCandidate = net.candidates.firstOrNull()
            detectedWifiIp = net.wifiIp
            activeTetherKinds = net.tetherKinds
            activeTetherMask = net.tetherMask
            renderUiState()
        }
    }

    private fun detectRootWithoutPopup(): Boolean {
        if (runCatching { com.topjohnwu.superuser.Shell.isAppGrantedRoot() == true }.getOrDefault(false)) {
            return true
        }
        val suPaths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/debug_ramdisk/su",
            "/su/bin/su",
            "/vendor/bin/su",
        )
        return suPaths.any { path -> runCatching { File(path).exists() }.getOrDefault(false) }
    }

    /** The Share (X) inbound port: the mixed inbound's SOCKS port. */
    private fun currentConfiguredPort(): Int = DataStore.inboundSocksPort

    private fun renderUiState() {
        val shareOn = DataStore.allowLanAccess
        val enabled = DataStore.hotspotGatewayEnabled
        setShareSwitchCheckedSilent(shareOn)
        setGatewaySwitchCheckedSilent(enabled)

        val snapshot = HotspotGatewayState.snapshot()
        // The Hotspot Gateway status line belongs to the Auto Connect (Y) card only; it never
        // bleeds into the Share (X) section's texts.
        binding.textGatewaySummary.text = statusSummaryText(enabled, snapshot)

        val primaryColor = getColorAttr(R.attr.primaryOrTextPrimary)
        val secondaryColor = getColorAttr(android.R.attr.textColorSecondary)

        val port = currentConfiguredPort()
        val hasAuth = DataStore.inboundAuth
        val authShort = if (hasAuth) {
            getString(R.string.lan_sharing_auth_enabled, DataStore.inboundUser.ifBlank { "***" })
        } else {
            getString(R.string.lan_sharing_auth_none)
        }

        val shareState = HotspotShareStateStore.snapshot()
        if (shareState is ShareState.Active) {
            lastActiveShareMethod = shareState.method
        }
        val lanProxyStatus = LanSharingLogics.lanProxyStatus(shareOn, shareState)
        binding.textSwitchSummary.text = when (lanProxyStatus) {
            LanSharingLogics.LanProxyStatus.OFF -> getString(R.string.lan_sharing_x_off)
            LanSharingLogics.LanProxyStatus.ALONGSIDE_HOTSPOT_SHARE ->
                getString(R.string.lan_sharing_x_on_parallel, port)
            LanSharingLogics.LanProxyStatus.STANDALONE,
            LanSharingLogics.LanProxyStatus.VIA_SHARE_MODE ->
                getString(R.string.lan_sharing_x_on_standalone, port)
        }
        val lanProxyBadgeActive = lanProxyStatus != LanSharingLogics.LanProxyStatus.OFF
        binding.badgeLanProxyStatus.text = when (lanProxyStatus) {
            LanSharingLogics.LanProxyStatus.OFF -> getString(R.string.hotspot_share_status_off)
            LanSharingLogics.LanProxyStatus.ALONGSIDE_HOTSPOT_SHARE ->
                getString(R.string.lan_sharing_proxy_badge_parallel, port)
            LanSharingLogics.LanProxyStatus.STANDALONE,
            LanSharingLogics.LanProxyStatus.VIA_SHARE_MODE ->
                getString(R.string.lan_sharing_proxy_badge_active, port)
        }
        val lanBadgeKey = if (lanProxyBadgeActive) "active" else "off"
        if (lastLanProxyBadgeKey != lanBadgeKey) {
            lastLanProxyBadgeKey = lanBadgeKey
            ExpressiveSettingsHelper.styleExpressiveBadge(
                binding.badgeLanProxyStatus,
                if (lanProxyBadgeActive) primaryColor else secondaryColor,
            )
        }
        val activeMethod = (shareState as? ShareState.Active)?.method
        val activeMethodCode = activeMethod?.code?.uppercase()
        val activeMethodLabel = when (activeMethod) {
            ShareMethod.SHIZUKU -> "Shizuku"
            ShareMethod.ROOT -> "Root"
            ShareMethod.LAN_PROXY -> getString(R.string.hotspot_share_method_lan_proxy)
            null -> ""
        }
        val heroMode = LanSharingLogics.resolveHeroBannerMode(shareOn, activeMethod)
        val anySharingActive = heroMode != LanSharingLogics.HeroBannerMode.INACTIVE

        val inactiveColor = getColorAttr(android.R.attr.textColorSecondary)
        val heroBadgeKey = if (anySharingActive) "active" else "inactive"
        when (heroMode) {
            LanSharingLogics.HeroBannerMode.BOTH_ACTIVE -> {
                binding.textHeroTitle.text = getString(R.string.lan_hero_both_active_title, activeMethodLabel)
                binding.textHeroSubtitle.text = getString(R.string.lan_hero_both_active_sub, port, authShort)
                binding.badgeServiceStatus.text = getString(R.string.lan_hero_both_active_badge, activeMethodCode ?: activeMethodLabel)
            }
            LanSharingLogics.HeroBannerMode.HOTSPOT_SHARE_ONLY -> {
                binding.textHeroTitle.text = getString(R.string.lan_sharing_active)
                binding.textHeroSubtitle.text = getString(R.string.hotspot_share_status_active, activeMethodLabel)
                binding.badgeServiceStatus.text = getString(R.string.hotspot_share_status_active, activeMethodCode ?: activeMethodLabel)
            }
            LanSharingLogics.HeroBannerMode.LAN_PROXY_ONLY -> {
                binding.textHeroTitle.text = getString(R.string.lan_sharing_active)
                binding.textHeroSubtitle.text = getString(R.string.lan_sharing_status_sub, port, authShort)
                binding.badgeServiceStatus.text = getString(R.string.lan_sharing_status_running)
            }
            LanSharingLogics.HeroBannerMode.INACTIVE -> {
                binding.textHeroTitle.text = getString(R.string.lan_sharing_inactive)
                binding.textHeroSubtitle.text = getString(R.string.lan_sharing_status_sub, port, authShort)
                binding.badgeServiceStatus.text = getString(R.string.lan_sharing_status_stopped)
            }
        }
        if (lastHeroBadgeState != heroBadgeKey) {
            lastHeroBadgeState = heroBadgeKey
            if (anySharingActive) {
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeServiceStatus, primaryColor)
                ExpressiveSettingsHelper.applySquircleIcon(
                    binding.imgHeroStatus,
                    R.drawable.ic_hardware_router,
                    sizeDp = 44,
                    iconSizeDp = 22,
                )
            } else {
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeServiceStatus, inactiveColor)
                ExpressiveSettingsHelper.applySquircleIcon(
                    binding.imgHeroStatus,
                    R.drawable.ic_hardware_router,
                    sizeDp = 44,
                    iconSizeDp = 22,
                    customIconColor = inactiveColor,
                )
            }
        }

        // X warns when the shared inbound carries no RFC1929 credential.
        binding.textShareWarn.visibility = if (shareOn && !hasAuth) View.VISIBLE else View.GONE
        binding.textAuthStatus.text = getString(R.string.lan_auth_port_summary, port, authShort)
        binding.btnAuthAction.visibility = View.VISIBLE
        renderLanProxyTrafficUi(lastLanProxyTrafficDisplay)

        // Hotspot card. The share card advertises this phone's own hotspot address:
        // LanSharingNetwork.findHotspotCandidates() is the only source and there is no default
        // address: a hotspot subnet changes between openings (192.168.43.1 is one of many).
        // HotspotGatewayState's gateway is the peer gateway discovered by Auto Connect (Y),
        // never X's own IP.
        val hotspotCandidate = detectedHotspotCandidate
        val hotspotIp = hotspotCandidate?.ip
        binding.textHotspotIp.text = hotspotIp ?: getString(R.string.lan_sharing_not_detected)
        renderHotspotShareUi()
        binding.textHotspotPort.text = port.toString()
        binding.textHotspotSocksLink.text = if (hotspotIp != null) {
            // Displayed masked; copy/share buttons below send the full link.
            LanSharingLogics.maskSocks5Link(buildSocksLinkForHost(hotspotIp))
        } else ""
        val isHotspotOn = hotspotActive || hotspotIp != null
        if (isHotspotOn) {
            binding.badgeHotspotStatus.text = getString(R.string.lan_sharing_hotspot_detected)
            if (lastHotspotBadgeActive != true) {
                lastHotspotBadgeActive = true
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeHotspotStatus, primaryColor)
            }
            binding.textHotspotDesc.text = getString(R.string.lan_sharing_hotspot_desc)
        } else {
            binding.badgeHotspotStatus.text = getString(R.string.lan_sharing_hotspot_not_active)
            if (lastHotspotBadgeActive != false) {
                lastHotspotBadgeActive = false
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeHotspotStatus, secondaryColor)
            }
            binding.textHotspotDesc.text = getString(R.string.lan_sharing_hotspot_hint_off)
        }
        // Same rule as the Wi-Fi card: no detectable gateway IP, no copy/share.
        binding.btnCopyHotspotIp.isEnabled = hotspotIp != null
        binding.btnCopyHotspotLink.isEnabled = hotspotIp != null
        binding.btnShareHotspotLink.isEnabled = hotspotIp != null

        // Wi-Fi card
        val wifiIp = detectedWifiIp
        binding.textWifiPort.text = port.toString()
        if (!wifiIp.isNullOrBlank()) {
            binding.textWifiIp.text = wifiIp
            binding.textWifiSocksLink.text = LanSharingLogics.maskSocks5Link(buildSocksLinkForHost(wifiIp))
            binding.badgeWifiStatus.text = getString(R.string.lan_sharing_wifi_detected)
            if (lastWifiBadgeActive != true) {
                lastWifiBadgeActive = true
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeWifiStatus, primaryColor)
            }
            binding.textWifiDesc.text = getString(R.string.lan_sharing_wifi_desc)
            binding.btnCopyWifiIp.isEnabled = true
            binding.btnCopyWifiLink.isEnabled = true
            binding.btnShareWifiLink.isEnabled = true
        } else {
            binding.textWifiIp.text = getString(R.string.lan_sharing_not_detected)
            binding.textWifiSocksLink.text = LanSharingLogics.maskSocks5Link(buildSocksLinkForHost("0.0.0.0"))
            binding.badgeWifiStatus.text = getString(R.string.lan_sharing_wifi_not_connected)
            if (lastWifiBadgeActive != false) {
                lastWifiBadgeActive = false
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeWifiStatus, secondaryColor)
            }
            binding.textWifiDesc.text = getString(R.string.lan_sharing_wifi_hint_off)
            binding.btnCopyWifiIp.isEnabled = false
            binding.btnCopyWifiLink.isEnabled = false
            binding.btnShareWifiLink.isEnabled = false
        }

        // Port & Authentication action remains enabled for Local Proxy configuration.
        binding.btnAuthAction.visibility = View.VISIBLE
    }

    private fun statusSummaryText(enabled: Boolean, snapshot: HotspotGatewaySnapshot): String {
        val baseSum = getString(R.string.hotspot_gateway_sum)
        val kind = LanSharingLogics.statusSummaryKind(enabled, snapshot)
        if (kind == "DISABLED") return baseSum
        val statusText = when (snapshot.status) {
            HotspotGatewayStatus.NO_GATEWAY -> getString(R.string.hotspot_gateway_status_no_gateway)
            HotspotGatewayStatus.PROBING -> getString(R.string.hotspot_gateway_status_probing)
            HotspotGatewayStatus.FOUND ->
                getString(R.string.hotspot_gateway_status_found, snapshot.gateway, snapshot.port)
            HotspotGatewayStatus.NOT_FOUND -> getString(R.string.hotspot_gateway_status_not_found)
            HotspotGatewayStatus.PAIRING_REQUIRED -> getString(R.string.hotspot_gateway_status_pairing_required)
            HotspotGatewayStatus.AUTH_REQUIRED -> getString(R.string.hotspot_gateway_status_auth_required)
            HotspotGatewayStatus.UNSUPPORTED_AUTH -> getString(R.string.hotspot_gateway_status_unsupported_auth)
            else -> baseSum
        }
        return if (statusText == baseSum) baseSum else "$baseSum — $statusText"
    }

    private fun showHotspotDialog(onSave: (Boolean) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.layout_hotspot_gateway_dialog, null)
        val editPort = dialogView.findViewById<EditText>(R.id.edit_port)
        val editUser = dialogView.findViewById<EditText>(R.id.edit_user)
        val editPass = dialogView.findViewById<EditText>(R.id.edit_pass)
        val checkUot = dialogView.findViewById<CheckBox>(R.id.check_uot)
        val checkStopOnLoss = dialogView.findViewById<CheckBox>(R.id.check_stop_on_loss)
        val checkAutoSwitch = dialogView.findViewById<CheckBox>(R.id.check_auto_switch)

        editPort.setText(DataStore.hotspotGatewayPort.toString())
        editUser.setText(DataStore.hotspotGatewayUser)
        editPass.setText(DataStore.hotspotGatewayPass)
        checkUot.isChecked = DataStore.hotspotGatewayUot
        checkStopOnLoss.isChecked = DataStore.hotspotGatewayStopOnLoss
        checkAutoSwitch.isChecked = DataStore.hotspotGatewayAutoSwitch

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hotspot_gateway)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val portStr = editPort.text.toString().trim()
                val port = LanSharingLogics.validateDialogPort(portStr)
                if (port == null) {
                    Toast.makeText(this, getString(R.string.invalid_port, portStr), Toast.LENGTH_SHORT).show()
                    onSave(false)
                    return@setPositiveButton
                }
                DataStore.hotspotGatewayPort = port
                DataStore.hotspotGatewayUser = editUser.text.toString().trim()
                // Never trim the password: a leading or trailing space may be part of the credential.
                DataStore.hotspotGatewayPass = editPass.text.toString()
                DataStore.hotspotGatewayUot = checkUot.isChecked
                DataStore.hotspotGatewayStopOnLoss = checkStopOnLoss.isChecked
                DataStore.hotspotGatewayAutoSwitch = checkAutoSwitch.isChecked
                onSave(true)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                onSave(false)
            }
            .setOnCancelListener {
                onSave(false)
            }
            .show()
    }

    private fun startPollingLoop() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                HotspotTrafficMonitor.stats.collect { stats ->
                    if (binding.layoutShareTraffic.visibility == View.VISIBLE) {
                        val down = Formatter.formatFileSize(this@LanSharingActivity, stats.rxSpeedBps) + "/s"
                        val up = Formatter.formatFileSize(this@LanSharingActivity, stats.txSpeedBps) + "/s"
                        val total = Formatter.formatShortFileSize(
                            this@LanSharingActivity,
                            stats.sessionRxBytes + stats.sessionTxBytes,
                        )
                        binding.textShareSpeed.text = getString(R.string.hotspot_share_speed_format, down, up)
                        binding.textShareUsage.text = getString(R.string.hotspot_share_traffic_format, total)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var tick = 0
                while (isActive) {
                    pollClientDevicesOnce(refreshNetworkExtras = (tick % 3 == 2))
                    tick++
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    private data class DevicePollResult(
        val shareSnapshot: ShareState,
        val devices: List<LanClientDevice>?,
        val clashReachable: Boolean,
        val shizukuRunning: Boolean,
        val lanProxyDisplay: LanSharingLogics.LanProxyTrafficDisplay? = null,
        val tetherKinds: Set<TetherKind> = emptySet(),
        val tetherMask: Int = 0,
        val hotspotCandidates: List<HotspotCandidate> = emptyList(),
        val clientInterfaces: Map<String, String> = emptyMap(),
        val refreshedWifiIp: String? = null,
        val refreshedHotspotActive: Boolean? = null,
        val loggingEnabled: Boolean = false,
    )

    private var hasRenderedDeviceList: Boolean = false

    private suspend fun pollClientDevicesOnce(
        force: Boolean = false,
        refreshNetworkExtras: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val pollResult = withContext(Dispatchers.IO) {
            HotspotShareStateStore.reconcileIfServiceDead(TetherWatchService.isRunningOrStarting())
            val shareSnapshot = HotspotShareStateStore.snapshot()
            val shizukuRunning = ShizukuBackend.isShizukuRunning()
            cachedShizukuRunning = shizukuRunning
            cachedShizukuGranted = if (shizukuRunning) {
                ShizukuBackend.isShizukuGranted(this@LanSharingActivity)
            } else {
                false
            }
            if (refreshNetworkExtras) {
                cachedAdb5555Open = ShizukuWatchdog.isLocalAdbPortOpen()
                cachedWatchdogFlagExists = runCatching {
                    File(ShizukuWatchdog.DEFAULT_KEEP_ALIVE_FLAG_PATH).exists()
                }.getOrDefault(false)
            }
            val nowElapsed = SystemClock.elapsedRealtime()
            val reuseRecentMask = (nowElapsed - lastMaskQueryElapsedMs) in 0L until MASK_DEDUP_WINDOW_MS
            val tetherKinds: Set<TetherKind>
            val tetherMask: Int
            if (reuseRecentMask) {
                tetherKinds = activeTetherKinds
                tetherMask = activeTetherMask
            } else {
                tetherKinds = runCatching {
                    LanSharingNetwork.getActiveTetherKinds(this@LanSharingActivity)
                }.getOrDefault(emptySet())
                val useShizukuMask = shizukuRunning && cachedShizukuGranted && !isRootShareModeActiveOrSelected()
                tetherMask = if (useShizukuMask) {
                    runCatching { ShizukuBackend.getActiveTetherMask(this@LanSharingActivity) }.getOrDefault(0)
                } else {
                    0
                }
                lastMaskQueryElapsedMs = SystemClock.elapsedRealtime()
            }
            val refreshedWifiIp = if (refreshNetworkExtras) {
                LanSharingNetwork.findWifiIp(this@LanSharingActivity)
            } else {
                null
            }
            val refreshedHotspotActive = if (refreshNetworkExtras) {
                LanSharingNetwork.isHotspotActive(this@LanSharingActivity)
            } else {
                null
            }

            val loggingEnabled = HotspotShareLogger.isLoggingEnabled()
            val shouldPollDevices = force ||
                DataStore.allowLanAccess ||
                DataStore.hotspotShareEnabled ||
                shareSnapshot is ShareState.Active
            if (!shouldPollDevices) {
                val hadHistory = clientHistory.isNotEmpty()
                clientHistory.clear()
                lastLanProxyTrafficSample = null
                return@withContext DevicePollResult(
                    shareSnapshot = shareSnapshot,
                    devices = if (hadHistory || !hasRenderedDeviceList) emptyList() else null,
                    clashReachable = false,
                    shizukuRunning = shizukuRunning,
                    lanProxyDisplay = null,
                    tetherKinds = tetherKinds,
                    tetherMask = tetherMask,
                    loggingEnabled = loggingEnabled,
                )
            }

            val activeMethod = (shareSnapshot as? ShareState.Active)?.method
            cachedShizukuClientCount = if (activeMethod == ShareMethod.SHIZUKU) {
                ShizukuBackend.queryActiveClientCount()
            } else {
                0
            }

            val candidates = LanSharingNetwork.findHotspotCandidates(this@LanSharingActivity)
                .ifEmpty { detectedHotspotCandidates }
            val primaryCandidate = detectedHotspotCandidate ?: candidates.firstOrNull()
            val arp = LanSharingNetwork.readArp()
            val body = LanSharingNetwork.fetchConnections(
                url = LanSharingNetwork.clashUrl(),
                secret = DataStore.coreBoxClashApiSecret,
            )
            val clashList = if (body != null) LanSharingLogics.parseConnections(body) else emptyList()
            val shizukuTraffic = if (activeMethod == ShareMethod.SHIZUKU) {
                val clashShizuku = if (body != null) {
                    LanSharingLogics.parseShizukuShareConnections(body)
                } else {
                    ShizukuTunnelTraffic()
                }
                val monitorStats = HotspotTrafficMonitor.stats.value
                ShizukuTunnelTraffic(
                    activeConnections = clashShizuku.activeConnections,
                    uploadBytes = maxOf(clashShizuku.uploadBytes, monitorStats.sessionTxBytes),
                    downloadBytes = maxOf(clashShizuku.downloadBytes, monitorStats.sessionRxBytes),
                    lastHost = clashShizuku.lastHost,
                )
            } else {
                null
            }
            val tcpRemotes = LanSharingNetwork.readProcNetTcpRemotes(DataStore.inboundSocksPort)
            val shizukuDump = if (activeMethod == ShareMethod.SHIZUKU) {
                ShizukuBackend.queryActiveNeighborDump()
            } else {
                null
            }
            val activeRootMode = activeMethod == ShareMethod.ROOT
            val rootDump = if (activeRootMode) RootBackend.queryNeighborDump() else null
            val hotspotIfaces = buildSet {
                primaryCandidate?.interfaceName?.let(::add)
                candidates.forEach { add(it.interfaceName) }
            }
            val neighSnapshot = LanSharingNetwork.readPrivilegedNeighbors(
                shizukuDump = shizukuDump,
                rootDump = rootDump,
                hotspotIfaces = hotspotIfaces,
            )
            val rawHostText = buildString {
                if (!shizukuDump.isNullOrBlank()) append(shizukuDump).append('\n')
                if (!rootDump.isNullOrBlank()) append(rootDump).append('\n')
            }
            val neighborHostnames = LanSharingLogics.parseNeighborHostnames(rawHostText)
            val combinedHostnames = LinkedHashMap<String, String>()
            hostnameLookupCache.forEach { (ip, host) ->
                if (host.isNotBlank()) combinedHostnames[ip] = host
            }
            combinedHostnames.putAll(neighborHostnames)

            val localIpSet = buildSet {
                detectedWifiIp?.let(::add)
                primaryCandidate?.ip?.let(::add)
                candidates.forEach { add(it.ip) }
            }
            val hotspotIp = primaryCandidate?.ip
            val allCandidatePeerIps = (arp + neighSnapshot.allIps + tcpRemotes).distinct()
            val extraTetherSubnetIps = allCandidatePeerIps.filter { ip ->
                candidates.any { candidate ->
                    LanSharingLogics.isSameSubnet(ip, candidate.ip, candidate.prefixLength)
                }
            }
            val result = LanSharingLogics.mergeDevices(
                history = clientHistory.toMap(),
                arpIps = (arp + neighSnapshot.allIps).distinct(),
                clash = clashList,
                hotspotIp = hotspotIp,
                hotspotPrefix = primaryCandidate?.prefixLength,
                localIps = localIpSet,
                nowMs = now,
                activePeerIps = tcpRemotes.toSet() + neighSnapshot.activeIps,
                hotspotHintIps = neighSnapshot.hotspotIps + extraTetherSubnetIps,
                shizukuTraffic = shizukuTraffic,
                hostnames = combinedHostnames,
                activeHotspotMethod = activeMethod,
                lanProxyEnabled = DataStore.allowLanAccess,
            )
            val clientInterfaces = resolveClientInterfaces(
                shizukuDump = shizukuDump,
                rootDump = rootDump,
                candidates = candidates,
                clientIps = result.map { it.ip },
            )
            scheduleHostnameLookups(result)
            clientHistory.clear()
            result.forEach { clientHistory[it.ip] = it }

            val lanProxyDisplay = if (DataStore.allowLanAccess) {
                val (nextSample, display) = LanSharingLogics.computeLanProxyTraffic(
                    previous = lastLanProxyTrafficSample,
                    clashDevices = clashList,
                    activeTcpRemotes = tcpRemotes,
                    localIps = localIpSet,
                    nowMs = now,
                )
                lastLanProxyTrafficSample = nextSample
                display
            } else {
                lastLanProxyTrafficSample = null
                null
            }

            DevicePollResult(
                shareSnapshot = shareSnapshot,
                devices = result,
                clashReachable = body != null,
                shizukuRunning = shizukuRunning,
                lanProxyDisplay = lanProxyDisplay,
                tetherKinds = tetherKinds,
                tetherMask = tetherMask,
                hotspotCandidates = candidates,
                clientInterfaces = clientInterfaces,
                refreshedWifiIp = refreshedWifiIp,
                refreshedHotspotActive = refreshedHotspotActive,
                loggingEnabled = loggingEnabled,
            )
        }
        if (isFinishing || isDestroyed) return
        activeTetherKinds = pollResult.tetherKinds
        activeTetherMask = pollResult.tetherMask
        if (pollResult.refreshedHotspotActive != null) {
            hotspotActive = pollResult.refreshedHotspotActive
            detectedWifiIp = pollResult.refreshedWifiIp
        }
        if (pollResult.hotspotCandidates.isNotEmpty()) {
            detectedHotspotCandidates = pollResult.hotspotCandidates
            if (detectedHotspotCandidate == null || pollResult.refreshedHotspotActive != null) {
                detectedHotspotCandidate = pollResult.hotspotCandidates.firstOrNull()
            }
        }
        clientInterfaceMap.clear()
        clientInterfaceMap.putAll(pollResult.clientInterfaces)
        if (pollResult.refreshedHotspotActive != null) {
            renderUiState()
        } else {
            renderTetherChannelChips()
        }

        lastLanProxyTrafficDisplay = pollResult.lanProxyDisplay
        renderLanProxyTrafficUi(pollResult.lanProxyDisplay)

        var activeShareSnapshot = pollResult.shareSnapshot
        if (pendingStartAfterShizukuPermission && !pollResult.shizukuRunning) {
            pendingStartAfterShizukuPermission = false
            val stoppedError = ShareState.Error(ShareFailure.SHIZUKU_NOT_RUNNING)
            activeShareSnapshot = stoppedError
            withContext(Dispatchers.IO) {
                DataStore.hotspotShareEnabled = false
                HotspotShareStateStore.publish(stoppedError)
            }
            if (isFinishing || isDestroyed) return
            renderHotspotShareUi()
        }
        if (pollResult.devices != null) {
            hasRenderedDeviceList = true
            renderDeviceList(pollResult.devices, pollResult.clashReachable)
        }
        updateShareClientCountLabel(activeShareSnapshot)
        if (selectedTabIndex == TAB_LOGS && pollResult.loggingEnabled) {
            refreshInlineLogs(scrollToBottom = false, knownLoggingEnabled = pollResult.loggingEnabled)
        }
    }

    private fun resolveClientInterfaces(
        shizukuDump: String?,
        rootDump: String?,
        candidates: List<HotspotCandidate>,
        clientIps: Collection<String>,
    ): Map<String, String> {
        val resolved = LinkedHashMap<String, String>()
        fun recordIfBetter(rawIp: String, rawIface: String) {
            val ip = LanSharingLogics.normalizeIp(rawIp)
            val iface = rawIface.trim()
            if (ip.isBlank() || iface.isEmpty()) return
            val existing = resolved[ip]
            if (existing == null) {
                resolved[ip] = iface
                return
            }
            val existingKind = LanSharingNetwork.getTetherKindForInterface(existing)
            val newKind = LanSharingNetwork.getTetherKindForInterface(iface)
            if (existingKind == TetherKind.OTHER && newKind != TetherKind.OTHER) {
                resolved[ip] = iface
            }
        }

        listOfNotNull(shizukuDump, rootDump).forEach { dump ->
            if (dump.isBlank()) return@forEach
            LanSharingLogics.parseIpNeigh(dump).forEach { entry ->
                recordIfBetter(entry.ip, entry.iface)
            }
        }

        for (ip in clientIps) {
            if (resolved.containsKey(ip)) continue
            val matched = candidates.firstOrNull { candidate ->
                LanSharingLogics.isSameSubnet(ip, candidate.ip, candidate.prefixLength)
            }
            if (matched != null) {
                resolved[ip] = matched.interfaceName
            }
        }
        return resolved
    }

    private fun renderDeviceList(devices: List<LanClientDevice>, clashReachable: Boolean) {
        val activeCount = devices.count { it.activeConnections > 0 }
        val count = activeCount.coerceAtLeast(devices.size)
        val hasDevices = count > 0
        binding.badgeDevicesCount.text = getString(R.string.lan_sharing_devices_count, count)
        if (lastDevicesBadgeActive != hasDevices) {
            lastDevicesBadgeActive = hasDevices
            val badgeColor = if (hasDevices) {
                getColorAttr(R.attr.primaryOrTextPrimary)
            } else {
                getColorAttr(android.R.attr.textColorSecondary)
            }
            ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeDevicesCount, badgeColor)
        }
        binding.textDevicesCount.text = getString(R.string.lan_sharing_devices_count, devices.size)

        val hotspotShareActive = HotspotShareStateStore.snapshot() is ShareState.Active
        val vpnConnected = DataStore.serviceState.connected
        val renderKey = buildString {
            append(hotspotShareActive).append('|').append(vpnConnected).append('|').append(clashReachable)
            devices.forEach { d ->
                val kind = clientInterfaceMap[d.ip]?.let { LanSharingNetwork.getTetherKindForInterface(it) }
                    ?: TetherKind.OTHER
                append(';').append(d.ip).append(',').append(d.hostname).append(',')
                    .append(d.isHotspot).append(',').append(d.route).append(',').append(kind).append(',')
                    .append(d.activeConnections).append(',').append(d.lastHost).append(',')
                    .append(d.uploadBytes).append(',').append(d.downloadBytes)
            }
        }
        if (lastRenderedDeviceKey == renderKey) {
            return
        }
        lastRenderedDeviceKey = renderKey

        binding.layoutDevicesList.removeAllViews()
        if (devices.isEmpty()) {
            if (vpnConnected && DataStore.allowLanAccess && !clashReachable) {
                ensureClashApiForManualLanIfNeeded()
            }
            val sharingReady = vpnConnected || hotspotShareActive
            binding.layoutNoDevices.visibility = View.VISIBLE
            binding.textNoDevicesHint.text = if (!sharingReady) {
                getString(R.string.lan_sharing_not_connected)
            } else {
                getString(R.string.lan_sharing_no_devices_hint)
            }
            return
        }

        binding.layoutNoDevices.visibility = View.GONE
        for (device in devices) {
            val row = layoutInflater.inflate(R.layout.item_lan_client_device, binding.layoutDevicesList, false)
            val cardDevice = row.findViewById<MaterialCardView>(R.id.card_lan_device)
            ExpressiveSettingsHelper.styleExpressiveCard(cardDevice, cornerRadiusDp = 20f)
            val ivIcon = row.findViewById<ImageView>(R.id.iv_device_icon)
            val dotStatus = row.findViewById<View>(R.id.dot_device_status)
            val textHostname = row.findViewById<TextView>(R.id.text_device_hostname)
            val textIp = row.findViewById<TextView>(R.id.text_device_ip)
            val badgeType = row.findViewById<TextView>(R.id.badge_device_type)
            val textDetail = row.findViewById<TextView>(R.id.text_device_status_detail)
            val textTraffic = row.findViewById<TextView>(R.id.text_device_traffic)

            val hostNameValue = device.hostname?.takeIf { it.isNotBlank() }
            if (hostNameValue != null) {
                textHostname.text = hostNameValue
                textHostname.visibility = View.VISIBLE
            } else {
                textHostname.visibility = View.GONE
            }
            textIp.text = device.ip
            val clientTetherKind = clientInterfaceMap[device.ip]
                ?.let { LanSharingNetwork.getTetherKindForInterface(it) }
                ?: TetherKind.OTHER
            badgeType.text = getString(resolveDeviceRouteBadgeRes(device.route, clientTetherKind))
            val isActivelyRouted = device.activeConnections > 0 ||
                device.route == ClientShareRoute.HOTSPOT_SHIZUKU ||
                device.route == ClientShareRoute.HOTSPOT_ROOT
            val deviceColor = getColorAttr(
                if (isActivelyRouted) R.attr.primaryOrTextPrimary else android.R.attr.textColorSecondary,
            )
            ExpressiveSettingsHelper.styleExpressiveBadge(badgeType, deviceColor)
            ivIcon.setImageResource(
                if (device.isHotspot) R.drawable.ic_communication_phonelink_ring else R.drawable.ic_hardware_router
            )
            dotStatus.setBackgroundResource(
                if (isActivelyRouted) R.drawable.bg_status_dot_green else R.drawable.bg_badge_soft
            )

            val statusLabel = if (device.activeConnections > 0) {
                getString(R.string.lan_sharing_device_active, device.activeConnections)
            } else {
                getString(R.string.lan_sharing_device_idle)
            }
            val hostPart = device.lastHost?.takeIf { it.isNotBlank() }?.let {
                " · ${getString(R.string.lan_sharing_device_recent, it)}"
            }.orEmpty()
            textDetail.text = "$statusLabel$hostPart"

            val hasTraffic = device.uploadBytes > 0L || device.downloadBytes > 0L
            if (hasTraffic) {
                val upStr = Formatter.formatFileSize(this, device.uploadBytes)
                val downStr = Formatter.formatFileSize(this, device.downloadBytes)
                textTraffic.text = "▲ $upStr\n▼ $downStr"
                textTraffic.visibility = View.VISIBLE
            } else {
                textTraffic.visibility = View.GONE
            }

            row.setOnClickListener {
                copyToClipboard(device.ip, getString(R.string.lan_sharing_copied_ip, device.ip))
            }
            binding.layoutDevicesList.addView(row)
        }
    }

    private fun scheduleHostnameLookups(devices: List<LanClientDevice>) {
        devices.forEach { device ->
            if (!device.isHotspot || !device.hostname.isNullOrBlank()) return@forEach
            val ip = device.ip
            if (hostnameLookupCache.containsKey(ip) || !hostnameLookupsInFlight.add(ip)) return@forEach
            runOnIoDispatcher {
                try {
                    val resolved = runCatching {
                        val addr = InetAddress.getByName(ip)
                        addr.canonicalHostName.takeIf { it.isNotBlank() && it != ip }
                            ?: addr.hostName.takeIf { it.isNotBlank() && it != ip }
                    }.getOrNull()
                    hostnameLookupCache[ip] = LanSharingLogics.cleanHostname(resolved).orEmpty()
                } finally {
                    hostnameLookupsInFlight.remove(ip)
                }
            }
        }
    }

    /** The Share (X) hotspot address to advertise: the detected one, no default is ever substituted. */
    private fun currentHotspotHostIp(): String? {
        return detectedHotspotCandidate?.ip
    }

    private fun buildSocksLinkForHost(host: String): String {
        // Share (X) links mirror the real inbound: its port and, when the inbound auth is on, its
        // username/password (no UoT parameter on the mixed inbound).
        return LanSharingLogics.buildSocks5ShareLink(
            host = host,
            port = DataStore.inboundSocksPort,
            username = if (DataStore.inboundAuth) DataStore.inboundUser else "",
            password = if (DataStore.inboundAuth) DataStore.inboundPass else "",
            uot = false,
            remark = getString(R.string.lan_sharing),
        )
    }

    private fun shareSocksLink(link: String) {
        if (link.isBlank()) return
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, link)
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.lan_sharing_share_link_btn)))
    }

    private fun copyToClipboard(text: String, message: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("LAN Sharing", text))
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


    private fun setHotspotShareSwitchCheckedSilent(checked: Boolean) {
        hotspotShareToggleChecked = checked
        styleInteractiveToggleButton(binding.btnHotspotShareToggle, checked)
    }

    private fun styleInteractiveToggleButton(
        btn: com.google.android.material.button.MaterialButton,
        active: Boolean,
    ) {
        val isNight = io.nekohasekai.sagernet.utils.Theme.usingNightMode(this)
        val accent = ExpressiveSettingsHelper.resolveAccentColor(this)
        val activeColor = ExpressiveSettingsHelper.resolveSemanticColor(
            this,
            ExpressiveSettingsHelper.SemanticRole.SUCCESS,
        )
        val primaryInk = getColorAttr(android.R.attr.textColorPrimary)
        val nestedBg = io.nekohasekai.sagernet.utils.Theme.resolveNestedBoxBackground(this)
        val strokeBase = io.nekohasekai.sagernet.utils.Theme.resolveCardStrokeColor(this, selected = false)

        btn.setText(if (active) R.string.hotspot_share_btn_stop else R.string.hotspot_share_btn_start)
        val fill = if (active) {
            androidx.core.graphics.ColorUtils.setAlphaComponent(activeColor, if (isNight) 54 else 36)
        } else {
            androidx.core.graphics.ColorUtils.blendARGB(nestedBg, accent, if (isNight) 0.14f else 0.08f)
        }
        val ink = if (active) activeColor else primaryInk
        val stroke = if (active) {
            androidx.core.graphics.ColorUtils.setAlphaComponent(activeColor, if (isNight) 170 else 130)
        } else {
            strokeBase
        }
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(fill)
        btn.setTextColor(ink)
        btn.iconTint = android.content.res.ColorStateList.valueOf(ink)
        btn.strokeColor = android.content.res.ColorStateList.valueOf(stroke)
        btn.strokeWidth = io.nekohasekai.sagernet.ktx.dp2px(1)
    }

    private fun shareMethodEntries(): List<String> = listOf(
        getString(R.string.hotspot_share_method_auto),
        getString(R.string.hotspot_share_method_shizuku),
        getString(R.string.hotspot_share_method_root),
        getString(R.string.hotspot_share_method_lan_proxy),
    )

    private fun failClosedEntries(): List<String> = listOf(
        getString(R.string.hotspot_share_fail_closed_block),
        getString(R.string.hotspot_share_fail_closed_stop),
    )

    private fun updateShareMethodSelectorText(code: String) {
        val idx = shareMethodCodes.indexOf(code).coerceAtLeast(0)
        binding.textShareMethodValue.text = shareMethodEntries().getOrNull(idx)
            ?: getString(R.string.hotspot_share_method_auto)
    }

    private fun updateFailClosedSelectorText(code: String) {
        val idx = failClosedCodes.indexOf(code).coerceAtLeast(0)
        binding.textFailClosedValue.text = failClosedEntries().getOrNull(idx)
            ?: getString(R.string.hotspot_share_fail_closed_block)
    }

    private fun setupHotspotShareViews() {
        styleInteractiveToggleButton(binding.btnHotspotShareToggle, hotspotShareToggleChecked)

        val toggleClick = View.OnClickListener {
            io.nekohasekai.sagernet.utils.HapticHelper.perform(
                this,
                binding.btnHotspotShareToggle,
                io.nekohasekai.sagernet.utils.HapticHelper.HapticKind.SELECTION,
            )
            val isChecked = !hotspotShareToggleChecked
            hotspotShareToggleChecked = isChecked
            styleInteractiveToggleButton(binding.btnHotspotShareToggle, isChecked)
            if (isChecked) {
                if (!DataStore.serviceState.connected) {
                    Toast.makeText(this, R.string.hotspot_share_vpn_not_running, Toast.LENGTH_LONG).show()
                }
                PlatformNotifications.cancelHotspotShareDropped(this)
                HotspotShareStateStore.publish(ShareState.Starting)
                DataStore.hotspotShareEnabled = true
                val pref = SharePreference.fromCode(DataStore.hotspotShareMethod)
                val logTag = when (pref) {
                    SharePreference.ROOT -> "RootShare"
                    SharePreference.SHIZUKU -> "ShizukuShare"
                    else -> if (cachedRootAvailable && !cachedShizukuRunning) "RootShare" else "ShizukuShare"
                }
                HotspotShareLogger.info(
                    logTag,
                    "UI start requested (pref=$pref, shizuku=$cachedShizukuRunning/$cachedShizukuGranted, root=$cachedRootAvailable)",
                )
                val needsShizukuPrompt = (pref == SharePreference.SHIZUKU || (pref == SharePreference.AUTO && !cachedRootAvailable)) &&
                    cachedShizukuRunning && !cachedShizukuGranted
                if (needsShizukuPrompt) {
                    HotspotShareLogger.info("ShizukuShare", "Requesting Shizuku permission before start")
                    pendingStartAfterShizukuPermission = true
                    runCatching { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE) }
                        .onFailure {
                            pendingStartAfterShizukuPermission = false
                            TetherWatchService.start(this)
                        }
                } else {
                    pendingStartAfterShizukuPermission = false
                    TetherWatchService.start(this)
                }
            } else {
                pendingStartAfterShizukuPermission = false
                PlatformNotifications.cancelHotspotShareDropped(this)
                DataStore.hotspotShareEnabled = false
                HotspotShareLogger.info("HotspotShare", "UI stop requested")
                val currentSnap = HotspotShareStateStore.snapshot()
                if (TetherWatchService.isRunning ||
                    currentSnap is ShareState.Active ||
                    currentSnap is ShareState.Starting ||
                    (currentSnap as? ShareState.Stopped)?.cause == ShareFailure.VPN_LOST
                ) {
                    TetherWatchService.stop(this)
                } else {
                    HotspotShareStateStore.publish(ShareState.Off)
                }
            }
            refreshNetworkAndState()
            lifecycleScope.launch { pollClientDevicesOnce(force = true) }
        }

        binding.btnHotspotShareToggle.setOnClickListener(toggleClick)
        binding.rowSwitchHotspotShare.setOnClickListener(toggleClick)

        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxShareMethodSelector, cornerRadiusDp = 14f, strokeAlpha = 160)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxFailClosedSelector, cornerRadiusDp = 14f, strokeAlpha = 160)
        updateShareMethodSelectorText(lastAppliedShareMethod)
        updateFailClosedSelectorText(lastAppliedFailClosed)

        binding.boxShareMethodSelector.setOnClickListener {
            val entries = shareMethodEntries()
            val checkedIdx = shareMethodCodes.indexOf(lastAppliedShareMethod).coerceAtLeast(0)
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.hotspot_share_method_label)
                .setSingleChoiceItems(entries.toTypedArray(), checkedIdx) { dialog, position ->
                    val selectedCode = shareMethodCodes.getOrNull(position) ?: "auto"
                    if (selectedCode == "root" && !cachedRootAvailable) {
                        Toast.makeText(this, R.string.hotspot_share_err_root, Toast.LENGTH_SHORT).show()
                        return@setSingleChoiceItems
                    }
                    if (selectedCode != lastAppliedShareMethod) {
                        lastAppliedShareMethod = selectedCode
                        DataStore.hotspotShareMethod = selectedCode
                        updateShareMethodSelectorText(selectedCode)
                        if (DataStore.hotspotShareEnabled) {
                            TetherWatchService.restart(this)
                        } else if (HotspotShareStateStore.snapshot() !is ShareState.Active) {
                            HotspotShareStateStore.publish(ShareState.Off)
                        }
                        refreshNetworkAndState()
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.boxFailClosedSelector.setOnClickListener {
            val entries = failClosedEntries()
            val checkedIdx = failClosedCodes.indexOf(lastAppliedFailClosed).coerceAtLeast(0)
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.hotspot_share_fail_closed_label)
                .setSingleChoiceItems(entries.toTypedArray(), checkedIdx) { dialog, position ->
                    val selectedCode = failClosedCodes.getOrNull(position) ?: "block"
                    if (selectedCode != lastAppliedFailClosed) {
                        lastAppliedFailClosed = selectedCode
                        DataStore.hotspotShareFailClosed = selectedCode
                        updateFailClosedSelectorText(selectedCode)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Active Tethering Channels filter chips (Wi-Fi / USB coming soon / Root-only Bluetooth)
        binding.chipTetherWifi.setOnClickListener {
            onTetherChannelChipClicked(
                tetherType = ShizukuBackend.TETHERING_WIFI,
                kind = TetherKind.WIFI,
                maskBit = ShizukuBackend.TETHER_MASK_WIFI,
                labelRes = R.string.hotspot_share_tether_channel_wifi,
            )
        }
        binding.chipTetherUsb.setOnClickListener {
            if (!LanSharingNetwork.USB_TETHER_ENABLED) {
                renderTetherChannelChips()
                return@setOnClickListener
            }
            onTetherChannelChipClicked(
                tetherType = ShizukuBackend.TETHERING_USB,
                kind = TetherKind.USB,
                maskBit = ShizukuBackend.TETHER_MASK_USB,
                labelRes = R.string.hotspot_share_tether_channel_usb,
            )
        }
        binding.chipTetherBluetooth.setOnClickListener {
            renderTetherChannelChips()
            if (isRootShareModeActiveOrSelected()) {
                openSystemHotspotSettings(preferAllTethering = true)
            }
        }

        // Open Android System Hotspot Settings button
        binding.btnOpenSystemHotspotSettings.setOnClickListener {
            openSystemHotspotSettings()
        }

        val requestShizukuPermClick = View.OnClickListener {
            lifecycleScope.launch {
                val running = withContext(Dispatchers.IO) { ShizukuBackend.isShizukuRunning() }
                if (isFinishing || isDestroyed) return@launch
                if (running) {
                    try {
                        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                    } catch (t: Throwable) {
                        Toast.makeText(
                            this@LanSharingActivity,
                            t.message ?: "Failed to request Shizuku permission",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@LanSharingActivity,
                        R.string.hotspot_share_shizuku_not_running,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
        binding.btnShizukuPermission.setOnClickListener(requestShizukuPermClick)
        binding.btnDashGrantShizuku.setOnClickListener(requestShizukuPermClick)

        binding.btnDashReviveAdb.setOnClickListener {
            binding.btnDashReviveAdb.isEnabled = false
            Toast.makeText(this, R.string.hotspot_share_dash_reviving, Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val sent = withContext(Dispatchers.IO) {
                    ShizukuWatchdog.tryReviveShizukuViaLocalAdbOrShell()
                }
                if (sent) {
                    delay(1200L)
                }
                if (isFinishing || isDestroyed) return@launch
                binding.btnDashReviveAdb.isEnabled = true
                Toast.makeText(
                    this@LanSharingActivity,
                    if (sent) R.string.hotspot_share_dash_revive_ok else R.string.hotspot_share_dash_revive_fail,
                    Toast.LENGTH_LONG,
                ).show()
                refreshNetworkAndState()
            }
        }
    }

    private fun isRootShareModeActiveOrSelected(): Boolean {
        val activeMethod = (HotspotShareStateStore.snapshot() as? ShareState.Active)?.method
        if (activeMethod != null) return activeMethod == ShareMethod.ROOT
        val pref = SharePreference.fromCode(DataStore.hotspotShareMethod)
        if (pref == SharePreference.ROOT) return true
        if (pref == SharePreference.AUTO) {
            val shizukuReady = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                cachedShizukuRunning && cachedShizukuGranted
            return cachedRootAvailable && !shizukuReady
        }
        return false
    }

    private fun isTetherChannelActive(kind: TetherKind, maskBit: Int): Boolean {
        return kind in activeTetherKinds || (activeTetherMask and maskBit) != 0
    }

    private fun renderTetherChannelChips() {
        if (tetherToggleInFlight) return
        val isRootMode = isRootShareModeActiveOrSelected()
        binding.chipTetherWifi.isChecked =
            isTetherChannelActive(TetherKind.WIFI, ShizukuBackend.TETHER_MASK_WIFI)

        if (LanSharingNetwork.USB_TETHER_ENABLED) {
            binding.chipTetherUsb.text = getString(R.string.hotspot_share_tether_channel_usb)
            binding.chipTetherUsb.isCheckable = true
            binding.chipTetherUsb.isEnabled = true
            binding.chipTetherUsb.alpha = 1f
            binding.chipTetherUsb.isChecked =
                isTetherChannelActive(TetherKind.USB, ShizukuBackend.TETHER_MASK_USB)
        } else {
            binding.chipTetherUsb.text = getString(R.string.hotspot_share_tether_channel_usb_soon)
            binding.chipTetherUsb.isCheckable = false
            binding.chipTetherUsb.isChecked = false
            binding.chipTetherUsb.isEnabled = false
            binding.chipTetherUsb.alpha = 0.55f
        }

        if (isRootMode) {
            binding.chipTetherBluetooth.visibility = View.VISIBLE
            binding.chipTetherBluetooth.isEnabled = true
            binding.chipTetherBluetooth.isChecked = TetherKind.BLUETOOTH in activeTetherKinds
        } else {
            binding.chipTetherBluetooth.visibility = View.GONE
            binding.chipTetherBluetooth.isChecked = false
        }
    }

    private fun setTetherChannelChipsEnabled(enabled: Boolean) {
        binding.chipTetherWifi.isEnabled = enabled
        binding.chipTetherUsb.isEnabled = enabled && LanSharingNetwork.USB_TETHER_ENABLED
        if (isRootShareModeActiveOrSelected()) {
            binding.chipTetherBluetooth.isEnabled = enabled
        }
    }

    private fun resolveDeviceRouteBadgeRes(route: ClientShareRoute, tetherKind: TetherKind): Int {
        if (route == ClientShareRoute.LAN_PROXY) {
            return R.string.lan_sharing_device_route_lan_proxy
        }
        return when (tetherKind) {
            TetherKind.USB -> when (route) {
                ClientShareRoute.HOTSPOT_SHIZUKU -> R.string.lan_sharing_device_route_usb_shizuku
                ClientShareRoute.HOTSPOT_ROOT -> R.string.lan_sharing_device_route_usb_root
                ClientShareRoute.HOTSPOT_LAN_PROXY -> R.string.lan_sharing_device_route_usb_proxy
                ClientShareRoute.HOTSPOT_IDLE,
                ClientShareRoute.LAN_PROXY -> R.string.lan_sharing_device_route_usb_idle
            }
            TetherKind.BLUETOOTH -> when (route) {
                ClientShareRoute.HOTSPOT_SHIZUKU -> R.string.lan_sharing_device_route_bt_shizuku
                ClientShareRoute.HOTSPOT_ROOT -> R.string.lan_sharing_device_route_bt_root
                ClientShareRoute.HOTSPOT_LAN_PROXY -> R.string.lan_sharing_device_route_bt_proxy
                ClientShareRoute.HOTSPOT_IDLE,
                ClientShareRoute.LAN_PROXY -> R.string.lan_sharing_device_route_bt_idle
            }
            TetherKind.WIFI,
            TetherKind.OTHER -> when (route) {
                ClientShareRoute.HOTSPOT_SHIZUKU -> R.string.lan_sharing_device_route_hotspot_shizuku
                ClientShareRoute.HOTSPOT_ROOT -> R.string.lan_sharing_device_route_hotspot_root
                ClientShareRoute.HOTSPOT_LAN_PROXY -> R.string.lan_sharing_device_route_hotspot_proxy
                ClientShareRoute.HOTSPOT_IDLE -> R.string.lan_sharing_device_route_hotspot_idle
                ClientShareRoute.LAN_PROXY -> R.string.lan_sharing_device_route_lan_proxy
            }
        }
    }

    private fun onTetherChannelChipClicked(
        tetherType: Int,
        kind: TetherKind,
        maskBit: Int,
        labelRes: Int,
    ) {
        if ((tetherType == ShizukuBackend.TETHERING_USB || tetherType == ShizukuBackend.TETHERING_NCM) &&
            !LanSharingNetwork.USB_TETHER_ENABLED
        ) {
            renderTetherChannelChips()
            return
        }
        val targetEnabled = !isTetherChannelActive(kind, maskBit)
        val channelName = getString(labelRes)
        // Restore actual checked state immediately until OS confirms the change.
        renderTetherChannelChips()
        if (tetherToggleInFlight) return

        tetherToggleInFlight = true
        setTetherChannelChipsEnabled(false)
        val preferAllTethering = tetherType != ShizukuBackend.TETHERING_WIFI

        lifecycleScope.launch {
            try {
                val (shizukuRunning, shizukuGranted) = withContext(Dispatchers.IO) {
                    val running = ShizukuBackend.isShizukuRunning()
                    val granted = running && ShizukuBackend.isShizukuGranted(this@LanSharingActivity)
                    cachedShizukuRunning = running
                    cachedShizukuGranted = granted
                    running to granted
                }
                if (isFinishing || isDestroyed) return@launch

                if (!shizukuRunning || !shizukuGranted) {
                    Snackbar.make(
                        binding.root,
                        R.string.hotspot_share_tether_shizuku_required,
                        Snackbar.LENGTH_LONG,
                    ).setAction(R.string.hotspot_share_tether_open_settings) {
                        openSystemHotspotSettings(preferAllTethering = preferAllTethering)
                    }.show()
                    return@launch
                }

                val result = ShizukuBackend.setTetherTypeEnabled(
                    type = tetherType,
                    enabled = targetEnabled,
                    context = this@LanSharingActivity,
                )
                if (result.isSuccess) {
                    delay(450L)
                }
                val (updatedKinds, updatedMask) = withContext(Dispatchers.IO) {
                    val kinds = runCatching {
                        LanSharingNetwork.getActiveTetherKinds(this@LanSharingActivity)
                    }.getOrDefault(emptySet())
                    val mask = runCatching {
                        ShizukuBackend.refreshActiveTetherMask(
                            this@LanSharingActivity,
                            force = true,
                        )
                    }.getOrDefault(0)
                    kinds to mask
                }
                if (isFinishing || isDestroyed) return@launch
                activeTetherKinds = updatedKinds
                activeTetherMask = updatedMask

                val channelNowActive = isTetherChannelActive(kind, maskBit)
                if (result.isSuccess && channelNowActive == targetEnabled) {
                    Toast.makeText(
                        this@LanSharingActivity,
                        getString(
                            if (targetEnabled) {
                                R.string.hotspot_share_tether_enabled
                            } else {
                                R.string.hotspot_share_tether_disabled
                            },
                            channelName,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshNetworkAndState()
                } else {
                    val detail = result.exceptionOrNull()?.message.orEmpty()
                    HotspotShareLogger.warn(
                        "HotspotShare",
                        "Tether channel toggle incomplete ($channelName, targetEnabled=$targetEnabled, activeNow=$channelNowActive): $detail",
                    )
                    val msg = when {
                        targetEnabled && !channelNowActive && tetherType == ShizukuBackend.TETHERING_USB ->
                            getString(R.string.hotspot_share_tether_usb_not_active)
                        preferAllTethering ||
                            detail.contains("SecurityException", ignoreCase = true) ||
                            detail.contains("permission", ignoreCase = true) ||
                            detail.contains("Failed to start", ignoreCase = true) ->
                            getString(R.string.hotspot_share_tether_toggle_blocked_rom, channelName)
                        else ->
                            getString(
                                R.string.hotspot_share_tether_toggle_failed,
                                channelName,
                                detail.ifBlank { "error" },
                            )
                    }
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                        .setAction(R.string.hotspot_share_tether_open_settings) {
                            openSystemHotspotSettings(preferAllTethering = preferAllTethering)
                        }
                        .show()
                    refreshNetworkAndState()
                }
            } finally {
                tetherToggleInFlight = false
                if (!isFinishing && !isDestroyed) {
                    setTetherChannelChipsEnabled(true)
                    renderTetherChannelChips()
                }
            }
        }
    }

    private fun openSystemHotspotSettings(preferAllTethering: Boolean = false) {
        val candidates = if (preferAllTethering) {
            listOf(
                Intent("android.settings.TETHER_SETTINGS"),
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
                Intent("com.android.settings.WIFI_TETHER_SETTINGS"),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
            )
        } else {
            listOf(
                Intent("com.android.settings.WIFI_TETHER_SETTINGS"),
                Intent("android.settings.TETHER_SETTINGS"),
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
            )
        }
        for (intent in candidates) {
            val started = runCatching {
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
            if (started) return
        }
        runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupLogViews() {
        binding.switchShareLogsEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (suppressLogsSwitchCallback) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    HotspotShareLogger.setLoggingEnabled(isChecked)
                    ShizukuBackend.setServiceLoggingEnabled(isChecked)
                    if (isChecked) {
                        emitLoggingEnabledDiagnosticSnapshot()
                    }
                }
                if (isFinishing || isDestroyed) return@launch
                sendBroadcast(Intent(Action.HOTSPOT_SHARE_LOGS_UPDATED).setPackage(packageName))
                updateInlineLogBoxAppearance(isChecked)
                refreshInlineLogs(scrollToBottom = false, forceRebind = true)
            }
        }

        binding.rowShareLogs.setOnClickListener {
            binding.switchShareLogsEnabled.toggle()
        }

        binding.btnLogDrawerActions.setOnClickListener {
            val open = binding.drawerLogActions.visibility != View.VISIBLE
            toggleLogDrawer(showActions = open, showFilters = false)
        }
        binding.btnLogDrawerFilters.setOnClickListener {
            val open = binding.drawerLogFilters.visibility != View.VISIBLE
            toggleLogDrawer(showActions = false, showFilters = open)
        }

        binding.btnRefreshInlineLogs.setOnClickListener {
            refreshInlineLogs(scrollToBottom = true, forceRebind = true)
        }
        binding.btnCopyInlineLogs.setOnClickListener {
            val filtered = HotspotShareLogger.filterAndSortLogs(
                currentRawShareLogs,
                source = currentLogSourceFilter,
                levelFilter = currentLogLevelFilter,
                searchQuery = currentLogSearchQuery,
                newestFirst = currentLogNewestFirst,
            )
            val toCopy = filtered.ifBlank {
                currentRawShareLogs.ifBlank { binding.textInlineShareLogs.text?.toString().orEmpty() }
            }
            copyToClipboard(toCopy, getString(R.string.hotspot_share_logs_copied))
        }
        binding.btnClearInlineLogs.setOnClickListener {
            clearAllShareLogs {
                currentRawShareLogs = ""
                applyLogFilters(scrollToEdge = false)
            }
        }

        binding.editLogSearch.doAfterTextChanged { text ->
            if (suppressFilterChipCallbacks) return@doAfterTextChanged
            currentLogSearchQuery = text?.toString().orEmpty()
            updateLogFilterUi()
            applyLogFilters(scrollToEdge = false)
        }

        binding.chipGroupLogSource.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressFilterChipCallbacks) return@setOnCheckedStateChangeListener
            val checkedId = checkedIds.firstOrNull()
            currentLogSourceFilter = when (checkedId) {
                R.id.chipLogSourceShizuku -> HotspotShareLogger.LogSource.SHIZUKU
                R.id.chipLogSourceRoot -> HotspotShareLogger.LogSource.ROOT
                R.id.chipLogSourceProxy -> HotspotShareLogger.LogSource.PROXY
                else -> HotspotShareLogger.LogSource.ALL
            }
            updateLogFilterUi()
            applyLogFilters(scrollToEdge = false)
        }

        binding.chipGroupLogLevel.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressFilterChipCallbacks) return@setOnCheckedStateChangeListener
            val checkedId = checkedIds.firstOrNull()
            currentLogLevelFilter = when (checkedId) {
                R.id.chipLogLevelWarn -> HotspotShareLogger.LogLevelFilter.WARN_PLUS
                R.id.chipLogLevelError -> HotspotShareLogger.LogLevelFilter.ERROR_ONLY
                else -> HotspotShareLogger.LogLevelFilter.ALL
            }
            updateLogFilterUi()
            applyLogFilters(scrollToEdge = false)
        }

        binding.chipLogSortOrder.setOnCheckedChangeListener { _, isChecked ->
            if (suppressFilterChipCallbacks) return@setOnCheckedChangeListener
            currentLogNewestFirst = isChecked
            updateLogFilterUi()
            applyLogFilters(scrollToEdge = true)
        }

        binding.btnResetLogFilters.setOnClickListener {
            resetLogFilters()
        }

        updateLogFilterUi()

        // Let the inner log scroll box consume vertical gestures cleanly without fighting the outer scroll view.
        binding.scrollInlineShareLogs.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }

    private fun toggleLogDrawer(showActions: Boolean, showFilters: Boolean) {
        runCatching {
            TransitionManager.beginDelayedTransition(
                binding.cardShareLogs,
                AutoTransition().setDuration(180L),
            )
        }
        binding.drawerLogActions.visibility = if (showActions) View.VISIBLE else View.GONE
        binding.drawerLogFilters.visibility = if (showFilters) View.VISIBLE else View.GONE
        updateLogFilterUi()
        updateLogsBoxHeightToFillViewport()
    }

    private fun resetLogFilters() {
        suppressFilterChipCallbacks = true
        currentLogSourceFilter = HotspotShareLogger.LogSource.ALL
        currentLogLevelFilter = HotspotShareLogger.LogLevelFilter.ALL
        currentLogSearchQuery = ""
        currentLogNewestFirst = true
        binding.editLogSearch.setText("")
        binding.chipGroupLogSource.check(R.id.chipLogSourceAll)
        binding.chipGroupLogLevel.check(R.id.chipLogLevelAll)
        binding.chipLogSortOrder.isChecked = true
        suppressFilterChipCallbacks = false
        updateLogFilterUi()
        applyLogFilters(scrollToEdge = true)
    }

    private fun countActiveLogFilters(): Int {
        var count = 0
        if (currentLogSourceFilter != HotspotShareLogger.LogSource.ALL) count++
        if (currentLogLevelFilter != HotspotShareLogger.LogLevelFilter.ALL) count++
        if (currentLogSearchQuery.isNotBlank()) count++
        if (!currentLogNewestFirst) count++
        return count
    }

    private fun updateLogFilterUi() {
        binding.chipLogSortOrder.text = getString(
            if (currentLogNewestFirst) R.string.hotspot_share_log_sort_newest else R.string.hotspot_share_log_sort_oldest,
        )
        val activeFilterCount = countActiveLogFilters()
        val hasActiveFilters = activeFilterCount > 0
        binding.btnResetLogFilters.visibility = if (hasActiveFilters) View.VISIBLE else View.GONE
        binding.btnLogDrawerFilters.text = if (hasActiveFilters) {
            "${getString(R.string.hotspot_share_log_drawer_filters)} ($activeFilterCount)"
        } else {
            getString(R.string.hotspot_share_log_drawer_filters)
        }

        val primaryColor = getColorAttr(R.attr.primaryOrTextPrimary)
        val secondaryColor = getColorAttr(android.R.attr.textColorSecondary)
        val activeBg = ColorStateList.valueOf(
            android.graphics.Color.argb(
                36,
                android.graphics.Color.red(primaryColor),
                android.graphics.Color.green(primaryColor),
                android.graphics.Color.blue(primaryColor),
            ),
        )
        val activeStroke = ColorStateList.valueOf(primaryColor)

        fun styleDrawerButton(btn: MaterialButton, highlighted: Boolean) {
            ExpressiveSettingsHelper.styleOutlinedButton(btn)
            if (highlighted) {
                btn.backgroundTintList = activeBg
                btn.strokeColor = activeStroke
                btn.setTextColor(primaryColor)
                btn.iconTint = ColorStateList.valueOf(primaryColor)
            }
        }

        styleDrawerButton(
            binding.btnLogDrawerActions,
            binding.drawerLogActions.visibility == View.VISIBLE,
        )
        styleDrawerButton(
            binding.btnLogDrawerFilters,
            binding.drawerLogFilters.visibility == View.VISIBLE || hasActiveFilters,
        )
    }

    private fun emitLoggingEnabledDiagnosticSnapshot() {
        val pref = SharePreference.fromCode(DataStore.hotspotShareMethod)
        val snap = HotspotShareStateStore.snapshot()
        val vpnConnected = DataStore.serviceState.connected
        HotspotShareLogger.info(
            "HotspotShare",
            "Diagnostic logging enabled (state=${snap.javaClass.simpleName}, pref=$pref, vpn=$vpnConnected, shizuku=$cachedShizukuRunning/$cachedShizukuGranted, adb5555=$cachedAdb5555Open, root=$cachedRootAvailable)",
        )
        if (DataStore.allowLanAccess) {
            val authOn = DataStore.inboundAuth && DataStore.inboundUser.isNotBlank()
            HotspotShareLogger.info(
                "LanProxy",
                "LAN Proxy status: active=true, port=${DataStore.inboundSocksPort}, auth=$authOn, vpn=$vpnConnected",
            )
        }
        ShizukuBackend.mirrorServiceLogs()
    }

    private data class LogThemeColors(
        val primaryColor: Int,
        val secondaryColor: Int,
        val accentColor: Int,
        val amberColor: Int,
        val redColor: Int,
    )

    private data class PreparedLogRender(
        val loggingEnabled: Boolean,
        val badgeText: String,
        val bodyText: CharSequence,
        val hasEntries: Boolean,
    )

    private fun resolveLogThemeColors(): LogThemeColors = LogThemeColors(
        primaryColor = getColorAttr(android.R.attr.textColorPrimary),
        secondaryColor = getColorAttr(android.R.attr.textColorSecondary),
        accentColor = getColorAttr(R.attr.primaryOrTextPrimary),
        amberColor = ContextCompat.getColor(this, R.color.material_amber_500),
        redColor = ContextCompat.getColor(this, R.color.material_red_500),
    )

    private suspend fun prepareLogRenderOffMain(
        rawLogs: String,
        sourceFilter: HotspotShareLogger.LogSource,
        levelFilter: HotspotShareLogger.LogLevelFilter,
        searchQuery: String,
        newestFirst: Boolean,
        hasActiveFilters: Boolean,
        colors: LogThemeColors,
        knownLoggingEnabled: Boolean? = null,
    ): PreparedLogRender = withContext(Dispatchers.Default) {
        val loggingEnabled = knownLoggingEnabled ?: withContext(Dispatchers.IO) {
            HotspotShareLogger.isLoggingEnabled()
        }
        val totalEntries = HotspotShareLogger.countEntries(rawLogs)
        if (rawLogs.isBlank()) {
            return@withContext PreparedLogRender(
                loggingEnabled = loggingEnabled,
                badgeText = getString(R.string.hotspot_share_logs_count_total, 0),
                bodyText = emptyLogPlaceholderText(loggingEnabled),
                hasEntries = false,
            )
        }
        val filtered = HotspotShareLogger.filterAndSortLogs(
            rawLogs,
            source = sourceFilter,
            levelFilter = levelFilter,
            searchQuery = searchQuery,
            newestFirst = newestFirst,
        )
        val visibleEntries = if (filtered.isBlank()) 0 else HotspotShareLogger.countEntries(filtered)
        val badgeText = if (hasActiveFilters && visibleEntries != totalEntries) {
            getString(R.string.hotspot_share_logs_count_filtered, visibleEntries, totalEntries)
        } else {
            getString(R.string.hotspot_share_logs_count_total, totalEntries)
        }
        if (filtered.isBlank()) {
            return@withContext PreparedLogRender(
                loggingEnabled = loggingEnabled,
                badgeText = badgeText,
                bodyText = getString(R.string.hotspot_share_logs_filter_empty, totalEntries),
                hasEntries = false,
            )
        }
        PreparedLogRender(
            loggingEnabled = loggingEnabled,
            badgeText = badgeText,
            bodyText = formatStyledLogSpannable(filtered, colors),
            hasEntries = true,
        )
    }

    private fun renderPreparedLogsOnMain(prepared: PreparedLogRender, scrollToEdge: Boolean) {
        lastRenderedLogEnabledState = prepared.loggingEnabled
        binding.textLogCountBadge.text = prepared.badgeText
        binding.textInlineShareLogs.text = prepared.bodyText
        if (prepared.hasEntries && scrollToEdge) {
            binding.scrollInlineShareLogs.post {
                if (currentLogNewestFirst) {
                    binding.scrollInlineShareLogs.scrollTo(0, 0)
                } else {
                    binding.scrollInlineShareLogs.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    private fun applyLogFilters(scrollToEdge: Boolean) {
        val rawSnapshot = currentRawShareLogs
        val sourceFilter = currentLogSourceFilter
        val levelFilter = currentLogLevelFilter
        val searchQuery = currentLogSearchQuery
        val newestFirst = currentLogNewestFirst
        val hasActiveFilters = countActiveLogFilters() > 0
        val colors = resolveLogThemeColors()
        logRenderJob?.cancel()
        logRenderJob = lifecycleScope.launch {
            val prepared = prepareLogRenderOffMain(
                rawLogs = rawSnapshot,
                sourceFilter = sourceFilter,
                levelFilter = levelFilter,
                searchQuery = searchQuery,
                newestFirst = newestFirst,
                hasActiveFilters = hasActiveFilters,
                colors = colors,
            )
            if (isFinishing || isDestroyed || !isActive) return@launch
            renderPreparedLogsOnMain(prepared, scrollToEdge)
        }
    }

    private fun updateLogsBoxHeightToFillViewport() {
        binding.scrollLanSharing.post {
            if (isFinishing || isDestroyed || selectedTabIndex != TAB_LOGS) return@post
            val viewportBottom = binding.scrollLanSharing.height - binding.scrollLanSharing.paddingBottom
            if (viewportBottom <= 0) return@post
            val rootContent = binding.scrollLanSharing.getChildAt(0) ?: return@post
            var topInContent = 0
            var current: View? = binding.scrollInlineShareLogs
            while (current != null && current !== rootContent) {
                topInContent += current.top
                current = current.parent as? View
            }
            val bottomTrimPx = binding.boxInlineShareLogs.paddingBottom +
                ((binding.boxInlineShareLogs.parent as? View)?.paddingBottom ?: 0) +
                dpToPx(12)
            val drawerOpen = binding.drawerLogActions.visibility == View.VISIBLE ||
                binding.drawerLogFilters.visibility == View.VISIBLE
            val minHeightPx = dpToPx(if (drawerOpen) 240 else 340)
            val targetHeight = (viewportBottom - topInContent - bottomTrimPx).coerceAtLeast(minHeightPx)
            val lp = binding.scrollInlineShareLogs.layoutParams
            if (lp.height != targetHeight) {
                lp.height = targetHeight
                binding.scrollInlineShareLogs.layoutParams = lp
                binding.scrollInlineShareLogs.minimumHeight = targetHeight
                binding.boxInlineShareLogs.requestLayout()
            }
        }
    }

    private fun updateInlineLogBoxAppearance(enabled: Boolean) {
        binding.boxInlineShareLogs.alpha = if (enabled) 1f else 0.72f
    }

    private fun emptyLogPlaceholderText(loggingEnabled: Boolean): String {
        return if (loggingEnabled) {
            getString(R.string.hotspot_share_logs_empty)
        } else {
            getString(R.string.hotspot_share_logs_disabled_hint)
        }
    }

    private fun refreshInlineLogs(
        scrollToBottom: Boolean,
        forceRebind: Boolean = false,
        knownLoggingEnabled: Boolean? = null,
    ) {
        logRefreshJob?.cancel()
        logRefreshJob = lifecycleScope.launch {
            val (merged, loggingEnabled) = withContext(Dispatchers.IO) {
                val logs = HotspotShareLogger.readMergedLogs(ShizukuBackend.fetchServiceLogs())
                val enabled = knownLoggingEnabled ?: HotspotShareLogger.isLoggingEnabled()
                logs to enabled
            }
            if (isFinishing || isDestroyed || !isActive) return@launch
            val changed = merged != currentRawShareLogs
            val enabledStateChanged = lastRenderedLogEnabledState != loggingEnabled
            currentRawShareLogs = merged
            if (changed || forceRebind || enabledStateChanged || binding.textInlineShareLogs.text?.isEmpty() != false) {
                val sourceFilter = currentLogSourceFilter
                val levelFilter = currentLogLevelFilter
                val searchQuery = currentLogSearchQuery
                val newestFirst = currentLogNewestFirst
                val hasActiveFilters = countActiveLogFilters() > 0
                val colors = resolveLogThemeColors()
                logRenderJob?.cancel()
                val prepared = prepareLogRenderOffMain(
                    rawLogs = merged,
                    sourceFilter = sourceFilter,
                    levelFilter = levelFilter,
                    searchQuery = searchQuery,
                    newestFirst = newestFirst,
                    hasActiveFilters = hasActiveFilters,
                    colors = colors,
                    knownLoggingEnabled = loggingEnabled,
                )
                if (isFinishing || isDestroyed || !isActive) return@launch
                renderPreparedLogsOnMain(prepared, scrollToBottom)
            } else if (scrollToBottom) {
                binding.scrollInlineShareLogs.post {
                    if (currentLogNewestFirst) {
                        binding.scrollInlineShareLogs.scrollTo(0, 0)
                    } else {
                        binding.scrollInlineShareLogs.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }

    private fun clearAllShareLogs(onDoneOnMain: () -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ShizukuBackend.clearServiceLogs()
                HotspotShareLogger.clearAll()
            }
            if (isFinishing || isDestroyed) return@launch
            sendBroadcast(Intent(Action.HOTSPOT_SHARE_LOGS_UPDATED).setPackage(packageName))
            onDoneOnMain()
            Toast.makeText(
                this@LanSharingActivity,
                R.string.hotspot_share_logs_cleared,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun formatStyledLogSpannable(
        rawLogs: String,
        colors: LogThemeColors = resolveLogThemeColors(),
    ): CharSequence {
        if (rawLogs.isBlank()) return ""
        val primaryColor = colors.primaryColor
        val secondaryColor = colors.secondaryColor
        val accentColor = colors.accentColor
        val amberColor = colors.amberColor
        val redColor = colors.redColor

        val sb = SpannableStringBuilder()
        var first = true
        for (rawLine in rawLogs.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            if (!first) sb.append("\n\n")
            first = false

            val parsed = HotspotShareLogger.parseLogLine(trimmed)
            if (parsed == null) {
                val start = sb.length
                sb.append(trimmed)
                sb.setSpan(ForegroundColorSpan(primaryColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                continue
            }

            // Line 1: HH:mm:ss.SSS  LEVEL  [Tag]
            val timeStart = sb.length
            sb.append(parsed.timeOnly)
            sb.setSpan(TypefaceSpan("monospace"), timeStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.88f), timeStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(secondaryColor), timeStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            sb.append("  ")
            val levelStart = sb.length
            sb.append(parsed.level)
            val levelColor = when (parsed.level) {
                "ERROR" -> ExpressiveSettingsHelper.resolveSemanticColor(this, ExpressiveSettingsHelper.SemanticRole.ERROR)
                "WARN" -> ExpressiveSettingsHelper.resolveSemanticColor(this, ExpressiveSettingsHelper.SemanticRole.WARNING)
                else -> ExpressiveSettingsHelper.resolveSemanticColor(this, ExpressiveSettingsHelper.SemanticRole.INFO)
            }
            sb.setSpan(ForegroundColorSpan(levelColor), levelStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), levelStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.88f), levelStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            sb.append("  ")
            val tagStart = sb.length
            sb.append("[").append(parsed.tag).append("]")
            sb.setSpan(ForegroundColorSpan(primaryColor), tagStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), tagStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.92f), tagStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Line 2: indented readable message
            sb.append("\n  ")
            val msgStart = sb.length
            sb.append(parsed.message)
            sb.setSpan(ForegroundColorSpan(primaryColor), msgStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    private fun renderLanProxyTrafficUi(display: LanSharingLogics.LanProxyTrafficDisplay?) {
        if (!DataStore.allowLanAccess) {
            binding.layoutLanProxyTraffic.visibility = View.GONE
            return
        }
        binding.layoutLanProxyTraffic.visibility = View.VISIBLE
        val rxSpeed = display?.rxSpeedBps ?: 0L
        val txSpeed = display?.txSpeedBps ?: 0L
        val total = (display?.totalRxBytes ?: 0L) + (display?.totalTxBytes ?: 0L)
        val clients = display?.activeClients ?: 0
        val downStr = Formatter.formatFileSize(this, rxSpeed) + "/s"
        val upStr = Formatter.formatFileSize(this, txSpeed) + "/s"
        val totalStr = Formatter.formatShortFileSize(this, total)
        binding.textLanProxySpeed.text = getString(R.string.hotspot_share_speed_format, downStr, upStr)
        binding.textLanProxyUsage.text = getString(R.string.hotspot_share_traffic_format, totalStr)
        binding.textLanProxyClients.text = getString(R.string.hotspot_share_clients_format, clients)
    }

    private fun showLanProxyAuthDialog() {
        val density = resources.displayMetrics.density
        val padOuter = (20 * density).toInt()
        val gap = (12 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padOuter, (10 * density).toInt(), padOuter, 0)
        }

        val portLabel = TextView(this).apply {
            text = getString(R.string.lan_sharing_port_label)
            setTextColor(getColorAttr(android.R.attr.textColorSecondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        }
        val editPort = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(DataStore.inboundSocksPort.toString())
            setSelection(text?.length ?: 0)
        }
        root.addView(portLabel)
        root.addView(editPort)

        val switchAuth = SwitchMaterial(this).apply {
            text = getString(R.string.lan_auth_enable_switch)
            isChecked = DataStore.inboundAuth
            setPadding(0, gap, 0, (4 * density).toInt())
        }
        root.addView(switchAuth)

        val userLabel = TextView(this).apply {
            text = getString(R.string.username)
            setTextColor(getColorAttr(android.R.attr.textColorSecondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, (6 * density).toInt(), 0, 0)
        }
        val editUser = EditText(this).apply {
            hint = getString(R.string.username)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setText(DataStore.inboundUser)
        }
        root.addView(userLabel)
        root.addView(editUser)

        val passLabel = TextView(this).apply {
            text = getString(R.string.password)
            setTextColor(getColorAttr(android.R.attr.textColorSecondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        val editPass = EditText(this).apply {
            hint = getString(R.string.password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setText(DataStore.inboundPass)
        }
        root.addView(passLabel)
        root.addView(editPass)

        fun updateCredentialFieldsEnabled(authOn: Boolean) {
            val alpha = if (authOn) 1f else 0.5f
            userLabel.alpha = alpha
            editUser.alpha = alpha
            editUser.isEnabled = authOn
            passLabel.alpha = alpha
            editPass.alpha = alpha
            editPass.isEnabled = authOn
        }
        updateCredentialFieldsEnabled(switchAuth.isChecked)
        switchAuth.setOnCheckedChangeListener { _, isChecked ->
            updateCredentialFieldsEnabled(isChecked)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lan_auth_dialog_title)
            .setView(root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val portStr = editPort.text?.toString()?.trim().orEmpty()
                val validPort = LanSharingLogics.validateDialogPort(portStr)
                if (validPort == null) {
                    Toast.makeText(this, getString(R.string.invalid_port, portStr), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val authEnabled = switchAuth.isChecked
                val user = editUser.text?.toString()?.trim().orEmpty()
                val pass = editPass.text?.toString().orEmpty()
                if (authEnabled && user.isEmpty()) {
                    Toast.makeText(this, R.string.lan_auth_credentials_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                DataStore.inboundSocksPort = validPort
                DataStore.inboundAuth = authEnabled
                DataStore.inboundUser = user
                DataStore.inboundPass = pass
                lastLanProxyTrafficSample = null
                HotspotShareLogger.info(
                    "LanProxy",
                    "Updated LAN Proxy config (port=${DataStore.inboundSocksPort}, auth=${DataStore.inboundAuth})",
                )
                reloadRunningServiceIfActiveAsync()
                refreshNetworkAndState()
                lifecycleScope.launch { pollClientDevicesOnce(force = true) }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun renderHotspotShareUi() {
        val enabled = DataStore.hotspotShareEnabled
        setHotspotShareSwitchCheckedSilent(enabled)

        val state = HotspotShareStateStore.snapshot()
        val primaryColor = getColorAttr(R.attr.primaryOrTextPrimary)
        val secondaryColor = getColorAttr(android.R.attr.textColorSecondary)
        val amberColor = ContextCompat.getColor(this, R.color.material_amber_500)
        val redColor = ContextCompat.getColor(this, R.color.material_red_500)

        when (state) {
            is ShareState.Off -> {
                binding.badgeShareStatus.text = getString(R.string.hotspot_share_status_off)
                if (lastShareBadgeKey != "off") {
                    lastShareBadgeKey = "off"
                    ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeShareStatus, secondaryColor)
                }
                binding.layoutShareTraffic.visibility = View.GONE
                binding.textShareClients.visibility = View.GONE
                binding.bannerShareWarning.visibility = View.GONE
            }
            is ShareState.Starting -> {
                binding.badgeShareStatus.text = getString(R.string.hotspot_share_status_starting)
                if (lastShareBadgeKey != "starting") {
                    lastShareBadgeKey = "starting"
                    ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeShareStatus, amberColor)
                }
                binding.layoutShareTraffic.visibility = View.GONE
                binding.textShareClients.visibility = View.GONE
                binding.bannerShareWarning.visibility = View.GONE
            }
            is ShareState.Active -> {
                lastActiveShareMethod = state.method
                val methodLabel = when (state.method) {
                    ShareMethod.SHIZUKU -> "Shizuku"
                    ShareMethod.ROOT -> "Root"
                    ShareMethod.LAN_PROXY -> getString(R.string.hotspot_share_method_lan_proxy)
                }
                binding.badgeShareStatus.text = getString(R.string.hotspot_share_status_active, methodLabel)
                if (lastShareBadgeKey != "active") {
                    lastShareBadgeKey = "active"
                    ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeShareStatus, primaryColor)
                }
                binding.layoutShareTraffic.visibility = if (state.method == ShareMethod.LAN_PROXY) View.GONE else View.VISIBLE
                updateShareClientCountLabel(state)
                binding.bannerShareWarning.visibility = View.GONE
            }
            is ShareState.Stopped -> {
                val pref = SharePreference.fromCode(DataStore.hotspotShareMethod)
                val failClosed = FailClosedAction.fromCode(DataStore.hotspotShareFailClosed)
                val pausedWaitingVpn = state.cause == ShareFailure.VPN_LOST && DataStore.hotspotShareEnabled
                val vpnNotice = LanSharingLogics.vpnLostNotice(pref, lastActiveShareMethod, failClosed)
                val showWaitingBadge = state.cause == ShareFailure.VPN_LOST &&
                    LanSharingLogics.vpnLostShowsWaitingBadge(vpnNotice, pausedWaitingVpn)
                binding.badgeShareStatus.text = getString(
                    if (showWaitingBadge) R.string.hotspot_share_waiting_vpn_title
                    else R.string.hotspot_share_status_stopped_clean
                )
                if (lastShareBadgeKey != "stopped") {
                    lastShareBadgeKey = "stopped"
                    ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeShareStatus, amberColor)
                }
                binding.layoutShareTraffic.visibility = View.GONE
                binding.textShareClients.visibility = View.GONE
                binding.bannerShareWarning.visibility = View.VISIBLE
                binding.textShareWarning.text = when (state.cause) {
                    ShareFailure.TETHER_LOST -> getString(R.string.hotspot_share_tether_lost_warn)
                    ShareFailure.VPN_LOST -> {
                        when (vpnNotice) {
                            LanSharingLogics.VpnLostNotice.LAN_PROXY_DROPPED ->
                                getString(R.string.hotspot_share_lan_proxy_dropped_text)
                            LanSharingLogics.VpnLostNotice.BLOCK_TRAFFIC_PROTECTED ->
                                getString(
                                    if (pausedWaitingVpn) R.string.hotspot_share_waiting_vpn_warn
                                    else R.string.hotspot_share_traffic_blocked_warn
                                )
                            LanSharingLogics.VpnLostNotice.HOTSPOT_STOPPED ->
                                getString(R.string.hotspot_share_vpn_dropped_warn)
                        }
                    }
                    else -> getString(R.string.hotspot_share_leak_warn)
                }
            }
            is ShareState.Error -> {
                binding.badgeShareStatus.text = getString(R.string.hotspot_share_status_unavailable)
                if (lastShareBadgeKey != "error") {
                    lastShareBadgeKey = "error"
                    ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeShareStatus, redColor)
                }
                binding.layoutShareTraffic.visibility = View.GONE
                binding.textShareClients.visibility = View.GONE
                binding.bannerShareWarning.visibility = View.VISIBLE
                binding.textShareWarning.text = formatShareError(state.reason, state.detail)
            }
        }

        // Shizuku status, permission & daemon watchdog UI (uses IO-cached flags — zero Main-thread Binder IPC)
        val pref = SharePreference.fromCode(DataStore.hotspotShareMethod)
        val showDash = pref == SharePreference.SHIZUKU || pref == SharePreference.AUTO
        binding.layoutShizukuDash.visibility = if (showDash) View.VISIBLE else View.GONE
        if (showDash) {
            when {
                cachedShizukuRunning && cachedShizukuGranted -> {
                    binding.textDashShizukuStatus.text = getString(R.string.hotspot_share_dash_shizuku_ready)
                    binding.textDashShizukuStatus.setTextColor(primaryColor)
                }
                cachedShizukuRunning -> {
                    binding.textDashShizukuStatus.text = getString(R.string.hotspot_share_dash_shizuku_no_perm)
                    binding.textDashShizukuStatus.setTextColor(amberColor)
                }
                else -> {
                    binding.textDashShizukuStatus.text = getString(R.string.hotspot_share_dash_shizuku_stopped)
                    binding.textDashShizukuStatus.setTextColor(amberColor)
                }
            }

            if (cachedAdb5555Open) {
                binding.textDashAdbStatus.text = getString(R.string.hotspot_share_dash_adb_open)
                binding.textDashAdbStatus.setTextColor(primaryColor)
            } else {
                binding.textDashAdbStatus.text = getString(R.string.hotspot_share_dash_adb_closed)
                binding.textDashAdbStatus.setTextColor(secondaryColor)
            }

            val watchdogLive = cachedWatchdogFlagExists ||
                (state is ShareState.Active && state.method == ShareMethod.SHIZUKU)
            if (watchdogLive) {
                binding.textDashWatchdogStatus.text = getString(R.string.hotspot_share_dash_watchdog_active)
                binding.textDashWatchdogStatus.setTextColor(primaryColor)
            } else {
                binding.textDashWatchdogStatus.text = getString(R.string.hotspot_share_dash_watchdog_standby)
                binding.textDashWatchdogStatus.setTextColor(secondaryColor)
            }

            val showGrant = cachedShizukuRunning && !cachedShizukuGranted
            val showRevive = !cachedShizukuRunning && cachedAdb5555Open
            binding.btnDashGrantShizuku.visibility = if (showGrant) View.VISIBLE else View.GONE
            binding.btnDashReviveAdb.visibility = if (showRevive) View.VISIBLE else View.GONE
            binding.layoutDashActions.visibility = if (showGrant || showRevive) View.VISIBLE else View.GONE
        }

        val hintKind = LanSharingLogics.shizukuHint(pref, cachedShizukuRunning, cachedShizukuGranted, cachedRootAvailable)
        when (hintKind) {
            LanSharingLogics.ShizukuHint.NOT_RUNNING -> {
                binding.layoutShizukuAction.visibility = if (showDash && cachedAdb5555Open) View.GONE else View.VISIBLE
                binding.textShizukuHint.visibility = View.VISIBLE
                binding.textShizukuHint.setTextColor(amberColor)
                binding.textShizukuHint.text = getString(R.string.hotspot_share_shizuku_not_running)
                binding.btnShizukuPermission.visibility = View.GONE
            }
            LanSharingLogics.ShizukuHint.NEEDS_PERMISSION -> {
                binding.layoutShizukuAction.visibility = if (showDash) View.GONE else View.VISIBLE
                binding.textShizukuHint.visibility = View.VISIBLE
                binding.textShizukuHint.setTextColor(amberColor)
                binding.textShizukuHint.text = getString(R.string.hotspot_share_err_shizuku_perm)
                binding.btnShizukuPermission.visibility = if (showDash) View.GONE else View.VISIBLE
            }
            LanSharingLogics.ShizukuHint.WATCHDOG_ACTIVE -> {
                binding.layoutShizukuAction.visibility = if (showDash) View.GONE else View.VISIBLE
                binding.textShizukuHint.visibility = View.VISIBLE
                binding.textShizukuHint.setTextColor(secondaryColor)
                binding.textShizukuHint.text = getString(R.string.hotspot_share_shizuku_watchdog_hint)
                binding.btnShizukuPermission.visibility = View.GONE
            }
            LanSharingLogics.ShizukuHint.HIDDEN -> {
                binding.layoutShizukuAction.visibility = View.GONE
            }
        }

        // Fail-closed row visibility (relevant for both Shizuku and Root modes)
        binding.rowFailClosed.visibility = if (pref != SharePreference.LAN_PROXY) {
            View.VISIBLE
        } else {
            View.GONE
        }

        renderTetherChannelChips()
    }

    private fun formatShareError(reason: ShareFailure, detail: String?): String = when (reason) {
        ShareFailure.UNSUPPORTED_ANDROID -> getString(R.string.hotspot_share_err_unsupported)
        ShareFailure.SHIZUKU_NOT_RUNNING -> getString(R.string.hotspot_share_shizuku_not_running)
        ShareFailure.SHIZUKU_PERMISSION_DENIED -> getString(R.string.hotspot_share_err_shizuku_perm)
        ShareFailure.ROOT_UNAVAILABLE -> getString(R.string.hotspot_share_err_root)
        ShareFailure.VPN_NOT_RUNNING -> getString(R.string.hotspot_share_vpn_not_running)
        ShareFailure.HOTSPOT_NOT_ACTIVE,
        ShareFailure.TETHER_LOST -> getString(R.string.hotspot_share_err_hotspot_off)
        ShareFailure.BACKEND_DIED -> getString(R.string.hotspot_share_err_died)
        ShareFailure.VPN_LOST -> {
            val failClosed = FailClosedAction.fromCode(DataStore.hotspotShareFailClosed)
            if (failClosed == FailClosedAction.BLOCK_TRAFFIC) {
                getString(R.string.hotspot_share_traffic_blocked_warn)
            } else {
                getString(R.string.hotspot_share_vpn_dropped_warn)
            }
        }
        ShareFailure.BACKEND_NOT_IMPLEMENTED,
        ShareFailure.BACKEND_FAILED -> detail?.takeIf { it.isNotBlank() }
            ?: getString(R.string.hotspot_share_err_generic)
    }
    private fun ensureClashApiForManualLanIfNeeded() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (!DataStore.allowLanAccess) return@withContext
                var modified = false
                if (LanSharingLogics.clashApiSecretShouldBeGenerated(DataStore.coreBoxClashApiSecret)) {
                    DataStore.coreBoxClashApiSecret = LanSharingLogics.generateClashApiSecret()
                    modified = true
                }
                val clashDecision = LanSharingLogics.ensureClashApiEnabledPort(DataStore.coreBoxClashApi)
                if (clashDecision.changed) {
                    DataStore.coreBoxClashApi = clashDecision.newRawPort
                    modified = true
                }
                if (modified && !isFinishing && !isDestroyed) {
                    reloadRunningServiceIfActive()
                }
            }
        }
    }

    private fun styleExpressiveViews() {
        listOf(
            binding.cardHero,
            binding.cardHotspotShare,
            binding.cardLanProxy,
            binding.cardGateway,
            binding.cardHotspot,
            binding.cardWifi,
            binding.cardAuth,
            binding.cardShareLogs,
        ).forEach { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 20f) }

        listOf(
            binding.layoutShareTraffic,
            binding.layoutLanProxyTraffic,
            binding.layoutShizukuDash,
            binding.bannerShareWarning,
            binding.boxHotspotSocksLink,
            binding.boxWifiSocksLink,
            binding.drawerLogActions,
            binding.drawerLogFilters,
            binding.editLogSearch,
            binding.boxInlineShareLogs,
        ).forEach { ExpressiveSettingsHelper.styleExpressiveNestedBox(it) }

        ExpressiveSettingsHelper.styleExpressiveChips(binding.tetherChannelsContainer)
        ExpressiveSettingsHelper.styleExpressiveChips(binding.drawerLogFilters)

        listOf(
            binding.btnRefreshLan,
            binding.btnOpenSystemHotspotSettings,
            binding.btnDashGrantShizuku,
            binding.btnDashReviveAdb,
            binding.btnShizukuPermission,
            binding.btnLogDrawerActions,
            binding.btnLogDrawerFilters,
            binding.btnRefreshInlineLogs,
            binding.btnCopyInlineLogs,
            binding.btnClearInlineLogs,
            binding.btnResetLogFilters,
            binding.btnGatewayConfigure,
            binding.btnCopyHotspotIp,
            binding.btnCopyHotspotPort,
            binding.btnCopyHotspotLink,
            binding.btnShareHotspotLink,
            binding.btnCopyWifiIp,
            binding.btnCopyWifiPort,
            binding.btnCopyWifiLink,
            binding.btnShareWifiLink,
            binding.btnAuthAction,
        ).forEach { ExpressiveSettingsHelper.styleOutlinedButton(it) }

        val mutedColor = getColorAttr(android.R.attr.textColorSecondary)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeServiceStatus, mutedColor)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeShareStatus, mutedColor)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.textLogCountBadge, mutedColor)
        updateLogFilterUi()
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeLanProxyStatus, mutedColor)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeHotspotStatus, mutedColor)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeWifiStatus, mutedColor)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeDevicesCount, mutedColor)
        ExpressiveSettingsHelper.applySquircleIcon(
            binding.imgHeroStatus,
            R.drawable.ic_hardware_router,
            sizeDp = 44,
            iconSizeDp = 22
        )
    }

    private fun updateShareClientCountLabel(state: ShareState = HotspotShareStateStore.snapshot()) {
        if (state !is ShareState.Active) {
            binding.textShareClients.visibility = View.GONE
            return
        }
        val shizukuCount = if (state.method == ShareMethod.SHIZUKU) cachedShizukuClientCount else 0
        val discoveredCount = clientHistory.count { it.value.isHotspot || it.value.activeConnections > 0 }
        val count = maxOf(shizukuCount, discoveredCount)
        binding.textShareClients.visibility = View.VISIBLE
        binding.textShareClients.text = getString(R.string.hotspot_share_clients_format, count)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PENDING_SHIZUKU_START, pendingStartAfterShizukuPermission)
        outState.putInt(STATE_SELECTED_TAB, selectedTabIndex)
        outState.putInt(STATE_SELECTED_LAN_SUBTAB, selectedLanProxySubTabIndex)
    }

    override fun onDestroy() {
        TetherWatchService.setUiForeground(false)
        refreshNetworkJob?.cancel()
        refreshNetworkJob = null
        val pendingReset = isFinishing && pendingStartAfterShizukuPermission && !TetherWatchService.isRunning
        pendingStartAfterShizukuPermission = false
        if (pendingReset) {
            runOnIoDispatcher {
                DataStore.hotspotShareEnabled = false
                HotspotShareStateStore.publish(ShareState.Off)
            }
        }
        statusReceiver?.let { runCatching { unregisterReceiver(it) } }
        statusReceiver = null
        netStateReceiver?.let { runCatching { unregisterReceiver(it) } }
        netStateReceiver = null
        HotspotGatewayManager.removeListener(gatewayChangedListener)
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {}
        connection.disconnect(this)
        super.onDestroy()
    }
}
