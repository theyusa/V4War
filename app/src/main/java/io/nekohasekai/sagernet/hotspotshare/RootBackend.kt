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
) : HotspotShare {

    override val method: ShareMethod = ShareMethod.ROOT

    private val _state = MutableStateFlow<ShareState>(ShareState.Off)
    override val state: StateFlow<ShareState> = _state.asStateFlow()

    private var watcherJob: Job? = null
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

        if (!isAvailable()) {
            _state.value = ShareState.Error(
                ShareFailure.ROOT_UNAVAILABLE,
                "Root izni verilmedi veya cihaz root'lu değil.",
            )
            return@withContext
        }

        val routeRes = shellExecutor.exec("ip route show table all")
        val vpn = RootRules.parseVpnRoute(routeRes.out)
        if (vpn == null) {
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

        _state.value = ShareState.Active(ShareMethod.ROOT)
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        stopWatcher()
        unregisterTetherReceiver()

        // Preserve fail-closed DROP rules during controller mirror auto-teardown;
        // explicit user stop in HotspotShareController calls RootBackend.cleanupLegacyRules().
        if (!isFailClosedBlocked) {
            shellExecutor.exec(RootRules.buildTeardownCommands())
            if (autoStartedHotspot) {
                stopHotspot()
            }
        }
        autoStartedHotspot = false
        activeVpn = null
        appliedIfaces = emptySet()

        _state.value = ShareState.Off
    }

    private suspend fun resolveTetheredInterfaces(): List<String> {
        val fromPlatform = getActiveTetheredInterfaces(context)
        if (fromPlatform.isNotEmpty()) return fromPlatform

        // Query dumpsys tethering from root shell (catches USB rndis / OEM ap interfaces)
        val dumpsysOut = runCatching {
            shellExecutor.exec("dumpsys tethering 2>/dev/null").out.joinToString("\n")
        }.getOrDefault("")
        if (dumpsysOut.isNotBlank()) {
            val parsed = UpstreamInspector.parseTetheredDownstreams(dumpsysOut)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
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
            while (isActive) {
                delay(WATCHER_INTERVAL_MS)
                if (_state.value !is ShareState.Active) break

                val routeRes = shellExecutor.exec("ip route show table all")
                val currentVpn = RootRules.parseVpnRoute(routeRes.out)

                if (currentVpn == null) {
                    handleVpnDropped()
                    break
                }

                // Self-healing: if Android Netd flushed FORWARD or changed VPN table after cellular/Wi-Fi handoff
                val ifaces = appliedIfaces
                if (ifaces.isNotEmpty()) {
                    val checkChain = shellExecutor.exec("iptables -C FORWARD -j ${RootRules.CHAIN_FORWARD} 2>/dev/null")
                    if (!checkChain.isSuccess || currentVpn != activeVpn) {
                        val subnets = RootRules.parseHotspotSubnets(routeRes.out, ifaces)
                        shellExecutor.exec(RootRules.buildSetupCommands(currentVpn, ifaces, subnets))
                        activeVpn = currentVpn
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
        unregisterTetherReceiver()

        if (action == FailClosedAction.STOP_HOTSPOT) {
            isFailClosedBlocked = false
            shellExecutor.exec(RootRules.buildTeardownCommands())
            stopHotspot()
        } else {
            // Default: BLOCK_TRAFFIC — stays armed across controller teardown until user turns sharing off
            isFailClosedBlocked = true
            val ifacesToBlock = appliedIfaces.ifEmpty { resolveTetheredInterfaces().toSet() }
            shellExecutor.exec(RootRules.buildBlockTrafficCommands(ifacesToBlock))
        }

        _state.value = ShareState.Stopped(
            ShareFailure.VPN_LOST,
        )
    }

    private fun stopHotspot() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val tm = context.getSystemService("tethering")
                if (tm != null) {
                    val method = tm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
                    method.invoke(tm, 0)
                }
            }
        }
    }

    private fun registerTetherReceiver() {
        if (tetherReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action != "android.net.conn.TETHER_STATE_CHANGED") return
                scope.launch {
                    val activeList = intent.getStringArrayListExtra("tetherArray")
                        ?: intent.getStringArrayListExtra("activeArray")
                        ?: emptyList()

                    if (activeList.isEmpty() && _state.value is ShareState.Active) {
                        _state.value = ShareState.Stopped(ShareFailure.TETHER_LOST)
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
        tetherReceiver?.let {
            runCatching { context.unregisterReceiver(it) }
            tetherReceiver = null
        }
    }

    companion object {
        private const val WATCHER_INTERVAL_MS = 3_000L
        private const val TETHER_WAIT_TIMEOUT_MS = 10_000L

        fun isRootAvailable(): Boolean {
            return try {
                com.topjohnwu.superuser.Shell.isAppGrantedRoot() == true || LibsuShellExecutor.isRoot()
            } catch (_: Throwable) {
                false
            }
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
                val res = executor.exec("ip -4 neigh show 2>/dev/null; echo '---ARP---'; cat /proc/net/arp 2>/dev/null")
                if (res.out.isEmpty()) null else res.out.joinToString("\n")
            } catch (_: Throwable) {
                null
            }
        }

        fun getActiveTetheredInterfaces(context: Context): List<String> {
            val tethered = mutableListOf<String>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    val tm = context.getSystemService("tethering")
                    if (tm != null) {
                        val method = tm.javaClass.getMethod("getTetheredIfaces")
                        val ifaces = method.invoke(tm) as? Array<*>
                        ifaces?.forEach { iface ->
                            if (iface is String && iface.isNotBlank()) tethered.add(iface)
                        }
                    }
                }
            }

            if (tethered.isEmpty()) {
                runCatching {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    if (cm != null) {
                        val method = cm.javaClass.getMethod("getTetheredIfaces")
                        val ifaces = method.invoke(cm) as? Array<*>
                        ifaces?.forEach { iface ->
                            if (iface is String && iface.isNotBlank()) tethered.add(iface)
                        }
                    }
                }
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
                    val candidateRegex = Regex("""^(?:ap\d+|wlan\d+|swlan\d+|softap\d*|rndis\d+|usb\d+|ncm\d+|bt-pan\d*)""")
                    for (nif in ifaces) {
                        if (nif.isUp && candidateRegex.containsMatchIn(nif.name)) {
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

            return tethered.distinct()
        }
    }
}
