package tr.theyusa.v4war.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.text.AnnotatedString
import tr.theyusa.v4war.compose.IconMaskColors
import tr.theyusa.v4war.compose.IconMaskShapes
import tr.theyusa.v4war.compose.MaskedIcon
import tr.theyusa.v4war.compose.MultilineTextField
import tr.theyusa.v4war.compose.PortTextField
import tr.theyusa.v4war.compose.PreferenceCategory
import tr.theyusa.v4war.compose.PreferenceDivider
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.compose.preferenceGroup
import tr.theyusa.v4war.fmt.SingBoxOptions
import tr.theyusa.v4war.ktx.contentOrUnset
import tr.theyusa.v4war.ktx.intListN
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.allow_insecure
import tr.theyusa.v4war.resources.allow_insecure_sum
import tr.theyusa.v4war.resources.alpn
import tr.theyusa.v4war.resources.assistant_direction
import tr.theyusa.v4war.resources.block
import tr.theyusa.v4war.resources.bolt
import tr.theyusa.v4war.resources.border_inner
import tr.theyusa.v4war.resources.cag_ws
import tr.theyusa.v4war.resources.cert_public_key_sha256
import tr.theyusa.v4war.resources.certificates
import tr.theyusa.v4war.resources.code
import tr.theyusa.v4war.resources.compare_arrows
import tr.theyusa.v4war.resources.copyright
import tr.theyusa.v4war.resources.directions_boat
import tr.theyusa.v4war.resources.early_data_header_name
import tr.theyusa.v4war.resources.ech
import tr.theyusa.v4war.resources.ech_config
import tr.theyusa.v4war.resources.ech_query_server_name
import tr.theyusa.v4war.resources.emoji_symbols
import tr.theyusa.v4war.resources.enable
import tr.theyusa.v4war.resources.enable_brutal
import tr.theyusa.v4war.resources.enhanced_encryption
import tr.theyusa.v4war.resources.grpc_service_name
import tr.theyusa.v4war.resources.http_headers
import tr.theyusa.v4war.resources.http_host
import tr.theyusa.v4war.resources.http_path
import tr.theyusa.v4war.resources.http_upgrade_host
import tr.theyusa.v4war.resources.http_upgrade_path
import tr.theyusa.v4war.resources.language
import tr.theyusa.v4war.resources.layers
import tr.theyusa.v4war.resources.lock
import tr.theyusa.v4war.resources.multiple_stop
import tr.theyusa.v4war.resources.mutual_tls
import tr.theyusa.v4war.resources.mux_number
import tr.theyusa.v4war.resources.mux_preference
import tr.theyusa.v4war.resources.mux_strategy
import tr.theyusa.v4war.resources.mux_type
import tr.theyusa.v4war.resources.nfc
import tr.theyusa.v4war.resources.numbers
import tr.theyusa.v4war.resources.padding
import tr.theyusa.v4war.resources.profile_name
import tr.theyusa.v4war.resources.proxy_cat
import tr.theyusa.v4war.resources.reality_public_key
import tr.theyusa.v4war.resources.reality_short_id
import tr.theyusa.v4war.resources.route
import tr.theyusa.v4war.resources.router
import tr.theyusa.v4war.resources.search
import tr.theyusa.v4war.resources.security
import tr.theyusa.v4war.resources.security_settings
import tr.theyusa.v4war.resources.server_address
import tr.theyusa.v4war.resources.server_port
import tr.theyusa.v4war.resources.sni
import tr.theyusa.v4war.resources.ssh_private_key
import tr.theyusa.v4war.resources.stream
import tr.theyusa.v4war.resources.texture
import tr.theyusa.v4war.resources.timer
import tr.theyusa.v4war.resources.tls_camouflage_settings
import tr.theyusa.v4war.resources.tls_fragment
import tr.theyusa.v4war.resources.tls_fragment_fallback_delay
import tr.theyusa.v4war.resources.tls_record_fragment
import tr.theyusa.v4war.resources.toc
import tr.theyusa.v4war.resources.tuic_disable_sni
import tr.theyusa.v4war.resources.type_specimen
import tr.theyusa.v4war.resources.utls_fingerprint
import tr.theyusa.v4war.resources.v2ray_transport
import tr.theyusa.v4war.resources.view_in_ar
import tr.theyusa.v4war.resources.vpn_key
import tr.theyusa.v4war.resources.wb_sunny
import tr.theyusa.v4war.resources.ws_host
import tr.theyusa.v4war.resources.ws_max_early_data
import tr.theyusa.v4war.resources.ws_path
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

