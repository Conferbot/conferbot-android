package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.RecordEntry
import com.conferbot.sdk.models.Agent
import com.conferbot.sdk.models.AgentDetails
import com.conferbot.sdk.models.RecordItem
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * The handover lifecycle against a fake socket, chat and flow
 * (core/livechat/HandoverLifecycle.kt), as the React Native SDK's
 * __tests__/context/useHandoverLifecycle.test.ts.
 */
class HandoverLifecycleTest {

    private class FakeSocket : HandoverSocket {
        val listeners = mutableMapOf<String, MutableList<(Map<String, Any?>?) -> Unit>>()
        val emitted = mutableListOf<Pair<String, Map<String, Any?>>>()
        val answers = mutableMapOf<String, Map<String, Any?>?>()
        val joined = mutableListOf<String>()
        override fun on(event: String, fn: (Map<String, Any?>?) -> Unit) { listeners.getOrPut(event) { mutableListOf() } += fn }
        override fun emit(event: String, data: Map<String, Any?>) { emitted += event to data }
        override fun ask(event: String, data: Map<String, Any?>, timeoutMs: Long, onAnswer: (Map<String, Any?>?) -> Unit) {
            emitted += event to data
            onAnswer(answers[event])
        }
        override fun joinRoom(chatSessionId: String) { joined += chatSessionId }
        fun fire(event: String, data: Map<String, Any?>? = null) = listeners[event].orEmpty().forEach { it(data) }
    }

    private class FakeFlow : HandoverFlow {
        override var isAtHandover = false
        val calls = mutableListOf<String>()
        var ui: NodeUIState? = null
        override fun waitAt(nodeId: String, ui: NodeUIState): Boolean {
            calls += "waitAt:$nodeId"
            this.ui = ui
            isAtHandover = true
            return true
        }
        override fun resumeFrom(nodeId: String) { calls += "resumeFrom:$nodeId" }
        override fun submitResponse(response: Any) { calls += "submit:$response" }
        override fun handleAgentAccepted(agentName: String) { calls += "accepted:$agentName" }
        override val handoverNodeId: String? get() = if (isAtHandover) "ho" else null
        override fun stop() { calls += "stop" }
    }

    private class FakeHost : HandoverHost {
        var screen = listOf<RecordItem>()
        val saved = linkedMapOf<String, RecordEntry>()
        var open = true
        var waiting = false
        var live = false
        var current: Agent? = null
        override fun items() = screen
        override fun updateItems(transform: (List<RecordItem>) -> List<RecordItem>) { screen = transform(screen) }
        override fun addItem(item: RecordItem) { screen = screen + item }
        override fun record() = saved.values.toList()
        override fun saveEntry(entry: RecordEntry) {
            val old = saved[entry.id]
            saved[entry.id] = if (old != null) entry.copy(data = (old.data + entry.data).toMutableMap()) else entry
        }
        override fun sessionId() = "s1"
        override fun chatOpen() = open
        override fun flowWaiting() = waiting
        override fun setLiveMode(on: Boolean) { live = on }
        override fun setAgent(agent: Agent?) { current = agent }
    }

    private lateinit var socket: FakeSocket
    private lateinit var flow: FakeFlow
    private lateinit var host: FakeHost
    private lateinit var clock: HandoverWaitingTest.FakeClock
    private lateinit var life: HandoverLifecycle
    private val signals = mutableListOf<WidgetSignal>()

    @Before
    fun setUp() {
        socket = FakeSocket()
        flow = FakeFlow()
        host = FakeHost()
        clock = HandoverWaitingTest.FakeClock()
        life = HandoverLifecycle({ socket }, { flow }, host, clock) { Date(1_000) }
        life.setupListeners()
        listOf(HandoverSignals.WAITING_FOR_HUMAN_EVENT, HandoverSignals.HUMAN_JOINED_EVENT, HandoverSignals.WAIT_OVER_EVENT)
            .forEach { name -> life.onWidgetSignal(name) { signals += it } }
    }

