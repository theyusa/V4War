package tr.theyusa.v4war.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ElevatedCard
import tr.theyusa.v4war.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tr.theyusa.v4war.compose.BoxedVerticalScrollbar
import tr.theyusa.v4war.compose.theme.LogColors
import tr.theyusa.v4war.libcore.Libcore
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.connection_status_active
import tr.theyusa.v4war.resources.connection_status_closed
import tr.theyusa.v4war.resources.delete_forever
import tr.theyusa.v4war.resources.traffic
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun DashboardConnectionsScreen(
    modifier: Modifier = Modifier,
    uiState: DashboardState,
    bottomPadding: Dp,
    resolveProcessInfo: suspend (String?, Int) -> ProcessInfo?,
    closeConnection: (uuid: String) -> Unit,
    openDetail: (uuid: String) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    val itemSpacing = 12.dp
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        onVisibleChange(true)
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                state = listState,
                contentPadding = PaddingValues(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
            ) {
                items(
                    items = uiState.connections,
                    key = { it.uuid },
                    contentType = { 0 },
                ) { connection ->
                    val swipState = rememberSwipeToDismissBoxState()
                    SwipeToDismissBox(
                        state = swipState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.delete_forever),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onError,
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        modifier = Modifier.fillMaxWidth(),
                        onDismiss = { swipeToDismissBoxValue ->
                            if (swipeToDismissBoxValue == SwipeToDismissBoxValue.EndToStart) {
                                closeConnection(connection.uuid)
                            }
                        },
                    ) {
                        ConnectionCard(
                            connection = connection,
                            resolveProcessInfo = resolveProcessInfo,
                            openDetail = openDetail,
                        )
                    }
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

@Composable
private fun ConnectionCard(
    modifier: Modifier = Modifier,
    connection: ConnectionDetailState,
    resolveProcessInfo: suspend (String?, Int) -> ProcessInfo?,
    openDetail: (id: String) -> Unit,
) {
    val process = connection.processes?.firstOrNull()
    val uid = connection.uid
    var processInfo by remember { mutableStateOf<ProcessInfo?>(null) }
    LaunchedEffect(Unit) {
        processInfo = resolveProcessInfo(process, uid)
    }

    val statusColor = if (connection.isClosed) Color.Gray else Color.Green
    val networkColor = when (connection.network) {
        "tcp" -> Color(0xFF2196F3)
        "udp" -> Color(0xFFFF9800)
        else -> Color(0xFF9C27B0)
    }

    ElevatedCard(
        onClick = { openDetail(connection.uuid) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = statusColor)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = connection.network.uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = networkColor,
                    )
                    Text(
                        text = if (connection.isClosed) "Closed" else "Active",
                        fontSize = 12.sp,
                        color = statusColor,
                    )
                }
                Text(
                    text = connection.dst,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
                val host = connection.host
                if (host.isNotBlank() && !connection.dst.startsWith(host)) {
                    Text(
                        text = host,
                        fontSize = 12.sp,
                        color = Color(0xFFFFB74D),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "↑ ${Libcore.formatBytes(connection.uploadTotal)}",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50),
                    )
                    Text(
                        text = "↓ ${Libcore.formatBytes(connection.downloadTotal)}",
                        fontSize = 12.sp,
                        color = Color(0xFF2196F3),
                    )
                }
                if (connection.inbound.isNotBlank()) {
                    Text(
                        text = "In: ${connection.inbound}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (connection.outbound.isNotBlank()) {
                    Text(
                        text = "Out: ${connection.outbound}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                    )
                }
            }
            processInfo?.icon?.let { icon ->
                ProcessIcon(
                    icon = icon,
                    contentDescription = processInfo?.label,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}
