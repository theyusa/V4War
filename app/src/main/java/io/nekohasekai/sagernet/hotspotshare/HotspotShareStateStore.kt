package io.nekohasekai.sagernet.hotspotshare

import io.nekohasekai.sagernet.database.DataStore

/** The persisted [ShareState] record: written by the :bg process, read by the UI process. */
object HotspotShareStateStore {

    fun publish(state: ShareState) {
        val encoded = ShareStateCodec.encode(state)
        DataStore.hotspotShareStatus = encoded.code
        DataStore.hotspotShareStatusMethod = encoded.method
        DataStore.hotspotShareStatusReason = encoded.reason
        DataStore.hotspotShareStatusUpdatedAt = System.currentTimeMillis()
    }

    fun snapshot(): ShareState = ShareStateCodec.decode(
        EncodedShareState(
            code = DataStore.hotspotShareStatus,
            method = DataStore.hotspotShareStatusMethod,
            reason = DataStore.hotspotShareStatusReason,
        ),
    )
}
