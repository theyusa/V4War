package io.nekohasekai.sagernet.ui

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import java.io.File
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.AdapterView
import android.widget.ArrayAdapter
import io.nekohasekai.sagernet.hotspotshare.FailClosedAction
import io.nekohasekai.sagernet.hotspotshare.HotspotShareStateStore
import io.nekohasekai.sagernet.hotspotshare.HotspotTrafficMonitor
import io.nekohasekai.sagernet.hotspotshare.RootBackend
import io.nekohasekai.sagernet.hotspotshare.ShareFailure
import io.nekohasekai.sagernet.hotspotshare.ShareMethod
import io.nekohasekai.sagernet.hotspotshare.SharePreference
import io.nekohasekai.sagernet.hotspotshare.ShareState
import io.nekohasekai.sagernet.hotspotshare.ShizukuBackend
import io.nekohasekai.sagernet.hotspotshare.TetherWatchService
import rikka.shizuku.Shizuku
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SettingsRegistry
import io.nekohasekai.sagernet.databinding.ActivityLanSharingBinding
import io.nekohasekai.sagernet.utils.PlatformNotifications
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.widget.applyListInsets
import io.nekohasekai.sagernet.utils.HotspotCandidate
import io.nekohasekai.sagernet.utils.HotspotGatewayManager
import io.nekohasekai.sagernet.utils.HotspotGatewaySnapshot
import io.nekohasekai.sagernet.utils.HotspotGatewayState
import io.nekohasekai.sagernet.utils.HotspotGatewayStatus
import io.nekohasekai.sagernet.utils.LanClientDevice
import io.nekohasekai.sagernet.utils.LanSharingLogics
import io.nekohasekai.sagernet.utils.LanSharingNetwork
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LanSharingActivity : ThemedActivity(), SagerConnection.Callback {

    private lateinit var binding: ActivityLanSharingBinding
    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
    private val clientHistory = ConcurrentHashMap<String, LanClientDevice>()
    private var statusReceiver: BroadcastReceiver? = null
    private var suppressShareSwitchCallback = false
    private var suppressGatewaySwitchCallback = false
    private var suppressHotspotShareSwitchCallback = false
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            runOnUiThread {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                cachedShizukuGranted = granted
                if (pendingStartAfterShizukuPermission) {
                    pendingStartAfterShizukuPermission = false
                    if (granted && DataStore.hotspotShareEnabled) {
                        TetherWatchService.start(this)
                    } else {
                        DataStore.hotspotShareEnabled = false
                        HotspotShareStateStore.publish(ShareState.Error(ShareFailure.SHIZUKU_PERMISSION_DENIED))
                    }
                }
                refreshNetworkAndState()
            }
        }
    }

    private var detectedHotspotCandidate: HotspotCandidate? = null
    private var detectedWifiIp: String? = null
    private var hotspotActive: Boolean = false

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

    private var pendingStartAfterShizukuPermission: Boolean = false
    private var shareMethodAdapter: ArrayAdapter<String>? = null
    private var lastHeroBadgeState: String? = null
    private var lastHotspotBadgeActive: Boolean? = null
    private var lastWifiBadgeActive: Boolean? = null
    private var lastDevicesBadgeActive: Boolean? = null
    private var lastShareBadgeKey: String? = null
    private var lastRenderedDeviceKey: String? = null

    private var netStateReceiver: BroadcastReceiver? = null

    private companion object {
        // System broadcasts that must re-detect the hotspot IP: AP state flips and tethering changes
        // are sent by the OS, therefore the receiver is registered RECEIVER_EXPORTED.
        const val WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 5051
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanSharingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ExpressiveSettingsHelper.attachActivityFloatingHeader(
            this,
            binding.root,
            getString(R.string.lan_sharing)
        )
        binding.scrollLanSharing.applyListInsets()
        styleExpressiveViews()

        setupViews()
        setupHotspotShareViews()
        ensureClashApiForManualLanIfNeeded()
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {}
        registerStatusUpdates()
        registerSystemNetReceivers()
        connection.connect(this, this)
        refreshNetworkAndState()
        startPollingLoop()
    }

    override fun onResume() {
        super.onResume()
        // Coming back to the screen re-detects: the hotspot subnet changes between openings.
        refreshNetworkAndState()
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
                lifecycleScope.launch { pollClientDevicesOnce() }
                true
            }
            R.id.action_config -> {
                showHotspotDialog { saved ->
                    if (saved) {
                        refreshNetworkAndState()
                        if (DataStore.hotspotGatewayEnabled) {
                            runOnDefaultDispatcher {
                                HotspotGatewayManager.checkAndUpdate(forceReload = true)
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
        refreshNetworkAndState()
        lifecycleScope.launch { pollClientDevicesOnce() }
    }

    override fun onServiceConnected(service: ISagerNetService) {
        refreshNetworkAndState()
        lifecycleScope.launch { pollClientDevicesOnce() }
    }

    private fun setupViews() {
        binding.btnRefreshLan.setOnClickListener {
            refreshNetworkAndState(forceRootCheck = true)
            lifecycleScope.launch { pollClientDevicesOnce() }
        }
        setShareSwitchCheckedSilent(DataStore.allowLanAccess)
        setGatewaySwitchCheckedSilent(DataStore.hotspotGatewayEnabled)

        // Share (X): the switch is the real LAN inbound switch; turning it writes inbound_address to the LAN
        // or loopback address and reloads the service (InboundSettingsFragment allowLan listener,
        // NetworkSettingsScreens.kt:74-78).
        binding.switchLanSharing.setOnCheckedChangeListener { _, isChecked ->
            if (suppressShareSwitchCallback) return@setOnCheckedChangeListener
            DataStore.inboundAddress =
                if (isChecked) SettingsRegistry.LAN_ADDRESS else SettingsRegistry.LOOPBACK_ADDRESS
            if (isChecked) {
                // Clear stale stopped/error banner when user enables manual LAN proxy sharing
                if (!DataStore.hotspotShareEnabled && HotspotShareStateStore.snapshot() !is ShareState.Active) {
                    HotspotShareStateStore.publish(ShareState.Off)
                    PlatformNotifications.cancelHotspotShareDropped(this)
                }
                // The live client list reads the Clash API /connections endpoint: make sure the API
                // is switched on, and that a secret exists. An existing secret is never overwritten;
                // the needReload() below makes the core pick up both values.
                if (LanSharingLogics.clashApiSecretShouldBeGenerated(DataStore.coreBoxClashApiSecret)) {
                    DataStore.coreBoxClashApiSecret = LanSharingLogics.generateClashApiSecret()
                }
                val clashDecision = LanSharingLogics.ensureClashApiEnabledPort(DataStore.coreBoxClashApi)
                if (clashDecision.changed) {
                    DataStore.coreBoxClashApi = clashDecision.newRawPort
                }
            }
            needReload()
            refreshNetworkAndState()
            lifecycleScope.launch { pollClientDevicesOnce() }
        }

        binding.rowSwitchLanSharing.setOnClickListener {
            binding.switchLanSharing.isChecked = !DataStore.allowLanAccess
        }

        // Auto connect (Y): the Wi-Fi gateway auto-connect feature, its dialog and its status.
        binding.switchGatewaySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressGatewaySwitchCallback) return@setOnCheckedChangeListener
            if (isChecked) {
                showHotspotDialog { saved ->
                    if (saved) {
                        DataStore.hotspotGatewayEnabled = true
                        setGatewaySwitchCheckedSilent(true)
                        HotspotGatewayManager.onSwitchChanged(true)
                    } else {
                        DataStore.hotspotGatewayEnabled = false
                        setGatewaySwitchCheckedSilent(false)
                    }
                    refreshNetworkAndState()
                }
            } else {
                DataStore.hotspotGatewayEnabled = false
                HotspotGatewayManager.onSwitchChanged(false)
                refreshNetworkAndState()
            }
        }

        binding.rowGatewaySwitch.setOnClickListener {
            binding.switchGatewaySwitch.isChecked = !DataStore.hotspotGatewayEnabled
        }
        binding.btnGatewayConfigure.setOnClickListener {
            showHotspotDialog { saved ->
                if (saved) {
                    if (DataStore.hotspotGatewayEnabled) {
                        runOnDefaultDispatcher {
                            HotspotGatewayManager.checkAndUpdate(forceReload = true)
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
            lifecycleScope.launch { pollClientDevicesOnce() }
        }
    }

    private fun setShareSwitchCheckedSilent(checked: Boolean) {
        suppressShareSwitchCallback = true
        binding.switchLanSharing.isChecked = checked
        suppressShareSwitchCallback = false
    }

    private fun setGatewaySwitchCheckedSilent(checked: Boolean) {
        suppressGatewaySwitchCallback = true
        binding.switchGatewaySwitch.isChecked = checked
        suppressGatewaySwitchCallback = false
    }

    private fun registerStatusUpdates() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                renderUiState()
                if (intent?.action == Action.HOTSPOT_SHARE_STATE) {
                    lifecycleScope.launch { pollClientDevicesOnce() }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Action.HOTSPOT_GATEWAY_STATUS)
            addAction(Action.HOTSPOT_SHARE_STATE)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onSuccess { statusReceiver = receiver }

        HotspotGatewayManager.onGatewayChanged = {
            runOnUiThread { renderUiState() }
        }
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

    private fun refreshNetworkAndState(forceRootCheck: Boolean = false) {
        lifecycleScope.launch {
            val prevRoot = cachedRootAvailable
            val checkRootNow = forceRootCheck || !rootCheckedOnce
            val net = withContext(Dispatchers.IO) {
                cachedShizukuRunning = ShizukuBackend.isShizukuRunning()
                cachedShizukuGranted = if (cachedShizukuRunning) {
                    ShizukuBackend.isShizukuGranted(this@LanSharingActivity)
                } else {
                    false
                }
                if (checkRootNow) {
                    cachedRootAvailable = detectRootWithoutPopup()
                    rootCheckedOnce = true
                }
                Triple(
                    LanSharingNetwork.isHotspotActive(this@LanSharingActivity),
                    LanSharingNetwork.findHotspotCandidates(this@LanSharingActivity),
                    LanSharingNetwork.findWifiIp(this@LanSharingActivity),
                )
            }
            if (prevRoot != cachedRootAvailable) {
                shareMethodAdapter?.notifyDataSetChanged()
            }
            hotspotActive = net.first
            // Show the first candidate; all candidates are in the list for diagnostics.
            detectedHotspotCandidate = net.second.firstOrNull()
            detectedWifiIp = net.third
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
        binding.textSwitchSummary.text =
            getString(if (shareOn) R.string.lan_sharing_x_on else R.string.lan_sharing_x_off)
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
        val activeMethodCode = (shareState as? ShareState.Active)?.method?.code?.uppercase()
        val anySharingActive = shareOn || activeMethodCode != null

        val inactiveColor = getColorAttr(android.R.attr.textColorSecondary)
        val heroBadgeKey = if (anySharingActive) "active" else "inactive"
        if (anySharingActive) {
            binding.textHeroTitle.text = getString(R.string.lan_sharing_active)
            binding.badgeServiceStatus.text = if (activeMethodCode != null) {
                getString(R.string.hotspot_share_status_active, activeMethodCode)
            } else {
                getString(R.string.lan_sharing_status_running)
            }
            if (lastHeroBadgeState != heroBadgeKey) {
                lastHeroBadgeState = heroBadgeKey
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeServiceStatus, primaryColor)
                ExpressiveSettingsHelper.applySquircleIcon(
                    binding.imgHeroStatus,
                    R.drawable.ic_hardware_router,
                    sizeDp = 44,
                    iconSizeDp = 22
                )
            }
        } else {
            binding.textHeroTitle.text = getString(R.string.lan_sharing_inactive)
            binding.badgeServiceStatus.text = getString(R.string.lan_sharing_status_stopped)
            if (lastHeroBadgeState != heroBadgeKey) {
                lastHeroBadgeState = heroBadgeKey
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeServiceStatus, inactiveColor)
                ExpressiveSettingsHelper.applySquircleIcon(
                    binding.imgHeroStatus,
                    R.drawable.ic_hardware_router,
                    sizeDp = 44,
                    iconSizeDp = 22,
                    customIconColor = inactiveColor
                )
            }
        }
        binding.textHeroSubtitle.text = if (activeMethodCode != null && !shareOn) {
            getString(R.string.hotspot_share_status_active, activeMethodCode)
        } else {
            getString(R.string.lan_sharing_status_sub, port, authShort)
        }
        // X warns when the shared inbound carries no RFC1929 credential.
        binding.textShareWarn.visibility = if (shareOn && !hasAuth) View.VISIBLE else View.GONE
        binding.textAuthStatus.text = authShort

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

        // Auth row: display only. The X credential is configured in Settings › Basic › Inbound,
        // not through the Hotspot Gateway dialog.
        binding.btnAuthAction.visibility = View.GONE
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
                    pollClientDevicesOnce()
                    // Every 3rd 2 s round the network info itself is refreshed so a reopened
                    // hotspot is picked up while staying on the screen.
                    if (tick % 3 == 2) refreshNetworkAndState()
                    tick++
                    delay(2000L)
                }
            }
        }
    }

    private suspend fun pollClientDevicesOnce() {
        val now = System.currentTimeMillis()
        val (merged, clashReachable) = withContext(Dispatchers.IO) {
            val shareSnapshot = HotspotShareStateStore.snapshot()
            val shizukuRunning = ShizukuBackend.isShizukuRunning()
            cachedShizukuRunning = shizukuRunning
            cachedShizukuGranted = if (shizukuRunning) {
                ShizukuBackend.isShizukuGranted(this@LanSharingActivity)
            } else {
                false
            }
            cachedShizukuClientCount = if ((shareSnapshot as? ShareState.Active)?.method == ShareMethod.SHIZUKU) {
                ShizukuBackend.queryActiveClientCount()
            } else {
                0
            }

            val arp = LanSharingNetwork.readArp()
            val body = LanSharingNetwork.fetchConnections(
                url = LanSharingNetwork.clashUrl(),
                secret = DataStore.coreBoxClashApiSecret,
            )
            val clashList = if (body != null) LanSharingLogics.parseConnections(body) else emptyList()
            val tcpRemotes = LanSharingNetwork.readProcNetTcpRemotes(DataStore.inboundSocksPort)
            val shizukuDump = if ((shareSnapshot as? ShareState.Active)?.method == ShareMethod.SHIZUKU) {
                ShizukuBackend.queryActiveNeighborDump()
            } else {
                null
            }
            val activeRootMode = (shareSnapshot as? ShareState.Active)?.method == ShareMethod.ROOT
            val rootDump = if (activeRootMode) RootBackend.queryNeighborDump() else null
            val hotspotIfaces = setOfNotNull(detectedHotspotCandidate?.interfaceName)
            val neighSnapshot = LanSharingNetwork.readPrivilegedNeighbors(
                shizukuDump = shizukuDump,
                rootDump = rootDump,
                hotspotIfaces = hotspotIfaces,
            )

            val hotspotIp = detectedHotspotCandidate?.ip
            val result = LanSharingLogics.mergeDevices(
                history = clientHistory.toMap(),
                arpIps = (arp + neighSnapshot.allIps).distinct(),
                clash = clashList,
                hotspotIp = hotspotIp,
                hotspotPrefix = detectedHotspotCandidate?.prefixLength,
                localIps = setOfNotNull(detectedHotspotCandidate?.ip, detectedWifiIp),
                nowMs = now,
                activePeerIps = tcpRemotes.toSet() + neighSnapshot.activeIps,
                hotspotHintIps = neighSnapshot.hotspotIps,
            )
            clientHistory.clear()
            result.forEach { clientHistory[it.ip] = it }
            result to (body != null)
        }
        renderDeviceList(merged, clashReachable)
        updateShareClientCountLabel()
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
                append(';').append(d.ip).append(',').append(d.isHotspot).append(',')
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
            val textIp = row.findViewById<TextView>(R.id.text_device_ip)
            val badgeType = row.findViewById<TextView>(R.id.badge_device_type)
            val textDetail = row.findViewById<TextView>(R.id.text_device_status_detail)
            val textTraffic = row.findViewById<TextView>(R.id.text_device_traffic)

            textIp.text = device.ip
            badgeType.text = getString(
                if (device.isHotspot) R.string.lan_sharing_device_hotspot else R.string.lan_sharing_device_wifi
            )
            val deviceColor = getColorAttr(
                if (device.activeConnections > 0) R.attr.primaryOrTextPrimary else android.R.attr.textColorSecondary
            )
            ExpressiveSettingsHelper.styleExpressiveBadge(badgeType, deviceColor)
            ivIcon.setImageResource(
                if (device.isHotspot) R.drawable.ic_communication_phonelink_ring else R.drawable.ic_hardware_router
            )
            dotStatus.setBackgroundResource(
                if (device.activeConnections > 0) R.drawable.bg_status_dot_green else R.drawable.bg_badge_soft
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

            val upStr = Formatter.formatFileSize(this, device.uploadBytes)
            val downStr = Formatter.formatFileSize(this, device.downloadBytes)
            textTraffic.text = "▲ $upStr\n▼ $downStr"

            row.setOnClickListener {
                copyToClipboard(device.ip, getString(R.string.lan_sharing_copied_ip, device.ip))
            }
            binding.layoutDevicesList.addView(row)
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
        suppressHotspotShareSwitchCallback = true
        binding.switchHotspotShare.isChecked = checked
        suppressHotspotShareSwitchCallback = false
    }

    private fun setupHotspotShareViews() {
        setHotspotShareSwitchCheckedSilent(DataStore.hotspotShareEnabled)

        binding.switchHotspotShare.setOnCheckedChangeListener { _, isChecked ->
            if (suppressHotspotShareSwitchCallback) return@setOnCheckedChangeListener
            if (isChecked) {
                if (!DataStore.serviceState.connected) {
                    Toast.makeText(this, R.string.hotspot_share_vpn_not_running, Toast.LENGTH_LONG).show()
                }
                PlatformNotifications.cancelHotspotShareDropped(this)
                HotspotShareStateStore.publish(ShareState.Starting)
                DataStore.hotspotShareEnabled = true
                val pref = SharePreference.fromCode(DataStore.hotspotShareMethod)
                val needsShizukuPrompt = (pref == SharePreference.SHIZUKU || (pref == SharePreference.AUTO && !cachedRootAvailable)) &&
                    cachedShizukuRunning && !cachedShizukuGranted
                if (needsShizukuPrompt) {
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
            lifecycleScope.launch { pollClientDevicesOnce() }
        }

        binding.rowSwitchHotspotShare.setOnClickListener {
            binding.switchHotspotShare.toggle()
        }

        // Method selector spinner (disable Root entry visually & functionally when no root exists)
        val methodEntries = listOf(
            getString(R.string.hotspot_share_method_auto),
            getString(R.string.hotspot_share_method_shizuku),
            getString(R.string.hotspot_share_method_root),
            getString(R.string.hotspot_share_method_lan_proxy),
        )
        val methodCodes = listOf("auto", "shizuku", "root", "lan_proxy")
        val methodAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, methodEntries) {
            override fun areAllItemsEnabled(): Boolean = false

            override fun isEnabled(position: Int): Boolean {
                return !(position == 2 && !cachedRootAvailable)
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getView(position, convertView, parent).apply {
                    alpha = if (position == 2 && !cachedRootAvailable) 0.45f else 1f
                }
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getDropDownView(position, convertView, parent).apply {
                    val disabled = position == 2 && !cachedRootAvailable
                    isEnabled = !disabled
                    alpha = if (disabled) 0.45f else 1f
                }
            }
        }.apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        shareMethodAdapter = methodAdapter
        binding.spinnerShareMethod.adapter = methodAdapter
        val currentMethodIndex = methodCodes.indexOf(DataStore.hotspotShareMethod).coerceAtLeast(0)
        binding.spinnerShareMethod.setSelection(currentMethodIndex)

        binding.spinnerShareMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCode = methodCodes.getOrNull(position) ?: "auto"
                if (selectedCode == "root" && !cachedRootAvailable) {
                    val fallbackIndex = methodCodes.indexOf(DataStore.hotspotShareMethod)
                        .takeIf { it >= 0 && it != 2 } ?: 0
                    binding.spinnerShareMethod.setSelection(fallbackIndex)
                    Toast.makeText(this@LanSharingActivity, R.string.hotspot_share_err_root, Toast.LENGTH_SHORT).show()
                    return
                }
                if (selectedCode != DataStore.hotspotShareMethod) {
                    DataStore.hotspotShareMethod = selectedCode
                    if (DataStore.hotspotShareEnabled) {
                        TetherWatchService.restart(this@LanSharingActivity)
                    } else if (HotspotShareStateStore.snapshot() !is ShareState.Active) {
                        HotspotShareStateStore.publish(ShareState.Off)
                    }
                    refreshNetworkAndState()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Fail-closed spinner
        val failClosedEntries = listOf(
            getString(R.string.hotspot_share_fail_closed_block),
            getString(R.string.hotspot_share_fail_closed_stop),
        )
        val failClosedCodes = listOf("block", "stop")
        val failClosedAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, failClosedEntries).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerFailClosed.adapter = failClosedAdapter
        val currentFailIndex = failClosedCodes.indexOf(DataStore.hotspotShareFailClosed).coerceAtLeast(0)
        binding.spinnerFailClosed.setSelection(currentFailIndex)

        binding.spinnerFailClosed.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCode = failClosedCodes.getOrNull(position) ?: "block"
                if (selectedCode != DataStore.hotspotShareFailClosed) {
                    DataStore.hotspotShareFailClosed = selectedCode
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Shizuku permission button
        binding.btnShizukuPermission.setOnClickListener {
            if (ShizukuBackend.isShizukuRunning()) {
                try {
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                } catch (t: Throwable) {
                    Toast.makeText(this, t.message ?: "Failed to request Shizuku permission", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, R.string.hotspot_share_shizuku_not_running, Toast.LENGTH_LONG).show()
            }
        }
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
                updateShareClientCountLabel()
                binding.bannerShareWarning.visibility = View.GONE
            }
            is ShareState.Stopped -> {
                val pref = SharePreference.fromCode(DataStore.hotspotShareMethod)
                val pausedWaitingVpn = state.cause == ShareFailure.VPN_LOST && DataStore.hotspotShareEnabled
                binding.badgeShareStatus.text = getString(
                    if (pausedWaitingVpn) R.string.hotspot_share_waiting_vpn_title
                    else R.string.hotspot_share_status_stopped_clean
                )
                if (lastShareBadgeKey != "stopped") {
                    lastShareBadgeKey = "stopped"
                    ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeShareStatus, amberColor)
                }
                binding.layoutShareTraffic.visibility = View.GONE
                binding.textShareClients.visibility = View.GONE
                binding.bannerShareWarning.visibility = View.VISIBLE
                val failClosed = FailClosedAction.fromCode(DataStore.hotspotShareFailClosed)
                binding.textShareWarning.text = when (state.cause) {
                    ShareFailure.TETHER_LOST -> getString(R.string.hotspot_share_tether_lost_warn)
                    ShareFailure.VPN_LOST -> {
                        when {
                            pausedWaitingVpn && pref != SharePreference.LAN_PROXY ->
                                getString(R.string.hotspot_share_waiting_vpn_warn)
                            pref == SharePreference.ROOT && failClosed == FailClosedAction.BLOCK_TRAFFIC ->
                                getString(R.string.hotspot_share_traffic_blocked_warn)
                            else -> getString(R.string.hotspot_share_vpn_dropped_warn)
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

        // Shizuku status & permission UI (uses IO-cached flags — zero Main-thread Binder IPC)
        val pref = SharePreference.fromCode(DataStore.hotspotShareMethod)
        val considersShizuku = pref == SharePreference.SHIZUKU || pref == SharePreference.AUTO
        if (considersShizuku) {
            if (!cachedShizukuRunning) {
                if (pref == SharePreference.SHIZUKU) {
                    binding.layoutShizukuAction.visibility = View.VISIBLE
                    binding.textShizukuHint.visibility = View.VISIBLE
                    binding.textShizukuHint.text = getString(R.string.hotspot_share_shizuku_not_running)
                    binding.btnShizukuPermission.visibility = View.GONE
                } else {
                    binding.layoutShizukuAction.visibility = View.GONE
                }
            } else if (!cachedShizukuGranted) {
                binding.layoutShizukuAction.visibility = View.VISIBLE
                binding.textShizukuHint.visibility = View.GONE
                binding.btnShizukuPermission.visibility = View.VISIBLE
            } else {
                binding.layoutShizukuAction.visibility = View.GONE
            }
        } else {
            binding.layoutShizukuAction.visibility = View.GONE
        }

        // Fail-closed row visibility (relevant for both Shizuku and Root modes)
        binding.rowFailClosed.visibility = if (pref != SharePreference.LAN_PROXY) {
            View.VISIBLE
        } else {
            View.GONE
        }
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
        ShareFailure.VPN_LOST -> getString(R.string.hotspot_share_vpn_dropped_warn)
        ShareFailure.BACKEND_NOT_IMPLEMENTED,
        ShareFailure.BACKEND_FAILED -> detail?.takeIf { it.isNotBlank() }
            ?: getString(R.string.hotspot_share_err_generic)
    }
    private fun ensureClashApiForManualLanIfNeeded() {
        if (!DataStore.allowLanAccess) return
        var changed = false
        if (LanSharingLogics.clashApiSecretShouldBeGenerated(DataStore.coreBoxClashApiSecret)) {
            DataStore.coreBoxClashApiSecret = LanSharingLogics.generateClashApiSecret()
            changed = true
        }
        val clashDecision = LanSharingLogics.ensureClashApiEnabledPort(DataStore.coreBoxClashApi)
        if (clashDecision.changed) {
            DataStore.coreBoxClashApi = clashDecision.newRawPort
            changed = true
        }
        if (changed) {
            needReload()
        }
    }

    private fun styleExpressiveViews() {
        listOf(
            binding.cardHero,
            binding.cardHotspotShare,
            binding.cardGateway,
            binding.cardHotspot,
            binding.cardWifi,
            binding.cardAuth
        ).forEach { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 20f) }

        listOf(
            binding.layoutShareTraffic,
            binding.bannerShareWarning,
            binding.boxHotspotSocksLink,
            binding.boxWifiSocksLink
        ).forEach { ExpressiveSettingsHelper.styleExpressiveNestedBox(it) }

        listOf(
            binding.btnRefreshLan,
            binding.btnShizukuPermission,
            binding.btnGatewayConfigure,
            binding.btnCopyHotspotIp,
            binding.btnCopyHotspotPort,
            binding.btnCopyHotspotLink,
            binding.btnShareHotspotLink,
            binding.btnCopyWifiIp,
            binding.btnCopyWifiPort,
            binding.btnCopyWifiLink,
            binding.btnShareWifiLink,
            binding.btnAuthAction
        ).forEach { ExpressiveSettingsHelper.styleOutlinedButton(it) }

        val mutedColor = getColorAttr(android.R.attr.textColorSecondary)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeServiceStatus, mutedColor)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeShareStatus, mutedColor)
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

    private fun updateShareClientCountLabel() {
        val state = HotspotShareStateStore.snapshot()
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

    override fun onDestroy() {
        statusReceiver?.let { runCatching { unregisterReceiver(it) } }
        statusReceiver = null
        netStateReceiver?.let { runCatching { unregisterReceiver(it) } }
        netStateReceiver = null
        HotspotGatewayManager.onGatewayChanged = null
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {}
        connection.disconnect(this)
        super.onDestroy()
    }
}
