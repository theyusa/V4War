package tr.theyusa.v4war.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import tr.theyusa.v4war.compose.material3.Icon
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import tr.theyusa.v4war.compose.MultilineTextField
import tr.theyusa.v4war.compose.PasswordPreference
import tr.theyusa.v4war.compose.PreferenceCategory
import tr.theyusa.v4war.compose.UIntegerTextField
import tr.theyusa.v4war.ktx.contentOrUnset
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.add_road
import tr.theyusa.v4war.resources.allow_insecure
import tr.theyusa.v4war.resources.alpn
import tr.theyusa.v4war.resources.block
import tr.theyusa.v4war.resources.cert_public_key_sha256
import tr.theyusa.v4war.resources.certificates
import tr.theyusa.v4war.resources.compare_arrows
import tr.theyusa.v4war.resources.copyright
import tr.theyusa.v4war.resources.directions_boat
import tr.theyusa.v4war.resources.ech
import tr.theyusa.v4war.resources.ech_config
import tr.theyusa.v4war.resources.ech_query_server_name
import tr.theyusa.v4war.resources.emoji_symbols
import tr.theyusa.v4war.resources.enable
import tr.theyusa.v4war.resources.flight_takeoff
import tr.theyusa.v4war.resources.lock
import tr.theyusa.v4war.resources.lock_open
import tr.theyusa.v4war.resources.mutual_tls
import tr.theyusa.v4war.resources.nfc
import tr.theyusa.v4war.resources.person
import tr.theyusa.v4war.resources.profile_config
import tr.theyusa.v4war.resources.profile_name
import tr.theyusa.v4war.resources.proxy_cat
import tr.theyusa.v4war.resources.router
import tr.theyusa.v4war.resources.search
import tr.theyusa.v4war.resources.security
import tr.theyusa.v4war.resources.server_address
import tr.theyusa.v4war.resources.server_port
import tr.theyusa.v4war.resources.sni
import tr.theyusa.v4war.resources.ssh_private_key
import tr.theyusa.v4war.resources.toc
import tr.theyusa.v4war.resources.tuic_congestion_controller
import tr.theyusa.v4war.resources.tuic_disable_sni
import tr.theyusa.v4war.resources.tuic_reduce_rtt
import tr.theyusa.v4war.resources.tuic_udp_relay_mode
import tr.theyusa.v4war.resources.uuid
import tr.theyusa.v4war.resources.vpn_key
import tr.theyusa.v4war.resources.wb_sunny
import tr.theyusa.v4war.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuicSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: TuicSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        TuicSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        tuicSettings(uiState as TuicUiState, viewModel)
    }
}


