package tr.theyusa.v4war.ui.tools

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import tr.theyusa.v4war.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import tr.theyusa.v4war.compose.material3.Tab
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.util.fastCoerceIn
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.bg.BackendState
import tr.theyusa.v4war.bg.ServiceState
import tr.theyusa.v4war.compose.CapsuleTopBar
import tr.theyusa.v4war.compose.PlatformMenuIcon
import tr.theyusa.v4war.compose.SagerFab
import tr.theyusa.v4war.compose.StatsBar
import tr.theyusa.v4war.compose.rememberStatsBarHazeState
import tr.theyusa.v4war.compose.statsBarHazeSource
import tr.theyusa.v4war.compose.paddingExceptBottom
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.ui.MainViewModel
import tr.theyusa.v4war.ui.MainViewModelUiEvent
import tr.theyusa.v4war.ui.NavRoutes
import tr.theyusa.v4war.ui.getStringOrRes
import kotlinx.coroutines.launch
import tr.theyusa.v4war.resources.*
import tr.theyusa.v4war.repository.resolveRepository

private const val PAGE_NETWORK = 0
private const val PAGE_BACKUP = 1
private const val PAGE_DEBUG = 2

@Composable
fun ToolsScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    onDrawerClick: () -> Unit,
    onOpenTool: (NavRoutes.ToolsPage) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }

    val isExpert by DataStore.configurationStore
        .booleanFlow(Key.APP_EXPERT, false)
        .collectAsStateWithLifecycle(false)
    val pagerState = rememberPagerState(
        initialPage = PAGE_NETWORK,
        pageCount = { 2 + if (isExpert) 1 else 0 },
    )

    var bottomVisible by remember { mutableStateOf(true) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val topAppBarColors = TopAppBarDefaults.topAppBarColors()
    val appBarContainerColor by animateColorAsState(
        targetValue = lerp(
            topAppBarColors.containerColor,
            topAppBarColors.scrolledContainerColor,
            scrollBehavior.state.overlappedFraction.fastCoerceIn(0f, 1f),
        ),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "appBarContainerColor",
    )
    val windowInsets = WindowInsets.safeDrawing

    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()
    val statsBarHazeState = rememberStatsBarHazeState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                title = { Text(stringResource(Res.string.menu_tools)) },
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
        bottomBar = {
            if (serviceStatus.state == ServiceState.Connected) {
                StatsBar(
                    status = serviceStatus,
                    visible = bottomVisible,
                    mainViewModel = mainViewModel,
                    hazeState = statsBarHazeState,
                )
            }
        },
        floatingActionButton = {
            SagerFab(
                visible = bottomVisible,
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
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statsBarHazeSource(statsBarHazeState)
                .paddingExceptBottom(innerPadding),
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = appBarContainerColor,
            ) {
                Tab(
                    selected = pagerState.currentPage == PAGE_NETWORK,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(PAGE_NETWORK)
                        }
                    },
                    text = { Text(stringResource(Res.string.tools_network)) },
                )
                Tab(
                    selected = pagerState.currentPage == PAGE_BACKUP,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(PAGE_BACKUP)
                        }
                    },
                    text = { Text(stringResource(Res.string.backup)) },
                )
                if (isExpert) Tab(
                    selected = pagerState.currentPage == PAGE_DEBUG,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(PAGE_DEBUG)
                        }
                    },
                    text = { Text("DEBUG") },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    PAGE_NETWORK -> NetworkScreen(
                        onVisibleChange = { bottomVisible = it },
                        onOpenTool = onOpenTool,
                    )

                    PAGE_BACKUP -> BackupScreen(
                        onVisibleChange = { bottomVisible = it },
                        showSnackbar = { message ->
                            scope.launch {
                                snackbarState.showSnackbar(
                                    message = message,
                                    actionLabel = resolveRepository().getString(Res.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                    )

                    PAGE_DEBUG -> DebugScreen(
                        onVisibleChange = { bottomVisible = it },
                        showSnackbar = { message ->
                            scope.launch {
                                snackbarState.showSnackbar(
                                    message = message,
                                    actionLabel = resolveRepository().getString(Res.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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
