package com.conferbot.sdk.core.home

import com.conferbot.sdk.core.customization.MascotArt
import com.conferbot.sdk.core.customization.ThemeTokens
import com.conferbot.sdk.core.requests.RequestState
import com.conferbot.sdk.core.requests.RequestSummary
import com.conferbot.sdk.core.waiting.WaitArticles.HelpArticle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The home screen's decisions (core/home/Home.kt): the React Native SDK's
 * __tests__/core/home.test.ts (the web's tests/homeCopy, homeUtils, askBox,
 * trust and visitorMemory cases) and what firstImpression.ts enhanceHome
 * shows, in what order.
 */
class HomeTest {

    private val NOW = 1_800_000_000_000L
    private fun art(id: String, title: String, description: String = "", content: String = "", extra: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        mapOf("_id" to id, "title" to title, "description" to description, "content" to content) + extra
    private val CATS = listOf(
        mapOf("_id" to "c1", "name" to "Billing", "articles" to listOf(
            art("3", "Changing your plan", "Upgrade, downgrade or cancel at any time."),
            art("4", "Downloading invoices", "Find every invoice in one place.")
        )),
        mapOf("_id" to "c2", "name" to "Getting started", "articles" to listOf(art("1", "How to build your first bot", "A step by step guide.")))
    )
    private val RECENT = listOf(art("2", "Embedding the widget on your website", "Copy one script tag into your site."), art("9", "A draft", extra = mapOf("reviewStatus" to "draft")))
    private val HOME = mapOf<String, Any?>("enableInitialPage" to true, "initialPageTitle" to "Hi there 👋", "logo" to "https://cdn.x/logo.png", "logoText" to "Acme")
    private val AGENT = HomeAgent("Ava", listOf("Pricing?", "Do you integrate with Shopify?", "Refunds", "Fourth"))

    private fun iso(ms: Long) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    // ------------------------------------------------------------ on or off

    @Test
    fun `only when the owner turned it on`() {
        assertThat(Home.on(mapOf("enableInitialPage" to true))).isTrue()
        assertThat(Home.on(mapOf("enableInitialPage" to false))).isFalse()
        assertThat(Home.on(emptyMap())).isFalse()
        assertThat(Home.on(null)).isFalse()
        assertThat(Home.model(mapOf("enableInitialPage" to false))).isNull()
        assertThat(Home.model(null)).isNull()
    }

    @Test
    fun `a view that cannot show falls back`() {
        val home = mapOf<String, Any?>("enableInitialPage" to true)
        assertThat(Home.safeView(home, null)).isEqualTo(HomeView.HOME)
        assertThat(Home.safeView(emptyMap(), null)).isEqualTo(HomeView.CHAT)
        assertThat(Home.safeView(emptyMap(), HomeView.HOME)).isEqualTo(HomeView.CHAT)
        assertThat(Home.safeView(home, HomeView.HELP)).isEqualTo(HomeView.HOME)
        assertThat(Home.safeView(home + ("enableKnowledgeBase" to true), HomeView.HELP)).isEqualTo(HomeView.HELP)
        assertThat(Home.safeView(emptyMap(), HomeView.HELP)).isEqualTo(HomeView.CHAT)
    }

    // ------------------------------------------------------------ words

    @Test
    fun `early bots' placeholder copy is shown as what is true`() {
        val c = mapOf(
            "initialPageCardSubTitle" to "Our usual reply time",
            "initialPageCardTagline" to "Later Today",
            "initialPageDescription" to "Welcome to the Conferbot\nWe help your business to connect with your customer"
        )
        assertThat(Home.copy(c, "Acme Support")).isEqualTo(HomeCopy("Acme Support", "Online now", Home.DESCRIPTION, true))
        assertThat(Home.copy(emptyMap(), "").cardTitle).isEqualTo("Support")
    }

    @Test
    fun `the owner's own words are kept exactly`() {
        val c = mapOf("initialPageCardSubTitle" to "Sales team", "initialPageCardTagline" to "Mon-Fri, 9-5", "initialPageDescription" to "Hi! Questions about orders?")
        assertThat(Home.copy(c, "Acme")).isEqualTo(HomeCopy("Sales team", "Mon-Fri, 9-5", "Hi! Questions about orders?", false))
    }

    @Test
    fun `the salutation follows the clock`() {
        assertThat(Home.salutation(9)).isEqualTo("Good morning")
        assertThat(Home.salutation(14)).isEqualTo("Good afternoon")
        assertThat(Home.salutation(20)).isEqualTo("Good evening")
        assertThat(Home.salutation(2)).isEqualTo("Hello")
        assertThat(Home.salutation(null)).isEqualTo("Good afternoon")
    }

    @Test
    fun `the brand is the header logo and name, nothing when hidden`() {
        assertThat(Home.brand(mapOf("logo" to "https://cdn.x/l.png", "logoText" to " Acme  Co "))).isEqualTo(HomeModel.Brand("https://cdn.x/l.png", "Acme Co"))
        assertThat(Home.brand(mapOf("logo" to "https://cdn.x/l.png"), "Bot")).isEqualTo(HomeModel.Brand("https://cdn.x/l.png", "Bot"))
        assertThat(Home.brand(mapOf("logo" to "https://cdn.x/l.png", "hideLogo" to true))).isNull()
        assertThat(Home.brand(mapOf("logo" to "javascript:x"))).isNull()
    }

    @Test
    fun `the card's face - the owner's, the bot's avatar, its logo, then Conferbot's`() {
        assertThat(Home.cardAvatar(mapOf("initialPageAvatar" to "https://a/me.png", "avatar" to "https://a/b.png"))).isEqualTo("https://a/me.png")
        assertThat(Home.cardAvatar(mapOf("avatar" to "https://a/b.png"))).isEqualTo("https://a/b.png")
        assertThat(Home.cardAvatar(mapOf("avatar" to "https://a/b.png", "hideAvatar" to true, "logo" to "https://a/l.png"))).isEqualTo("https://a/l.png")
        assertThat(Home.cardAvatar(mapOf("logo" to "https://x/conferbot-logo.png"))).contains("conferbot-logo-1710782074234")
    }

    @Test
    fun `light colours take dark ink`() {
        assertThat(Home.isLight(Home.parseColor("#ffffff"))).isTrue()
        assertThat(Home.isLight(Home.parseColor("#0808EE"))).isFalse()
        assertThat(Home.isLight(Home.parseColor("rgb(250, 240, 200)"))).isTrue()
        assertThat(Home.parseColor("teal")).isNull()
    }

    @Test
    fun `the theme's canvas and cards, else the classic widget's`() {
        assertThat(Home.palette(null)).isEqualTo(Home.CLASSIC_PALETTE)
        val t = ThemeTokens(
            bg = "#0c0c10", bg2 = "#101014", card = "#18181d", card2 = "#222228", field = "#000000", menu = "#000000",
            ink = "#f4f4f5", text = "#f4f4f5", muted = "#a1a1aa", faint = "#71717a", line = "#2a2a31", line2 = "#333333"
        )
        assertThat(Home.palette(t)).isEqualTo(HomePalette("#101014", "#18181d", "#222228", "#f4f4f5", "#a1a1aa", "#2a2a31"))
    }

    // ------------------------------------------------------------ the help centre

    @Test
    fun `recent articles show only with the home, the help centre, a published article and not hidden`() {
        val c = mapOf<String, Any?>("enableInitialPage" to true, "enableKnowledgeBase" to true)
        assertThat(Home.showsRecentArticles(c, RECENT)).isTrue()
        assertThat(Home.showsRecentArticles(c + ("hideRecentArticles" to true), RECENT)).isFalse()
        assertThat(Home.showsRecentArticles(c + ("enableKnowledgeBase" to false), RECENT)).isFalse()
        assertThat(Home.showsRecentArticles(c, listOf(RECENT[1]))).isFalse()
    }

    @Test
    fun `topics - named, not empty, most articles first`() {
        val cats = listOf(
            mapOf("name" to "A", "articles" to listOf(1)),
            mapOf("name" to "", "articles" to listOf(1, 2)),
            mapOf("name" to "B", "articles" to listOf(1, 2)),
            mapOf("name" to "C", "articles" to emptyList<Any>())
        )
        assertThat(Home.topCategories(cats).map { it["name"] }).containsExactly("B", "A").inOrder()
    }

    // ------------------------------------------------------------ trust

    @Test
    fun `only https images, once each, at most max`() {
        val list = listOf("https://cdn.x/a.png", "http://cdn.x/b.png", "javascript:alert(1)", "https://cdn.x/a.png", " https://cdn.x/c.png ", "https://x/\"onerror=1", 42, "https://cdn.x/d.png")
        assertThat(Home.trustImages(list, 6)).containsExactly("https://cdn.x/a.png", "https://cdn.x/c.png", "https://cdn.x/d.png").inOrder()
        assertThat(Home.trustImages(list, 1)).containsExactly("https://cdn.x/a.png")
        assertThat(Home.trustImages(null, 3)).isEmpty()
    }

    @Test
    fun `counts read as people say them`() {
        assertThat(Home.compactCount(7)).isEqualTo("7")
        assertThat(Home.compactCount(999)).isEqualTo("999")
        assertThat(Home.compactCount(1320)).isEqualTo("1.3k")
        assertThat(Home.compactCount(12900)).isEqualTo("12k")
        assertThat(Home.compactCount(1000)).isEqualTo("1k")
    }

    @Test
    fun `a rating line for a real average only`() {
        assertThat(Home.ratingLine(mapOf("average" to 4.83, "count" to 120))).isEqualTo(HomeRating("4.8", "120 reviews", 96.0, "Rated 4.8 out of 5 from 120 reviews"))
        assertThat(Home.ratingLine(mapOf("average" to 4.36, "count" to 1520))).isEqualTo(HomeRating("4.4", "1.5k reviews", 88.0, "Rated 4.4 out of 5 from 1520 reviews"))
        assertThat(Home.ratingLine(mapOf("average" to 5, "count" to 1))!!.count).isEqualTo("1 review")
        assertThat(Home.ratingLine(null)).isNull()
        assertThat(Home.ratingLine(mapOf("average" to 6, "count" to 50))).isNull()
        assertThat(Home.ratingLine(mapOf("average" to 4.5, "count" to 0))).isNull()
        assertThat(Home.ratingLine(mapOf("average" to "x", "count" to 30))).isNull()
    }

    // ------------------------------------------------------------ visitor memory

    @Test
    fun `a conversation's first real question is what they came for`() {
        var m = Home.withMessage(null, "s1", "hi", NOW)
        assertThat(m.topic).isNull()
        m = Home.withMessage(m, "s1", "Do you support Shopify stores?", NOW)
        m = Home.withMessage(m, "s1", "And what about WooCommerce?", NOW + 1000)
        assertThat(m.topic).isEqualTo("Do you support Shopify stores?")
        m = Home.withMessage(m, "s2", "Can I cancel my plan anytime?", NOW + 5000)
        assertThat(m.topic).isEqualTo("Can I cancel my plan anytime?")
        assertThat(m.session).isEqualTo("s2")
        assertThat(m.at).isEqualTo(NOW + 5000)
    }

    @Test
    fun `a first name only, and never something that is not a name`() {
        val m = Home.withName(null, "jamie lee", NOW)!!
        assertThat(m.name).isEqualTo("Jamie")
        assertThat(Home.withName(m, "x@y.com", NOW)).isNull()
        assertThat(Home.withName(m, "R2D2", NOW)).isNull()
    }

    @Test
    fun `welcome back once the last chat is over, not while it would be restored`() {
        val m = VisitorMemory(at = NOW, name = "Jamie", topic = "Do you support Shopify stores?")
        assertThat(Home.welcomeBack(m, NOW + Home.LIVE_CHAT_MS - 1)).isNull()
        assertThat(Home.welcomeBack(m, NOW + Home.LIVE_CHAT_MS)).isEqualTo(Home.WelcomeBack("Welcome back, Jamie", "Do you support Shopify stores?"))
        assertThat(Home.welcomeBack(VisitorMemory(at = NOW, topic = "Do you support Shopify stores?"), NOW + Home.LIVE_CHAT_MS))
            .isEqualTo(Home.WelcomeBack("Welcome back", "Do you support Shopify stores?"))
        assertThat(Home.welcomeBack(VisitorMemory(at = NOW), NOW + Home.LIVE_CHAT_MS)).isNull()
        assertThat(Home.welcomeBack(null, NOW)).isNull()
    }

    @Test
    fun `forgotten after 60 days, broken storage is no memory`() {
        val raw = Home.serializeMemory(Home.withMessage(null, "s1", "Do you support Shopify stores?", NOW))
        assertThat(Home.parseMemory(raw, NOW)!!.topic).isEqualTo("Do you support Shopify stores?")
        assertThat(Home.parseMemory(raw, NOW + 61 * 86_400_000L)).isNull()
        assertThat(Home.parseMemory("{nope", NOW)).isNull()
        assertThat(Home.parseMemory(null, NOW)).isNull()
        // An old memory is started again, not added to.
        assertThat(Home.withMessage(Home.parseMemory(raw, NOW), "s9", "hello", NOW + 61 * 86_400_000L).topic).isNull()
    }

    @Test
    fun `the card opens the conversation only when it is the one on the device`() {
        val m = Home.withMessage(null, "s1", "Do you support Shopify stores?", NOW)
        assertThat(Home.canResume(m, "s1")).isTrue()
        assertThat(Home.canResume(m, "s9")).isFalse()
        assertThat(Home.canResume(m, null)).isFalse()
        assertThat(Home.canResume(null, "s1")).isFalse()
    }

    // ------------------------------------------------------------ "Ask anything"

    private val ARTICLES = listOf(
        HelpArticle("1", "How to build your first bot", "A step by step guide to your first chatbot.", "", "Open the builder."),
        HelpArticle("3", "Changing your plan", "Upgrade, downgrade or cancel at any time.", "Billing", ""),
        HelpArticle("4", "Downloading invoices", "Find every invoice in one place.", "Billing", "")
    )

    @Test
    fun `the box asks for a question with an agent, a search without`() {
        assertThat(Home.askPlaceholder(true, false)).isEqualTo("Ask a question…")
        assertThat(Home.askPlaceholder(false, true)).isEqualTo("Search for help")
    }

    @Test
    fun `it shows with an agent or articles, unless the owner turned it off`() {
        assertThat(Home.askBoxOn(emptyMap(), true, false)).isTrue()
        assertThat(Home.askBoxOn(emptyMap(), false, true)).isTrue()
        assertThat(Home.askBoxOn(mapOf("homeAskBox" to false), true, true)).isFalse()
        assertThat(Home.askBoxOn(emptyMap(), false, false)).isFalse()
    }

    @Test
    fun `an agent - Ask first (what Enter does), then the articles`() {
        val rows = Home.askRows("how do I change my plan", "Ava", ARTICLES)
        assertThat(rows.map { it::class }).containsExactly(AskRow.Ask::class, AskRow.Label::class, AskRow.Article::class).inOrder()
        assertThat(rows[0]).isEqualTo(AskRow.Ask("ask", "how do I change my plan", "Ava answers right away"))
        val article = rows[2] as AskRow.Article
        assertThat(article.id).isEqualTo("3")
        assertThat(article.sub).isEqualTo("Upgrade, downgrade or cancel at any time.")
        assertThat(Home.askDefault(rows)).isInstanceOf(AskRow.Ask::class.java)
    }

    @Test
    fun `a flow bot - the articles, else No articles match and Chat with us`() {
        val rows = Home.askRows("invoice", null, ARTICLES)
        assertThat(rows.map { it::class }).containsExactly(AskRow.Label::class, AskRow.Article::class).inOrder()
        assertThat((Home.askDefault(rows) as AskRow.Article).id).isEqualTo("4")
        val none = Home.askRows("do you ship to mars", null, ARTICLES)
        assertThat(none).containsExactly(
            AskRow.None("none", "No articles match “do you ship to mars”."),
            AskRow.Chat("chat", "Chat with us", "We'll help you with it")
        ).inOrder()
        assertThat(Home.askDefault(none)).isInstanceOf(AskRow.Chat::class.java)
        // An agent with nothing found: only the ask.
        assertThat(Home.askRows("do you ship to mars", "Ava", ARTICLES).map { it::class }).containsExactly(AskRow.Ask::class)
        assertThat(Home.askRows("   ", "Ava", ARTICLES)).isEmpty()
    }

    @Test
    fun `the server's matches inside article text come after the local ones, for the query asked`() {
        val remote = RemoteHits("pdf vat", listOf("4" to "Each invoice is a PDF with your VAT number.", "zz" to "not here"))
        val rows = Home.askRows("PDF VAT", null, ARTICLES, remote)
        val hit = rows[1] as AskRow.Article
        assertThat(hit.id).isEqualTo("4")
        assertThat(hit.sub).isEqualTo("Each invoice is a PDF with your VAT number.")
        assertThat(Home.askRows("other", null, ARTICLES, remote).any { it is AskRow.Article }).isFalse()
    }

    @Test
    fun `asks the server with the two longest key words, only when a body is missing`() {
        assertThat(Home.remoteQueryFor("where is my vat number", listOf(mapOf("_id" to "1")))).isEqualTo("number vat")
        assertThat(Home.remoteQueryFor("where is my vat number", listOf(mapOf("_id" to "1", "content" to "<p>x</p>")))).isNull()
        assertThat(Home.remoteQueryFor("how do I", listOf(mapOf("_id" to "1")))).isNull()
    }

    @Test
    fun `marks the words that matched`() {
        assertThat(Home.highlightRuns("Changing your plan", listOf("plan"))).containsExactly(TextRun("Changing your "), TextRun("plan", true)).inOrder()
        assertThat(Home.highlightRuns("Plain", emptyList())).containsExactly(TextRun("Plain"))
        // Characters a pattern would read specially are words like any other.
        assertThat(Home.highlightRuns("Plans {pro}", listOf("{pro}"))).containsExactly(TextRun("Plans "), TextRun("{pro}", true)).inOrder()
    }

    @Test
    fun `the help card's own search - up to five, else ask or chat instead`() {
        val found = Home.helpSearch("billing", ARTICLES, false) as HelpSearch.Results
        assertThat(found.rows.map { it.id }).containsExactly("3", "4").inOrder()
        assertThat(Home.helpSearch("mars", ARTICLES, true)).isEqualTo(HelpSearch.Empty("No articles match “mars”.", "Ask us instead", true))
        assertThat((Home.helpSearch("mars", ARTICLES, false) as HelpSearch.Empty).action).isEqualTo("Chat with us instead")
        assertThat(Home.helpSearch("", ARTICLES, false)).isNull()
    }

    // ------------------------------------------------------------ the home screen

    @Test
    fun `a plain flow bot - the header and Talk to us`() {
        val m = Home.model(HOME, botName = "Acme bot", now = NOW, hour = 9)!!
        assertThat(m.hello).isEqualTo("Good morning")
        assertThat(m.brand).isEqualTo(HomeModel.Brand("https://cdn.x/logo.png", "Acme"))
        assertThat(m.title).isEqualTo("Hi there 👋")
        assertThat(m.description).isEqualTo(Home.DESCRIPTION)
        assertThat(m.order).containsExactly(HomeSection.TALK)
        assertThat(m.ask).isNull()
        assertThat(m.help).isNull()
        assertThat(m.talk.title).isEqualTo("Acme")
        assertThat(m.talk.tagline).isNull()
        assertThat(m.talk.status).isEqualTo("Online · Answers instantly, day or night")
        assertThat(m.talk.dot).isTrue()
        assertThat(m.talk.button).isEqualTo("Chat with us")
        assertThat(m.talk.starters).isEmpty()
        assertThat(m.light).isFalse()
    }

    @Test
    fun `the owner's tagline is kept, the status line after it, status off takes Online and the dot`() {
        val c = HOME + mapOf(
            "initialPageCardTagline" to "Mon-Fri 9-5",
            "hideOnlineStatus" to true,
            "replyTime" to mapOf("mode" to "auto", "handover" to true, "state" to "online", "bucket" to "few_minutes")
        )
        val m = Home.model(c, now = NOW)!!
        assertThat(m.talk.tagline).isEqualTo("Mon-Fri 9-5")
        assertThat(m.talk.status).isEqualTo("Usually replies in a few minutes")
        assertThat(m.talk.dot).isFalse()
        // Hide: no line at all.
        assertThat(Home.model(c + ("replyTime" to mapOf("mode" to "hide", "handover" to true)), now = NOW)!!.talk.status).isNull()
    }

    @Test
    fun `the owner's button text, or the stock one`() {
        assertThat(Home.model(HOME + ("initialPageCardButtonText" to "Message us"))!!.talk.button).isEqualTo("Message us")
        assertThat(Home.model(HOME + ("initialPageCardButtonText" to "Chat with us"))!!.talk.button).isEqualTo("Chat with us")
    }

    @Test
    fun `an agent - the ask box first, three of its questions on the card`() {
        val m = Home.model(HOME, agent = AGENT, now = NOW)!!
        assertThat(m.order).containsExactly(HomeSection.ASK, HomeSection.TALK).inOrder()
        assertThat(m.ask).isEqualTo(HomeModel.Ask("Ask a question…", "Ask Ava a question", "Ava"))
        assertThat(m.talk.starters).containsExactly("Pricing?", "Do you integrate with Shopify?", "Refunds").inOrder()
        assertThat(m.talk.title).isEqualTo("Ava")
        assertThat(Home.model(HOME + ("homeAskBox" to false), agent = AGENT)!!.ask).isNull()
    }

    @Test
    fun `the help centre - ask box over articles, recent list, topics and See all, drafts never`() {
        val c = HOME + ("enableKnowledgeBase" to true)
        val m = Home.model(c, categories = CATS, recentArticles = RECENT)!!
        assertThat(m.order).containsExactly(HomeSection.ASK, HomeSection.TALK, HomeSection.HELP).inOrder()
        assertThat(m.ask!!.placeholder).isEqualTo("Search for help")
        assertThat(m.help!!.title).isEqualTo("Recent articles")
        assertThat(m.help!!.seeAll).isTrue()
        assertThat(m.help!!.search).isFalse()
        assertThat(m.help!!.recent.map { it.id }).containsExactly("2")
        assertThat(m.help!!.topics).containsExactly(HomeModel.Topic("c1", "Billing", 2), HomeModel.Topic("c2", "Getting started", 1)).inOrder()
        assertThat(m.articles.map { it.id }).containsExactly("3", "4", "1", "2").inOrder()
    }

    @Test
    fun `recent articles hidden - the card is the help centre, the ask box off - the card searches`() {
        val c = HOME + mapOf("enableKnowledgeBase" to true, "hideRecentArticles" to true, "homeAskBox" to false)
        val m = Home.model(c, categories = CATS, recentArticles = RECENT)!!
        assertThat(m.order).containsExactly(HomeSection.TALK, HomeSection.HELP).inOrder()
        assertThat(m.help!!.title).isEqualTo("Help centre")
        assertThat(m.help!!.recent).isEmpty()
        assertThat(m.help!!.search).isTrue()
    }

    @Test
    fun `a help centre with no published article counts as off`() {
        val m = Home.model(HOME + ("enableKnowledgeBase" to true), categories = emptyList(), recentArticles = listOf(RECENT[1]))!!
        assertThat(m.help).isNull()
        assertThat(m.ask).isNull()
        assertThat(m.articles).isEmpty()
    }

    @Test
    fun `welcome back, and pick up where they left off for an agent bot`() {
        val memory = VisitorMemory(at = NOW - Home.LIVE_CHAT_MS, name = "Jamie", topic = "Do you support Shopify stores?", session = "s1")
        val m = Home.model(HOME, agent = AGENT, memory = memory, chatSessionId = "s1", now = NOW)!!
        assertThat(m.hello).isEqualTo("Welcome back, Jamie")
        assertThat(m.order).containsExactly(HomeSection.ASK, HomeSection.RESUME, HomeSection.TALK).inOrder()
        assertThat(m.resume).isEqualTo(HomeModel.Resume("Do you support Shopify stores?", "Continue the conversation", true))
        assertThat(Home.model(HOME, agent = AGENT, memory = memory, chatSessionId = "s2", now = NOW)!!.resume!!.line).isEqualTo("Ask it again - we'll answer right away")
        // A flow bot: welcomed back, nothing to pick up.
        val flow = Home.model(HOME, memory = memory, now = NOW)!!
        assertThat(flow.hello).isEqualTo("Welcome back, Jamie")
        assertThat(flow.resume).isNull()
    }

    @Test
    fun `your requests - two rows and See all (n), below Talk to us, above it when a reply is new`() {
        fun req(id: String, lastAt: Long, lastFrom: String) = RequestSummary(id = id, title = "Request $id", status = "open", lastFrom = lastFrom, lastAt = iso(lastAt), teamName = "Ada")
        val requests = listOf(req("a", NOW - 3000, "you"), req("b", NOW - 2000, "you"), req("c", NOW - 1000, "you"))
        val m = Home.model(HOME, requests = requests, now = NOW)!!
        assertThat(m.order).containsExactly(HomeSection.TALK, HomeSection.REQUESTS).inOrder()
        assertThat(m.requests!!.rows.map { it.id }).containsExactly("c", "b").inOrder()
        assertThat(m.requests!!.seeAll).isEqualTo("See all (3)")
        assertThat(m.requests!!.fresh).isFalse()
        val replied = listOf(req("a", NOW - 3000, "team"), req("b", NOW - 2000, "you"))
        val n = Home.model(HOME, requests = replied, now = NOW)!!
        assertThat(n.order).containsExactly(HomeSection.REQUESTS, HomeSection.TALK).inOrder()
        assertThat(n.requests!!.rows[0].id).isEqualTo("a")
        assertThat(n.requests!!.rows[0].fresh).isTrue()
        assertThat(n.requests!!.rows[0].state).isEqualTo(RequestState.REPLIED)
        assertThat(n.requests!!.seeAll).isNull()
        // Read here since: no longer new.
        assertThat(Home.model(HOME, requests = replied, seen = mapOf("a" to NOW), now = NOW)!!.order).containsExactly(HomeSection.TALK, HomeSection.REQUESTS).inOrder()
        // Nothing at all for a first-time visitor.
        assertThat(Home.model(HOME, requests = null)!!.requests).isNull()
        assertThat(Home.model(HOME, requests = emptyList())!!.requests).isNull()
    }

    @Test
    fun `trust - faces and the rating on the card, the logos with their line last`() {
        val c = HOME + mapOf(
            "homeTeamPhotos" to listOf("https://cdn.x/1.png", "https://cdn.x/2.png"),
            "homeRating" to mapOf("average" to 4.83, "count" to 120),
            "homeTrustLogos" to listOf("https://cdn.x/l.png"),
            "homeTrustLabel" to " Trusted by 2,000+ teams "
        )
        val m = Home.model(c)!!
        assertThat(m.talk.faces).containsExactly("https://cdn.x/1.png", "https://cdn.x/2.png").inOrder()
        assertThat(m.talk.rating!!.average).isEqualTo("4.8")
        assertThat(m.trust).isEqualTo(HomeModel.Trust("Trusted by 2,000+ teams", listOf("https://cdn.x/l.png")))
        assertThat(m.order).containsExactly(HomeSection.TALK, HomeSection.TRUST).inOrder()
        // A label with no logo is a claim with no proof: nothing.
        assertThat(Home.model(HOME + ("homeTrustLabel" to "Trusted"))!!.trust).isNull()
    }

    @Test
    fun `the band is the launcher colours, light ones take dark ink`() {
        val g = HOME + mapOf("widgetIconThemeType" to "Gradient", "widgetGradientBgOne" to "#0808EE", "widgetGradientBgTwo" to "#6366F1")
        assertThat(Home.model(g)!!.band).isEqualTo("#0808EE" to "#6366F1")
        val light = Home.model(HOME + ("widgetIconBgColor" to "#ffffff"))!!
        assertThat(light.light).isTrue()
        assertThat(light.accent).isEqualTo("#4F46E5")
    }

    @Test
    fun `while the visitor types, the list is the page`() {
        assertThat(Home.HIDDEN_WHILE_ASKING).containsExactly(HomeSection.RESUME, HomeSection.REQUESTS, HomeSection.TALK, HomeSection.HELP).inOrder()
    }

    @Test
    fun `carries the owner's mascot for an app's own spot (the web's home draws none)`() {
        assertThat(Home.model(HOME)!!.mascot.look).isEqualTo(MascotArt.Look("blob", "round", "none", "", ""))
        assertThat(Home.model(HOME + mapOf("mascotShape" to "owl", "mascotName" to "Hoot", "widgetIconBgColor" to "#123456"))!!.mascot)
            .isEqualTo(HomeMascot(MascotArt.Look("owl", "round", "none", "", "Hoot"), "#123456"))
    }
}
