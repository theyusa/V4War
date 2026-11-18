package io.nekohasekai.sagernet.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SettingsRegistry
import io.nekohasekai.sagernet.hotspotshare.ShizukuBackend
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * One IPv4 address on an interface snapshot: the dotted-quad string and, when the OS reports it,
 * the interface's prefix length (0..32).
 */
data class LanSharingAddress(val ip: String, val prefixLength: Int?)

/** One network interface snapshot: its name, up/loopback state and the IPv4 addresses it holds. */
data class LanSharingIface(
    val name: String,
    val up: Boolean,
    val loopback: Boolean,
    val ipv4: List<LanSharingAddress>,
)

/**
 * Channel category of a downstream tethered interface.
 */
enum class TetherKind {
    WIFI,
    USB,
    BLUETOOTH,
    OTHER,
}

/**
 * A detected hotspot interface: its name, the IPv4 gateway address clients reach this phone on and,
 * when reported, the interface's prefix length for exact subnet matching.
 */
data class HotspotCandidate(
    val interfaceName: String,
    val ip: String,
    val prefixLength: Int?,
)

/**
 * Android network sniffing (hotspot / Wi-Fi local IPs, `/proc/net/arp`) and the Clash API
 * `/connections` poller that feeds [LanSharingLogics].
 */
object LanSharingNetwork {

