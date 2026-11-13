package io.nekohasekai.sagernet.utils.toolbox

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.nekohasekai.sagernet.bg.CoreRuntime
import io.nekohasekai.sagernet.bg.CoreServiceClient
import io.nekohasekai.sagernet.bg.proto.CoreConfigs
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.appUserAgent
import io.nekohasekai.sagernet.ktx.newHttpClient
import io.throneproj.mobile.Mobile
import io.throneproj.mobile.ResolveRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.Locale

enum class IpCategory {
    PUBLIC,
    PRIVATE,
    LOOPBACK,
    CGNAT,
    FAKE_IP,
    LINK_LOCAL,
    MULTICAST,
    RESERVED,
}

data class IpGeoInfo(
    val ip: String,
    val version: Int,
    val category: IpCategory,
    val country: String = "",
    val countryCode: String = "",
    val region: String = "",
    val city: String = "",
    val isp: String = "",
    val org: String = "",
    val asn: String = "",
    val timezone: String = "",
    val source: String = "",
    val error: String = "",
)

data class IpTargetLookupResult(
    val query: String,
    val normalizedTarget: String,
    val isIpLiteral: Boolean,
    val resolvedVia: String,
    val results: List<IpGeoInfo>,
    val error: String = "",
)

data class ExitComparisonResult(
    val vpnConnected: Boolean,
    val proxyExit: IpGeoInfo?,
    val directExit: IpGeoInfo?,
)

object IpLookupEngine {

    private val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")

