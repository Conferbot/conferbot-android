package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.NodeResult
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.testutils.TestFlowContext
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.conferbot.sdk.core.flow.FlowContextElement
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** delay-node and the flow side of human-handover-node (web special.ts). */
@OptIn(ExperimentalCoroutinesApi::class)
class SpecialNodeHandlersTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        ChatState.reset()
        ChatState.initialize("s", "v", "b", "w")
    }

    @After
    fun tearDown() {
        ChatState.reset()
        Dispatchers.resetMain()
    }

    @Test
    fun `delay waits data delay seconds, none means no wait`() {
        val ctx = TestFlowContext()
        assertThat(ctx.process(DelayNodeHandler(), mapOf("delay" to 2))).isEqualTo(NodeResult.DelayedProceed(2000))
        assertThat(ctx.process(DelayNodeHandler(), emptyMap())).isEqualTo(NodeResult.Proceed())
    }

    @Test
    fun `handover asks pre-chat questions in the composer, then initiates`() {
        val ctx = TestFlowContext(nodeId = "ho")
        val h = HumanHandoverNodeHandler()
        val data = mapOf(
            "handoverMessage" to "<p>Connecting…</p>", "maxWaitTime" to 2, "fallbackMessage" to "<p>Busy</p>",
            "enablePreChatQuestions" to true, "preChatQuestions" to listOf(mapOf("id" to "pq1", "questionText" to "<p>Email?</p>", "answerVariable" to "email"))
        )
        val first = (ctx.process(h, data) as NodeResult.DisplayUI).uiState
        assertThat((first as NodeUIState.TextInput).questionText).isEqualTo("Email?")
        assertThat(ctx.respond(h, data, "bad")).isInstanceOf(NodeResult.Invalid::class.java)
        val waiting = (ctx.respond(h, data, "ana@acme.io") as NodeResult.DisplayUI).uiState as NodeUIState.HumanHandover
        assertThat(waiting.state).isEqualTo(NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT)
        assertThat(ctx.emits.map { it.first }).containsExactly("initiate-handover")
        assertThat(ChatState.getAnswer("email")).isEqualTo("ana@acme.io")
    }

    @Test
    fun `nobody free says the fallback and carries on when leave a message is off`() {
        val ctx = TestFlowContext(nodeId = "ho", customizations = mapOf("chatLeaveMessage" to false))
        val h = HumanHandoverNodeHandler()
        ctx.process(h, mapOf("handoverMessage" to "x", "fallbackMessage" to "<p>All busy</p>"))
        // The engine runs a step's socket listener in the step's context.
        runBlocking { withContext(FlowContextElement(ctx)) { ctx.listeners.getValue("no-agents-available").invoke(emptyMap()) } }
        assertThat(ctx.said).containsExactly("All busy")
        assertThat(ctx.responded).containsExactly(mapOf("action" to "continue"))
    }

    @Test
    fun `cancel leaves the queue and moves on`() {
        val ctx = TestFlowContext(nodeId = "ho")
        val r = ctx.respond(HumanHandoverNodeHandler(), emptyMap(), mapOf("action" to "cancel"))
        assertThat(r).isEqualTo(NodeResult.Proceed())
        assertThat(ctx.emits.single().first).isEqualTo("queue-self-resolve")
    }
}
