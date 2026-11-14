package moe.matsuri.nb4a

import io.nekohasekai.sagernet.outbound.TestProxyFactory
import io.nekohasekai.sagernet.utils.NetworkListenerEngine
import io.throneproj.mobile.InterfaceUpdateListener
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInterfaceMonitorTest {

    @Test
    fun proxyWrappersWithSameRefnumShareKeyAndCleanUpEngineAndReportStates() = runBlocking {
        var registerCalls = 0
        var unregisterCalls = 0
        val engine = NetworkListenerEngine(
            registerCallback = {
                registerCalls++
                true
            },
            unregisterCallback = {
                unregisterCalls++
            },
            activeNetworkFallback = { null },
            elapsedRealtime = { 0L },
            logInfo = {},
            logDebug = {},
            logWarn = {},
        )
        val nativeInterface = NativeInterface(engine)

        val startWrapper = TestProxyFactory.newProxyWrapper(42)
        val closeWrapper = TestProxyFactory.newProxyWrapper(42)
        val otherWrapper = TestProxyFactory.newProxyWrapper(99)

        assertNotEquals(System.identityHashCode(startWrapper), System.identityHashCode(closeWrapper))
        assertEquals("go-iface:42", nativeInterface.monitorKey(startWrapper))
        assertEquals(nativeInterface.monitorKey(startWrapper), nativeInterface.monitorKey(closeWrapper))
        assertNotEquals(nativeInterface.monitorKey(startWrapper), nativeInterface.monitorKey(otherWrapper))

        nativeInterface.startDefaultInterfaceMonitor(startWrapper)
        nativeInterface.touchReportStateForTest(startWrapper)

        assertEquals(1, registerCalls)
        assertEquals(0, unregisterCalls)
        assertEquals(setOf<Any>("go-iface:42"), engine.snapshotKeys())
        assertTrue(nativeInterface.hasReportStateForTest(closeWrapper))
        assertEquals(1, nativeInterface.reportStateCountForTest())

        nativeInterface.closeDefaultInterfaceMonitor(closeWrapper)

        assertEquals(1, unregisterCalls)
        assertTrue("Listener must be removed from NetworkListenerEngine", engine.snapshotKeys().isEmpty())
        assertFalse("State must be removed from ifaceReportStates", nativeInterface.hasReportStateForTest(startWrapper))
        assertEquals(0, nativeInterface.reportStateCountForTest())
    }

    @Test
    fun nonProxyListenersUseIdentityFallbackWithoutCollidingOnEquals() = runBlocking {
        val engine = NetworkListenerEngine(
            registerCallback = { true },
            unregisterCallback = {},
            activeNetworkFallback = { null },
            elapsedRealtime = { 0L },
            logInfo = {},
            logDebug = {},
            logWarn = {},
        )
        val nativeInterface = NativeInterface(engine)

        class AlwaysEqualListener : InterfaceUpdateListener {
            override fun updateDefaultInterface(name: String?, index: Int, isExpensive: Boolean, isConstrained: Boolean) = Unit
            override fun updateNetworkPath(path: String?) = Unit
            override fun equals(other: Any?): Boolean = other is AlwaysEqualListener
            override fun hashCode(): Int = 7
        }

        val first = AlwaysEqualListener()
        val second = AlwaysEqualListener()
        assertEquals(nativeInterface.monitorKey(first), nativeInterface.monitorKey(first))
        assertNotEquals(nativeInterface.monitorKey(first), nativeInterface.monitorKey(second))

        nativeInterface.startDefaultInterfaceMonitor(first)
        nativeInterface.startDefaultInterfaceMonitor(second)
        assertEquals(2, engine.snapshotKeys().size)

        nativeInterface.closeDefaultInterfaceMonitor(first)
        assertEquals(1, engine.snapshotKeys().size)

        nativeInterface.closeDefaultInterfaceMonitor(second)
        assertTrue(engine.snapshotKeys().isEmpty())
    }

    @Test
    fun stopDoesNotUnregisterOrCrashActorWhenRegisterFellBack() = runBlocking {
        var registerCalls = 0
        var unregisterCalls = 0
        val engine = NetworkListenerEngine(
            registerCallback = {
                registerCalls++
                false
            },
            unregisterCallback = {
                unregisterCalls++
                error("unregister must not be called when callback was never registered")
            },
            activeNetworkFallback = { null },
            elapsedRealtime = { 0L },
            logInfo = {},
            logDebug = {},
            logWarn = {},
        )
        val nativeInterface = NativeInterface(engine)

        val w1 = TestProxyFactory.newProxyWrapper(7)
        val w2 = TestProxyFactory.newProxyWrapper(7)
        nativeInterface.startDefaultInterfaceMonitor(w1)
        assertEquals(1, engine.snapshotKeys().size)

        nativeInterface.closeDefaultInterfaceMonitor(w2)
        assertEquals(0, unregisterCalls)
        assertTrue(engine.snapshotKeys().isEmpty())

        // Actor must remain alive for subsequent start/stop cycles.
        val w3 = TestProxyFactory.newProxyWrapper(8)
        nativeInterface.startDefaultInterfaceMonitor(w3)
        assertEquals(setOf<Any>("go-iface:8"), engine.snapshotKeys())
        nativeInterface.closeDefaultInterfaceMonitor(w3)
        assertEquals(2, registerCalls)
        assertEquals(0, unregisterCalls)
    }
}
