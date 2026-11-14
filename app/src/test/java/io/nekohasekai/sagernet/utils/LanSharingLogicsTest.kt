package io.nekohasekai.sagernet.utils

import io.nekohasekai.sagernet.hotspotshare.FailClosedAction
import io.nekohasekai.sagernet.hotspotshare.ShareFailure
import io.nekohasekai.sagernet.hotspotshare.ShareMethod
import io.nekohasekai.sagernet.hotspotshare.SharePreference
import io.nekohasekai.sagernet.hotspotshare.ShareState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure decisions of the LAN Sharing client list: ARP-table parsing, Clash /connections parsing and the
 * merge/dedupe/sort/expire pipeline that turns raw network state into the device rows.
 */
class LanSharingLogicsTest {

    @Test
    fun arpSkipsLoopbackLinkLocalAndUnresolvedRows() {
        val arp = """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.43.52    0x1         0x2         aa:bb:cc:dd:ee:01     *        wlan1
            192.168.43.53    0x1         0x2         00:00:00:00:00:00     *        wlan1
            127.0.0.4        0x1         0x0         ab:cd:ef:01:02:03     *        lo
            169.254.3.9      0x1         0x2         ab:cd:ef:01:02:04     *        wlan0
            192.168.43.54    0x1         0x2         aa:bb:cc:dd:ee:02     *        ap0
            172.19.0.7       0x1         0x2         ab:cd:ef:01:02:05     *        tun0
        """.trimIndent()
        assertEquals(listOf("192.168.43.52", "192.168.43.54"), LanSharingLogics.parseArpFile(arp))
    }

    @Test
    fun connectionsParsesAndSumsPerSourceIp() {
        val json = """
            {
              "connections": [
                {"metadata": {"sourceIP": "192.168.43.52", "host": "example.com:443"},
                 "upload": 1000, "download": 2000},
                {"metadata": {"sourceIP": "192.168.43.52", "host": "example.net:80"},
                 "upload": 500, "download": 100},
                {"metadata": {"sourceIP": "192.168.43.60", "host": null},
                 "upload": 7, "download": 9}
              ]
            }
        """.trimIndent()
        val devices = LanSharingLogics.parseConnections(json)
        assertEquals(2, devices.size)
        val first = devices.first { it.ip == "192.168.43.52" }
        assertEquals(2, first.activeConnections)
        assertEquals(1500L, first.uploadBytes)
        assertEquals(2100L, first.downloadBytes)
        assertEquals("example.net:80", first.lastHost)
        assertFalse(first.isHotspot)
        // Malformed and empty input never throws and always yields an empty list.
        assertEquals(0, LanSharingLogics.parseConnections("").size)
        assertEquals(0, LanSharingLogics.parseConnections("not json").size)
        assertEquals(0, LanSharingLogics.parseConnections("{\"connections\":[]}").size)
    }

    @Test
    fun mergeDedupesSortsAndExpires() {
        val now = 10_000_000L
        val history = mapOf(
            // Idle beyond the cutoff: expired, must not reappear.
            "192.168.43.70" to LanClientDevice(
                "192.168.43.70", 0, 0L, 0L, null, lastSeenMs = now - 20 * LanSharingLogics.IDLE_MS, isHotspot = false
            ),
            // Historical row inside the cutoff, now quiet: kept and shown as idle.
            "192.168.43.71" to LanClientDevice(
                "192.168.43.71", 0, 0L, 0L, null, lastSeenMs = now - 60_000L, isHotspot = false
            ),
        )
        val clash = listOf(
            // Two entries for the same IP: the poller already aggregated them upstream.
            LanClientDevice("192.168.43.52", 3, 300L, 400L, "a.example.com:443", now, false),
            // isHotspot is decided by the hotspot IP, not by the device.
            LanClientDevice("192.168.43.90", 1, 1L, 2L, "b.example.com:80", now - 1000L, false),
        )
        val arp = listOf("192.168.43.52", "192.168.43.54")

        val merged = LanSharingLogics.mergeDevices(
            history = history,
            arpIps = arp,
            clash = clash,
            hotspotIp = "192.168.43.52",
            hotspotPrefix = null,
            localIps = emptySet(),
            nowMs = now,
        )

        val ips = merged.map { it.ip }
        // No duplicates; history entry with no fresh signal is retained once; expired entry is gone.
        assertTrue(merged.count { it.ip == "192.168.43.52" } == 1)
        assertTrue(merged.count { it.ip == "192.168.43.54" } == 1)
        assertTrue(merged.any { it.ip == "192.168.43.71" })
        assertFalse(ips.contains("192.168.43.70"))
        // Active connections first, then recency within each group.
        assertTrue((merged[0].activeConnections) >= (merged[1].activeConnections))
        assertEquals("192.168.43.52", merged[0].ip)
        // isHotspot comes from the hotspot ip alone.
        assertTrue(merged.first { it.ip == "192.168.43.52" }.isHotspot)
        // Item 3: sharing the hotspot's /24 prefix makes a device a hotspot client, not only the exact interface IP.
        assertTrue(merged.first { it.ip == "192.168.43.90" }.isHotspot)
        assertTrue(merged.first { it.ip == "192.168.43.71" }.isHotspot)
    }

