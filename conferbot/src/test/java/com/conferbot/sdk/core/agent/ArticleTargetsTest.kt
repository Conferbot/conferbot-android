package com.conferbot.sdk.core.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Where a cited help article and an article card open
 * (AgentBotParser.sourceTarget / richArticleTarget), the cases of the React
 * Native SDK's __tests__/core/articleTargets.test.ts (#90).
 */
class ArticleTargetsTest {

    private val id = "65a1b2c3d4e5f60718293a4b"

    @Test
    fun `a source - in the app while articles can open there, else its page, else nowhere`() {
        val both = AgentSource("Refunds", "https://help.example.com/refunds", id)
        assertThat(AgentBotParser.sourceTarget(both, true)).isEqualTo(OpenTarget.Article(id, "Refunds"))
        assertThat(AgentBotParser.sourceTarget(both, false)).isEqualTo(OpenTarget.Url("https://help.example.com/refunds"))
        assertThat(AgentBotParser.sourceTarget(AgentSource("Refunds", null, id), false)).isNull()
        assertThat(AgentBotParser.sourceTarget(AgentSource("Page", "http://example.com/a"), true)).isEqualTo(OpenTarget.Url("http://example.com/a"))
        // Not an ObjectId, not an http(s) page: drawn, not tappable.
        assertThat(AgentBotParser.sourceTarget(AgentSource("x", "javascript:alert(1)", "abc"), true)).isNull()
        assertThat(AgentBotParser.sourceTarget(null, true)).isNull()
    }

    @Test
    fun `an article card - its public page first, else the article in the app`() {
        val withPage = RichEntry.Article(id, "Refunds", "", "https://help.example.com/refunds")
        assertThat(AgentBotParser.richArticleTarget(withPage, true)).isEqualTo(OpenTarget.Url("https://help.example.com/refunds"))
        val noPage = RichEntry.Article(id, "Refunds", "", null)
        assertThat(AgentBotParser.richArticleTarget(noPage, true)).isEqualTo(OpenTarget.Article(id, "Refunds"))
        assertThat(AgentBotParser.richArticleTarget(noPage, false)).isNull()
        // A card's id must be an article's.
        assertThat(AgentBotParser.richArticleTarget(RichEntry.Article("not-an-id", "x", "", null), true)).isNull()
        assertThat(AgentBotParser.richArticleTarget(RichEntry.QuickReplies("q", listOf("a", "b")), true)).isNull()
    }

    @Test
    fun `parsed sources and cards keep only safe links`() {
        val sources = AgentBotParser.normalizeSources(listOf(mapOf("articleId" to id, "title" to "Refunds", "url" to "ftp://x")))
        assertThat(AgentBotParser.sourceTarget(sources.single(), false)).isNull()
        val rich = AgentBotParser.safeRich(mapOf("kind" to "article", "articleId" to id, "title" to "Refunds", "url" to "http://insecure"))
        assertThat(AgentBotParser.richArticleTarget(rich, true)).isEqualTo(OpenTarget.Article(id, "Refunds"))
    }
}
