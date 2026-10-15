package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.state.RecordEntry
import com.conferbot.sdk.models.AgentDetails
import com.conferbot.sdk.models.RecordItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Date

/**
 * The handover lifecycle (core/livechat/HandoverState.kt): the cases of the
 * React Native SDK's __tests__/core/handoverState.test.ts (the web widget's
 * tests/handoverLifecycle.test.js), plus the socket readings and the two
 * records it reads.
 */
class HandoverStateTest {

    private val aiJoin = HandoverEntry("agent-joined-message", agentId = "ai-agent-1")
    private val escalated = HandoverEntry("agent-left-chat", handover = true)
    private val human = HandoverEntry("agent-joined-message", agentId = "u1")
    private val humanLeft = HandoverEntry("agent-left-chat")
    private val ended = HandoverEntry("agent-chat-ended")
    private val reply = HandoverEntry("bot-message", shape = "agent-message")

    @Test
    fun `phase - bot, connecting, a person joined, left, ended`() {
        assertThat(HandoverState.phase(emptyList())).isEqualTo(HandoverPhase.NONE)
        assertThat(HandoverState.phase(listOf(reply))).isEqualTo(HandoverPhase.NONE)
        assertThat(HandoverState.phase(listOf(aiJoin, reply))).isEqualTo(HandoverPhase.NONE)
        assertThat(HandoverState.phase(listOf(aiJoin, reply, escalated))).isEqualTo(HandoverPhase.CONNECTING)
        assertThat(HandoverState.phase(listOf(escalated, aiJoin.copy(ai = true), reply))).isEqualTo(HandoverPhase.CONNECTING)
        assertThat(HandoverState.phase(listOf(escalated, human, reply))).isEqualTo(HandoverPhase.JOINED)
        assertThat(HandoverState.phase(listOf(escalated, human, humanLeft))).isEqualTo(HandoverPhase.NONE)
        assertThat(HandoverState.phase(listOf(escalated, human, ended))).isEqualTo(HandoverPhase.NONE)
        assertThat(HandoverState.phase(listOf(escalated, HandoverEntry("agent-declined-message")))).isEqualTo(HandoverPhase.NONE)
        assertThat(HandoverState.phase(listOf(escalated, HandoverEntry("queue-self-resolved")))).isEqualTo(HandoverPhase.NONE)
        assertThat(HandoverState.phase(listOf(HandoverEntry("agent-joined-message", agentId = "u2", takeover = true)))).isEqualTo(HandoverPhase.JOINED)
    }

    @Test
    fun `Talk to a person is offered only when nobody is coming or here`() {
        assertThat(HandoverState.talkToPersonAllowed(listOf(aiJoin, reply))).isTrue()
        assertThat(HandoverState.talkToPersonAllowed(listOf(aiJoin, escalated))).isFalse()
        assertThat(HandoverState.talkToPersonAllowed(listOf(escalated, human))).isFalse()
        assertThat(HandoverState.talkToPersonAllowed(listOf(escalated, human, humanLeft, reply))).isTrue()
    }

    @Test
    fun `the same agent joining again is a repeat, another agent is news`() {
        assertThat(HandoverState.isRepeatJoin(listOf(escalated, human), "u1")).isTrue()
        assertThat(HandoverState.isRepeatJoin(listOf(escalated, human, reply), "u1")).isTrue()
        assertThat(HandoverState.isRepeatJoin(listOf(escalated, human), "u2")).isFalse()
        assertThat(HandoverState.isRepeatJoin(listOf(escalated, human, humanLeft), "u1")).isFalse()
        assertThat(HandoverState.isRepeatJoin(listOf(escalated), "u1")).isFalse()
        assertThat(HandoverState.isRepeatJoin(listOf(escalated, human), null)).isFalse()
    }