    @Test
    fun connectionsFilterLocalSources() {
        val json = """
            {
              "connections": [
                {"metadata": {"sourceIP": "192.168.43.52", "host": "example.com:443"},
                 "upload": 10, "download": 20},
                {"metadata": {"sourceIP": "127.0.0.5"}, "upload": 1, "download": 1},
                {"metadata": {"sourceIP": "::1"}, "upload": 1, "download": 1},
                {"metadata": {"sourceIP": "172.19.0.9"}, "upload": 1, "download": 1},
                {"metadata": {"sourceIP": ""}, "upload": 1, "download": 1}
              ]
            }
        """.trimIndent()
        val devices = LanSharingLogics.parseConnections(json)
        assertEquals(1, devices.size)
        assertEquals("192.168.43.52", devices[0].ip)
    }

    @Test
    fun sameSubnet24Matches() {
        assertTrue(LanSharingLogics.isSameSubnet24("192.168.43.1", "192.168.43.222"))
        assertFalse(LanSharingLogics.isSameSubnet24("192.168.43.1", "192.168.9.222"))
        assertFalse(LanSharingLogics.isSameSubnet24("::1", "192.168.43.1"))
        assertFalse(LanSharingLogics.isSameSubnet24("not-an-ip", "192.168.43.1"))
        assertFalse(LanSharingLogics.isSameSubnet24("0.0.0.0", "192.168.43.1"))
    }

    @Test
    fun sameSubnetHonorsPrefixAndFallsBackTo24() {
        // Same /30 network (10.125.69.72..75) but the /24s agree; with /30 they do not.
        assertTrue(LanSharingLogics.isSameSubnet("10.125.69.74", "10.125.69.75", 30))
        assertFalse(LanSharingLogics.isSameSubnet("10.125.69.74", "10.125.69.77", 30))
        assertTrue(LanSharingLogics.isSameSubnet("10.125.69.74", "10.125.69.77", 24))
        // A null prefix falls back to the /24 default.
        assertTrue(LanSharingLogics.isSameSubnet("192.168.43.1", "192.168.43.222", null))
        assertFalse(LanSharingLogics.isSameSubnet("192.168.43.1", "192.168.9.222", null))
        // Out-of-range prefixes are clamped; /32 means exact address equality.
        assertTrue(LanSharingLogics.isSameSubnet("10.0.0.1", "10.0.0.1", 32))
        assertFalse(LanSharingLogics.isSameSubnet("10.0.0.1", "10.0.0.2", 32))
        // Garbage and the all-zero address never share a subnet.
        assertFalse(LanSharingLogics.isSameSubnet("::1", "192.168.43.1", null))
        assertFalse(LanSharingLogics.isSameSubnet("not-an-ip", "192.168.43.1", null))
        assertFalse(LanSharingLogics.isSameSubnet("0.0.0.0", "192.168.43.1", null))
    }

    @Test
    fun mergeDevicesUsesReportedHotspotPrefixNotTheDefault() {
        val now = 10_000_000L
        val clash = listOf(
            // Same /30 as the hotspot IP 10.125.69.74 (network .72..75).
            LanClientDevice("10.125.69.75", 1, 1L, 1L, null, now, false),
            // Same /24 but a different /30: only the /24 default would mark it a hotspot client.
            LanClientDevice("10.125.69.77", 1, 2L, 2L, null, now, false),
        )
        val merged = LanSharingLogics.mergeDevices(
            history = emptyMap(),
            arpIps = emptyList(),
            clash = clash,
            hotspotIp = "10.125.69.74",
            hotspotPrefix = 30,
            localIps = emptySet(),
            nowMs = now,
        )
        assertTrue(merged.first { it.ip == "10.125.69.75" }.isHotspot)
        assertFalse(merged.first { it.ip == "10.125.69.77" }.isHotspot)
    }

