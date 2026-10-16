package com.conferbot.sdk.core.requests

import com.conferbot.sdk.core.flow.FlowCalendar
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

/**
 * "Your requests" ([Requests]): the web's words.ts, store.ts and api.ts
 * (conferbot-widget src/ts/home/requests/), against the payloads of
 * embed-server socket/handlers/visitorRequestsHandler.js. The cases of the
 * React Native SDK's __tests__/core/requests.test.ts.
 */
class RequestsTest {

    private fun local(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(y, mo, d, h, mi, 0) }.timeInMillis

    /** Fri 16 Oct 2026, 15:00 on the phone. */
    private val now = local(2026, 9, 16, 15)

    private fun req(
        id: String = "mobile_1_abcdef",
        status: String = "open",
        lastAt: String? = FlowCalendar.iso(now - 2 * 3_600_000L),
        lastFrom: String = "team",
        teamName: String = "Ada"
    ) = RequestSummary(id = id, title = "My invoice is wrong", status = status, lastAt = lastAt, lastFrom = lastFrom, lastText = "Fixed it", teamName = teamName)

    // ---------------------------------------------------------- the words (web words.ts)

    @Test
    fun `where it stands - replied, waiting, solved`() {
        assertThat(Requests.stateOf(req())).isEqualTo(RequestState.REPLIED)
        assertThat(Requests.stateOf(req(lastFrom = "you"))).isEqualTo(RequestState.WAITING)
        assertThat(Requests.stateOf(req(status = "solved"))).isEqualTo(RequestState.SOLVED)
        assertThat(Requests.STATE_WORDS[RequestState.REPLIED]!!.label).isEqualTo("We replied")
        assertThat(Requests.STATE_WORDS[RequestState.WAITING]!!.label).isEqualTo("We're on it")
        assertThat(Requests.STATE_WORDS[RequestState.SOLVED]!!.detail).isEqualTo("Still need help with this? Write below and we'll pick it up again.")
    }

    @Test
    fun `a new reply - from the team, on an open request, after it was last read here`() {
        val r = req()
        assertThat(Requests.isNewReply(r, emptyMap())).isTrue()
        assertThat(Requests.isNewReply(r, mapOf(r.id to now))).isFalse()
        assertThat(Requests.isNewReply(req(status = "solved"), emptyMap())).isFalse()
        assertThat(Requests.isNewReply(req(lastFrom = "you"), emptyMap())).isFalse()
        assertThat(Requests.unreadCount(listOf(r, req(id = "other_123", lastFrom = "you")), emptyMap())).isEqualTo(1)
        assertThat(Requests.unreadCount(null, emptyMap())).isEqualTo(0)
    }

    @Test
    fun `when, in words`() {
        assertThat(Requests.whenWords(now - 20_000L, now)).isEqualTo("Just now")
        assertThat(Requests.whenWords(now - 5 * 60_000L, now)).isEqualTo("5 min ago")
        assertThat(Requests.whenWords(now - 70 * 60_000L, now)).isEqualTo("1 hour ago")
        assertThat(Requests.whenWords(now - 3 * 3_600_000L, now)).isEqualTo("3 hours ago")
        assertThat(Requests.whenWords(local(2026, 9, 15, 20), now)).isEqualTo("Yesterday")
        assertThat(Requests.whenWords(local(2026, 9, 12, 9), now)).isEqualTo("Monday")
        assertThat(Requests.whenWords(local(2026, 9, 3, 9), now)).isEqualTo("3 Oct")
        assertThat(Requests.whenWords(local(2025, 11, 24, 9), now)).isEqualTo("24 Dec 2025")
        assertThat(Requests.whenWords(FlowCalendar.iso(now - 5 * 60_000L), now)).isEqualTo("5 min ago")
        assertThat(Requests.whenWords(null, now)).isEqualTo("")
        assertThat(Requests.whenWords("nonsense", now)).isEqualTo("")
    }

