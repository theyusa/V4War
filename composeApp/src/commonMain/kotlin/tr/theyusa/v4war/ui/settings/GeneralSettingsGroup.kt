package tr.theyusa.v4war.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.TunImplementation
import tr.theyusa.v4war.compose.IconMaskColors
import tr.theyusa.v4war.compose.IconMaskShapes
import tr.theyusa.v4war.compose.MaskedIcon
import tr.theyusa.v4war.compose.PreferenceDivider
import tr.theyusa.v4war.compose.TextButton
import tr.theyusa.v4war.compose.material3.Icon
import tr.theyusa.v4war.compose.material3.Surface
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.compose.theme.DEFAULT
import tr.theyusa.v4war.compose.theme.themeString
import tr.theyusa.v4war.compose.theme.themes
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.ktx.contentOrUnset
import tr.theyusa.v4war.ktx.intListN
import tr.theyusa.v4war.logLevelString
import tr.theyusa.v4war.platform.PlatformInfo
import tr.theyusa.v4war.repository.resolveRepository
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.allow_insecure
import tr.theyusa.v4war.resources.allow_insecure_sum
import tr.theyusa.v4war.resources.always_show_address
import tr.theyusa.v4war.resources.always_show_address_sum
import tr.theyusa.v4war.resources.auto
import tr.theyusa.v4war.resources.blurred_address
import tr.theyusa.v4war.resources.bug_report
import tr.theyusa.v4war.resources.cancel
import tr.theyusa.v4war.resources.center_focus_weak
import tr.theyusa.v4war.resources.check
import tr.theyusa.v4war.resources.color_lens
import tr.theyusa.v4war.resources.description
import tr.theyusa.v4war.resources.developer_board
import tr.theyusa.v4war.resources.developer_mode
import tr.theyusa.v4war.resources.disable
import tr.theyusa.v4war.resources.enable
import tr.theyusa.v4war.resources.flip_camera_android
import tr.theyusa.v4war.resources.follow_system
import tr.theyusa.v4war.resources.insecure_warn
import tr.theyusa.v4war.resources.language
import tr.theyusa.v4war.resources.language_system_default
import tr.theyusa.v4war.resources.log_level
import tr.theyusa.v4war.resources.long_click_to_see_name
import tr.theyusa.v4war.resources.max_log_line
import tr.theyusa.v4war.resources.mtu
import tr.theyusa.v4war.resources.night_mode
import tr.theyusa.v4war.resources.profile_traffic_statistics
import tr.theyusa.v4war.resources.profile_traffic_statistics_summary
import tr.theyusa.v4war.resources.public_icon
import tr.theyusa.v4war.resources.security
import tr.theyusa.v4war.resources.service_mode
import tr.theyusa.v4war.resources.service_mode_proxy
import tr.theyusa.v4war.resources.service_mode_vpn
import tr.theyusa.v4war.resources.show_direct_speed
import tr.theyusa.v4war.resources.show_direct_speed_sum
import tr.theyusa.v4war.resources.shutter_speed
import tr.theyusa.v4war.resources.speed
import tr.theyusa.v4war.resources.speed_interval
import tr.theyusa.v4war.resources.theme
import tr.theyusa.v4war.resources.traffic
import tr.theyusa.v4war.resources.transgender
import tr.theyusa.v4war.resources.translate
import tr.theyusa.v4war.resources.tun_implementation
import tr.theyusa.v4war.resources.wb_sunny
import tr.theyusa.v4war.ui.AppLanguage
import tr.theyusa.v4war.ui.AutoConnectPreference
import tr.theyusa.v4war.ui.MeteredNetworkPreference
import tr.theyusa.v4war.ui.PlatformGeneralOptions
import tr.theyusa.v4war.ui.PlatformSecurityOptions
import tr.theyusa.v4war.ui.StringOrRes
import tr.theyusa.v4war.ui.getStringOrRes
import tr.theyusa.v4war.ui.rememberAppLanguageController
import tr.theyusa.v4war.ui.rememberApplyNightMode
import tr.theyusa.v4war.ui.rememberThemeExtraColors
import tr.theyusa.v4war.ui.stringOrRes
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
private fun ColorPickerPreference(
    key: String,
    title: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    val currentTheme by DataStore.configurationStore
        .intFlow(key, DEFAULT)
        .collectAsStateWithLifecycle(DEFAULT)
    var showDialog by remember { mutableStateOf(false) }
    val extraColors = rememberThemeExtraColors()
    Preference(
        title = { title() },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        icon = {
            MaskedIcon(
                Res.drawable.color_lens,
                color = IconMaskColors.IconLightOrange,
            )
        },
        summary = { Text(stringResource(themeString(currentTheme))) },
        widgetContainer = {
            Box(modifier = Modifier.padding(end = 8.dp)) {
                Circle(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        },
        onClick = { showDialog = true },
    )

    if (showDialog) {
        val colors = themes + extraColors

        BasicAlertDialog(
            onDismissRequest = { showDialog = false },
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.theme),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (PlatformInfo.isAndroid) Text(
                        text = stringResource(Res.string.long_click_to_see_name),
                        modifier = Modifier.padding(bottom = 16.dp),
                        style = MaterialTheme.typography.labelSmallEmphasized,
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        items(
                            count = colors.size,
                            key = { index -> index },
                            contentType = { 0 },
                        ) { index ->
                            val theme = index + 1
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable {
                                        DataStore.configurationStore.putInt(key, theme)
                                        showDialog = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                        TooltipAnchorPosition.Above,
                                    ),
                                    tooltip = {
                                        PlainTooltip {
                                            Text(stringResource(themeString(theme)))
                                        }
                                    },
                                    state = rememberTooltipState(),
                                ) {
                                    Circle(
                                        modifier = Modifier.size(48.dp),
                                        color = colors[index],
                                        selected = currentTheme == theme,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(stringResource(Res.string.cancel)) {
                            showDialog = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GeneralSettingsGroup(
    needReload: () -> Unit,
    needRestart: () -> Unit,
) {
    val applyNightMode = rememberApplyNightMode()
    val isExpertState by DataStore.configurationStore
        .booleanFlow(Key.APP_EXPERT, false)
        .collectAsStateWithLifecycle(false)

    AutoConnectPreference()
    PreferenceDivider()

    ColorPickerPreference(
        key = Key.APP_THEME,
        title = { Text(stringResource(Res.string.theme)) },
    )
    PreferenceDivider()

    fun nightString(index: Int): StringResource = when (index) {
        0 -> Res.string.follow_system
        1 -> Res.string.enable
        2 -> Res.string.disable
        3 -> Res.string.auto
        else -> Res.string.follow_system
    }

    val nightValue by DataStore.configurationStore
        .intFlow(Key.NIGHT_THEME, 0)
        .collectAsStateWithLifecycle(0)
    ListPreference(
        value = nightValue,
        onValueChange = {
            DataStore.nightTheme = it
            applyNightMode(it)
        },
        values = intListN(4),
        title = { Text(stringResource(Res.string.night_mode)) },
        icon = {
            MaskedIcon(
                Res.drawable.wb_sunny,
                color = IconMaskColors.IconLightOrange,
            )
        },
        summary = { Text(stringResource(nightString(nightValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(nightString(it))) },
    )
    PreferenceDivider()

    fun getLanguageDisplayName(tag: String): String =
        AppLanguage.fromTag(tag)?.displayName ?: runBlocking {
            resolveRepository().getString(Res.string.language_system_default)
        }

    val languageValues = AppLanguage.entries.map { it.tag }
    val languageController = rememberAppLanguageController(defaultTag = "")
    val appLanguage by languageController.flow.collectAsStateWithLifecycle(languageController.value)
    val selectedLanguage = if (appLanguage in languageValues) appLanguage else ""
    ListPreference(
        value = selectedLanguage,
        onValueChange = { languageController.value = it },
        values = languageValues,
        title = { Text(stringResource(Res.string.language)) },
        icon = {
            MaskedIcon(Res.drawable.translate, color = IconMaskColors.IconLavender)
        },
        summary = { Text(getLanguageDisplayName(selectedLanguage)) },
        type = ListPreferenceType.ALERT_DIALOG,
        valueToText = { AnnotatedString(getLanguageDisplayName(it)) },
    )
    PreferenceDivider()

    fun serviceModeText(mode: String): StringResource = when (mode) {
        Key.MODE_VPN -> Res.string.service_mode_vpn
        Key.MODE_PROXY -> Res.string.service_mode_proxy
        else -> Res.string.service_mode_vpn
    }

    val serviceModeValue by DataStore.configurationStore
        .stringFlow(Key.SERVICE_MODE, Key.MODE_VPN)
        .collectAsStateWithLifecycle(Key.MODE_VPN)
    ListPreference(
        value = serviceModeValue,
        onValueChange = { DataStore.serviceMode = it },
        values = listOf(Key.MODE_VPN, Key.MODE_PROXY),
        title = { Text(stringResource(Res.string.service_mode)) },
        icon = {
            MaskedIcon(
                Res.drawable.developer_mode,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(stringResource(serviceModeText(serviceModeValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(serviceModeText(it))) },
    )
    PreferenceDivider()

    fun tunImplText(value: Int): String = when (value) {
        TunImplementation.GVISOR -> "gVisor"
        TunImplementation.SYSTEM -> "System"
        TunImplementation.MIXED -> "Mixed"
        else -> error("impossible")
    }

    val tunValue by DataStore.configurationStore
        .intFlow(Key.TUN_IMPLEMENTATION, TunImplementation.MIXED)
        .collectAsStateWithLifecycle(TunImplementation.MIXED)
    ListPreference(
        value = tunValue,
        onValueChange = {
            DataStore.tunImplementation = it
            needReload()
        },
        values = listOf(
            TunImplementation.GVISOR,
            TunImplementation.SYSTEM,
            TunImplementation.MIXED,
        ),
        title = { Text(stringResource(Res.string.tun_implementation)) },
        icon = {
            MaskedIcon(
                Res.drawable.flip_camera_android,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(tunImplText(tunValue)) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(tunImplText(it)) },
    )
    PreferenceDivider()

    val mtuValue by DataStore.configurationStore
        .intFlow(Key.MTU, 9000)
        .collectAsStateWithLifecycle(9000)
    TextFieldPreference(
        value = mtuValue,
        onValueChange = {
            DataStore.mtu = it
            needReload()
        },
        title = { Text(stringResource(Res.string.mtu)) },
        textToValue = { it.toIntOrNull() ?: 9000 },
        icon = {
            MaskedIcon(
                Res.drawable.public_icon,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(mtuValue.toString()) },
        valueToText = { it.toString() },
    )
    PlatformGeneralOptions(needReload)
    PreferenceDivider()

    fun speedIntervalText(ms: Int): StringOrRes = when (ms) {
        0 -> StringOrRes.Res(Res.string.disable)
        500 -> StringOrRes.Direct("500ms")
        1000 -> StringOrRes.Direct("1s")
        3000 -> StringOrRes.Direct("3s")
        10000 -> StringOrRes.Direct("10s")
        else -> StringOrRes.Direct("1s")
    }

    val speedIntervalValue by DataStore.configurationStore
        .intFlow(Key.SPEED_INTERVAL, 1000)
        .collectAsStateWithLifecycle(1000)
    ListPreference(
        value = speedIntervalValue,
        onValueChange = { DataStore.speedInterval = it },
        values = listOf(0, 500, 1000, 3000, 10000),
        title = { Text(stringResource(Res.string.speed_interval)) },
        icon = {
            MaskedIcon(
                Res.drawable.shutter_speed,
                color = IconMaskColors.IconLightPink,
            )
        },
        summary = { Text(stringOrRes(speedIntervalText(speedIntervalValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = {
            val text = runBlocking { getStringOrRes(speedIntervalText(it)) }
            AnnotatedString(text)
        },
    )
    PreferenceDivider()

    val profileTrafficStatisticsValue by DataStore.configurationStore
        .booleanFlow(Key.PROFILE_TRAFFIC_STATISTICS, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = profileTrafficStatisticsValue,
        onValueChange = { DataStore.profileTrafficStatistics = it },
        title = { Text(stringResource(Res.string.profile_traffic_statistics)) },
        icon = {
            MaskedIcon(
                Res.drawable.traffic,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringResource(Res.string.profile_traffic_statistics_summary)) },
        enabled = speedIntervalValue != 0,
    )
    PreferenceDivider()

    val showDirectSpeedValue by DataStore.configurationStore
        .booleanFlow(Key.SHOW_DIRECT_SPEED, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = showDirectSpeedValue,
        onValueChange = { DataStore.showDirectSpeed = it },
        title = { Text(stringResource(Res.string.show_direct_speed)) },
        icon = {
            MaskedIcon(Res.drawable.speed, color = IconMaskColors.IconLightPink)
        },
        summary = { Text(stringResource(Res.string.show_direct_speed_sum)) },
        enabled = speedIntervalValue != 0,
    )
    PreferenceDivider()

    val alwaysShowAddressValue by DataStore.configurationStore
        .booleanFlow(Key.ALWAYS_SHOW_ADDRESS, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = alwaysShowAddressValue,
        onValueChange = { DataStore.alwaysShowAddress = it },
        title = { Text(stringResource(Res.string.always_show_address)) },
        icon = {
            MaskedIcon(
                Res.drawable.center_focus_weak,
                color = IconMaskColors.IconCoral,
            )
        },
        summary = { Text(stringResource(Res.string.always_show_address_sum)) },
    )
    PreferenceDivider()

    val blurredAddressValue by DataStore.configurationStore
        .booleanFlow(Key.BLURRED_ADDRESS, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = blurredAddressValue,
        onValueChange = { DataStore.blurredAddress = it },
        title = { Text(stringResource(Res.string.blurred_address)) },
        icon = {
            MaskedIcon(
                Res.drawable.transgender,
                color = IconMaskColors.IconLavender,
            )
        },
        enabled = alwaysShowAddressValue,
    )
    PreferenceDivider()

    val securityAdvisoryValue by DataStore.configurationStore
        .booleanFlow(Key.SECURITY_ADVISORY, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = securityAdvisoryValue,
        onValueChange = { DataStore.securityAdvisory = it },
        title = { Text(stringResource(Res.string.insecure_warn)) },
        icon = {
            MaskedIcon(
                Res.drawable.security,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.risk(),
            )
        },
    )
    PreferenceDivider()
    val globalAllowInsecureValue by DataStore.configurationStore
        .booleanFlow(Key.GLOBAL_ALLOW_INSECURE, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = globalAllowInsecureValue,
        onValueChange = { DataStore.globalAllowInsecure = it },
        title = { Text(stringResource(Res.string.allow_insecure)) },
        icon = {
            MaskedIcon(
                Res.drawable.security,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.risk(),
            )
        },
        summary = { Text(stringResource(Res.string.allow_insecure_sum)) },
    )
    PlatformSecurityOptions()
    MeteredNetworkPreference(needReload)
    PreferenceDivider()

    val logLevelValue by DataStore.configurationStore
        .intFlow(Key.LOG_LEVEL, 3)
        .collectAsStateWithLifecycle(3)
    ListPreference(
        value = logLevelValue,
        onValueChange = {
            DataStore.logLevel = it
            needRestart()
        },
        values = intListN(7),
        title = { Text(stringResource(Res.string.log_level)) },
        icon = {
            MaskedIcon(
                Res.drawable.bug_report,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(logLevelString(logLevelValue)) },
        type = ListPreferenceType.ALERT_DIALOG,
        valueToText = { AnnotatedString(logLevelString(it)) },
    )
    PreferenceDivider()

    val maxLogLineValue by DataStore.configurationStore
        .intFlow(Key.LOG_MAX_LINE, 1024)
        .collectAsStateWithLifecycle(1024)
    var previewValue by remember { mutableFloatStateOf(maxLogLineValue.toFloat()) }
    SliderPreference(
        value = maxLogLineValue.toFloat(),
        onValueChange = { DataStore.logMaxLine = it.toInt() },
        sliderValue = previewValue,
        onSliderValueChange = { previewValue = it },
        title = { Text(stringResource(Res.string.max_log_line)) },
        valueRange = 1024f..1024f * 64f,
        valueSteps = 128,
        icon = {
            MaskedIcon(
                Res.drawable.description,
                color = IconMaskColors.IconWarmGray,
            )
        },
        valueText = { Text(previewValue.toInt().toString()) },
    )
    if (isExpertState) {
        PreferenceDivider()
        val debugListenValue by DataStore.configurationStore
            .stringFlow(Key.DEBUG_LISTEN, "")
            .collectAsStateWithLifecycle("")
        TextFieldPreference(
            value = debugListenValue,
            onValueChange = {
                DataStore.debugListen = it
                needReload()
            },
            title = { Text("pprof listen") },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.developer_board,
                    color = IconMaskColors.IconCoral,
                    shape = IconMaskShapes.risk(),
                )
            },
            summary = { Text(contentOrUnset(debugListenValue)) },
            valueToText = { it },
        )
    }
}

@Composable
private fun Circle(
    modifier: Modifier = Modifier,
    color: Color,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier.background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = vectorResource(Res.drawable.check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
