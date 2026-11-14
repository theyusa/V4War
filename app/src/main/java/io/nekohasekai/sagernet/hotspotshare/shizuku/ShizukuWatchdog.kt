package io.nekohasekai.sagernet.hotspotshare.shizuku

import io.nekohasekai.sagernet.hotspotshare.HotspotShareLogger
import io.nekohasekai.sagernet.hotspotshare.ShizukuBackend
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Keeps Shizuku / Shizuku Plus daemon alive while Hotspot Share is active on devices
 * where broken ROM keystores cause `shizuku_plus_server` or `shizuku_server` to crash mid-session.
 */
object ShizukuWatchdog {

    private const val TAG = "ShizukuWatchdog"

    const val DEFAULT_KEEP_ALIVE_FLAG_PATH = "/data/local/tmp/v4war_shizuku_keepalive"
    const val DEFAULT_PID_FILE_PATH = "/data/local/tmp/v4war_shizuku_watchdog.pid"
    const val LOCAL_ADB_HOST = "127.0.0.1"
    const val LOCAL_ADB_PORT = 5555

    private const val SOCKET_CONNECT_TIMEOUT_MS = 300
    private const val ADB_IO_TIMEOUT_MS = 1_500

    private const val A_CNXN = 0x4e584e43
    private const val A_OPEN = 0x4e45504f
    private const val A_OKAY = 0x59414b4f
    private const val A_VERSION = 0x01000000
    private const val A_MAXDATA = 4096

    /**
     * Builds an idempotent POSIX shell command string that writes the keep-alive flag,
     * verifies whether a watchdog loop PID is already active, and spawns a detached `setsid`
     * monitor loop that restarts `shizuku_plus_server` (`af.shizuku.plus.api`) or
     * `shizuku_server` (`moe.shizuku.privileged.api`) every 4 seconds if neither is running.
     */
    fun buildWatchdogShellCommand(
        keepAliveFlagPath: String = DEFAULT_KEEP_ALIVE_FLAG_PATH,
        pidFilePath: String = DEFAULT_PID_FILE_PATH,
    ): String {
        val innerLoop = buildString {
            append("echo $$ > \"$pidFilePath\"; ")
            append("while [ -f \"$keepAliveFlagPath\" ]; do ")
            append("if ! pidof shizuku_plus_server >/dev/null 2>&1 && ! pidof shizuku_server >/dev/null 2>&1; then ")
            append("BIN=\"\"; ")
            append("for PKG in af.shizuku.plus.api moe.shizuku.privileged.api; do ")
            append("APK_LINE=$(pm path \"\$PKG\" 2>/dev/null | head -n 1); ")
            append("APK_PATH=\${APK_LINE#package:}; ")
            append("if [ -n \"\$APK_PATH\" ]; then ")
            append("APK_DIR=\$(dirname \"\$APK_PATH\"); ")
            append("for CANDIDATE in \"\$APK_DIR/lib/arm64/libshizuku.so\" \"\$APK_DIR/lib/arm64-v8a/libshizuku.so\"; do ")
            append("if [ -x \"\$CANDIDATE\" ]; then BIN=\"\$CANDIDATE\"; break; fi; ")
            append("done; ")
            append("fi; ")
            append("if [ -n \"\$BIN\" ]; then break; fi; ")
            append("done; ")
            append("if [ -n \"\$BIN\" ] && [ -x \"\$BIN\" ]; then ")
            append("\"\$BIN\" </dev/null >/dev/null 2>&1 & ")
            append("fi; ")
            append("fi; ")
            append("sleep 2; ")
            append("done; ")
            append("rm -f \"$pidFilePath\"")
        }

        return buildString {
            append("echo 1 > \"$keepAliveFlagPath\"; ")
            append("chmod 644 \"$keepAliveFlagPath\" 2>/dev/null || true; ")
            append("if [ -f \"$pidFilePath\" ]; then ")
            append("OLD_PID=\$(cat \"$pidFilePath\" 2>/dev/null); ")
            append("if [ -n \"\$OLD_PID\" ] && kill -0 \"\$OLD_PID\" 2>/dev/null; then exit 0; fi; ")
            append("fi; ")
            append("setsid sh -c '")
            append(innerLoop)
            append("' </dev/null >/dev/null 2>&1 &")
        }
    }

