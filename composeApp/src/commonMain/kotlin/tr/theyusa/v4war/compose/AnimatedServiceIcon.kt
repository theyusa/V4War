package tr.theyusa.v4war.compose

import androidx.compose.runtime.Composable
import tr.theyusa.v4war.bg.ServiceState

@Composable
expect fun AnimatedServiceIcon(state: ServiceState, contentDescription: String)
