package io.nekohasekai.sagernet.hotspotshare

import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamInspector
import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamReading
import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamTolerance
import io.nekohasekai.sagernet.hotspotshare.shizuku.classifyUpstream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpstreamInspectorTest {

    @Test
    fun parsesUpstreamInterfacesFromDumpsysTethering() {
        val sampleDumpsys = """
            Tethering:
              mUpstreamNetworkMonitor:
                Current upstream interface(s): [ testtun0 ]
              Tethered interfaces:
                wlan1 - TetheredState - lastError = 0
        """.trimIndent()

        val upstreams = UpstreamInspector.parseUpstreamInterfaces(sampleDumpsys)
        assertEquals(listOf("testtun0"), upstreams)

        val downstreams = UpstreamInspector.parseTetheredDownstreams(sampleDumpsys)
        assertEquals(listOf("wlan1"), downstreams)
    }

    @Test
    fun parsesWifiConnectedClients() {
        val sampleWifi = """
            SoftApManager:
              getConnectedClientList().size(): 1
              getConnectedClientList().size(): 3
        """.trimIndent()

        assertEquals(3, UpstreamInspector.parseWifiClientCount(sampleWifi))
    }

    @Test
    fun classifyUpstreamDetectsDriftAndHealthy() {
        assertEquals(
            UpstreamReading.HEALTHY,
            classifyUpstream(listOf("testtun0"), "testtun0", didTimeout = false)
        )
        assertEquals(
            UpstreamReading.DRIFTED,
            classifyUpstream(listOf("rmnet_data0"), "testtun0", didTimeout = false)
        )
        assertEquals(
            UpstreamReading.ABSENT,
            classifyUpstream(emptyList(), "testtun0", didTimeout = false)
        )
        assertEquals(
            UpstreamReading.TIMED_OUT,
            classifyUpstream(listOf("testtun0"), "testtun0", didTimeout = true)
        )
    }

    @Test
    fun upstreamToleranceTriggersOnSecondConsecutiveDriftStrike() {
        val strikes = mutableListOf<String>()
        val tolerance = UpstreamTolerance("testtun0") { strikes += it }

        // Strike 1: warning only
        val verdict1 = tolerance.judge(UpstreamReading.DRIFTED, listOf("rmnet_data0"))
        assertNull(verdict1)
        assertEquals(1, strikes.size)

        // Strike 2: fail-closed verdict!
        val verdict2 = tolerance.judge(UpstreamReading.DRIFTED, listOf("rmnet_data0"))
        assertNotNull(verdict2)
        assertTrue(verdict2!!.contains("drifted from testtun0"))
    }

    @Test
    fun upstreamToleranceResetsWhenHealthyReturns() {
        val tolerance = UpstreamTolerance("testtun0")
        assertNull(tolerance.judge(UpstreamReading.DRIFTED, listOf("wlan0")))
        assertNull(tolerance.judge(UpstreamReading.HEALTHY, listOf("testtun0")))
        assertNull(tolerance.judge(UpstreamReading.DRIFTED, listOf("wlan0")))
    }

    @Test
    fun upstreamToleranceIgnoresAbsentWhenDownstreamAwaiting() {
        val tolerance = UpstreamTolerance("testtun0")
        repeat(UpstreamTolerance.ABSENT_STRIKES + 5) {
            assertNull(
                tolerance.judge(
                    UpstreamReading.ABSENT,
                    emptyList(),
                    isDownstreamAwaiting = true,
                )
            )
        }
    }
}
