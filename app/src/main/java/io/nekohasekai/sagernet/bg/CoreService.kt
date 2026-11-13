package io.nekohasekai.sagernet.bg

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.nekohasekai.sagernet.aidl.ICoreService
import io.nekohasekai.sagernet.aidl.ISubscriptionCallback
import io.nekohasekai.sagernet.aidl.ITestSessionCallback
import io.nekohasekai.sagernet.bg.proto.CoreConfigs
import io.nekohasekai.sagernet.bg.test.TestEngine
import io.nekohasekai.sagernet.bg.test.TestSpec
import io.nekohasekai.sagernet.group.SubscriptionQueue
import io.throneproj.mobile.Mobile
import io.throneproj.mobile.ResolveRequest
import org.json.JSONArray
import org.json.JSONObject

// Always-bindable :bg host for the probe boxes, so the main process never loads the core.
class CoreService : Service() {

    private val binder = object : ICoreService.Stub() {

        override fun startTest(spec: TestSpec, cb: ITestSessionCallback): Int = TestEngine.start(spec, cb)

        override fun stopTests() = TestEngine.stop()

        override fun runningTestSession(): Int = TestEngine.runningSession()

        override fun refreshGroup(gid: Long, showDiff: Boolean) = SubscriptionQueue.refreshGroup(gid, showDiff)

        override fun refreshAll(onlyAllowed: Boolean) = SubscriptionQueue.refreshAll(onlyAllowed)

        override fun subscribeUrl(url: String, name: String?, autoUpdate: Boolean): Long =
            SubscriptionQueue.subscribeUrl(url, name.orEmpty(), autoUpdate)

        override fun importUrl(url: String, gid: Long) = SubscriptionQueue.importUrl(url, gid)

        override fun importFile(path: String, gid: Long) = SubscriptionQueue.importFile(path, gid)

        override fun groupAction(gid: Long, action: String): String = SubscriptionQueue.groupAction(gid, action)

        override fun resolveProfiles(ids: LongArray?): String = SubscriptionQueue.resolveProfiles(ids?.toList().orEmpty())

        override fun registerSubscriptionCallback(cb: ISubscriptionCallback) = SubscriptionQueue.register(cb)

        override fun unregisterSubscriptionCallback(cb: ISubscriptionCallback) = SubscriptionQueue.unregister(cb)

        override fun warpRegister(tunnelType: String, proxy: String?, apiHosts: Array<String>?): String =
            WarpRegistration.register(tunnelType, proxy.orEmpty(), apiHosts ?: emptyArray())

        override fun genQuicSignature(domain: String?): String = QuicSignature.generate(domain.orEmpty())

        override fun captureDiagnostics(
            durationMs: Int,
            contention: Boolean,
            trace: Boolean,
            attachmentPaths: Array<String>?,
            outputPath: String?,
        ): String = CoreDiagnostics.capture(
            durationMs, contention, trace, attachmentPaths ?: emptyArray(), outputPath.orEmpty()
        )

        override fun stopDiagnostics() = CoreDiagnostics.stop()

        override fun resolveDomain(domain: String?): String {
            val target = domain.orEmpty().trim()
            val out = JSONObject()
            val arr = JSONArray()
            if (target.isEmpty()) {
                out.put("ips", arr)
                out.put("error", "Empty domain")
                return out.toString()
            }
            val err = runCatching {
                val running = CoreRuntime.running?.instance
                val req = ResolveRequest().apply {
                    if (running == null) {
                        val probe = CoreConfigs.buildTest(emptyList())
                        if (!probe.ok) error(probe.error.orEmpty())
                        coreConfig = probe.coreConfig
                    }
                    addDomain(target)
                }
                val iter = Mobile.resolveDomains(running, CoreRuntime.platform, req)
                var resolveErr = ""
                if (iter.hasNext()) {
                    val res = iter.next()
                    resolveErr = res.error.orEmpty()
                    val addrs = res.addresses()
                    while (addrs.hasNext()) {
                        val ip = addrs.next()
                        if (ip.isNotBlank()) arr.put(ip)
                    }
                }
                resolveErr
            }.getOrElse { it.message ?: "Resolve failed" }
            out.put("ips", arr)
            out.put("error", err)
            return out.toString()
        }
    }

    override fun onCreate() {
        super.onCreate()
        CoreForeground.attach(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        CoreForeground.onStartCommand(this, startId)

    override fun onTimeout(startId: Int, fgsType: Int) = CoreForeground.onTimeout(this)

    override fun onDestroy() {
        CoreForeground.detach(this)
        super.onDestroy()
    }
}
