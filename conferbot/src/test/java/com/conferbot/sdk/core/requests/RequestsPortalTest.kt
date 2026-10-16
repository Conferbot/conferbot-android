package com.conferbot.sdk.core.requests

import com.conferbot.sdk.core.flow.FlowCalendar
import com.conferbot.sdk.core.requests.RequestsPortalState.Screen
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * "Your requests" screens as states ([RequestsPortal]; web conferbot-widget
 * src/ts/home/requests/view.ts mountRequestsView). The cases of the React
 * Native SDK's __tests__/core/requestsPortal.test.ts. The fake api holds
 * each answer until the test gives it, as RN's deferred promises.
 */
class RequestsPortalTest {

    private val now = 1_792_152_000_000L // 2026-10-16T12:00:00Z

    private fun summary(status: String = "open", lastFrom: String = "team", id: String = "mobile_1_abcdef") =
        RequestSummary(id, "My invoice is wrong", status, lastAt = FlowCalendar.iso(now - 60_000L), lastFrom = lastFrom, lastText = "Fixed it", teamName = "Ada")

    private val messages = listOf(
        RequestMessage("m1", "you", "", "My invoice is wrong", FlowCalendar.iso(now - 120_000L)),
        RequestMessage("m2", "team", "Ada", "Fixed it", FlowCalendar.iso(now - 60_000L))
    )

    private fun detail(status: String = "open", lastFrom: String = "team", messages: List<RequestMessage> = this.messages) =
        RequestDetail(summary(status, lastFrom), messages)

    private class FakeApi : RequestsApi {
        var listAnswer: List<RequestSummary>? = null
        var openAnswer: RequestDetail? = null
        var replyAnswer: ReplyResult = ReplyResult(true)
        /** Answer at once (true) or hold the answer for the test (false). */
        var instant = true
        val held = mutableListOf<() -> Unit>()
        val lists = mutableListOf<Unit>()
        val opens = mutableListOf<String>()
        val closes = mutableListOf<String>()
        val replies = mutableListOf<Triple<String, String, String>>()
        var update: ((String) -> Unit)? = null
        var subscriptions = 0

        private fun answer(fn: () -> Unit) = if (instant) fn() else { held += fn }
        override fun list(onDone: (List<RequestSummary>?) -> Unit) { lists += Unit; val a = listAnswer; answer { onDone(a) } }
        override fun open(id: String, onDone: (RequestDetail?) -> Unit) { opens += id; val a = openAnswer; answer { onDone(a) } }
        override fun close(id: String) { closes += id }
        override fun reply(id: String, text: String, entryId: String, onDone: (ReplyResult) -> Unit) {
            replies += Triple(id, text, entryId); val a = replyAnswer; answer { onDone(a) }
        }
        override fun onUpdate(fn: (String) -> Unit): () -> Unit { subscriptions++; update = fn; return { update = null } }
        fun release() { val h = held.toList(); held.clear(); h.forEach { it() } }
    }

    private inner class Setup(current: String? = null) {
        val api = FakeApi().apply { listAnswer = listOf(summary()); openAnswer = detail(); replyAnswer = ReplyResult(true, detail(lastFrom = "you")) }
        val seen = mutableMapOf<String, Long>()
        val marked = mutableListOf<Pair<String, Long>>()
        var openedChat = 0
        var changed = 0
        val portal = RequestsPortal(
            api = api,
            seen = { seen },
            markSeen = { id, at -> marked += id to at; seen[id] = at },
            isCurrentChat = { it == current },
            openChat = { openedChat++ },
            onChanged = { changed++ },
            now = { now },
            rand = { 0.5 }
        )
        val s get() = portal.state.value
    }

    // ---------------------------------------------------------- the list

    @Test
    fun `loading, then the requests in order - Back leaves and the list is read again`() {
        val t = Setup()
        t.api.listAnswer = listOf(summary(lastFrom = "you", id = "old_aaaaaa"), summary())
        t.api.instant = false
        t.portal.openList()
        assertThat(t.s.screen).isEqualTo(Screen.LIST)
        assertThat(t.s.title).isEqualTo("Your requests")
        assertThat(t.s.loading).isTrue()
        t.api.release()
        assertThat(t.s.loading).isFalse()
        assertThat(t.s.list!!.map { it.id }).containsExactly("mobile_1_abcdef", "old_aaaaaa").inOrder()
        t.portal.back()
        assertThat(t.s.screen).isEqualTo(Screen.CLOSED)
        assertThat(t.changed).isEqualTo(1)
        assertThat(t.api.closes).isEmpty()
    }

