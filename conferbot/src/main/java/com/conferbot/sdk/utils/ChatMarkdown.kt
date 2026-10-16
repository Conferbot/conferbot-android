package com.conferbot.sdk.utils

/**
 * Agent message text for a native bubble. AI replies arrive as markdown
 * wrapped in <p> (embed-server aiAgentHandlerService), a person's as the
 * inbox editor's HTML. Both become one markdown subset, parsed into blocks
 * of inline runs. What the web's renderer (conferbot-widget
 * utils/agentHtml.ts) draws, ported from the React Native SDK's
 * src/utils/chatMarkdown.ts:
 *
 *   blocks   paragraphs, "- " / "1." lists, # headings (levels 3-6; # and
 *            ## are drawn as 4, as the web folds h1/h2 into h4), | tables |
 *            with a |---| rule, and from a person's HTML: <blockquote>
 *            ("> " lines here) and <pre> (``` fences here)
 *   inline   **bold**, _italic_, `code`, ~~strikethrough~~ (<s>, <del>),
 *            [links](https://...) and bare links
 *
 * Never HTML on screen: only http(s), mailto and tel links survive. Pure
 * (no Android types). No literal "}" in a pattern: Android's ICU regex
 * refuses it (#70).
 */
object ChatMarkdown {

    sealed class Inline {
        abstract val text: String
        data class Plain(override val text: String) : Inline()
        data class Bold(override val text: String) : Inline()
        data class Italic(override val text: String) : Inline()
        data class Code(override val text: String) : Inline()
        data class Link(override val text: String, val url: String) : Inline()
        data class Strike(override val text: String) : Inline()
    }

    sealed class Block {
        abstract val inlines: List<Inline>
        data class Paragraph(override val inlines: List<Inline>) : Block()
        data class Bullet(override val inlines: List<Inline>) : Block()
        data class Numbered(val n: Int, override val inlines: List<Inline>) : Block()
        /** Levels 3-6 (# and ## arrive as 4). */
        data class Heading(val level: Int, override val inlines: List<Inline>) : Block()
        data class Quote(override val inlines: List<Inline>) : Block()
        /** Preformatted lines, nothing formatted inside. */
        data class Pre(val text: String) : Block() {
            override val inlines: List<Inline> get() = listOf(Inline.Plain(text))
        }
        /** A table: the head's cells, and each row filled to the head's width. */
        data class Table(val head: List<List<Inline>>, val rows: List<List<List<Inline>>>) : Block() {
            override val inlines: List<Inline> get() = emptyList()
        }
    }

    private val ENTITIES = mapOf("&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&#x27;" to "'")

    private fun decode(s: String): String =
        Regex("&(nbsp|amp|lt|gt|quot|#39|#x27);").replace(s) { ENTITIES[it.value] ?: it.value }

    fun safeLinkUrl(url: String): String? {
        val u = url.trim()
        return if (Regex("^(https?://|mailto:|tel:)", RegexOption.IGNORE_CASE).containsMatchIn(u)) u else null
    }

    private val OPTS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("<[^>]+>")
    private val BR = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

    // A cell's text: no tags, no line breaks, and its own "|" kept as text.
    private fun cellText(html: String): String =
        html.replace(BR, " ").replace(TAG, "").replace(Regex("\\s+"), " ").trim().replace("|", "\\|")

    /** An HTML table as a markdown one: the first row is the head. */
    private fun tableToMarkdown(inner: String): String {
        val rows = Regex("<tr[^>]*>(.*?)</tr>", OPTS).findAll(inner).map { tr ->
            Regex("<t[hd][^>]*>(.*?)</t[hd]>", OPTS).findAll(tr.groupValues[1]).map { cellText(it.groupValues[1]) }.toList()
        }.filter { it.isNotEmpty() }.toList()
        if (rows.isEmpty()) return ""
        fun line(cells: List<String>) = "| ${cells.joinToString(" | ")} |"
        val lines = listOf(line(rows[0]), line(rows[0].map { "---" })) + rows.drop(1).map(::line)
        return "\n\n${lines.joinToString("\n")}\n\n"
    }

