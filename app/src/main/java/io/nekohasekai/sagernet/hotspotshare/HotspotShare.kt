package io.nekohasekai.sagernet.hotspotshare

import kotlinx.coroutines.flow.StateFlow

/** One way of sharing the running VPN over the hotspot. */
interface HotspotShare {
    val method: ShareMethod

    /** The backend's own state; [ShareState.Off] until [start] is called. */
    val state: StateFlow<ShareState>

    /** Drives [state] through Starting to Active or Error; returns once that is decided. */
    suspend fun start()

    /** Tears everything down (idempotent) and ends in [ShareState.Off]. */
    suspend fun stop()
}

/**
 * Backend capable of pausing fail-closed on VPN drop without tearing down the hotspot
 * and resuming cleanly once VPN reconnects.
 */
interface PausableHotspotShare : HotspotShare {
    /** Enters zero-leak paused state without turning off the hotspot. */
    suspend fun pauseForVpnDrop()

    /** Resumes active sharing from the paused state once VPN is connected again. */
    suspend fun resumeAfterVpnDrop(): Boolean
}

/**
 * Backend capable of stopping the physical Wi-Fi hotspot when explicitly turned off by the user.
 * Internal errors, crashes, VPN drops, and restarts must call [HotspotShare.stop] without closing hotspot.
 */
interface HotspotControllingBackend : HotspotShare {
    suspend fun stopHotspotByUser()
}
