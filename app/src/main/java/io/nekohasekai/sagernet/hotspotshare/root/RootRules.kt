package io.nekohasekai.sagernet.hotspotshare.root

data class VpnRouteInfo(
    val tunInterface: String,
    val table: String,
)

data class HotspotSubnet(
    val iface: String,
    val cidr: String,
)

object RootRules {
    const val CHAIN_FORWARD = "throne_fwd"
    const val CHAIN_NAT = "throne_nat"
    const val CHAIN_FORWARD_V6 = "throne_fwd6"

    const val PRIORITY_LOCAL_SUBNET = 10099
    const val PRIORITY_VPN_ROUTING = 10100

    private val TUN_REGEX = Regex("""\bdev\s+((?:tun|testtun|v4war-tun)\w*)""")
    private val TABLE_REGEX = Regex("""\btable\s+(\S+)""")
    private val SUBNET_REGEX = Regex("""^(\d+\.\d+\.\d+\.\d+/\d+)""")

    /**
     * Parses the output of `ip route show table all` to find an active VPN TUN interface and its routing table.
     * Prefers non-local tables containing default routes or link routes for the TUN interface.
     */
    fun parseVpnRoute(ipRouteLines: List<String>): VpnRouteInfo? {
        var fallbackRoute: VpnRouteInfo? = null

        for (line in ipRouteLines) {
            val tunMatch = TUN_REGEX.find(line) ?: continue
            val iface = tunMatch.groupValues[1]

            val tableMatch = TABLE_REGEX.find(line)
            val table = tableMatch?.groupValues?.get(1) ?: "main"

            // Skip local table routes (e.g. broadcast, local interface IP)
            if (table == "local") continue

            // A default route or route explicitly specifying the VPN table is highest priority
            if (line.startsWith("default")) {
                return VpnRouteInfo(tunInterface = iface, table = table)
            }

            if (fallbackRoute == null) {
                fallbackRoute = VpnRouteInfo(tunInterface = iface, table = table)
            }
        }

        return fallbackRoute
    }

    /**
     * Identifies local IPv4 subnets for the given hotspot interfaces (e.g. 192.168.43.0/24 dev wlan0).
     */
    fun parseHotspotSubnets(
        ipRouteLines: List<String>,
        hotspotIfaces: Collection<String>,
    ): List<HotspotSubnet> {
        val result = mutableListOf<HotspotSubnet>()
        val seen = mutableSetOf<String>()

        for (line in ipRouteLines) {
            val tableMatch = TABLE_REGEX.find(line)
            val table = tableMatch?.groupValues?.get(1) ?: "main"
            if (table == "local") continue

            val subnetMatch = SUBNET_REGEX.find(line) ?: continue
            val cidr = subnetMatch.groupValues[1]

            for (iface in hotspotIfaces) {
                if (line.contains("dev $iface") && !seen.contains(cidr)) {
                    seen.add(cidr)
                    result.add(HotspotSubnet(iface = iface, cidr = cidr))
                }
            }
        }

        return result
    }

    /**
     * Builds root shell commands to configure policy routing, iptables forwarding & NAT,
     * and IPv6 leak protection.
     */
    fun buildSetupCommands(
        vpn: VpnRouteInfo,
        hotspotIfaces: Collection<String>,
        subnets: Collection<HotspotSubnet>,
    ): List<String> {
        val cmds = mutableListOf<String>()

        // 1. Enable IPv4 forwarding in kernel
        cmds.add("sysctl -w net.ipv4.ip_forward=1 2>/dev/null || echo 1 > /proc/sys/net/ipv4/ip_forward")

        // 2. IPv4 FORWARD filter chain
        cmds.add("iptables -N $CHAIN_FORWARD 2>/dev/null || true")
        cmds.add("iptables -C FORWARD -j $CHAIN_FORWARD 2>/dev/null || iptables -I FORWARD 1 -j $CHAIN_FORWARD")
        cmds.add("iptables -F $CHAIN_FORWARD")
        for (iface in hotspotIfaces) {
            cmds.add("iptables -A $CHAIN_FORWARD -i $iface -o ${vpn.tunInterface} -j ACCEPT")
            cmds.add("iptables -A $CHAIN_FORWARD -i ${vpn.tunInterface} -o $iface -m state --state RELATED,ESTABLISHED -j ACCEPT")
        }

        // 3. IPv4 NAT MASQUERADE
        cmds.add("iptables -t nat -N $CHAIN_NAT 2>/dev/null || true")
        cmds.add("iptables -t nat -C POSTROUTING -j $CHAIN_NAT 2>/dev/null || iptables -t nat -I POSTROUTING 1 -j $CHAIN_NAT")
        cmds.add("iptables -t nat -F $CHAIN_NAT")
        cmds.add("iptables -t nat -A $CHAIN_NAT -o ${vpn.tunInterface} -j MASQUERADE")

        // 4. IPv6 leak protection: unconditionally drop hotspot IPv6 forward traffic
        cmds.add("ip6tables -N $CHAIN_FORWARD_V6 2>/dev/null || true")
        cmds.add("ip6tables -C FORWARD -j $CHAIN_FORWARD_V6 2>/dev/null || ip6tables -I FORWARD 1 -j $CHAIN_FORWARD_V6")
        cmds.add("ip6tables -F $CHAIN_FORWARD_V6")
        for (iface in hotspotIfaces) {
            cmds.add("ip6tables -A $CHAIN_FORWARD_V6 -i $iface -j DROP")
            cmds.add("ip6tables -A $CHAIN_FORWARD_V6 -o $iface -j DROP")
        }

        // 5. Clean prior rules at our priorities (idempotent)
        cmds.add("while ip rule del pref $PRIORITY_LOCAL_SUBNET 2>/dev/null; do :; done")
        cmds.add("while ip rule del pref $PRIORITY_VPN_ROUTING 2>/dev/null; do :; done")

        // 6. Add local subnet rules so client-to-client and client-to-gateway traffic stays in main table
        for (subnet in subnets) {
            cmds.add("ip rule add from all iif ${subnet.iface} to ${subnet.cidr} lookup main pref $PRIORITY_LOCAL_SUBNET")
        }

        // 7. Add hotspot incoming traffic rule to lookup VPN routing table
        for (iface in hotspotIfaces) {
            cmds.add("ip rule add from all iif $iface lookup ${vpn.table} pref $PRIORITY_VPN_ROUTING")
        }

        return cmds
    }

    /**
     * Builds root shell commands to cleanly tear down rules and custom iptables chains.
     */
    fun buildTeardownCommands(): List<String> {
        return listOf(
            "while ip rule del pref $PRIORITY_LOCAL_SUBNET 2>/dev/null; do :; done",
            "while ip rule del pref $PRIORITY_VPN_ROUTING 2>/dev/null; do :; done",
            "iptables -D FORWARD -j $CHAIN_FORWARD 2>/dev/null || true",
            "iptables -F $CHAIN_FORWARD 2>/dev/null || true",
            "iptables -X $CHAIN_FORWARD 2>/dev/null || true",
            "iptables -t nat -D POSTROUTING -j $CHAIN_NAT 2>/dev/null || true",
            "iptables -t nat -F $CHAIN_NAT 2>/dev/null || true",
            "iptables -t nat -X $CHAIN_NAT 2>/dev/null || true",
            "ip6tables -D FORWARD -j $CHAIN_FORWARD_V6 2>/dev/null || true",
            "ip6tables -F $CHAIN_FORWARD_V6 2>/dev/null || true",
            "ip6tables -X $CHAIN_FORWARD_V6 2>/dev/null || true",
        )
    }
}
