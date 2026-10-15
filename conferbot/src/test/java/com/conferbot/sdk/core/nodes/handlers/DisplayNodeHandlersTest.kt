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

/** The display steps read the fields the builder saves (flow-node spec, web displayStep.ts). */
@OptIn(ExperimentalCoroutinesApi::class)
class DisplayNodeHandlersTest {
    private val ctx = TestFlowContext(nodeId = "d")

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

    private fun answer(key: String, value: Any?) {
        ChatState.registerAnswerVariable("q", key)
        ChatState.fillAnswerVariable("q", value)
    }

    @Test
    fun `message fills answers and drops unknown variables`() {
        answer("name", "Ana <b>")
        val r = ctx.process(MessageNodeHandler(), mapOf("text" to "<p>Hi {name}, your {plan} is ready</p>")) as NodeResult.DisplayUI
        assertThat((r.uiState as NodeUIState.Message).text).isEqualTo("Hi Ana <b>, your is ready")
    }

    @Test
    fun `image reads data image`() {
        val r = ctx.process(ImageNodeHandler(), mapOf("image" to " https://x/a.png ")) as NodeResult.DisplayUI
        assertThat((r.uiState as NodeUIState.Image).url).isEqualTo("https://x/a.png")
    }

    @Test
    fun `video audio and file read their own fields`() {
        assertThat(((ctx.process(VideoNodeHandler(), mapOf("video" to "https://x/v.mp4")) as NodeResult.DisplayUI).uiState as NodeUIState.Video).url).isEqualTo("https://x/v.mp4")
        assertThat(((ctx.process(AudioNodeHandler(), mapOf("audio" to listOf("x"))) as NodeResult.DisplayUI).uiState as NodeUIState.Audio).url).isEmpty()
        val file = (ctx.process(FileNodeHandler(), mapOf("file" to "https://x/docs/Price%20list.pdf?v=1")) as NodeResult.DisplayUI).uiState as NodeUIState.File
        assertThat(file.fileName).isEqualTo("Price list.pdf")
    }

    @Test
    fun `html is filled and sanitised`() {
        answer("name", "<i>Ana</i>")
        val r = ctx.process(HtmlNodeHandler(), mapOf("html" to "<b onclick=\"x()\">{name}</b><script>alert(1)</script>")) as NodeResult.DisplayUI
        assertThat((r.uiState as NodeUIState.Html).htmlContent).isEqualTo("<b>&lt;i&gt;Ana&lt;/i&gt;</b>")
    }

    @Test
    fun `user redirect shows the link and never opens it`() {
        val r = ctx.process(UserRedirectNodeHandler(), mapOf("redirectPrompt" to "<p>Our pricing</p>", "link" to "conferbot.com/pricing")) as NodeResult.DisplayUI
        assertThat((r.uiState as NodeUIState.Message).text).isEqualTo("Our pricing\nhttps://conferbot.com/pricing")
    }

    @Test
    fun `navigate shows links and Done, a link keeps waiting`() {
        val data = mapOf("navigatePrompt" to "<p>Where?</p>", "navigateOptions" to listOf(mapOf("navigateText" to "Docs", "navigateLink" to "docs.example.com"), mapOf("navigateText" to "")), "doneNavigatingText" to "All set")
        val ui = (ctx.process(NavigateNodeHandler(), data) as NodeResult.DisplayUI).uiState as NodeUIState.SingleChoice
        assertThat(ui.choices.map { it.text }).containsExactly("Docs", "All set").inOrder()
        assertThat(ctx.respond(NavigateNodeHandler(), data, mapOf("id" to "link-0"))).isInstanceOf(NodeResult.DisplayUI::class.java)
        assertThat(ctx.respond(NavigateNodeHandler(), data, mapOf("id" to "done"))).isEqualTo(NodeResult.DelayedProceed(600))
        assertThat(ChatState.record.value.map { it.shape }).containsExactly("user-navigate-link-click", "user-navigate-done").inOrder()
    }
}
