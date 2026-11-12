package io.nekohasekai.sagernet.hotspotshare

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Traffic snapshot representing instantaneous speed (bytes/sec) and cumulative session usage.
 */
data class HotspotTrafficStats(
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
)

/**
 * Parses /proc/net/dev lines to extract rx_bytes and tx_bytes.
 */
object ProcNetDevParser {

    /**
     * Parses the content of /proc/net/dev and sums rx_bytes and tx_bytes for [interfaceNames].
     * If [interfaceNames] is empty, defaults to common hotspot interfaces: ap0, softap0, swlan0, wlan0, wlan1, rndis0, testtun0.
     */
    fun parse(
        content: String,
        interfaceNames: Set<String> = emptySet(),
    ): Pair<Long, Long> {
        val targets = if (interfaceNames.isNotEmpty()) {
            interfaceNames
        } else {
            setOf("ap0", "softap0", "swlan0", "wlan0", "wlan1", "rndis0", "testtun0")
        }

        var totalRx = 0L
        var totalTx = 0L

        content.lineSequence().forEach { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex <= 0) return@forEach

            val iface = line.substring(0, colonIndex).trim()
            if (iface !in targets) return@forEach

            val statsPart = line.substring(colonIndex + 1).trim()
            val tokens = statsPart.split(Regex("\\s+"))
            if (tokens.size >= 9) {
                val rx = tokens[0].toLongOrNull() ?: 0L
                val tx = tokens[8].toLongOrNull() ?: 0L
                totalRx += rx
                totalTx += tx
            }
        }

        return Pair(totalRx, totalTx)
    }

    fun readCurrent(interfaceNames: Set<String> = emptySet()): Pair<Long, Long>? {
        return runCatching {
            val file = File("/proc/net/dev")
            if (file.exists() && file.canRead()) {
                parse(file.readText(), interfaceNames)
            } else null
        }.getOrNull()
    }
}

/**
 * Calculates real-time speed and session delta from successive raw (rxBytes, txBytes) readings.
 */
class HotspotTrafficCounter(
    private val rawStatsProvider: () -> Pair<Long, Long>?,
) {
    private var baseRx: Long = -1L
    private var baseTx: Long = -1L
    private var lastRx: Long = -1L
    private var lastTx: Long = -1L
    private var lastTime: Long = 0L

    fun reset() {
        baseRx = -1L
        baseTx = -1L
        lastRx = -1L
        lastTx = -1L
        lastTime = 0L
    }

    fun tick(currentTimeMillis: Long = System.currentTimeMillis()): HotspotTrafficStats {
        val raw = rawStatsProvider() ?: return HotspotTrafficStats()
        val (currentRx, currentTx) = raw

        if (baseRx < 0L || baseTx < 0L) {
            baseRx = currentRx
            baseTx = currentTx
            lastRx = currentRx
            lastTx = currentTx
            lastTime = currentTimeMillis
            return HotspotTrafficStats()
        }

        val elapsedMs = (currentTimeMillis - lastTime).coerceAtLeast(1L)
        val deltaRx = (currentRx - lastRx).coerceAtLeast(0L)
        val deltaTx = (currentTx - lastTx).coerceAtLeast(0L)

        val rxSpeed = (deltaRx * 1000L) / elapsedMs
        val txSpeed = (deltaTx * 1000L) / elapsedMs

        lastRx = currentRx
        lastTx = currentTx
        lastTime = currentTimeMillis

        val sessionRx = (currentRx - baseRx).coerceAtLeast(0L)
        val sessionTx = (currentTx - baseTx).coerceAtLeast(0L)

        return HotspotTrafficStats(
            rxSpeedBps = rxSpeed,
            txSpeedBps = txSpeed,
            sessionRxBytes = sessionRx,
            sessionTxBytes = sessionTx,
        )
    }
}

/**
 * Shared hotspot traffic monitor providing live StateFlow<HotspotTrafficStats> for UI and Foreground Service.
 */
object HotspotTrafficMonitor {

    private val _stats = MutableStateFlow(HotspotTrafficStats())
    val stats: StateFlow<HotspotTrafficStats> = _stats.asStateFlow()

    private var pollJob: Job? = null
    private var counter: HotspotTrafficCounter? = null

    fun start(
        scope: CoroutineScope,
        rawStatsProvider: () -> Pair<Long, Long>?,
        intervalMs: Long = 1000L,
    ) {
        stop()
        val cnt = HotspotTrafficCounter(rawStatsProvider)
        counter = cnt
        pollJob = scope.launch {
            while (isActive) {
                val current = cnt.tick()
                _stats.value = current
                delay(intervalMs)
            }
        }
    }

    fun updateDirect(stats: HotspotTrafficStats) {
        _stats.value = stats
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        counter?.reset()
        counter = null
        _stats.value = HotspotTrafficStats()
    }
}