    @Test
    fun localSourcesNeverBecomeDevices() {
        val now = 10_000_000L
        val arp = listOf("192.168.43.52", "172.19.0.9")
        val clash = listOf(
            LanClientDevice("127.0.0.9", 1, 1L, 1L, "x.example.com:80", now, false),
            LanClientDevice("192.168.43.90", 1, 1L, 2L, null, now, false),
        )
        val history = mapOf(
            // The phone's own hotspot address (detectedHotspotIp) fed back through history.
            "192.168.43.1" to LanClientDevice(
                "192.168.43.1", 0, 0L, 0L, null, lastSeenMs = now, isHotspot = false
            ),
        )
        val merged = LanSharingLogics.mergeDevices(
            history = history,
            arpIps = arp,
            clash = clash,
            hotspotIp = "192.168.43.1",
            hotspotPrefix = null,
            localIps = setOf("192.168.43.1", "192.168.43.7"),
            nowMs = now,
        )
        val ips = merged.map { it.ip }
        assertFalse(ips.contains("172.19.0.9"))
        assertFalse(ips.contains("127.0.0.9"))
        assertFalse(ips.contains("192.168.43.1"))
        assertTrue(ips.contains("192.168.43.52"))
        assertTrue(ips.contains("192.168.43.90"))
        // Any device on the hotspot subnet counts as a hotspot client, not just the interface IP itself.
        assertTrue(merged.first { it.ip == "192.168.43.52" }.isHotspot)
        assertTrue(merged.first { it.ip == "192.168.43.90" }.isHotspot)
    }

    @Test
    fun clashApiSecretGeneratedOnlyWhenBlank() {
        // Blank: a secret must be generated; an existing one is never overwritten.
        assertTrue(LanSharingLogics.clashApiSecretShouldBeGenerated(""))
        assertTrue(LanSharingLogics.clashApiSecretShouldBeGenerated("   "))
        assertFalse(LanSharingLogics.clashApiSecretShouldBeGenerated("FixedSecret"))

        val first = LanSharingLogics.generateClashApiSecret()
        val second = LanSharingLogics.generateClashApiSecret()
        assertEquals(64, first.length)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
        // Two generations are independent fresh randomness.
        assertNotEquals(first, second)
    }

    @Test
    fun maskSocks5LinkHidesPasswordOnly() {
        // user:pass@ is masked; the host, port and remark stay readable.
        assertEquals(
            "socks5://yusa:\u2022\u2022\u2022\u2022@192.168.43.1:2080#LAN",
            LanSharingLogics.maskSocks5Link("socks5://yusa:secretPw@192.168.43.1:2080#LAN"),
        )
        // A password containing @ is masked to its first user:pass pair boundary (last @ wins: the authority
        // boundary is the last one before the host).
        assertEquals(
            "socks5://yusa:\u2022\u2022\u2022\u2022@192.168.43.1:2080",
            LanSharingLogics.maskSocks5Link("socks5://yusa:pw@with@it@192.168.43.1:2080"),
        )
        // A link without credentials is unchanged.
        assertEquals(
            "socks5://192.168.43.1:2080",
            LanSharingLogics.maskSocks5Link("socks5://192.168.43.1:2080"),
        )
        // Garbage in, unchanged out: never throws.
        assertEquals("", LanSharingLogics.maskSocks5Link(""))
        assertEquals("socks5://host:2080#x", LanSharingLogics.maskSocks5Link("socks5://host:2080#x"))
    }

    @Test
    fun switchOnEnablesClashApiWhenOff() {
        // Negative sign-encoded port means Clash API is off: enabling LAN sharing flips it positive and requests reload.
        val fromNegative = LanSharingLogics.ensureClashApiEnabledPort(-9090)
        assertEquals(9090, fromNegative.newRawPort)
        assertTrue(fromNegative.changed)

        val fromZero = LanSharingLogics.ensureClashApiEnabledPort(0)
        assertEquals(9090, fromZero.newRawPort)
        assertTrue(fromZero.changed)

        // Already positive: kept untouched, no extra reload.
        val alreadyOn = LanSharingLogics.ensureClashApiEnabledPort(9091)
        assertEquals(9091, alreadyOn.newRawPort)
        assertFalse(alreadyOn.changed)
    }

    @Test
    fun firstEnableWithoutConfigRefusesInvalidOrBlankPort() {
        assertEquals(null, LanSharingLogics.validateDialogPort(""))
        assertEquals(null, LanSharingLogics.validateDialogPort("   "))
        assertEquals(null, LanSharingLogics.validateDialogPort("0"))
        assertEquals(null, LanSharingLogics.validateDialogPort("70000"))
        assertEquals(null, LanSharingLogics.validateDialogPort("abc"))
        assertEquals(1080, LanSharingLogics.validateDialogPort(" 1080 "))
    }

