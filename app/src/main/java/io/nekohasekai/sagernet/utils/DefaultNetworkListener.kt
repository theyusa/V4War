package io.nekohasekai.sagernet.utils

import android.annotation.TargetApi
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal interface DefaultNetworkMonitor {
    suspend fun start(key: Any, listener: (Network?) -> Unit)
    suspend fun stop(key: Any)
}

@OptIn(ObsoleteCoroutinesApi::class)
internal class NetworkListenerEngine(
    private val registerCallback: () -> Boolean,
    private val unregisterCallback: () -> Unit,
    private val activeNetworkFallback: () -> Network?,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    private val describeNetwork: (Network) -> String = { it.toString() },
    private val logInfo: (String) -> Unit = { Logs.i(it) },
    private val logDebug: (String) -> Unit = { Logs.d(it) },
    private val logWarn: (Throwable) -> Unit = { Logs.w(it) },
    scope: CoroutineScope = GlobalScope,
) : DefaultNetworkMonitor {

    private sealed class NetworkMessage {
        class Start(val key: Any, val listener: (Network?) -> Unit) : NetworkMessage() {
            val processed = CompletableDeferred<Unit>()
        }

        class Get : NetworkMessage() {
            val response = CompletableDeferred<Network>()
        }

        class Stop(val key: Any) : NetworkMessage() {
            val processed = CompletableDeferred<Unit>()
        }

        class SnapshotKeys : NetworkMessage() {
            val keys = CompletableDeferred<Set<Any>>()
        }

        class Put(val network: Network) : NetworkMessage()
        class Update(val network: Network) : NetworkMessage()
        class Lost(val network: Network) : NetworkMessage()
    }

    @Volatile
    private var fallback = false

    private val networkActor = scope.actor<NetworkMessage>(Dispatchers.Unconfined, capacity = Channel.UNLIMITED) {
        val listeners = mutableMapOf<Any, (Network?) -> Unit>()
        var registered = false
        var network: Network? = null
        val pendingRequests = arrayListOf<NetworkMessage.Get>()
        for (message in channel) when (message) {
            is NetworkMessage.Start -> {
                try {
                    if (listeners.isEmpty()) {
                        registered = registerCallback()
                        fallback = !registered
                    }
                    listeners[message.key] = message.listener
                    if (network != null) message.listener(network)
                    message.processed.complete(Unit)
                } catch (error: Throwable) {
                    val removed = listeners.remove(message.key) != null
                    if (removed && listeners.isEmpty() && registered) {
                        registered = false
                        runCatching { unregisterCallback() }.onFailure(logWarn)
                    }
                    message.processed.completeExceptionally(error)
                }
            }

            is NetworkMessage.Get -> {
                check(listeners.isNotEmpty()) { "Getting network without any listeners is not supported" }
                if (network == null) pendingRequests += message else message.response.complete(
                    network
                )
            }

            is NetworkMessage.Stop -> {
                try {
                    if (listeners.isNotEmpty() && // was not empty
                        listeners.remove(message.key) != null && listeners.isEmpty()
                    ) {
                        network = null
                        if (registered) {
                            registered = false
                            runCatching { unregisterCallback() }.onFailure(logWarn)
                        }
                    }
                    message.processed.complete(Unit)
                } catch (error: Throwable) {
                    message.processed.completeExceptionally(error)
                }
            }

            is NetworkMessage.SnapshotKeys -> {
                message.keys.complete(listeners.keys.toSet())
            }

            is NetworkMessage.Put -> {
                // Diagnostics: network event timeline for night / airplane mode (compare with Go UpdateDefaultInterface)
                // elapsed covers every listener, which run synchronously (the Unconfined actor runs on the callback thread)
                val start = elapsedRealtime()
                network = message.network
                pendingRequests.forEach { it.response.complete(message.network) }
                pendingRequests.clear()
                listeners.values.forEach { listener ->
                    try {
                        listener(network)
                    } catch (e: Exception) {
                        logWarn(e)
                    }
                }
                logInfo("DefaultNetworkListener Put network=${describeNetwork(message.network)} listeners=${listeners.size} elapsed=${elapsedRealtime() - start}ms thread=${Thread.currentThread().name}")
            }

            is NetworkMessage.Update -> if (network === message.network || network == message.network) {
                // This event storms (onCapabilitiesChanged) while switching networks or on a flaky signal: debug level
                val start = elapsedRealtime()
                listeners.values.forEach { listener ->
                    try {
                        listener(network)
                    } catch (e: Exception) {
                        logWarn(e)
                    }
                }
                logDebug("DefaultNetworkListener Update network=${describeNetwork(message.network)} listeners=${listeners.size} elapsed=${elapsedRealtime() - start}ms thread=${Thread.currentThread().name}")
            }

            is NetworkMessage.Lost -> if (network === message.network || network == message.network) {
                val start = elapsedRealtime()
                network = null
                listeners.values.forEach { listener ->
                    try {
                        listener(null)
                    } catch (e: Exception) {
                        logWarn(e)
                    }
                }
                logInfo("DefaultNetworkListener Lost network=${describeNetwork(message.network)} listeners=${listeners.size} elapsed=${elapsedRealtime() - start}ms thread=${Thread.currentThread().name}")
            }
        }
    }

    override suspend fun start(key: Any, listener: (Network?) -> Unit) {
        val message = NetworkMessage.Start(key, listener)
        networkActor.send(message)
        // send only queues the message; the Start branch and the cached network's first callback may still be pending.
        // Wait for processed, or a test box created meanwhile may get default=nil from the Go monitor
        // and its first dial fails before updateDefaultInterface(wlan0, index).
        message.processed.await()
    }

    suspend fun get(): Network = if (fallback) @TargetApi(23) {
        activeNetworkFallback()
            ?: throw UnknownHostException() // failed to listen, return current if available
    } else NetworkMessage.Get().run {
        networkActor.send(this)
        response.await()
    }

    override suspend fun stop(key: Any) {
        val message = NetworkMessage.Stop(key)
        networkActor.send(message)
        message.processed.await()
    }

    suspend fun snapshotKeys(): Set<Any> = NetworkMessage.SnapshotKeys().run {
        networkActor.send(this)
        keys.await()
    }

    suspend fun put(network: Network) = networkActor.send(NetworkMessage.Put(network))
    suspend fun update(network: Network) = networkActor.send(NetworkMessage.Update(network))
    suspend fun lost(network: Network) = networkActor.send(NetworkMessage.Lost(network))

    fun postPut(network: Network) {
        networkActor.trySend(NetworkMessage.Put(network))
    }

    fun postUpdate(network: Network) {
        networkActor.trySend(NetworkMessage.Update(network))
    }

    fun postLost(network: Network) {
        networkActor.trySend(NetworkMessage.Lost(network))
    }
}

