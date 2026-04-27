package tr.theyusa.v4war.ui.profile

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.database.DataStore

@Composable
internal fun rememberEffectiveAllowInsecure(profileAllowInsecure: Boolean): Boolean {
    val globalAllowInsecure = DataStore.configurationStore
        .booleanFlow(Key.GLOBAL_ALLOW_INSECURE, false)
        .collectAsStateWithLifecycle(false)
        .value

    return profileAllowInsecure || globalAllowInsecure
}
