package tr.theyusa.v4war.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.compose.DurationTextField
import tr.theyusa.v4war.compose.IconMaskColors
import tr.theyusa.v4war.compose.MaskedIcon
import tr.theyusa.v4war.compose.PortTextField
import tr.theyusa.v4war.compose.PreferenceDivider
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.ktx.contentOrUnset
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.directions_boat
import tr.theyusa.v4war.resources.enable_ntp
import tr.theyusa.v4war.resources.flip_camera_android
import tr.theyusa.v4war.resources.ntp_server_address
import tr.theyusa.v4war.resources.ntp_server_port
import tr.theyusa.v4war.resources.ntp_sum
import tr.theyusa.v4war.resources.ntp_sync_interval
import tr.theyusa.v4war.resources.router
import tr.theyusa.v4war.resources.timelapse
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NtpSettingsGroup(
    needReload: () -> Unit,
) {
    val enableNtpValue by DataStore.configurationStore
        .booleanFlow(Key.ENABLE_NTP, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = enableNtpValue,
        onValueChange = {
            DataStore.ntpEnable = it
            needReload()
        },
        title = { Text(stringResource(Res.string.enable_ntp)) },
        icon = {
            MaskedIcon(
                Res.drawable.timelapse,
                color = IconMaskColors.IconLightPink,
            )
        },
        summary = { Text(stringResource(Res.string.ntp_sum)) },
    )
    PreferenceDivider()

    val ntpServerValue by DataStore.configurationStore
        .stringFlow(Key.NTP_SERVER, "time.apple.com")
        .collectAsStateWithLifecycle("time.apple.com")
    TextFieldPreference(
        value = ntpServerValue,
        onValueChange = {
            DataStore.ntpAddress = it
            needReload()
        },
        title = { Text(stringResource(Res.string.ntp_server_address)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.router, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(contentOrUnset(ntpServerValue)) },
        valueToText = { it },
        enabled = enableNtpValue,
    )
    PreferenceDivider()

    val ntpPortValue by DataStore.configurationStore
        .intFlow(Key.NTP_PORT, 123)
        .collectAsStateWithLifecycle(123)
    TextFieldPreference(
        value = ntpPortValue,
        onValueChange = {
            DataStore.ntpPort = it
            needReload()
        },
        title = { Text(stringResource(Res.string.ntp_server_port)) },
        textToValue = { it.toIntOrNull() ?: 123 },
        icon = {
            MaskedIcon(
                Res.drawable.directions_boat,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(ntpPortValue.toString()) },
        valueToText = { it.toString() },
        enabled = enableNtpValue,
    ) { value, onValueChange, onOk ->
        PortTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val ntpIntervalValue by DataStore.configurationStore
        .stringFlow(Key.NTP_INTERVAL, "30m")
        .collectAsStateWithLifecycle("30m")
    TextFieldPreference(
        value = ntpIntervalValue,
        onValueChange = {
            DataStore.ntpInterval = it
            needReload()
        },
        title = { Text(stringResource(Res.string.ntp_sync_interval)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.flip_camera_android,
                color = IconMaskColors.IconCyan,
            )
        },
        summary = { Text(contentOrUnset(ntpIntervalValue)) },
        valueToText = { it },
        enabled = enableNtpValue,
    ) { value, onValueChange, onOk ->
        DurationTextField(value, onValueChange, onOk)
    }
}
