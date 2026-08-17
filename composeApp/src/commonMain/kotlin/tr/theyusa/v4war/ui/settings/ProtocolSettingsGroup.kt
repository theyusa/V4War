package tr.theyusa.v4war.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.ProtocolProvider
import tr.theyusa.v4war.compose.IconMaskColors
import tr.theyusa.v4war.compose.MaskedIcon
import tr.theyusa.v4war.compose.PreferenceDivider
import tr.theyusa.v4war.compose.UIntegerTextField
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.download
import tr.theyusa.v4war.resources.file_upload
import tr.theyusa.v4war.resources.flight_takeoff
import tr.theyusa.v4war.resources.hysteria2_provider
import tr.theyusa.v4war.resources.hysteria_download_mbps
import tr.theyusa.v4war.resources.hysteria_upload_mbps
import tr.theyusa.v4war.resources.plugin
import tr.theyusa.v4war.ui.StringOrRes
import tr.theyusa.v4war.ui.stringOrRes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProtocolSettingsGroup(
    needReload: () -> Unit,
) {
    val uploadSpeedValue by DataStore.configurationStore
        .intFlow(Key.UPLOAD_SPEED, 0)
        .collectAsStateWithLifecycle(0)
    TextFieldPreference(
        value = uploadSpeedValue,
        onValueChange = {
            DataStore.uploadSpeed = it
            needReload()
        },
        title = { Text(stringResource(Res.string.hysteria_upload_mbps)) },
        textToValue = { it.toIntOrNull() ?: 0 },
        icon = {
            MaskedIcon(
                Res.drawable.file_upload,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(uploadSpeedValue.toString()) },
        valueToText = { it.toString() },
    ) { value, onValueChange, onOk ->
        UIntegerTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val downloadSpeedValue by DataStore.configurationStore
        .intFlow(Key.DOWNLOAD_SPEED, 0)
        .collectAsStateWithLifecycle(0)
    TextFieldPreference(
        value = downloadSpeedValue,
        onValueChange = {
            DataStore.downloadSpeed = it
            needReload()
        },
        title = { Text(stringResource(Res.string.hysteria_download_mbps)) },
        textToValue = { it.toIntOrNull() ?: 0 },
        icon = {
            MaskedIcon(Res.drawable.download, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(downloadSpeedValue.toString()) },
        valueToText = { it.toString() },
    ) { value, onValueChange, onOk ->
        UIntegerTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    fun pluginProviderText(index: Int): StringOrRes = when (index) {
        ProtocolProvider.CORE -> StringOrRes.Direct("sing-box")
        ProtocolProvider.PLUGIN -> StringOrRes.Res(Res.string.plugin)
        else -> StringOrRes.Direct("sing-box")
    }

    val hysteria2ProviderValue by DataStore.configurationStore
        .intFlow(Key.PROVIDER_HYSTERIA2, ProtocolProvider.CORE)
        .collectAsStateWithLifecycle(ProtocolProvider.CORE)
    ListPreference(
        value = hysteria2ProviderValue,
        onValueChange = {
            DataStore.providerHysteria2 = it
            needReload()
        },
        values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
        title = { Text(stringResource(Res.string.hysteria2_provider)) },
        icon = {
            MaskedIcon(
                Res.drawable.flight_takeoff,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(pluginProviderText(hysteria2ProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringOrRes(pluginProviderText(it))) },
    )
}
