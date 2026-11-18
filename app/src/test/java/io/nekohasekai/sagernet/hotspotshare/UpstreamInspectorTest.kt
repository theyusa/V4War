package io.nekohasekai.sagernet.hotspotshare

import io.nekohasekai.sagernet.hotspotshare.shizuku.ShareUserService
import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamInspector
import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamReading
import io.nekohasekai.sagernet.hotspotshare.shizuku.UpstreamTolerance
import io.nekohasekai.sagernet.hotspotshare.shizuku.classifyUpstream
import io.nekohasekai.sagernet.utils.LanSharingAddress
import io.nekohasekai.sagernet.utils.LanSharingIface
import io.nekohasekai.sagernet.utils.LanSharingLogics
import io.nekohasekai.sagernet.utils.LanSharingNetwork
import io.nekohasekai.sagernet.utils.TetherKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun parsesUsbAndBluetoothDownstreamsFromDumpsysTethering() {
        val sampleDumpsys = """
            Tethering:
              mUpstreamNetworkMonitor:
                Current upstream interface(s): [ testtun0, ncm0 ]
              Tethered interfaces:
                rndis0 - TetheredState - lastError = 0
                usb0 - TetheredState - lastError = 0
                ncm0 - TetheredState - lastError = 0
                bt-pan - TetheredState - lastError = 0
                bt-pan0 - TetheredState - lastError = 0
                bnep0 - TetheredState - lastError = 0
                ap0 - TetheredState - lastError = 0
                swlan0 - TetheredState - lastError = 0
                softap0 - TetheredState - lastError = 0
        """.trimIndent()

        val upstreams = UpstreamInspector.parseUpstreamInterfaces(sampleDumpsys)
        assertTrue(upstreams.contains("testtun0"))
        assertTrue(upstreams.contains("ncm0"))

        val downstreams = UpstreamInspector.parseTetheredDownstreams(sampleDumpsys)
        assertEquals(
            listOf("rndis0", "usb0", "ncm0", "bt-pan", "bt-pan0", "bnep0", "ap0", "swlan0", "softap0"),
            downstreams,
        )
    }

    @Test
    fun classifyDownstreamTypeIdentifiesWifiUsbBluetoothAndOther() {
        assertEquals("WIFI", UpstreamInspector.classifyDownstreamType("wlan1"))
        assertEquals("WIFI", UpstreamInspector.classifyDownstreamType("ap0"))
        assertEquals("WIFI", UpstreamInspector.classifyDownstreamType("swlan0"))
        assertEquals("WIFI", UpstreamInspector.classifyDownstreamType("softap0"))

        assertEquals("USB", UpstreamInspector.classifyDownstreamType("rndis0"))
        assertEquals("USB", UpstreamInspector.classifyDownstreamType("usb0"))
        assertEquals("USB", UpstreamInspector.classifyDownstreamType("ncm0"))

        assertEquals("BLUETOOTH", UpstreamInspector.classifyDownstreamType("bt-pan"))
        assertEquals("BLUETOOTH", UpstreamInspector.classifyDownstreamType("bt-pan0"))
        assertEquals("BLUETOOTH", UpstreamInspector.classifyDownstreamType("bnep0"))

        assertEquals("OTHER", UpstreamInspector.classifyDownstreamType("wlan0"))
        assertEquals("OTHER", UpstreamInspector.classifyDownstreamType("rmnet_data0"))
        assertEquals("OTHER", UpstreamInspector.classifyDownstreamType("testtun0"))
        assertEquals("OTHER", UpstreamInspector.classifyDownstreamType("eth0"))
    }

    @Test
    fun detectActiveTetherTypesCombinesDumpsysAndNetworkInterfacesSafely() {
        val multiDumpsys = """
            Tethering:
              Tethered interfaces:
                rndis0 - TetheredState - lastError = 0
                bt-pan - TetheredState - lastError = 0
                ap0 - TetheredState - lastError = 0
        """.trimIndent()
        assertEquals(
            setOf("USB", "BLUETOOTH", "WIFI"),
            UpstreamInspector.detectActiveTetherTypes(multiDumpsys),
        )

        // Explicit wlan0 inside TetheredState counts as WIFI SoftAP on single-radio devices.
        val wlan0TetherDumpsys = """
            Tethering:
              Tethered interfaces:
                wlan0 - TetheredState - lastError = 0
        """.trimIndent()
        assertEquals(
            setOf("WIFI"),
            UpstreamInspector.detectActiveTetherTypes(wlan0TetherDumpsys),
        )

        // Normal wlan0 STA from networkInterfaces must NOT trigger WIFI tethering.
        assertEquals(
            setOf("USB", "BLUETOOTH"),
            UpstreamInspector.detectActiveTetherTypes(
                dumpsysOutput = null,
                networkInterfaces = listOf("wlan0", "ncm0", "bnep0", "testtun0"),
            ),
        )
        assertEquals(
            setOf("BLUETOOTH", "WIFI"),
            UpstreamInspector.detectActiveTetherTypes(
                dumpsysOutput = null,
                networkInterfaces = listOf("bt-pan", "swlan0"),
            ),
        )
        assertEquals(
            setOf("USB", "WIFI"),
            UpstreamInspector.detectActiveTetherTypes(
                dumpsysOutput = "  usb0 - TetheredState",
                networkInterfaces = listOf("wlan1"),
            ),
        )
        assertTrue(UpstreamInspector.detectActiveTetherTypes(null, emptyList()).isEmpty())
    }

    @Test
    fun lanSharingNetworkSupportsUsbAndBluetoothInterfaces() {
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("ap0"))
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("swlan0"))
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("softap0"))
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("wlan1"))
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("rndis0"))
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("usb0"))
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("ncm0"))
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("bt-pan"))
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("bt-pan0"))
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("bnep0"))
        assertFalse(LanSharingNetwork.isHotspotInterfaceName("wlan0"))
        assertFalse(LanSharingNetwork.isHotspotInterfaceName("rmnet_data0"))

        assertEquals(TetherKind.WIFI, LanSharingNetwork.getTetherKindForInterface("ap0"))
        assertEquals(TetherKind.WIFI, LanSharingNetwork.getTetherKindForInterface("swlan0"))
        assertEquals(TetherKind.WIFI, LanSharingNetwork.getTetherKindForInterface("softap0"))
        assertEquals(TetherKind.WIFI, LanSharingNetwork.getTetherKindForInterface("wlan1"))
        assertEquals(TetherKind.USB, LanSharingNetwork.getTetherKindForInterface("rndis0"))
        assertEquals(TetherKind.USB, LanSharingNetwork.getTetherKindForInterface("usb0"))
        assertEquals(TetherKind.USB, LanSharingNetwork.getTetherKindForInterface("ncm0"))
        assertEquals(TetherKind.BLUETOOTH, LanSharingNetwork.getTetherKindForInterface("bt-pan"))
        assertEquals(TetherKind.BLUETOOTH, LanSharingNetwork.getTetherKindForInterface("bt-pan0"))
        assertEquals(TetherKind.BLUETOOTH, LanSharingNetwork.getTetherKindForInterface("bnep0"))
        assertEquals(TetherKind.OTHER, LanSharingNetwork.getTetherKindForInterface("wlan0"))
        assertEquals(TetherKind.OTHER, LanSharingNetwork.getTetherKindForInterface("eth0"))

        val interfaces = listOf(
            LanSharingIface("wlan0", true, false, listOf(LanSharingAddress("192.168.1.50", 24))),
            LanSharingIface("ncm0", true, false, listOf(LanSharingAddress("192.168.42.129", 24))),
            LanSharingIface("bt-pan", true, false, listOf(LanSharingAddress("192.168.44.1", 24))),
            LanSharingIface("bnep0", true, false, listOf(LanSharingAddress("192.168.45.1", 24))),
            LanSharingIface("usb0", true, false, listOf(LanSharingAddress("192.168.46.1", 24))),
        )
        val candidates = LanSharingNetwork.hotspotCandidates(
            interfaces = interfaces,
            tetheredIfaces = listOf("bt-pan"),
            knownIfaces = setOf("wlan0"),
            wifiIp = "192.168.1.50",
        )
        assertEquals(listOf("bt-pan", "ncm0", "bnep0", "usb0"), candidates.map { it.interfaceName })
    }

    @Test
    fun shareUserServiceDownstreamMatchersAndShizukuConstantsMatchContract() {
        assertTrue(ShareUserService.isUsbDownstreamName("rndis0"))
        assertTrue(ShareUserService.isUsbDownstreamName("usb0"))
        assertTrue(ShareUserService.isUsbDownstreamName("ncm0"))
        assertFalse(ShareUserService.isUsbDownstreamName("bt-pan"))

        assertTrue(ShareUserService.isAnyTetherDownstreamName("ap0"))
        assertEquals(LanSharingNetwork.USB_TETHER_ENABLED, ShareUserService.isAnyTetherDownstreamName("ncm0"))
        // Bluetooth is removed from Shizuku mode.
        assertFalse(ShareUserService.isAnyTetherDownstreamName("bnep0"))
        assertFalse(ShareUserService.isAnyTetherDownstreamName("bt-pan"))
        // Confirmed downstreams from TetheringManager include wlan0 (single-radio SoftAP).
        assertTrue(ShareUserService.isWifiDownstreamName("wlan0"))
        assertTrue(ShareUserService.isAnyTetherDownstreamName("wlan0"))
        assertFalse(ShareUserService.isAnyTetherDownstreamName("rmnet_data0"))
        assertFalse(ShareUserService.isAnyTetherDownstreamName("testtun0"))
        assertFalse(ShareUserService.isAnyTetherDownstreamName("eth0"))

        assertEquals(0, ShizukuBackend.TETHERING_WIFI)
        assertEquals(1, ShizukuBackend.TETHERING_USB)
        assertEquals(4, ShizukuBackend.TETHERING_NCM)
        assertEquals(1, ShizukuBackend.TETHER_MASK_WIFI)
        assertEquals(2, ShizukuBackend.TETHER_MASK_USB)
        assertEquals(ShareUserService.TETHERING_WIFI, ShizukuBackend.TETHERING_WIFI)
        assertEquals(ShareUserService.TETHERING_USB, ShizukuBackend.TETHERING_USB)
        assertEquals(ShareUserService.TETHER_MASK_WIFI, ShizukuBackend.TETHER_MASK_WIFI)
        assertEquals(ShareUserService.TETHER_MASK_USB, ShizukuBackend.TETHER_MASK_USB)
    }

    @Test
    fun parsesAvailableStateErrorsFromDumpsysTethering() {
        val sampleDumpsys = """
            Tether state:
              rndis0 - AvailableState - lastError = 8
              wlan2 - TetheredState - lastError = 0
              ncm0 - AvailableState - lastError = 0
        """.trimIndent()

        val errors = UpstreamInspector.parseAvailableDownstreamErrors(sampleDumpsys)
        assertEquals(mapOf("rndis0" to 8, "ncm0" to 0), errors)
        assertTrue(UpstreamInspector.parseTetheredDownstreams(sampleDumpsys) == listOf("wlan2"))
    }

    @Test
    fun usbTetheringIsDeactivatedAndBluetoothRemovedFromShizukuMasks() {
        assertFalse(LanSharingNetwork.USB_TETHER_ENABLED)

        // When USB is deactivated, USB and Bluetooth interfaces never set bits in Shizuku's tether mask.
        val disabledMask = ShareUserService.computeActiveTetherMaskFromInputs(
            wifiApActive = false,
            downstreams = listOf("rndis0", "ncm0", "bt-pan", "bnep0"),
            networkInterfaces = listOf("usb0", "bnep0"),
            usbEnabled = false,
        )
        assertEquals(0, disabledMask)

        // Wi-Fi AP and confirmed Wi-Fi downstreams still set TETHER_MASK_WIFI; Bluetooth stays excluded.
        val wifiOnlyMask = ShareUserService.computeActiveTetherMaskFromInputs(
            wifiApActive = true,
            downstreams = listOf("wlan0", "bt-pan"),
            networkInterfaces = listOf("bnep0"),
            usbEnabled = false,
        )
        assertEquals(ShareUserService.TETHER_MASK_WIFI, wifiOnlyMask)

        // When USB flag is flipped on, USB mask works while Bluetooth remains excluded.
        val usbEnabledMask = ShareUserService.computeActiveTetherMaskFromInputs(
            wifiApActive = false,
            downstreams = listOf("rndis0", "bt-pan"),
            networkInterfaces = listOf("bnep0"),
            usbEnabled = true,
        )
        assertEquals(ShareUserService.TETHER_MASK_USB, usbEnabledMask)

        assertEquals(
            ShizukuBackend.TETHER_MASK_WIFI,
            ShizukuBackend.sanitizeActiveTetherMask(0b111, usbEnabled = false),
        )
        assertEquals(
            ShizukuBackend.TETHER_MASK_WIFI or ShizukuBackend.TETHER_MASK_USB,
            ShizukuBackend.sanitizeActiveTetherMask(0b111, usbEnabled = true),
        )
    }

    @Test
    fun disabledUsbAndRemovedBluetoothRejectSetTetherTypeEnabledImmediately() = runBlocking {
        val btResult = ShizukuBackend.setTetherTypeEnabled(type = 2, enabled = true, context = null)
        assertTrue(btResult.isFailure)

        val usbResult = ShizukuBackend.setTetherTypeEnabled(
            type = ShizukuBackend.TETHERING_USB,
            enabled = true,
            context = null,
        )
        assertTrue(usbResult.isFailure)

        val ncmResult = ShizukuBackend.setTetherTypeEnabled(
            type = ShizukuBackend.TETHERING_NCM,
            enabled = true,
            context = null,
        )
        assertTrue(ncmResult.isFailure)
    }

    @Test
    fun callbackClientCountAvoidsFallbackWhenRegisteredAndFormatsHostLines() {
        var fallbackCalls = 0
        val fromCallbacks = ShareUserService.resolveConnectedClientCount(
            softApRegistered = true,
            softApCount = 2,
            tetherRegistered = true,
            tetherCount = 3,
            fallback = {
                fallbackCalls++
                99
            },
        )
        assertEquals(3, fromCallbacks)
        assertEquals(0, fallbackCalls)

        val zeroFromSoftAp = ShareUserService.resolveConnectedClientCount(
            softApRegistered = true,
            softApCount = 0,
            tetherRegistered = false,
            tetherCount = 0,
            fallback = {
                fallbackCalls++
                5
            },
        )
        assertEquals(0, zeroFromSoftAp)
        assertEquals(0, fallbackCalls)

        val fallbackCount = ShareUserService.resolveConnectedClientCount(
            softApRegistered = false,
            softApCount = 0,
            tetherRegistered = false,
            tetherCount = 0,
            fallback = {
                fallbackCalls++
                4
            },
        )
        assertEquals(4, fallbackCount)
        assertEquals(1, fallbackCalls)

        val hostLines = ShareUserService.formatCachedClientHostLines(
            listOf(
                ShareUserService.CachedTetherClient("192.168.43.10", "MyLaptop", "aa:bb:cc:dd:ee:ff", 0),
                ShareUserService.CachedTetherClient("192.168.43.11", null, "11:22:33:44:55:66", 0),
            ),
        )
        assertEquals(listOf("AddressInfo {address 192.168.43.10/24, hostname MyLaptop}"), hostLines)
        assertEquals(
            mapOf("192.168.43.10" to "MyLaptop"),
            LanSharingLogics.parseNeighborHostnames(hostLines.joinToString("\n")),
        )
        assertEquals(
            listOf("ap0", "wlan2"),
            ShareUserService.extractTetheredIfaceNames(listOf("ap0", "wlan2", "ap0", "")),
        )
    }
}
