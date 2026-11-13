package io.nekohasekai.sagernet.hotspotshare

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.hotspotshare.shizuku.HevTunnel
import io.nekohasekai.sagernet.hotspotshare.shizuku.IShareUserService
import io.nekohasekai.sagernet.hotspotshare.shizuku.ShareUserService
import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamReading
import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamTolerance
import io.nekohasekai.sagernet.utils.PlatformNotifications
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Rootless HotspotShare backend using Shizuku UserService to control AOSP
 * TestNetworkManager and TetheringManager under shell UID (2000).
 *
 * Includes full lifecycle hardening:
 * - Contract version check & stale daemon replacement
 * - IPv4-only TUN + IPv4 routes/DNS + keep-alive NetworkRequest in ShareUserService
 * - Preserves already-active hotspot on attach; starts SoftAp only when closed
 * - Upstream verification (`verifyUpstream`) after tethering starts
 * - Periodic `UpstreamTolerance` + debounced downstream watchdog
 * - Stops hotspot ONLY when user explicitly turns sharing off ([stopHotspotByUser])
 */
class ShizukuBackend(
    private val context: Context,
    private val hevTunnel: HevTunnel = HevTunnel(context),
    private val socksPort: Int = 2088,
) : PausableHotspotShare, HotspotControllingBackend {

    override val method: ShareMethod = ShareMethod.SHIZUKU

    private val _state = MutableStateFlow<ShareState>(ShareState.Off)
    override val state: StateFlow<ShareState> = _state.asStateFlow()

    private val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchdogJob: Job? = null

    private var userService: IShareUserService? = null
    @Volatile
    private var userServiceBinder: IBinder? = null
    private var boundArgs: Shizuku.UserServiceArgs? = null
    private var sessionTunPfd: ParcelFileDescriptor? = null

    @Volatile
    private var pausedForVpnDrop = false
    @Volatile
    private var intentionalUnbind = false
    @Volatile
    private var linkDeathOnConnect = true

    @Volatile
    var activeInterfaceName: String? = null
        private set

    private val deathRecipient = IBinder.DeathRecipient {
        if (intentionalUnbind) return@DeathRecipient
        backendScope.launch {
            handleUnexpectedServiceDeath()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val stub = IShareUserService.Stub.asInterface(service)
            userService = stub
            userServiceBinder = service
            activeServiceInstance = stub
            if (linkDeathOnConnect && !intentionalUnbind) {
                runCatching { service?.linkToDeath(deathRecipient, 0) }
            }
            connectDeferred?.complete(stub)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            unlinkDeathRecipient()
            if (activeServiceInstance === userService) {
                activeServiceInstance = null
            }
            userService = null
            connectDeferred = null
        }
    }

    private fun unlinkDeathRecipient() {
        val binder = userServiceBinder
        userServiceBinder = null
        if (binder != null) {
            runCatching { binder.unlinkToDeath(deathRecipient, 0) }
        }
    }

    private var connectDeferred: CompletableDeferred<IShareUserService>? = null

    override suspend fun start(): Unit = withContext(Dispatchers.IO) {
        if (_state.value is ShareState.Active) return@withContext
        _state.value = ShareState.Starting

        // 1. Verify Shizuku availability and permission
        if (!isShizukuRunning()) {
            _state.value = ShareState.Error(ShareFailure.SHIZUKU_NOT_RUNNING)
            return@withContext
        }
        if (!isShizukuGranted(context)) {
            _state.value = ShareState.Error(ShareFailure.SHIZUKU_PERMISSION_DENIED)
            return@withContext
        }

        // 2. Connect to Shizuku UserService (verifying contract version)
        val service: IShareUserService
        try {
            service = bindVerifiedService()
        } catch (t: Throwable) {
            _state.value = ShareState.Error(
                ShareFailure.BACKEND_DIED,
                "Failed to bind Shizuku UserService: ${t.message}"
            )
            return@withContext
        }

        // 2.5. Ensure sing-box loopback SOCKS inbound (2088) is actually listening
        // (critical if VPN started before Hotspot Share was enabled).
        if (!ensureLoopbackSocksReady()) {
            abortStartSession(
                service,
                ShareFailure.VPN_NOT_RUNNING,
                "127.0.0.1:$socksPort dahili SOCKS girişi hazır değil. Lütfen VPN bağlantısını başlatın."
            )
            return@withContext
        }

        // 3. Record whether SoftAp is already open, create dual-stack test network TUN +
        // TRANSPORT_TEST keep-alive, and start hev-socks5-tunnel FIRST so packets arriving on
        // upstream attachment already have a live datapath (mirrors carlelieser/shizzi).
        val hotspotWasAlreadyOpen = runCatching { service.isWifiHotspotActive() }.getOrDefault(false)

        val tunPfd = try {
            service.createTestNetwork(
                listOf("1.1.1.1", "8.8.8.8", "2606:4700:4700::1111", "2001:4860:4860::8888"),
            )
        } catch (t: Throwable) {
            abortStartSession(service, ShareFailure.BACKEND_FAILED, "Failed to create Test Network: ${t.message}")
            return@withContext
        }

        val ifaceName = runCatching { service.interfaceName }.getOrNull() ?: "testtun0"
        activeInterfaceName = ifaceName
        sessionTunPfd = tunPfd

        // 4. Start hev-socks5-tunnel with a duplicated FD (sessionTunPfd stays owned by ShizukuBackend
        // across VPN-drop pauses so testtun0 remains up as a zero-leak blackhole when paused).
        val hevPfd = runCatching { tunPfd.dup() }.getOrElse { t ->
            abortStartSession(service, ShareFailure.BACKEND_FAILED, "Failed to duplicate TUN FD: ${t.message}")
            return@withContext
        }
        val hevStarted = try {
            hevTunnel.start(hevPfd, socksPort, HevTunnel.DEFAULT_TUN_MTU)
        } catch (t: Throwable) {
            runCatching { hevPfd.close() }
            abortStartSession(service, ShareFailure.BACKEND_FAILED, "hev-socks5-tunnel start failed: ${t.message}")
            return@withContext
        }

        if (!hevStarted) {
            abortStartSession(service, ShareFailure.BACKEND_FAILED, "hev-socks5-tunnel start returned false")
            return@withContext
        }

        // 5. Arm preferTestNetworks AFTER testtunN and HevTunnel are up, then ensure Wi-Fi tethering is active.
        try {
            service.setPreferTestNetworks(true)

            val tetherStarted = service.startTethering()
            if (!tetherStarted) {
                abortStartSession(
                    service,
                    ShareFailure.BACKEND_FAILED,
                    "Wi-Fi hotspot failed to start via TetheringManager / WifiManager"
                )
                return@withContext
            }

            // 6. Verify via dumpsys tethering that testtunN is selected as sole upstream.
            // If an already-open SoftAp retained cellular/wifi upstream (common on OEM tethering stacks),
            // re-arm preferTestNetworks and restart the Wi-Fi tethering downstream so upstream selection re-runs.
            var verified = runCatching { service.verifyUpstream(INITIAL_UPSTREAM_SETTLE_MS) }.getOrDefault(false)
            if (!verified && !hotspotWasAlreadyOpen) {
                verified = runCatching { service.verifyUpstream(UPSTREAM_SETTLE_MS) }.getOrDefault(false)
            }
            if (!verified) {
                runCatching { service.setPreferTestNetworks(true) }
                val restarted = runCatching { service.restartWifiTethering() }.getOrDefault(false)
                if (!restarted && !runCatching { service.isWifiHotspotActive() }.getOrDefault(false)) {
                    abortStartSession(
                        service,
                        ShareFailure.BACKEND_FAILED,
                        "Wi-Fi hotspot failed to restart onto test-network upstream ($ifaceName)"
                    )
                    return@withContext
                }
                verified = runCatching { service.verifyUpstream(UPSTREAM_SETTLE_MS) }.getOrDefault(false)
            }
            if (!verified) {
                abortStartSession(
                    service,
                    ShareFailure.BACKEND_FAILED,
                    "Upstream failed to attach to $ifaceName (another upstream remained selected)"
                )
                return@withContext
            }
        } catch (t: Throwable) {
            abortStartSession(service, ShareFailure.BACKEND_FAILED, "Exception starting tethering: ${t.message}")
            return@withContext
        }

        pausedForVpnDrop = false
        _state.value = ShareState.Active(ShareMethod.SHIZUKU)
        startUpstreamWatchdog(service, ifaceName)
    }

    private fun startUpstreamWatchdog(service: IShareUserService, expectedIface: String) {
        watchdogJob?.cancel()
        val tolerance = UpstreamTolerance(expectedIface) { strike ->
            Log.w(TAG, strike)
        }
        val startElapsedMs = android.os.SystemClock.elapsedRealtime()
        var downstreamOffStrikes = 0
        watchdogJob = backendScope.launch {
            while (isActive && (_state.value is ShareState.Active || pausedForVpnDrop)) {
                delay(WATCHDOG_INTERVAL_MS)
                if (_state.value !is ShareState.Active && !pausedForVpnDrop) break

                val rawHealth = runCatching { service.checkUpstreamHealth() }.getOrNull()
                    ?: break
                var effectiveHealth = rawHealth
                if (effectiveHealth == "DOWNSTREAM_OFF") {
                    if (android.os.SystemClock.elapsedRealtime() - startElapsedMs < STARTUP_DOWNSTREAM_GRACE_MS) {
                        continue
                    }
                    val hotspotStillUp = runCatching { service.isWifiHotspotActive() }.getOrDefault(false)
                    if (hotspotStillUp) {
                        downstreamOffStrikes = 0
                        effectiveHealth = "AWAITING"
                    } else {
                        downstreamOffStrikes++
                        if (downstreamOffStrikes < DOWNSTREAM_OFF_STRIKES) {
                            Log.w(TAG, "Downstream reported off ($downstreamOffStrikes/$DOWNSTREAM_OFF_STRIKES); waiting before drop")
                            continue
                        }
                        Log.i(TAG, "Downstream hotspot stayed off; stopping Shizuku share cleanly")
                        teardownSession(service, stopHotspot = false)
                        _state.value = ShareState.Stopped(ShareFailure.TETHER_LOST)
                        break
                    }
                } else {
                    downstreamOffStrikes = 0
                }
                val isAwaiting = effectiveHealth == "AWAITING"
                val (reading, names) = parseHealthString(effectiveHealth, expectedIface)
                val verdict = tolerance.judge(reading, names, isDownstreamAwaiting = isAwaiting)
                if (verdict != null) {
                    Log.e(TAG, "Upstream watchdog triggered fail-closed teardown: $verdict")
                    val hotspotStillUp = runCatching { service.isWifiHotspotActive() }.getOrDefault(false)
                    teardownSession(service, stopHotspot = false)
                    _state.value = resolveWatchdogFailureState(hotspotStillUp, verdict)
                    break
                }
            }
        }
    }

    private fun parseHealthString(rawHealth: String, expectedIface: String): Pair<UpstreamReading, List<String>> {
        return when {
            rawHealth == "HEALTHY" -> UpstreamReading.HEALTHY to listOf(expectedIface)
            rawHealth == "ABSENT" || rawHealth == "AWAITING" -> UpstreamReading.ABSENT to emptyList()
            rawHealth == "TIMED_OUT" -> UpstreamReading.TIMED_OUT to emptyList()
            rawHealth.startsWith("DRIFTED:") -> {
                val driftedList = rawHealth.removePrefix("DRIFTED:").split(",").filter { it.isNotBlank() }
                UpstreamReading.DRIFTED to driftedList
            }
            else -> UpstreamReading.HEALTHY to listOf(expectedIface)
        }
    }

    /**
     * Pauses forwarding on VPN drop while keeping `testtunN`, `preferTestNetworks`, and
     * hotspot UP as a zero-leak blackhole so clients cannot fall back to mobile data.
     */
    override suspend fun pauseForVpnDrop(): Unit = withContext(Dispatchers.IO) {
        if (pausedForVpnDrop && _state.value is ShareState.Stopped) return@withContext
        pausedForVpnDrop = true
        hevTunnel.stop()
        _state.value = ShareState.Stopped(ShareFailure.VPN_LOST)
    }

    /**
     * Resumes `hev-socks5-tunnel` on the still-held `testtunN` when the VPN reconnects.
     */
    override suspend fun resumeAfterVpnDrop(): Boolean = withContext(Dispatchers.IO) {
        val service = userService ?: return@withContext false
        val pfd = sessionTunPfd ?: return@withContext false
        val expectedIface = activeInterfaceName ?: return@withContext false

        if (!ensureLoopbackSocksReady()) return@withContext false

        val hevPfd = runCatching { pfd.dup() }.getOrNull() ?: return@withContext false
        val started = runCatching {
            hevTunnel.start(hevPfd, socksPort, HevTunnel.DEFAULT_TUN_MTU)
        }.getOrElse {
            runCatching { hevPfd.close() }
            false
        }
        if (!started) {
            stop()
            return@withContext false
        }

        pausedForVpnDrop = false
        _state.value = ShareState.Active(ShareMethod.SHIZUKU)
        if (watchdogJob?.isActive != true) {
            startUpstreamWatchdog(service, expectedIface)
        }
        true
    }

    override suspend fun stop(): Unit = stopInternal(stopHotspot = false)

    override suspend fun stopHotspotByUser(): Unit = stopInternal(stopHotspot = true)

    private suspend fun stopInternal(stopHotspot: Boolean): Unit = withContext(Dispatchers.IO) {
        val current = _state.value
        if (current is ShareState.Off && !pausedForVpnDrop && sessionTunPfd == null && userService == null && !stopHotspot) {
            return@withContext
        }

        watchdogJob?.cancel()
        watchdogJob = null
        teardownSession(userService, stopHotspot = stopHotspot)

        if (DataStore.serviceState.connected && !DataStore.hotspotShareEnabled) {
            withContext(Dispatchers.Main) {
                runCatching { SagerNet.reloadService() }
            }
        }

        _state.value = ShareState.Off
    }

    private fun abortStartSession(
        service: IShareUserService,
        failure: ShareFailure,
        message: String,
    ) {
        teardownSession(service, stopHotspot = false)
        _state.value = ShareState.Error(failure, message)
    }

    private fun teardownSession(service: IShareUserService?, stopHotspot: Boolean) {
        pausedForVpnDrop = false
        hevTunnel.stop()
        closeSessionTunPfd()
        if (service != null) {
            if (stopHotspot) {
                runCatching { service.stopTethering() }
            }
            safeCleanup(service)
            unbindService()
        }
        activeInterfaceName = null
        activeServiceInstance = null
    }

    private suspend fun ensureLoopbackSocksReady(): Boolean {
        if (isLoopbackPortOpen(socksPort)) return true
        val vpnRunning = DataStore.serviceState.connected ||
            DataStore.serviceState.started ||
            isLoopbackPortOpen(DataStore.inboundSocksPort)
        if (!vpnRunning) return false

        Log.i(TAG, "Loopback port $socksPort not open yet; reloading VPN core to bind share inbound")
        withContext(Dispatchers.Main) {
            runCatching { SagerNet.reloadService() }
        }
        val deadline = System.currentTimeMillis() + SOCKS_WAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isLoopbackPortOpen(socksPort)) return true
            delay(200)
        }
        return isLoopbackPortOpen(socksPort)
    }

    private fun isLoopbackPortOpen(port: Int): Boolean {
        return runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), 300)
                true
            }
        }.getOrDefault(false)
    }

    /**
     * Proactively cleans up leftover test networks and tethering state (dirty state recovery).
     */
    suspend fun recoverDirtyState(): Unit = withContext(Dispatchers.IO) {
        if (!isShizukuAvailableAndGranted()) return@withContext
        try {
            val service = bindVerifiedService(linkDeath = false)
            try {
                service.cleanupDirtyState()
            } finally {
                unbindService()
            }
        } catch (_: Throwable) {
            unbindService()
        }
    }

    fun getTrafficStats(): Pair<Long, Long>? {
        val hevStats = hevTunnel.getStats()
        if (hevStats.size >= 4) {
            return Pair(hevStats[3].coerceAtLeast(0L), hevStats[1].coerceAtLeast(0L))
        }

        val iface = activeInterfaceName ?: "testtun0"
        return ProcNetDevParser.readCurrent(setOf(iface))
    }

    fun getConnectedClientCount(): Int {
        return runCatching { userService?.connectedClientCount ?: 0 }.getOrDefault(0)
    }

    private suspend fun handleUnexpectedServiceDeath() {
        val current = _state.value
        if (intentionalUnbind || current is ShareState.Off || current is ShareState.Stopped) {
            return
        }
        unlinkDeathRecipient()
        pausedForVpnDrop = false
        watchdogJob?.cancel()
        watchdogJob = null
        hevTunnel.stop()
        closeSessionTunPfd()
        userService = null
        activeInterfaceName = null
        var recovered = false
        try {
            val recoveryService = bindService(linkDeath = false)
            try {
                recoveryService.cleanupDirtyState()
                recovered = true
            } finally {
                unbindService()
            }
        } catch (_: Throwable) {
            unbindService()
        }

        if (!recovered) {
            PlatformNotifications.hotspotShareDropped(context)
        }

        _state.value = ShareState.Error(
            ShareFailure.BACKEND_DIED,
            if (recovered) "Paylaşım servisi kapandı ve hotspot temizlendi."
            else "Paylaşım düştü, hotspot yeniden başlatılmalı!"
        )
    }

    private fun closeSessionTunPfd() {
        runCatching { sessionTunPfd?.close() }
        sessionTunPfd = null
    }

    private fun safeCleanup(service: IShareUserService) {
        try {
            service.cleanupDirtyState()
        } catch (_: Throwable) {
        }
    }

    private suspend fun bindVerifiedService(linkDeath: Boolean = true): IShareUserService {
        val first = bindService(linkDeath)
        val version = runCatching { first.contractVersion }.getOrDefault(-1)
        if (version == ShareUserService.CONTRACT_VERSION) {
            return first
        }
        Log.w(TAG, "Stale ShareUserService contract version ($version != ${ShareUserService.CONTRACT_VERSION}); restarting")
        unbindService()
        delay(300)
        return bindService(linkDeath)
    }

    private suspend fun bindService(linkDeath: Boolean = true): IShareUserService {
        if (!intentionalUnbind) {
            userService?.let { return it }
        }

        intentionalUnbind = !linkDeath
        linkDeathOnConnect = linkDeath

        val deferred = CompletableDeferred<IShareUserService>()
        connectDeferred = deferred

        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShareUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("hotspot_share")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
            .tag("share_service")

        boundArgs = args
        Shizuku.bindUserService(args, serviceConnection)

        val service = withTimeoutOrNull(10_000) {
            deferred.await()
        }
        if (service == null) {
            connectDeferred = null
            unbindService()
            throw IllegalStateException("Timeout binding Shizuku UserService")
        }

        return service
    }

    private fun unbindService() {
        intentionalUnbind = true
        unlinkDeathRecipient()
        if (activeServiceInstance === userService) {
            activeServiceInstance = null
        }
        userService = null
        connectDeferred = null
        val args = boundArgs ?: return
        boundArgs = null
        try {
            Shizuku.unbindUserService(args, serviceConnection, true)
        } catch (_: Throwable) {
        }
    }

    private suspend fun isShizukuAvailableAndGranted(): Boolean = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) {
            val binderDeferred = CompletableDeferred<Boolean>()
            val listener = Shizuku.OnBinderReceivedListener {
                binderDeferred.complete(true)
            }
            Shizuku.addBinderReceivedListenerSticky(listener)
            withTimeoutOrNull(3000) {
                binderDeferred.await()
            }
            Shizuku.removeBinderReceivedListener(listener)
        }

        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku binder is not available (pingBinder=false)")
            return@withContext false
        }

        try {
            val perm = Shizuku.checkSelfPermission()
            val granted = perm == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "Shizuku.checkSelfPermission() = $perm, granted = $granted")
            granted
        } catch (t: Throwable) {
            Log.e(TAG, "Shizuku.checkSelfPermission() threw: ${t.message}", t)
            false
        }
    }

    companion object {
        private const val TAG = "ShizukuBackend"
        private const val INITIAL_UPSTREAM_SETTLE_MS = 3_500L
        private const val UPSTREAM_SETTLE_MS = 8_000L
        private const val WATCHDOG_INTERVAL_MS = 5_000L
        private const val STARTUP_DOWNSTREAM_GRACE_MS = 10_000L
        private const val DOWNSTREAM_OFF_STRIKES = 3
        private const val SOCKS_WAIT_TIMEOUT_MS = 6_000L

        internal fun resolveWatchdogFailureState(hotspotStillUp: Boolean, verdict: String): ShareState {
            return if (!hotspotStillUp) {
                ShareState.Stopped(ShareFailure.TETHER_LOST)
            } else {
                ShareState.Error(ShareFailure.BACKEND_FAILED, verdict)
            }
        }

        @Volatile
        private var activeServiceInstance: IShareUserService? = null

        fun queryActiveNeighborDump(): String? {
            return runCatching { activeServiceInstance?.neighborDump }.getOrNull()
        }

        fun queryActiveClientCount(): Int {
            return runCatching { activeServiceInstance?.connectedClientCount ?: 0 }.getOrDefault(0)
        }

        fun isShizukuRunning(): Boolean {
            return try {
                Shizuku.pingBinder()
            } catch (_: Throwable) {
                false
            }
        }

        fun isShizukuGranted(context: Context): Boolean {
            return try {
                if (!isShizukuRunning()) return false
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                false
            }
        }
    }
}
