package io.nekohasekai.sagernet.hotspotshare

import io.nekohasekai.sagernet.hotspotshare.shizuku.ShizukuWatchdog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ShizukuWatchdogTest {

    @Test
    fun buildWatchdogShellCommand_containsIdempotentPidGuardAndBothShizukuPackages() {
        val flagPath = "/data/local/tmp/test_keepalive"
        val pidPath = "/data/local/tmp/test_watchdog.pid"
        val cmd = ShizukuWatchdog.buildWatchdogShellCommand(flagPath, pidPath)

        // Writes keep-alive flag
        assertTrue(cmd.contains("echo 1 > \"$flagPath\""))
        // Idempotent PID check via kill -0
        assertTrue(cmd.contains("kill -0 \"\$OLD_PID\""))
        // Detached setsid loop checking every 4 seconds
        assertTrue(cmd.contains("setsid sh -c"))
        assertTrue(cmd.contains("while [ -f \"$flagPath\" ]; do"))
        assertTrue(cmd.contains("sleep 4"))
        // Checks both shizuku_plus_server and shizuku_server
        assertTrue(cmd.contains("pidof shizuku_plus_server"))
        assertTrue(cmd.contains("pidof shizuku_server"))
        // Probes af.shizuku.plus.api first, then moe.shizuku.privileged.api
        val plusIdx = cmd.indexOf("af.shizuku.plus.api")
        val stdIdx = cmd.indexOf("moe.shizuku.privileged.api")
        assertTrue(plusIdx in 0..<stdIdx)
        // Looks for both arm64 and arm64-v8a libshizuku.so paths
        assertTrue(cmd.contains("lib/arm64/libshizuku.so"))
        assertTrue(cmd.contains("lib/arm64-v8a/libshizuku.so"))
    }

    @Test
    fun buildAdbPacket_encodesLittleEndianHeaderWithValidMagicAndChecksum() {
        val payload = "host::\u0000".toByteArray(Charsets.UTF_8)
        val packet = ShizukuWatchdog.buildAdbPacket(0x4e584e43, 0x01000000, 4096, payload)
        assertEquals(24 + payload.size, packet.size)

        val buf = ByteBuffer.wrap(packet, 0, 24).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val length = buf.int
        val checksum = buf.int
        val magic = buf.int

        assertEquals(0x4e584e43, command)
        assertEquals(0x01000000, arg0)
        assertEquals(4096, arg1)
        assertEquals(payload.size, length)
        assertEquals(payload.sumOf { it.toInt() and 0xFF }, checksum)
        assertEquals(command xor -1, magic)
    }

    @Test
    fun isSafeBlackholeHealth_acceptsOnlyHealthyOrDownstreamOff() {
        assertTrue(ShizukuBackend.isSafeBlackholeHealth("HEALTHY"))
        assertTrue(ShizukuBackend.isSafeBlackholeHealth("DOWNSTREAM_OFF"))
        assertFalse(ShizukuBackend.isSafeBlackholeHealth("ABSENT"))
        assertFalse(ShizukuBackend.isSafeBlackholeHealth("TIMED_OUT"))
        assertFalse(ShizukuBackend.isSafeBlackholeHealth("DRIFTED:rmnet_data0"))
    }
}
