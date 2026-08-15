package tr.theyusa.v4war.ui.configuration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConnectionErrorClassifierTest {

    @Test
    fun `refused maps to connection refused`() {
        assertEquals(
            FailureReason.ConnectionRefused,
            classifyConnectionError("dial tcp 1.2.3.4:443: connect: connection refused"),
        )
    }

    @Test
    fun `dns failure maps to dns failure`() {
        assertEquals(
            FailureReason.DnsFailure,
            classifyConnectionError("lookup example.com on 8.8.8.8:53: no such host"),
        )
    }

    @Test
    fun `timeout maps to timeout`() {
        assertEquals(
            FailureReason.Timeout,
            classifyConnectionError("dial tcp 1.2.3.4:443: i/o timeout"),
        )
        assertEquals(FailureReason.Timeout, classifyConnectionError("context deadline exceeded"))
    }

    @Test
    fun `unreachable maps to network unreachable`() {
        assertEquals(
            FailureReason.NetworkUnreachable,
            classifyConnectionError("connect: network is unreachable"),
        )
    }

    @Test
    fun `tls error maps to tls failure`() {
        assertEquals(
            FailureReason.TlsFailure,
            classifyConnectionError("tls: first record does not look like a TLS handshake"),
        )
        assertEquals(
            FailureReason.TlsFailure,
            classifyConnectionError("x509: certificate signed by unknown authority"),
        )
    }

    @Test
    fun `unknown transport maps to unsupported`() {
        assertEquals(
            FailureReason.Unsupported,
            classifyConnectionError("unknown transport type: xhttp"),
        )
    }

    @Test
    fun `unknown message maps to generic with message preserved`() {
        val reason = classifyConnectionError("some unexpected failure")
        assertIs<FailureReason.Generic>(reason)
        assertEquals("some unexpected failure", reason.message)
    }
}
