package tr.theyusa.v4war.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import tr.theyusa.v4war.compose.PasswordPreference
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.profile_config
import tr.theyusa.v4war.ui.NavRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrojanSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: TrojanSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        TrojanSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, scrollTo ->
        trojanSettings(uiState as TrojanUiState, viewModel, scrollTo)
    }
}


private fun LazyListScope.trojanSettings(
    uiState: TrojanUiState,
    viewModel: TrojanSettingsViewModel,
    scrollTo: (String) -> Unit,
) {
    headSettings(uiState, viewModel)
    item("password") {
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
        )
    }
    transportSettings(uiState, viewModel)
    muxSettings(uiState, viewModel)
    tlsSettings(uiState, viewModel, scrollTo)
}
