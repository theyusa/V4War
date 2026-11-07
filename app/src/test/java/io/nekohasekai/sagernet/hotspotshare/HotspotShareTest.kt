package io.nekohasekai.sagernet.hotspotshare

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotShareSelectorTest {

    private fun env(
        sdk: Int = 34,
        running: Boolean = true,
        granted: Boolean = true,
        root: Boolean = false,
    ) = ShareEnvironment(sdk, running, granted, root)

    @Test
    fun autoPrefersShizukuWhenRunningGrantedAndAndroid13() {
        val selection = HotspotShareSelector.resolve(SharePreference.AUTO, env(root = true))
        assertEquals(ShareSelection.Chosen(ShareMethod.SHIZUKU, automatic = true), selection)
    }

    @Test
    fun autoFallsBackToRootBelowAndroid13() {
        val selection = HotspotShareSelector.resolve(SharePreference.AUTO, env(sdk = 32, root = true))
        assertEquals(ShareSelection.Chosen(ShareMethod.ROOT, automatic = true), selection)
    }

    @Test
    fun autoFallsBackToRootWhenShizukuIsNotGranted() {
        val selection = HotspotShareSelector.resolve(SharePreference.AUTO, env(granted = false, root = true))
        assertEquals(ShareSelection.Chosen(ShareMethod.ROOT, automatic = true), selection)
    }

    @Test
    fun autoFallsBackToLanProxyWithoutShizukuOrRoot() {
        val selection = HotspotShareSelector.resolve(SharePreference.AUTO, env(running = false))
        assertEquals(ShareSelection.Chosen(ShareMethod.LAN_PROXY, automatic = true), selection)
    }

    @Test
    fun manualChoiceIsHonouredWhenAvailable() {
        val selection = HotspotShareSelector.resolve(SharePreference.ROOT, env(root = true))
        assertEquals(ShareSelection.Chosen(ShareMethod.ROOT, automatic = false), selection)
    }

    @Test
    fun manualChoiceIsNeverSilentlyReplaced() {
        assertEquals(
            ShareSelection.Unavailable(ShareMethod.ROOT, ShareFailure.ROOT_UNAVAILABLE),
            HotspotShareSelector.resolve(SharePreference.ROOT, env(root = false)),
        )
        assertEquals(
            ShareSelection.Unavailable(ShareMethod.SHIZUKU, ShareFailure.UNSUPPORTED_ANDROID),
            HotspotShareSelector.resolve(SharePreference.SHIZUKU, env(sdk = 32, root = true)),
        )
        assertEquals(
            ShareSelection.Unavailable(ShareMethod.SHIZUKU, ShareFailure.SHIZUKU_NOT_RUNNING),
            HotspotShareSelector.resolve(SharePreference.SHIZUKU, env(running = false)),
        )
        assertEquals(
            ShareSelection.Unavailable(ShareMethod.SHIZUKU, ShareFailure.SHIZUKU_PERMISSION_DENIED),
            HotspotShareSelector.resolve(SharePreference.SHIZUKU, env(granted = false)),
        )
    }

    @Test
    fun lanProxyIsAlwaysAvailable() {
        val none = env(sdk = 24, running = false, granted = false, root = false)
        assertNull(HotspotShareSelector.unavailableReason(ShareMethod.LAN_PROXY, none))
    }

    @Test
    fun shizukuMinimumIsApi33() {
        assertNull(HotspotShareSelector.unavailableReason(ShareMethod.SHIZUKU, env(sdk = 33)))
        assertEquals(
            ShareFailure.UNSUPPORTED_ANDROID,
            HotspotShareSelector.unavailableReason(ShareMethod.SHIZUKU, env(sdk = 32)),
        )
    }

    @Test
    fun unknownPreferenceCodeMeansAuto() {
        assertEquals(SharePreference.AUTO, SharePreference.fromCode("nonsense"))
        assertEquals(SharePreference.LAN_PROXY, SharePreference.fromCode("lan_proxy"))
    }
}

class ShareStateCodecTest {

    private fun roundTrip(state: ShareState) = ShareStateCodec.decode(ShareStateCodec.encode(state))

    @Test
    fun everyStateSurvivesARoundTrip() {
        val states = listOf(
            ShareState.Off,
            ShareState.Starting,
            ShareState.Active(ShareMethod.SHIZUKU),
            ShareState.Active(ShareMethod.LAN_PROXY),
            ShareState.Error(ShareFailure.ROOT_UNAVAILABLE),
            ShareState.Stopped(ShareFailure.VPN_LOST),
        )
        states.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun damagedRecordsDecodeToOff() {
        assertEquals(ShareState.Off, ShareStateCodec.decode(EncodedShareState("active", "bogus", "")))
        assertEquals(ShareState.Off, ShareStateCodec.decode(EncodedShareState("error", "", "bogus")))
        assertEquals(ShareState.Off, ShareStateCodec.decode(EncodedShareState("???", "", "")))
    }

    @Test
    fun failureCodesAreUnique() {
        assertEquals(ShareFailure.entries.size, ShareFailure.entries.map { it.code }.toSet().size)
    }
}

class HotspotShareControllerTest {

