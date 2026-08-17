package tr.theyusa.v4war.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.compose.HostTextField
import tr.theyusa.v4war.compose.IconMaskColors
import tr.theyusa.v4war.compose.IconMaskShapes
import tr.theyusa.v4war.compose.MaskedIcon
import tr.theyusa.v4war.compose.PreferenceDivider
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.ktx.contentOrUnset
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.auto
import tr.theyusa.v4war.resources.direct_dns
import tr.theyusa.v4war.resources.dns
import tr.theyusa.v4war.resources.dns_hosts
import tr.theyusa.v4war.resources.domain_strategy_for_direct
import tr.theyusa.v4war.resources.domain_strategy_for_server
import tr.theyusa.v4war.resources.fake_dns
import tr.theyusa.v4war.resources.fake_dns_for_all
import tr.theyusa.v4war.resources.fake_dns_for_all_sum
import tr.theyusa.v4war.resources.fake_ip_range_4
import tr.theyusa.v4war.resources.fake_ip_range_6
import tr.theyusa.v4war.resources.fakedns_message
import tr.theyusa.v4war.resources.hijack_dns
import tr.theyusa.v4war.resources.hijack_dns_sum
import tr.theyusa.v4war.resources.ipv4_only
import tr.theyusa.v4war.resources.ipv6_only
import tr.theyusa.v4war.resources.lock
import tr.theyusa.v4war.resources.optimistic_dns_cache
import tr.theyusa.v4war.resources.optimistic_dns_cache_sum
import tr.theyusa.v4war.resources.prefer_ipv4
import tr.theyusa.v4war.resources.prefer_ipv6
import tr.theyusa.v4war.resources.remote_dns
import tr.theyusa.v4war.resources.speed
import tr.theyusa.v4war.resources.text_select_end
import tr.theyusa.v4war.resources.transform
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DnsSettingsGroup(
    needReload: () -> Unit,
) {
    val remoteDnsValue by DataStore.configurationStore
        .stringFlow(Key.REMOTE_DNS, "tcp://dns.google")
        .collectAsStateWithLifecycle("tcp://dns.google")
    TextFieldPreference(
        value = remoteDnsValue,
        onValueChange = {
            DataStore.remoteDns = it
            needReload()
        },
        title = { Text(stringResource(Res.string.remote_dns)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.dns, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(contentOrUnset(remoteDnsValue)) },
        valueToText = { it },
    )
    PreferenceDivider()

    val directDnsValue by DataStore.configurationStore
        .stringFlow(Key.DIRECT_DNS, "local")
        .collectAsStateWithLifecycle("local")
    TextFieldPreference(
        value = directDnsValue,
        onValueChange = {
            DataStore.directDns = it
            needReload()
        },
        title = { Text(stringResource(Res.string.direct_dns)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.dns, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(contentOrUnset(directDnsValue)) },
        valueToText = { it },
    )
    PreferenceDivider()

    val domainStrategyDirectValue by DataStore.configurationStore
        .stringFlow(Key.DOMAIN_STRATEGY_FOR_DIRECT, "auto")
        .collectAsStateWithLifecycle("auto")
    val domainStrategyValues =
        listOf("auto", "prefer_ipv6", "prefer_ipv4", "ipv4_only", "ipv6_only")
    val domainStrategyEntries = listOf(
        stringResource(Res.string.auto),
        stringResource(Res.string.prefer_ipv6),
        stringResource(Res.string.prefer_ipv4),
        stringResource(Res.string.ipv4_only),
        stringResource(Res.string.ipv6_only),
    )
    ListPreference(
        value = domainStrategyDirectValue,
        onValueChange = {
            DataStore.domainStrategyForDirect = it
            needReload()
        },
        values = domainStrategyValues,
        title = { Text(stringResource(Res.string.domain_strategy_for_direct)) },
        icon = { Spacer(Modifier.size(24.dp)) },
        summary = {
            val selectedIndex =
                domainStrategyValues.indexOf(domainStrategyDirectValue).takeIf { it >= 0 } ?: 0
            Text(domainStrategyEntries[selectedIndex])
        },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = {
            val selectedIndex = domainStrategyValues.indexOf(it).takeIf { index -> index >= 0 } ?: 0
            AnnotatedString(domainStrategyEntries[selectedIndex])
        },
    )
    PreferenceDivider()

    val domainStrategyServerValue by DataStore.configurationStore
        .stringFlow(Key.DOMAIN_STRATEGY_FOR_SERVER, "auto")
        .collectAsStateWithLifecycle("auto")
    ListPreference(
        value = domainStrategyServerValue,
        onValueChange = {
            DataStore.domainStrategyForServer = it
            needReload()
        },
        values = domainStrategyValues,
        title = { Text(stringResource(Res.string.domain_strategy_for_server)) },
        icon = { Spacer(Modifier.size(24.dp)) },
        summary = {
            val selectedIndex =
                domainStrategyValues.indexOf(domainStrategyServerValue).takeIf { it >= 0 } ?: 0
            Text(domainStrategyEntries[selectedIndex])
        },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = {
            val selectedIndex = domainStrategyValues.indexOf(it).takeIf { index -> index >= 0 } ?: 0
            AnnotatedString(domainStrategyEntries[selectedIndex])
        },
    )
    PreferenceDivider()

    val enableFakeDnsValue by DataStore.configurationStore
        .booleanFlow(Key.ENABLE_FAKE_DNS, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = enableFakeDnsValue,
        onValueChange = {
            DataStore.enableFakeDns = it
            needReload()
        },
        title = { Text(stringResource(Res.string.fake_dns)) },
        icon = {
            MaskedIcon(Res.drawable.lock, color = IconMaskColors.IconLightPink)
        },
        summary = { Text(stringResource(Res.string.fakedns_message)) },
    )
    PreferenceDivider()

    val fakeDnsForAllValue by DataStore.configurationStore
        .booleanFlow(Key.FAKE_DNS_FOR_ALL, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = fakeDnsForAllValue,
        onValueChange = {
            DataStore.fakeDNSForAll = it
            needReload()
        },
        title = { Text(stringResource(Res.string.fake_dns_for_all)) },
        enabled = enableFakeDnsValue,
        icon = {
            MaskedIcon(
                Res.drawable.lock,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.risk(),
            )
        },
        summary = { Text(stringResource(Res.string.fake_dns_for_all_sum)) },
    )
    PreferenceDivider()

    val fakeDnsRange4Value by DataStore.configurationStore
        .stringFlow(Key.FAKE_DNS_RANGE_4, "198.51.100.0/24")
        .collectAsStateWithLifecycle("198.51.100.0/24")
    TextFieldPreference(
        value = fakeDnsRange4Value,
        onValueChange = {
            DataStore.fakeDNSRange4 = it
            needReload()
        },
        title = { Text(stringResource(Res.string.fake_ip_range_4)) },
        textToValue = { it },
        enabled = enableFakeDnsValue,
        icon = {
            MaskedIcon(
                Res.drawable.text_select_end,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = { Text(contentOrUnset(fakeDnsRange4Value)) },
        valueToText = { it },
    )
    PreferenceDivider()

    val fakeDnsRange6Value by DataStore.configurationStore
        .stringFlow(Key.FAKE_DNS_RANGE_6, "2001:2::/48")
        .collectAsStateWithLifecycle("2001:2::/48")
    TextFieldPreference(
        value = fakeDnsRange6Value,
        onValueChange = {
            DataStore.fakeDNSRange6 = it
            needReload()
        },
        title = { Text(stringResource(Res.string.fake_ip_range_6)) },
        textToValue = { it },
        enabled = enableFakeDnsValue,
        icon = {
            MaskedIcon(
                Res.drawable.text_select_end,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = { Text(contentOrUnset(fakeDnsRange6Value)) },
        valueToText = { it },
    )
    PreferenceDivider()

    val dnsHostsValue by DataStore.configurationStore
        .stringFlow(Key.DNS_HOSTS, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = dnsHostsValue,
        onValueChange = {
            DataStore.dnsHosts = it
            needReload()
        },
        title = { Text(stringResource(Res.string.dns_hosts)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.transform, color = IconMaskColors.IconCyan)
        },
        summary = { Text(contentOrUnset(dnsHostsValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        HostTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()
    val hijackDnsValue by DataStore.configurationStore
        .booleanFlow(Key.ENABLE_DNS_ROUTING, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = hijackDnsValue,
        onValueChange = { DataStore.enableDnsRouting = it; needReload() },
        title = { Text(stringResource(Res.string.hijack_dns)) },
        icon = { MaskedIcon(Res.drawable.dns, color = IconMaskColors.IconCyan) },
        summary = { Text(stringResource(Res.string.hijack_dns_sum)) },
    )
    PreferenceDivider()
    val optimisticDnsCacheValue by DataStore.configurationStore
        .booleanFlow(Key.OPTIMISTIC_DNS_CACHE, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = optimisticDnsCacheValue,
        onValueChange = { DataStore.optimisticDnsCache = it; needReload() },
        title = { Text(stringResource(Res.string.optimistic_dns_cache)) },
        icon = { MaskedIcon(Res.drawable.speed, color = IconMaskColors.IconLightGreen) },
        summary = { Text(stringResource(Res.string.optimistic_dns_cache_sum)) },
    )
}
