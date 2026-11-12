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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shizuku UserService implementation executing in a dedicated shell process (UID 2000).
 * Provides privileged network management (TestNetworkManager, TetheringManager) with
 * full lifecycle protections adapted from carlelieser/shizzi:
 * - Dual-stack TUN (192.0.2.2/24 + 2001:db8::2/64) avoiding sing-box FakeIP collision
 * - setupTestNetwork with isUp = true and IPv4 + IPv6 DNS servers via addDnsServer
 * - Network availability wait + ConnectivityManager.requestNetwork(TRANSPORT_TEST) keep-alive
 * - Stale preferTestNetworks(false) -> (true) reset
 * - Existing hotspot stop + settle before start so upstream selection re-runs
 * - Multi-fallback startTethering (exempt=true -> exempt=false -> WifiManager.startTetheredHotspot)
 * - Upstream verification & drift inspection via dumpsys tethering
 * - Ordered teardown with retry verification, teardownTestNetwork(network), and JVM shutdown hook
 */
class ShareUserService : IShareUserService.Stub {

    private val baseContext: Context?

    @Suppress("unused")
    constructor() {
        this.baseContext = null
        proactiveInitCleanup()
    }

    @Suppress("unused")
    constructor(context: Context) {
        this.baseContext = context
        proactiveInitCleanup()
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
    private var activeInterfaceName: String? = null
    private var tunPfd: ParcelFileDescriptor? = null
    private var testNetworkBinder: IBinder? = null
    private var acquiredNetwork: Network? = null
    private var keepAliveCallback: ConnectivityManager.NetworkCallback? = null
    private var shutdownHook: Thread? = null

    private val inspector = UpstreamInspector()

    private fun proactiveInitCleanup() {
        try {
            cleanupDirtyState()
        } catch (_: Throwable) {
        }
    }

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
    }

    override fun getContractVersion(): Int = CONTRACT_VERSION

    @Synchronized
    override fun getInterfaceName(): String? = activeInterfaceName

