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

    @Volatile
    var isPausedForVpnDrop: Boolean = false
        private set

    suspend fun start() = mutex.withLock {
        if (backend != null) return@withLock
        isPausedForVpnDrop = false
        startInternal()
    }

    /** Tears down any running backend and starts fresh with the current preference and environment. */
    suspend fun restart() = mutex.withLock {
        val hadPausedRoot = isPausedForVpnDrop && backend is RootBackend
        isPausedForVpnDrop = false
        teardown()
        if (hadPausedRoot) {
            runCatching { RootBackend.cleanupLegacyRules() }
        }
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
                    if (isPausedForVpnDrop && backendState == ShareState.Stopped(ShareFailure.VPN_LOST)) {
                        return@collect
                    }
                    scope.launch {
                        mutex.withLock {
                            if (backend === created) {
                                isPausedForVpnDrop = false
                                teardown()
                            }
                        }
                    }
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

    /** The user turned sharing off: tear down (and close hotspot if requested) and report [ShareState.Off]. */
    suspend fun stop(stopHotspot: Boolean = false) = mutex.withLock {
        val currentBackend = backend
        val wasPaused = isPausedForVpnDrop
        isPausedForVpnDrop = false
        teardown(stopHotspot = stopHotspot)
        // If Root fail-closed held DROP rules after a VPN drop,
        // user turning sharing OFF explicitly removes those firewall rules too.
        val hasRoot = withContext(Dispatchers.IO) { environment().rootAvailable }
        if ((currentBackend == null || (wasPaused && currentBackend is RootBackend)) && hasRoot) {
            runCatching { RootBackend.cleanupLegacyRules() }
        }
        publish(ShareState.Off)
    }

    /**
     * Pauses sharing fail-closed (`BLOCK_TRAFFIC`) on VPN drop while keeping Hotspot + leak lock
     * alive so sharing can auto-resume when VPN reconnects.
     */
    suspend fun suspendForVpnDrop() = mutex.withLock {
        val current = backend ?: return@withLock
        if (current is PausableHotspotShare) {
            isPausedForVpnDrop = true
            current.pauseForVpnDrop()
            publish(ShareState.Stopped(ShareFailure.VPN_LOST))
        } else {
            isPausedForVpnDrop = false
            teardown()
            publish(ShareState.Stopped(ShareFailure.VPN_LOST))
        }
    }

    /**
     * Resumes a fail-closed paused backend once the VPN comes back up.
     */
    suspend fun resumeFromVpnDrop(): Boolean = mutex.withLock {
        if (!isPausedForVpnDrop) return@withLock false
        val current = backend ?: run {
            isPausedForVpnDrop = false
            return@withLock false
        }
        val resumed = if (current is PausableHotspotShare) {
            current.resumeAfterVpnDrop()
        } else {
            false
        }
        if (resumed) {
            isPausedForVpnDrop = false
            publish(ShareState.Active(current.method))
            true
        } else {
            isPausedForVpnDrop = false
            teardown()
            publish(ShareState.Stopped(ShareFailure.VPN_LOST))
            false
        }
    }

    /** Tears down the active backend after an external failure (e.g. VPN or hotspot drop) without closing hotspot, and publishes [ShareState.Stopped]. */
    suspend fun stopDueToFailure(failure: ShareFailure) = mutex.withLock {
        if (backend == null && _state.value !is ShareState.Active && !isPausedForVpnDrop) return@withLock
        val current = backend
        val wasPausedRoot = isPausedForVpnDrop && current is RootBackend
        isPausedForVpnDrop = false
        if (failure == ShareFailure.VPN_LOST && current is RootBackend) {
            runCatching { current.handleVpnDropped(FailClosedAction.BLOCK_TRAFFIC) }
        }
        teardown(stopHotspot = false)
        if (wasPausedRoot) {
            runCatching { RootBackend.cleanupLegacyRules() }
        }
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
    private suspend fun teardown(stopHotspot: Boolean = false) {
        val current = backend ?: return
        backend = null
        mirror?.cancel()
        mirror = null
        try {
            if (stopHotspot && current is HotspotControllingBackend) {
                current.stopHotspotByUser()
            } else {
                current.stop()
            }
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
