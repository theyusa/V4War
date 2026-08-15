package tr.theyusa.v4war.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.ktx.Logs
import tr.theyusa.v4war.ktx.runOnIoDispatcher
import tr.theyusa.v4war.utils.AppScanner
import tr.theyusa.v4war.utils.PackageCache

class AppChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logs.d("onReceive: ${intent.action}")
        val pendingResult = goAsync()
        runOnIoDispatcher {
            try {
                checkUpdate(intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun checkUpdate(intent: Intent) {
        if (!DataStore.proxyApps) {
            Logs.d("should not check in bypass mode")
            return
        }
        if (!DataStore.updateProxyAppsWhenInstall) {
            Logs.d("per app proxy disabled")
            return
        }
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            Logs.d("skip app update because of EXTRA_REPLACING")
            return
        }
        val packageName = intent.dataString?.substringAfter("package:")
        if (packageName.isNullOrBlank()) {
            Logs.d("missing package name in intent")
            return
        }
        val isChinaApp = AppScanner.isChinaApp(packageName, PackageCache.packageManager)
        Logs.d("scan china app result for $packageName: $isChinaApp")
        if (isChinaApp && DataStore.bypassMode) {
            DataStore.packages += packageName
        } else if (!isChinaApp && !DataStore.bypassMode) {
            DataStore.packages += packageName
        }
    }

}
