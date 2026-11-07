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
