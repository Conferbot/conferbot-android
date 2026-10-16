package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.services.SocketClient
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A waiting visitor's queue position asked again at once on a reconnect (#94), as the web widget does. */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueReconnectTest {

    @Test
    fun `a reconnect while waiting asks for the queue position at once`() = runTest {
        val socket = mockk<SocketClient>(relaxed = true)
        val x = LiveChatExtras(scope = backgroundScope, socket = { socket }, sessionId = { "s1" }, addMessage = {})
        x.startQueuePoll()
        runCurrent()
        verify(exactly = 1) { socket.emitWithAck("queue-status", any(), any(), any()) }
        x.askQueueNow()
        runCurrent()
        verify(exactly = 2) { socket.emitWithAck("queue-status", any(), any(), any()) }
        x.stopQueuePoll()
    }

    @Test
    fun `a reconnect while not waiting asks nothing`() = runTest {
        val socket = mockk<SocketClient>(relaxed = true)
        val x = LiveChatExtras(scope = backgroundScope, socket = { socket }, sessionId = { "s1" }, addMessage = {})
        x.askQueueNow()
        runCurrent()
        verify(exactly = 0) { socket.emitWithAck("queue-status", any(), any(), any()) }
    }
}
