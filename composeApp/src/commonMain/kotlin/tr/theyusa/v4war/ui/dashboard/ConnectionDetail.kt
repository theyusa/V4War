package tr.theyusa.v4war.ui.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.CardDefaults
import tr.theyusa.v4war.compose.material3.Checkbox
import tr.theyusa.v4war.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tr.theyusa.v4war.compose.connectionStatusColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tr.theyusa.v4war.compose.BoxedVerticalScrollbar
import tr.theyusa.v4war.compose.SimpleIconButton
import tr.theyusa.v4war.compose.withNavigation
import tr.theyusa.v4war.fmt.SingBoxOptions
import tr.theyusa.v4war.ktx.blankAsNull
import tr.theyusa.v4war.ktx.emptyAsNull
import tr.theyusa.v4war.libcore.Libcore
import tr.theyusa.v4war.repository.resolveRepository
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.add_road
import tr.theyusa.v4war.resources.arrow_back
import tr.theyusa.v4war.resources.back
import tr.theyusa.v4war.resources.cancel
import tr.theyusa.v4war.resources.chain
import tr.theyusa.v4war.resources.close
import tr.theyusa.v4war.resources.closed_time
import tr.theyusa.v4war.resources.connection_status
import tr.theyusa.v4war.resources.connection_status_active
import tr.theyusa.v4war.resources.connection_status_closed
import tr.theyusa.v4war.resources.create_rule
import tr.theyusa.v4war.resources.delete_forever
import tr.theyusa.v4war.resources.destination_address
import tr.theyusa.v4war.resources.done
import tr.theyusa.v4war.resources.download
import tr.theyusa.v4war.resources.http_host
import tr.theyusa.v4war.resources.inbound
import tr.theyusa.v4war.resources.ip_version
import tr.theyusa.v4war.resources.network
import tr.theyusa.v4war.resources.ok
import tr.theyusa.v4war.resources.outbound
import tr.theyusa.v4war.resources.outbound_rule
import tr.theyusa.v4war.resources.process
import tr.theyusa.v4war.resources.protocol
import tr.theyusa.v4war.resources.source_address
import tr.theyusa.v4war.resources.start_time
import tr.theyusa.v4war.resources.upload
import tr.theyusa.v4war.ui.RouteSettingsUiState
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

private enum class ConnectionFields {
    // STATUS,
    // INBOUND,
    // IP_VERSION,
    NETWORK,

    // UPLOAD_TOTAL,
    // DOWNLOAD_TOTAL,
    // START,
    SOURCE,
    DESTINATION,
    HOST,

    // MATCHED_RULE,
    // OUTBOUND,
    // CHAIN,
    PROTOCOL,
    PROCESS,
}

