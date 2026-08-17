package tr.theyusa.v4war.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.CONNECTION_TEST_URL
import tr.theyusa.v4war.CertProvider
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.compose.IconMaskColors
import tr.theyusa.v4war.compose.IconMaskShapes
import tr.theyusa.v4war.compose.LinkOrContentTextField
import tr.theyusa.v4war.compose.MaskedIcon
import tr.theyusa.v4war.compose.PreferenceDivider
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.ktx.contentOrUnset
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.allow_insecure_on_request
import tr.theyusa.v4war.resources.allow_insecure_on_request_sum
import tr.theyusa.v4war.resources.apps
import tr.theyusa.v4war.resources.cast_connected
import tr.theyusa.v4war.resources.cert_chrome
import tr.theyusa.v4war.resources.certificate_authority
import tr.theyusa.v4war.resources.connection_test_url
import tr.theyusa.v4war.resources.fast_forward
import tr.theyusa.v4war.resources.flip_camera_android
import tr.theyusa.v4war.resources.follow_system
import tr.theyusa.v4war.resources.mozilla
import tr.theyusa.v4war.resources.network_change_reset_connections
import tr.theyusa.v4war.resources.network_change_reset_connections_sum
import tr.theyusa.v4war.resources.push_pin
import tr.theyusa.v4war.resources.security
import tr.theyusa.v4war.resources.system_and_user
import tr.theyusa.v4war.resources.test_concurrency
import tr.theyusa.v4war.resources.test_timeout
import tr.theyusa.v4war.resources.traffic
import tr.theyusa.v4war.resources.wake_reset_connections
import tr.theyusa.v4war.resources.wake_reset_connections_sum
import tr.theyusa.v4war.ui.DisableProcessTextPreference
import tr.theyusa.v4war.ui.PlatformMiscOptions
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MiscSettingsGroup(
    needReload: () -> Unit,
    needRestart: () -> Unit,
) {
    val connectionTestUrlValue by DataStore.configurationStore
        .stringFlow(Key.CONNECTION_TEST_URL, CONNECTION_TEST_URL)
        .collectAsStateWithLifecycle(CONNECTION_TEST_URL)
    TextFieldPreference(
        value = connectionTestUrlValue,
        onValueChange = { DataStore.connectionTestURL = it },
        title = { Text(stringResource(Res.string.connection_test_url)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.cast_connected,
                color = IconMaskColors.IconCyan,
            )
        },
        summary = { Text(contentOrUnset(connectionTestUrlValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        LinkOrContentTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val connectionTestConcurrentValue by DataStore.configurationStore
        .intFlow(Key.CONNECTION_TEST_CONCURRENT, 5)
        .collectAsStateWithLifecycle(5)
    var concurrentPreview by remember { mutableFloatStateOf(connectionTestConcurrentValue.toFloat()) }
    SliderPreference(
        value = connectionTestConcurrentValue.toFloat(),
        onValueChange = { DataStore.connectionTestConcurrent = it.toInt() },
        sliderValue = concurrentPreview,
        onSliderValueChange = { concurrentPreview = it },
        title = { Text(stringResource(Res.string.test_concurrency)) },
        valueRange = 1f..32f,
        valueSteps = 32,
        icon = {
            MaskedIcon(
                Res.drawable.fast_forward,
                color = IconMaskColors.IconLightGreen,
            )
        },
        valueText = { Text(concurrentPreview.toInt().toString()) },
    )
    PreferenceDivider()

    val connectionTestTimeoutValue by DataStore.configurationStore
        .intFlow(Key.CONNECTION_TEST_TIMEOUT, 3000)
        .collectAsStateWithLifecycle(3000)
    var timeoutPreview by remember { mutableFloatStateOf(connectionTestTimeoutValue.toFloat()) }
    SliderPreference(
        value = connectionTestTimeoutValue.toFloat(),
        onValueChange = { DataStore.connectionTestTimeout = it.toInt() },
        sliderValue = timeoutPreview,
        onSliderValueChange = { timeoutPreview = it },
        title = { Text(stringResource(Res.string.test_timeout)) },
        valueRange = 1024f..8192f,
        valueSteps = 20,
        icon = {
            MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconWarmGray)
        },
        valueText = { Text(timeoutPreview.toInt().toString()) },
    )
    PreferenceDivider()

    PlatformMiscOptions(needReload)
    PreferenceDivider()

    val certProviderValue by DataStore.configurationStore
        .intFlow(Key.CERT_PROVIDER, CertProvider.MOZILLA)
        .collectAsStateWithLifecycle(CertProvider.MOZILLA)

    fun certProviderTextRes(index: Int): StringResource = when (index) {
        CertProvider.SYSTEM -> Res.string.follow_system
        CertProvider.MOZILLA -> Res.string.mozilla
        CertProvider.SYSTEM_AND_USER -> Res.string.system_and_user
        CertProvider.CHROME -> Res.string.cert_chrome
        else -> Res.string.mozilla
    }
    ListPreference(
        value = certProviderValue,
        onValueChange = {
            DataStore.certProvider = it
            needRestart()
        },
        values = listOf(
            CertProvider.SYSTEM,
            CertProvider.MOZILLA,
            CertProvider.SYSTEM_AND_USER,
            CertProvider.CHROME,
        ),
        title = { Text(stringResource(Res.string.certificate_authority)) },
        icon = {
            MaskedIcon(
                Res.drawable.push_pin,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.credential(),
            )
        },
        summary = { Text(stringResource(certProviderTextRes(certProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(certProviderTextRes(it))) },
    )
    PreferenceDivider()

    val allowInsecureOnRequestValue by DataStore.configurationStore
        .booleanFlow(Key.ALLOW_INSECURE_ON_REQUEST, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = allowInsecureOnRequestValue,
        onValueChange = { DataStore.allowInsecureOnRequest = it },
        title = { Text(stringResource(Res.string.allow_insecure_on_request)) },
        icon = {
            MaskedIcon(
                Res.drawable.security,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.risk(),
            )
        },
        summary = { Text(stringResource(Res.string.allow_insecure_on_request_sum)) },
    )
    PreferenceDivider()

    val networkChangeResetValue by DataStore.configurationStore
        .booleanFlow(Key.NETWORK_CHANGE_RESET_CONNECTIONS, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = networkChangeResetValue,
        onValueChange = { DataStore.networkChangeResetConnections = it },
        title = { Text(stringResource(Res.string.network_change_reset_connections)) },
        icon = { MaskedIcon(Res.drawable.traffic, color = IconMaskColors.IconLightGreen) },
        summary = { Text(stringResource(Res.string.network_change_reset_connections_sum)) },
    )
    PreferenceDivider()

    val wakeResetValue by DataStore.configurationStore
        .booleanFlow(Key.WAKE_RESET_CONNECTIONS, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = wakeResetValue,
        onValueChange = { DataStore.wakeResetConnections = it },
        title = { Text(stringResource(Res.string.wake_reset_connections)) },
        icon = { MaskedIcon(Res.drawable.flip_camera_android, color = IconMaskColors.IconLightBlue) },
        summary = { Text(stringResource(Res.string.wake_reset_connections_sum)) },
    )
    PreferenceDivider()

    DisableProcessTextPreference()
}
