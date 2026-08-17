package tr.theyusa.v4war.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource
import tr.theyusa.v4war.bg.ServiceState
import tr.theyusa.v4war.compose.material3.Surface
import tr.theyusa.v4war.compose.material3.Text
import tr.theyusa.v4war.ktx.readableUrlTestError
import tr.theyusa.v4war.resources.*
import tr.theyusa.v4war.ui.MainViewModel
import tr.theyusa.v4war.ui.URLTestStatus
import kotlin.time.Duration.Companion.seconds

/**
 * Compact connection time/status pill rendered directly below the connection
 * FAB. It appears when the service starts, shows the elapsed connection time
 * while connected (ticking every second), and hides again when the service
 * stops. When [mainViewModel] is provided, tapping it while connected runs the
 * URL test and the pill reflects the test state (testing / latency / error).
 */
@Composable
fun ConnectionTimeBar(
    modifier: Modifier = Modifier,
    state: ServiceState,
    startedAt: Long,
    visible: Boolean = true,
    mainViewModel: MainViewModel? = null,
) {
    val urlTestStatus by (mainViewModel?.urlTestStatus ?: emptyFlow<URLTestStatus>())
        .collectAsStateWithLifecycle(initialValue = null)

    AnimatedVisibility(
        visible = visible && state.started,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        modifier = modifier,
    ) {
        ConnectionTimeBarContent(
            state = state,
            startedAt = startedAt,
            urlTestStatus = urlTestStatus,
            onUrlTest = mainViewModel?.let { viewModel -> { viewModel.urlTest() } },
        )
    }
}

@Composable
private fun ConnectionTimeBarContent(
    state: ServiceState,
    startedAt: Long,
    urlTestStatus: URLTestStatus?,
    onUrlTest: (() -> Unit)?,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state, startedAt) {
        if (state != ServiceState.Connected || startedAt <= 0L) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1.seconds)
        }
    }

    Surface(
        modifier = Modifier.clickable(
            enabled = state == ServiceState.Connected && urlTestStatus != URLTestStatus.Testing,
        ) {
            onUrlTest?.invoke()
        },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                ServiceState.Connected -> StatusDot(color = MaterialTheme.colorScheme.primary)

                ServiceState.Connecting -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.tertiary,
                )

                ServiceState.Stopping -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> {}
            }
            Text(
                text = barText(state, startedAt, now, urlTestStatus),
                style = MaterialTheme.typography.labelLarge,
                color = when (urlTestStatus) {
                    is URLTestStatus.Exception -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun barText(
    state: ServiceState,
    startedAt: Long,
    now: Long,
    urlTestStatus: URLTestStatus?,
): String {
    return when (state) {
        ServiceState.Connecting -> stringResource(Res.string.connecting)
        ServiceState.Stopping -> stringResource(Res.string.stopping)
        ServiceState.Connected -> connectedText(startedAt, now, urlTestStatus)
        else -> ""
    }
}

@Composable
private fun connectedText(startedAt: Long, now: Long, urlTestStatus: URLTestStatus?): String {
    val time = formatUptime(startedAt, now)
    return when (urlTestStatus) {
        URLTestStatus.Testing -> stringResource(Res.string.connection_test_testing)

        is URLTestStatus.Success -> stringResource(
            Res.string.connected_time_latency,
            time,
            urlTestStatus.legacy,
        )

        is URLTestStatus.Exception -> stringResource(
            Res.string.connection_test_error,
            readableUrlTestError(urlTestStatus.exception)?.let { stringResource(it) }
                ?: urlTestStatus.exception,
        )

        else -> stringResource(Res.string.connected_time, time)
    }
}

@Composable
private fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(color = color, shape = CircleShape),
    )
}

internal fun formatUptime(startedAtMillis: Long, nowMillis: Long): String {
    val totalSeconds = ((nowMillis - startedAtMillis) / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