    /**
     * Builds a one-shot shell command to immediately start `libshizuku.so` if neither
     * `shizuku_plus_server` nor `shizuku_server` is currently running.
     */
    internal fun buildOneShotReviveCommand(): String {
        return buildString {
            append("if ! pidof shizuku_plus_server >/dev/null 2>&1 && ! pidof shizuku_server >/dev/null 2>&1; then ")
            append("for PKG in af.shizuku.plus.api moe.shizuku.privileged.api; do ")
            append("APK_LINE=\$(pm path \"\$PKG\" 2>/dev/null | head -n 1); ")
            append("APK_PATH=\${APK_LINE#package:}; ")
            append("if [ -n \"\$APK_PATH\" ]; then ")
            append("APK_DIR=\$(dirname \"\$APK_PATH\"); ")
            append("for CANDIDATE in \"\$APK_DIR/lib/arm64/libshizuku.so\" \"\$APK_DIR/lib/arm64-v8a/libshizuku.so\"; do ")
            append("if [ -x \"\$CANDIDATE\" ]; then \"\$CANDIDATE\" </dev/null >/dev/null 2>&1 & exit 0; fi; ")
            append("done; ")
            append("fi; ")
            append("done; ")
            append("fi")
        }
    }

    /**
     * Executed inside `ShareUserService` (UID 2000) to ensure the detached keep-alive loop is active.
     */
    fun ensureKeepAliveRunning(
        keepAliveFlagPath: String = DEFAULT_KEEP_ALIVE_FLAG_PATH,
        pidFilePath: String = DEFAULT_PID_FILE_PATH,
    ): Boolean {
        return runCatching {
            val cmd = buildWatchdogShellCommand(keepAliveFlagPath, pidFilePath)
            val proc = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(2, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
            }
            val ok = File(keepAliveFlagPath).exists()
            HotspotShareLogger.info(TAG, "ensureKeepAliveRunning finished=$finished flagExists=$ok")
            ok
        }.onFailure { t ->
            HotspotShareLogger.warn(TAG, "ensureKeepAliveRunning failed: ${t.message}")
        }.getOrDefault(false)
    }

    /**
     * Removes the keep-alive flag file and terminates any running watchdog loop when the user
     * explicitly stops sharing.
     */
    fun disableKeepAlive(
        keepAliveFlagPath: String = DEFAULT_KEEP_ALIVE_FLAG_PATH,
        pidFilePath: String = DEFAULT_PID_FILE_PATH,
    ) {
        runCatching {
            File(keepAliveFlagPath).delete()
        }
        runCatching {
            val pidFile = File(pidFilePath)
            if (pidFile.exists()) {
                val pid = pidFile.readText().trim().toIntOrNull()
                pidFile.delete()
                if (pid != null && pid > 1) {
                    ProcessBuilder("sh", "-c", "kill $pid 2>/dev/null || true").start().waitFor(1, TimeUnit.SECONDS)
                }
            }
        }
        HotspotShareLogger.info(TAG, "Keep-alive watchdog disabled")
    }