    @Test
    fun `a row's line - New reply from the person, else where it stands`() {
        assertThat(Requests.rowLine(req(), emptyMap(), now)).isEqualTo("New reply from Ada · 2 hours ago")
        assertThat(Requests.rowLine(req(teamName = ""), emptyMap(), now)).isEqualTo("New reply · 2 hours ago")
        assertThat(Requests.rowLine(req(), mapOf("mobile_1_abcdef" to now), now)).isEqualTo("We replied · 2 hours ago")
        assertThat(Requests.rowLine(req(lastFrom = "you", lastAt = null), emptyMap(), now)).isEqualTo("We're on it")
    }

    @Test
    fun `newest replies first, then whatever moved last`() {
        val a = req(id = "aaaaaa", lastFrom = "you", lastAt = FlowCalendar.iso(now - 1000))
        val b = req(id = "bbbbbb", lastAt = FlowCalendar.iso(now - 9000))
        val c = req(id = "cccccc", lastFrom = "you", lastAt = FlowCalendar.iso(now - 5000))
        assertThat(Requests.ordered(listOf(a, b, c), emptyMap()).map { it.id }).containsExactly("bbbbbb", "aaaaaa", "cccccc").inOrder()
        assertThat(Requests.ordered(listOf(a, b, c), mapOf("bbbbbb" to now)).map { it.id }).containsExactly("aaaaaa", "cccccc", "bbbbbb").inOrder()
    }

    @Test
    fun `read up to the newest message, by the server's clock when it runs ahead`() {
        assertThat(Requests.seenAt(FlowCalendar.iso(now + 60_000L), now)).isEqualTo(now + 60_000L)
        assertThat(Requests.seenAt(null, now)).isEqualTo(now)
    }

    @Test
    fun `links in a message are their own runs (web appendText)`() {
        assertThat(Requests.linkRuns("See https://example.com/a?b=1. Thanks")).containsExactly(
            Requests.Run("See "),
            Requests.Run("https://example.com/a?b=1", "https://example.com/a?b=1"),
            Requests.Run(". Thanks")
        ).inOrder()
        assertThat(Requests.linkRuns("HTTP://X.CO/y)")).containsExactly(Requests.Run("HTTP://X.CO/y", "HTTP://X.CO/y"), Requests.Run(")")).inOrder()
        assertThat(Requests.linkRuns("no links")).containsExactly(Requests.Run("no links"))
        assertThat(Requests.linkRuns("")).isEmpty()
    }

    @Test
    fun `the menu item says when a reply is new`() {
        assertThat(Requests.menu(0)).isEqualTo("Your requests")
        assertThat(Requests.menu(2)).isEqualTo("Your requests · New reply")
    }

    // ---------------------------------------------------------- what the device remembers (web store.ts)

    @Test
    fun `reads only clean ids and numbers`() {
        val m = Requests.parseMemory("""{"sessions":["mobile_1_abcdef","bad id!",42,"x"],"seen":{"mobile_1_abcdef":5,"no way":3,"other_123":"x"}}""")
        assertThat(m).isEqualTo(RequestMemory(listOf("mobile_1_abcdef"), mapOf("mobile_1_abcdef" to 5L)))
        assertThat(Requests.parseMemory(null)).isEqualTo(RequestMemory())
        assertThat(Requests.parseMemory("{nope")).isEqualTo(RequestMemory())
        assertThat(Requests.parseMemory("[1,2]")).isEqualTo(RequestMemory())
        assertThat(Requests.storageKey("bot1")).isEqualTo("conferbot_requests_bot1")
    }

