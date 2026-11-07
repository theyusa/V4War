package io.nekohasekai.sagernet.hotspotshare.shizuku;

import android.os.ParcelFileDescriptor;
import java.util.List;

interface IShareUserService {
    /**
     * Creates a TUN interface with 198.18.0.1/15, associates it with a test network
     * and sets DNS forwarders. Returns the TUN ParcelFileDescriptor to be fed to hev-socks5-tunnel.
     */
    ParcelFileDescriptor createTestNetwork(in List<String> dnsServers);

    /**
     * Directs TetheringManager to prefer test networks as upstream.
     */
    void setPreferTestNetworks(boolean prefer);

    /**
     * Starts Wi-Fi tethering (hotspot).
     */
    boolean startTethering();

    /**
     * Stops Wi-Fi tethering (hotspot).
     */
    void stopTethering();

    /**
     * Dirty state recovery: restores normal upstream routing and stops tethering.
     */
    void cleanupDirtyState();

    /**
     * Destroys all test networks and stops the service.
     */
    void destroy();
}
