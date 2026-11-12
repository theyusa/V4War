package io.nekohasekai.sagernet.hotspotshare.shizuku;

import android.os.ParcelFileDescriptor;
import java.util.List;

interface IShareUserService {
    /**
     * Returns contract version to verify client/service compatibility.
     */
    int getContractVersion();

    /**
     * Creates a dual-stack TUN interface (192.0.2.2/24, 2001:db8::2/64), associates it
     * with a test network (isUp=true), awaits Network availability, registers a
     * TRANSPORT_TEST keep-alive request so ConnectivityService does not linger it away,
     * and sets IPv4 + IPv6 DNS forwarders.
     * Returns the TUN ParcelFileDescriptor to be fed to hev-socks5-tunnel.
     */
    ParcelFileDescriptor createTestNetwork(in List<String> dnsServers);

    /**
     * Returns the active testtunN interface name (or null if none).
     */
    String getInterfaceName();

    /**
     * Directs TetheringManager to prefer test networks as upstream.
     */
    void setPreferTestNetworks(boolean prefer);

    /**
     * Restarts and starts Wi-Fi tethering (hotspot) with multi-fallback
     * (exempt=true -> exempt=false -> WifiManager.startTetheredHotspot).
     */
    boolean startTethering();

    /**
     * Stops Wi-Fi tethering (hotspot) with downstream verification retries.
     */
    void stopTethering();

    /**
     * Verifies via `dumpsys tethering` that the created testtunN interface is
     * selected as the active upstream within timeoutMs.
     */
    boolean verifyUpstream(long timeoutMs);

    /**
     * Checks current upstream health ("HEALTHY", "ABSENT", "TIMED_OUT", or "DRIFTED:<ifaces>").
     */
    String checkUpstreamHealth();

    /**
     * Returns connected hotspot client count from `dumpsys wifi`.
     */
    int getConnectedClientCount();

    /**
     * Returns raw neighbor table output (`ip -4 neigh show` + `/proc/net/arp`)
     * executed as shell UID 2000 for accurate client IP discovery.
     */
    String getNeighborDump();

    /**
     * Probes device compatibility for TestNetworkManager and TetheringManager.setPreferTestNetworks.
     */
    String checkCompatibility();

    /**
     * Dirty state recovery: restores normal upstream routing, stops tethering,
     * unregisters keep-alive callback, and tears down the test network.
     */
    void cleanupDirtyState();

    /**
     * Destroys all test networks and stops the service.
     */
    void destroy();
}
