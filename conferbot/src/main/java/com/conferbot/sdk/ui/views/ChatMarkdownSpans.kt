package com.conferbot.sdk.ui.views

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import com.conferbot.sdk.utils.ChatMarkdown

/** An agent message's markdown (utils/ChatMarkdown.kt) as a TextView Spanned. */
object ChatMarkdownSpans {
    fun toSpanned(markdown: String?): Spanned {
        val out = SpannableStringBuilder()
        ChatMarkdown.parse(markdown).forEachIndexed { i, block ->
            if (i > 0) out.append("\n")
            when (block) {
                is ChatMarkdown.Block.Bullet -> out.append("•  ")
                is ChatMarkdown.Block.Numbered -> out.append("${block.n}.  ")
                is ChatMarkdown.Block.Paragraph -> {}
            }
            block.inlines.forEach { inline ->
                val start = out.length
                out.append(inline.text)
                val end = out.length
                when (inline) {
                    is ChatMarkdown.Inline.Bold -> out.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    is ChatMarkdown.Inline.Italic -> out.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    is ChatMarkdown.Inline.Code -> out.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    is ChatMarkdown.Inline.Link -> out.setSpan(URLSpan(inline.url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    is ChatMarkdown.Inline.Plain -> {}
                }
            }
        }
        return out
    }
}
