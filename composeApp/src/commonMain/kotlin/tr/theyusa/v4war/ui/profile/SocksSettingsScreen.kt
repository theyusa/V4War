package tr.theyusa.v4war.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import tr.theyusa.v4war.compose.IconMaskColors
import tr.theyusa.v4war.compose.MaskedIcon
import tr.theyusa.v4war.compose.PasswordPreference
import tr.theyusa.v4war.compose.PreferenceCategory
import tr.theyusa.v4war.compose.PreferenceDivider
import tr.theyusa.v4war.compose.UIntegerTextField
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.compose.preferenceGroup
import tr.theyusa.v4war.fmt.socks.SOCKSBean
import tr.theyusa.v4war.ktx.contentOrUnset
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.directions_boat
import tr.theyusa.v4war.resources.emoji_symbols
import tr.theyusa.v4war.resources.experimental_settings
import tr.theyusa.v4war.resources.grid_on
import tr.theyusa.v4war.resources.nfc
import tr.theyusa.v4war.resources.password_opt
import tr.theyusa.v4war.resources.person
import tr.theyusa.v4war.resources.profile_config
import tr.theyusa.v4war.resources.profile_name
import tr.theyusa.v4war.resources.protocol_version
import tr.theyusa.v4war.resources.proxy_cat
import tr.theyusa.v4war.resources.router
import tr.theyusa.v4war.resources.server_address
import tr.theyusa.v4war.resources.server_port
import tr.theyusa.v4war.resources.udp_over_tcp
import tr.theyusa.v4war.resources.username_opt
import tr.theyusa.v4war.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocksSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: SocksSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        SocksSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        socksSettings(uiState as SocksUiState, viewModel)
    }
}

private fun LazyListScope.socksSettings(
    uiState: SocksUiState,
    viewModel: SocksSettingsViewModel,
) {
    preferenceGroup {
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
    val showAuth = uiState.protocol == SOCKSBean.PROTOCOL_SOCKS5
    preferenceGroup {
        ListPreference(
            value = uiState.protocol,
            values = listOf(
                SOCKSBean.PROTOCOL_SOCKS4,
                SOCKSBean.PROTOCOL_SOCKS4A,
                SOCKSBean.PROTOCOL_SOCKS5,
            ),
            onValueChange = { viewModel.setProtocol(it) },
            title = { Text(stringResource(Res.string.protocol_version)) },
            icon = {
                MaskedIcon(Res.drawable.nfc, color = IconMaskColors.IconLightBlue)
            },
            summary = {
                val text = when (uiState.protocol) {
                    SOCKSBean.PROTOCOL_SOCKS4 -> "SOCKS4"
                    SOCKSBean.PROTOCOL_SOCKS4A -> "SOCKS4A"
                    else -> "SOCKS5"
                }
                Text(text)
            },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                AnnotatedString(
                    when (it) {
                        SOCKSBean.PROTOCOL_SOCKS4 -> "SOCKS4"
                        SOCKSBean.PROTOCOL_SOCKS4A -> "SOCKS4A"
                        else -> "SOCKS5"
                    },
                )
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.router,
                    color = IconMaskColors.IconLightOrange,
                )
            },
            summary = { Text(contentOrUnset(uiState.address)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.port,
            onValueChange = { viewModel.setPort(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 1080 },
            icon = {
                MaskedIcon(
                    Res.drawable.directions_boat,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        if (showAuth) {
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.username,
                onValueChange = { viewModel.setUsername(it) },
                title = { Text(stringResource(Res.string.username_opt)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.person,
                        color = IconMaskColors.IconCyan,
                    )
                },
                summary = { Text(contentOrUnset(uiState.username)) },
                valueToText = { it },
            )
            PreferenceDivider()
            PasswordPreference(
                value = uiState.password,
                onValueChange = { viewModel.setPassword(it) },
                title = { Text(stringResource(Res.string.password_opt)) },
            )
        }
    }

    item("category_experimental") {
        PreferenceCategory(
            text = { Text(stringResource(Res.string.experimental_settings)) },
        )
    }
    preferenceGroup {
        SwitchPreference(
            value = uiState.udpOverTcp,
            onValueChange = { viewModel.setUdpOverTcp(it) },
            title = { Text(stringResource(Res.string.udp_over_tcp)) },
            icon = {
                MaskedIcon(Res.drawable.grid_on, color = IconMaskColors.IconCoral)
            },
        )
    }
}
