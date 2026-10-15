package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.flow.ApiReply
import com.conferbot.sdk.core.nodes.NodeResult
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

/** The integration steps as the web runs them: the server makes the calls. */
@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationNodeHandlersTest {
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
    fun `gpt asks the server with the transcript and shows the reply`() {
        ChatState.addToTranscript("user", "What is it?")
        val ctx = TestFlowContext(nodeId = "g", replies = mapOf("gpt" to ApiReply(true, 200, mapOf("reply" to "A bot builder."))))
        assertThat(ctx.process(GptNodeHandler(), mapOf("apiKey" to "", "selectedModel" to "gpt-4o"))).isEqualTo(NodeResult.Proceed())
        assertThat(ctx.calls.single().first).isEqualTo("gpt")
        assertThat(ctx.calls.single().second.keys).containsExactly("nodeId", "transcript")
        assertThat(ctx.said).containsExactly("A bot builder.")
    }

    @Test
    fun `gpt failure shows nothing and moves on`() {
        val ctx = TestFlowContext(nodeId = "g")
        assertThat(ctx.process(GptNodeHandler(), emptyMap())).isEqualTo(NodeResult.Proceed())
        assertThat(ctx.said).isEmpty()
    }

    @Test
    fun `email reached by a jump sends nothing`() {
        val ctx = TestFlowContext(via = "jump")
        ctx.process(EmailNodeHandler(), emptyMap())
        assertThat(ctx.emits).isEmpty()
        val sent = TestFlowContext()
        sent.process(EmailNodeHandler(), mapOf("subject" to "Hi"))
        assertThat(sent.emits.single().first).isEqualTo("email-node-trigger")
    }

    @Test
    fun `webhook registers its variable and does not hold the flow`() {
        val ctx = TestFlowContext(nodeId = "wh")
        assertThat(ctx.process(WebhookNodeHandler(), mapOf("url" to "https://api.example.com/x", "answerVariable" to "resp"))).isEqualTo(NodeResult.Proceed())
        assertThat(ChatState.answerVariables.value.single().key).isEqualTo("resp")
        ctx.runBackground()
        // A network failure is tried again, three more times.
        assertThat(ctx.calls.map { it.first }).containsExactly("https://api.example.com/x", "https://api.example.com/x", "https://api.example.com/x", "https://api.example.com/x")
    }

    @Test
    fun `native integrations go to the server and move on at once`() {
        val ctx = TestFlowContext(nodeId = "hs")
        assertThat(ctx.process(HubspotNodeHandler(), mapOf("credentialId" to "c"))).isEqualTo(NodeResult.Proceed())
        // The server call runs in the background (it waits for integration-result).
        assertThat(ctx.backgroundWork).hasSize(1)
    }

    @Test
    fun `zapier without an active webhook moves on quietly`() {
        val ctx = TestFlowContext(nodeId = "z")
        assertThat(ctx.process(ZapierNodeHandler(), mapOf("nodeId" to "z"))).isEqualTo(NodeResult.Proceed())
        assertThat(ctx.emits).isEmpty()
    }
}
