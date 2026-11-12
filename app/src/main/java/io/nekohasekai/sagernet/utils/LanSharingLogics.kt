package io.nekohasekai.sagernet.utils

import io.nekohasekai.sagernet.database.SettingValidators
import io.nekohasekai.sagernet.outbound.link.LinkBuilder
import kotlin.math.abs
import org.json.JSONObject
import java.security.SecureRandom

/**
 * One row of the LAN Sharing device list; entries are keyed by IP.
 */
data class LanClientDevice(
    val ip: String,
    val activeConnections: Int,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val lastHost: String?,
    val lastSeenMs: Long,
    val isHotspot: Boolean,
)

/** Parsed entry from `ip -4 neigh show` or `/proc/net/arp`. */
data class NeighborEntry(
    val ip: String,
    val iface: String,
    val mac: String?,
    val state: String,
) {
    val isActive: Boolean
        get() = state.equals("REACHABLE", ignoreCase = true) ||
            state.equals("DELAY", ignoreCase = true) ||
            state.equals("PROBE", ignoreCase = true) ||
            state.equals("PERMANENT", ignoreCase = true)
}

/**
 * The pure decisions of the LAN Sharing client list: turning the ARP table and the Clash API /connections
 * payload into device rows, and merging them with the caller's history into one sorted view.
 *
 * Nothing here touches Android: it runs in JVM unit tests and is fed by [LanSharingNetwork].
 */
object LanSharingLogics {

    /** A quiet device may still be shown for this long after its last sighting. */
    const val IDLE_MS: Long = 600_000L // 10 minutes

    /**
     * Normalizes bracketed IPv6 or IPv4-mapped IPv6 addresses (e.g. `[::ffff:192.168.43.52]`)
     * into dotted IPv4 when applicable. Pure and unit-testable.
     */
    fun normalizeIp(rawIp: String): String {
        val noBrackets = rawIp.trim().removePrefix("[").substringBefore('%').removeSuffix("]")
        return if (noBrackets.startsWith("::ffff:", ignoreCase = true)) {
            noBrackets.substring(7)
        } else {
            noBrackets
        }
    }

