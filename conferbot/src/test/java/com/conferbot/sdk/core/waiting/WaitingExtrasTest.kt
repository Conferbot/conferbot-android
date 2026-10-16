package com.conferbot.sdk.core.waiting

import com.conferbot.sdk.core.livechat.HandoverFlow
import com.conferbot.sdk.core.livechat.HandoverHost
import com.conferbot.sdk.core.livechat.HandoverLifecycle
import com.conferbot.sdk.core.livechat.HandoverSignals
import com.conferbot.sdk.core.livechat.HandoverSocket
import com.conferbot.sdk.core.livechat.HandoverWaitingTest
import com.conferbot.sdk.core.livechat.WaitHooks
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.RecordEntry
import com.conferbot.sdk.models.Agent
import com.conferbot.sdk.models.RecordItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Date

/**
 * While you wait, end to end: [HandoverLifecycle] says where a wait for a
 * person begins and ends and sends the signals, [WaitingExtras] draws the
 * help card (6s in), the games (2.5s after the waiting signal) and the
 * server's queue-help pick with "That solved it", against a fake socket on a
 * fake clock. The cases of the React Native SDK's
 * __tests__/context/useWaitingExtras.test.ts.
 */
class WaitingExtrasTest {

    private class FakeSocket(val answers: Map<String, Map<String, Any?>?> = emptyMap()) : HandoverSocket {
        val listeners = mutableMapOf<String, MutableList<(Map<String, Any?>?) -> Unit>>()
        val asked = mutableListOf<Triple<String, Map<String, Any?>, Long>>()
        override fun on(event: String, fn: (Map<String, Any?>?) -> Unit) { listeners.getOrPut(event) { mutableListOf() } += fn }
        override fun emit(event: String, data: Map<String, Any?>) {}
        override fun ask(event: String, data: Map<String, Any?>, timeoutMs: Long, onAnswer: (Map<String, Any?>?) -> Unit) {
            asked += Triple(event, data, timeoutMs)
            onAnswer(answers[event])
        }
        override fun joinRoom(chatSessionId: String) {}
        fun fire(event: String, data: Map<String, Any?>? = null) = listeners[event].orEmpty().forEach { it(data) }
    }

    private class FakeHost : HandoverHost {
        var screen = listOf<RecordItem>(RecordItem.UserMessage(id = "u1", time = Date(0), text = "Where can I download my invoices?"))
        val saved = linkedMapOf<String, RecordEntry>()
        override fun items() = screen
        override fun updateItems(transform: (List<RecordItem>) -> List<RecordItem>) { screen = transform(screen) }
        override fun addItem(item: RecordItem) { screen = screen + item }
        override fun record() = saved.values.toList()
        override fun saveEntry(entry: RecordEntry) { saved[entry.id] = entry }
        override fun sessionId() = "s1"
        override fun chatOpen() = true
        override fun flowWaiting() = false
        override fun setLiveMode(on: Boolean) {}
        override fun setAgent(agent: Agent?) {}
    }

    private val kb = mapOf<String, Any?>(
        "categories" to listOf(mapOf("name" to "Billing", "articles" to listOf(mapOf("_id" to "a1", "title" to "Downloading invoices", "description" to "Find every invoice in one place.")))),
        "recentArticles" to emptyList<Any?>()
    )

