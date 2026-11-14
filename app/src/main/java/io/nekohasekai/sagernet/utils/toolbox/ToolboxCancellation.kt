package io.nekohasekai.sagernet.utils.toolbox

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Executes blocking [block] and immediately invokes [onCancel] on [Dispatchers.IO] when the
 * enclosing coroutine is cancelled, ensuring any I/O failure caused by cancellation surfaces as
 * a [kotlinx.coroutines.CancellationException].
 */
internal suspend inline fun <T> withImmediateCancel(
    crossinline onCancel: () -> Unit,
    crossinline block: () -> T,
): T = coroutineScope {
    val finished = AtomicBoolean(false)
    val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!finished.get()) {
                runCatching { onCancel() }
            }
        }
    }
    try {
        val result = block()
        finished.set(true)
        result
    } catch (t: Throwable) {
        currentCoroutineContext().ensureActive()
        throw t
    } finally {
        finished.set(true)
        watcher.cancel()
    }
}

internal suspend inline fun <T> Call.executeCancellable(
    crossinline block: (Response) -> T,
): T = withImmediateCancel(onCancel = { cancel() }) {
    execute().use { block(it) }
}