    private val leftLines get() = host.screen.filterIsInstance<RecordItem.AgentLeftMessage>()
    private val escalation = mapOf("reason" to "escalated", "agentDetails" to mapOf("_id" to "ai-agent-1", "name" to "Ava", "isAIAgent" to true))
    private val person = mapOf("agentDetails" to mapOf("_id" to "u1", "name" to "Sam"))

    @Test
    fun `an AI escalating draws one Connecting line, a person settles it joined with the note`() {
        assertThat(life.onAgentLeft(escalation)).isEqualTo(AgentLeftKind.HANDOVER)
        life.onAgentLeft(escalation)
        assertThat(leftLines).hasSize(1)
        assertThat(leftLines[0].text).isEqualTo("Connecting you with the team…")
        assertThat(life.phase()).isEqualTo(HandoverPhase.CONNECTING)

        val plan = life.onAccepted(person)
        assertThat(plan.announce).isTrue()
        assertThat(plan.afterAI).isTrue()
        life.addJoin(AgentDetails("u1", "Sam"), plan.afterAI)
        assertThat(leftLines[0].settled).isEqualTo("joined")
        assertThat(host.saved.values.first { it.type == "agent-left-chat" }.data["settled"]).isEqualTo("joined")
        assertThat(host.screen.filterIsInstance<RecordItem.AgentJoinedMessage>().single().afterAI).isTrue()
        assertThat(life.phase()).isEqualTo(HandoverPhase.JOINED)
        assertThat(signals).containsExactly(WidgetSignal.HumanJoined("Sam"))
        // A repeat accept from the same person: nothing new to announce.
        assertThat(life.onAccepted(person).announce).isFalse()
    }

    @Test
    fun `an idle pause draws nothing, a person leaving says so`() {
        assertThat(life.onAgentLeft(mapOf("reason" to "visitor_inactive"))).isEqualTo(AgentLeftKind.IGNORE)
        assertThat(host.screen).isEmpty()
        assertThat(life.onAgentLeft(mapOf("agentDetails" to mapOf("name" to "Sam")))).isEqualTo(AgentLeftKind.LEFT)
        assertThat(leftLines.single().text).isEqualTo("Sam left the chat")
        assertThat(leftLines.single().handover).isFalse()
    }

    @Test
    fun `the 20s signal for a wait, and none once the server says it waits`() {
        life.onAgentLeft(escalation)
        clock.tick(20_000)
        assertThat(signals).containsExactly(WidgetSignal.WaitingForHuman(false, "busy", null, false))
        signals.clear()

        life.onAccepted(mapOf("agentDetails" to mapOf("_id" to "ai-agent-1", "isAIAgent" to true)))
        assertThat(leftLines[0].settled).isEqualTo("unavailable")
        life.onAgentLeft(escalation)
        socket.fire("handover-waiting", mapOf("chatSessionId" to "s1", "offline" to true))
        clock.tick(60_000)
        assertThat(signals).containsExactly(WidgetSignal.WaitingForHuman(true, "offline", null, false))
        assertThat(leftLines.last().settled).isEqualTo("unavailable")
    }

    @Test
    fun `a flow handover with an AI agent first has no 20s signal`() {
        life.flowWaitStarted(mapOf("assignedAIAgents" to listOf("a1")))
        clock.tick(30_000)
        assertThat(signals).isEmpty()
        life.flowWaitStarted(mapOf("assignedAIAgents" to emptyList<String>()))
        clock.tick(20_000)
        assertThat(signals).hasSize(1)
    }

    @Test
    fun `the chat ending settles the line ended and the wait is over`() {
        life.onAgentLeft(escalation)
        socket.fire("chat-ended", mapOf("chatSessionId" to "s1"))
        assertThat(leftLines[0].settled).isEqualTo("ended")
        assertThat(signals).containsExactly(WidgetSignal.WaitOver("ended"))
        // Another session's event is not ours.
        life.onAgentLeft(escalation)
        socket.fire("ai-agent-inactive-close", mapOf("chatSessionId" to "other"))
        assertThat(leftLines.last().settled).isNull()
    }

