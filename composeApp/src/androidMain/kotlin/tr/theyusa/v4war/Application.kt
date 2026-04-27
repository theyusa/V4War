package tr.theyusa.v4war

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.StrictMode
import tr.theyusa.v4war.bg.AppChangeReceiver
import tr.theyusa.v4war.bg.DefaultNetworkMonitor
import tr.theyusa.v4war.bg.SubscriptionUpdater
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.di.initV4WarKoin
import tr.theyusa.v4war.ktx.runOnDefaultDispatcher
import tr.theyusa.v4war.libcore.Libcore
import tr.theyusa.v4war.libcore.loadCA
import tr.theyusa.v4war.repository.AndroidRepository
import tr.theyusa.v4war.repository.SagerRepository
import tr.theyusa.v4war.utils.CrashHandler
import tr.theyusa.v4war.utils.PackageCache
import tr.theyusa.v4war.utils.copyBundledRuleSetAssetsIfNeeded
import go.Seq
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import kotlinx.coroutines.runBlocking
import java.io.File
import androidx.work.Configuration as WorkConfiguration

class Application : Application(),
    WorkConfiguration.Provider {

    private lateinit var repository: AndroidRepository

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        repository = SagerRepository(this, isMainProcess, isBgProcess)
    }

    val externalAssets: File by lazy { getExternalFilesDir(null) ?: filesDir }
    private val appId by lazy { packageName }
    private val process by lazy { tryGetProcessName() }
    val isMainProcess get() = process == appId
    val isBgProcess get() = process.endsWith(":bg")

    override fun onCreate() {
        super.onCreate()
        initV4WarKoin(repository)

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)

        if (isMainProcess || isBgProcess) {
            runOnDefaultDispatcher {
                PackageCache.register(this@Application)
            }
        }

        Seq.setContext(this)
        runOnDefaultDispatcher {
            repository.updateNotificationChannels()
        }

        // init core
        externalAssets.mkdirs()
        if (isBgProcess && DataStore.rulesProvider == RuleProvider.OFFICIAL) {
            runBlocking { copyBundledRuleSetAssetsIfNeeded() }
        }
        Libcore.initCore(
            isBgProcess,
            cacheDir.absolutePath + "/",
            filesDir.absolutePath + "/",
            externalAssets.absolutePath + "/",
            DataStore.logMaxLine,
            DataStore.logLevel,
            DataStore.rulesProvider == 0,
            DataStore.isExpert,
        )
        loadCA(DataStore.certProvider)

        if (isMainProcess) runOnDefaultDispatcher {
            runCatching {
                SubscriptionUpdater.reconfigureUpdater()
            }
            registerReceiver(
                AppChangeReceiver(),
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addDataScheme("package")
                },
            )
        }

        if (isMainProcess) {
            runOnDefaultDispatcher {
                DefaultNetworkMonitor.start()
            }
        }

        if (isBgProcess) {
            repository.boxService?.start()
        }

        if (DataStore.isExpert) StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        runOnDefaultDispatcher {
            repository.updateNotificationChannels()
        }
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setDefaultProcessName(appId)
            .build()

    @SuppressLint("PrivateApi")
    private fun tryGetProcessName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return getProcessName()

        // Using the same technique as Application.getProcessName() for older devices
        // Using reflection since ActivityThread is an internal API
        try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val methodName = "currentProcessName"
            val getProcessName = activityThread.getDeclaredMethod(methodName)
            return getProcessName.invoke(null) as String
        } catch (_: Exception) {
            return appId
        }
    }

}