    @Synchronized
    override fun createTestNetwork(dnsServers: List<String>?): ParcelFileDescriptor {
        val ctx = context
        val tnm = testNetworkManager ?: throw IllegalStateException("TestNetworkManager is not ready")
        val cm = connectivityManager ?: (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)

        // Build dual-stack LinkAddresses (RFC 5737 192.0.2.2/24 + RFC 3849 2001:db8::2/64)
        // Avoids colliding with sing-box FakeIP 198.18.0.0/15 and enables IPv6 downstream provisioning
        val addresses = listOf(
            buildLinkAddress(InetAddress.getByName(TUN_IPV4_ADDRESS), TUN_IPV4_PREFIX),
            buildLinkAddress(InetAddress.getByName(TUN_IPV6_ADDRESS), TUN_IPV6_PREFIX),
        )

        val ifaceObj = invokeCreateTunInterface(tnm, addresses)
            ?: throw IllegalStateException("createTunInterface returned null for $addresses")

        testTunInterface = ifaceObj
        val ifaceName = ifaceObj.javaClass.getMethod("getInterfaceName").invoke(ifaceObj) as String
        val pfd = ifaceObj.javaClass.getMethod("getFileDescriptor").invoke(ifaceObj) as ParcelFileDescriptor
        activeInterfaceName = ifaceName
        tunPfd = pfd

        // Build LinkProperties with interfaceName, linkAddresses, and IPv6 + IPv4 DNS servers
        val dnsInetList = buildDnsList(dnsServers)
        val lp = LinkProperties().apply {
            runCatching {
                LinkProperties::class.java.getMethod("setInterfaceName", String::class.java)
                    .invoke(this, ifaceName)
            }
            runCatching {
                LinkProperties::class.java.getMethod("setLinkAddresses", Collection::class.java)
                    .invoke(this, addresses)
            }
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

        val binder = Binder()
        testNetworkBinder = binder

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

        // CRITICAL FIX (from shizzi): Await network registration in ConnectivityManager and
        // hold a TRANSPORT_TEST NetworkRequest keep-alive callback so ConnectivityService
        // does not linger and tear down testtunN after a few seconds!
        acquiredNetwork = awaitNetworkAvailability(cm, ifaceName, AVAILABILITY_TIMEOUT_MS)
        requestKeepAlive(cm)

        Log.i(TAG, "Test network up: $ifaceName (network=$acquiredNetwork)")
        return pfd.dup()
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

    private fun buildDnsList(dnsServers: List<String>?): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        // Always include IPv6 DNS so Android tethering provisions IPv6 downstream (shizzi R2)
        runCatching { result.add(InetAddress.getByName(DEFAULT_IPV6_DNS)) }
        if (!dnsServers.isNullOrEmpty()) {
            for (dns in dnsServers) {
                runCatching {
                    val addr = InetAddress.getByName(dns)
                    if (addr !in result) result.add(addr)
                }
            }
        }
        if (result.none { it.address.size == 4 }) {
            runCatching { result.add(InetAddress.getByName("1.1.1.1")) }
            runCatching { result.add(InetAddress.getByName("8.8.8.8")) }
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
    }

    @Synchronized
    override fun startTethering(): Boolean {
        val ctx = context
        val tm = tetheringManager ?: throw IllegalStateException("TetheringManager is not ready")

        // If Wi-Fi hotspot is already tethered, stop and wait for it to settle so
        // TetheringManager re-runs upstream selection onto testtunN (from shizzi R4.4)
        val existingDownstreams = runCatching { inspector.observeTethering().tetheredDownstreams }
            .getOrDefault(emptyList())
        if (existingDownstreams.isNotEmpty()) {
            Log.i(TAG, "Stopping already-active downstream ($existingDownstreams) to re-run upstream selection")
            stopTetheringInternal(tm)
            Thread.sleep(STOP_SETTLE_MS)
        }

        // 1. Try TetheringManager.startTethering with setExemptFromEntitlementCheck(true)
        if (startViaTetheringRequest(tm, isExempt = true)) {
            installShutdownHook()
            return true
        }

        // 2. Fallback: TetheringManager.startTethering with setExemptFromEntitlementCheck(false)
        if (startViaTetheringRequest(tm, isExempt = false)) {
            installShutdownHook()
            return true
        }

        // 3. Fallback: WifiManager.startTetheredHotspot(null) (reaches SoftAp via WifiServiceImpl)
        if (startViaWifiManager(ctx)) {
            installShutdownHook()
            return true
        }

        return false
    }

    private fun startViaTetheringRequest(tm: Any, isExempt: Boolean): Boolean {
        return runCatching {
            val reqBuilderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
            val reqBuilderCtor: Constructor<*> = reqBuilderClass.getConstructor(Int::class.javaPrimitiveType)
            val reqBuilder = reqBuilderCtor.newInstance(TETHERING_WIFI)

            if (isExempt) {
                runCatching {
                    reqBuilderClass
                        .getDeclaredMethod("setExemptFromEntitlementCheck", Boolean::class.javaPrimitiveType)
                        .invoke(reqBuilder, true)
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
                Log.w(TAG, "startViaTetheringRequest(exempt=$isExempt) failed: completed=$completed, error=${tetherErrorCode.get()}")
            }
            ok
        }.getOrElse { t ->
            Log.w(TAG, "startViaTetheringRequest(exempt=$isExempt) threw: ${t.message}")
            false
        }
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

    @Synchronized
    override fun verifyUpstream(timeoutMs: Long): Boolean {
        val expected = activeInterfaceName ?: return false
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val obs = runCatching { inspector.observeTethering() }.getOrNull()
            val live = obs?.liveInterfaceNames(expected).orEmpty()
            if (live.isNotEmpty() && live.all { it == expected }) {
                Log.i(TAG, "Upstream verified: $expected is sole upstream")
                return true
            }
            Thread.sleep(UPSTREAM_POLL_MS)
        }
        val finalObs = runCatching { inspector.observeTethering().liveInterfaceNames(expected) }.getOrDefault(emptyList())
        Log.w(TAG, "verifyUpstream timed out after ${timeoutMs}ms: expected=$expected, observed=$finalObs")
        return false
    }

    @Synchronized
    override fun checkUpstreamHealth(): String {
        val expected = activeInterfaceName ?: return "ABSENT"
        val obs = runCatching { inspector.observeTethering() }.getOrElse {
            return "TIMED_OUT"
        }
        val live = obs.liveInterfaceNames(expected)
        return when (classifyUpstream(live, expected, obs.didTimeout)) {
            UpstreamReading.HEALTHY -> "HEALTHY"
            UpstreamReading.ABSENT -> "ABSENT"
            UpstreamReading.TIMED_OUT -> "TIMED_OUT"
            UpstreamReading.DRIFTED -> "DRIFTED:${live.joinToString(",")}"
        }
    }

    override fun getConnectedClientCount(): Int {
        return runCatching { inspector.observeWifiClientCount() }.getOrDefault(0)
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

    @Synchronized
    override fun stopTethering() {
        val tm = tetheringManager ?: return
        stopTetheringWithRetry(tm)
    }

    private fun stopTetheringInternal(tm: Any): Boolean {
        return runCatching {
            val stopMethod = tm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
            stopMethod.invoke(tm, TETHERING_WIFI)
            true
        }.getOrDefault(false)
    }

    private fun stopTetheringWithRetry(tm: Any) {
        for (attempt in 1..DOWNSTREAM_STOP_ATTEMPTS) {
            stopTetheringInternal(tm)
            val obs = runCatching { inspector.observeTethering() }.getOrNull()
            if (obs == null || obs.tetheredDownstreams.isEmpty()) {
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
        val hook = Thread {
            runCatching {
                tetheringManager?.let { stopTetheringInternal(it) }
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
        removeShutdownHook()

        // 1. Stop downstream hotspot with verification retries
        tetheringManager?.let { stopTetheringWithRetry(it) }

        // 2. Clear preferTestNetworks(false) and wait for upstream to move off testtunN
        val oldIface = activeInterfaceName
        releaseUpstreamSelection(oldIface)

        // 3. Unregister keep-alive NetworkCallback
        val cm = connectivityManager
        keepAliveCallback?.let { cb ->
            runCatching { cm?.unregisterNetworkCallback(cb) }
        }
        keepAliveCallback = null

        // 4. Teardown TestNetwork in TestNetworkManager
        val tnm = testNetworkManager
        acquiredNetwork?.let { net ->
            runCatching {
                val teardownMethod = tnm?.javaClass?.getMethod("teardownTestNetwork", Network::class.java)
                teardownMethod?.invoke(tnm, net)
            }
        }
        acquiredNetwork = null

        // 5. Close TUN fd
        runCatching { tunPfd?.close() }
        tunPfd = null
        testTunInterface = null
        activeInterfaceName = null
        testNetworkBinder = null
    }

    @Synchronized
    override fun destroy() {
        cleanupDirtyState()
        System.exit(0)
    }

    companion object {
        private const val TAG = "ShareUserService"
        const val CONTRACT_VERSION = 2

        const val TUN_IPV4_ADDRESS = "192.0.2.2"
        const val TUN_IPV4_PREFIX = 24
        const val TUN_IPV6_ADDRESS = "2001:db8::2"
        const val TUN_IPV6_PREFIX = 64
        const val DEFAULT_IPV6_DNS = "2001:4860:4860::8888"

        private const val TETHERING_WIFI = 0
        private const val TRANSPORT_TEST_FALLBACK = 7

        private const val AVAILABILITY_TIMEOUT_MS = 10_000L
        private const val POLL_INTERVAL_MS = 200L
        private const val STOP_SETTLE_MS = 3_000L
        private const val STOP_RETRY_DELAY_MS = 1_000L
        private const val DOWNSTREAM_STOP_ATTEMPTS = 3
        private const val START_TIMEOUT_SEC = 15L
        private const val UPSTREAM_POLL_MS = 500L
        private const val UPSTREAM_RELEASE_MS = 4_000L
    }
}
