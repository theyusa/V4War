package io.nekohasekai.sagernet.hotspotshare

import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Cross-process ring-buffer and file diagnostic logger for Hotspot & LAN Share.
 * Writes to `/data/local/tmp/v4war_hotspot_share.log` when running in the isolated
 * Shizuku UserService process (`ShareUserService`, UID 2000 or UID 0), and writes to
 * per-process files under `noBackupFilesDir` in app (`main` / `:bg`) processes so
 * concurrent appends and truncations never corrupt one another.
 */
object HotspotShareLogger {

    private const val LOGCAT_TAG = "HotspotShareDiag"
    const val SHELL_LOG_PATH = "/data/local/tmp/v4war_hotspot_share.log"
    const val SHELL_LOG_DISABLED_FLAG_PATH = "/data/local/tmp/v4war_hotspot_share_log.disabled"
    const val APP_LOG_FILENAME = "hotspot_share_diag.log"
    const val MAIN_LOG_FILENAME = "hotspot_share_main.log"
    const val BG_LOG_FILENAME = "hotspot_share_bg.log"
    const val SHELL_MIRROR_FILENAME = "hotspot_share_shell_mirror.log"
    private const val ENABLED_MARKER_FILENAME = "hotspot_share_log.enabled"
    private const val CLEAR_CUTOFF_FILENAME = "hotspot_share_log.cutoff"

    const val MAX_MEMORY_LINES = 300
    const val MAX_FILE_BYTES = 128 * 1024L
    const val KEEP_FILE_BYTES = 64 * 1024L
    private const val MIGRATION_DEFAULT_OFF_KEY = "hotspot_share_log_default_off_v1"

    enum class LogSource {
        ALL,
        SHIZUKU,
        ROOT,
        PROXY,
    }

    enum class LogLevelFilter {
        ALL,
        WARN_PLUS,
        ERROR_ONLY,
    }

    private const val SHELL_UID = 2000

    data class ParsedLogLine(
        val fullTimestamp: String,
        val timeOnly: String,
        val level: String,
        val tag: String,
        val message: String,
    )

    private data class LogEntryBlock(
        val parsed: ParsedLogLine?,
        val lines: MutableList<String>,
    )

    private val memoryRing = ConcurrentLinkedDeque<String>()
    private val fileLock = Any()