    private inner class Setup(answers: Map<String, Map<String, Any?>?> = emptyMap(), val customizations: Map<String, Any?> = mapOf("enableKnowledgeBase" to true)) {
        val clock = HandoverWaitingTest.FakeClock()
        val socket = FakeSocket(answers)
        val host = FakeHost()
        lateinit var waiting: WaitingExtras
        val handover = HandoverLifecycle(
            { socket }, { null as HandoverFlow? }, host, clock,
            object : WaitHooks {
                override fun start() = waiting.startWait()
                override fun end() = waiting.endWait()
            }
        ) { Date(1_000) }

        init {
            waiting = WaitingExtras(
                socket = { socket },
                sessionId = { "s1" },
                customizations = { customizations },
                record = { listOf(mapOf("type" to "user-message", "text" to "Where can I download my invoices?")) },
                joined = { handover.phase() == com.conferbot.sdk.core.livechat.HandoverPhase.JOINED },
                lastItemId = { host.screen.lastOrNull()?.id },
                leftQueueHelped = { handover.leftQueueHelped() },
                botId = { "bot1" },
                timer = clock,
                now = { clock.now }
            )
            listOf(HandoverSignals.WAITING_FOR_HUMAN_EVENT, HandoverSignals.HUMAN_JOINED_EVENT, HandoverSignals.WAIT_OVER_EVENT)
                .forEach { name -> handover.onWidgetSignal(name) { waiting.signal(it) } }
            handover.setupListeners()
            waiting.setupListeners()
            waiting.setKnowledgeBase(kb)
        }

        fun escalate() = handover.onAgentLeft(mapOf("reason" to "escalated", "agentDetails" to mapOf("name" to "Ava", "isAIAgent" to true)))
        val help get() = waiting.help.value
        val games get() = waiting.games.value
    }

    @Test
    fun `an AI passes them on - 6s in, the articles for their question, after the conversation so far`() {
        val s = Setup()
        s.escalate()
        s.clock.tick(5999)
        assertThat(s.help).isNull()
        s.clock.tick(1)
        assertThat(s.help!!.card.solvable).isFalse()
        assertThat(s.help!!.card.items.map { it.id to it.title }).containsExactly("a1" to "Downloading invoices")
        // After the Connecting line, the last item on screen when it came.
        assertThat(s.help!!.after).isEqualTo(s.host.screen.last().id)
        assertThat(s.host.screen.last()).isInstanceOf(RecordItem.AgentLeftMessage::class.java)
    }

    @Test
    fun `20s in the waiting signal, 2,5s later the games - a person joining folds the unused offer away`() {
        val s = Setup()
        s.escalate()
        s.clock.tick(20_000)
        assertThat(s.games).isNull()
        s.clock.tick(2500)
        assertThat(s.games!!.card.state.view).isEqualTo("menu")
        assertThat(s.games!!.card.state.title).isEqualTo("While you wait - fancy a quick game? 🎮")
        s.handover.onAccepted(mapOf("agentDetails" to mapOf("_id" to "p1", "name" to "Sam")))
        assertThat(s.games).isNull()
        // The help card stays, as history.
        assertThat(s.help!!.card.settled).isTrue()
    }

    @Test
    fun `a game under way when a person joins - saved and folded away, Finish my game`() {
        val s = Setup()
        s.escalate()
        s.clock.tick(22_500)
        s.games!!.card.open("ttt")
        s.handover.onAccepted(mapOf("agentDetails" to mapOf("_id" to "p1", "name" to "Sam")))
        assertThat(s.games!!.card.state.view).isEqualTo("paused")
        assertThat(s.games!!.card.state.title).isEqualTo("Sam's here!")
    }

    @Test
    fun `the games off - no offer - typing play is sent as words`() {
        val s = Setup(customizations = mapOf("enableKnowledgeBase" to true, "waitingGames" to mapOf("enabled" to false)))
        s.escalate()
        s.clock.tick(30_000)
        assertThat(s.games).isNull()
        assertThat(s.waiting.interceptPlay("play")).isFalse()
    }

    @Test
    fun `typing play while waiting opens the menu`() {
        val s = Setup()
        s.escalate()
        assertThat(s.waiting.interceptPlay("play")).isFalse()
        s.clock.tick(20_000)
        assertThat(s.waiting.interceptPlay("let's play")).isTrue()
        assertThat(s.games!!.card.state.title).isEqualTo("Pick a game")
    }