    @Test
    fun staleSnapshotRendersWithoutCrashForEveryStatus() {
        for (status in HotspotGatewayStatus.entries) {
            val snap = HotspotGatewaySnapshot(status = status, gateway = "", port = 0, updatedAt = 0L)
            val key = LanSharingLogics.statusSummaryKind(enabled = true, snapshot = snap)
            assertTrue(key.isNotEmpty())
        }
        val disabledKey = LanSharingLogics.statusSummaryKind(
            enabled = false,
            snapshot = HotspotGatewaySnapshot(HotspotGatewayStatus.PROBING, "", 0, 0L),
        )
        assertEquals("DISABLED", disabledKey)
    }

    @Test
    fun buildSocks5ShareLinkEncodesCredentialsUotAndParsesBack() {
        // Anonymous link without UoT or remark
        val plain = LanSharingLogics.buildSocks5ShareLink(
            host = "192.168.43.1",
            port = 2080,
            username = "",
            password = "",
            uot = false,
            remark = "",
        )
        assertEquals("socks5://192.168.43.1:2080", plain)

        // With credentials (special characters in password preserved verbatim, username trimmed) + UoT + remark
        val withAuth = LanSharingLogics.buildSocks5ShareLink(
            host = "192.168.43.1",
            port = 2080,
            username = " yusa ",
            password = " p@ss:1#2 ",
            uot = true,
            remark = "Hotspot Gateway",
        )
        val parsed = io.nekohasekai.sagernet.outbound.types.Socks()
        assertTrue(parsed.parseFromLink(withAuth))
        assertEquals("192.168.43.1", parsed.server)
        assertEquals(2080, parsed.serverPort)
        assertEquals("yusa", parsed.username)
        assertEquals(" p@ss:1#2 ", parsed.password)
        assertTrue(parsed.uot)
        assertEquals("Hotspot Gateway", parsed.name)

        // Blank host or invalid port returns empty string
        assertEquals("", LanSharingLogics.buildSocks5ShareLink("", 2080, "u", "p", false, ""))
        assertEquals("", LanSharingLogics.buildSocks5ShareLink("192.168.43.1", 0, "u", "p", false, ""))
    }

    @Test
    fun parseIpNeighExtractsIpsInterfacesAndReachability() {
        val dump = """
            192.168.43.52 dev wlan1 lladdr aa:bb:cc:dd:ee:01 REACHABLE
            192.168.1.99 dev wlan0 lladdr 11:22:33:44:55:66 STALE
            192.168.43.120 dev ap0  FAILED
            fe80::1 dev wlan0 lladdr aa:bb:cc:dd:ee:02 REACHABLE
        """.trimIndent()
        val parsed = LanSharingLogics.parseIpNeigh(dump)
        assertEquals(2, parsed.size)
        assertEquals("192.168.43.52", parsed[0].ip)
        assertEquals("wlan1", parsed[0].iface)
        assertEquals("REACHABLE", parsed[0].state)
        assertEquals("192.168.1.99", parsed[1].ip)
        assertEquals("wlan0", parsed[1].iface)
        assertEquals("STALE", parsed[1].state)
    }

    @Test
    fun parseProcNetTcpRemotesDecodesIpv4AndMappedIpv6SocketsOnListenPort() {
        // Port 2080 = 0x0820; 192.168.43.52 = 0x342BA8C0; 192.168.43.88 = 0x582BA8C0; 127.0.0.1 = 0x0100007F
        val procTcp = """
              sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
               0: 00000000:0820 00000000:0000 0A 00000000:00000000 00:00000000 00000000  10123        0 1001
               1: 012BA8C0:0820 342BA8C0:D431 01 00000000:00000000 00:00000000 00000000  10123        0 1002
               2: 0100007F:0820 0100007F:E001 01 00000000:00000000 00:00000000 00000000  10123        0 1003
               3: 0000000000000000FFFF0000012BA8C0:0820 0000000000000000FFFF0000582BA8C0:D500 01 00000000:00000000 00:00000000 00000000 10123 0 1004
        """.trimIndent()
        val remotes = LanSharingLogics.parseProcNetTcpRemotes(procTcp, listenPort = 2080)
        assertEquals(listOf("192.168.43.52", "192.168.43.88"), remotes)
    }

