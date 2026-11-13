package io.nekohasekai.sagernet.outbound

import io.nekohasekai.sagernet.outbound.common.Tls
import io.nekohasekai.sagernet.outbound.types.Vless
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsFragmentTest {

    @Test
    fun defaultTlsDoesNotEmitFragmentByDefault() {
        val vless = Vless().apply {
            server = "185.157.217.124"
            serverPort = 443
            uuid = "a7c60bce-79c0-4442-9369-7a652b36c625"
            tls.enabled = true
            tls.server_name = "cdn.openai.com"
        }

        val ctx = BuildContext.DEFAULT
        assertFalse("Default fragmentDefaultOn should be false", ctx.fragmentDefaultOn)

        val built = vless.build(ctx)
        assertTrue(built.ok)
        val tlsObj = built.json.obj("tls")

        assertFalse("fragment must not be emitted by default", tlsObj.contains("fragment"))
        assertFalse("record_fragment must not be emitted by default", tlsObj.contains("record_fragment"))
        assertFalse(tlsObj.contains("fragment_fallback_delay"))
    }

    @Test
    fun explicitFragmentOffSuppressesFragmentWhenGlobalOn() {
        val vless = Vless().apply {
            server = "1.2.3.4"
            serverPort = 443
            uuid = "a7c60bce-79c0-4442-9369-7a652b36c625"
            tls.enabled = true
            tls.server_name = "example.com"
            tls.fragment = false
            tls.fragment_unspecified = false
        }

        val ctx = BuildContext(fragmentDefaultOn = true)
        val built = vless.build(ctx)
        assertTrue(built.ok)
        val tlsObj = built.json.obj("tls")

        assertFalse(tlsObj.contains("fragment"))
        assertFalse(tlsObj.contains("record_fragment"))
    }

    @Test
    fun explicitFragmentOnEmitsFragmentWithoutForcingRecordFragment() {
        val vless = Vless().apply {
            server = "1.2.3.4"
            serverPort = 443
            uuid = "a7c60bce-79c0-4442-9369-7a652b36c625"
            tls.enabled = true
            tls.server_name = "example.com"
            tls.fragment = true
            tls.fragment_unspecified = false
            tls.fragment_fallback_delay = "50ms"
        }

        val ctx = BuildContext(fragmentDefaultOn = false)
        val built = vless.build(ctx)
        assertTrue(built.ok)
        val tlsObj = built.json.obj("tls")

        assertTrue(tlsObj.bool("fragment"))
        assertFalse(tlsObj.contains("record_fragment"))
        assertEquals("50ms", tlsObj.string("fragment_fallback_delay"))
    }

    @Test
    fun globalOnEmitsFragmentForUnspecifiedProfiles() {
        val vless = Vless().apply {
            server = "1.2.3.4"
            serverPort = 443
            uuid = "a7c60bce-79c0-4442-9369-7a652b36c625"
            tls.enabled = true
            tls.server_name = "example.com"
            tls.record_fragment = true
        }

        val ctx = BuildContext(fragmentDefaultOn = true)
        val built = vless.build(ctx)
        assertTrue(built.ok)
        val tlsObj = built.json.obj("tls")

        assertTrue(tlsObj.bool("fragment"))
        assertTrue(tlsObj.bool("record_fragment"))
    }
}
