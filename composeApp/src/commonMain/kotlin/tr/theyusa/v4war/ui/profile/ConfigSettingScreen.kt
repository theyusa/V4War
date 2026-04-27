package tr.theyusa.v4war.ui.profile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import tr.theyusa.v4war.compose.material3.Icon
import androidx.compose.material3.Scaffold
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.compose.BackHandler
import tr.theyusa.v4war.compose.BoxedVerticalScrollbar
import tr.theyusa.v4war.compose.SimpleIconButton
import tr.theyusa.v4war.compose.TextButton
import tr.theyusa.v4war.fmt.config.ConfigBean
import tr.theyusa.v4war.ktx.contentOrUnset
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.apply
import tr.theyusa.v4war.resources.cancel
import tr.theyusa.v4war.resources.close
import tr.theyusa.v4war.resources.custom_config
import tr.theyusa.v4war.resources.delete
import tr.theyusa.v4war.resources.delete_confirm_prompt
import tr.theyusa.v4war.resources.done
import tr.theyusa.v4war.resources.emoji_symbols
import tr.theyusa.v4war.resources.is_outbound_only
import tr.theyusa.v4war.resources.layers
import tr.theyusa.v4war.resources.lines
import tr.theyusa.v4war.resources.no
import tr.theyusa.v4war.resources.not_set
import tr.theyusa.v4war.resources.ok
import tr.theyusa.v4war.resources.outbond
import tr.theyusa.v4war.resources.profile_name
import tr.theyusa.v4war.resources.question_mark
import tr.theyusa.v4war.resources.unsaved_changes_prompt
import tr.theyusa.v4war.resources.warning
import tr.theyusa.v4war.results.ResultEffect
import tr.theyusa.v4war.ui.NavRoutes
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigSettingScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: ConfigSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        ConfigSettingsViewModel()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()

    var showBackAlert by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }

    BackHandler(enabled = isDirty) {
        showBackAlert = true
    }

    val resultKeyNumber = rememberSaveable {
        viewModel.editingId.takeIf { it >= 0L } ?: Random.nextLong()
    }
    val resultKey = "config-settings-result-$resultKeyNumber"
    ResultEffect<String?>(resultKey = resultKey) { result ->
        if (result == null) return@ResultEffect
        viewModel.setConfigForResult(result)
    }

    val config = when (uiState.type) {
        ConfigBean.TYPE_CONFIG -> uiState.customConfig
        ConfigBean.TYPE_OUTBOUND -> uiState.customOutbound
        else -> error("impossible")
    }

    val windowInsets = WindowInsets.safeDrawing
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.custom_config)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                    ) {
                        if (isDirty) {
                            showBackAlert = true
                        } else {
                            onResult(false)
                        }
                    }
                },
                actions = {
                    if (!viewModel.isNew) {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.delete),
                            contentDescription = stringResource(Res.string.delete),
                            onClick = { showDeleteAlert = true },
                        )
                    }
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.done),
                        contentDescription = stringResource(Res.string.apply),
                    ) {
                        viewModel.save()
                        onResult(true)
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        ProvidePreferenceLocals {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(innerPadding),
                ) {
                    item("name") {
                        TextFieldPreference(
                            value = uiState.name,
                            onValueChange = { viewModel.setName(it) },
                            title = { Text(stringResource(Res.string.profile_name)) },
                            textToValue = { it },
                            icon = { Icon(vectorResource(Res.drawable.emoji_symbols), null) },
                            summary = { Text(contentOrUnset(uiState.name)) },
                            valueToText = { it },
                        )
                    }
                    item("outbound_only") {
                        SwitchPreference(
                            value = uiState.type == ConfigBean.TYPE_OUTBOUND,
                            onValueChange = {
                                viewModel.setType(
                                    if (it) {
                                        ConfigBean.TYPE_OUTBOUND
                                    } else {
                                        ConfigBean.TYPE_CONFIG
                                    },
                                )
                            },
                            title = { Text(stringResource(Res.string.is_outbound_only)) },
                            icon = { Icon(vectorResource(Res.drawable.outbond), null) },
                        )
                    }
                    item("config") {
                        Preference(
                            title = { Text(stringResource(Res.string.custom_config)) },
                            icon = { Icon(vectorResource(Res.drawable.layers), null) },
                            summary = {
                                val text = if (config.isBlank()) {
                                    stringResource(Res.string.not_set)
                                } else {
                                    val count = config.count { it == '\n' } + 1
                                    pluralStringResource(Res.plurals.lines, count, count)
                                }
                                Text(text)
                            },
                            onClick = {
                                onOpenConfigEditor(
                                    NavRoutes.ConfigEditor(
                                        initialText = config,
                                        resultKey = resultKey,
                                    ),
                                )
                            },
                        )
                    }
                    item("bottom_padding") {
                        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier.fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }
        }
    }

    if (showBackAlert) {
        AlertDialog(
            onDismissRequest = { showBackAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    viewModel.save()
                    onResult(true)
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.no)) {
                    onResult(false)
                }
            },
            icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
            title = { Text(stringResource(Res.string.unsaved_changes_prompt)) },
        )
    }

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    viewModel.delete()
                    onResult(true)
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    showDeleteAlert = false
                }
            },
            icon = { Icon(vectorResource(Res.drawable.warning), null) },
            title = { Text(stringResource(Res.string.delete_confirm_prompt)) },
        )
    }
}
