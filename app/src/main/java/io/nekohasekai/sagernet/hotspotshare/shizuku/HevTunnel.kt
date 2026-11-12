package io.nekohasekai.sagernet.hotspotshare.shizuku

import android.content.Context
import android.os.ParcelFileDescriptor
import hev.htproxy.TProxyService
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Controller for the hev-socks5-tunnel JNI service.
 * Translates TUN layer-3 packets into loopback SOCKS5 inbound connections.
 *
 * Uses MTU 1500 to match AOSP TestNetworkManager's default 1500 MTU on `testtunN`
 * (matching carlelieser/shizzi's TUN_MTU = 1500).
 */
class HevTunnel(private val context: Context) {

    private val tproxy = TProxyService()
    private val running = AtomicBoolean(false)
    private var configFile: File? = null

    @Synchronized
    fun start(
        tunPfd: ParcelFileDescriptor,
        socksPort: Int = 2088,
        mtu: Int = DEFAULT_TUN_MTU,
    ): Boolean {
        if (running.get()) return true

        val config = File(context.cacheDir, "hev_tunnel.yml")
        val yaml = buildString {
            append("tunnel:\n")
            append("  mtu: $mtu\n")
            append("socks5:\n")
            append("  port: $socksPort\n")
            append("  address: 127.0.0.1\n")
            append("  udp: 'udp'\n")
            append("misc:\n")
            append("  log-level: warn\n")
        }

        FileOutputStream(config).use { it.write(yaml.toByteArray()) }
        configFile = config

        val started = tproxy.TProxyStartService(config.absolutePath, tunPfd.fd)
        if (started) {
            running.set(true)
        } else {
            config.delete()
        }
        return started
    }

    @Synchronized
    fun stop() {
        if (!running.get()) return
        try {
            tproxy.TProxyStopService()
        } catch (_: Throwable) {
        }
        running.set(false)
        configFile?.delete()
        configFile = null
    }

    fun isRunning(): Boolean = running.get() && tproxy.TProxyIsRunning()

    /**
     * Returns stats array: [txPackets, txBytes, rxPackets, rxBytes] or empty array if not running.
     */
    fun getStats(): LongArray {
        return if (isRunning()) {
            tproxy.TProxyGetStats() ?: LongArray(4)
        } else {
            LongArray(4)
        }
    }

    companion object {
        const val DEFAULT_TUN_MTU = 1500
    }
}
