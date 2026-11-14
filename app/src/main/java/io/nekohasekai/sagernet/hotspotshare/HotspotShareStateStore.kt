package io.nekohasekai.sagernet.hotspotshare

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SettingsRegistry

/** The persisted [ShareState] record: written by the :bg process, read by the UI process. */
object HotspotShareStateStore {

    fun publish(state: ShareState) {
        val encoded = ShareStateCodec.encode(state)
        val current = EncodedShareState(
            code = DataStore.hotspotShareStatus,
            method = DataStore.hotspotShareStatusMethod,
            reason = DataStore.hotspotShareStatusReason,
        )
        if (current == encoded) return
        DataStore.configurationStore.putAll(
            mapOf(
                SettingsRegistry.HOTSPOT_SHARE_STATUS.key to SettingsRegistry.HOTSPOT_SHARE_STATUS.encode(encoded.code),
                SettingsRegistry.HOTSPOT_SHARE_STATUS_METHOD.key to SettingsRegistry.HOTSPOT_SHARE_STATUS_METHOD.encode(encoded.method),
                SettingsRegistry.HOTSPOT_SHARE_STATUS_REASON.key to SettingsRegistry.HOTSPOT_SHARE_STATUS_REASON.encode(encoded.reason),
                SettingsRegistry.HOTSPOT_SHARE_STATUS_UPDATED_AT.key to SettingsRegistry.HOTSPOT_SHARE_STATUS_UPDATED_AT.encode(System.currentTimeMillis()),
            ),
        )
    }

    fun snapshot(): ShareState = ShareStateCodec.decode(
        EncodedShareState(
            code = DataStore.hotspotShareStatus,
            method = DataStore.hotspotShareStatusMethod,
            reason = DataStore.hotspotShareStatusReason,
        ),
    )

    internal fun isStalePersistedState(
        snapshot: ShareState,
        persistedEnabled: Boolean,
        serviceAlive: Boolean,
    ): Boolean {
        if (serviceAlive) return false
        if (persistedEnabled) return true
        return snapshot is ShareState.Active ||
            snapshot is ShareState.Starting ||
            (snapshot as? ShareState.Stopped)?.cause == ShareFailure.VPN_LOST
    }

    /**
     * Clears persisted Active / Starting / paused-VPN state when [TetherWatchService]
     * is no longer alive (for example after an APK upgrade or OS process kill), so
     * the UI and Quick Settings tile never display a false Active state with 0 B.
     */
    fun reconcileIfServiceDead(serviceAlive: Boolean): Boolean {
        val snapshot = snapshot()
        val enabled = DataStore.hotspotShareEnabled
        if (!isStalePersistedState(snapshot, enabled, serviceAlive)) {
            return false
        }
        if (enabled) {
            DataStore.hotspotShareEnabled = false
        }
        publish(ShareState.Off)
        return true
    }
}
