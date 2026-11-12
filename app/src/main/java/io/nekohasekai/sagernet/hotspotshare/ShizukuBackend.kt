package io.nekohasekai.sagernet.hotspotshare

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import io.nekohasekai.sagernet.BuildConfig
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
import kotlin.math.max

/**
 * Rootless HotspotShare backend using Shizuku UserService to control AOSP
 * TestNetworkManager and TetheringManager under shell UID (2000).
 *
 * Incorporates carlelieser/shizzi's production reliability mechanisms:
 * - Contract version check & stale daemon replacement
 * - Dual-stack TUN + keep-alive NetworkRequest in ShareUserService
 * - Upstream verification (`verifyUpstream`) after tethering starts
 * - Periodic `UpstreamTolerance` watchdog to prevent cellular/Wi-Fi drift leaks
 * - Actual `testtunN` interface name tracking for `/proc/net/dev` counters
 */
class ShizukuBackend(
    private val context: Context,
    private val hevTunnel: HevTunnel = HevTunnel(context),
    private val socksPort: Int = 2088,
) : HotspotShare {

    override val method: ShareMethod = ShareMethod.SHIZUKU

    private val _state = MutableStateFlow<ShareState>(ShareState.Off)
    override val state: StateFlow<ShareState> = _state.asStateFlow()

    private val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchdogJob: Job? = null

    private var userService: IShareUserService? = null
    private var boundArgs: Shizuku.UserServiceArgs? = null

    @Volatile
    var activeInterfaceName: String? = null
        private set

    private val deathRecipient = IBinder.DeathRecipient {
        backendScope.launch {
            handleUnexpectedServiceDeath()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val stub = IShareUserService.Stub.asInterface(service)
            userService = stub
            try {
                service?.linkToDeath(deathRecipient, 0)
            } catch (_: Throwable) {
            }
            connectDeferred?.complete(stub)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            connectDeferred = null
        }
    }

    private var connectDeferred: CompletableDeferred<IShareUserService>? = null

    override suspend fun start(): Unit = withContext(Dispatchers.IO) {
        if (_state.value is ShareState.Active) return@withContext
        _state.value = ShareState.Starting

        // 1. Verify Shizuku availability and permission
        if (!isShizukuAvailableAndGranted()) {
            _state.value = ShareState.Error(
                ShareFailure.SHIZUKU_PERMISSION_DENIED,
                "Shizuku çalışmıyor veya izin verilmedi"
            )
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

        // 3. Create dual-stack test network TUN + TRANSPORT_TEST keep-alive
        val tunPfd = try {
            service.createTestNetwork(listOf("1.1.1.1", "8.8.8.8"))
        } catch (t: Throwable) {
            safeCleanup(service)
            _state.value = ShareState.Error(
                ShareFailure.BACKEND_FAILED,
                "Failed to create Test Network: ${t.message}"
            )
            return@withContext
        }

        val ifaceName = runCatching { service.interfaceName }.getOrNull() ?: "testtun0"
        activeInterfaceName = ifaceName

        // 4. Start hev-socks5-tunnel (MTU 1500 matching TestNetworkManager)
        val hevStarted = try {
            hevTunnel.start(tunPfd, socksPort, HevTunnel.DEFAULT_TUN_MTU)
        } catch (t: Throwable) {
            runCatching { tunPfd.close() }
            safeCleanup(service)
            _state.value = ShareState.Error(
                ShareFailure.BACKEND_FAILED,
                "hev-socks5-tunnel start failed: ${t.message}"
            )
            return@withContext
        }

        if (!hevStarted) {
            runCatching { tunPfd.close() }
            safeCleanup(service)
            _state.value = ShareState.Error(
                ShareFailure.BACKEND_FAILED,
                "hev-socks5-tunnel start returned false"
            )
            return@withContext
        }

        // 5. Prefer test networks (false -> true reset) & start Wi-Fi tethering (with multi-fallback)
        try {
            service.setPreferTestNetworks(true)
            val tetherStarted = service.startTethering()
            if (!tetherStarted) {
                runCatching { tunPfd.close() }
                hevTunnel.stop()
                safeCleanup(service)
                _state.value = ShareState.Error(
                    ShareFailure.BACKEND_FAILED,
                    "Wi-Fi hotspot failed to start via TetheringManager / WifiManager"
                )
                return@withContext
            }

            // 6. Verify via dumpsys tethering that testtunN is selected as upstream
            val verified = runCatching { service.verifyUpstream(UPSTREAM_SETTLE_MS) }.getOrDefault(true)
            if (!verified) {
                Log.w(TAG, "verifyUpstream did not confirm $ifaceName within ${UPSTREAM_SETTLE_MS}ms; watchdog will monitor")
            }
        } catch (t: Throwable) {
            runCatching { tunPfd.close() }
            hevTunnel.stop()
            safeCleanup(service)
            _state.value = ShareState.Error(
                ShareFailure.BACKEND_FAILED,
                "Exception starting tethering: ${t.message}"
            )
            return@withContext
        }

        _state.value = ShareState.Active(ShareMethod.SHIZUKU)
        startUpstreamWatchdog(service, ifaceName)
    }

    private fun startUpstreamWatchdog(service: IShareUserService, expectedIface: String) {
        watchdogJob?.cancel()
        val tolerance = UpstreamTolerance(expectedIface) { strike ->
            Log.w(TAG, strike)
        }
        watchdogJob = backendScope.launch {
            while (isActive && _state.value is ShareState.Active) {
                delay(WATCHDOG_INTERVAL_MS)
                if (_state.value !is ShareState.Active) break

                val rawHealth = runCatching { service.checkUpstreamHealth() }.getOrNull()
                    ?: break
                val (reading, names) = parseHealthString(rawHealth, expectedIface)
                val verdict = tolerance.judge(reading, names)
                if (verdict != null) {
                    Log.e(TAG, "Upstream watchdog triggered fail-closed teardown: $verdict")
                    hevTunnel.stop()
                    safeCleanup(service)
                    unbindService()
                    activeInterfaceName = null
                    PlatformNotifications.hotspotShareDropped(context)
                    _state.value = ShareState.Stopped(ShareFailure.TETHER_LOST)
                    break
                }
            }
        }
    }

    private fun parseHealthString(rawHealth: String, expectedIface: String): Pair<UpstreamReading, List<String>> {
        return when {
            rawHealth == "HEALTHY" -> UpstreamReading.HEALTHY to listOf(expectedIface)
            rawHealth == "ABSENT" -> UpstreamReading.ABSENT to emptyList()
            rawHealth == "TIMED_OUT" -> UpstreamReading.TIMED_OUT to emptyList()
            rawHealth.startsWith("DRIFTED:") -> {
                val driftedList = rawHealth.removePrefix("DRIFTED:").split(",").filter { it.isNotBlank() }
                UpstreamReading.DRIFTED to driftedList
            }
            else -> UpstreamReading.HEALTHY to listOf(expectedIface)
        }
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        val current = _state.value
        if (current is ShareState.Off) return@withContext

        watchdogJob?.cancel()
        watchdogJob = null

        hevTunnel.stop()

        val service = userService
        if (service != null) {
            safeCleanup(service)
            unbindService()
        }
        activeInterfaceName = null

        _state.value = ShareState.Off
    }

    /**
     * Proactively cleans up leftover test networks and tethering state (dirty state recovery).
     */
    suspend fun recoverDirtyState(): Unit = withContext(Dispatchers.IO) {
        if (!isShizukuAvailableAndGranted()) return@withContext
        try {
            val service = bindVerifiedService()
            service.cleanupDirtyState()
            unbindService()
        } catch (_: Throwable) {
        }
    }

    fun getTrafficStats(): Pair<Long, Long>? {
        val hevStats = hevTunnel.getStats()
        val hevRx = if (hevStats.size >= 4) hevStats[3] else 0L
        val hevTx = if (hevStats.size >= 4) hevStats[1] else 0L

        val iface = activeInterfaceName ?: "testtun0"
        val procStats = ProcNetDevParser.readCurrent(setOf(iface))
        val procRx = procStats?.first ?: 0L
        val procTx = procStats?.second ?: 0L

        val rx = max(hevRx, procRx)
        val tx = max(hevTx, procTx)
        return Pair(rx, tx)
    }

    fun getConnectedClientCount(): Int {
        return runCatching { userService?.connectedClientCount ?: 0 }.getOrDefault(0)
    }

    private suspend fun handleUnexpectedServiceDeath() {
        watchdogJob?.cancel()
        watchdogJob = null
        hevTunnel.stop()
        userService = null
        activeInterfaceName = null
        var recovered = false
        try {
            val recoveryService = bindService()
            recoveryService.cleanupDirtyState()
            unbindService()
            recovered = true
        } catch (_: Throwable) {
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

    private fun safeCleanup(service: IShareUserService) {
        try {
            service.cleanupDirtyState()
        } catch (_: Throwable) {
        }
    }

    private suspend fun bindVerifiedService(): IShareUserService {
        val first = bindService()
        val version = runCatching { first.contractVersion }.getOrDefault(-1)
        if (version == ShareUserService.CONTRACT_VERSION) {
            return first
        }
        Log.w(TAG, "Stale ShareUserService contract version ($version != ${ShareUserService.CONTRACT_VERSION}); restarting")
        unbindService()
        delay(300)
        return bindService()
    }

    private suspend fun bindService(): IShareUserService {
        userService?.let { return it }

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
        } ?: throw IllegalStateException("Timeout binding Shizuku UserService")

        return service
    }

    private fun unbindService() {
        val args = boundArgs ?: return
        try {
            Shizuku.unbindUserService(args, serviceConnection, true)
        } catch (_: Throwable) {
        }
        boundArgs = null
        userService = null
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
        private const val UPSTREAM_SETTLE_MS = 8_000L
        private const val WATCHDOG_INTERVAL_MS = 5_000L

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
