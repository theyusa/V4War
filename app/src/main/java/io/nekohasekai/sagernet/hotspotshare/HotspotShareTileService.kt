package io.nekohasekai.sagernet.hotspotshare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService as BaseTileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.LanSharingNetwork
import io.nekohasekai.sagernet.utils.PlatformNotifications
import io.nekohasekai.sagernet.utils.TetherKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.N)
class HotspotShareTileService : BaseTileService() {

    private val tileIcon by lazy { Icon.createWithResource(this, R.drawable.ic_hardware_router) }
    private var stateReceiver: BroadcastReceiver? = null
    private var systemNetReceiver: BroadcastReceiver? = null
    private var subtitleRefreshJob: Job? = null

    @Volatile
    private var isListening: Boolean = false

    @Volatile
    private var cachedActiveChannels: List<String> = emptyList()

    override fun onStartListening() {
        super.onStartListening()
        isListening = true
        registerReceivers()
        HotspotShareStateStore.reconcileIfServiceDead(TetherWatchService.isRunningOrStarting())
        updateTile(HotspotShareStateStore.snapshot())
    }

    override fun onStopListening() {
        isListening = false
        subtitleRefreshJob?.cancel()
        subtitleRefreshJob = null
        unregisterReceivers()
        super.onStopListening()
    }

    override fun onDestroy() {
        isListening = false
        subtitleRefreshJob?.cancel()
        subtitleRefreshJob = null
        unregisterReceivers()
        super.onDestroy()
    }

    override fun onClick() {
        if (isLocked) {
            unlockAndRun(this::toggleHotspotShare)
        } else {
            toggleHotspotShare()
        }
    }

    private fun toggleHotspotShare() {
        val currentState = HotspotShareStateStore.snapshot()
        val isWaitingForVpn = currentState is ShareState.Stopped &&
            currentState.cause == ShareFailure.VPN_LOST &&
            DataStore.hotspotShareEnabled
        val isCurrentlyOn = when (currentState) {
            is ShareState.Active,
            is ShareState.Starting -> true
            is ShareState.Stopped -> isWaitingForVpn
            is ShareState.Error -> false
            ShareState.Off -> DataStore.hotspotShareEnabled && TetherWatchService.isRunning
        }

        if (isCurrentlyOn) {
            PlatformNotifications.cancelHotspotShareDropped(this)
            DataStore.hotspotShareEnabled = false
            HotspotShareLogger.info(TAG, "Quick Settings tile stop requested")
            if (TetherWatchService.isRunning ||
                currentState is ShareState.Active ||
                currentState is ShareState.Starting ||
                isWaitingForVpn
            ) {
                TetherWatchService.stop(this)
            } else {
                HotspotShareStateStore.publish(ShareState.Off)
            }
            updateTile(ShareState.Off)
        } else {
            PlatformNotifications.cancelHotspotShareDropped(this)
            HotspotShareStateStore.publish(ShareState.Starting)
            DataStore.hotspotShareEnabled = true
            HotspotShareLogger.info(TAG, "Quick Settings tile start requested")
            TetherWatchService.start(this)
            updateTile(ShareState.Starting)
        }
    }