    private val timestampFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    private val lineTimestampRegex = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\b""")
    private val fullLineRegex = Regex(
        """^(\d{4}-\d{2}-\d{2}\s+(\d{2}:\d{2}:\d{2}\.\d{3}))\s+(INFO|WARN|ERROR|DEBUG)\s+\[([^\]]+)]\s*(.*)$"""
    )

    @Volatile
    private var fallbackInProcessEnabled: Boolean = false

    @Volatile
    private var isolatedServiceProcess: Boolean = false

    @Volatile
    private var explicitIsolatedEnabled: Boolean? = null

    @Volatile
    private var cachedClearCutoffStamp: String? = null

    @Volatile
    internal var overrideEnabledForTest: Boolean? = null

    @Volatile
    internal var overrideShellDisabledFlagFileForTest: File? = null

    @Volatile
    internal var overrideWriteFileForTest: File? = null

    @Volatile
    internal var overrideReadFilesForTest: List<File>? = null

    /** Marks the current process as the standalone Shizuku daemon (`ShareUserService`). */
    fun markIsolatedServiceProcess() {
        isolatedServiceProcess = true
    }

    internal fun resetIsolatedModeForTest() {
        isolatedServiceProcess = false
        explicitIsolatedEnabled = null
        cachedClearCutoffStamp = null
    }

    private fun isIsolatedOrShellProcess(): Boolean {
        if (isolatedServiceProcess) return true
        val uid = runCatching { Process.myUid() }.getOrDefault(-1)
        return uid == SHELL_UID
    }

    private fun isBgProcess(): Boolean {
        val processName = runCatching {
            if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else null
        }.getOrNull()
        return processName?.endsWith(":bg") == true
    }

    fun isLoggingEnabled(): Boolean {
        overrideEnabledForTest?.let { return it }
        if (isIsolatedOrShellProcess()) {
            explicitIsolatedEnabled?.let { return it }
            val flagFile = overrideShellDisabledFlagFileForTest ?: File(SHELL_LOG_DISABLED_FLAG_PATH)
            return !runCatching { flagFile.exists() }.getOrDefault(false)
        }
        val fromStore = runCatching { DataStore.hotspotShareLogEnabled }.getOrNull()
        if (fromStore != null) return fromStore
        val marker = readAppMarkerEnabled()
        if (marker != null) return marker
        return fallbackInProcessEnabled
    }

    fun setLoggingEnabled(enabled: Boolean) {
        fallbackInProcessEnabled = enabled
        if (isIsolatedOrShellProcess()) {
            explicitIsolatedEnabled = enabled
            updateShellDisabledFlag(enabled)
        } else {
            runCatching { DataStore.hotspotShareLogEnabled = enabled }
            writeAppMarkerEnabled(enabled)
            updateShellDisabledFlag(enabled)
        }
    }

    fun ensureDefaultOffMigration() {
        runCatching {
            val store = DataStore.configurationStore
            if (!store.getBoolean(MIGRATION_DEFAULT_OFF_KEY, false)) {
                store.putBoolean(MIGRATION_DEFAULT_OFF_KEY, true)
                DataStore.hotspotShareLogEnabled = false
                fallbackInProcessEnabled = false
                writeAppMarkerEnabled(false)
                updateShellDisabledFlag(false)
                clearAll()
            } else {
                val enabled = DataStore.hotspotShareLogEnabled
                fallbackInProcessEnabled = enabled
                writeAppMarkerEnabled(enabled)
                updateShellDisabledFlag(enabled)
            }
        }
    }

    fun classifyTagSource(tag: String): LogSource {
        val lower = tag.lowercase()
        return when {
            lower.contains("root") -> LogSource.ROOT
            lower.contains("shizuku") ||
                lower.contains("shareuserservice") ||
                lower.contains("upstream") ||
                lower.contains("tetherwatch") ||
                lower.contains("tun2socks") ||
                lower.contains("hevtunnel") ||
                lower.contains("testtun") ||
                lower.contains("watchdog") -> LogSource.SHIZUKU
            lower.contains("lanproxy") ||
                lower.contains("autogateway") ||
                lower.contains("autoconnect") ||
                lower.contains("clash") ||
                lower.contains("lansharing") ||
                lower.contains("proxy") -> LogSource.PROXY
            else -> LogSource.ALL
        }
    }

    private fun parseBlocks(rawLogs: String): List<LogEntryBlock> {
        val blocks = ArrayList<LogEntryBlock>()
        for (raw in rawLogs.lineSequence()) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue
            val parsed = parseLogLine(line)
            if (parsed != null || blocks.isEmpty()) {
                blocks.add(LogEntryBlock(parsed, mutableListOf(line.trim())))
            } else {
                blocks.last().lines.add(line.trim())
            }
        }
        return blocks
    }

    fun countEntries(rawLogs: String): Int {
        if (rawLogs.isBlank()) return 0
        return parseBlocks(rawLogs).size
    }

    fun filterAndSortLogs(
        rawLogs: String,
        source: LogSource = LogSource.ALL,
        levelFilter: LogLevelFilter = LogLevelFilter.ALL,
        searchQuery: String = "",
        newestFirst: Boolean = true,
    ): String {
        if (rawLogs.isBlank()) return ""
        val blocks = parseBlocks(rawLogs)
        val query = searchQuery.trim()
        val filtered = blocks.filter { block ->
            val parsed = block.parsed
            val sourceMatches = when (source) {
                LogSource.ALL -> true
                else -> parsed != null && classifyTagSource(parsed.tag) == source
            }
            if (!sourceMatches) return@filter false

            val level = parsed?.level?.uppercase().orEmpty()
            val levelMatches = when (levelFilter) {
                LogLevelFilter.ALL -> true
                LogLevelFilter.WARN_PLUS -> level == "WARN" || level == "WARNING" || level == "ERROR"
                LogLevelFilter.ERROR_ONLY -> level == "ERROR"
            }
            if (!levelMatches) return@filter false

            if (query.isNotEmpty()) {
                block.lines.any { it.contains(query, ignoreCase = true) }
            } else {
                true
            }
        }
        val ordered = if (newestFirst) filtered.asReversed() else filtered
        return ordered.joinToString("\n") { it.lines.joinToString("\n") }
    }

    internal fun updateShellDisabledFlag(enabled: Boolean) {
        val flagFile = overrideShellDisabledFlagFileForTest ?: File(SHELL_LOG_DISABLED_FLAG_PATH)
        runCatching {
            if (enabled) {
                if (flagFile.exists()) flagFile.delete()
            } else {
                flagFile.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
                if (!flagFile.exists()) {
                    flagFile.writeText("disabled\n")
                    runCatching { flagFile.setReadable(true, false) }
                }
            }
        }
    }

    fun parseLogLine(rawLine: String): ParsedLogLine? {
        val trimmed = rawLine.trim()
        val match = fullLineRegex.matchEntire(trimmed) ?: return null
        return ParsedLogLine(
            fullTimestamp = match.groupValues[1],
            timeOnly = match.groupValues[2],
            level = match.groupValues[3],
            tag = match.groupValues[4],
            message = match.groupValues[5],
        )
    }

    fun formatReadableLogs(rawLogs: String): String {
        if (rawLogs.isBlank()) return ""
        val formatted = ArrayList<String>()
        for (raw in rawLogs.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val parsed = parseLogLine(line)
            if (parsed != null) {
                formatted.add("${parsed.timeOnly} · ${parsed.level} · [${parsed.tag}]\n  ${parsed.message}")
            } else {
                formatted.add(line)
            }
        }
        return formatted.joinToString("\n\n")
    }

    fun info(tag: String, message: String) {
        record("INFO", Log.INFO, tag, message)
    }

    fun warn(tag: String, message: String) {
        record("WARN", Log.WARN, tag, message)
    }

    fun error(tag: String, message: String) {
        record("ERROR", Log.ERROR, tag, message)
    }

    internal fun formatEntry(
        level: String,
        tag: String,
        message: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val stamp = timestampFormat.get()!!.format(Date(nowMillis))
        val cleanTag = tag.trim().ifEmpty { "HotspotShare" }
        val singleLineMessage = message.replace('\n', ' ').replace('\r', ' ').trim()
        return "$stamp $level [$cleanTag] $singleLineMessage"
    }

    private fun record(level: String, logPriority: Int, tag: String, message: String) {
        if (!isLoggingEnabled()) return
        var nowMillis = System.currentTimeMillis()
        // Ensure entries recorded right after clearAll() never share <= clear cutoff timestamp.
        val cutoff = if (!isIsolatedOrShellProcess() && overrideReadFilesForTest == null) {
            readClearCutoffStamp()
        } else {
            null
        }
        if (cutoff != null && timestampFormat.get()!!.format(Date(nowMillis)) <= cutoff) {
            nowMillis += 2L
        }
        val line = formatEntry(level, tag, message, nowMillis)
        runCatching { Log.println(logPriority, LOGCAT_TAG, "[$tag] $message") }
        appendToMemory(line)
        appendToFile(line)
    }

    internal fun appendToMemory(line: String) {
        memoryRing.addLast(line)
        while (memoryRing.size > MAX_MEMORY_LINES) {
            memoryRing.pollFirst() ?: break
        }
    }

    private fun resolveAppNoBackupFile(filename: String): File? {
        return runCatching {
            val dir = SagerNet.application.noBackupFilesDir
            if (dir != null) File(dir, filename) else null
        }.getOrNull()
    }

    private fun writeAppMarkerEnabled(enabled: Boolean) {
        val file = resolveAppNoBackupFile(ENABLED_MARKER_FILENAME) ?: return
        runCatching {
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            file.writeText(if (enabled) "1" else "0")
        }
    }

    private fun readAppMarkerEnabled(): Boolean? {
        val file = resolveAppNoBackupFile(ENABLED_MARKER_FILENAME) ?: return null
        return runCatching {
            if (!file.exists()) return@runCatching null
            when (file.readText().trim()) {
                "1" -> true
                "0" -> false
                else -> null
            }
        }.getOrNull()
    }

    private fun readClearCutoffStamp(): String? {
        if (overrideReadFilesForTest != null || overrideWriteFileForTest != null) return null
        cachedClearCutoffStamp?.let { return it }
        val file = resolveAppNoBackupFile(CLEAR_CUTOFF_FILENAME) ?: return null
        val loaded = runCatching {
            if (file.exists()) file.readText().trim().ifEmpty { null } else null
        }.getOrNull()
        if (loaded != null) {
            cachedClearCutoffStamp = loaded
        }
        return loaded
    }

    private fun writeClearCutoffStampNow() {
        if (overrideReadFilesForTest != null || overrideWriteFileForTest != null) return
        val file = resolveAppNoBackupFile(CLEAR_CUTOFF_FILENAME) ?: return
        val stamp = timestampFormat.get()!!.format(Date(System.currentTimeMillis()))
        cachedClearCutoffStamp = stamp
        runCatching {
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            file.writeText(stamp)
        }
    }

    private fun resolveWriteFile(): File? {
        overrideWriteFileForTest?.let { return it }
        if (isIsolatedOrShellProcess()) {
            return File(SHELL_LOG_PATH)
        }
        val filename = if (isBgProcess()) BG_LOG_FILENAME else MAIN_LOG_FILENAME
        return resolveAppNoBackupFile(filename)
    }

    private fun resolveReadFiles(): List<File> {
        overrideReadFilesForTest?.let { return it }
        val files = LinkedHashSet<File>()
        if (isIsolatedOrShellProcess()) {
            files.add(File(SHELL_LOG_PATH))
        } else {
            resolveAppNoBackupFile(MAIN_LOG_FILENAME)?.let { files.add(it) }
            resolveAppNoBackupFile(BG_LOG_FILENAME)?.let { files.add(it) }
            resolveAppNoBackupFile(SHELL_MIRROR_FILENAME)?.let { files.add(it) }
            resolveAppNoBackupFile(APP_LOG_FILENAME)?.let { files.add(it) }
            files.add(File(SHELL_LOG_PATH))
        }
        overrideWriteFileForTest?.let { files.add(it) }
        return files.toList()
    }

    private fun appendToFile(line: String) {
        val target = resolveWriteFile() ?: return
        synchronized(fileLock) {
            runCatching {
                val isNew = !target.exists()
                target.parentFile?.let { parent ->
                    if (!parent.exists()) parent.mkdirs()
                }
                target.appendText("$line\n")
                if (isNew) {
                    runCatching { target.setReadable(true, false) }
                }
                truncateIfLarge(target)
            }
        }
    }

    /**
     * Persists log lines fetched over Binder from `ShareUserService` (UID 2000 or UID 0)
     * into the app's private `noBackupFilesDir`, ensuring daemon logs remain readable
     * under strict SELinux and survive `ShareUserService` termination or crashes.
     */
    fun mirrorShellServiceLogs(
        lines: List<String>,
        overrideMirrorFileForTest: File? = null,
    ) {
        if (lines.isEmpty()) return
        if (isIsolatedOrShellProcess() && overrideMirrorFileForTest == null) return
        val mirrorFile = overrideMirrorFileForTest
            ?: resolveAppNoBackupFile(SHELL_MIRROR_FILENAME)
            ?: return
        val cutoff = if (overrideMirrorFileForTest == null) readClearCutoffStamp() else null
        synchronized(fileLock) {
            runCatching {
                val combined = LinkedHashSet<String>()
                if (mirrorFile.exists() && mirrorFile.canRead()) {
                    mirrorFile.useLines { seq ->
                        seq.forEach { raw ->
                            val trimmed = raw.trim()
                            if (trimmed.isNotEmpty() && !isBeforeCutoff(trimmed, cutoff)) {
                                combined.add(trimmed)
                            }
                        }
                    }
                }
                for (raw in lines) {
                    val trimmed = raw.trim()
                    if (trimmed.isNotEmpty() && !isBeforeCutoff(trimmed, cutoff)) {
                        combined.add(trimmed)
                    }
                }
                if (combined.isEmpty()) return@runCatching
                val sorted = sortLinesByTimestamp(combined)
                mirrorFile.parentFile?.let { parent ->
                    if (!parent.exists()) parent.mkdirs()
                }
                mirrorFile.writeText(sorted.joinToString("\n", postfix = "\n"))
                truncateIfLarge(mirrorFile)
            }
        }
    }

    internal fun truncateIfLarge(
        file: File,
        maxBytes: Long = MAX_FILE_BYTES,
        keepBytes: Long = KEEP_FILE_BYTES,
    ) {
        if (!file.exists()) return
        val length = file.length()
        if (length <= maxBytes) return

        val kept = RandomAccessFile(file, "r").use { reader ->
            val seekTo = (length - keepBytes).coerceAtLeast(0L)
            reader.seek(seekTo)
            if (seekTo > 0L) {
                reader.readLine()
            }
            val remainingLen = (reader.length() - reader.filePointer).coerceAtLeast(0L).toInt()
            val remaining = ByteArray(remainingLen)
            reader.readFully(remaining)
            remaining
        }
        file.writeBytes(kept)
        runCatching { file.setReadable(true, false) }
    }

    fun getInMemoryLines(): List<String> {
        return memoryRing.toList()
    }

    private fun isBeforeCutoff(line: String, cutoffStamp: String?): Boolean {
        if (cutoffStamp.isNullOrEmpty()) return false
        val ts = lineTimestampRegex.find(line)?.groupValues?.get(1) ?: return false
        return ts <= cutoffStamp
    }

    private fun sortLinesByTimestamp(lines: Collection<String>): List<String> {
        val indexed = lines.mapIndexed { idx, line ->
            val ts = lineTimestampRegex.find(line)?.groupValues?.get(1) ?: ""
            Triple(ts, idx, line)
        }
        return indexed.sortedWith(
            Comparator { a, b ->
                val aHasTs = a.first.isNotEmpty()
                val bHasTs = b.first.isNotEmpty()
                when {
                    aHasTs && bHasTs -> {
                        val cmp = a.first.compareTo(b.first)
                        if (cmp != 0) cmp else a.second.compareTo(b.second)
                    }
                    aHasTs -> -1
                    bHasTs -> 1
                    else -> a.second.compareTo(b.second)
                }
            }
        ).map { it.third }
    }

    fun readMergedLogs(extraLines: List<String> = emptyList()): String {
        val cutoff = if (!isIsolatedOrShellProcess()) readClearCutoffStamp() else null
        val combined = LinkedHashSet<String>()
        synchronized(fileLock) {
            for (file in resolveReadFiles()) {
                runCatching {
                    if (file.exists() && file.canRead()) {
                        file.useLines { seq ->
                            seq.forEach { raw ->
                                val trimmed = raw.trim()
                                if (trimmed.isNotEmpty() && !isBeforeCutoff(trimmed, cutoff)) {
                                    combined.add(trimmed)
                                }
                            }
                        }
                    }
                }
            }
        }
        for (line in memoryRing) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !isBeforeCutoff(trimmed, cutoff)) {
                combined.add(trimmed)
            }
        }
        for (line in extraLines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !isBeforeCutoff(trimmed, cutoff)) {
                combined.add(trimmed)
            }
        }
        if (combined.isEmpty()) return ""
        return sortLinesByTimestamp(combined).joinToString("\n")
    }

    fun clearAll() {
        memoryRing.clear()
        if (!isIsolatedOrShellProcess()) {
            writeClearCutoffStampNow()
        }
        synchronized(fileLock) {
            for (file in resolveReadFiles()) {
                runCatching {
                    if (file.exists()) {
                        file.writeText("")
                    }
                }
            }
        }
    }
}
