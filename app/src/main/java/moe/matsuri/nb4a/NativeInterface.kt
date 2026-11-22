package moe.matsuri.nb4a

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import io.nekohasekai.sagernet.utils.DefaultNetworkMonitor
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.WifiStateAccess
import go.Seq
import io.throneproj.mobile.ConnectionOwner
import io.throneproj.mobile.InterfaceUpdateListener
import io.throneproj.mobile.LocalDNSTransport
import io.throneproj.mobile.Mobile
import io.throneproj.mobile.NetworkInterfaceIterator
import io.throneproj.mobile.PlatformInterface
import io.throneproj.mobile.StringIterator
import io.throneproj.mobile.TunOptions
import io.throneproj.mobile.WIFIState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moe.matsuri.nb4a.net.LocalResolverImpl
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.Collections
import io.throneproj.mobile.NetworkInterface as CoreNetworkInterface
import io.throneproj.mobile.Notification as CoreNotification

class NativeInterface internal constructor(
    private val networkMonitor: DefaultNetworkMonitor,
    retryDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    private val maxProbeAttempts: Int = MAX_PROBE_ATTEMPTS,
    private val interfaceProbe: ((Network) -> InterfaceSnapshot?)? = null,
) : PlatformInterface {

    constructor() : this(DefaultNetworkListener)

    internal data class InterfaceSnapshot(
        val name: String,
        val index: Int,
        val isExpensive: Boolean,
    )

    companion object {
        private const val DEFAULT_RETRY_DELAY_MS = 100L
        private const val MAX_PROBE_ATTEMPTS = 10
    }

    private val retryScope = CoroutineScope(SupervisorJob() + retryDispatcher)

    override fun localDNSTransport(): LocalDNSTransport? = try {
        LocalResolverImpl
    } catch (_: Throwable) {
        null
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    // Test boxes run in this process while no VPN service exists; their sockets then take the plain
    // dial path, which is what those tests want, so a missing service is not an error.
    override fun autoDetectInterfaceControl(fd: Int) {
        DataStore.vpnService?.protect(fd)
    }

    override fun openTun(options: TunOptions): Int {
        val service = DataStore.vpnService ?: error("android: no VpnService")
        return service.openTun(options)
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            error("android: connection owner lookup requires API 29")
        }
        val uid = SagerNet.connectivity.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort),
        )
        if (uid == Process.INVALID_UID) error("android: connection owner not found")
        return ConnectionOwner().apply {
            userId = uid
            setAndroidPackageNames(packageNamesOf(uid).toStringIterator())
        }
    }

    // Below API 29 the core finds the owner's uid through procfs and asks for its packages here.
    override fun packageNamesByUid(uid: Int): StringIterator = packageNamesOf(uid).toStringIterator()

    private fun packageNamesOf(uid: Int): List<String> {
        if (uid % PackageCache.PER_USER_RANGE <= 1000) return listOf("android")
        return PackageCache.snapshot().packagesOf(uid)
    }

    internal fun monitorKey(listener: InterfaceUpdateListener): Any {
        if (listener is Seq.Proxy) {
            extractProxyRefnum(listener)?.let { return "go-iface:$it" }
        }
        return IdentityKey(listener)
    }

    private class IdentityKey(val ref: Any) {
        override fun equals(other: Any?): Boolean = other is IdentityKey && ref === other.ref
        override fun hashCode(): Int = System.identityHashCode(ref)
    }

    // Read gomobile's package-private proxy `refnum` without calling `incRefnum()` (which bumps Go refcounts).
    private fun extractProxyRefnum(proxy: Any): Int? {
        var cls: Class<*>? = proxy.javaClass
        while (cls != null && cls != Any::class.java) {
            try {
                val field = cls.getDeclaredField("refnum")
                field.isAccessible = true
                return field.getInt(proxy)
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            } catch (_: Throwable) {
                return null
            }
        }
        return null
    }

    // Registered synchronously so DefaultInterface() is populated before the box's first dial.
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val key = monitorKey(listener)
        synchronized(ifaceReportStates) {
            ifaceReportStates.remove(key)
        }?.let { oldState ->
            synchronized(oldState) {
                oldState.generation++
                oldState.retryJob?.cancel()
                oldState.retryJob = null
            }
        }
        runBlocking {
            networkMonitor.start(key) { network ->
                checkDefaultInterfaceUpdate(key, listener, network)
            }
        }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val key = monitorKey(listener)
        try {
            runBlocking {
                networkMonitor.stop(key)
            }
        } finally {
            val removed = synchronized(ifaceReportStates) {
                ifaceReportStates.remove(key)
            }
            removed?.let { state ->
                synchronized(state) {
                    state.generation++
                    state.retryJob?.cancel()
                    state.retryJob = null
                }
            }
        }
    }

    private class IfaceReportState {
        var name: String? = null
        var index: Int = Int.MIN_VALUE
        var network: Network? = null
        var isExpensive: Boolean = false

        // Survives clearInterface: the first network after a loss is a switch too, only the very first report is not.
        var reported = false
        var generation: Long = 0L
        var retryJob: Job? = null
    }

    private val ifaceReportStates = Collections.synchronizedMap(
        HashMap<Any, IfaceReportState>()
    )

    @Volatile
    private var lastResetNetwork: Network? = null

    private fun reportState(key: Any): IfaceReportState =
        synchronized(ifaceReportStates) {
            ifaceReportStates.getOrPut(key) { IfaceReportState() }
        }

    private fun isActiveState(key: Any, state: IfaceReportState): Boolean =
        synchronized(ifaceReportStates) { ifaceReportStates[key] === state }

    internal fun touchReportStateForTest(listener: InterfaceUpdateListener) {
        reportState(monitorKey(listener))
    }

    internal fun reportStateCountForTest(): Int = synchronized(ifaceReportStates) { ifaceReportStates.size }

    internal fun hasReportStateForTest(listener: InterfaceUpdateListener): Boolean =
        synchronized(ifaceReportStates) { ifaceReportStates.containsKey(monitorKey(listener)) }

    private fun clearInterface(listener: InterfaceUpdateListener, state: IfaceReportState) {
        state.name = null
        state.index = Int.MIN_VALUE
        state.network = null
        state.isExpensive = false
        listener.updateDefaultInterface("", -1, false, false)
    }

    private fun probeInterface(network: Network): InterfaceSnapshot? {
        interfaceProbe?.let { return it(network) }
        val linkProperties = SagerNet.connectivity.getLinkProperties(network) ?: return null
        val interfaceName = linkProperties.interfaceName
        if (interfaceName.isNullOrEmpty()) return null
        val interfaceIndex = try {
            NetworkInterface.getByName(interfaceName)?.index
        } catch (_: Exception) {
            null
        } ?: return null
        val capabilities = SagerNet.connectivity.getNetworkCapabilities(network)
        val isExpensive = capabilities?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } ?: false
        return InterfaceSnapshot(interfaceName, interfaceIndex, isExpensive)
    }

    private fun applySnapshot(
        listener: InterfaceUpdateListener,
        state: IfaceReportState,
        network: Network,
        snapshot: InterfaceSnapshot,
    ) {
        val sameInterface = state.name == snapshot.name && state.index == snapshot.index &&
            (state.network === network || state.network == network)
        // Capability-change storms repeat the same interface; skip them without a JNI round trip. A metering change
        // alone (a Wi-Fi marked metered) is still reported so the core re-reads its interfaces, but is no switch.
        if (sameInterface && state.isExpensive == snapshot.isExpensive) return
        val changed = !sameInterface && state.reported
        state.reported = true
        state.name = snapshot.name
        state.index = snapshot.index
        state.network = network
        state.isExpensive = snapshot.isExpensive
        listener.updateDefaultInterface(snapshot.name, snapshot.index, snapshot.isExpensive, false)
        if (changed) onDefaultInterfaceChanged(network)
    }

    private fun checkDefaultInterfaceUpdate(key: Any, listener: InterfaceUpdateListener, network: Network?) {
        val state = reportState(key)
        val generation = synchronized(state) {
            val nextGen = ++state.generation
            state.retryJob?.cancel()
            state.retryJob = null
            if (network == null) {
                clearInterface(listener, state)
                return
            }
            nextGen
        }
        val targetNetwork = network ?: return
        // Fast path: populate synchronously when LinkProperties & NetworkInterface are already ready.
        val immediate = probeInterface(targetNetwork)
        if (immediate != null) {
            synchronized(state) {
                if (state.generation != generation || !isActiveState(key, state)) return
                applySnapshot(listener, state, targetNetwork, immediate)
            }
            return
        }
        // LinkProperties / NetworkInterface may lag behind the callback briefly: retry off the NetworkCallback actor.
        synchronized(state) {
            if (state.generation != generation || !isActiveState(key, state)) return
            state.retryJob = retryScope.launch {
                repeat((maxProbeAttempts - 1).coerceAtLeast(0)) {
                    delay(retryDelayMs)
                    if (!isActive) return@launch
                    val stillValid = synchronized(state) {
                        state.generation == generation && isActiveState(key, state)
                    }
                    if (!stillValid) return@launch
                    val snapshot = probeInterface(targetNetwork)
                    if (snapshot != null) {
                        synchronized(state) {
                            if (state.generation != generation || !isActiveState(key, state)) return@launch
                            state.retryJob = null
                            applySnapshot(listener, state, targetNetwork, snapshot)
                        }
                        return@launch
                    }
                }
                synchronized(state) {
                    if (state.generation != generation || !isActiveState(key, state)) return@launch
                    state.retryJob = null
                    Logs.w("checkDefaultInterfaceUpdate exhausted retries for $targetNetwork")
                    clearInterface(listener, state)
                }
            }
        }
    }

    // Every box in this process has its own listener, so the user's "reset on network change"
    // switch is applied once per new network to the running VPN instance only.
    private fun onDefaultInterfaceChanged(network: Network) {
        if (!DataStore.networkChangeResetConnections) return
        if (lastResetNetwork === network || lastResetNetwork == network) return
        lastResetNetwork = network
        DataStore.baseService?.data?.proxy?.boxOrNull?.resetNetwork()
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        @Suppress("DEPRECATION") val networks = SagerNet.connectivity.allNetworks
        val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val interfaces = mutableListOf<CoreNetworkInterface>()
        for (network in networks) {
            val linkProperties = SagerNet.connectivity.getLinkProperties(network) ?: continue
            val networkCapabilities = SagerNet.connectivity.getNetworkCapabilities(network) ?: continue
            val networkInterface = networkInterfaces.find { it.name == linkProperties.interfaceName } ?: continue
            val boxInterface = CoreNetworkInterface()
            boxInterface.name = linkProperties.interfaceName
            boxInterface.index = networkInterface.index
            runCatching { boxInterface.mtu = networkInterface.mtu }
                .onFailure { Logs.w("failed to get mtu for interface ${boxInterface.name}: $it") }
            boxInterface.addresses = networkInterface.interfaceAddresses.map { it.toPrefix() }.toStringIterator()
            boxInterface.dnsServer = linkProperties.dnsServers.mapNotNull { it.hostAddress }.toStringIterator()
            boxInterface.gateway = linkProperties.routes.filter { it.isDefaultRoute }
                .mapNotNull { it.gateway?.hostAddress }.toStringIterator()
            boxInterface.type = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Mobile.InterfaceTypeWIFI
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Mobile.InterfaceTypeCellular
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Mobile.InterfaceTypeEthernet
                else -> Mobile.InterfaceTypeOther
            }
            var dumpFlags = 0
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                dumpFlags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            }
            if (networkInterface.isLoopback) dumpFlags = dumpFlags or OsConstants.IFF_LOOPBACK
            if (networkInterface.isPointToPoint) dumpFlags = dumpFlags or OsConstants.IFF_POINTOPOINT
            if (networkInterface.supportsMulticast()) dumpFlags = dumpFlags or OsConstants.IFF_MULTICAST
            boxInterface.flags = dumpFlags
            boxInterface.metered =
                !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            interfaces.add(boxInterface)
        }
        return InterfaceArray(interfaces.iterator())
    }

    private class InterfaceArray(
        private val iterator: Iterator<CoreNetworkInterface>,
    ) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): CoreNetworkInterface = iterator.next()
    }

    // Callbacks whose generated Java method declares no exception must never throw: gomobile does not check or
    // clear a Java exception for them, so it would stay pending on the Go-owned JNI thread.
    override fun readWIFIState(): WIFIState? = try {
        val (ssid, bssid) = WifiStateAccess.read(app)
        WIFIState(ssid, bssid)
    } catch (_: Throwable) {
        null
    }

    override fun clearDNSCache() {
    }

    override fun sendNotification(notification: CoreNotification) {
        val channel = notification.typeName.ifBlank { "core" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SagerNet.notification.createNotificationChannel(
                NotificationChannel(channel, channel, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setShowBadge(false)
                }
            )
        }
        val builder = NotificationCompat.Builder(app, channel)
            .setSmallIcon(R.drawable.ic_throne_tile)
            .setContentTitle(notification.title)
            .setContentText(listOf(notification.subtitle, notification.body).filter { it.isNotBlank() }.joinToString("\n"))
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
            .setAutoCancel(true)
        if (notification.openURL.isNotBlank()) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    app, 0, Intent(Intent.ACTION_VIEW, notification.openURL.toUri()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        SagerNet.notification.notify(notification.identifier, notification.typeID, builder.build())
    }

    override fun cancelNotification(identifier: String, typeID: Int) {
        SagerNet.notification.cancel(identifier, typeID)
    }

}

private fun Iterable<String>.toStringIterator(): StringIterator {
    val values = toList()
    return object : StringIterator {
        private val it = values.iterator()
        override fun hasNext(): Boolean = it.hasNext()
        override fun next(): String = it.next()
        override fun len(): Int = values.size
    }
}

private fun InterfaceAddress.toPrefix(): String {
    return if (address is Inet6Address) {
        "${Inet6Address.getByAddress(address.address).hostAddress}/${networkPrefixLength}"
    } else {
        "${address.hostAddress}/${networkPrefixLength}"
    }
}
