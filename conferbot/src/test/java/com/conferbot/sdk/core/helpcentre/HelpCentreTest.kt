package com.conferbot.sdk.core.helpcentre

import com.conferbot.sdk.models.KnowledgeBaseArticle
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The help centre rules and parser, on a payload captured from the local
 * embed server (bot 6ab686f8d47ff029b76892f7): `fetched-chatbot-data`'s
 * knowledgeBaseData and customizations, a `get-kb-article` reply and a
 * `search-kb-articles` reply (src/test/resources/help-centre-payload.json).
 */
@Suppress("UNCHECKED_CAST")
class HelpCentreTest {

    private val captured: Map<String, Any?> by lazy {
        val text = javaClass.classLoader!!.getResource("help-centre-payload.json")!!.readText()
        Gson().fromJson(text, Map::class.java) as Map<String, Any?>
    }
    private val kb get() = captured["knowledgeBaseData"] as Map<String, Any?>
    private val on = mapOf<String, Any?>("enableKnowledgeBase" to true)

    @Test
    fun `parses categories and recent articles from the real payload`() {
        val data = HelpCentre.parse(kb, captured["customizations"] as Map<String, Any?>)
        assertTrue(data.isEnabled)
        assertFalse(data.contentIncluded)
        assertEquals(listOf(8, 7, 6, 4, 1), data.categories.map { it.articles.size })
        assertEquals("Chatbot Essentials", data.categories[0].name)
        assertEquals("plug", data.categories[0].icon)
        assertEquals(4, data.recentArticles.size)

        val first = data.categories[0].articles[0]
        assertEquals("6ab7ac01b03f13ff22412c4e", first.id)
        assertEquals("How to Integrate OpenAI with Conferbot", first.title)
        // A category's article has its category as a bare id.
        assertEquals("6ab7abfcb03f13ff22412c36", first.categoryId)
        assertEquals("Chatbot Essentials", first.categoryName)
        assertEquals("admin", first.author?.name)
        assertEquals(2, first.readingTime)
        assertEquals("2 min read", first.calculateReadingTime())
        assertNotNull(first.publishedDate)
        // No bodies on connect: fetched on open.
        assertNull(first.content)
        assertFalse(first.hasBody)

        // A recent article has its category populated.
        val recent = data.recentArticles[0]
        assertEquals("6ab7abfcb03f13ff22412c38", recent.categoryId)
        assertEquals("Chatbot Creation", recent.categoryName)
    }

    @Test
    fun `get-kb-article reply gives the body`() {
        val article = HelpCentre.parseArticleReply(captured["getKbArticle"] as Map<String, Any?>)
        assertNotNull(article)
        assertEquals("6ab7ac01b03f13ff22412c4e", article!!.id)
        assertTrue(article.hasBody)
        assertTrue(article.content!!.startsWith("<p>Integrating OpenAI"))
        assertEquals("Chatbot Essentials", article.categoryName)
    }

    @Test
    fun `a failed or bodiless get-kb-article reply gives nothing`() {
        assertNull(HelpCentre.parseArticleReply(mapOf("success" to false, "articleId" to "x", "error" to "Article not found")))
        assertNull(HelpCentre.parseArticleReply(mapOf("success" to true, "article" to mapOf("_id" to "a", "title" to "T"))))
        assertNull(HelpCentre.parseArticleReply(null))
    }

    @Test
    fun `search-kb-articles reply gives matches with snippets, merged after local ones`() {
        val data = HelpCentre.parse(kb, on)
        val remote = HelpCentre.parseSearchReply(captured["search"] as Map<String, Any?>)!!
        assertEquals(3, remote.size)
        assertTrue(remote[0].snippet!!.startsWith("Live chat with AI chatbot handoff"))

        val local = listOf(data.getArticleById("6ab7ac01b03f13ff22412c4e")!!)
        val merged = HelpCentre.mergeSearch(local, remote, data)
        assertEquals(listOf("6ab7ac01b03f13ff22412c4e", "6ab7ac19b03f13ff22412cd5", "6ab7ac0bb03f13ff22412c87"), merged.map { it.id })
        // A remote hit the payload lists keeps its category from the payload.
        assertEquals("AI Chatbot Features", merged[1].categoryName)
        assertNull(HelpCentre.parseSearchReply(mapOf("success" to false, "articles" to emptyList<Any>())))
    }

