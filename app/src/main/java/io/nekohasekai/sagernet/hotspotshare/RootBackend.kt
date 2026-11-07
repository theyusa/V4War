package io.nekohasekai.sagernet.hotspotshare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import io.nekohasekai.sagernet.hotspotshare.root.LibsuShellExecutor
import io.nekohasekai.sagernet.hotspotshare.root.RootRules
import io.nekohasekai.sagernet.hotspotshare.root.ShellExecutor
import io.nekohasekai.sagernet.hotspotshare.root.VpnRouteInfo
import io.nekohasekai.sagernet.utils.PlatformNotifications
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

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        shellExecutor.isRoot()
    }

    override suspend fun start(): Unit = withContext(Dispatchers.IO) {
        if (_state.value is ShareState.Active) return@withContext

        _state.value = ShareState.Starting

        if (!isAvailable()) {
            _state.value = ShareState.Error(
                ShareFailure.ROOT_UNAVAILABLE,
                "Root izni verilmedi veya cihaz root'lu değil.",
            )
            return@withContext
        }

        var tetherIfaces = getActiveTetheredInterfaces(context)
        if (tetherIfaces.isEmpty()) {
            delay(1000)
            tetherIfaces = getActiveTetheredInterfaces(context)
        }

        if (tetherIfaces.isEmpty()) {
            _state.value = ShareState.Error(
                ShareFailure.HOTSPOT_NOT_ACTIVE,
                "Aktif bir hotspot/tethering arayüzü bulunamadı. Lütfen önce hotspot'u açın.",
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

        val subnets = RootRules.parseHotspotSubnets(routeRes.out, tetherIfaces)
        val setupCmds = RootRules.buildSetupCommands(vpn, tetherIfaces, subnets)

        val res = shellExecutor.exec(setupCmds)
        if (!res.isSuccess) {
            shellExecutor.exec(RootRules.buildTeardownCommands())
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

        shellExecutor.exec(RootRules.buildTeardownCommands())
        activeVpn = null
        appliedIfaces = emptySet()

        _state.value = ShareState.Off
    }

    private fun startWatcher() {
        watcherJob?.cancel()
        watcherJob = scope.launch {
            while (isActive) {
                delay(3000)
                if (_state.value !is ShareState.Active) break

                val routeRes = shellExecutor.exec("ip route show table all")
                val currentVpn = RootRules.parseVpnRoute(routeRes.out)

                if (currentVpn == null) {
                    // VPN dropped unexpectedly!
                    handleVpnDropped()
                    break
                }
            }
        }
    }

    private fun stopWatcher() {
        watcherJob?.cancel()
        watcherJob = null
    }

    private suspend fun handleVpnDropped() {
        activeVpn = null
        unregisterTetherReceiver()

        val action = failClosedAction()
        if (action == FailClosedAction.STOP_HOTSPOT) {
            shellExecutor.exec(RootRules.buildTeardownCommands())
            stopHotspot()
        } else {
            // Default: BLOCK_TRAFFIC
            shellExecutor.exec(RootRules.buildBlockTrafficCommands(appliedIfaces))
        }

        PlatformNotifications.hotspotShareDropped(context)

        _state.value = ShareState.Stopped(
            ShareFailure.VPN_LOST,
        )
    }

    private fun stopHotspot() {
        try {
            shellExecutor.exec("cmd wifi stop-softap 2>/dev/null || svc wifi disable-hotspot 2>/dev/null || true")
        } catch (_: Throwable) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val tm = context.getSystemService("tethering")
                if (tm != null) {
                    val method = tm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
                    method.invoke(tm, 0)
                }
            } catch (_: Throwable) {
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

                    if (activeList.isEmpty()) {
                        // Hotspot turned off by user or system
                        stop()
                    }
                }
            }
        }
        val filter = IntentFilter("android.net.conn.TETHER_STATE_CHANGED")
        context.registerReceiver(receiver, filter)
        tetherReceiver = receiver
    }

    private fun unregisterTetherReceiver() {
        tetherReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Throwable) {
            }
            tetherReceiver = null
        }
    }

    companion object {
        fun cleanupLegacyRules(executor: ShellExecutor = LibsuShellExecutor) {
            try {
                if (executor.isRoot()) {
                    executor.exec(RootRules.buildTeardownCommands())
                }
            } catch (_: Throwable) {
            }
        }

        fun getActiveTetheredInterfaces(context: Context): List<String> {
            val tethered = mutableListOf<String>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val tm = context.getSystemService("tethering")
                    if (tm != null) {
                        val method = tm.javaClass.getMethod("getTetheredIfaces")
                        val ifaces = method.invoke(tm) as? Array<*>
                        ifaces?.forEach { iface ->
                            if (iface is String && iface.isNotBlank()) tethered.add(iface)
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            if (tethered.isEmpty()) {
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    if (cm != null) {
                        val method = cm.javaClass.getMethod("getTetheredIfaces")
                        val ifaces = method.invoke(cm) as? Array<*>
                        ifaces?.forEach { iface ->
                            if (iface is String && iface.isNotBlank()) tethered.add(iface)
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            if (tethered.isEmpty()) {
                try {
                    val ifaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
                    val candidateRegex = Regex("""^(?:ap\d+|wlan\d+|rndis\d+|usb\d+|bt-pan\d*)""")
                    for (nif in ifaces) {
                        if (nif.isUp && candidateRegex.containsMatchIn(nif.name)) {
                            val hasTetherIp = nif.inetAddresses.toList().any { addr ->
                                val ip = addr.hostAddress ?: ""
                                ip.startsWith("192.168.4") || ip.startsWith("192.168.2")
                            }
                            if (hasTetherIp) {
                                tethered.add(nif.name)
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            return tethered.distinct()
        }
    }
}
