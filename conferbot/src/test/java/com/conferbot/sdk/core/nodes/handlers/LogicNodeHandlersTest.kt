package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.flow.FlowLogic
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

/** The logic steps, with the web's rules (conditionEval.ts, logicValues.ts). */
@OptIn(ExperimentalCoroutinesApi::class)
class LogicNodeHandlersTest {
    private val ctx = TestFlowContext(nodeId = "l")

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

    private fun answer(key: String, value: Any?) = ChatState.saveVariableByName(key, value, "q-$key")
    private fun cond(data: Map<String, Any?>) = (ctx.process(ConditionNodeHandler(), data) as NodeResult.Proceed).targetPort

    @Test
    fun `a bare variable name in leftValue is the answer`() {
        answer("plan", "Pro")
        assertThat(cond(mapOf("leftValue" to "plan", "operator" to "=", "rightValue" to "Pro", "customRightValue" to true))).isEqualTo("source-0")
        assertThat(cond(mapOf("leftValue" to "\${plan}", "operator" to "not_equals", "rightValue" to "Pro", "customRightValue" to true))).isEqualTo("source-1")
    }

    @Test
    fun `operators as the web reads them`() {
        answer("t", "Hello World")
        fun op(o: String, r: String, ci: Boolean = false) = cond(mapOf("leftValue" to "t", "operator" to o, "rightValue" to r, "customRightValue" to true, "caseInsensitive" to ci))
        assertThat(op("contains", "World")).isEqualTo("source-0")
        assertThat(op("not_contains", "world")).isEqualTo("source-0")
        assertThat(op("starts_with", "hello", ci = true)).isEqualTo("source-0")
        assertThat(op("ends with", "World")).isEqualTo("source-0")
        assertThat(op("matches", "^H.*d$")).isEqualTo("source-0")
        assertThat(op("matches", "([")).isEqualTo("source-1")
        assertThat(op("is_set", "")).isEqualTo("source-0")
        assertThat(op("weird", "x")).isEqualTo("source-1")
        assertThat(op("=", "hello world")).isEqualTo("source-1")
    }

    @Test
    fun `numbers compare as numbers, text does not`() {
        answer("n", "10")
        assertThat(cond(mapOf("leftValue" to "n", "operator" to ">", "rightValue" to "9", "customRightValue" to true))).isEqualTo("source-0")
        answer("w", "ten")
        assertThat(cond(mapOf("leftValue" to "w", "operator" to ">", "rightValue" to "9", "customRightValue" to true))).isEqualTo("source-1")
    }

    @Test
    fun `math saves by name and a divide by zero is empty`() {
        answer("a", 7)
        ctx.process(MathOperationNodeHandler(), mapOf("leftValue" to "a", "operator" to "*", "rightValue" to "3", "answerVariable" to "total"))
        assertThat(ChatState.getAnswer("total")).isEqualTo(21L)
        ctx.process(MathOperationNodeHandler(), mapOf("leftValue" to "a", "operator" to "/", "rightValue" to "0"))
        assertThat(ChatState.getAnswer(FlowLogic.DEFAULT_MATH_VARIABLE)).isEqualTo("")
    }

    @Test
    fun `boolean logic reads No and 0 as false`() {
        answer("x", "No")
        assertThat((ctx.process(BooleanLogicNodeHandler(), mapOf("leftValue" to "x", "operator" to "OR", "rightValue" to "0")) as NodeResult.Proceed).targetPort).isEqualTo("source-1")
    }

    @Test
    fun `random split goes by branch id, wired branches only`() {
        val c = TestFlowContext(nodeId = "r", edgeList = listOf(TestFlowContext.edge("r", "source-b", "x")), rand = 0.1)
        val r = c.process(RandomFlowNodeHandler(), mapOf("branches" to listOf(mapOf("id" to "a", "weight" to 50), mapOf("id" to "b", "weight" to 50)))) as NodeResult.Proceed
        assertThat(r.targetPort).isEqualTo("source-b")
    }

    @Test
    fun `jump-to reads nodeId first`() {
        assertThat(ctx.process(JumpToNodeHandler(), mapOf("targetNodeId" to "t", "nodeId" to "n"))).isEqualTo(NodeResult.JumpTo("n"))
    }

    @Test
    fun `business hours on the owner's clock`() {
        // Thursday 2026-10-15 10:00 UTC is 14:00 in Dubai.
        val data = mapOf("timezone" to "Asia/Dubai", "weeklyHours" to listOf(mapOf("dayName" to "Thursday", "available" to true, "slots" to listOf(mapOf("start" to "13:00", "end" to "15:00")))))
        assertThat((ctx.process(BusinessHoursNodeHandler(), data) as NodeResult.Proceed).targetPort).isEqualTo("source-0")
        assertThat((ctx.process(BusinessHoursNodeHandler(), data + ("excludeDays" to listOf("Thursday"))) as NodeResult.Proceed).targetPort).isEqualTo("source-1")
    }
}