    /** Strips URL schemes, credentials, paths, and ports cleanly to yield host or IP literal. */
    fun normalizeTarget(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.contains("://")) {
            runCatching {
                val host = URI(trimmed).host
                if (!host.isNullOrBlank()) return host.trim('[', ']')
            }
        }
        if (trimmed.startsWith("[")) {
            val endBracket = trimmed.indexOf(']')
            if (endBracket > 1) return trimmed.substring(1, endBracket)
        }
        val withoutPath = trimmed.substringBefore('/').substringBefore('?').substringBefore('#')
        val colonCount = withoutPath.count { it == ':' }
        if (colonCount == 1) {
            val candidateHost = withoutPath.substringBefore(':')
            val candidatePort = withoutPath.substringAfter(':')
            if (candidatePort.all { it.isDigit() }) {
                return candidateHost
            }
        }
        return withoutPath.trim('[', ']')
    }

    fun isIpLiteral(host: String): Boolean {
        if (host.isBlank()) return false
        if (ipv4Regex.matches(host)) {
            return host.split('.').all { it.toIntOrNull() in 0..255 }
        }
        return host.contains(':') && runCatching { InetAddress.getByName(host) is Inet6Address }.getOrDefault(false)
    }

    fun classifyIp(ip: String): Pair<Int, IpCategory> {
        val addr = runCatching { InetAddress.getByName(ip.trim()) }.getOrNull()
            ?: return 4 to IpCategory.RESERVED
        val bytes = addr.address
        if (addr is Inet4Address && bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val category = when {
                b0 == 127 -> IpCategory.LOOPBACK
                b0 == 10 -> IpCategory.PRIVATE
                b0 == 172 && b1 in 16..31 -> IpCategory.PRIVATE
                b0 == 192 && b1 == 168 -> IpCategory.PRIVATE
                b0 == 100 && b1 in 64..127 -> IpCategory.CGNAT
                b0 == 198 && (b1 == 18 || b1 == 19) -> IpCategory.FAKE_IP
                b0 == 169 && b1 == 254 -> IpCategory.LINK_LOCAL
                b0 in 224..239 -> IpCategory.MULTICAST
                b0 == 0 || b0 >= 240 -> IpCategory.RESERVED
                else -> IpCategory.PUBLIC
            }
            return 4 to category
        }
        if (addr is Inet6Address && bytes.size == 16) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val category = when {
                addr.isLoopbackAddress -> IpCategory.LOOPBACK
                addr.isLinkLocalAddress -> IpCategory.LINK_LOCAL
                addr.isMulticastAddress -> IpCategory.MULTICAST
                addr.isSiteLocalAddress -> IpCategory.PRIVATE
                (b0 and 0xFE) == 0xFC -> IpCategory.PRIVATE // fc00::/7 ULA
                b0 == 0xFC && b1 == 0x00 -> IpCategory.FAKE_IP
                else -> IpCategory.PUBLIC
            }
            return 6 to category
        }
        return 4 to IpCategory.RESERVED
    }

    fun parseIpWhoJson(body: String, fallbackIp: String = ""): IpGeoInfo? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (!json.optBoolean("success", true)) return null
        val ip = json.optString("ip").ifBlank { fallbackIp }
        if (ip.isBlank()) return null
        val (ver, cat) = classifyIp(ip)
        val conn = json.optJSONObject("connection")
        val tz = json.optJSONObject("timezone")
        val asnInt = conn?.optInt("asn", 0) ?: 0
        val asnStr = if (asnInt > 0) "AS$asnInt" else ""
        return IpGeoInfo(
            ip = ip,
            version = ver,
            category = cat,
            country = json.optString("country"),
            countryCode = json.optString("country_code"),
            region = json.optString("region"),
            city = json.optString("city"),
            isp = conn?.optString("isp").orEmpty(),
            org = conn?.optString("org").orEmpty(),
            asn = asnStr,
            timezone = tz?.optString("id").orEmpty(),
            source = "ipwho.is",
        )
    }

    fun parseGeoJsJson(body: String, fallbackIp: String = ""): IpGeoInfo? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val ip = json.optString("ip").ifBlank { fallbackIp }
        if (ip.isBlank()) return null
        val (ver, cat) = classifyIp(ip)
        val asnVal = json.optString("asn").takeIf { it.isNotBlank() && it != "0" }?.let {
            if (it.startsWith("AS", ignoreCase = true)) it.uppercase(Locale.US) else "AS$it"
        }.orEmpty()
        val org = json.optString("organization_name").ifBlank { json.optString("organization") }
        return IpGeoInfo(
            ip = ip,
            version = ver,
            category = cat,
            country = json.optString("country"),
            countryCode = json.optString("country_code"),
            region = json.optString("region"),
            city = json.optString("city"),
            isp = org,
            org = org,
            asn = asnVal,
            timezone = json.optString("timezone"),
            source = "geojs.io",
        )
    }

    fun parseIpSbJson(body: String, fallbackIp: String = ""): IpGeoInfo? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val ip = json.optString("ip").ifBlank { fallbackIp }
        if (ip.isBlank()) return null
        val (ver, cat) = classifyIp(ip)
        val asnNum = json.optInt("asn", 0)
        val asn = if (asnNum > 0) "AS$asnNum" else ""
        val org = json.optString("organization").ifBlank { json.optString("asn_organization") }
        return IpGeoInfo(
            ip = ip,
            version = ver,
            category = cat,
            country = json.optString("country"),
            countryCode = json.optString("country_code"),
            region = json.optString("region"),
            city = json.optString("city"),
            isp = json.optString("isp").ifBlank { org },
            org = org,
            asn = asn,
            timezone = json.optString("timezone"),
            source = "ip.sb",
        )
    }

    fun parseIpInfoJson(body: String, fallbackIp: String = ""): IpGeoInfo? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (json.has("bogon") && json.optBoolean("bogon")) {
            val ip = json.optString("ip").ifBlank { fallbackIp }
            if (ip.isBlank()) return null
            val (ver, cat) = classifyIp(ip)
            return IpGeoInfo(ip = ip, version = ver, category = cat, source = "ipinfo.io")
        }
        val ip = json.optString("ip").ifBlank { fallbackIp }
        if (ip.isBlank()) return null
        val (ver, cat) = classifyIp(ip)
        val orgRaw = json.optString("org").trim()
        val firstToken = orgRaw.substringBefore(' ', "")
        val asn = if (firstToken.startsWith("AS", ignoreCase = true) && firstToken.length > 2) firstToken else ""
        val orgName = if (asn.isNotEmpty()) orgRaw.substringAfter(' ', orgRaw).trim() else orgRaw
        val countryCode = json.optString("country")
        return IpGeoInfo(
            ip = ip,
            version = ver,
            category = cat,
            country = countryCode,
            countryCode = countryCode,
            region = json.optString("region"),
            city = json.optString("city"),
            isp = orgName,
            org = orgName,
            asn = asn,
            timezone = json.optString("timezone"),
            source = "ipinfo.io",
        )
    }

    fun parseIfconfigCoJson(body: String, fallbackIp: String = ""): IpGeoInfo? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val ip = json.optString("ip").ifBlank { fallbackIp }
        if (ip.isBlank()) return null
        val (ver, cat) = classifyIp(ip)
        val asn = json.optString("asn")
        val org = json.optString("asn_org")
        return IpGeoInfo(
            ip = ip,
            version = ver,
            category = cat,
            country = json.optString("country"),
            countryCode = json.optString("country_iso"),
            region = json.optString("region_name"),
            city = json.optString("city"),
            isp = org,
            org = org,
            asn = asn,
            timezone = json.optString("time_zone"),
            source = "ifconfig.co",
        )
    }

    fun parseCloudflareTrace(body: String): IpGeoInfo? {
        var ip = ""
        var loc = ""
        for (line in body.lines()) {
            val t = line.trim()
            if (t.startsWith("ip=")) ip = t.substringAfter("ip=").trim()
            if (t.startsWith("loc=")) loc = t.substringAfter("loc=").trim()
        }
        if (ip.isBlank()) return null
        val (ver, cat) = classifyIp(ip)
        return IpGeoInfo(
            ip = ip,
            version = ver,
            category = cat,
            countryCode = loc,
            country = loc,
            source = "cloudflare",
        )
    }

    private fun parsePlainIp(body: String, source: String): IpGeoInfo? {
        val ip = body.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        if (!isIpLiteral(ip)) return null
        val (ver, cat) = classifyIp(ip)
        return IpGeoInfo(ip = ip, version = ver, category = cat, source = source)
    }

    suspend fun resolveViaCore(domain: String): Pair<List<String>, String> {
        val localRunning = CoreRuntime.running?.instance
        if (localRunning != null) {
            return runCatching {
                val req = ResolveRequest().apply { addDomain(domain) }
                val iter = Mobile.resolveDomains(localRunning, CoreRuntime.platform, req)
                val ips = mutableListOf<String>()
                var err = ""
                if (iter.hasNext()) {
                    val res = iter.next()
                    err = res.error.orEmpty()
                    val addrs = res.addresses()
                    while (addrs.hasNext()) {
                        addrs.next().takeIf { it.isNotBlank() }?.let { ips.add(it) }
                    }
                }
                ips.distinct() to err
            }.getOrElse { emptyList<String>() to (it.message ?: "Resolve failed") }
        }
        return runCatching {
            val raw = CoreServiceClient.call { it.resolveDomain(domain) }
            val json = JSONObject(raw)
            val arr = json.optJSONArray("ips")
            val ips = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val ip = arr.optString(i).trim()
                        if (ip.isNotEmpty()) add(ip)
                    }
                }
            }.distinct()
            ips to json.optString("error")
        }.getOrElse { emptyList<String>() to (it.message ?: "Core IPC resolve failed") }
    }

    suspend fun queryProxyExit(): IpGeoInfo? = withContext(Dispatchers.IO) {
        if (!DataStore.serviceState.connected) null
        else queryOwnExit(buildClient(viaProxy = true, physicalNetwork = null))
    }

    suspend fun queryDirectExit(connectivity: ConnectivityManager?): IpGeoInfo = withContext(Dispatchers.IO) {
        queryDirectOwnExit(connectivity, DataStore.serviceState.connected)
    }

    suspend fun compareExits(connectivity: ConnectivityManager?): ExitComparisonResult =
        withContext(Dispatchers.IO) {
            val vpnConnected = DataStore.serviceState.connected
            coroutineScope {
                val proxyDeferred = async {
                    if (!vpnConnected) null else queryOwnExit(buildClient(viaProxy = true, physicalNetwork = null))
                }
                val directDeferred = async {
                    queryDirectOwnExit(connectivity, vpnConnected)
                }
                ExitComparisonResult(
                    vpnConnected = vpnConnected,
                    proxyExit = proxyDeferred.await(),
                    directExit = directDeferred.await(),
                )
            }
        }

    private fun queryDirectOwnExit(connectivity: ConnectivityManager?, vpnConnected: Boolean): IpGeoInfo {
        val candidates = candidatePhysicalNetworks(connectivity).take(3)
        for (net in candidates) {
            val info = queryOwnExit(buildClient(viaProxy = false, physicalNetwork = net))
            if (info.error.isBlank() && info.ip.isNotBlank()) return info
        }
        if (!vpnConnected) {
            val unbound = queryOwnExit(buildClient(viaProxy = false, physicalNetwork = null))
            if (unbound.error.isBlank() && unbound.ip.isNotBlank()) return unbound
        }
        return IpGeoInfo(
            ip = "",
            version = 4,
            category = IpCategory.RESERVED,
            error = "Unable to reach IP lookup endpoints",
        )
    }

    suspend fun lookupTarget(
        rawQuery: String,
        viaProxy: Boolean,
        connectivity: ConnectivityManager?,
    ): IpTargetLookupResult = withContext(Dispatchers.IO) {
        val target = normalizeTarget(rawQuery)
        if (target.isBlank()) {
            return@withContext IpTargetLookupResult(
                query = rawQuery,
                normalizedTarget = "",
                isIpLiteral = false,
                resolvedVia = "",
                results = emptyList(),
                error = "Invalid host or IP",
            )
        }

        val vpnConnected = DataStore.serviceState.connected
        val useProxy = viaProxy && vpnConnected
        val candidates = if (!useProxy) candidatePhysicalNetworks(connectivity) else emptyList()
        val primaryPhysical = candidates.firstOrNull()
        val primaryClient = buildClient(viaProxy = useProxy, physicalNetwork = primaryPhysical)

        if (isIpLiteral(target)) {
            val info = lookupIpDetailsWithFallback(primaryClient, target, useProxy, vpnConnected, candidates)
            return@withContext IpTargetLookupResult(
                query = rawQuery,
                normalizedTarget = target,
                isIpLiteral = true,
                resolvedVia = if (useProxy) "Proxy" else "Direct",
                results = listOf(info),
            )
        }

        val resolvedIps = mutableListOf<String>()
        var resolvedVia = "System DNS"
        if (useProxy) {
            val (coreIps, _) = resolveViaCore(target)
            if (coreIps.isNotEmpty()) {
                resolvedIps.addAll(coreIps)
                resolvedVia = "Core DNS"
            }
        }

        if (resolvedIps.isEmpty()) {
            for (net in candidates) {
                val addrs = runCatching { net.getAllByName(target) }.getOrNull()
                if (addrs != null && addrs.isNotEmpty()) {
                    addrs.mapNotNull { it.hostAddress }.distinct().forEach { resolvedIps.add(it) }
                    if (resolvedIps.isNotEmpty()) {
                        resolvedVia = "Physical Network DNS"
                        break
                    }
                }
            }
        }

        if (resolvedIps.isEmpty()) {
            runCatching {
                InetAddress.getAllByName(target)
                    .mapNotNull { it.hostAddress }
                    .distinct()
                    .forEach { resolvedIps.add(it) }
                if (resolvedIps.isNotEmpty()) resolvedVia = "System DNS"
            }
        }

        if (resolvedIps.isEmpty()) {
            return@withContext IpTargetLookupResult(
                query = rawQuery,
                normalizedTarget = target,
                isIpLiteral = false,
                resolvedVia = resolvedVia,
                results = emptyList(),
                error = "DNS resolution returned no addresses",
            )
        }

        val capped = resolvedIps.distinct().take(6)
        val details = coroutineScope {
            capped.map { ip ->
                async { lookupIpDetailsWithFallback(primaryClient, ip, useProxy, vpnConnected, candidates) }
            }.awaitAll()
        }

        IpTargetLookupResult(
            query = rawQuery,
            normalizedTarget = target,
            isIpLiteral = false,
            resolvedVia = resolvedVia,
            results = details,
        )
    }

    private fun lookupIpDetailsWithFallback(
        primaryClient: OkHttpClient,
        ip: String,
        useProxy: Boolean,
        vpnConnected: Boolean,
        candidates: List<Network>,
    ): IpGeoInfo {
        val first = lookupIpDetails(primaryClient, ip)
        if (first.error.isBlank()) return first
        if (!useProxy) {
            for (alt in candidates.drop(1).take(2)) {
                val res = lookupIpDetails(buildClient(viaProxy = false, physicalNetwork = alt), ip)
                if (res.error.isBlank()) return res
            }
            if (!vpnConnected && candidates.isNotEmpty()) {
                val unbound = lookupIpDetails(buildClient(viaProxy = false, physicalNetwork = null), ip)
                if (unbound.error.isBlank()) return unbound
            }
        } else if (vpnConnected) {
            // If direct geo endpoints failed through proxy or proxy geo failed, keep error accurate.
            return first
        }
        return first
    }

    private fun queryOwnExit(client: OkHttpClient): IpGeoInfo {
        executeGet(client, "https://ipwho.is/")?.let { body ->
            parseIpWhoJson(body)?.let { return it }
        }
        executeGet(client, "https://ipinfo.io/json")?.let { body ->
            parseIpInfoJson(body)?.let { return it }
        }
        executeGet(client, "https://api.ip.sb/geoip")?.let { body ->
            parseIpSbJson(body)?.let { return it }
        }
        executeGet(client, "https://get.geojs.io/v1/ip/geo.json")?.let { body ->
            parseGeoJsJson(body)?.let { return it }
        }
        executeGet(client, "https://ifconfig.co/json")?.let { body ->
            parseIfconfigCoJson(body)?.let { return it }
        }
        executeGet(client, "https://1.1.1.1/cdn-cgi/trace")?.let { body ->
            parseCloudflareTrace(body)?.let { return it }
        }
        executeGet(client, "https://api64.ipify.org")?.let { body ->
            parsePlainIp(body, "ipify")?.let { return it }
        }
        return IpGeoInfo(
            ip = "",
            version = 4,
            category = IpCategory.RESERVED,
            error = "Unable to reach IP lookup endpoints",
        )
    }

    private fun lookupIpDetails(client: OkHttpClient, ip: String): IpGeoInfo {
        val (ver, cat) = classifyIp(ip)
        if (cat != IpCategory.PUBLIC) {
            return IpGeoInfo(ip = ip, version = ver, category = cat, source = "local")
        }
        executeGet(client, "https://ipwho.is/$ip")?.let { body ->
            parseIpWhoJson(body, ip)?.let { return it }
        }
        executeGet(client, "https://ipinfo.io/$ip/json")?.let { body ->
            parseIpInfoJson(body, ip)?.let { return it }
        }
        executeGet(client, "https://api.ip.sb/geoip/$ip")?.let { body ->
            parseIpSbJson(body, ip)?.let { return it }
        }
        executeGet(client, "https://get.geojs.io/v1/ip/geo/$ip.json")?.let { body ->
            parseGeoJsJson(body, ip)?.let { return it }
        }
        return IpGeoInfo(
            ip = ip,
            version = ver,
            category = cat,
            error = "Geo lookup unavailable",
        )
    }

    private fun executeGet(client: OkHttpClient, url: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", appUserAgent())
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }

    private fun buildClient(viaProxy: Boolean, physicalNetwork: Network?): OkHttpClient {
        if (viaProxy) {
            return newHttpClient(viaProxy = true, timeoutSeconds = 10)
        }
        val base = newHttpClient(viaProxy = false, timeoutSeconds = 8)
        if (physicalNetwork == null) return base
        return base.newBuilder()
            .socketFactory(physicalNetwork.socketFactory)
            .dns(
                Dns { hostname ->
                    val addrs = runCatching { physicalNetwork.getAllByName(hostname).toList() }.getOrDefault(emptyList())
                    if (addrs.isNotEmpty()) addrs else throw UnknownHostException("Unable to resolve $hostname on physical network")
                },
            )
            .build()
    }

    @Suppress("DEPRECATION")
    fun candidatePhysicalNetworks(connectivity: ConnectivityManager?): List<Network> {
        connectivity ?: return emptyList()
        val activeNonVpn = connectivity.activeNetwork?.takeIf { net ->
            val caps = connectivity.getNetworkCapabilities(net)
            caps != null &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        return connectivity.allNetworks
            .mapNotNull { net ->
                val caps = connectivity.getNetworkCapabilities(net) ?: return@mapNotNull null
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
                var score = 0
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)) score += 1000
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) score += 400
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)) score += 200
                if (net == activeNonVpn) score += 80
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) score += 30
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) score += 25
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) score += 15
                net to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun findPhysicalNetwork(connectivity: ConnectivityManager?): Network? =
        candidatePhysicalNetworks(connectivity).firstOrNull()
}
