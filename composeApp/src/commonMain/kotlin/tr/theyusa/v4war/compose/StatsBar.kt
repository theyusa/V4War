@file:OptIn(ExperimentalLayoutApi::class)

package tr.theyusa.v4war.compose

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import tr.theyusa.v4war.compose.material3.Surface
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.bg.ServiceStatus
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.ktx.readableUrlTestError
import tr.theyusa.v4war.libcore.Libcore
import tr.theyusa.v4war.resources.*
import tr.theyusa.v4war.ui.MainViewModel
import tr.theyusa.v4war.ui.URLTestStatus
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource

@Composable
fun StatsBar(
    modifier: Modifier = Modifier,
    status: ServiceStatus,
    visible: Boolean = true,
    mainViewModel: MainViewModel,
    onUpdateSubscription: (() -> Unit)? = null,
) {
    val urlTestStatus by mainViewModel.urlTestStatus.collectAsStateWithLifecycle()
    val isHTTPS by DataStore.configurationStore
        .stringFlow(Key.CONNECTION_TEST_URL)
        .map { it.startsWith("https://") }
        .collectAsStateWithLifecycle(false)

    var height by remember { mutableIntStateOf(0) }
    val offsetY by animateIntAsState(
        targetValue = if (visible) 0 else height,
        label = "statsBarOffset",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { height = it.height }
            .graphicsLayer { translationY = offsetY.toFloat() }
            .then(
                if (visible) {
                    Modifier.clickable { mainViewModel.urlTest() }
                } else {
                    Modifier
                },
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(
                    bottom = navigationBarsAlwaysInsets()
                        .asPaddingValues()
                        .calculateBottomPadding(),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "▲ " + stringResource(
                            Res.string.speed,
                            Libcore.formatBytes(status.speed?.txRateProxy ?: 0L),
                        ),
                    )
                    Text(
                        text = "▼ " + stringResource(
                            Res.string.speed,
                            Libcore.formatBytes(status.speed?.rxRateProxy ?: 0L),
                        ),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val text = when (urlTestStatus) {
                    URLTestStatus.Initial -> stringResource(Res.string.vpn_connected)
                    URLTestStatus.Testing -> stringResource(Res.string.connection_test_testing)

                    is URLTestStatus.Success -> stringResource(
                        if (isHTTPS) {
                            Res.string.connection_test_available
                        } else {
                            Res.string.connection_test_available_http
                        },
                        (urlTestStatus as URLTestStatus.Success).legacy,
                    )

                    is URLTestStatus.Exception -> {
                        val exception = (urlTestStatus as URLTestStatus.Exception).exception
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
            if (onUpdateSubscription != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = vectorResource(Res.drawable.update),
                    contentDescription = stringResource(Res.string.update_subscription),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onUpdateSubscription() },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}