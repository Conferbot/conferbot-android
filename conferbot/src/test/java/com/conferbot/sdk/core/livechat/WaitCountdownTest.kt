package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.i18n.I18n
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

/**
 * The wait card's max-wait countdown (core/livechat/LiveChat.kt), the cases
 * of the React Native SDK's __tests__/core/waitCountdown.test.ts.
 */
class WaitCountdownTest {

    @After
    fun tearDown() { I18n.setWidgetLanguage("en", emptyList()) }

    @Test
    fun `the clock, never below 0 00`() {
        assertThat(LiveChat.clock(65)).isEqualTo("1:05")
        assertThat(LiveChat.clock(600)).isEqualTo("10:00")
        assertThat(LiveChat.clock(9)).isEqualTo("0:09")
        assertThat(LiveChat.clock(0)).isEqualTo("0:00")
        assertThat(LiveChat.clock(-3)).isEqualTo("0:00")
        assertThat(LiveChat.clock(59.6)).isEqualTo("1:00")
        assertThat(LiveChat.clock(Double.NaN)).isEqualTo("0:00")
    }

    @Test
    fun `the line in the visitor's words`() {
        assertThat(LiveChat.maxWaitText(65)).isEqualTo("Max wait 1:05")
        I18n.setWidgetLanguage("de", emptyList())
        assertThat(LiveChat.maxWaitText(65)).isEqualTo("Max. Wartezeit 1:05")
    }

    @Test
    fun `the deadline - the step's max wait from now, 2 minutes when unset or not above 0`() {
        assertThat(LiveChat.waitDeadline(5, null, 1_000)).isEqualTo(301_000L)
        assertThat(LiveChat.waitDeadline("0.5", null, 0)).isEqualTo(30_000L)
        assertThat(LiveChat.waitDeadline(null, null, 0)).isEqualTo(120_000L)
        assertThat(LiveChat.waitDeadline(0, null, 0)).isEqualTo(120_000L)
        assertThat(LiveChat.waitDeadline(-1, null, 0)).isEqualTo(120_000L)
    }

    @Test
    fun `a restored wait counts from the server's seconds left`() {
        assertThat(LiveChat.waitDeadline(5, 65, 1_000)).isEqualTo(66_000L)
        assertThat(LiveChat.waitDeadline(5, 0, 1_000)).isEqualTo(1_000L)
        // A negative count is not the server's: the step's max wait.
        assertThat(LiveChat.waitDeadline(5, -2, 0)).isEqualTo(300_000L)
        // HandoverState's restored wait gives the seconds left (5 at least).
        val r = HandoverState.restoredLiveChat(mapOf("status" to "waiting", "waitEndsAt" to 166_000L), emptyList(), now = 100_000L)
        assertThat(r.remainingSeconds).isEqualTo(66L)
        assertThat(LiveChat.waitDeadline(5, r.remainingSeconds, 100_000L)).isEqualTo(166_000L)
    }

    @Test
    fun `whole seconds left, 0 once past`() {
        assertThat(LiveChat.secondsLeft(10_000, 0)).isEqualTo(10L)
        assertThat(LiveChat.secondsLeft(10_000, 500)).isEqualTo(10L)
        assertThat(LiveChat.secondsLeft(10_000, 9_001)).isEqualTo(1L)
        assertThat(LiveChat.secondsLeft(10_000, 10_000)).isEqualTo(0L)
        assertThat(LiveChat.secondsLeft(10_000, 99_000)).isEqualTo(0L)
        assertThat(LiveChat.maxWaitText(LiveChat.secondsLeft(65_000, 0))).isEqualTo("Max wait 1:05")
    }
}
