package io.nekohasekai.sagernet.hotspotshare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.hotspotshare.root.LibsuShellExecutor
import io.nekohasekai.sagernet.hotspotshare.root.RootRules
import io.nekohasekai.sagernet.hotspotshare.root.RootTetherHelper
import io.nekohasekai.sagernet.hotspotshare.root.ShellExecutor
import io.nekohasekai.sagernet.hotspotshare.root.VpnRouteInfo
import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamInspector
import io.nekohasekai.sagernet.utils.LanSharingNetwork
import io.nekohasekai.sagernet.utils.TetherKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

class RootBackend(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val shellExecutor: ShellExecutor = LibsuShellExecutor,
    private val failClosedAction: () -> FailClosedAction = { FailClosedAction.BLOCK_TRAFFIC },
) : PausableHotspotShare, HotspotControllingBackend {

    override val method: ShareMethod = ShareMethod.ROOT

    private val _state = MutableStateFlow<ShareState>(ShareState.Off)
    override val state: StateFlow<ShareState> = _state.asStateFlow()

    private var watcherJob: Job? = null
    private var tetherDebounceJob: Job? = null
    private var tetherReceiver: BroadcastReceiver? = null
    private var activeVpn: VpnRouteInfo? = null
    private var appliedIfaces = setOf<String>()
    private var autoStartedHotspot = false

    @Volatile
    private var isFailClosedBlocked = false

    val activeInterfaces: Set<String> get() = appliedIfaces

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        shellExecutor.isRoot()
    }

    override suspend fun start(): Unit = withContext(Dispatchers.IO) {
        if (_state.value is ShareState.Active) return@withContext

        isFailClosedBlocked = false
        autoStartedHotspot = false
        _state.value = ShareState.Starting
        HotspotShareLogger.info(TAG, "Starting Root hotspot share session")

        if (!isAvailable()) {
            HotspotShareLogger.error(TAG, "Root is not available")
            _state.value = ShareState.Error(
                ShareFailure.ROOT_UNAVAILABLE,
                "Root izni verilmedi veya cihaz root'lu değil.",
            )
            return@withContext
        }

        val routeRes = shellExecutor.exec("ip route show table all")
        val vpn = RootRules.parseVpnRoute(routeRes.out)
        if (vpn == null) {
            HotspotShareLogger.error(TAG, "VPN routing table not found")
            _state.value = ShareState.Error(
                ShareFailure.VPN_NOT_RUNNING,
                "VPN yönlendirme tablosu bulunamadı. Lütfen önce VPN bağlantısını başlatın.",
            )
            return@withContext
        }

        var tetherIfaces = resolveTetheredInterfaces()
        if (tetherIfaces.isEmpty()) {
            // Auto-tether just like ShizukuBackend: launch RootTetherHelper via root app_process
            if (tryAutoStartHotspot()) {
                autoStartedHotspot = true
                val deadline = System.currentTimeMillis() + TETHER_WAIT_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    tetherIfaces = resolveTetheredInterfaces()
                    if (tetherIfaces.isNotEmpty()) break
                    delay(400)
                }
            }
        }

        if (tetherIfaces.isEmpty()) {
            _state.value = ShareState.Error(
                ShareFailure.HOTSPOT_NOT_ACTIVE,
                "Aktif bir hotspot/tethering arayüzü bulunamadı. Lütfen hotspot'u açıp tekrar deneyin.",
            )
            return@withContext
        }

        // Re-fetch routes after hotspot comes up so the newly assigned hotspot subnet is included
        val postRouteRes = if (autoStartedHotspot) {
            shellExecutor.exec("ip route show table all")
        } else {
            routeRes
        }
        val subnets = RootRules.parseHotspotSubnets(postRouteRes.out, tetherIfaces)
        val setupCmds = RootRules.buildSetupCommands(vpn, tetherIfaces, subnets)

        val res = shellExecutor.exec(setupCmds)
        if (!res.isSuccess) {
            HotspotShareLogger.error(TAG, "Failed to apply Root routing rules: ${res.err.joinToString("; ")}")
            shellExecutor.exec(RootRules.buildTeardownCommands())
            if (autoStartedHotspot) stopHotspot()
            _state.value = ShareState.Error(
                ShareFailure.BACKEND_FAILED,
                "Root yönlendirme kuralları uygulanamadı: ${res.err.joinToString("\n")}",
            )
            return@withContext
        }

        activeVpn = vpn
        appliedIfaces = tetherIfaces.toSet()

        registerTetherReceiver()
        startWatcher()

        val kinds = appliedIfaces.map { LanSharingNetwork.getTetherKindForInterface(it) }.distinct()
        HotspotShareLogger.info(
            TAG,
            "Root tether share ACTIVE (vpn=${vpn.tunInterface}/${vpn.table}, ifaces=$appliedIfaces, kinds=$kinds)",
        )
        _state.value = ShareState.Active(ShareMethod.ROOT)
    }

    override suspend fun stop(): Unit = stopInternal(stopHotspot = false)

    override suspend fun stopHotspotByUser(): Unit = stopInternal(stopHotspot = true)

    private suspend fun stopInternal(stopHotspot: Boolean): Unit = withContext(Dispatchers.IO) {
        HotspotShareLogger.info(TAG, "Stopping RootBackend (stopHotspot=$stopHotspot, isFailClosedBlocked=$isFailClosedBlocked)")
        stopWatcher()
        unregisterTetherReceiver()

        // Preserve fail-closed DROP rules during internal/controller teardown unless
        // user explicitly turned sharing OFF (stopHotspot = true).
        if (stopHotspot) {
            isFailClosedBlocked = false
            stopHotspot()
            shellExecutor.exec(RootRules.buildTeardownCommands())
        } else if (!isFailClosedBlocked) {
            shellExecutor.exec(RootRules.buildTeardownCommands())
        }
        autoStartedHotspot = false
        activeVpn = null
        appliedIfaces = emptySet()

        _state.value = ShareState.Off
    }

    private suspend fun resolveTetheredInterfaces(): List<String> {
        val merged = linkedSetOf<String>()
        merged += getActiveTetheredInterfaces(context)

        // Query dumpsys tethering from root shell (catches USB rndis/ncm, Bluetooth bt-pan/bnep, and OEM ap interfaces)
        val dumpsysOut = runCatching {
            shellExecutor.exec("dumpsys tethering 2>/dev/null").out.joinToString("\n")
        }.getOrDefault("")
        if (dumpsysOut.isNotBlank()) {
            merged += UpstreamInspector.parseTetheredDownstreams(dumpsysOut)
        }
        return if (LanSharingNetwork.USB_TETHER_ENABLED) {
            merged.toList()
        } else {
            merged.filterNot { LanSharingNetwork.getTetherKindForInterface(it) == TetherKind.USB }
        }
    }

    private fun tryAutoStartHotspot(): Boolean {
        val apkPath = runCatching { context.applicationInfo?.sourceDir }.getOrNull()
        if (apkPath.isNullOrBlank()) return false
        val helperClass = RootTetherHelper::class.java.name
        val res = runCatching {
            shellExecutor.exec(
                "CLASSPATH=\"$apkPath\" /system/bin/app_process /system/bin $helperClass start"
            )
        }.getOrNull() ?: return false
        if (res.isSuccess) return true

        // Best-effort secondary fallback via cmd wifi
        val softapRes = runCatching {
            shellExecutor.exec("cmd wifi start-tethered-hotspot 2>/dev/null || svc wifi enable 2>/dev/null")
        }.getOrNull()
        return softapRes?.isSuccess == true
    }

    private fun startWatcher() {
        watcherJob?.cancel()
        watcherJob = scope.launch {
            var vpnMissingStrikes = 0
            while (isActive) {
                delay(WATCHER_INTERVAL_MS)
                if (_state.value !is ShareState.Active) break

                val routeRes = shellExecutor.exec("ip route show table all")
                val currentVpn = RootRules.parseVpnRoute(routeRes.out)

                if (currentVpn == null) {
                    vpnMissingStrikes++
                    // Give TetherWatchService's RELOAD_GRACE_MS (8s) + suspendForVpnDrop()
                    // time to handle VPN reloads/disconnects cleanly without breaking auto-resume.
                    if (vpnMissingStrikes >= VPN_MISSING_STRIKES) {
                        handleVpnDropped()
                        break
                    }
                    continue
                }
                vpnMissingStrikes = 0

                // Self-healing: if Android Netd flushed FORWARD, changed VPN table, or a USB/BT/Wi-Fi
                // tether interface was added/removed while sharing is active, re-apply RootRules.
                val liveIfaces = resolveTetheredInterfaces().toSet()
                val targetIfaces = if (liveIfaces.isNotEmpty()) liveIfaces else appliedIfaces
                if (targetIfaces.isNotEmpty()) {
                    val ifacesChanged = liveIfaces.isNotEmpty() && liveIfaces != appliedIfaces
                    val checkChain = shellExecutor.exec("iptables -C FORWARD -j ${RootRules.CHAIN_FORWARD} 2>/dev/null")
                    if (ifacesChanged || !checkChain.isSuccess || currentVpn != activeVpn) {
                        if (ifacesChanged) {
                            HotspotShareLogger.info(TAG, "Root tethered interfaces updated: $appliedIfaces -> $targetIfaces")
                        }
                        val subnets = RootRules.parseHotspotSubnets(routeRes.out, targetIfaces)
                        if (shellExecutor.exec(RootRules.buildSetupCommands(currentVpn, targetIfaces, subnets)).isSuccess) {
                            appliedIfaces = targetIfaces
                            activeVpn = currentVpn
                        }
                    }
                }
            }
        }
    }

    private fun stopWatcher() {
        watcherJob?.cancel()
        watcherJob = null
    }

    suspend fun handleVpnDropped(action: FailClosedAction = failClosedAction()) {
        stopWatcher()
        activeVpn = null

        if (action == FailClosedAction.STOP_HOTSPOT) {
            HotspotShareLogger.warn(TAG, "Fail-closed STOP_HOTSPOT on RootBackend: tearing down rules and stopping Wi-Fi Hotspot")
            unregisterTetherReceiver()
            isFailClosedBlocked = false
            shellExecutor.exec(RootRules.buildTeardownCommands())
            stopHotspot()
        } else {
            // Default: BLOCK_TRAFFIC — keep Hotspot up behind DROP + blackhole lock so we can auto-resume when VPN returns
            isFailClosedBlocked = true
            val ifacesToBlock = appliedIfaces.ifEmpty { resolveTetheredInterfaces().toSet() }
            HotspotShareLogger.warn(TAG, "Fail-closed BLOCK_TRAFFIC on RootBackend: installing blackhole + DROP rules on $ifacesToBlock")
            shellExecutor.exec(RootRules.buildBlockTrafficCommands(ifacesToBlock))
        }

        _state.value = ShareState.Stopped(
            ShareFailure.VPN_LOST,
        )
    }

    override suspend fun pauseForVpnDrop() {
        handleVpnDropped(failClosedAction())
    }

    /**
     * Re-arms NAT & FORWARD rules after VPN reconnects from a fail-closed BLOCK_TRAFFIC pause.
     */
    override suspend fun resumeAfterVpnDrop(): Boolean = withContext(Dispatchers.IO) {
        if (!isFailClosedBlocked && failClosedAction() == FailClosedAction.STOP_HOTSPOT) {
            return@withContext false
        }
        val ifaces = appliedIfaces.ifEmpty { resolveTetheredInterfaces().toSet() }
        if (ifaces.isEmpty()) return@withContext false

        var routeOut: List<String> = emptyList()
        var vpnRoute: VpnRouteInfo? = null
        repeat(8) {
            val res = shellExecutor.exec("ip route show table all")
            vpnRoute = RootRules.parseVpnRoute(res.out)
            if (vpnRoute != null) {
                routeOut = res.out
                return@repeat
            }
            delay(500L)
        }
        val resolvedVpn = vpnRoute ?: return@withContext false
        val subnets = RootRules.parseHotspotSubnets(routeOut, ifaces)
        val cmds = RootRules.buildSetupCommands(resolvedVpn, ifaces, subnets)
        if (!shellExecutor.exec(cmds).isSuccess) {
            HotspotShareLogger.error(TAG, "Failed to re-apply Root setup rules on VPN resume")
            return@withContext false
        }

        isFailClosedBlocked = false
        activeVpn = resolvedVpn
        appliedIfaces = ifaces
        registerTetherReceiver()
        startWatcher()
        HotspotShareLogger.info(TAG, "Resumed Root hotspot share on $ifaces after VPN reconnect")
        _state.value = ShareState.Active(ShareMethod.ROOT)
        true
    }

    private fun stopHotspot() {
        val activeKinds = appliedIfaces.map { LanSharingNetwork.getTetherKindForInterface(it) }.toSet()
        val stopWifi = activeKinds.isEmpty() || TetherKind.WIFI in activeKinds
        val stopUsb = LanSharingNetwork.USB_TETHER_ENABLED && TetherKind.USB in activeKinds
        val stopBt = TetherKind.BLUETOOTH in activeKinds

        if (stopWifi) {
            val apkPath = runCatching { context.applicationInfo?.sourceDir }.getOrNull()
            if (!apkPath.isNullOrBlank()) {
                runCatching {
                    shellExecutor.exec(
                        "CLASSPATH=\"$apkPath\" /system/bin/app_process /system/bin ${RootTetherHelper::class.java.name} stop"
                    )
                }
            }
            runCatching {
                shellExecutor.exec("cmd wifi stop-softap 2>/dev/null || svc wifi disable-hotspot 2>/dev/null || true")
            }
        }
        if (stopUsb) {
            runCatching {
                shellExecutor.exec("cmd connectivity stop-tethering usb 2>/dev/null || cmd tethering stop-tethering usb 2>/dev/null || true")
            }
        }
        if (stopBt) {
            runCatching {
                shellExecutor.exec("cmd connectivity stop-tethering bluetooth 2>/dev/null || cmd tethering stop-tethering bluetooth 2>/dev/null || true")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val tm = context.getSystemService("tethering")
                if (tm != null) {
                    val method = tm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
                    if (stopWifi) runCatching { method.invoke(tm, 0) }
                    if (stopUsb) {
                        runCatching { method.invoke(tm, 1) }
                        if (appliedIfaces.any { it.lowercase().startsWith("ncm") }) {
                            runCatching { method.invoke(tm, 4) }
                        }
                    }
                    if (stopBt) runCatching { method.invoke(tm, 2) }
                }
            }
        }
    }

    private fun shouldMonitorTetherLoss(): Boolean =
        _state.value is ShareState.Active ||
            (isFailClosedBlocked && (_state.value as? ShareState.Stopped)?.cause == ShareFailure.VPN_LOST)

    private fun registerTetherReceiver() {
        if (tetherReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action != "android.net.conn.TETHER_STATE_CHANGED") return
                val activeList = (intent.getStringArrayListExtra("tetherArray").orEmpty() +
                    intent.getStringArrayListExtra("activeArray").orEmpty()).distinct()

                if (activeList.isNotEmpty()) {
                    tetherDebounceJob?.cancel()
                    tetherDebounceJob = null
                    if (_state.value is ShareState.Active) {
                        scope.launch {
                            val currentVpn = activeVpn ?: return@launch
                            val liveIfaces = resolveTetheredInterfaces().toSet()
                            if (liveIfaces.isNotEmpty() && liveIfaces != appliedIfaces) {
                                HotspotShareLogger.info(TAG, "TETHER_STATE_CHANGED updated Root interfaces: $appliedIfaces -> $liveIfaces")
                                val routeRes = shellExecutor.exec("ip route show table all")
                                val vpn = RootRules.parseVpnRoute(routeRes.out) ?: currentVpn
                                val subnets = RootRules.parseHotspotSubnets(routeRes.out, liveIfaces)
                                if (shellExecutor.exec(RootRules.buildSetupCommands(vpn, liveIfaces, subnets)).isSuccess) {
                                    appliedIfaces = liveIfaces
                                    activeVpn = vpn
                                }
                            }
                        }
                    }
                    return
                }

                if (shouldMonitorTetherLoss()) {
                    tetherDebounceJob?.cancel()
                    tetherDebounceJob = scope.launch {
                        delay(TETHER_LOSS_DEBOUNCE_MS)
                        if (shouldMonitorTetherLoss() && resolveTetheredInterfaces().isEmpty()) {
                            _state.value = ShareState.Stopped(ShareFailure.TETHER_LOST)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter("android.net.conn.TETHER_STATE_CHANGED")
        runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }.onSuccess {
            tetherReceiver = receiver
        }
    }

    private fun unregisterTetherReceiver() {
        tetherDebounceJob?.cancel()
        tetherDebounceJob = null
        tetherReceiver?.let {
            runCatching { context.unregisterReceiver(it) }
            tetherReceiver = null
        }
    }

    companion object {
        private const val TAG = "RootBackend"
        private const val WATCHER_INTERVAL_MS = 5_000L
        private const val VPN_MISSING_STRIKES = 2
        private const val TETHER_WAIT_TIMEOUT_MS = 10_000L
        private const val TETHER_LOSS_DEBOUNCE_MS = 2_000L
        private const val ROOT_CACHE_TTL_MS = 30_000L

        @Volatile
        private var cachedRootResult: Boolean? = null

        @Volatile
        private var cachedRootCheckedAtMs: Long = 0L

        fun isRootAvailable(forceRefresh: Boolean = false): Boolean {
            if (!forceRefresh) {
                val cached = cachedRootResult
                val ageMs = (System.nanoTime() / 1_000_000L) - cachedRootCheckedAtMs
                if (cached != null && (cached || ageMs < ROOT_CACHE_TTL_MS)) {
                    return cached
                }
            }
            val result = try {
                com.topjohnwu.superuser.Shell.isAppGrantedRoot() == true || LibsuShellExecutor.isRoot()
            } catch (_: Throwable) {
                false
            }
            cachedRootResult = result
            cachedRootCheckedAtMs = System.nanoTime() / 1_000_000L
            return result
        }

        fun cleanupLegacyRules(executor: ShellExecutor = LibsuShellExecutor) {
            try {
                if (executor.isRoot()) {
                    executor.exec(RootRules.buildTeardownCommands())
                }
            } catch (_: Throwable) {
            }
        }

        fun queryNeighborDump(executor: ShellExecutor = LibsuShellExecutor): String? {
            return try {
                if (!executor.isRoot()) return null
                val res = executor.exec(
                    "ip -4 neigh show 2>/dev/null; echo '---ARP---'; cat /proc/net/arp 2>/dev/null; echo '---HOSTS---'; cat /data/misc/dhcp/dnsmasq.leases /data/vendor/wifi/dhcp/dnsmasq.leases /data/misc/dhcp/dnsmasq.ap.leases /data/misc/irsi/dnsmasq.leases 2>/dev/null; dumpsys tethering 2>/dev/null | grep -iE 'AddressInfo|TetheredClient|hostname'",
                )
                if (res.out.isEmpty()) null else res.out.joinToString("\n")
            } catch (_: Throwable) {
                null
            }
        }

        fun getActiveTetheredInterfaces(context: Context): List<String> {
            val tethered = mutableListOf<String>()

            if (LanSharingNetwork.canReflectTetheredIfaces()) {
                tethered += LanSharingNetwork.getTetheredIfaces(
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager,
                )
            } else {
                tethered += LanSharingNetwork.readStickyTetheredIfaces(context)
            }

            if (tethered.isEmpty()) {
                runCatching {
                    val candidates = LanSharingNetwork.findHotspotCandidates(context)
                    candidates.forEach { tethered.add(it.interfaceName) }
                }
            }

            if (tethered.isEmpty()) {
                runCatching {
                    val ifaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
                    val candidateRegex = Regex("""^(?:ap\d+|wlan[1-9]\d*|swlan\d+|softap\d*|rndis\d+|usb\d+|ncm\d+|bt-pan\d*|bnep\d*)""")
                    for (nif in ifaces) {
                        val isUp = runCatching { nif.isUp }.getOrDefault(false)
                        if (isUp && (candidateRegex.containsMatchIn(nif.name) || LanSharingNetwork.isHotspotInterfaceName(nif.name))) {
                            val hasTetherIp = nif.inetAddresses.toList().any { addr ->
                                val ip = addr.hostAddress ?: ""
                                LanSharingNetwork.isPrivateIpv4(ip)
                            }
                            if (hasTetherIp) {
                                tethered.add(nif.name)
                            }
                        }
                    }
                }
            }

            return if (LanSharingNetwork.USB_TETHER_ENABLED) {
                tethered.distinct()
            } else {
                tethered.distinct()
                    .filterNot { LanSharingNetwork.getTetherKindForInterface(it) == TetherKind.USB }
            }
        }
    }
}
