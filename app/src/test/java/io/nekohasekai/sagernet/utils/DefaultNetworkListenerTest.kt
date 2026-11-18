package io.nekohasekai.sagernet.utils

import android.net.Network
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultNetworkListenerTest {

    private class FakePlatformCallback(private val isActive: () -> Boolean) {
        fun fireAvailable(engine: NetworkListenerEngine, network: Network) {
            if (!isActive()) return
            engine.postPut(network)
        }

        fun fireCapabilitiesChanged(engine: NetworkListenerEngine, network: Network) {
            if (!isActive()) return
            engine.postUpdate(network)
        }

        fun fireLost(engine: NetworkListenerEngine, network: Network) {
            if (!isActive()) return
            engine.postLost(network)
        }
    }

    private fun stubNetwork(): Network {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocate = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, Network::class.java) as Network
    }

    @Test
    fun allocatesFreshCallbackPerRegisterAndUnregistersExactInstanceOnce() {
        val registered = ArrayList<FakePlatformCallback>()
        val unregistered = ArrayList<FakePlatformCallback>()
        val registry = NetworkCallbackRegistry(
            createCallback = { isActive -> FakePlatformCallback(isActive) },
            registerPlatform = { registered.add(it) },
            unregisterPlatform = { unregistered.add(it) },
        )

        assertTrue(registry.register())
        assertEquals(1, registered.size)
        val first = registered[0]
        assertSame(first, registry.activeCallback())

        registry.unregister()
        registry.unregister() // double-unregister must be a no-op
        assertEquals(1, unregistered.size)
        assertSame(first, unregistered[0])
        assertNull(registry.activeCallback())

        assertTrue(registry.register())
        assertEquals(2, registered.size)
        val second = registered[1]
        assertNotSame(first, second)
        assertSame(second, registry.activeCallback())

        registry.unregister()
        assertEquals(2, unregistered.size)
        assertSame(second, unregistered[1])
        assertNull(registry.activeCallback())
    }

    @Test
    fun failedRegisterClearsActiveCallbackAndSkipsUnregister() {
        val registered = ArrayList<FakePlatformCallback>()
        val unregistered = ArrayList<FakePlatformCallback>()
        val warnings = ArrayList<Throwable>()
        val registry = NetworkCallbackRegistry(
            createCallback = { isActive -> FakePlatformCallback(isActive) },
            registerPlatform = {
                registered.add(it)
                throw SecurityException("register failed")
            },
            unregisterPlatform = { unregistered.add(it) },
            logWarn = { warnings.add(it) },
        )

        assertFalse(registry.register())
        assertEquals(1, registered.size)
        assertEquals(1, warnings.size)
        assertNull(registry.activeCallback())

        registry.unregister()
        assertTrue(unregistered.isEmpty())
    }

    @Test
    fun staleCallbacksAfterUnregisterAreIgnoredAndDoNotClobberNewCycle() = runBlocking {
        val registered = ArrayList<FakePlatformCallback>()
        val unregistered = ArrayList<FakePlatformCallback>()
        val registry = NetworkCallbackRegistry(
            createCallback = { isActive -> FakePlatformCallback(isActive) },
            registerPlatform = { registered.add(it) },
            unregisterPlatform = { unregistered.add(it) },
        )
        val engine = NetworkListenerEngine(
            registerCallback = { registry.register() },
            unregisterCallback = { registry.unregister() },
            activeNetworkFallback = { null },
            elapsedRealtime = { 0L },
            describeNetwork = { "stub-net" },
            logInfo = {},
            logDebug = {},
            logWarn = {},
        )

        val net1 = stubNetwork()
        val net2 = stubNetwork()
        val events = ArrayList<Network?>()

        engine.start("k1") { events.add(it) }
        val cb1 = registered[0]
        cb1.fireAvailable(engine, net1)
        assertEquals(1, events.size)
        assertSame(net1, events[0])

        engine.stop("k1")
        assertSame(cb1, unregistered.single())

        // Stale callbacks arriving for cb1 after unregister must be ignored.
        cb1.fireAvailable(engine, net1)
        cb1.fireCapabilitiesChanged(engine, net1)
        cb1.fireLost(engine, net1)
        assertEquals(1, events.size)

        // Start a new cycle -> allocates cb2; stale cb1.onLost(net2) must not drop net2.
        events.clear()
        engine.start("k2") { events.add(it) }
        val cb2 = registered[1]
        assertNotSame(cb1, cb2)
        cb2.fireAvailable(engine, net2)
        cb1.fireLost(engine, net2)
        cb2.fireCapabilitiesChanged(engine, net2)
        assertEquals(2, events.size)
        assertSame(net2, events[0])
        assertSame(net2, events[1])

        engine.stop("k2")
        assertSame(cb2, unregistered[1])
    }
}
