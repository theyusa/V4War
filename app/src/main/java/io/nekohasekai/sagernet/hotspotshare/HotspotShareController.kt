package io.nekohasekai.sagernet.hotspotshare

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
        startInternal()
    }

    /** Tears down any running backend and starts fresh with the current preference and environment. */
    suspend fun restart() = mutex.withLock {
        teardown()
        startInternal()
    }

    private suspend fun startInternal() {
        publish(ShareState.Starting)
        val pref = preference()
        val env = withContext(Dispatchers.IO) { environment() }
        val method = when (val selection = HotspotShareSelector.resolve(pref, env)) {
            is ShareSelection.Unavailable -> return publish(ShareState.Error(selection.reason))
            is ShareSelection.Chosen -> selection.method
        }
        startBackend(method, allowAutoFallback = (pref == SharePreference.AUTO && method != ShareMethod.LAN_PROXY))
    }

    private suspend fun startBackend(method: ShareMethod, allowAutoFallback: Boolean) {
        val created = backendFactory(method)
            ?: return publish(ShareState.Error(ShareFailure.BACKEND_NOT_IMPLEMENTED, method.code))
        backend = created
        var startingBackend = true
        var pendingFallback = false
        mirror = scope.launch {
            created.state.collect { backendState ->
                // Off is the backend's idle value; this controller publishes Off itself when the user stops.
                if (backendState == ShareState.Off) return@collect
                if (startingBackend && allowAutoFallback &&
                    backendState is ShareState.Error && backendState.reason in AUTO_FALLBACK_REASONS
                ) {
                    pendingFallback = true
                    return@collect
                }
                publish(backendState)
                if (backendState is ShareState.Error || backendState is ShareState.Stopped) {
                    scope.launch { mutex.withLock { if (backend === created) teardown() } }
                }
            }
        }
        try {
            created.start()
            startingBackend = false
            val currentState = created.state.value
            if (allowAutoFallback && (pendingFallback ||
                    (currentState is ShareState.Error && currentState.reason in AUTO_FALLBACK_REASONS))
            ) {
                teardown()
                startBackend(ShareMethod.LAN_PROXY, allowAutoFallback = false)
            }
        } catch (e: Exception) {
            startingBackend = false
            teardown()
            if (allowAutoFallback) {
                startBackend(ShareMethod.LAN_PROXY, allowAutoFallback = false)
            } else {
                publish(ShareState.Error(ShareFailure.BACKEND_FAILED, e.message))
            }
        }
    }

    /** The user turned sharing off: tear down and report [ShareState.Off]. */
    suspend fun stop() = mutex.withLock {
        val currentBackend = backend
        teardown()
        // If Root fail-closed held DROP rules after a VPN drop (so backend was already cleared),
        // user turning sharing OFF explicitly removes those firewall rules too.
        val hasRoot = withContext(Dispatchers.IO) { environment().rootAvailable }
        if (currentBackend == null && hasRoot) {
            runCatching { RootBackend.cleanupLegacyRules() }
        }
        publish(ShareState.Off)
    }

    /** Tears down the active backend after an external failure (e.g. VPN or hotspot drop) and publishes [ShareState.Stopped]. */
    suspend fun stopDueToFailure(failure: ShareFailure) = mutex.withLock {
        if (backend == null && _state.value !is ShareState.Active) return@withLock
        teardown()
        publish(ShareState.Stopped(failure))
    }

    /**
     * Proactively cleans up leftover test networks, tethering state, and legacy iptables rules
     * on app startup or crash recovery.
     */
    suspend fun recoverDirtyState() = mutex.withLock {
        try {
            val env = withContext(Dispatchers.IO) { environment() }
            if (env.sdkInt >= 33 && env.shizukuGranted && env.shizukuRunning) {
                val backend = backendFactory(ShareMethod.SHIZUKU)
                if (backend is ShizukuBackend) {
                    backend.recoverDirtyState()
                }
            }
        } catch (_: Exception) {
        }

        try {
            RootBackend.cleanupLegacyRules()
        } catch (_: Exception) {
        }
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

    companion object {
        private val AUTO_FALLBACK_REASONS = setOf(
            ShareFailure.UNSUPPORTED_ANDROID,
            ShareFailure.SHIZUKU_NOT_RUNNING,
            ShareFailure.SHIZUKU_PERMISSION_DENIED,
            ShareFailure.ROOT_UNAVAILABLE,
        )
    }
}
