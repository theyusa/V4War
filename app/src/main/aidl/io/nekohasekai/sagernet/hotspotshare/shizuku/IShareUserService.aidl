package io.nekohasekai.sagernet.hotspotshare.shizuku;

import android.os.ParcelFileDescriptor;
import java.util.List;

interface IShareUserService {
    /**
     * Returns contract version to verify client/service compatibility.
     */
    int getContractVersion();

    /**
     * Creates an IPv4-only TUN interface (192.0.2.2/24), associates it
     * with a test network (isUp=true), awaits Network availability, registers a
     * TRANSPORT_TEST keep-alive request so ConnectivityService does not linger it away,
     * and sets IPv4 routes + DNS forwarders.
     * Returns the TUN ParcelFileDescriptor to be fed to hev-socks5-tunnel.
     */
    ParcelFileDescriptor createTestNetwork(in List<String> dnsServers);

    /**
     * Creates a dual-stack test network TUN interface backed by a caller-owned
     * life binder so transient shell daemon restarts do not immediately destroy testtunN.
     */
    ParcelFileDescriptor createTestNetworkWithBinder(in List<String> dnsServers, IBinder lifeBinder);

    /**
     * Adopts an already-live testtunN interface in ConnectivityManager after a daemon restart,
     * re-arming TRANSPORT_TEST keep-alive and preferTestNetworks without recreating the TUN.
     */
    boolean adoptExistingTestNetwork(String expectedIface);

    /**
     * Ensures all supported channels present in targetMask (1=WIFI, 2=USB when enabled) are started,
     * used to finish a downstream restart after a binder reset.
     */
    boolean ensureTetherMaskStarted(int targetMask, boolean preferNcm);

    /**
     * Returns the active testtunN interface name (or null if none).
     */
    String getInterfaceName();

    /**
     * Directs TetheringManager to prefer test networks as upstream.
     */
    void setPreferTestNetworks(boolean prefer);

    /**
     * Ensures Wi-Fi tethering (hotspot) is active without stopping an already-open SoftAp.
     * If closed, starts it with multi-fallback (exempt=true -> exempt=false -> WifiManager).
     */
    boolean startTethering();

    /**
     * Restarts Wi-Fi tethering downstream (TetheringManager stopTethering -> startTethering with fallback)
     * so an already-open SoftAp re-runs upstream selection onto testtunN.
     */
    boolean restartWifiTethering();

    /**
     * Returns true if Wi-Fi SoftAp is currently enabled/starting according to WifiManager
     * or an active downstream interface in dumpsys tethering.
     */
    boolean isWifiHotspotActive();

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
     * Returns connected hotspot client count (from SoftAp/Tethering callbacks, or cached fallback).
     */
    int getConnectedClientCount();

    /**
     * Returns raw neighbor table output (`ip -4 neigh show` + `/proc/net/arp` + cached client hosts)
     * executed as shell UID 2000 for accurate client IP discovery without dumpsys tethering overhead.
     */
    String getNeighborDump();

    /**
     * Probes device compatibility for TestNetworkManager and TetheringManager.setPreferTestNetworks.
     */
    String checkCompatibility();

    /**
     * Returns in-memory and shell file diagnostic logs collected in the UID 2000 process.
     */
    List<String> getDiagnosticLogs();

    /**
     * Clears diagnostic logs in the UID 2000 process.
     */
    void clearDiagnosticLogs();

    /**
     * Enables or disables diagnostic log recording in the UID 2000 process.
     */
    void setDiagnosticLoggingEnabled(boolean enabled);

    /**
     * Dirty state recovery: restores normal upstream routing,
     * unregisters keep-alive callback, and tears down the test network (preserves hotspot).
     */
    void cleanupDirtyState();

    /**
     * Destroys all test networks and stops the service.
     */
    void destroy();

    /**
     * Starts a specific tethering channel type (0=WIFI; 1=USB, 4=NCM when enabled).
     */
    boolean startTetheringType(int type);

    /**
     * Stops a specific tethering channel type (0=WIFI; 1=USB, 4=NCM when enabled).
     */
    void stopTetheringType(int type);

    /**
     * Returns bitmask of currently active supported tethering channels
     * (1=WIFI, 2=USB when enabled).
     */
    int getActiveTetherMask();

    /**
     * Returns active tethered downstream interface names observed via TetheringEventCallback
     * (or platform fallback if callback is unavailable).
     */
    List<String> getTetheredIfaces();
}