    @Test
    fun `outcome of a handover line, from what came after it`() {
        fun at(vararg r: HandoverEntry) = HandoverState.outcomeAt(r.toList(), 0)
        assertThat(at(escalated)).isNull()
        assertThat(at(escalated, reply)).isNull()
        assertThat(at(escalated, human)).isEqualTo(HandoverOutcome.JOINED)
        assertThat(at(escalated, HandoverEntry("agent-joined-message", agentId = "u2", takeover = true))).isEqualTo(HandoverOutcome.JOINED)
        assertThat(at(escalated, aiJoin)).isEqualTo(HandoverOutcome.UNAVAILABLE)
        assertThat(at(escalated, HandoverEntry("agent-declined-message"))).isEqualTo(HandoverOutcome.UNAVAILABLE)
        assertThat(at(escalated, HandoverEntry("queue-self-resolved"))).isEqualTo(HandoverOutcome.CANCELLED)
        assertThat(at(escalated, ended)).isEqualTo(HandoverOutcome.ENDED)
        assertThat(at(escalated, HandoverEntry("ai-agent-inactive-close"))).isEqualTo(HandoverOutcome.ENDED)
        assertThat(at(escalated, reply, escalated)).isEqualTo(HandoverOutcome.UNAVAILABLE)
        assertThat(at(escalated.copy(settled = HandoverOutcome.UNAVAILABLE), human)).isEqualTo(HandoverOutcome.UNAVAILABLE)
    }

    @Test
    fun `a restored record - finished waits settle, a join after an AI handover keeps its note`() {
        val a = HandoverState.annotateRestored(listOf(aiJoin, escalated, human, reply, humanLeft))
        assertThat(a.changed).isEqualTo(2)
        assertThat(a.settle).containsExactly(1, HandoverOutcome.JOINED)
        assertThat(a.afterAI).containsExactly(2)

        assertThat(HandoverState.annotateRestored(listOf(aiJoin, escalated, reply)).changed).isEqualTo(0)

        val later = HandoverState.annotateRestored(listOf(escalated, ended, HandoverEntry("agent-joined-message", agentId = "u3")))
        assertThat(later.settle).containsExactly(0, HandoverOutcome.ENDED)
        assertThat(later.afterAI).isEmpty()
    }

    @Test
    fun `the line - pending words, then each of the four outcomes`() {
        assertThat(HandoverState.lineText(null)).isEqualTo("Connecting you with the team…")
        assertThat(HandoverState.lineText(HandoverOutcome.JOINED)).isEqualTo("Connected with the team")
        assertThat(HandoverState.lineText(HandoverOutcome.UNAVAILABLE)).isEqualTo("The team couldn't join just now")
        assertThat(HandoverState.lineText(HandoverOutcome.CANCELLED)).isEqualTo("You left the queue")
        assertThat(HandoverState.lineText(HandoverOutcome.ENDED)).isEqualTo("Chat ended before the team joined")
        assertThat(HandoverState.agentLeftText("Ava", true)).isEqualTo("Connecting you with the team…")
        assertThat(HandoverState.agentLeftText("Sam", false)).isEqualTo("Sam left the chat")
        assertThat(HandoverState.agentLeftText("", false)).isEqualTo("The agent left the chat")
        assertThat(HandoverState.joinedText("Sam")).isEqualTo("Sam joined the chat")
        assertThat(HandoverState.joinedText(null)).isEqualTo("An agent joined the chat")
    }

    // ---------- socket readings ----------

    @Test
    fun `agent-left - an AI escalating is a handover, an idle pause is nothing, a person leaves`() {
        assertThat(HandoverState.agentLeftKind(mapOf("reason" to "escalated", "agentDetails" to mapOf("name" to "Ava")))).isEqualTo(AgentLeftKind.HANDOVER)
        assertThat(HandoverState.agentLeftKind(mapOf("handover" to true))).isEqualTo(AgentLeftKind.HANDOVER)
        assertThat(HandoverState.agentLeftKind(mapOf("agentDetails" to mapOf("name" to "Ava", "isAIAgent" to true)))).isEqualTo(AgentLeftKind.HANDOVER)
        assertThat(HandoverState.agentLeftKind(mapOf("reason" to "visitor_inactive", "agentDetails" to mapOf("isAIAgent" to true)))).isEqualTo(AgentLeftKind.IGNORE)
        assertThat(HandoverState.agentLeftKind(mapOf("agentDetails" to mapOf("name" to "Sam")))).isEqualTo(AgentLeftKind.LEFT)
        assertThat(HandoverState.agentLeftKind(null)).isEqualTo(AgentLeftKind.LEFT)
    }

