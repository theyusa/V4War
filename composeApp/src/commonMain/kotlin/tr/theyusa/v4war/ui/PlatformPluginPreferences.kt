package tr.theyusa.v4war.ui

import androidx.compose.foundation.lazy.LazyListScope

internal expect fun LazyListScope.platformPluginPreferences(
    isExpert: Boolean,
    needRestart: () -> Unit
)