    @Test
    fun normalizeIpAndMergeDevicesPreservesHistoryAndMarksAllModes() {
        assertEquals("192.168.43.52", LanSharingLogics.normalizeIp("[::ffff:192.168.43.52]%wlan0"))
        assertTrue(LanSharingLogics.isLocalSource("192.0.2.2"))

        val history = mapOf(
            "192.168.43.52" to LanClientDevice(
                ip = "192.168.43.52",
                activeConnections = 2,
                uploadBytes = 1024L,
                downloadBytes = 4096L,
                lastHost = "example.com",
                lastSeenMs = 1_000L,
                isHotspot = true,
            ),
        )
        // Device became idle in Clash but remains REACHABLE in ip neigh & active TCP socket
        val merged = LanSharingLogics.mergeDevices(
            history = history,
            arpIps = listOf("192.168.43.52", "192.0.2.2", "192.168.43.99"),
            clash = emptyList(),
            hotspotIp = "192.168.43.1",
            hotspotPrefix = 24,
            localIps = setOf("192.168.43.1"),
            nowMs = 5_000L,
            activePeerIps = setOf("192.168.43.52"),
            hotspotHintIps = setOf("192.168.43.99"),
        )

        assertEquals(2, merged.size)
        val first = merged.first { it.ip == "192.168.43.52" }
        assertEquals(1, first.activeConnections)
        assertEquals(1024L, first.uploadBytes)
        assertEquals(4096L, first.downloadBytes)
        assertEquals("example.com", first.lastHost)
        assertTrue(first.isHotspot)

        val second = merged.first { it.ip == "192.168.43.99" }
        assertTrue(second.isHotspot)
        assertFalse(merged.any { it.ip == "192.0.2.2" })
    }

    @Test
    fun lanProxyStatusAndIndependenceFromShizukuAndRootShare() {
        assertEquals(
            LanSharingLogics.LanProxyStatus.OFF,
            LanSharingLogics.lanProxyStatus(false, ShareState.Active(ShareMethod.SHIZUKU)),
        )
        assertEquals(
            LanSharingLogics.LanProxyStatus.STANDALONE,
            LanSharingLogics.lanProxyStatus(true, ShareState.Off),
        )
        assertEquals(
            LanSharingLogics.LanProxyStatus.STANDALONE,
            LanSharingLogics.lanProxyStatus(true, ShareState.Stopped(ShareFailure.VPN_LOST)),
        )
        assertEquals(
            LanSharingLogics.LanProxyStatus.ALONGSIDE_HOTSPOT_SHARE,
            LanSharingLogics.lanProxyStatus(true, ShareState.Active(ShareMethod.SHIZUKU)),
        )
        assertEquals(
            LanSharingLogics.LanProxyStatus.ALONGSIDE_HOTSPOT_SHARE,
            LanSharingLogics.lanProxyStatus(true, ShareState.Active(ShareMethod.ROOT)),
        )
        assertEquals(
            LanSharingLogics.LanProxyStatus.VIA_SHARE_MODE,
            LanSharingLogics.lanProxyStatus(true, ShareState.Active(ShareMethod.LAN_PROXY)),
        )

        // Turning off LAN proxy must never claim dependency when Shizuku or Root share is active or selected.
        assertFalse(
            LanSharingLogics.hotspotShareDependsOnLanProxy(
                shareEnabled = true,
                preference = SharePreference.SHIZUKU,
                activeMethod = ShareMethod.SHIZUKU,
            ),
        )
        assertFalse(
            LanSharingLogics.hotspotShareDependsOnLanProxy(
                shareEnabled = true,
                preference = SharePreference.ROOT,
                activeMethod = ShareMethod.ROOT,
            ),
        )
        assertFalse(
            LanSharingLogics.hotspotShareDependsOnLanProxy(
                shareEnabled = true,
                preference = SharePreference.AUTO,
                activeMethod = ShareMethod.SHIZUKU,
            ),
        )
        assertFalse(
            LanSharingLogics.hotspotShareDependsOnLanProxy(
                shareEnabled = true,
                preference = SharePreference.SHIZUKU,
                activeMethod = null,
            ),
        )
        assertTrue(
            LanSharingLogics.hotspotShareDependsOnLanProxy(
                shareEnabled = true,
                preference = SharePreference.LAN_PROXY,
                activeMethod = ShareMethod.LAN_PROXY,
            ),
        )
        assertTrue(
            LanSharingLogics.hotspotShareDependsOnLanProxy(
                shareEnabled = true,
                preference = SharePreference.LAN_PROXY,
                activeMethod = null,
            ),
        )
    }

