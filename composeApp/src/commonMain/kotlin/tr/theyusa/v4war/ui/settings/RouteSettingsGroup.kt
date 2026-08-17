package tr.theyusa.v4war.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.NetworkInterfaceStrategy
import tr.theyusa.v4war.RuleProvider
import tr.theyusa.v4war.TrafficSniffing
import tr.theyusa.v4war.compose.IconMaskColors
import tr.theyusa.v4war.compose.LinkOrContentTextField
import tr.theyusa.v4war.compose.MaskedIcon
import tr.theyusa.v4war.compose.PreferenceDivider
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.ktx.contentOrUnset
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.auto
import tr.theyusa.v4war.resources.construction
import tr.theyusa.v4war.resources.custom_rule_provider
import tr.theyusa.v4war.resources.dns
import tr.theyusa.v4war.resources.fallback
import tr.theyusa.v4war.resources.hybrid
import tr.theyusa.v4war.resources.import_contacts
import tr.theyusa.v4war.resources.ipv4_only
import tr.theyusa.v4war.resources.ipv6_only
import tr.theyusa.v4war.resources.keep_default
import tr.theyusa.v4war.resources.network_interface_preference
import tr.theyusa.v4war.resources.network_interface_strategy
import tr.theyusa.v4war.resources.network_strategy
import tr.theyusa.v4war.resources.not_set
import tr.theyusa.v4war.resources.prefer_ipv4
import tr.theyusa.v4war.resources.prefer_ipv6
import tr.theyusa.v4war.resources.public_icon
import tr.theyusa.v4war.resources.resolve_destination
import tr.theyusa.v4war.resources.resolve_destination_summary
import tr.theyusa.v4war.resources.route_rules_official
import tr.theyusa.v4war.resources.route_rules_provider
import tr.theyusa.v4war.resources.router
import tr.theyusa.v4war.resources.rule_folder
import tr.theyusa.v4war.resources.traffic_sniffing
import tr.theyusa.v4war.resources.traffic_sniffing_disabled
import tr.theyusa.v4war.resources.traffic_sniffing_enabled
import tr.theyusa.v4war.resources.traffic_sniffing_route
import tr.theyusa.v4war.ui.PlatformRouteOptions
import tr.theyusa.v4war.ui.ProxyAppsPreferences
import tr.theyusa.v4war.ui.StringOrRes
import tr.theyusa.v4war.ui.stringOrRes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.MultiSelectListPreference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RouteSettingsGroup(
    needReload: () -> Unit,
    openAppManager: () -> Unit,
) {
    val serviceModeState by DataStore.configurationStore
        .stringFlow(Key.SERVICE_MODE, Key.MODE_VPN)
        .collectAsStateWithLifecycle(Key.MODE_VPN)

    ProxyAppsPreferences(openAppManager)

    PlatformRouteOptions(
        needReload = needReload,
        isVpnMode = serviceModeState == Key.MODE_VPN,
    )
    PreferenceDivider()

    fun networkStrategyTextRes(value: String): StringResource = when (value) {
        "" -> Res.string.auto
        "prefer_ipv6" -> Res.string.prefer_ipv6
        "prefer_ipv4" -> Res.string.prefer_ipv4
        "ipv4_only" -> Res.string.ipv4_only
        "ipv6_only" -> Res.string.ipv6_only
        else -> Res.string.auto
    }

    val networkStrategyValue by DataStore.configurationStore
        .stringFlow(Key.NETWORK_STRATEGY, "")
        .collectAsStateWithLifecycle("")
    ListPreference(
        value = networkStrategyValue,
        onValueChange = {
            DataStore.networkStrategy = it
            needReload()
        },
        values = listOf("", "prefer_ipv6", "prefer_ipv4", "ipv4_only", "ipv6_only"),
        title = { Text(stringResource(Res.string.network_strategy)) },
        icon = {
            MaskedIcon(Res.drawable.router, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(stringResource(networkStrategyTextRes(networkStrategyValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(networkStrategyTextRes(it))) },
    )
    PreferenceDivider()

    fun networkInterfaceStrategyTextRes(selection: Int): StringResource = when (selection) {
        NetworkInterfaceStrategy.DEFAULT -> Res.string.keep_default
        NetworkInterfaceStrategy.HYBRID -> Res.string.hybrid
        NetworkInterfaceStrategy.FALLBACK -> Res.string.fallback
        else -> Res.string.keep_default
    }

    val networkInterfaceValue by DataStore.configurationStore
        .intFlow(Key.NETWORK_INTERFACE_STRATEGY, NetworkInterfaceStrategy.DEFAULT)
        .collectAsStateWithLifecycle(NetworkInterfaceStrategy.DEFAULT)
    ListPreference(
        value = networkInterfaceValue,
        onValueChange = {
            DataStore.networkInterfaceType = it
            needReload()
        },
        values = listOf(
            NetworkInterfaceStrategy.DEFAULT,
            NetworkInterfaceStrategy.HYBRID,
            NetworkInterfaceStrategy.FALLBACK,
        ),
        title = { Text(stringResource(Res.string.network_interface_strategy)) },
        icon = {
            MaskedIcon(
                Res.drawable.construction,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = { Text(stringResource(networkInterfaceStrategyTextRes(networkInterfaceValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(networkInterfaceStrategyTextRes(it))) },
    )
    PreferenceDivider()

    val preferredInterfaces by DataStore.configurationStore
        .stringSetFlow(Key.NETWORK_PREFERRED_INTERFACES, emptySet())
        .collectAsStateWithLifecycle(emptySet())
    MultiSelectListPreference(
        value = preferredInterfaces,
        onValueChange = {
            DataStore.networkPreferredInterfaces = it
            needReload()
        },
        values = listOf("wifi", "cellular", "ethernet", "other"),
        title = { Text(stringResource(Res.string.network_interface_preference)) },
        icon = {
            MaskedIcon(
                Res.drawable.public_icon,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = {
            val text = if (preferredInterfaces.isEmpty()) {
                stringResource(Res.string.not_set)
            } else preferredInterfaces.joinToString("\n")
            Text(text)
        },
        valueToText = { AnnotatedString(it) },
    )
    PreferenceDivider()

    fun sniffingTextRes(value: Int): StringResource = when (value) {
        TrafficSniffing.DISABLED -> Res.string.traffic_sniffing_disabled
        TrafficSniffing.ENABLED -> Res.string.traffic_sniffing_enabled
        TrafficSniffing.ROUTE -> Res.string.traffic_sniffing_route
        else -> Res.string.traffic_sniffing_disabled
    }
    val trafficSniffingValue by DataStore.configurationStore
        .intFlow(Key.TRAFFIC_SNIFFING, 1)
        .collectAsStateWithLifecycle(1)
    ListPreference(
        value = trafficSniffingValue,
        onValueChange = { DataStore.trafficSniffing = it; needReload() },
        values = listOf(TrafficSniffing.DISABLED, TrafficSniffing.ENABLED, TrafficSniffing.ROUTE),
        title = { Text(stringResource(Res.string.traffic_sniffing)) },
        icon = { MaskedIcon(Res.drawable.router, color = IconMaskColors.IconLightBlue) },
        summary = { Text(stringResource(sniffingTextRes(trafficSniffingValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(sniffingTextRes(it))) },
    )
    PreferenceDivider()

    val resolveDestinationValue by DataStore.configurationStore
        .booleanFlow(Key.RESOLVE_DESTINATION, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = resolveDestinationValue,
        onValueChange = { DataStore.resolveDestination = it; needReload() },
        title = { Text(stringResource(Res.string.resolve_destination)) },
        icon = { MaskedIcon(Res.drawable.dns, color = IconMaskColors.IconCyan) },
        summary = { Text(stringResource(Res.string.resolve_destination_summary)) },
    )
    PreferenceDivider()

    fun rulesProviderText(index: Int): StringOrRes = when (index) {
        RuleProvider.OFFICIAL -> StringOrRes.Res(Res.string.route_rules_official)
        RuleProvider.LOYALSOLDIER -> StringOrRes.Direct("Loyalsoldier (1715173329/sing-geo*)")
        RuleProvider.CHOCOLATE4U -> StringOrRes.Direct("Chocolate4U/Iran-sing-box-rules")
        RuleProvider.CUSTOM -> StringOrRes.Res(Res.string.custom_rule_provider)
        else -> StringOrRes.Res(Res.string.route_rules_official)
    }

    val rulesProviderValue by DataStore.configurationStore
        .intFlow(Key.RULES_PROVIDER, RuleProvider.OFFICIAL)
        .collectAsStateWithLifecycle(RuleProvider.OFFICIAL)
    ListPreference(
        value = rulesProviderValue,
        onValueChange = { DataStore.rulesProvider = it },
        values = listOf(
            RuleProvider.OFFICIAL,
            RuleProvider.LOYALSOLDIER,
            RuleProvider.CHOCOLATE4U,
            RuleProvider.CUSTOM,
        ),
        title = { Text(stringResource(Res.string.route_rules_provider)) },
        icon = {
            MaskedIcon(
                Res.drawable.rule_folder,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(rulesProviderText(rulesProviderValue))) },
        type = ListPreferenceType.ALERT_DIALOG,
        valueToText = { AnnotatedString(stringOrRes(rulesProviderText(it))) },
    )
    if (rulesProviderValue == RuleProvider.CUSTOM) {
        PreferenceDivider()
        val defaultUrl =
            "https://codeload.github.com/SagerNet/sing-geosite/tar.gz/refs/heads/rule-set"
        val customRuleProviderValue by DataStore.configurationStore
            .stringFlow(Key.CUSTOM_RULE_PROVIDER, defaultUrl)
            .collectAsStateWithLifecycle(defaultUrl)
        TextFieldPreference(
            value = customRuleProviderValue,
            onValueChange = { DataStore.customRuleProvider = it },
            title = { Text(stringResource(Res.string.custom_rule_provider)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.import_contacts,
                    color = IconMaskColors.IconLightYellow,
                )
            },
            summary = { Text(contentOrUnset(customRuleProviderValue)) },
            valueToText = { it },
        ) { value, onValueChange, onOk ->
            LinkOrContentTextField(value, onValueChange, onOk)
        }
    }
}
