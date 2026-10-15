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

/** The rich steps keep their gates: consent, OTP, payment, the AI router (web richNodes.ts). */
@OptIn(ExperimentalCoroutinesApi::class)
class RichNodeHandlersTest {
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

    @Test
    fun `required consent waits until agreed`() {
        val ctx = TestFlowContext(nodeId = "cs")
        ChatState.registerAnswerVariable("cs", "ok")
        val data = mapOf("consentText" to "I agree to the [Terms](https://x.com/t)", "answerVariable" to "ok")
        assertThat(ctx.respond(ConsentNodeHandler(), data, mapOf("id" to "continue"))).isInstanceOf(NodeResult.Invalid::class.java)
        assertThat(ctx.respond(ConsentNodeHandler(), data, mapOf("id" to "agree"))).isEqualTo(NodeResult.Proceed("source"))
        assertThat(ChatState.getAnswer("ok")).isEqualTo(true)
    }

    @Test
    fun `otp leaves by source only once the server verifies`() {
        val ctx = TestFlowContext(
            nodeId = "otp",
            replies = mapOf("otp/send" to ApiReply(true, 200, mapOf("sentTo" to "a***@x.io")), "otp/verify" to ApiReply(true, 200, mapOf("verified" to true, "email" to "a@x.io")))
        )
        val h = OtpNodeHandler()
        ctx.process(h, emptyMap())
        assertThat(ctx.respond(h, emptyMap(), mapOf("action" to "send", "email" to "a@x.io"))).isInstanceOf(NodeResult.DisplayUI::class.java)
        assertThat(ctx.respond(h, emptyMap(), mapOf("action" to "verify", "code" to "12"))).isInstanceOf(NodeResult.Invalid::class.java)
        assertThat(ctx.respond(h, emptyMap(), mapOf("action" to "verify", "code" to "123456"))).isEqualTo(NodeResult.Proceed("source"))
    }

    @Test
    fun `otp skip takes the failed path, falling back to source`() {
        val ctx = TestFlowContext(nodeId = "otp")
        assertThat(ctx.respond(OtpNodeHandler(), mapOf("allowSkip" to true), mapOf("action" to "skip"))).isEqualTo(NodeResult.Proceed("source-failed", "source"))
    }

    @Test
    fun `ai route takes the intent edge, else the fallback`() {
        val reply = mapOf("ai/route" to ApiReply(true, 200, mapOf("intentId" to "i1", "intent" to "Billing", "confidence" to 0.9)))
        val wired = TestFlowContext(nodeId = "ar", replies = reply, edgeList = listOf(TestFlowContext.edge("ar", "source-i1", "b")))
        assertThat(wired.respond(AiRouteNodeHandler(), emptyMap(), "my invoice")).isEqualTo(NodeResult.Proceed("source-i1"))
        val unwired = TestFlowContext(nodeId = "ar", replies = reply)
        assertThat(unwired.respond(AiRouteNodeHandler(), emptyMap(), "my invoice")).isEqualTo(NodeResult.Proceed("source-fallback"))
    }

    @Test
    fun `payment not configured fails, never the paid path`() {
        val ctx = TestFlowContext(nodeId = "pay", replies = mapOf("pay/start" to ApiReply(false, 400, mapOf("code" to "not_configured"))))
        assertThat(ctx.process(PaymentNodeHandler(), mapOf("currency" to "usd"))).isEqualTo(NodeResult.Proceed("source-failed"))
    }

    @Test
    fun `slider snaps to the step`() {
        val ctx = TestFlowContext(nodeId = "sl")
        ChatState.registerAnswerVariable("sl", "v")
        ctx.respond(SliderNodeHandler(), mapOf("min" to 0, "max" to 100, "step" to 5, "answerVariable" to "v"), mapOf("value" to 42))
        assertThat(ChatState.getAnswer("v")).isEqualTo(40L)
    }
}
