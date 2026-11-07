package io.nekohasekai.sagernet.hotspotshare

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.hotspotshare.shizuku.HevTunnel
import io.nekohasekai.sagernet.hotspotshare.shizuku.IShareUserService
import io.nekohasekai.sagernet.hotspotshare.shizuku.ShareUserService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Rootless HotspotShare backend using Shizuku UserService to control AOSP
 * TestNetworkManager and TetheringManager under shell UID (2000).
 */
class ShizukuBackend(
    private val context: Context,
    private val hevTunnel: HevTunnel = HevTunnel(context),
    private val socksPort: Int = 2088,
) : HotspotShare {

    override val method: ShareMethod = ShareMethod.SHIZUKU

    private val _state = MutableStateFlow<ShareState>(ShareState.Off)
    override val state: StateFlow<ShareState> = _state.asStateFlow()

    private var userService: IShareUserService? = null
    private var boundArgs: Shizuku.UserServiceArgs? = null

    private val deathRecipient = IBinder.DeathRecipient {
        CoroutineScope(Dispatchers.Default).launch {
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
                "Shizuku is not running or permission is denied"
            )
            return@withContext
        }

        // 2. Connect to Shizuku UserService
        val service: IShareUserService
        try {
            service = bindService()
        } catch (t: Throwable) {
            _state.value = ShareState.Error(
                ShareFailure.BACKEND_DIED,
                "Failed to bind Shizuku UserService: ${t.message}"
            )
            return@withContext
        }

        // 3. Create test network TUN
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

        // 4. Start hev-socks5-tunnel
        val hevStarted = try {
            hevTunnel.start(tunPfd, socksPort)
        } catch (t: Throwable) {
            tunPfd.close()
            safeCleanup(service)
            _state.value = ShareState.Error(
                ShareFailure.BACKEND_FAILED,
                "hev-socks5-tunnel start failed: ${t.message}"
            )
            return@withContext
        }

        if (!hevStarted) {
            tunPfd.close()
            safeCleanup(service)
            _state.value = ShareState.Error(
                ShareFailure.BACKEND_FAILED,
                "hev-socks5-tunnel start returned false"
            )
            return@withContext
        }

        // 5. Prefer test networks & start Wi-Fi tethering
        try {
            service.setPreferTestNetworks(true)
            val tetherStarted = service.startTethering()
            if (!tetherStarted) {
                tunPfd.close()
                hevTunnel.stop()
                safeCleanup(service)
                _state.value = ShareState.Error(
                    ShareFailure.BACKEND_FAILED,
                    "Wi-Fi hotspot failed to start via TetheringManager"
                )
                return@withContext
            }
        } catch (t: Throwable) {
            tunPfd.close()
            hevTunnel.stop()
            safeCleanup(service)
            _state.value = ShareState.Error(
                ShareFailure.BACKEND_FAILED,
                "Exception starting tethering: ${t.message}"
            )
            return@withContext
        }

        _state.value = ShareState.Active(ShareMethod.SHIZUKU)
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        val current = _state.value
        if (current is ShareState.Off) return@withContext

        hevTunnel.stop()

        val service = userService
        if (service != null) {
            safeCleanup(service)
            unbindService()
        }

        _state.value = ShareState.Off
    }

    /**
     * Proactively cleans up leftover test networks and tethering state (dirty state recovery).
     */
    suspend fun recoverDirtyState(): Unit = withContext(Dispatchers.IO) {
        if (!isShizukuAvailableAndGranted()) return@withContext
        try {
            val service = bindService()
            service.cleanupDirtyState()
            unbindService()
        } catch (_: Throwable) {
        }
    }

    fun getTrafficStats(): Pair<Long, Long>? {
        val stats = hevTunnel.getStats()
        return if (stats.size >= 4) Pair(stats[3], stats[1]) else null
    }

    private suspend fun handleUnexpectedServiceDeath() {
        hevTunnel.stop()
        userService = null
        var recovered = false
        try {
            val recoveryService = bindService()
            recoveryService.cleanupDirtyState()
            unbindService()
            recovered = true
        } catch (_: Throwable) {
        }

        if (!recovered) {
            io.nekohasekai.sagernet.utils.PlatformNotifications.hotspotShareDropped(context)
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
            android.util.Log.e("ShizukuBackend", "Shizuku binder is not available (pingBinder=false)")
            return@withContext false
        }

        try {
            val perm = Shizuku.checkSelfPermission()
            val granted = perm == android.content.pm.PackageManager.PERMISSION_GRANTED
            android.util.Log.i("ShizukuBackend", "Shizuku.checkSelfPermission() = $perm, granted = $granted")
            granted
        } catch (t: Throwable) {
            android.util.Log.e("ShizukuBackend", "Shizuku.checkSelfPermission() threw: ${t.message}", t)
            false
        }
    }

    companion object {
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
