package io.nekohasekai.sagernet.hotspotshare.shizuku

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.hotspotshare.HotspotShareLogger
import io.nekohasekai.sagernet.utils.LanSharingNetwork
import io.nekohasekai.sagernet.utils.TetherKind
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Shizuku UserService implementation executing in a dedicated shell process (UID 2000).
 * Provides privileged network management (TestNetworkManager, TetheringManager) with
 * full lifecycle protections:
 * - IPv4-only TUN (192.0.2.2/24) avoiding sing-box FakeIP 198.18.0.0/15 collision & IPv6 blackholes
 * - setupTestNetwork with isUp = true, IPv4 routes, and IPv4 DNS servers via addDnsServer
 * - Network availability wait + ConnectivityManager.requestNetwork(TRANSPORT_TEST) keep-alive
 * - Stale preferTestNetworks(false) -> (true) transition
 * - Preserves already-active Wi-Fi hotspot without bouncing SoftAp; starts it only if closed
 * - Multi-fallback startTethering (exempt=true -> exempt=false -> WifiManager.startTetheredHotspot)
 * - Upstream verification & drift inspection via dumpsys tethering
 * - Ordered cleanup on disconnect/error that keeps hotspot alive; explicit stopTethering() only
 */
class ShareUserService : IShareUserService.Stub {

    private val baseContext: Context?

    @Suppress("unused")
    constructor() {
        HotspotShareLogger.markIsolatedServiceProcess()
        this.baseContext = null
    }

    @Suppress("unused")
    constructor(context: Context) {
        HotspotShareLogger.markIsolatedServiceProcess()
        this.baseContext = context
    }

