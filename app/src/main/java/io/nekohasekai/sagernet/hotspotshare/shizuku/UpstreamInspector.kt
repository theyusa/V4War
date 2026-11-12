package io.nekohasekai.sagernet.hotspotshare.shizuku

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * How a single upstream reading compares to the interface the session owns.
 *
 * Adapted from carlelieser/shizzi:
 * - ABSENT: tethering currently forwards nowhere, so no traffic leaks off the TUN while it lasts.
 * - DRIFTED: tethering selected a different live interface (e.g. rmnet_data* or wlan*) which leaks!
 */
enum class UpstreamReading {
    HEALTHY,
    ABSENT,
    TIMED_OUT,
    DRIFTED,
}

data class UpstreamObservation(
    val rawOutput: String,
    val interfaceNames: List<String>,
    val tetheredDownstreams: List<String>,
    val didTimeout: Boolean,
) {
    fun liveInterfaceNames(owned: String, existsCheck: (String) -> Boolean = ::defaultInterfaceExists): List<String> {
        return interfaceNames.filter { it == owned || existsCheck(it) }
    }
}

private fun defaultInterfaceExists(name: String): Boolean =
    runCatching { File("/sys/class/net/$name").exists() }.getOrDefault(true)

fun classifyUpstream(
    liveNames: List<String>,
    expectedInterface: String,
    didTimeout: Boolean,
): UpstreamReading = when {
    didTimeout -> UpstreamReading.TIMED_OUT
    liveNames.isEmpty() -> UpstreamReading.ABSENT
    liveNames.all { it == expectedInterface } -> UpstreamReading.HEALTHY
    else -> UpstreamReading.DRIFTED
}

/**
 * Tracks consecutive bad upstream readings and decides when the session must abort
 * to prevent cleartext cellular/Wi-Fi traffic leaks.
 */
class UpstreamTolerance(
    private val expectedInterface: String,
    private val onStrike: (String) -> Unit = {},
) {
    private var strikes = 0
    private var lastReading = UpstreamReading.HEALTHY

    fun judge(
        observed: UpstreamReading,
        names: List<String>,
        isDownstreamAwaiting: Boolean = false,
    ): String? {
        val isExpectedAbsence = observed == UpstreamReading.ABSENT && isDownstreamAwaiting
        val reading = if (isExpectedAbsence) UpstreamReading.HEALTHY else observed

        if (reading == UpstreamReading.HEALTHY) {
            strikes = 0
            lastReading = reading
            return null
        }

        if (reading != lastReading) strikes = 0
        lastReading = reading
        strikes++

        val allowed = when (reading) {
            UpstreamReading.ABSENT -> ABSENT_STRIKES
            else -> DRIFT_STRIKES
        }
        if (strikes < allowed) {
            onStrike("watchdog strike $strikes/$allowed: ${describe(reading, names)}")
            return null
        }
        return describe(reading, names)
    }

    private fun describe(reading: UpstreamReading, names: List<String>): String = when (reading) {
        UpstreamReading.TIMED_OUT -> "upstream check timed out $strikes times"
        UpstreamReading.ABSENT ->
            "upstream reported no interface for $strikes checks, expected $expectedInterface"
        else -> "upstream drifted from $expectedInterface to $names"
    }

    companion object {
        const val DRIFT_STRIKES = 2
        const val ABSENT_STRIKES = 12
    }
}

/**
 * Inspects `dumpsys tethering` and `dumpsys wifi` in the privileged shell process
 * to verify that tethering selected our `testtunN` interface, check downstream
 * TetheredState, and count connected hotspot clients.
 */
class UpstreamInspector(private val deadlineMs: Long = DEFAULT_DEADLINE_MS) {

    fun observeTethering(): UpstreamObservation = runDumpsys("tethering")

    fun observeWifiClientCount(): Int {
        val obs = runDumpsys("wifi")
        if (obs.didTimeout) return 0
        return parseWifiClientCount(obs.rawOutput)
    }

    fun observeNeighbors(): String {
        val neigh = runShellCommand(listOf("ip", "-4", "neigh", "show")).rawOutput
        val arp = runCatching { File("/proc/net/arp").readText() }.getOrDefault("")
        return buildString {
            if (neigh.isNotBlank()) {
                append(neigh)
                append('\n')
            }
            if (arp.isNotBlank()) {
                append("---ARP---\n")
                append(arp)
            }
        }
    }

    private fun runDumpsys(service: String): UpstreamObservation =
        runShellCommand(listOf("dumpsys", service))

    private fun runShellCommand(cmd: List<String>): UpstreamObservation {
        val process = ProcessBuilder(cmd).start()
        val drainPool = Executors.newFixedThreadPool(2)

        val stdout = drainPool.submit<String> { process.inputStream.drain() }
        val stderr = drainPool.submit<String> { process.errorStream.drain() }

        val didExit = process.waitFor(deadlineMs, TimeUnit.MILLISECONDS)
        if (!didExit) process.destroyForcibly()

        val output = collectOutput(stdout, stderr)
        drainPool.shutdownNow()

        return UpstreamObservation(
            rawOutput = output,
            interfaceNames = parseUpstreamInterfaces(output),
            tetheredDownstreams = parseTetheredDownstreams(output),
            didTimeout = !didExit,
        )
    }

    private fun collectOutput(stdout: Future<String>, stderr: Future<String>): String {
        val out = runCatching { stdout.get(deadlineMs, TimeUnit.MILLISECONDS) }.getOrDefault("")
        val err = runCatching { stderr.get(deadlineMs, TimeUnit.MILLISECONDS) }.getOrDefault("")
        return if (err.isBlank()) out else "$out\n[stderr]\n$err"
    }

    private fun InputStream.drain(): String =
        bufferedReader().use(BufferedReader::readText)

    companion object {
        const val DEFAULT_DEADLINE_MS = 3_000L

        private val UPSTREAM_HINTS = listOf(
            "current upstream interface(s):",
            "current upstream:",
            "selected upstream:",
            "upstream network:",
            "mCurrentUpstream",
        )

        private val INTERFACE_PATTERN =
            Regex("""\b(testtun\d+|wlan\d+|rmnet[a-z_]*\d*|eth\d+|ap\d+|swlan\d+)\b""")

        private val TETHERED_STATE_PATTERN =
            Regex("""^\s*([\w.-]+)\s+-\s+TetheredState\s+-""")

        private val CONNECTED_CLIENTS_PATTERN =
            Regex("""getConnectedClientList\(\)\.size\(\):\s*(\d+)""")

        @JvmStatic
        fun parseUpstreamInterfaces(output: String): List<String> {
            return output.lineSequence()
                .map(String::trim)
                .filter { line ->
                    UPSTREAM_HINTS.any { hint -> line.startsWith(hint, ignoreCase = true) }
                }
                .flatMap { line -> INTERFACE_PATTERN.findAll(line).map { it.value } }
                .distinct()
                .toList()
        }

        @JvmStatic
        fun parseTetheredDownstreams(output: String): List<String> {
            return output.lineSequence()
                .mapNotNull { line -> TETHERED_STATE_PATTERN.find(line)?.groupValues?.get(1) }
                .distinct()
                .toList()
        }

        @JvmStatic
        fun parseWifiClientCount(output: String): Int {
            return CONNECTED_CLIENTS_PATTERN.findAll(output)
                .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
                .maxOrNull() ?: 0
        }
    }
}