    /**
     * Parses `/proc/net/arp`. Ends with the device IP; rows with an all-zero MAC are hardware assertions, not
     * devices, and loopback / link-local / our own VPN range rows are not LAN clients either.
     */
    fun parseArpFile(text: String): List<String> {
        return text.lines().drop(1).mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 4) return@mapNotNull null
            val ip = normalizeIp(parts[0])
            val mac = parts[3]
            val isReal = !parts.all { it == "IP address" } &&
                mac.isNotBlank() && mac != "00:00:00:00:00:00"
            val usable = !isLocalSource(ip) && !ip.startsWith("169.254.")
            if (isReal && usable) ip else null
        }.distinct()
    }

    /**
     * Parses `ip -4 neigh show` output (and optional `---ARP---` section) into structured [NeighborEntry] rows.
     * Excludes FAILED/INCOMPLETE/local/VPN addresses. Pure and unit-testable.
     */
    fun parseIpNeigh(text: String): List<NeighborEntry> {
        val result = ArrayList<NeighborEntry>()
        val seen = HashSet<String>()
        var inArpSection = false
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line == "---ARP---") {
                inArpSection = true
                continue
            }
            if (inArpSection) {
                if (line.startsWith("IP address")) continue
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 6) continue
                val ip = normalizeIp(parts[0])
                val mac = parts[3]
                val iface = parts[5]
                if (mac.isBlank() || mac == "00:00:00:00:00:00") continue
                if (isLocalSource(ip) || ip.startsWith("169.254.") || ipv4ToInt(ip) == null) continue
                if (seen.add(ip)) {
                    result.add(NeighborEntry(ip = ip, iface = iface, mac = mac, state = "STALE"))
                }
                continue
            }

            // Format: "<ip> dev <iface> [lladdr <mac>] <STATE>"
            val tokens = line.split(Regex("\\s+"))
            if (tokens.size < 3) continue
            val ip = normalizeIp(tokens[0])
            if (isLocalSource(ip) || ip.startsWith("169.254.") || ipv4ToInt(ip) == null) continue
            val devIdx = tokens.indexOf("dev")
            val iface = if (devIdx >= 0 && devIdx + 1 < tokens.size) tokens[devIdx + 1] else continue
            val llIdx = tokens.indexOf("lladdr")
            val mac = if (llIdx >= 0 && llIdx + 1 < tokens.size) tokens[llIdx + 1] else null
            val state = tokens.lastOrNull() ?: ""
            if (state.equals("FAILED", ignoreCase = true) || state.equals("INCOMPLETE", ignoreCase = true)) {
                continue
            }
            if (mac == null && state.equals("NONE", ignoreCase = true)) continue
            if (seen.add(ip)) {
                result.add(NeighborEntry(ip = ip, iface = iface, mac = mac, state = state))
            }
        }
        return result
    }

    /**
     * Parses kernel `/proc/net/tcp` or `/proc/net/tcp6` text for sockets whose `local_address` port
     * matches [listenPort] and are not in LISTEN (`0A`) state. Picks up manual proxy clients even
     * when Clash `/connections` is off or between short-lived proxy requests.
     */
    fun parseProcNetTcpRemotes(procTcpText: String, listenPort: Int): List<String> {
        if (listenPort <= 0 || procTcpText.isBlank()) return emptyList()
        val targetPortHex = "%04X".format(listenPort)
        val result = LinkedHashSet<String>()
        for (rawLine in procTcpText.lineSequence().drop(1)) {
            val parts = rawLine.trim().split(Regex("\\s+"))
            if (parts.size < 4) continue
            val local = parts[1]
            val remote = parts[2]
            val state = parts[3]
            if (state.equals("0A", ignoreCase = true)) continue // skip LISTEN socket
            val localPortHex = local.substringAfter(':', "")
            if (!localPortHex.equals(targetPortHex, ignoreCase = true)) continue
            val remoteHex = remote.substringBefore(':', "")
            val ip = decodeProcNetHexIp(remoteHex) ?: continue
            if (!isLocalSource(ip) && !ip.startsWith("169.254.")) {
                result.add(ip)
            }
        }
        return result.toList()
    }

    private fun decodeProcNetHexIp(hex: String): String? {
        val clean = hex.trim()
        val ipv4Hex = when (clean.length) {
            8 -> clean
            // IPv4-mapped IPv6 in little-endian 32-bit words: 00000000 00000000 FFFF0000 <LE-IPv4>
            32 -> if (clean.startsWith("0000000000000000FFFF0000", ignoreCase = true)) {
                clean.substring(24, 32)
            } else {
                return null
            }
            else -> return null
        }
        val word = ipv4Hex.toLongOrNull(16) ?: return null
        if (word == 0L) return null
        val b0 = (word and 0xFF).toInt()
        val b1 = ((word shr 8) and 0xFF).toInt()
        val b2 = ((word shr 16) and 0xFF).toInt()
        val b3 = ((word shr 24) and 0xFF).toInt()
        return "$b0.$b1.$b2.$b3"
    }

    /**
     * Parses the Clash API `/connections` body: per source IP the number of open connections, summed traffic
     * and the most recent destination host. Empty, malformed or connection-less bodies yield an empty list.
     * Local sources (loopback, the tun range and, at merge time, the phone's own addresses) are never devices.
     */
    fun parseConnections(json: String): List<LanClientDevice> {
        val root = runCatching { JSONObject().put("body", json) }.getOrNull()
        val body = root?.optString("body")
        if (body.isNullOrEmpty()) return emptyList()
        val tidy = body.trim()
        if (tidy.isEmpty()) return emptyList()
        val parsed = runCatching { JSONObject(tidy) }.getOrNull() ?: return emptyList()
        val connections = parsed.optJSONArray("connections") ?: return emptyList()

        // Aggregate traffic per source IP while remembering the last destination host.
        data class Acc(var active: Int, var up: Long, var down: Long, var lastHost: String?)
        val acc = LinkedHashMap<String, Acc>()
        for (i in 0 until connections.length()) {
            val item = connections.getJSONObject(i)
            val meta = item.optJSONObject("metadata") ?: continue
            val rawIp = meta.optString("sourceIP")
            val ip = normalizeIp(rawIp)
            if (ip.isEmpty() || isLocalSource(ip)) continue
            val entry = acc.getOrPut(ip) { Acc(0, 0L, 0L, null) }
            entry.active += 1
            entry.up += item.optLong("upload", 0L)
            entry.down += item.optLong("download", 0L)
            item.optString("host").takeIf { it.isNotEmpty() }?.let { entry.lastHost = it }
            meta.optString("host").takeIf { it.isNotEmpty() }?.let { entry.lastHost = it }
        }
        return acc.map { (ip, a) ->
            LanClientDevice(ip, a.active, a.up, a.down, a.lastHost, lastSeenMs = 0L, isHotspot = false)
        }
    }

    /**
     * Sources that are not LAN clients: loopback (`127.*`, `::1`), the VPN/tun range (`172.19.*`, `192.0.2.*`)
     * and, via [localIps], the phone's own hotspot / Wi-Fi addresses. Pure and unit-testable.
     */
    fun isLocalSource(ip: String, localIps: Set<String> = emptySet()): Boolean {
        val norm = normalizeIp(ip)
        return norm in localIps ||
            norm.startsWith("127.") ||
            norm == "::1" ||
            norm.startsWith("172.19.") ||
            norm.startsWith("192.0.2.")
    }

    /** The /24 default when the OS does not report a hotspot prefix length. */
    private const val DEFAULT_PREFIX_BITS = 24

    /**
     * Whether both IPv4 addresses share one subnet masked to [prefixLength] bits. A null or
     * out-of-range prefix falls back to the /24 default ([DEFAULT_PREFIX_BITS]); `0..32` is the
     * accepted range. Anything that is not a dotted IPv4 address never shares a subnet. Pure and
     * unit-testable.
     */
    fun isSameSubnet(first: String, second: String, prefixLength: Int?): Boolean {
        val a = ipv4ToInt(first) ?: return false
        val b = ipv4ToInt(second) ?: return false
        if (a == 0L) return false
        val prefix = (prefixLength ?: DEFAULT_PREFIX_BITS).coerceIn(0, 32)
        val mask = (-1L shl (32 - prefix)) and 0xFFFF_FFFFL
        return (a and mask) == (b and mask)
    }

    /**
     * Whether both IPv4 addresses share the same /24 prefix. Anything that is not a dotted IPv4 address
     * never shares a subnet. Pure and unit-testable.
     */
    fun isSameSubnet24(first: String, second: String): Boolean =
        isSameSubnet(first, second, DEFAULT_PREFIX_BITS)

    /** Packs a dotted-quad IPv4 string into a Long (0..255 per octet), or null when it is not an IPv4 address. */
    private fun ipv4ToInt(ip: String): Long? {
        val parts = normalizeIp(ip).split(".")
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            value = value shl 8 or octet.toLong()
        }
        return value
    }

    /**
     * Merges a history map (rows the caller kept from earlier polls) with ARP/neighbor entries and fresh Clash
     * entries, deduplicating by IP with Clash winning over ARP. Rows past [IDLE_MS] are dropped; the rest are
     * shown with active connections first and the most recently seen afterwards. Local sources ([localIps]
     * plus the static loopback and tun ranges) never become devices.
     *
     * Optional [activePeerIps] marks clients that are actively connected via Shizuku/Root tethering (e.g.
     * REACHABLE hotspot neighbors) or live manual-proxy TCP sockets when Clash `/connections` shows 0 flows.
     * Optional [hotspotHintIps] marks IPs known to sit on a hotspot/tethering interface even before subnet
     * metadata settles.
     */
    @JvmOverloads
    fun mergeDevices(
        history: Map<String, LanClientDevice>,
        arpIps: List<String>,
        clash: List<LanClientDevice>,
        hotspotIp: String?,
        hotspotPrefix: Int?,
        localIps: Set<String>,
        nowMs: Long,
        activePeerIps: Set<String> = emptySet(),
        hotspotHintIps: Set<String> = emptySet(),
    ): List<LanClientDevice> {
        val normalizedLocals = localIps.mapTo(HashSet()) { normalizeIp(it) }
        val normalizedHotspot = hotspotIp?.let { normalizeIp(it) }
        val normalizedActivePeers = activePeerIps.mapTo(HashSet()) { normalizeIp(it) }
        val normalizedHotspotHints = hotspotHintIps.mapTo(HashSet()) { normalizeIp(it) }
        val byIp = LinkedHashMap<String, LanClientDevice>()

        fun isHotspotFor(ip: String): Boolean =
            isHotspotClient(ip, normalizedHotspot, hotspotPrefix) || ip in normalizedHotspotHints

        arpIps.forEach { rawIp ->
            val ip = normalizeIp(rawIp)
            if (isLocalSource(ip, normalizedLocals)) return@forEach
            val historyRow = history[ip]
            val peerActive = ip in normalizedActivePeers
            byIp[ip] = LanClientDevice(
                ip = ip,
                activeConnections = if (peerActive) 1 else 0,
                uploadBytes = historyRow?.uploadBytes ?: 0L,
                downloadBytes = historyRow?.downloadBytes ?: 0L,
                lastHost = historyRow?.lastHost,
                lastSeenMs = if (peerActive) nowMs else (historyRow?.lastSeenMs ?: nowMs),
                isHotspot = isHotspotFor(ip),
            )
        }
        normalizedActivePeers.forEach { ip ->
            if (isLocalSource(ip, normalizedLocals)) return@forEach
            if (ip !in byIp) {
                val historyRow = history[ip]
                byIp[ip] = LanClientDevice(
                    ip = ip,
                    activeConnections = 1,
                    uploadBytes = historyRow?.uploadBytes ?: 0L,
                    downloadBytes = historyRow?.downloadBytes ?: 0L,
                    lastHost = historyRow?.lastHost,
                    lastSeenMs = nowMs,
                    isHotspot = isHotspotFor(ip),
                )
            }
        }
        history.forEach { (rawIp, row) ->
            val ip = normalizeIp(rawIp)
            if (isLocalSource(ip, normalizedLocals)) return@forEach
            if (row.lastSeenMs >= nowMs - IDLE_MS) {
                // Idle rows are re-classified against the current hotspot subnet: the hotspot
                // interface (and the whole subnet) changes between openings.
                byIp.putIfAbsent(
                    ip,
                    row.copy(
                        ip = ip,
                        activeConnections = 0,
                        isHotspot = isHotspotFor(ip),
                    ),
                )
            }
        }
        clash.forEach { rawEntry ->
            val ip = normalizeIp(rawEntry.ip)
            if (isLocalSource(ip, normalizedLocals)) return@forEach
            val prev = byIp[ip]
            byIp[ip] = rawEntry.copy(
                ip = ip,
                activeConnections = rawEntry.activeConnections.coerceAtLeast(if (ip in normalizedActivePeers) 1 else 0),
                uploadBytes = rawEntry.uploadBytes.coerceAtLeast(prev?.uploadBytes ?: 0L),
                downloadBytes = rawEntry.downloadBytes.coerceAtLeast(prev?.downloadBytes ?: 0L),
                lastHost = rawEntry.lastHost ?: prev?.lastHost,
                lastSeenMs = rawEntry.lastSeenMs.takeIf { it > 0L } ?: nowMs,
                isHotspot = isHotspotFor(ip),
            )
        }
        return byIp.values
            .filter { nowMs - it.lastSeenMs < IDLE_MS || it.activeConnections > 0 }
            .sortedWith(compareByDescending<LanClientDevice> { it.activeConnections > 0 }
                .thenByDescending { it.lastSeenMs })
    }

    /**
     * A hotspot client is any IPv4 device that shares the hotspot interface's subnet. The subnet is
     * the interface's own reported prefix length when the OS gives one; unknown prefixes fall back
     * to the /24 default inside [isSameSubnet].
     */
    private fun isHotspotClient(ip: String, hotspotIp: String?, hotspotPrefix: Int?): Boolean =
        hotspotIp != null && isSameSubnet(ip, hotspotIp, hotspotPrefix)

    data class ClashApiAutoEnable(val newRawPort: Int, val changed: Boolean)

    /**
     * When LAN Sharing turns on and Clash API is off (`<= 0`), flips the sign-encoded port positive so
     * `/connections` becomes reachable after a service reload.
     */
    fun ensureClashApiEnabledPort(rawPort: Int): ClashApiAutoEnable {
        if (rawPort > 0) return ClashApiAutoEnable(rawPort, changed = false)
        val positive = abs(rawPort).takeIf { it in 1..65535 } ?: 9090
        return ClashApiAutoEnable(positive, changed = true)
    }

    /**
     * Validates the user-entered port string in the Hotspot Gateway configuration dialog.
     * Blank or out-of-range strings return null and never fall back to a default port.
     */
    fun validateDialogPort(rawInput: String): Int? {
        val port = rawInput.trim().toIntOrNull() ?: return null
        return port.takeIf { SettingValidators.isPort(it) }
    }

    /**
     * Pure status classifier used by the LAN Sharing hero/summary view so any persisted [HotspotGatewaySnapshot]
     * (including a stale PROBING record after process restart) maps cleanly without throwing.
     */
    fun statusSummaryKind(enabled: Boolean, snapshot: HotspotGatewaySnapshot): String {
        if (!enabled) return "DISABLED"
        return snapshot.status.name
    }

    /**
     * Pure decision: a blank Clash API secret needs a fresh one. An existing, non-blank secret is
     * never overwritten - it is the key the running core already answers with.
     */
    fun clashApiSecretShouldBeGenerated(currentSecret: String): Boolean = currentSecret.isBlank()

    /**
     * Fresh 64-character lowercase hex (32 SecureRandom bytes) for the core's Clash API secret.
     * Pure JVM (SecureRandom has no Android dependency), so the size and alphabet are unit-testable.
     */
    fun generateClashApiSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(HEX_DIGITS[(b.toInt() shr 4) and 0xF]).append(HEX_DIGITS[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    // The same lowercase hex alphabet the config fingerprint uses.
    private val HEX_DIGITS = "0123456789abcdef"

    /**
     * The display form of a socks5:// link: the password half of a user:pass@ authority becomes
     * "••••" so the screen never shows the credential. Copy and share must keep sending the full
     * link (the [buildSocks5ShareLink] output). Links without credentials, garbage and empty input
     * are returned unchanged. Pure and unit-testable.
     */
    fun maskSocks5Link(link: String): String {
        val authorityStart = link.indexOf("://")
        val at = link.lastIndexOf('@')
        if (authorityStart < 0 || at < authorityStart + 3) return link
        val auth = link.substring(authorityStart + 3, at)
        val colon = auth.indexOf(':')
        if (colon < 0) return link
        return link.substring(0, authorityStart + 3) + auth.substring(0, colon) + ":\u2022\u2022\u2022\u2022" + link.substring(at)
    }

    /**
     * Builds a shareable `socks5://[user[:pass]@]host:port[?uot=1][#remark]` URI compatible with
     * [io.nekohasekai.sagernet.outbound.types.Socks.parseFromLink]. Username is trimmed once; password is kept
     * verbatim. Returns an empty string when [host] is blank or [port] is invalid.
     */
    fun buildSocks5ShareLink(
        host: String,
        port: Int,
        username: String,
        password: String,
        uot: Boolean,
        remark: String = "",
    ): String {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty() || !SettingValidators.isPort(port)) return ""
        val url = LinkBuilder("socks5")
        url.host = cleanHost
        url.port = port
        val cleanUser = username.trim()
        if (cleanUser.isNotEmpty()) url.setUserName(cleanUser)
        if (password.isNotEmpty()) url.setPassword(password)
        if (uot) url.addQueryItem("uot", "1")
        val cleanRemark = remark.trim()
        if (cleanRemark.isNotEmpty()) url.fragment = cleanRemark
        return url.build()
    }
}
