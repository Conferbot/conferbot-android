package com.conferbot.sdk.utils

/**
 * Agent message text for a native bubble. AI replies arrive as markdown
 * wrapped in <p> (embed-server aiAgentHandlerService), a person's as the
 * inbox editor's HTML. Both become one small markdown subset - paragraphs,
 * "- " / "1." lists, **bold**, _italic_, `code`, [links](https://...) and
 * bare links - parsed into blocks of inline runs. Never HTML on screen:
 * only http(s), mailto and tel links survive. Pure (no Android types).
 */
object ChatMarkdown {

    sealed class Inline {
        abstract val text: String
        data class Plain(override val text: String) : Inline()
        data class Bold(override val text: String) : Inline()
        data class Italic(override val text: String) : Inline()
        data class Code(override val text: String) : Inline()
        data class Link(override val text: String, val url: String) : Inline()
    }

    sealed class Block {
        abstract val inlines: List<Inline>
        data class Paragraph(override val inlines: List<Inline>) : Block()
        data class Bullet(override val inlines: List<Inline>) : Block()
        data class Numbered(val n: Int, override val inlines: List<Inline>) : Block()
    }

    private val ENTITIES = mapOf("&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&#x27;" to "'")

    private fun decode(s: String): String =
        Regex("&(nbsp|amp|lt|gt|quot|#39|#x27);").replace(s) { ENTITIES[it.value] ?: it.value }

    fun safeLinkUrl(url: String): String? {
        val u = url.trim()
        return if (Regex("^(https?://|mailto:|tel:)", RegexOption.IGNORE_CASE).containsMatchIn(u)) u else null
    }

    /** The message as markdown: the inbox's HTML tags turned into their markdown. */
    fun htmlToMarkdown(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        var s = html.replace(Regex("\r\n?"), "\n")
        s = Regex("<a\\s[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", opts).replace(s) { m ->
            val url = m.groupValues[1]
            val label = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            if (safeLinkUrl(url) != null) "[${label.ifEmpty { url }}]($url)" else label
        }
        s = Regex("<(strong|b)>(.*?)</\\1>", opts).replace(s, "**$2**")
        s = Regex("<(em|i)>(.*?)</\\1>", opts).replace(s, "_$2_")
        s = Regex("<code>(.*?)</code>", opts).replace(s, "`$1`")
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
        "\\*\\*([^*]+)\\*\\*|__([^_]+)__|`([^`]+)`|\\[([^\\]]+)\\]\\(([^)\\s]+)\\)|(^|[^\\w*])[*_]([^*_\\n]+)[*_](?![\\w*])|(https?://[^\\s<>()]+[^\\s<>().,;:!?'\"])"
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
            }
        }
        push(line.substring(last))
        return out
    }

    /** The text as blocks: list items, and paragraphs (a single newline is a line break). */
    fun parse(markdown: String?): List<Block> {
        val blocks = mutableListOf<Block>()
        val para = mutableListOf<String>()
        fun flush() {
            if (para.isNotEmpty()) blocks += Block.Paragraph(parseInline(para.joinToString("\n")))
            para.clear()
        }
        for (raw in (markdown ?: "").split("\n")) {
            val line = raw.trim()
            val bullet = Regex("^[-*•]\\s+(.*)$").find(line)
            val numbered = Regex("^(\\d{1,3})[.)]\\s+(.*)$").find(line)
            when {
                bullet != null -> { flush(); blocks += Block.Bullet(parseInline(bullet.groupValues[1])) }
                numbered != null -> { flush(); blocks += Block.Numbered(numbered.groupValues[1].toInt(), parseInline(numbered.groupValues[2])) }
                line.isEmpty() -> flush()
                else -> para += line.replace(Regex("^#{1,6}\\s+"), "")
            }
        }
        flush()
        return blocks
    }

    /** Plain text of the blocks (accessibility, transcript, notifications). */
    fun plainText(blocks: List<Block>): String = blocks.joinToString("\n") { b ->
        val text = b.inlines.joinToString("") { it.text }
        when (b) {
            is Block.Bullet -> "• $text"
            is Block.Numbered -> "${b.n}. $text"
            is Block.Paragraph -> text
        }
    }
}
