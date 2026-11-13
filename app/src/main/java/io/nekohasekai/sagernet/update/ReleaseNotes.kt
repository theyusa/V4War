package io.nekohasekai.sagernet.update

import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.launchCustomTab

/**
 * CHANGELOG.md (the desktop's format: `#### <version>` sections, newest first) as update release notes. CI publishes a
 * version's section as its release body (buildScript/release_notes.sh); the updater reads the file at the offered tag.
 */
object ReleaseNotes {

    private const val HEADING = "#### "

    internal sealed interface Block {
        data object Blank : Block
        data object Divider : Block
        data class Heading(val level: Int, val text: String) : Block
        data class Bullet(val text: String) : Block
        data class Quote(val text: String) : Block
        data class Paragraph(val text: String) : Block
    }

    internal sealed interface InlineToken {
        data class Plain(val text: String) : InlineToken
        data class Bold(val text: String) : InlineToken
        data class Italic(val text: String) : InlineToken
        data class Code(val text: String) : InlineToken
        data class Link(val label: String, val url: String) : InlineToken
    }

    /** Sections after [installed] up to [target]; a stable release's own section covers its pre-releases. */
    fun select(changelog: String, installed: String, target: String): String {
        val current = Semver.parse(installed) ?: return ""
        val offered = Semver.parse(target) ?: return ""
        val withPreReleases = offered.pre.isNotEmpty()

        val sections = ArrayList<String>()
        var heading: String? = null
        val body = ArrayList<String>()
        fun flush() {
            val version = heading?.let(Semver::parse) ?: return
            val text = body.joinToString("\n").trim()
            if (text.isEmpty() || Semver.compare(version, current) <= 0 || Semver.compare(version, offered) > 0) return
            if (version.pre.isNotEmpty() && !withPreReleases) return
            sections += "$HEADING$heading\n\n$text"
        }
        for (line in changelog.lineSequence().map { it.removeSuffix("\r") }) {
            if (line.startsWith(HEADING)) {
                flush()
                heading = line.substring(HEADING.length).trim()
                body.clear()
            } else {
                body += line
            }
        }
        flush()
        return sections.joinToString("\n\n")
    }

    private val htmlAnchor = Regex("""<a\s+[^>]*href=["'](https?://[^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val htmlBreak = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val htmlTag = Regex("""</?(?:div|p|center|span|details|summary|kbd|sub|sup|b|i|strong|em|small|img|hr|h[1-6]|ul|ol|li|table|thead|tbody|tr|th|td|a)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val horizontalRule = Regex("""^(?:[-*_]\s*){3,}$""")
    private val atxHeading = Regex("""^(#{1,6})\s+(.+)$""")
    private val tableSeparator = Regex("""^\|?[\s:\-]+\|[\s:\-|]*$""")
    private val blockquotePrefix = Regex("""^(?:>\s?)+""")
    private val inlineRegex = Regex(
        """\[!\[([^\]]*)]\((https?://[^)\s]+)\)]\((https?://[^)\s]+)\)""" + // linked badge: [![alt](img)](url)
            """|!\[([^\]]*)]\((https?://[^)\s]+)\)""" +                       // standalone image/badge link: ![alt](url)
            """|\[([^\]]+)]\((https?://[^)\s]+)\)""" +                        // markdown link: [label](url)
            """|<((?:https?://)[^>\s]+)>""" +                                 // autolink: <https://...>
            """|\*\*(.+?)\*\*""" +                                            // bold: **text**
            """|`([^`]+)`""" +                                                // inline code: `code`
            """|\*(?!\s)([^*\n]+?)(?<!\s)\*"""                                // italic: *text*
    )

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }

    private fun cleanHtml(rawMarkdown: String): String {
        val withLinks = htmlAnchor.replace(rawMarkdown) { match ->
            val url = match.groupValues[1].trim()
            val label = htmlTag.replace(match.groupValues[2], "").trim().ifEmpty { url }
            "[$label]($url)"
        }
        val withBreaks = htmlBreak.replace(withLinks, "\n")
        val stripped = htmlTag.replace(withBreaks, "")
        return decodeHtmlEntities(stripped)
    }

