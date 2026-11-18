package io.nekohasekai.sagernet.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.settings.GeneralSettingsFragment
import io.nekohasekai.sagernet.utils.BatteryOptimizations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The first start: the notification permission (13+), then the battery-optimization exemption, each asked once.
 * `KEY_SHOWN` is set before the battery prompt shows, so a kill or a rotation never asks twice. Create it while the
 * activity initialises.
 */
class FirstStartPrompts(private val activity: MainActivity) {

    private val notificationPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { askBattery() }

    private val batteryRequest = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // The request reports nothing useful: ask the system what was decided.
        activity.lifecycleScope.launch {
            val appContext = activity.applicationContext
            val ignoring = withContext(Dispatchers.IO) { BatteryOptimizations.isIgnoring(appContext) }
            if (!ignoring) showLaterHint()
        }
    }

    /**
     * Kept out of the database: a reinstall restores it from the cloud backup (only sager_net.db is backed up), and
     * the prompts must run again there. An upgrade carries the old database flag over; a fresh install that got the
     * database back from a backup (first install time == last update time) does not.
     */
    private val prefs by lazy { activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private var startJob: Job? = null
    private var batteryJob: Job? = null

    private fun readOrInitShown(): Boolean {
        if (prefs.contains(KEY_SHOWN)) return prefs.getBoolean(KEY_SHOWN, false)
        val appContext = activity.applicationContext
        val info = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val upgraded = info != null && info.lastUpdateTime > info.firstInstallTime
        return (DataStore.batteryPromptShown && upgraded).also {
            prefs.edit().putBoolean(KEY_SHOWN, it).apply()
        }
    }

    fun start() {
        if (startJob?.isActive == true) return
        startJob = activity.lifecycleScope.launch {
            val alreadyShown = withContext(Dispatchers.IO) { readOrInitShown() }
            if (alreadyShown || activity.isFinishing || activity.isDestroyed) return@launch
            val isTv = SagerNet.awaitIsTv()
            if (activity.isFinishing || activity.isDestroyed) return@launch
            if (Build.VERSION.SDK_INT >= 33 && !isTv &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                if (activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.notification_permission)
                        .setMessage(R.string.notification_permission_rationale)
                        .setPositiveButton(R.string.wifi_permission_continue) { _, _ -> requestNotifications() }
                        .setNegativeButton(R.string.not_now) { _, _ -> askBattery() }
                        .setOnCancelListener { askBattery() }
                        .show()
                } else {
                    requestNotifications()
                }
            } else {
                askBattery()
            }
        }
    }

    private fun requestNotifications() {
        try {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } catch (_: ActivityNotFoundException) {
            askBattery()
        }
    }

    private fun askBattery() {
        if (batteryJob?.isActive == true) return
        batteryJob = activity.lifecycleScope.launch {
            val appContext = activity.applicationContext
            val intent = withContext(Dispatchers.IO) {
                if (readOrInitShown()) return@withContext null
                prefs.edit().putBoolean(KEY_SHOWN, true).apply()
                if (BatteryOptimizations.isIgnoring(appContext)) return@withContext null
                // No request screen (TVs, trimmed ROMs): nothing to offer.
                BatteryOptimizations.requestIntent(appContext)
            } ?: return@launch
            if (activity.isFinishing || activity.isDestroyed) return@launch
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.battery_prompt_title)
                .setMessage(R.string.battery_prompt_message)
                .setPositiveButton(R.string.battery_prompt_allow) { _, _ ->
                    try {
                        batteryRequest.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        showLaterHint()
                    } catch (_: SecurityException) {
                        showLaterHint()
                    }
                }
                .setNegativeButton(R.string.not_now) { _, _ -> showLaterHint() }
                .setOnCancelListener { showLaterHint() }
                .show()
        }
    }

    private fun showLaterHint() {
        if (activity.isFinishing || activity.isDestroyed) return
        activity.snackbar(R.string.battery_prompt_later).setAction(R.string.settings) {
            activity.openSettingsScreen(
                GeneralSettingsFragment::class.java.name, activity.getString(R.string.settings_general)
            )
        }.show()
    }

    private companion object {
        const val PREFS = "first_start"
        const val KEY_SHOWN = "prompts_shown"
    }
}
