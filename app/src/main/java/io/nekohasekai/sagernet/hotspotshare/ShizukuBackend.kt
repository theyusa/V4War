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
import io.nekohasekai.sagernet.hotspotshare.shizuku.ShizukuWatchdog
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * - `.daemon(true)` UserService survival across transient Shizuku server restarts
 * - UID 2000 `ShizukuWatchdog` + local ADB 127.0.0.1:5555 fallback revival
 * - Bounded binder-death recovery without dropping user's Wi-Fi Hotspot
 * - Zero-rating fail-closed protection (`STOP_HOTSPOT` or `BLOCK_TRAFFIC` with 2s blackhole health monitor)
 */
class ShizukuBackend(
    private val context: Context,
    private val hevTunnel: HevTunnel = HevTunnel(context),
    private val socksPort: Int = 2088,
    private val failClosedAction: () -> FailClosedAction = {
        runCatching { FailClosedAction.fromCode(DataStore.hotspotShareFailClosed) }
            .getOrDefault(FailClosedAction.BLOCK_TRAFFIC)
    },
) : PausableHotspotShare, HotspotControllingBackend {

    override val method: ShareMethod = ShareMethod.SHIZUKU

    private val _state = MutableStateFlow<ShareState>(ShareState.Off)
    override val state: StateFlow<ShareState> = _state.asStateFlow()

    private val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()
    private var watchdogJob: Job? = null
    private var pausedSafetyJob: Job? = null

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
            val deferred = connectDeferred
            if (intentionalUnbind || deferred == null) {
                runCatching { boundArgs?.let { Shizuku.unbindUserService(it, this, false) } }
                return
            }
            userService = stub
            userServiceBinder = service
            activeServiceInstance = stub
            if (linkDeathOnConnect && !intentionalUnbind) {
                runCatching { service?.linkToDeath(deathRecipient, 0) }
            }
            deferred.complete(stub)
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
        HotspotShareLogger.info(TAG, "Starting Shizuku hotspot share session")

        // 1. Verify Shizuku availability (attempting local ADB revival if down) and permission
        if (!isShizukuRunning()) {
            HotspotShareLogger.warn(TAG, "Shizuku binder not running on start(); attempting revival")
            ShizukuWatchdog.tryReviveShizukuViaLocalAdbOrShell()
        }
        if (!isShizukuRunning()) {
            HotspotShareLogger.error(TAG, "Shizuku is not running")
            _state.value = ShareState.Error(ShareFailure.SHIZUKU_NOT_RUNNING)
            return@withContext
        }
        if (!isShizukuGranted(context)) {
            HotspotShareLogger.error(TAG, "Shizuku permission denied")
            _state.value = ShareState.Error(ShareFailure.SHIZUKU_PERMISSION_DENIED)
            return@withContext
        }

        // 2. Connect to Shizuku UserService (verifying contract version)
        val service: IShareUserService
        try {
            service = bindVerifiedService()
        } catch (t: Throwable) {
            HotspotShareLogger.error(TAG, "Failed to bind Shizuku UserService: ${t.message}")
            _state.value = ShareState.Error(
                ShareFailure.BACKEND_DIED,
                "Failed to bind Shizuku UserService: ${t.message}"
            )
            return@withContext
        }

        // 2.5. Ensure sing-box loopback SOCKS inbound (2088) is actually listening
        if (!ensureLoopbackSocksReady()) {
            HotspotShareLogger.error(TAG, "Loopback SOCKS port 127.0.0.1:$socksPort is not ready")
            abortStartSession(
                service,
                ShareFailure.VPN_NOT_RUNNING,
                "127.0.0.1:$socksPort dahili SOCKS girişi hazır değil. Lütfen VPN bağlantısını başlatın."
            )
            return@withContext
        }

        // 3. Record whether SoftAp is already open, create dual-stack test network TUN +
        // TRANSPORT_TEST keep-alive, and start hev-socks5-tunnel FIRST so packets arriving on
        // upstream attachment already have a live datapath.
        val hotspotWasAlreadyOpen = runCatching { service.isWifiHotspotActive() }.getOrDefault(false)

        val tunPfd = try {
            service.createTestNetwork( DEFAULT_DNS_SERVERS )
        } catch (t: Throwable) {
            HotspotShareLogger.error(TAG, "createTestNetwork failed: ${t.message}")
            abortStartSession(service, ShareFailure.BACKEND_FAILED, "Failed to create Test Network: ${t.message}")
            return@withContext
        }

        val ifaceName = runCatching { service.interfaceName }.getOrNull() ?: "testtun0"
        activeInterfaceName = ifaceName
        sessionTunPfd = tunPfd

        // 4. Start hev-socks5-tunnel with a duplicated FD (sessionTunPfd stays owned by ShizukuBackend
        // across VPN-drop pauses so testtun0 remains up as a zero-leak blackhole when paused).
        val hevPfd = runCatching { tunPfd.dup() }.getOrElse { t ->
            HotspotShareLogger.error(TAG, "Failed to duplicate TUN FD: ${t.message}")
            abortStartSession(service, ShareFailure.BACKEND_FAILED, "Failed to duplicate TUN FD: ${t.message}")
            return@withContext
        }
        val hevStarted = try {
            hevTunnel.start(hevPfd, socksPort, HevTunnel.DEFAULT_TUN_MTU)
        } catch (t: Throwable) {
            runCatching { hevPfd.close() }
            HotspotShareLogger.error(TAG, "hev-socks5-tunnel start threw: ${t.message}")
            abortStartSession(service, ShareFailure.BACKEND_FAILED, "hev-socks5-tunnel start failed: ${t.message}")
            return@withContext
        }

        if (!hevStarted) {
            HotspotShareLogger.error(TAG, "hev-socks5-tunnel start returned false")
            abortStartSession(service, ShareFailure.BACKEND_FAILED, "hev-socks5-tunnel start returned false")
            return@withContext
        }

        // 5. Arm preferTestNetworks AFTER testtunN and HevTunnel are up, then ensure Wi-Fi tethering is active.
        try {
            service.setPreferTestNetworks(true)

            val tetherStarted = service.startTethering()
            if (!tetherStarted) {
                HotspotShareLogger.error(TAG, "Wi-Fi hotspot failed to start via TetheringManager / WifiManager")
                abortStartSession(
                    service,
                    ShareFailure.BACKEND_FAILED,
                    "Wi-Fi hotspot failed to start via TetheringManager / WifiManager"
                )
                return@withContext
            }

            // 6. Verify via dumpsys tethering that testtunN is selected as sole upstream.
            var verified = runCatching { service.verifyUpstream(INITIAL_UPSTREAM_SETTLE_MS) }.getOrDefault(false)
            if (!verified && !hotspotWasAlreadyOpen) {
                verified = runCatching { service.verifyUpstream(UPSTREAM_SETTLE_MS) }.getOrDefault(false)
            }
            if (!verified) {
                HotspotShareLogger.warn(TAG, "Initial upstream attach did not settle on $ifaceName; restarting Wi-Fi tethering downstream")
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
                HotspotShareLogger.error(TAG, "Upstream failed to attach to $ifaceName")
                abortStartSession(
                    service,
                    ShareFailure.BACKEND_FAILED,
                    "Upstream failed to attach to $ifaceName (another upstream remained selected)"
                )
                return@withContext
            }
        } catch (t: Throwable) {
            HotspotShareLogger.error(TAG, "Exception starting tethering: ${t.message}")
            abortStartSession(service, ShareFailure.BACKEND_FAILED, "Exception starting tethering: ${t.message}")
            return@withContext
        }

        pausedSafetyJob?.cancel()
        pausedSafetyJob = null
        pausedForVpnDrop = false
        HotspotShareLogger.info(TAG, "Shizuku hotspot share ACTIVE on $ifaceName")
        mirrorServiceLogs(service)
        _state.value = ShareState.Active(ShareMethod.SHIZUKU)
        startUpstreamWatchdog(service, ifaceName)
    }

    private fun startUpstreamWatchdog(service: IShareUserService, expectedIface: String) {
        watchdogJob?.cancel()
        val tolerance = UpstreamTolerance(expectedIface) { strike ->
            Log.w(TAG, strike)
            HotspotShareLogger.warn(TAG, strike)
        }
        val startElapsedMs = android.os.SystemClock.elapsedRealtime()
        var downstreamOffStrikes = 0
        var tickCount = 0
        watchdogJob = backendScope.launch {
            while (isActive && (_state.value is ShareState.Active || pausedForVpnDrop)) {
                delay(WATCHDOG_INTERVAL_MS)
                if (_state.value !is ShareState.Active && !pausedForVpnDrop) break

                val currentService = userService ?: service
                tickCount++
                if (HotspotShareLogger.isLoggingEnabled() && tickCount % 3 == 0) {
                    mirrorServiceLogs(currentService)
                }
                val rawHealth = runCatching { currentService.checkUpstreamHealth() }.getOrNull()
                    ?: break
                var effectiveHealth = rawHealth
                if (effectiveHealth == "DOWNSTREAM_OFF") {
                    if (android.os.SystemClock.elapsedRealtime() - startElapsedMs < STARTUP_DOWNSTREAM_GRACE_MS) {
                        continue
                    }
                    val hotspotStillUp = runCatching { currentService.isWifiHotspotActive() }.getOrDefault(false)
                    if (hotspotStillUp) {
                        downstreamOffStrikes = 0
                        effectiveHealth = "AWAITING"
                    } else {
                        downstreamOffStrikes++
                        if (downstreamOffStrikes < DOWNSTREAM_OFF_STRIKES) {
                            Log.w(TAG, "Downstream reported off ($downstreamOffStrikes/$DOWNSTREAM_OFF_STRIKES); waiting before drop")
                            HotspotShareLogger.warn(TAG, "Downstream reported off ($downstreamOffStrikes/$DOWNSTREAM_OFF_STRIKES); waiting before drop")
                            continue
                        }
                        Log.i(TAG, "Downstream hotspot stayed off; stopping Shizuku share cleanly")
                        HotspotShareLogger.info(TAG, "Downstream hotspot stayed off; stopping Shizuku share cleanly")
                        sessionMutex.withLock {
                            if (!isActive || intentionalUnbind || (_state.value !is ShareState.Active && !pausedForVpnDrop)) {
                                return@withLock
                            }
                            teardownSession(currentService, stopHotspot = false, terminateDaemon = true)
                            _state.value = ShareState.Stopped(ShareFailure.TETHER_LOST)
                        }
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
                    HotspotShareLogger.error(TAG, "Upstream watchdog triggered fail-closed teardown: $verdict")
                    val hotspotStillUp = runCatching { currentService.isWifiHotspotActive() }.getOrDefault(false)
                    sessionMutex.withLock {
                        if (!isActive || intentionalUnbind || (_state.value !is ShareState.Active && !pausedForVpnDrop)) {
                            return@withLock
                        }
                        teardownSession(currentService, stopHotspot = false, terminateDaemon = false)
                        _state.value = resolveWatchdogFailureState(hotspotStillUp, verdict)
                    }
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
     * Zero-rating / fail-closed protection when VPN drops:
     * - `STOP_HOTSPOT`: immediately stops HevTunnel AND shuts down Wi-Fi Hotspot (`stopTethering()`),
     *   preventing any possibility of carrier data leak.
     * - `BLOCK_TRAFFIC`: stops only HevTunnel while keeping `testtun0` + `setPreferTestNetworks(true)`
     *   active as a kernel blackhole, and launches a 2000ms safety monitor that emergency-stops
     *   the Hotspot if `testtun0` ever drifts or disappears while VPN is down.
     */
    override suspend fun pauseForVpnDrop(): Unit = withContext(Dispatchers.IO) {
        val action = runCatching { failClosedAction() }.getOrDefault(FailClosedAction.BLOCK_TRAFFIC)
        sessionMutex.withLock {
            if (action == FailClosedAction.STOP_HOTSPOT) {
                HotspotShareLogger.warn(
                    TAG,
                    "Fail-closed STOP_HOTSPOT triggered on VPN drop: stopping HevTunnel and shutting down Wi-Fi Hotspot immediately"
                )
                pausedSafetyJob?.cancel()
                pausedSafetyJob = null
                watchdogJob?.cancel()
                watchdogJob = null
                pausedForVpnDrop = false
                teardownSession(userService, stopHotspot = true, terminateDaemon = true)
                _state.value = ShareState.Stopped(ShareFailure.VPN_LOST)
                return@withLock
            }

            if (pausedForVpnDrop && _state.value is ShareState.Stopped) return@withLock
            HotspotShareLogger.warn(
                TAG,
                "Fail-closed BLOCK_TRAFFIC triggered on VPN drop: keeping ${activeInterfaceName ?: "testtun0"} as zero-leak blackhole"
            )
            pausedForVpnDrop = true
            hevTunnel.stop()
            _state.value = ShareState.Stopped(ShareFailure.VPN_LOST)
            startPausedBlackholeSafetyMonitor()
        }
    }

    private fun startPausedBlackholeSafetyMonitor() {
        pausedSafetyJob?.cancel()
        pausedSafetyJob = backendScope.launch {
            while (isActive && pausedForVpnDrop) {
                delay(PAUSED_SAFETY_INTERVAL_MS)
                if (!pausedForVpnDrop || intentionalUnbind) break

                val currentService = userService
                val expectedIface = activeInterfaceName
                val rawHealth = if (currentService != null && expectedIface != null) {
                    runCatching { currentService.checkUpstreamHealth() }.getOrNull()
                } else {
                    null
                }

                if (rawHealth != null && isSafeBlackholeHealth(rawHealth)) {
                    continue
                }

                HotspotShareLogger.warn(
                    TAG,
                    "Zero-rating safety monitor detected unhealthy blackhole while VPN dropped (health=$rawHealth, iface=$expectedIface); attempting 1 immediate recovery"
                )

                val locked = sessionMutex.withLock {
                    if (!pausedForVpnDrop || intentionalUnbind) return@withLock true
                    tryRecoverBlackholeOnceLocked()
                }

                if (!locked) {
                    sessionMutex.withLock {
                        if (!pausedForVpnDrop) return@withLock
                        HotspotShareLogger.error(
                            TAG,
                            "EMERGENCY ZERO-RATING STOP: could not lock testtun blackhole while VPN is down (health=$rawHealth). Stopping Wi-Fi Hotspot immediately to prevent cellular data leak!"
                        )
                        pausedForVpnDrop = false
                        watchdogJob?.cancel()
                        watchdogJob = null
                        val svc = userService ?: runCatching { bindService(linkDeath = false) }.getOrNull()
                        runCatching { svc?.stopTethering() }
                        teardownSession(svc, stopHotspot = true, terminateDaemon = true)
                        PlatformNotifications.hotspotShareDropped(context)
                        _state.value = ShareState.Stopped(ShareFailure.VPN_LOST)
                    }
                    break
                }
            }
        }
    }

    private suspend fun tryRecoverBlackholeOnceLocked(): Boolean {
        return try {
            if (!isShizukuRunning()) {
                ShizukuWatchdog.tryReviveShizukuViaLocalAdbOrShell()
            }
            val svc = userService?.takeIf { it.asBinder().pingBinder() }
                ?: bindVerifiedService(linkDeath = true)

            runCatching { svc.setPreferTestNetworks(true) }
            val currentIface = runCatching { svc.interfaceName }.getOrNull()
            if (currentIface != null && sessionTunPfd != null) {
                activeInterfaceName = currentIface
                val verifiedExisting = runCatching { svc.verifyUpstream(BLACKHOLE_LOCK_TIMEOUT_MS) }.getOrDefault(false)
                val checkExisting = runCatching { svc.checkUpstreamHealth() }.getOrNull()
                if (verifiedExisting || (checkExisting != null && isSafeBlackholeHealth(checkExisting))) {
                    HotspotShareLogger.info(TAG, "Zero-rating blackhole re-locked onto existing $currentIface")
                    return true
                }
            }

            // Re-create blackhole testtun0 without starting HevTunnel (packets dropped in kernel)
            closeSessionTunPfd()
            val newPfd = svc.createTestNetwork(DEFAULT_DNS_SERVERS)
            val newIface = runCatching { svc.interfaceName }.getOrNull() ?: "testtun0"
            sessionTunPfd = newPfd
            activeInterfaceName = newIface
            svc.setPreferTestNetworks(true)
            val verifiedNew = runCatching { svc.verifyUpstream(BLACKHOLE_LOCK_TIMEOUT_MS) }.getOrDefault(false)
            val checkNew = runCatching { svc.checkUpstreamHealth() }.getOrNull()
            val ok = verifiedNew || (checkNew != null && isSafeBlackholeHealth(checkNew))
            if (ok) {
                HotspotShareLogger.info(TAG, "Zero-rating blackhole recreated and locked on $newIface")
            }
            ok
        } catch (t: Throwable) {
            HotspotShareLogger.error(TAG, "Blackhole recovery attempt failed: ${t.message}")
            false
        }
    }

    /**
     * Resumes `hev-socks5-tunnel` on the still-held `testtunN` when the VPN reconnects.
     */
    override suspend fun resumeAfterVpnDrop(): Boolean = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            val service = userService ?: return@withLock false
            val pfd = sessionTunPfd ?: return@withLock false
            val expectedIface = activeInterfaceName ?: return@withLock false

            if (!ensureLoopbackSocksReady()) return@withLock false

            val hevPfd = runCatching { pfd.dup() }.getOrNull() ?: return@withLock false
            val started = runCatching {
                hevTunnel.start(hevPfd, socksPort, HevTunnel.DEFAULT_TUN_MTU)
            }.getOrElse {
                runCatching { hevPfd.close() }
                false
            }
            if (!started) {
                HotspotShareLogger.error(TAG, "Failed to restart HevTunnel on VPN resume")
                stopLocked(stopHotspot = false)
                return@withLock false
            }

            pausedSafetyJob?.cancel()
            pausedSafetyJob = null
            pausedForVpnDrop = false
            HotspotShareLogger.info(TAG, "Resumed Shizuku hotspot share on $expectedIface after VPN reconnect")
            _state.value = ShareState.Active(ShareMethod.SHIZUKU)
            if (watchdogJob?.isActive != true) {
                startUpstreamWatchdog(service, expectedIface)
            }
            true
        }
    }

    override suspend fun stop(): Unit = stopInternal(stopHotspot = false)

    override suspend fun stopHotspotByUser(): Unit = stopInternal(stopHotspot = true)

    private suspend fun stopInternal(stopHotspot: Boolean): Unit = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            stopLocked(stopHotspot = stopHotspot)
        }
    }

    private fun stopLocked(stopHotspot: Boolean) {
        val current = _state.value
        if (current is ShareState.Off && !pausedForVpnDrop && sessionTunPfd == null && userService == null && !stopHotspot) {
            return
        }

        HotspotShareLogger.info(TAG, "Stopping ShizukuBackend (stopHotspot=$stopHotspot)")
        pausedSafetyJob?.cancel()
        pausedSafetyJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        teardownSession(userService, stopHotspot = stopHotspot, terminateDaemon = true)
        _state.value = ShareState.Off
    }

    private fun abortStartSession(
        service: IShareUserService,
        failure: ShareFailure,
        message: String,
    ) {
        teardownSession(service, stopHotspot = false, terminateDaemon = true)
        _state.value = ShareState.Error(failure, message)
    }

    private fun teardownSession(
        service: IShareUserService?,
        stopHotspot: Boolean,
        terminateDaemon: Boolean,
    ) {
        pausedSafetyJob?.cancel()
        pausedSafetyJob = null
        pausedForVpnDrop = false
        hevTunnel.stop()
        closeSessionTunPfd()
        if (terminateDaemon) {
            runCatching { ShizukuWatchdog.disableKeepAlive() }
        }
        if (service != null) {
            if (HotspotShareLogger.isLoggingEnabled()) {
                mirrorServiceLogs(service)
            }
            if (stopHotspot) {
                runCatching { service.stopTethering() }
            }
            safeCleanup(service)
            if (HotspotShareLogger.isLoggingEnabled()) {
                mirrorServiceLogs(service)
            }
            if (terminateDaemon) {
                runCatching { service.destroy() }
            }
            unbindService(terminateDaemon = terminateDaemon)
        } else if (boundArgs != null) {
            unbindService(terminateDaemon = terminateDaemon)
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
        HotspotShareLogger.info(TAG, "Loopback port $socksPort not open yet; reloading VPN core to bind share inbound")
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
                unbindService(terminateDaemon = false)
            }
        } catch (_: Throwable) {
            unbindService(terminateDaemon = false)
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

    /**
     * Handles unexpected binder death without EVER stopping the user's Wi-Fi Hotspot.
     * Enters a bounded recovery loop (5 attempts x 3s = ~15s) to allow the UID 2000 watchdog
     * or local ADB 127.0.0.1:5555 fallback to revive `shizuku_plus_server` / `shizuku_server`
     * while keeping `hevTunnel` and `sessionTunPfd` alive if `.daemon(true)` preserved `ShareUserService`.
     */
    private suspend fun handleUnexpectedServiceDeath() {
        if (intentionalUnbind) return
        sessionMutex.withLock {
            val current = _state.value
            if (intentionalUnbind || current is ShareState.Off || (current is ShareState.Stopped && !pausedForVpnDrop)) {
                return@withLock
            }
            HotspotShareLogger.warn(
                TAG,
                "ShareUserService binder died unexpectedly (state=$current, paused=$pausedForVpnDrop); starting bounded recovery (never stopping Hotspot)"
            )
            unlinkDeathRecipient()
            watchdogJob?.cancel()
            watchdogJob = null
            if (activeServiceInstance === userService) {
                activeServiceInstance = null
            }
            userService = null

            val previousIface = activeInterfaceName
            var reboundService: IShareUserService? = null

            for (attempt in 1..SERVICE_DEATH_RECOVERY_ATTEMPTS) {
                if (intentionalUnbind) return@withLock
                HotspotShareLogger.info(
                    TAG,
                    "Binder recovery attempt $attempt/$SERVICE_DEATH_RECOVERY_ATTEMPTS"
                )
                if (!isShizukuRunning()) {
                    runCatching { ShizukuWatchdog.tryReviveShizukuViaLocalAdbOrShell() }
                }
                if (isShizukuRunning()) {
                    val candidate = runCatching { bindVerifiedService(linkDeath = true) }.getOrNull()
                    if (candidate != null) {
                        reboundService = candidate
                        break
                    }
                }
                delay(SERVICE_DEATH_RECOVERY_DELAY_MS)
            }

            if (reboundService != null) {
                val restored = tryRestoreSessionOnReboundService(reboundService, previousIface)
                if (restored) {
                    HotspotShareLogger.info(
                        TAG,
                        "Seamless recovery succeeded after Shizuku binder death (iface=$activeInterfaceName)"
                    )
                    return@withLock
                }
            }

            // All recovery attempts failed: clean up local TUN/HevTunnel, NEVER call stopTethering()
            // unless we are paused for VPN drop and must prevent a zero-rating leak!
            HotspotShareLogger.error(
                TAG,
                "All $SERVICE_DEATH_RECOVERY_ATTEMPTS Shizuku recovery attempts failed; tearing down local tunnel without stopping Hotspot"
            )
            val wasPaused = pausedForVpnDrop
            pausedSafetyJob?.cancel()
            pausedSafetyJob = null
            pausedForVpnDrop = false
            hevTunnel.stop()
            closeSessionTunPfd()
            activeInterfaceName = null
            if (reboundService != null) {
                if (wasPaused) {
                    HotspotShareLogger.error(
                        TAG,
                        "VPN was down during unrecoverable binder failure; stopping tethering to prevent carrier leak"
                    )
                    runCatching { reboundService.stopTethering() }
                }
                safeCleanup(reboundService)
                unbindService(terminateDaemon = false)
            } else {
                unbindService(terminateDaemon = false)
            }

            PlatformNotifications.hotspotShareDropped(context)
            _state.value = ShareState.Error(
                ShareFailure.BACKEND_DIED,
                "Shizuku servisi beklenmedik şekilde kapandı ve yeniden bağlanılamadı (Hotspot açık bırakıldı)."
            )
        }
    }

    private suspend fun tryRestoreSessionOnReboundService(
        service: IShareUserService,
        previousIface: String?,
    ): Boolean {
        return try {
            val hotspotActive = runCatching { service.isWifiHotspotActive() }.getOrDefault(false)
            val existingServiceIface = runCatching { service.interfaceName }.getOrNull()

            // Case 1: ShareUserService (.daemon(true)) survived in UID 2000 with testtun0 intact!
            if (existingServiceIface != null && existingServiceIface == previousIface && sessionTunPfd != null) {
                runCatching { service.setPreferTestNetworks(true) }
                if (!hotspotActive) {
                    runCatching { service.startTethering() }
                }
                val verified = runCatching { service.verifyUpstream(INITIAL_UPSTREAM_SETTLE_MS) }.getOrDefault(false)
                if (verified) {
                    if (!pausedForVpnDrop) {
                        _state.value = ShareState.Active(ShareMethod.SHIZUKU)
                        startUpstreamWatchdog(service, existingServiceIface)
                    } else {
                        startPausedBlackholeSafetyMonitor()
                    }
                    return true
                }
            }

            // Case 2: ShareUserService restarted fresh; rebuild testtunN + HevTunnel seamlessly
            hevTunnel.stop()
            closeSessionTunPfd()

            val newTunPfd = service.createTestNetwork(DEFAULT_DNS_SERVERS)
            val newIface = runCatching { service.interfaceName }.getOrNull() ?: "testtun0"
            sessionTunPfd = newTunPfd
            activeInterfaceName = newIface

            if (!pausedForVpnDrop) {
                val hevPfd = newTunPfd.dup()
                val hevStarted = runCatching {
                    hevTunnel.start(hevPfd, socksPort, HevTunnel.DEFAULT_TUN_MTU)
                }.getOrElse {
                    runCatching { hevPfd.close() }
                    false
                }
                if (!hevStarted) return false
            }

            service.setPreferTestNetworks(true)
            if (!service.startTethering()) return false

            var verified = runCatching { service.verifyUpstream(INITIAL_UPSTREAM_SETTLE_MS) }.getOrDefault(false)
            if (!verified) {
                runCatching { service.setPreferTestNetworks(true) }
                runCatching { service.restartWifiTethering() }
                verified = runCatching { service.verifyUpstream(UPSTREAM_SETTLE_MS) }.getOrDefault(false)
            }
            if (!verified) return false

            if (!pausedForVpnDrop) {
                _state.value = ShareState.Active(ShareMethod.SHIZUKU)
                startUpstreamWatchdog(service, newIface)
            } else {
                startPausedBlackholeSafetyMonitor()
            }
            true
        } catch (t: Throwable) {
            HotspotShareLogger.warn(TAG, "tryRestoreSessionOnReboundService failed: ${t.message}")
            false
        }
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
        val resolved = if (version == ShareUserService.CONTRACT_VERSION) {
            first
        } else {
            Log.w(TAG, "Stale ShareUserService contract version ($version != ${ShareUserService.CONTRACT_VERSION}); restarting")
            HotspotShareLogger.warn(TAG, "Stale ShareUserService contract version ($version != ${ShareUserService.CONTRACT_VERSION}); restarting")
            unbindService(terminateDaemon = true)
            delay(300)
            bindService(linkDeath)
        }
        runCatching { resolved.setDiagnosticLoggingEnabled(DataStore.hotspotShareLogEnabled) }
        if (DataStore.hotspotShareLogEnabled) {
            mirrorServiceLogs(resolved)
        }
        return resolved
    }

    private suspend fun bindService(linkDeath: Boolean = true): IShareUserService {
        if (!intentionalUnbind) {
            userService?.let { existing ->
                if (runCatching { existing.asBinder().pingBinder() }.getOrDefault(false)) {
                    return existing
                }
                userService = null
            }
        }

        intentionalUnbind = !linkDeath
        linkDeathOnConnect = linkDeath

        val deferred = CompletableDeferred<IShareUserService>()
        connectDeferred = deferred

        val args = buildUserServiceArgs(context.packageName)

        boundArgs = args
        Shizuku.bindUserService(args, serviceConnection)

        val service = withTimeoutOrNull(10_000) {
            deferred.await()
        }
        if (service == null) {
            connectDeferred = null
            unbindService(terminateDaemon = false)
            throw IllegalStateException("Timeout binding Shizuku UserService")
        }

        return service
    }

    private fun unbindService(terminateDaemon: Boolean = true) {
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
            Shizuku.unbindUserService(args, serviceConnection, terminateDaemon)
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
        private const val PAUSED_SAFETY_INTERVAL_MS = 2_000L
        private const val BLACKHOLE_LOCK_TIMEOUT_MS = 2_500L
        private const val STARTUP_DOWNSTREAM_GRACE_MS = 10_000L
        private const val DOWNSTREAM_OFF_STRIKES = 3
        private const val SOCKS_WAIT_TIMEOUT_MS = 6_000L
        private const val SERVICE_DEATH_RECOVERY_ATTEMPTS = 5
        private const val SERVICE_DEATH_RECOVERY_DELAY_MS = 3_000L

        private val DEFAULT_DNS_SERVERS = listOf(
            "1.1.1.1",
            "8.8.8.8",
            "2606:4700:4700::1111",
            "2001:4860:4860::8888",
        )

        internal fun buildUserServiceArgs(packageName: String): Shizuku.UserServiceArgs {
            return Shizuku.UserServiceArgs(
                ComponentName(packageName, ShareUserService::class.java.name)
            )
                .daemon(true)
                .processNameSuffix("hotspot_share")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)
                .tag("share_service")
        }

        internal fun isSafeBlackholeHealth(rawHealth: String): Boolean {
            return rawHealth == "HEALTHY" || rawHealth == "DOWNSTREAM_OFF"
        }

        internal fun resolveWatchdogFailureState(hotspotStillUp: Boolean, verdict: String): ShareState {
            return if (!hotspotStillUp) {
                ShareState.Stopped(ShareFailure.TETHER_LOST)
            } else {
                ShareState.Error(ShareFailure.BACKEND_FAILED, verdict)
            }
        }

        @Volatile
        private var activeServiceInstance: IShareUserService? = null

        fun mirrorServiceLogs(service: IShareUserService? = activeServiceInstance): List<String> {
            val svc = service ?: return emptyList()
            val lines = runCatching { svc.diagnosticLogs ?: emptyList() }.getOrDefault(emptyList())
            if (lines.isNotEmpty()) {
                HotspotShareLogger.mirrorShellServiceLogs(lines)
            }
            return lines
        }

        fun fetchServiceLogs(): List<String> {
            return mirrorServiceLogs(activeServiceInstance)
        }

        fun clearServiceLogs() {
            val svc = activeServiceInstance ?: return
            runCatching { svc.clearDiagnosticLogs() }
        }

        fun setServiceLoggingEnabled(enabled: Boolean) {
            val svc = activeServiceInstance ?: return
            runCatching { svc.setDiagnosticLoggingEnabled(enabled) }
            if (enabled) {
                mirrorServiceLogs(svc)
            }
        }

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
