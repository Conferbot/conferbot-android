package com.conferbot.sdk.core.waiting

import com.conferbot.sdk.core.livechat.HandoverWaitingTest
import com.conferbot.sdk.core.waiting.WaitArticles.HelpItem
import com.conferbot.sdk.core.waiting.WaitArticles.WAIT_BEFORE_MS
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * While you wait for a person (core/waiting/WaitArticles.kt): the articles
 * for the visitor's question, the server's queue-help card and "That solved
 * it", the cases of the React Native SDK's __tests__/core/waitArticles.test.ts
 * (web tests/waitArticles.test.js, special.ts _wireQueue), on a fake clock.
 */
class WaitArticlesTest {

    private val cats = listOf(
        mapOf(
            "name" to "Billing",
            "articles" to listOf(
                mapOf("_id" to "1", "title" to "Downloading invoices", "description" to "Find every invoice in one place."),
                mapOf("_id" to "2", "title" to "Changing your plan", "description" to "Upgrade, downgrade or cancel at any time.")
            )
        )
    )
    private val on = mapOf<String, Any?>("enableKnowledgeBase" to true)

    @Test
    fun `the articles that may answer their question, at most two`() {
        assertThat(WaitArticles.waitArticlesFor(on, "Where can I download my invoices?", cats, emptyList()).map { it.id }).containsExactly("1")
        assertThat(WaitArticles.waitArticlesFor(on, "Where can I download my invoices?", cats, emptyList())[0])
            .isEqualTo(HelpItem("article", "1", "Downloading invoices", "Find every invoice in one place."))
        assertThat(WaitArticles.waitArticlesFor(on, "billing", cats, emptyList()).size).isAtMost(2)
        assertThat(WaitArticles.waitArticlesFor(on, "how do I change my plan", cats, emptyList()).map { it.id }).containsExactly("2")
    }

    @Test
    fun `nothing with the help centre off, no question, or nothing that fits`() {
        assertThat(WaitArticles.waitArticlesFor(mapOf("enableKnowledgeBase" to false), "download my invoices", cats, emptyList())).isEmpty()
        assertThat(WaitArticles.waitArticlesFor(emptyMap(), "download my invoices", cats, emptyList())).isEmpty()
        assertThat(WaitArticles.waitArticlesFor(on, "", cats, emptyList())).isEmpty()
        assertThat(WaitArticles.waitArticlesFor(on, "do you ship to mars", cats, emptyList())).isEmpty()
    }

    @Test
    fun `the help centre once each, categories first, recent articles after`() {
        val all = WaitArticles.flattenArticles(
            cats,
            listOf(
                mapOf("_id" to "1", "title" to "dup"),
                mapOf("_id" to "9", "title" to " Recent ", "content" to "<p>Hi&amp;there</p>", "category" to mapOf("name" to "X")),
                mapOf("title" to "no id")
            )
        )
        assertThat(all.map { Triple(it.id, it.title, it.categoryName) }).containsExactly(
            Triple("1", "Downloading invoices", "Billing"),
            Triple("2", "Changing your plan", "Billing"),
            Triple("9", "Recent", "X")
        ).inOrder()
        assertThat(all[2].text).isEqualTo("Hi there")
        assertThat(WaitArticles.findArticle("2", cats, emptyList())?.get("title")).isEqualTo("Changing your plan")
        assertThat(WaitArticles.findArticle("9", cats, listOf(mapOf("_id" to "9", "title" to "R")))?.get("title")).isEqualTo("R")
        assertThat(WaitArticles.findArticle("nope", cats, emptyList())).isNull()
        assertThat(WaitArticles.keywordsOf("How do I change my plan?")).containsExactly("change", "plan").inOrder()
        assertThat(WaitArticles.answersFor(all, "invoices", 4).map { it.id }).containsExactly("1")
    }

