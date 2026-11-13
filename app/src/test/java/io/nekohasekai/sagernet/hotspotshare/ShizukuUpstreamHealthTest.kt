package io.nekohasekai.sagernet.hotspotshare

import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamInspector
import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamObservation
import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuUpstreamHealthTest {

    @Test
    fun parseUpstreamInterfacesRecognizesCcmniV4AndSoftapNames() {
        val dump = """
            Current upstream interface(s): [v4-rmnet_data0, ccmni0]
            Selected upstream: ccmni_rmnet1
            mCurrentUpstream: testtun0
            Current upstream: softap0 rndis0 usb0 swlan0
        """.trimIndent()

        val parsed = UpstreamInspector.parseUpstreamInterfaces(dump)
        assertEquals(
            listOf("v4-rmnet_data0", "ccmni0", "ccmni_rmnet1", "testtun0", "softap0", "rndis0", "usb0", "swlan0"),
            parsed,
        )
    }

    @Test
    fun parseTetheredDownstreamsHandlesStandardAndOemLines() {
        val dump = """
              ap0 - TetheredState - last state change
              softap0 - TetheredState
        """.trimIndent()

        assertEquals(
            listOf("ap0", "softap0"),
            UpstreamInspector.parseTetheredDownstreams(dump),
        )
    }

    @Test
    fun evaluateUpstreamHealthReturnsAwaitingInsteadOfDownstreamOffWhileSoftApActive() {
        val obs = UpstreamObservation(
            rawOutput = "",
            interfaceNames = emptyList(),
            tetheredDownstreams = emptyList(),
            didTimeout = false,
        )

        val status = UpstreamInspector.evaluateUpstreamHealth(obs, "testtun0") { true }
        assertEquals("AWAITING", status)
    }

    @Test
    fun evaluateUpstreamHealthReturnsDownstreamOffOnlyWhenSoftApAndDownstreamsAreOff() {
        val obs = UpstreamObservation(
            rawOutput = "",
            interfaceNames = emptyList(),
            tetheredDownstreams = emptyList(),
            didTimeout = false,
        )

        val status = UpstreamInspector.evaluateUpstreamHealth(obs, "testtun0") { false }
        assertEquals("DOWNSTREAM_OFF", status)
    }

    @Test
    fun evaluateUpstreamHealthReportsDriftedAndHealthyAccurately() {
        val healthy = UpstreamObservation(
            rawOutput = "",
            interfaceNames = listOf("testtun0"),
            tetheredDownstreams = listOf("ap0"),
            didTimeout = false,
        )
        assertEquals("HEALTHY", UpstreamInspector.evaluateUpstreamHealth(healthy, "testtun0") { true })

        val drifted = UpstreamObservation(
            rawOutput = "",
            interfaceNames = listOf("v4-rmnet_data0"),
            tetheredDownstreams = emptyList(),
            didTimeout = false,
        )
        assertEquals(
            "DRIFTED:v4-rmnet_data0",
            UpstreamInspector.evaluateUpstreamHealth(drifted, "testtun0", existsCheck = { true }) { true },
        )

        val timedOut = UpstreamObservation(
            rawOutput = "",
            interfaceNames = emptyList(),
            tetheredDownstreams = emptyList(),
            didTimeout = true,
        )
        assertEquals("TIMED_OUT", UpstreamInspector.evaluateUpstreamHealth(timedOut, "testtun0") { true })
    }

    @Test
    fun watchdogFailureStateReportsBackendErrorWhileHotspotRemainsActive() {
        assertEquals(
            ShareState.Error(ShareFailure.BACKEND_FAILED, "drifted"),
            ShizukuBackend.resolveWatchdogFailureState(hotspotStillUp = true, verdict = "drifted"),
        )
        assertEquals(
            ShareState.Stopped(ShareFailure.TETHER_LOST),
            ShizukuBackend.resolveWatchdogFailureState(hotspotStillUp = false, verdict = "drifted"),
        )
    }
}
