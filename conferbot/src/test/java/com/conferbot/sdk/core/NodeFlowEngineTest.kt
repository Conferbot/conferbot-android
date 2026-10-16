package com.conferbot.sdk.core

import com.conferbot.sdk.core.flow.FlowDriver
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.ChatState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The engine's own rules (flow-node spec "engine"): start, first edge, exact
 * ports, unknown steps, jumps, the end, and the handover hooks the live chat
 * uses. The full behaviour is FlowFixturesTest's; these pin the API.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NodeFlowEngineTest {
    private val clock = FlowDriver.VirtualClock()
    private lateinit var socket: FlowDriver.FakeSocket
    private lateinit var engine: NodeFlowEngine
    private val trace = mutableListOf<FlowTrace>()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        ChatState.reset()
        ChatState.initialize("s", "v", "b", null)
        socket = FlowDriver.FakeSocket(emptyMap(), clock)
        engine = NodeFlowEngine(socket, NodeFlowEngine.Config(dispatcher = Dispatchers.Unconfined, clock = { clock }, api = FlowDriver.ScriptedApi(emptyMap()), onTrace = { trace.add(it) }))
    }

    @After
    fun tearDown() {
        engine.stop()
        ChatState.reset()
        Dispatchers.resetMain()
    }

    private fun node(id: String, type: String, data: Map<String, Any?> = emptyMap()) = mapOf("id" to id, "type" to type, "data" to data)
    private fun edge(s: String, h: String?, t: String) = mapOf("id" to "$s-$t", "source" to s, "sourceHandle" to h, "target" to t)
    private fun entered() = trace.filterIsInstance<FlowTrace.Enter>().map { it.node }

    private fun run(nodes: List<Map<String, Any?>>, edges: List<Map<String, Any?>>) {
        engine.loadFlow(nodes, edges)
        engine.start()
        clock.settle()
    }

    @Test
    fun `starts at the first welcome node and takes the first edge whatever its handle`() {
        run(
            listOf(node("m0", "message-node", mapOf("text" to "x")), node("w", "welcome-node", mapOf("text" to "Hi")), node("a", "message-node", mapOf("text" to "A"))),
            listOf(edge("w", "source-7", "a"))
        )
        assertThat(entered()).containsExactly("w", "a").inOrder()
        assertThat(engine.isEnded).isTrue()
        assertThat(socket.emits).containsExactly("chat-flow-completed")
    }

    @Test
    fun `an unknown step shows nothing and continues on its first edge`() {
        run(listOf(node("w", "welcome-node", mapOf("text" to "Hi")), node("u", "mystery-node"), node("a", "message-node", mapOf("text" to "A"))), listOf(edge("w", "source", "u"), edge("u", null, "a")))
        assertThat(entered()).containsExactly("w", "u", "a").inOrder()
        assertThat(trace.filterIsInstance<FlowTrace.Unsupported>().single().type).isEqualTo("mystery-node")
    }

    @Test
    fun `a choice with no edge from its handle ends the chat`() {
        run(
            listOf(node("w", "welcome-node", mapOf("text" to "Hi")), node("c", "n-choices-node", mapOf("choices" to listOf(mapOf("id" to "1", "choiceText" to "One"), mapOf("id" to "2", "choiceText" to "Two")))), node("a", "message-node", mapOf("text" to "A"))),
            listOf(edge("w", "source", "c"), edge("c", "source-1", "a"))
        )
        assertThat(engine.isWaitingForInput).isTrue()
        assertThat(engine.currentUIState.value).isInstanceOf(NodeUIState.SingleChoice::class.java)
        engine.submitResponse(mapOf("id" to "2", "text" to "Two"))
        clock.settle()
        assertThat(entered()).containsExactly("w", "c").inOrder()
        assertThat(trace.filterIsInstance<FlowTrace.Port>().single().wired).isFalse()
        assertThat(engine.isEnded).isTrue()
    }

    @Test
    fun `a jump to a missing node ends the chat`() {
        run(listOf(node("w", "welcome-node", mapOf("text" to "Hi")), node("j", "jump-to-node", mapOf("nodeId" to "gone"))), listOf(edge("w", "source", "j")))
        assertThat(engine.isEnded).isTrue()
    }

    @Test
    fun `typed answers reach the waiting text step and refusals keep it waiting`() {
        run(listOf(node("w", "welcome-node", mapOf("text" to "Hi")), node("e", "ask-email-node", mapOf("questionText" to "Email?", "answerVariable" to "email"))), listOf(edge("w", "source", "e")))
        assertThat(engine.isAwaitingInput).isTrue()
        engine.submitResponse("nope")
        clock.settle()
        assertThat(engine.isAwaitingInput).isTrue()
        assertThat((engine.currentUIState.value as NodeUIState.TextInput).errorMessage).isEqualTo("Please enter a valid email address")
        engine.submitResponse("ana@acme.io")
        clock.settle()
        assertThat(ChatState.getAnswer("email")).isEqualTo("ana@acme.io")
        assertThat(engine.isEnded).isTrue()
    }

    @Test
    fun `waitAt puts a restored chat back on its handover step without running it`() {
        engine.loadFlow(listOf(node("w", "welcome-node"), node("ho", "human-handover-node"), node("a", "message-node", mapOf("text" to "A"))), listOf(edge("w", "source", "ho"), edge("ho", "source", "a")))
        val ui = NodeUIState.HumanHandover(state = NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT, nodeId = "ho")
        assertThat(engine.waitAt("ho", ui)).isTrue()
        assertThat(engine.isAtHandover).isTrue()
        assertThat(socket.emits).isEmpty()
        engine.submitResponse(mapOf("action" to "continue"))
        clock.settle()
        assertThat(entered()).containsExactly("a")
        assertThat(engine.waitAt("nope", ui)).isFalse()
    }

    @Test
    fun `a live chat's end carries the flow on from its handover step, never from the start`() {
        engine.loadFlow(listOf(node("w", "welcome-node"), node("ho", "human-handover-node"), node("a", "message-node", mapOf("text" to "A"))), listOf(edge("w", "source", "ho"), edge("ho", "source", "a")))
        engine.waitAt("ho", NodeUIState.HumanHandover(state = NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT, nodeId = "ho"))
        engine.handleAgentAccepted("Sam")
        engine.handleChatEnded()
        clock.settle()
        assertThat(entered()).containsExactly("a")
        assertThat(engine.handoverNodeId).isNull()
    }

    @Test
    fun `a live chat's end asks the handover step's post-chat survey questions first`() {
        val data = mapOf(
            "enablePostChatSurveyQuestions" to true,
            "postChatSurveyQuestions" to listOf(mapOf("id" to "q1", "questionText" to "How did we do?", "answerVariable" to "rating"))
        )
        engine.loadFlow(listOf(node("w", "welcome-node"), node("ho", "human-handover-node", data), node("a", "message-node", mapOf("text" to "A"))), listOf(edge("w", "source", "ho"), edge("ho", "source", "a")))
        engine.waitAt("ho", NodeUIState.HumanHandover(state = NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT, nodeId = "ho"))
        assertThat(engine.handoverNodeId).isEqualTo("ho")
        engine.handleAgentAccepted("Sam")
        engine.handleChatEnded()
        clock.settle()
        val survey = engine.currentUIState.value as NodeUIState.PostChatSurvey
        assertThat(survey.questions.single().question).isEqualTo("How did we do?")
        assertThat(entered()).isEmpty()
    }

    @Test
    fun `a greeting comes as a bot line of its own`() {
        val lines = mutableListOf<NodeUIState>()
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined).launch { engine.botMessages.collect { lines.add(it) } }
        run(listOf(node("w", "welcome-node", mapOf("text" to "Hi")), node("n", "ask-name-node", mapOf("questionText" to "Name?", "answerVariable" to "name"))), listOf(edge("w", "source", "n")))
        engine.submitResponse("ana")
        clock.settle()
        job.cancel()
        assertThat(lines.map { (it as NodeUIState.Message).text }).containsExactly("Nice to meet you, Ana")
    }

    @Test
    fun `the flow's end tells the chat once, a sub-flow's path end does not`() {
        val ends = mutableListOf<String>()
        engine.onFlowEnd = { ends.add(it) }
        // A sub-flow whose path just ends returns to Run sub-flow: not the end.
        run(
            listOf(
                node("w", "welcome-node", mapOf("text" to "Hi")),
                node("run", "run-subflow-node", mapOf("subflowId" to "sf", "inputs" to emptyList<Any>(), "outputs" to emptyList<Any>())),
                node("back", "message-node", mapOf("text" to "back")),
                node("sf", "subflow-node", mapOf("name" to "Inner")),
                node("in", "message-node", mapOf("text" to "inside"))
            ),
            listOf(edge("w", "source", "run"), edge("run", "source", "back"), edge("sf", "source", "in"))
        )
        assertThat(entered()).containsExactly("w", "run", "sf", "in", "back").inOrder()
        assertThat(ends).hasSize(1)
        assertThat(engine.isEnded).isTrue()
    }

    @Test
    fun `a step waiting for the visitor is not the end`() {
        val ends = mutableListOf<String>()
        engine.onFlowEnd = { ends.add(it) }
        run(listOf(node("w", "welcome-node", mapOf("text" to "Hi")), node("n", "ask-name-node", mapOf("questionText" to "Name?", "answerVariable" to "name"))), listOf(edge("w", "source", "n")))
        assertThat(ends).isEmpty()
        engine.submitResponse("ana")
        clock.settle()
        assertThat(ends).hasSize(1)
    }

    @Test
    fun `a listener's error never stops the flow`() {
        engine.onFlowEnd = { throw IllegalStateException("x") }
        run(listOf(node("w", "welcome-node", mapOf("text" to "Hi"))), emptyList())
        assertThat(engine.isEnded).isTrue()
        assertThat(engine.isFlowComplete.value).isTrue()
    }
}
