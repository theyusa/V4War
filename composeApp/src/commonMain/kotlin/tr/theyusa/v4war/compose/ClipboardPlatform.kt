package tr.theyusa.v4war.compose

import androidx.compose.ui.platform.Clipboard

expect suspend fun Clipboard.setPlainText(text: String)

expect suspend fun Clipboard.getPlainText(): String?