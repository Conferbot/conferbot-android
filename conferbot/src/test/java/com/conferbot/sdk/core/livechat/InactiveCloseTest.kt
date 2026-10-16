package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.i18n.I18n
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

/**
 * The AI's inactivity close (core/livechat/LiveEvents.kt), the cases of the
 * React Native SDK's __tests__/core/inactiveClose.test.ts.
 */
class InactiveCloseTest {

    @After
    fun tearDown() { I18n.setWidgetLanguage("en", emptyList()) }

    @Test
    fun `continue_flow on a flow bot ends the chat and the flow goes on, anything else pauses`() {
        assertThat(LiveEvents.inactiveClosePlan(mapOf("action" to "continue_flow"), agentBotChat = false))
            .isEqualTo(LiveEvents.InactiveClosePlan(paused = false, continueFlow = true))
        assertThat(LiveEvents.inactiveClosePlan(mapOf("action" to "close"), agentBotChat = false).paused).isTrue()
        assertThat(LiveEvents.inactiveClosePlan(emptyMap(), agentBotChat = false).paused).isTrue()
        assertThat(LiveEvents.inactiveClosePlan(null, agentBotChat = false).continueFlow).isFalse()
    }

    @Test
    fun `an agent bot is only paused, even for continue_flow`() {
        assertThat(LiveEvents.inactiveClosePlan(mapOf("action" to "continue_flow"), agentBotChat = true))
            .isEqualTo(LiveEvents.InactiveClosePlan(paused = true, continueFlow = false))
    }

    @Test
    fun `its entry has its own id, the action and paused`() {
        val a = LiveEvents.inactiveCloseEntry(mapOf("action" to "close"), false, 7)
        val b = LiveEvents.inactiveCloseEntry(mapOf("action" to "close"), false, 7)
        assertThat(a.id).startsWith("ai-agent-inactive-close-7-")
        assertThat(a.id).isNotEqualTo(b.id)
        assertThat(a.paused).isTrue()
        assertThat(a.text).isEqualTo("Chat paused")
        assertThat(a.data).containsExactly("action", "close", "paused", true)

        val ended = LiveEvents.inactiveCloseEntry(mapOf("action" to "continue_flow"), false)
        assertThat(ended.paused).isFalse()
        assertThat(ended.text).isEqualTo("Chat ended")
        assertThat(ended.data).containsExactly("action", "continue_flow", "paused", false)
        // No action sent: none kept.
        assertThat(LiveEvents.inactiveCloseEntry(null, true).data).containsExactly("paused", true)
    }

    @Test
    fun `the line - paused with its note, ended without, an older entry is a pause`() {
        assertThat(LiveEvents.inactiveCloseLine(true))
            .isEqualTo(LiveEvents.InactiveCloseLine("Chat paused", "Send a message to pick up where you left off", true))
        assertThat(LiveEvents.inactiveCloseLine(false)).isEqualTo(LiveEvents.InactiveCloseLine("Chat ended", null, false))
        assertThat(LiveEvents.inactiveCloseLine(null).paused).isTrue()
    }

    @Test
    fun `the line in the visitor's language`() {
        I18n.setWidgetLanguage("de", emptyList())
        assertThat(LiveEvents.inactiveCloseLine(true).text).isEqualTo("Chat pausiert")
        assertThat(LiveEvents.inactiveCloseLine(true).note).isEqualTo("Schreib eine Nachricht, um dort weiterzumachen, wo du aufgehört hast")
        assertThat(LiveEvents.inactiveCloseLine(false).text).isEqualTo("Chat beendet")
        // The record keeps the English words.
        assertThat(LiveEvents.inactiveCloseEntry(null, false).text).isEqualTo("Chat paused")
    }
}
