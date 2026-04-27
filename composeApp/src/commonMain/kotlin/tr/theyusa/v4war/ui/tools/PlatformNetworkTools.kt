package tr.theyusa.v4war.ui.tools

import androidx.compose.runtime.Composable
import tr.theyusa.v4war.ui.NavRoutes

@Composable
internal expect fun PlatformNetworkTools(onOpenTool: (NavRoutes.ToolsPage) -> Unit)
