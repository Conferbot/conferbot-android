package com.conferbot.sdk.core.requests

import com.conferbot.sdk.core.flow.FlowCalendar
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * "Your requests" in the SDK ([RequestsExtras]; RN
 * context/hooks/useRequestsPortal.ts, web conferbot-widget
 * src/ts/home/requests/card.ts, store.ts and index.ts rememberSession): what
 * the device keeps, when the list is read, the unread count. The cases of
 * the React Native SDK's __tests__/context/useRequestsPortal.test.ts, on a
 * fake socket, store and clock.
 */
class RequestsExtrasTest {

    private class FakeSocket(val answers: MutableMap<String, Map<String, Any?>?> = mutableMapOf()) : RequestsSocket {
        val handlers = mutableMapOf<String, MutableList<(Map<String, Any?>?) -> Unit>>()
        val asked = mutableListOf<Pair<String, Map<String, Any?>>>()
        val emitted = mutableListOf<Pair<String, Map<String, Any?>>>()
        override fun ask(event: String, data: Map<String, Any?>, timeoutMs: Long, onAnswer: (Map<String, Any?>?) -> Unit) {
            asked += event to data
            onAnswer(answers[event])
        }
        override fun emit(event: String, data: Map<String, Any?>) { emitted += event to data }
        override fun on(event: String, fn: (Map<String, Any?>?) -> Unit): () -> Unit {
            handlers.getOrPut(event) { mutableListOf() } += fn
            return { handlers[event]?.remove(fn) }
        }
        fun fire(event: String, data: Map<String, Any?>? = null) = handlers[event].orEmpty().toList().forEach { it(data) }
    }

    private class FakeStore(val data: MutableMap<String, String> = mutableMapOf()) : RequestsStore {
        var writes = 0
        override fun read(key: String) = data[key]
        override fun write(key: String, value: String) { writes++; data[key] = value }
    }

    private val key = "conferbot_requests_bot1"
    private val team = mapOf<String, Any?>(
        "id" to "mobile_1_abcdef", "title" to "Invoice", "status" to "open",
        "lastAt" to FlowCalendar.iso(System.currentTimeMillis() - 60_000L), "lastFrom" to "team", "lastText" to "Done", "teamName" to "Ada"
    )

    private inner class Setup(
        val socket: FakeSocket = FakeSocket(),
        val store: FakeStore? = FakeStore(),
        val user: RequestsUser? = null,
        var connected: Boolean = true,
        var session: String? = null,
        var ended: Boolean = false
    ) {
        var clock = 1_000_000L
        var openedChat = 0
        val extras = RequestsExtras(
            botId = { "bot1" },
            socket = { socket },
            connected = { connected },
            visitorId = { "v_1" },
            user = { user },
            store = { store },
            chatSessionId = { session },
            chatEnded = { ended },
            openChat = { openedChat++ },
            now = { clock }
        ).also { it.attach() }
        fun lists() = socket.asked.count { it.first == "my-requests" }
    }

    @Test
    fun `a first-time visitor - nothing is asked`() {
        val t = Setup()
        t.extras.onConnected()
        t.extras.onChatShown()
        assertThat(t.socket.asked).isEmpty()
        assertThat(t.extras.requests.value).isNull()
    }

    @Test
    fun `a chat the visitor wrote in is remembered - then the list is read with it, and the unread counted`() {
        val socket = FakeSocket(mutableMapOf("my-requests" to mapOf("ok" to true, "requests" to listOf(team))))
        val t = Setup(socket, session = "mobile_1_abcdef")
        t.extras.onRecord("mobile_1_abcdef", listOf("bot-message" to "bot-message"))
        assertThat(t.store!!.writes).isEqualTo(0)
        t.extras.onRecord("mobile_1_abcdef", listOf("bot-message" to "bot-message", "user-message" to "user-message"))
        assertThat(Requests.parseMemory(t.store.data[key]).sessions).containsExactly("mobile_1_abcdef")
        // Saved once: the same chat again changes nothing.
        t.extras.onRecord("mobile_1_abcdef", listOf("user-message" to "user-message"))
        assertThat(t.store.writes).isEqualTo(1)
        t.extras.refresh(force = true)
        assertThat(socket.asked.single()).isEqualTo("my-requests" to mapOf("visitorId" to "v_1", "sessions" to listOf("mobile_1_abcdef")))
        assertThat(t.extras.requests.value!!.map { it.id }).containsExactly("mobile_1_abcdef")
        assertThat(t.extras.unread.value).isEqualTo(1)
    }

