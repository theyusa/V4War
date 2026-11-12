package io.nekohasekai.sagernet.hotspotshare.root

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.nekohasekai.sagernet.hotspotshare.shizuku.ShellContextHelper
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Standalone `app_process` entrypoint invoked under root (`su`) by RootBackend
 * to automatically start or stop Wi-Fi hotspot tethering via AOSP TetheringManager
 * (with WifiManager.startTetheredHotspot fallback), mirroring shizzi's DownstreamControl.
 *
 * Usage: CLASSPATH=<apkPath> /system/bin/app_process /system/bin io.nekohasekai.sagernet.hotspotshare.root.RootTetherHelper start|stop
 */
object RootTetherHelper {

    private const val TETHERING_WIFI = 0
    private const val START_TIMEOUT_SEC = 15L

    @JvmStatic
    fun main(args: Array<String>) {
        val action = args.firstOrNull()?.lowercase() ?: "start"
        val code = runCatching {
            if (Looper.getMainLooper() == null) {
                @Suppress("DEPRECATION")
                Looper.prepareMainLooper()
            }
            exemptHiddenApis()
            val ctx = ShellContextHelper.createShellContext()
            when (action) {
                "start" -> if (startWifiHotspot(ctx)) 0 else 1
                "stop" -> {
                    stopWifiHotspot(ctx)
                    0
                }
                else -> 2
            }
        }.getOrElse { err ->
            System.err.println("RootTetherHelper failed: ${err.message}")
            err.printStackTrace(System.err)
            1
        }
        exitProcess(code)
    }

    private fun exemptHiddenApis() {
        runCatching {
            val vmRuntime = Class.forName("dalvik.system.VMRuntime")
            val runtime = vmRuntime.getMethod("getRuntime").invoke(null)
            vmRuntime.getMethod("setHiddenApiExemptions", Array<String>::class.java)
                .invoke(runtime, arrayOf("L"))
        }
    }

    private fun startWifiHotspot(ctx: Context): Boolean {
        val tm = getTetheringManager(ctx)
        if (tm != null) {
            ShellContextHelper.injectContextIntoTetheringManager(tm, ctx)
            if (startViaTetheringRequest(tm, exempt = true)) return true
            if (startViaTetheringRequest(tm, exempt = false)) return true
        }
        return startViaWifiManager(ctx)
    }

    private fun stopWifiHotspot(ctx: Context) {
        val tm = getTetheringManager(ctx) ?: return
        runCatching {
            ShellContextHelper.injectContextIntoTetheringManager(tm, ctx)
            tm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
                .invoke(tm, TETHERING_WIFI)
        }
    }

    private fun getTetheringManager(ctx: Context): Any? {
        runCatching { ctx.getSystemService("tethering") }.getOrNull()?.let { return it }
        return runCatching {
            val gss = Context::class.java.getMethod("getSystemService", Class::class.java)
            gss.invoke(ctx, Class.forName("android.net.TetheringManager"))
        }.getOrNull()
    }

    private fun startViaTetheringRequest(tm: Any, exempt: Boolean): Boolean {
        return runCatching {
            val builderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
            val builder = builderClass.getConstructor(Int::class.javaPrimitiveType)
                .newInstance(TETHERING_WIFI)
            if (exempt) {
                runCatching {
                    builderClass.getDeclaredMethod("setExemptFromEntitlementCheck", Boolean::class.javaPrimitiveType)
                        .invoke(builder, true)
                }
            }
            val request = builderClass.getMethod("build").invoke(builder) ?: return false

            val latch = CountDownLatch(1)
            val ok = AtomicBoolean(false)
            val cbClass = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
            val cbProxy = Proxy.newProxyInstance(
                cbClass.classLoader,
                arrayOf(cbClass),
            ) { _, method, _ ->
                when (method.name) {
                    "onTetheringStarted" -> {
                        ok.set(true)
                        latch.countDown()
                    }
                    "onTetheringFailed" -> {
                        latch.countDown()
                    }
                }
                null
            }

            val mainHandler = Handler(Looper.getMainLooper())
            val executor = Executor { cmd -> mainHandler.post(cmd) }
            tm.javaClass.getMethod(
                "startTethering",
                Class.forName("android.net.TetheringManager\$TetheringRequest"),
                Executor::class.java,
                cbClass,
            ).invoke(tm, request, executor, cbProxy)

            latch.await(START_TIMEOUT_SEC, TimeUnit.SECONDS) && ok.get()
        }.getOrDefault(false)
    }

    private fun startViaWifiManager(ctx: Context): Boolean {
        return runCatching {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) ?: return false
            val configClass = Class.forName("android.net.wifi.SoftApConfiguration")
            val method = wm.javaClass.getMethod("startTetheredHotspot", configClass)
            method.invoke(wm, null) as? Boolean ?: false
        }.getOrDefault(false)
    }
}