    @Test
    fun `leaving the queue - queue-self-resolve, the line, the record, the flow moves on, once`() {
        host.waiting = true
        flow.isAtHandover = true
        socket.answers["queue-self-resolve"] = mapOf("ok" to true)
        var result: Boolean? = null
        life.leaveQueue { result = it }
        assertThat(result).isTrue()
        assertThat(socket.emitted.first()).isEqualTo("queue-self-resolve" to mapOf<String, Any?>("chatSessionId" to "s1"))
        val line = host.screen.filterIsInstance<RecordItem.SystemMessage>().single()
        assertThat(line.text).isEqualTo("You left the queue")
        assertThat(line.kind).isEqualTo("queue-self-resolved")
        assertThat(host.saved.values.single().type).isEqualTo("queue-self-resolved")
        assertThat(flow.calls).containsExactly("submit:{action=cancel, queueLeft=true}")
        assertThat(signals).containsExactly(WidgetSignal.WaitOver("cancelled"))
        // The room's queue-left for the same leave: not twice.
        socket.fire("queue-left", mapOf("chatSessionId" to "s1", "reason" to "self_service"))
        assertThat(host.screen.filterIsInstance<RecordItem.SystemMessage>()).hasSize(1)
    }

    @Test
    fun `a person already has the chat - the visitor stays`() {
        socket.answers["queue-self-resolve"] = mapOf("ok" to false, "reason" to "not_waiting")
        socket.answers["live-chat-state"] = mapOf("status" to "live")
        var result: Boolean? = null
        life.leaveQueue { result = it }
        assertThat(result).isFalse()
        assertThat(socket.emitted.map { it.first }).containsExactly("queue-self-resolve", "live-chat-state").inOrder()
        assertThat(host.screen).isEmpty()
    }

    @Test
    fun `left the queue from another device`() {
        host.waiting = true
        flow.isAtHandover = true
        socket.fire("queue-left", mapOf("chatSessionId" to "s1", "reason" to "self_service"))
        assertThat(host.screen.single().let { (it as RecordItem.SystemMessage).kind }).isEqualTo("queue-self-resolved")
    }

    @Test
    fun `restore - a live chat comes back live with the person and the join line it missed`() {
        host.screen = listOf(
            RecordItem.UserLiveMessage("m1", Date(0), "hi"),
            RecordItem.AgentLeftMessage("l1", Date(0), "Connecting you with the team…", handover = true)
        )
        socket.answers["live-chat-state"] = mapOf("status" to "live", "agentDetails" to mapOf("_id" to "u1", "name" to "Sam"))
        var step: RestoreStep? = null
        life.resumeRestored(null) { step = it }
        assertThat(step).isEqualTo(RestoreStep.LIVE)
        assertThat(socket.joined).containsExactly("s1")
        assertThat(host.live).isTrue()
        assertThat(host.current?.name).isEqualTo("Sam")
        assertThat(leftLines.single().settled).isEqualTo("joined")
        assertThat(host.screen.filterIsInstance<RecordItem.AgentJoinedMessage>().single().afterAI).isTrue()
    }

    @Test
    fun `restore - still in the queue at the flow's step - back on its wait without asking again`() {
        socket.answers["live-chat-state"] = mapOf("status" to "waiting")
        var step: RestoreStep? = null
        life.resumeRestored("handover-1") { step = it }
        assertThat(step).isEqualTo(RestoreStep.WAIT)
        assertThat(flow.calls).containsExactly("waitAt:handover-1")
        assertThat((flow.ui as NodeUIState.HumanHandover).state).isEqualTo(NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT)
        assertThat(socket.emitted.map { it.first }).doesNotContain("initiate-handover")
        clock.tick(20_000)
        assertThat(signals).hasSize(1)
    }