    @Test
    fun `help centre is off unless enableKnowledgeBase is true`() {
        assertFalse(HelpCentre.on(null))
        assertFalse(HelpCentre.on(emptyMap()))
        assertFalse(HelpCentre.on(mapOf("enableKnowledgeBase" to false)))
        assertFalse(HelpCentre.on(mapOf("enableKnowledgeBase" to "true")))
        assertTrue(HelpCentre.on(on))
    }

    @Test
    fun `off keeps no articles, whatever the server still sends`() {
        val off = mapOf<String, Any?>("enableKnowledgeBase" to false, "enableRecentArticles" to true)
        val data = HelpCentre.parse(kb, off)
        assertFalse(data.isEnabled)
        assertTrue(data.categories.isEmpty())
        assertTrue(data.recentArticles.isEmpty())
        assertFalse(HelpCentre.visible(off, HelpCentre.parse(kb, on)))
        assertFalse(HelpCentre.canOpen(off, HelpCentre.parse(kb, on), "6ab7ac01b03f13ff22412c4e"))
    }

    @Test
    fun `on with no published article counts as off`() {
        val empty = HelpCentre.parse(mapOf("recentArticles" to emptyList<Any>(), "categories" to emptyList<Any>()), on)
        assertFalse(HelpCentre.visible(on, empty))
        assertFalse(HelpCentre.visible(on, null))
        assertTrue(HelpCentre.visible(on, HelpCentre.parse(kb, on)))
    }

    @Test
    fun `hideRecentArticles hides the recent list only`() {
        val data = HelpCentre.parse(kb, on)
        assertTrue(HelpCentre.showsRecent(on, data))
        val hidden = on + ("hideRecentArticles" to true)
        assertFalse(HelpCentre.showsRecent(hidden, HelpCentre.parse(kb, hidden)))
        assertTrue(HelpCentre.visible(hidden, HelpCentre.parse(kb, hidden)))
        // null (as captured) means shown
        assertTrue(HelpCentre.showsRecent(on + ("hideRecentArticles" to null), data))
    }

    @Test
    fun `drafts and rows without an id are dropped`() {
        val data = HelpCentre.parse(
            mapOf(
                "recentArticles" to listOf(
                    mapOf("_id" to "a1", "title" to "Live"),
                    mapOf("_id" to "a2", "title" to "Draft", "reviewStatus" to "draft"),
                    mapOf("title" to "No id")
                ),
                "categories" to listOf(
                    mapOf("_id" to "c1", "name" to "Only drafts", "articles" to listOf(mapOf("_id" to "d", "title" to "D", "reviewStatus" to "pending")))
                )
            ),
            on
        )
        assertEquals(listOf("a1"), data.recentArticles.map { it.id })
        assertTrue(data.categories.isEmpty())
    }

    @Test
    fun `findArticle opens by id from a category, the recent list, or the id and title`() {
        val data = HelpCentre.parse(kb, on)
        val (inCategory, category) = HelpCentre.findArticle(data, "6ab7ac01b03f13ff22412c4e")
        assertEquals("How to Integrate OpenAI with Conferbot", inCategory?.title)
        assertEquals("Chatbot Essentials", category?.name)

        // An AI source the payload does not list: from its id and title.
        val (cited, none) = HelpCentre.findArticle(data, "6ab7ffffffffffffffffffff", "Cited article")
        assertEquals(KnowledgeBaseArticle(id = "6ab7ffffffffffffffffffff", title = "Cited article"), cited)
        assertNull(none)
        assertNull(HelpCentre.findArticle(data, "6ab7ffffffffffffffffffff").first)
    }

    @Test
    fun `canOpen needs the help centre on and an id`() {
        val data = HelpCentre.parse(kb, on)
        assertTrue(HelpCentre.canOpen(on, data, "6ab7ac01b03f13ff22412c4e"))
        assertFalse(HelpCentre.canOpen(on, data, ""))
        assertFalse(HelpCentre.canOpen(on, data, null))
    }
}
