package io.nekohasekai.sagernet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Pure JVM tests for [LanSharingNetwork]: Clash API URL construction from sign-encoded port,
 * hotspot interface / IP classification, and graceful failure when the Clash endpoint errors.
 */
class LanSharingNetworkLogicTest {

    @Test
    fun clashUrlUsesAbsPortAndDefaultsWhenZero() {
        assertEquals(
            "http://127.0.0.1:9090/connections",
            LanSharingNetwork.buildClashConnectionsUrl(-9090),
        )
        assertEquals(
            "http://127.0.0.1:9091/connections",
            LanSharingNetwork.buildClashConnectionsUrl(9091),
        )
        assertEquals(
            "http://127.0.0.1:9090/connections",
            LanSharingNetwork.buildClashConnectionsUrl(0),
        )
    }

    @Test
    fun excludesCellularAndVirtualInterfacesAndMatchesHotspotOnes() {
        assertTrue(LanSharingNetwork.isCellularOrVirtualInterface("rmnet0"))
        assertTrue(LanSharingNetwork.isCellularOrVirtualInterface("r_rmnet_data0"))
        assertTrue(LanSharingNetwork.isCellularOrVirtualInterface("dummy0"))
        assertTrue(LanSharingNetwork.isCellularOrVirtualInterface("tun0"))
        assertFalse(LanSharingNetwork.isCellularOrVirtualInterface("wlan1"))
        assertFalse(LanSharingNetwork.isCellularOrVirtualInterface("ap0"))

        assertTrue(LanSharingNetwork.isHotspotInterfaceName("ap0"))
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("wlan1"))
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("swlan0"))
        assertTrue(LanSharingNetwork.isHotspotInterfaceName("softap0"))
        assertFalse(LanSharingNetwork.isHotspotInterfaceName("wlan0"))
        assertFalse(LanSharingNetwork.isHotspotInterfaceName("rmnet_data1"))

        assertTrue(LanSharingNetwork.isValidHotspotIp("192.168.43.1"))
        assertFalse(LanSharingNetwork.isValidHotspotIp("127.0.0.1"))
        assertFalse(LanSharingNetwork.isValidHotspotIp("169.254.1.1"))
        assertFalse(LanSharingNetwork.isValidHotspotIp("172.19.0.1"))
    }

    @Test
    fun isPrivateIpv4MatchesRfc1918Only() {
        // Private ranges count.
        assertTrue(LanSharingNetwork.isPrivateIpv4("192.168.43.1"))
        assertTrue(LanSharingNetwork.isPrivateIpv4("10.125.69.74"))
        assertTrue(LanSharingNetwork.isPrivateIpv4("172.20.10.1"))
        assertTrue(LanSharingNetwork.isPrivateIpv4("172.16.0.1"))
        assertTrue(LanSharingNetwork.isPrivateIpv4("172.31.255.255"))
        // Everything else does not: CGNAT, link-local, loopback, public, garbage.
        assertFalse(LanSharingNetwork.isPrivateIpv4("100.64.0.1"))
        assertFalse(LanSharingNetwork.isPrivateIpv4("172.32.0.1"))
        assertFalse(LanSharingNetwork.isPrivateIpv4("169.254.1.1"))
        assertFalse(LanSharingNetwork.isPrivateIpv4("127.0.0.1"))
        assertFalse(LanSharingNetwork.isPrivateIpv4("8.8.8.8"))
        assertFalse(LanSharingNetwork.isPrivateIpv4("192.168.1"))
        assertFalse(LanSharingNetwork.isPrivateIpv4("192.168.999.1"))
        assertFalse(LanSharingNetwork.isPrivateIpv4(""))
    }

    @Test
    fun hotspotCandidatesPickEveryUpInterfaceHoldingAPrivateIpv4() {
        val interfaces = listOf(
            // ConnectivityManager's own Wi-Fi client side: never a hotspot candidate.
            LanSharingIface("wlan0", true, false, listOf(LanSharingAddress("192.168.1.23", 24))),
            // Cellular and virtual helpers are excluded by name.
            LanSharingIface("rmnet_data0", true, false, listOf(LanSharingAddress("10.44.2.1", null))),
            LanSharingIface("tun0", true, false, listOf(LanSharingAddress("10.19.0.1", null))),
            LanSharingIface("ccmni0", true, false, listOf(LanSharingAddress("192.168.99.1", null))),
            // Explicitly tethered AP interface: leads the result.
            LanSharingIface("ap0", true, false, listOf(LanSharingAddress("10.125.69.74", 24))),
            // USB tethering (RNDIS) and a second Wi-Fi interface the OS does not report: candidates.
            LanSharingIface("usb0", true, false, listOf(LanSharingAddress("192.168.43.1", null))),
            LanSharingIface("wlan1", true, false, listOf(LanSharingAddress("172.20.10.1", 30))),
            // Loopback is never a candidate; a down interface neither.
            LanSharingIface("lo", true, true, listOf(LanSharingAddress("127.0.0.1", null))),
            LanSharingIface("ap1", false, false, listOf(LanSharingAddress("192.168.101.1", 24))),
        )
        val candidates = LanSharingNetwork.hotspotCandidates(
            interfaces = interfaces,
            tetheredIfaces = listOf("ap0"),
            knownIfaces = setOf("wlan0"),
            wifiIp = "192.168.1.23",
        )
        assertEquals(listOf("ap0", "usb0", "wlan1"), candidates.map { it.interfaceName })
        assertEquals("10.125.69.74", candidates[0].ip)
        assertEquals(24, candidates[0].prefixLength)
        // A carrier-only private IP (rmnet) must never leak into the candidates.
        assertFalse(candidates.any { it.ip == "10.44.2.1" })
    }

    @Test
    fun hotspotCandidatesRejectCgnatAndWifiAddresses() {
        val cgnatOnly = listOf(
            LanSharingIface("rndis0", true, false, listOf(LanSharingAddress("100.64.0.1", 24))),
        )
        assertTrue(
            LanSharingNetwork.hotspotCandidates(cgnatOnly, emptyList(), emptySet(), null).isEmpty()
        )
        // The same interface name ConnectivityManager knows is excluded even when it is tethered.
        val known = listOf(
            LanSharingIface("wlan1", true, false, listOf(LanSharingAddress("192.168.43.1", null))),
        )
        assertTrue(
            LanSharingNetwork.hotspotCandidates(known, listOf("wlan1"), setOf("wlan1"), null).isEmpty()
        )
    }

    @Test
    fun pollerUsesAbsPortAndSecretHeaderAndReturnsNullOnError() {
        var capturedUrl: String? = null
        var capturedAuth: String? = null
        val body = LanSharingNetwork.pollConnectionsWith(
            rawPort = -9191,
            secret = "my-secret",
            transport = { url, authHeader ->
                capturedUrl = url
                capturedAuth = authHeader
                "{\"connections\":[]}"
            },
        )
        assertEquals("http://127.0.0.1:9191/connections", capturedUrl)
        assertEquals("Bearer my-secret", capturedAuth)
        assertEquals("{\"connections\":[]}", body)

        // Blank secret -> no Authorization header; transport exception -> null (never throws).
        var blankAuth: String? = "not-null"
        val failed = LanSharingNetwork.pollConnectionsWith(
            rawPort = 9090,
            secret = "   ",
            transport = { _, authHeader ->
                blankAuth = authHeader
                throw RuntimeException("HTTP 401")
            },
        )
        assertNull(blankAuth)
        assertNull(failed)
    }

    @Test
    fun reflectionAndProcNetGuardsRespectApiLevelAndUid() {
        // Untrusted app process on Android 11+ (API 30+) must never reflect getTetheredIfaces
        assertFalse(LanSharingNetwork.canReflectTetheredIfaces(sdkInt = 30, uid = 10000))
        assertFalse(LanSharingNetwork.canReflectTetheredIfaces(sdkInt = 35, uid = 10234))
        // Shell (2000), root (0), and pre-R devices remain allowed
        assertTrue(LanSharingNetwork.canReflectTetheredIfaces(sdkInt = 35, uid = 2000))
        assertTrue(LanSharingNetwork.canReflectTetheredIfaces(sdkInt = 35, uid = 0))
        assertTrue(LanSharingNetwork.canReflectTetheredIfaces(sdkInt = 29, uid = 10234))

        // /proc/net/* is restricted in untrusted app processes on Android 10+ (API 29+)
        assertTrue(LanSharingNetwork.isProcNetRestrictedForCurrentProcess(sdkInt = 29, uid = 10000))
        assertTrue(LanSharingNetwork.isProcNetRestrictedForCurrentProcess(sdkInt = 35, uid = 10234))
        assertFalse(LanSharingNetwork.isProcNetRestrictedForCurrentProcess(sdkInt = 33, uid = 2000))
        assertFalse(LanSharingNetwork.isProcNetRestrictedForCurrentProcess(sdkInt = 33, uid = 0))
        assertFalse(LanSharingNetwork.isProcNetRestrictedForCurrentProcess(sdkInt = 28, uid = 10234))
    }

    @Test
    fun loopbackOnlyPortProbeDetectsMismatchWithoutProcNet() {
        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            assertTrue(LanSharingNetwork.isTcpEndpointOpen("127.0.0.1", port, timeoutMs = 250))
            assertTrue(
                LanSharingNetwork.isPortBoundLoopbackOnly(
                    port = port,
                    nonLoopbackLocalIps = listOf("192.168.43.1"),
                    endpointOpenProbe = { host, p, t ->
                        if (host == "127.0.0.1") LanSharingNetwork.isTcpEndpointOpen(host, p, t) else false
                    },
                ),
            )
            assertFalse(
                LanSharingNetwork.isPortBoundLoopbackOnly(
                    port = port,
                    nonLoopbackLocalIps = listOf("192.168.43.1"),
                    endpointOpenProbe = { _, _, _ -> true },
                ),
            )
            assertFalse(
                LanSharingNetwork.isPortBoundLoopbackOnly(
                    port = port,
                    nonLoopbackLocalIps = emptyList(),
                ),
            )
        }
    }
}
