package tr.theyusa.v4war.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable

internal expect fun LazyListScope.appSelectPreference(
    packages: Set<String>,
    onSelectApps: (Set<String>) -> Unit,
)

@Composable
internal expect fun ProxyAppsPreferences(
    openAppManager: () -> Unit,
)
