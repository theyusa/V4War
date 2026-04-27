package tr.theyusa.v4war.ui

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.lazy.LazyListScope
import tr.theyusa.v4war.compose.material3.Icon
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.DEFAULT_HTTP_BYPASS
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.compose.HostTextField
import tr.theyusa.v4war.compose.PreferenceType
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.ktx.findActivity
import tr.theyusa.v4war.ktx.getColour
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.acquire_wake_lock
import tr.theyusa.v4war.resources.acquire_wake_lock_summary
import tr.theyusa.v4war.resources.battery_charging_full
import tr.theyusa.v4war.resources.smart_wake_lock
import tr.theyusa.v4war.resources.smart_wake_lock_summary
import tr.theyusa.v4war.resources.allow_apps_bypass_vpn
import tr.theyusa.v4war.resources.auto_connect
import tr.theyusa.v4war.resources.auto_connect_summary
import tr.theyusa.v4war.resources.data_usage
import tr.theyusa.v4war.resources.developer_board
import tr.theyusa.v4war.resources.disable_process_text
import tr.theyusa.v4war.resources.domain
import tr.theyusa.v4war.resources.format_align_left
import tr.theyusa.v4war.resources.http_proxy_bypass
import tr.theyusa.v4war.resources.label
import tr.theyusa.v4war.resources.legend_toggle
import tr.theyusa.v4war.resources.metered
import tr.theyusa.v4war.resources.metered_summary
import tr.theyusa.v4war.resources.phonelink_ring
import tr.theyusa.v4war.resources.privacy
import tr.theyusa.v4war.resources.privacy_mode
import tr.theyusa.v4war.resources.privacy_mode_summary
import tr.theyusa.v4war.resources.route_opt_bypass_lan
import tr.theyusa.v4war.resources.show_group_in_notification
import tr.theyusa.v4war.resources.transform
import kotlinx.coroutines.flow.flowOf
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

internal actual fun LazyListScope.autoConnect() {
    item(Key.PERSIST_ACROSS_REBOOT, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.PERSIST_ACROSS_REBOOT, false)
            .collectAsStateWithLifecycle(false)
        SwitchPreference(
            value = value,
            onValueChange = { DataStore.persistAcrossReboot = it },
            title = { Text(stringResource(Res.string.auto_connect)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.phonelink_ring),
                    null,
                )
            },
            summary = { Text(stringResource(Res.string.auto_connect_summary)) },
        )
    }
}

@Composable
internal actual fun rememberApplyNightMode(): (Int) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { selection ->
            AppCompatDelegate.setDefaultNightMode(
                when (selection) {
                    0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    2 -> AppCompatDelegate.MODE_NIGHT_NO
                    else -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                },
            )
            context.findActivity<Activity>()!!.recreate()
        }
    }
}

internal actual fun LazyListScope.platformGeneralOptions(needReload: () -> Unit) {
    item(Key.ALLOW_APPS_BYPASS_VPN, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.ALLOW_APPS_BYPASS_VPN, false)
            .collectAsStateWithLifecycle(false)
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.allowAppsBypassVpn = it
                needReload()
            },
            title = { Text(stringResource(Res.string.allow_apps_bypass_vpn)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.transform),
                    null,
                )
            },
        )
    }
    item(Key.SHOW_GROUP_IN_NOTIFICATION, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.SHOW_GROUP_IN_NOTIFICATION, false)
            .collectAsStateWithLifecycle(false)
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.showGroupInNotification = it
                needReload()
            },
            title = { Text(stringResource(Res.string.show_group_in_notification)) },
            icon = { Icon(vectorResource(Res.drawable.label), null) },
        )
    }
}

internal actual fun LazyListScope.platformSecurityOptions() {
    item(Key.PRIVACY_MODE, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.PRIVACY_MODE, false)
            .collectAsStateWithLifecycle(false)
        SwitchPreference(
            value = value,
            onValueChange = { DataStore.privacyMode = it },
            title = { Text(stringResource(Res.string.privacy_mode)) },
            icon = { Icon(vectorResource(Res.drawable.privacy), null) },
            summary = { Text(stringResource(Res.string.privacy_mode_summary)) },
        )
    }
}

