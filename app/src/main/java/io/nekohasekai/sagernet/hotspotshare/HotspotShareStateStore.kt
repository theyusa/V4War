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
}
