package com.conferbot.sdk.core.helpcentre

/**
 * A help centre article's body, HTML from `get-kb-article` (as the web widget
 * renders it), read into blocks the app draws without a WebView (#57; Flutter
 * draws it with flutter_html, RN with its own tag pass). Pure Kotlin, no
 * android.text.Html, so the tests run it on the JVM.
 *
 * Headings, paragraphs, line breaks, bold / italic / underline / struck /
 * inline code, links, bulleted and numbered lists (nested), quotes, code
 * blocks, images, simple tables and rules. Scripts, styles and comments are
 * dropped; any other tag keeps its text.
 */
object ArticleHtml {

    /** A stretch of text in one style. [href] is a link's target, already checked by [safeLink]. */
    data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val code: Boolean = false,
        val href: String? = null
    ) {
        internal fun sameStyle(o: Run) =
            bold == o.bold && italic == o.italic && underline == o.underline && strike == o.strike && code == o.code && href == o.href
    }

    sealed class Block {
        data class Heading(val level: Int, val runs: List<Run>) : Block()
        data class Paragraph(val runs: List<Run>) : Block()

        /**
         * A list item's text. [number] is its number in a numbered list (0 for
         * a bullet); [continued] is a later paragraph of the same item (no
         * bullet or number of its own). [depth] 0 is the outermost list.
         */
        data class ListItem(
            val ordered: Boolean,
            val number: Int,
            val depth: Int,
            val runs: List<Run>,
            val continued: Boolean = false
        ) : Block()

        data class Quote(val runs: List<Run>) : Block()
        data class Code(val text: String) : Block()
        data class Image(val src: String, val alt: String?) : Block()

        /** Rows of cells; [header] when the first row is header cells (th). */
        data class Table(val rows: List<List<List<Run>>>, val header: Boolean) : Block()
        object Rule : Block()
    }

    /** True when the text has tags (a plain-text body is drawn as text). */
    fun isHtml(content: String): Boolean = TAG_LIKE.containsMatchIn(content)

    /**
     * A link the app may open outside itself: http(s), mailto and tel only.
     * Anything else (a relative path, javascript:) stays plain text.
     */
    fun safeLink(href: String?): String? {
        val h = href?.trim().orEmpty()
        if (h.isEmpty()) return null
        val scheme = h.substringBefore(':', "").lowercase()
        return if (scheme in LINK_SCHEMES && h.length > scheme.length + 1) h else null
    }

    /** An image the app loads: http(s) only. */
    fun safeImage(src: String?): String? {
        val s = src?.trim().orEmpty()
        val scheme = s.substringBefore(':', "").lowercase()
        return if (scheme == "http" || scheme == "https") s else null
    }

    /** The article's blocks; a plain-text body is its paragraphs, line breaks kept. */
    fun parse(content: String): List<Block> {
        if (content.isBlank()) return emptyList()
        if (!isHtml(content)) {
            return content.replace("\r\n", "\n").split(BLANK_LINES)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { Block.Paragraph(listOf(Run(decodeEntities(it)))) }
        }
        return Parser(content).run()
    }

    /** Named (the common ones) and numeric character references. */
    fun decodeEntities(text: String): String {
        if (text.indexOf('&') < 0) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '&') {
                val semi = text.indexOf(';', i + 1)
                if (semi > i + 1 && semi - i <= 12) {
                    val name = text.substring(i + 1, semi)
                    val code: Int? = when {
                        name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)
                        name.startsWith("#") -> name.substring(1).toIntOrNull()
                        else -> null
                    }
                    if (code != null && code in 1..0x10FFFF) {
                        out.appendCodePoint(code)
                        i = semi + 1
                        continue
                    }
                    val named = ENTITIES[name]
                    if (named != null) {
                        out.append(named)
                        i = semi + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    // ---------------------------------------------------------------------

    private val TAG_LIKE = Regex("<[a-zA-Z][^>]*>")
    private val BLANK_LINES = Regex("\\n[ \\t]*\\n")
    private val ATTR = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+)))?")
    private val LINK_SCHEMES = setOf("http", "https", "mailto", "tel")
    private val SKIPPED = setOf("script", "style", "head", "title", "noscript", "template", "svg", "iframe", "object")
    private val BLOCKS = setOf(
        "p", "div", "section", "article", "header", "footer", "main", "aside", "nav",
        "figure", "figcaption", "address", "details", "summary", "center", "dl", "dt", "dd"
    )
    private val VOID = setOf("br", "img", "hr", "input", "meta", "link", "source", "wbr", "col", "area", "base", "embed", "track")

    private val ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0",
        "ndash" to "\u2013", "mdash" to "\u2014", "hellip" to "\u2026", "lsquo" to "\u2018", "rsquo" to "\u2019",
        "ldquo" to "\u201C", "rdquo" to "\u201D", "laquo" to "\u00AB", "raquo" to "\u00BB", "bull" to "\u2022",
        "middot" to "\u00B7", "copy" to "\u00A9", "reg" to "\u00AE", "trade" to "\u2122", "euro" to "\u20AC",
        "pound" to "\u00A3", "yen" to "\u00A5", "cent" to "\u00A2", "deg" to "\u00B0", "times" to "\u00D7",
        "divide" to "\u00F7", "rarr" to "\u2192", "larr" to "\u2190", "uarr" to "\u2191", "darr" to "\u2193",
        "check" to "\u2713", "sect" to "\u00A7", "para" to "\u00B6", "iexcl" to "\u00A1", "iquest" to "\u00BF",
        "shy" to "", "zwj" to "\u200D", "zwnj" to "\u200C", "ensp" to "\u2002", "emsp" to "\u2003", "thinsp" to "\u2009"
    )

    private class ListCtx(val ordered: Boolean, var n: Int) {
        /** An item of this list is open (its later paragraphs continue it). */
        var open = false
    }

    private class TableCtx {
        val rows = mutableListOf<List<List<Run>>>()
        var row: MutableList<List<Run>>? = null
        var firstRowHeader = false
        var sawHeaderCell = false
        var inCell = false
    }

    private class Parser(private val html: String) {
        private val blocks = mutableListOf<Block>()
        private var runs = mutableListOf<Run>()

        private var bold = 0
        private var italic = 0
        private var underline = 0
        private var strike = 0
        private var code = 0
        private val links = ArrayDeque<String?>()

        private var heading = 0
        private var quote = 0
        private val lists = ArrayDeque<ListCtx>()
        private var liPending: Pair<ListCtx, Int>? = null
        private var table: TableCtx? = null
        private var pre: StringBuilder? = null

        fun run(): List<Block> {
            var i = 0
            val n = html.length
            while (i < n) {
                val lt = html.indexOf('<', i)
                if (lt < 0) {
                    text(html.substring(i))
                    break
                }
                if (lt > i) text(html.substring(i, lt))
                if (html.startsWith("<!--", lt)) {
                    val end = html.indexOf("-->", lt + 4)
                    i = if (end < 0) n else end + 3
                    continue
                }
                val gt = html.indexOf('>', lt + 1)
                if (gt < 0) {
                    text(html.substring(lt))
                    break
                }
                val raw = html.substring(lt + 1, gt)
                i = gt + 1
                if (raw.isEmpty() || raw[0] == '!' || raw[0] == '?') continue
                val closing = raw[0] == '/'
                val body = if (closing) raw.substring(1) else raw
                val name = body.takeWhile { it.isLetterOrDigit() }.lowercase()
                if (name.isEmpty()) {
                    text("<$raw>")
                    continue
                }
                if (!closing && name in SKIPPED) {
                    val end = html.indexOf("</$name", i, ignoreCase = true)
                    i = if (end < 0) n else (html.indexOf('>', end).takeIf { it >= 0 }?.plus(1) ?: n)
                    continue
                }
                if (closing) close(name) else open(name, attrs(body.substring(name.length)), body.trimEnd().endsWith("/"))
            }
            flush()
            closeTable()
            return blocks
        }

        private fun attrs(s: String): Map<String, String> {
            val out = HashMap<String, String>()
            ATTR.findAll(s).forEach { m ->
                val v = m.groups[2]?.value ?: m.groups[3]?.value ?: m.groups[4]?.value ?: ""
                out[m.groupValues[1].lowercase()] = decodeEntities(v)
            }
            return out
        }

        private fun open(name: String, a: Map<String, String>, selfClosing: Boolean) {
            pre?.let { p ->
                if (name == "br") p.append('\n')
                return
            }
            when (name) {
                "b", "strong" -> bold++
                "i", "em", "cite", "var", "dfn" -> italic++
                "u", "ins" -> underline++
                "s", "strike", "del" -> strike++
                "code", "kbd", "samp", "tt" -> code++
                "a" -> if (!selfClosing) links.addLast(safeLink(a["href"]))
                "br" -> runs.add(style("\n"))
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flush()
                    heading = name[1] - '0'
                }
                "blockquote" -> {
                    flush()
                    quote++
                }
                "ul", "ol" -> {
                    flush()
                    val start = if (name == "ol") (a["start"]?.trim()?.toIntOrNull() ?: 1) - 1 else 0
                    lists.addLast(ListCtx(name == "ol", start))
                }
                "li" -> {
                    flush()
                    val ctx = lists.lastOrNull() ?: ListCtx(false, 0).also { lists.addLast(it) }
                    ctx.n++
                    liPending = ctx to (lists.size - 1)
                    ctx.open = true
                }
                "pre" -> {
                    flush()
                    pre = StringBuilder()
                }
                "hr" -> {
                    flush()
                    if (table == null) blocks.add(Block.Rule)
                }
                "img" -> {
                    val src = safeImage(a["src"])
                    if (table != null) {
                        // A picture in a cell: its alt text keeps the cell's meaning.
                        a["alt"]?.takeIf { it.isNotBlank() }?.let { runs.add(style(it)) }
                    } else if (src != null) {
                        flush()
                        blocks.add(Block.Image(src, a["alt"]?.takeIf { it.isNotBlank() }))
                    }
                }
                "table" -> {
                    flush()
                    if (table == null) table = TableCtx()
                }
                "tr" -> table?.let { t ->
                    endCell()
                    endRow()
                    t.row = mutableListOf()
                    t.sawHeaderCell = false
                }
                "td", "th" -> table?.let { t ->
                    endCell()
                    if (t.row == null) t.row = mutableListOf()
                    if (name == "th" && t.rows.isEmpty()) t.sawHeaderCell = true
                    t.inCell = true
                }
                else -> if (name in BLOCKS) flush()
            }
            if (selfClosing && name !in VOID) close(name)
        }

        private fun close(name: String) {
            pre?.let { p ->
                if (name == "pre") {
                    val text = decodeEntities(p.toString()).trim('\n', '\r').trimEnd()
                    pre = null
                    if (text.isNotBlank()) blocks.add(Block.Code(text))
                }
                return
            }
            when (name) {
                "b", "strong" -> bold = (bold - 1).coerceAtLeast(0)
                "i", "em", "cite", "var", "dfn" -> italic = (italic - 1).coerceAtLeast(0)
                "u", "ins" -> underline = (underline - 1).coerceAtLeast(0)
                "s", "strike", "del" -> strike = (strike - 1).coerceAtLeast(0)
                "code", "kbd", "samp", "tt" -> code = (code - 1).coerceAtLeast(0)
                "a" -> links.removeLastOrNull()
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flush()
                    heading = 0
                }
                "blockquote" -> {
                    flush()
                    quote = (quote - 1).coerceAtLeast(0)
                }
                "ul", "ol" -> {
                    flush()
                    lists.removeLastOrNull()
                    liPending = null
                }
                "li" -> {
                    flush()
                    lists.lastOrNull()?.open = false
                    liPending = null
                }
                "td", "th" -> endCell()
                "tr" -> {
                    endCell()
                    endRow()
                }
                "table" -> closeTable()
                else -> if (name in BLOCKS) flush()
            }
        }

        private fun style(text: String) = Run(
            text = text,
            bold = bold > 0 || heading > 0,
            italic = italic > 0,
            underline = underline > 0,
            strike = strike > 0,
            code = code > 0,
            href = links.lastOrNull()
        )

        private fun text(raw: String) {
            pre?.let {
                it.append(raw)
                return
            }
            val t = table
            if (t != null && !t.inCell) return // whitespace between rows and cells
            val collapsed = decodeEntities(collapse(raw))
            if (collapsed.isEmpty()) return
            runs.add(style(collapsed))
        }

        private fun collapse(s: String): String {
            val sb = StringBuilder(s.length)
            var space = false
            for (c in s) {
                if (c == ' ' || c == '\n' || c == '\t' || c == '\r' || c == '\u000C') {
                    if (!space) sb.append(' ')
                    space = true
                } else {
                    sb.append(c)
                    space = false
                }
            }
            return sb.toString()
        }

        /** The runs so far as one block of the current kind. */
        private fun flush() {
            if (table?.inCell == true) {
                // A paragraph or list inside a cell: a line of the cell.
                if (runs.isNotEmpty() && runs.last().text != "\n") runs.add(style("\n"))
                return
            }
            val r = tidy(runs)
            runs = mutableListOf()
            if (r.isEmpty()) return
            val pending = liPending
            when {
                heading > 0 -> blocks.add(Block.Heading(heading, r))
                pending != null -> {
                    liPending = null
                    blocks.add(Block.ListItem(pending.first.ordered, if (pending.first.ordered) pending.first.n else 0, pending.second, r))
                }
                lists.lastOrNull()?.open == true -> {
                    val ctx = lists.last()
                    blocks.add(Block.ListItem(ctx.ordered, 0, lists.size - 1, r, continued = true))
                }
                quote > 0 -> blocks.add(Block.Quote(r))
                else -> blocks.add(Block.Paragraph(r))
            }
        }

        private fun endCell() {
            val t = table ?: return
            if (!t.inCell) return
            t.inCell = false
            val cell = tidy(runs)
            runs = mutableListOf()
            (t.row ?: mutableListOf<List<Run>>().also { t.row = it }).add(cell)
        }

        private fun endRow() {
            val t = table ?: return
            val row = t.row ?: return
            t.row = null
            if (row.isEmpty()) return
            if (t.rows.isEmpty()) t.firstRowHeader = t.sawHeaderCell
            t.rows.add(row)
        }

        private fun closeTable() {
            val t = table ?: return
            endCell()
            endRow()
            table = null
            if (t.rows.any { row -> row.any { it.isNotEmpty() } }) blocks.add(Block.Table(t.rows.toList(), t.firstRowHeader))
        }

        /** Merges same-styled neighbours and trims the block's ends (line breaks kept inside). */
        private fun tidy(input: List<Run>): List<Run> {
            val merged = mutableListOf<Run>()
            for (r in input) {
                if (r.text.isEmpty()) continue
                val last = merged.lastOrNull()
                // No double space across a style change.
                val text = if (last != null && last.text.endsWith(" ") && r.text.startsWith(" ")) r.text.substring(1) else r.text
                if (text.isEmpty()) continue
                if (last != null && last.sameStyle(r)) merged[merged.size - 1] = last.copy(text = last.text + text)
                else merged.add(r.copy(text = text))
            }
            // A space either side of a line break goes.
            for (k in merged.indices) merged[k] = merged[k].copy(text = merged[k].text.replace(" \n", "\n").replace("\n ", "\n"))
            while (merged.isNotEmpty()) {
                val first = merged.first()
                val t = first.text.trimStart(' ', '\n')
                if (t.isEmpty()) merged.removeAt(0) else {
                    merged[0] = first.copy(text = t)
                    break
                }
            }
            while (merged.isNotEmpty()) {
                val lastRun = merged.last()
                val t = lastRun.text.trimEnd(' ', '\n')
                if (t.isEmpty()) merged.removeAt(merged.size - 1) else {
                    merged[merged.size - 1] = lastRun.copy(text = t)
                    break
                }
            }
            return if (merged.all { it.text.isBlank() }) emptyList() else merged
        }
    }
}