    @Test
    fun vpnLostNoticeDistinguishesBlockTrafficFromStopHotspotAndLanProxy() {
        val shizukuBlock = LanSharingLogics.vpnLostNotice(
            preference = SharePreference.SHIZUKU,
            lastActiveMethod = ShareMethod.SHIZUKU,
            failClosed = FailClosedAction.BLOCK_TRAFFIC,
        )
        assertEquals(LanSharingLogics.VpnLostNotice.BLOCK_TRAFFIC_PROTECTED, shizukuBlock)
        assertTrue(LanSharingLogics.vpnLostShowsWaitingBadge(shizukuBlock, pausedWaitingVpn = true))

        val shizukuStop = LanSharingLogics.vpnLostNotice(
            preference = SharePreference.SHIZUKU,
            lastActiveMethod = ShareMethod.SHIZUKU,
            failClosed = FailClosedAction.STOP_HOTSPOT,
        )
        assertEquals(LanSharingLogics.VpnLostNotice.HOTSPOT_STOPPED, shizukuStop)
        assertFalse(LanSharingLogics.vpnLostShowsWaitingBadge(shizukuStop, pausedWaitingVpn = true))

        val autoRootBlock = LanSharingLogics.vpnLostNotice(
            preference = SharePreference.AUTO,
            lastActiveMethod = ShareMethod.ROOT,
            failClosed = FailClosedAction.BLOCK_TRAFFIC,
        )
        assertEquals(LanSharingLogics.VpnLostNotice.BLOCK_TRAFFIC_PROTECTED, autoRootBlock)

        val lanDropped = LanSharingLogics.vpnLostNotice(
            preference = SharePreference.LAN_PROXY,
            lastActiveMethod = ShareMethod.LAN_PROXY,
            failClosed = FailClosedAction.BLOCK_TRAFFIC,
        )
        assertEquals(LanSharingLogics.VpnLostNotice.LAN_PROXY_DROPPED, lanDropped)
    }

    @Test
    fun shizukuHintReflectsWatchdogWhenReadyAndPermissionStates() {
        assertEquals(
            LanSharingLogics.ShizukuHint.WATCHDOG_ACTIVE,
            LanSharingLogics.shizukuHint(
                preference = SharePreference.SHIZUKU,
                shizukuRunning = true,
                shizukuGranted = true,
                rootAvailable = false,
            ),
        )
        assertEquals(
            LanSharingLogics.ShizukuHint.WATCHDOG_ACTIVE,
            LanSharingLogics.shizukuHint(
                preference = SharePreference.AUTO,
                shizukuRunning = true,
                shizukuGranted = true,
                rootAvailable = false,
            ),
        )
        assertEquals(
            LanSharingLogics.ShizukuHint.HIDDEN,
            LanSharingLogics.shizukuHint(
                preference = SharePreference.AUTO,
                shizukuRunning = true,
                shizukuGranted = true,
                rootAvailable = true,
            ),
        )
        assertEquals(
            LanSharingLogics.ShizukuHint.NEEDS_PERMISSION,
            LanSharingLogics.shizukuHint(
                preference = SharePreference.SHIZUKU,
                shizukuRunning = true,
                shizukuGranted = false,
                rootAvailable = false,
            ),
        )
        assertEquals(
            LanSharingLogics.ShizukuHint.NOT_RUNNING,
            LanSharingLogics.shizukuHint(
                preference = SharePreference.SHIZUKU,
                shizukuRunning = false,
                shizukuGranted = false,
                rootAvailable = false,
            ),
        )
        assertEquals(
            LanSharingLogics.ShizukuHint.HIDDEN,
            LanSharingLogics.shizukuHint(
                preference = SharePreference.ROOT,
                shizukuRunning = true,
                shizukuGranted = true,
                rootAvailable = true,
            ),
        )
    }

    @Test
    fun resolveHeroBannerModeCoversDualHotspotProxyAndInactiveStates() {
        assertEquals(
            LanSharingLogics.HeroBannerMode.BOTH_ACTIVE,
            LanSharingLogics.resolveHeroBannerMode(lanAccessOn = true, activeShareMethod = ShareMethod.SHIZUKU),
        )
        assertEquals(
            LanSharingLogics.HeroBannerMode.BOTH_ACTIVE,
            LanSharingLogics.resolveHeroBannerMode(lanAccessOn = true, activeShareMethod = ShareMethod.ROOT),
        )
        assertEquals(
            LanSharingLogics.HeroBannerMode.HOTSPOT_SHARE_ONLY,
            LanSharingLogics.resolveHeroBannerMode(lanAccessOn = false, activeShareMethod = ShareMethod.SHIZUKU),
        )
        assertEquals(
            LanSharingLogics.HeroBannerMode.HOTSPOT_SHARE_ONLY,
            LanSharingLogics.resolveHeroBannerMode(lanAccessOn = false, activeShareMethod = ShareMethod.ROOT),
        )
        assertEquals(
            LanSharingLogics.HeroBannerMode.LAN_PROXY_ONLY,
            LanSharingLogics.resolveHeroBannerMode(lanAccessOn = true, activeShareMethod = null),
        )
        assertEquals(
            LanSharingLogics.HeroBannerMode.LAN_PROXY_ONLY,
            LanSharingLogics.resolveHeroBannerMode(lanAccessOn = true, activeShareMethod = ShareMethod.LAN_PROXY),
        )
        assertEquals(
            LanSharingLogics.HeroBannerMode.INACTIVE,
            LanSharingLogics.resolveHeroBannerMode(lanAccessOn = false, activeShareMethod = null),
        )
    }

