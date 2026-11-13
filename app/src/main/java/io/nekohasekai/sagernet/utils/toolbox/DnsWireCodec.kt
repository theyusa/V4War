package io.nekohasekai.sagernet.utils.toolbox

import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.security.SecureRandom
import kotlin.math.min

enum class DnsRecordType(val code: Int, val label: String) {
    A(1, "A"),
    AAAA(28, "AAAA"),
    CNAME(5, "CNAME"),
    HTTPS(65, "HTTPS"),
    TXT(16, "TXT"),
    MX(15, "MX"),
    NS(2, "NS"),
    PTR(12, "PTR"),
    ;

    companion object {
        fun fromLabel(name: String): DnsRecordType =
            entries.firstOrNull { it.label.equals(name.trim(), ignoreCase = true) } ?: A
    }
}

data class DnsAnswerRecord(
    val name: String,
    val typeCode: Int,
    val typeLabel: String,
    val ttl: Long,
    val value: String,
)

data class DnsDecodedResponse(
    val txId: Int,
    val rcode: Int,
    val rcodeName: String,
    val truncated: Boolean,
    val recursionAvailable: Boolean,
    val answers: List<DnsAnswerRecord>,
    val authorities: List<DnsAnswerRecord>,
    val additionals: List<DnsAnswerRecord>,
)

object DnsWireCodec {

    private val random = SecureRandom()

    fun normalizeQueryName(raw: String, type: DnsRecordType): String {
        val cleaned = IpLookupEngine.normalizeTarget(raw).trimEnd('.')
        if (type == DnsRecordType.PTR && IpLookupEngine.isIpLiteral(cleaned)) {
            return toReverseArpa(cleaned) ?: cleaned
        }
        return cleaned
    }

    fun toReverseArpa(ip: String): String? {
        val addr = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return null
        val bytes = addr.address
        if (addr is Inet4Address && bytes.size == 4) {
            return (3 downTo 0).joinToString(".") { (bytes[it].toInt() and 0xFF).toString() } + ".in-addr.arpa"
        }
        if (addr is Inet6Address && bytes.size == 16) {
            val nibbles = StringBuilder()
            for (i in 15 downTo 0) {
                val b = bytes[i].toInt() and 0xFF
                nibbles.append((b and 0x0F).toString(16)).append('.')
                nibbles.append(((b ushr 4) and 0x0F).toString(16)).append('.')
            }
            return nibbles.append("ip6.arpa").toString()
        }
        return null
    }

    fun encodeQuery(
        domain: String,
        type: DnsRecordType,
        txId: Int = random.nextInt(0x10000),
    ): ByteArray {
        val out = ByteArrayOutputStream(64)
        // Header: ID(2), Flags(2)=0x0100 (RD=1), QDCOUNT=1, AN=0, NS=0, AR=0
        writeShort(out, txId)
        writeShort(out, 0x0100)
        writeShort(out, 1)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, 0)

        val clean = domain.trim().trimEnd('.')
        if (clean.isEmpty()) {
            out.write(0)
        } else {
            for (label in clean.split('.')) {
                val bytes = label.toByteArray(Charsets.UTF_8)
                val len = min(bytes.size, 63)
                out.write(len)
                out.write(bytes, 0, len)
            }
            out.write(0)
        }

