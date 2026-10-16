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
    fun `parses lists, headings and paragraphs`() {
        val blocks = ChatMarkdown.parse("## Plans\nWe have:\n- Pro\n+ Plus\n2. Team\n\nBye")
        assertThat(blocks.map { it::class.simpleName }).containsExactly("Heading", "Paragraph", "Bullet", "Bullet", "Numbered", "Paragraph").inOrder()
        assertThat(ChatMarkdown.plainText(blocks)).isEqualTo("Plans\nWe have:\n• Pro\n• Plus\n2. Team\nBye")
        assertThat(ChatMarkdown.htmlToMarkdown("<ul><li>One</li><li>Two</li></ul>")).isEqualTo("- One\n\n- Two")
    }

    @Test
    fun `headings - # and ## drawn as level 4, 3-6 keep theirs`() {
        val levels = ChatMarkdown.parse("# A\n## B\n### C\n#### D\n##### E\n###### F").map { (it as ChatMarkdown.Block.Heading).level }
        assertThat(levels).containsExactly(4, 4, 3, 4, 5, 6).inOrder()
        assertThat(ChatMarkdown.htmlToMarkdown("<h2>Plans</h2><h3>Pro</h3><h6>small</h6>")).isEqualTo("#### Plans\n\n### Pro\n\n###### small")
        assertThat(ChatMarkdown.parse("#hashtag")[0]).isInstanceOf(ChatMarkdown.Block.Paragraph::class.java)
    }

    @Test
    fun `tables, as the web draws markdown tables`() {
        val t = ChatMarkdown.parse("| Plan | Price |\n|---|:---:|\n| **Pro** | \$49 |\n| Team |").single() as ChatMarkdown.Block.Table
        assertThat(t.head).containsExactly(listOf(ChatMarkdown.Inline.Plain("Plan")), listOf(ChatMarkdown.Inline.Plain("Price"))).inOrder()
        assertThat(t.rows).containsExactly(
            listOf(listOf(ChatMarkdown.Inline.Bold("Pro")), listOf(ChatMarkdown.Inline.Plain("\$49"))),
            // A short row is filled to the head's width.
            listOf(listOf(ChatMarkdown.Inline.Plain("Team")), emptyList())
        ).inOrder()
        // A row without its rule is not a table.
        assertThat(ChatMarkdown.parse("| a | b |\n| c | d |")[0]).isInstanceOf(ChatMarkdown.Block.Paragraph::class.java)
    }

    @Test
    fun `a person's html table, its cells' pipes kept`() {
        val md = ChatMarkdown.htmlToMarkdown("<table><thead><tr><th>Plan</th><th>Note</th></tr></thead><tbody><tr><td><b>Pro</b></td><td>a | b</td></tr></tbody></table>")
        assertThat(md).isEqualTo("| Plan | Note |\n| --- | --- |\n| **Pro** | a \\| b |")
        val t = ChatMarkdown.parse(md).single() as ChatMarkdown.Block.Table
        assertThat(t.rows[0][1]).containsExactly(ChatMarkdown.Inline.Plain("a | b"))
    }

    @Test
    fun `blockquote, pre and strikethrough from a person's html`() {
        assertThat(ChatMarkdown.htmlToMarkdown("<blockquote><p>Quoted</p><p>twice</p></blockquote><p>After</p>")).isEqualTo("> Quoted\n> twice\n\nAfter")
        assertThat(ChatMarkdown.htmlToMarkdown("<pre>npm i\n  **x**</pre>")).isEqualTo("```\nnpm i\n  **x**\n```")
        assertThat(ChatMarkdown.htmlToMarkdown("<p><s>\$59</s> <del>old</del> \$49</p>")).isEqualTo("~~\$59~~ ~~old~~ \$49")
        val blocks = ChatMarkdown.parse("> Quoted\n> twice\n\n```\nnpm i\n  **x**\n```\nok ~~no~~")
        assertThat(blocks.map { it::class.simpleName }).containsExactly("Quote", "Pre", "Paragraph").inOrder()
        assertThat(blocks[0].inlines).containsExactly(ChatMarkdown.Inline.Plain("Quoted\ntwice"))
        assertThat((blocks[1] as ChatMarkdown.Block.Pre).text).isEqualTo("npm i\n  **x**")
        assertThat(blocks[2].inlines).containsExactly(ChatMarkdown.Inline.Plain("ok "), ChatMarkdown.Inline.Strike("no")).inOrder()
        assertThat(ChatMarkdown.plainText(blocks)).isEqualTo("Quoted\ntwice\nnpm i\n  **x**\nok no")
    }

    @Test
    fun `no pattern has a lone closing brace (Android ICU, #70)`() {
        val src = java.io.File("src/main/java/com/conferbot/sdk/utils/ChatMarkdown.kt").readText()
        Regex("Regex\\(\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(src).forEach { m ->
            val pattern = m.groupValues[1]
            assertThat(pattern.replace(Regex("\\{\\d+(,\\d*)?\\}"), "")).doesNotContain("}")
        }
    }
}