/**
 * Tracks a single active platform network callback instance so each registration allocates a fresh
 * callback, unregisters that exact instance at most once, and ignores stale deliveries after unregister.
 */
internal class NetworkCallbackRegistry<T : Any>(
    private val createCallback: (isActive: () -> Boolean) -> T,
    private val registerPlatform: (T) -> Unit,
    private val unregisterPlatform: (T) -> Unit,
    private val logWarn: (Throwable) -> Unit = {},
) {
    private class Entry<T : Any>(val callback: T) {
        val active = AtomicBoolean(true)
    }

    private val current = AtomicReference<Entry<T>?>(null)

    fun register(): Boolean {
        current.getAndSet(null)?.let { previous ->
            previous.active.set(false)
            runCatching { unregisterPlatform(previous.callback) }.onFailure(logWarn)
        }
        lateinit var entry: Entry<T>
        val callback = createCallback { entry.active.get() && current.get() === entry }
        entry = Entry(callback)
        current.set(entry)
        return try {
            registerPlatform(callback)
            true
        } catch (e: Exception) {
            entry.active.set(false)
            current.compareAndSet(entry, null)
            logWarn(e)
            false
        }
    }

    fun unregister() {
        val entry = current.getAndSet(null) ?: return
        entry.active.set(false)
        unregisterPlatform(entry.callback)
    }

    fun activeCallback(): T? = current.get()?.callback
}

