package io.nekohasekai.sagernet.utils.toolbox

import android.net.ConnectivityManager
import android.net.Network
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.appUserAgent
import io.nekohasekai.sagernet.ktx.newHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class DnsResolverMode {
    FOLLOW_CORE,
    SYSTEM_DNS,
    CUSTOM,
}

data class DnsResolverPreset(
    val id: String,
    val label: String,
    val endpoint: String,
)

data class DnsServerResult(
    val label: String,
    val endpoint: String,
    val protocol: String,
    val rcodeName: String,
    val rttMs: Long,
    val answers: List<DnsAnswerRecord>,
    val error: String = "",
)

data class DnsLookupReport(
    val queryDomain: String,
    val normalizedDomain: String,
    val recordType: DnsRecordType,
    val results: List<DnsServerResult>,
)

object DnsLookupEngine {

    val PRESETS = listOf(
        DnsResolverPreset("cf_doh", "Cloudflare DoH", "https://cloudflare-dns.com/dns-query"),
        DnsResolverPreset("google_doh", "Google DoH", "https://dns.google/dns-query"),
        DnsResolverPreset("quad9_doh", "Quad9 DoH", "https://dns.quad9.net/dns-query"),
        DnsResolverPreset("adguard_doh", "AdGuard DoH", "https://dns.adguard-dns.com/dns-query"),
        DnsResolverPreset("cf_udp", "Cloudflare UDP", "1.1.1.1"),
        DnsResolverPreset("google_udp", "Google UDP", "8.8.8.8"),
        DnsResolverPreset("cf_dot", "Cloudflare DoT", "tls://1.1.1.1"),
    )

    suspend fun lookup(
        rawDomain: String,
        recordType: DnsRecordType,
        mode: DnsResolverMode,
        customEndpoint: String,
        compareMode: Boolean,
        viaProxy: Boolean,
        connectivity: ConnectivityManager?,
    ): DnsLookupReport = withContext(Dispatchers.IO) {
        val domain = DnsWireCodec.normalizeQueryName(rawDomain, recordType)
        if (domain.isBlank()) {
            return@withContext DnsLookupReport(
                queryDomain = rawDomain,
                normalizedDomain = "",
                recordType = recordType,
                results = listOf(
                    DnsServerResult(
                        label = "Input",
                        endpoint = "",
                        protocol = "-",
                        rcodeName = "ERR",
                        rttMs = 0,
                        answers = emptyList(),
                        error = "Invalid domain or IP",
                    ),
                ),
            )
        }

        val dohClient = lazy { buildDohClient(viaProxy, connectivity) }

        if (compareMode) {
            val targets = buildList {
                if (DataStore.serviceState.connected) add(DnsResolverPreset("core", "Core DNS", "core"))
                addAll(PRESETS.take(4))
                if (customEndpoint.isNotBlank() && PRESETS.none { it.endpoint == customEndpoint.trim() }) {
                    add(DnsResolverPreset("custom", customEndpoint.trim(), customEndpoint.trim()))
                }
            }
            val results = coroutineScope {
                targets.map { target ->
                    async {
                        if (target.endpoint == "core") {
                            queryCore(domain, recordType)
                        } else {
                            queryServer(target.label, target.endpoint, domain, recordType, viaProxy, connectivity, dohClient)
                        }
                    }
                }.awaitAll()
            }
            return@withContext DnsLookupReport(rawDomain, domain, recordType, results)
        }

        val single = when (mode) {
            DnsResolverMode.FOLLOW_CORE -> {
                if (DataStore.serviceState.connected) {
                    queryCore(domain, recordType)
                } else {
                    querySystemDns(domain, recordType, connectivity)
                }
            }
            DnsResolverMode.SYSTEM_DNS -> querySystemDns(domain, recordType, connectivity)
            DnsResolverMode.CUSTOM -> {
                val ep = customEndpoint.trim().ifBlank { "https://cloudflare-dns.com/dns-query" }
                val label = PRESETS.firstOrNull { it.endpoint == ep }?.label ?: ep
                queryServer(label, ep, domain, recordType, viaProxy, connectivity, dohClient)
            }
        }

        DnsLookupReport(rawDomain, domain, recordType, listOf(single))
    }

