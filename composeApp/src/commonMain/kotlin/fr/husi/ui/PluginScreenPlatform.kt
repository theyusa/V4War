package fr.husi.ui

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

internal expect fun platformPluginsFlow(): Flow<List<PluginDisplay>>

@Composable
internal expect fun rememberOpenPluginCard(): (PluginDisplay) -> Unit

@Composable
internal expect fun rememberShouldRequestBatteryOptimizations(): Boolean

@Composable
internal expect fun rememberRequestIgnoreBatteryOptimizations(): () -> Unit
