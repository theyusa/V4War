@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package tr.theyusa.v4war.ui.settings

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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import tr.theyusa.v4war.bg.Executable
import tr.theyusa.v4war.compose.BoxedVerticalScrollbar
import tr.theyusa.v4war.compose.CapsuleTopBar
import tr.theyusa.v4war.compose.SimpleIconButton
import tr.theyusa.v4war.compose.fadingEdge
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.compose.preferenceGroup
import tr.theyusa.v4war.compose.withNavigation
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.database.SagerDatabase
import tr.theyusa.v4war.ktx.onIoDispatcher
import tr.theyusa.v4war.ktx.restartApplication
import tr.theyusa.v4war.ktx.runOnDefaultDispatcher
import tr.theyusa.v4war.ktx.showAndDismissOld
import tr.theyusa.v4war.repository.resolveRepository
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.apply
import tr.theyusa.v4war.resources.arrow_back
import tr.theyusa.v4war.resources.back
import tr.theyusa.v4war.resources.cag_dns
import tr.theyusa.v4war.resources.cag_misc
import tr.theyusa.v4war.resources.general_settings
import tr.theyusa.v4war.resources.inbound_settings
import tr.theyusa.v4war.resources.need_reload
import tr.theyusa.v4war.resources.need_restart
import tr.theyusa.v4war.resources.ntp_category
import tr.theyusa.v4war.resources.protocol_settings
import tr.theyusa.v4war.resources.route_options
import tr.theyusa.v4war.ui.NavRoutes

@Composable
fun SettingsPageScreen(
    kind: NavRoutes.SettingsPage.Kind,
    onBackPress: () -> Unit,
    openAppManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        onIoDispatcher {
            DataStore.initGlobal()
        }
    }

    fun needReload() = scope.launch {
        if (!DataStore.serviceState.started) return@launch
        val result = snackbarState.showAndDismissOld(
            message = resolveRepository().getString(Res.string.need_reload),
            actionLabel = resolveRepository().getString(Res.string.apply),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.Dismissed) return@launch
        resolveRepository().reloadService()
    }

    fun needRestart() = scope.launch {
        val result = snackbarState.showAndDismissOld(
            message = resolveRepository().getString(Res.string.need_restart),
            actionLabel = resolveRepository().getString(Res.string.apply),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.Dismissed) return@launch
        resolveRepository().stopService()
        runOnDefaultDispatcher {
            delay(500)
            SagerDatabase.instance.close()
            Executable.killAll(true)
            restartApplication()
        }
    }

    val title = when (kind) {
        NavRoutes.SettingsPage.Kind.General -> Res.string.general_settings
        NavRoutes.SettingsPage.Kind.Route -> Res.string.route_options
        NavRoutes.SettingsPage.Kind.Protocol -> Res.string.protocol_settings
        NavRoutes.SettingsPage.Kind.Dns -> Res.string.cag_dns
        NavRoutes.SettingsPage.Kind.Inbound -> Res.string.inbound_settings
        NavRoutes.SettingsPage.Kind.Misc -> Res.string.cag_misc
        NavRoutes.SettingsPage.Kind.Ntp -> Res.string.ntp_category
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
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
                        when (kind) {
                            NavRoutes.SettingsPage.Kind.General -> GeneralSettingsGroup(
                                needReload = { needReload() },
                                needRestart = { needRestart() },
                            )
                            NavRoutes.SettingsPage.Kind.Route -> RouteSettingsGroup(
                                needReload = { needReload() },
                                openAppManager = openAppManager,
                            )
                            NavRoutes.SettingsPage.Kind.Protocol -> ProtocolSettingsGroup(
                                needReload = { needReload() },
                            )
                            NavRoutes.SettingsPage.Kind.Dns -> DnsSettingsGroup(
                                needReload = { needReload() },
                            )
                            NavRoutes.SettingsPage.Kind.Inbound -> InboundSettingsGroup(
                                needReload = { needReload() },
                            )
                            NavRoutes.SettingsPage.Kind.Misc -> MiscSettingsGroup(
                                needReload = { needReload() },
                                needRestart = { needRestart() },
                            )
                            NavRoutes.SettingsPage.Kind.Ntp -> NtpSettingsGroup(
                                needReload = { needReload() },
                            )
                        }
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
}
