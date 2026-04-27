package tr.theyusa.v4war.compose

import androidx.compose.runtime.Composable

@Composable
expect fun rememberVpnServiceLauncher(onFailed: () -> Unit): () -> Unit
