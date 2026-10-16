package com.conferbot.sdk.core

import com.conferbot.sdk.core.delight.Delight
import com.conferbot.sdk.core.flow.FlowDriver
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.core.state.RecordEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * "Edit" on an answer (RN __tests__/core/changeAnswer.test.ts; web index.ts
 * _noteQuestionPoint / _rewindToQuestion): the engine keeps a point for each
 * question, says what ran since, and goes back to it - the conversation's
 * state as it was, the question asked again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangeAnswerTest {
    private val clock = FlowDriver.VirtualClock()
    private lateinit var engine: NodeFlowEngine

    private fun node(id: String, type: String, data: Map<String, Any?> = emptyMap()) = mapOf("id" to id, "type" to type, "data" to data)
    private fun edge(s: String, t: String) = mapOf("id" to "e-$s-$t", "source" to s, "sourceHandle" to "source", "target" to t)

    private val nodes = listOf(
        node("w", "welcome-node", mapOf("text" to "<p>Hi</p>", "disableImage" to true)),
        node("q1", "ask-name-node", mapOf("questionText" to "<p>Your name?</p>", "answerVariable" to "name")),
        node("m", "message-node", mapOf("text" to "<p>Thanks {name}</p>")),
        node("q2", "ask-email-node", mapOf("questionText" to "<p>Your email?</p>", "answerVariable" to "email")),
        node("q3", "ask-file-node", mapOf("questionText" to "<p>A file?</p>"))
    )
    private val edges = listOf(edge("w", "q1"), edge("q1", "m"), edge("m", "q2"), edge("q2", "q3"))

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        ChatState.reset()
        ChatState.initialize("s1", "v", "b1", null)
        val socket = FlowDriver.FakeSocket(emptyMap(), clock)
        engine = NodeFlowEngine(socket, NodeFlowEngine.Config(dispatcher = Dispatchers.Unconfined, clock = { clock }, api = FlowDriver.ScriptedApi(emptyMap()), typingDelay = 0))
        engine.loadFlow(nodes, edges)
        engine.start()
        clock.settle()
    }

    @After
    fun tearDown() {
        engine.stop()
        ChatState.reset()
        Dispatchers.resetMain()
    }

    private fun answer(text: String) {
        engine.submitResponse(text)
        clock.settle()
    }

    @Test
    fun `a point for each question, before it is drawn`() {
        val points = engine.questionPoints()
        assertThat(points.map { it.nodeId }).containsExactly("q1")
        // Taken before the question's own record entry.
        assertThat(ChatState.record.value[points[0].recordLen].id).isEqualTo("q1")
    }

    @Test
    fun `what ran since a question - a message is safe to undo, a file step is not`() {
        answer("Jamie")
        val (p1, p2) = engine.questionPoints()
        assertThat(p2.nodeId).isEqualTo("q2")
        assertThat(engine.typesSince(p1)).containsExactly("message-node", "ask-email-node").inOrder()
        assertThat(Delight.canChangeAnswer(p1.type, engine.typesSince(p1).dropLast(1))).isTrue()
        answer("jamie@example.com")
        // The file step acts on the world: the email can no longer be changed.
        assertThat(engine.typesSince(engine.questionPoints()[1])).containsExactly("ask-file-node")
        assertThat(Delight.canChangeAnswer("ask-email-node", listOf("ask-file-node"))).isFalse()
    }

    @Test
    fun `goes back - the state as it was, the question asked again, a new answer taken`() {
        answer("Jamie")
        assertThat(ChatState.getAnswer("name")).isEqualTo("Jamie")
        assertThat(engine.currentNode).isEqualTo("q2")
        val transcriptBefore = ChatState.transcript.value.size

        val p1 = engine.questionPoints()[0]
        assertThat(engine.rewindTo(p1.id)).isTrue()
        clock.settle()

        assertThat(engine.currentNode).isEqualTo("q1")
        assertThat(engine.isWaitingForInput).isTrue()
        assertThat(ChatState.getAnswer("name")).isNull()
        // The answer and everything after it are gone; the question is asked again.
        assertThat(ChatState.transcript.value.size).isLessThan(transcriptBefore)
        assertThat(ChatState.transcript.value.filter { it.by == "user" }).isEmpty()
        // Points after it are gone; the question has a new one.
        assertThat(engine.questionPoints().map { it.nodeId }).containsExactly("q1")
        assertThat(engine.questionPoints()[0].id).isNotEqualTo(p1.id)

        answer("Sam")
        assertThat(ChatState.getAnswer("name")).isEqualTo("Sam")
        assertThat(engine.currentNode).isEqualTo("q2")
    }

    @Test
    fun `a point that is gone is refused, and once the chat is over`() {
        assertThat(engine.rewindTo(999)).isFalse()
        // A flow that ends after its one question.
        engine.loadFlow(nodes.take(2), edges.take(1))
        engine.start()
        clock.settle()
        val p1 = engine.questionPoints().single()
        answer("Jamie")
        assertThat(engine.isEnded).isTrue()
        assertThat(engine.rewindTo(p1.id)).isFalse()
    }

    @Test
    fun `a person in the conversation since counts as not safe`() {
        val p1 = engine.questionPoints()[0]
        ChatState.appendRecord(RecordEntry(id = "a1", shape = "agent-message", type = "agent-message", text = "Hi"))
        assertThat(engine.typesSince(p1)).contains("agent")
        assertThat(Delight.canChangeAnswer("ask-name-node", engine.typesSince(p1))).isFalse()
    }

    @Test
    fun `a saved state put back in place`() {
        ChatState.registerAnswerVariable("qx", "city")
        ChatState.fillAnswerVariable("qx", "Paris")
        val snap = ChatState.snapshot()
        ChatState.fillAnswerVariable("qx", "Rome")
        ChatState.restore(snap)
        assertThat(ChatState.getAnswer("city")).isEqualTo("Paris")
        assertThat(ChatState.chatSessionId).isEqualTo("s1")
    }
}
