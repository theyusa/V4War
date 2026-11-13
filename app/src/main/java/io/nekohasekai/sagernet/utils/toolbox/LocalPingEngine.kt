package io.nekohasekai.sagernet.utils.toolbox

import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlin.math.abs

enum class PingProtocol {
    TCP,
    UDP,
    ICMP,
}

data class PingSample(
    val seq: Int,
    val success: Boolean,
    val rttMs: Double = 0.0,
    val detail: String = "",
)

data class PingStats(
    val sent: Int = 0,
    val received: Int = 0,
    val lossPercent: Double = 0.0,
    val minMs: Double = 0.0,
    val avgMs: Double = 0.0,
    val maxMs: Double = 0.0,
    val jitterMs: Double = 0.0,
)

object LocalPingEngine {

    private val systemPingTimeRegex = Regex("""time[=<]\s*([0-9]+(?:\.[0-9]+)?)\s*ms""", RegexOption.IGNORE_CASE)

    fun parseSystemPingTimeMs(output: String): Double? {
        val match = systemPingTimeRegex.find(output) ?: return null
        return match.groupValues.getOrNull(1)?.toDoubleOrNull()
    }

    fun computeStats(samples: List<PingSample>): PingStats {
        if (samples.isEmpty()) return PingStats()
        val ok = samples.filter { it.success }.map { it.rttMs }
        val sent = samples.size
        val received = ok.size
        val loss = ((sent - received) * 100.0) / sent.toDouble()
        if (ok.isEmpty()) {
            return PingStats(sent = sent, received = 0, lossPercent = loss)
        }
        val min = ok.min()
        val max = ok.max()
        val avg = ok.average()
        val jitter = if (ok.size > 1) {
            var diffSum = 0.0
            for (i in 1 until ok.size) diffSum += abs(ok[i] - ok[i - 1])
            diffSum / (ok.size - 1)
        } else {
            0.0
        }
        return PingStats(
            sent = sent,
            received = received,
            lossPercent = loss,
            minMs = min,
            avgMs = avg,
            maxMs = max,
            jitterMs = jitter,
        )
    }

