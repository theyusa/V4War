package fr.husi.ui.profile

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.database.DataStore

@Composable
internal fun rememberEffectiveAllowInsecure(profileAllowInsecure: Boolean): Boolean {
    val globalAllowInsecure = DataStore.configurationStore
        .booleanFlow(Key.GLOBAL_ALLOW_INSECURE, false)
        .collectAsStateWithLifecycle(false)
        .value

    return profileAllowInsecure || globalAllowInsecure
}
