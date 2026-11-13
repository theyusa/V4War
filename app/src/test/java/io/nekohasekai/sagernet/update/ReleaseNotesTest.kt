package io.nekohasekai.sagernet.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    private val changelog = listOf(
        "Preamble that belongs to no version.",
        "",
        "#### 2.1.0-pre.3",
        "",
        "* Pre 3",
        "",
        "#### 2.1.0-pre.2",
        "",
        "* Pre 2",
        "",
        "#### 2.1.0-pre.1",
        "",
        "* Pre 1",
        "",
        "#### 2.0.5",
        "",
        "* Five **1**",
        "",
        "**1**:",
        "Footnote.",
        "",
        "#### 2.0.5-pre.1",
        "",
        "* Five pre",
        "",
        "#### 2.0.4",
        "",
        "",
        "#### 2.0.3",
        "",
        "* Three",
        "",
    ).joinToString("\r\n")

    @Test
    fun stableTargetSkipsPreReleasesAndEmptySections() {
        assertEquals(
            "#### 2.0.5\n\n* Five **1**\n\n**1**:\nFootnote.",
            ReleaseNotes.select(changelog, "2.0.3", "v2.0.5"),
        )
    }

    @Test
    fun preReleaseTargetIncludesSectionsUpToIt() {
        assertEquals(
            "#### 2.1.0-pre.2\n\n* Pre 2\n\n#### 2.1.0-pre.1\n\n* Pre 1",
            ReleaseNotes.select(changelog, "2.0.5", "v2.1.0-pre.2"),
        )
    }

    @Test
    fun previewBuildSeesItsOwnRelease() {
        assertEquals(
            "#### 2.0.5\n\n* Five **1**\n\n**1**:\nFootnote.",
            ReleaseNotes.select(changelog, "2.0.5-pre.12", "v2.0.5"),
        )
    }

    @Test
    fun unparsableVersionsGiveNothing() {
        assertEquals("", ReleaseNotes.select(changelog, "", "v2.0.5"))
        assertEquals("", ReleaseNotes.select(changelog, "2.0.3", "latest"))
        assertEquals("", ReleaseNotes.select(changelog, "2.0.5", "v2.0.5"))
    }

    @Test
    fun stripsHtmlAndParsesTablesRulesQuotesAndImages() {
        val md = """
            ---

            <div align="center">

            ![Release Downloads](https://github.com/theyusa/V4War/releases/download/2.1-beta)

            </div>

            ### ⬇️ Download

            | Architecture | Download |
            |---|---|
            | ARM64 — modern phones | ![arm64-v8a](https://github.com/theyusa/V4War/releases/download/2.1-beta/V4War-2.0.20-arm64-v8a.apk) |
            | ARMv7 — 32-bit devices | [![armeabi-v7a](https://img.shields.io/badge/apk-v7-blue)](https://github.com/theyusa/V4War/releases/download/2.1-beta/V4War-2.0.20-armeabi-v7a.apk) |

            > 🔒 Verify integrity with [SHA256SUMS](https://github.com/theyusa/V4War/releases/download/2.1-beta/SHA256SUMS).
        """.trimIndent()

        val blocks = ReleaseNotes.preprocessBlocks(md)
        assertTrue(blocks.any { it is ReleaseNotes.Block.Divider })
        assertTrue(blocks.none { it.toString().contains("<div") })
        assertTrue(blocks.none { it.toString().contains("|---") })

        val heading = blocks.filterIsInstance<ReleaseNotes.Block.Heading>().firstOrNull()
        assertEquals(3, heading?.level)
        assertEquals("⬇️ Download", heading?.text)

        val bullets = blocks.filterIsInstance<ReleaseNotes.Block.Bullet>()
        assertEquals(2, bullets.size)
        val firstTokens = ReleaseNotes.tokenizeInline(bullets[0].text)
        assertTrue(firstTokens.any { it == ReleaseNotes.InlineToken.Bold("ARM64 — modern phones") })
        assertTrue(
            firstTokens.any {
                it == ReleaseNotes.InlineToken.Link(
                    "arm64-v8a",
                    "https://github.com/theyusa/V4War/releases/download/2.1-beta/V4War-2.0.20-arm64-v8a.apk",
                )
            },
        )

        val secondTokens = ReleaseNotes.tokenizeInline(bullets[1].text)
        assertTrue(
            secondTokens.any {
                it == ReleaseNotes.InlineToken.Link(
                    "armeabi-v7a",
                    "https://github.com/theyusa/V4War/releases/download/2.1-beta/V4War-2.0.20-armeabi-v7a.apk",
                )
            },
        )

        val quote = blocks.filterIsInstance<ReleaseNotes.Block.Quote>().single()
        val quoteTokens = ReleaseNotes.tokenizeInline(quote.text)
        assertTrue(
            quoteTokens.any {
                it == ReleaseNotes.InlineToken.Link(
                    "SHA256SUMS",
                    "https://github.com/theyusa/V4War/releases/download/2.1-beta/SHA256SUMS",
                )
            },
        )
    }
}