    /**
     * TODO: Re-enable USB tethering once RNDIS/NCM upstream selection and USB gadget
     * re-enumeration issues are investigated and fixed.
     */
    const val USB_TETHER_ENABLED: Boolean = false

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    internal fun newDaemonCachedThreadPool(threadName: String): ExecutorService =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, threadName).apply { isDaemon = true }
        }

    private val neighExecutor = newDaemonCachedThreadPool("LanSharing-neigh")

    /**
     * Builds the loopback `/connections` URL from the sign-encoded `core_box_clash_api` port.
     */
    fun buildClashConnectionsUrl(rawPort: Int): String {
        val port = abs(rawPort).takeIf { it > 0 } ?: abs(SettingsRegistry.CORE_BOX_CLASH_API.default)
        return "http://127.0.0.1:$port/connections"
    }

    fun clashUrl(): String = buildClashConnectionsUrl(DataStore.coreBoxClashApi)

    fun isCellularOrVirtualInterface(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("rmnet") ||
            n.startsWith("v4-") ||
            n.startsWith("r_") ||
            n.startsWith("ccmni") ||
            n.startsWith("pdp") ||
            n.startsWith("wwan") ||
            n.startsWith("dummy") ||
            n.startsWith("tun") ||
            n.startsWith("testtun") ||
            n.startsWith("sit") ||
            n.startsWith("ip6") ||
            n.startsWith("clat") ||
            n.startsWith("seth") ||
            n.startsWith("ipa") ||
            n.startsWith("epdg") ||
            n.startsWith("bond") ||
            n == "lo"
    }

    /**
     * Classifies a downstream tether/hotspot interface name into [TetherKind].
     * Normal Wi-Fi STA (`wlan0`) and cellular/virtual interfaces return [TetherKind.OTHER].
     */
    fun getTetherKindForInterface(iface: String): TetherKind {
        val n = iface.trim().lowercase()
        if (n.isEmpty() || isCellularOrVirtualInterface(n)) return TetherKind.OTHER
        return when {
            n.startsWith("rndis") || n.contains("rndis") || n.startsWith("usb") || n.startsWith("ncm") ->
                TetherKind.USB
            n.startsWith("bt-pan") || n.startsWith("bnep") ->
                TetherKind.BLUETOOTH
            n.startsWith("ap") ||
                (n.startsWith("wlan") && (n.removePrefix("wlan").toIntOrNull() ?: 0) >= 1) ||
                n.startsWith("swlan") ||
                n.startsWith("softap") ||
                n.startsWith("wigig") ||
                n.startsWith("wifi") ||
                n.contains("hotspot") ->
                TetherKind.WIFI
            else -> TetherKind.OTHER
        }
    }

    fun isHotspotInterfaceName(name: String): Boolean =
        getTetherKindForInterface(name) != TetherKind.OTHER

    /**
     * RFC 1918 private IPv4 (10/8, 172.16/12, 192.168/16). The hotspot gateway side always lives in
     * a private range; CGNAT (`100.64/10`) and anything else does not count. Pure and unit-testable.
     */
    fun isPrivateIpv4(ip: String): Boolean {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return when {
            octets[0] == 10 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            else -> false
        }
    }

    fun isValidHotspotIp(ip: String): Boolean {
        if (ip.isBlank()) return false
        return ip != "127.0.0.1" &&
            ip != "0.0.0.0" &&
            !ip.startsWith("127.") &&
            !ip.startsWith("169.254.") &&
            !ip.startsWith("172.19.") &&
            !ip.startsWith("192.0.2.")
    }

    /**
     * Returns active tethering channels ([TetherKind.WIFI], [TetherKind.USB], [TetherKind.BLUETOOTH])
     * by inspecting Wi-Fi AP state, `getTetheredIfaces`, and active non-upstream [NetworkInterface]s.
     */
    fun getActiveTetherKinds(context: Context): Set<TetherKind> {
        val appCtx = context.applicationContext ?: context
        val kinds = linkedSetOf<TetherKind>()

        val wifiManager = appCtx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val apEnabled = runCatching {
            val method = wifiManager?.javaClass?.getDeclaredMethod("isWifiApEnabled")
            method?.isAccessible = true
            method?.invoke(wifiManager) as? Boolean
        }.getOrNull()
        val apState = runCatching {
            val method = wifiManager?.javaClass?.getDeclaredMethod("getWifiApState")
            method?.isAccessible = true
            method?.invoke(wifiManager) as? Int
        }.getOrNull()
        if (apEnabled == true || apState == 12 || apState == 13) {
            kinds += TetherKind.WIFI
        }

        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        for (iface in collectAllTetheredIfaces(appCtx, cm)) {
            val lower = iface.trim().lowercase()
            val kind = if (lower.startsWith("wlan")) TetherKind.WIFI else getTetherKindForInterface(iface)
            if (kind != TetherKind.OTHER && (USB_TETHER_ENABLED || kind != TetherKind.USB)) {
                kinds += kind
            }
        }

        val knownIfaces = collectKnownNetworkIfaces(cm)
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { nif ->
                val isUp = runCatching { nif.isUp }.getOrDefault(false)
                val isLoopback = runCatching { nif.isLoopback }.getOrDefault(false)
                if (!isUp || isLoopback) return@forEach
                if (nif.name.lowercase() in knownIfaces) return@forEach
                val kind = getTetherKindForInterface(nif.name)
                if (kind != TetherKind.OTHER && (USB_TETHER_ENABLED || kind != TetherKind.USB)) {
                    kinds += kind
                }
            }
        }

        return kinds
    }

    fun isHotspotActive(context: Context): Boolean {
        if (getActiveTetherKinds(context).isNotEmpty()) return true
        return findHotspotIp(context) != null
    }

    private const val SHELL_UID = 2000
    private val cmTetheredIfacesBlocked = AtomicBoolean(false)
    private val tmTetheredIfacesBlocked = AtomicBoolean(false)

    internal fun isPrivilegedShellOrRootUid(
        uid: Int = runCatching { Process.myUid() }.getOrDefault(-1),
    ): Boolean = uid == SHELL_UID || uid == 0

    internal fun canReflectTetheredIfaces(
        sdkInt: Int = Build.VERSION.SDK_INT,
        uid: Int = runCatching { Process.myUid() }.getOrDefault(-1),
    ): Boolean = sdkInt < Build.VERSION_CODES.R || isPrivilegedShellOrRootUid(uid)

    internal fun isProcNetRestrictedForCurrentProcess(
        sdkInt: Int = Build.VERSION.SDK_INT,
        uid: Int = runCatching { Process.myUid() }.getOrDefault(-1),
    ): Boolean = sdkInt >= Build.VERSION_CODES.Q && uid >= Process.FIRST_APPLICATION_UID

    internal fun extractTetheredIfacesFromIntent(intent: Intent?): List<String> {
        if (intent == null) return emptyList()
        val tetherArray = runCatching { intent.getStringArrayListExtra("tetherArray") }.getOrNull().orEmpty()
        val activeArray = runCatching { intent.getStringArrayListExtra("activeArray") }.getOrNull().orEmpty()
        return (tetherArray + activeArray)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    internal fun readStickyTetheredIfaces(context: Context): List<String> = runCatching {
        val sticky = context.registerReceiver(null, IntentFilter("android.net.conn.TETHER_STATE_CHANGED"))
        extractTetheredIfacesFromIntent(sticky)
    }.getOrDefault(emptyList())

    private fun collectNonUpstreamHotspotIfaces(cm: ConnectivityManager?): List<String> {
        val knownIfaces = collectKnownNetworkIfaces(cm)
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().mapNotNull { nif ->
                val isUp = runCatching { nif.isUp }.getOrDefault(false)
                val isLoopback = runCatching { nif.isLoopback }.getOrDefault(false)
                if (!isUp || isLoopback) return@mapNotNull null
                if (nif.name.lowercase() in knownIfaces) return@mapNotNull null
                if (!isHotspotInterfaceName(nif.name)) return@mapNotNull null
                nif.name
            }
        }.getOrDefault(emptyList())
    }

    fun getTetheredIfaces(cm: ConnectivityManager?): List<String> {
        if (canReflectTetheredIfaces()) {
            if (cm != null && !cmTetheredIfacesBlocked.get()) {
                val reflected = try {
                    val method = cm.javaClass.getDeclaredMethod("getTetheredIfaces")
                    method.isAccessible = true
                    (method.invoke(cm) as? Array<*>)?.filterIsInstance<String>().orEmpty()
                } catch (_: Throwable) {
                    cmTetheredIfacesBlocked.set(true)
                    emptyList()
                }
                if (reflected.isNotEmpty() || isPrivilegedShellOrRootUid()) {
                    return reflected
                }
            }
            return emptyList()
        }

        val boundShizuku = runCatching { ShizukuBackend.getBoundTetheredIfaces() }.getOrNull()
        if (boundShizuku != null) {
            return boundShizuku
        }

        val result = linkedSetOf<String>()
        runCatching { SagerNet.application }.getOrNull()?.let { appCtx ->
            result += readStickyTetheredIfaces(appCtx)
        }
        result += collectNonUpstreamHotspotIfaces(cm)
        return result.toList()
    }

    /**
     * Returns true when the OS explicitly reports an active USB tethered interface
     * in TetheredState via TetheringManager/ConnectivityManager.
     */
    fun isUsbTetheringConfirmedInOs(context: Context): Boolean {
        if (!USB_TETHER_ENABLED) return false
        val appCtx = context.applicationContext ?: context
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return collectAllTetheredIfaces(appCtx, cm)
            .any { getTetherKindForInterface(it) == TetherKind.USB }
    }

    /**
     * Returns true when an active tethered USB interface uses the NCM protocol (`ncm*`).
     */
    fun hasActiveNcmTetheredIface(context: Context): Boolean {
        if (!USB_TETHER_ENABLED) return false
        val appCtx = context.applicationContext ?: context
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return collectAllTetheredIfaces(appCtx, cm)
            .any { it.trim().lowercase().startsWith("ncm") }
    }

    private fun collectAllTetheredIfaces(appCtx: Context, cm: ConnectivityManager?): List<String> {
        val tethered = linkedSetOf<String>()
        if (canReflectTetheredIfaces()) {
            tethered += getTetheredIfaces(cm)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !tmTetheredIfacesBlocked.get()) {
                try {
                    val tm = appCtx.getSystemService("tethering")
                    val method = tm?.javaClass?.getMethod("getTetheredIfaces")
                    (method?.invoke(tm) as? Array<*>)?.filterIsInstance<String>()?.forEach { tethered += it }
                } catch (_: Throwable) {
                    tmTetheredIfacesBlocked.set(true)
                }
            }
            return tethered.toList()
        }

        val boundShizuku = runCatching { ShizukuBackend.getBoundTetheredIfaces() }.getOrNull()
        if (boundShizuku != null) {
            tethered += boundShizuku
            return tethered.toList()
        }

        tethered += readStickyTetheredIfaces(appCtx)
        tethered += collectNonUpstreamHotspotIfaces(cm)
        return tethered.toList()
    }

    private fun collectKnownNetworkIfaces(cm: ConnectivityManager?): Set<String> = runCatching {
        cm?.allNetworks?.toList().orEmpty()
            .mapNotNull { network -> cm?.getLinkProperties(network)?.interfaceName }
            .map { it.lowercase() }
            .toSet()
    }.getOrDefault(emptySet())

    fun findHotspotIp(context: Context): String? = findHotspotCandidates(context).firstOrNull()?.ip

    /**
     * Finds every interface that looks like the hotspot side. Pure and unit-testable via
     * [hotspotCandidates]; the Android layer only collects the input snapshots.
     */
    fun findHotspotCandidates(context: Context): List<HotspotCandidate> {
        val appCtx = context.applicationContext ?: context
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val tetheredIfaces = collectAllTetheredIfaces(appCtx, cm)

        // Every interface ConnectivityManager itself knows about (Wi-Fi client, cellular, VPN,
        // Ethernet): they belong to other networks and are never the hotspot side.
        val knownIfaces = collectKnownNetworkIfaces(cm)

        val interfaces = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .map { intf ->
                    LanSharingIface(
                        intf.name,
                        runCatching { intf.isUp }.getOrDefault(false),
                        runCatching { intf.isLoopback }.getOrDefault(false),
                        runCatching { intf.interfaceAddresses }.getOrDefault(emptyList())
                            .filter { it.address is Inet4Address }
                            .mapNotNull { address ->
                                val ip = address.address.hostAddress ?: return@mapNotNull null
                                val prefix = runCatching { address.networkPrefixLength }.getOrNull()
                                    ?.toInt()?.takeIf { it in 0..32 }
                                LanSharingAddress(ip, prefix)
                            },
                    )
                }
        }.getOrDefault(emptyList())

        val wifiIp = findWifiIp(context)
        return hotspotCandidates(interfaces, tetheredIfaces, knownIfaces, wifiIp)
    }

    /**
     * The hotspot-candidate decision, pure and unit-testable. An interface is a candidate when it
     * is up and not loopback, not a cellular/virtual helper ([isCellularOrVirtualInterface]), not
     * an interface ConnectivityManager lists for its own networks ([knownIfaces]) — the Wi-Fi
     * client, cellular and VPN sides — and when it carries a private IPv4 ([isPrivateIpv4]) that is
     * not the Wi-Fi client address. Interfaces that are explicitly tethered (getTetheredIfaces)
     * lead the result, followed by recognized tethering interfaces (`ap*`, `swlan*`, `softap*`,
     * `wlan1+`, `rndis*`, `usb*`, `ncm*`, `bt-pan*`, `bnep*`), and then remaining interfaces in OS
     * order. No IP pattern and no default address is ever guessed.
     */
    fun hotspotCandidates(
        interfaces: List<LanSharingIface>,
        tetheredIfaces: List<String>,
        knownIfaces: Set<String>,
        wifiIp: String?,
    ): List<HotspotCandidate> {
        val picked = LinkedHashMap<String, HotspotCandidate>()
        val tethered = tetheredIfaces.map { it.lowercase() }.toSet()
        fun consider(iface: LanSharingIface) {
            if (!iface.up || iface.loopback) return
            val key = iface.name.lowercase()
            if (key in picked) return
            if (isCellularOrVirtualInterface(iface.name)) return
            if (key in knownIfaces) return
            val address = iface.ipv4.firstOrNull { candidate ->
                candidate.ip.isNotBlank() &&
                    isValidHotspotIp(candidate.ip) &&
                    isPrivateIpv4(candidate.ip) &&
                    candidate.ip != wifiIp
            } ?: return
            picked[key] = HotspotCandidate(iface.name, address.ip, address.prefixLength)
        }
        interfaces.filter { it.name.lowercase() in tethered }.forEach(::consider)
        interfaces.filter { isHotspotInterfaceName(it.name) }.forEach(::consider)
        interfaces.forEach(::consider)
        return picked.values.toList()
    }

    fun findWifiIp(context: Context): String? {
        val appCtx = context.applicationContext
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val networks = runCatching { cm.allNetworks.toList() }.getOrDefault(emptyList())
        for (network in networks) {
            val caps = runCatching { cm.getNetworkCapabilities(network) }.getOrNull() ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val lp = runCatching { cm.getLinkProperties(network) }.getOrNull() ?: continue
            val ip = lp.linkAddresses
                .map { it.address }
                .filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
                .firstOrNull { isValidHotspotIp(it) }
            if (ip != null) return ip
        }
        return null
    }

    private val procArpBlocked = AtomicBoolean(isProcNetRestrictedForCurrentProcess())
    private val procTcpBlocked = AtomicBoolean(isProcNetRestrictedForCurrentProcess())
    private val localNeighBlocked = AtomicBoolean(false)

    internal fun isTcpEndpointOpen(host: String, port: Int, timeoutMs: Int = 250): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
        }
        true
    }.getOrDefault(false)

    internal fun isPortBoundLoopbackOnly(
        port: Int,
        nonLoopbackLocalIps: List<String>,
        timeoutMs: Int = 250,
        endpointOpenProbe: (host: String, port: Int, timeoutMs: Int) -> Boolean = ::isTcpEndpointOpen,
    ): Boolean {
        if (port !in 1..65535 || nonLoopbackLocalIps.isEmpty()) return false
        if (!endpointOpenProbe("127.0.0.1", port, timeoutMs)) return false
        return nonLoopbackLocalIps.none { ip -> endpointOpenProbe(ip, port, timeoutMs) }
    }

    fun findLocalNonLoopbackIpv4Addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().flatMap { nif ->
            val isUp = runCatching { nif.isUp }.getOrDefault(false)
            val isLoopback = runCatching { nif.isLoopback }.getOrDefault(false)
            if (!isUp || isLoopback || isCellularOrVirtualInterface(nif.name)) return@flatMap emptyList()
            runCatching { nif.interfaceAddresses }.getOrDefault(emptyList())
                .mapNotNull { addr ->
                    (addr.address as? Inet4Address)?.hostAddress?.takeIf { isValidHotspotIp(it) }
                }
        }.distinct()
    }.getOrDefault(emptyList())

    fun readArp(): List<String> {
        if (procArpBlocked.get()) return emptyList()
        val text = try {
            File("/proc/net/arp").readText()
        } catch (_: Throwable) {
            procArpBlocked.set(true)
            return emptyList()
        }
        return LanSharingLogics.parseArpFile(text)
    }

    data class NeighborSnapshot(
        val allIps: List<String>,
        val activeIps: Set<String>,
        val hotspotIps: Set<String>,
    )

    fun readProcNetTcpRemotes(listenPort: Int): List<String> {
        if (listenPort <= 0 || procTcpBlocked.get()) return emptyList()
        val results = linkedSetOf<String>()
        var anyReadable = false
        for (path in listOf("/proc/net/tcp", "/proc/net/tcp6")) {
            val text = try {
                File(path).readText().also { anyReadable = true }
            } catch (_: Throwable) {
                continue
            }
            results += LanSharingLogics.parseProcNetTcpRemotes(text, listenPort)
        }
        if (results.isEmpty() && !anyReadable) {
            procTcpBlocked.set(true)
        }
        return results.toList()
    }

    fun readPrivilegedNeighbors(
        shizukuDump: String?,
        rootDump: String?,
        hotspotIfaces: Set<String> = emptySet(),
    ): NeighborSnapshot {
        val allIps = linkedSetOf<String>()
        val activeIps = linkedSetOf<String>()
        val hotspotIps = linkedSetOf<String>()

        fun ingestEntries(entries: List<NeighborEntry>) {
            entries.forEach { entry ->
                val ip = entry.ip
                if (!isValidHotspotIp(ip)) return@forEach
                allIps += ip
                val iface = entry.iface
                if (iface.isNotBlank() && (isHotspotInterfaceName(iface) || iface in hotspotIfaces)) {
                    hotspotIps += ip
                }
                if (entry.isActive) {
                    activeIps += ip
                }
            }
        }

        val combinedDumps = listOfNotNull(shizukuDump, rootDump).filter { it.isNotBlank() }
        combinedDumps.forEach { dump ->
            val lines = dump.lineSequence().toList()
            val arpLines = mutableListOf<String>()
            val neighLines = mutableListOf<String>()
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEach
                if (trimmed.contains(" dev ")) {
                    neighLines += trimmed
                } else {
                    arpLines += trimmed
                }
            }
            if (neighLines.isNotEmpty()) {
                ingestEntries(LanSharingLogics.parseIpNeigh(neighLines.joinToString("\n")))
            }
            if (arpLines.isNotEmpty()) {
                LanSharingLogics.parseArpFile(arpLines.joinToString("\n"))
                    .filter { isValidHotspotIp(it) }
                    .forEach { allIps += it }
            }
        }

        if (combinedDumps.isEmpty()) {
            readArp().filter { isValidHotspotIp(it) }.forEach { allIps += it }

            if (!localNeighBlocked.get()) {
                val localNeigh = try {
                    val p = ProcessBuilder("ip", "-4", "neigh", "show").redirectErrorStream(true).start()
                    try {
                        val readFuture = neighExecutor.submit<String> {
                            p.inputStream.bufferedReader().use { it.readText() }
                        }
                        val exited = p.waitFor(2, TimeUnit.SECONDS)
                        if (!exited) {
                            p.destroyForcibly()
                            readFuture.cancel(true)
                            null
                        } else {
                            val out = runCatching { readFuture.get(1, TimeUnit.SECONDS) }.getOrDefault("")
                            val code = p.exitValue()
                            if (code != 0 ||
                                out.contains("Permission denied", ignoreCase = true) ||
                                out.contains("Cannot bind", ignoreCase = true)
                            ) {
                                localNeighBlocked.set(true)
                                null
                            } else {
                                out
                            }
                        }
                    } finally {
                        runCatching { p.outputStream.close() }
                        runCatching { p.inputStream.close() }
                        runCatching { p.errorStream.close() }
                        if (p.isAlive) runCatching { p.destroyForcibly() }
                    }
                } catch (_: Throwable) {
                    localNeighBlocked.set(true)
                    null
                }
                if (!localNeigh.isNullOrBlank()) {
                    ingestEntries(LanSharingLogics.parseIpNeigh(localNeigh))
                }
            }
        }

        return NeighborSnapshot(
            allIps = allIps.toList(),
            activeIps = activeIps,
            hotspotIps = hotspotIps,
        )
    }

    /**
     * Testable core of the Clash `/connections` poller: builds the URL + optional Bearer header
     * and returns null on any transport exception.
     */
    fun pollConnectionsWith(
        rawPort: Int,
        secret: String,
        transport: (url: String, authorizationHeader: String?) -> String?,
    ): String? {
        val url = buildClashConnectionsUrl(rawPort)
        val trimmedSecret = secret.trim()
        val auth = if (trimmedSecret.isNotEmpty()) "Bearer $trimmedSecret" else null
        return runCatching { transport(url, auth) }.getOrNull()
    }

    fun fetchConnections(url: String = clashUrl(), secret: String = DataStore.coreBoxClashApiSecret): String? {
        val trimmedSecret = secret.trim()
        val auth = if (trimmedSecret.isNotEmpty()) "Bearer $trimmedSecret" else null
        return runCatching {
            val reqBuilder = Request.Builder().url(url).get()
            if (auth != null) {
                reqBuilder.header("Authorization", auth)
            }
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }
}
