package com.conferbot.sdk.core.helpcentre

import com.conferbot.sdk.core.helpcentre.ArticleHtml.Block
import com.conferbot.sdk.core.helpcentre.ArticleHtml.Run
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A help centre article's HTML read into blocks (#57): what the web widget
 * renders, drawn by the app without a WebView.
 */
class ArticleHtmlTest {

    private fun text(b: Block): String = when (b) {
        is Block.Heading -> b.runs.joinToString("") { it.text }
        is Block.Paragraph -> b.runs.joinToString("") { it.text }
        is Block.ListItem -> b.runs.joinToString("") { it.text }
        is Block.Quote -> b.runs.joinToString("") { it.text }
        is Block.Code -> b.text
        else -> ""
    }

    @Test
    fun `headings and paragraphs, no tags left`() {
        val blocks = ArticleHtml.parse("<h2>Getting started</h2><p>Open the <b>dashboard</b>.</p>")
        assertThat(blocks).hasSize(2)
        val h = blocks[0] as Block.Heading
        assertThat(h.level).isEqualTo(2)
        assertThat(text(h)).isEqualTo("Getting started")
        val p = blocks[1] as Block.Paragraph
        assertThat(p.runs).containsExactly(Run("Open the "), Run("dashboard", bold = true), Run(".")).inOrder()
    }

    @Test
    fun `bold, italic, underline, struck and inline code`() {
        val p = ArticleHtml.parse("<p><strong>a</strong><em>b</em><u>c</u><del>d</del><code>e</code><b><i>f</i></b></p>").single() as Block.Paragraph
        assertThat(p.runs).containsExactly(
            Run("a", bold = true), Run("b", italic = true), Run("c", underline = true),
            Run("d", strike = true), Run("e", code = true), Run("f", bold = true, italic = true)
        ).inOrder()
    }

    @Test
    fun `links keep their target, only ones the app may open`() {
        val p = ArticleHtml.parse(
            "<p><a href=\"https://conferbot.com/docs\">Docs</a> <a href='mailto:hi@x.io'>mail</a> " +
                "<a href=\"javascript:alert(1)\">bad</a> <a href=\"/relative\">rel</a></p>"
        ).single() as Block.Paragraph
        assertThat(p.runs.first { it.text == "Docs" }.href).isEqualTo("https://conferbot.com/docs")
        assertThat(p.runs.first { it.text == "mail" }.href).isEqualTo("mailto:hi@x.io")
        assertThat(p.runs.filter { it.href == null }.joinToString("") { it.text }).contains("bad")
        assertThat(p.runs.filter { it.href == null }.joinToString("") { it.text }).contains("rel")
    }

    @Test
    fun `bulleted and numbered lists, nested, start honoured`() {
        val blocks = ArticleHtml.parse(
            "<ul><li>One</li><li>Two<ul><li>Two a</li></ul></li></ul><ol start=\"3\"><li>Third</li><li>Fourth</li></ol>"
        )
        val items = blocks.filterIsInstance<Block.ListItem>()
        assertThat(items.map { text(it) }).containsExactly("One", "Two", "Two a", "Third", "Fourth").inOrder()
        assertThat(items.map { it.depth }).containsExactly(0, 0, 1, 0, 0).inOrder()
        assertThat(items[0].ordered).isFalse()
        assertThat(items[3].ordered).isTrue()
        assertThat(items[3].number).isEqualTo(3)
        assertThat(items[4].number).isEqualTo(4)
    }

    @Test
    fun `an item's later paragraph continues it`() {
        val items = ArticleHtml.parse("<ol><li><p>Step</p><p>More on it</p></li></ol>").filterIsInstance<Block.ListItem>()
        assertThat(items).hasSize(2)
        assertThat(items[0].number).isEqualTo(1)
        assertThat(items[1].continued).isTrue()
    }

    @Test
    fun `images are blocks, http only`() {
        val blocks = ArticleHtml.parse("<p>See:</p><img src=\"https://cdn.x/a.png\" alt=\"Settings\"><img src=\"data:image/png;base64,AAA\">")
        val images = blocks.filterIsInstance<Block.Image>()
        assertThat(images).containsExactly(Block.Image("https://cdn.x/a.png", "Settings"))
    }

    @Test
    fun `simple tables, the header row known`() {
        val t = ArticleHtml.parse(
            "<table><thead><tr><th>Plan</th><th>Price</th></tr></thead><tbody>\n<tr><td>Pro</td><td><b>$29</b></td></tr></tbody></table>"
        ).single() as Block.Table
        assertThat(t.header).isTrue()
        assertThat(t.rows).hasSize(2)
        assertThat(t.rows[0].map { cell -> cell.joinToString("") { it.text } }).containsExactly("Plan", "Price").inOrder()
        assertThat(t.rows[1][1]).containsExactly(Run("$29", bold = true))
    }

    @Test
    fun `line breaks kept, whitespace collapsed, entities decoded`() {
        val p = ArticleHtml.parse("<p>  Line&nbsp;one<br/>line\n   two &amp; &#8220;three&#x201D; &copy;</p>").single() as Block.Paragraph
        assertThat(text(p)).isEqualTo("Line one\nline two & “three” ©")
    }

    @Test
    fun `code blocks keep their spacing, quotes and rules`() {
        val blocks = ArticleHtml.parse("<pre><code>val a = 1\n  val b = &lt;2&gt;</code></pre><blockquote>Note this</blockquote><hr>")
        assertThat((blocks[0] as Block.Code).text).isEqualTo("val a = 1\n  val b = <2>")
        assertThat(text(blocks[1])).isEqualTo("Note this")
        assertThat(blocks[1]).isInstanceOf(Block.Quote::class.java)
        assertThat(blocks[2]).isEqualTo(Block.Rule)
    }

    @Test
    fun `scripts, styles and comments dropped, unknown tags keep their text`() {
        val blocks = ArticleHtml.parse("<style>p{color:red}</style><script>alert(1)</script><!-- hidden --><p><span class=\"x\">Kept</span></p>")
        assertThat(blocks.map { text(it) }).containsExactly("Kept")
    }

    @Test
    fun `a plain-text body is its paragraphs`() {
        assertThat(ArticleHtml.isHtml("Just text, 2 < 3")).isFalse()
        val blocks = ArticleHtml.parse("First line\nsecond line\n\nNext paragraph")
        assertThat(blocks.map { text(it) }).containsExactly("First line\nsecond line", "Next paragraph").inOrder()
    }

    @Test
    fun `broken markup still reads`() {
        val blocks = ArticleHtml.parse("<p>Unclosed <b>bold<p>Next</p><div>tail")
        assertThat(blocks.map { text(it) }).containsExactly("Unclosed bold", "Next", "tail").inOrder()
        assertThat(ArticleHtml.parse("")).isEmpty()
    }
}
