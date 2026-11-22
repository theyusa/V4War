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
import io.nekohasekai.sagernet.utils.LanSharingNetwork
import io.nekohasekai.sagernet.utils.PlatformNotifications
import io.nekohasekai.sagernet.utils.TetherKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.os.SystemClock

class TetherWatchService : Service(), SagerConnection.Callback {

    companion object {
        private const val TAG = "TetherWatchService"
        const val ACTION_START = "io.nekohasekai.sagernet.hotspotshare.action.START"
        const val ACTION_RESTART = "io.nekohasekai.sagernet.hotspotshare.action.RESTART"
        const val ACTION_STOP = "io.nekohasekai.sagernet.hotspotshare.action.STOP"

        const val CHANNEL_ID = "hotspot_share_service_v2"
        const val NOTIFICATION_ID = 5050

        const val WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"

        private const val RELOAD_GRACE_MS = 8_000L
        private const val DISCONNECT_GRACE_MS = 800L
        private const val GRACE_POLL_MS = 400L
        private const val TETHER_LOSS_STARTUP_GRACE_MS = 6_000L
        private const val TETHER_LOSS_DEBOUNCE_MS = 3_000L
        private const val START_GRACE_MS = 8_000L
        private const val CHANNEL_REFRESH_FALLBACK_MS = 25_000L
        private const val CHANNEL_REFRESH_MIN_GAP_MS = 20_000L
        private const val TRAFFIC_POLL_UI_FOREGROUND_MS = 1_000L
        private const val TRAFFIC_POLL_SHIZUKU_BG_MS = 2_500L
        private const val TRAFFIC_POLL_ROOT_BG_MS = 2_000L

        @Volatile
        private var isUiForeground: Boolean = false

        fun setUiForeground(foreground: Boolean) {
            isUiForeground = foreground
        }

        internal fun resolveTrafficPollIntervalMs(method: ShareMethod, uiForeground: Boolean): Long =
            when {
                uiForeground -> TRAFFIC_POLL_UI_FOREGROUND_MS
                method == ShareMethod.SHIZUKU -> TRAFFIC_POLL_SHIZUKU_BG_MS
                else -> TRAFFIC_POLL_ROOT_BG_MS
            }

        var isRunning: Boolean = false
            private set

        @Volatile
        private var lastStartRequestElapsedMs: Long = 0L

        fun isRunningOrStarting(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
            if (isRunning) return true
            val requestedAt = lastStartRequestElapsedMs
            return requestedAt > 0L && (nowElapsedMs - requestedAt) in 0L until START_GRACE_MS
        }

        fun start(context: Context) {
            lastStartRequestElapsedMs = SystemClock.elapsedRealtime()
            val intent = Intent(context, TetherWatchService::class.java).apply {
                action = ACTION_START
            }
            launchService(context, intent)
        }

        fun restart(context: Context) {
            lastStartRequestElapsedMs = SystemClock.elapsedRealtime()
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
            lastStartRequestElapsedMs = 0L
            val intent = Intent(context, TetherWatchService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)

    private var controller: HotspotShareController? = null
    private var activeBackend: HotspotShare? = null
    private var systemNetReceiver: BroadcastReceiver? = null
    @Volatile
    private var statsJob: Job? = null
    @Volatile
    private var vpnDropGraceJob: Job? = null
    @Volatile
    private var tetherDropDebounceJob: Job? = null
    @Volatile
    private var channelRefreshJob: Job? = null
    @Volatile
    private var channelSafetyJob: Job? = null
    @Volatile
    private var lastPersistJob: Job? = null
    private val persistMutex = Mutex()
    private val notificationLock = Any()
    @Volatile
    private var lastState: ShareState = ShareState.Off
    @Volatile
    private var lastActiveMethod: ShareMethod? = null
    @Volatile
    private var sessionGeneration: Long = 0L
    @Volatile
    private var sessionStartElapsedMs: Long = 0L
    @Volatile
    private var desiredActive: Boolean = false
    @Volatile
    private var cachedChannelLabel: String = ""
    @Volatile
    private var lastChannelRefreshElapsedMs: Long = 0L
    @Volatile
    private var lastNotificationSignature: String? = null
    private val contentPendingIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, LanSharingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
    private val stopPendingIntent: PendingIntent by lazy {
        PendingIntent.getService(
            this,
            1,
            Intent(this, TetherWatchService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

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
                    ShareMethod.SHIZUKU -> ShizukuBackend(
                        context = this,
                        failClosedAction = { FailClosedAction.fromCode(DataStore.hotspotShareFailClosed) },
                    )
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
                HotspotShareLogger.info(TAG, "State changed -> $state")
                lastState = state
                handleStateChange(state, sessionGeneration)
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
            ACTION_START -> {
                val fgState = if (lastState is ShareState.Active) lastState else ShareState.Starting
                startForeground(NOTIFICATION_ID, buildNotification(fgState, HotspotTrafficMonitor.stats.value))
                startSharingSession(restart = false)
                return START_STICKY
            }
            else -> {
                if (intent == null && !DataStore.hotspotShareEnabled) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val fgState = if (lastState is ShareState.Active) lastState else ShareState.Starting
                startForeground(NOTIFICATION_ID, buildNotification(fgState, HotspotTrafficMonitor.stats.value))
                startSharingSession(restart = false)
                return START_STICKY
            }
        }
    }

    private fun startSharingSession(restart: Boolean) {
        val alreadyActive = !restart &&
            lastState is ShareState.Active &&
            controller?.isPausedForVpnDrop != true
        sessionGeneration++
        val startGen = sessionGeneration
        desiredActive = true
        if (!alreadyActive) {
            sessionStartElapsedMs = SystemClock.elapsedRealtime()
            lastActiveMethod = null
        }
        vpnDropGraceJob?.cancel()
        vpnDropGraceJob = null
        tetherDropDebounceJob?.cancel()
        tetherDropDebounceJob = null
        PlatformNotifications.cancelHotspotShareDropped(this)
        serviceScope.launch {
            persistMutex.withLock {
                withContext(Dispatchers.IO + NonCancellable) {
                    if (!DataStore.hotspotShareEnabled) {
                        DataStore.hotspotShareEnabled = true
                    }
                }
            }
            if (startGen != sessionGeneration || !desiredActive) return@launch
            if (restart) {
                controller?.restart()
            } else {
                controller?.start()
            }
        }
    }

    private fun stopSharingSession() {
        sessionGeneration++
        val stopGen = sessionGeneration
        desiredActive = false
        vpnDropGraceJob?.cancel()
        vpnDropGraceJob = null
        tetherDropDebounceJob?.cancel()
        tetherDropDebounceJob = null
        channelRefreshJob?.cancel()
        channelRefreshJob = null
        channelSafetyJob?.cancel()
        channelSafetyJob = null
        cachedChannelLabel = ""
        lastActiveMethod = null
        PlatformNotifications.cancelHotspotShareDropped(this)
        serviceScope.launch {
            persistMutex.withLock {
                withContext(Dispatchers.IO + NonCancellable) {
                    if (DataStore.hotspotShareEnabled) {
                        DataStore.hotspotShareEnabled = false
                    }
                }
            }
            try {
                controller?.stop(stopHotspot = true)
            } finally {
                lastPersistJob?.join()
                if (stopGen == sessionGeneration && !desiredActive) {
                    activeBackend = null
                    stopTrafficMonitoring()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun handleStateChange(state: ShareState, publishGen: Long) {
        val pausedWaitingVpn = state is ShareState.Stopped &&
            state.cause == ShareFailure.VPN_LOST &&
            controller?.isPausedForVpnDrop == true &&
            lastActiveMethod != null
        val droppedMethod = lastActiveMethod

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
                refreshActiveChannelsAndNotify()
                startChannelSafetyFallback()
            }
            is ShareState.Stopped -> {
                vpnDropGraceJob?.cancel()
                vpnDropGraceJob = null
                channelSafetyJob?.cancel()
                channelSafetyJob = null
                stopTrafficMonitoring()
                if (pausedWaitingVpn) {
                    PlatformNotifications.cancelHotspotShareDropped(this)
                    updateNotification(state, HotspotTrafficStats())
                } else {
                    tetherDropDebounceJob?.cancel()
                    tetherDropDebounceJob = null
                    channelRefreshJob?.cancel()
                    channelRefreshJob = null
                    cachedChannelLabel = ""
                    desiredActive = false
                    if (droppedMethod == ShareMethod.SHIZUKU || droppedMethod == ShareMethod.ROOT) {
                        PlatformNotifications.hotspotShareDropped(this)
                    }
                    lastActiveMethod = null
                }
            }
            is ShareState.Error -> {
                desiredActive = false
                vpnDropGraceJob?.cancel()
                vpnDropGraceJob = null
                tetherDropDebounceJob?.cancel()
                tetherDropDebounceJob = null
                channelRefreshJob?.cancel()
                channelRefreshJob = null
                channelSafetyJob?.cancel()
                channelSafetyJob = null
                cachedChannelLabel = ""
                lastActiveMethod = null
                stopTrafficMonitoring()
            }
            is ShareState.Off -> {
                vpnDropGraceJob?.cancel()
                vpnDropGraceJob = null
                tetherDropDebounceJob?.cancel()
                tetherDropDebounceJob = null
                if (!desiredActive) {
                    channelRefreshJob?.cancel()
                    channelRefreshJob = null
                    channelSafetyJob?.cancel()
                    channelSafetyJob = null
                    cachedChannelLabel = ""
                    lastActiveMethod = null
                    PlatformNotifications.cancelHotspotShareDropped(this)
                    stopTrafficMonitoring()
                }
            }
            ShareState.Starting -> {
                updateNotification(state, HotspotTrafficStats())
            }
        }

        val shouldPersistEnabled = state is ShareState.Active ||
            state is ShareState.Starting ||
            pausedWaitingVpn
        val terminalShutdown = (state is ShareState.Error) ||
            (state is ShareState.Stopped && !pausedWaitingVpn) ||
            (state is ShareState.Off && !desiredActive)

        lastPersistJob = serviceScope.launch {
            persistMutex.withLock {
                withContext(Dispatchers.IO + NonCancellable) {
                    HotspotShareStateStore.publish(state)
                    val targetEnabled = if (desiredActive) true else shouldPersistEnabled
                    if (DataStore.hotspotShareEnabled != targetEnabled) {
                        DataStore.hotspotShareEnabled = targetEnabled
                    }
                }
            }
            sendBroadcast(Intent(Action.HOTSPOT_SHARE_STATE).setPackage(packageName))
            if (terminalShutdown && !desiredActive && publishGen == sessionGeneration) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
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

        HotspotTrafficMonitor.start(
            scope = serviceScope,
            rawStatsProvider = provider,
            intervalMs = TRAFFIC_POLL_UI_FOREGROUND_MS,
            dispatcher = Dispatchers.IO,
            intervalProvider = { resolveTrafficPollIntervalMs(method, isUiForeground) },
        )

        statsJob = serviceScope.launch(Dispatchers.Default) {
            HotspotTrafficMonitor.stats.collectLatest { stats ->
                val stateSnapshot = lastState
                if (stateSnapshot is ShareState.Active) {
                    updateNotification(stateSnapshot, stats)
                }
            }
        }
    }

    private fun startChannelSafetyFallback() {
        channelSafetyJob?.cancel()
        val currentGen = sessionGeneration
        channelSafetyJob = serviceScope.launch(Dispatchers.Default) {
            while (currentGen == sessionGeneration && lastState is ShareState.Active) {
                delay(CHANNEL_REFRESH_FALLBACK_MS)
                if (currentGen != sessionGeneration || lastState !is ShareState.Active) break
                val elapsedSinceLast = SystemClock.elapsedRealtime() - lastChannelRefreshElapsedMs
                if (elapsedSinceLast >= CHANNEL_REFRESH_MIN_GAP_MS) {
                    refreshActiveChannelsAndNotify()
                }
            }
        }
    }

    private fun stopTrafficMonitoring() {
        statsJob?.cancel()
        statsJob = null
        HotspotTrafficMonitor.stop()
    }

    private fun isLiveVpnConnected(): Boolean {
        val liveState = runCatching {
            connection.service?.state?.let { BaseService.State.entries.getOrNull(it) }
        }.getOrNull()
        return (liveState ?: DataStore.serviceState).connected
    }

    // VPN state listener from SagerConnection
    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        if (state.connected) {
            vpnDropGraceJob?.cancel()
            vpnDropGraceJob = null
            val currentGen = sessionGeneration
            serviceScope.launch {
                if (currentGen == sessionGeneration && controller?.isPausedForVpnDrop == true) {
                    controller?.resumeFromVpnDrop()
                }
            }
            return
        }
        val activeState = lastState as? ShareState.Active ?: return
        val currentGen = sessionGeneration
        val maxWaitMs = when (state) {
            BaseService.State.Stopping, BaseService.State.Connecting -> RELOAD_GRACE_MS
            else -> DISCONNECT_GRACE_MS
        }
        vpnDropGraceJob?.cancel()
        vpnDropGraceJob = serviceScope.launch {
            var elapsed = 0L
            while (elapsed < maxWaitMs) {
                delay(GRACE_POLL_MS)
                elapsed += GRACE_POLL_MS
                if (currentGen != sessionGeneration) return@launch
                val liveState = runCatching {
                    connection.service?.state?.let { BaseService.State.entries.getOrNull(it) }
                }.getOrNull() ?: DataStore.serviceState
                if (liveState.connected) return@launch
                // If VPN has finished stopping (not reloading/reconnecting), act after DISCONNECT_GRACE_MS
                if (!liveState.started && elapsed >= DISCONNECT_GRACE_MS) break
            }
            if (currentGen != sessionGeneration) return@launch
            if (isLiveVpnConnected()) return@launch
            if (lastState !is ShareState.Active) return@launch

            // Keep hotspot & switchHotspotShare alive across VPN drops and auto-resume when VPN reconnects.
            HotspotShareLogger.warn(TAG, "VPN disconnected while sharing (${activeState.method}); triggering fail-closed suspendForVpnDrop")
            controller?.suspendForVpnDrop()
            if (currentGen == sessionGeneration && isLiveVpnConnected() && controller?.isPausedForVpnDrop == true) {
                HotspotShareLogger.info(TAG, "VPN reconnected immediately after drop; resuming hotspot share")
                controller?.resumeFromVpnDrop()
            }
        }
    }

    override fun onServiceConnected(service: ISagerNetService) {
        val liveState = runCatching { BaseService.State.entries.getOrNull(service.state) }.getOrNull()
        if (liveState?.connected == true && controller?.isPausedForVpnDrop == true) {
            val currentGen = sessionGeneration
            serviceScope.launch {
                if (currentGen == sessionGeneration && controller?.isPausedForVpnDrop == true) {
                    controller?.resumeFromVpnDrop()
                }
            }
        }
    }

    private fun registerSystemNetReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                if (action != TETHER_STATE_CHANGED && action != WIFI_AP_STATE_CHANGED) return
                val activeMethod = (lastState as? ShareState.Active)?.method ?: lastActiveMethod ?: return
                val relevantState = lastState is ShareState.Active ||
                    (lastState as? ShareState.Stopped)?.cause == ShareFailure.VPN_LOST
                if (!relevantState) return

                // Shizuku sessions rely on ShareUserService's privileged UID-2000 watchdog for drop
                // detection; unprivileged TETHER_STATE_CHANGED extras can be incomplete on Android 13+ OEMs.
                if (action == TETHER_STATE_CHANGED && activeMethod == ShareMethod.ROOT) {
                    val active = (intent.getStringArrayListExtra("activeArray").orEmpty() +
                        intent.getStringArrayListExtra("tetherArray").orEmpty()).distinct()
                    if (active.isNotEmpty()) {
                        tetherDropDebounceJob?.cancel()
                        tetherDropDebounceJob = null
                    } else if (SystemClock.elapsedRealtime() - sessionStartElapsedMs >= TETHER_LOSS_STARTUP_GRACE_MS) {
                        val currentGen = sessionGeneration
                        tetherDropDebounceJob?.cancel()
                        tetherDropDebounceJob = serviceScope.launch {
                            delay(TETHER_LOSS_DEBOUNCE_MS)
                            if (currentGen != sessionGeneration) return@launch
                            val stillEmpty = withContext(Dispatchers.IO) {
                                !LanSharingNetwork.isHotspotActive(this@TetherWatchService) &&
                                    LanSharingNetwork.getActiveTetherKinds(this@TetherWatchService).isEmpty() &&
                                    RootBackend.getActiveTetheredInterfaces(this@TetherWatchService).isEmpty()
                            }
                            if (stillEmpty) {
                                controller?.stopDueToFailure(ShareFailure.TETHER_LOST)
                            }
                        }
                    }
                }

                refreshActiveChannelsAndNotify()
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

    private fun refreshActiveChannelsAndNotify() {
        val stateSnapshot = lastState
        val currentGen = sessionGeneration
        lastChannelRefreshElapsedMs = SystemClock.elapsedRealtime()
        channelRefreshJob?.cancel()
        channelRefreshJob = serviceScope.launch {
            val label = withContext(Dispatchers.IO) {
                resolveActiveChannelLabel(stateSnapshot)
            }
            if (currentGen != sessionGeneration) return@launch
            val changed = cachedChannelLabel != label
            cachedChannelLabel = label
            val latestState = lastState
            if (latestState is ShareState.Active) {
                updateNotification(latestState, HotspotTrafficMonitor.stats.value)
                if (changed) {
                    sendBroadcast(Intent(Action.HOTSPOT_SHARE_STATE).setPackage(packageName))
                }
            }
        }
    }

    private fun resolveActiveChannelLabel(state: ShareState): String {
        val kinds = linkedSetOf<TetherKind>()
        val activeMethod = (state as? ShareState.Active)?.method ?: lastActiveMethod
        when (activeMethod) {
            ShareMethod.SHIZUKU -> {
                val localKinds = runCatching { LanSharingNetwork.getActiveTetherKinds(this) }.getOrDefault(emptySet())
                if (TetherKind.WIFI in localKinds) kinds += TetherKind.WIFI
                if (LanSharingNetwork.USB_TETHER_ENABLED && TetherKind.USB in localKinds) kinds += TetherKind.USB
                val mask = ShizukuBackend.getActiveTetherMask(this)
                if ((mask and ShizukuBackend.TETHER_MASK_WIFI) != 0) kinds += TetherKind.WIFI
                if (LanSharingNetwork.USB_TETHER_ENABLED && (mask and ShizukuBackend.TETHER_MASK_USB) != 0) {
                    kinds += TetherKind.USB
                }
            }
            ShareMethod.ROOT -> {
                kinds += runCatching { LanSharingNetwork.getActiveTetherKinds(this) }.getOrDefault(emptySet())
                (activeBackend as? RootBackend)?.activeInterfaces?.forEach { iface ->
                    val kind = LanSharingNetwork.getTetherKindForInterface(iface)
                    if (kind != TetherKind.OTHER && (LanSharingNetwork.USB_TETHER_ENABLED || kind != TetherKind.USB)) {
                        kinds += kind
                    }
                }
            }
            else -> {
                kinds += runCatching { LanSharingNetwork.getActiveTetherKinds(this) }.getOrDefault(emptySet())
            }
        }
        return listOfNotNull(
            if (TetherKind.WIFI in kinds) "Wi-Fi" else null,
            if (LanSharingNetwork.USB_TETHER_ENABLED && TetherKind.USB in kinds) "USB" else null,
            if (activeMethod != ShareMethod.SHIZUKU && TetherKind.BLUETOOTH in kinds) "Bluetooth" else null,
        ).joinToString(" + ")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { manager.deleteNotificationChannel("hotspot_share_service") }
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

    private data class NotificationContent(
        val title: String,
        val text: String,
        val ongoing: Boolean,
    ) {
        fun signature(): String = "$title\u0000$text\u0000$ongoing"
    }

    private fun notificationContent(state: ShareState, stats: HotspotTrafficStats): NotificationContent {
        val channelSuffix = cachedChannelLabel.takeIf { it.isNotBlank() }
        val title = when (state) {
            is ShareState.Active -> when (state.method) {
                ShareMethod.LAN_PROXY -> if (channelSuffix != null) {
                    "${getString(R.string.hotspot_share_lan_proxy_active)} ($channelSuffix)"
                } else {
                    getString(R.string.hotspot_share_lan_proxy_active)
                }
                ShareMethod.SHIZUKU -> if (channelSuffix != null) {
                    "${getString(R.string.hotspot_share_service_active)} (Shizuku • $channelSuffix)"
                } else {
                    "${getString(R.string.hotspot_share_service_active)} (Shizuku)"
                }
                ShareMethod.ROOT -> if (channelSuffix != null) {
                    "${getString(R.string.hotspot_share_service_active)} (Root • $channelSuffix)"
                } else {
                    "${getString(R.string.hotspot_share_service_active)} (Root)"
                }
            }
            is ShareState.Starting -> getString(R.string.hotspot_share_status_starting)
            is ShareState.Stopped -> if (state.cause == ShareFailure.VPN_LOST && controller?.isPausedForVpnDrop == true) {
                getString(R.string.hotspot_share_waiting_vpn_title)
            } else {
                getString(R.string.hotspot_share_status_stopped_clean)
            }
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
            is ShareState.Stopped -> when {
                state.cause == ShareFailure.TETHER_LOST -> getString(R.string.hotspot_share_tether_lost_warn)
                controller?.isPausedForVpnDrop == true -> getString(R.string.hotspot_share_waiting_vpn_warn)
                else -> getString(R.string.hotspot_share_vpn_dropped_warn)
            }
            is ShareState.Error -> state.detail ?: getString(R.string.hotspot_share_err_generic)
            else -> getString(R.string.hotspot_share_card_summary)
        }

        val ongoing = state is ShareState.Active ||
            state is ShareState.Starting ||
            (state is ShareState.Stopped && state.cause == ShareFailure.VPN_LOST && controller?.isPausedForVpnDrop == true)

        return NotificationContent(title = title, text = text, ongoing = ongoing)
    }

    private fun buildNotification(content: NotificationContent): Notification {
        lastNotificationSignature = content.signature()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_throne_tile)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(contentPendingIntent)
            .setOngoing(content.ongoing)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_navigation_close, getString(R.string.hotspot_share_stop), stopPendingIntent)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
            .build()
    }

    private fun buildNotification(state: ShareState, stats: HotspotTrafficStats): Notification {
        return buildNotification(notificationContent(state, stats))
    }

    private fun updateNotification(state: ShareState, stats: HotspotTrafficStats) {
        synchronized(notificationLock) {
            val content = notificationContent(state, stats)
            val sig = content.signature()
            if (sig == lastNotificationSignature) return
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                manager.notify(NOTIFICATION_ID, buildNotification(content))
            } catch (_: SecurityException) {
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        lastStartRequestElapsedMs = 0L
        desiredActive = false
        lastNotificationSignature = null
        vpnDropGraceJob?.cancel()
        vpnDropGraceJob = null
        tetherDropDebounceJob?.cancel()
        tetherDropDebounceJob = null
        channelRefreshJob?.cancel()
        channelRefreshJob = null
        channelSafetyJob?.cancel()
        channelSafetyJob = null
        cachedChannelLabel = ""
        stopTrafficMonitoring()
        systemNetReceiver?.let { runCatching { unregisterReceiver(it) } }
        systemNetReceiver = null
        connection.disconnect(this)
        serviceScope.cancel()
        super.onDestroy()
    }
}