    fun isLocalAdbPortOpen(
        host: String = LOCAL_ADB_HOST,
        port: Int = LOCAL_ADB_PORT,
        timeoutMs: Int = SOCKET_CONNECT_TIMEOUT_MS,
    ): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        }.getOrDefault(false)
    }

    /**
     * Best-effort revival from app/bg process when Shizuku binder is down.
     * Checks if TCP 127.0.0.1:5555 is accepting connections and attempts either the system `adb`
     * binary or a minimal unauthenticated ADB CNXN/OPEN `shell:` trigger within <=2 seconds.
     */
    fun tryReviveShizukuViaLocalAdbOrShell(
        host: String = LOCAL_ADB_HOST,
        port: Int = LOCAL_ADB_PORT,
    ): Boolean {
        if (ShizukuBackend.isShizukuRunning()) return true

        val portOpen = isLocalAdbPortOpen(host, port, SOCKET_CONNECT_TIMEOUT_MS)
        if (!portOpen) {
            HotspotShareLogger.info(TAG, "Local ADB port $host:$port is not open; waiting for UID 2000 watchdog")
            return ShizukuBackend.isShizukuRunning()
        }

        HotspotShareLogger.info(TAG, "Local ADB port $host:$port is open; attempting Shizuku revival")
        val reviveCmd = buildOneShotReviveCommand()

        val adbBinSuccess = runCatching {
            val adbBinary = listOf("/system/bin/adb", "adb").firstOrNull { candidate ->
                candidate == "adb" || File(candidate).canExecute()
            } ?: "adb"
            val proc = ProcessBuilder(
                adbBinary,
                "-s",
                "$host:$port",
                "shell",
                reviveCmd,
            ).redirectErrorStream(true).start()
            val done = proc.waitFor(1500, TimeUnit.MILLISECONDS)
            if (!done) {
                proc.destroyForcibly()
                false
            } else {
                proc.exitValue() == 0
            }
        }.getOrDefault(false)

        if (!adbBinSuccess) {
            runCatching {
                sendMinimalAdbShellCommand(host, port, reviveCmd)
            }.onFailure { t ->
                HotspotShareLogger.warn(TAG, "Local ADB socket trigger finished with: ${t.message}")
            }
        }

        val running = ShizukuBackend.isShizukuRunning()
        HotspotShareLogger.info(TAG, "tryReviveShizukuViaLocalAdbOrShell outcome: shizukuRunning=$running")
        return running
    }

    internal fun buildAdbPacket(command: Int, arg0: Int, arg1: Int, payload: ByteArray): ByteArray {
        val checksum = payload.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) }
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(payload.size)
        header.putInt(checksum)
        header.putInt(command xor -1)
        val out = ByteArray(24 + payload.size)
        System.arraycopy(header.array(), 0, out, 0, 24)
        System.arraycopy(payload, 0, out, 24, payload.size)
        return out
    }

    private fun sendMinimalAdbShellCommand(host: String, port: Int, shellCommand: String): Boolean {
        Socket().use { socket ->
            socket.soTimeout = ADB_IO_TIMEOUT_MS
            socket.connect(InetSocketAddress(host, port), SOCKET_CONNECT_TIMEOUT_MS)
            val out = socket.getOutputStream()
            val input = socket.getInputStream()

            val banner = "host::\u0000".toByteArray(StandardCharsets.UTF_8)
            out.write(buildAdbPacket(A_CNXN, A_VERSION, A_MAXDATA, banner))
            out.flush()

            val respHeader = ByteArray(24)
            var readTotal = 0
            while (readTotal < 24) {
                val n = input.read(respHeader, readTotal, 24 - readTotal)
                if (n <= 0) return false
                readTotal += n
            }
            val respBuf = ByteBuffer.wrap(respHeader).order(ByteOrder.LITTLE_ENDIAN)
            val cmd = respBuf.int
            val dataLen = respBuf.getInt(12).coerceIn(0, 65536)
            var skipped = 0
            val discard = ByteArray(1024)
            while (skipped < dataLen) {
                val n = input.read(discard, 0, minOf(discard.size, dataLen - skipped))
                if (n <= 0) break
                skipped += n
            }
            if (cmd != A_CNXN && cmd != A_OKAY) {
                return false
            }

            val openPayload = "shell:$shellCommand\u0000".toByteArray(StandardCharsets.UTF_8)
            out.write(buildAdbPacket(A_OPEN, 1, 0, openPayload))
            out.flush()
            return true
        }
    }
}