    @Test
    fun `restore - ended while closed - says so and the flow carries on`() {
        host.screen = listOf(RecordItem.UserLiveMessage("m1", Date(0), "hi"))
        socket.answers["live-chat-state"] = mapOf("status" to "ended")
        var step: RestoreStep? = null
        life.resumeRestored("handover-1") { step = it }
        assertThat(step).isEqualTo(RestoreStep.CONTINUE)
        assertThat(host.screen.filterIsInstance<RecordItem.SystemMessage>().single().kind).isEqualTo("agent-chat-ended")
        assertThat(flow.calls).containsExactly("waitAt:handover-1", "submit:{action=continue}").inOrder()
    }

    @Test
    fun `restore - the request never went out - the step runs again, and a plain chat starts as before`() {
        socket.answers["live-chat-state"] = mapOf("status" to "none")
        var step: RestoreStep? = null
        life.resumeRestored("handover-1") { step = it }
        assertThat(step).isEqualTo(RestoreStep.ASK)
        assertThat(flow.calls).containsExactly("resumeFrom:handover-1")

        host.screen = listOf(RecordItem.BotMessage("b1", Date(0), "Hello"))
        life.resumeRestored(null) { step = it }
        assertThat(step).isEqualTo(RestoreStep.START)
        assertThat(socket.joined).containsExactly("s1")
    }

    @Test
    fun `back in the queue - the requeued line, no agent, and the handover step waits again`() {
        host.current = Agent(id = "u1", name = "Sam")
        host.live = true
        flow.isAtHandover = true
        socket.fire("live-chat-requeued", mapOf("chatSessionId" to "s1", "teamName" to "Billing"))
        val line = host.screen.filterIsInstance<RecordItem.SystemMessage>().single()
        assertThat(line.text).isEqualTo("Connecting you to Billing...")
        assertThat(line.kind).isEqualTo("live-chat-requeued")
        assertThat(host.saved.values.single().type).isEqualTo("live-chat-requeued")
        assertThat(host.current).isNull()
        assertThat(host.live).isFalse()
        assertThat(flow.calls).containsExactly("waitAt:ho")
        assertThat((flow.ui as NodeUIState.HumanHandover).state).isEqualTo(NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT)
        // Another session's requeue is not ours.
        socket.fire("live-chat-requeued", mapOf("chatSessionId" to "other"))
        assertThat(host.screen).hasSize(1)
    }

    @Test
    fun `back in the queue without a flow says another agent`() {
        socket.fire("live-chat-requeued", mapOf("chatSessionId" to "s1"))
        assertThat(host.screen.filterIsInstance<RecordItem.SystemMessage>().single().text).isEqualTo("Connecting you to another agent...")
        assertThat(flow.calls).isEmpty()
    }

    @Test
    fun `a transfer is kept in the record, not drawn`() {
        socket.fire("live-chat-transferred", mapOf("chatSessionId" to "s1", "entry" to mapOf("id" to "transfer-1", "text" to "Transferred from Maya to Sales", "kind" to "team")))
        assertThat(host.screen).isEmpty()
        val e = host.saved.getValue("transfer-1")
        assertThat(e.type).isEqualTo("live-chat-transfer")
        assertThat(e.text).isEqualTo("Transferred from Maya to Sales")
        assertThat(e.data["kind"]).isEqualTo("team")
        socket.fire("live-chat-transferred", mapOf("chatSessionId" to "s1", "entry" to mapOf("id" to 3)))
        assertThat(host.saved).hasSize(1)
    }

    @Test
    fun `blocked - the Connecting line settles ended, the flow stops, no live chat`() {
        life.onAgentLeft(escalation)
        host.live = true
        life.blocked()
        assertThat(leftLines.single().settled).isEqualTo("ended")
        assertThat(flow.calls).containsExactly("stop")
        assertThat(host.live).isFalse()
    }
}