    private fun isPipeTableRow(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("|") && trimmed.indexOf('|', 1) > 0
    }

    private fun splitPipeCells(line: String): List<String> {
        return line.trim()
            .removePrefix("|")
            .removeSuffix("|")
            .split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    internal fun preprocessBlocks(markdown: String): List<Block> {
        val lines = cleanHtml(markdown).replace("\r\n", "\n").split('\n')
        val blocks = ArrayList<Block>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                if (blocks.isNotEmpty() && blocks.last() !is Block.Blank) {
                    blocks += Block.Blank
                }
                i++
                continue
            }

            if (isPipeTableRow(line)) {
                val tableLines = ArrayList<String>()
                while (i < lines.size && isPipeTableRow(lines[i])) {
                    tableLines += lines[i].trim()
                    i++
                }
                val hasSeparator = tableLines.size >= 2 && tableSeparator.matches(tableLines[1])
                val dataRows = tableLines.drop(if (hasSeparator) 2 else 0)
                    .filterNot { tableSeparator.matches(it) }
                for (row in dataRows) {
                    val cells = splitPipeCells(row)
                    if (cells.isEmpty()) continue
                    val formatted = if (cells.size >= 2) {
                        val rest = cells.drop(1).joinToString(" · ")
                        "**${cells[0]}**: $rest"
                    } else {
                        cells[0]
                    }
                    blocks += Block.Bullet(formatted)
                }
                continue
            }

            val headingMatch = atxHeading.matchEntire(line)
            when {
                horizontalRule.matches(line) -> {
                    blocks += Block.Divider
                }

                headingMatch != null -> {
                    val level = headingMatch.groupValues[1].length
                    val text = headingMatch.groupValues[2].trim()
                    if (text.isNotEmpty()) {
                        blocks += Block.Heading(level, text)
                    }
                }

                line.startsWith(">") -> {
                    val quoteText = blockquotePrefix.replaceFirst(line, "").trim()
                    if (quoteText.isNotEmpty()) {
                        blocks += Block.Quote(quoteText)
                    }
                }

                line.startsWith("* ") || line.startsWith("- ") || line.startsWith("• ") -> {
                    val bulletText = line.substring(2).trim()
                    if (bulletText.isNotEmpty()) {
                        blocks += Block.Bullet(bulletText)
                    }
                }

                else -> {
                    blocks += Block.Paragraph(line)
                }
            }
            i++
        }
        while (blocks.lastOrNull() is Block.Blank) {
            blocks.removeAt(blocks.lastIndex)
        }
        return blocks
    }

    private fun isPureImageAssetUrl(url: String): Boolean {
        val cleanUrl = url.substringBefore('?').substringBefore('#').lowercase()
        if ("shields.io" in cleanUrl) return true
        return cleanUrl.endsWith(".png") ||
            cleanUrl.endsWith(".jpg") ||
            cleanUrl.endsWith(".jpeg") ||
            cleanUrl.endsWith(".gif") ||
            cleanUrl.endsWith(".svg") ||
            cleanUrl.endsWith(".webp")
    }

    internal fun tokenizeInline(text: String): List<InlineToken> {
        val tokens = ArrayList<InlineToken>()
        var last = 0
        for (match in inlineRegex.findAll(text)) {
            if (match.range.first > last) {
                tokens += InlineToken.Plain(text.substring(last, match.range.first))
            }
            last = match.range.last + 1
            val groups = match.groupValues
            val badgeAlt = groups[1]
            val badgeUrl = groups[3]
            val imgAlt = groups[4]
            val imgUrl = groups[5]
            val linkLabel = groups[6]
            val linkUrl = groups[7]
            val autoUrl = groups[8]
            val bold = groups[9]
            val code = groups[10]
            val italic = groups[11]

            when {
                badgeUrl.isNotEmpty() -> {
                    val label = badgeAlt.trim().ifEmpty { "Download" }
                    tokens += InlineToken.Link(label, badgeUrl)
                }

                imgUrl.isNotEmpty() -> {
                    val label = imgAlt.trim()
                    if (!isPureImageAssetUrl(imgUrl)) {
                        tokens += InlineToken.Link(label.ifEmpty { imgUrl }, imgUrl)
                    } else if (label.isNotEmpty()) {
                        tokens += InlineToken.Plain(label)
                    }
                }

                linkUrl.isNotEmpty() -> {
                    tokens += InlineToken.Link(linkLabel, linkUrl)
                }

                autoUrl.isNotEmpty() -> {
                    tokens += InlineToken.Link(autoUrl, autoUrl)
                }

                bold.isNotEmpty() -> {
                    tokens += InlineToken.Bold(bold)
                }

                code.isNotEmpty() -> {
                    tokens += InlineToken.Code(code)
                }

                italic.isNotEmpty() -> {
                    tokens += InlineToken.Italic(italic)
                }
            }
        }
        if (last < text.length) {
            tokens += InlineToken.Plain(text.substring(last))
        }
        return tokens
    }

    /**
     * Renders GitHub Release Notes / CHANGELOG markdown (headings, lists, tables, blockquotes,
     * horizontal rules, inline emphasis/code/links/badges, HTML wrappers stripped) for [view].
     * Links use [ClickableSpan] without XML autoLink interference.
     */
    fun render(markdown: String, view: TextView): CharSequence {
        val density = view.resources.displayMetrics.density
        val accentColor = view.context.getColorAttr(R.attr.colorPrimary).takeIf { it != 0 }
            ?: view.currentTextColor
        val mutedColor = ColorUtils.setAlphaComponent(view.currentTextColor, 115)
        val out = SpannableStringBuilder()
        var gap = false

        for (block in preprocessBlocks(markdown)) {
            if (block is Block.Blank) {
                gap = out.isNotEmpty()
                continue
            }
            if (out.isNotEmpty()) out.append(if (gap) "\n\n" else "\n")
            gap = false
            val start = out.length
            when (block) {
                Block.Blank -> Unit

                Block.Divider -> {
                    out.append("────────────────────────")
                    out.setSpan(ForegroundColorSpan(mutedColor), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(RelativeSizeSpan(0.75f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                is Block.Heading -> {
                    appendInline(out, block.text)
                    val scale = when (block.level) {
                        1 -> 1.30f
                        2 -> 1.22f
                        3 -> 1.15f
                        else -> 1.10f
                    }
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(RelativeSizeSpan(scale), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                is Block.Bullet -> {
                    appendInline(out, block.text)
                    val bulletGap = (8 * density).toInt()
                    val bullet = if (Build.VERSION.SDK_INT >= 28) {
                        BulletSpan(bulletGap, view.currentTextColor, (2 * density).toInt())
                    } else {
                        BulletSpan(bulletGap)
                    }
                    out.setSpan(bullet, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                is Block.Quote -> {
                    appendInline(out, block.text)
                    val quoteSpan = if (Build.VERSION.SDK_INT >= 28) {
                        QuoteSpan(accentColor, (3 * density).toInt(), (10 * density).toInt())
                    } else {
                        QuoteSpan(accentColor)
                    }
                    out.setSpan(quoteSpan, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                is Block.Paragraph -> {
                    appendInline(out, block.text)
                }
            }
        }
        return out
    }

    private fun appendInline(out: SpannableStringBuilder, text: String) {
        for (token in tokenizeInline(text)) {
            val start = out.length
            when (token) {
                is InlineToken.Plain -> out.append(token.text)

                is InlineToken.Bold -> {
                    appendInline(out, token.text)
                    if (out.length > start) {
                        out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }

                is InlineToken.Italic -> {
                    appendInline(out, token.text)
                    if (out.length > start) {
                        out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }

                is InlineToken.Code -> {
                    out.append(token.text)
                    if (out.length > start) {
                        out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }

                is InlineToken.Link -> {
                    out.append(token.label)
                    if (out.length > start) {
                        out.setSpan(LinkSpan(token.url), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
    }

    private class LinkSpan(private val url: String) : ClickableSpan() {
        override fun onClick(widget: View) {
            widget.context.launchCustomTab(url)
        }
    }
}
