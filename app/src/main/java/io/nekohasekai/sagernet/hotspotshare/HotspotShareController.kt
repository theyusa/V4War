package io.nekohasekai.sagernet.hotspotshare

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the one running [HotspotShare] backend: resolves which method to use, mirrors the backend's state into [state]
 * (and into [publisher], the cross-process record the UI reads) and tears the backend down whenever it ends by itself.
 *
 * All inputs are injected, so the whole flow runs in JVM tests.
 */
class HotspotShareController(
    private val scope: CoroutineScope,
    private val environment: () -> ShareEnvironment,
    private val preference: () -> SharePreference,
    private val backendFactory: (ShareMethod) -> HotspotShare?,
    private val publisher: (ShareState) -> Unit = {},
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ShareState>(ShareState.Off)
    val state: StateFlow<ShareState> = _state.asStateFlow()

    private var backend: HotspotShare? = null
    private var mirror: Job? = null

    suspend fun start() = mutex.withLock {
        if (backend != null) return@withLock
        publish(ShareState.Starting)
        val method = when (val selection = HotspotShareSelector.resolve(preference(), environment())) {
            is ShareSelection.Unavailable -> return@withLock publish(ShareState.Error(selection.reason))
            is ShareSelection.Chosen -> selection.method
        }
        val created = backendFactory(method)
            ?: return@withLock publish(ShareState.Error(ShareFailure.BACKEND_NOT_IMPLEMENTED, method.code))
        backend = created
        mirror = scope.launch {
            created.state.collect { backendState ->
                // Off is the backend's idle value; this controller publishes Off itself when the user stops.
                if (backendState == ShareState.Off) return@collect
                publish(backendState)
                if (backendState is ShareState.Error || backendState is ShareState.Stopped) {
                    scope.launch { mutex.withLock { if (backend === created) teardown() } }
                }
            }
        }
        try {
            created.start()
        } catch (e: Exception) {
            publish(ShareState.Error(ShareFailure.BACKEND_FAILED, e.message))
            teardown()
        }
    }

    /** The user turned sharing off: tear down and report [ShareState.Off]. */
    suspend fun stop() = mutex.withLock {
        teardown()
        publish(ShareState.Off)
    }

    /** Ends the backend without touching the published state, so an Error or Stopped stays visible. */
    private suspend fun teardown() {
        val current = backend ?: return
        backend = null
        mirror?.cancel()
        mirror = null
        try {
            current.stop()
        } catch (_: Exception) {
            // The backend is gone either way; its own cleanup must be idempotent for the next start.
        }
    }

    private fun publish(state: ShareState) {
        _state.value = state
        publisher(state)
    }
}
