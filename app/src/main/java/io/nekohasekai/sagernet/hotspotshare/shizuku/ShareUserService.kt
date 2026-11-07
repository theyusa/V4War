package io.nekohasekai.sagernet.hotspotshare.shizuku

import android.content.Context
import android.net.LinkAddress
import android.net.LinkProperties
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shizuku UserService implementation executing in a dedicated shell process (UID 2000).
 * Provides privileged network management (TestNetworkManager, TetheringManager).
 */
class ShareUserService : IShareUserService.Stub() {

    private val context: Context by lazy {
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper()
        }
        exemptHiddenApis()
        val ctx = ShellContextHelper.createShellContext()
        initServices(ctx)
        ctx
    }

    private var testNetworkManager: Any? = null
    private var tetheringManager: Any? = null

    private var testTunInterface: Any? = null
    private var tunPfd: ParcelFileDescriptor? = null
    private var testNetworkBinder: IBinder? = null

    init {
        // Run proactive cleanup on instantiation to reset any dirty state from prior crashes
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

    private fun initServices(ctx: Context) {
        // 1. TestNetworkManager
        try {
            val gss = Context::class.java.getMethod("getSystemService", Class::class.java)
            testNetworkManager = gss.invoke(ctx, Class.forName("android.net.TestNetworkManager"))
        } catch (_: Throwable) {
        }
        if (testNetworkManager == null) {
            val b = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "test_network_service") as? IBinder
            if (b != null) {
                val iTnm = Class.forName("android.net.ITestNetworkManager\$Stub")
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, b)
                val ctor = Class.forName("android.net.TestNetworkManager")
                    .getConstructor(Class.forName("android.net.ITestNetworkManager"))
                testNetworkManager = ctor.newInstance(iTnm)
            }
        }
        if (testNetworkManager == null) {
            throw IllegalStateException("Failed to obtain TestNetworkManager service")
        }

        // 2. TetheringManager
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

    @Synchronized
    override fun createTestNetwork(dnsServers: List<String>?): ParcelFileDescriptor {
        val ctx = context
        val tnm = testNetworkManager ?: throw IllegalStateException("TestNetworkManager is not ready")

        val tunAddress: LinkAddress = try {
            val ctor = LinkAddress::class.java.getConstructor(String::class.java)
            ctor.newInstance("198.18.0.1/15")
        } catch (_: Throwable) {
            val ctor = LinkAddress::class.java.getConstructor(InetAddress::class.java, Int::class.javaPrimitiveType)
            ctor.newInstance(InetAddress.getByName("198.18.0.1"), 15)
        }

        val createTunMethod: Method = try {
            tnm.javaClass.getMethod("createTunInterface", Array<LinkAddress>::class.java)
        } catch (_: NoSuchMethodException) {
            tnm.javaClass.getMethod("createTunInterface", Collection::class.java)
        }

        val ifaceObj = if (createTunMethod.parameterTypes[0].isArray) {
            val arr = java.lang.reflect.Array.newInstance(LinkAddress::class.java, 1)
            java.lang.reflect.Array.set(arr, 0, tunAddress)
            createTunMethod.invoke(tnm, arr)
        } else {
            createTunMethod.invoke(tnm, Collections.singletonList(tunAddress))
        } ?: throw IllegalStateException("createTunInterface returned null")

        testTunInterface = ifaceObj
        val ifaceName = ifaceObj.javaClass.getMethod("getInterfaceName").invoke(ifaceObj) as String
        val pfd = ifaceObj.javaClass.getMethod("getFileDescriptor").invoke(ifaceObj) as ParcelFileDescriptor
        tunPfd = pfd

        // LinkProperties setup with preserved DNS forwarders
        val lp = LinkProperties()
        try {
            lp.javaClass.getMethod("setInterfaceName", String::class.java).invoke(lp, ifaceName)
            lp.javaClass.getMethod("setLinkAddresses", java.util.Collection::class.java)
                .invoke(lp, Collections.singletonList(tunAddress))

            val dnsList = mutableListOf<InetAddress>()
            if (!dnsServers.isNullOrEmpty()) {
                for (dns in dnsServers) {
                    try {
                        dnsList.add(InetAddress.getByName(dns))
                    } catch (_: Throwable) {
                    }
                }
            }
            if (dnsList.isEmpty()) {
                dnsList.add(InetAddress.getByName("1.1.1.1"))
                dnsList.add(InetAddress.getByName("8.8.8.8"))
            }
            lp.javaClass.getMethod("setDnsServers", java.util.Collection::class.java).invoke(lp, dnsList)
        } catch (_: Throwable) {
        }

        val binder = Binder()
        testNetworkBinder = binder

        try {
            val setupMethod = tnm.javaClass.getMethod(
                "setupTestNetwork",
                LinkProperties::class.java,
                Boolean::class.javaPrimitiveType,
                IBinder::class.java
            )
            setupMethod.invoke(tnm, lp, false, binder)
        } catch (_: NoSuchMethodException) {
            val setupMethod = tnm.javaClass.getMethod(
                "setupTestNetwork",
                String::class.java,
                IBinder::class.java
            )
            setupMethod.invoke(tnm, ifaceName, binder)
        }

        return pfd.dup()
    }

    @Synchronized
    override fun setPreferTestNetworks(prefer: Boolean) {
        val tm = tetheringManager ?: run {
            context
            tetheringManager ?: throw IllegalStateException("TetheringManager is not ready")
        }
        val setPreferMethod = tm.javaClass.getMethod("setPreferTestNetworks", Boolean::class.javaPrimitiveType)
        setPreferMethod.invoke(tm, prefer)
    }

    @Synchronized
    override fun startTethering(): Boolean {
        val tm = tetheringManager ?: run {
            context
            tetheringManager ?: throw IllegalStateException("TetheringManager is not ready")
        }

        val tetheringWifi = 0
        val reqBuilderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
        val reqBuilderCtor: Constructor<*> = reqBuilderClass.getConstructor(Int::class.javaPrimitiveType)
        val reqBuilder = reqBuilderCtor.newInstance(tetheringWifi)
        val tetherRequest = reqBuilderClass.getMethod("build").invoke(reqBuilder)

        val tetherLatch = CountDownLatch(1)
        val tetherStarted = AtomicBoolean(false)
        val tetherErrorCode = AtomicInteger(0)

        val startCbClass = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
        val callbackProxy = Proxy.newProxyInstance(
            startCbClass.classLoader,
            arrayOf(startCbClass)
        ) { _, method, mArgs ->
            when (method.name) {
                "onTetheringStarted" -> {
                    tetherStarted.set(true)
                    tetherLatch.countDown()
                }
                "onTetheringFailed" -> {
                    val err = mArgs?.get(0) as? Int ?: -1
                    tetherErrorCode.set(err)
                    tetherLatch.countDown()
                }
            }
            null
        }

        val directExecutor = Executor { it.run() }
        val startTetheringMethod = tm.javaClass.getMethod(
            "startTethering",
            Class.forName("android.net.TetheringManager\$TetheringRequest"),
            Executor::class.java,
            startCbClass
        )

        startTetheringMethod.invoke(tm, tetherRequest, directExecutor, callbackProxy)

        val completed = tetherLatch.await(15, TimeUnit.SECONDS)
        if (!completed) {
            return false
        }
        return tetherStarted.get()
    }

    @Synchronized
    override fun stopTethering() {
        val tm = tetheringManager ?: return
        try {
            val stopMethod = tm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
            stopMethod.invoke(tm, 0)
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    override fun cleanupDirtyState() {
        stopTethering()
        try {
            setPreferTestNetworks(false)
        } catch (_: Throwable) {
        }
        try {
            tunPfd?.close()
        } catch (_: Throwable) {
        }
        tunPfd = null
        testTunInterface = null
        testNetworkBinder = null
    }

    @Synchronized
    override fun destroy() {
        cleanupDirtyState()
        System.exit(0)
    }
}
