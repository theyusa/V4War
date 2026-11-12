package io.nekohasekai.sagernet.hotspotshare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import io.nekohasekai.sagernet.hotspotshare.root.ShellExecutor
import io.nekohasekai.sagernet.hotspotshare.root.ShellResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootBackendTest {

    private class FakeContext : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? = null
        override fun unregisterReceiver(receiver: BroadcastReceiver?) {}
    }

    private class FakeShellExecutor : ShellExecutor {
        var root: Boolean = true
        var failExecution: Boolean = false
        var routeOutput: List<String> = listOf(
            "192.168.43.0/24 dev ap0 proto kernel scope link src 192.168.43.1",
            "default dev tun0 table 102 proto static scope link",
            "198.18.0.0/15 dev tun0 proto kernel scope link src 198.18.0.1",
        )
        val executedCommands = mutableListOf<String>()

        override fun isRoot(): Boolean = root

        override fun exec(command: String): ShellResult {
            executedCommands.add(command)
            return when {
                command.contains("ip route show table all") ->
                    ShellResult(isSuccess = true, code = 0, out = routeOutput, err = emptyList())
                command.contains("dumpsys tethering") ->
                    ShellResult(isSuccess = true, code = 0, out = listOf("  ap0 - TetheredState - Tethered"), err = emptyList())
                else ->
                    ShellResult(isSuccess = !failExecution, code = if (failExecution) 1 else 0, out = emptyList(), err = emptyList())
            }
        }

        override fun exec(commands: List<String>): ShellResult {
            executedCommands.addAll(commands)
            return ShellResult(
                isSuccess = !failExecution,
                code = if (failExecution) 1 else 0,
                out = emptyList(),
                err = if (failExecution) listOf("Simulated iptables failure") else emptyList(),
            )
        }
    }

    @Test
    fun isAvailable_returnsFalseWhenNotRoot() = runBlocking {
        val fakeExecutor = FakeShellExecutor().apply { root = false }
        val backend = RootBackend(FakeContext(), shellExecutor = fakeExecutor)

        assertFalse(backend.isAvailable())
    }

    @Test
    fun start_failsWhenNotRoot() = runBlocking {
        val fakeExecutor = FakeShellExecutor().apply { root = false }
        val backend = RootBackend(FakeContext(), shellExecutor = fakeExecutor)

        backend.start()
        assertTrue(backend.state.value is ShareState.Error)
        assertEquals(ShareFailure.ROOT_UNAVAILABLE, (backend.state.value as ShareState.Error).reason)
    }

    @Test
    fun start_failsWhenNoVpnRoute() = runBlocking {
        val fakeExecutor = FakeShellExecutor().apply {
            routeOutput = listOf("10.0.2.0/24 dev wlan0 proto kernel scope link")
        }
        val backend = RootBackend(FakeContext(), shellExecutor = fakeExecutor)

        backend.start()
        assertTrue(backend.state.value is ShareState.Error)
    }

    @Test
    fun stop_flushesRulesAndSetsStateToOff() = runBlocking {
        val fakeExecutor = FakeShellExecutor()
        val backend = RootBackend(FakeContext(), shellExecutor = fakeExecutor)

        backend.stop()

        assertEquals(ShareState.Off, backend.state.value)
        assertTrue(fakeExecutor.executedCommands.any { it.contains("iptables -F throne_fwd") })
        assertTrue(fakeExecutor.executedCommands.any { it.contains("ip rule del pref 10100") })
    }

    @Test
    fun cleanupLegacyRules_executesTeardownWhenRoot() {
        val fakeExecutor = FakeShellExecutor()
        RootBackend.cleanupLegacyRules(fakeExecutor)

        assertTrue(fakeExecutor.executedCommands.any { it.contains("throne_fwd") })
        assertTrue(fakeExecutor.executedCommands.any { it.contains("throne_nat") })
    }

    @Test
    fun failClosed_blockTrafficExecutesDropCommands() = runBlocking {
        val fakeExecutor = FakeShellExecutor()
        val backend = RootBackend(
            FakeContext(),
            shellExecutor = fakeExecutor,
            failClosedAction = { FailClosedAction.BLOCK_TRAFFIC },
        )

        backend.stop() // cleanup base
        // Verify block command generation works
        val blockCmds = io.nekohasekai.sagernet.hotspotshare.root.RootRules.buildBlockTrafficCommands(listOf("ap0"))
        fakeExecutor.exec(blockCmds)

        assertTrue(fakeExecutor.executedCommands.any { it.contains("iptables -A throne_fwd -i ap0 -j DROP") })
    }

    @Test
    fun failClosed_blockTrafficPreservesDropAcrossStopUntilExplicitCleanup() = runBlocking {
        val fakeExecutor = FakeShellExecutor()
        val backend = RootBackend(
            FakeContext(),
            shellExecutor = fakeExecutor,
            failClosedAction = { FailClosedAction.BLOCK_TRAFFIC },
        )

        backend.start()
        assertTrue(backend.state.value is ShareState.Active)
        fakeExecutor.executedCommands.clear()

        runCatching { backend.handleVpnDropped(FailClosedAction.BLOCK_TRAFFIC) }
        assertTrue(fakeExecutor.executedCommands.any { it.contains("-j DROP") })
        fakeExecutor.executedCommands.clear()

        // Controller mirror stop should preserve the armed DROP rules instead of tearing them down
        backend.stop()
        assertFalse(fakeExecutor.executedCommands.any { it.contains("iptables -X throne_fwd") })

        // Explicit legacy cleanup strips the armed DROP rules when user turns sharing off
        RootBackend.cleanupLegacyRules(fakeExecutor)
        assertTrue(fakeExecutor.executedCommands.any { it.contains("iptables -X throne_fwd") })
    }
}
