package com.conferbot.sdk.core.delight

import com.conferbot.sdk.core.livechat.SignalTimer
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.models.RecordItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Date

/**
 * The human touches wired (core/delight/DelightExtras.kt; RN
 * context/hooks/useDelight.ts): the tapback and burst a beat after an
 * answer, "Edit" once the bot is quiet and its cut, the nudge on a question.
 */
class DelightExtrasTest {

    private class Clock : SignalTimer {
        var now = 1_000_000L
        private var seq = 0
        private val timers = mutableListOf<Triple<Long, Int, () -> Unit>>()
        override fun set(ms: Long, fn: () -> Unit): Any { val id = ++seq; timers.add(Triple(now + ms, id, fn)); return id }
        override fun clear(handle: Any) { timers.removeAll { it.second == handle } }
        fun advance(ms: Long) {
            val end = now + ms
            while (true) {
                val next = timers.filter { it.first <= end }.minByOrNull { it.first } ?: break
                timers.remove(next)
                now = next.first
                next.third()
            }
            now = end
        }
    }

    private class Engine(var since: List<String> = emptyList()) : ChangeAnswerEngine {
        val points = mutableListOf(Triple(1, "q1", "ask-name-node"))
        var rewound: Int? = null
        override fun points() = points
        override fun typesSince(pointId: Int) = since
        override fun rewindTo(pointId: Int): Boolean { rewound = pointId; return true }
    }

    private val clock = Clock()
    private val engine = Engine()
    private var rows = listOf<RecordItem>(RecordItem.BotMessage("w", Date(clock.now), "Hi"))
    private var locked = false
    private var customizations: Map<String, Any?> = emptyMap()
    private val extras = DelightExtras(
        customizations = { customizations },
        engine = { engine },
        record = { rows },
        setRecord = { fn -> rows = fn(rows) },
        locked = { locked },
        chatVisible = { true },
        reducedMotion = { false },
        timer = clock,
        now = { clock.now }
    )

    private fun answerName() {
        rows = rows + RecordItem.BotMessage("q", Date(clock.now), "Your name?", nodeId = "q1") +
            RecordItem.UserInputResponse("a", Date(clock.now), "Jamie", answerOf = AnswerOf("q1", "ask-name-node", "Jamie"))
        extras.onRecord(rows)
    }

    @Test
    fun `the tapback 550 ms after an answer, nothing for what was there first`() {
        extras.onRecord(rows)
        answerName()
        clock.advance(Delight.REACT_AFTER_MS - 1)
        assertThat(extras.touches.value).isEmpty()
        clock.advance(1)
        assertThat(extras.touches.value["a"]).isEqualTo(AnswerTouch(Delight.WAVE, null))
    }

    @Test
    fun `the owner's switch turns the tapback off`() {
        customizations = mapOf("botReactions" to false)
        extras.onRecord(rows)
        answerName()
        clock.advance(1000)
        assertThat(extras.touches.value).isEmpty()
    }

    @Test
    fun `Edit under the latest answer once the bot is quiet, and it cuts back to the question`() {
        extras.onRecord(rows)
        answerName()
        clock.advance(Delight.QUIET_BEFORE_CHANGE_MS)
        assertThat(extras.changeRowId.value).isEqualTo("a")
        extras.changeAnswer()
        assertThat(rows.map { it.id }).containsExactly("w")
        assertThat(engine.rewound).isEqualTo(1)
        assertThat(extras.changeRowId.value).isNull()
    }

    @Test
    fun `no Edit once something irreversible ran, or with a person in the chat`() {
        extras.onRecord(rows)
        engine.since = listOf("ask-file-node")
        answerName()
        clock.advance(2000)
        assertThat(extras.changeRowId.value).isNull()
        engine.since = emptyList()
        locked = true
        extras.refreshChange()
        clock.advance(2000)
        assertThat(extras.changeRowId.value).isNull()
    }

    @Test
    fun `the nudge arms on a question and goes with the answer`() {
        extras.onRecord(rows)
        clock.advance(Delight.STALL_QUIET_MS)
        val ui = NodeUIState.TextInput(questionText = "Your name?", inputType = NodeUIState.TextInput.InputType.NAME, nodeId = "q1", answerKey = "name")
            .withStep(NodeUIState.StepInfo(type = "ask-name-node", nodeId = "q1", visit = 3))
        extras.onUiState(ui)
        clock.advance(Delight.STALL_AFTER_MS)
        assertThat(extras.stallHint.value).isEqualTo("No rush - type your answer below whenever you're ready.")
        answerName()
        assertThat(extras.stallHint.value).isNull()
    }
}
