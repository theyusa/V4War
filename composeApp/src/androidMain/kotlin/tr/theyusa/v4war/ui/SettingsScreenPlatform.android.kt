package tr.theyusa.v4war.ui

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.DEFAULT_HTTP_BYPASS
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.compose.HostTextField
import tr.theyusa.v4war.compose.IconMaskColors
import tr.theyusa.v4war.compose.MaskedIcon
import tr.theyusa.v4war.compose.PreferenceDivider
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.ktx.findActivity
import tr.theyusa.v4war.ktx.getColour
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.acquire_wake_lock
import tr.theyusa.v4war.resources.acquire_wake_lock_summary
import tr.theyusa.v4war.resources.allow_apps_bypass_vpn
import tr.theyusa.v4war.resources.auto_connect
import tr.theyusa.v4war.resources.auto_connect_summary
import tr.theyusa.v4war.resources.battery_charging_full
import tr.theyusa.v4war.resources.battery_throttle_factor
import tr.theyusa.v4war.resources.battery_throttle_factor_summary
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
import tr.theyusa.v4war.resources.smart_wake_lock
import tr.theyusa.v4war.resources.smart_wake_lock_summary
import tr.theyusa.v4war.resources.transform
import kotlinx.coroutines.flow.flowOf
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun AutoConnectPreference() {
    val value by DataStore.configurationStore
        .booleanFlow(Key.PERSIST_ACROSS_REBOOT, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = value,
        onValueChange = { DataStore.persistAcrossReboot = it },
        title = { Text(stringResource(Res.string.auto_connect)) },
        icon = {
            MaskedIcon(
                Res.drawable.phonelink_ring,
                color = IconMaskColors.IconLightPink,
            )
        },
        summary = { Text(stringResource(Res.string.auto_connect_summary)) },
    )
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

@Composable
internal actual fun PlatformGeneralOptions(needReload: () -> Unit) {
    PreferenceDivider()
    val bypassValue by DataStore.configurationStore
        .booleanFlow(Key.ALLOW_APPS_BYPASS_VPN, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = bypassValue,
        onValueChange = {
            DataStore.allowAppsBypassVpn = it
            needReload()
        },
        title = { Text(stringResource(Res.string.allow_apps_bypass_vpn)) },
        icon = {
            MaskedIcon(Res.drawable.transform, color = IconMaskColors.IconCyan)
        },
    )
    PreferenceDivider()
    val showGroupValue by DataStore.configurationStore
        .booleanFlow(Key.SHOW_GROUP_IN_NOTIFICATION, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = showGroupValue,
        onValueChange = {
            DataStore.showGroupInNotification = it
            needReload()
        },
        title = { Text(stringResource(Res.string.show_group_in_notification)) },
        icon = {
            MaskedIcon(Res.drawable.label, color = IconMaskColors.IconLightPink)
        },
    )
}

@Composable
internal actual fun PlatformRouteOptions(needReload: () -> Unit, isVpnMode: Boolean) {
    PreferenceDivider()
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
            MaskedIcon(
                Res.drawable.legend_toggle,
                color = IconMaskColors.IconLightGreen,
            )
        },
    )
}

@Composable
internal actual fun PlatformSecurityOptions() {
    PreferenceDivider()
    val value by DataStore.configurationStore
        .booleanFlow(Key.PRIVACY_MODE, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = value,
        onValueChange = { DataStore.privacyMode = it },
        title = { Text(stringResource(Res.string.privacy_mode)) },
        icon = {
            MaskedIcon(Res.drawable.privacy, color = IconMaskColors.IconCoral)
        },
        summary = { Text(stringResource(Res.string.privacy_mode_summary)) },
    )
}

@Composable
internal actual fun MeteredNetworkPreference(needReload: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    PreferenceDivider()
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
            MaskedIcon(
                Res.drawable.data_usage,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(stringResource(Res.string.metered_summary)) },
    )
}

@Composable
internal actual fun HttpProxyBypassPreference(enabled: Boolean, needReload: () -> Unit) {
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
            MaskedIcon(Res.drawable.domain, color = IconMaskColors.IconCyan)
        },
        valueToText = { it },
        enabled = enabled,
    ) { value, onValueChange, onOk ->
        HostTextField(value, onValueChange, onOk)
    }
}

@Composable
internal actual fun PlatformMiscOptions(needReload: () -> Unit) {
    PreferenceDivider()
    val wakeLockValue by DataStore.configurationStore
        .booleanFlow(Key.ACQUIRE_WAKE_LOCK, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = wakeLockValue,
        onValueChange = {
            DataStore.acquireWakeLock = it
            needReload()
        },
        title = { Text(stringResource(Res.string.acquire_wake_lock)) },
        icon = {
            MaskedIcon(
                Res.drawable.developer_board,
                color = IconMaskColors.IconLightGreen,
            )
        },
        summary = { Text(stringResource(Res.string.acquire_wake_lock_summary)) },
    )
    PreferenceDivider()
    val smartWakeLockValue by DataStore.configurationStore
        .booleanFlow(Key.SMART_WAKE_LOCK, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = smartWakeLockValue,
        onValueChange = {
            DataStore.smartWakeLock = it
            needReload()
        },
        title = { Text(stringResource(Res.string.smart_wake_lock)) },
        icon = {
            MaskedIcon(
                Res.drawable.battery_charging_full,
                color = IconMaskColors.IconLightGreen,
            )
        },
        summary = { Text(stringResource(Res.string.smart_wake_lock_summary)) },
        enabled = !wakeLockValue,
    )
    PreferenceDivider()
    val batteryThrottleFactorValue by DataStore.configurationStore
        .intFlow(Key.BATTERY_THROTTLE_FACTOR, 5)
        .collectAsStateWithLifecycle(5)
    var previewValue by remember { mutableFloatStateOf(batteryThrottleFactorValue.toFloat()) }
    SliderPreference(
        value = batteryThrottleFactorValue.toFloat(),
        onValueChange = { floatValue ->
            DataStore.batteryThrottleFactor = floatValue.toInt()
        },
        sliderValue = previewValue,
        onSliderValueChange = { previewValue = it },
        title = { Text(stringResource(Res.string.battery_throttle_factor)) },
        valueRange = 1f..10f,
        valueSteps = 9,
        icon = {
            MaskedIcon(
                Res.drawable.battery_charging_full,
                color = IconMaskColors.IconLightGreen,
            )
        },
        summary = { Text(stringResource(Res.string.battery_throttle_factor_summary)) },
        valueText = { Text(previewValue.toInt().toString()) },
    )
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

@Composable
internal actual fun DisableProcessTextPreference() {
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
            MaskedIcon(
                Res.drawable.format_align_left,
                color = IconMaskColors.IconWarmGray,
            )
        },
    )
}
