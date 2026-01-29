package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.NodeResult
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.testutils.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*

/**
 * Unit tests for Logic Node Handlers
 * Tests ConditionNodeHandler, BooleanLogicNodeHandler, MathOperationNodeHandler,
 * VariableNodeHandler, JumpToNodeHandler, RandomFlowNodeHandler, and BusinessHoursNodeHandler
 */
class LogicNodeHandlersTest {

    @Before
    fun setUp() {
        mockkObject(ChatState)
        // Initialize ChatState for tests
        ChatState.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            TestFixtures.TEST_WORKSPACE_ID
        )
    }

    @After
    fun tearDown() {
        ChatState.reset()
        unmockkObject(ChatState)
    }

    // ==================== ConditionNodeHandler Tests ====================

    @Test
    fun `ConditionNodeHandler - string equals condition returns true branch`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("hello") } returns "hello"
        every { ChatState.resolveValue("hello") } returns "hello"

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "hello",
            "rightValue" to "hello",
            "operator" to "=",
            "isNumber" to false
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - string equals condition returns false branch`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("hello") } returns "hello"
        every { ChatState.resolveValue("world") } returns "world"

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "hello",
            "rightValue" to "world",
            "operator" to "=",
            "isNumber" to false
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-1")
    }

    @Test
    fun `ConditionNodeHandler - string contains condition`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("hello world") } returns "hello world"
        every { ChatState.resolveValue("world") } returns "world"

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "hello world",
            "rightValue" to "world",
            "operator" to "contains",
            "isNumber" to false
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - string does not contain condition`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("hello world") } returns "hello world"
        every { ChatState.resolveValue("xyz") } returns "xyz"

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "hello world",
            "rightValue" to "xyz",
            "operator" to "does not contain",
            "isNumber" to false
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - string starts with condition`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("hello world") } returns "hello world"
        every { ChatState.resolveValue("hello") } returns "hello"

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "hello world",
            "rightValue" to "hello",
            "operator" to "starts with",
            "isNumber" to false
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - string ends with condition`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("hello world") } returns "hello world"
        every { ChatState.resolveValue("world") } returns "world"

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "hello world",
            "rightValue" to "world",
            "operator" to "ends with",
            "isNumber" to false
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - string matches regex condition`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("hello123") } returns "hello123"
        every { ChatState.resolveValue("^[a-z]+[0-9]+$") } returns "^[a-z]+[0-9]+$"

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "hello123",
            "rightValue" to "^[a-z]+[0-9]+$",
            "operator" to "matches",
            "isNumber" to false
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - string does not match regex condition`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("hello") } returns "hello"
        every { ChatState.resolveValue("^[0-9]+$") } returns "^[0-9]+$"

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "hello",
            "rightValue" to "^[0-9]+$",
            "operator" to "does not match",
            "isNumber" to false
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - numeric greater than condition`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("10") } returns "10"
        every { ChatState.resolveValue("5") } returns "5"

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "10",
            "rightValue" to "5",
            "operator" to ">",
            "isNumber" to true
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - numeric less than condition`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("5") } returns "5"
        every { ChatState.resolveValue("10") } returns "10"

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "5",
            "rightValue" to "10",
            "operator" to "<",
            "isNumber" to true
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - numeric equals condition`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("10") } returns 10
        every { ChatState.resolveValue("10") } returns 10

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "10",
            "rightValue" to "10",
            "operator" to "==",
            "isNumber" to true
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - numeric not equals condition`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("10") } returns 10
        every { ChatState.resolveValue("5") } returns 5

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "10",
            "rightValue" to "5",
            "operator" to "!=",
            "isNumber" to true
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - numeric greater than or equal condition`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("10") } returns 10
        every { ChatState.resolveValue("10") } returns 10

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "10",
            "rightValue" to "10",
            "operator" to ">=",
            "isNumber" to true
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - numeric less than or equal condition`() = runTest {
        val handler = ConditionNodeHandler()

        every { ChatState.resolveValue("5") } returns 5
        every { ChatState.resolveValue("10") } returns 10

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "5",
            "rightValue" to "10",
            "operator" to "<=",
            "isNumber" to true
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `ConditionNodeHandler - resolves variable references`() = runTest {
        val handler = ConditionNodeHandler()

        // Simulate variable resolution
        every { ChatState.resolveValue("{{userAge}}") } returns 25
        every { ChatState.resolveValue("18") } returns 18

        val nodeData = mapOf(
            "type" to "condition-node",
            "leftValue" to "{{userAge}}",
            "rightValue" to "18",
            "operator" to ">=",
            "isNumber" to true
        )

        val result = handler.process(nodeData, "condition-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    // ==================== BooleanLogicNodeHandler Tests ====================

    @Test
    fun `BooleanLogicNodeHandler - AND operation both true`() = runTest {
        val handler = BooleanLogicNodeHandler()

        every { ChatState.resolveValue("true") } returns "true"

        val nodeData = mapOf(
            "type" to "boolean-logic-node",
            "leftValue" to "true",
            "rightValue" to "true",
            "operator" to "AND"
        )

        val result = handler.process(nodeData, "boolean-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `BooleanLogicNodeHandler - AND operation one false`() = runTest {
        val handler = BooleanLogicNodeHandler()

        every { ChatState.resolveValue("true") } returns "true"
        every { ChatState.resolveValue("false") } returns "false"

        val nodeData = mapOf(
            "type" to "boolean-logic-node",
            "leftValue" to "true",
            "rightValue" to "false",
            "operator" to "AND"
        )

        val result = handler.process(nodeData, "boolean-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-1")
    }

    @Test
    fun `BooleanLogicNodeHandler - OR operation one true`() = runTest {
        val handler = BooleanLogicNodeHandler()

        every { ChatState.resolveValue("false") } returns "false"
        every { ChatState.resolveValue("true") } returns "true"

        val nodeData = mapOf(
            "type" to "boolean-logic-node",
            "leftValue" to "false",
            "rightValue" to "true",
            "operator" to "OR"
        )

        val result = handler.process(nodeData, "boolean-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `BooleanLogicNodeHandler - OR operation both false`() = runTest {
        val handler = BooleanLogicNodeHandler()

        every { ChatState.resolveValue("false") } returns "false"

        val nodeData = mapOf(
            "type" to "boolean-logic-node",
            "leftValue" to "false",
            "rightValue" to "false",
            "operator" to "OR"
        )

        val result = handler.process(nodeData, "boolean-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-1")
    }

    @Test
    fun `BooleanLogicNodeHandler - NOT operation`() = runTest {
        val handler = BooleanLogicNodeHandler()

        every { ChatState.resolveValue("true") } returns "true"
        every { ChatState.resolveValue("false") } returns "false"

        val nodeData = mapOf(
            "type" to "boolean-logic-node",
            "leftValue" to "true",
            "rightValue" to "false",
            "operator" to "NOT"
        )

        val result = handler.process(nodeData, "boolean-1")

        // NOT is "left AND NOT right", so true AND NOT false = true AND true = true
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `BooleanLogicNodeHandler - XOR operation different values`() = runTest {
        val handler = BooleanLogicNodeHandler()

        every { ChatState.resolveValue("true") } returns "true"
        every { ChatState.resolveValue("false") } returns "false"

        val nodeData = mapOf(
            "type" to "boolean-logic-node",
            "leftValue" to "true",
            "rightValue" to "false",
            "operator" to "XOR"
        )

        val result = handler.process(nodeData, "boolean-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `BooleanLogicNodeHandler - XOR operation same values`() = runTest {
        val handler = BooleanLogicNodeHandler()

        every { ChatState.resolveValue("true") } returns "true"

        val nodeData = mapOf(
            "type" to "boolean-logic-node",
            "leftValue" to "true",
            "rightValue" to "true",
            "operator" to "XOR"
        )

        val result = handler.process(nodeData, "boolean-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-1")
    }

    @Test
    fun `BooleanLogicNodeHandler - NAND operation`() = runTest {
        val handler = BooleanLogicNodeHandler()

        every { ChatState.resolveValue("true") } returns "true"

        val nodeData = mapOf(
            "type" to "boolean-logic-node",
            "leftValue" to "true",
            "rightValue" to "true",
            "operator" to "NAND"
        )

        val result = handler.process(nodeData, "boolean-1")

        // NAND of true AND true = NOT true = false
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-1")
    }

    @Test
    fun `BooleanLogicNodeHandler - NOR operation`() = runTest {
        val handler = BooleanLogicNodeHandler()

        every { ChatState.resolveValue("false") } returns "false"

        val nodeData = mapOf(
            "type" to "boolean-logic-node",
            "leftValue" to "false",
            "rightValue" to "false",
            "operator" to "NOR"
        )

        val result = handler.process(nodeData, "boolean-1")

        // NOR of false OR false = NOT false = true
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `BooleanLogicNodeHandler - converts numeric values to boolean`() = runTest {
        val handler = BooleanLogicNodeHandler()

        every { ChatState.resolveValue("1") } returns 1
        every { ChatState.resolveValue("0") } returns 0

        val nodeData = mapOf(
            "type" to "boolean-logic-node",
            "leftValue" to "1",
            "rightValue" to "0",
            "operator" to "AND"
        )

        val result = handler.process(nodeData, "boolean-1")

        // 1 is truthy, 0 is falsy, so AND = false
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-1")
    }

    @Test
    fun `BooleanLogicNodeHandler - converts yes string to true`() = runTest {
        val handler = BooleanLogicNodeHandler()

        every { ChatState.resolveValue("yes") } returns "yes"

        val nodeData = mapOf(
            "type" to "boolean-logic-node",
            "leftValue" to "yes",
            "rightValue" to "yes",
            "operator" to "AND"
        )

        val result = handler.process(nodeData, "boolean-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    // ==================== MathOperationNodeHandler Tests ====================

    @Test
    fun `MathOperationNodeHandler - addition`() = runTest {
        val handler = MathOperationNodeHandler()

        every { ChatState.resolveValue("5") } returns "5"
        every { ChatState.resolveValue("3") } returns "3"
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "math-operation-node",
            "leftValue" to "5",
            "rightValue" to "3",
            "operator" to "+"
        )

        val result = handler.process(nodeData, "math-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        // Result 8.0 should be stored
        io.mockk.verify { ChatState.setAnswerVariable("math-1", 8.0) }
    }

    @Test
    fun `MathOperationNodeHandler - subtraction`() = runTest {
        val handler = MathOperationNodeHandler()

        every { ChatState.resolveValue("10") } returns "10"
        every { ChatState.resolveValue("4") } returns "4"
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "math-operation-node",
            "leftValue" to "10",
            "rightValue" to "4",
            "operator" to "-"
        )

        val result = handler.process(nodeData, "math-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setAnswerVariable("math-1", 6.0) }
    }

    @Test
    fun `MathOperationNodeHandler - multiplication`() = runTest {
        val handler = MathOperationNodeHandler()

        every { ChatState.resolveValue("6") } returns "6"
        every { ChatState.resolveValue("7") } returns "7"
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "math-operation-node",
            "leftValue" to "6",
            "rightValue" to "7",
            "operator" to "*"
        )

        val result = handler.process(nodeData, "math-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setAnswerVariable("math-1", 42.0) }
    }

    @Test
    fun `MathOperationNodeHandler - division`() = runTest {
        val handler = MathOperationNodeHandler()

        every { ChatState.resolveValue("20") } returns "20"
        every { ChatState.resolveValue("4") } returns "4"
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "math-operation-node",
            "leftValue" to "20",
            "rightValue" to "4",
            "operator" to "/"
        )

        val result = handler.process(nodeData, "math-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setAnswerVariable("math-1", 5.0) }
    }

    @Test
    fun `MathOperationNodeHandler - division by zero returns zero`() = runTest {
        val handler = MathOperationNodeHandler()

        every { ChatState.resolveValue("20") } returns "20"
        every { ChatState.resolveValue("0") } returns "0"
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "math-operation-node",
            "leftValue" to "20",
            "rightValue" to "0",
            "operator" to "/"
        )

        val result = handler.process(nodeData, "math-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setAnswerVariable("math-1", 0.0) }
    }

    @Test
    fun `MathOperationNodeHandler - modulo`() = runTest {
        val handler = MathOperationNodeHandler()

        every { ChatState.resolveValue("17") } returns "17"
        every { ChatState.resolveValue("5") } returns "5"
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "math-operation-node",
            "leftValue" to "17",
            "rightValue" to "5",
            "operator" to "%"
        )

        val result = handler.process(nodeData, "math-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setAnswerVariable("math-1", 2.0) }
    }

    @Test
    fun `MathOperationNodeHandler - modulo by zero returns zero`() = runTest {
        val handler = MathOperationNodeHandler()

        every { ChatState.resolveValue("17") } returns "17"
        every { ChatState.resolveValue("0") } returns "0"
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "math-operation-node",
            "leftValue" to "17",
            "rightValue" to "0",
            "operator" to "%"
        )

        val result = handler.process(nodeData, "math-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setAnswerVariable("math-1", 0.0) }
    }

    @Test
    fun `MathOperationNodeHandler - handles decimal numbers`() = runTest {
        val handler = MathOperationNodeHandler()

        every { ChatState.resolveValue("3.14") } returns "3.14"
        every { ChatState.resolveValue("2") } returns "2"
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "math-operation-node",
            "leftValue" to "3.14",
            "rightValue" to "2",
            "operator" to "*"
        )

        val result = handler.process(nodeData, "math-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setAnswerVariable("math-1", 6.28) }
    }

    @Test
    fun `MathOperationNodeHandler - handles variable references`() = runTest {
        val handler = MathOperationNodeHandler()

        every { ChatState.resolveValue("{{quantity}}") } returns 5
        every { ChatState.resolveValue("{{price}}") } returns 10.50
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "math-operation-node",
            "leftValue" to "{{quantity}}",
            "rightValue" to "{{price}}",
            "operator" to "*"
        )

        val result = handler.process(nodeData, "math-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setAnswerVariable("math-1", 52.5) }
    }

    @Test
    fun `MathOperationNodeHandler - invalid number defaults to zero`() = runTest {
        val handler = MathOperationNodeHandler()

        every { ChatState.resolveValue("not-a-number") } returns "not-a-number"
        every { ChatState.resolveValue("5") } returns "5"
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "math-operation-node",
            "leftValue" to "not-a-number",
            "rightValue" to "5",
            "operator" to "+"
        )

        val result = handler.process(nodeData, "math-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setAnswerVariable("math-1", 5.0) }
    }

    // ==================== VariableNodeHandler Tests ====================

    @Test
    fun `VariableNodeHandler - sets string variable`() = runTest {
        val handler = VariableNodeHandler()

        every { ChatState.resolveValue("John Doe") } returns "John Doe"
        every { ChatState.setVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariableByKey(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "variable-node",
            "name" to "userName",
            "value" to "John Doe",
            "isNumber" to false,
            "customNameValue" to false
        )

        val result = handler.process(nodeData, "var-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setVariable("userName", "John Doe") }
    }

    @Test
    fun `VariableNodeHandler - sets numeric variable`() = runTest {
        val handler = VariableNodeHandler()

        every { ChatState.resolveValue("42") } returns "42"
        every { ChatState.setVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariableByKey(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "variable-node",
            "name" to "count",
            "value" to "42",
            "isNumber" to true,
            "customNameValue" to false
        )

        val result = handler.process(nodeData, "var-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setVariable("count", 42.0) }
    }

    @Test
    fun `VariableNodeHandler - uses nodeId as name when customNameValue is true`() = runTest {
        val handler = VariableNodeHandler()

        every { ChatState.resolveValue("test value") } returns "test value"
        every { ChatState.setVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariableByKey(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "variable-node",
            "name" to "ignoredName",
            "value" to "test value",
            "isNumber" to false,
            "customNameValue" to true
        )

        val result = handler.process(nodeData, "custom-var-id")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setVariable("custom-var-id", "test value") }
    }

    @Test
    fun `VariableNodeHandler - resolves variable references in value`() = runTest {
        val handler = VariableNodeHandler()

        every { ChatState.resolveValue("{{firstName}} {{lastName}}") } returns "John Smith"
        every { ChatState.setVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariableByKey(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "variable-node",
            "name" to "fullName",
            "value" to "{{firstName}} {{lastName}}",
            "isNumber" to false,
            "customNameValue" to false
        )

        val result = handler.process(nodeData, "var-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setVariable("fullName", "John Smith") }
    }

    @Test
    fun `VariableNodeHandler - converts string to number when isNumber is true`() = runTest {
        val handler = VariableNodeHandler()

        every { ChatState.resolveValue("not-a-number") } returns "not-a-number"
        every { ChatState.setVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariableByKey(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "variable-node",
            "name" to "numVar",
            "value" to "not-a-number",
            "isNumber" to true,
            "customNameValue" to false
        )

        val result = handler.process(nodeData, "var-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        io.mockk.verify { ChatState.setVariable("numVar", 0.0) }
    }

    // ==================== JumpToNodeHandler Tests ====================

    @Test
    fun `JumpToNodeHandler - jumps to target node`() = runTest {
        val handler = JumpToNodeHandler()

        val nodeData = mapOf(
            "type" to "jump-to-node",
            "targetNodeId" to "target-node-123"
        )

        val result = handler.process(nodeData, "jump-1")

        assertThat(result).isInstanceOf(NodeResult.JumpTo::class.java)
        assertThat((result as NodeResult.JumpTo).targetNodeId).isEqualTo("target-node-123")
    }

    @Test
    fun `JumpToNodeHandler - uses nodeId fallback`() = runTest {
        val handler = JumpToNodeHandler()

        val nodeData = mapOf(
            "type" to "jump-to-node",
            "nodeId" to "fallback-target-456"
        )

        val result = handler.process(nodeData, "jump-1")

        assertThat(result).isInstanceOf(NodeResult.JumpTo::class.java)
        assertThat((result as NodeResult.JumpTo).targetNodeId).isEqualTo("fallback-target-456")
    }

    @Test
    fun `JumpToNodeHandler - returns error when no target specified`() = runTest {
        val handler = JumpToNodeHandler()

        val nodeData = mapOf(
            "type" to "jump-to-node"
        )

        val result = handler.process(nodeData, "jump-1")

        assertThat(result).isInstanceOf(NodeResult.Error::class.java)
        assertThat((result as NodeResult.Error).message).isEqualTo("No target node specified")
        assertThat(result.shouldProceed).isTrue()
    }

    @Test
    fun `JumpToNodeHandler - returns error when target is empty string`() = runTest {
        val handler = JumpToNodeHandler()

        val nodeData = mapOf(
            "type" to "jump-to-node",
            "targetNodeId" to ""
        )

        val result = handler.process(nodeData, "jump-1")

        assertThat(result).isInstanceOf(NodeResult.Error::class.java)
    }

    // ==================== RandomFlowNodeHandler Tests ====================

    @Test
    fun `RandomFlowNodeHandler - returns proceed when no branches`() = runTest {
        val handler = RandomFlowNodeHandler()

        val nodeData = mapOf(
            "type" to "random-flow-node",
            "branches" to emptyList<Map<String, Any?>>()
        )

        val result = handler.process(nodeData, "random-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isNull()
    }

    @Test
    fun `RandomFlowNodeHandler - selects single branch`() = runTest {
        val handler = RandomFlowNodeHandler()

        val nodeData = mapOf(
            "type" to "random-flow-node",
            "branches" to listOf(
                mapOf("id" to "branch-0", "weight" to 1.0)
            )
        )

        val result = handler.process(nodeData, "random-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `RandomFlowNodeHandler - respects weights for branch selection`() = runTest {
        val handler = RandomFlowNodeHandler()

        val nodeData = mapOf(
            "type" to "random-flow-node",
            "branches" to listOf(
                mapOf("id" to "branch-0", "weight" to 100.0),
                mapOf("id" to "branch-1", "weight" to 0.0)
            )
        )

        // With weight 100 vs 0, should always select branch 0
        repeat(10) {
            val result = handler.process(nodeData, "random-1")

            assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
            assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
        }
    }

    @Test
    fun `RandomFlowNodeHandler - handles string weights`() = runTest {
        val handler = RandomFlowNodeHandler()

        val nodeData = mapOf(
            "type" to "random-flow-node",
            "branches" to listOf(
                mapOf("id" to "branch-0", "weight" to "50"),
                mapOf("id" to "branch-1", "weight" to "50")
            )
        )

        val result = handler.process(nodeData, "random-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        val targetPort = (result as NodeResult.Proceed).targetPort
        assertThat(targetPort).isAnyOf("source-0", "source-1")
    }

    @Test
    fun `RandomFlowNodeHandler - defaults to weight 1 when not specified`() = runTest {
        val handler = RandomFlowNodeHandler()

        val nodeData = mapOf(
            "type" to "random-flow-node",
            "branches" to listOf(
                mapOf("id" to "branch-0"),
                mapOf("id" to "branch-1")
            )
        )

        val result = handler.process(nodeData, "random-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        val targetPort = (result as NodeResult.Proceed).targetPort
        assertThat(targetPort).isAnyOf("source-0", "source-1")
    }

    // ==================== BusinessHoursNodeHandler Tests ====================

    @Test
    fun `BusinessHoursNodeHandler - within business hours`() = runTest {
        val handler = BusinessHoursNodeHandler()

        // Create node data for 24/7 availability
        val nodeData = mapOf(
            "type" to "business-hours-node",
            "timezone" to "UTC",
            "excludeDays" to emptyList<String>(),
            "excludeDates" to emptyList<String>(),
            "weeklyHours" to listOf(
                createDayHours("Sunday", true, "00:00", "23:59"),
                createDayHours("Monday", true, "00:00", "23:59"),
                createDayHours("Tuesday", true, "00:00", "23:59"),
                createDayHours("Wednesday", true, "00:00", "23:59"),
                createDayHours("Thursday", true, "00:00", "23:59"),
                createDayHours("Friday", true, "00:00", "23:59"),
                createDayHours("Saturday", true, "00:00", "23:59")
            )
        )

        val result = handler.process(nodeData, "business-hours-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `BusinessHoursNodeHandler - excluded day`() = runTest {
        val handler = BusinessHoursNodeHandler()

        // Get current day name
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val currentDayName = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]

        val nodeData = mapOf(
            "type" to "business-hours-node",
            "timezone" to "UTC",
            "excludeDays" to listOf(currentDayName),
            "excludeDates" to emptyList<String>(),
            "weeklyHours" to listOf(
                createDayHours("Sunday", true, "00:00", "23:59"),
                createDayHours("Monday", true, "00:00", "23:59"),
                createDayHours("Tuesday", true, "00:00", "23:59"),
                createDayHours("Wednesday", true, "00:00", "23:59"),
                createDayHours("Thursday", true, "00:00", "23:59"),
                createDayHours("Friday", true, "00:00", "23:59"),
                createDayHours("Saturday", true, "00:00", "23:59")
            )
        )

        val result = handler.process(nodeData, "business-hours-1")

        // Should be outside business hours
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-1")
    }

    @Test
    fun `BusinessHoursNodeHandler - excluded date`() = runTest {
        val handler = BusinessHoursNodeHandler()

        // Get current date
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val currentDate = dateFormat.format(Date())

        val nodeData = mapOf(
            "type" to "business-hours-node",
            "timezone" to "UTC",
            "excludeDays" to emptyList<String>(),
            "excludeDates" to listOf(currentDate),
            "weeklyHours" to listOf(
                createDayHours("Sunday", true, "00:00", "23:59"),
                createDayHours("Monday", true, "00:00", "23:59"),
                createDayHours("Tuesday", true, "00:00", "23:59"),
                createDayHours("Wednesday", true, "00:00", "23:59"),
                createDayHours("Thursday", true, "00:00", "23:59"),
                createDayHours("Friday", true, "00:00", "23:59"),
                createDayHours("Saturday", true, "00:00", "23:59")
            )
        )

        val result = handler.process(nodeData, "business-hours-1")

        // Should be outside business hours
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-1")
    }

    @Test
    fun `BusinessHoursNodeHandler - day not available`() = runTest {
        val handler = BusinessHoursNodeHandler()

        // Get current day name
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val currentDayName = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]

        // Create weekly hours with current day not available
        val weeklyHours = dayNames.map { day ->
            createDayHours(day, day != currentDayName, "00:00", "23:59")
        }

        val nodeData = mapOf(
            "type" to "business-hours-node",
            "timezone" to "UTC",
            "excludeDays" to emptyList<String>(),
            "excludeDates" to emptyList<String>(),
            "weeklyHours" to weeklyHours
        )

        val result = handler.process(nodeData, "business-hours-1")

        // Should be outside business hours
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-1")
    }

    @Test
    fun `BusinessHoursNodeHandler - outside time slots`() = runTest {
        val handler = BusinessHoursNodeHandler()

        // Get current day name
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val currentDayName = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]

        // Create time slot that's impossible (23:50-23:55 if current time is different)
        val nodeData = mapOf(
            "type" to "business-hours-node",
            "timezone" to "UTC",
            "excludeDays" to emptyList<String>(),
            "excludeDates" to emptyList<String>(),
            "weeklyHours" to listOf(
                createDayHours(currentDayName, true, "23:50", "23:55")
            )
        )

        val result = handler.process(nodeData, "business-hours-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        // Result depends on actual current time
    }

    @Test
    fun `BusinessHoursNodeHandler - different timezone`() = runTest {
        val handler = BusinessHoursNodeHandler()

        // Get current day name in specific timezone
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"))
        val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val currentDayName = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]

        val nodeData = mapOf(
            "type" to "business-hours-node",
            "timezone" to "America/New_York",
            "excludeDays" to emptyList<String>(),
            "excludeDates" to emptyList<String>(),
            "weeklyHours" to listOf(
                createDayHours(currentDayName, true, "00:00", "23:59")
            )
        )

        val result = handler.process(nodeData, "business-hours-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `BusinessHoursNodeHandler - uses default timezone when not specified`() = runTest {
        val handler = BusinessHoursNodeHandler()

        // Get current day name in default timezone
        val calendar = Calendar.getInstance()
        val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val currentDayName = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]

        val nodeData = mapOf(
            "type" to "business-hours-node",
            "excludeDays" to emptyList<String>(),
            "excludeDates" to emptyList<String>(),
            "weeklyHours" to listOf(
                createDayHours(currentDayName, true, "00:00", "23:59")
            )
        )

        val result = handler.process(nodeData, "business-hours-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat((result as NodeResult.Proceed).targetPort).isEqualTo("source-0")
    }

    @Test
    fun `BusinessHoursNodeHandler - multiple time slots`() = runTest {
        val handler = BusinessHoursNodeHandler()

        // Get current day name
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val currentDayName = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]

        val nodeData = mapOf(
            "type" to "business-hours-node",
            "timezone" to "UTC",
            "excludeDays" to emptyList<String>(),
            "excludeDates" to emptyList<String>(),
            "weeklyHours" to listOf(
                mapOf(
                    "dayName" to currentDayName,
                    "available" to true,
                    "slots" to listOf(
                        mapOf("start" to "00:00", "end" to "12:00"),
                        mapOf("start" to "13:00", "end" to "23:59")
                    )
                )
            )
        )

        val result = handler.process(nodeData, "business-hours-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        // Result depends on current time
    }

    // ==================== Helper Functions ====================

    private fun createDayHours(
        dayName: String,
        available: Boolean,
        startTime: String,
        endTime: String
    ): Map<String, Any?> {
        return mapOf(
            "dayName" to dayName,
            "available" to available,
            "slots" to listOf(
                mapOf("start" to startTime, "end" to endTime)
            )
        )
    }
}