    private suspend fun queryCore(domain: String, recordType: DnsRecordType): DnsServerResult {
        val startNs = System.nanoTime()
        if (recordType != DnsRecordType.A && recordType != DnsRecordType.AAAA) {
            return DnsServerResult(
                label = "Core DNS",
                endpoint = "sing-box core",
                protocol = "CORE",
                rcodeName = "N/A",
                rttMs = 0,
                answers = emptyList(),
                error = "Core resolver returns A/AAAA records only; switch to Custom DoH/UDP for ${recordType.label}.",
            )
        }
        return try {
            val (ips, resolveErr) = IpLookupEngine.resolveViaCore(domain)
            val answers = ips.mapNotNull { ip ->
                val isV6 = ip.contains(':')
                val matchesType = (recordType == DnsRecordType.A && !isV6) ||
                    (recordType == DnsRecordType.AAAA && isV6)
                if (matchesType) {
                    DnsAnswerRecord(
                        name = domain,
                        typeCode = recordType.code,
                        typeLabel = recordType.label,
                        ttl = 0L,
                        value = ip,
                    )
                } else {
                    null
                }
            }
            val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
            DnsServerResult(
                label = "Core DNS",
                endpoint = "sing-box core",
                protocol = "CORE",
                rcodeName = when {
                    answers.isNotEmpty() -> "NOERROR"
                    resolveErr.isNotBlank() -> "ERR"
                    else -> "NODATA"
                },
                rttMs = rtt,
                answers = answers,
                error = if (answers.isEmpty()) resolveErr else "",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
            DnsServerResult("Core DNS", "sing-box core", "CORE", "ERR", rtt, emptyList(), e.message ?: "Error")
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun querySystemDns(
        domain: String,
        recordType: DnsRecordType,
        connectivity: ConnectivityManager?,
    ): DnsServerResult {
        val candidates = IpLookupEngine.candidatePhysicalNetworks(connectivity)
        val physicalNet = candidates.firstOrNull()
        val physicalDns = physicalNet
            ?.let { connectivity?.getLinkProperties(it)?.dnsServers }
            ?.mapNotNull { it.hostAddress }
            .orEmpty()
        val activeServers = connectivity?.activeNetwork
            ?.let { connectivity.getLinkProperties(it)?.dnsServers }
            ?.mapNotNull { it.hostAddress }
            .orEmpty()

        val serverIp = (physicalDns + activeServers).firstOrNull {
            !IpLookupEngine.classifyIp(it).second.name.contains("LOOPBACK")
        }
        if (!serverIp.isNullOrBlank()) {
            val isPhysicalDns = physicalDns.contains(serverIp)
            val wireRes = queryUdp(
                label = "System DNS",
                host = serverIp,
                port = 53,
                domain = domain,
                recordType = recordType,
                viaProxy = !isPhysicalDns && DataStore.serviceState.connected,
                connectivity = connectivity,
                explicitPhysicalNet = if (isPhysicalDns) physicalNet else null,
            )
            if (wireRes.error.isBlank() || (recordType != DnsRecordType.A && recordType != DnsRecordType.AAAA)) {
                return wireRes
            }
        }

        // Fallback to physical network / InetAddress.getAllByName for A/AAAA
        val startNs = System.nanoTime()
        return try {
            val addrs = (physicalNet?.let { runCatching { it.getAllByName(domain).toList() }.getOrNull() }
                ?: InetAddress.getAllByName(domain).toList())
                .mapNotNull { it.hostAddress }
            val answers = addrs.filter {
                val isV6 = it.contains(':')
                (recordType == DnsRecordType.A && !isV6) || (recordType == DnsRecordType.AAAA && isV6)
            }.map {
                DnsAnswerRecord(domain, recordType.code, recordType.label, 0L, it)
            }
            val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
            DnsServerResult(
                label = "System DNS",
                endpoint = serverIp ?: "system",
                protocol = "SYS",
                rcodeName = if (answers.isEmpty()) "NODATA" else "NOERROR",
                rttMs = rtt,
                answers = answers,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
            DnsServerResult("System DNS", serverIp ?: "system", "SYS", "ERR", rtt, emptyList(), e.message ?: "Lookup failed")
        }
    }

    private suspend fun queryServer(
        label: String,
        endpoint: String,
        domain: String,
        recordType: DnsRecordType,
        viaProxy: Boolean,
        connectivity: ConnectivityManager?,
        dohClient: Lazy<OkHttpClient>,
    ): DnsServerResult {
        val trimmed = endpoint.trim()
        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> {
                queryDoh(label, trimmed, domain, recordType, dohClient.value)
            }
            trimmed.startsWith("tls://", ignoreCase = true) -> {
                val hostPort = trimmed.substringAfter("tls://").substringBefore('/')
                val (host, port) = parseHostPort(hostPort, 853)
                queryDot(label, host, port, domain, recordType, viaProxy, connectivity)
            }
            else -> {
                val (host, port) = parseHostPort(trimmed.removePrefix("udp://"), 53)
                queryUdp(label, host, port, domain, recordType, viaProxy, connectivity)
            }
        }
    }

    private fun buildDohClient(viaProxy: Boolean, connectivity: ConnectivityManager?): OkHttpClient {
        val useProxy = viaProxy && DataStore.serviceState.connected
        val baseClient = newHttpClient(viaProxy = useProxy, timeoutSeconds = 10)
        if (viaProxy) return baseClient
        val net = IpLookupEngine.findPhysicalNetwork(connectivity) ?: return baseClient
        return baseClient.newBuilder()
            .socketFactory(net.socketFactory)
            .dns(
                Dns { host ->
                    val addrs = runCatching { net.getAllByName(host).toList() }.getOrDefault(emptyList())
                    if (addrs.isNotEmpty()) addrs else throw UnknownHostException("Unable to resolve $host on physical network")
                },
            )
            .build()
    }

    private suspend fun queryDoh(
        label: String,
        url: String,
        domain: String,
        recordType: DnsRecordType,
        client: OkHttpClient,
    ): DnsServerResult {
        val startNs = System.nanoTime()
        return try {
            val packet = DnsWireCodec.encodeQuery(domain, recordType)
            val body = packet.toRequestBody("application/dns-message".toMediaType())
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-message")
                .header("User-Agent", appUserAgent())
                .post(body)
                .build()
            client.newCall(req).executeCancellable { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val raw = resp.body?.bytes() ?: error("Empty DoH body")
                val decoded = DnsWireCodec.decodeResponse(raw)
                val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
                DnsServerResult(
                    label = label,
                    endpoint = url,
                    protocol = "DoH",
                    rcodeName = decoded.rcodeName,
                    rttMs = rtt,
                    answers = decoded.answers,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
            DnsServerResult(label, url, "DoH", "ERR", rtt, emptyList(), e.message ?: "DoH failed")
        }
    }

    private suspend fun queryDot(
        label: String,
        host: String,
        port: Int,
        domain: String,
        recordType: DnsRecordType,
        viaProxy: Boolean,
        connectivity: ConnectivityManager?,
    ): DnsServerResult {
        val startNs = System.nanoTime()
        return try {
            val packet = DnsWireCodec.encodeQuery(domain, recordType)
            val physicalNet = if (!viaProxy) IpLookupEngine.findPhysicalNetwork(connectivity) else null
            val resolved = physicalNet?.getAllByName(host)?.firstOrNull() ?: InetAddress.getByName(host)
            val rawSocket = physicalNet?.socketFactory?.createSocket() ?: Socket()
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslRef = AtomicReference<SSLSocket?>(null)
            try {
                withImmediateCancel(
                    onCancel = {
                        runCatching { sslRef.get()?.close() }
                        runCatching { rawSocket.close() }
                    },
                ) {
                    rawSocket.connect(InetSocketAddress(resolved, port), 8_000)
                    rawSocket.soTimeout = 8_000
                    val wrapped = sslFactory.createSocket(rawSocket, host, port, true) as SSLSocket
                    sslRef.set(wrapped)
                    wrapped.soTimeout = 8_000
                    wrapped.startHandshake()
                    val out = DataOutputStream(wrapped.getOutputStream())
                    val inp = DataInputStream(wrapped.getInputStream())
                    out.writeShort(packet.size)
                    out.write(packet)
                    out.flush()
                    val respLen = inp.readUnsignedShort()
                    val respBytes = ByteArray(respLen)
                    inp.readFully(respBytes)
                    val decoded = DnsWireCodec.decodeResponse(respBytes)
                    val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
                    DnsServerResult(
                        label = label,
                        endpoint = "tls://$host:$port",
                        protocol = "DoT",
                        rcodeName = decoded.rcodeName,
                        rttMs = rtt,
                        answers = decoded.answers,
                    )
                }
            } finally {
                val sslSocket = sslRef.get()
                if (sslSocket != null) {
                    runCatching { sslSocket.close() }
                } else {
                    runCatching { rawSocket.close() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
            DnsServerResult(label, "tls://$host:$port", "DoT", "ERR", rtt, emptyList(), e.message ?: "DoT failed")
        }
    }

    internal suspend fun queryUdp(
        label: String,
        host: String,
        port: Int,
        domain: String,
        recordType: DnsRecordType,
        viaProxy: Boolean,
        connectivity: ConnectivityManager?,
        explicitPhysicalNet: Network? = null,
    ): DnsServerResult {
        val startNs = System.nanoTime()
        return try {
            val packet = DnsWireCodec.encodeQuery(domain, recordType)
            val physicalNet = explicitPhysicalNet
                ?: if (!viaProxy) IpLookupEngine.findPhysicalNetwork(connectivity) else null
            DatagramSocket().use { socket ->
                withImmediateCancel(onCancel = { socket.close() }) {
                    physicalNet?.let { runCatching { it.bindSocket(socket) } }
                    socket.soTimeout = 5_000
                    val address = physicalNet?.getAllByName(host)?.firstOrNull()
                        ?: InetAddress.getByName(host)
                    socket.send(DatagramPacket(packet, packet.size, address, port))
                    val buf = ByteArray(4096)
                    val dp = DatagramPacket(buf, buf.size)
                    socket.receive(dp)
                    val decoded = DnsWireCodec.decodeResponse(buf.copyOf(dp.length))
                    val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
                    DnsServerResult(
                        label = label,
                        endpoint = "$host:$port",
                        protocol = "UDP",
                        rcodeName = decoded.rcodeName,
                        rttMs = rtt,
                        answers = decoded.answers,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
            DnsServerResult(label, "$host:$port", "UDP", "ERR", rtt, emptyList(), e.message ?: "UDP query failed")
        }
    }

    fun parseHostPort(raw: String, defaultPort: Int): Pair<String, Int> {
        val t = raw.trim()
        if (t.startsWith("[")) {
            val close = t.indexOf(']')
            if (close > 0) {
                val host = t.substring(1, close)
                val rest = t.substring(close + 1)
                val port = if (rest.startsWith(":")) rest.substring(1).toIntOrNull() ?: defaultPort else defaultPort
                return host to port
            }
        }
        val colons = t.count { it == ':' }
        if (colons == 1) {
            val host = t.substringBefore(':')
            val port = t.substringAfter(':').toIntOrNull() ?: defaultPort
            return host to port
        }
        return t to defaultPort
    }
}
