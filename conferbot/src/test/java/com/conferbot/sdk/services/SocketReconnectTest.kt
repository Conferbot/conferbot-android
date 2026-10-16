package com.conferbot.sdk.services

import com.conferbot.sdk.models.SocketEvents
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import org.junit.Test

/**
 * Back in the chat's room after a reconnect (#94). socket.io-client 2.x
 * tells 'reconnect' on the Manager, not the Socket, so the rejoin runs on
 * the Socket's 'connect', which fires on every connect, each reconnect too.
 */
class SocketReconnectTest {

    private class Fake {
        val handlers = mutableMapOf<String, MutableList<Emitter.Listener>>()
        val managerHandlers = mutableMapOf<String, MutableList<Emitter.Listener>>()
        val emitted = mutableListOf<Pair<String, Any?>>()
        var connected = false
        val manager = mockk<Manager>(relaxed = true).also { m ->
            every { m.on(any(), any()) } answers {
                managerHandlers.getOrPut(firstArg()) { mutableListOf() }.add(secondArg())
                m
            }
        }
        val socket = mockk<Socket>(relaxed = true).also { s ->
            every { s.on(any(), any()) } answers {
                handlers.getOrPut(firstArg()) { mutableListOf() }.add(secondArg())
                s
            }
            every { s.connected() } answers { connected }
            every { s.io() } returns manager
            every { s.emit(any<String>(), *anyVararg()) } answers {
                @Suppress("UNCHECKED_CAST")
                emitted.add(firstArg<String>() to (args[1] as Array<Any?>).firstOrNull())
                s
            }
        }

        fun fire(event: String) = handlers[event].orEmpty().forEach { it.call() }
        fun connect() { connected = true; fire(Socket.EVENT_CONNECT) }
        fun drop() { connected = false; fire(Socket.EVENT_DISCONNECT) }
        fun joins() = emitted.filter { it.first == SocketEvents.JOIN_CHAT_ROOM }.map { it.second as JSONObject }
    }

    private fun client(fake: Fake, visitorId: String? = null) =
        SocketClient("k", "b", "http://localhost", visitorId) { _, _ -> fake.socket }.also { it.connect() }

    @Test
    fun `the room is joined again after a disconnect and reconnect, with the visitor`() {
        val fake = Fake()
        val c = client(fake, "v1")
        fake.connect()
        c.joinChatRoom("s1")
        assertThat(fake.joins()).hasSize(1)
        fake.drop()
        fake.connect()
        assertThat(fake.joins()).hasSize(2)
        val again = fake.joins()[1]
        assertThat(again.getString("chatSessionId")).isEqualTo("s1")
        assertThat(again.getString("visitorId")).isEqualTo("v1")
        fake.drop()
        fake.connect()
        assertThat(fake.joins()).hasSize(3)
    }

    @Test
    fun `nothing is joined on connect before a chat is known`() {
        val fake = Fake()
        client(fake)
        fake.connect()
        assertThat(fake.joins()).isEmpty()
    }

    @Test
    fun `a join asked while offline is made on the next connect, once`() {
        val fake = Fake()
        val c = client(fake)
        c.joinChatRoom("s2", mapOf("os" to "Android"))
        assertThat(fake.joins()).isEmpty()
        fake.connect()
        assertThat(fake.joins()).hasSize(1)
        assertThat(fake.joins()[0].getString("chatSessionId")).isEqualTo("s2")
        assertThat(fake.joins()[0].getJSONObject("deviceInfo").getString("os")).isEqualTo("Android")
        assertThat(fake.joins()[0].has("visitorId")).isFalse()
    }

    @Test
    fun `a room left is not joined again`() {
        val fake = Fake()
        val c = client(fake)
        fake.connect()
        c.joinChatRoom("s3")
        c.leaveChatRoom("s3")
        fake.drop()
        fake.connect()
        assertThat(fake.joins()).hasSize(1)
    }

    @Test
    fun `the reconnection listener is told on each reconnect, not the first connect`() {
        val fake = Fake()
        val c = client(fake)
        var told = 0
        c.setReconnectionListener { told++ }
        fake.connect()
        assertThat(told).isEqualTo(0)
        fake.drop()
        fake.connect()
        assertThat(told).isEqualTo(1)
        assertThat(c.connectionState.value).isEqualTo(SocketClient.ConnectionState.CONNECTED)
    }

    @Test
    fun `reconnect attempts are heard on the Manager, not the Socket`() {
        val fake = Fake()
        val c = client(fake)
        assertThat(fake.handlers.keys).doesNotContain("reconnect")
        assertThat(fake.handlers.keys).doesNotContain("reconnect_attempt")
        fake.managerHandlers[Manager.EVENT_RECONNECT_ATTEMPT].orEmpty().forEach { it.call(1) }
        assertThat(c.connectionState.value).isEqualTo(SocketClient.ConnectionState.RECONNECTING)
    }
}
