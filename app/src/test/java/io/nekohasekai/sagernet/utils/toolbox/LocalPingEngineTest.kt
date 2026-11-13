package io.nekohasekai.sagernet.utils.toolbox

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalPingEngineTest {

    @Test
    fun computeStatsCalculatesLossAndJitter() {
        val samples = listOf(
            PingSample(1, true, 10.0),
            PingSample(2, true, 20.0),
            PingSample(3, false, 0.0),
            PingSample(4, true, 14.0),
        )
        val stats = LocalPingEngine.computeStats(samples)
        assertEquals(4, stats.sent)
        assertEquals(3, stats.received)
        assertEquals(25.0, stats.lossPercent, 0.01)
        assertEquals(10.0, stats.minMs, 0.01)
        assertEquals(20.0, stats.maxMs, 0.01)
        assertEquals(14.666, stats.avgMs, 0.01)
        // diffs: |20 - 10| = 10, |14 - 20| = 6 -> avg 8.0
        assertEquals(8.0, stats.jitterMs, 0.01)
    }

    @Test
    fun parseSystemPingOutput() {
        val line = "64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=14.8 ms"
        assertEquals(14.8, LocalPingEngine.parseSystemPingTimeMs(line)!!, 0.01)
        val fast = "64 bytes from 127.0.0.1: icmp_seq=1 ttl=64 time<1.0 ms"
        assertEquals(1.0, LocalPingEngine.parseSystemPingTimeMs(fast)!!, 0.01)
    }
}