    @Test
    fun `the question - the newest real question, not hi and not an ask for a person`() {
        assertThat(WaitArticles.isRealQuestion("hi")).isFalse()
        assertThat(WaitArticles.isRealQuestion("Where can I download my invoices?")).isTrue()
        assertThat(WaitArticles.isRealQuestion("me@example.com")).isFalse()
        val record = listOf(
            mapOf("type" to "user-message", "text" to "Where can I download my invoices?"),
            mapOf("type" to "bot-message", "text" to "Let me check that for you right now."),
            mapOf("type" to "user-message", "text" to "Can I talk to a real person please"),
            mapOf("type" to "user-message", "text" to "thanks")
        )
        assertThat(WaitArticles.waitQuestion(record)).isEqualTo("Where can I download my invoices?")
        assertThat(WaitArticles.waitQuestion(emptyList())).isEmpty()
        assertThat(WaitArticles.waitQuestion(null)).isEmpty()
        // The record as this SDK sends it: a typed answer's shape, its type the step's.
        assertThat(WaitArticles.waitQuestion(listOf(mapOf("type" to null, "shape" to "user-input-response", "text" to "Where can I download my invoices?"))))
            .isEqualTo("Where can I download my invoices?")
    }

    @Test
    fun `helpItemsOf - articles and pages, an id and a title each - a page only with an http address`() {
        val items = WaitArticles.helpItemsOf(
            mapOf(
                "chatSessionId" to "s",
                "offer" to 1,
                "items" to listOf(
                    mapOf("kind" to "article", "id" to "a1", "title" to "Invoices", "description" to "Find them", "score" to 0.8),
                    mapOf("kind" to "page", "id" to "p1", "title" to "Pricing", "url" to "https://example.com/pricing", "description" to ""),
                    mapOf("kind" to "page", "id" to "p2", "title" to "Bad", "url" to "javascript:alert(1)"),
                    mapOf("id" to "x", "title" to "  "),
                    null
                )
            )
        )
        assertThat(items).containsExactly(
            HelpItem("article", "a1", "Invoices", "Find them"),
            HelpItem("page", "p1", "Pricing", url = "https://example.com/pricing"),
            HelpItem("page", "p2", "Bad")
        ).inOrder()
        assertThat(WaitArticles.helpItemsOf(null)).isEmpty()
    }

    private class Setup(question: String = "Where can I download my invoices?", c: Map<String, Any?>? = mapOf("enableKnowledgeBase" to true), joined: Boolean = false, cats: List<Any?>) {
        val k = HandoverWaitingTest.FakeClock()
        val cards = mutableListOf<HelpCardState?>()
        val help = WaitHelp(
            customizations = { c },
            question = { question },
            articles = { cats to emptyList() },
            joined = { joined },
            onChange = { cards += it },
            timer = k
        )
        fun card() = help.card()
    }

    @Test
    fun `WAIT_BEFORE_MS into a wait - the articles for their question, no That solved it`() {
        val s = Setup(cats = cats)
        s.help.startWait()
        s.k.tick(WAIT_BEFORE_MS - 1)
        assertThat(s.card()).isNull()
        s.k.tick(1)
        val c = s.card()!!
        assertThat(c.key).isEqualTo(1)
        assertThat(c.solvable).isFalse()
        assertThat(c.settled).isFalse()
        assertThat(c.items.map { it.id to it.title }).containsExactly("1" to "Downloading invoices")
        assertThat(s.help.solving()).isNull()
    }

    @Test
    fun `nothing when the wait ends first, a person is here, nothing fits or there is no question`() {
        val a = Setup(cats = cats)
        a.help.startWait()
        a.k.tick(3000)
        a.help.endWait()
        a.k.tick(10_000)
        assertThat(a.card()).isNull()
        listOf(
            Setup(joined = true, cats = cats),
            Setup(question = "do you ship to mars", cats = cats),
            Setup(question = "", cats = cats),
            Setup(c = emptyMap(), cats = cats)
        ).forEach { s ->
            s.help.startWait()
            s.k.tick(WAIT_BEFORE_MS)
            assertThat(s.card()).isNull()
        }
    }

