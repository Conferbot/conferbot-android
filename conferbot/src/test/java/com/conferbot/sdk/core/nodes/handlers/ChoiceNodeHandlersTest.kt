package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.NodeResult
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.testutils.TestFlowContext
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Choice steps: the builder's fields, the web's ports and saved values; legacy steps too. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChoiceNodeHandlersTest {
    private val ctx = TestFlowContext(nodeId = "c")

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        ChatState.reset()
        ChatState.initialize("s", "v", "b", null)
        ChatState.registerAnswerVariable("c", "picked")
    }

    @After
    fun tearDown() {
        ChatState.reset()
        Dispatchers.resetMain()
    }

    private fun picked() = ChatState.getAnswer("picked")

    @Test
    fun `n-choices leaves by the choice id and hides blank choices`() {
        val data = mapOf("choicePrompt" to "<p>Pick</p>", "choices" to listOf(mapOf("id" to "1", "choiceText" to "<p>One</p>"), mapOf("id" to "2", "choiceText" to ""), mapOf("id" to "u-9", "choiceText" to "Nine")))
        val ui = (ctx.process(NChoicesNodeHandler(), data) as NodeResult.DisplayUI).uiState as NodeUIState.SingleChoice
        assertThat(ui.choices.map { it.id }).containsExactly("1", "u-9").inOrder()
        assertThat(ctx.respond(NChoicesNodeHandler(), data, mapOf("id" to "u-9", "text" to "Nine"))).isEqualTo(NodeResult.Proceed("source-u-9"))
        assertThat(picked()).isEqualTo("Nine")
    }

    @Test
    fun `yes or no defaults to ids 1 and 2`() {
        assertThat(ctx.respond(YesOrNoChoiceNodeHandler(), mapOf("questionText" to "Ok?"), mapOf("id" to "2"))).isEqualTo(NodeResult.Proceed("source-2"))
        assertThat(picked()).isEqualTo("No")
    }

    @Test
    fun `rating saves the number and leaves by value minus one`() {
        assertThat(ctx.respond(RatingChoiceNodeHandler(), mapOf("ratingType" to "10"), 7)).isEqualTo(NodeResult.Proceed("source-6"))
        assertThat(picked()).isEqualTo(7)
    }

    @Test
    fun `opinion scale starts at 0 and leaves by the offset`() {
        assertThat(ctx.respond(OpinionScaleChoiceNodeHandler(), mapOf("from" to 3, "to" to 7), 5)).isEqualTo(NodeResult.Proceed("source-2"))
        val ui = (ctx.process(OpinionScaleChoiceNodeHandler(), emptyMap()) as NodeResult.DisplayUI).uiState as NodeUIState.Rating
        assertThat(ui.minValue to ui.maxValue).isEqualTo(0 to 10)
    }

    @Test
    fun `n-check keeps list order and moves on after 600 ms`() {
        val data = mapOf("checkOptionsPrompt" to "Which?", "options" to listOf(mapOf("id" to "a", "optionText" to "A"), mapOf("id" to "b", "optionText" to "B")))
        assertThat(ctx.respond(NCheckOptionsNodeHandler(), data, listOf("B", "A"))).isEqualTo(NodeResult.DelayedProceed(600))
        assertThat(picked()).isEqualTo("A, B")
        assertThat(ChatState.record.value.last().shape).isEqualTo("user-selected-check-options")
    }

    @Test
    fun `carousel path button saves title and label`() {
        val data = mapOf("cards" to listOf(mapOf("id" to "c1", "title" to "Pro", "buttons" to listOf(mapOf("id" to "b1", "label" to "Buy"), mapOf("id" to "b2", "label" to "Site", "action" to "link", "url" to "x.com")))))
        assertThat(ctx.respond(CarouselNodeHandler(), data, mapOf("id" to "b2"))).isInstanceOf(NodeResult.DisplayUI::class.java)
        assertThat(ctx.respond(CarouselNodeHandler(), data, mapOf("id" to "b1"))).isEqualTo(NodeResult.Proceed("source-b1"))
        assertThat(picked()).isEqualTo("Pro - Buy")
    }

    @Test
    fun `legacy two choices read choiceText and leave by its number`() {
        val data = mapOf("choicePrompt" to "?", "choiceText1" to "Yes", "choiceText2" to "No")
        val ui = (ctx.process(TwoChoicesNodeHandler(), data) as NodeResult.DisplayUI).uiState as NodeUIState.SingleChoice
        assertThat(ui.choices.map { it.text }).containsExactly("Yes", "No").inOrder()
        assertThat(ctx.respond(TwoChoicesNodeHandler(), data, mapOf("id" to "2"))).isEqualTo(NodeResult.Proceed("source-2"))
        assertThat(ChatState.answerVariables.value.single().value).isNull()
    }

    @Test
    fun `quiz compares the option name, right leaves by source-2`() {
        val data = mapOf("quizPrompt" to "2+2?", "option1" to "3", "option2" to "4", "option3" to "5", "correctAnswer" to "option2")
        assertThat(ctx.respond(QuizNodeHandler(), data, mapOf("id" to "1"))).isEqualTo(NodeResult.Proceed("source-1"))
        assertThat(ctx.respond(QuizNodeHandler(), data, mapOf("id" to "2"))).isEqualTo(NodeResult.Proceed("source-2"))
    }
}