    @Test
    fun `a chat goes first, once - 30 kept`() {
        var m = RequestMemory()
        for (i in 0 until 35) m = Requests.withSession(m, "mobile_${i}_abcdef") ?: m
        assertThat(m.sessions).hasSize(30)
        assertThat(m.sessions[0]).isEqualTo("mobile_34_abcdef")
        assertThat(Requests.withSession(m, "mobile_34_abcdef")).isNull()
        assertThat(Requests.withSession(m, "short")).isNull()
        assertThat(Requests.withSession(m, "mobile_3_abcdef")!!.sessions[0]).isEqualTo("mobile_3_abcdef")
    }

    @Test
    fun `read marks only move forward - the newest 60 are kept`() {
        var m = Requests.withSeen(RequestMemory(), "mobile_1_abcdef", 10)!!
        assertThat(m.seen["mobile_1_abcdef"]).isEqualTo(10L)
        assertThat(Requests.withSeen(m, "mobile_1_abcdef", 5)).isNull()
        for (i in 0 until 70) m = Requests.withSeen(m, "mobile_${i}_zzzzzz", (i + 1).toLong())!!
        val back = Requests.parseMemory(Requests.serializeMemory(m))
        assertThat(back.seen).hasSize(60)
        assertThat(back.seen["mobile_69_zzzzzz"]).isEqualTo(70L)
        assertThat(back.seen["mobile_1_abcdef"]).isNull()
        assertThat(back.sessions).isEmpty()
    }

    @Test
    fun `a chat becomes one to remember once the visitor wrote in it`() {
        assertThat(Requests.hasVisitorEntry(listOf("bot-message" to "bot-message"))).isFalse()
        assertThat(Requests.hasVisitorEntry(listOf("bot-message" to "bot-message", "user-message" to ""))).isTrue()
        assertThat(Requests.hasVisitorEntry(listOf(null to "user-live-message"))).isTrue()
        assertThat(Requests.hasVisitorEntry(null)).isFalse()
    }

    // ---------------------------------------------------------- who is asking (web api.ts who)

    @Test
    fun `the visitor id, this device's chats and only the signed user's checked fields`() {
        val m = RequestMemory(listOf("mobile_1_abcdef"))
        assertThat(Requests.who("v_1", m, null)).isEqualTo(mapOf("visitorId" to "v_1", "sessions" to listOf("mobile_1_abcdef")))
        assertThat(Requests.who("v_1", m, RequestsUser("u1", "a@b.co", "h"))).isEqualTo(
            mapOf("visitorId" to "v_1", "sessions" to listOf("mobile_1_abcdef"), "user" to mapOf("id" to "u1", "email" to "a@b.co", "hash" to "h"))
        )
        assertThat(Requests.who(null, m, RequestsUser(id = "u2", hash = "h2"))["user"]).isEqualTo(mapOf("id" to "u2", "hash" to "h2"))
        assertThat(Requests.who(null, m, null)["visitorId"]).isEqualTo("")
    }

    @Test
    fun `asks only with a chat on this device or a signed-in user`() {
        assertThat(Requests.canAsk(RequestMemory(), null)).isFalse()
        assertThat(Requests.canAsk(RequestMemory(), RequestsUser(id = "u1"))).isFalse()
        assertThat(Requests.canAsk(RequestMemory(), RequestsUser(id = "u1", hash = "h"))).isTrue()
        assertThat(Requests.canAsk(RequestMemory(listOf("mobile_1_abcdef")), null)).isTrue()
    }

    // ---------------------------------------------------------- the server (web api.ts, visitorRequestsHandler.js)

    private class FakeSocket(val answers: Map<String, Map<String, Any?>?> = emptyMap()) : RequestsSocket {
        val handlers = mutableMapOf<String, MutableList<(Map<String, Any?>?) -> Unit>>()
        val asked = mutableListOf<Triple<String, Map<String, Any?>, Long>>()
        val emitted = mutableListOf<Pair<String, Map<String, Any?>>>()
        override fun ask(event: String, data: Map<String, Any?>, timeoutMs: Long, onAnswer: (Map<String, Any?>?) -> Unit) {
            asked += Triple(event, data, timeoutMs)
            onAnswer(answers[event])
        }
        override fun emit(event: String, data: Map<String, Any?>) { emitted += event to data }
        override fun on(event: String, fn: (Map<String, Any?>?) -> Unit): () -> Unit {
            handlers.getOrPut(event) { mutableListOf() } += fn
            return { handlers[event]?.remove(fn) }
        }
        fun fire(event: String, data: Map<String, Any?>? = null) = handlers[event].orEmpty().toList().forEach { it(data) }
    }

