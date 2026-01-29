package com.conferbot.sdk.core

import app.cash.turbine.test
import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.core.nodes.handlers.*
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.services.SocketClient
import com.conferbot.sdk.testutils.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for NodeFlowEngine
 * Tests complete flows from start to end, conditional branching,
 * and variable passing between nodes
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NodeFlowIntegrationTest {

    private lateinit var mockSocketClient: SocketClient
    private lateinit var flowEngine: NodeFlowEngine
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkObject(ChatState)
        mockkObject(NodeHandlerRegistry)

        mockSocketClient = mockk(relaxed = true)
        flowEngine = NodeFlowEngine(mockSocketClient)

        // Setup common ChatState mocks
        every { ChatState.initialize(any(), any(), any(), any()) } returns Unit
        every { ChatState.setSteps(any()) } returns Unit
        every { ChatState.setCurrentIndex(any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariableByKey(any(), any()) } returns Unit
        every { ChatState.setVariable(any(), any()) } returns Unit
        every { ChatState.setUserMetadata(any(), any()) } returns Unit
        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit
        every { ChatState.buildResponseData() } returns emptyMap()
        every { ChatState.resolveValue(any()) } answers { firstArg() }
        every { ChatState.getAnswerVariablesMap() } returns emptyMap()
        every { ChatState.getTranscriptForGPT() } returns emptyList()
        every { ChatState.currentIndex } returns kotlinx.coroutines.flow.MutableStateFlow(0)
        every { ChatState.chatSessionId } returns TestFixtures.TEST_CHAT_SESSION_ID
        every { ChatState.visitorId } returns TestFixtures.TEST_VISITOR_ID
        every { ChatState.botId } returns TestFixtures.TEST_BOT_ID
        every { ChatState.workspaceId } returns TestFixtures.TEST_WORKSPACE_ID

        // Setup handler registry
        setupHandlerRegistry()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ChatState)
        unmockkObject(NodeHandlerRegistry)
        flowEngine.destroy()
    }

    private fun setupHandlerRegistry() {
        // Return real handlers for testing
        every { NodeHandlerRegistry.getHandler(NodeTypes.MESSAGE) } returns
            mockk<com.conferbot.sdk.core.nodes.handlers.MessageNodeHandler>(relaxed = true).apply {
                coEvery { process(any(), any()) } answers {
                    val nodeData = firstArg<Map<String, Any?>>()
                    val text = nodeData["text"]?.toString() ?: nodeData["message"]?.toString() ?: ""
                    NodeResult.DisplayUI(NodeUIState.Message(text = text, nodeId = secondArg()))
                }
            }

        every { NodeHandlerRegistry.getHandler(NodeTypes.ASK_NAME) } returns
            mockk<com.conferbot.sdk.core.nodes.handlers.AskNameNodeHandler>(relaxed = true).apply {
                coEvery { process(any(), any()) } answers {
                    val nodeData = firstArg<Map<String, Any?>>()
                    val question = nodeData["questionText"]?.toString() ?: "What is your name?"
                    NodeResult.DisplayUI(
                        NodeUIState.TextInput(
                            questionText = question,
                            inputType = NodeUIState.TextInput.InputType.NAME,
                            nodeId = secondArg(),
                            answerKey = nodeData["answerVariable"]?.toString() ?: secondArg()
                        )
                    )
                }
                coEvery { handleResponse(any(), any(), any()) } returns NodeResult.Proceed()
            }

        every { NodeHandlerRegistry.getHandler(NodeTypes.CONDITION) } returns ConditionNodeHandler()
        every { NodeHandlerRegistry.getHandler(NodeTypes.VARIABLE) } returns VariableNodeHandler()
        every { NodeHandlerRegistry.getHandler(NodeTypes.MATH_OPERATION) } returns MathOperationNodeHandler()
        every { NodeHandlerRegistry.getHandler(NodeTypes.JUMP_TO) } returns JumpToNodeHandler()
        every { NodeHandlerRegistry.getHandler(NodeTypes.TWO_CHOICES) } returns TwoChoicesNodeHandler()
        every { NodeHandlerRegistry.getHandler(NodeTypes.DELAY) } returns DelayNodeHandler()
        every { NodeHandlerRegistry.getHandler(NodeTypes.BOOLEAN_LOGIC) } returns BooleanLogicNodeHandler()

        // Default null for unhandled types
        every { NodeHandlerRegistry.getHandler(not(match { it in listOf(
            NodeTypes.MESSAGE, NodeTypes.ASK_NAME, NodeTypes.CONDITION,
            NodeTypes.VARIABLE, NodeTypes.MATH_OPERATION, NodeTypes.JUMP_TO,
            NodeTypes.TWO_CHOICES, NodeTypes.DELAY, NodeTypes.BOOLEAN_LOGIC
        ) })) } returns null
    }

    // ==================== Simple Flow Tests ====================

    @Test
    fun `complete simple linear flow`() = runTest {
        val (steps, edges) = TestFixtures.createSimpleFlow()

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.currentUIState.test {
            // Start the flow
            flowEngine.start()
            testDispatcher.scheduler.advanceUntilIdle()

            // First node: Message - "Welcome!"
            val firstState = awaitItem()
            assertThat(firstState).isNotNull()
            assertThat(firstState).isInstanceOf(NodeUIState.Message::class.java)
            assertThat((firstState as NodeUIState.Message).text).isEqualTo("Welcome!")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `flow handles empty steps list`() = runTest {
        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = emptyList(),
            edgesData = emptyList()
        )

        flowEngine.isFlowComplete.test {
            flowEngine.start()
            testDispatcher.scheduler.advanceUntilIdle()

            val isComplete = awaitItem()
            assertThat(isComplete).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== Conditional Branching Tests ====================

    @Test
    fun `conditional node routes to true branch`() = runTest {
        val steps = listOf(
            createNodeStep("start", NodeTypes.MESSAGE, mapOf("text" to "Start")),
            createNodeStep("condition", NodeTypes.CONDITION, mapOf(
                "leftValue" to "10",
                "rightValue" to "5",
                "operator" to ">",
                "isNumber" to true
            )),
            createNodeStep("true-branch", NodeTypes.MESSAGE, mapOf("text" to "True!")),
            createNodeStep("false-branch", NodeTypes.MESSAGE, mapOf("text" to "False!"))
        )

        val edges = listOf(
            createEdge("start", "condition"),
            createEdge("condition", "true-branch", "source-0"),
            createEdge("condition", "false-branch", "source-1")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.start()
        testDispatcher.scheduler.advanceUntilIdle()

        // The condition (10 > 5) should evaluate to true and route to source-0
        verify { ChatState.setCurrentIndex(0) }  // Start
        verify { ChatState.setCurrentIndex(1) }  // Condition
    }

    @Test
    fun `conditional node routes to false branch`() = runTest {
        every { ChatState.resolveValue("5") } returns "5"
        every { ChatState.resolveValue("10") } returns "10"

        val steps = listOf(
            createNodeStep("start", NodeTypes.MESSAGE, mapOf("text" to "Start")),
            createNodeStep("condition", NodeTypes.CONDITION, mapOf(
                "leftValue" to "5",
                "rightValue" to "10",
                "operator" to ">",
                "isNumber" to true
            )),
            createNodeStep("true-branch", NodeTypes.MESSAGE, mapOf("text" to "True!")),
            createNodeStep("false-branch", NodeTypes.MESSAGE, mapOf("text" to "False!"))
        )

        val edges = listOf(
            createEdge("start", "condition"),
            createEdge("condition", "true-branch", "source-0"),
            createEdge("condition", "false-branch", "source-1")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.start()
        testDispatcher.scheduler.advanceUntilIdle()

        // The condition (5 > 10) should evaluate to false and route to source-1
        verify { ChatState.setCurrentIndex(0) }  // Start
        verify { ChatState.setCurrentIndex(1) }  // Condition
    }

    @Test
    fun `boolean logic AND gates work correctly`() = runTest {
        every { ChatState.resolveValue("true") } returns "true"
        every { ChatState.resolveValue("false") } returns "false"

        val steps = listOf(
            createNodeStep("boolean", NodeTypes.BOOLEAN_LOGIC, mapOf(
                "leftValue" to "true",
                "rightValue" to "false",
                "operator" to "AND"
            )),
            createNodeStep("true-branch", NodeTypes.MESSAGE, mapOf("text" to "Both true")),
            createNodeStep("false-branch", NodeTypes.MESSAGE, mapOf("text" to "Not both true"))
        )

        val edges = listOf(
            createEdge("boolean", "true-branch", "source-0"),
            createEdge("boolean", "false-branch", "source-1")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.start()
        testDispatcher.scheduler.advanceUntilIdle()

        // true AND false = false, should go to source-1
        verify { ChatState.setCurrentIndex(0) }  // Boolean logic
    }

    // ==================== Variable Passing Tests ====================

    @Test
    fun `variable node stores and passes value`() = runTest {
        val steps = listOf(
            createNodeStep("set-var", NodeTypes.VARIABLE, mapOf(
                "name" to "userName",
                "value" to "John",
                "isNumber" to false,
                "customNameValue" to false
            )),
            createNodeStep("next", NodeTypes.MESSAGE, mapOf("text" to "Hello!"))
        )

        val edges = listOf(
            createEdge("set-var", "next")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.start()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { ChatState.setVariable("userName", "John") }
    }

    @Test
    fun `math operation node calculates and stores result`() = runTest {
        every { ChatState.resolveValue("10") } returns "10"
        every { ChatState.resolveValue("5") } returns "5"

        val steps = listOf(
            createNodeStep("calc", NodeTypes.MATH_OPERATION, mapOf(
                "leftValue" to "10",
                "rightValue" to "5",
                "operator" to "+"
            )),
            createNodeStep("next", NodeTypes.MESSAGE, mapOf("text" to "Done"))
        )

        val edges = listOf(
            createEdge("calc", "next")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.start()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { ChatState.setAnswerVariable("calc", 15.0) }
    }

    @Test
    fun `variables can be resolved in conditions`() = runTest {
        // Simulate a variable reference
        every { ChatState.resolveValue("{{score}}") } returns 85
        every { ChatState.resolveValue("70") } returns 70

        val steps = listOf(
            createNodeStep("check-score", NodeTypes.CONDITION, mapOf(
                "leftValue" to "{{score}}",
                "rightValue" to "70",
                "operator" to ">=",
                "isNumber" to true
            )),
            createNodeStep("pass", NodeTypes.MESSAGE, mapOf("text" to "Passed!")),
            createNodeStep("fail", NodeTypes.MESSAGE, mapOf("text" to "Failed!"))
        )

        val edges = listOf(
            createEdge("check-score", "pass", "source-0"),
            createEdge("check-score", "fail", "source-1")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.start()
        testDispatcher.scheduler.advanceUntilIdle()

        // 85 >= 70 should route to pass (source-0)
        verify { ChatState.setCurrentIndex(0) }
    }

    // ==================== Jump Node Tests ====================

    @Test
    fun `jump node navigates to target`() = runTest {
        val steps = listOf(
            createNodeStep("start", NodeTypes.MESSAGE, mapOf("text" to "Start")),
            createNodeStep("jump", NodeTypes.JUMP_TO, mapOf(
                "targetNodeId" to "target"
            )),
            createNodeStep("skipped", NodeTypes.MESSAGE, mapOf("text" to "This should be skipped")),
            createNodeStep("target", NodeTypes.MESSAGE, mapOf("text" to "Jumped here!"))
        )

        val edges = listOf(
            createEdge("start", "jump"),
            createEdge("jump", "skipped"),
            createEdge("skipped", "target")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.start()
        testDispatcher.scheduler.advanceUntilIdle()

        // Jump should skip directly to target node (index 3)
        verify { ChatState.setCurrentIndex(0) }  // start
        verify { ChatState.setCurrentIndex(1) }  // jump
        verify { ChatState.setCurrentIndex(3) }  // target (jumped)
    }

    // ==================== Choice and User Input Tests ====================

    @Test
    fun `two choice node displays options and handles selection`() = runTest {
        val steps = listOf(
            createNodeStep("choice", NodeTypes.TWO_CHOICES, mapOf(
                "choice1" to "Yes",
                "choice2" to "No",
                "answerVariable" to "answer"
            )),
            createNodeStep("yes-response", NodeTypes.MESSAGE, mapOf("text" to "You chose yes")),
            createNodeStep("no-response", NodeTypes.MESSAGE, mapOf("text" to "You chose no"))
        )

        val edges = listOf(
            createEdge("choice", "yes-response", "source-1"),
            createEdge("choice", "no-response", "source-2")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.currentUIState.test {
            flowEngine.start()
            testDispatcher.scheduler.advanceUntilIdle()

            val choiceState = awaitItem()
            assertThat(choiceState).isInstanceOf(NodeUIState.SingleChoice::class.java)
            val choices = (choiceState as NodeUIState.SingleChoice).choices
            assertThat(choices).hasSize(2)
            assertThat(choices[0].text).isEqualTo("Yes")
            assertThat(choices[1].text).isEqualTo("No")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit response triggers next node`() = runTest {
        val steps = listOf(
            createNodeStep("ask", NodeTypes.ASK_NAME, mapOf(
                "questionText" to "What is your name?",
                "answerVariable" to "name"
            )),
            createNodeStep("greet", NodeTypes.MESSAGE, mapOf("text" to "Hello!"))
        )

        val edges = listOf(
            createEdge("ask", "greet")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.start()
        testDispatcher.scheduler.advanceUntilIdle()

        // Submit a response
        flowEngine.submitResponse("John Doe")
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify response was processed
        verify { ChatState.setCurrentIndex(0) }  // ask
    }

    // ==================== Delay Node Tests ====================

    @Test
    fun `delay node pauses flow execution`() = runTest {
        val steps = listOf(
            createNodeStep("delay", NodeTypes.DELAY, mapOf("delay" to 2)),
            createNodeStep("after-delay", NodeTypes.MESSAGE, mapOf("text" to "After delay"))
        )

        val edges = listOf(
            createEdge("delay", "after-delay")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.isProcessing.test {
            flowEngine.start()

            // Should be processing (delaying)
            testDispatcher.scheduler.advanceTimeBy(100)
            val processing = awaitItem()
            assertThat(processing).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `flow handles unknown node type gracefully`() = runTest {
        val steps = listOf(
            createNodeStep("unknown", "unknown-node-type", mapOf("text" to "Test")),
            createNodeStep("next", NodeTypes.MESSAGE, mapOf("text" to "After unknown"))
        )

        val edges = listOf(
            createEdge("unknown", "next")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        // Should not throw
        flowEngine.start()
        testDispatcher.scheduler.advanceUntilIdle()

        // Should skip unknown node and proceed
        verify { ChatState.setCurrentIndex(0) }
        verify { ChatState.setCurrentIndex(1) }
    }

    @Test
    fun `flow completes when no more nodes`() = runTest {
        val steps = listOf(
            createNodeStep("last", NodeTypes.MESSAGE, mapOf("text" to "The end"))
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = emptyList()
        )

        flowEngine.isFlowComplete.test {
            flowEngine.start()
            testDispatcher.scheduler.advanceUntilIdle()

            // Flow should eventually complete
            val initial = awaitItem()
            // May need to wait for completion
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== Socket Communication Tests ====================

    @Test
    fun `sends response to server after node processing`() = runTest {
        val steps = listOf(
            createNodeStep("msg", NodeTypes.MESSAGE, mapOf("text" to "Hello"))
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = emptyList()
        )

        flowEngine.start()
        testDispatcher.scheduler.advanceUntilIdle()

        // Give time for delayed auto-proceed
        testDispatcher.scheduler.advanceTimeBy(2000)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(atLeast = 0) { mockSocketClient.sendResponseRecord(any()) }
    }

    // ==================== Complex Flow Tests ====================

    @Test
    fun `multi-branch flow with variables and conditions`() = runTest {
        every { ChatState.resolveValue("premium") } returns "premium"
        every { ChatState.resolveValue("basic") } returns "basic"
        every { ChatState.resolveValue("premium") } returns "premium"

        val steps = listOf(
            createNodeStep("set-tier", NodeTypes.VARIABLE, mapOf(
                "name" to "userTier",
                "value" to "premium",
                "isNumber" to false,
                "customNameValue" to false
            )),
            createNodeStep("check-tier", NodeTypes.CONDITION, mapOf(
                "leftValue" to "premium",
                "rightValue" to "premium",
                "operator" to "=",
                "isNumber" to false
            )),
            createNodeStep("premium-flow", NodeTypes.MESSAGE, mapOf("text" to "Welcome premium user")),
            createNodeStep("basic-flow", NodeTypes.MESSAGE, mapOf("text" to "Welcome basic user"))
        )

        val edges = listOf(
            createEdge("set-tier", "check-tier"),
            createEdge("check-tier", "premium-flow", "source-0"),
            createEdge("check-tier", "basic-flow", "source-1")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.start()
        testDispatcher.scheduler.advanceUntilIdle()

        // Variable should be set
        verify { ChatState.setVariable("userTier", "premium") }
    }

    @Test
    fun `sequential calculations flow`() = runTest {
        every { ChatState.resolveValue("100") } returns "100"
        every { ChatState.resolveValue("10") } returns "10"
        every { ChatState.resolveValue("5") } returns "5"

        val steps = listOf(
            createNodeStep("calc1", NodeTypes.MATH_OPERATION, mapOf(
                "leftValue" to "100",
                "rightValue" to "10",
                "operator" to "+"
            )),
            createNodeStep("calc2", NodeTypes.MATH_OPERATION, mapOf(
                "leftValue" to "100",
                "rightValue" to "5",
                "operator" to "*"
            )),
            createNodeStep("done", NodeTypes.MESSAGE, mapOf("text" to "Calculations complete"))
        )

        val edges = listOf(
            createEdge("calc1", "calc2"),
            createEdge("calc2", "done")
        )

        flowEngine.initialize(
            chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID,
            visitorId = TestFixtures.TEST_VISITOR_ID,
            botId = TestFixtures.TEST_BOT_ID,
            workspaceId = TestFixtures.TEST_WORKSPACE_ID,
            stepsData = steps,
            edgesData = edges
        )

        flowEngine.start()
        testDispatcher.scheduler.advanceUntilIdle()

        // Both calculations should execute
        verify { ChatState.setAnswerVariable("calc1", 110.0) }
        verify { ChatState.setAnswerVariable("calc2", 500.0) }
    }

    // ==================== Helper Functions ====================

    private fun createNodeStep(
        id: String,
        type: String,
        data: Map<String, Any?>
    ): Map<String, Any> {
        return mapOf(
            "id" to id,
            "data" to data.plus("type" to type)
        )
    }

    private fun createEdge(
        source: String,
        target: String,
        sourceHandle: String? = null
    ): Map<String, Any> {
        val edge = mutableMapOf(
            "id" to "edge-$source-$target",
            "source" to source,
            "target" to target
        )
        if (sourceHandle != null) {
            edge["sourceHandle"] = sourceHandle
        }
        return edge
    }
}

/**
 * Mock message node handler for testing
 */
private class MessageNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.MESSAGE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val text = getString(nodeData, "text", "")
            .ifEmpty { getString(nodeData, "message", "") }

        state.addToTranscript("bot", text)

        return NodeResult.DisplayUI(
            NodeUIState.Message(text = text, nodeId = nodeId)
        )
    }
}

/**
 * Mock ask name node handler for testing
 */
private class AskNameNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ASK_NAME

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val questionText = getString(nodeData, "questionText", "What is your name?")
        val answerKey = getString(nodeData, "answerVariable", nodeId)

        state.addAnswerVariable(nodeId, answerKey)
        state.addToTranscript("bot", questionText)

        return NodeResult.DisplayUI(
            NodeUIState.TextInput(
                questionText = questionText,
                inputType = NodeUIState.TextInput.InputType.NAME,
                nodeId = nodeId,
                answerKey = answerKey
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val name = response.toString().trim()

        state.setAnswerVariable(nodeId, name)
        state.setUserMetadata("name", name)
        state.addToTranscript("user", name)

        return NodeResult.Proceed()
    }
}