    /** The message as markdown: the inbox's HTML tags turned into their markdown. */
    fun htmlToMarkdown(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        val opts = OPTS
        var s = html.replace(Regex("\r\n?"), "\n")
        // Preformatted text keeps its lines and nothing inside is formatted.
        s = Regex("<pre[^>]*>(.*?)</pre>", opts).replace(s) { m ->
            val text = m.groupValues[1].replace(BR, "\n").replace(TAG, "").trim('\n')
            "\n\n```\n$text\n```\n\n"
        }
        s = Regex("<a\\s[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", opts).replace(s) { m ->
            val url = m.groupValues[1]
            val label = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            if (safeLinkUrl(url) != null) "[${label.ifEmpty { url }}]($url)" else label
        }
        s = Regex("<(strong|b)>(.*?)</\\1>", opts).replace(s, "**$2**")
        s = Regex("<(em|i)>(.*?)</\\1>", opts).replace(s, "_$2_")
        s = Regex("<code>(.*?)</code>", opts).replace(s, "`$1`")
        s = Regex("<(s|del|strike)>(.*?)</\\1>", opts).replace(s, "~~$2~~")
        // h1 / h2 are drawn as h4, as the web's sanitizer folds them.
        s = Regex("<h([1-6])[^>]*>(.*?)</h\\1>", opts).replace(s) { m ->
            val n = m.groupValues[1].toInt()
            val level = if (n <= 2) 4 else n
            "\n\n${"#".repeat(level)} ${m.groupValues[2].replace(TAG, "").replace(Regex("\\s+"), " ").trim()}\n\n"
        }
        s = Regex("<table[^>]*>(.*?)</table>", opts).replace(s) { tableToMarkdown(it.groupValues[1]) }
        s = Regex("<blockquote[^>]*>(.*?)</blockquote>", opts).replace(s) { m ->
            val lines = m.groupValues[1]
                .replace(BR, "\n")
                .replace(Regex("</p>\\s*<p[^>]*>", RegexOption.IGNORE_CASE), "\n")
                .replace(TAG, "")
                .split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            "\n\n${lines.joinToString("\n") { "> $it" }}\n\n"
        }
        s = Regex("<li[^>]*>", RegexOption.IGNORE_CASE).replace(s, "\n- ")
        s = Regex("</(li|ul|ol)>", RegexOption.IGNORE_CASE).replace(s, "\n")
        s = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE).replace(s, "\n")
        s = Regex("</p>\\s*<p[^>]*>", RegexOption.IGNORE_CASE).replace(s, "\n\n")
        s = Regex("</?(p|div|ul|ol)[^>]*>", RegexOption.IGNORE_CASE).replace(s, "\n")
        s = Regex("<[^>]+>").replace(s, "")
        s = decode(s)
        s = Regex("[ \\t]+\n").replace(s, "\n")
        s = Regex("\n{3,}").replace(s, "\n\n")
        return s.trim()
    }

    private val INLINE = Regex(
        "\\*\\*([^*]+)\\*\\*|__([^_]+)__|`([^`]+)`|\\[([^\\]]+)\\]\\(([^)\\s]+)\\)|(^|[^\\w*])[*_]([^*_\\n]+)[*_](?![\\w*])|(https?://[^\\s<>()]+[^\\s<>().,;:!?'\"])|~~([^~\\n]+)~~"
    )

    /** One line's inline runs. */
    fun parseInline(line: String): List<Inline> {
        val out = mutableListOf<Inline>()
        var last = 0
        fun push(text: String) {
            if (text.isEmpty()) return
            val prev = out.lastOrNull()
            if (prev is Inline.Plain) out[out.size - 1] = Inline.Plain(prev.text + text) else out += Inline.Plain(text)
        }
        for (m in INLINE.findAll(line)) {
            push(line.substring(last, m.range.first))
            last = m.range.last + 1
            val g = m.groupValues
            when {
                g[1].isNotEmpty() || g[2].isNotEmpty() -> out += Inline.Bold(g[1].ifEmpty { g[2] })
                g[3].isNotEmpty() -> out += Inline.Code(g[3])
                g[4].isNotEmpty() -> {
                    val url = safeLinkUrl(g[5])
                    if (url != null) out += Inline.Link(g[4], url) else push(g[4])
                }
                g[7].isNotEmpty() -> {
                    push(g[6])
                    out += Inline.Italic(g[7])
                }
                g[8].isNotEmpty() -> out += Inline.Link(g[8], g[8])
                g[9].isNotEmpty() -> out += Inline.Strike(g[9])
            }
        }
        push(line.substring(last))
        return out
    }

    // A markdown table: a header row, a |---| rule, then rows (web agentHtml.ts).
    private val TABLE_ROW = Regex("^\\s*\\|.*\\|\\s*$")
    private val TABLE_RULE = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")
    private val HEADING = Regex("^#{1,6}\\s+(.*?)\\s*#*\\s*$")
    private val QUOTED = Regex("^>\\s?(.*)$")
    private val BULLET = Regex("^[-*+•]\\s+(.*)$")
    private val NUMBERED = Regex("^(\\d{1,3})[.)]\\s+(.*)$")
    private val FENCE = Regex("^```")
    private val FENCE_END = Regex("^```\\s*$")

    /** A row's cells; a "\\|" inside a cell is its own text. */
    fun tableCells(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        val s = line.trim()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length && s[i + 1] == '|') {
                cell.append('|')
                i++
            } else if (c == '|') {
                cells += cell.toString()
                cell.setLength(0)
            } else cell.append(c)
            i++
        }
        cells += cell.toString()
        // The outer pipes open and close the row.
        if (s.startsWith("|")) cells.removeAt(0)
        if (s.endsWith("|") && !s.endsWith("\\|") && cells.isNotEmpty()) cells.removeAt(cells.size - 1)
        return cells.map { it.trim() }
    }

    /** The text as blocks: list items, headings, tables, quotes, code and paragraphs (a single newline is a line break). */
    fun parse(markdown: String?): List<Block> {
        val blocks = mutableListOf<Block>()
        val para = mutableListOf<String>()
        val quote = mutableListOf<String>()
        fun flushPara() {
            if (para.isNotEmpty()) blocks += Block.Paragraph(parseInline(para.joinToString("\n")))
            para.clear()
        }
        fun flush() {
            flushPara()
            if (quote.isNotEmpty()) blocks += Block.Quote(parseInline(quote.joinToString("\n")))
            quote.clear()
        }
        val lines = (markdown ?: "").split("\n")
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trim()
            // ``` fences: the lines as they are, nothing formatted.
            if (FENCE.containsMatchIn(line)) {
                flush()
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !FENCE_END.matches(lines[i].trim())) body += lines[i++]
                blocks += Block.Pre(body.joinToString("\n"))
                i++
                continue
            }
            // A table: this row, a rule under it, and every row after.
            val next = lines.getOrNull(i + 1).orEmpty()
            if (TABLE_ROW.matches(raw) && TABLE_RULE.matches(next) && next.contains('-')) {
                flush()
                val head = tableCells(raw)
                i += 2
                val rows = mutableListOf<List<List<Inline>>>()
                while (i < lines.size && TABLE_ROW.matches(lines[i])) {
                    val cells = tableCells(lines[i++])
                    rows += head.indices.map { k -> parseInline(cells.getOrElse(k) { "" }) }
                }
                blocks += Block.Table(head.map { parseInline(it) }, rows)
                continue
            }
            val quoted = QUOTED.find(line)
            if (quoted != null) {
                flushPara()
                quote += quoted.groupValues[1]
                i++
                continue
            }
            if (quote.isNotEmpty()) flush()
            val bullet = BULLET.find(line)
            val numbered = NUMBERED.find(line)
            val heading = HEADING.find(line)
            when {
                bullet != null -> { flush(); blocks += Block.Bullet(parseInline(bullet.groupValues[1])) }
                numbered != null -> { flush(); blocks += Block.Numbered(numbered.groupValues[1].toInt(), parseInline(numbered.groupValues[2])) }
                heading != null -> {
                    flush()
                    val hashes = line.takeWhile { it == '#' }.length
                    blocks += Block.Heading(if (hashes <= 2) 4 else hashes, parseInline(heading.groupValues[1]))
                }
                line.isEmpty() -> flush()
                else -> para += line
            }
            i++
        }
        flush()
        return blocks
    }

    /** Plain text of the blocks (accessibility, transcript, notifications). */
    fun plainText(blocks: List<Block>): String = blocks.joinToString("\n") { b ->
        fun text(inlines: List<Inline>) = inlines.joinToString("") { it.text }
        when (b) {
            is Block.Bullet -> "• ${text(b.inlines)}"
            is Block.Numbered -> "${b.n}. ${text(b.inlines)}"
            is Block.Pre -> b.text
            is Block.Table -> (listOf(b.head) + b.rows).joinToString("\n") { r -> r.joinToString(" | ") { text(it) } }
            else -> text(b.inlines)
        }
    }
}