    @Test
    fun `read on the device - opening it marks it read and the count drops`() {
        val socket = FakeSocket(mutableMapOf(
            "my-requests" to mapOf("ok" to true, "requests" to listOf(team)),
            "my-request-open" to mapOf("ok" to true, "request" to team + ("messages" to emptyList<Any?>()))
        ))
        val t = Setup(socket, FakeStore(mutableMapOf(key to """{"sessions":["mobile_1_abcdef"],"seen":{}}""")))
        t.extras.onConnected()
        assertThat(t.extras.unread.value).isEqualTo(1)
        t.extras.open("mobile_1_abcdef")
        assertThat(t.extras.portal.isOpen).isTrue()
        assertThat(t.extras.unread.value).isEqualTo(0)
        assertThat(Requests.parseMemory(t.store!!.data[key]).seen["mobile_1_abcdef"]).isGreaterThan(0L)
        t.extras.close()
        assertThat(t.extras.portal.isOpen).isFalse()
        assertThat(socket.emitted).contains("my-request-close" to mapOf("chatSessionId" to "mobile_1_abcdef"))
        // Closing the portal reads the list again.
        assertThat(t.lists()).isEqualTo(2)
    }

    @Test
    fun `a signed-in user is asked about with only the checked fields`() {
        val socket = FakeSocket(mutableMapOf("my-requests" to mapOf("ok" to true, "requests" to emptyList<Any?>())))
        Setup(socket, store = null, user = RequestsUser("u1", "a@b.co", "h")).extras.onConnected()
        assertThat(socket.asked.single()).isEqualTo(
            "my-requests" to mapOf("visitorId" to "v_1", "sessions" to emptyList<String>(), "user" to mapOf("id" to "u1", "email" to "a@b.co", "hash" to "h"))
        )
    }

    @Test
    fun `a request changed while the portal is closed - the list is read again`() {
        val socket = FakeSocket(mutableMapOf("my-requests" to mapOf("ok" to true, "requests" to emptyList<Any?>())))
        val t = Setup(socket, FakeStore(mutableMapOf(key to """{"sessions":["mobile_1_abcdef"],"seen":{}}""")))
        t.extras.onConnected()
        assertThat(t.lists()).isEqualTo(1)
        socket.fire("my-request-update", mapOf("chatSessionId" to "mobile_1_abcdef"))
        assertThat(t.lists()).isEqualTo(2)
    }

    @Test
    fun `the chat opening or closing reads it at most every 15s - offline nothing`() {
        val socket = FakeSocket(mutableMapOf("my-requests" to mapOf("ok" to true, "requests" to emptyList<Any?>())))
        val t = Setup(socket, FakeStore(mutableMapOf(key to """{"sessions":["mobile_1_abcdef"],"seen":{}}""")))
        t.extras.onChatShown()
        t.clock += 14_999
        t.extras.onChatShown()
        assertThat(t.lists()).isEqualTo(1)
        t.clock += 1
        t.extras.onChatShown()
        assertThat(t.lists()).isEqualTo(2)
        t.connected = false
        t.extras.onConnected()
        assertThat(t.lists()).isEqualTo(2)
    }

    @Test
    fun `the chat on screen, not ended, opens the chat - an ended one opens as a request`() {
        val socket = FakeSocket(mutableMapOf("my-request-open" to mapOf("ok" to true, "request" to team + ("messages" to emptyList<Any?>()))))
        val t = Setup(socket, session = "mobile_1_abcdef")
        t.extras.open("mobile_1_abcdef")
        assertThat(t.openedChat).isEqualTo(1)
        assertThat(t.extras.portal.isOpen).isFalse()
        t.ended = true
        t.extras.open("mobile_1_abcdef")
        assertThat(t.extras.portal.isOpen).isTrue()
        assertThat(t.extras.state.value.request!!.id).isEqualTo("mobile_1_abcdef")
    }

    @Test
    fun `set up again on a new socket - the updates come from it, not the old one`() {
        val first = FakeSocket(mutableMapOf("my-requests" to mapOf("ok" to true, "requests" to emptyList<Any?>())))
        var current = first
        val store = FakeStore(mutableMapOf(key to """{"sessions":["mobile_1_abcdef"],"seen":{}}"""))
        val extras = RequestsExtras(
            botId = { "bot1" }, socket = { current }, connected = { true }, visitorId = { "v_1" }, user = { null },
            store = { store }, chatSessionId = { null }, chatEnded = { false }
        )
        extras.attach()
        val second = FakeSocket(mutableMapOf("my-requests" to mapOf("ok" to true, "requests" to emptyList<Any?>())))
        current = second
        extras.attach()
        first.fire("my-request-update", mapOf("chatSessionId" to "mobile_1_abcdef"))
        assertThat(second.asked).isEmpty()
        second.fire("my-request-update", mapOf("chatSessionId" to "mobile_1_abcdef"))
        assertThat(second.asked.map { it.first }).containsExactly("my-requests")
    }