private const val KEY_SECURITY = "security"

internal fun LazyListScope.headSettings(
    state: StandardV2RayUiState,
    viewModel: StandardV2RaySettingsViewModel<*>,
) {
    item("category_basic") {
        PreferenceCategory(text = { Text(stringResource(Res.string.proxy_cat)) })
    }
    preferenceGroup {
        TextFieldPreference(
            value = state.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.emoji_symbols,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(state.name)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.router,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(state.address)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.port,
            onValueChange = { viewModel.setPort(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 443 },
            icon = {
                MaskedIcon(
                    Res.drawable.directions_boat,
                    color = IconMaskColors.IconLightOrange,
                )
            },
            summary = { Text(contentOrUnset(state.port)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk -> PortTextField(value, onValueChange, onOk) },
        )
    }
}

internal fun LazyListScope.tlsSettings(
    state: StandardV2RayUiState,
    viewModel: StandardV2RaySettingsViewModel<*>,
    scrollTo: (key: String) -> Unit,
) {
    val isTls = state.security == "tls"
    val isReality = state.realityPublicKey.isNotBlank()

    item("category_security") {
        PreferenceCategory(text = { Text(stringResource(Res.string.security_settings)) })
    }
    preferenceGroup(key = KEY_SECURITY) {
        ListPreference(
            value = state.security,
            values = listOf("", "tls"),
            onValueChange = {
                viewModel.setSecurity(it)
                if (it == "tls") {
                    scrollTo(KEY_SECURITY)
                }
            },
            title = { Text(stringResource(Res.string.security)) },
            icon = {
                MaskedIcon(
                    Res.drawable.layers,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(state.security)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }

    if (isTls) {
        preferenceGroup {
            TextFieldPreference(
                value = state.sni,
                onValueChange = { viewModel.setSni(it) },
                title = { Text(stringResource(Res.string.sni)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.copyright,
                        color = IconMaskColors.IconCyan,
                    )
                },
                summary = { Text(contentOrUnset(state.sni)) },
                valueToText = { it },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.alpn,
                onValueChange = { viewModel.setAlpn(it) },
                title = { Text(stringResource(Res.string.alpn)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.toc,
                        color = IconMaskColors.IconLightBlue,
                    )
                },
                summary = { Text(contentOrUnset(state.alpn)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.certificate,
                onValueChange = { viewModel.setCertificate(it) },
                title = { Text(stringResource(Res.string.certificates)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.vpn_key,
                        color = IconMaskColors.IconLightOrange,
                        shape = IconMaskShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(state.certificate)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.certPublicKeySha256,
                onValueChange = { viewModel.setCertPublicKeySha256(it) },
                title = { Text(stringResource(Res.string.cert_public_key_sha256)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.wb_sunny,
                        color = IconMaskColors.IconLightYellow,
                    )
                },
                summary = { Text(contentOrUnset(state.certPublicKeySha256)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            SwitchPreference(
                value = rememberEffectiveAllowInsecure(state.allowInsecure),
                onValueChange = { viewModel.setAllowInsecure(it) },
                title = { Text(stringResource(Res.string.allow_insecure)) },
                summary = { Text(stringResource(Res.string.allow_insecure_sum)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.enhanced_encryption,
                        color = IconMaskColors.IconCoral,
                        shape = IconMaskShapes.risk(),
                    )
                },
            )
            if (!isReality) {
                PreferenceDivider()
                SwitchPreference(
                    value = state.disableSNI,
                    onValueChange = { viewModel.setDisableSNI(it) },
                    title = { Text(stringResource(Res.string.tuic_disable_sni)) },
                    icon = {
                        MaskedIcon(
                            Res.drawable.block,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
                )
            }
            PreferenceDivider()
            SwitchPreference(
                value = state.tlsFragment,
                onValueChange = { viewModel.setTlsFragment(it) },
                title = { Text(stringResource(Res.string.tls_fragment)) },
                enabled = !state.tlsRecordFragment,
                icon = {
                    MaskedIcon(
                        Res.drawable.texture,
                        color = IconMaskColors.IconLightBlue,
                    )
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.tlsFragmentFallbackDelay,
                onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
                title = { Text(stringResource(Res.string.tls_fragment_fallback_delay)) },
                textToValue = { it },
                enabled = state.tlsFragment,
                icon = {
                    MaskedIcon(
                        Res.drawable.timer,
                        color = IconMaskColors.IconLightOrange,
                    )
                },
                summary = { Text(contentOrUnset(state.tlsFragmentFallbackDelay)) },
                valueToText = { it },
            )
            PreferenceDivider()
            SwitchPreference(
                value = state.tlsRecordFragment,
                onValueChange = { viewModel.setTlsRecordFragment(it) },
                title = { Text(stringResource(Res.string.tls_record_fragment)) },
                enabled = !state.tlsFragment,
                icon = {
                    MaskedIcon(
                        Res.drawable.wb_sunny,
                        color = IconMaskColors.IconLavender,
                    )
                },
            )
        }

        item("category_tls_camouflage") {
            PreferenceCategory(text = { Text(stringResource(Res.string.tls_camouflage_settings)) })
        }
        preferenceGroup {
            ListPreference(
                value = state.utlsFingerprint,
                values = fingerprints,
                onValueChange = { viewModel.setUtlsFingerprint(it) },
                title = { Text(stringResource(Res.string.utls_fingerprint)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.security,
                        color = IconMaskColors.IconCyan,
                    )
                },
                summary = { Text(contentOrUnset(state.utlsFingerprint)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.realityPublicKey,
                onValueChange = { viewModel.setRealityPublicKey(it) },
                title = { Text(stringResource(Res.string.reality_public_key)) },
                textToValue = { it },
                enabled = state.utlsFingerprint.isNotBlank(),
                icon = {
                    MaskedIcon(
                        Res.drawable.vpn_key,
                        color = IconMaskColors.IconLightBlue,
                    )
                },
                summary = { Text(contentOrUnset(state.realityPublicKey)) },
                valueToText = { it },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.realityShortID,
                onValueChange = { viewModel.setRealityShortID(it) },
                title = { Text(stringResource(Res.string.reality_short_id)) },
                textToValue = { it },
                enabled = isReality,
                icon = {
                    MaskedIcon(
                        Res.drawable.texture,
                        color = IconMaskColors.IconLightOrange,
                    )
                },
                summary = { Text(contentOrUnset(state.realityShortID)) },
                valueToText = { it },
            )
        }

        item("category_ech") { PreferenceCategory(text = { Text(stringResource(Res.string.ech)) }) }
        preferenceGroup {
            SwitchPreference(
                value = state.ech,
                onValueChange = { viewModel.setEch(it) },
                title = { Text(stringResource(Res.string.enable)) },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.security,
                        color = IconMaskColors.IconCoral,
                        shape = IconMaskShapes.risk(),
                    )
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.echConfig,
                onValueChange = { viewModel.setEchConfig(it) },
                title = { Text(stringResource(Res.string.ech_config)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.nfc,
                        color = IconMaskColors.IconLightBlue,
                        shape = IconMaskShapes.credential(),
                    )
                },
                enabled = state.ech,
                summary = { Text(contentOrUnset(state.echConfig)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.echQueryServerName,
                onValueChange = { viewModel.setEchQueryServerName(it) },
                title = { Text(stringResource(Res.string.ech_query_server_name)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.search,
                        color = IconMaskColors.IconLightOrange,
                    )
                },
                enabled = state.ech,
                summary = { Text(contentOrUnset(state.echQueryServerName)) },
                valueToText = { it },
            )
        }

        item("category_mutual_tls") {
            PreferenceCategory(text = { Text(stringResource(Res.string.mutual_tls)) })
        }
        preferenceGroup {
            TextFieldPreference(
                value = state.clientCert,
                onValueChange = { viewModel.setClientCert(it) },
                title = { Text(stringResource(Res.string.certificates)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.lock,
                        color = IconMaskColors.IconCyan,
                        shape = IconMaskShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(state.clientCert)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.clientKey,
                onValueChange = { viewModel.setClientKey(it) },
                title = { Text(stringResource(Res.string.ssh_private_key)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.vpn_key,
                        color = IconMaskColors.IconLavender,
                        shape = IconMaskShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(state.clientKey)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
    }
}

internal fun LazyListScope.muxSettings(
    state: StandardV2RayUiState,
    viewModel: StandardV2RaySettingsViewModel<*>,
) {
    item("category_mux") {
        PreferenceCategory(text = { Text(stringResource(Res.string.mux_preference)) })
    }
    preferenceGroup {
        SwitchPreference(
            value = state.enableMux,
            onValueChange = { viewModel.setEnableMux(it) },
            title = { Text(stringResource(Res.string.enable)) },
            icon = {
                MaskedIcon(
                    resource = Res.drawable.multiple_stop,
                    color = IconMaskColors.IconLightPink,
                )
            },
        )
        AnimatedVisibility(visible = state.enableMux) {
            Column {
                PreferenceDivider()
                SwitchPreference(
                    value = state.brutal,
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
                    value = state.muxType,
                    values = intListN(muxTypes.size),
                    onValueChange = { viewModel.setMuxType(it) },
                    title = { Text(stringResource(Res.string.mux_type)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.type_specimen,
                            color = IconMaskColors.IconLightGreen,
                        )
                    },
                    summary = { Text(muxTypes[state.muxType]) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(muxTypes[it]) },
                )
                PreferenceDivider()
                ListPreference(
                    value = state.muxStrategy,
                    values = intListN(muxStrategies.size),
                    onValueChange = { viewModel.setMuxStrategy(it) },
                    title = { Text(stringResource(Res.string.mux_strategy)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.view_in_ar,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
                    summary = { Text(stringResource(muxStrategies[state.muxStrategy])) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(stringResource(muxStrategies[it])) },
                    enabled = !state.brutal,
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.muxNumber,
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
                    summary = { Text(state.muxNumber.toString()) },
                    valueToText = { it.toString() },
                    enabled = !state.brutal,
                )
                PreferenceDivider()
                SwitchPreference(
                    value = state.muxPadding,
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
}

internal fun LazyListScope.transportSettings(
    state: StandardV2RayUiState,
    viewModel: StandardV2RaySettingsViewModel<*>,
) {
    item("category_transport") {
        PreferenceCategory(text = { Text(stringResource(Res.string.v2ray_transport)) })
    }
    preferenceGroup {
        ListPreference(
            value = state.v2rayTransport,
            values =
                listOf(
                    "",
                    SingBoxOptions.TRANSPORT_WS,
                    SingBoxOptions.TRANSPORT_HTTP,
                    SingBoxOptions.TRANSPORT_GRPC,
                    SingBoxOptions.TRANSPORT_HTTPUPGRADE,
                    SingBoxOptions.TRANSPORT_XHTTP,
                    SingBoxOptions.TRANSPORT_QUIC,
                ),
            onValueChange = { viewModel.setTransport(it) },
            title = { Text(stringResource(Res.string.v2ray_transport)) },
            icon = {
                MaskedIcon(
                    Res.drawable.route,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(state.v2rayTransport)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(contentOrUnset(it)) },
        )
        when (state.v2rayTransport) {
            "", "tcp" -> Unit

            SingBoxOptions.TRANSPORT_HTTP -> {
                PreferenceDivider()
                TextFieldPreference(
                    value = state.host,
                    onValueChange = { viewModel.setHost(it) },
                    title = { Text(stringResource(Res.string.http_host)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.language,
                            color = IconMaskColors.IconCyan,
                        )
                    },
                    summary = { Text(contentOrUnset(state.host)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.http_path)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.assistant_direction,
                            color = IconMaskColors.IconLightOrange,
                            shape = IconMaskShapes.route(),
                        )
                    },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.headers,
                    onValueChange = { viewModel.setHeaders(it) },
                    title = { Text(stringResource(Res.string.http_headers)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.code,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    summary = { Text(contentOrUnset(state.headers)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }

            SingBoxOptions.TRANSPORT_WS -> {
                PreferenceDivider()
                TextFieldPreference(
                    value = state.host,
                    onValueChange = { viewModel.setHost(it) },
                    title = { Text(stringResource(Res.string.ws_host)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.language,
                            color = IconMaskColors.IconCyan,
                        )
                    },
                    summary = { Text(contentOrUnset(state.host)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.ws_path)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.assistant_direction,
                            color = IconMaskColors.IconLightOrange,
                            shape = IconMaskShapes.route(),
                        )
                    },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.headers,
                    onValueChange = { viewModel.setHeaders(it) },
                    title = { Text(stringResource(Res.string.http_headers)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.code,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    summary = { Text(contentOrUnset(state.headers)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }

            SingBoxOptions.TRANSPORT_GRPC -> {
                PreferenceDivider()
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.grpc_service_name)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.assistant_direction,
                            color = IconMaskColors.IconLightGreen,
                            shape = IconMaskShapes.route(),
                        )
                    },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
            }

            SingBoxOptions.TRANSPORT_HTTPUPGRADE -> {
                PreferenceDivider()
                TextFieldPreference(
                    value = state.host,
                    onValueChange = { viewModel.setHost(it) },
                    title = { Text(stringResource(Res.string.http_upgrade_host)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.language,
                            color = IconMaskColors.IconCyan,
                            shape = IconMaskShapes.route(),
                        )
                    },
                    summary = { Text(contentOrUnset(state.host)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.http_upgrade_path)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.assistant_direction,
                            color = IconMaskColors.IconLightOrange,
                        )
                    },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.headers,
                    onValueChange = { viewModel.setHeaders(it) },
                    title = { Text(stringResource(Res.string.http_headers)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.code,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    summary = { Text(contentOrUnset(state.headers)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }

            SingBoxOptions.TRANSPORT_XHTTP -> {
                PreferenceDivider()
                TextFieldPreference(
                    value = state.host,
                    onValueChange = { viewModel.setHost(it) },
                    title = { Text(stringResource(Res.string.http_upgrade_host)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.language,
                            color = IconMaskColors.IconCyan,
                            shape = IconMaskShapes.route(),
                        )
                    },
                    summary = { Text(contentOrUnset(state.host)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.http_upgrade_path)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.assistant_direction,
                            color = IconMaskColors.IconLightOrange,
                        )
                    },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.headers,
                    onValueChange = { viewModel.setHeaders(it) },
                    title = { Text(stringResource(Res.string.http_headers)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.code,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    summary = { Text(contentOrUnset(state.headers)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }

            SingBoxOptions.TRANSPORT_QUIC -> Unit
        }
    }

    if (state.v2rayTransport == SingBoxOptions.TRANSPORT_WS) {
        item("category_ws_options") {
            PreferenceCategory(text = { Text(stringResource(Res.string.cag_ws)) })
        }
        preferenceGroup {
            TextFieldPreference(
                value = state.wsMaxEarlyData,
                onValueChange = { viewModel.setWsMaxEarlyData(it) },
                title = { Text(stringResource(Res.string.ws_max_early_data)) },
                textToValue = { it.toIntOrNull() ?: 0 },
                icon = {
                    MaskedIcon(
                        Res.drawable.compare_arrows,
                        color = IconMaskColors.IconLightGreen,
                    )
                },
                summary = { Text(contentOrUnset(state.wsMaxEarlyData)) },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.wsEarlyDataHeaderName,
                onValueChange = { viewModel.setWsEarlyDataHeaderName(it) },
                title = { Text(stringResource(Res.string.early_data_header_name)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.stream,
                        color = IconMaskColors.IconWarmGray,
                    )
                },
                summary = { Text(contentOrUnset(state.wsEarlyDataHeaderName)) },
                valueToText = { it },
            )
        }
    }
}
