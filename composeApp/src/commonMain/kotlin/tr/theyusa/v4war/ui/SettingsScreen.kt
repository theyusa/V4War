@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package tr.theyusa.v4war.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import tr.theyusa.v4war.bg.BackendState
import tr.theyusa.v4war.compose.BoxedVerticalScrollbar
import tr.theyusa.v4war.compose.CapsuleTopBar
import tr.theyusa.v4war.compose.IconMaskColors
import tr.theyusa.v4war.compose.MaskedIcon
import tr.theyusa.v4war.compose.PlatformMenuIcon
import tr.theyusa.v4war.compose.PreferenceCategory
import tr.theyusa.v4war.compose.PreferenceDivider
import tr.theyusa.v4war.compose.SagerFab
import tr.theyusa.v4war.compose.fadingEdge
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.compose.preferenceGroup
import tr.theyusa.v4war.compose.rememberHapticClick
import tr.theyusa.v4war.compose.rememberScrollHideState
import tr.theyusa.v4war.compose.withNavigation
import tr.theyusa.v4war.repository.resolveRepository
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.cag_dns
import tr.theyusa.v4war.resources.cag_misc
import tr.theyusa.v4war.resources.cast_connected
import tr.theyusa.v4war.resources.dns
import tr.theyusa.v4war.resources.flight_takeoff
import tr.theyusa.v4war.resources.general_settings
import tr.theyusa.v4war.resources.inbound_settings
import tr.theyusa.v4war.resources.info
import tr.theyusa.v4war.resources.menu
import tr.theyusa.v4war.resources.menu_about
import tr.theyusa.v4war.resources.more
import tr.theyusa.v4war.resources.nat
import tr.theyusa.v4war.resources.nfc
import tr.theyusa.v4war.resources.ntp_category
import tr.theyusa.v4war.resources.ok
import tr.theyusa.v4war.resources.plugin
import tr.theyusa.v4war.resources.protocol_settings
import tr.theyusa.v4war.resources.route_options
import tr.theyusa.v4war.resources.router
import tr.theyusa.v4war.resources.settings
import tr.theyusa.v4war.resources.timelapse
import tr.theyusa.v4war.resources.tools_network
import tr.theyusa.v4war.resources.wifi

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    onDrawerClick: () -> Unit,
    openSettingsPage: (NavRoutes.SettingsPage.Kind) -> Unit,
    openTools: () -> Unit,
    openPlugin: () -> Unit,
    openAbout: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollHideVisible by rememberScrollHideState(listState)
    val hapticClick = rememberHapticClick()
    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = {
                    PlatformMenuIcon(
                        imageVector = vectorResource(Res.drawable.menu),
                        contentDescription = stringResource(Res.string.menu),
                        onClick = onDrawerClick,
                    )
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            SagerFab(
                visible = scrollHideVisible,
                status = serviceStatus,
                showSnackbar = { message ->
                    scope.launch {
                        snackbarState.showSnackbar(
                            message = getStringOrRes(message),
                            actionLabel = resolveRepository().getString(Res.string.ok),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                mainViewModel = mainViewModel,
            )
        },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation()
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fadingEdge(listState),
                    contentPadding = contentPadding,
                ) {
                    preferenceGroup {
                        Preference(
                            title = { Text(stringResource(Res.string.general_settings)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.settings,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            onClick = {
                                hapticClick()
                                openSettingsPage(NavRoutes.SettingsPage.Kind.General)
                            },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.route_options)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.router,
                                    color = IconMaskColors.IconLightGreen,
                                )
                            },
                            onClick = {
                                hapticClick()
                                openSettingsPage(NavRoutes.SettingsPage.Kind.Route)
                            },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.protocol_settings)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.flight_takeoff,
                                    color = IconMaskColors.IconLightYellow,
                                )
                            },
                            onClick = {
                                hapticClick()
                                openSettingsPage(NavRoutes.SettingsPage.Kind.Protocol)
                            },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.cag_dns)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.dns,
                                    color = IconMaskColors.IconCyan,
                                )
                            },
                            onClick = {
                                hapticClick()
                                openSettingsPage(NavRoutes.SettingsPage.Kind.Dns)
                            },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.inbound_settings)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.nat,
                                    color = IconMaskColors.IconCoral,
                                )
                            },
                            onClick = {
                                hapticClick()
                                openSettingsPage(NavRoutes.SettingsPage.Kind.Inbound)
                            },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.cag_misc)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.cast_connected,
                                    color = IconMaskColors.IconWarmGray,
                                )
                            },
                            onClick = {
                                hapticClick()
                                openSettingsPage(NavRoutes.SettingsPage.Kind.Misc)
                            },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.ntp_category)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.timelapse,
                                    color = IconMaskColors.IconLightPink,
                                )
                            },
                            onClick = {
                                hapticClick()
                                openSettingsPage(NavRoutes.SettingsPage.Kind.Ntp)
                            },
                        )
                    }

                    item { PreferenceCategory(text = { Text(stringResource(Res.string.more)) }) }
                    preferenceGroup {
                        Preference(
                            title = { Text(stringResource(Res.string.tools_network)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.wifi,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            onClick = {
                                hapticClick()
                                openTools()
                            },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.plugin)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.nfc,
                                    color = IconMaskColors.IconCyan,
                                )
                            },
                            onClick = {
                                hapticClick()
                                openPlugin()
                            },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.menu_about)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.info,
                                    color = IconMaskColors.IconLavender,
                                )
                            },
                            onClick = {
                                hapticClick()
                                openAbout()
                            },
                        )
                    }
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.Snackbar -> scope.launch {
                    snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = resolveRepository().getString(Res.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                is MainViewModelUiEvent.SnackbarWithAction -> scope.launch {
                    val result = snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = getStringOrRes(event.actionLabel),
                        duration = SnackbarDuration.Short,
                    )
                    event.callback(result)
                }

                else -> {}
            }
        }
    }
}