    @Test
    fun `the server keeps a queue - its pick only, ours stays out`() {
        val s = Setup(cats = cats)
        s.help.startWait()
        s.help.queueStatus(mapOf("status" to "waiting", "position" to 1))
        s.k.tick(WAIT_BEFORE_MS)
        assertThat(s.card()).isNull()
        // A reload: queue-status carries the help it was offered.
        s.help.queueStatus(mapOf("status" to "waiting", "help" to mapOf("items" to listOf(mapOf("kind" to "article", "id" to "a1", "title" to "Invoices")), "offer" to 1)))
        assertThat(s.card()!!.solvable).isTrue()
        assertThat(s.card()!!.items.map { it.id }).containsExactly("a1")
        s.help.queueStatus(mapOf("status" to "none", "help" to mapOf("items" to listOf(mapOf("id" to "zz", "title" to "Other")))))
        assertThat(s.card()!!.items[0].id).isEqualTo("a1")
    }

    @Test
    fun `queue-help - the card, replaced only by new items`() {
        val s = Setup(cats = cats)
        s.help.startWait()
        s.help.queueHelp(mapOf("items" to listOf(mapOf("kind" to "article", "id" to "a1", "title" to "Invoices")), "offer" to 1))
        assertThat(s.card()!!.key).isEqualTo(1)
        assertThat(s.card()!!.solvable).isTrue()
        s.help.queueHelp(mapOf("items" to listOf(mapOf("kind" to "article", "id" to "a1", "title" to "Invoices")), "offer" to 1))
        assertThat(s.cards).hasSize(1)
        s.help.queueHelp(mapOf("items" to listOf(mapOf("kind" to "article", "id" to "a2", "title" to "Plans")), "offer" to 2))
        assertThat(s.card()!!.key).isEqualTo(2)
        assertThat(s.card()!!.items.map { it.id }).containsExactly("a2")
        s.help.queueHelp(mapOf("items" to emptyList<Any>()))
        assertThat(s.card()!!.key).isEqualTo(2)
    }

    @Test
    fun `That solved it - the opened row (else the first) goes - ok leaves, else the button comes back`() {
        val s = Setup(cats = cats)
        s.help.startWait()
        s.help.queueHelp(mapOf("items" to listOf(mapOf("id" to "a1", "title" to "One"), mapOf("id" to "a2", "title" to "Two"))))
        s.help.open("a2")
        assertThat(s.help.solving()?.id).isEqualTo("a2")
        assertThat(s.card()!!.busy).isTrue()
        // Busy: a second press sends nothing.
        assertThat(s.help.solving()).isNull()
        s.help.solved(false)
        assertThat(s.card()!!.busy).isFalse()
        assertThat(s.card()!!.done).isFalse()
        assertThat(s.help.solving()?.id).isEqualTo("a2")
        s.help.solved(true)
        assertThat(s.card()!!.busy).isFalse()
        assertThat(s.card()!!.done).isTrue()
        // Done: no more server cards for this wait.
        s.help.queueHelp(mapOf("items" to listOf(mapOf("id" to "a3", "title" to "Three"))))
        assertThat(s.card()!!.items[0].id).isEqualTo("a1")
        // The wait settling keeps the done line.
        s.help.endWait()
        assertThat(s.card()!!.settled).isTrue()
        assertThat(s.card()!!.done).isTrue()
    }

    @Test
    fun `the wait over - the card stays as history, without the button - a new wait gets new cards`() {
        val s = Setup(cats = cats)
        s.help.startWait()
        s.help.queueHelp(mapOf("items" to listOf(mapOf("id" to "a1", "title" to "One"))))
        s.help.endWait()
        assertThat(s.card()!!.settled).isTrue()
        assertThat(s.card()!!.solvable).isFalse()
        assertThat(s.help.solving()).isNull()
        s.help.startWait()
        s.help.queueHelp(mapOf("items" to listOf(mapOf("id" to "a1", "title" to "One"))))
        assertThat(s.card()!!.key).isEqualTo(2)
        assertThat(s.card()!!.settled).isFalse()
        assertThat(s.card()!!.solvable).isTrue()
    }

    @Test
    fun `reset - no card, and our own articles again`() {
        val s = Setup(cats = cats)
        s.help.queueHelp(mapOf("items" to listOf(mapOf("id" to "a1", "title" to "One"))))
        s.help.reset()
        assertThat(s.card()).isNull()
        s.help.startWait()
        s.k.tick(WAIT_BEFORE_MS)
        assertThat(s.card()!!.solvable).isFalse()
    }
}