    @Test
    fun `agent-accepted - a person settles joined, the AI taking it back settles unavailable, a repeat is not announced`() {
        val p = HandoverState.acceptedPlan(listOf(aiJoin, escalated), mapOf("agentDetails" to mapOf("_id" to "u1", "name" to "Sam")))
        assertThat(p).isEqualTo(AcceptedPlan(ai = false, outcome = HandoverOutcome.JOINED, announce = true, afterAI = true, humanJoined = true))
        val back = HandoverState.acceptedPlan(listOf(aiJoin, escalated), mapOf("agentDetails" to mapOf("_id" to "ai-agent-1", "isAIAgent" to true)))
        assertThat(back.outcome).isEqualTo(HandoverOutcome.UNAVAILABLE)
        assertThat(back.humanJoined).isFalse()
        assertThat(back.afterAI).isFalse()
        val again = HandoverState.acceptedPlan(listOf(escalated, human), mapOf("agentDetails" to mapOf("_id" to "u1")))
        assertThat(again.announce).isFalse()
        assertThat(again.humanJoined).isFalse()
        // A flow handover with no AI before it: no note.
        assertThat(HandoverState.acceptedPlan(emptyList(), mapOf("agentDetails" to mapOf("_id" to "u1"))).afterAI).isFalse()
    }

    @Test
    fun `leaving the queue - queue-self-resolve, and stay only when a person already has it`() {
        assertThat(HandoverState.queueSelfResolvePayload("s1")).isEqualTo(mapOf("chatSessionId" to "s1"))
        assertThat(HandoverState.queueSelfResolvePayload("s1", "a9")).isEqualTo(mapOf("chatSessionId" to "s1", "articleId" to "a9"))
        assertThat(HandoverState.queueLeaves(mapOf("ok" to true))).isTrue()
        assertThat(HandoverState.queueLeaves(mapOf("ok" to false, "reason" to "not_waiting"))).isTrue()
        assertThat(HandoverState.queueLeaves(null)).isTrue()
        assertThat(HandoverState.queueLeaves(mapOf("ok" to false, "reason" to "agent_joined"))).isFalse()
        // A person accepted (ticket in progress): not_waiting, and live-chat-state says live.
        assertThat(HandoverState.queueLeaveNeedsState(mapOf("ok" to false, "reason" to "not_waiting"))).isTrue()
        assertThat(HandoverState.queueLeaveNeedsState(mapOf("ok" to true))).isFalse()
        assertThat(HandoverState.queueLeaves(mapOf("ok" to false, "reason" to "not_waiting"), mapOf("status" to "live"))).isFalse()
        assertThat(HandoverState.queueLeaves(mapOf("ok" to false, "reason" to "not_waiting"), mapOf("status" to "ended"))).isTrue()
    }

    // ---------- restore ----------

    @Test
    fun `asks the server only for a chat in a live episode or at its handover`() {
        val bot = HandoverEntry("bot-message")
        val agentMsg = HandoverEntry("agent-message")
        assertThat(HandoverState.restoreNeedsLiveChatState(emptyList())).isFalse()
        assertThat(HandoverState.restoreNeedsLiveChatState(listOf(bot))).isFalse()
        assertThat(HandoverState.restoreNeedsLiveChatState(listOf(bot), true)).isTrue()
        assertThat(HandoverState.restoreNeedsLiveChatState(listOf(human, agentMsg))).isTrue()
        assertThat(HandoverState.restoreNeedsLiveChatState(listOf(aiJoin, escalated))).isTrue()
        // The episode ended: over.
        assertThat(HandoverState.restoreNeedsLiveChatState(listOf(human, agentMsg, ended))).isFalse()
        assertThat(HandoverState.restoreNeedsLiveChatState(listOf(human, ended), true)).isFalse()
        // A new episode after an ended one.
        assertThat(HandoverState.restoreNeedsLiveChatState(listOf(human, ended, HandoverEntry("user-message", shape = "user-live-message")))).isTrue()
    }

