@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package tr.theyusa.v4war.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import tr.theyusa.v4war.compose.material3.Icon
import tr.theyusa.v4war.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import tr.theyusa.v4war.compose.material3.Switch
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ernestoyaquello.dragdropswipelazycolumn.DragDropSwipeLazyColumn
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItem
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItemScope
import com.ernestoyaquello.dragdropswipelazycolumn.config.DraggableSwipeableItemColors
import com.ernestoyaquello.dragdropswipelazycolumn.state.rememberDragDropSwipeLazyColumnState
import tr.theyusa.v4war.bg.BackendState
import androidx.compose.foundation.layout.fillMaxHeight
import tr.theyusa.v4war.compose.BoxedVerticalScrollbar
import tr.theyusa.v4war.compose.CapsuleActionButton
import tr.theyusa.v4war.compose.CapsuleTopBar
import tr.theyusa.v4war.compose.PlatformMenuIcon
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import tr.theyusa.v4war.compose.SagerFab
import tr.theyusa.v4war.compose.SimpleIconButton
import tr.theyusa.v4war.compose.TextButton
import tr.theyusa.v4war.compose.navigationBarsAlwaysInsets
import tr.theyusa.v4war.compose.paddingExceptBottom
import tr.theyusa.v4war.compose.rememberScrollHideState
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.database.ProfileManager
import tr.theyusa.v4war.database.RuleEntity
import tr.theyusa.v4war.database.RuleEntity.Companion.OUTBOUND_BLOCK
import tr.theyusa.v4war.database.RuleEntity.Companion.OUTBOUND_DIRECT
import tr.theyusa.v4war.database.RuleEntity.Companion.OUTBOUND_PROXY
import tr.theyusa.v4war.fmt.SingBoxOptions
import tr.theyusa.v4war.ktx.showAndDismissOld
import tr.theyusa.v4war.platform.PlatformInfo
import tr.theyusa.v4war.repository.resolveRepository
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.add_road
import tr.theyusa.v4war.resources.apply
import tr.theyusa.v4war.resources.apps_message
import tr.theyusa.v4war.resources.cag_dns
import tr.theyusa.v4war.resources.cancel
import tr.theyusa.v4war.resources.clear_profiles_message
import tr.theyusa.v4war.resources.confirm
import tr.theyusa.v4war.resources.delete
import tr.theyusa.v4war.resources.dns_only
import tr.theyusa.v4war.resources.drag_indicator
import tr.theyusa.v4war.resources.edit
import tr.theyusa.v4war.resources.error_title
import tr.theyusa.v4war.resources.layers
import tr.theyusa.v4war.resources.menu
import tr.theyusa.v4war.resources.menu_route
import tr.theyusa.v4war.resources.more
import tr.theyusa.v4war.resources.more_vert
import tr.theyusa.v4war.resources.need_reload
import tr.theyusa.v4war.resources.ok
import tr.theyusa.v4war.resources.process
import tr.theyusa.v4war.resources.removed
import tr.theyusa.v4war.resources.replay
import tr.theyusa.v4war.resources.route_add
import tr.theyusa.v4war.resources.route_block
import tr.theyusa.v4war.resources.route_bypass
import tr.theyusa.v4war.resources.route_manage_assets
import tr.theyusa.v4war.resources.route_proxy
import tr.theyusa.v4war.resources.route_reset
import tr.theyusa.v4war.resources.route_warn
import tr.theyusa.v4war.resources.undo
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource


