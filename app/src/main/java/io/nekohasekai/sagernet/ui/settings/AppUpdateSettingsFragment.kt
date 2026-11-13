package io.nekohasekai.sagernet.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SettingsRegistry
import io.nekohasekai.sagernet.hotspotshare.ShizukuBackend
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.update.UpdateActivity
import io.nekohasekai.sagernet.update.UpdateChecker
import io.nekohasekai.sagernet.update.UpdateManager
import io.nekohasekai.sagernet.update.UpdateScheduler
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Material 3 Expressive App Update screen:
 * auto-check toggle, pre-release toggle, optional GitHub API token, unknown-sources install permission,
 * Shizuku silent install toggle + runtime state, instant check + last-checked date, and Obtainium tracking.
 */
class AppUpdateSettingsFragment : SettingsScreenFragment(R.xml.settings_app_update) {

    private lateinit var autoCheckPref: SwitchPreference
    private lateinit var betaPref: SwitchPreference
    private lateinit var githubTokenPref: Preference
    private lateinit var allowInstallPref: Preference
    private lateinit var shizukuPref: SwitchPreference
    private lateinit var checkNowPref: Preference
    private lateinit var obtainiumPref: Preference

    private val unknownSourcesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshRuntimeStates()
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQ_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                DataStore.updateInstallShizuku = true
            }
            refreshRuntimeStates()
        }
    }

    override fun bind() {
        autoCheckPref = pref(Key.UPDATE_CHECK_AUTO)
        betaPref = pref(SettingsRegistry.ALLOW_BETA_UPDATE.key)
        githubTokenPref = pref(Key.UPDATE_GITHUB_TOKEN)
        allowInstallPref = pref(KEY_ALLOW_INSTALLING_APPS)
        shizukuPref = pref(Key.UPDATE_INSTALL_SHIZUKU)
        checkNowPref = pref(KEY_CHECK_UPDATES_NOW)
        obtainiumPref = pref(KEY_TRACK_IN_OBTAINIUM)

        if (!BuildConfig.IN_APP_UPDATER) {
            autoCheckPref.isVisible = false
            betaPref.isVisible = false
            allowInstallPref.isVisible = false
            shizukuPref.isVisible = false
            checkNowPref.isVisible = false
        }

        autoCheckPref.setOnPreferenceChangeListener { _, newValue ->
            UpdateScheduler.schedule(enabled = newValue as Boolean)
            true
        }

        githubTokenPref.setOnPreferenceClickListener {
            showGithubTokenDialog()
            true
        }

        allowInstallPref.setOnPreferenceClickListener {
            openUnknownSourcesSettings()
            true
        }

        shizukuPref.setOnPreferenceChangeListener { _, newValue ->
            val target = newValue as Boolean
            if (!target) return@setOnPreferenceChangeListener true

            if (!ShizukuBackend.isShizukuRunning()) {
                refreshRuntimeStates()
                return@setOnPreferenceChangeListener false
            }
            if (!ShizukuBackend.isShizukuGranted(requireContext())) {
                runCatching { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQ_CODE) }
                return@setOnPreferenceChangeListener false
            }
            true
        }

        checkNowPref.setOnPreferenceClickListener {
            if (BuildConfig.IN_APP_UPDATER) {
                UpdateActivity.start(requireContext())
            } else {
                requireContext().launchCustomTab(UpdateChecker.RELEASES_PAGE)
            }
            true
        }

        obtainiumPref.setOnPreferenceClickListener {
            openInObtainium()
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                UpdateManager.state.collect {
                    refreshLastCheckedSummary()
                }
            }
        }
    }

    override fun onDestroyView() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        refreshRuntimeStates()
    }

    private fun refreshRuntimeStates() {
        if (!::githubTokenPref.isInitialized || !isAdded) return
        val context = requireContext()

        val token = DataStore.updateGithubToken.trim()
        githubTokenPref.summary = if (token.isEmpty()) {
            getString(R.string.app_update_github_token_not_set)
        } else {
            val suffix = if (token.length >= 4) token.takeLast(4) else token
            getString(R.string.app_update_github_token_set, suffix)
        }

        val canInstall = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
        allowInstallPref.summary = getString(
            if (canInstall) R.string.app_update_allow_install_granted
            else R.string.app_update_allow_install_denied
        )

        val shizukuInstalled = isAnyPackageInstalled(context, SHIZUKU_PACKAGES)
        val shizukuRunning = shizukuInstalled && ShizukuBackend.isShizukuRunning()
        val shizukuGranted = shizukuRunning && ShizukuBackend.isShizukuGranted(context)
        shizukuPref.isEnabled = shizukuRunning
        if (!shizukuRunning) {
            shizukuPref.isChecked = false
        } else {
            shizukuPref.isChecked = DataStore.updateInstallShizuku && shizukuGranted
        }
        shizukuPref.summary = when {
            !shizukuInstalled -> getString(R.string.app_update_shizuku_not_installed)
            !shizukuRunning -> getString(R.string.app_update_shizuku_not_running)
            !shizukuGranted -> getString(R.string.app_update_shizuku_needs_permission)
            else -> getString(R.string.app_update_shizuku_ready)
        }

        val obtainiumPkg = findInstalledPackage(context, OBTAINIUM_PACKAGES)
        obtainiumPref.summary = getString(
            if (obtainiumPkg != null) R.string.app_update_obtainium_installed_sum
            else R.string.app_update_obtainium_copy_sum
        )

        refreshLastCheckedSummary()
    }

    private fun refreshLastCheckedSummary() {
        if (!::checkNowPref.isInitialized || !isAdded) return
        val lastCheckMs = DataStore.updateLastCheckTime
        checkNowPref.summary = if (lastCheckMs > 0L) {
            val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(lastCheckMs))
            getString(R.string.app_update_last_checked, formatted)
        } else {
            getString(R.string.app_update_never_checked)
        }
    }

    private fun showGithubTokenDialog() {
        val context = requireContext()
        val editText = EditText(context).apply {
            setText(DataStore.updateGithubToken)
            setSelection(text.length)
            hint = getString(R.string.app_update_github_token_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            isSingleLine = true
        }
        val container = FrameLayout(context).apply {
            val padH = dp2px(24)
            val padV = dp2px(12)
            setPadding(padH, padV, padH, dp2px(4))
            addView(
                editText,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.app_update_github_token_title)
            .setMessage(R.string.app_update_github_token_dialog_message)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                DataStore.updateGithubToken = editText.text?.toString()?.trim().orEmpty()
                refreshRuntimeStates()
            }
            .setNeutralButton(R.string.app_update_clear) { _, _ ->
                DataStore.updateGithubToken = ""
                refreshRuntimeStates()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openUnknownSourcesSettings() {
        val context = requireContext()
        if (Build.VERSION.SDK_INT < 26) {
            toast(R.string.app_update_allow_install_granted)
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        )
        runCatching { unknownSourcesLauncher.launch(intent) }
            .onFailure { toast(R.string.update_permission_message) }
    }

    private fun openInObtainium() {
        val context = requireContext()
        val obtainiumPkg = findInstalledPackage(context, OBTAINIUM_PACKAGES)
        if (obtainiumPkg != null) {
            val obtainiumUri = "obtainium://add/${Uri.encode(UpdateChecker.RELEASES_PAGE)}".toUri()
            val intent = Intent(Intent.ACTION_VIEW, obtainiumUri).apply {
                setPackage(obtainiumPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching {
                startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), UpdateChecker.RELEASES_PAGE))
        toast(R.string.app_update_obtainium_copied)
    }

    private fun isAnyPackageInstalled(context: Context, packages: List<String>): Boolean {
        return findInstalledPackage(context, packages) != null
    }

    @Suppress("DEPRECATION")
    private fun findInstalledPackage(context: Context, packages: List<String>): String? {
        val pm = context.packageManager
        for (pkg in packages) {
            val found = runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    pm.getPackageInfo(pkg, 0)
                }
                true
            }.getOrDefault(false)
            if (found) return pkg
        }
        return null
    }

    private companion object {
        const val KEY_ALLOW_INSTALLING_APPS = "allowInstallingApps"
        const val KEY_CHECK_UPDATES_NOW = "checkUpdatesNow"
        const val KEY_TRACK_IN_OBTAINIUM = "trackInObtainium"
        const val SHIZUKU_PERMISSION_REQ_CODE = 4091
        val SHIZUKU_PACKAGES = listOf("moe.shizuku.privileged.api")
        val OBTAINIUM_PACKAGES = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")
    }
}
