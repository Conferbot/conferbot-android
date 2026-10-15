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

/** The question steps: questionText, answerVariable, the web's checks and saved values. */
@OptIn(ExperimentalCoroutinesApi::class)
class AskQuestionNodeHandlersTest {
    private val ctx = TestFlowContext(nodeId = "q")

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        ChatState.reset()
        ChatState.initialize("s", "v", "b", null)
    }

    @After
    fun tearDown() {
        ChatState.reset()
        Dispatchers.resetMain()
    }

    private fun registered(key: String) = ChatState.registerAnswerVariable("q", key)
    private fun value(key: String) = ChatState.getAnswer(key)

    @Test
    fun `prompt is questionText`() {
        val ui = (ctx.process(AskCustomNodeHandler(), mapOf("questionText" to "<p>Why?</p>", "answerVariable" to "why")) as NodeResult.DisplayUI).uiState as NodeUIState.TextInput
        assertThat(ui.questionText).isEqualTo("Why?")
        assertThat(ui.answerKey).isEqualTo("why")
    }

    @Test
    fun `a step without answerVariable stores nothing`() {
        assertThat(ctx.respond(AskCustomNodeHandler(), emptyMap(), "hello")).isEqualTo(NodeResult.Proceed())
        assertThat(ChatState.answerVariables.value).isEmpty()
        assertThat(ChatState.record.value.single().shape).isEqualTo("user-ask-custom-question-response")
    }

    @Test
    fun `ask name greets by first name after typing`() {
        registered("name")
        val before = ctx.clock
        ctx.respond(AskNameNodeHandler(), mapOf("answerVariable" to "name"), "deepak gopal")
        assertThat(value("name")).isEqualTo("deepak gopal")
        assertThat(ctx.said).containsExactly("Nice to meet you, Deepak")
        assertThat(ctx.clock - before).isEqualTo(2000)
    }

    @Test
    fun `ask email refuses a bad address and a typo once`() {
        val h = AskEmailNodeHandler()
        assertThat(ctx.respond(h, emptyMap(), "nope")).isInstanceOf(NodeResult.Invalid::class.java)
        val typo = ctx.respond(h, emptyMap(), "jo@gmial.com") as NodeResult.Invalid
        assertThat(typo.message).contains("jo@gmail.com")
        assertThat(ctx.respond(h, emptyMap(), "jo@gmial.com")).isEqualTo(NodeResult.Proceed())
    }

    @Test
    fun `ask phone saves the number normalised`() {
        registered("phone")
        assertThat(ctx.respond(AskPhoneNodeHandler(), emptyMap(), "12")).isInstanceOf(NodeResult.Invalid::class.java)
        ctx.respond(AskPhoneNodeHandler(), emptyMap(), "+1 (555) 123-4567")
        assertThat(value("phone")).isEqualTo("+15551234567")
        assertThat(ChatState.record.value.last().shape).isEqualTo("user-ask-phone-number-response")
    }

    @Test
    fun `ask number reads 1,200 and checks the bounds`() {
        registered("n")
        val data = mapOf("enableConditions" to mapOf("minVal" to 1, "maxVal" to 5000))
        assertThat((ctx.respond(AskNumberNodeHandler(), data, "9000") as NodeResult.Invalid).message).isEqualTo("Please enter a number between 1 and 5000")
        ctx.respond(AskNumberNodeHandler(), data, "1,200")
        assertThat(value("n")).isEqualTo(1200L)
    }

    @Test
    fun `ask url gives a bare host https`() {
        registered("site")
        ctx.respond(AskUrlNodeHandler(), emptyMap(), "conferbot.com")
        assertThat(value("site")).isEqualTo("https://conferbot.com")
    }

    @Test
    fun `ask file checks the rules, uploads, and skips when optional`() {
        registered("doc")
        val data = mapOf("questionText" to "Your CV (optional)", "allowedFileTypes" to listOf("pdf"), "maxFileSizeMB" to 1)
        val big = mapOf("action" to "upload", "files" to listOf(mapOf("name" to "a.pdf", "size" to 5_000_000, "mimeType" to "application/pdf", "uri" to "content://a")))
        assertThat((ctx.respond(AskFileNodeHandler(), data, big) as NodeResult.Invalid).inCard).isTrue()
        val ok = mapOf("action" to "upload", "files" to listOf(mapOf("name" to "a.pdf", "size" to 100, "mimeType" to "application/pdf", "uri" to "content://a")))
        assertThat(ctx.respond(AskFileNodeHandler(), data, ok)).isEqualTo(NodeResult.Proceed())
        assertThat(value("doc")).isEqualTo("https://api.test/media/f")
        assertThat(ctx.respond(AskFileNodeHandler(), data, mapOf("skip" to true))).isEqualTo(NodeResult.Proceed())
    }

    @Test
    fun `calendar saves the long date and moves on after 400 ms`() {
        ChatState.registerAnswerVariable("q", "day")
        val r = ctx.respond(CalendarNodeHandler(), mapOf("timeZone" to "UTC"), mapOf("date" to "2026-10-20"))
        assertThat(r).isEqualTo(NodeResult.DelayedProceed(400))
        assertThat(value("day")).isEqualTo("Tuesday, October 20, 2026")
    }

    @Test
    fun `multiple questions save each answer under its own variable`() {
        val data = mapOf("questions" to listOf(mapOf("id" to "a", "questionText" to "Name?", "answerVariable" to "name"), mapOf("id" to "b", "questionText" to "Email?", "answerVariable" to "email")))
        ChatState.registerAnswerVariable("q0", "name")
        ChatState.registerAnswerVariable("q1", "email")
        ctx.process(AskMultipleQuestionsNodeHandler(), data)
        assertThat(ctx.respond(AskMultipleQuestionsNodeHandler(), data, "Ana")).isInstanceOf(NodeResult.DisplayUI::class.java)
        assertThat(ctx.respond(AskMultipleQuestionsNodeHandler(), data, "bad")).isInstanceOf(NodeResult.Invalid::class.java)
        assertThat(ctx.respond(AskMultipleQuestionsNodeHandler(), data, "ana@acme.io")).isEqualTo(NodeResult.Proceed())
        assertThat(value("name")).isEqualTo("Ana")
        assertThat(value("email")).isEqualTo("ana@acme.io")
    }
}