object DefaultNetworkListener : DefaultNetworkMonitor {

    private val engine by lazy {
        NetworkListenerEngine(
            registerCallback = ::register,
            unregisterCallback = ::unregister,
            activeNetworkFallback = { SagerNet.connectivity.activeNetwork },
        )
    }

    override suspend fun start(key: Any, listener: (Network?) -> Unit) = engine.start(key, listener)

    suspend fun get() = engine.get()

    override suspend fun stop(key: Any) = engine.stop(key)

    // NB: below API 26 this runs on ConnectivityThread; 26+ dispatches through the callbackHandler given to register
    // onto a dedicated worker thread (dispatching to the main thread through mainHandler once blocked the UI
    // for seconds with several test listeners and onCapabilitiesChanged storms).
    // A fresh instance is allocated per registration so ConnectivityManager never reuses a Callback identity
    // across stop/start cycles, and late callbacks from a previously unregistered instance are ignored.
    private class ListeningCallback(
        private val isActive: () -> Boolean,
    ) : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!isActive()) return
            Logs.d("DefaultNetworkListener onAvailable enter network=$network thread=${Thread.currentThread().name}")
            engine.postPut(network)
        }

        override fun onCapabilitiesChanged(
            network: Network, networkCapabilities: NetworkCapabilities
        ) { // it's a good idea to refresh capabilities
            if (!isActive()) return
            // Storm source: debug per event (info would flood the log)
            Logs.d("DefaultNetworkListener onCapabilitiesChanged enter network=$network thread=${Thread.currentThread().name}")
            engine.postUpdate(network)
        }

        override fun onLost(network: Network) {
            if (!isActive()) return
            Logs.d("DefaultNetworkListener onLost enter network=$network thread=${Thread.currentThread().name}")
            engine.postLost(network)
        }
    }

    private val request by lazy {
        NetworkRequest.Builder().apply {
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            if (Build.VERSION.SDK_INT == 23) {  // workarounds for OEM bugs
                removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            }
        }.build()
    }
    private val callbackThread by lazy { HandlerThread("DefaultNetworkListener").apply { start() } }
    private val callbackHandler by lazy { Handler(callbackThread.looper) }

    private val callbackRegistry = NetworkCallbackRegistry(
        createCallback = { isActive -> ListeningCallback(isActive) },
        registerPlatform = { callback ->
            when (Build.VERSION.SDK_INT) {
                in 31..Int.MAX_VALUE -> @TargetApi(31) {
                    SagerNet.connectivity.registerBestMatchingNetworkCallback(
                        request, callback, callbackHandler
                    )
                }
                in 28 until 31 -> @TargetApi(28) {  // we want REQUEST here instead of LISTEN
                    SagerNet.connectivity.requestNetwork(request, callback, callbackHandler)
                }
                in 26 until 28 -> @TargetApi(26) {
                    SagerNet.connectivity.registerDefaultNetworkCallback(callback, callbackHandler)
                }
                in 24 until 26 -> @TargetApi(24) {
                    SagerNet.connectivity.registerDefaultNetworkCallback(callback)
                }
                else -> {
                    SagerNet.connectivity.requestNetwork(request, callback)
                    // known bug on API 23: https://stackoverflow.com/a/33509180/2245107
                }
            }
        },
        unregisterPlatform = { callback ->
            SagerNet.connectivity.unregisterNetworkCallback(callback)
        },
        logWarn = { Logs.w(it) },
    )

    /**
     * Unfortunately registerDefaultNetworkCallback is going to return VPN interface since Android P DP1:
     * https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately, we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    private fun register(): Boolean = callbackRegistry.register()

    private fun unregister() = callbackRegistry.unregister()
}
