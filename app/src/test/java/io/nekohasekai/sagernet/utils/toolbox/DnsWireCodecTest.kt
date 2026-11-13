package io.nekohasekai.sagernet.utils.toolbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsWireCodecTest {

    @Test
    fun encodeAndDecodeCompressedAResponse() {
        val query = DnsWireCodec.encodeQuery("example.com", DnsRecordType.A, txId = 0x1234)
        assertTrue(query.size > 12)

        // Build response echoing question with a compressed pointer answer (0xC0 0x0C -> offset 12)
        val out = ByteArrayOutputStream()
        out.write(0x12); out.write(0x34) // txId
        out.write(0x81); out.write(0x80) // standard response, recursion available, NOERROR
        out.write(0x00); out.write(0x01) // QDCOUNT=1
        out.write(0x00); out.write(0x01) // ANCOUNT=1
        out.write(0x00); out.write(0x00) // NSCOUNT=0
        out.write(0x00); out.write(0x00) // ARCOUNT=0
        // Copy question from query (byte 12 to end)
        out.write(query, 12, query.size - 12)
        // Answer: pointer to name at 12, type A (1), class IN (1), TTL=300, RDLEN=4, 93.184.216.34
        out.write(0xC0); out.write(0x0C)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x00); out.write(0x01); out.write(0x2C)
        out.write(0x00); out.write(0x04)
        out.write(93); out.write(184); out.write(216); out.write(34)

        val decoded = DnsWireCodec.decodeResponse(out.toByteArray())
        assertEquals(0x1234, decoded.txId)
        assertEquals("NOERROR", decoded.rcodeName)
        assertEquals(1, decoded.answers.size)
        assertEquals("example.com", decoded.answers[0].name)
        assertEquals("A", decoded.answers[0].typeLabel)
        assertEquals(300L, decoded.answers[0].ttl)
        assertEquals("93.184.216.34", decoded.answers[0].value)
    }

    @Test
    fun ptrReverseArpaConversion() {
        assertEquals("1.0.0.1.in-addr.arpa", DnsWireCodec.toReverseArpa("1.0.0.1"))
        assertEquals(
            "1.1.1.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.7.4.6.0.6.2.ip6.arpa",
            DnsWireCodec.toReverseArpa("2606:4700::1111"),
        )
    }

    @Test
    fun parseHostPortSupportsIpv4AndIpv6() {
        assertEquals("1.1.1.1" to 53, DnsLookupEngine.parseHostPort("1.1.1.1", 53))
        assertEquals("1.1.1.1" to 5353, DnsLookupEngine.parseHostPort("1.1.1.1:5353", 53))
        assertEquals("2606:4700:4700::1111" to 853, DnsLookupEngine.parseHostPort("[2606:4700:4700::1111]:853", 853))
    }
}
