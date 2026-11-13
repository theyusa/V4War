package io.nekohasekai.sagernet.hotspotshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.LanSharingActivity
import io.nekohasekai.sagernet.utils.PlatformNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TetherWatchService : Service(), SagerConnection.Callback {

    companion object {
        const val ACTION_START = "io.nekohasekai.sagernet.hotspotshare.action.START"
        const val ACTION_RESTART = "io.nekohasekai.sagernet.hotspotshare.action.RESTART"
        const val ACTION_STOP = "io.nekohasekai.sagernet.hotspotshare.action.STOP"

        const val CHANNEL_ID = "hotspot_share_service"
        const val NOTIFICATION_ID = 5050

        const val WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"

        private const val RELOAD_GRACE_MS = 4_000L
        private const val DISCONNECT_GRACE_MS = 1_000L

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, TetherWatchService::class.java).apply {
                action = ACTION_START
            }
            launchService(context, intent)
        }

        fun restart(context: Context) {
            val intent = Intent(context, TetherWatchService::class.java).apply {
                action = ACTION_RESTART
            }
            launchService(context, intent)
        }

        private fun launchService(context: Context, intent: Intent) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // If background restriction prevents startForegroundService
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TetherWatchService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)

    private var controller: HotspotShareController? = null
    private var activeBackend: HotspotShare? = null
    private var systemNetReceiver: BroadcastReceiver? = null
    private var statsJob: Job? = null
    private var vpnDropGraceJob: Job? = null
    private var lastState: ShareState = ShareState.Off
    private var lastActiveMethod: ShareMethod? = null
    private var sessionGeneration: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        registerSystemNetReceiver()
        connection.connect(this, this)
        initController()
    }

    private fun initController() {
        controller = HotspotShareController(
            scope = serviceScope,
            environment = {
                ShareEnvironment(
                    sdkInt = Build.VERSION.SDK_INT,
                    shizukuRunning = ShizukuBackend.isShizukuRunning(),
                    shizukuGranted = ShizukuBackend.isShizukuGranted(this),
                    rootAvailable = RootBackend.isRootAvailable(),
                )
            },
            preference = { SharePreference.fromCode(DataStore.hotspotShareMethod) },
            backendFactory = { method ->
                val backend: HotspotShare = when (method) {
                    ShareMethod.SHIZUKU -> ShizukuBackend(this)
                    ShareMethod.ROOT -> RootBackend(
                        context = this,
                        failClosedAction = { FailClosedAction.fromCode(DataStore.hotspotShareFailClosed) },
                    )
                    ShareMethod.LAN_PROXY -> LanProxyBackend()
                }
                activeBackend = backend
                backend
            },
            publisher = { state ->
                lastState = state
                HotspotShareStateStore.publish(state)
                handleStateChange(state)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSharingSession()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                val fgState = if (lastState is ShareState.Active) lastState else ShareState.Starting
                startForeground(NOTIFICATION_ID, buildNotification(fgState, HotspotTrafficMonitor.stats.value))
                startSharingSession(restart = true)
                return START_STICKY
            }
            else -> {
                val fgState = if (lastState is ShareState.Active) lastState else ShareState.Starting
                startForeground(NOTIFICATION_ID, buildNotification(fgState, HotspotTrafficMonitor.stats.value))
                startSharingSession(restart = false)
                return START_STICKY
            }
        }
    }

    private fun startSharingSession(restart: Boolean) {
        sessionGeneration++
        vpnDropGraceJob?.cancel()
        vpnDropGraceJob = null
        lastActiveMethod = null
        PlatformNotifications.cancelHotspotShareDropped(this)
        DataStore.hotspotShareEnabled = true
        serviceScope.launch {
            if (restart) {
                controller?.restart()
            } else {
                controller?.start()
            }
        }
    }

    private fun stopSharingSession() {
        sessionGeneration++
        vpnDropGraceJob?.cancel()
        vpnDropGraceJob = null
        lastActiveMethod = null
        PlatformNotifications.cancelHotspotShareDropped(this)
        DataStore.hotspotShareEnabled = false
        serviceScope.launch {
            try {
                controller?.stop()
            } finally {
                activeBackend = null
                stopTrafficMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun handleStateChange(state: ShareState) {
        sendBroadcast(Intent(Action.HOTSPOT_SHARE_STATE).setPackage(packageName))
        when (state) {
            is ShareState.Active -> {
                lastActiveMethod = state.method
                vpnDropGraceJob?.cancel()
                vpnDropGraceJob = null
                PlatformNotifications.cancelHotspotShareDropped(this)
                if (state.method != ShareMethod.LAN_PROXY) {
                    startTrafficMonitoring(state.method)
                } else {
                    stopTrafficMonitoring()
                }
                updateNotification(state, HotspotTrafficMonitor.stats.value)
            }
            is ShareState.Stopped -> {
                DataStore.hotspotShareEnabled = false
                vpnDropGraceJob?.cancel()
                vpnDropGraceJob = null
                stopTrafficMonitoring()
                if (lastActiveMethod == ShareMethod.SHIZUKU || lastActiveMethod == ShareMethod.ROOT) {
                    PlatformNotifications.hotspotShareDropped(this)
                }
                lastActiveMethod = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            is ShareState.Error -> {
                DataStore.hotspotShareEnabled = false
                vpnDropGraceJob?.cancel()
                vpnDropGraceJob = null
                lastActiveMethod = null
                stopTrafficMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            is ShareState.Off -> {
                DataStore.hotspotShareEnabled = false
                vpnDropGraceJob?.cancel()
                vpnDropGraceJob = null
                lastActiveMethod = null
                PlatformNotifications.cancelHotspotShareDropped(this)
                stopTrafficMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ShareState.Starting -> {
                updateNotification(state, HotspotTrafficStats())
            }
        }
    }

    private fun startTrafficMonitoring(method: ShareMethod) {
        statsJob?.cancel()
        val provider: () -> Pair<Long, Long>? = {
            when (method) {
                ShareMethod.SHIZUKU -> {
                    val shizuku = activeBackend as? ShizukuBackend
                    val iface = shizuku?.activeInterfaceName ?: "testtun0"
                    shizuku?.getTrafficStats() ?: ProcNetDevParser.readCurrent(setOf(iface))
                }
                ShareMethod.ROOT -> (activeBackend as? RootBackend)?.let { root ->
                    ProcNetDevParser.readCurrent(root.activeInterfaces.ifEmpty { emptySet() })
                } ?: ProcNetDevParser.readCurrent()
                ShareMethod.LAN_PROXY -> ProcNetDevParser.readCurrent()
            }
        }

        HotspotTrafficMonitor.start(serviceScope, provider)

        statsJob = serviceScope.launch {
            HotspotTrafficMonitor.stats.collectLatest { stats ->
                if (lastState is ShareState.Active) {
                    updateNotification(lastState, stats)
                }
            }
        }
    }

    private fun stopTrafficMonitoring() {
        statsJob?.cancel()
        statsJob = null
        HotspotTrafficMonitor.stop()
    }

    // VPN state listener from SagerConnection
    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        if (state.connected) {
            vpnDropGraceJob?.cancel()
            vpnDropGraceJob = null
            return
        }
        val activeState = lastState as? ShareState.Active ?: return
        val currentGen = sessionGeneration
        val graceMs = when (state) {
            BaseService.State.Stopping, BaseService.State.Connecting -> RELOAD_GRACE_MS
            else -> DISCONNECT_GRACE_MS
        }
        vpnDropGraceJob?.cancel()
        vpnDropGraceJob = serviceScope.launch {
            delay(graceMs)
            if (currentGen != sessionGeneration) return@launch
            val liveState = runCatching {
                connection.service?.state?.let { BaseService.State.entries.getOrNull(it) }
            }.getOrNull() ?: DataStore.serviceState
            if (liveState.connected) return@launch
            if (lastState !is ShareState.Active) return@launch

            val backend = activeBackend
            if (activeState.method == ShareMethod.LAN_PROXY) {
                // LAN Proxy has no hotspot leak risk on reload or brief reconnects; only turn off if VPN fully stopped.
                if (!liveState.started) {
                    controller?.stop()
                }
                return@launch
            }

            val failClosedAction = FailClosedAction.fromCode(DataStore.hotspotShareFailClosed)
            when (backend) {
                is RootBackend -> backend.handleVpnDropped(failClosedAction)
                else -> controller?.stopDueToFailure(ShareFailure.VPN_LOST)
            }
        }
    }

    override fun onServiceConnected(service: ISagerNetService) {}

    private fun registerSystemNetReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != TETHER_STATE_CHANGED) return
                val activeMethod = (lastState as? ShareState.Active)?.method ?: return
                if (activeMethod != ShareMethod.ROOT && activeMethod != ShareMethod.SHIZUKU) return

                val active = intent.getStringArrayListExtra("activeArray")
                    ?: intent.getStringArrayListExtra("tetherArray")
                val tetherActive = active?.isNotEmpty() == true
                if (!tetherActive) {
                    serviceScope.launch {
                        controller?.stopDueToFailure(ShareFailure.TETHER_LOST)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WIFI_AP_STATE_CHANGED)
            addAction(TETHER_STATE_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }.onSuccess { systemNetReceiver = receiver }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.hotspot_share_service_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.hotspot_share_service_active)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: ShareState, stats: HotspotTrafficStats): Notification {
        val title = when (state) {
            is ShareState.Active -> when (state.method) {
                ShareMethod.LAN_PROXY -> getString(R.string.hotspot_share_lan_proxy_active)
                ShareMethod.SHIZUKU -> "${getString(R.string.hotspot_share_service_active)} (Shizuku)"
                ShareMethod.ROOT -> "${getString(R.string.hotspot_share_service_active)} (Root)"
            }
            is ShareState.Starting -> getString(R.string.hotspot_share_status_starting)
            is ShareState.Stopped -> getString(R.string.hotspot_share_status_stopped_clean)
            is ShareState.Error -> getString(R.string.hotspot_share_status_unavailable)
            ShareState.Off -> getString(R.string.hotspot_share_service_name)
        }

        val text = when (state) {
            is ShareState.Active -> if (state.method == ShareMethod.LAN_PROXY) {
                getString(R.string.hotspot_share_method_lan_proxy)
            } else {
                val down = Formatter.formatFileSize(this, stats.rxSpeedBps) + "/s"
                val up = Formatter.formatFileSize(this, stats.txSpeedBps) + "/s"
                val total = Formatter.formatShortFileSize(this, stats.sessionRxBytes + stats.sessionTxBytes)
                getString(R.string.hotspot_share_speed_format, down, up) + " • " + getString(R.string.hotspot_share_traffic_format, total)
            }
            is ShareState.Stopped -> if (state.cause == ShareFailure.TETHER_LOST) {
                getString(R.string.hotspot_share_tether_lost_warn)
            } else {
                getString(R.string.hotspot_share_vpn_dropped_warn)
            }
            is ShareState.Error -> state.detail ?: getString(R.string.hotspot_share_err_generic)
            else -> getString(R.string.hotspot_share_card_summary)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LanSharingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TetherWatchService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_throne_tile)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(state is ShareState.Active || state is ShareState.Starting)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_navigation_close, getString(R.string.hotspot_share_stop), stopIntent)

        return builder.build()
    }

    private fun updateNotification(state: ShareState, stats: HotspotTrafficStats) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(NOTIFICATION_ID, buildNotification(state, stats))
        } catch (_: SecurityException) {
        }
    }

    override fun onDestroy() {
        isRunning = false
        stopTrafficMonitoring()
        systemNetReceiver?.let { runCatching { unregisterReceiver(it) } }
        systemNetReceiver = null
        connection.disconnect(this)
        serviceScope.cancel()
        super.onDestroy()
    }
}
