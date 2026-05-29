package tr.theyusa.v4war.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import tr.theyusa.v4war.compose.BoxedVerticalScrollbar
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val uploadRate = uiState.uploadRate
    val downloadRate = uiState.downloadRate
    val totalUpload = uiState.totalUpload
    val totalDownload = uiState.totalDownload
    val activeConnections = uiState.activeConnectionCount

    val maxRate = maxOf(uploadRate, downloadRate, 1L)

    val animatedUploadRatio by animateFloatAsState(
        targetValue = if (maxRate > 0) uploadRate.toFloat() / maxRate else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "uploadRatio",
    )
    val animatedDownloadRatio by animateFloatAsState(
        targetValue = if (maxRate > 0) downloadRate.toFloat() / maxRate else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "downloadRatio",
    )

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Network Stats",
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatMiniCard(
                    label = "Upload",
                    value = Libcore.formatBytes(totalUpload),
                    color = primaryColor,
                    modifier = Modifier.weight(1f),
                )
                StatMiniCard(
                    label = "Download",
                    value = Libcore.formatBytes(totalDownload),
                    color = secondaryColor,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatMiniCard(
                    label = "Upload Rate",
                    value = Libcore.formatBytes(uploadRate) + "/s",
                    color = primaryColor,
                    modifier = Modifier.weight(1f),
                )
                StatMiniCard(
                    label = "Download Rate",
                    value = Libcore.formatBytes(downloadRate) + "/s",
                    color = secondaryColor,
                    modifier = Modifier.weight(1f),
                )
            }

            SpeedBarChart(
                uploadRatio = animatedUploadRatio,
                downloadRatio = animatedDownloadRatio,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                backgroundColor = surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Active Connections",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = activeConnections.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryColor,
                )
            }
        }
    }
}

@Composable
private fun StatMiniCard(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
    }
}

@Composable
private fun SpeedBarChart(
    uploadRatio: Float,
    downloadRatio: Float,
    primaryColor: androidx.compose.ui.graphics.Color,
    secondaryColor: androidx.compose.ui.graphics.Color,
    backgroundColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor.copy(alpha = 0.3f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    text = "▲",
                    style = MaterialTheme.typography.bodySmall,
                    color = primaryColor,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height((40 * uploadRatio).coerceAtLeast(4f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(primaryColor),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Upload",
                style = MaterialTheme.typography.bodySmall,
                color = primaryColor,
            )
        }

        Column(
            modifier = Modifier
                .width(1.dp)
                .height(48.dp)
                .background(backgroundColor)
        ) {}

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    text = "▼",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height((40 * downloadRatio).coerceAtLeast(4f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(secondaryColor),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Download",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
            )
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