@Composable
fun RouteScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    viewModel: RouteScreenViewModel = viewModel { RouteScreenViewModel() },
    onDrawerClick: () -> Unit,
    openRouteSettings: (Long) -> Unit,
    openAssets: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.commit()
        }
    }

    val dragDropListState = rememberDragDropSwipeLazyColumnState()
    val scrollHideVisible by rememberScrollHideState(dragDropListState.lazyListState)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMoreAction by remember { mutableStateOf(false) }
    var showResetAlert by remember { mutableStateOf(false) }

    fun needReload() = scope.launch {
        if (!DataStore.serviceState.started) return@launch
        val result = snackbarState.showSnackbar(
            message = resolveRepository().getString(Res.string.need_reload),
            actionLabel = resolveRepository().getString(Res.string.apply),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            resolveRepository().reloadService()
        }
    }

    LaunchedEffect(uiState.pendingDeleteCount) {
        if (uiState.pendingDeleteCount > 0) {
            val result = snackbarState.showAndDismissOld(
                message = resolveRepository().getPluralString(
                    Res.plurals.removed,
                    uiState.pendingDeleteCount,
                    uiState.pendingDeleteCount,
                ),
                actionLabel = resolveRepository().getString(Res.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undo()
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing

    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                title = { Text(stringResource(Res.string.menu_route)) },
                navigationIcon = {
                    PlatformMenuIcon(
                        imageVector = vectorResource(Res.drawable.menu),
                        contentDescription = stringResource(Res.string.menu),
                        onClick = onDrawerClick,
                    )
                },
                actions = {
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.add_road),
                            contentDescription = stringResource(Res.string.route_add),
                            onClick = {
                                openRouteSettings(-1L)
                            },
                        )
                    }
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.replay),
                            contentDescription = stringResource(Res.string.route_reset),
                            onClick = { showResetAlert = true },
                        )
                    }
                    CapsuleActionButton {
                        Box {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.more_vert),
                                contentDescription = stringResource(Res.string.more),
                                onClick = { showMoreAction = true },
                            )
                            DropdownMenu(
                                expanded = showMoreAction,
                                onDismissRequest = { showMoreAction = false },
                                shape = MenuDefaults.standaloneGroupShape,
                                containerColor = MenuDefaults.groupStandardContainerColor,
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.route_manage_assets)) },
                                    onClick = {
                                        showMoreAction = false
                                        openAssets()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = vectorResource(Res.drawable.layers),
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                        }
                    }
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
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .paddingExceptBottom(innerPadding),
            ) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    elevation = CardDefaults.elevatedCardElevation(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val uriHandler = LocalUriHandler.current
                        Text(
                            text = stringResource(Res.string.route_warn),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    uriHandler.openUri("https://github.com/TheYusa")
                                },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                DragDropSwipeLazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = dragDropListState,
                    items = uiState.rules.toImmutableList(),
                    key = { it.id },
                    contentType = { 0 },
                    contentPadding = PaddingValues(
                        bottom = navigationBarsAlwaysInsets()
                            .asPaddingValues()
                            .calculateBottomPadding(),
                    ),
                    userScrollEnabled = true,
                    onIndicesChangedViaDragAndDrop = {
                        viewModel.submitReorder(it)
                        needReload()
                    },
                ) { _, rule ->
                    val swipeState = rememberSwipeToDismissBoxState()

                    // Monitor swipe state changes and perform deletion when user completes swipe gesture.
                    // After deletion, immediately reset state to Settled to prevent re-triggering
                    // when item is restored via undo. Without this, the preserved swipeState
                    // (due to stable key) would cause onDismiss to fire again on recomposition.
                    LaunchedEffect(swipeState.currentValue) {
                        if (swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
                            viewModel.undoableRemove(rule.id)
                            swipeState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }

                    DraggableSwipeableItem(
                        modifier = Modifier.animateDraggableSwipeableItem(),
                        colors = DraggableSwipeableItemColors.createRemembered(
                            containerBackgroundColor = Color.Transparent,
                            containerBackgroundColorWhileDragged = Color.Transparent,
                        ),
                    ) {
                        SwipeToDismissBox(
                            state = swipeState,
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(vectorResource(Res.drawable.delete), null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RuleCard(
                                rule = rule,
                                viewModel = viewModel,
                                onNeedReload = { needReload() },
                                openRouteSettings = openRouteSettings,
                            )
                        }
                    }
                }

                // Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }

            BoxedVerticalScrollbar(
                modifier = Modifier.fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = dragDropListState.lazyListState),
                style = defaultMaterialScrollbarStyle().copy(
                    thickness = 12.dp,
                ),
            )
        }
    }

    if (showResetAlert) AlertDialog(
        onDismissRequest = { showResetAlert = false },
        title = { Text(stringResource(Res.string.confirm)) },
        text = { Text(stringResource(Res.string.clear_profiles_message)) },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                showResetAlert = false
                viewModel.reset()
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.cancel)) {
                showResetAlert = false
            }
        },
    )

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

