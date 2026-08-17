package tr.theyusa.v4war.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import tr.theyusa.v4war.compose.IconMaskColors
import tr.theyusa.v4war.compose.IconMaskShapes
import tr.theyusa.v4war.compose.MaskedIcon
import tr.theyusa.v4war.compose.MultilineTextField
import tr.theyusa.v4war.compose.PasswordPreference
import tr.theyusa.v4war.compose.PreferenceCategory
import tr.theyusa.v4war.compose.PreferenceDivider
import tr.theyusa.v4war.compose.UIntegerTextField
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.compose.preferenceGroup
import tr.theyusa.v4war.ktx.contentOrUnset
import tr.theyusa.v4war.ktx.intListN
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.bolt
import tr.theyusa.v4war.resources.border_inner
import tr.theyusa.v4war.resources.build
import tr.theyusa.v4war.resources.directions_boat
import tr.theyusa.v4war.resources.emoji_symbols
import tr.theyusa.v4war.resources.enable_brutal
import tr.theyusa.v4war.resources.enable_mux
import tr.theyusa.v4war.resources.enc_method
import tr.theyusa.v4war.resources.enhanced_encryption
import tr.theyusa.v4war.resources.experimental_settings
import tr.theyusa.v4war.resources.multiple_stop
import tr.theyusa.v4war.resources.mux_number
import tr.theyusa.v4war.resources.mux_preference
import tr.theyusa.v4war.resources.mux_strategy
import tr.theyusa.v4war.resources.mux_sum
import tr.theyusa.v4war.resources.mux_type
import tr.theyusa.v4war.resources.numbers
import tr.theyusa.v4war.resources.padding
import tr.theyusa.v4war.resources.plugin
import tr.theyusa.v4war.resources.plugin_configure
import tr.theyusa.v4war.resources.profile_config
import tr.theyusa.v4war.resources.profile_name
import tr.theyusa.v4war.resources.proxy_cat
import tr.theyusa.v4war.resources.router
import tr.theyusa.v4war.resources.server_address
import tr.theyusa.v4war.resources.server_port
import tr.theyusa.v4war.resources.settings
import tr.theyusa.v4war.resources.type_specimen
import tr.theyusa.v4war.resources.udp_over_tcp
import tr.theyusa.v4war.resources.view_in_ar
import tr.theyusa.v4war.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowsocksSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: ShadowsocksSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        ShadowsocksSettingsViewModel()
    }


    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, scrollTo ->
        shadowsocksSettings(
            uiState as ShadowsocksUiState,
            viewModel,
            scrollTo,
        )
    }
}

