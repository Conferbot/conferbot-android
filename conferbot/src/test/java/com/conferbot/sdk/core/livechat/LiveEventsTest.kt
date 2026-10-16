package com.conferbot.sdk.core.livechat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Requeued, transferred, the same end, blocked and closed, as RN's __tests__/core/liveEvents.test.ts. */
class LiveEventsTest {

    private fun t(type: String) = type to null as String?
    private fun shape(shape: String) = null as String? to shape

    @Test
    fun `back in the queue says which team, or another agent`() {
        assertThat(LiveEvents.requeuedText(null)).isEqualTo("Connecting you to another agent...")
        assertThat(LiveEvents.requeuedText("  Sales <b> ")).isEqualTo("Connecting you to Sales b...")
        assertThat(LiveEvents.requeuedText(42)).isEqualTo("Connecting you to another agent...")
        assertThat(LiveEvents.requeuedText("x".repeat(80))).isEqualTo("Connecting you to ${"x".repeat(60)}...")
    }

    @Test
    fun `each event has its own id`() {
        val a = LiveEvents.eventId("requeued", 5)
        val b = LiveEvents.eventId("requeued", 5)
        assertThat(a).startsWith("requeued-5-")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `a transfer keeps the server's entry as it wrote it`() {
        assertThat(LiveEvents.transferOf(mapOf("entry" to mapOf("id" to "transfer-1", "text" to "Transferred from Maya to Sales", "time" to "t", "kind" to "team"))))
            .isEqualTo(LiveEvents.Transfer("transfer-1", "Transferred from Maya to Sales", "t", "team"))
        assertThat(LiveEvents.transferOf(mapOf("entry" to mapOf("id" to "x", "text" to "y".repeat(300))))!!.text).hasLength(200)
        assertThat(LiveEvents.transferOf(mapOf("entry" to mapOf("id" to 1, "text" to "x")))).isNull()
        assertThat(LiveEvents.transferOf(emptyMap())).isNull()
    }

    @Test
    fun `the same end again only while nothing live came after it`() {
        assertThat(LiveEvents.endedSinceLastLive(emptyList())).isFalse()
        assertThat(LiveEvents.endedSinceLastLive(listOf(t("agent-message"), t("agent-chat-ended")))).isTrue()
        assertThat(LiveEvents.endedSinceLastLive(listOf(t("agent-chat-ended"), t("bot-message")))).isTrue()
        assertThat(LiveEvents.endedSinceLastLive(listOf(t("agent-chat-ended"), t("agent-joined-message")))).isFalse()
        assertThat(LiveEvents.endedSinceLastLive(listOf(t("agent-chat-ended"), shape("user-live-message")))).isFalse()
        assertThat(LiveEvents.endedSinceLastLive(listOf(t("agent-chat-ended"), "user-message" to "user-live-message"))).isFalse()
    }

    @Test
    fun `reads the handshake refusal and the event`() {
        assertThat(LiveEvents.isBlockedError("visitor_blocked")).isTrue()
        assertThat(LiveEvents.isBlockedError("timeout")).isFalse()
        assertThat(LiveEvents.blockedText(mapOf("message" to "This conversation has been closed."))).isEqualTo(LiveEvents.CLOSED_TEXT)
        assertThat(LiveEvents.blockedText(emptyMap())).isEqualTo(LiveEvents.CLOSED_TEXT)
        assertThat(LiveEvents.blockedText(mapOf("message" to "  Closed by the team "))).isEqualTo("Closed by the team")
    }

    @Test
    fun `a blocked visitor gets the closed line, no input and no new chat`() {
        assertThat(LiveEvents.closedStateOf(emptyList(), blocked = LiveEvents.CLOSED_TEXT))
            .isEqualTo(LiveEvents.ClosedState(LiveEvents.ClosedKind.BLOCKED, LiveEvents.CLOSED_TEXT, newChat = false, inputOff = true))
    }

    @Test
    fun `an ended chat offers a new one once nothing is going on`() {
        val ended = listOf(t("agent-message"), t("agent-chat-ended"))
        assertThat(LiveEvents.closedStateOf(ended)).isEqualTo(LiveEvents.ClosedState(LiveEvents.ClosedKind.ENDED, "Chat ended", newChat = true, inputOff = true))
        assertThat(LiveEvents.closedStateOf(ended, stepWaiting = true)).isNull()
        assertThat(LiveEvents.closedStateOf(ended, live = true)).isNull()
        assertThat(LiveEvents.closedStateOf(listOf(t("agent-message")))).isNull()
    }
}
