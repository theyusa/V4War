/******************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-git@sekai.icu>                  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package tr.theyusa.v4war.tasker

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.AlertDialog
import tr.theyusa.v4war.compose.material3.Icon
import androidx.compose.material3.Scaffold
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.compose.SimpleIconButton
import tr.theyusa.v4war.compose.TextButton
import tr.theyusa.v4war.compose.paddingExceptBottom
import tr.theyusa.v4war.compose.theme.AppTheme
import tr.theyusa.v4war.database.ProfileManager
import tr.theyusa.v4war.ktx.intListN
import tr.theyusa.v4war.permission.LocalPermissionPlatform
import tr.theyusa.v4war.permission.rememberAndroidPermissionPlatform
import tr.theyusa.v4war.repository.resolveRepository
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.apply
import tr.theyusa.v4war.resources.close
import tr.theyusa.v4war.resources.done
import tr.theyusa.v4war.resources.layers
import tr.theyusa.v4war.resources.menu_configuration
import tr.theyusa.v4war.resources.no
import tr.theyusa.v4war.resources.not_set
import tr.theyusa.v4war.resources.ok
import tr.theyusa.v4war.resources.question_mark
import tr.theyusa.v4war.resources.route_profile
import tr.theyusa.v4war.resources.router
import tr.theyusa.v4war.resources.tasker_action
import tr.theyusa.v4war.resources.tasker_action_start_service
import tr.theyusa.v4war.resources.tasker_action_stop_service
import tr.theyusa.v4war.resources.tasker_blurb_start_profile
import tr.theyusa.v4war.resources.tasker_settings
import tr.theyusa.v4war.resources.tasker_start_current_profile
import tr.theyusa.v4war.resources.unsaved_changes_prompt
import tr.theyusa.v4war.ui.ComposeActivity
import tr.theyusa.v4war.ui.configuration.ProfileSelectSheet
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

class TaskerActivity : ComposeActivity() {

    private val viewModel by viewModels<TaskerActivityViewModel>()
    private lateinit var settings: TaskerBundle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            reloadIntent(intent)
        }

        setContent {
            val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
            var showBackAlert by remember { mutableStateOf(false) }
            BackHandler(enabled = isDirty) {
                showBackAlert = true
            }
            var profileSelectSession by remember { mutableStateOf<ProfileSelectSession?>(null) }

            val windowInsets = WindowInsets.safeDrawing
            val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

            val platformPermission = rememberAndroidPermissionPlatform()

            CompositionLocalProvider(
                LocalPermissionPlatform provides platformPermission,
            ) {
                AppTheme {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(Res.string.tasker_settings)) },
                                navigationIcon = {
                                    SimpleIconButton(
                                        imageVector = vectorResource(Res.drawable.close),
                                        contentDescription = stringResource(Res.string.close),
                                        onClick = {
                                            onBackPressedDispatcher.onBackPressed()
                                        },
                                    )
                                },
                                actions = {
                                    SimpleIconButton(
                                        imageVector = vectorResource(Res.drawable.done),
                                        contentDescription = stringResource(Res.string.apply),
                                        onClick = {
                                            saveAndExit()
                                        },
                                    )
                                },
                                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                                scrollBehavior = scrollBehavior,
                            )
                        },
                    ) { innerPadding ->
                        Column(modifier = Modifier.paddingExceptBottom(innerPadding)) {
                            TaskerPreference(
                                onOpenProfileSelect = { preSelected, onSelected ->
                                    profileSelectSession =
                                        ProfileSelectSession(preSelected, onSelected)
                                },
                            )

                            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                        }
                    }

                    if (showBackAlert) AlertDialog(
                        onDismissRequest = { showBackAlert = false },
                        confirmButton = {
                            TextButton(stringResource(Res.string.ok)) {
                                saveAndExit()
                            }
                        },
                        dismissButton = {
                            TextButton(stringResource(Res.string.no)) {
                                finish()
                            }
                        },
                        icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
                        title = { Text(stringResource(Res.string.unsaved_changes_prompt)) },
                    )

                    val session = profileSelectSession
                    if (session != null) {
                        ProfileSelectSheet(
                            preSelected = session.preSelected,
                            onDismiss = { profileSelectSession = null },
                            onSelected = { id ->
                                session.onSelected(id)
                                profileSelectSession = null
                            },
                        )
                    }
                }
            }
        }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        reloadIntent(intent)
    }

    private fun reloadIntent(intent: Intent) {
        settings = TaskerBundle.fromIntent(intent)
        viewModel.loadFromSetting(settings.action, settings.profileId)
    }

    private fun saveAndExit() {
        setResult(RESULT_OK, buildIntent())
        finish()
    }

    private fun buildIntent(): Intent {
        val uiState = viewModel.uiState.value
        val action = uiState.action
        val profileId = uiState.profileID
        settings.action = action
        settings.profileId = profileId
        var blurb = ""
        when (action) {
            TaskerBundle.ACTION_START -> {
                if (profileId > 0) {
                    val entity = ProfileManager.getProfile(profileId)
                    if (entity != null) {
                        blurb = runBlocking {
                            resolveRepository().getString(
                                Res.string.tasker_blurb_start_profile, entity.displayName(),
                            )
                        }
                    }
                }
                if (blurb.isBlank()) runBlocking {
                    blurb = resolveRepository().getString(Res.string.tasker_action_start_service)
                }
            }

            TaskerBundle.ACTION_STOP -> runBlocking {
                blurb = resolveRepository().getString(Res.string.tasker_action_stop_service)
            }
        }
        return Intent().apply {
            putExtra(TaskerBundle.EXTRA_BUNDLE, settings.bundle)
            putExtra(TaskerBundle.EXTRA_STRING_BLURB, blurb)
        }
    }

    @Composable
    private fun TaskerPreference(
        onOpenProfileSelect: (preSelected: Long?, onSelected: (Long) -> Unit) -> Unit,
    ) {
        ProvidePreferenceLocals {
            val uiState by viewModel.uiState.collectAsState()

            fun actionText(action: Int) = when (action) {
                TaskerBundle.ACTION_START -> Res.string.tasker_action_start_service
                TaskerBundle.ACTION_STOP -> Res.string.tasker_action_stop_service
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.action,
                onValueChange = { viewModel.setAction(it) },
                values = intListN(2),
                title = { Text(stringResource(Res.string.tasker_action)) },
                icon = { Icon(vectorResource(Res.drawable.layers), null) },
                summary = { Text(stringResource(actionText(uiState.action))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = {
                    val text = runBlocking { resolveRepository().getString(actionText(it)) }
                    AnnotatedString(text)
                },
            )

            ListPreference(
                value = uiState.profileID,
                onValueChange = {
                    if (it == -1L) {
                        viewModel.setProfileID(it)
                    } else {
                        onOpenProfileSelect(uiState.profileID.takeIf { id -> id > 0 }) { id ->
                            viewModel.setProfileID(id)
                        }
                    }
                },
                values = listOf(-1L, 0L),
                title = { Text(stringResource(Res.string.menu_configuration)) },
                enabled = uiState.action == TaskerBundle.ACTION_START,
                icon = { Icon(vectorResource(Res.drawable.router), null) },
                summary = {
                    val summary = ProfileManager.getProfile(uiState.profileID)?.displayName()
                        ?: stringResource(Res.string.not_set)
                    Text(summary)
                },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = {
                    val id = if (it == -1L) {
                        Res.string.tasker_start_current_profile
                    } else {
                        Res.string.route_profile
                    }
                    val text = runBlocking { resolveRepository().getString(id) }
                    AnnotatedString(text)
                },
            )
        }
    }

}

private data class ProfileSelectSession(
    val preSelected: Long?,
    val onSelected: (Long) -> Unit,
)
