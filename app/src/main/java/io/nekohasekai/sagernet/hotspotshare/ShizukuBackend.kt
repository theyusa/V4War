package io.nekohasekai.sagernet.hotspotshare

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
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
import io.nekohasekai.sagernet.utils.LanSharingNetwork
import io.nekohasekai.sagernet.utils.PlatformNotifications
import io.nekohasekai.sagernet.utils.TetherKind
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

    init {
        lastAppContext = context.applicationContext ?: context
    }

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
    private var sessionLifeBinder: Binder? = null

    @Volatile
    private var pausedForVpnDrop = false
    @Volatile
    private var intentionalUnbind = false
    @Volatile
    private var linkDeathOnConnect = true
    @Volatile
    private var lastDaemonTerminateAtMs: Long = 0L

    @Volatile
    var activeInterfaceName: String? = null
        private set

    private val deathRecipient = IBinder.DeathRecipient {
        if (intentionalUnbind || _state.value is ShareState.Starting) return@DeathRecipient
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
            val pending = connectDeferred
            connectDeferred = null
            if (pending != null && !pending.isCompleted) {
                pending.completeExceptionally(IllegalStateException("ShareUserService disconnected during bind"))
            }
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
            val lifeBinder = Binder()
            sessionLifeBinder = lifeBinder
            service.createTestNetworkWithBinder(DEFAULT_DNS_SERVERS, lifeBinder)
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

        // 5. Arm preferTestNetworks AFTER testtunN and HevTunnel are up, then ensure tethering is active.
        var currentService = service
        var currentIface = ifaceName
        try {
            currentService.setPreferTestNetworks(true)

            val tetherStarted = currentService.startTethering()
            if (!tetherStarted) {
                HotspotShareLogger.error(TAG, "Wi-Fi hotspot failed to start via TetheringManager / WifiManager")
                abortStartSession(
                    currentService,
                    ShareFailure.BACKEND_FAILED,
                    "Wi-Fi hotspot failed to start via TetheringManager / WifiManager"
                )
                return@withContext
            }

            // 6. Verify via dumpsys tethering that testtunN is selected as sole upstream.
            val initialSettleMs = resolveInitialUpstreamSettleMs(hotspotWasAlreadyOpen)
            var verified = runCatching { currentService.verifyUpstream(initialSettleMs) }.getOrDefault(false)
            if (!verified && !hotspotWasAlreadyOpen) {
                verified = runCatching { currentService.verifyUpstream(UPSTREAM_SETTLE_MS) }.getOrDefault(false)
            }
            if (!verified) {
                val preRestartMask = runCatching { sanitizeActiveTetherMask(currentService.activeTetherMask) }
                    .getOrDefault(TETHER_MASK_WIFI)
                    .let { if (it != 0) it else TETHER_MASK_WIFI }
                val preRestartNcm = if (LanSharingNetwork.USB_TETHER_ENABLED) {
                    runCatching { LanSharingNetwork.hasActiveNcmTetheredIface(context) }.getOrDefault(false)
                } else {
                    false
                }
                HotspotShareLogger.warn(
                    TAG,
                    "Initial upstream attach did not settle on $currentIface (mask=$preRestartMask); restarting active tethering downstream",
                )
                runCatching { currentService.setPreferTestNetworks(true) }

                val restartCall = runCatching { currentService.restartWifiTethering() }
                var restarted = restartCall.getOrDefault(false)
                val binderLost = restartCall.exceptionOrNull() is RemoteException ||
                    !runCatching { currentService.asBinder().pingBinder() }.getOrDefault(false)

                if (binderLost) {
                    HotspotShareLogger.warn(
                        TAG,
                        "ShareUserService binder lost during downstream restart (mask=$preRestartMask); waiting for recovery to finish restart",
                    )
                    recoverServiceDuringStart(currentIface)?.let { (recoveredSvc, recoveredIface) ->
                        currentService = recoveredSvc
                        currentIface = recoveredIface
                        runCatching { currentService.setPreferTestNetworks(true) }
                        val ensureCall = runCatching {
                            currentService.ensureTetherMaskStarted(preRestartMask, preRestartNcm)
                        }
                        if (ensureCall.exceptionOrNull() is RemoteException ||
                            !runCatching { currentService.asBinder().pingBinder() }.getOrDefault(false)
                        ) {
                            recoverServiceDuringStart(currentIface)?.let { (reboundSvc, reboundIface) ->
                                currentService = reboundSvc
                                currentIface = reboundIface
                                runCatching { currentService.setPreferTestNetworks(true) }
                                val currentMask = runCatching { currentService.activeTetherMask }.getOrDefault(0)
                                restarted = if ((currentMask and preRestartMask) == preRestartMask) {
                                    true
                                } else {
                                    runCatching {
                                        currentService.ensureTetherMaskStarted(preRestartMask, preRestartNcm)
                                    }.getOrDefault(false) ||
                                        runCatching { currentService.activeTetherMask != 0 }.getOrDefault(false)
                                }
                            }
                        } else {
                            restarted = ensureCall.getOrDefault(false) ||
                                runCatching { currentService.activeTetherMask != 0 }.getOrDefault(false)
                        }
                    }
                }

                val anyChannelUp = restarted ||
                    runCatching { currentService.activeTetherMask != 0 }.getOrDefault(false) ||
                    runCatching { currentService.isWifiHotspotActive() }.getOrDefault(false)
                if (!anyChannelUp) {
                    abortStartSession(
                        currentService,
                        ShareFailure.BACKEND_FAILED,
                        "Tethering failed to restart onto test-network upstream ($currentIface)"
                    )
                    return@withContext
                }

                val verifyCall = runCatching { currentService.verifyUpstream(UPSTREAM_SETTLE_MS) }
                if (verifyCall.exceptionOrNull() is RemoteException) {
                    recoverServiceDuringStart(currentIface)?.let { (reboundSvc, reboundIface) ->
                        currentService = reboundSvc
                        currentIface = reboundIface
                        runCatching { currentService.setPreferTestNetworks(true) }
                        verified = runCatching { currentService.verifyUpstream(UPSTREAM_SETTLE_MS) }.getOrDefault(false)
                    }
                } else {
                    verified = verifyCall.getOrDefault(false)
                }
            }
            if (!verified) {
                HotspotShareLogger.error(TAG, "Upstream failed to attach to $currentIface")
                abortStartSession(
                    currentService,
                    ShareFailure.BACKEND_FAILED,
                    "Upstream failed to attach to $currentIface (another upstream remained selected)"
                )
                return@withContext
            }
        } catch (t: Throwable) {
            HotspotShareLogger.error(TAG, "Exception starting tethering: ${t.message}")
            abortStartSession(currentService, ShareFailure.BACKEND_FAILED, "Exception starting tethering: ${t.message}")
            return@withContext
        }

        pausedSafetyJob?.cancel()
        pausedSafetyJob = null
        pausedForVpnDrop = false
        HotspotShareLogger.info(TAG, "Shizuku hotspot share ACTIVE on $currentIface")
        mirrorServiceLogs(currentService)
        _state.value = ShareState.Active(ShareMethod.SHIZUKU)
        startUpstreamWatchdog(currentService, currentIface)
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
            val lifeBinder = Binder()
            sessionLifeBinder = lifeBinder
            val newPfd = svc.createTestNetworkWithBinder(DEFAULT_DNS_SERVERS, lifeBinder)
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
        sessionLifeBinder = null
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

    private suspend fun tryBindRecoveredCandidate(
        previousIface: String?,
        requireExactAdopt: Boolean,
    ): Pair<IShareUserService, String?>? {
        if (!isShizukuRunning()) {
            runCatching { ShizukuWatchdog.tryReviveShizukuViaLocalAdbOrShell() }
        }
        if (!isShizukuRunning()) return null

        val candidate = runCatching { bindVerifiedService(linkDeath = true) }.getOrNull() ?: return null
        val existingSvcIface = runCatching { candidate.interfaceName }.getOrNull()
        val adopted = if (existingSvcIface == null && previousIface != null && sessionTunPfd != null) {
            runCatching { candidate.adoptExistingTestNetwork(previousIface) }.getOrDefault(false)
        } else {
            existingSvcIface != null && existingSvcIface == previousIface
        }
        if (adopted && previousIface != null && sessionTunPfd != null) {
            activeInterfaceName = previousIface
            return candidate to previousIface
        }
        return if (requireExactAdopt) candidate to null else candidate to (existingSvcIface ?: previousIface ?: "testtun0")
    }

    private suspend fun recoverServiceDuringStart(
        previousIface: String?,
    ): Pair<IShareUserService, String>? {
        unlinkDeathRecipient()
        if (activeServiceInstance === userService) {
            activeServiceInstance = null
        }
        userService = null

        for (attempt in 1..START_RECOVERY_ATTEMPTS) {
            if (intentionalUnbind || _state.value !is ShareState.Starting) return null
            delay(START_RECOVERY_DELAY_MS)
            if (intentionalUnbind || _state.value !is ShareState.Starting) return null

            HotspotShareLogger.info(
                TAG,
                "Startup binder recovery attempt $attempt/$START_RECOVERY_ATTEMPTS",
            )
            val bound = tryBindRecoveredCandidate(previousIface, requireExactAdopt = true) ?: continue
            val candidate = bound.first
            val adoptedIface = bound.second
            if (adoptedIface != null) {
                return candidate to adoptedIface
            }

            // Re-establish testtunN + HevTunnel cleanly if old testtun vanished during binder death
            hevTunnel.stop()
            closeSessionTunPfd()
            val rebuilt = runCatching {
                val lifeBinder = Binder()
                sessionLifeBinder = lifeBinder
                val newPfd = candidate.createTestNetworkWithBinder(DEFAULT_DNS_SERVERS, lifeBinder)
                val newIface = runCatching { candidate.interfaceName }.getOrNull() ?: "testtun0"
                sessionTunPfd = newPfd
                activeInterfaceName = newIface
                val hevPfd = newPfd.dup()
                if (!hevTunnel.start(hevPfd, socksPort, HevTunnel.DEFAULT_TUN_MTU)) {
                    runCatching { hevPfd.close() }
                    return@runCatching null
                }
                candidate to newIface
            }.getOrNull()
            if (rebuilt != null) {
                return rebuilt
            }
        }
        return null
    }

    /**
     * Handles unexpected binder death without EVER stopping the user's Wi-Fi Hotspot.
     * Enters a bounded recovery loop (5 attempts x 3s = ~15s) to allow the UID 2000 watchdog
     * or local ADB 127.0.0.1:5555 fallback to revive `shizuku_plus_server` / `shizuku_server`
     * while keeping `hevTunnel` and `sessionTunPfd` alive if `.daemon(true)` preserved `ShareUserService`.
     */
    private suspend fun handleUnexpectedServiceDeath() {
        if (intentionalUnbind) return
        if (_state.value is ShareState.Starting) return
        sessionMutex.withLock {
            val current = _state.value
            if (intentionalUnbind ||
                current is ShareState.Off ||
                current is ShareState.Starting ||
                (current is ShareState.Stopped && !pausedForVpnDrop)
            ) {
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
            val adoptedExisting = if (existingServiceIface == null && previousIface != null && sessionTunPfd != null) {
                runCatching { service.adoptExistingTestNetwork(previousIface) }.getOrDefault(false)
            } else {
                false
            }
            val liveIface = when {
                existingServiceIface != null && existingServiceIface == previousIface -> existingServiceIface
                adoptedExisting -> previousIface
                else -> null
            }

            // Case 1: ShareUserService survived OR we adopted the app-binder-owned testtunN intact!
            if (liveIface != null && sessionTunPfd != null) {
                runCatching { service.setPreferTestNetworks(true) }
                if (!hotspotActive) {
                    runCatching { service.startTethering() }
                }
                val verified = runCatching { service.verifyUpstream(INITIAL_UPSTREAM_SETTLE_MS) }.getOrDefault(false)
                if (verified) {
                    if (!pausedForVpnDrop) {
                        _state.value = ShareState.Active(ShareMethod.SHIZUKU)
                        startUpstreamWatchdog(service, liveIface)
                    } else {
                        startPausedBlackholeSafetyMonitor()
                    }
                    return true
                }
            }

            // Case 2: ShareUserService restarted fresh and old testtunN is gone; rebuild testtunN + HevTunnel seamlessly
            hevTunnel.stop()
            closeSessionTunPfd()

            val lifeBinder = Binder()
            sessionLifeBinder = lifeBinder
            val newTunPfd = service.createTestNetworkWithBinder(DEFAULT_DNS_SERVERS, lifeBinder)
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

            val preRestartMask = runCatching { sanitizeActiveTetherMask(service.activeTetherMask) }
                .getOrDefault(TETHER_MASK_WIFI)
                .let { if (it != 0) it else TETHER_MASK_WIFI }
            val preRestartNcm = if (LanSharingNetwork.USB_TETHER_ENABLED) {
                runCatching { LanSharingNetwork.hasActiveNcmTetheredIface(context) }.getOrDefault(false)
            } else {
                false
            }
            service.setPreferTestNetworks(true)
            if (!service.startTethering()) return false

            var activeSvc = service
            var activeIface = newIface
            var verified = runCatching {
                activeSvc.verifyUpstream(resolveInitialUpstreamSettleMs(hotspotActive))
            }.getOrDefault(false)
            if (!verified) {
                runCatching { activeSvc.setPreferTestNetworks(true) }
                val restartCall = runCatching { activeSvc.restartWifiTethering() }
                val restartLostBinder = restartCall.exceptionOrNull() is RemoteException ||
                    !runCatching { activeSvc.asBinder().pingBinder() }.getOrDefault(false)
                if (restartLostBinder) {
                    val rebound = rebindRecoveredService(activeIface) ?: return false
                    activeSvc = rebound.first
                    activeIface = rebound.second
                    runCatching { activeSvc.setPreferTestNetworks(true) }
                    runCatching { activeSvc.ensureTetherMaskStarted(preRestartMask, preRestartNcm) }
                }
                verified = runCatching { activeSvc.verifyUpstream(UPSTREAM_SETTLE_MS) }.getOrDefault(false)
            }
            if (!verified) return false

            if (!pausedForVpnDrop) {
                _state.value = ShareState.Active(ShareMethod.SHIZUKU)
                startUpstreamWatchdog(activeSvc, activeIface)
            } else {
                startPausedBlackholeSafetyMonitor()
            }
            true
        } catch (t: Throwable) {
            HotspotShareLogger.warn(TAG, "tryRestoreSessionOnReboundService failed: ${t.message}")
            false
        }
    }

    private suspend fun rebindRecoveredService(previousIface: String?): Pair<IShareUserService, String>? {
        unlinkDeathRecipient()
        if (activeServiceInstance === userService) {
            activeServiceInstance = null
        }
        userService = null
        for (attempt in 1..SERVICE_DEATH_RECOVERY_ATTEMPTS) {
            if (intentionalUnbind) return null
            delay(START_RECOVERY_DELAY_MS)
            if (intentionalUnbind) return null
            val bound = tryBindRecoveredCandidate(previousIface, requireExactAdopt = false) ?: continue
            val resolvedIface = bound.second ?: previousIface ?: "testtun0"
            return bound.first to resolvedIface
        }
        return null
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

        // Wait briefly if a prior daemon termination was just requested so Shizuku's
        // UserService manager finishes tearing down the old process before rebinding.
        val terminatedAt = lastDaemonTerminateAtMs
        if (terminatedAt > 0L) {
            val remainingMs = 350L - (SystemClock.elapsedRealtime() - terminatedAt)
            if (remainingMs > 0L) {
                delay(remainingMs)
            }
        }

        val args = buildUserServiceArgs(context.packageName)
        repeat(2) { attempt ->
            intentionalUnbind = !linkDeath
            linkDeathOnConnect = linkDeath

            val deferred = CompletableDeferred<IShareUserService>()
            connectDeferred = deferred
            boundArgs = args
            Shizuku.bindUserService(args, serviceConnection)

            val service = runCatching {
                withTimeoutOrNull(6_000L) {
                    deferred.await()
                }
            }.getOrNull()
            if (service != null) {
                return service
            }

            connectDeferred = null
            unbindService(terminateDaemon = false)
            if (attempt == 0) {
                delay(300L)
            }
        }
        throw IllegalStateException("Timeout binding Shizuku UserService")
    }

    private fun unbindService(terminateDaemon: Boolean = true) {
        intentionalUnbind = true
        if (terminateDaemon) {
            lastDaemonTerminateAtMs = SystemClock.elapsedRealtime()
        }
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
        private const val ALREADY_OPEN_UPSTREAM_SETTLE_MS = 650L
        private const val UPSTREAM_SETTLE_MS = 8_000L

        internal fun resolveInitialUpstreamSettleMs(hotspotWasAlreadyOpen: Boolean): Long =
            if (hotspotWasAlreadyOpen) ALREADY_OPEN_UPSTREAM_SETTLE_MS else INITIAL_UPSTREAM_SETTLE_MS
        private const val WATCHDOG_INTERVAL_MS = 5_000L
        private const val PAUSED_SAFETY_INTERVAL_MS = 2_000L
        private const val BLACKHOLE_LOCK_TIMEOUT_MS = 2_500L
        private const val STARTUP_DOWNSTREAM_GRACE_MS = 10_000L
        private const val DOWNSTREAM_OFF_STRIKES = 3
        private const val SOCKS_WAIT_TIMEOUT_MS = 6_000L
        private const val SERVICE_DEATH_RECOVERY_ATTEMPTS = 5
        private const val SERVICE_DEATH_RECOVERY_DELAY_MS = 3_000L
        private const val START_RECOVERY_ATTEMPTS = 6
        private const val START_RECOVERY_DELAY_MS = 1_500L
        private const val USB_REENUMERATION_WAIT_MS = 4_000L
        private const val USB_REENUMERATION_POLL_MS = 250L
        private const val USB_RETRY_REVIVE_WAIT_MS = 5_000L
        private const val TEMP_MASK_REFRESH_INTERVAL_MS = 10_000L
        private const val LIVE_MASK_CACHE_TTL_MS = 1_500L

        const val TETHERING_WIFI = ShareUserService.TETHERING_WIFI
        const val TETHERING_USB = ShareUserService.TETHERING_USB
        const val TETHERING_NCM = ShareUserService.TETHERING_NCM

        const val TETHER_MASK_WIFI = ShareUserService.TETHER_MASK_WIFI
        const val TETHER_MASK_USB = ShareUserService.TETHER_MASK_USB

        internal fun sanitizeActiveTetherMask(
            rawMask: Int,
            usbEnabled: Boolean = LanSharingNetwork.USB_TETHER_ENABLED,
        ): Int {
            var mask = rawMask and TETHER_MASK_WIFI
            if (usbEnabled) {
                mask = mask or (rawMask and TETHER_MASK_USB)
            }
            return mask
        }

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

        @Volatile
        private var lastAppContext: Context? = null

        @Volatile
        private var lastKnownServiceTetherMask: Int = 0

        @Volatile
        private var lastTempMaskRefreshAtMs: Long = 0L

        private val tempBindMutex = Mutex()

        private fun expectedMaskBitForType(type: Int): Int = when (type) {
            TETHERING_WIFI -> TETHER_MASK_WIFI
            TETHERING_USB, TETHERING_NCM -> if (LanSharingNetwork.USB_TETHER_ENABLED) TETHER_MASK_USB else 0
            else -> 0
        }

        /**
         * Returns active tethered downstream interface names from the bound [IShareUserService]
         * (backed by `TetheringEventCallback.cachedTetheredIfaces`), or null when not bound.
         */
        fun getBoundTetheredIfaces(): List<String>? {
            val svc = activeServiceInstance ?: return null
            if (!runCatching { svc.asBinder().pingBinder() }.getOrDefault(false)) return null
            return runCatching { svc.tetheredIfaces }.getOrNull()
        }

        /**
         * Returns the active tethering channel bitmask ([TETHER_MASK_WIFI], and [TETHER_MASK_USB] when enabled).
         * Uses the privileged Shizuku service when bound, otherwise falls back to [LanSharingNetwork.getActiveTetherKinds].
         */
        @JvmOverloads
        fun getActiveTetherMask(context: Context? = null): Int {
            val svc = activeServiceInstance
            if (svc != null && runCatching { svc.asBinder().pingBinder() }.getOrDefault(false)) {
                val mask = runCatching { sanitizeActiveTetherMask(svc.activeTetherMask) }.getOrNull()
                if (mask != null) {
                    lastKnownServiceTetherMask = mask
                    lastTempMaskRefreshAtMs = System.currentTimeMillis()
                    return mask
                }
            }
            val ctx = context ?: lastAppContext ?: runCatching { SagerNet.application }.getOrNull()
                ?: return 0
            val kinds = runCatching { LanSharingNetwork.getActiveTetherKinds(ctx) }.getOrDefault(emptySet())
            var mask = 0
            if (TetherKind.WIFI in kinds) mask = mask or TETHER_MASK_WIFI
            if (LanSharingNetwork.USB_TETHER_ENABLED && TetherKind.USB in kinds) {
                mask = mask or TETHER_MASK_USB
            }
            return mask
        }

        /**
         * Refreshes the active tether bitmask from [IShareUserService], temporarily binding
         * when no Share session is running (throttled unless [force] is true).
         */
        suspend fun refreshActiveTetherMask(
            context: Context? = null,
            force: Boolean = false,
        ): Int = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val live = activeServiceInstance?.takeIf { svc ->
                runCatching { svc.asBinder().pingBinder() }.getOrDefault(false)
            }
            if (live != null) {
                if (!force && now - lastTempMaskRefreshAtMs in 0L until LIVE_MASK_CACHE_TTL_MS) {
                    return@withContext sanitizeActiveTetherMask(lastKnownServiceTetherMask)
                }
                val mask = runCatching { sanitizeActiveTetherMask(live.activeTetherMask) }.getOrNull()
                if (mask != null) {
                    lastKnownServiceTetherMask = mask
                    lastTempMaskRefreshAtMs = System.currentTimeMillis()
                    return@withContext mask
                }
            }

            val ctx = context ?: lastAppContext ?: runCatching { SagerNet.application }.getOrNull()
            if (ctx != null &&
                (force || now - lastTempMaskRefreshAtMs >= TEMP_MASK_REFRESH_INTERVAL_MS) &&
                isShizukuRunning() &&
                isShizukuGranted(ctx)
            ) {
                val refreshed = tempBindMutex.withLock {
                    val recheck = activeServiceInstance?.takeIf { svc ->
                        runCatching { svc.asBinder().pingBinder() }.getOrDefault(false)
                    }
                    if (recheck != null) {
                        val mask = runCatching { sanitizeActiveTetherMask(recheck.activeTetherMask) }.getOrNull()
                        if (mask != null) {
                            lastKnownServiceTetherMask = mask
                            lastTempMaskRefreshAtMs = System.currentTimeMillis()
                        }
                        return@withLock mask
                    }
                    val args = buildUserServiceArgs(ctx.packageName)
                    var tempConn: ServiceConnection? = null
                    try {
                        val (svc, conn) = bindTempVerifiedService(args)
                        tempConn = conn
                        val mask = runCatching { sanitizeActiveTetherMask(svc.activeTetherMask) }.getOrNull()
                        if (mask != null) {
                            lastKnownServiceTetherMask = mask
                            lastTempMaskRefreshAtMs = System.currentTimeMillis()
                        }
                        mask
                    } catch (_: Throwable) {
                        null
                    } finally {
                        tempConn?.let { conn ->
                            runCatching { Shizuku.unbindUserService(args, conn, false) }
                        }
                    }
                }
                if (refreshed != null) {
                    return@withContext refreshed
                }
            }

            getActiveTetherMask(ctx)
        }

        /**
         * Cleans up any orphaned test network / preferTestNetworks state left behind
         * by a killed [TetherWatchService] process when no live session is running.
         */
        suspend fun cleanupOrphanedServiceState(context: Context): Unit = withContext(Dispatchers.IO) {
            val appCtx = context.applicationContext ?: context
            if (TetherWatchService.isRunningOrStarting()) return@withContext
            if (activeServiceInstance != null) return@withContext
            if (!isShizukuRunning() || !isShizukuGranted(appCtx)) return@withContext
            tempBindMutex.withLock {
                if (TetherWatchService.isRunningOrStarting() || activeServiceInstance != null) return@withLock
                val args = buildUserServiceArgs(appCtx.packageName)
                var tempConn: ServiceConnection? = null
                try {
                    val (svc, conn) = bindTempVerifiedService(args)
                    tempConn = conn
                    val activeTestIface = runCatching { svc.interfaceName }.getOrNull()
                    if (!activeTestIface.isNullOrBlank()) {
                        HotspotShareLogger.info(TAG, "Cleaning up orphaned test network ($activeTestIface) after dead service")
                    }
                    runCatching { svc.cleanupDirtyState() }
                    mirrorServiceLogs(svc)
                } catch (_: Throwable) {
                } finally {
                    tempConn?.let { conn ->
                        runCatching { Shizuku.unbindUserService(args, conn, false) }
                    }
                }
            }
        }

        /**
         * Enables or disables a specific tethering channel ([TETHERING_WIFI], or [TETHERING_USB] /
         * [TETHERING_NCM] when enabled) via Shizuku [IShareUserService], binding
         * temporarily if no session is currently running.
         */
        suspend fun setTetherTypeEnabled(
            type: Int,
            enabled: Boolean,
            context: Context? = null,
        ): Result<Unit> = withContext(Dispatchers.IO) {
            if (type == 2) {
                return@withContext Result.failure(UnsupportedOperationException("Bluetooth tethering is not supported in Shizuku mode"))
            }
            if ((type == TETHERING_USB || type == TETHERING_NCM) && !LanSharingNetwork.USB_TETHER_ENABLED) {
                return@withContext Result.failure(UnsupportedOperationException("USB tethering is currently disabled"))
            }
            HotspotShareLogger.info(TAG, "setTetherTypeEnabled(type=$type, enabled=$enabled)")
            val ctx = context ?: lastAppContext ?: runCatching { SagerNet.application }.getOrNull()
            val liveService = activeServiceInstance?.takeIf { svc ->
                runCatching {
                    svc.asBinder().pingBinder() &&
                        svc.contractVersion == ShareUserService.CONTRACT_VERSION
                }.getOrDefault(false)
            }
            if (liveService != null) {
                return@withContext executeTetherTypeCommand(liveService, type, enabled, ctx)
            }

            if (ctx == null) {
                return@withContext Result.failure(IllegalStateException("Application context unavailable"))
            }

            if (!isShizukuRunning()) {
                ShizukuWatchdog.tryReviveShizukuViaLocalAdbOrShell()
            }
            if (!isShizukuRunning()) {
                HotspotShareLogger.warn(TAG, "setTetherTypeEnabled aborted: Shizuku is not running")
                return@withContext Result.failure(IllegalStateException("Shizuku is not running"))
            }
            if (!isShizukuGranted(ctx)) {
                HotspotShareLogger.warn(TAG, "setTetherTypeEnabled aborted: Shizuku permission denied")
                return@withContext Result.failure(SecurityException("Shizuku permission denied"))
            }

            tempBindMutex.withLock {
                val rechecked = activeServiceInstance?.takeIf { svc ->
                    runCatching {
                        svc.asBinder().pingBinder() &&
                            svc.contractVersion == ShareUserService.CONTRACT_VERSION
                    }.getOrDefault(false)
                }
                if (rechecked != null) {
                    return@withLock executeTetherTypeCommand(rechecked, type, enabled, ctx)
                }

                val args = buildUserServiceArgs(ctx.packageName)
                var tempConn: ServiceConnection? = null
                val firstResult = try {
                    val (svc, conn) = bindTempVerifiedService(args)
                    tempConn = conn
                    executeTetherTypeCommand(svc, type, enabled, ctx)
                } catch (t: Throwable) {
                    HotspotShareLogger.error(TAG, "setTetherTypeEnabled($type, $enabled) failed: ${t.message}")
                    Result.failure(t)
                } finally {
                    tempConn?.let { conn ->
                        runCatching { Shizuku.unbindUserService(args, conn, false) }
                    }
                    tempConn = null
                }

                // If enabling USB tethering died during resetStuckUsbTethering (after clearing stuck rndis0),
                // wait briefly for Shizuku revival and retry startTetheringType(type) once cleanly.
                val shouldRetryAfterReset = firstResult.isFailure &&
                    enabled &&
                    (type == TETHERING_USB || type == TETHERING_NCM) &&
                    firstResult.exceptionOrNull()?.cause is RemoteException
                if (!shouldRetryAfterReset) {
                    return@withLock firstResult
                }

                HotspotShareLogger.info(
                    TAG,
                    "USB channel binder reset before activation; waiting for Shizuku to retry startTetheringType($type)",
                )
                val reviveDeadline = System.currentTimeMillis() + USB_RETRY_REVIVE_WAIT_MS
                while (System.currentTimeMillis() < reviveDeadline && !isShizukuRunning()) {
                    ShizukuWatchdog.tryReviveShizukuViaLocalAdbOrShell()
                    if (!isShizukuRunning()) {
                        delay(400L)
                    }
                }
                if (!isShizukuRunning() || !isShizukuGranted(ctx)) {
                    return@withLock firstResult
                }

                try {
                    val (retrySvc, retryConn) = bindTempVerifiedService(args)
                    tempConn = retryConn
                    executeTetherTypeCommand(retrySvc, type, enabled, ctx)
                } catch (t: Throwable) {
                    HotspotShareLogger.error(TAG, "Retry setTetherTypeEnabled($type, $enabled) failed: ${t.message}")
                    Result.failure(t)
                } finally {
                    tempConn?.let { conn ->
                        runCatching { Shizuku.unbindUserService(args, conn, false) }
                    }
                }
            }
        }

        private suspend fun executeTetherTypeCommand(
            service: IShareUserService,
            type: Int,
            enabled: Boolean,
            context: Context?,
        ): Result<Unit> {
            val isUsbChannel = type == TETHERING_USB || type == TETHERING_NCM
            return try {
                val expectedBit = expectedMaskBitForType(type)
                if (enabled) {
                    val started = service.startTetheringType(type)
                    val postMask = runCatching { sanitizeActiveTetherMask(service.activeTetherMask) }.getOrDefault(0)
                    lastKnownServiceTetherMask = postMask
                    lastTempMaskRefreshAtMs = System.currentTimeMillis()
                    mirrorServiceLogs(service)
                    val active = started && (expectedBit == 0 || (postMask and expectedBit) != 0)
                    if (active) {
                        HotspotShareLogger.info(TAG, "startTetheringType($type) succeeded (mask=$postMask)")
                        Result.success(Unit)
                    } else {
                        HotspotShareLogger.warn(
                            TAG,
                            "startTetheringType($type) did not activate channel (started=$started, mask=$postMask)",
                        )
                        Result.failure(
                            IllegalStateException("Channel $type did not become active (started=$started, mask=$postMask)"),
                        )
                    }
                } else {
                    service.stopTetheringType(type)
                    val postMask = runCatching { sanitizeActiveTetherMask(service.activeTetherMask) }.getOrDefault(0)
                    lastKnownServiceTetherMask = postMask
                    lastTempMaskRefreshAtMs = System.currentTimeMillis()
                    mirrorServiceLogs(service)
                    HotspotShareLogger.info(TAG, "stopTetheringType($type) completed (mask=$postMask)")
                    Result.success(Unit)
                }
            } catch (t: RemoteException) {
                val ctx = context ?: lastAppContext ?: runCatching { SagerNet.application }.getOrNull()
                if (isUsbChannel && ctx != null && awaitUsbStateAfterBinderReset(ctx, targetEnabled = enabled)) {
                    HotspotShareLogger.info(
                        TAG,
                        "USB tethering transition (enabled=$enabled) completed across USB/ADB re-enumeration",
                    )
                    return Result.success(Unit)
                }
                val detail = t.message ?: "${t.javaClass.simpleName} during tether type=$type"
                HotspotShareLogger.error(TAG, "executeTetherTypeCommand($type, $enabled) threw: $detail")
                Result.failure(IllegalStateException(detail, t))
            } catch (t: Throwable) {
                runCatching { mirrorServiceLogs(service) }
                val detail = t.message ?: t.javaClass.simpleName
                HotspotShareLogger.error(TAG, "executeTetherTypeCommand($type, $enabled) threw: $detail")
                Result.failure(t)
            }
        }

        private suspend fun awaitUsbStateAfterBinderReset(
            ctx: Context,
            targetEnabled: Boolean,
        ): Boolean {
            val deadline = System.currentTimeMillis() + USB_REENUMERATION_WAIT_MS
            var firstConfirmedAt = 0L
            while (System.currentTimeMillis() < deadline) {
                val svcMask = activeServiceInstance?.takeIf { svc ->
                    runCatching { svc.asBinder().pingBinder() }.getOrDefault(false)
                }?.let { svc ->
                    runCatching { svc.activeTetherMask }.getOrNull()
                }
                if (svcMask != null) {
                    lastKnownServiceTetherMask = svcMask
                }
                val osConfirmedUsb = runCatching {
                    LanSharingNetwork.isUsbTetheringConfirmedInOs(ctx)
                }.getOrDefault(false)
                val usbActive = if (targetEnabled) {
                    osConfirmedUsb || ((svcMask ?: 0) and TETHER_MASK_USB) != 0
                } else {
                    val anyKindUsb = runCatching {
                        TetherKind.USB in LanSharingNetwork.getActiveTetherKinds(ctx)
                    }.getOrDefault(false)
                    anyKindUsb || ((svcMask ?: 0) and TETHER_MASK_USB) != 0
                }
                if (usbActive == targetEnabled) {
                    if (!targetEnabled) {
                        if (svcMask == null) {
                            lastKnownServiceTetherMask = lastKnownServiceTetherMask and TETHER_MASK_USB.inv()
                        }
                        return true
                    }
                    val now = System.currentTimeMillis()
                    if (firstConfirmedAt == 0L) {
                        firstConfirmedAt = now
                    } else if (now - firstConfirmedAt >= 350L) {
                        if (svcMask == null) {
                            lastKnownServiceTetherMask = lastKnownServiceTetherMask or TETHER_MASK_USB
                        }
                        return true
                    }
                } else {
                    firstConfirmedAt = 0L
                }
                delay(USB_REENUMERATION_POLL_MS)
            }
            return false
        }

        private suspend fun bindTempVerifiedService(
            args: Shizuku.UserServiceArgs,
        ): Pair<IShareUserService, ServiceConnection> {
            val (first, firstConn) = bindTempOnce(args)
            val version = runCatching { first.contractVersion }.getOrDefault(-1)
            if (version == ShareUserService.CONTRACT_VERSION) {
                runCatching { first.setDiagnosticLoggingEnabled(DataStore.hotspotShareLogEnabled) }
                return first to firstConn
            }
            HotspotShareLogger.warn(
                TAG,
                "Stale temporary ShareUserService contract ($version != ${ShareUserService.CONTRACT_VERSION}); restarting",
            )
            runCatching { Shizuku.unbindUserService(args, firstConn, true) }
            delay(300)
            val (second, secondConn) = bindTempOnce(args)
            runCatching { second.setDiagnosticLoggingEnabled(DataStore.hotspotShareLogEnabled) }
            return second to secondConn
        }

        private suspend fun bindTempOnce(
            args: Shizuku.UserServiceArgs,
        ): Pair<IShareUserService, ServiceConnection> {
            val deferred = CompletableDeferred<IShareUserService>()
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val stub = IShareUserService.Stub.asInterface(service)
                    if (stub != null) {
                        deferred.complete(stub)
                    } else {
                        deferred.completeExceptionally(IllegalStateException("Null IShareUserService binder"))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(IllegalStateException("ShareUserService disconnected during bind"))
                    }
                }
            }
            Shizuku.bindUserService(args, conn)
            val svc = withTimeoutOrNull(10_000) { deferred.await() }
            if (svc == null) {
                runCatching { Shizuku.unbindUserService(args, conn, false) }
                throw IllegalStateException("Timeout binding Shizuku UserService")
            }
            return svc to conn
        }

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