    @Test
    fun `unreadable memory is nothing, and a store that throws still remembers for the session`() {
        val throwing = object : RequestsStore {
            override fun read(key: String): String = throw IllegalStateException("blocked")
            override fun write(key: String, value: String) = throw IllegalStateException("full")
        }
        val socket = FakeSocket(mutableMapOf("my-requests" to mapOf("ok" to true, "requests" to emptyList<Any?>())))
        val extras = RequestsExtras(
            botId = { "bot1" }, socket = { socket }, connected = { true }, visitorId = { "v_1" }, user = { null },
            store = { throwing }, chatSessionId = { null }, chatEnded = { false }
        )
        extras.onRecord("mobile_9_abcdef", listOf("user-message" to "user-message"))
        extras.onConnected()
        assertThat(socket.asked.single().second["sessions"]).isEqualTo(listOf("mobile_9_abcdef"))
    }

    /** Answers my-requests per signed-in user; with [hold] on, answers wait until [release]. */
    private class UserSocket(val user: () -> RequestsUser?) : RequestsSocket {
        val asked = mutableListOf<Map<String, Any?>>()
        var hold = false
        private val held = mutableListOf<() -> Unit>()
        override fun ask(event: String, data: Map<String, Any?>, timeoutMs: Long, onAnswer: (Map<String, Any?>?) -> Unit) {
            if (event == "my-requests") asked += data
            val u = (data["user"] as? Map<*, *>)?.get("id") as? String
            val answer = mapOf<String, Any?>("ok" to true, "requests" to listOfNotNull(u?.let { mapOf<String, Any?>(
                "id" to "mobile_1_$it", "title" to "Hi", "status" to "open",
                "lastAt" to FlowCalendar.iso(System.currentTimeMillis() - 60_000L), "lastFrom" to "team", "lastText" to "Done"
            ) }))
            if (hold) held += { onAnswer(answer) } else onAnswer(answer)
        }
        fun release() { val h = held.toList(); held.clear(); h.forEach { it() } }
        override fun emit(event: String, data: Map<String, Any?>) {}
        override fun on(event: String, fn: (Map<String, Any?>?) -> Unit): () -> Unit = {}
    }

    private val ada = RequestsUser("u1", "ada@example.com", "h1")
    private val bob = RequestsUser("u2", "bob@example.com", "h2")

    private fun signedIn(socket: () -> UserSocket, user: () -> RequestsUser?) = RequestsExtras(
        botId = { "bot1" }, socket = socket, connected = { true }, visitorId = { "v_1" }, user = user,
        store = { FakeStore() }, chatSessionId = { null }, chatEnded = { false }, now = { 1_000_000L }
    ).also { it.attach() }

    @Test
    fun `a sign-in reads the list for them straight away - a sign-out drops it (#79)`() {
        var user: RequestsUser? = null
        lateinit var socket: UserSocket
        socket = UserSocket { user }
        val r = signedIn({ socket }, { user })
        r.onConnected()
        // A first-time visitor with nobody signed in is asked nothing.
        assertThat(socket.asked).isEmpty()

        user = ada
        r.onUserChanged()
        assertThat(socket.asked.single()["user"]).isEqualTo(mapOf("id" to "u1", "email" to "ada@example.com", "hash" to "h1"))
        assertThat(r.requests.value!!.single().id).isEqualTo("mobile_1_u1")
        assertThat(r.unread.value).isEqualTo(1)

        // Within 15s of the last read, still read again for the next user.
        user = bob
        r.onUserChanged()
        assertThat(socket.asked).hasSize(2)
        assertThat(r.requests.value!!.single().id).isEqualTo("mobile_1_u2")

        // Signed out with nothing on this device: their list goes, nothing asked.
        user = null
        r.onUserChanged()
        assertThat(socket.asked).hasSize(2)
        assertThat(r.requests.value).isNull()
        assertThat(r.unread.value).isEqualTo(0)
    }

    @Test
    fun `a list still on its way for the last user is not shown - the new one is read (#79)`() {
        var user: RequestsUser? = ada
        lateinit var socket: UserSocket
        socket = UserSocket { user }
        val r = signedIn({ socket }, { user })
        socket.hold = true
        r.onConnected()
        assertThat(socket.asked).hasSize(1)

        user = bob
        r.onUserChanged()
        // Dropped at once, before any answer.
        assertThat(r.requests.value).isNull()
        socket.hold = false
        socket.release()

        assertThat(socket.asked).hasSize(2)
        assertThat(socket.asked.last()["user"]).isEqualTo(mapOf("id" to "u2", "email" to "bob@example.com", "hash" to "h2"))
        assertThat(r.requests.value!!.single().id).isEqualTo("mobile_1_u2")
    }

    @Test
    fun `a sign-out with an answer on its way and the portal open - closed, nothing of theirs shown (#79)`() {
        var user: RequestsUser? = ada
        lateinit var socket: UserSocket
        socket = UserSocket { user }
        val r = signedIn({ socket }, { user })
        r.onConnected()
        r.open()
        assertThat(r.portal.isOpen).isTrue()
        socket.hold = true
        r.refresh(force = true)
        user = null
        r.onUserChanged()
        assertThat(r.portal.isOpen).isFalse()
        socket.release()
        assertThat(r.requests.value).isNull()
        assertThat(r.unread.value).isEqualTo(0)
    }
}
