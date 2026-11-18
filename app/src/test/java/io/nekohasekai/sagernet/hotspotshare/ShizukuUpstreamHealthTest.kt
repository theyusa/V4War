package io.nekohasekai.sagernet.hotspotshare

import io.nekohasekai.sagernet.hotspotshare.shizuku.ShareUserService
import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamInspector
import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

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

    @Test
    fun callbackUpstreamObservationEvaluatesHealthWithoutDumpsys() {
        val healthyObs = ShareUserService.buildCallbackUpstreamObservation("testtun0", listOf("ap0"))
        assertEquals("HEALTHY", UpstreamInspector.evaluateUpstreamHealth(healthyObs, "testtun0") { true })

        val awaitingObs = ShareUserService.buildCallbackUpstreamObservation(null, listOf("ap0"))
        assertEquals("AWAITING", UpstreamInspector.evaluateUpstreamHealth(awaitingObs, "testtun0") { false })

        val offObs = ShareUserService.buildCallbackUpstreamObservation(null, emptyList())
        assertEquals("DOWNSTREAM_OFF", UpstreamInspector.evaluateUpstreamHealth(offObs, "testtun0") { false })

        val driftedObs = ShareUserService.buildCallbackUpstreamObservation("rmnet_data0", listOf("ap0"))
        assertEquals(
            "DRIFTED:rmnet_data0",
            UpstreamInspector.evaluateUpstreamHealth(driftedObs, "testtun0", existsCheck = { true }) { true },
        )
    }

    @Test
    fun tetherMaskStopRequiresWifiApDisabledAndEmptyCachedDownstreams() {
        val wifiMask = ShareUserService.TETHER_MASK_WIFI
        assertFalse(
            ShareUserService.isTetherMaskStopSettled(
                targetMask = wifiMask,
                currentMask = 0,
                wifiApState = ShareUserService.WIFI_AP_STATE_DISABLING,
                cachedDownstreams = emptyList(),
            ),
        )
        assertFalse(
            ShareUserService.isTetherMaskStopSettled(
                targetMask = wifiMask,
                currentMask = 0,
                wifiApState = ShareUserService.WIFI_AP_STATE_ENABLED,
                cachedDownstreams = emptyList(),
            ),
        )
        assertFalse(
            ShareUserService.isTetherMaskStopSettled(
                targetMask = wifiMask,
                currentMask = 0,
                wifiApState = ShareUserService.WIFI_AP_STATE_DISABLED,
                cachedDownstreams = listOf("ap0"),
            ),
        )
        assertTrue(
            ShareUserService.isTetherMaskStopSettled(
                targetMask = wifiMask,
                currentMask = 0,
                wifiApState = ShareUserService.WIFI_AP_STATE_DISABLED,
                cachedDownstreams = emptyList(),
            ),
        )
        assertEquals(13, ShareUserService.CONTRACT_VERSION)
    }

    @Test
    fun ownedTestTunMarkerIsolatesDebugAndReleasePackages() {
        val tempDir = Files.createTempDirectory("owned_testtun_test").toFile()
        try {
            val releasePkg = "com.nb4a.throne.yusa"
            val debugPkg = "com.nb4a.throne.yusa.debug"

            ShareUserService.persistOwnedTestTunInterface(releasePkg, "testtun3", tempDir)
            assertEquals("testtun3", ShareUserService.readOwnedTestTunInterface(releasePkg, tempDir))
            assertNull(ShareUserService.readOwnedTestTunInterface(debugPkg, tempDir))

            // Debug build only ever selects its own owned testtun*, never release's testtun3
            assertTrue(
                ShareUserService.selectOwnedOrphanedTestInterfaces(
                    networkIfaces = listOf("wlan0", "testtun3", "rmnet_data0"),
                    activeIface = null,
                    persistedOwnedIface = ShareUserService.readOwnedTestTunInterface(debugPkg, tempDir),
                ).isEmpty(),
            )

            // If debug had previously crashed owning testtun4, only testtun4 is selected (testtun3 stays untouched)
            ShareUserService.persistOwnedTestTunInterface(debugPkg, "testtun4", tempDir)
            assertEquals(
                setOf("testtun4"),
                ShareUserService.selectOwnedOrphanedTestInterfaces(
                    networkIfaces = listOf("testtun3", "testtun4"),
                    activeIface = null,
                    persistedOwnedIface = ShareUserService.readOwnedTestTunInterface(debugPkg, tempDir),
                ),
            )

            ShareUserService.clearOwnedTestTunInterface(debugPkg, tempDir)
            assertNull(ShareUserService.readOwnedTestTunInterface(debugPkg, tempDir))
            assertEquals("testtun3", ShareUserService.readOwnedTestTunInterface(releasePkg, tempDir))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun sanitizePackageForMarkerReplacesUnsafeCharacters() {
        assertEquals("com.nb4a.throne.yusa.debug", ShareUserService.sanitizePackageForMarker("com.nb4a.throne.yusa.debug"))
        assertEquals("com.foo_bar", ShareUserService.sanitizePackageForMarker("com.foo/bar"))
        assertEquals("default", ShareUserService.sanitizePackageForMarker("   "))
    }

    @Test
    fun destroySkipsCleanupWhenAlreadyClean() {
        assertFalse(ShareUserService.shouldCleanupOnDestroy(null, null, null))
        assertTrue(ShareUserService.shouldCleanupOnDestroy("testtun4", null, null))
        assertTrue(ShareUserService.shouldCleanupOnDestroy(null, Any(), null))
        assertTrue(ShareUserService.shouldCleanupOnDestroy(null, null, Any()))
    }

    @Test
    fun initialUpstreamSettleIsShortWhenHotspotAlreadyOpen() {
        assertEquals(650L, ShizukuBackend.resolveInitialUpstreamSettleMs(hotspotWasAlreadyOpen = true))
        assertEquals(3_500L, ShizukuBackend.resolveInitialUpstreamSettleMs(hotspotWasAlreadyOpen = false))
    }
}