    @Test
    fun computeLanProxyTrafficComputesSpeedsMonotonicTotalsAndClientCount() {
        val localIps = setOf("192.168.43.1", "10.154.42.162")

        // Poll 1 (baseline): excludes local/loopback IPs and dedupes Clash + TCP remotes
        val firstDevices = listOf(
            LanClientDevice("192.168.43.1", 5, 9_000L, 12_000L, "local.example", 1_000L, true),
            LanClientDevice("127.0.0.1", 2, 500L, 500L, "loop.example", 1_000L, false),
            LanClientDevice("192.168.43.52", 2, 1_000L, 4_000L, "client1.example", 1_000L, false),
            LanClientDevice("192.168.43.60", 0, 200L, 600L, "client2.example", 1_000L, false),
        )
        val (sample1, display1) = LanSharingLogics.computeLanProxyTraffic(
            previous = null,
            clashDevices = firstDevices,
            activeTcpRemotes = listOf("192.168.43.52", "192.168.43.88", "127.0.0.1", "192.168.43.1"),
            localIps = localIps,
            nowMs = 10_000L,
        )
        assertEquals(0L, display1.rxSpeedBps)
        assertEquals(0L, display1.txSpeedBps)
        assertEquals(4_600L, display1.totalRxBytes)
        assertEquals(1_200L, display1.totalTxBytes)
        assertEquals(2, display1.activeClients) // .52 and .88 (.60 has 0 activeConnections and isn't in tcpRemotes)

        // Poll 2 (+2s): traffic grows -> computes B/s and adds positive deltas
        val secondDevices = listOf(
            LanClientDevice("192.168.43.52", 3, 2_000L, 10_600L, "client1.example", 12_000L, false),
            LanClientDevice("192.168.43.60", 1, 200L, 1_000L, "client2.example", 12_000L, false),
        )
        val (sample2, display2) = LanSharingLogics.computeLanProxyTraffic(
            previous = sample1,
            clashDevices = secondDevices,
            activeTcpRemotes = listOf("192.168.43.88"),
            localIps = localIps,
            nowMs = 12_000L,
        )
        // rawRx = 11_600 (delta = 7_000 / 2s = 3_500 B/s), rawTx = 2_200 (delta = 1_000 / 2s = 500 B/s)
        assertEquals(3_500L, display2.rxSpeedBps)
        assertEquals(500L, display2.txSpeedBps)
        assertEquals(11_600L, display2.totalRxBytes)
        assertEquals(2_200L, display2.totalTxBytes)
        assertEquals(3, display2.activeClients) // .52, .60, .88

        // Poll 3 (+2s): active flows closed so raw Clash totals shrink -> cumulative total remains monotonic
        val (sample3, display3) = LanSharingLogics.computeLanProxyTraffic(
            previous = sample2,
            clashDevices = emptyList(),
            activeTcpRemotes = emptyList(),
            localIps = localIps,
            nowMs = 14_000L,
        )
        assertEquals(0L, display3.rxSpeedBps)
        assertEquals(0L, display3.txSpeedBps)
        assertEquals(11_600L, display3.totalRxBytes)
        assertEquals(2_200L, display3.totalTxBytes)
        assertEquals(0, display3.activeClients)

        // Poll 4 (new traffic after reset, non-advancing timestamp guards against /0)
        val (_, display4) = LanSharingLogics.computeLanProxyTraffic(
            previous = sample3,
            clashDevices = listOf(LanClientDevice("192.168.43.52", 1, 300L, 400L, null, 14_000L, false)),
            activeTcpRemotes = emptyList(),
            localIps = localIps,
            nowMs = 14_000L,
        )
        assertEquals(0L, display4.rxSpeedBps)
        assertEquals(0L, display4.txSpeedBps)
        assertEquals(12_000L, display4.totalRxBytes)
        assertEquals(2_500L, display4.totalTxBytes)
        assertEquals(1, display4.activeClients)
    }

