package tr.theyusa.v4war.compose

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.bg.ServiceStatus
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.ktx.readableUrlTestError
import tr.theyusa.v4war.libcore.Libcore
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.connection_test_available
import tr.theyusa.v4war.resources.connection_test_available_http
import tr.theyusa.v4war.resources.connection_test_error
import tr.theyusa.v4war.resources.connection_test_testing
import tr.theyusa.v4war.resources.speed
import tr.theyusa.v4war.resources.status_direct
import tr.theyusa.v4war.resources.status_proxy
import tr.theyusa.v4war.resources.vpn_connected
import tr.theyusa.v4war.ui.MainViewModel
import tr.theyusa.v4war.ui.URLTestStatus

/**
 * Connection stats bar rendered at the bottom of the screen, below the connection
 * FAB. It appears while the service is connected and shows the current proxy
 * and direct upload/download speeds plus the connection status. Tapping it while
 * visible runs the URL test, and the bar reflects the test state (testing /
 * latency / error). It slides out of view when [visible] becomes false
 * (e.g. on scroll).
 */
@Composable
fun rememberStatsBarHazeState(): HazeState = rememberHazeState()

@Composable
fun Modifier.statsBarHazeSource(hazeState: HazeState): Modifier {
    return hazeSource(state = hazeState)
        .background(MaterialTheme.colorScheme.background)
}

@Composable
fun StatsBar(
    modifier: Modifier = Modifier,
    status: ServiceStatus,
    visible: Boolean = true,
    mainViewModel: MainViewModel,
    hazeState: HazeState,
) {
    val urlTestStatus by mainViewModel.urlTestStatus.collectAsStateWithLifecycle()
    val isHTTPS by remember {
        DataStore.configurationStore
            .stringFlow(Key.CONNECTION_TEST_URL)
            .map { it.startsWith("https://") }
    }.collectAsStateWithLifecycle(false)

    var totalHeight by remember { mutableIntStateOf(0) }
    val offsetY by animateIntAsState(
        targetValue = if (visible) 0 else totalHeight,
        label = "statsBarOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { totalHeight = it.height }
            .graphicsLayer { translationY = offsetY.toFloat() }
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = navigationBarsAlwaysInsets()
                    .asPaddingValues()
                    .calculateBottomPadding() + 8.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val statsBarShape = RoundedCornerShape(28.dp)
        val statsBarTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.24f)
        val statsBarFallbackBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)
        val statsBarBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .then(
                    if (visible) {
                        Modifier.clickable { mainViewModel.urlTest() }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(statsBarShape)
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = statsBarFallbackBackground,
                            tint = HazeTint(statsBarTint),
                            blurRadius = 22.dp,
                            noiseFactor = 0.08f,
                            fallbackTint = HazeTint(statsBarTint),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        color = statsBarBorder,
                        shape = statsBarShape,
                    ),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Proxy speed
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(Res.string.status_proxy),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "▲ " + stringResource(
                                Res.string.speed,
                                Libcore.formatBytes(status.speed?.txRateProxy ?: 0L),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "▼ " + stringResource(
                                Res.string.speed,
                                Libcore.formatBytes(status.speed?.rxRateProxy ?: 0L),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Direct speed
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(Res.string.status_direct),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "▲ " + stringResource(
                                Res.string.speed,
                                Libcore.formatBytes(status.speed?.txRateDirect ?: 0L),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "▼ " + stringResource(
                                Res.string.speed,
                                Libcore.formatBytes(status.speed?.rxRateDirect ?: 0L),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val text = when (val status = urlTestStatus) {
                    URLTestStatus.Initial -> stringResource(Res.string.vpn_connected)
                    URLTestStatus.Testing -> stringResource(Res.string.connection_test_testing)

                    is URLTestStatus.Success -> stringResource(
                        if (isHTTPS) {
                            Res.string.connection_test_available
                        } else {
                            Res.string.connection_test_available_http
                        },
                        status.legacy,
                    )

                    is URLTestStatus.Exception -> {
                        val exception = status.exception
                        stringResource(
                            Res.string.connection_test_error,
                            readableUrlTestError(exception)?.let {
                                stringResource(it)
                            } ?: exception,
                        )
                    }
                }
                Text(text)
            }
        }
    }
}
