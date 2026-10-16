package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.services.SocketClient
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The flow handover's max wait in LiveChatExtras (#38): the wait card's
 * deadline ([LiveChatExtras.waitEndsAt]) and handover-timeout at that same
 * moment; a restored wait from the server's seconds left, once. As RN's
 * __tests__/context/useLiveChatExtras.test.ts (#42), on virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WaitDeadlineTimerTest {

    private fun TestScope.extras(socket: SocketClient) =
        LiveChatExtras(scope = backgroundScope, socket = { socket }, sessionId = { "s1" }, addMessage = {})

    @Test
    fun `the card's deadline is the step's max wait, handover-timeout fires at it, once`() = runTest {
        val socket = mockk<SocketClient>(relaxed = true)
        val x = extras(socket)
        x.startWaitTimer(mapOf("maxWaitTime" to 1), "w1") { testScheduler.currentTime }
        assertThat(x.waitEndsAt.value).isEqualTo(60_000L)
        // The same wait again (the step's state seen twice): one timer.
        x.startWaitTimer(mapOf("maxWaitTime" to 9), "w1") { testScheduler.currentTime }
        assertThat(x.waitEndsAt.value).isEqualTo(60_000L)
        advanceTimeBy(59_999)
        runCurrent()
        verify(exactly = 0) { socket.emit("handover-timeout", any()) }
        advanceTimeBy(2)
        runCurrent()
        verify(exactly = 1) { socket.emit("handover-timeout", any()) }
        x.stopWaitTimer()
        assertThat(x.waitEndsAt.value).isNull()
    }

    @Test
    fun `a max wait of 0 or unset is 2 minutes`() = runTest {
        val x = extras(mockk(relaxed = true))
        x.startWaitTimer(mapOf("maxWaitTime" to 0), null) { testScheduler.currentTime }
        assertThat(x.waitEndsAt.value).isEqualTo(120_000L)
        x.stopWaitTimer()
        x.startWaitTimer(null, null) { testScheduler.currentTime }
        assertThat(x.waitEndsAt.value).isEqualTo(120_000L)
        x.stopWaitTimer()
    }

    @Test
    fun `a restored wait counts from the server's seconds left and times out then, the next wait from the full max wait`() = runTest {
        val socket = mockk<SocketClient>(relaxed = true)
        val x = extras(socket)
        x.resumeWaitWith(65)
        x.startWaitTimer(mapOf("maxWaitTime" to 5), "w1") { testScheduler.currentTime }
        assertThat(x.waitEndsAt.value).isEqualTo(65_000L)
        advanceTimeBy(64_999)
        runCurrent()
        verify(exactly = 0) { socket.emit("handover-timeout", any()) }
        advanceTimeBy(2)
        runCurrent()
        verify(exactly = 1) { socket.emit("handover-timeout", any()) }
        x.stopWaitTimer()

        val start = testScheduler.currentTime
        x.startWaitTimer(mapOf("maxWaitTime" to 5), "w1") { testScheduler.currentTime }
        assertThat(x.waitEndsAt.value).isEqualTo(start + 300_000L)
        x.stopWaitTimer()
    }

    @Test
    fun `stopped before the deadline - no timeout, no countdown`() = runTest {
        val socket = mockk<SocketClient>(relaxed = true)
        val x = extras(socket)
        x.startWaitTimer(mapOf("maxWaitTime" to 1), "w1") { testScheduler.currentTime }
        advanceTimeBy(30_000)
        x.stopWaitTimer()
        assertThat(x.waitEndsAt.value).isNull()
        advanceTimeBy(600_000)
        runCurrent()
        verify(exactly = 0) { socket.emit("handover-timeout", any()) }
    }
}