        writeShort(out, type.code)
        writeShort(out, 1) // IN class
        return out.toByteArray()
    }

    fun decodeResponse(packet: ByteArray): DnsDecodedResponse {
        require(packet.size >= 12) { "DNS packet too short (${packet.size} bytes)" }

        val txId = readShort(packet, 0)
        val flags = readShort(packet, 2)
        val truncated = (flags and 0x0200) != 0
        val recursionAvailable = (flags and 0x0080) != 0
        val rcode = flags and 0x000F

        val qdCount = readShort(packet, 4)
        val anCount = readShort(packet, 6)
        val nsCount = readShort(packet, 8)
        val arCount = readShort(packet, 10)

        var offset = 12
        repeat(qdCount) {
            val (_, nextOffset) = readName(packet, offset)
            offset = nextOffset + 4 // QTYPE(2) + QCLASS(2)
            if (offset > packet.size) error("Malformed question section")
        }

        val answers = mutableListOf<DnsAnswerRecord>()
        repeat(anCount) {
            if (offset >= packet.size) return@repeat
            val (rr, nextOffset) = readResourceRecord(packet, offset)
            answers.add(rr)
            offset = nextOffset
        }

        val authorities = mutableListOf<DnsAnswerRecord>()
        repeat(nsCount) {
            if (offset >= packet.size) return@repeat
            val (rr, nextOffset) = readResourceRecord(packet, offset)
            authorities.add(rr)
            offset = nextOffset
        }

        val additionals = mutableListOf<DnsAnswerRecord>()
        repeat(arCount) {
            if (offset >= packet.size) return@repeat
            val (rr, nextOffset) = readResourceRecord(packet, offset)
            additionals.add(rr)
            offset = nextOffset
        }

        return DnsDecodedResponse(
            txId = txId,
            rcode = rcode,
            rcodeName = rcodeToName(rcode),
            truncated = truncated,
            recursionAvailable = recursionAvailable,
            answers = answers,
            authorities = authorities,
            additionals = additionals,
        )
    }

    fun rcodeToName(rcode: Int): String = when (rcode) {
        0 -> "NOERROR"
        1 -> "FORMERR"
        2 -> "SERVFAIL"
        3 -> "NXDOMAIN"
        4 -> "NOTIMP"
        5 -> "REFUSED"
        else -> "RCODE($rcode)"
    }

    private fun readResourceRecord(packet: ByteArray, start: Int): Pair<DnsAnswerRecord, Int> {
        val (name, afterName) = readName(packet, start)
        if (afterName + 10 > packet.size) error("Truncated RR header")
        val typeCode = readShort(packet, afterName)
        // class at afterName + 2
        val ttl = readUInt32(packet, afterName + 4)
        val rdLength = readShort(packet, afterName + 8)
        val rdataStart = afterName + 10
        val rdataEnd = rdataStart + rdLength
        if (rdataEnd > packet.size) error("Truncated RDATA")

        val value = decodeRdata(packet, typeCode, rdataStart, rdLength)
        val rr = DnsAnswerRecord(
            name = name.ifBlank { "." },
            typeCode = typeCode,
            typeLabel = typeCodeToLabel(typeCode),
            ttl = ttl,
            value = value,
        )
        return rr to rdataEnd
    }

    private fun decodeRdata(packet: ByteArray, typeCode: Int, start: Int, len: Int): String {
        return when (typeCode) {
            1 -> { // A
                if (len != 4) "${len}B"
                else InetAddress.getByAddress(packet.copyOfRange(start, start + 4)).hostAddress.orEmpty()
            }
            28 -> { // AAAA
                if (len != 16) "${len}B"
                else InetAddress.getByAddress(packet.copyOfRange(start, start + 16)).hostAddress.orEmpty()
            }
            2, 5, 12 -> { // NS, CNAME, PTR
                readName(packet, start).first.ifBlank { "." }
            }
            15 -> { // MX
                if (len < 3) return "${len}B"
                val pref = readShort(packet, start)
                val exchange = readName(packet, start + 2).first.ifBlank { "." }
                "$pref $exchange"
            }
            16 -> { // TXT
                val parts = mutableListOf<String>()
                var idx = start
                val end = start + len
                while (idx < end) {
                    val strLen = packet[idx].toInt() and 0xFF
                    idx++
                    val actual = min(strLen, end - idx)
                    parts.add(String(packet, idx, actual, Charsets.UTF_8))
                    idx += actual
                }
                parts.joinToString("")
            }
            65 -> { // HTTPS (SVCB wire format: priority(2) + TargetName + SvcParams)
                if (len < 3) return "${len}B"
                val priority = readShort(packet, start)
                val (target, afterTarget) = readName(packet, start + 2)
                val params = decodeSvcParams(packet, afterTarget, start + len)
                val targetDisplay = target.ifBlank { "." }
                if (params.isBlank()) "$priority $targetDisplay" else "$priority $targetDisplay ($params)"
            }
            else -> "TYPE$typeCode (${len}B)"
        }
    }

    private fun decodeSvcParams(packet: ByteArray, start: Int, end: Int): String {
        var idx = start
        val chunks = mutableListOf<String>()
        while (idx + 4 <= end) {
            val key = readShort(packet, idx)
            val valLen = readShort(packet, idx + 2)
            idx += 4
            if (idx + valLen > end) break
            when (key) {
                1 -> { // alpn
                    val alpns = mutableListOf<String>()
                    var p = idx
                    while (p < idx + valLen) {
                        val l = packet[p].toInt() and 0xFF
                        p++
                        if (p + l <= idx + valLen) {
                            alpns.add(String(packet, p, l, Charsets.UTF_8))
                        }
                        p += l
                    }
                    if (alpns.isNotEmpty()) chunks.add("alpn=${alpns.joinToString(",")}")
                }
                4 -> { // ipv4hint
                    val ips = mutableListOf<String>()
                    var p = idx
                    while (p + 4 <= idx + valLen) {
                        ips.add(InetAddress.getByAddress(packet.copyOfRange(p, p + 4)).hostAddress.orEmpty())
                        p += 4
                    }
                    if (ips.isNotEmpty()) chunks.add("ipv4=${ips.joinToString(",")}")
                }
                6 -> { // ipv6hint
                    val ips = mutableListOf<String>()
                    var p = idx
                    while (p + 16 <= idx + valLen) {
                        ips.add(InetAddress.getByAddress(packet.copyOfRange(p, p + 16)).hostAddress.orEmpty())
                        p += 16
                    }
                    if (ips.isNotEmpty()) chunks.add("ipv6=${ips.joinToString(",")}")
                }
            }
            idx += valLen
        }
        return chunks.joinToString(" ")
    }

    private fun readName(packet: ByteArray, startOffset: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var offset = startOffset
        var jumped = false
        var jumpBackOffset = startOffset
        var hops = 0

        while (offset < packet.size && hops < 16) {
            val len = packet[offset].toInt() and 0xFF
            if (len == 0) {
                if (!jumped) jumpBackOffset = offset + 1
                break
            }
            if ((len and 0xC0) == 0xC0) {
                if (offset + 1 >= packet.size) error("Truncated DNS pointer")
                val ptr = ((len and 0x3F) shl 8) or (packet[offset + 1].toInt() and 0xFF)
                if (!jumped) jumpBackOffset = offset + 2
                jumped = true
                offset = ptr
                hops++
                continue
            }
            offset++
            if (offset + len > packet.size) error("Truncated DNS label")
            labels.add(String(packet, offset, len, Charsets.UTF_8))
            offset += len
            if (!jumped) jumpBackOffset = offset
        }

        return labels.joinToString(".") to jumpBackOffset
    }

    private fun typeCodeToLabel(code: Int): String =
        DnsRecordType.entries.firstOrNull { it.code == code }?.label ?: "TYPE$code"

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun readShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun readUInt32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
}
