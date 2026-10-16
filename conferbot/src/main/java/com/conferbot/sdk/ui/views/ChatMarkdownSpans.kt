package com.conferbot.sdk.ui.views

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import com.conferbot.sdk.utils.ChatMarkdown

/**
 * An agent message's markdown (utils/ChatMarkdown.kt) as a TextView Spanned:
 * headings bold and larger, quotes with their bar, preformatted text in
 * monospace, and a table as its rows of cells split by " | " (the head bold),
 * as a TextView cannot scroll a table sideways.
 */
object ChatMarkdownSpans {
    private fun SpannableStringBuilder.span(what: Any, start: Int) {
        if (length > start) setSpan(what, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun SpannableStringBuilder.inlines(list: List<ChatMarkdown.Inline>) {
        list.forEach { inline ->
            val start = length
            append(inline.text)
            when (inline) {
                is ChatMarkdown.Inline.Bold -> span(StyleSpan(Typeface.BOLD), start)
                is ChatMarkdown.Inline.Italic -> span(StyleSpan(Typeface.ITALIC), start)
                is ChatMarkdown.Inline.Code -> span(TypefaceSpan("monospace"), start)
                is ChatMarkdown.Inline.Strike -> span(StrikethroughSpan(), start)
                is ChatMarkdown.Inline.Link -> span(URLSpan(inline.url), start)
                is ChatMarkdown.Inline.Plain -> {}
            }
        }
    }

    fun toSpanned(markdown: String?): Spanned {
        val out = SpannableStringBuilder()
        ChatMarkdown.parse(markdown).forEachIndexed { i, block ->
            if (i > 0) out.append("\n")
            val start = out.length
            when (block) {
                is ChatMarkdown.Block.Bullet -> { out.append("•  "); out.inlines(block.inlines) }
                is ChatMarkdown.Block.Numbered -> { out.append("${block.n}.  "); out.inlines(block.inlines) }
                is ChatMarkdown.Block.Paragraph -> out.inlines(block.inlines)
                is ChatMarkdown.Block.Heading -> {
                    out.inlines(block.inlines)
                    out.span(StyleSpan(Typeface.BOLD), start)
                    out.span(RelativeSizeSpan(if (block.level == 3) 1.17f else 1.07f), start)
                }
                is ChatMarkdown.Block.Quote -> {
                    out.inlines(block.inlines)
                    out.span(QuoteSpan(), start)
                }
                is ChatMarkdown.Block.Pre -> {
                    out.append(block.text)
                    out.span(TypefaceSpan("monospace"), start)
                }
                is ChatMarkdown.Block.Table -> {
                    fun row(cells: List<List<ChatMarkdown.Inline>>) = cells.forEachIndexed { k, cell ->
                        if (k > 0) out.append("  |  ")
                        out.inlines(cell)
                    }
                    row(block.head)
                    out.span(StyleSpan(Typeface.BOLD), start)
                    block.rows.forEach { out.append("\n"); row(it) }
                }
            }
        }
        return out
    }
}
