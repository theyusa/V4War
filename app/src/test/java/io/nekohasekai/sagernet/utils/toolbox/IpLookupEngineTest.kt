package io.nekohasekai.sagernet.utils.toolbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpLookupEngineTest {

    @Test
    fun normalizeTargetStripsUrlAndPorts() {
        assertEquals("example.com", IpLookupEngine.normalizeTarget("https://example.com:8443/path?x=1"))
        assertEquals("1.1.1.1", IpLookupEngine.normalizeTarget("1.1.1.1:443"))
        assertEquals("2606:4700:4700::1111", IpLookupEngine.normalizeTarget("[2606:4700:4700::1111]:443"))
        assertEquals("2606:4700:4700::1111", IpLookupEngine.normalizeTarget("2606:4700:4700::1111"))
    }

    @Test
    fun classifyIpHandlesSpecialRanges() {
        assertTrue(IpLookupEngine.isIpLiteral("8.8.8.8"))
        assertTrue(IpLookupEngine.isIpLiteral("2001:4860:4860::8888"))
        assertFalse(IpLookupEngine.isIpLiteral("openai.com"))

        assertEquals(4 to IpCategory.PUBLIC, IpLookupEngine.classifyIp("1.1.1.1"))
        assertEquals(4 to IpCategory.PRIVATE, IpLookupEngine.classifyIp("192.168.1.10"))
        assertEquals(4 to IpCategory.PRIVATE, IpLookupEngine.classifyIp("10.0.0.1"))
        assertEquals(4 to IpCategory.PRIVATE, IpLookupEngine.classifyIp("172.16.5.2"))
        assertEquals(4 to IpCategory.CGNAT, IpLookupEngine.classifyIp("100.64.12.1"))
        assertEquals(4 to IpCategory.FAKE_IP, IpLookupEngine.classifyIp("198.18.0.15"))
        assertEquals(4 to IpCategory.LOOPBACK, IpLookupEngine.classifyIp("127.0.0.1"))
        assertEquals(4 to IpCategory.LINK_LOCAL, IpLookupEngine.classifyIp("169.254.1.2"))
        assertEquals(6 to IpCategory.LOOPBACK, IpLookupEngine.classifyIp("::1"))
        assertEquals(6 to IpCategory.PRIVATE, IpLookupEngine.classifyIp("fd00::1"))
    }

    @Test
    fun parseProviderPayloads() {
        val ipWho = IpLookupEngine.parseIpWhoJson(
            """{"success":true,"ip":"1.1.1.1","country":"Australia","country_code":"AU","region":"NSW","city":"Sydney","connection":{"asn":13335,"org":"Cloudflare","isp":"APNIC"},"timezone":{"id":"Australia/Sydney"}}""",
        )
        assertNotNull(ipWho)
        assertEquals("1.1.1.1", ipWho!!.ip)
        assertEquals("AU", ipWho.countryCode)
        assertEquals("AS13335", ipWho.asn)

        val cf = IpLookupEngine.parseCloudflareTrace("fl=1\nip=8.8.4.4\nloc=US\n")
        assertNotNull(cf)
        assertEquals("8.8.4.4", cf!!.ip)
        assertEquals("US", cf.countryCode)
    }
}
