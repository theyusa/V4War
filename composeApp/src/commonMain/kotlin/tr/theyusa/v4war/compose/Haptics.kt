package tr.theyusa.v4war.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Returns a stable lambda that triggers a light tactile "click" when invoked.
 * Call it at the start of an [onClick] handler so icon/action presses give
 * physical feedback. No-op on platforms without a haptic engine.
 */
@Composable
fun rememberHapticClick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}