    @Test
    fun `the server's queue-help - That solved it leaves the queue, the article named`() {
        val s = Setup(answers = mapOf("queue-self-resolve" to mapOf("ok" to true)))
        s.escalate()
        s.socket.fire("queue-help", mapOf("chatSessionId" to "s1", "items" to listOf(mapOf("kind" to "article", "id" to "k9", "title" to "Invoices")), "offer" to 1))
        assertThat(s.help!!.card.solvable).isTrue()
        assertThat(s.help!!.card.items.map { it.id }).containsExactly("k9")
        var ok = false
        s.waiting.solveWait { ok = it }
        assertThat(ok).isTrue()
        assertThat(s.socket.asked).contains(Triple("queue-self-resolve", mapOf<String, Any?>("chatSessionId" to "s1", "articleId" to "k9"), 8000L))
        assertThat(s.help!!.card.done).isTrue()
        assertThat(s.help!!.card.settled).isTrue()
        val line = s.host.screen.filterIsInstance<RecordItem.SystemMessage>().first { it.kind == "queue-self-resolved" }
        assertThat(line.text).isEqualTo("You left the queue. Glad that helped.")
        assertThat(s.host.saved.values.first { it.type == "queue-self-resolved" }.text).isEqualTo("Resolved while waiting")
        // The Connecting line settled as left.
        assertThat(s.host.screen.filterIsInstance<RecordItem.AgentLeftMessage>().single().settled).isEqualTo("cancelled")
        // The room's queue-left for the same leave changes nothing.
        s.socket.fire("queue-left", mapOf("chatSessionId" to "s1", "reason" to "self_service"))
        assertThat(s.host.screen.count { it is RecordItem.SystemMessage && it.kind == "queue-self-resolved" }).isEqualTo(1)
    }

    @Test
    fun `That solved it refused - the button comes back, nothing recorded`() {
        val s = Setup(answers = mapOf("queue-self-resolve" to mapOf("ok" to false, "reason" to "agent_joined")))
        s.escalate()
        s.socket.fire("queue-help", mapOf("items" to listOf(mapOf("id" to "k9", "title" to "Invoices"))))
        s.waiting.solveWait()
        assertThat(s.help!!.card.busy).isFalse()
        assertThat(s.help!!.card.done).isFalse()
        assertThat(s.help!!.card.solvable).isTrue()
        assertThat(s.host.screen.any { it is RecordItem.SystemMessage && it.kind == "queue-self-resolved" }).isFalse()
    }

    @Test
    fun `a waiting queue-status - the server picks the help, ours stays out`() {
        val s = Setup()
        s.escalate()
        s.waiting.onQueueStatus(mapOf("chatSessionId" to "s1", "status" to "waiting", "position" to 2))
        s.clock.tick(6000)
        assertThat(s.help).isNull()
        s.waiting.onQueueStatus(mapOf("chatSessionId" to "other", "status" to "waiting", "help" to mapOf("items" to listOf(mapOf("id" to "x", "title" to "X")))))
        assertThat(s.help).isNull()
    }

    @Test
    fun `a help article is found in the help centre data by id`() {
        val s = Setup()
        assertThat(s.waiting.findArticle("a1")?.get("title")).isEqualTo("Downloading invoices")
        assertThat(s.waiting.findArticle("zz")).isNull()
    }

    @Test
    fun `the chat ending before 6s - no card, no games`() {
        val s = Setup()
        s.escalate()
        s.socket.fire("chat-ended", mapOf("chatSessionId" to "s1"))
        s.clock.tick(30_000)
        assertThat(s.help).isNull()
        assertThat(s.games).isNull()
    }

    @Test
    fun `a flow handover with no AI agents starts the wait - with AI agents it does not`() {
        val a = Setup()
        a.handover.flowWaitStarted(mapOf("assignedAIAgents" to listOf("ai-1")))
        a.clock.tick(30_000)
        assertThat(a.help).isNull()
        val b = Setup()
        b.handover.flowWaitStarted(emptyMap())
        b.clock.tick(6000)
        assertThat(b.help).isNotNull()
    }

    @Test
    fun `a chat that cannot draw the cards - no games, play is sent as words`() {
        val clock = HandoverWaitingTest.FakeClock()
        val w = WaitingExtras(
            socket = { null }, sessionId = { "s1" }, customizations = { emptyMap() }, record = { emptyList() },
            joined = { false }, lastItemId = { null }, leftQueueHelped = {}, canDraw = { false }, timer = clock
        )
        w.signal(com.conferbot.sdk.core.livechat.WidgetSignal.WaitingForHuman(false, "busy", null, false))
        clock.tick(30_000)
        assertThat(w.games.value).isNull()
        assertThat(w.interceptPlay("play")).isFalse()
    }

