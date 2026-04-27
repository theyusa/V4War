package tr.theyusa.v4war.ui.profile

import androidx.compose.runtime.Composable
import tr.theyusa.v4war.database.ProxyEntity

@Composable
internal expect fun platformSupportShortcut(): Boolean

@Composable
internal expect fun ShortcutMenuItem(entity: ProxyEntity, postClick: () -> Unit)