    private val who = { mapOf<String, Any?>("visitorId" to "v_1", "sessions" to listOf("mobile_1_abcdef")) }

    private val wire = mapOf<String, Any?>(
        "id" to "mobile_1_abcdef", "title" to "My invoice is wrong", "status" to "open", "startedAt" to "2026-10-16T10:00:00.000Z",
        "lastAt" to "2026-10-16T12:00:00.000Z", "lastFrom" to "team", "lastText" to "Fixed it", "teamName" to "Ada"
    )

    @Test
    fun `my-requests - the list, or null when it did not come`() {
        val s = FakeSocket(mapOf("my-requests" to mapOf("ok" to true, "requests" to listOf(wire))))
        var got: List<RequestSummary>? = null
        Requests.api({ s }, who).list { got = it }
        assertThat(got).containsExactly(RequestSummary("mobile_1_abcdef", "My invoice is wrong", "open", "2026-10-16T10:00:00.000Z", "2026-10-16T12:00:00.000Z", "team", "Fixed it", "Ada"))
        assertThat(s.asked.single()).isEqualTo(Triple("my-requests", who(), 10_000L))
        var none: List<RequestSummary>? = listOf()
        Requests.api({ FakeSocket(mapOf("my-requests" to mapOf("ok" to false))) }, who).list { none = it }
        assertThat(none).isNull()
        none = listOf()
        Requests.api({ FakeSocket() }, who).list { none = it }
        assertThat(none).isNull()
        none = listOf()
        Requests.api({ null }, who).list { none = it }
        assertThat(none).isNull()
    }

    @Test
    fun `my-request-open, my-request-close and my-request-reply carry the conversation`() {
        val detail = wire + ("messages" to listOf(
            mapOf("id" to "m1", "from" to "you", "name" to "", "text" to "My invoice is wrong", "at" to "2026-10-16T11:00:00.000Z"),
            mapOf("id" to "m2", "from" to "team", "name" to "Ada", "text" to "Fixed it", "at" to null)
        ))
        val s = FakeSocket(mapOf("my-request-open" to mapOf("ok" to true, "request" to detail), "my-request-reply" to mapOf("ok" to false, "reason" to "locked")))
        val api = Requests.api({ s }, who)
        var opened: RequestDetail? = null
        api.open("mobile_1_abcdef") { opened = it }
        assertThat(opened!!.id).isEqualTo("mobile_1_abcdef")
        assertThat(opened!!.messages.map { Triple(it.from, it.name, it.at) }).containsExactly(Triple("you", "", "2026-10-16T11:00:00.000Z"), Triple("team", "Ada", null)).inOrder()
        assertThat(s.asked.last()).isEqualTo(Triple("my-request-open", who() + ("chatSessionId" to "mobile_1_abcdef"), 10_000L))

        var out: ReplyResult? = null
        api.reply("mobile_1_abcdef", "hi", "request-reply-x") { out = it }
        assertThat(out).isEqualTo(ReplyResult(false, null, "locked"))
        assertThat(s.asked.last()).isEqualTo(Triple("my-request-reply", who() + mapOf("chatSessionId" to "mobile_1_abcdef", "text" to "hi", "entryId" to "request-reply-x"), 10_000L))

        api.close("mobile_1_abcdef")
        assertThat(s.emitted.single()).isEqualTo("my-request-close" to mapOf("chatSessionId" to "mobile_1_abcdef"))

        Requests.api({ FakeSocket() }, who).reply("a", "b", "c") { out = it }
        assertThat(out).isEqualTo(ReplyResult(false, reason = "offline"))
        Requests.api({ FakeSocket(mapOf("my-request-reply" to mapOf("ok" to false, "reason" to "empty"))) }, who).reply("a", " ", "c") { out = it }
        assertThat(out!!.reason).isEqualTo("empty")
        opened = null
        Requests.api({ FakeSocket(mapOf("my-request-open" to mapOf("ok" to false, "reason" to "not_found"))) }, who).open("x") { opened = it }
        assertThat(opened).isNull()
    }

