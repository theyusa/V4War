package io.nekohasekai.sagernet.hotspotshare

import io.nekohasekai.sagernet.hotspotshare.root.HotspotSubnet
import io.nekohasekai.sagernet.hotspotshare.root.RootRules
import io.nekohasekai.sagernet.hotspotshare.root.VpnRouteInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootRulesTest {

    private val sampleIpRouteOutput = listOf(
        "10.0.2.0/24 dev wlan0 table wlan0_local proto static scope link",
        "default dev dummy0 table dummy0 proto static scope link",
        "default via 10.0.2.2 dev wlan0 table wlan0 proto static",
        "192.168.43.0/24 dev ap0 proto kernel scope link src 192.168.43.1",
        "default dev tun0 table 102 proto static scope link",
        "198.18.0.0/15 dev tun0 proto kernel scope link src 198.18.0.1",
        "local 198.18.0.1 dev tun0 table local proto kernel scope host src 198.18.0.1",
        "broadcast 198.19.255.255 dev tun0 table local proto kernel scope link src 198.18.0.1",
    )

    @Test
    fun parseVpnRoute_extractsTunAndTable() {
        val vpn = RootRules.parseVpnRoute(sampleIpRouteOutput)
        assertNotNull(vpn)
        assertEquals("tun0", vpn!!.tunInterface)
        assertEquals("102", vpn.table)
    }

    @Test
    fun parseVpnRoute_returnsNullWhenNoTunInterface() {
        val noTun = listOf(
            "default via 10.0.2.2 dev wlan0 table wlan0 proto static",
            "10.0.2.0/24 dev wlan0 proto kernel scope link src 10.0.2.18",
        )
        assertNull(RootRules.parseVpnRoute(noTun))
    }

    @Test
    fun parseHotspotSubnets_findsMatchingSubnet() {
        val subnets = RootRules.parseHotspotSubnets(sampleIpRouteOutput, listOf("ap0"))
        assertEquals(1, subnets.size)
        assertEquals("ap0", subnets[0].iface)
        assertEquals("192.168.43.0/24", subnets[0].cidr)
    }

    @Test
    fun buildSetupCommands_containsAllNecessaryRulesAndIdempotentJumps() {
        val vpn = VpnRouteInfo("tun0", "102")
        val hotspotIfaces = listOf("ap0")
        val subnets = listOf(HotspotSubnet("ap0", "192.168.43.0/24"))

        val cmds = RootRules.buildSetupCommands(vpn, hotspotIfaces, subnets)

        // Must enable IP forwarding
        assertTrue(cmds.any { it.contains("net.ipv4.ip_forward=1") })

        // Must strip existing jumps before inserting exactly one jump
        assertTrue(cmds.any { it.contains("while iptables -D FORWARD -j throne_fwd") })
        assertTrue(cmds.any { it.contains("iptables -I FORWARD 1 -j throne_fwd") })
        assertTrue(cmds.any { it.contains("-i ap0 -o tun0 -j ACCEPT") })
        assertTrue(cmds.any { it.contains("-i tun0 -o ap0 -m state --state RELATED,ESTABLISHED -j ACCEPT") })

        // Must set up throne_nat MASQUERADE
        assertTrue(cmds.any { it.contains("while iptables -t nat -D POSTROUTING -j throne_nat") })
        assertTrue(cmds.any { it.contains("iptables -t nat -I POSTROUTING 1 -j throne_nat") })
        assertTrue(cmds.any { it.contains("iptables -t nat -A throne_nat -o tun0 -j MASQUERADE") })

        // Must set up IPv6 leak protection DROP
        assertTrue(cmds.any { it.contains("while ip6tables -D FORWARD -j throne_fwd6") })
        assertTrue(cmds.any { it.contains("ip6tables -I FORWARD 1 -j throne_fwd6") })
        assertTrue(cmds.any { it.contains("ip6tables -A throne_fwd6 -i ap0 -j DROP") })

        // Must set up policy routing rules at 10099 and 10100
        assertTrue(cmds.any { it.contains("ip rule add from all iif ap0 to 192.168.43.0/24 lookup main pref 10099") })
        assertTrue(cmds.any { it.contains("ip rule add from all iif ap0 lookup 102 pref 10100") })
    }

    @Test
    fun buildTeardownCommands_flushesAndRemovesChainsAndRules() {
        val cmds = RootRules.buildTeardownCommands()
        // Jumps removed before chains deleted
        val jumpRemoveIdx = cmds.indexOfFirst { it.contains("iptables -D FORWARD -j throne_fwd") }
        val chainDeleteIdx = cmds.indexOfFirst { it.contains("iptables -X throne_fwd") }
        assertTrue(jumpRemoveIdx in 0..<chainDeleteIdx)

        assertTrue(cmds.any { it.contains("ip rule del pref 10099") })
        assertTrue(cmds.any { it.contains("ip rule del pref 10100") })
        assertTrue(cmds.any { it.contains("iptables -t nat -X throne_nat") })
        assertTrue(cmds.any { it.contains("ip6tables -X throne_fwd6") })
    }

    @Test
    fun buildBlockTrafficCommands_configuresFailClosedDrop() {
        val cmds = RootRules.buildBlockTrafficCommands(listOf("ap0"))

        // Must delete vpn policy routing
        assertTrue(cmds.any { it.contains("ip rule del pref 10100") })
        // Must clear nat
        assertTrue(cmds.any { it.contains("iptables -t nat -X throne_nat") || it.contains("iptables -t nat -F throne_nat") })
        // Must insert DROP on ap0 in FORWARD and FORWARD_V6
        assertTrue(cmds.any { it.contains("iptables -A throne_fwd -i ap0 -j DROP") })
        assertTrue(cmds.any { it.contains("iptables -A throne_fwd -o ap0 -j DROP") })
        assertTrue(cmds.any { it.contains("ip6tables -A throne_fwd6 -i ap0 -j DROP") })
    }

    @Test
    fun buildSetupCommands_includesDnsHijackTcpmssAndRpFilter() {
        val vpn = VpnRouteInfo("tun0", "102")
        val cmds = RootRules.buildSetupCommands(
            vpn = vpn,
            hotspotIfaces = listOf("ap0"),
            subnets = listOf(HotspotSubnet("ap0", "192.168.43.0/24")),
        )

        assertTrue(cmds.any { it.contains("rp_filter=0") })
        assertTrue(cmds.any { it.contains("TCPMSS --clamp-mss-to-pmtu") })
        assertTrue(cmds.any { it.contains("-t nat -I PREROUTING 1 -j throne_dns") })
        assertTrue(cmds.any { it.contains("--to-destination 1.1.1.1:53") })
        assertTrue(cmds.any { it.contains("-m conntrack --ctstate RELATED,ESTABLISHED") })
    }

    @Test
    fun buildTeardownCommands_removesDnsHijackChain() {
        val cmds = RootRules.buildTeardownCommands()
        val jumpIdx = cmds.indexOfFirst { it.contains("iptables -t nat -D PREROUTING -j throne_dns") }
        val deleteIdx = cmds.indexOfFirst { it.contains("iptables -t nat -X throne_dns") }
        assertTrue(jumpIdx in 0..<deleteIdx)
    }

    @Test
    fun parseVpnRoute_supportsSplitDefaultRoutes() {
        val splitLines = listOf(
            "0.0.0.0/1 dev tun0 table 102 proto static",
            "128.0.0.0/1 dev tun0 table 102 proto static",
            "192.168.43.0/24 dev ap0 proto kernel scope link",
        )
        val parsed = RootRules.parseVpnRoute(splitLines)
        assertEquals("tun0", parsed?.tunInterface)
        assertEquals("102", parsed?.table)
    }
}
