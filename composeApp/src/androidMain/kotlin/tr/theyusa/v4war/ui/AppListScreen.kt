package tr.theyusa.v4war.ui

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import tr.theyusa.v4war.compose.material3.Icon
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tr.theyusa.v4war.compose.BackHandler
import tr.theyusa.v4war.ui.ensurePreviewRepository
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.cleaning_services
import tr.theyusa.v4war.resources.clear_selections
import tr.theyusa.v4war.resources.fiber_smart_record
import tr.theyusa.v4war.resources.invert_selections
import tr.theyusa.v4war.resources.select_apps
import tr.theyusa.v4war.results.LocalResultEventBus
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.random.Random

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal actual fun AppListScreen(
    initialPackages: Set<String>,
    resultKey: String,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val resultBus = LocalResultEventBus.current
    val context = LocalContext.current
    val viewModel: AppListViewModel = viewModel {
        AppListViewModel(
            pm = context.packageManager,
            appPackageName = context.packageName,
            packages = initialPackages,
        )
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun saveAndExit() {
        resultBus.sendResult<Set<String>>(resultKey, viewModel.allPackages().toSet())
        onBack()
    }
    BackHandler(enabled = true) {
        saveAndExit()
    }

    AppListScaffold(
        viewModel = viewModel,
        title = { Text(stringResource(Res.string.select_apps)) },
        isLoading = uiState.isLoading,
        apps = uiState.apps,
        filteredApps = uiState.filteredApps,
        snackbarMessage = uiState.snackbarMessage,
        onNavigationClick = ::saveAndExit,
        modifier = modifier,
        dropdownMenuItems = { onDismiss ->
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.invert_selections)) },
                onClick = {
                    viewModel.invertSections()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.fiber_smart_record),
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.clear_selections)) },
                onClick = {
                    viewModel.clearSections()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.cleaning_services),
                        contentDescription = null,
                    )
                },
            )
        },
    )
}

@Preview
@Composable
private fun PreviewAppListScreen() {
    ensurePreviewRepository()

    AppListScreen(
        initialPackages = emptySet(),
        resultKey = "app-list-${Random.nextLong()}",
        onBack = {},
    )
}
