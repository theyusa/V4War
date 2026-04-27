@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package tr.theyusa.v4war.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import tr.theyusa.v4war.compose.material3.Button
import tr.theyusa.v4war.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import tr.theyusa.v4war.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import tr.theyusa.v4war.compose.BoxedVerticalScrollbar
import tr.theyusa.v4war.compose.TextButton
import tr.theyusa.v4war.compose.rememberScrollHideState
import tr.theyusa.v4war.ktx.Logs
import tr.theyusa.v4war.ktx.currentBackupFileTimestamp
import tr.theyusa.v4war.ktx.readableMessage
import tr.theyusa.v4war.platform.PlatformInfo
import tr.theyusa.v4war.repository.resolveRepository
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.action_export
import tr.theyusa.v4war.resources.action_export_msg
import tr.theyusa.v4war.resources.action_import_file
import tr.theyusa.v4war.resources.backup_groups_and_configurations
import tr.theyusa.v4war.resources.backup_import
import tr.theyusa.v4war.resources.backup_import_summary
import tr.theyusa.v4war.resources.backup_rules
import tr.theyusa.v4war.resources.backup_settings
import tr.theyusa.v4war.resources.backup_summary
import tr.theyusa.v4war.resources.cancel
import tr.theyusa.v4war.resources.error
import tr.theyusa.v4war.resources.error_title
import tr.theyusa.v4war.resources.ok
import tr.theyusa.v4war.resources.question_mark
import tr.theyusa.v4war.resources.share
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import java.io.File

@Composable
internal fun BackupScreen(
    modifier: Modifier = Modifier,
    onVisibleChange: (Boolean) -> Unit,
    viewModel: BackupViewModel = viewModel { BackupViewModel() },
    showSnackbar: (message: String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val visible by rememberScrollHideState(scrollState)
    val lifecycleOwner = LocalLifecycleOwner.current
    val shareBackupFile = rememberShareBackupFile()

    LaunchedEffect(visible) {
        onVisibleChange(visible)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var errorDialog by remember { mutableStateOf<String?>(null) }

    val exportFileLauncher = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
    ) { file ->
        if (file == null) {
            viewModel.postExport()
            return@rememberFileSaverLauncher
        }
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                file.write(uiState.exported!!.encodeToByteArray())
                showSnackbar(resolveRepository().getString(Res.string.action_export_msg))
            } catch (e: Exception) {
                Logs.e(e)
                showSnackbar(e.readableMessage)
            } finally {
                viewModel.postExport()
            }
        }
    }
    LaunchedEffect(uiState.exported) {
        uiState.exported?.let {
            val time = currentBackupFileTimestamp()
            val fileName = "v4war_backup_${time}"
            exportFileLauncher.launch(fileName, "json")
        }
    }

    val importFileLauncher = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = listOf("json")),
    ) { file ->
        if (file == null) return@rememberFilePickerLauncher
        val fileName = file.name
        if (!fileName.endsWith(".json", ignoreCase = true)) {
            showSnackbar("Selected file is not a .json backup file.")
            return@rememberFilePickerLauncher
        }
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val bytes = try {
                file.readBytes()
            } catch (e: Exception) {
                Logs.e(e)
                errorDialog = e.readableMessage
                return@launch
            }
            viewModel.inputFromBytes(
                bytes = bytes,
                onError = showSnackbar,
            )
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.action_export),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CheckBoxLine(
                        checked = uiState.backupGroupsAndConfig,
                        onCheckedChange = { viewModel.setBackupGroupsAndConfig(it) },
                        text = stringResource(Res.string.backup_groups_and_configurations),
                    )
                    CheckBoxLine(
                        checked = uiState.backupRules,
                        onCheckedChange = { viewModel.setBackupRules(it) },
                        text = stringResource(Res.string.backup_rules),
                    )
                    CheckBoxLine(
                        checked = uiState.backupSettings,
                        onCheckedChange = { viewModel.setBackupSettings(it) },
                        text = stringResource(Res.string.backup_settings),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.export() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.action_export))
                    }
                    if (PlatformInfo.isAndroid) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.share(
                                    createFile = { fileName ->
                                        File(resolveRepository().cacheDir, fileName)
                                    },
                                    launch = { file ->
                                        shareBackupFile(file)
                                    },
                                    onFailed = { message ->
                                        showSnackbar(message)
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.share))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.action_import_file),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.backup_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            importFileLauncher.launch()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.action_import_file))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        BoxedVerticalScrollbar(
            modifier = Modifier.fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = scrollState),
            style = defaultMaterialScrollbarStyle().copy(
                thickness = 12.dp,
            ),
        )
    }

    errorDialog?.let { error ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    errorDialog = null
                }
            },
            icon = {
                Icon(
                    vectorResource(Res.drawable.error),
                    null,
                )
            },
            title = { Text(stringResource(Res.string.error_title)) },
            text = { Text(error) },
        )
    }

    uiState.inputResult?.let { inputResult ->
        var importGroupsAndConfig by rememberSaveable { mutableStateOf(true) }
        var importRules by rememberSaveable { mutableStateOf(true) }
        var importSettings by rememberSaveable { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(stringResource(Res.string.backup_import)) {
                    viewModel.finishInput(
                        inputResult,
                        importGroupsAndConfig,
                        importRules,
                        importSettings,
                    )
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    viewModel.clearInputResult()
                }
            },
            icon = {
                Icon(
                    vectorResource(Res.drawable.question_mark),
                    null,
                )
            },
            title = { Text(stringResource(Res.string.backup_import)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CheckBoxLine(
                        checked = importGroupsAndConfig,
                        onCheckedChange = { importGroupsAndConfig = it },
                        text = stringResource(Res.string.backup_groups_and_configurations),
                    )
                    CheckBoxLine(
                        checked = importRules,
                        onCheckedChange = { importRules = it },
                        text = stringResource(Res.string.backup_rules),
                    )
                    CheckBoxLine(
                        checked = importSettings,
                        onCheckedChange = { importSettings = it },
                        text = stringResource(Res.string.backup_settings),
                    )
                    Text(
                        text = stringResource(Res.string.backup_import_summary),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )
                }
            },
        )
    }

    if (uiState.isImporting) Dialog(
        onDismissRequest = {},
    ) {
        CircularWavyProgressIndicator()
    }
}

@Composable
private fun CheckBoxLine(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.graphicsLayer {
                scaleX = 1.2f
                scaleY = 1.2f
            },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
        )
    }
}
