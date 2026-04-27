package tr.theyusa.v4war.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import tr.theyusa.v4war.compose.material3.Icon
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import tr.theyusa.v4war.compose.MultilineTextField
import tr.theyusa.v4war.ktx.contentOrUnset
import tr.theyusa.v4war.ktx.intListN
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.encrypted
import tr.theyusa.v4war.resources.encryption
import tr.theyusa.v4war.resources.not_set
import tr.theyusa.v4war.resources.outbox
import tr.theyusa.v4war.resources.packet_encoding
import tr.theyusa.v4war.resources.person
import tr.theyusa.v4war.resources.profile_config
import tr.theyusa.v4war.resources.stream
import tr.theyusa.v4war.resources.uuid
import tr.theyusa.v4war.resources.xtls_flow
import tr.theyusa.v4war.ui.NavRoutes
import tr.theyusa.v4war.ui.StringOrRes
import tr.theyusa.v4war.ui.stringOrRes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VLESSSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: VLESSSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        VLESSSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, scrollTo ->
        vlessSettings(uiState as VLESSUiState, viewModel, scrollTo)
    }
}


private fun LazyListScope.vlessSettings(
    uiState: VLESSUiState,
    viewModel: VLESSSettingsViewModel,
    scrollTo: (String) -> Unit,
) {
    headSettings(uiState, viewModel)
    item("uuid") {
        TextFieldPreference(
            value = uiState.uuid,
            onValueChange = { viewModel.setUUID(it) },
            title = { Text(stringResource(Res.string.uuid)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.person), null) },
            summary = { Text(contentOrUnset(uiState.uuid)) },
            valueToText = { it },
        )
    }
    item("flow") {
        ListPreference(
            value = uiState.flow,
            onValueChange = { viewModel.setFlow(it) },
            values = listOf("", "xtls-rprx-vision"),
            title = { Text(stringResource(Res.string.xtls_flow)) },
            icon = { Icon(vectorResource(Res.drawable.stream), null) },
            summary = { Text(contentOrUnset(uiState.flow)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }
    item("encryption") {
        TextFieldPreference(
            value = uiState.encryption,
            onValueChange = { viewModel.setEncryption(it) },
            title = { Text(stringResource(Res.string.encryption)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.encrypted), null) },
            summary = { Text(contentOrUnset(uiState.encryption)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
    item("packet_encoding") {
        fun packetEncodingName(packetEncoding: Int): StringOrRes = when (packetEncoding) {
            0 -> StringOrRes.Res(Res.string.not_set)
            1 -> StringOrRes.Direct("packetaddr")
            2 -> StringOrRes.Direct("XUDP")
            else -> error("impossible")
        }
        ListPreference(
            value = uiState.packetEncoding,
            onValueChange = { viewModel.setPacketEncoding(it) },
            values = intListN(3),
            title = { Text(stringResource(Res.string.packet_encoding)) },
            icon = { Icon(vectorResource(Res.drawable.outbox), null) },
            summary = { Text(stringOrRes(packetEncodingName(uiState.packetEncoding))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(stringOrRes(packetEncodingName(it))) },
        )
    }

    transportSettings(uiState, viewModel)
    muxSettings(uiState, viewModel)
    tlsSettings(uiState, viewModel, scrollTo)
}