private fun LazyListScope.shadowsocksSettings(
    uiState: ShadowsocksUiState,
    viewModel: ShadowsocksSettingsViewModel,
    scrollTo: (key: String) -> Unit,
) {
    val encryptionMethods = listOf(
        "2022-blake3-aes-128-gcm",
        "2022-blake3-aes-256-gcm",
        "2022-blake3-chacha20-poly1305",
        "none",
        "aes-128-gcm",
        "aes-192-gcm",
        "aes-256-gcm",
        "chacha20-ietf-poly1305",
        "xchacha20-ietf-poly1305",
        "aes-128-ctr",
        "aes-192-ctr",
        "aes-256-ctr",
        "aes-128-cfb",
        "aes-192-cfb",
        "aes-256-cfb",
        "rc4-md5",
        "chacha20-ietf",
        "xchacha20",
    )
    val keyEnableMux = "enable_mux"

    preferenceGroup(key = "name") {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.emoji_symbols,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
    }

    item("category_proxy") {
        PreferenceCategory(text = { Text(stringResource(Res.string.proxy_cat)) })
    }
    preferenceGroup(key = "address") {
        TextFieldPreference(
            value = uiState.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.router, color = IconMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.address)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.port,
            onValueChange = { viewModel.setPort(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 8388 },
            icon = {
                MaskedIcon(
                    Res.drawable.directions_boat,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        ListPreference(
            value = uiState.method,
            values = encryptionMethods,
            onValueChange = { viewModel.setMethod(it) },
            title = { Text(stringResource(Res.string.enc_method)) },
            icon = {
                MaskedIcon(
                    Res.drawable.enhanced_encryption,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.method)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        PreferenceDivider()
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
        )
    }

    item("category_mux") {
        PreferenceCategory(text = { Text(stringResource(Res.string.mux_preference)) })
    }
    preferenceGroup(key = keyEnableMux) {
        SwitchPreference(
            value = uiState.enableMux,
            onValueChange = {
                viewModel.setEnableMux(it)
                if (it) {
                    scrollTo(keyEnableMux)
                }
            },
            title = { Text(stringResource(Res.string.enable_mux)) },
            summary = { Text(stringResource(Res.string.mux_sum)) },
            icon = {
                MaskedIcon(
                    Res.drawable.multiple_stop,
                    color = IconMaskColors.IconLightPink,
                )
            },
        )
        AnimatedVisibility(visible = uiState.enableMux) {
            Column {
                PreferenceDivider()
                SwitchPreference(
                    value = uiState.brutal,
                    onValueChange = { viewModel.setBrutal(it) },
                    title = { Text(stringResource(Res.string.enable_brutal)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.bolt,
                            color = IconMaskColors.IconCoral,
                            shape = IconMaskShapes.risk(),
                        )
                    },
                )
                PreferenceDivider()
                ListPreference(
                    value = uiState.muxType,
                    values = intListN(muxTypes.size),
                    onValueChange = { viewModel.setMuxType(it) },
                    title = { Text(stringResource(Res.string.mux_type)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.type_specimen,
                            color = IconMaskColors.IconLightGreen,
                        )
                    },
                    summary = { Text(muxTypes[uiState.muxType]) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(muxTypes[it]) },
                )
                PreferenceDivider()
                ListPreference(
                    value = uiState.muxStrategy,
                    values = intListN(muxStrategies.size),
                    onValueChange = { viewModel.setMuxStrategy(it) },
                    title = { Text(stringResource(Res.string.mux_strategy)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.view_in_ar,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
                    summary = { Text(stringResource(muxStrategies[uiState.muxStrategy])) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(stringResource(muxStrategies[it])) },
                    enabled = !uiState.brutal,
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = uiState.muxNumber,
                    onValueChange = { viewModel.setMuxNumber(it) },
                    title = { Text(stringResource(Res.string.mux_number)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.numbers,
                            color = IconMaskColors.IconLightYellow,
                            shape = IconMaskShapes.route(),
                        )
                    },
                    summary = { Text(uiState.muxNumber.toString()) },
                    valueToText = { it.toString() },
                    enabled = !uiState.brutal,
                )
                PreferenceDivider()
                SwitchPreference(
                    value = uiState.muxPadding,
                    onValueChange = { viewModel.setMuxPadding(it) },
                    title = { Text(stringResource(Res.string.padding)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.border_inner,
                            color = IconMaskColors.IconLightBlue,
                        )
                    },
                )
            }
        }
    }

    item("category_plugin") {
        PreferenceCategory(text = { Text(stringResource(Res.string.plugin)) })
    }
    preferenceGroup(key = "plugin_name") {
        ListPreference(
            value = uiState.pluginName,
            values = listOf("", "obfs-local", "v2ray-plugin"),
            onValueChange = { viewModel.setPluginName(it) },
            title = { Text(stringResource(Res.string.plugin)) },
            icon = {
                MaskedIcon(Res.drawable.build, IconMaskColors.IconLightBlue)
            },
            summary = { Text(contentOrUnset(uiState.pluginName)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.pluginConfig,
            onValueChange = { viewModel.setPluginConfig(it) },
            title = { Text(stringResource(Res.string.plugin_configure)) },
            textToValue = { it },
            enabled = uiState.pluginName.isNotBlank(),
            icon = {
                MaskedIcon(Res.drawable.settings, IconMaskColors.IconLightYellow)
            },
            summary = { Text(contentOrUnset(uiState.pluginConfig)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }

    item("category_experimental") {
        PreferenceCategory(
            text = { Text(stringResource(Res.string.experimental_settings)) },
        )
    }
    preferenceGroup(key = "udp_over_tcp") {
        SwitchPreference(
            value = uiState.udpOverTcp,
            onValueChange = { viewModel.setUdpOverTcp(it) },
            title = { Text(stringResource(Res.string.udp_over_tcp)) },
            enabled = !uiState.enableMux,
            icon = { Spacer(Modifier.size(24.dp)) },
        )
    }
}
