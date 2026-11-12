package io.nekohasekai.sagernet.hotspotshare

import org.junit.Assert.assertEquals
import org.junit.Test

class HotspotTrafficMonitorTest {

    private val sampleProcNetDev = """
Inter-|   Receive                                                |  Transmit
 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
    lo:    9631     102    0    0    0     0          0         0     9631     102    0    0    0     0       0          0
 dummy0:       0       0    0    0    0     0          0         0    41710     193    0    0    0     0       0          0
  wlan0:  105973     275    0    0    0     0          0         0   124522     558    0    0    0     0       0          0
testtun0:   50000     100    0    0    0     0          0         0    60000     120    0    0    0     0       0          0
   tun0:    1160      22    0    0    0     0          0         0     2876      42    0    0    0     0       0          0
    """.trimIndent()

    @Test
    fun parseProcNetDevSpecificInterface() {
        val (rx, tx) = ProcNetDevParser.parse(sampleProcNetDev, setOf("wlan0"))
        assertEquals(105973L, rx)
        assertEquals(124522L, tx)
    }

    @Test
    fun parseProcNetDevMultipleInterfaces() {
        val (rx, tx) = ProcNetDevParser.parse(sampleProcNetDev, setOf("wlan0", "testtun0"))
        assertEquals(155973L, rx)
        assertEquals(184522L, tx)
    }

    @Test
    fun parseProcNetDevDefaultFallback() {
        // Defaults to common hotspot names including wlan0 and testtun0
        val (rx, tx) = ProcNetDevParser.parse(sampleProcNetDev, emptySet())
        assertEquals(155973L, rx)
        assertEquals(184522L, tx)
    }

    @Test
    fun trafficCounterCalculatesSpeedAndSessionTotal() {
        var simulatedRx = 1000L
        var simulatedTx = 2000L

        val counter = HotspotTrafficCounter { Pair(simulatedRx, simulatedTx) }

        // First tick establishes baseline
        val tick0 = counter.tick(currentTimeMillis = 1000L)
        assertEquals(0L, tick0.rxSpeedBps)
        assertEquals(0L, tick0.txSpeedBps)
        assertEquals(0L, tick0.sessionRxBytes)
        assertEquals(0L, tick0.sessionTxBytes)

        // Second tick after 1000ms: +5000 bytes RX, +10000 bytes TX
        simulatedRx += 5000L
        simulatedTx += 10000L
        val tick1 = counter.tick(currentTimeMillis = 2000L)
        assertEquals(5000L, tick1.rxSpeedBps)
        assertEquals(10000L, tick1.txSpeedBps)
        assertEquals(5000L, tick1.sessionRxBytes)
        assertEquals(10000L, tick1.sessionTxBytes)

        // Third tick after another 1000ms: +2500 bytes RX, +5000 bytes TX
        simulatedRx += 2500L
        simulatedTx += 5000L
        val tick2 = counter.tick(currentTimeMillis = 3000L)
        assertEquals(2500L, tick2.rxSpeedBps)
        assertEquals(5000L, tick2.txSpeedBps)
        assertEquals(7500L, tick2.sessionRxBytes)
        assertEquals(15000L, tick2.sessionTxBytes)

        // Reset clears session totals
        counter.reset()
        val tickAfterReset = counter.tick(currentTimeMillis = 4000L)
        assertEquals(0L, tickAfterReset.sessionRxBytes)
        assertEquals(0L, tickAfterReset.sessionTxBytes)
    }
}
