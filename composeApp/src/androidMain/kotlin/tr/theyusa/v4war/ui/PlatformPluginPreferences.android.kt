package tr.theyusa.v4war.ui

import androidx.compose.foundation.lazy.LazyListScope
import tr.theyusa.v4war.compose.material3.Icon
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.compose.PreferenceType
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.copyright
import tr.theyusa.v4war.resources.custom_plugin_prefix
import tr.theyusa.v4war.resources.custom_plugin_prefix_summary
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

internal actual fun LazyListScope.platformPluginPreferences(
    isExpert: Boolean,
    needRestart: () -> Unit,
) {
    if (isExpert) item(Key.CUSTOM_PLUGIN_PREFIX, PreferenceType.TEXT_FIELD) {
        val value by DataStore.configurationStore
            .stringFlow(Key.CUSTOM_PLUGIN_PREFIX, "")
            .collectAsStateWithLifecycle("")
        TextFieldPreference(
            value = value,
            onValueChange = {
                DataStore.customPluginPrefix = it
                needRestart()
            },
            title = { Text(stringResource(Res.string.custom_plugin_prefix)) },
            textToValue = { it },
            icon = {
                Icon(
                    vectorResource(Res.drawable.copyright),
                    null,
                )
            },
            summary = { Text(stringResource(Res.string.custom_plugin_prefix_summary)) },
            valueToText = { it },
        )
    }
}