@Composable
private fun DraggableSwipeableItemScope<RuleEntity>.RuleCard(
    modifier: Modifier = Modifier,
    rule: RuleEntity,
    viewModel: RouteScreenViewModel,
    onNeedReload: () -> Unit,
    openRouteSettings: (Long) -> Unit,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.drag_indicator),
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp)
                    .dragDropModifier(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = rule.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(
                        onClick = {
                            openRouteSettings(rule.id)
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.edit),
                            contentDescription = stringResource(Res.string.edit),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                ) {
                    Text(
                        text = rule.summary(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when (rule.action) {
                            "", SingBoxOptions.ACTION_ROUTE -> rule.displayOutbound()
                            else -> "action: ${rule.action}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )

                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = {
                            viewModel.toggleEnabled(rule)
                            onNeedReload()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleEntity.summary(): String {
    if (dnsOnly) return stringResource(Res.string.dns_only)

    var summary = ""
    if (domains.isNotBlank()) summary += "$domains\n"
    if (ip.isNotBlank()) summary += "$ip\n"
    if (source.isNotBlank()) summary += "source: $source\n"
    if (sourcePort.isNotBlank()) summary += "sourcePort: $sourcePort\n"
    if (port.isNotBlank()) summary += "port: $port\n"
    if (network.isNotEmpty()) summary += "network: $network\n"
    if (protocol.isNotEmpty()) summary += "protocol: $protocol\n"
    if (clientType.isNotEmpty()) summary += "client: $clientType\n"
    if (packages.isNotEmpty()) {
        summary += if (PlatformInfo.isAndroid) {
            pluralStringResource(Res.plurals.apps_message, packages.size, packages.size)
        } else {
            "${stringResource(Res.string.process)}: ${packages.joinToString(", ")}"
        } + "\n"
    }
    if (ssid.isNotBlank()) summary += "ssid: $ssid\n"
    if (bssid.isNotBlank()) summary += "bssid: $bssid\n"
    if (clashMode.isNotBlank()) summary += "clashMode: $clashMode\n"
    if (networkType.isNotEmpty()) summary += "networkType: $networkType\n"
    if (networkIsExpensive) summary += "networkIsExpensive\n"
    if (networkInterfaceAddress.isNotEmpty()) summary += "networkInterfaceAddress: $networkInterfaceAddress\n"

    if (overrideAddress.isNotBlank()) summary += "overrideAddress: $overrideAddress\n"
    if (overridePort > 0) summary += "overridePort: $overridePort\n"
    if (tlsFragment) {
        summary += "TLS fragment\n"
        if (tlsFragmentFallbackDelay.isNotBlank()) {
            summary += "tlsFragmentFallbackDelay: $tlsFragmentFallbackDelay\n"
        }
    }
    if (tlsRecordFragment) {
        summary += "TLS record fragment\n"
    }

    if (resolveStrategy.isNotBlank()) summary += "resolveStrategy: $resolveStrategy\n"
    if (resolveDisableCache) summary += "resolveDisableCache\n"
    if (resolveRewriteTTL >= 0) summary += "resolveRewriteTTL: $resolveRewriteTTL\n"
    if (resolveClientSubnet.isNotBlank()) summary += "resolveClientSubnet: $resolveClientSubnet\n"

    if (sniffTimeout.isNotBlank()) summary += "sniffTimeout: $sniffTimeout\n"
    if (sniffers.isNotEmpty()) summary += "sniffers: $sniffers\n"

    if (customConfig.isNotBlank()) summary += stringResource(Res.string.menu_route) + "\n"
    if (customDnsConfig.isNotBlank()) summary += stringResource(Res.string.cag_dns) + "\n"

    // Even has "\n" suffix, TextView's "..." will be added and remove the last "\n".
    val lines = summary.trim().split("\n")
    return if (lines.size > 5) {
        lines.subList(0, 5).joinToString("\n", postfix = "\n...")
    } else {
        summary.trim()
    }
}

@Composable
private fun RuleEntity.displayOutbound(): String {
    return when (outbound) {
        OUTBOUND_PROXY -> stringResource(Res.string.route_proxy)
        OUTBOUND_DIRECT -> stringResource(Res.string.route_bypass)
        OUTBOUND_BLOCK -> stringResource(Res.string.route_block)
        else -> ProfileManager.getProfile(outbound)?.displayName()
            ?: stringResource(Res.string.error_title)
    }
}