    private fun updateTile(state: ShareState) {
        applyTileVisuals(state, if (state is ShareState.Active) cachedActiveChannels else emptyList())

        subtitleRefreshJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && state is ShareState.Active) {
            subtitleRefreshJob = runOnDefaultDispatcher {
                val channels = withContext(Dispatchers.IO) {
                    queryActiveChannelLabels(state)
                }
                cachedActiveChannels = channels
                onMainDispatcher {
                    if (!isListening) return@onMainDispatcher
                    val latest = HotspotShareStateStore.snapshot()
                    applyTileVisuals(latest, if (latest is ShareState.Active) channels else emptyList())
                }
            }
        } else {
            subtitleRefreshJob = null
        }
    }

    private fun applyTileVisuals(state: ShareState, activeChannels: List<String>) {
        val tile = qsTile ?: return
        val isWaitingForVpn = state is ShareState.Stopped &&
            state.cause == ShareFailure.VPN_LOST &&
            DataStore.hotspotShareEnabled

        tile.icon = tileIcon
        tile.label = getString(R.string.hotspot_share_tile_title)
        tile.state = when (state) {
            is ShareState.Active,
            is ShareState.Starting -> Tile.STATE_ACTIVE
            is ShareState.Stopped -> if (isWaitingForVpn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            is ShareState.Error,
            ShareState.Off -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = buildSubtitle(state, isWaitingForVpn, activeChannels)
        }
        tile.updateTile()
    }

    private fun buildSubtitle(
        state: ShareState,
        isWaitingForVpn: Boolean,
        activeChannels: List<String>,
    ): CharSequence = when (state) {
        is ShareState.Active -> {
            val methodLabel = when (state.method) {
                ShareMethod.SHIZUKU -> getString(R.string.hotspot_share_tile_method_shizuku)
                ShareMethod.ROOT -> getString(R.string.hotspot_share_tile_method_root)
                ShareMethod.LAN_PROXY -> getString(R.string.hotspot_share_tile_method_proxy)
            }
            if (activeChannels.isNotEmpty()) {
                getString(
                    R.string.hotspot_share_tile_active_format,
                    methodLabel,
                    activeChannels.joinToString(", "),
                )
            } else {
                getString(R.string.hotspot_share_tile_active_no_channel, methodLabel)
            }
        }
        is ShareState.Starting -> getString(R.string.hotspot_share_tile_starting)
        is ShareState.Stopped -> if (isWaitingForVpn) {
            getString(R.string.hotspot_share_tile_waiting_vpn)
        } else {
            getString(R.string.hotspot_share_tile_off)
        }
        is ShareState.Error -> getString(R.string.hotspot_share_tile_error)
        ShareState.Off -> getString(R.string.hotspot_share_tile_off)
    }

    private fun queryActiveChannelLabels(state: ShareState): List<String> {
        if (state !is ShareState.Active) return emptyList()
        val kinds = runCatching {
            LanSharingNetwork.getActiveTetherKinds(this)
        }.getOrDefault(emptySet())
        val mask = runCatching {
            if (ShizukuBackend.isShizukuRunning() && ShizukuBackend.isShizukuGranted(this)) {
                ShizukuBackend.getActiveTetherMask(this)
            } else {
                0
            }
        }.getOrDefault(0)

        val hasWifi = TetherKind.WIFI in kinds ||
            (mask and ShizukuBackend.TETHER_MASK_WIFI) != 0
        val hasUsb = TetherKind.USB in kinds ||
            (mask and ShizukuBackend.TETHER_MASK_USB) != 0
        val hasBt = TetherKind.BLUETOOTH in kinds ||
            (mask and ShizukuBackend.TETHER_MASK_BLUETOOTH) != 0

        val labels = ArrayList<String>(3)
        if (hasWifi) labels += getString(R.string.hotspot_share_tile_channel_wifi)
        if (hasUsb) labels += getString(R.string.hotspot_share_tile_channel_usb)
        if (hasBt) labels += getString(R.string.hotspot_share_tile_channel_bt)
        return labels
    }

    private fun registerReceivers() {
        if (stateReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    updateTile(HotspotShareStateStore.snapshot())
                }
            }
            runCatching {
                ContextCompat.registerReceiver(
                    this,
                    receiver,
                    IntentFilter(Action.HOTSPOT_SHARE_STATE),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }.onSuccess { stateReceiver = receiver }
        }

        if (systemNetReceiver == null) {
            val netReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    updateTile(HotspotShareStateStore.snapshot())
                }
            }
            val filter = IntentFilter().apply {
                addAction(WIFI_AP_STATE_CHANGED)
                addAction(TETHER_STATE_CHANGED)
            }
            runCatching {
                ContextCompat.registerReceiver(
                    this,
                    netReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED,
                )
            }.onSuccess { systemNetReceiver = netReceiver }
        }
    }

    private fun unregisterReceivers() {
        stateReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
            stateReceiver = null
        }
        systemNetReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
            systemNetReceiver = null
        }
    }

    private companion object {
        const val TAG = "HotspotShare"
        const val WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
    }
}
