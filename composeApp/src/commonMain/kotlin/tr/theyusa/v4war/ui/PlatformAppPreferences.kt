package tr.theyusa.v4war.ui

import androidx.compose.foundation.lazy.LazyListScope

internal expect fun LazyListScope.appSelectPreference(
    packages: Set<String>,
    onSelectApps: (Set<String>) -> Unit,
)

internal expect fun LazyListScope.proxyAppsPreferences(
    openAppManager: () -> Unit,
)
