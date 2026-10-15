package com.conferbot.sdk.core.livechat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The connecting wait's signal (core/livechat/HandoverWaiting.kt), the cases
 * of the React Native SDK's __tests__/core/handoverWaiting.test.ts (the web
 * widget's tests/waitingSignal.test.js), on a fake clock.
 */
class HandoverWaitingTest {

    /** A fake clock: timers run in order as time is ticked on. */
    class FakeClock : SignalTimer {
        var now = 0L
        private var id = 0
        private val timers = linkedMapOf<Int, Pair<Long, () -> Unit>>()
        override fun set(ms: Long, fn: () -> Unit): Any {
            timers[++id] = (now + ms) to fn
            return id
        }
        override fun clear(handle: Any) {
            timers.remove(handle as Int)
        }
        fun tick(ms: Long) {
            val until = now + ms
            while (true) {
                val next = timers.entries.minByOrNull { it.value.first } ?: break
                if (next.value.first > until) break
                timers.remove(next.key)
                now = next.value.first
                next.value.second()
            }
            now = until
        }
        val size get() = timers.size
    }

    @Test
    fun `signals after 20s, once`() {
        val c = FakeClock()
        var fired = 0
        val sig = ConnectingSignal(c, { true }) { fired++ }
        assertThat(HandoverSignals.CONNECTING_SIGNAL_MS).isEqualTo(20_000L)
        sig.start()
        sig.start()
        c.tick(19_999)
        assertThat(fired).isEqualTo(0)
        c.tick(1)
        assertThat(fired).isEqualTo(1)
        assertThat(sig.pending).isFalse()
        c.tick(60_000)
        assertThat(fired).isEqualTo(1)
    }

    @Test
    fun `a join, nobody available, leaving the queue or the chat ending - no signal`() {
        val c = FakeClock()
        var fired = 0
        val sig = ConnectingSignal(c, { true }) { fired++ }
        sig.start()
        c.tick(10_000)
        sig.end()
        c.tick(60_000)
        assertThat(fired).isEqualTo(0)
        assertThat(c.size).isEqualTo(0)
        sig.start()
        c.tick(20_000)
        assertThat(fired).isEqualTo(1)
    }

    @Test
    fun `due while the chat is closed - held until it opens`() {
        val c = FakeClock()
        var fired = 0
        var open = false
        val sig = ConnectingSignal(c, { open }) { fired++ }
        sig.start()
        c.tick(30_000)
        assertThat(fired).isEqualTo(0)
        open = true
        c.tick(HandoverSignals.CONNECTING_RECHECK_MS)
        assertThat(fired).isEqualTo(1)
        open = false
        sig.start()
        c.tick(25_000)
        sig.end()
        open = true
        c.tick(10_000)
        assertThat(fired).isEqualTo(1)
    }

    @Test
    fun `names as on the web, details carry no personal data`() {
        assertThat(HandoverSignals.WAITING_FOR_HUMAN_EVENT).isEqualTo("cb:waiting-for-human")
        assertThat(HandoverSignals.HUMAN_JOINED_EVENT).isEqualTo("cb:human-joined")
        assertThat(HandoverSignals.WAIT_OVER_EVENT).isEqualTo("cb:wait-over")
        assertThat(HandoverSignals.waitingDetail(mapOf("offline" to false, "reason" to "busy", "email" to "a@b.co")))
            .isEqualTo(WidgetSignal.WaitingForHuman(offline = false, reason = "busy", backAt = null, emailCaptured = true))
        assertThat(HandoverSignals.waitingDetail(mapOf("reason" to "offline", "backAt" to "2026-10-16T09:00:00Z")))
            .isEqualTo(WidgetSignal.WaitingForHuman(offline = false, reason = "offline", backAt = "2026-10-16T09:00:00Z", emailCaptured = false))
    }

    @Test
    fun `the bus - listeners get the signal, unsubscribe works, a throwing listener is contained`() {
        val bus = SignalBus()
        val got = mutableListOf<WidgetSignal>()
        val off = bus.on(HandoverSignals.WAIT_OVER_EVENT) { got += it }
        bus.on(HandoverSignals.WAIT_OVER_EVENT) { throw IllegalStateException("x") }
        assertThat(bus.emit(WidgetSignal.WaitOver("cancelled"))).isEqualTo(1)
        assertThat(got).containsExactly(WidgetSignal.WaitOver("cancelled"))
        off()
        bus.emit(WidgetSignal.WaitOver("ended"))
        assertThat(got).hasSize(1)
        assertThat(bus.emit(WidgetSignal.HumanJoined("Sam"))).isEqualTo(0)
    }
}