    @Test
    fun `empty is an empty list (the view says Nothing here yet)`() {
        val t = Setup()
        t.api.listAnswer = emptyList()
        t.portal.openList()
        assertThat(t.s.list).isEmpty()
    }

    @Test
    fun `could not load - failed, and Try again reads it again`() {
        val t = Setup()
        t.api.listAnswer = null
        t.portal.openList()
        assertThat(t.s.failed).isTrue()
        assertThat(t.s.loading).isFalse()
        t.api.listAnswer = listOf(summary())
        t.portal.reload()
        assertThat(t.s.failed).isFalse()
        assertThat(t.s.list).containsExactly(summary())
        // Nothing failed: Try again does nothing.
        t.portal.reload()
        assertThat(t.api.lists).hasSize(2)
    }

    // ---------------------------------------------------------- one request

    @Test
    fun `from the list - the banner, the messages, read here - Back to the list (and its live updates stop)`() {
        val t = Setup()
        t.portal.openList()
        t.portal.pick("mobile_1_abcdef")
        assertThat(t.s.screen).isEqualTo(Screen.REQUEST)
        assertThat(t.s.title).isEqualTo("Your request")
        assertThat(t.s.requestState).isEqualTo(RequestState.REPLIED)
        assertThat(t.s.placeholder).isEqualTo("Write your reply...")
        assertThat(t.s.banner!!.label).isEqualTo("We replied")
        assertThat(t.s.request!!.messages).hasSize(2)
        assertThat(t.marked).containsExactly("mobile_1_abcdef" to now)
        t.portal.back()
        assertThat(t.api.closes).containsExactly("mobile_1_abcdef")
        assertThat(t.s.screen).isEqualTo(Screen.LIST)
        t.portal.back()
        assertThat(t.s.screen).isEqualTo(Screen.CLOSED)
    }

    @Test
    fun `opened straight away - Back leaves`() {
        val t = Setup()
        t.portal.openRequest("mobile_1_abcdef")
        t.portal.back()
        assertThat(t.s.screen).isEqualTo(Screen.CLOSED)
        assertThat(t.api.closes).containsExactly("mobile_1_abcdef")
    }

    @Test
    fun `a solved one says a reply opens it again`() {
        val t = Setup()
        t.api.openAnswer = detail(status = "solved")
        t.portal.openRequest("mobile_1_abcdef")
        assertThat(t.s.requestState).isEqualTo(RequestState.SOLVED)
        assertThat(t.s.placeholder).isEqualTo("Write here to open it again...")
        assertThat(t.s.banner!!.detail).contains("pick it up again")
    }

    @Test
    fun `the chat on screen right now - that chat instead`() {
        val t = Setup(current = "mobile_1_abcdef")
        t.portal.openRequest("mobile_1_abcdef")
        assertThat(t.api.opens).isEmpty()
        assertThat(t.openedChat).isEqualTo(1)
        assertThat(t.s.screen).isEqualTo(Screen.CLOSED)
    }

    @Test
    fun `not found - failed with Try again`() {
        val t = Setup()
        t.api.openAnswer = null
        t.portal.openRequest("mobile_1_abcdef")
        assertThat(t.s.screen).isEqualTo(Screen.REQUEST)
        assertThat(t.s.failed).isTrue()
        t.api.openAnswer = detail()
        t.portal.reload()
        assertThat(t.s.request).isNotNull()
    }

    // ---------------------------------------------------------- a reply

    @Test
    fun `sent - shown as sending, then the answer replaces it and the draft is gone`() {
        val t = Setup()
        t.portal.openRequest("mobile_1_abcdef")
        assertThat(t.s.canSend).isFalse()
        t.portal.setDraft("  Thanks!  ")
        assertThat(t.s.draft).isEqualTo("  Thanks!  ")
        assertThat(t.s.canSend).isTrue()
        t.api.instant = false
        t.portal.send()
        val (_, text, entryId) = t.api.replies.single()
        assertThat(text).isEqualTo("Thanks!")
        assertThat(entryId).startsWith("request-reply-")
        assertThat(t.s.pending).containsExactly(PendingReply(entryId, "Thanks!", FlowCalendar.iso(now), PendingReply.Status.SENDING))
        assertThat(t.s.draft).isEmpty()
        assertThat(t.s.canSend).isFalse()
        // Still on its way: Try again does nothing.
        t.portal.retry(entryId)
        assertThat(t.api.replies).hasSize(1)
        // The server's answer: the request with the reply in it.
        t.api.release()
        assertThat(t.s.pending).isEmpty()
        assertThat(t.s.requestState).isEqualTo(RequestState.WAITING)
    }

