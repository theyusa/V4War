package tr.theyusa.v4war.ui.dashboard

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberOpenProcessAppInfo(process: String?): (() -> Unit)?
