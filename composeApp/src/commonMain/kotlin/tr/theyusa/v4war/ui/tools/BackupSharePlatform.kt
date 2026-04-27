package tr.theyusa.v4war.ui.tools

import androidx.compose.runtime.Composable
import java.io.File

@Composable
internal expect fun rememberShareBackupFile(): (File) -> Unit
