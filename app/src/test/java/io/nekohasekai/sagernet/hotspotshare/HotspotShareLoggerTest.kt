package io.nekohasekai.sagernet.hotspotshare

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HotspotShareLoggerTest {

    private lateinit var tempDir: File
    private lateinit var appLogFile: File
    private lateinit var shellLogFile: File

    private lateinit var disabledFlagFile: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("hotspot_logger_test").toFile()
        appLogFile = File(tempDir, "hotspot_share_diag.log")
        shellLogFile = File(tempDir, "v4war_hotspot_share.log")
        disabledFlagFile = File(tempDir, "v4war_hotspot_share_log.disabled")
        HotspotShareLogger.overrideEnabledForTest = null
        HotspotShareLogger.overrideShellDisabledFlagFileForTest = disabledFlagFile
        HotspotShareLogger.setLoggingEnabled(true)
        HotspotShareLogger.overrideWriteFileForTest = appLogFile
        HotspotShareLogger.overrideReadFilesForTest = listOf(shellLogFile, appLogFile)
        HotspotShareLogger.clearAll()
    }

    @After
    fun tearDown() {
        HotspotShareLogger.clearAll()
        HotspotShareLogger.overrideEnabledForTest = null
        HotspotShareLogger.setLoggingEnabled(false)
        HotspotShareLogger.overrideShellDisabledFlagFileForTest = null
        HotspotShareLogger.overrideWriteFileForTest = null
        HotspotShareLogger.overrideReadFilesForTest = null
        HotspotShareLogger.resetIsolatedModeForTest()
        tempDir.deleteRecursively()
    }

    @Test
    fun formatEntry_matchesExpectedTimestampLevelTagFormat() {
        val formatted = HotspotShareLogger.formatEntry(
            level = "INFO",
            tag = "ShizukuBackend",
            message = "Test network up: testtun0",
            nowMillis = 1700000000123L,
        )
        assertTrue(
            "Formatted entry was: $formatted",
            formatted.matches(Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.123 INFO \[ShizukuBackend\] Test network up: testtun0$""")),
        )
    }

    @Test
    fun ringBuffer_capsInMemoryLinesAtMaxLimit() {
        assertEquals(300, HotspotShareLogger.MAX_MEMORY_LINES)
        assertEquals(128 * 1024L, HotspotShareLogger.MAX_FILE_BYTES)
        assertEquals(64 * 1024L, HotspotShareLogger.KEEP_FILE_BYTES)
        for (i in 1..450) {
            HotspotShareLogger.info("TestTag", "line-$i")
        }
        val lines = HotspotShareLogger.getInMemoryLines()
        assertEquals(HotspotShareLogger.MAX_MEMORY_LINES, lines.size)
        val expectedFirst = 450 - HotspotShareLogger.MAX_MEMORY_LINES + 1
        assertTrue(lines.first().endsWith("[TestTag] line-$expectedFirst"))
        assertTrue(lines.last().endsWith("[TestTag] line-450"))
    }

    @Test
    fun truncateIfLarge_truncatesFileAbove256KbKeepingTail128Kb() {
        val largeBuilder = StringBuilder()
        var idx = 0
        while (largeBuilder.length < 300 * 1024) {
            idx++
            largeBuilder.append("2026-11-14 21:00:00.000 INFO [Fill] entry-number-$idx-padding-0123456789abcdef\n")
        }
        appLogFile.writeText(largeBuilder.toString())
        assertTrue(appLogFile.length() > HotspotShareLogger.MAX_FILE_BYTES)

        HotspotShareLogger.truncateIfLarge(appLogFile)

        assertTrue(
            "Expected truncated length <= ${HotspotShareLogger.KEEP_FILE_BYTES}, got ${appLogFile.length()}",
            appLogFile.length() in 1L..HotspotShareLogger.KEEP_FILE_BYTES,
        )
        val remainingLines = appLogFile.readLines()
        assertTrue(remainingLines.last().contains("entry-number-$idx-padding"))
        assertFalse(remainingLines.first().contains("entry-number-1-padding"))
    }

    @Test
    fun readMergedLogs_mergesDeduplicatesAndSortsChronologically() {
        shellLogFile.writeText(
            """
            2026-11-14 21:10:02.000 INFO [ShareUserService] Upstream verified: testtun0
            2026-11-14 21:10:01.000 INFO [ShareUserService] Test network up: testtun0
            """.trimIndent() + "\n"
        )
        appLogFile.writeText(
            """
            2026-11-14 21:10:00.500 INFO [ShizukuBackend] Starting Shizuku hotspot share session
            2026-11-14 21:10:02.000 INFO [ShareUserService] Upstream verified: testtun0
            """.trimIndent() + "\n"
        )
        val extra = listOf(
            "2026-11-14 21:10:01.000 INFO [ShareUserService] Test network up: testtun0",
            "2026-11-14 21:10:03.250 WARN [ShizukuBackend] Fail-closed BLOCK_TRAFFIC triggered",
        )

        val merged = HotspotShareLogger.readMergedLogs(extra)
        val lines = merged.lines()
        assertEquals(4, lines.size)
        assertTrue(lines[0].startsWith("2026-11-14 21:10:00.500"))
        assertTrue(lines[1].startsWith("2026-11-14 21:10:01.000"))
        assertTrue(lines[2].startsWith("2026-11-14 21:10:02.000"))
        assertTrue(lines[3].startsWith("2026-11-14 21:10:03.250"))
    }

    @Test
    fun clearAll_emptiesMemoryAndLogFiles() {
        HotspotShareLogger.info("Tag", "first entry")
        shellLogFile.writeText("2026-11-14 21:10:00.000 INFO [Shell] test\n")
        assertTrue(HotspotShareLogger.getInMemoryLines().isNotEmpty())

        HotspotShareLogger.clearAll()

        assertTrue(HotspotShareLogger.getInMemoryLines().isEmpty())
        assertEquals("", HotspotShareLogger.readMergedLogs())
    }

    @Test
    fun setLoggingEnabled_gatesRecordingAndTogglesState() {
        assertTrue(HotspotShareLogger.isLoggingEnabled())

        HotspotShareLogger.setLoggingEnabled(false)
        org.junit.Assert.assertFalse(HotspotShareLogger.isLoggingEnabled())
        HotspotShareLogger.info("GateTest", "should not be recorded")
        assertTrue(HotspotShareLogger.getInMemoryLines().isEmpty())
        org.junit.Assert.assertFalse(appLogFile.exists() && appLogFile.readText().contains("should not be recorded"))

        HotspotShareLogger.setLoggingEnabled(true)
        assertTrue(HotspotShareLogger.isLoggingEnabled())
        HotspotShareLogger.info("GateTest", "should be recorded")
        assertEquals(1, HotspotShareLogger.getInMemoryLines().size)
        assertTrue(appLogFile.readText().contains("should be recorded"))
    }

    @Test
    fun overrideEnabledForTest_suppressesRecording() {
        HotspotShareLogger.overrideEnabledForTest = false
        org.junit.Assert.assertFalse(HotspotShareLogger.isLoggingEnabled())
        HotspotShareLogger.warn("OverrideTest", "suppressed entry")
        assertTrue(HotspotShareLogger.getInMemoryLines().isEmpty())

        HotspotShareLogger.overrideEnabledForTest = null
        assertTrue(HotspotShareLogger.isLoggingEnabled())
        HotspotShareLogger.warn("OverrideTest", "visible entry")
        assertEquals(1, HotspotShareLogger.getInMemoryLines().size)
    }

    @Test
    fun updateShellDisabledFlag_createsAndRemovesFlagFile() {
        org.junit.Assert.assertFalse(disabledFlagFile.exists())

        HotspotShareLogger.updateShellDisabledFlag(false)
        assertTrue(disabledFlagFile.exists())

        HotspotShareLogger.updateShellDisabledFlag(true)
        org.junit.Assert.assertFalse(disabledFlagFile.exists())
    }

    @Test
    fun parseLogLine_extractsStructuredFieldsOrReturnsNullForUnformattedLine() {
        val parsed = HotspotShareLogger.parseLogLine(
            "2026-11-14 21:42:30.672 WARN [ShizukuBackend] Initial upstream attach timed out",
        )
        org.junit.Assert.assertNotNull(parsed)
        assertEquals("2026-11-14 21:42:30.672", parsed!!.fullTimestamp)
        assertEquals("21:42:30.672", parsed.timeOnly)
        assertEquals("WARN", parsed.level)
        assertEquals("ShizukuBackend", parsed.tag)
        assertEquals("Initial upstream attach timed out", parsed.message)

        org.junit.Assert.assertNull(HotspotShareLogger.parseLogLine("not a structured log line"))
        org.junit.Assert.assertNull(HotspotShareLogger.parseLogLine("   "))
    }

    @Test
    fun formatReadableLogs_formatsEntriesAndPreservesRawFallbackLines() {
        assertEquals("", HotspotShareLogger.formatReadableLogs(""))
        assertEquals("", HotspotShareLogger.formatReadableLogs("   \n  "))

        val raw = listOf(
            "2026-11-14 21:42:30.672 INFO [ShizukuBackend] Shizuku hotspot share ACTIVE on testtun15",
            "continuation line without timestamp",
        ).joinToString("\n")

        val formatted = HotspotShareLogger.formatReadableLogs(raw)
        assertTrue(formatted.contains("21:42:30.672"))
        assertTrue(formatted.contains("INFO"))
        assertTrue(formatted.contains("[ShizukuBackend]"))
        assertTrue(formatted.contains("Shizuku hotspot share ACTIVE on testtun15"))
        assertTrue(formatted.contains("continuation line without timestamp"))
    }

    @Test
    fun classifyTagSource_mapsShizukuRootAndProxyTags() {
        assertEquals(HotspotShareLogger.LogSource.SHIZUKU, HotspotShareLogger.classifyTagSource("ShizukuBackend"))
        assertEquals(HotspotShareLogger.LogSource.SHIZUKU, HotspotShareLogger.classifyTagSource("ShareUserService"))
        assertEquals(HotspotShareLogger.LogSource.SHIZUKU, HotspotShareLogger.classifyTagSource("TetherWatchService"))
        assertEquals(HotspotShareLogger.LogSource.ROOT, HotspotShareLogger.classifyTagSource("RootBackend"))
        assertEquals(HotspotShareLogger.LogSource.PROXY, HotspotShareLogger.classifyTagSource("LanProxy"))
        assertEquals(HotspotShareLogger.LogSource.PROXY, HotspotShareLogger.classifyTagSource("AutoGateway"))
    }

    @Test
    fun filterAndSortLogs_filtersBySourceLevelQueryAndSortsOrder() {
        val sample = listOf(
            "2026-11-14 21:00:01.000 INFO [ShizukuBackend] starting shizuku",
            "2026-11-14 21:00:02.000 WARN [RootBackend] retrying iptables",
            "2026-11-14 21:00:03.000 ERROR [LanProxy] bind failed on port 2080",
            "2026-11-14 21:00:04.000 INFO [AutoGateway] Gateway verified 192.168.43.1:2080",
        ).joinToString("\n")

        val newestFirst = HotspotShareLogger.filterAndSortLogs(sample, newestFirst = true).lines()
        assertEquals(4, newestFirst.size)
        assertTrue(newestFirst[0].contains("[AutoGateway]"))
        assertTrue(newestFirst[3].contains("[ShizukuBackend]"))

        val oldestProxy = HotspotShareLogger.filterAndSortLogs(
            sample,
            source = HotspotShareLogger.LogSource.PROXY,
            newestFirst = false,
        ).lines()
        assertEquals(2, oldestProxy.size)
        assertTrue(oldestProxy[0].contains("[LanProxy]"))
        assertTrue(oldestProxy[1].contains("[AutoGateway]"))

        val warnPlus = HotspotShareLogger.filterAndSortLogs(
            sample,
            levelFilter = HotspotShareLogger.LogLevelFilter.WARN_PLUS,
            newestFirst = true,
        ).lines()
        assertEquals(2, warnPlus.size)
        assertTrue(warnPlus[0].contains("ERROR [LanProxy]"))
        assertTrue(warnPlus[1].contains("WARN [RootBackend]"))

        val errorOnly = HotspotShareLogger.filterAndSortLogs(
            sample,
            levelFilter = HotspotShareLogger.LogLevelFilter.ERROR_ONLY,
            searchQuery = "2080",
            newestFirst = true,
        ).lines()
        assertEquals(1, errorOnly.size)
        assertTrue(errorOnly[0].contains("bind failed on port 2080"))
    }

    @Test
    fun isolatedServiceProcess_controlsLoggingWithoutDataStoreOrShellUid() {
        HotspotShareLogger.markIsolatedServiceProcess()
        HotspotShareLogger.setLoggingEnabled(true)
        assertTrue(HotspotShareLogger.isLoggingEnabled())
        HotspotShareLogger.info("ShareUserService", "isolated daemon alive under uid 0 or 2000")
        assertTrue(HotspotShareLogger.getInMemoryLines().any { it.contains("isolated daemon alive") })

        HotspotShareLogger.setLoggingEnabled(false)
        assertFalse(HotspotShareLogger.isLoggingEnabled())
        assertFalse(disabledFlagFile.exists().not())
    }

    @Test
    fun mirrorShellServiceLogs_deduplicatesAndPersistsToMirrorFile() {
        val mirrorFile = File(tempDir, "shell_mirror.log")
        HotspotShareLogger.mirrorShellServiceLogs(
            listOf(
                "2026-11-15 00:10:00.000 INFO [ShareUserService] first daemon event",
                "2026-11-15 00:10:02.000 WARN [ShareUserService] third daemon event",
            ),
            overrideMirrorFileForTest = mirrorFile,
        )
        HotspotShareLogger.mirrorShellServiceLogs(
            listOf(
                "2026-11-15 00:10:01.000 INFO [ShareUserService] second daemon event",
                "2026-11-15 00:10:00.000 INFO [ShareUserService] first daemon event",
            ),
            overrideMirrorFileForTest = mirrorFile,
        )
        val lines = mirrorFile.readLines().filter { it.isNotBlank() }
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("first daemon event"))
        assertTrue(lines[1].contains("second daemon event"))
        assertTrue(lines[2].contains("third daemon event"))
    }

    @Test
    fun countEntries_andExtendedClassification_handleHevTunnelAndAutoConnect() {
        assertEquals(HotspotShareLogger.LogSource.SHIZUKU, HotspotShareLogger.classifyTagSource("HevTunnel"))
        assertEquals(HotspotShareLogger.LogSource.PROXY, HotspotShareLogger.classifyTagSource("AutoConnect"))

        val logs = listOf(
            "2026-11-15 00:10:00.000 INFO [HevTunnel] HevTunnel started (socksPort=2088, mtu=1500)",
            "continuation line",
            "2026-11-15 00:10:01.000 INFO [AutoConnect] gateway ready",
        ).joinToString("\n")
        assertEquals(2, HotspotShareLogger.countEntries(logs))
    }
}
