package io.nekohasekai.sagernet.hotspotshare

import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SettingsRegistry
import io.nekohasekai.sagernet.utils.LanSharingLogics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The two operations of the existing LAN proxy switch, injectable so the backend is testable. */
interface LanProxyControl {
    fun isLanAccess(): Boolean
    fun setLanAccess(enabled: Boolean)
}

/**
 * LAN_PROXY: the existing simple mode. It only flips the mixed inbound's listen address between loopback and LAN, the
 * same switch as LanSharingActivity; clients still enter the proxy by hand (or use auto connect). It never touches the
 * hotspot or the routing tables.
 */
class LanProxyBackend(private val control: LanProxyControl = DataStoreLanProxyControl) : PausableHotspotShare {

    override val method = ShareMethod.LAN_PROXY

    private val _state = MutableStateFlow<ShareState>(ShareState.Off)
    override val state: StateFlow<ShareState> = _state.asStateFlow()

    override suspend fun start() {
        _state.value = ShareState.Starting
        try {
            control.setLanAccess(true)
            _state.value = ShareState.Active(ShareMethod.LAN_PROXY)
        } catch (e: Exception) {
            _state.value = ShareState.Error(ShareFailure.BACKEND_FAILED, e.message)
        }
    }

    override suspend fun pauseForVpnDrop() {
        _state.value = ShareState.Stopped(ShareFailure.VPN_LOST)
    }

    override suspend fun resumeAfterVpnDrop(): Boolean {
        return try {
            control.setLanAccess(true)
            _state.value = ShareState.Active(ShareMethod.LAN_PROXY)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun stop() {
        if (control.isLanAccess()) control.setLanAccess(false)
        _state.value = ShareState.Off
    }
}

/**
 * The LAN switch of LanSharingActivity without its UI: the inbound address, the Clash API the client list needs, and a
 * reload of a running service so the core listens on the new address.
 */
object DataStoreLanProxyControl : LanProxyControl {

    override fun isLanAccess(): Boolean = DataStore.allowLanAccess

    override fun setLanAccess(enabled: Boolean) {
        val targetAddr = if (enabled) SettingsRegistry.LAN_ADDRESS else SettingsRegistry.LOOPBACK_ADDRESS
        var changed = DataStore.inboundAddress != targetAddr
        if (changed) {
            DataStore.inboundAddress = targetAddr
        }
        if (enabled) {
            if (LanSharingLogics.clashApiSecretShouldBeGenerated(DataStore.coreBoxClashApiSecret)) {
                DataStore.coreBoxClashApiSecret = LanSharingLogics.generateClashApiSecret()
                changed = true
            }
            val clash = LanSharingLogics.ensureClashApiEnabledPort(DataStore.coreBoxClashApi)
            if (clash.changed) {
                DataStore.coreBoxClashApi = clash.newRawPort
                changed = true
            }
        }
        if (changed && DataStore.serviceState.started) SagerNet.reloadService()
    }
}