internal actual fun LazyListScope.meteredNetworkSetting(needReload: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    item(Key.METERED_NETWORK, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.METERED_NETWORK, false)
            .collectAsStateWithLifecycle(false)
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.meteredNetwork = it
                needReload()
            },
            title = { Text(stringResource(Res.string.metered)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.data_usage),
                    null,
                )
            },
            summary = { Text(stringResource(Res.string.metered_summary)) },
        )
    }
}

internal actual fun LazyListScope.platformRouteOptions(needReload: () -> Unit, isVpnMode: Boolean) {
    item(Key.BYPASS_LAN, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.BYPASS_LAN, true)
            .collectAsStateWithLifecycle(true)
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.bypassLan = it
                needReload()
            },
            title = { Text(stringResource(Res.string.route_opt_bypass_lan)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.legend_toggle),
                    null,
                )
            },
        )
    }
}

internal actual fun LazyListScope.platformMiscOptions(needReload: () -> Unit) {
    item(Key.ACQUIRE_WAKE_LOCK, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.ACQUIRE_WAKE_LOCK, true)
            .collectAsStateWithLifecycle(true)
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.acquireWakeLock = it
                needReload()
            },
            title = { Text(stringResource(Res.string.acquire_wake_lock)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.developer_board),
                    null,
                )
            },
            summary = { Text(stringResource(Res.string.acquire_wake_lock_summary)) },
        )
    }
    item(Key.SMART_WAKE_LOCK, PreferenceType.SWITCH) {
        val wakeLockValue by DataStore.configurationStore
            .booleanFlow(Key.ACQUIRE_WAKE_LOCK, true)
            .collectAsStateWithLifecycle(true)
        val value by DataStore.configurationStore
            .booleanFlow(Key.SMART_WAKE_LOCK, true)
            .collectAsStateWithLifecycle(true)
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.smartWakeLock = it
                needReload()
            },
            title = { Text(stringResource(Res.string.smart_wake_lock)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.battery_charging_full),
                    null,
                )
            },
            summary = { Text(stringResource(Res.string.smart_wake_lock_summary)) },
            enabled = !wakeLockValue,
        )
    }
}

@Composable
internal actual fun rememberThemeExtraColors(): List<Color> {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Color(context.getColour(android.R.color.system_accent1_600)))
        } else {
            emptyList()
        }
    }
}

@Composable
internal actual fun rememberAppLanguageController(defaultTag: String): AppLanguageController {
    val initialValue = remember(defaultTag) {
        AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { defaultTag }
    }
    return remember {
        object : AppLanguageController {
            override var value: String = initialValue
                set(value) {
                    field = value
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(value),
                    )
                }
            override val flow = flowOf(initialValue)
        }
    }
}

internal actual fun LazyListScope.disableProcessText() {
    item(Key.DISABLE_PROCESS_TEXT, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.DISABLE_PROCESS_TEXT, false)
            .collectAsStateWithLifecycle(false)
        val context = LocalContext.current
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.disableProcessText = it
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(
                        context,
                        "tr.theyusa.v4war.ui.ProcessTextActivityAlias",
                    ),
                    if (it) {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    },
                    PackageManager.DONT_KILL_APP,
                )
            },
            title = { Text(stringResource(Res.string.disable_process_text)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.format_align_left),
                    null,
                )
            },
        )
    }
}

internal actual fun LazyListScope.httpProxyBypass(enabled: Boolean, needReload: () -> Unit) {
    item(Key.HTTP_PROXY_BYPASS, PreferenceType.TEXT_FIELD) {
        val value by DataStore.configurationStore
            .stringFlow(Key.HTTP_PROXY_BYPASS, DEFAULT_HTTP_BYPASS)
            .collectAsStateWithLifecycle(DEFAULT_HTTP_BYPASS)
        TextFieldPreference(
            value = value,
            onValueChange = {
                DataStore.httpProxyBypass = it
                needReload()
            },
            title = { Text(stringResource(Res.string.http_proxy_bypass)) },
            textToValue = { it },
            icon = {
                Icon(
                    vectorResource(Res.drawable.domain),
                    null,
                )
            },
            valueToText = { it },
            enabled = enabled,
        ) { value, onValueChange, onOk ->
            HostTextField(value, onValueChange, onOk)
        }
    }
}