private fun LazyListScope.tuicSettings(
    uiState: TuicUiState,
    viewModel: TuicSettingsViewModel,
) {
    item("name") {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.emoji_symbols), null) },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
    }

    item("category_proxy") {
        PreferenceCategory(text = { Text(stringResource(Res.string.proxy_cat)) })
    }
    item("address") {
        TextFieldPreference(
            value = uiState.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.router), null) },
            summary = { Text(contentOrUnset(uiState.address)) },
            valueToText = { it },
        )
    }
    item("port") {
        TextFieldPreference(
            value = uiState.port,
            onValueChange = { viewModel.setPort(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 443 },
            icon = { Icon(vectorResource(Res.drawable.directions_boat), null) },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
    item("uuid") {
        TextFieldPreference(
            value = uiState.uuid,
            onValueChange = { viewModel.setUuid(it) },
            title = { Text(stringResource(Res.string.uuid)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.person), null) },
            summary = { Text(contentOrUnset(uiState.uuid)) },
            valueToText = { it },
        )
    }
    item("token") {
        PasswordPreference(
            value = uiState.token,
            onValueChange = { viewModel.setToken(it) },
        )
    }
    item("alpn") {
        TextFieldPreference(
            value = uiState.alpn,
            onValueChange = { viewModel.setAlpn(it) },
            title = { Text(stringResource(Res.string.alpn)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.toc), null) },
            summary = { Text(contentOrUnset(uiState.alpn)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
    item("certificates") {
        TextFieldPreference(
            value = uiState.certificates,
            onValueChange = { viewModel.setCertificates(it) },
            title = { Text(stringResource(Res.string.certificates)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.vpn_key), null) },
            summary = { Text(contentOrUnset(uiState.certificates)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
    item("cert_public_key_sha256") {
        TextFieldPreference(
            value = uiState.certPublicKeySha256,
            onValueChange = { viewModel.setCertPublicKeySha256(it) },
            title = { Text(stringResource(Res.string.cert_public_key_sha256)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.wb_sunny), null) },
            summary = { Text(contentOrUnset(uiState.certPublicKeySha256)) },
            valueToText = { it },
        )
    }
    item("udp_relay_mode") {
        ListPreference(
            value = uiState.udpRelayMode,
            values = listOf("native", "quic", "UDP over Stream"),
            onValueChange = { viewModel.setUdpRelayMode(it) },
            title = { Text(stringResource(Res.string.tuic_udp_relay_mode)) },
            icon = { Icon(vectorResource(Res.drawable.add_road), null) },
            summary = { Text(contentOrUnset(uiState.udpRelayMode)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }
    item("congestion_controller") {
        ListPreference(
            value = uiState.congestionController,
            values = congestionControls,
            onValueChange = { viewModel.setCongestionController(it) },
            title = { Text(stringResource(Res.string.tuic_congestion_controller)) },
            icon = { Icon(vectorResource(Res.drawable.compare_arrows), null) },
            summary = { Text(contentOrUnset(uiState.congestionController)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }
    item("disable_sni") {
        SwitchPreference(
            value = uiState.disableSNI,
            onValueChange = { viewModel.setDisableSNI(it) },
            title = { Text(stringResource(Res.string.tuic_disable_sni)) },
            icon = { Icon(vectorResource(Res.drawable.block), null) },
        )
    }
    item("sni") {
        TextFieldPreference(
            value = uiState.sni,
            onValueChange = { viewModel.setSni(it) },
            title = { Text(stringResource(Res.string.sni)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.copyright), null) },
            summary = { Text(contentOrUnset(uiState.sni)) },
            valueToText = { it },
        )
    }
    item("zero_rtt") {
        SwitchPreference(
            value = uiState.zeroRTT,
            onValueChange = { viewModel.setZeroRTT(it) },
            title = { Text(stringResource(Res.string.tuic_reduce_rtt)) },
            icon = { Icon(vectorResource(Res.drawable.flight_takeoff), null) },
        )
    }
    item("allow_insecure") {
        SwitchPreference(
            value = rememberEffectiveAllowInsecure(uiState.allowInsecure),
            onValueChange = { viewModel.setAllowInsecure(it) },
            title = { Text(stringResource(Res.string.allow_insecure)) },
            icon = { Icon(vectorResource(Res.drawable.lock_open), null) },
        )
    }

    item("category_ech") {
        PreferenceCategory(text = { Text(stringResource(Res.string.ech)) })
    }
    item("ech") {
        SwitchPreference(
            value = uiState.ech,
            onValueChange = { viewModel.setEch(it) },
            title = { Text(stringResource(Res.string.enable)) },
            icon = { Icon(vectorResource(Res.drawable.security), null) },
        )
    }
    item("ech_config") {
        TextFieldPreference(
            value = uiState.echConfig,
            onValueChange = { viewModel.setEchConfig(it) },
            title = { Text(stringResource(Res.string.ech_config)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.nfc), null) },
            enabled = uiState.ech,
            summary = { Text(contentOrUnset(uiState.echConfig)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
    item("ech_query_server_name") {
        TextFieldPreference(
            value = uiState.echQueryServerName,
            onValueChange = { viewModel.setEchQueryServerName(it) },
            title = { Text(stringResource(Res.string.ech_query_server_name)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.search), null) },
            enabled = uiState.ech,
            summary = { Text(contentOrUnset(uiState.echQueryServerName)) },
            valueToText = { it },
        )
    }

    item("category_mtls") {
        PreferenceCategory(text = { Text(stringResource(Res.string.mutual_tls)) })
    }
    item("mtls_cert") {
        TextFieldPreference(
            value = uiState.clientCert,
            onValueChange = { viewModel.setClientCert(it) },
            title = { Text(stringResource(Res.string.certificates)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.lock), null) },
            summary = { Text(contentOrUnset(uiState.clientCert)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
    item("mtls_key") {
        TextFieldPreference(
            value = uiState.clientKey,
            onValueChange = { viewModel.setClientKey(it) },
            title = { Text(stringResource(Res.string.ssh_private_key)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.vpn_key), null) },
            summary = { Text(contentOrUnset(uiState.clientKey)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
}
