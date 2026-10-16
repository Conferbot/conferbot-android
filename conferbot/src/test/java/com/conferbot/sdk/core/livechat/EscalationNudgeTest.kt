package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.state.RecordEntry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The AI escalation's max wait (core/livechat/HandoverWaiting.kt
 * EscalationWait, EscalationNudge), the cases of the React Native SDK's
 * __tests__/core/escalationNudge.test.ts, on a fake clock.
 */
class EscalationNudgeTest {

    @Test
    fun `the step's max wait plus 30s, 2 minutes when unset or not above 0`() {
        assertThat(EscalationWait.SERVER_GRACE_MS).isEqualTo(30_000L)
        assertThat(EscalationWait.nudgeMs(5)).isEqualTo(330_000L)
        assertThat(EscalationWait.nudgeMs("3")).isEqualTo(210_000L)
        assertThat(EscalationWait.nudgeMs(0.5)).isEqualTo(60_000L)
        assertThat(EscalationWait.nudgeMs(null)).isEqualTo(150_000L)
        assertThat(EscalationWait.nudgeMs(0)).isEqualTo(150_000L)
        assertThat(EscalationWait.nudgeMs(-4)).isEqualTo(150_000L)
        assertThat(EscalationWait.nudgeMs("soon")).isEqualTo(150_000L)
    }

    @Test
    fun `only an AI handing the visitor on starts it`() {
        val ai = mapOf("isAIAgent" to true)
        assertThat(EscalationWait.starts(mapOf("reason" to "escalated", "agentDetails" to ai))).isTrue()
        assertThat(EscalationWait.starts(mapOf("handover" to true, "agentDetails" to ai))).isTrue()
        // A person's escalation, a person leaving, an idle pause: no.
        assertThat(EscalationWait.starts(mapOf("reason" to "escalated", "agentDetails" to mapOf("name" to "Sam")))).isFalse()
        assertThat(EscalationWait.starts(mapOf("reason" to "visitor_inactive", "agentDetails" to ai))).isFalse()
        assertThat(EscalationWait.starts(null)).isFalse()
    }

    @Test
    fun `its step - the flow's, else the agent bot's silent one in the record`() {
        val flowStep = mapOf<String, Any?>("maxWaitTime" to 4)
        assertThat(EscalationWait.handoverData(flowStep, emptyList())).isEqualTo(flowStep)
        val record = listOf(
            RecordEntry(id = "h1", shape = "human-handover-node", type = "human-handover-node", data = mutableMapOf("maxWaitTime" to 1)),
            RecordEntry(id = "m", shape = "user-message", type = "user-message", text = "hi"),
            RecordEntry(id = "h2", shape = "human-handover-node", type = "", data = mutableMapOf("maxWaitTime" to 3))
        )
        assertThat(EscalationWait.handoverData(null, record)).containsEntry("maxWaitTime", 3)
        assertThat(EscalationWait.handoverData(null, record.take(2))).containsEntry("maxWaitTime", 1)
        assertThat(EscalationWait.handoverData(null, null)).isNull()
    }

    @Test
    fun `fires once at the deadline, cleared it never does, armed again it starts over`() {
        val c = HandoverWaitingTest.FakeClock()
        var nudged = 0
        val n = EscalationNudge(c) { nudged++ }
        n.arm(1)
        assertThat(n.pending).isTrue()
        c.tick(89_999)
        assertThat(nudged).isEqualTo(0)
        c.tick(1)
        assertThat(nudged).isEqualTo(1)
        assertThat(n.pending).isFalse()
        c.tick(600_000)
        assertThat(nudged).isEqualTo(1)

        n.arm(1)
        c.tick(60_000)
        n.clear()
        c.tick(600_000)
        assertThat(nudged).isEqualTo(1)

        // One timer per chat: a second escalation restarts it.
        n.arm(1)
        c.tick(60_000)
        n.arm(1)
        c.tick(60_000)
        assertThat(nudged).isEqualTo(1)
        c.tick(30_000)
        assertThat(nudged).isEqualTo(2)
        assertThat(c.size).isEqualTo(0)
    }
}