    @Test
    fun parseShizukuShareConnections_extractsShareSocksTrafficAndLastHost() {
        val json = """
            {
              "connections": [
                {
                  "metadata": {
                    "sourceIP": "127.0.0.1",
                    "type": "socks/share-socks-in",
                    "host": "youtube.com:443"
                  },
                  "upload": 1200,
                  "download": 8400
                },
                {
                  "metadata": {
                    "sourceIP": "127.0.0.1",
                    "inboundName": "share-socks-in",
                    "destinationIP": "1.1.1.1",
                    "destinationPort": "443"
                  },
                  "upload": 300,
                  "download": 600
                },
                {
                  "metadata": {
                    "sourceIP": "10.154.42.99",
                    "type": "mixed/mixed-in",
                    "host": "ignored-lan-proxy.org"
                  },
                  "upload": 50000,
                  "download": 50000
                }
              ]
            }
        """.trimIndent()

        val traffic = LanSharingLogics.parseShizukuShareConnections(json)
        assertEquals(2, traffic.activeConnections)
        assertEquals(1500L, traffic.uploadBytes)
        assertEquals(9000L, traffic.downloadBytes)
        assertEquals("1.1.1.1:443", traffic.lastHost)
    }

    @Test
    fun parseNeighborHostnames_extractsTetheredClientAndDnsmasqLeases() {
        val dump = """
            10.154.42.195 dev swlan0 lladdr aa:bb:cc:dd:ee:01 REACHABLE
            ---HOSTS---
            1794689000 aa:bb:cc:dd:ee:02 10.154.42.88 Pixel-9-Pro *
            1794689000 aa:bb:cc:dd:ee:03 10.154.42.99 * *
            TetheredClient { macAddress: aa:bb:cc:dd:ee:01, addresses: [AddressInfo {address 10.154.42.195/24, hostname TheYusaPC}] }
            10.154.42.77 ThinkPad-Work
        """.trimIndent()

        val hosts = LanSharingLogics.parseNeighborHostnames(dump)
        assertEquals("TheYusaPC", hosts["10.154.42.195"])
        assertEquals("Pixel-9-Pro", hosts["10.154.42.88"])
        assertEquals("ThinkPad-Work", hosts["10.154.42.77"])
        assertFalse(hosts.containsKey("10.154.42.99"))
        assertTrue(LanSharingLogics.parseIpNeigh(dump).any { it.ip == "10.154.42.195" })
    }

    @Test
    fun mergeDevices_filtersNonProxyWifiArpEntriesAndAttributesShizukuTrafficAndRoutes() {
        val now = 10_000_000L
        val merged = LanSharingLogics.mergeDevices(
            history = emptyMap(),
            arpIps = listOf("10.154.42.195", "192.168.1.1", "192.168.1.55"),
            clash = listOf(
                LanClientDevice("192.168.1.80", 2, 500L, 1500L, "lan.example.org", now, false),
            ),
            hotspotIp = "10.154.42.162",
            hotspotPrefix = 24,
            localIps = setOf("10.154.42.162", "192.168.1.10"),
            nowMs = now,
            shizukuTraffic = io.nekohasekai.sagernet.utils.ShizukuTunnelTraffic(
                activeConnections = 4,
                uploadBytes = 10_240L,
                downloadBytes = 384_000L,
                lastHost = "github.com:443",
            ),
            hostnames = mapOf("10.154.42.195" to "TheYusaPC"),
            activeHotspotMethod = ShareMethod.SHIZUKU,
            lanProxyEnabled = true,
        )

        val ips = merged.map { it.ip }
        assertTrue(ips.contains("10.154.42.195"))
        assertTrue(ips.contains("192.168.1.80"))
        // Router/neighbor Wi-Fi ARP entries that are not using the proxy are excluded.
        assertFalse(ips.contains("192.168.1.1"))
        assertFalse(ips.contains("192.168.1.55"))

        val hotspotClient = merged.first { it.ip == "10.154.42.195" }
        assertEquals("TheYusaPC", hotspotClient.hostname)
        assertEquals(io.nekohasekai.sagernet.utils.ClientShareRoute.HOTSPOT_SHIZUKU, hotspotClient.route)
        assertEquals(4, hotspotClient.activeConnections)
        assertEquals(10_240L, hotspotClient.uploadBytes)
        assertEquals(384_000L, hotspotClient.downloadBytes)
        assertEquals("github.com:443", hotspotClient.lastHost)

        val lanClient = merged.first { it.ip == "192.168.1.80" }
        assertEquals(io.nekohasekai.sagernet.utils.ClientShareRoute.LAN_PROXY, lanClient.route)
    }
}
