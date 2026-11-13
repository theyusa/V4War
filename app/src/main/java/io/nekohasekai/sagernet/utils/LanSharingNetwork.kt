package io.nekohasekai.sagernet.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SettingsRegistry
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
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

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

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

    fun isHotspotInterfaceName(name: String): Boolean {
        if (isCellularOrVirtualInterface(name)) return false
        val n = name.lowercase()
        return n.startsWith("ap") ||
            n == "wlan1" ||
            n.startsWith("swlan") ||
            n.startsWith("softap") ||
            n.startsWith("wigig") ||
            n.contains("hotspot") ||
            n.contains("rndis")
    }

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

    fun isHotspotActive(context: Context): Boolean {
        val appCtx = context.applicationContext
        val wifiManager = appCtx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val apEnabled = runCatching {
            val method = wifiManager?.javaClass?.getDeclaredMethod("isWifiApEnabled")
            method?.isAccessible = true
            method?.invoke(wifiManager) as? Boolean
        }.getOrNull()
        if (apEnabled == true) return true

        val apState = runCatching {
            val method = wifiManager?.javaClass?.getDeclaredMethod("getWifiApState")
            method?.isAccessible = true
            method?.invoke(wifiManager) as? Int
        }.getOrNull()
        if (apState == 12 || apState == 13) return true

        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val tethered = getTetheredIfaces(cm)
        if (tethered.isNotEmpty()) return true

        return findHotspotIp(context) != null
    }

    fun getTetheredIfaces(cm: ConnectivityManager?): List<String> = runCatching {
        val method = cm?.javaClass?.getDeclaredMethod("getTetheredIfaces")
        method?.isAccessible = true
        (method?.invoke(cm) as? Array<*>)?.filterIsInstance<String>().orEmpty()
    }.getOrDefault(emptyList())

    fun findHotspotIp(context: Context): String? = findHotspotCandidates(context).firstOrNull()?.ip

    /**
     * Finds every interface that looks like the hotspot side. Pure and unit-testable via
     * [hotspotCandidates]; the Android layer only collects the input snapshots.
     */
    fun findHotspotCandidates(context: Context): List<HotspotCandidate> {
        val appCtx = context.applicationContext
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val tetheredIfaces = getTetheredIfaces(cm)

        // Every interface ConnectivityManager itself knows about (Wi-Fi client, cellular, VPN,
        // Ethernet): they belong to other networks and are never the hotspot side.
        val knownIfaces = runCatching {
            cm?.allNetworks?.toList().orEmpty()
                .mapNotNull { network -> cm?.getLinkProperties(network)?.interfaceName }
                .map { it.lowercase() }
                .toSet()
        }.getOrDefault(emptySet())

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
     * lead the result; then the remaining interfaces follow in OS order. No IP pattern and no
     * default address is ever guessed.
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

    private val procArpBlocked = AtomicBoolean(false)
    private val procTcpBlocked = AtomicBoolean(false)
    private val localNeighBlocked = AtomicBoolean(false)

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
                    val out = p.inputStream.bufferedReader().readText()
                    val code = p.waitFor()
                    if (code != 0 ||
                        out.contains("Permission denied", ignoreCase = true) ||
                        out.contains("Cannot bind", ignoreCase = true)
                    ) {
                        localNeighBlocked.set(true)
                        null
                    } else {
                        out
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
