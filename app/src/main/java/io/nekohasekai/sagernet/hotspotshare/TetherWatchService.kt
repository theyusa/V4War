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
import com.topjohnwu.superuser.Shell
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TetherWatchService : Service(), SagerConnection.Callback {

    companion object {
        const val ACTION_START = "io.nekohasekai.sagernet.hotspotshare.action.START"
        const val ACTION_STOP = "io.nekohasekai.sagernet.hotspotshare.action.STOP"

        const val CHANNEL_ID = "hotspot_share_service"
        const val NOTIFICATION_ID = 5050

        const val WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, TetherWatchService::class.java).apply {
                action = ACTION_START
            }
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
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)

    private var controller: HotspotShareController? = null
    private var activeBackend: HotspotShare? = null
    private var systemNetReceiver: BroadcastReceiver? = null
    private var statsJob: Job? = null
    private var lastState: ShareState = ShareState.Off

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
                    rootAvailable = Shell.isAppGrantedRoot() == true || RootBackend.isRootAvailable(),
                )
            },
            preference = { SharePreference.fromCode(DataStore.hotspotShareMethod) },
            backendFactory = { method ->
                val backend: HotspotShare = when (method) {
                    ShareMethod.SHIZUKU -> ShizukuBackend(this)
                    ShareMethod.ROOT -> RootBackend(this)
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
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification(ShareState.Starting, HotspotTrafficStats()))
                startSharingSession()
                return START_STICKY
            }
        }
    }

    private fun startSharingSession() {
        serviceScope.launch {
            controller?.start()
        }
    }

    private fun stopSharingSession() {
        serviceScope.launch {
            controller?.stop()
            activeBackend = null
            HotspotTrafficMonitor.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun handleStateChange(state: ShareState) {
        when (state) {
            is ShareState.Active -> {
                startTrafficMonitoring(state.method)
                updateNotification(state, HotspotTrafficMonitor.stats.value)
            }
            is ShareState.Stopped -> {
                stopTrafficMonitoring()
                PlatformNotifications.hotspotShareDropped(this)
                updateNotification(state, HotspotTrafficStats())
            }
            is ShareState.Error -> {
                stopTrafficMonitoring()
                updateNotification(state, HotspotTrafficStats())
            }
            is ShareState.Off -> {
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
                ShareMethod.SHIZUKU -> (activeBackend as? ShizukuBackend)?.getTrafficStats()
                    ?: ProcNetDevParser.readCurrent(setOf("testtun0"))
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
        if (!state.connected && lastState is ShareState.Active) {
            val backend = activeBackend
            val failClosedAction = FailClosedAction.fromCode(DataStore.hotspotShareFailClosed)
            serviceScope.launch {
                if (backend is RootBackend) {
                    backend.handleVpnDropped(failClosedAction)
                } else if (backend is ShizukuBackend) {
                    controller?.stop()
                }
                val stoppedState = ShareState.Stopped(ShareFailure.VPN_LOST)
                lastState = stoppedState
                HotspotShareStateStore.publish(stoppedState)
                PlatformNotifications.hotspotShareDropped(this@TetherWatchService)
                updateNotification(stoppedState, HotspotTrafficStats())
            }
        }
    }

    override fun onServiceConnected(service: ISagerNetService) {}

    private fun registerSystemNetReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val active = intent?.getStringArrayListExtra("activeArray")
                val tetherActive = active?.isNotEmpty() == true
                if (intent?.action == TETHER_STATE_CHANGED && !tetherActive && lastState is ShareState.Active) {
                    serviceScope.launch {
                        controller?.stop()
                        val stoppedState = ShareState.Stopped(ShareFailure.TETHER_LOST)
                        lastState = stoppedState
                        HotspotShareStateStore.publish(stoppedState)
                        PlatformNotifications.hotspotShareDropped(this@TetherWatchService)
                        updateNotification(stoppedState, HotspotTrafficStats())
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
            is ShareState.Active -> getString(R.string.hotspot_share_service_active) + " (${state.method.code})"
            is ShareState.Starting -> getString(R.string.hotspot_share_status_starting)
            is ShareState.Stopped -> getString(R.string.hotspot_share_leak_warn)
            is ShareState.Error -> getString(R.string.hotspot_share_status_error, state.reason.code)
            ShareState.Off -> getString(R.string.hotspot_share_service_name)
        }

        val text = when (state) {
            is ShareState.Active -> {
                val down = Formatter.formatFileSize(this, stats.rxSpeedBps) + "/s"
                val up = Formatter.formatFileSize(this, stats.txSpeedBps) + "/s"
                val total = Formatter.formatShortFileSize(this, stats.sessionRxBytes + stats.sessionTxBytes)
                getString(R.string.hotspot_share_speed_format, down, up) + " • " + getString(R.string.hotspot_share_traffic_format, total)
            }
            is ShareState.Stopped -> getString(R.string.hotspot_share_leak_warn)
            is ShareState.Error -> state.detail ?: state.reason.code
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
            .setSmallIcon(R.drawable.ic_hardware_router)
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