@Composable
fun ConnectionDetailScreen(
    modifier: Modifier = Modifier,
    uuid: String,
    viewModel: ConnectionDetailViewModel = viewModel { ConnectionDetailViewModel(uuid) },
    popup: () -> Unit,
    openRouteSettings: (RouteSettingsUiState) -> Unit,
) {
    var isSelecting by remember { mutableStateOf(false) }
    var selectedField by remember { mutableStateOf(emptySet<ConnectionFields>()) }
    val connection by viewModel.connection.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(uuid)
                },
                navigationIcon = {
                    if (isSelecting) {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.close),
                            contentDescription = stringResource(Res.string.cancel),
                            onClick = {
                                selectedField = emptySet()
                                isSelecting = false
                            },
                        )
                    } else {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.arrow_back),
                            contentDescription = stringResource(Res.string.back),
                            onClick = popup,
                        )
                    }
                },
                actions = {
                    AppBarRow {
                        if (isSelecting) {
                            clickableItem(
                                onClick = {
                                    openRouteSettings(
                                        createRouteDraft(selectedField, connection),
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = vectorResource(Res.drawable.done),
                                        contentDescription = stringResource(Res.string.ok),
                                    )
                                },
                                label = runBlocking { resolveRepository().getString(Res.string.ok) },
                            )
                        } else {
                            clickableItem(
                                onClick = { isSelecting = true },
                                icon = {
                                    Icon(
                                        imageVector = vectorResource(Res.drawable.add_road),
                                        contentDescription = stringResource(Res.string.create_rule),
                                    )
                                },
                                label = runBlocking { resolveRepository().getString(Res.string.create_rule) },
                            )
                            clickableItem(
                                onClick = {
                                    viewModel.closeConnection(uuid)
                                    popup()
                                },
                                icon = {
                                    Icon(
                                        imageVector = vectorResource(Res.drawable.delete_forever),
                                        contentDescription = stringResource(Res.string.close),
                                    )
                                },
                                label = runBlocking { resolveRepository().getString(Res.string.close) },
                            )
                        }
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        val contentPadding = innerPadding.withNavigation()
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("status", 0) {
                    ConnectionDataCard(
                        field = Res.string.connection_status,
                        value = {
                            Text(
                                text = if (connection.isClosed) {
                                    stringResource(Res.string.connection_status_closed)
                                } else {
                                    stringResource(Res.string.connection_status_active)
                                },
                                color = connectionStatusColor(connection.isClosed),
                            )
                        },
                        isSelecting = isSelecting,
                        isSelectable = false,
                    )
                }
                item("inbound", 1) {
                    ConnectionDataCard(
                        field = Res.string.inbound,
                        value = { Text(connection.inbound) },
                        isSelecting = isSelecting,
                        isSelectable = false,
                    )
                }
                if (connection.ipVersion != null) item("ip_version", 1) {
                    ConnectionDataCard(
                        field = Res.string.ip_version,
                        value = { Text(connection.ipVersion.toString()) },
                        isSelecting = isSelecting,
                        isSelectable = false,
                    )
                }
                item("network", 1) {
                    ConnectionDataCard(
                        field = Res.string.network,
                        value = { Text(connection.network) },
                        isSelecting = isSelecting,
                        isSelected = ConnectionFields.NETWORK in selectedField,
                        onSelectedChange = { checked ->
                            selectedField = selectedField.toMutableSet().apply {
                                if (checked) {
                                    add(ConnectionFields.NETWORK)
                                } else {
                                    remove(ConnectionFields.NETWORK)
                                }
                            }
                        },
                    )
                }
                item("upload_total", 1) {
                    ConnectionDataCard(
                        field = Res.string.upload,
                        value = { Text(Libcore.formatBytes(connection.uploadTotal)) },
                        isSelecting = isSelecting,
                        isSelectable = false,
                    )
                }
                item("download_total", 1) {
                    ConnectionDataCard(
                        field = Res.string.download,
                        value = { Text(Libcore.formatBytes(connection.downloadTotal)) },
                        isSelecting = isSelecting,
                        isSelectable = false,
                    )
                }
                item("start", 1) {
                    ConnectionDataCard(
                        field = Res.string.start_time,
                        value = { Text(connection.startedAt) },
                        isSelecting = isSelecting,
                        isSelectable = false,
                    )
                }
                connection.closedAt.emptyAsNull()?.let { closedAt ->
                    item("closed", 1) {
                        ConnectionDataCard(
                            field = Res.string.closed_time,
                            value = { Text(closedAt) },
                            isSelecting = isSelecting,
                            isSelectable = false,
                        )
                    }
                }
                item("source", 1) {
                    ConnectionDataCard(
                        field = Res.string.source_address,
                        value = { Text(connection.src) },
                        isSelecting = isSelecting,
                        isSelected = ConnectionFields.SOURCE in selectedField,
                        onSelectedChange = { checked ->
                            selectedField = selectedField.toMutableSet().apply {
                                if (checked) {
                                    add(ConnectionFields.SOURCE)
                                } else {
                                    remove(ConnectionFields.SOURCE)
                                }
                            }
                        },
                    )
                }
                item("destination", 1) {
                    ConnectionDataCard(
                        field = Res.string.destination_address,
                        value = { Text(connection.dst) },
                        isSelecting = isSelecting,
                        isSelected = ConnectionFields.DESTINATION in selectedField,
                        onSelectedChange = { checked ->
                            selectedField = selectedField.toMutableSet().apply {
                                if (checked) {
                                    add(ConnectionFields.DESTINATION)
                                } else {
                                    remove(ConnectionFields.DESTINATION)
                                }
                            }
                        },
                    )
                }
                if (connection.host.isNotBlank()) {
                    item("host", 1) {
                        ConnectionDataCard(
                            field = Res.string.http_host,
                            value = { Text(connection.host) },
                            isSelecting = isSelecting,
                            isSelected = ConnectionFields.HOST in selectedField,
                            onSelectedChange = { checked ->
                                selectedField = selectedField.toMutableSet().apply {
                                    if (checked) {
                                        add(ConnectionFields.HOST)
                                    } else {
                                        remove(ConnectionFields.HOST)
                                    }
                                }
                            },
                        )
                    }
                }
                item("matched_rule", 1) {
                    ConnectionDataCard(
                        field = Res.string.outbound_rule,
                        value = { Text(connection.matchedRule) },
                        isSelecting = isSelecting,
                        isSelectable = false,
                    )
                }
                item("outbound", 1) {
                    ConnectionDataCard(
                        field = Res.string.outbound,
                        value = { Text(connection.outbound) },
                        isSelecting = isSelecting,
                        isSelectable = false,
                    )
                }
                item("chain", 1) {
                    ConnectionDataCard(
                        field = Res.string.chain,
                        value = { Text(connection.chain) },
                        isSelecting = isSelecting,
                        isSelectable = false,
                    )
                }
                if (connection.protocol != null) item("protocol", 1) {
                    ConnectionDataCard(
                        field = Res.string.protocol,
                        value = { Text(connection.protocol!!) },
                        isSelecting = isSelecting,
                        isSelected = ConnectionFields.PROTOCOL in selectedField,
                        onSelectedChange = { checked ->
                            selectedField = selectedField.toMutableSet().apply {
                                if (checked) {
                                    add(ConnectionFields.PROTOCOL)
                                } else {
                                    remove(ConnectionFields.PROTOCOL)
                                }
                            }
                        },
                    )
                }
                val process = connection.processes?.firstOrNull()
                val processText = buildProcessText(connection.uid, process)
                if (processText.isNotEmpty()) item("process", 1) {
                    val uid = connection.uid
                    var processInfo by remember { mutableStateOf<ProcessInfo?>(null) }
                    LaunchedEffect(Unit) {
                        processInfo = viewModel.resolveProcessInfo(process, uid)
                    }
                    val processLabel = processInfo?.label.blankAsNull()
                    val openProcessAppInfo = rememberOpenProcessAppInfo(process)
                    ConnectionDataCard(
                        field = Res.string.process,
                        value = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (processLabel != null) {
                                        Text(
                                            text = processLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    Text(processText)
                                }
                                processInfo?.icon?.let { icon ->
                                    ProcessIcon(
                                        icon = icon,
                                        contentDescription = processLabel
                                            ?: processInfo?.packageName,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                            }
                        },
                        isSelecting = isSelecting,
                        isSelected = ConnectionFields.PROCESS in selectedField,
                        onLongClick = openProcessAppInfo,
                        onSelectedChange = { checked ->
                            selectedField = selectedField.toMutableSet().apply {
                                if (checked) {
                                    add(ConnectionFields.PROCESS)
                                } else {
                                    remove(ConnectionFields.PROCESS)
                                }
                            }
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

private fun buildProcessText(uid: Int, process: String?): String {
    var text = process.orEmpty()
    if (uid >= 0) {
        text = "[$uid] $text"
    }
    return text
}

@Composable
private fun ConnectionDataCard(
    modifier: Modifier = Modifier,
    field: StringResource,
    value: @Composable () -> Unit,
    isSelecting: Boolean = false,
    isSelected: Boolean = false,
    isSelectable: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onSelectedChange: (Boolean) -> Unit = {},
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(80),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rotation",
    )
    val onClick = {
        if (isSelecting) {
            onSelectedChange(!isSelected)
        }
    }
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .graphicsLayer {
            rotationZ = if (isSelecting && isSelectable) {
                rotation
            } else {
                0f
            }
        }

    val cardColors = CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    )
    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelecting) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectedChange,
                    enabled = isSelectable,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(field),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                SelectionContainer {
                    ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                        value()
                    }
                }
            }
        }
    }
    if (onLongClick == null) {
        OutlinedCard(
            onClick = onClick,
            modifier = cardModifier,
            colors = cardColors,
        ) {
            cardContent()
        }
    } else {
        OutlinedCard(
            modifier = cardModifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
            colors = cardColors,
        ) {
            cardContent()
        }
    }
}

