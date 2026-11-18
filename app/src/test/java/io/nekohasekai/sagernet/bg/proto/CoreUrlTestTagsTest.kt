package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.widget.StatsBar
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreUrlTestTagsTest {

    @Test
    fun keepsProxyTagWhenPresentInStandardConfig() {
        val core = CoreConfig(
            coreConfig = """{"outbounds":[{"tag":"proxy","type":"socks"},{"tag":"direct","type":"direct"}],"route":{"final":"proxy"}}""",
            outboundTags = listOf("proxy"),
        )
        assertEquals(listOf("proxy"), currentTestOutboundTags(core))
    }

    @Test
    fun dropsMissingProxyTagForCustomFullConfigWithoutProxyOutbound() {
        val core = CoreConfig(
            coreConfig = """{"outbounds":[{"tag":"vless-custom","type":"vless"},{"tag":"direct","type":"direct"}],"route":{"final":"vless-custom"}}""",
            outboundTags = listOf("proxy"),
        )
        assertEquals(emptyList<String>(), currentTestOutboundTags(core))
    }

    @Test
    fun keepsProxyTagWhenDeclaredInEndpointsSection() {
        val core = CoreConfig(
            coreConfig = """{"endpoints":[{"tag":"proxy","type":"wireguard"}],"outbounds":[{"tag":"direct","type":"direct"}]}""",
            outboundTags = listOf("proxy"),
        )
        assertEquals(listOf("proxy"), currentTestOutboundTags(core))
    }

    @Test
    fun fallsBackToConfiguredTagsWhenCoreConfigIsBlankOrInvalidJson() {
        val blankCore = CoreConfig(coreConfig = "", outboundTags = listOf("proxy"))
        val invalidCore = CoreConfig(coreConfig = "not-json", outboundTags = listOf("proxy"))
        assertEquals(listOf("proxy"), currentTestOutboundTags(blankCore))
        assertEquals(listOf("proxy"), currentTestOutboundTags(invalidCore))
    }

    @Test
    fun clientUrlTestSafetyTimeoutAddsMarginAndRespectsMinimum() {
        assertEquals(4000L, StatsBar.computeClientUrlTestTimeoutMs(0))
        assertEquals(4000L, StatsBar.computeClientUrlTestTimeoutMs(500))
        assertEquals(6000L, StatsBar.computeClientUrlTestTimeoutMs(3000))
        assertEquals(13000L, StatsBar.computeClientUrlTestTimeoutMs(10000))
    }
}