    @Test
    fun `live - the person, the join line it missed, the line settled`() {
        val r = HandoverState.restoredLiveChat(
            mapOf("status" to "live", "agentDetails" to mapOf("_id" to "u1", "name" to "Sam", "email" to "s@x.io")),
            listOf(aiJoin, escalated)
        )
        assertThat(r).isEqualTo(RestoredLiveChat("live", RestoredAgent("u1", "Sam", "s@x.io"), HandoverOutcome.JOINED, true, true, null))
        // Already drawn before the restart: not again.
        assertThat(HandoverState.restoredLiveChat(mapOf("status" to "live", "agentDetails" to mapOf("_id" to "u1", "name" to "Sam")), listOf(escalated, human)).drawJoin).isFalse()
        // The AI on the chat is not a person.
        assertThat(HandoverState.restoredLiveChat(mapOf("status" to "live", "agentDetails" to mapOf("_id" to "ai-agent-1", "isAIAgent" to true)), listOf(escalated)).status).isEqualTo("none")
    }

    @Test
    fun `waiting keeps the line pending with the time left, ended and fallback settle it`() {
        val now = 1_000_000L
        val w = HandoverState.restoredLiveChat(mapOf("status" to "waiting", "waitEndsAt" to now + 61_000), listOf(escalated), now)
        assertThat(w.status).isEqualTo("waiting")
        assertThat(w.settle).isNull()
        assertThat(w.remainingSeconds).isEqualTo(61)
        assertThat(HandoverState.restoredLiveChat(mapOf("status" to "waiting", "waitEndsAt" to now - 5000), emptyList(), now).remainingSeconds).isEqualTo(5)
        assertThat(HandoverState.restoredLiveChat(mapOf("status" to "ended"), listOf(escalated)).settle).isEqualTo(HandoverOutcome.ENDED)
        assertThat(HandoverState.restoredLiveChat(mapOf("status" to "fallback"), listOf(escalated)).status).isEqualTo("fallback")
        assertThat(HandoverState.restoredLiveChat(mapOf("status" to "fallback"), listOf(escalated)).settle).isEqualTo(HandoverOutcome.UNAVAILABLE)
        assertThat(HandoverState.restoredLiveChat(mapOf("status" to "none"), listOf(reply)).settle).isNull()
        assertThat(HandoverState.restoredLiveChat(null, emptyList()).status).isEqualTo("none")
    }

    @Test
    fun `what a restored chat does next`() {
        fun at(status: String) = HandoverState.restoredLiveChat(mapOf("status" to status), emptyList())
        val live = HandoverState.restoredLiveChat(mapOf("status" to "live", "agentDetails" to mapOf("_id" to "u1", "name" to "Sam")), emptyList())
        assertThat(HandoverState.restoreStep(live, true)).isEqualTo(RestoreStep.LIVE)
        assertThat(HandoverState.restoreStep(live, false)).isEqualTo(RestoreStep.LIVE)
        assertThat(HandoverState.restoreStep(at("waiting"), true)).isEqualTo(RestoreStep.WAIT)
        // Waiting after an AI escalation: the AI keeps the chat meanwhile.
        assertThat(HandoverState.restoreStep(at("waiting"), false)).isEqualTo(RestoreStep.LIVE)
        assertThat(HandoverState.restoreStep(at("ended"), true, listOf(human))).isEqualTo(RestoreStep.CONTINUE)
        assertThat(HandoverState.restoreStep(at("fallback"), true, emptyList())).isEqualTo(RestoreStep.CONTINUE)
        assertThat(HandoverState.restoreStep(at("none"), true, listOf(HandoverEntry("bot-message")))).isEqualTo(RestoreStep.ASK)
        assertThat(HandoverState.restoreStep(at("none"), true, listOf(human, reply))).isEqualTo(RestoreStep.CONTINUE)
        assertThat(HandoverState.restoreStep(at("ended"), false)).isEqualTo(RestoreStep.START)
        assertThat(HandoverState.restoreStep(at("none"), false)).isEqualTo(RestoreStep.START)
    }

