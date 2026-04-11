package fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.compose.PreferenceType
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.resources.Res
import fr.husi.resources.apps
import fr.husi.resources.apps_message
import fr.husi.resources.keyboard_tab
import fr.husi.resources.legend_toggle
import fr.husi.resources.not_set
import fr.husi.resources.proxied_apps
import fr.husi.resources.proxied_apps_summary
import fr.husi.resources.update_proxy_apps_when_install
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

internal actual fun LazyListScope.proxyAppsPreferences(
    openAppManager: () -> Unit,
) {
    item(Key.PROXY_APPS, PreferenceType.SWITCH) {
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
                Icon(
                    vectorResource(Res.drawable.apps),
                    null,
                )
            },
            summary = { Text(stringResource(Res.string.proxied_apps_summary)) },
            onClick = {
                if (!value) {
                    DataStore.proxyApps = true
                }
                openAppManager()
            },
        )
    }
    item(Key.UPDATE_PROXY_APPS_WHEN_INSTALL, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.UPDATE_PROXY_APPS_WHEN_INSTALL, false)
            .collectAsStateWithLifecycle(false)
        SwitchPreference(
            value = value,
            onValueChange = { DataStore.updateProxyAppsWhenInstall = it },
            title = { Text(stringResource(Res.string.update_proxy_apps_when_install)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.keyboard_tab),
                    null,
                )
            },
        )
    }
}