    @Test
    fun `a new conversation - the cards go`() {
        val s = Setup()
        s.escalate()
        s.clock.tick(22_500)
        assertThat(s.help).isNotNull()
        assertThat(s.games).isNotNull()
        s.waiting.reset()
        assertThat(s.help).isNull()
        assertThat(s.games).isNull()
    }

    private val kbReply = mapOf<String, Any?>(
        "success" to true, "articleId" to "a9",
        "article" to mapOf("_id" to "a9", "title" to "Refunds", "content" to "<p>Within 7 days.</p>", "status" to "published")
    )

    @Test
    fun `a wait's article the help centre cannot open - fetched by id and shown on its own (#75)`() {
        val s = Setup(mapOf("get-kb-article" to kbReply), customizations = mapOf("enableKnowledgeBase" to false))
        s.waiting.openArticleById("a9", "Refunds")
        assertThat(s.socket.asked.single().first).isEqualTo("get-kb-article")
        assertThat(s.socket.asked.single().second).isEqualTo(mapOf("chatbotId" to "bot1", "articleId" to "a9"))
        val a = s.waiting.article.value!!
        assertThat(a.failed).isFalse()
        assertThat(a.article!!.title).isEqualTo("Refunds")
        assertThat(a.article!!.content).isEqualTo("<p>Within 7 days.</p>")
        s.waiting.closeArticle()
        assertThat(s.waiting.article.value).isNull()
    }

    @Test
    fun `not found or offline - it says so, and Try again asks again (#75)`() {
        val s = Setup(mapOf("get-kb-article" to mapOf("success" to false, "error" to "Article not found")))
        s.waiting.openArticleById("a9", "Refunds")
        assertThat(s.waiting.article.value).isEqualTo(WaitArticleView("a9", "Refunds", failed = true))
        s.waiting.retryArticle()
        assertThat(s.socket.asked.count { it.first == "get-kb-article" }).isEqualTo(2)
        // No socket at all: failed at once, nothing asked.
        val clock = HandoverWaitingTest.FakeClock()
        val w = WaitingExtras(
            socket = { null }, sessionId = { "s1" }, customizations = { emptyMap() }, record = { emptyList() },
            joined = { false }, lastItemId = { null }, leftQueueHelped = {}, timer = clock
        )
        w.openArticleById("a9")
        assertThat(w.article.value!!.failed).isTrue()
        w.openArticleById(" ")
        assertThat(w.article.value!!.id).isEqualTo("a9")
    }

    @Test
    fun `an answer for an article closed meanwhile is dropped, and a new conversation closes it (#75)`() {
        val held = mutableListOf<() -> Unit>()
        val socket = object : HandoverSocket {
            override fun on(event: String, fn: (Map<String, Any?>?) -> Unit) {}
            override fun emit(event: String, data: Map<String, Any?>) {}
            override fun ask(event: String, data: Map<String, Any?>, timeoutMs: Long, onAnswer: (Map<String, Any?>?) -> Unit) { held += { onAnswer(kbReply) } }
            override fun joinRoom(chatSessionId: String) {}
        }
        val clock = HandoverWaitingTest.FakeClock()
        val w = WaitingExtras(
            socket = { socket }, sessionId = { "s1" }, customizations = { emptyMap() }, record = { emptyList() },
            joined = { false }, lastItemId = { null }, leftQueueHelped = {}, timer = clock
        )
        w.openArticleById("a9", "Refunds")
        assertThat(w.article.value).isEqualTo(WaitArticleView("a9", "Refunds"))
        w.closeArticle()
        held.removeAt(0)()
        assertThat(w.article.value).isNull()

        w.openArticleById("a9", "Refunds")
        w.reset()
        held.removeAt(0)()
        assertThat(w.article.value).isNull()
    }
}