    @Test
    fun `my-request-update names the request - a reconnect reads the open one again`() {
        val s = FakeSocket(mapOf("my-request-open" to mapOf("ok" to true, "request" to wire + ("messages" to emptyList<Any?>()))))
        val api = Requests.api({ s }, who)
        val seen = mutableListOf<String>()
        val off = api.onUpdate { seen += it }
        s.fire("my-request-update", mapOf("chatSessionId" to "other_123"))
        s.fire("my-request-update", emptyMap())
        s.fire("connect")
        assertThat(seen).containsExactly("other_123")
        api.open("mobile_1_abcdef") {}
        s.fire("connect")
        assertThat(seen).containsExactly("other_123", "mobile_1_abcdef").inOrder()
        api.close("mobile_1_abcdef")
        s.fire("connect")
        off()
        s.fire("my-request-update", mapOf("chatSessionId" to "other_123"))
        assertThat(seen).containsExactly("other_123", "mobile_1_abcdef").inOrder()
    }

    @Test
    fun `answers are posted where the portal lives`() {
        val s = FakeSocket(mapOf("my-requests" to mapOf("ok" to true, "requests" to emptyList<Any?>())))
        val queue = mutableListOf<() -> Unit>()
        var got: List<RequestSummary>? = null
        Requests.api({ s }, who, post = { queue += it }).list { got = it }
        assertThat(got).isNull()
        queue.forEach { it() }
        assertThat(got).isEmpty()
    }

    @Test
    fun `a reply's id is one the server keeps (request-reply-word chars, 1 to 60)`() {
        val id = Requests.replyEntryId(now) { 0.123456789 }
        assertThat(id).matches("^request-reply-[\\w-]{1,60}$")
        assertThat(id).startsWith("request-reply-${now.toString(36)}")
        // As JavaScript: (0.5).toString(36) is "0.i".
        assertThat(Requests.replyEntryId(1000L) { 0.5 }).isEqualTo("request-reply-rsi")
        assertThat(Requests.replyEntryId(now) { 0.123456789 }.removePrefix("request-reply-${now.toString(36)}")).hasLength(6)
    }

    // ---------------------------------------------------------- the accent (web accentFor)

    @Test
    fun `the brand colour, the header, the launcher, unless too light - then indigo`() {
        assertThat(Requests.accent(mapOf("brandColor" to "#0808EE", "headerBgColor" to "#ff0000"))).isEqualTo("#0808EE")
        assertThat(Requests.accent(mapOf("brandColor" to "#0808EE", "_themePalette" to true, "headerBgColor" to "#123456"))).isEqualTo("#123456")
        assertThat(Requests.accent(mapOf("headerBgColor" to "#ffffff", "widgetIconBgColor" to "#222222"))).isEqualTo("#222222")
        assertThat(Requests.accent(mapOf("headerBgColor" to "#fff", "widgetIconThemeType" to "Gradient", "widgetGradientBgOne" to "#eeeeee", "widgetGradientBgTwo" to "#333333"))).isEqualTo("#333333")
        assertThat(Requests.accent(mapOf("headerBgColor" to "#fff"))).isEqualTo("#4F46E5")
        assertThat(Requests.accent(null)).isEqualTo("#4F46E5")
    }
}
