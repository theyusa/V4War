package tr.theyusa.v4war.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.compose.IconMaskColors
import tr.theyusa.v4war.compose.MaskedIcon
import tr.theyusa.v4war.compose.PreferenceDivider
import tr.theyusa.v4war.compose.material3.Icon
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.apps
import tr.theyusa.v4war.resources.apps_message
import tr.theyusa.v4war.resources.keyboard_tab
import tr.theyusa.v4war.resources.legend_toggle
import tr.theyusa.v4war.resources.not_set
import tr.theyusa.v4war.resources.proxied_apps
import tr.theyusa.v4war.resources.proxied_apps_summary
import tr.theyusa.v4war.resources.update_proxy_apps_when_install
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TwoTargetSwitchPreference
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

internal actual fun LazyListScope.appSelectPreference(
    packages: Set<String>,
    onSelectApps: (Set<String>) -> Unit,
) {
    item("apps") {
        Preference(
            title = { Text(stringResource(Res.string.apps)) },
            icon = { Icon(vectorResource(Res.drawable.legend_toggle), null) },
            summary = {
                val text = when (val size = packages.size) {
                    0 -> stringResource(Res.string.not_set)
                    in 1..5 -> packages.joinToString("\n")
                    else -> pluralStringResource(Res.plurals.apps_message, size, size)
                }
                Text(text)
            },
            onClick = {
                onSelectApps(packages)
            },
        )
    }
}

@Composable
internal actual fun ProxyAppsPreferences(
    openAppManager: () -> Unit,
) {
    val value by DataStore.configurationStore
        .booleanFlow(Key.PROXY_APPS, false)
        .collectAsStateWithLifecycle(false)
    TwoTargetSwitchPreference(
        value = value,
        onValueChange = {
            DataStore.proxyApps = it
            if (it) {
                openAppManager()
            }
        },
        title = { Text(stringResource(Res.string.proxied_apps)) },
        icon = {
            MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconCyan)
        },
        summary = { Text(stringResource(Res.string.proxied_apps_summary)) },
        onClick = {
            if (!value) {
                DataStore.proxyApps = true
            }
            openAppManager()
        },
    )
    PreferenceDivider()
    val updateValue by DataStore.configurationStore
        .booleanFlow(Key.UPDATE_PROXY_APPS_WHEN_INSTALL, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = updateValue,
        onValueChange = { DataStore.updateProxyAppsWhenInstall = it },
        title = { Text(stringResource(Res.string.update_proxy_apps_when_install)) },
        icon = {
            MaskedIcon(
                Res.drawable.keyboard_tab,
                color = IconMaskColors.IconLavender,
            )
        },
    )
}