    // ---------- the two records ----------

    private val t = Date(0)

    @Test
    fun `the screen's record reads as handover entries`() {
        val items = listOf(
            RecordItem.AgentJoinedMessage("j1", t, AgentDetails("ai-agent-1", "Ava", isAIAgent = true)),
            RecordItem.AgentLeftMessage("l1", t, "Connecting you with the team…", handover = true),
            RecordItem.SystemMessage("q1", t, "You left the queue", kind = "queue-self-resolved"),
            RecordItem.BotMessage("agent-declined-1", t, "Nobody is around"),
            RecordItem.UserLiveMessage("u1", t, "hi")
        )
        val e = HandoverState.entriesOfItems(items)
        assertThat(e.map { it.type }).containsExactly(
            "agent-joined-message", "agent-left-chat", "queue-self-resolved", "agent-declined-message", "user-live-message"
        ).inOrder()
        assertThat(e[0].ai).isTrue()
        assertThat(e[1].handover).isTrue()
        assertThat(HandoverState.outcomeAt(e, 1)).isEqualTo(HandoverOutcome.CANCELLED)
    }

    @Test
    fun `settling touches only pending handover lines, on screen and in the record`() {
        val items = listOf(
            RecordItem.AgentLeftMessage("a", t, "x", handover = true, settled = "unavailable"),
            RecordItem.AgentLeftMessage("b", t, "Connecting you with the team…", handover = true),
            RecordItem.AgentLeftMessage("c", t, "Sam left the chat")
        )
        val out = HandoverState.settleItems(items, HandoverOutcome.JOINED)
        assertThat((out[0] as RecordItem.AgentLeftMessage).settled).isEqualTo("unavailable")
        assertThat((out[1] as RecordItem.AgentLeftMessage).settled).isEqualTo("joined")
        assertThat((out[1] as RecordItem.AgentLeftMessage).text).isEqualTo("Connected with the team")
        assertThat((out[2] as RecordItem.AgentLeftMessage).settled).isNull()
        assertThat(HandoverState.settleItems(out, HandoverOutcome.JOINED)).isSameInstanceAs(out)
        assertThat(HandoverState.hasPendingLine(HandoverState.entriesOfItems(items))).isTrue()
        assertThat(HandoverState.hasPendingLine(HandoverState.entriesOfItems(out))).isFalse()

        val record = listOf(
            RecordEntry("b", "agent-left-chat", "agent-left-chat", "Connecting you with the team…", data = mutableMapOf("handover" to true)),
            RecordEntry("c", "agent-left-chat", "agent-left-chat", "Sam left the chat")
        )
        val saved = HandoverState.settledRecordEntries(record, HandoverOutcome.CANCELLED)
        assertThat(saved.map { it.id }).containsExactly("b")
        assertThat(saved[0].data["settled"]).isEqualTo("cancelled")
        assertThat(saved[0].text).isEqualTo("You left the queue")
        // The input is left alone.
        assertThat(record[0].data).doesNotContainKey("settled")
    }

    @Test
    fun `a restored screen and record are annotated in place`() {
        val items = listOf(
            RecordItem.AgentLeftMessage("l", t, "Connecting you with the team…", handover = true),
            RecordItem.AgentJoinedMessage("j", t, AgentDetails("u1", "Sam"))
        )
        val out = HandoverState.annotateItems(items)
        assertThat((out[0] as RecordItem.AgentLeftMessage).settled).isEqualTo("joined")
        assertThat((out[1] as RecordItem.AgentJoinedMessage).afterAI).isTrue()

        val record = listOf(
            RecordEntry("l", "agent-left-chat", "agent-left-chat", data = mutableMapOf("handover" to true)),
            RecordEntry("j", "agent-joined-message", "agent-joined-message", data = mutableMapOf("agentId" to "u1"))
        )
        val changed = HandoverState.annotatedRecordEntries(record)
        assertThat(changed.map { it.id }).containsExactly("l", "j").inOrder()
        assertThat(changed[0].data["settled"]).isEqualTo("joined")
        assertThat(changed[1].data["afterAI"]).isEqualTo(true)
    }
}