    fun startPing(
        host: String,
        port: Int,
        protocol: PingProtocol,
        count: Int, // <= 0 means continuous until cancelled
        intervalMs: Long,
        timeoutMs: Int,
        directPhysical: Boolean,
        connectivity: ConnectivityManager?,
    ): Flow<PingSample> = flow {
        val cleanHost = IpLookupEngine.normalizeTarget(host)
        var physicalNet: Network? = null
        var resolvedAddress: InetAddress? = null
        if (directPhysical) {
            val candidates = IpLookupEngine.candidatePhysicalNetworks(connectivity)
            for (net in candidates) {
                val addr = runCatching { net.getAllByName(cleanHost).firstOrNull() }.getOrNull()
                if (addr != null) {
                    physicalNet = net
                    resolvedAddress = addr
                    break
                }
            }
            if (physicalNet == null) {
                physicalNet = candidates.firstOrNull()
            }
        }
        if (resolvedAddress == null) {
            resolvedAddress = runCatching { InetAddress.getByName(cleanHost) }.getOrNull()
        }

        if (resolvedAddress == null) {
            emit(PingSample(seq = 1, success = false, detail = "DNS resolution failed for $cleanHost"))
            return@flow
        }

        val physicalIface = if (physicalNet != null) {
            runCatching { connectivity?.getLinkProperties(physicalNet)?.interfaceName }.getOrNull()
        } else {
            null
        }

        var seq = 0
        while (currentCoroutineContext().isActive && (count <= 0 || seq < count)) {
            seq++
            val sample = when (protocol) {
                PingProtocol.TCP -> probeTcp(seq, resolvedAddress, port.coerceIn(1, 65535), timeoutMs, physicalNet)
                PingProtocol.UDP -> probeUdpDns(seq, resolvedAddress, port.coerceIn(1, 65535), timeoutMs, physicalNet)
                PingProtocol.ICMP -> probeIcmp(seq, resolvedAddress, timeoutMs, physicalNet, physicalIface)
            }
            emit(sample)
            if (currentCoroutineContext().isActive && (count <= 0 || seq < count)) {
                delay(intervalMs.coerceAtLeast(150L))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun probeTcp(
        seq: Int,
        address: InetAddress,
        port: Int,
        timeoutMs: Int,
        physicalNet: Network?,
    ): PingSample {
        val startNs = System.nanoTime()
        return runCatching {
            val socket = physicalNet?.socketFactory?.createSocket() ?: Socket()
            socket.use {
                it.connect(InetSocketAddress(address, port), timeoutMs)
            }
            val rtt = (System.nanoTime() - startNs) / 1_000_000.0
            PingSample(seq, true, rtt, "${address.hostAddress}:$port")
        }.getOrElse { e ->
            PingSample(seq, false, 0.0, e.message ?: "TCP timeout")
        }
    }

    private fun probeUdpDns(
        seq: Int,
        address: InetAddress,
        port: Int,
        timeoutMs: Int,
        physicalNet: Network?,
    ): PingSample {
        val startNs = System.nanoTime()
        return runCatching {
            val packet = DnsWireCodec.encodeQuery("example.com", DnsRecordType.A)
            DatagramSocket().use { socket ->
                physicalNet?.bindSocket(socket)
                socket.soTimeout = timeoutMs
                socket.send(DatagramPacket(packet, packet.size, address, port))
                val buf = ByteArray(1024)
                val dp = DatagramPacket(buf, buf.size)
                socket.receive(dp)
            }
            val rtt = (System.nanoTime() - startNs) / 1_000_000.0
            PingSample(seq, true, rtt, "${address.hostAddress}:$port")
        }.getOrElse { e ->
            PingSample(seq, false, 0.0, e.message ?: "UDP timeout")
        }
    }

    private fun probeIcmp(
        seq: Int,
        address: InetAddress,
        timeoutMs: Int,
        physicalNet: Network?,
        physicalIface: String?,
    ): PingSample {
        val hostIp = address.hostAddress.orEmpty()
        // When no specific physical binding is needed, or when physical interface name is known, /system/bin/ping gives accurate ICMP RTT.
        if ((physicalNet == null || !physicalIface.isNullOrBlank()) && hostIp.isNotBlank()) {
            val cmdRes = runCatching {
                val bin = if (hostIp.contains(':')) "/system/bin/ping6" else "/system/bin/ping"
                val waitSec = ((timeoutMs + 999) / 1000).coerceAtLeast(1).toString()
                val cmd = buildList {
                    add(bin)
                    if (!physicalIface.isNullOrBlank()) {
                        add("-I")
                        add(physicalIface)
                    }
                    add("-c")
                    add("1")
                    add("-W")
                    add(waitSec)
                    add(hostIp)
                }
                val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
                val text = proc.inputStream.bufferedReader().readText()
                val exit = proc.waitFor()
                val parsed = parseSystemPingTimeMs(text)
                if (exit == 0 && parsed != null) {
                    PingSample(seq, true, parsed, hostIp)
                } else {
                    null
                }
            }.getOrNull()
            if (cmdRes != null) return cmdRes
        }

        // Fallback to InetAddress.isReachable (bound to physical interface if known)
        val startNs = System.nanoTime()
        return runCatching {
            val netIf = physicalIface?.let { runCatching { NetworkInterface.getByName(it) }.getOrNull() }
            val ok = if (netIf != null) address.isReachable(netIf, 0, timeoutMs) else address.isReachable(timeoutMs)
            val rtt = (System.nanoTime() - startNs) / 1_000_000.0
            if (ok) PingSample(seq, true, rtt, hostIp)
            else PingSample(seq, false, 0.0, "Timeout")
        }.getOrElse { e ->
            PingSample(seq, false, 0.0, e.message ?: "Unreachable")
        }
    }
}
