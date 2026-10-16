package com.conferbot.sdk.core.home

import com.conferbot.sdk.core.livechat.SignalTimer
import com.conferbot.sdk.core.requests.RequestsSocket
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The home screen in the SDK ([HomeExtras]; RN context/hooks/useHomeScreen.ts,
 * web conferbot-widget src/ts/home/firstImpression.ts goToChat,
 * visitorMemory.ts, knowledgeBase/kbContent.ts remoteSearch): the view, what
 * the device remembers, the question asked from the home. The cases of the
 * React Native SDK's __tests__/context/useHomeScreen.test.ts, on a fake
 * clock, store and socket.
 */
class HomeExtrasTest {

    private class FakeTimer : SignalTimer {
        var clock = 0L
        private val due = mutableListOf<Triple<Long, Any, () -> Unit>>()
        override fun set(ms: Long, fn: () -> Unit): Any = Any().also { due += Triple(clock + ms, it, fn) }
        override fun clear(handle: Any) { due.removeAll { it.second === handle } }
        fun advance(ms: Long) {
            clock += ms
            due.filter { it.first <= clock }.forEach { d -> due.remove(d); d.third() }
        }
    }

    private class FakeStore(val data: MutableMap<String, String> = mutableMapOf()) : HomeStore {
        override fun read(key: String) = data[key]
        override fun write(key: String, value: String) { data[key] = value }
    }

    private class FakeSocket(val answer: Map<String, Any?>?) : RequestsSocket {
        val asked = mutableListOf<Triple<String, Map<String, Any?>, Long>>()
        override fun ask(event: String, data: Map<String, Any?>, timeoutMs: Long, onAnswer: (Map<String, Any?>?) -> Unit) {
            asked += Triple(event, data, timeoutMs)
            onAnswer(answer)
        }
        override fun emit(event: String, data: Map<String, Any?>) {}
        override fun on(event: String, fn: (Map<String, Any?>?) -> Unit): () -> Unit = {}
    }

    private val KEY = "conferbot_memory_bot1"
    private val HOME = mapOf<String, Any?>("enableInitialPage" to true)

    private inner class Setup(val store: FakeStore? = null, val socket: FakeSocket? = null) {
        var c: Map<String, Any?> = HOME
        var session: String? = "s1"
        val timer = FakeTimer()
        val sent = mutableListOf<String>()
        var homeShown = 0
        val home = HomeExtras(
            botId = { "bot1" },
            customizations = { c },
            chatSessionId = { session },
            store = { store },
            socket = { socket },
            connected = { true },
            send = { sent += it },
            onHomeShown = { homeShown++ },
            timer = timer,
            now = { System.currentTimeMillis() }
        )
    }

    @Test
    fun `opens on the home screen only while the owner has one`() {
        val t = Setup()
        assertThat(t.home.view()).isEqualTo(HomeView.HOME)
        t.home.showChat()
        assertThat(t.home.view()).isEqualTo(HomeView.CHAT)
        t.home.showHome()
        assertThat(t.home.view()).isEqualTo(HomeView.HOME)
        // Turned off: the conversation, and Back has nowhere to go.
        t.c = mapOf("enableInitialPage" to false)
        assertThat(t.home.view()).isEqualTo(HomeView.CHAT)
        t.home.showHome()
        assertThat(t.home.view()).isEqualTo(HomeView.CHAT)
        assertThat(t.home.back()).isFalse()
    }

    @Test
    fun `the help centre on a topic, and back to the home when it is off`() {
        val t = Setup()
        t.home.showHelp("c1")
        assertThat(t.home.view()).isEqualTo(HomeView.HOME)
        assertThat(t.home.helpCategoryId.value).isEqualTo("c1")
        t.c = HOME + ("enableKnowledgeBase" to true)
        assertThat(t.home.view()).isEqualTo(HomeView.HELP)
    }

    @Test
    fun `back - the help centre to where it came from, the conversation to the home`() {
        val t = Setup()
        t.c = HOME + ("enableKnowledgeBase" to true)
        t.home.showHelp()
        assertThat(t.home.back()).isTrue()
        assertThat(t.home.view()).isEqualTo(HomeView.HOME)
        // An article opened from the conversation goes back to it.
        t.home.showChat()
        t.home.showHelp(from = HomeView.CHAT)
        assertThat(t.home.back()).isTrue()
        assertThat(t.home.view()).isEqualTo(HomeView.CHAT)
        assertThat(t.home.back()).isTrue()
        assertThat(t.home.view()).isEqualTo(HomeView.HOME)
        assertThat(t.home.back()).isFalse()
    }