    private class FakeBackend(
        override val method: ShareMethod,
        private val startResult: ShareState = ShareState.Active(method),
        private val failStart: Boolean = false,
    ) : HotspotShare {
        private val _state = MutableStateFlow<ShareState>(ShareState.Off)
        override val state: StateFlow<ShareState> = _state.asStateFlow()
        var startCalls = 0
        var stopCalls = 0

        override suspend fun start() {
            startCalls++
            _state.value = ShareState.Starting
            if (failStart) error("boom")
            _state.value = startResult
        }

        override suspend fun stop() {
            stopCalls++
            _state.value = ShareState.Off
        }

        fun drop(cause: ShareFailure) {
            _state.value = ShareState.Stopped(cause)
        }
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    private val published = mutableListOf<ShareState>()

    @After
    fun tearDown() = job.cancel()

    private fun controller(
        pref: SharePreference = SharePreference.AUTO,
        env: ShareEnvironment = ShareEnvironment(34, shizukuRunning = false, shizukuGranted = false, rootAvailable = false),
        factory: (ShareMethod) -> HotspotShare?,
    ) = HotspotShareController(scope, { env }, { pref }, factory, { published += it })

    @Test
    fun startsTheAutoSelectedBackendAndPublishesActive() = runBlocking {
        val backend = FakeBackend(ShareMethod.LAN_PROXY)
        val controller = controller { backend }
        controller.start()
        assertEquals(ShareState.Active(ShareMethod.LAN_PROXY), controller.state.value)
        assertEquals(1, backend.startCalls)
        assertEquals(ShareState.Starting, published.first())
    }

    @Test
    fun missingBackendReportsNotImplemented() = runBlocking {
        val controller = controller { null }
        controller.start()
        assertEquals(
            ShareState.Error(ShareFailure.BACKEND_NOT_IMPLEMENTED, "lan_proxy"),
            controller.state.value,
        )
    }

    @Test
    fun unavailableManualChoiceBecomesAnError() = runBlocking {
        var created = false
        val controller = controller(pref = SharePreference.ROOT) { created = true; FakeBackend(it) }
        controller.start()
        assertEquals(ShareState.Error(ShareFailure.ROOT_UNAVAILABLE), controller.state.value)
        assertFalse(created)
    }

    @Test
    fun stopTearsDownAndReportsOff() = runBlocking {
        val backend = FakeBackend(ShareMethod.LAN_PROXY)
        val controller = controller { backend }
        controller.start()
        controller.stop()
        assertEquals(ShareState.Off, controller.state.value)
        assertEquals(1, backend.stopCalls)
    }

    @Test
    fun secondStartWhileRunningDoesNothing() = runBlocking {
        val backend = FakeBackend(ShareMethod.LAN_PROXY)
        val controller = controller { backend }
        controller.start()
        controller.start()
        assertEquals(1, backend.startCalls)
    }

    @Test
    fun backendDropKeepsStoppedVisibleAndCleansUp() = runBlocking {
        val backend = FakeBackend(ShareMethod.LAN_PROXY)
        val controller = controller { backend }
        controller.start()
        backend.drop(ShareFailure.VPN_LOST)
        assertEquals(ShareState.Stopped(ShareFailure.VPN_LOST), controller.state.value)
        assertEquals(1, backend.stopCalls)
        // a fresh start is possible after the drop
        controller.start()
        assertEquals(2, backend.startCalls)
    }

    @Test
    fun throwingBackendBecomesErrorAndIsCleanedUp() = runBlocking {
        val backend = FakeBackend(ShareMethod.LAN_PROXY, failStart = true)
        val controller = controller { backend }
        controller.start()
        assertEquals(ShareState.Error(ShareFailure.BACKEND_FAILED, "boom"), controller.state.value)
        assertEquals(1, backend.stopCalls)
    }

    @Test
    fun lanProxyBackendTogglesTheControl() = runBlocking {
        var lan = false
        val backend = LanProxyBackend(object : LanProxyControl {
            override fun isLanAccess() = lan
            override fun setLanAccess(enabled: Boolean) { lan = enabled }
        })
        backend.start()
        assertTrue(lan)
        assertEquals(ShareState.Active(ShareMethod.LAN_PROXY), backend.state.value)
        backend.stop()
        assertFalse(lan)
        assertEquals(ShareState.Off, backend.state.value)
    }
}
