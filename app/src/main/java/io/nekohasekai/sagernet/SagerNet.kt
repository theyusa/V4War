package io.nekohasekai.sagernet

import android.annotation.SuppressLint
import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.os.StrictMode
import android.os.UserManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.nekohasekai.sagernet.appwidget.Widgets
import io.nekohasekai.sagernet.bg.CoreForeground
import io.nekohasekai.sagernet.bg.CoreRuntime
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.bg.autoselector.AutoSelectorRuntime
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SettingsMapper
import io.nekohasekai.sagernet.group.RemoteRouteUpdater
import io.nekohasekai.sagernet.group.SubscriptionScheduler
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.isOss
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.update.UpdateManager
import io.nekohasekai.sagernet.utils.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import moe.matsuri.nb4a.utils.JavaUtil
import moe.matsuri.nb4a.utils.cleanWebview
import androidx.work.Configuration as WorkConfiguration

class SagerNet : Application(),
    WorkConfiguration.Provider {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        application = this
    }

    val process: String = JavaUtil.getProcessName()
    private val isMainProcess = process == BuildConfig.APPLICATION_ID
    val isBgProcess = process.endsWith(":bg")

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)

        if (isMainProcess || isBgProcess) {
            SettingsMapper.installOutboundHooks()
            if (isBgProcess) {
                // The tile, shortcuts, widgets and the boot receiver start the foreground service from here.
                updateNotificationChannels()
                // A new :bg process means no service runs: heal widgets left "Connected" by a killed one.
                Widgets.push(this)
                // The core (and its JNI library) is loaded in the :bg process only.
                CoreRuntime.setup(this)
                AutoSelectorRuntime.install()
                // Warm the default-network cache so a box's interface monitor gets its interface synchronously.
                runOnDefaultDispatcher {
                    DefaultNetworkListener.start(this@SagerNet) {
                        underlyingNetwork = it
                    }
                }
            }

            // fix multi process issue in Android 9+
            JavaUtil.initWebviewSuffix(this)

            runOnIoDispatcher {
                JavaUtil.cleanWebviewDir(this@SagerNet)
                cleanWebview()
                DataStore.initGlobal()
                PackageCache.register()
            }
        }

        if (isMainProcess) {
            warmSystemFeatures()
            Theme.applyNightTheme()
            Theme.apply(this)
            AppLocale.apply()
            UpdateManager.onAppStart(this)
            HotspotGatewayManager.init()
            runOnDefaultDispatcher {
                DefaultNetworkListener.start(this) {
                    underlyingNetwork = it
                }

                updateNotificationChannels()
            }
            // The icon pack of the removed custom icon feature; a no-op once it is gone.
            runOnIoDispatcher { File(filesDir, "custom_icon").deleteRecursively() }
            RemoteRouteUpdater.schedule()
            SubscriptionScheduler.schedule()
            Widgets.watchSelection(this)
        }

        if (BuildConfig.DEBUG) {
            System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateNotificationChannels()
    }

    // Enqueueing (RemoteWorkManagerService) and SystemJobService live in the main process, so the reschedule after a
    // reboot, force-stop or update (ForceStopRunnable, default process only) must run there too; workers hop to :bg.
    override fun getWorkManagerConfiguration(): WorkConfiguration {
        return WorkConfiguration.Builder()
            .setDefaultProcessName(BuildConfig.APPLICATION_ID)
            .build()
    }

    @SuppressLint("InlinedApi")
    companion object {

        const val CHANNEL_SERVICE_VPN = "service-vpn-v2"
        const val CHANNEL_SERVICE_PROXY = "service-proxy-v2"
        const val CHANNEL_SERVICE_SUBSCRIPTION = "service-subscription-v2"

        lateinit var application: SagerNet

        @Volatile
        private var cachedIsTv: Boolean? = null

        @Volatile
        private var cachedHasCamera: Boolean? = null

        @Volatile
        private var systemFeaturesStarted = false

        private val systemFeaturesWarmed = CompletableDeferred<Unit>()

        private fun computeIsTv(): Boolean {
            return uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                application.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }

        fun warmSystemFeatures() {
            if (cachedIsTv != null && cachedHasCamera != null) {
                systemFeaturesWarmed.complete(Unit)
                return
            }
            if (systemFeaturesStarted) return
            synchronized(this) {
                if (systemFeaturesStarted) return
                systemFeaturesStarted = true
            }
            runOnIoDispatcher {
                try {
                    if (cachedIsTv == null) {
                        cachedIsTv = computeIsTv()
                    }
                    if (cachedHasCamera == null) {
                        cachedHasCamera = application.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
                    }
                } finally {
                    systemFeaturesWarmed.complete(Unit)
                }
            }
        }

        val isTv: Boolean
            get() {
                cachedIsTv?.let { return it }
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    return computeIsTv().also { cachedIsTv = it }
                }
                warmSystemFeatures()
                return (application.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
                    Configuration.UI_MODE_TYPE_TELEVISION
            }

        fun cachedIsTvOrNull(): Boolean? = cachedIsTv

        suspend fun awaitIsTv(): Boolean {
            cachedIsTv?.let { return it }
            warmSystemFeatures()
            systemFeaturesWarmed.await()
            return cachedIsTv ?: withContext(Dispatchers.IO) {
                computeIsTv().also { cachedIsTv = it }
            }
        }

        fun cachedHasCamera(): Boolean? = cachedHasCamera

        suspend fun hasCamera(): Boolean {
            cachedHasCamera?.let { return it }
            warmSystemFeatures()
            systemFeaturesWarmed.await()
            return cachedHasCamera ?: withContext(Dispatchers.IO) {
                application.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
                    .also { cachedHasCamera = it }
            }
        }

        val configureIntent: (Context) -> PendingIntent by lazy {
            {
                PendingIntent.getActivity(
                    it,
                    0,
                    Intent(
                        application, MainActivity::class.java
                    ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )
            }
        }
        val activity by lazy { application.getSystemService<ActivityManager>()!! }
        val clipboard by lazy { application.getSystemService<ClipboardManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val user by lazy { application.getSystemService<UserManager>()!! }
        val uiMode by lazy { application.getSystemService<UiModeManager>()!! }
        val power by lazy { application.getSystemService<PowerManager>()!! }

        fun getClipboardText(): String {
            return clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.text?.toString() ?: ""
        }

        /** The clip travels as UTF-16 in one binder transaction, whose buffer (about 1 MB) the process shares. */
        const val MAX_CLIP_CHARS = 400_000

        /** False when the text is over [MAX_CLIP_CHARS] or the clipboard refused it. */
        fun trySetPrimaryClip(clip: String): Boolean {
            if (clip.length > MAX_CLIP_CHARS) return false
            return try {
                clipboard.setPrimaryClip(ClipData.newPlainText(null, clip))
                true
            } catch (e: RuntimeException) {
                Logs.w(e)
                false
            }
        }

        fun updateNotificationChannels() {
            if (Build.VERSION.SDK_INT >= 26) @RequiresApi(26) {
                for (legacyId in listOf(
                    "service-vpn",
                    "service-proxy",
                    "service-subscription",
                    "connection-test",
                    "warnings",
                )) {
                    runCatching { notification.deleteNotificationChannel(legacyId) }
                }
                notification.createNotificationChannels(
                    listOf(
                        NotificationChannel(
                            CHANNEL_SERVICE_VPN,
                            application.getText(R.string.service_vpn),
                            if (Build.VERSION.SDK_INT >= 28) NotificationManager.IMPORTANCE_MIN
                            else NotificationManager.IMPORTANCE_LOW
                        ).apply { setShowBadge(false) },   // #1355
                        NotificationChannel(
                            CHANNEL_SERVICE_PROXY,
                            application.getText(R.string.service_proxy),
                            NotificationManager.IMPORTANCE_LOW
                        ).apply { setShowBadge(false) },
                        NotificationChannel(
                            CHANNEL_SERVICE_SUBSCRIPTION,
                            application.getText(R.string.service_subscription),
                            NotificationManager.IMPORTANCE_DEFAULT
                        ).apply { setShowBadge(false) },
                        NotificationChannel(
                            CoreForeground.CHANNEL,
                            application.getText(R.string.connection_test),
                            NotificationManager.IMPORTANCE_DEFAULT
                        ).apply { setShowBadge(false) },
                        NotificationChannel(
                            PlatformNotifications.CHANNEL_WARNINGS,
                            application.getText(R.string.channel_warnings),
                            NotificationManager.IMPORTANCE_DEFAULT
                        ).apply { setShowBadge(false) },
                    )
                )
            }
        }

        fun startService() = ContextCompat.startForegroundService(
            application, Intent(application, SagerConnection.serviceClass)
        )

        fun reloadService() =
            application.sendBroadcast(Intent(Action.RELOAD).setPackage(application.packageName))

        fun refreshNotification() =
            application.sendBroadcast(Intent(Action.REFRESH_NOTIFICATION).setPackage(application.packageName))

        fun stopService() =
            application.sendBroadcast(Intent(Action.CLOSE).setPackage(application.packageName))

        var underlyingNetwork: Network? = null

        var appVersionNameForDisplay = {
            var n = BuildConfig.VERSION_NAME
            if (!isOss) {
                n += " ${BuildConfig.FLAVOR}"
            }
            if (BuildConfig.DEBUG) {
                n += " DEBUG"
            }
            n
        }()
    }

}
