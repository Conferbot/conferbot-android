package com.conferbot.sdk.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatMarkdownTest {

    @Test
    fun `turns the inbox html into markdown`() {
        assertThat(ChatMarkdown.htmlToMarkdown("<p>Hi <strong>Sam</strong></p><p>See <a href=\"https://a.co/x\">this</a> &amp; that</p>"))
            .isEqualTo("Hi **Sam**\n\nSee [this](https://a.co/x) & that")
        assertThat(ChatMarkdown.htmlToMarkdown("<a href=\"javascript:alert(1)\">click</a>")).isEqualTo("click")
        assertThat(ChatMarkdown.htmlToMarkdown("<p>**Pro** is \$49</p>")).isEqualTo("**Pro** is \$49")
    }

    @Test
    fun `parses inline runs`() {
        val runs = ChatMarkdown.parseInline("a **b** _c_ `d` [e](https://e.co) https://f.co/x.")
        assertThat(runs).containsExactly(
            ChatMarkdown.Inline.Plain("a "),
            ChatMarkdown.Inline.Bold("b"),
            ChatMarkdown.Inline.Plain(" "),
            ChatMarkdown.Inline.Italic("c"),
            ChatMarkdown.Inline.Plain(" "),
            ChatMarkdown.Inline.Code("d"),
            ChatMarkdown.Inline.Plain(" "),
            ChatMarkdown.Inline.Link("e", "https://e.co"),
            ChatMarkdown.Inline.Plain(" "),
            ChatMarkdown.Inline.Link("https://f.co/x", "https://f.co/x"),
            ChatMarkdown.Inline.Plain(".")
        ).inOrder()
        assertThat(ChatMarkdown.parseInline("snake_case_name stays")).containsExactly(ChatMarkdown.Inline.Plain("snake_case_name stays"))
        assertThat(ChatMarkdown.parseInline("[x](javascript:1)")).containsExactly(ChatMarkdown.Inline.Plain("x"))
    }

    @Test
    fun `parses lists and paragraphs`() {
        val blocks = ChatMarkdown.parse("## Plans\nWe have:\n- Pro\n2. Team\n\nBye")
        assertThat(blocks.map { it::class.simpleName }).containsExactly("Paragraph", "Bullet", "Numbered", "Paragraph").inOrder()
        assertThat(ChatMarkdown.plainText(blocks)).isEqualTo("Plans\nWe have:\n• Pro\n2. Team\nBye")
    }
}