    private val context: Context by lazy {
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper()
        }
        exemptHiddenApis()
        val ctx = ShellContextHelper.createShellContext(baseContext)
        initServices(ctx)
        ctx
    }

    private var testNetworkManager: Any? = null
    private var tetheringManager: Any? = null
    private var connectivityManager: ConnectivityManager? = null

    private var testTunInterface: Any? = null
    @Volatile
    private var activeInterfaceName: String? = null
    @Volatile
    private var ownerPackageName: String? = null
    private var tunPfd: ParcelFileDescriptor? = null
    private var testNetworkBinder: IBinder? = null
    @Volatile
    private var usesExternalLifeBinder: Boolean = false
    private var acquiredNetwork: Network? = null
    private var keepAliveCallback: ConnectivityManager.NetworkCallback? = null
    private var shutdownHook: Thread? = null
    private val destroying = AtomicBoolean(false)
    private val tetherChannelLock = Any()
    private val callbackExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ShareUserService-cb").apply { isDaemon = true }
    }
    private val tetheringCallbackRegistered = AtomicBoolean(false)
    private val tetheringCallbackInitialIfacesReceived = AtomicBoolean(false)
    private val tetheringCallbackInitialUpstreamReceived = AtomicBoolean(false)
    @Volatile
    private var tetheringEventCallbackProxy: Any? = null
    private val softApCallbackRegistered = AtomicBoolean(false)
    @Volatile
    private var softApCallbackProxy: Any? = null
    @Volatile
    private var cachedTetheredIfaces: List<String> = emptyList()
    @Volatile
    private var cachedUpstreamNetwork: Network? = null
    @Volatile
    private var cachedUpstreamIface: String? = null
    private val upstreamWaitLock = Object()
    @Volatile
    private var cachedTetherClients: List<CachedTetherClient> = emptyList()
    private val cachedTetherWifiClientCount = AtomicInteger(0)
    private val softApClientsSingle = AtomicReference<Int?>(null)
    private val softApClientsByInfo = ConcurrentHashMap<String, Int>()

    private val inspector = UpstreamInspector()

    private fun exemptHiddenApis() {
        try {
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = vmRuntimeClass.getMethod("getRuntime")
            val runtime = getRuntime.invoke(null)
            val setExemptions = vmRuntimeClass.getMethod(
                "setHiddenApiExemptions",
                Array<String>::class.java
            )
            setExemptions.invoke(runtime, arrayOf("L"))
        } catch (_: Throwable) {
        }
    }

    @SuppressLint("WrongConstant")
    private fun initServices(ctx: Context) {
        // 1. ConnectivityManager
        connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        // 2. TestNetworkManager ("test_network" or Class or ServiceManager fallback)
        try {
            testNetworkManager = ctx.getSystemService("test_network")
        } catch (_: Throwable) {
        }
        if (testNetworkManager == null) {
            try {
                val gss = Context::class.java.getMethod("getSystemService", Class::class.java)
                testNetworkManager = gss.invoke(ctx, Class.forName("android.net.TestNetworkManager"))
            } catch (_: Throwable) {
            }
        }
        if (testNetworkManager == null) {
            for (serviceName in listOf("test_network", "test_network_service")) {
                val b = runCatching {
                    Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String::class.java)
                        .invoke(null, serviceName) as? IBinder
                }.getOrNull()
                if (b != null) {
                    val iTnm = Class.forName("android.net.ITestNetworkManager\$Stub")
                        .getMethod("asInterface", IBinder::class.java)
                        .invoke(null, b)
                    val ctor = Class.forName("android.net.TestNetworkManager")
                        .getDeclaredConstructor(Class.forName("android.net.ITestNetworkManager"))
                    ctor.isAccessible = true
                    testNetworkManager = ctor.newInstance(iTnm)
                    break
                }
            }
        }
        if (testNetworkManager == null) {
            throw IllegalStateException("Failed to obtain TestNetworkManager service")
        }

        // 3. TetheringManager
        try {
            tetheringManager = ctx.getSystemService("tethering")
        } catch (_: Throwable) {
        }
        if (tetheringManager == null) {
            val gss = Context::class.java.getMethod("getSystemService", Class::class.java)
            tetheringManager = gss.invoke(ctx, Class.forName("android.net.TetheringManager"))
        }
        if (tetheringManager == null) {
            throw IllegalStateException("Failed to obtain TetheringManager service")
        }

        ShellContextHelper.injectContextIntoTetheringManager(tetheringManager!!, ctx)
        registerTetheringEventCallback(tetheringManager!!)
        registerSoftApCallback(ctx)
    }

    private fun registerTetheringEventCallback(tm: Any) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (tetheringCallbackRegistered.get()) return
        runCatching {
            val cbClass = Class.forName("android.net.TetheringManager\$TetheringEventCallback")
            if (!cbClass.isInterface) return@runCatching
            val proxy = Proxy.newProxyInstance(
                cbClass.classLoader,
                arrayOf(cbClass),
            ) { proxyInstance, method, args ->
                when (method.name) {
                    "equals" -> return@newProxyInstance proxyInstance === args?.firstOrNull()
                    "hashCode" -> return@newProxyInstance System.identityHashCode(proxyInstance)
                    "toString" -> return@newProxyInstance "ShareUserService.TetheringEventCallback"
                    "onTetheredInterfacesChanged" -> {
                        val raw = args?.firstOrNull() as? Collection<*>
                        if (raw != null) {
                            cachedTetheredIfaces = extractTetheredIfaceNames(raw)
                            tetheringCallbackInitialIfacesReceived.set(true)
                        }
                    }
                    "onUpstreamChanged" -> {
                        val net = args?.firstOrNull() as? Network
                        val iface = net?.let { n ->
                            val cm = connectivityManager
                                ?: (baseContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                            runCatching { cm?.getLinkProperties(n)?.interfaceName }.getOrNull()
                                ?.takeIf { it.isNotBlank() }
                        }
                        synchronized(upstreamWaitLock) {
                            cachedUpstreamNetwork = net
                            cachedUpstreamIface = iface
                            tetheringCallbackInitialUpstreamReceived.set(true)
                            upstreamWaitLock.notifyAll()
                        }
                    }
                    "onClientsChanged" -> {
                        val raw = args?.firstOrNull() as? Collection<*>
                        if (raw != null) {
                            val parsed = ArrayList<CachedTetherClient>()
                            var wifiClientCount = 0
                            for (client in raw) {
                                if (client == null) continue
                                val tetheringType = runCatching {
                                    client.javaClass.getMethod("getTetheringType").invoke(client) as? Int
                                }.getOrNull() ?: -1
                                if (tetheringType == TETHERING_BLUETOOTH_TYPE) continue
                                if ((tetheringType == TETHERING_USB || tetheringType == TETHERING_NCM) &&
                                    !LanSharingNetwork.USB_TETHER_ENABLED
                                ) {
                                    continue
                                }
                                if (tetheringType == TETHERING_WIFI) {
                                    wifiClientCount++
                                }
                                val mac = runCatching {
                                    client.javaClass.getMethod("getMacAddress").invoke(client)?.toString()
                                }.getOrNull()
                                val addresses = runCatching {
                                    client.javaClass.getMethod("getAddresses").invoke(client) as? Collection<*>
                                }.getOrNull().orEmpty()
                                for (addrInfo in addresses) {
                                    if (addrInfo == null) continue
                                    val linkAddr = runCatching {
                                        addrInfo.javaClass.getMethod("getAddress").invoke(addrInfo) as? LinkAddress
                                    }.getOrNull()
                                    val ip = linkAddr?.address?.takeIf { it is Inet4Address }?.hostAddress ?: continue
                                    val hostname = runCatching {
                                        addrInfo.javaClass.getMethod("getHostname").invoke(addrInfo) as? String
                                    }.getOrNull()?.takeIf { it.isNotBlank() }
                                    parsed += CachedTetherClient(
                                        ip = ip,
                                        hostname = hostname,
                                        mac = mac,
                                        tetheringType = tetheringType,
                                    )
                                }
                            }
                            cachedTetherClients = parsed
                            cachedTetherWifiClientCount.set(wifiClientCount)
                        }
                    }
                }
                null
            }
            tm.javaClass.getMethod("registerTetheringEventCallback", Executor::class.java, cbClass)
                .invoke(tm, callbackExecutor, proxy)
            tetheringEventCallbackProxy = proxy
            tetheringCallbackRegistered.set(true)
            HotspotShareLogger.info(TAG, "Registered TetheringEventCallback in ShareUserService")
        }.onFailure { t ->
            HotspotShareLogger.warn(TAG, "TetheringEventCallback unavailable: ${t.message}")
        }
    }

    private fun registerSoftApCallback(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (softApCallbackRegistered.get()) return
        runCatching {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) ?: return@runCatching
            val cbClass = Class.forName("android.net.wifi.WifiManager\$SoftApCallback")
            if (!cbClass.isInterface) return@runCatching
            val proxy = Proxy.newProxyInstance(
                cbClass.classLoader,
                arrayOf(cbClass),
            ) { proxyInstance, method, args ->
                when (method.name) {
                    "equals" -> return@newProxyInstance proxyInstance === args?.firstOrNull()
                    "hashCode" -> return@newProxyInstance System.identityHashCode(proxyInstance)
                    "toString" -> return@newProxyInstance "ShareUserService.SoftApCallback"
                    "onStateChanged" -> {
                        val state = args?.getOrNull(0) as? Int ?: -1
                        if (state == WIFI_AP_STATE_DISABLED || state == WIFI_AP_STATE_FAILED) {
                            softApClientsSingle.set(0)
                            softApClientsByInfo.clear()
                        }
                    }
                    "onConnectedClientsChanged" -> {
                        if (args != null && args.size == 1) {
                            val clients = args[0] as? Collection<*>
                            if (clients != null) {
                                softApClientsSingle.set(clients.size)
                            }
                        } else if (args != null && args.size >= 2) {
                            val info = args[0]
                            val clients = args[1] as? Collection<*>
                            if (clients != null) {
                                val key = runCatching {
                                    info?.javaClass?.getMethod("getApInstanceIdentifier")?.invoke(info)?.toString()
                                }.getOrNull() ?: info?.toString() ?: "default"
                                softApClientsByInfo[key] = clients.size
                            }
                        }
                    }
                    "onInfoChanged" -> {
                        val infos = args?.firstOrNull() as? Collection<*>
                        if (infos != null) {
                            val activeKeys = infos.mapNotNull { info ->
                                runCatching {
                                    info?.javaClass?.getMethod("getApInstanceIdentifier")?.invoke(info)?.toString()
                                }.getOrNull() ?: info?.toString()
                            }.toSet()
                            softApClientsByInfo.keys.retainAll(activeKeys)
                        }
                    }
                }
                null
            }
            wm.javaClass.getMethod("registerSoftApCallback", Executor::class.java, cbClass)
                .invoke(wm, callbackExecutor, proxy)
            softApCallbackProxy = proxy
            softApCallbackRegistered.set(true)
            HotspotShareLogger.info(TAG, "Registered WifiManager.SoftApCallback in ShareUserService")
        }.onFailure { t ->
            HotspotShareLogger.warn(TAG, "SoftApCallback unavailable (will use cached fallback): ${t.message}")
        }
    }

    private fun unregisterPlatformCallbacks() {
        val tm = tetheringManager
        val tetheringProxy = tetheringEventCallbackProxy
        tetheringEventCallbackProxy = null
        tetheringCallbackRegistered.set(false)
        if (tm != null && tetheringProxy != null) {
            runCatching {
                val cbClass = Class.forName("android.net.TetheringManager\$TetheringEventCallback")
                tm.javaClass.getMethod("unregisterTetheringEventCallback", cbClass).invoke(tm, tetheringProxy)
            }
        }
        val softApProxy = softApCallbackProxy
        softApCallbackProxy = null
        softApCallbackRegistered.set(false)
        if (softApProxy != null) {
            runCatching {
                val wm = baseContext?.getSystemService(Context.WIFI_SERVICE)
                    ?: runCatching { context.getSystemService(Context.WIFI_SERVICE) }.getOrNull()
                val cbClass = Class.forName("android.net.wifi.WifiManager\$SoftApCallback")
                wm?.javaClass?.getMethod("unregisterSoftApCallback", cbClass)?.invoke(wm, softApProxy)
            }
        }
        softApClientsByInfo.clear()
        softApClientsSingle.set(null)
        cachedTetherClients = emptyList()
        cachedTetheredIfaces = emptyList()
        tetheringCallbackInitialIfacesReceived.set(false)
        synchronized(upstreamWaitLock) {
            cachedUpstreamNetwork = null
            cachedUpstreamIface = null
            tetheringCallbackInitialUpstreamReceived.set(false)
            upstreamWaitLock.notifyAll()
        }
        cachedTetherWifiClientCount.set(0)
    }

    private fun getCachedSoftApClientCount(): Int {
        if (softApClientsByInfo.isNotEmpty()) {
            return softApClientsByInfo.values.sum()
        }
        return softApClientsSingle.get() ?: 0
    }

    override fun getContractVersion(): Int = CONTRACT_VERSION

    @Synchronized
    override fun getInterfaceName(): String? = activeInterfaceName

    @Synchronized
    override fun createTestNetwork(dnsServers: List<String>?): ParcelFileDescriptor =
        createTestNetworkWithBinder(dnsServers, null)

    @Synchronized
    override fun createTestNetworkWithBinder(
        dnsServers: List<String>?,
        lifeBinder: IBinder?,
    ): ParcelFileDescriptor {
        ensureWatchdogAsync()
        val ctx = context
        val ownerPkg = resolveOwnerPackageName(ctx)
        val tnm = testNetworkManager ?: throw IllegalStateException("TestNetworkManager is not ready")
        val cm = connectivityManager ?: (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
        HotspotShareLogger.info(
            TAG,
            "createTestNetwork requested (dns=${dnsServers?.joinToString(",")}, externalBinder=${lifeBinder != null}, pkg=$ownerPkg)",
        )

        cleanupOrphanedTestNetworks(tnm, cm, ownerPkg)

        // Build dual-stack LinkAddresses (RFC 5737 192.0.2.2/24 + non-FakeIP ULA fd00:2:0:2::2/64)
        // avoiding sing-box FakeIP 198.18.0.0/15 & FakeDNS 2001:db8::/32 collisions.
        val ipv4Addr = buildLinkAddress(InetAddress.getByName(TUN_IPV4_ADDRESS), TUN_IPV4_PREFIX)
        val dualStackAddresses = runCatching {
            listOf(
                ipv4Addr,
                buildLinkAddress(InetAddress.getByName(TUN_IPV6_ADDRESS), TUN_IPV6_PREFIX),
            )
        }.getOrElse { listOf(ipv4Addr) }

        var usedAddresses = dualStackAddresses
        var ifaceObj = runCatching { invokeCreateTunInterface(tnm, dualStackAddresses) }.getOrNull()
        if (ifaceObj == null && dualStackAddresses.size > 1) {
            Log.w(TAG, "Dual-stack createTunInterface unavailable; falling back to IPv4-only")
            HotspotShareLogger.warn(TAG, "Dual-stack createTunInterface unavailable; falling back to IPv4-only")
            usedAddresses = listOf(ipv4Addr)
            ifaceObj = invokeCreateTunInterface(tnm, usedAddresses)
        }
        val tunIface = ifaceObj ?: run {
            HotspotShareLogger.error(TAG, "createTunInterface returned null for $usedAddresses")
            throw IllegalStateException("createTunInterface returned null for $usedAddresses")
        }

        val hasIpv6 = usedAddresses.any { it.address is Inet6Address }
        testTunInterface = tunIface
        val ifaceName = tunIface.javaClass.getMethod("getInterfaceName").invoke(tunIface) as String
        val pfd = tunIface.javaClass.getMethod("getFileDescriptor").invoke(tunIface) as ParcelFileDescriptor
        activeInterfaceName = ifaceName
        tunPfd = pfd
        persistOwnedTestTunInterface(ownerPkg, ifaceName)

        // Build LinkProperties with interfaceName, LinkAddresses, routes, and DNS servers
        val dnsInetList = buildDnsList(dnsServers, includeIpv6 = hasIpv6)
        val lp = LinkProperties().apply {
            runCatching {
                LinkProperties::class.java.getMethod("setInterfaceName", String::class.java)
                    .invoke(this, ifaceName)
            }
            runCatching {
                LinkProperties::class.java.getMethod("setLinkAddresses", Collection::class.java)
                    .invoke(this, usedAddresses)
            }
            addRoutes(this, ifaceName, includeIpv6 = hasIpv6)
            val addDnsMethod = runCatching {
                LinkProperties::class.java.getMethod("addDnsServer", InetAddress::class.java)
            }.getOrNull()
            if (addDnsMethod != null) {
                dnsInetList.forEach { dns -> runCatching { addDnsMethod.invoke(this, dns) } }
            } else {
                runCatching {
                    LinkProperties::class.java.getMethod("setDnsServers", Collection::class.java)
                        .invoke(this, dnsInetList)
                }
            }
        }

        val hasExternalBinder = lifeBinder?.let { runCatching { it.isBinderAlive }.getOrDefault(false) } == true
        val binder = if (hasExternalBinder) lifeBinder!! else Binder()
        testNetworkBinder = binder
        usesExternalLifeBinder = hasExternalBinder

        // Register TRANSPORT_TEST keep-alive BEFORE setupTestNetwork so ConnectivityService
        // never lingers and tears down testtunN immediately after registration.
        requestKeepAlive(cm)

        // CRITICAL FIX (from shizzi): 2nd argument to setupTestNetwork(LinkProperties, boolean, IBinder)
        // is `isUp = true`! Passing `false` marks the test network interface down.
        val setupWithLp = runCatching {
            val setupMethod = tnm.javaClass.getMethod(
                "setupTestNetwork",
                LinkProperties::class.java,
                Boolean::class.javaPrimitiveType,
                IBinder::class.java,
            )
            setupMethod.invoke(tnm, lp, true, binder)
        }.isSuccess

        if (!setupWithLp) {
            val setupMethod = tnm.javaClass.getMethod(
                "setupTestNetwork",
                String::class.java,
                IBinder::class.java,
            )
            setupMethod.invoke(tnm, ifaceName, binder)
        }

        acquiredNetwork = awaitNetworkAvailability(cm, ifaceName, AVAILABILITY_TIMEOUT_MS)

        Log.i(TAG, "Test network up: $ifaceName (network=$acquiredNetwork)")
        HotspotShareLogger.info(TAG, "Test network up: $ifaceName (network=$acquiredNetwork, ipv6=$hasIpv6)")
        return pfd.dup()
    }

    @Synchronized
    override fun adoptExistingTestNetwork(expectedIface: String?): Boolean {
        val target = expectedIface?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        ensureWatchdogAsync()
        val ctx = context
        val cm = connectivityManager
            ?: (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
            ?: return false
        val foundNetwork = runCatching {
            cm.allNetworks.firstOrNull { candidate ->
                cm.getLinkProperties(candidate)?.interfaceName == target
            }
        }.getOrNull() ?: return false

        val ifaceUp = runCatching {
            NetworkInterface.getByName(target)?.isUp == true
        }.getOrDefault(true)
        if (!ifaceUp) return false

        runCatching { requestKeepAlive(cm) }
        acquiredNetwork = foundNetwork
        activeInterfaceName = target
        usesExternalLifeBinder = true
        persistOwnedTestTunInterface(resolveOwnerPackageName(ctx), target)
        runCatching { setPreferTestNetworks(true) }
        installShutdownHook()
        HotspotShareLogger.info(TAG, "Adopted live $target after ShareUserService restart (network=$foundNetwork)")
        return true
    }

    private fun resolveOwnerPackageName(ctx: Context? = baseContext): String {
        val callingUid = runCatching { Binder.getCallingUid() }.getOrDefault(-1)
        val myUid = runCatching { Process.myUid() }.getOrDefault(-1)
        if (callingUid > 0 && callingUid != myUid && callingUid != ShellContextHelper.SHELL_UID) {
            val fromUid = runCatching {
                val pm = (ctx ?: baseContext)?.packageManager
                pm?.getPackagesForUid(callingUid)?.firstOrNull { it.isNotBlank() }
            }.getOrNull()
            if (!fromUid.isNullOrBlank()) {
                ownerPackageName = fromUid
                return fromUid
            }
        }
        ownerPackageName?.let { return it }
        val fromBase = baseContext?.packageName?.takeIf {
            it.isNotBlank() && it != ShellContextHelper.SHELL_PACKAGE && it != "android"
        }
        val resolved = fromBase
            ?: BuildConfig.APPLICATION_ID.takeIf { it.isNotBlank() }
            ?: if (callingUid > 0) "uid_$callingUid" else "default"
        ownerPackageName = resolved
        return resolved
    }

    private fun cleanupOrphanedTestNetworks(
        tnm: Any,
        cm: ConnectivityManager,
        ownerPkg: String = resolveOwnerPackageName(baseContext),
    ) {
        val persistedOwned = readOwnedTestTunInterface(ownerPkg)
        val candidateIfaces = runCatching {
            cm.allNetworks.mapNotNull { net ->
                runCatching { cm.getLinkProperties(net)?.interfaceName }.getOrNull()
            }
        }.getOrDefault(emptyList())
        val ownedIfaces = selectOwnedOrphanedTestInterfaces(
            networkIfaces = candidateIfaces,
            activeIface = activeInterfaceName,
            persistedOwnedIface = persistedOwned,
        )
        if (ownedIfaces.isEmpty()) {
            clearOwnedTestTunInterface(ownerPkg)
            return
        }
        val teardownMethod = runCatching {
            tnm.javaClass.getMethod("teardownTestNetwork", Network::class.java)
        }.getOrNull() ?: return
        runCatching {
            cm.allNetworks.forEach { network ->
                val ifName = runCatching { cm.getLinkProperties(network)?.interfaceName }.getOrNull()
                if (ifName != null && ifName in ownedIfaces) {
                    runCatching { teardownMethod.invoke(tnm, network) }
                    HotspotShareLogger.info(TAG, "Cleaned up owned test network $ifName before creating new tunnel (pkg=$ownerPkg)")
                }
            }
        }
        clearOwnedTestTunInterface(ownerPkg)
    }

    private fun ensureWatchdogAsync() {
        Thread {
            runCatching { ShizukuWatchdog.ensureKeepAliveRunning() }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun buildLinkAddress(address: InetAddress, prefixLength: Int): LinkAddress {
        return try {
            val ctor = LinkAddress::class.java.getDeclaredConstructor(
                InetAddress::class.java,
                Int::class.javaPrimitiveType,
            )
            ctor.isAccessible = true
            ctor.newInstance(address, prefixLength)
        } catch (_: Throwable) {
            val ctor = LinkAddress::class.java.getConstructor(String::class.java)
            ctor.newInstance("${address.hostAddress}/$prefixLength")
        }
    }

    private fun invokeCreateTunInterface(tnm: Any, addresses: List<LinkAddress>): Any? {
        // Probe Collection<LinkAddress> overload first, then LinkAddress[] array overload
        val viaCollection = runCatching {
            val method = tnm.javaClass.getMethod("createTunInterface", Collection::class.java)
            method.invoke(tnm, addresses)
        }.getOrNull()
        if (viaCollection != null) return viaCollection

        return runCatching {
            val arrayType = Class.forName("[Landroid.net.LinkAddress;")
            val method = tnm.javaClass.getMethod("createTunInterface", arrayType)
            val arr = java.lang.reflect.Array.newInstance(LinkAddress::class.java, addresses.size)
            addresses.forEachIndexed { idx, addr -> java.lang.reflect.Array.set(arr, idx, addr) }
            method.invoke(tnm, arr)
        }.getOrNull()
    }

    private fun addRoutes(lp: LinkProperties, ifaceName: String, includeIpv6: Boolean) {
        runCatching {
            val ipPrefixClass = Class.forName("android.net.IpPrefix")
            val ipPrefixCtor = ipPrefixClass.getDeclaredConstructor(
                InetAddress::class.java,
                Int::class.javaPrimitiveType,
            ).apply { isAccessible = true }
            val routeInfoClass = Class.forName("android.net.RouteInfo")
            val routeCtor = routeInfoClass.getDeclaredConstructor(
                ipPrefixClass,
                InetAddress::class.java,
                String::class.java,
            ).apply { isAccessible = true }
            val subnetPrefix = ipPrefixCtor.newInstance(InetAddress.getByName("192.0.2.0"), TUN_IPV4_PREFIX)
            val defaultPrefix = ipPrefixCtor.newInstance(InetAddress.getByName("0.0.0.0"), 0)
            lp.addRoute(routeCtor.newInstance(subnetPrefix, null, ifaceName) as android.net.RouteInfo)
            lp.addRoute(routeCtor.newInstance(defaultPrefix, null, ifaceName) as android.net.RouteInfo)
            if (includeIpv6) {
                val subnetV6 = ipPrefixCtor.newInstance(InetAddress.getByName("fd00:2:0:2::"), TUN_IPV6_PREFIX)
                val defaultV6 = ipPrefixCtor.newInstance(InetAddress.getByName("::"), 0)
                lp.addRoute(routeCtor.newInstance(subnetV6, null, ifaceName) as android.net.RouteInfo)
                lp.addRoute(routeCtor.newInstance(defaultV6, null, ifaceName) as android.net.RouteInfo)
            }
        }
    }

    private fun buildDnsList(dnsServers: List<String>?, includeIpv6: Boolean): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        if (!dnsServers.isNullOrEmpty()) {
            for (dns in dnsServers) {
                runCatching {
                    val addr = InetAddress.getByName(dns)
                    val allowed = addr.address.size == 4 || (includeIpv6 && addr is Inet6Address)
                    if (allowed && addr !in result) result.add(addr)
                }
            }
        }
        if (result.none { it.address.size == 4 }) {
            runCatching { result.add(InetAddress.getByName("1.1.1.1")) }
            runCatching { result.add(InetAddress.getByName("8.8.8.8")) }
        }
        if (includeIpv6 && result.none { it is Inet6Address }) {
            runCatching { result.add(InetAddress.getByName("2606:4700:4700::1111")) }
            runCatching { result.add(InetAddress.getByName("2001:4860:4860::8888")) }
        }
        return result
    }

    private fun awaitNetworkAvailability(
        cm: ConnectivityManager,
        ifaceName: String,
        timeoutMs: Long,
    ): Network? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val found = runCatching {
                cm.allNetworks.firstOrNull { candidate ->
                    cm.getLinkProperties(candidate)?.interfaceName == ifaceName
                }
            }.getOrNull()
            if (found != null) return found
            Thread.sleep(POLL_INTERVAL_MS)
        }
        Log.w(TAG, "Test network '$ifaceName' did not appear in allNetworks within ${timeoutMs}ms")
        return null
    }

    private fun requestKeepAlive(cm: ConnectivityManager) {
        keepAliveCallback?.let { old ->
            runCatching { cm.unregisterNetworkCallback(old) }
        }
        val transportTest = resolveTransportTest()
        val request = NetworkRequest.Builder()
            .clearCapabilities()
            .addTransportType(transportTest)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {}
        keepAliveCallback = callback
        runCatching {
            cm.requestNetwork(request, callback)
        }.onFailure { failure ->
            keepAliveCallback = null
            throw IllegalStateException(
                "requestKeepAlive failed: test network would be lingered away within seconds",
                failure,
            )
        }
    }

    private fun resolveTransportTest(): Int = runCatching {
        NetworkCapabilities::class.java.getField("TRANSPORT_TEST").getInt(null)
    }.getOrDefault(TRANSPORT_TEST_FALLBACK)

    @Synchronized
    override fun setPreferTestNetworks(prefer: Boolean) {
        val tm = tetheringManager ?: run {
            context
            tetheringManager ?: throw IllegalStateException("TetheringManager is not ready")
        }
        val setPreferMethod = tm.javaClass.getMethod("setPreferTestNetworks", Boolean::class.javaPrimitiveType)
        if (prefer) {
            // Clear stale preference first so UpstreamNetworkMonitor sees a fresh transition (from shizzi)
            runCatching { setPreferMethod.invoke(tm, false) }
        }
        setPreferMethod.invoke(tm, prefer)
        HotspotShareLogger.info(TAG, "setPreferTestNetworks($prefer)")
    }

    @Synchronized
    override fun startTethering(): Boolean {
        ensureWatchdogAsync()
        val ctx = context
        val tm = tetheringManager ?: throw IllegalStateException("TetheringManager is not ready")

        // Preserve any already-open tethering downstream (Wi-Fi, USB, or Bluetooth);
        // ShizukuBackend will call restartWifiTethering() only if upstream fails to switch onto testtunN.
        if (isWifiHotspotActiveInternal(ctx, waitIfEnabling = true)) {
            val mask = getActiveTetherMask()
            Log.i(TAG, "Tethering downstream already active (mask=$mask); attaching upstream without forcing Wi-Fi SoftAp")
            HotspotShareLogger.info(TAG, "Tethering downstream already active (mask=$mask); attaching upstream without forcing Wi-Fi SoftAp")
            installShutdownHook()
            return true
        }

        if (startWifiTetheringWithFallback(tm, ctx)) {
            HotspotShareLogger.info(TAG, "Wi-Fi hotspot started via startWifiTetheringWithFallback")
            installShutdownHook()
            return true
        }
        HotspotShareLogger.error(TAG, "startTethering failed across all fallbacks")
        return false
    }

    @Synchronized
    override fun restartWifiTethering(): Boolean {
        val ctx = context
        val tm = tetheringManager ?: throw IllegalStateException("TetheringManager is not ready")

        val activeMask = getActiveTetherMask()
        val targetMask = if (activeMask != 0) activeMask else TETHER_MASK_WIFI
        val hasNcm = hasActiveNcmDownstream(ctx)

        Log.i(TAG, "Restarting active tethering downstreams (mask=$targetMask, ncm=$hasNcm) so upstream selects $activeInterfaceName")
        HotspotShareLogger.info(TAG, "Restarting active tethering downstreams (mask=$targetMask, ncm=$hasNcm) onto $activeInterfaceName")

        stopMaskChannels(tm, ctx, targetMask, hasNcm)
        waitForTetherMaskStopped(ctx, targetMask)

        // Explicitly re-toggle preferTestNetworks(false -> true) before restarting channels.
        runCatching { setPreferTestNetworks(false) }
        runCatching { setPreferTestNetworks(true) }

        var restarted = startMaskChannels(tm, ctx, targetMask, hasNcm)
        if ((targetMask and TETHER_MASK_WIFI) != 0) {
            val settled = restarted && awaitWifiHotspotSettled(ctx)
            if (!settled) {
                HotspotShareLogger.warn(TAG, "Wi-Fi hotspot did not settle after restart; retrying with WifiManager fallback")
                runCatching { Thread.sleep(RESTART_RETRY_DELAY_MS) }
                restarted = startWifiTetheringWithFallback(tm, ctx, verifySettled = true) &&
                    awaitWifiHotspotSettled(ctx)
            } else {
                restarted = true
            }
        } else if (!restarted) {
            runCatching { Thread.sleep(RESTART_RETRY_DELAY_MS) }
            restarted = startMaskChannels(tm, ctx, targetMask, hasNcm) ||
                isWifiHotspotActiveInternal(ctx, waitIfEnabling = true)
        }
        if (restarted) {
            HotspotShareLogger.info(TAG, "restartWifiTethering succeeded (mask=$targetMask)")
            installShutdownHook()
        } else {
            HotspotShareLogger.error(TAG, "restartWifiTethering failed (mask=$targetMask)")
        }
        return restarted
    }

    @Synchronized
    override fun ensureTetherMaskStarted(targetMask: Int, preferNcm: Boolean): Boolean {
        val ctx = context
        val tm = tetheringManager ?: throw IllegalStateException("TetheringManager is not ready")
        ensureWatchdogAsync()
        val allowedMask = if (LanSharingNetwork.USB_TETHER_ENABLED) {
            targetMask and (TETHER_MASK_WIFI or TETHER_MASK_USB)
        } else {
            targetMask and TETHER_MASK_WIFI
        }
        val desired = if (allowedMask != 0) allowedMask else TETHER_MASK_WIFI
        val currentMask = getActiveTetherMask()
        val missingMask = desired and currentMask.inv()
        if (missingMask == 0) {
            HotspotShareLogger.info(TAG, "ensureTetherMaskStarted(mask=$desired): all channels already active (mask=$currentMask)")
            installShutdownHook()
            return true
        }
        HotspotShareLogger.info(
            TAG,
            "ensureTetherMaskStarted(target=$desired, current=$currentMask, missing=$missingMask, preferNcm=$preferNcm)",
        )
        if (activeInterfaceName != null) {
            runCatching { setPreferTestNetworks(false) }
            runCatching { setPreferTestNetworks(true) }
        }
        val started = startMaskChannels(tm, ctx, missingMask, preferNcm)
        val afterMask = getActiveTetherMask()
        val ok = started || (afterMask and desired) != 0
        if (ok) {
            installShutdownHook()
        }
        HotspotShareLogger.info(TAG, "ensureTetherMaskStarted result: ok=$ok afterMask=$afterMask")
        return ok
    }

    override fun startTetheringType(type: Int): Boolean = synchronized(tetherChannelLock) {
        val ctx = runCatching { context }.getOrNull() ?: return false
        val tm = tetheringManager ?: return false
        HotspotShareLogger.info(TAG, "startTetheringType(type=$type)")

        if (type == TETHERING_BLUETOOTH_TYPE) {
            HotspotShareLogger.info(TAG, "startTetheringType($type): Bluetooth tethering is not supported in Shizuku mode")
            return false
        }
        if ((type == TETHERING_USB || type == TETHERING_NCM) && !LanSharingNetwork.USB_TETHER_ENABLED) {
            HotspotShareLogger.info(TAG, "startTetheringType($type): USB tethering is currently disabled")
            return false
        }

        val activeMask = getActiveTetherMask()
        val alreadyActive = when (type) {
            TETHERING_WIFI -> (activeMask and TETHER_MASK_WIFI) != 0
            TETHERING_USB, TETHERING_NCM -> (activeMask and TETHER_MASK_USB) != 0
            else -> false
        }
        if (alreadyActive) {
            HotspotShareLogger.info(TAG, "startTetheringType($type): channel already active (mask=$activeMask)")
            return true
        }

        return when (type) {
            TETHERING_WIFI -> startWifiTetheringWithFallback(tm, ctx)
            TETHERING_USB, TETHERING_NCM -> startUsbTetheringWithFallback(tm, ctx, type)
            else -> startViaTetheringRequest(tm, type, isExempt = true) ||
                startViaTetheringRequest(tm, type, isExempt = false)
        }
    }

    override fun stopTetheringType(type: Int) = synchronized(tetherChannelLock) {
        val ctx = runCatching { context }.getOrNull() ?: return
        val tm = tetheringManager ?: return
        HotspotShareLogger.info(TAG, "stopTetheringType(type=$type)")
        when (type) {
            TETHERING_WIFI -> stopWifiTetheringInternal(tm, ctx)
            TETHERING_USB, TETHERING_NCM -> if (LanSharingNetwork.USB_TETHER_ENABLED) {
                stopUsbTetheringInternal(tm, ctx, type)
            }
            TETHERING_BLUETOOTH_TYPE -> Unit
            else -> invokeStopTethering(tm, type)
        }
        Unit
    }

    override fun getActiveTetherMask(): Int {
        val ctx = runCatching { context }.getOrNull() ?: return 0
        return computeActiveTetherMask(ctx, cachedTetheredIfaces)
    }

    override fun getTetheredIfaces(): List<String> {
        val ctx = runCatching { context }.getOrNull() ?: return cachedTetheredIfaces
        if (hasAuthoritativeTetheredIfacesCallback()) {
            return cachedTetheredIfaces
        }
        return queryPlatformTetheredIfaces(ctx)
    }

    override fun isWifiHotspotActive(): Boolean {
        val ctx = runCatching { context }.getOrNull() ?: return false
        return isWifiHotspotActiveInternal(ctx, waitIfEnabling = false)
    }

    private fun startWifiTetheringWithFallback(
        tm: Any,
        ctx: Context,
        verifySettled: Boolean = false,
    ): Boolean {
        if (startViaTetheringRequest(tm, TETHERING_WIFI, isExempt = true) &&
            (!verifySettled || awaitWifiHotspotSettled(ctx))
        ) {
            return true
        }
        if (startViaTetheringRequest(tm, TETHERING_WIFI, isExempt = false) &&
            (!verifySettled || awaitWifiHotspotSettled(ctx))
        ) {
            return true
        }
        return startViaWifiManager(ctx)
    }

    private fun startUsbTetheringWithFallback(tm: Any, ctx: Context, requestedType: Int): Boolean {
        if (activeInterfaceName != null) {
            runCatching { setPreferTestNetworks(true) }
        }

        // Try direct TetheringRequest FIRST: resetting USB prematurely when sys.usb.state is
        // already `rndis,adb` bounces the USB gadget and resets adbd before TetheringRequest runs.
        if (startViaTetheringRequest(tm, requestedType, isExempt = true) && awaitUsbMaskActive(ctx)) {
            return true
        }

        val postFirstObs = runCatching { inspector.observeTethering() }.getOrNull()
        val usbAvailErrors = postFirstObs?.let {
            UpstreamInspector.parseAvailableDownstreamErrors(it.rawOutput)
                .filterKeys { iface -> isUsbDownstreamName(iface) }
        }.orEmpty()
        val sysUsbState = runShellWithResult(listOf("getprop", "sys.usb.state")).second.trim().lowercase()
        val postFirstMask = computeActiveTetherMask(ctx, postFirstObs?.tetheredDownstreams.orEmpty())
        if ((postFirstMask and TETHER_MASK_USB) != 0) {
            return true
        }

        val isStuckUsb = usbAvailErrors.isNotEmpty() ||
            sysUsbState.contains("rndis") ||
            sysUsbState.contains("ncm")
        if (isStuckUsb) {
            HotspotShareLogger.warn(
                TAG,
                "USB tethering did not activate on exempt request (availErrors=$usbAvailErrors, sys.usb.state=$sysUsbState); resetting stuck state once",
            )
            resetStuckUsbTethering(tm, ctx, requestedType)
        } else {
            HotspotShareLogger.info(TAG, "USB tethering did not activate on exempt request; trying non-exempt fallback")
        }

        if (startViaTetheringRequest(tm, requestedType, isExempt = false) && awaitUsbMaskActive(ctx)) {
            return true
        }

        val cm = connectivityManager ?: (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
        if (cm != null) {
            val cmRes = runCatching {
                cm.javaClass.getMethod("setUsbTethering", Boolean::class.javaPrimitiveType)
                    .invoke(cm, true) as? Int
            }.getOrNull()
            HotspotShareLogger.info(TAG, "ConnectivityManager.setUsbTethering(true) returned $cmRes")
            if (cmRes == 0 && awaitUsbMaskActive(ctx)) {
                return true
            }
        }

        val proto = if (requestedType == TETHERING_NCM) "ncm" else "usb"
        for (cmd in listOf(
            listOf("cmd", "connectivity", "start-tethering", proto),
            listOf("cmd", "tethering", "start-tethering", proto),
        )) {
            val (exitCode, out) = runShellWithResult(cmd)
            HotspotShareLogger.info(TAG, "Fallback '${cmd.joinToString(" ")}' -> exit=$exitCode out=${out.trim()}")
            if (exitCode == 0 && awaitUsbMaskActive(ctx)) {
                return true
            }
        }

        val baseFn = if (requestedType == TETHERING_NCM) "ncm" else "rndis"
        val usbFn = if (sysUsbState.contains("adb") || sysUsbState.isBlank()) "$baseFn,adb" else baseFn
        val (svcExit, svcOut) = runShellWithResult(listOf("svc", "usb", "setFunctions", usbFn))
        HotspotShareLogger.info(TAG, "Fallback 'svc usb setFunctions $usbFn' -> exit=$svcExit out=${svcOut.trim()}")
        if (svcExit == 0 && awaitUsbMaskActive(ctx)) {
            return true
        }

        val finalErrors = runCatching {
            UpstreamInspector.parseAvailableDownstreamErrors(inspector.observeTethering().rawOutput)
                .filterKeys { iface -> isUsbDownstreamName(iface) }
        }.getOrDefault(emptyMap())
        HotspotShareLogger.warn(
            TAG,
            "startUsbTetheringWithFallback(type=$requestedType) failed across all fallbacks (availErrors=$finalErrors)",
        )
        return false
    }

    private fun resetStuckUsbTethering(tm: Any, ctx: Context, requestedType: Int) {
        invokeStopTethering(tm, TETHERING_USB)
        if (requestedType == TETHERING_NCM || hasActiveNcmDownstream(ctx)) {
            invokeStopTethering(tm, TETHERING_NCM)
        }
        val cm = connectivityManager ?: (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
        runCatching {
            cm?.javaClass?.getMethod("setUsbTethering", Boolean::class.javaPrimitiveType)
                ?.invoke(cm, false)
        }
        runCatching { Thread.sleep(USB_RESET_SETTLE_MS) }
        if (activeInterfaceName != null) {
            runCatching { setPreferTestNetworks(true) }
        }
    }

    private fun awaitUsbMaskActive(ctx: Context): Boolean {
        val deadline = System.currentTimeMillis() + USB_FALLBACK_SETTLE_MS
        while (System.currentTimeMillis() < deadline) {
            val mask = computeActiveTetherMask(ctx, runCatching { inspector.observeTethering().tetheredDownstreams }.getOrDefault(emptyList()))
            if ((mask and TETHER_MASK_USB) != 0) return true
            runCatching { Thread.sleep(POLL_INTERVAL_MS) }
        }
        return false
    }

    private fun stopMaskChannels(tm: Any, ctx: Context, mask: Int, hasNcm: Boolean) {
        if ((mask and TETHER_MASK_WIFI) != 0) {
            stopWifiTetheringInternal(tm, ctx)
        }
        if (LanSharingNetwork.USB_TETHER_ENABLED && (mask and TETHER_MASK_USB) != 0) {
            stopUsbTetheringInternal(tm, ctx, if (hasNcm) TETHERING_NCM else TETHERING_USB)
        }
    }

    private fun startMaskChannels(tm: Any, ctx: Context, mask: Int, hasNcm: Boolean): Boolean {
        var anyStarted = false
        if ((mask and TETHER_MASK_WIFI) != 0) {
            if (startWifiTetheringWithFallback(tm, ctx)) {
                anyStarted = true
            }
        }
        if (LanSharingNetwork.USB_TETHER_ENABLED && (mask and TETHER_MASK_USB) != 0) {
            val usbType = if (hasNcm) TETHERING_NCM else TETHERING_USB
            val usbOk = startUsbTetheringWithFallback(tm, ctx, usbType) ||
                (hasNcm && startUsbTetheringWithFallback(tm, ctx, TETHERING_USB))
            if (usbOk) anyStarted = true
        }
        return anyStarted
    }

    private fun waitForTetherMaskStopped(ctx: Context, targetMask: Int) {
        val deadline = System.currentTimeMillis() + STOP_SETTLE_MS
        while (System.currentTimeMillis() < deadline) {
            val downstreams = cachedTetheredIfaces
            val currentMask = computeActiveTetherMask(ctx, downstreams)
            val wifiState = if ((targetMask and TETHER_MASK_WIFI) != 0) queryWifiApState(ctx) else WIFI_AP_STATE_DISABLED
            if (isTetherMaskStopSettled(targetMask, currentMask, wifiState, downstreams)) {
                return
            }
            runCatching { Thread.sleep(POLL_INTERVAL_MS) }
        }
    }

    private fun awaitWifiHotspotSettled(
        ctx: Context,
        timeoutMs: Long = START_SETTLE_MS,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isWifiHotspotActiveInternal(ctx, waitIfEnabling = true)) {
                return true
            }
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) break
            runCatching { Thread.sleep(minOf(POLL_INTERVAL_MS, remaining)) }
        }
        return isWifiHotspotActiveInternal(ctx, waitIfEnabling = false)
    }

    private fun startViaTetheringRequest(tm: Any, tetheringType: Int, isExempt: Boolean): Boolean {
        return runCatching {
            val reqBuilderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
            val reqBuilderCtor: Constructor<*> = reqBuilderClass.getConstructor(Int::class.javaPrimitiveType)
            val reqBuilder = reqBuilderCtor.newInstance(tetheringType)

            if (isExempt) {
                runCatching {
                    reqBuilderClass
                        .getDeclaredMethod("setExemptFromEntitlementCheck", Boolean::class.javaPrimitiveType)
                        .invoke(reqBuilder, true)
                }
                runCatching {
                    reqBuilderClass
                        .getDeclaredMethod("setShouldShowEntitlementUi", Boolean::class.javaPrimitiveType)
                        .invoke(reqBuilder, false)
                }
            }

            val tetherRequest = reqBuilderClass.getMethod("build").invoke(reqBuilder)
                ?: return false

            val tetherLatch = CountDownLatch(1)
            val tetherStarted = AtomicBoolean(false)
            val tetherErrorCode = AtomicInteger(0)

            val startCbClass = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
            val callbackProxy = Proxy.newProxyInstance(
                startCbClass.classLoader,
                arrayOf(startCbClass),
            ) { _, method, mArgs ->
                when (method.name) {
                    "onTetheringStarted" -> {
                        tetherStarted.set(true)
                        tetherLatch.countDown()
                    }
                    "onTetheringFailed" -> {
                        val err = mArgs?.firstOrNull() as? Int ?: -1
                        tetherErrorCode.set(err)
                        tetherLatch.countDown()
                    }
                }
                null
            }

            val mainHandler = Handler(Looper.getMainLooper())
            val mainExecutor = Executor { command -> mainHandler.post(command) }
            val startTetheringMethod: Method = tm.javaClass.getMethod(
                "startTethering",
                Class.forName("android.net.TetheringManager\$TetheringRequest"),
                Executor::class.java,
                startCbClass,
            )

            startTetheringMethod.invoke(tm, tetherRequest, mainExecutor, callbackProxy)

            val completed = tetherLatch.await(START_TIMEOUT_SEC, TimeUnit.SECONDS)
            val ok = completed && tetherStarted.get()
            if (!ok) {
                val msg = "startViaTetheringRequest(type=$tetheringType, exempt=$isExempt) failed: completed=$completed, error=${tetherErrorCode.get()}"
                Log.w(TAG, msg)
                HotspotShareLogger.warn(TAG, msg)
            }
            ok
        }.getOrElse { t ->
            val msg = "startViaTetheringRequest(type=$tetheringType, exempt=$isExempt) threw: ${t.message}"
            Log.w(TAG, msg)
            HotspotShareLogger.warn(TAG, msg)
            false
        }
    }

    private fun hasAuthoritativeTetheredIfacesCallback(): Boolean =
        tetheringCallbackRegistered.get() && tetheringCallbackInitialIfacesReceived.get()

    private fun resolveCurrentCallbackUpstreamIface(ctx: Context): String? {
        val cached = cachedUpstreamIface
        if (!cached.isNullOrBlank()) return cached
        val net = cachedUpstreamNetwork ?: return null
        val cm = connectivityManager ?: (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
        val resolved = runCatching { cm?.getLinkProperties(net)?.interfaceName }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        if (resolved != null) {
            cachedUpstreamIface = resolved
        }
        return resolved
    }

    private fun isWifiHotspotActiveInternal(
        ctx: Context,
        waitIfEnabling: Boolean,
        downstreamsOverride: List<String>? = null,
    ): Boolean {
        val wifiState = queryWifiApState(ctx)
        if (wifiState == WIFI_AP_STATE_ENABLING) {
            if (!waitIfEnabling) return true
            val deadline = System.currentTimeMillis() + STOP_SETTLE_MS
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(POLL_INTERVAL_MS)
                val next = queryWifiApState(ctx)
                if (next == WIFI_AP_STATE_ENABLED) return true
                if (next != WIFI_AP_STATE_ENABLING) break
            }
        }
        if (wifiState == WIFI_AP_STATE_ENABLED || isWifiApEnabledReflective(ctx)) {
            return true
        }
        val downstreams = downstreamsOverride ?: if (hasAuthoritativeTetheredIfacesCallback()) {
            cachedTetheredIfaces
        } else {
            (cachedTetheredIfaces + queryPlatformTetheredIfaces(ctx)).distinct()
        }
        if (downstreams.any { isAnyTetherDownstreamName(it) }) {
            return true
        }
        return computeActiveTetherMask(ctx, downstreams) != 0
    }

    private fun computeActiveTetherMask(ctx: Context, extraDownstreams: List<String>): Int {
        val wifiState = queryWifiApState(ctx)
        val wifiApActive = wifiState == WIFI_AP_STATE_ENABLED ||
            wifiState == WIFI_AP_STATE_ENABLING ||
            isWifiApEnabledReflective(ctx)

        val downstreams = if (hasAuthoritativeTetheredIfacesCallback()) {
            if (extraDownstreams.isNotEmpty()) extraDownstreams.distinct() else cachedTetheredIfaces
        } else {
            (extraDownstreams + queryPlatformTetheredIfaces(ctx)).distinct()
        }

        val cm = connectivityManager ?: (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
        val knownIfaces = runCatching {
            cm?.allNetworks?.toList().orEmpty()
                .mapNotNull { network -> cm?.getLinkProperties(network)?.interfaceName }
                .map { it.lowercase() }
                .toSet()
        }.getOrDefault(emptySet())

        val unknownUpIfaces = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().mapNotNull { nif ->
                val isUp = runCatching { nif.isUp }.getOrDefault(false)
                val isLoopback = runCatching { nif.isLoopback }.getOrDefault(false)
                if (!isUp || isLoopback) return@mapNotNull null
                if (nif.name.lowercase() in knownIfaces) return@mapNotNull null
                nif.name
            }
        }.getOrDefault(emptyList())

        return computeActiveTetherMaskFromInputs(
            wifiApActive = wifiApActive,
            downstreams = downstreams,
            networkInterfaces = unknownUpIfaces,
        )
    }

    private fun hasActiveNcmDownstream(ctx: Context): Boolean {
        if (!LanSharingNetwork.USB_TETHER_ENABLED) return false
        if (cachedTetheredIfaces.any { it.trim().lowercase().startsWith("ncm") }) return true
        if (!hasAuthoritativeTetheredIfacesCallback() &&
            queryPlatformTetheredIfaces(ctx).any { it.trim().lowercase().startsWith("ncm") }
        ) {
            return true
        }
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .any { nif ->
                    runCatching { nif.isUp }.getOrDefault(false) &&
                        nif.name.trim().lowercase().startsWith("ncm")
                }
        }.getOrDefault(false)
    }

    private fun queryPlatformTetheredIfaces(ctx: Context): List<String> {
        val result = linkedSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val tm = tetheringManager ?: ctx.getSystemService("tethering")
                val method = tm?.javaClass?.getMethod("getTetheredIfaces")
                (method?.invoke(tm) as? Array<*>)?.filterIsInstance<String>()?.forEach { result += it }
            }
        }
        val cm = connectivityManager ?: (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
        result += LanSharingNetwork.getTetheredIfaces(cm)
        return result.toList()
    }

    private fun runShellWithResult(cmd: List<String>): Pair<Int, String> {
        return runCatching {
            val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val out = process.inputStream.bufferedReader().use { it.readText() }
            val completed = process.waitFor(SHELL_CMD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                return@runCatching -1 to "$out (timed out)"
            }
            process.exitValue() to out
        }.getOrElse { t -> -1 to (t.message ?: "error") }
    }

    private fun queryWifiApState(ctx: Context): Int {
        return runCatching {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) ?: return -1
            wm.javaClass.getMethod("getWifiApState").invoke(wm) as? Int ?: -1
        }.getOrDefault(-1)
    }

    private fun isWifiApEnabledReflective(ctx: Context): Boolean {
        return runCatching {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) ?: return false
            wm.javaClass.getMethod("isWifiApEnabled").invoke(wm) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun startViaWifiManager(ctx: Context): Boolean {
        return runCatching {
            val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) ?: return false
            val configClass = Class.forName("android.net.wifi.SoftApConfiguration")
            val method = wifiManager.javaClass.getMethod("startTetheredHotspot", configClass)
            val accepted = method.invoke(wifiManager, null) as? Boolean ?: false
            Log.i(TAG, "WifiManager.startTetheredHotspot(null) = $accepted")
            accepted
        }.getOrElse { t ->
            Log.w(TAG, "startViaWifiManager threw: ${t.message}")
            false
        }
    }

    override fun verifyUpstream(timeoutMs: Long): Boolean {
        val expected = activeInterfaceName ?: return false
        val ctx = runCatching { context }.getOrNull()
        val deadline = System.currentTimeMillis() + timeoutMs

        if (tetheringCallbackRegistered.get() && ctx != null) {
            while (true) {
                val cbIface = resolveCurrentCallbackUpstreamIface(ctx)
                if (tetheringCallbackInitialUpstreamReceived.get() && cbIface == expected) {
                    Log.i(TAG, "Upstream verified via TetheringEventCallback: $expected")
                    HotspotShareLogger.info(TAG, "Upstream verified via TetheringEventCallback: $expected")
                    return true
                }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) break
                synchronized(upstreamWaitLock) {
                    val refreshed = resolveCurrentCallbackUpstreamIface(ctx)
                    if (tetheringCallbackInitialUpstreamReceived.get() && refreshed == expected) {
                        Log.i(TAG, "Upstream verified via TetheringEventCallback: $expected")
                        HotspotShareLogger.info(TAG, "Upstream verified via TetheringEventCallback: $expected")
                        return true
                    }
                    runCatching {
                        upstreamWaitLock.wait(minOf(remaining, UPSTREAM_CALLBACK_POLL_MS))
                    }
                }
            }
            if (!tetheringCallbackInitialUpstreamReceived.get()) {
                val fallbackLive = runCatching {
                    inspector.observeTethering().liveInterfaceNames(expected)
                }.getOrDefault(emptyList())
                if (fallbackLive.isNotEmpty() && fallbackLive.all { it == expected }) {
                    Log.i(TAG, "Upstream verified via dumpsys fallback: $expected is sole upstream")
                    HotspotShareLogger.info(TAG, "Upstream verified via dumpsys fallback: $expected is sole upstream")
                    return true
                }
                Log.w(TAG, "verifyUpstream timed out after ${timeoutMs}ms: expected=$expected, observed=$fallbackLive")
                HotspotShareLogger.warn(TAG, "verifyUpstream timed out after ${timeoutMs}ms: expected=$expected, observed=$fallbackLive")
                return false
            }
            val observed = resolveCurrentCallbackUpstreamIface(ctx)
            Log.w(TAG, "verifyUpstream timed out after ${timeoutMs}ms: expected=$expected, observed=${observed ?: "none"}")
            HotspotShareLogger.warn(TAG, "verifyUpstream timed out after ${timeoutMs}ms: expected=$expected, observed=${observed ?: "none"}")
            return false
        }

        while (System.currentTimeMillis() < deadline) {
            val obs = runCatching { inspector.observeTethering() }.getOrNull()
            val live = obs?.liveInterfaceNames(expected).orEmpty()
            if (live.isNotEmpty() && live.all { it == expected }) {
                Log.i(TAG, "Upstream verified: $expected is sole upstream")
                HotspotShareLogger.info(TAG, "Upstream verified: $expected is sole upstream")
                return true
            }
            Thread.sleep(UPSTREAM_POLL_MS)
        }
        val finalObs = runCatching { inspector.observeTethering().liveInterfaceNames(expected) }.getOrDefault(emptyList())
        Log.w(TAG, "verifyUpstream timed out after ${timeoutMs}ms: expected=$expected, observed=$finalObs")
        HotspotShareLogger.warn(TAG, "verifyUpstream timed out after ${timeoutMs}ms: expected=$expected, observed=$finalObs")
        return false
    }

    override fun checkUpstreamHealth(): String {
        val expected = activeInterfaceName ?: return "ABSENT"
        val ctx = runCatching { context }.getOrNull()
        val obs = if (ctx != null &&
            tetheringCallbackRegistered.get() &&
            tetheringCallbackInitialUpstreamReceived.get()
        ) {
            val upstream = resolveCurrentCallbackUpstreamIface(ctx)
            val downstreams = if (hasAuthoritativeTetheredIfacesCallback()) {
                cachedTetheredIfaces
            } else {
                (cachedTetheredIfaces + queryPlatformTetheredIfaces(ctx)).distinct()
            }
            buildCallbackUpstreamObservation(upstream, downstreams)
        } else {
            runCatching { inspector.observeTethering() }.getOrElse {
                HotspotShareLogger.warn(TAG, "checkUpstreamHealth observeTethering failed: ${it.message}")
                return "TIMED_OUT"
            }
        }
        val health = UpstreamInspector.evaluateUpstreamHealth(obs, expected) {
            isWifiHotspotActiveInternal(
                context,
                waitIfEnabling = false,
                downstreamsOverride = obs.tetheredDownstreams,
            )
        }
        if (health != "HEALTHY") {
            HotspotShareLogger.warn(TAG, "checkUpstreamHealth($expected) = $health")
        }
        return health
    }

    override fun getConnectedClientCount(): Int {
        runCatching { context }
        return resolveConnectedClientCount(
            softApRegistered = softApCallbackRegistered.get(),
            softApCount = getCachedSoftApClientCount(),
            tetherRegistered = tetheringCallbackRegistered.get(),
            tetherCount = cachedTetherWifiClientCount.get(),
            fallback = { runCatching { inspector.observeWifiClientCountCached() }.getOrDefault(0) },
        )
    }

    override fun getNeighborDump(): String {
        runCatching { context }
        val hostLines = formatCachedClientHostLines(cachedTetherClients)
        return runCatching { inspector.observeNeighbors(hostLines) }.getOrDefault("")
    }

    override fun checkCompatibility(): String {
        val ctx = runCatching { context }.getOrNull()
        val testNetPresent = testNetworkManager != null
        val preferPresent = runCatching {
            Class.forName("android.net.TetheringManager")
                .getMethod("setPreferTestNetworks", Boolean::class.javaPrimitiveType) != null
        }.getOrDefault(false)

        return JSONObject().apply {
            put("shellPackage", ctx?.opPackageName ?: "unknown")
            put("testNetworkAvailable", testNetPresent)
            put("preferTestNetworksAvailable", preferPresent)
        }.toString()
    }

    override fun getDiagnosticLogs(): List<String> {
        HotspotShareLogger.markIsolatedServiceProcess()
        return runCatching {
            val merged = HotspotShareLogger.readMergedLogs()
            if (merged.isBlank()) {
                HotspotShareLogger.getInMemoryLines()
            } else {
                merged.lines().filter { it.isNotBlank() }
            }
        }.getOrElse {
            HotspotShareLogger.getInMemoryLines()
        }
    }

    override fun clearDiagnosticLogs() {
        HotspotShareLogger.markIsolatedServiceProcess()
        runCatching { HotspotShareLogger.clearAll() }
    }

    override fun setDiagnosticLoggingEnabled(enabled: Boolean) {
        HotspotShareLogger.markIsolatedServiceProcess()
        val wasEnabled = HotspotShareLogger.isLoggingEnabled()
        runCatching { HotspotShareLogger.setLoggingEnabled(enabled) }
        if (enabled && !wasEnabled) {
            HotspotShareLogger.info(
                TAG,
                "Diagnostic logging enabled in ShareUserService (uid=${android.os.Process.myUid()}, iface=${activeInterfaceName ?: "none"})",
            )
        }
    }

    @Synchronized
    override fun stopTethering() {
        HotspotShareLogger.info(TAG, "stopTethering invoked")
        val tm = tetheringManager ?: run {
            runCatching { context }
            tetheringManager ?: return
        }
        stopTetheringWithRetry(tm)
    }

    private fun invokeStopTethering(tm: Any, type: Int): Boolean {
        return runCatching {
            val stopMethod = tm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
            stopMethod.invoke(tm, type)
            true
        }.onFailure { t ->
            Log.w(TAG, "TetheringManager.stopTethering($type) failed: ${t.message}")
            HotspotShareLogger.warn(TAG, "stopTethering($type) failed: ${t.message}")
        }.getOrDefault(false)
    }

    private fun stopWifiTetheringInternal(tm: Any, ctx: Context): Boolean {
        if (invokeStopTethering(tm, TETHERING_WIFI)) {
            return true
        }
        return runCatching {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) ?: return@runCatching false
            wm.javaClass.getMethod("stopSoftAp").invoke(wm) as? Boolean ?: true
        }.getOrDefault(false)
    }

    private fun stopUsbTetheringInternal(tm: Any, ctx: Context, requestedType: Int): Boolean {
        val stoppedRequested = invokeStopTethering(tm, requestedType)
        val stoppedAlt = if (requestedType == TETHERING_NCM) {
            invokeStopTethering(tm, TETHERING_USB)
        } else if (hasActiveNcmDownstream(ctx)) {
            invokeStopTethering(tm, TETHERING_NCM)
        } else {
            false
        }
        val cm = connectivityManager ?: (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
        val cmStopped = if (cm != null) {
            runCatching {
                (cm.javaClass.getMethod("setUsbTethering", Boolean::class.javaPrimitiveType)
                    .invoke(cm, false) as? Int) == 0
            }.getOrDefault(false)
        } else {
            false
        }
        if ((computeActiveTetherMask(ctx, runCatching { inspector.observeTethering().tetheredDownstreams }.getOrDefault(emptyList())) and TETHER_MASK_USB) != 0) {
            val proto = if (requestedType == TETHERING_NCM) "ncm" else "usb"
            runShellWithResult(listOf("cmd", "connectivity", "stop-tethering", proto))
            runShellWithResult(listOf("cmd", "tethering", "stop-tethering", proto))
        }
        waitForTetherMaskStopped(ctx, TETHER_MASK_USB)
        return stoppedRequested || stoppedAlt || cmStopped
    }

    private fun stopTetheringInternal(tm: Any, ctx: Context, mask: Int): Boolean {
        val hasNcm = hasActiveNcmDownstream(ctx)
        var any = false
        if ((mask and TETHER_MASK_WIFI) != 0 || mask == 0) {
            if (stopWifiTetheringInternal(tm, ctx)) any = true
        }
        if (LanSharingNetwork.USB_TETHER_ENABLED && (mask and TETHER_MASK_USB) != 0) {
            if (stopUsbTetheringInternal(tm, ctx, if (hasNcm) TETHERING_NCM else TETHERING_USB)) any = true
        }
        return any
    }

    private fun stopTetheringWithRetry(tm: Any) {
        val ctx = context
        val initialMask = getActiveTetherMask()
        val targetMask = if (initialMask != 0) initialMask else TETHER_MASK_WIFI
        for (attempt in 1..DOWNSTREAM_STOP_ATTEMPTS) {
            stopTetheringInternal(tm, ctx, targetMask)
            val obs = runCatching { inspector.observeTethering() }.getOrNull()
            val downstreams = obs?.tetheredDownstreams.orEmpty()
            val hasAnyDownstream = downstreams.any { isAnyTetherDownstreamName(it) }
            val currentMask = computeActiveTetherMask(ctx, downstreams)
            if (!hasAnyDownstream && (currentMask and targetMask) == 0 && !isWifiApEnabledReflective(ctx)) {
                return
            }
            if (attempt < DOWNSTREAM_STOP_ATTEMPTS) {
                Thread.sleep(STOP_RETRY_DELAY_MS)
            }
        }
    }

    private fun releaseUpstreamSelection(ifaceName: String?) {
        runCatching { setPreferTestNetworks(false) }
        val name = ifaceName ?: return
        val deadline = System.currentTimeMillis() + UPSTREAM_RELEASE_MS
        while (System.currentTimeMillis() < deadline) {
            val observed = runCatching { inspector.observeTethering().interfaceNames }.getOrDefault(emptyList())
            if (observed.none { it == name }) {
                return
            }
            Thread.sleep(UPSTREAM_POLL_MS)
        }
    }

    private fun installShutdownHook() {
        removeShutdownHook()
        if (usesExternalLifeBinder) {
            // App-held sessionLifeBinder owns testtunN lifecycle across Shizuku restarts;
            // clearing preferTestNetworks on transient SIGTERM would flip tethering back to rmnet mid-recovery.
            return
        }
        val hook = Thread {
            runCatching {
                setPreferTestNetworks(false)
            }
        }
        runCatching { Runtime.getRuntime().addShutdownHook(hook) }
            .onSuccess { shutdownHook = hook }
    }

    private fun removeShutdownHook() {
        shutdownHook?.let { hook ->
            runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
        }
        shutdownHook = null
    }

    @Synchronized
    override fun cleanupDirtyState() {
        val ownerPkg = resolveOwnerPackageName(baseContext)
        HotspotShareLogger.info(TAG, "cleanupDirtyState (iface=$activeInterfaceName, pkg=$ownerPkg)")
        removeShutdownHook()

        // 1. Clear preferTestNetworks(false) and wait for upstream to move off testtunN
        //    (Hotspot itself is NEVER stopped here — only explicit user OFF stops SoftAp.)
        val oldIface = activeInterfaceName
        releaseUpstreamSelection(oldIface)

        // 2. Unregister keep-alive NetworkCallback
        val cm = connectivityManager
            ?: runCatching { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }.getOrNull()
        keepAliveCallback?.let { cb ->
            runCatching { cm?.unregisterNetworkCallback(cb) }
        }
        keepAliveCallback = null

        // 3. Teardown TestNetwork in TestNetworkManager
        val tnm = testNetworkManager ?: run {
            runCatching { context }
            testNetworkManager
        }
        val targetNetwork = acquiredNetwork ?: if (oldIface != null && cm != null) {
            runCatching {
                cm.allNetworks.firstOrNull { net ->
                    cm.getLinkProperties(net)?.interfaceName == oldIface
                }
            }.getOrNull()
        } else {
            null
        }
        targetNetwork?.let { net ->
            runCatching {
                val teardownMethod = tnm?.javaClass?.getMethod("teardownTestNetwork", Network::class.java)
                teardownMethod?.invoke(tnm, net)
            }
        }
        if (tnm != null && cm != null) {
            cleanupOrphanedTestNetworks(tnm, cm, ownerPkg)
        }
        clearOwnedTestTunInterface(ownerPkg)
        acquiredNetwork = null

        // 4. Close TUN fd
        runCatching { tunPfd?.close() }
        tunPfd = null
        testTunInterface = null
        activeInterfaceName = null
        testNetworkBinder = null
        usesExternalLifeBinder = false
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == TRANSACTION_SHIZUKU_DESTROY) {
            destroy()
            if ((flags and IBinder.FLAG_ONEWAY) == 0) {
                reply?.writeNoException()
            }
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    @Synchronized
    override fun destroy() {
        if (!destroying.compareAndSet(false, true)) return
        HotspotShareLogger.info(TAG, "destroy() called; disabling keep-alive and cleaning up")
        runCatching { ShizukuWatchdog.disableKeepAlive() }
        runCatching { unregisterPlatformCallbacks() }
        runCatching { callbackExecutor.shutdownNow() }
        if (shouldCleanupOnDestroy(activeInterfaceName, keepAliveCallback, acquiredNetwork)) {
            cleanupDirtyState()
        } else {
            removeShutdownHook()
            HotspotShareLogger.info(TAG, "destroy() skipping redundant cleanupDirtyState (already clean)")
        }
        // Exit asynchronously after Binder IPC returns so Shizuku unbind doesn't hit
        // DeadObjectException and no stale :hotspot_share process remains alive.
        Thread {
            runCatching { Thread.sleep(80L) }
            Runtime.getRuntime().halt(0)
        }.apply {
            isDaemon = true
            start()
        }
    }

    internal data class CachedTetherClient(
        val ip: String,
        val hostname: String?,
        val mac: String?,
        val tetheringType: Int,
    )

    companion object {
        private const val TAG = "ShareUserService"
        const val CONTRACT_VERSION = 13

        /** Matches rikka.shizuku.ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy. */
        private const val TRANSACTION_SHIZUKU_DESTROY = 16777115

        const val TUN_IPV4_ADDRESS = "192.0.2.2"
        const val TUN_IPV4_PREFIX = 24
        const val TUN_IPV6_ADDRESS = "fd00:2:0:2::2"
        const val TUN_IPV6_PREFIX = 64

        const val TETHERING_WIFI = 0
        const val TETHERING_USB = 1
        private const val TETHERING_BLUETOOTH_TYPE = 2
        const val TETHERING_NCM = 4

        const val TETHER_MASK_WIFI = 1
        const val TETHER_MASK_USB = 2

        internal const val WIFI_AP_STATE_DISABLING = 10
        internal const val WIFI_AP_STATE_DISABLED = 11
        internal const val WIFI_AP_STATE_ENABLING = 12
        internal const val WIFI_AP_STATE_ENABLED = 13
        internal const val WIFI_AP_STATE_FAILED = 14
        private const val TRANSPORT_TEST_FALLBACK = 7

        private const val AVAILABILITY_TIMEOUT_MS = 10_000L
        private const val POLL_INTERVAL_MS = 200L
        private const val STOP_SETTLE_MS = 5_000L
        private const val START_SETTLE_MS = 3_000L
        private const val RESTART_RETRY_DELAY_MS = 500L
        private const val STOP_RETRY_DELAY_MS = 1_000L
        private const val DOWNSTREAM_STOP_ATTEMPTS = 3
        private const val START_TIMEOUT_SEC = 15L
        private const val UPSTREAM_POLL_MS = 500L
        private const val UPSTREAM_CALLBACK_POLL_MS = 250L
        private const val UPSTREAM_RELEASE_MS = 4_000L
        private const val USB_RESET_SETTLE_MS = 450L
        private const val USB_FALLBACK_SETTLE_MS = 2_500L
        private const val SHELL_CMD_TIMEOUT_MS = 3_000L
        private const val OWNED_TESTTUN_MARKER_DIR = "/data/local/tmp"
        private const val OWNED_TESTTUN_MARKER_PREFIX = "v4war_owned_testtun_"

        internal fun sanitizePackageForMarker(pkg: String?): String {
            val raw = pkg?.trim().orEmpty()
            if (raw.isEmpty()) return "default"
            val sanitized = buildString(raw.length) {
                for (ch in raw) {
                    if (ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-') {
                        append(ch)
                    } else {
                        append('_')
                    }
                }
            }.trim('.')
            return sanitized.ifEmpty { "default" }
        }

        internal fun ownedTestTunMarkerFile(
            pkg: String?,
            markerDir: File = File(OWNED_TESTTUN_MARKER_DIR),
        ): File = File(markerDir, "$OWNED_TESTTUN_MARKER_PREFIX${sanitizePackageForMarker(pkg)}")

        internal fun persistOwnedTestTunInterface(
            pkg: String?,
            ifaceName: String?,
            markerDir: File = File(OWNED_TESTTUN_MARKER_DIR),
        ) {
            val cleanIface = ifaceName?.trim()?.takeIf { it.startsWith("testtun") } ?: return
            val file = ownedTestTunMarkerFile(pkg, markerDir)
            runCatching {
                file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
                file.writeText("$cleanIface\n")
                runCatching { file.setReadable(true, false) }
            }
        }

        internal fun readOwnedTestTunInterface(
            pkg: String?,
            markerDir: File = File(OWNED_TESTTUN_MARKER_DIR),
        ): String? {
            val file = ownedTestTunMarkerFile(pkg, markerDir)
            return runCatching {
                if (!file.exists()) return@runCatching null
                file.readText().trim().takeIf { it.startsWith("testtun") }
            }.getOrNull()
        }

        internal fun clearOwnedTestTunInterface(
            pkg: String?,
            markerDir: File = File(OWNED_TESTTUN_MARKER_DIR),
        ) {
            val file = ownedTestTunMarkerFile(pkg, markerDir)
            runCatching {
                if (file.exists()) file.delete()
            }
        }

        internal fun selectOwnedOrphanedTestInterfaces(
            networkIfaces: Collection<String>,
            activeIface: String?,
            persistedOwnedIface: String?,
        ): Set<String> {
            val owned = LinkedHashSet<String>()
            activeIface?.trim()?.takeIf { it.startsWith("testtun") }?.let { owned.add(it) }
            persistedOwnedIface?.trim()?.takeIf { it.startsWith("testtun") }?.let { owned.add(it) }
            if (owned.isEmpty()) return emptySet()
            return networkIfaces.filterTo(LinkedHashSet()) { iface ->
                iface.startsWith("testtun") && iface in owned
            }
        }

        internal fun shouldCleanupOnDestroy(
            activeIface: String?,
            keepAlive: Any?,
            network: Any?,
        ): Boolean = activeIface != null || keepAlive != null || network != null

        internal fun buildCallbackUpstreamObservation(
            upstreamIface: String?,
            tetheredDownstreams: List<String>,
        ): UpstreamObservation = UpstreamObservation(
            rawOutput = "",
            interfaceNames = if (upstreamIface.isNullOrBlank()) emptyList() else listOf(upstreamIface),
            tetheredDownstreams = tetheredDownstreams,
            didTimeout = false,
        )

        internal fun isTetherMaskStopSettled(
            targetMask: Int,
            currentMask: Int,
            wifiApState: Int,
            cachedDownstreams: List<String>,
            usbEnabled: Boolean = LanSharingNetwork.USB_TETHER_ENABLED,
        ): Boolean {
            if ((currentMask and targetMask) != 0) return false
            if ((targetMask and TETHER_MASK_WIFI) != 0) {
                val wifiStopped = wifiApState == WIFI_AP_STATE_DISABLED ||
                    wifiApState == WIFI_AP_STATE_FAILED ||
                    wifiApState == -1
                if (!wifiStopped) return false
                if (cachedDownstreams.any { isWifiDownstreamName(it) }) return false
            }
            if (usbEnabled && (targetMask and TETHER_MASK_USB) != 0) {
                if (cachedDownstreams.any { isUsbDownstreamName(it) }) return false
            }
            return true
        }

        internal fun isWifiDownstreamName(iface: String): Boolean {
            val lower = iface.trim().lowercase()
            return lower.startsWith("wlan") ||
                LanSharingNetwork.getTetherKindForInterface(iface) == TetherKind.WIFI
        }

        internal fun isUsbDownstreamName(iface: String): Boolean =
            LanSharingNetwork.getTetherKindForInterface(iface) == TetherKind.USB

        internal fun isAnyTetherDownstreamName(iface: String): Boolean =
            isWifiDownstreamName(iface) ||
                (LanSharingNetwork.USB_TETHER_ENABLED && isUsbDownstreamName(iface))

        internal fun extractTetheredIfaceNames(raw: Collection<*>): List<String> =
            raw.mapNotNull { item ->
                when (item) {
                    is String -> item.takeIf { it.isNotBlank() }
                    null -> null
                    else -> runCatching {
                        item.javaClass.getMethod("getInterface").invoke(item) as? String
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                }
            }.distinct()

        internal fun formatCachedClientHostLines(clients: List<CachedTetherClient>): List<String> =
            clients.mapNotNull { client ->
                val host = client.hostname?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val ip = client.ip.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                "AddressInfo {address $ip/24, hostname $host}"
            }.distinct()

        internal fun resolveConnectedClientCount(
            softApRegistered: Boolean,
            softApCount: Int,
            tetherRegistered: Boolean,
            tetherCount: Int,
            fallback: () -> Int,
        ): Int {
            if (softApRegistered || tetherRegistered) {
                return maxOf(
                    if (softApRegistered) softApCount.coerceAtLeast(0) else 0,
                    if (tetherRegistered) tetherCount.coerceAtLeast(0) else 0,
                )
            }
            return fallback().coerceAtLeast(0)
        }

        internal fun computeActiveTetherMaskFromInputs(
            wifiApActive: Boolean,
            downstreams: List<String>,
            networkInterfaces: List<String>,
            usbEnabled: Boolean = LanSharingNetwork.USB_TETHER_ENABLED,
        ): Int {
            var mask = 0
            if (wifiApActive) {
                mask = mask or TETHER_MASK_WIFI
            }
            for (iface in downstreams) {
                mask = mask or when {
                    isWifiDownstreamName(iface) -> TETHER_MASK_WIFI
                    usbEnabled && isUsbDownstreamName(iface) -> TETHER_MASK_USB
                    else -> 0
                }
            }
            for (iface in networkInterfaces) {
                mask = mask or when (LanSharingNetwork.getTetherKindForInterface(iface)) {
                    TetherKind.WIFI -> TETHER_MASK_WIFI
                    TetherKind.USB -> if (usbEnabled) TETHER_MASK_USB else 0
                    TetherKind.BLUETOOTH,
                    TetherKind.OTHER -> 0
                }
            }
            return mask
        }
    }
}