    @Test
    fun `a question from the home is asked in the chat, after the chat is there`() {
        val t = Setup()
        t.home.showChat("  Do you ship abroad?  ")
        assertThat(t.home.view()).isEqualTo(HomeView.CHAT)
        assertThat(t.sent).isEmpty()
        t.timer.advance(HomeExtras.ASK_AFTER_MS)
        assertThat(t.sent).containsExactly("Do you ship abroad?")
        // Gone with a new session before it was asked: not asked.
        t.home.showChat("Another one")
        t.home.reset()
        t.timer.advance(HomeExtras.ASK_AFTER_MS)
        assertThat(t.sent).containsExactly("Do you ship abroad?")
    }

    @Test
    fun `the home coming into view reads the requests again`() {
        val t = Setup()
        t.home.homeShown()
        t.home.homeShown()
        assertThat(t.homeShown).isEqualTo(2)
    }

    @Test
    fun `remembers the first real question and the name on the device, per bot`() {
        val store = FakeStore()
        val t = Setup(store = store)
        t.home.load()
        t.home.rememberText("hi")
        t.home.rememberText("Do you support Shopify stores?")
        assertThat(t.home.memory.value!!.topic).isEqualTo("Do you support Shopify stores?")
        assertThat(t.home.memory.value!!.session).isEqualTo("s1")
        t.home.rememberName("jamie lee")
        assertThat(t.home.memory.value!!.name).isEqualTo("Jamie")
        val kept = Home.parseMemory(store.data[KEY])!!
        assertThat(kept.name).isEqualTo("Jamie")
        assertThat(kept.topic).isEqualTo("Do you support Shopify stores?")
        // Not a name: nothing changes.
        t.home.rememberName("x@y.com")
        assertThat(t.home.memory.value!!.name).isEqualTo("Jamie")
    }

    @Test
    fun `what the device kept is read back`() {
        val at = System.currentTimeMillis() - 3_600_000L
        val store = FakeStore(mutableMapOf(KEY to Home.serializeMemory(VisitorMemory(at, "Jamie", "Pricing for teams please", "old"))))
        val t = Setup(store = store)
        t.home.load()
        assertThat(t.home.memory.value).isEqualTo(VisitorMemory(at, "Jamie", "Pricing for teams please", "old"))
    }

    @Test
    fun `keeps the help centre from the bot data`() {
        val t = Setup()
        t.home.setKnowledgeBase(mapOf("categories" to listOf(mapOf("_id" to "c1", "articles" to listOf(mapOf("_id" to "a1")))), "recentArticles" to "nope"))
        assertThat(t.home.knowledgeBase.value.categories).hasSize(1)
        assertThat(t.home.knowledgeBase.value.recentArticles).isEmpty()
        assertThat(t.home.knowledgeBase.value.rawArticles).containsExactly(mapOf("_id" to "a1"))
    }

    @Test
    fun `asks the server for matches inside article text, once per query that answered`() {
        val socket = FakeSocket(mapOf("success" to true, "articles" to listOf(mapOf("_id" to "a1", "snippet" to "VAT number"), mapOf("snippet" to "no id"))))
        val t = Setup(socket = socket)
        var hits: List<Pair<String, String>>? = null
        t.home.searchArticleText("VAT number") { hits = it }
        assertThat(hits).containsExactly("a1" to "VAT number")
        assertThat(socket.asked).containsExactly(Triple("search-kb-articles", mapOf("chatbotId" to "bot1", "query" to "vat number", "limit" to 10), 5000L))
        t.home.searchArticleText("vat number") { hits = it }
        assertThat(socket.asked).hasSize(1)
        t.home.searchArticleText("x") { hits = it }
        assertThat(hits).isEmpty()
    }

    @Test
    fun `a failed search is not kept`() {
        val socket = FakeSocket(null)
        val t = Setup(socket = socket)
        var hits: List<Pair<String, String>>? = null
        t.home.searchArticleText("vat number") { hits = it }
        t.home.searchArticleText("vat number") { hits = it }
        assertThat(hits).isEmpty()
        assertThat(socket.asked).hasSize(2)
    }
}