    @Test
    fun `a sent reply's answer replaces the pending bubble`() {
        val t = Setup()
        t.portal.openRequest("mobile_1_abcdef")
        t.portal.setDraft("Thanks!")
        t.api.instant = false
        t.api.replyAnswer = ReplyResult(true, detail(lastFrom = "you", messages = messages + RequestMessage("r1", "you", "", "Thanks!", null)))
        t.portal.send()
        assertThat(t.s.pending).hasSize(1)
        t.api.release()
        assertThat(t.s.pending).isEmpty()
        assertThat(t.s.request!!.messages).hasSize(3)
        assertThat(t.s.requestState).isEqualTo(RequestState.WAITING)
    }

    @Test
    fun `not sent - Try again sends the same entry - locked - no Try again`() {
        val t = Setup()
        t.api.replyAnswer = ReplyResult(false, reason = "offline")
        t.portal.openRequest("mobile_1_abcdef")
        t.portal.setDraft("Hello")
        t.portal.send()
        val p = t.s.pending.single()
        assertThat(p.status).isEqualTo(PendingReply.Status.FAILED)
        t.api.replyAnswer = ReplyResult(false, reason = "locked")
        t.portal.retry(p.entryId)
        assertThat(t.api.replies[1].third).isEqualTo(p.entryId)
        assertThat(t.s.pending.single().status).isEqualTo(PendingReply.Status.LOCKED)
        t.portal.retry(p.entryId)
        assertThat(t.api.replies).hasSize(2)
    }

    @Test
    fun `an empty draft sends nothing - drafts are kept per request until reset`() {
        val t = Setup()
        t.portal.openRequest("mobile_1_abcdef")
        t.portal.setDraft("   ")
        t.portal.send()
        assertThat(t.api.replies).isEmpty()
        t.portal.setDraft("half written")
        t.portal.close()
        t.portal.openRequest("mobile_1_abcdef")
        assertThat(t.s.draft).isEqualTo("half written")
        t.portal.reset()
        t.portal.close()
        t.portal.openRequest("mobile_1_abcdef")
        assertThat(t.s.draft).isEmpty()
    }

    // ---------------------------------------------------------- live updates (my-request-update)

    @Test
    fun `the open request is read again, keeping the draft and a reply that did not go`() {
        val t = Setup()
        t.api.replyAnswer = ReplyResult(false, reason = "error")
        t.portal.openRequest("mobile_1_abcdef")
        t.portal.setDraft("one")
        t.portal.send()
        t.portal.setDraft("still typing")
        t.api.openAnswer = detail(status = "solved")
        t.api.update!!("mobile_1_abcdef")
        assertThat(t.s.requestState).isEqualTo(RequestState.SOLVED)
        assertThat(t.s.draft).isEqualTo("still typing")
        assertThat(t.s.pending.map { it.status }).containsExactly(PendingReply.Status.FAILED)
    }

    @Test
    fun `another request, a closed portal or a reply on its way - nothing`() {
        val t = Setup()
        t.api.update!!("mobile_1_abcdef")
        t.portal.openRequest("mobile_1_abcdef")
        t.api.update!!("other_123456")
        assertThat(t.api.opens).hasSize(1)
        t.portal.setDraft("x")
        t.api.instant = false
        t.portal.send()
        t.api.instant = true
        t.api.openAnswer = detail(status = "solved")
        t.api.update!!("mobile_1_abcdef")
        assertThat(t.s.requestState).isEqualTo(RequestState.REPLIED)
    }

    @Test
    fun `a late answer for a screen that is gone is dropped`() {
        val t = Setup()
        t.api.instant = false
        t.portal.openRequest("mobile_1_abcdef")
        t.portal.close()
        t.api.release()
        assertThat(t.s.screen).isEqualTo(Screen.CLOSED)
        // A late list after the list was left and opened again is dropped too.
        t.portal.openList()
        val first = t.api.held.toList()
        t.api.held.clear()
        t.portal.back()
        t.api.listAnswer = listOf(summary(id = "second_aaaaaa"))
        t.portal.openList()
        t.api.release()
        first.forEach { it() }
        assertThat(t.s.list!!.map { it.id }).containsExactly("second_aaaaaa")
    }

    @Test
    fun `subscribers hear each change - destroy stops the updates`() {
        val t = Setup()
        val heard = mutableListOf<Screen>()
        t.portal.openList()
        heard += t.portal.state.value.screen
        assertThat(heard).containsExactly(Screen.LIST)
        t.portal.destroy()
        assertThat(t.api.update).isNull()
        assertThat(t.api.subscriptions).isEqualTo(1)
    }
}
