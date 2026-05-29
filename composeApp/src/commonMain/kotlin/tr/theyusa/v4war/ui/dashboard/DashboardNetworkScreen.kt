package tr.theyusa.v4war.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import tr.theyusa.v4war.compose.BoxedVerticalScrollbar
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tr.theyusa.v4war.compose.setPlainText
import tr.theyusa.v4war.libcore.Libcore
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun DashboardNetworkScreen(
    uiState: DashboardState,
    bottomPadding: Dp,
    onCopySuccess: () -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    LaunchedEffect(Unit) {
        onVisibleChange(true)
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo }
            .distinctUntilChanged()
            .collect {}
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    NetworkStatsCard(uiState = uiState)
                }

                item {
                    SourceAddressCard(
                        ipv4 = uiState.ipv4,
                        ipv6 = uiState.ipv6,
                        onCopySuccess = onCopySuccess,
                    )
                }

                item {
                    NetworkInterfacesCard(uiState = uiState)
                }
            }

            BoxedVerticalScrollbar(
                modifier = Modifier.fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = scrollState),
                style = defaultMaterialScrollbarStyle().copy(
                    thickness = 12.dp,
                ),
            )
        }
    }
}

@Composable
private fun NetworkStatsCard(
    uiState: DashboardState,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Network Stats",
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Upload Rate",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = Libcore.formatBytes(uiState.uploadRate) + "/s",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Download Rate",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = Libcore.formatBytes(uiState.downloadRate) + "/s",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Upload",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = Libcore.formatBytes(uiState.totalUpload),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Download",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = Libcore.formatBytes(uiState.totalDownload),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Active Connections",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = uiState.activeConnectionCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun SourceAddressCard(
    ipv4: String?,
    ipv6: String?,
    onCopySuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Source Info",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "IPv4",
                    style = MaterialTheme.typography.bodySmall,
                )
                val text = ipv4 ?: "No statistics yet"
                Text(
                    text = text,
                    modifier = Modifier.clickable {
                        scope.launch {
                            clipboard.setPlainText(text)
                        }
                        onCopySuccess()
                    },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmallEmphasized,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "IPv6",
                    style = MaterialTheme.typography.bodySmall,
                )
                val text = ipv6 ?: "No statistics yet"
                Text(
                    text = text,
                    modifier = Modifier.clickable {
                        scope.launch {
                            clipboard.setPlainText(text)
                        }
                        onCopySuccess()
                    },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmallEmphasized,
                )
            }
        }
    }
}

@Composable
private fun NetworkInterfacesCard(
    uiState: DashboardState,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Network Interfaces",
                style = MaterialTheme.typography.titleMedium,
            )
            SelectionContainer {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.networkInterfaces.forEach { interfaceInfo ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = interfaceInfo.name,
                                style = MaterialTheme.typography.titleSmallEmphasized,
                            )
                            for (address in interfaceInfo.addresses) {
                                Text(
                                    text = address,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}