private fun createRouteDraft(
    fields: Set<ConnectionFields>,
    connection: ConnectionDetailState,
): RouteSettingsUiState {
    var domains = ""
    var ip = ""
    var port = ""
    var source = ""
    var sourcePort = ""
    var network = emptySet<String>()
    var protocol = emptySet<String>()
    var packages = emptySet<String>()

    for (field in fields) {
        when (field) {
            ConnectionFields.HOST -> {
                if (connection.host.isNotBlank()) {
                    domains = "full:${connection.host}"
                }
            }

            ConnectionFields.DESTINATION -> {
                val (dstIp, dstPort) = parseAddress(connection.dst)
                if (dstIp.isNotBlank()) ip = dstIp
                if (dstPort.isNotBlank()) port = dstPort
            }

            ConnectionFields.SOURCE -> {
                val (srcIp, srcPort) = parseAddress(connection.src)
                if (srcIp.isNotBlank()) source = srcIp
                if (srcPort.isNotBlank()) sourcePort = srcPort
            }

            ConnectionFields.NETWORK -> {
                if (connection.network.isNotBlank()) {
                    network = setOf(connection.network)
                }
            }

            ConnectionFields.PROTOCOL -> {
                if (connection.protocol != null) {
                    protocol = setOf(connection.protocol)
                }
            }

            ConnectionFields.PROCESS -> {
                if (connection.processes?.isNotEmpty() == true) {
                    packages = connection.processes.toSet()
                }
            }

        }
    }

    return RouteSettingsUiState(
        name = connection.uuid,
        action = SingBoxOptions.ACTION_ROUTE,
        domains = domains,
        ip = ip,
        port = port,
        source = source,
        sourcePort = sourcePort,
        network = network,
        protocol = protocol,
        packages = packages,
    )
}

private fun parseAddress(address: String): Pair<String, String> {
    if (address.isBlank()) return "" to ""
    return if (address.startsWith("[")) {
        // IPv6
        val closeBracket = address.indexOf(']')
        if (closeBracket == -1) return address to ""
        val ip = address.substring(1, closeBracket)
        val port = if (closeBracket + 2 < address.length) {
            address.substring(closeBracket + 2)
        } else ""
        ip to port
    } else {
        // IPv4
        val lastColon = address.lastIndexOf(':')
        if (lastColon == -1) return address to ""
        address.take(lastColon) to address.substring(lastColon + 1)
    }
}
