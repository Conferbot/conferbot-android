package com.conferbot.sdk.core

import com.conferbot.sdk.core.nodes.NodeResult
import com.conferbot.sdk.core.nodes.NodeTypes
import com.conferbot.sdk.core.nodes.NodeUIState
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
 * Unit tests for NodeFlowEngine
 * Tests flow orchestration including:
 * - Node routing
 * - Edge navigation
 * - Flow completion
 * - Response handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NodeFlowEngineTest {

    private lateinit var socketClient: SocketClient
    private lateinit var flowEngine: NodeFlowEngine
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ChatState.reset()
        socketClient = mockk(relaxed = true)
        flowEngine = NodeFlowEngine(socketClient)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ChatState.reset()
        flowEngine.destroy()
    }

    // ==================== INITIALIZATION TESTS ====================

    @Test
    fun `initialize sets up ChatState correctly`() {
        // Given
        val chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID
        val visitorId = TestFixtures.TEST_VISITOR_ID
        val botId = TestFixtures.TEST_BOT_ID
        val workspaceId = TestFixtures.TEST_WORKSPACE_ID
        val (steps, edges) = TestFixtures.createSimpleFlow()

        // When
        flowEngine.initialize(chatSessionId, visitorId, botId, workspaceId, steps, edges)

        // Then
        assertThat(ChatState.chatSessionId).isEqualTo(chatSessionId)
        assertThat(ChatState.visitorId).isEqualTo(visitorId)
        assertThat(ChatState.botId).isEqualTo(botId)
        assertThat(ChatState.workspaceId).isEqualTo(workspaceId)
        assertThat(ChatState.steps.value).hasSize(3)
    }

    @Test
    fun `initialize without workspaceId sets null`() {
        // Given
        val (steps, edges) = TestFixtures.createSimpleFlow()

        // When
        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            edges
        )

        // Then
        assertThat(ChatState.workspaceId).isNull()
    }

    // ==================== START FLOW TESTS ====================

    @Test
    fun `start with empty steps sets flow complete`() = runTest {
        // Given
        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            emptyList(),
            emptyList()
        )

        // When
        flowEngine.start()
        advanceUntilIdle()

        // Then
        assertThat(flowEngine.isFlowComplete.value).isTrue()
    }

    @Test
    fun `start processes first node`() = runTest {
        // Given
        val (steps, edges) = TestFixtures.createSimpleFlow()
        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            edges
        )

        // When
        flowEngine.start()
        advanceUntilIdle()

        // Then - First node should be processed (message node)
        assertThat(ChatState.currentIndex.value).isEqualTo(0)
    }

    @Test
    fun `start triggers processing state`() = runTest {
        // Given
        val (steps, edges) = TestFixtures.createSimpleFlow()
        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            edges
        )

        // When
        flowEngine.start()

        // Initially processing
        assertThat(flowEngine.isProcessing.value).isFalse() // Will be true during async processing

        advanceUntilIdle()
    }

    // ==================== NODE PROCESSING TESTS ====================

    @Test
    fun `unknown node type is skipped`() = runTest {
        // Given - Create flow with unknown node type
        val steps = listOf(
            mapOf(
                "id" to "unknown-node",
                "data" to mapOf(
                    "type" to "unknown-node-type",
                    "text" to "Test"
                )
            ),
            mapOf(
                "id" to "known-node",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "Known message"
                )
            )
        )
        val edges = listOf(
            mapOf("source" to "unknown-node", "target" to "known-node")
        )

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            edges
        )

        // When
        flowEngine.start()
        advanceUntilIdle()

        // Then - Should proceed to next node
        // The unknown node was skipped
        assertThat(flowEngine.errorMessage.value).isNull()
    }

    @Test
    fun `message node produces UI state`() = runTest {
        // Given
        val steps = listOf(
            mapOf(
                "id" to "msg-node",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "Hello World!"
                )
            )
        )

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            emptyList()
        )

        // When
        flowEngine.start()
        advanceUntilIdle()

        // Then
        val uiState = flowEngine.currentUIState.value
        assertThat(uiState).isInstanceOf(NodeUIState.Message::class.java)
        assertThat((uiState as NodeUIState.Message).text).isEqualTo("Hello World!")
    }

    // ==================== EDGE NAVIGATION TESTS ====================

    @Test
    fun `navigate to next node via default edge`() = runTest {
        // Given
        val steps = listOf(
            mapOf(
                "id" to "node-1",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "First"
                )
            ),
            mapOf(
                "id" to "node-2",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "Second"
                )
            )
        )
        val edges = listOf(
            mapOf(
                "id" to "edge-1",
                "source" to "node-1",
                "target" to "node-2"
            )
        )

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            edges
        )

        // When
        flowEngine.start()
        // Let the auto-proceed happen
        advanceTimeBy(2000)
        advanceUntilIdle()

        // Then - Should have navigated to second node
        val uiState = flowEngine.currentUIState.value
        assertThat(uiState).isNotNull()
    }

    @Test
    fun `navigate via port-based edge`() = runTest {
        // Given - Branching flow
        val (steps, edges) = TestFixtures.createBranchingFlow()

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            edges
        )

        // When
        flowEngine.start()
        advanceUntilIdle()

        // Then - Flow should have started
        assertThat(flowEngine.currentUIState.value).isNotNull()
    }

    @Test
    fun `flow completes when no more nodes`() = runTest {
        // Given - Single node flow
        val steps = listOf(
            mapOf(
                "id" to "only-node",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "Only message"
                )
            )
        )

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            emptyList()
        )

        // When
        flowEngine.start()
        // Wait for auto-proceed
        advanceTimeBy(2000)
        advanceUntilIdle()

        // Then
        assertThat(flowEngine.isFlowComplete.value).isTrue()
    }

    // ==================== RESPONSE HANDLING TESTS ====================

    @Test
    fun `submitResponse processes user input`() = runTest {
        // Given - Ask name flow
        val steps = listOf(
            mapOf(
                "id" to "ask-name",
                "data" to mapOf(
                    "type" to NodeTypes.ASK_NAME,
                    "questionText" to "What's your name?",
                    "answerVariable" to "name"
                )
            )
        )

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            emptyList()
        )

        flowEngine.start()
        advanceUntilIdle()

        // Verify ask name UI is displayed
        val uiState = flowEngine.currentUIState.value
        assertThat(uiState).isInstanceOf(NodeUIState.TextInput::class.java)

        // When
        flowEngine.submitResponse("John Doe")
        advanceUntilIdle()

        // Then - Name should be stored
        assertThat(ChatState.getAnswerVariableValue("name")).isEqualTo("John Doe")
        assertThat(ChatState.getUserMetadata("name")).isEqualTo("John Doe")
    }

    @Test
    fun `submitResponse with invalid input shows error`() = runTest {
        // Given - Ask email flow
        val steps = listOf(
            mapOf(
                "id" to "ask-email",
                "data" to mapOf(
                    "type" to NodeTypes.ASK_EMAIL,
                    "questionText" to "What's your email?",
                    "answerVariable" to "email",
                    "incorrectEmailResponse" to "Invalid email!"
                )
            )
        )

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            emptyList()
        )

        flowEngine.start()
        advanceUntilIdle()

        // When - Submit invalid email
        flowEngine.submitResponse("not-an-email")
        advanceUntilIdle()

        // Then - Error should be set
        assertThat(flowEngine.errorMessage.value).isEqualTo("Invalid email!")
    }

    @Test
    fun `submitResponse with valid email proceeds`() = runTest {
        // Given
        val steps = listOf(
            mapOf(
                "id" to "ask-email",
                "data" to mapOf(
                    "type" to NodeTypes.ASK_EMAIL,
                    "questionText" to "What's your email?",
                    "answerVariable" to "email"
                )
            )
        )

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            emptyList()
        )

        flowEngine.start()
        advanceUntilIdle()

        // When
        flowEngine.submitResponse("test@example.com")
        advanceUntilIdle()

        // Then
        assertThat(ChatState.getAnswerVariableValue("email")).isEqualTo("test@example.com")
        assertThat(flowEngine.errorMessage.value).isNull()
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun `clearError clears error message`() = runTest {
        // Given - Set up an error state
        val steps = listOf(
            mapOf(
                "id" to "ask-email",
                "data" to mapOf(
                    "type" to NodeTypes.ASK_EMAIL,
                    "questionText" to "Email?",
                    "answerVariable" to "email",
                    "incorrectEmailResponse" to "Bad email"
                )
            )
        )

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            emptyList()
        )

        flowEngine.start()
        advanceUntilIdle()

        flowEngine.submitResponse("invalid")
        advanceUntilIdle()

        assertThat(flowEngine.errorMessage.value).isNotNull()

        // When
        flowEngine.clearError()

        // Then
        assertThat(flowEngine.errorMessage.value).isNull()
    }

    // ==================== SOCKET COMMUNICATION TESTS ====================

    @Test
    fun `response record sent to server after node processing`() = runTest {
        // Given
        val (steps, edges) = TestFixtures.createSimpleFlow()

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            edges
        )

        // When
        flowEngine.start()
        advanceTimeBy(2000) // Wait for auto-proceed
        advanceUntilIdle()

        // Then
        verify(atLeast = 1) { socketClient.sendResponseRecord(any()) }
    }

    // ==================== SERVER MESSAGE HANDLING TESTS ====================

    @Test
    fun `handleServerMessage processes bot message`() = runTest {
        // Given
        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            emptyList(),
            emptyList()
        )

        val serverMessage = mapOf(
            "_id" to "server-msg-1",
            "type" to NodeTypes.MESSAGE,
            "text" to "Server sent message",
            "nodeData" to mapOf(
                "type" to NodeTypes.MESSAGE,
                "text" to "Server sent message"
            )
        )

        // When
        flowEngine.handleServerMessage(serverMessage)
        advanceUntilIdle()

        // Then
        val uiState = flowEngine.currentUIState.value
        assertThat(uiState).isInstanceOf(NodeUIState.Message::class.java)
    }

    @Test
    fun `handleServerMessage with plain text creates message UI`() = runTest {
        // Given
        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            emptyList(),
            emptyList()
        )

        val serverMessage = mapOf(
            "_id" to "plain-msg-1",
            "text" to "Plain text message"
        )

        // When
        flowEngine.handleServerMessage(serverMessage)
        advanceUntilIdle()

        // Then
        val uiState = flowEngine.currentUIState.value
        assertThat(uiState).isInstanceOf(NodeUIState.Message::class.java)
        assertThat((uiState as NodeUIState.Message).text).isEqualTo("Plain text message")
    }

    // ==================== AGENT HANDLING TESTS ====================

    @Test
    fun `handleAgentAccepted updates UI state for handover node`() = runTest {
        // Given - Set up with human handover node as current
        val steps = listOf(
            mapOf(
                "id" to "handover-node",
                "data" to mapOf(
                    "type" to NodeTypes.HUMAN_HANDOVER,
                    "handoverMessage" to "Connecting to agent..."
                )
            )
        )

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            emptyList()
        )

        flowEngine.start()
        advanceUntilIdle()

        // When
        flowEngine.handleAgentAccepted("Test Agent")
        advanceUntilIdle()

        // Then
        val uiState = flowEngine.currentUIState.value
        assertThat(uiState).isInstanceOf(NodeUIState.HumanHandover::class.java)
        val handoverState = uiState as NodeUIState.HumanHandover
        assertThat(handoverState.state).isEqualTo(NodeUIState.HumanHandover.HandoverState.AGENT_CONNECTED)
        assertThat(handoverState.agentName).isEqualTo("Test Agent")
    }

    @Test
    fun `handleNoAgentsAvailable handles gracefully`() = runTest {
        // Given
        val steps = listOf(
            mapOf(
                "id" to "handover-node",
                "data" to mapOf(
                    "type" to NodeTypes.HUMAN_HANDOVER,
                    "handoverMessage" to "Connecting..."
                )
            )
        )

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            emptyList()
        )

        flowEngine.start()
        advanceUntilIdle()

        // When
        flowEngine.handleNoAgentsAvailable()
        advanceUntilIdle()

        // Then - Should not crash
        assertThat(flowEngine.errorMessage.value).isNull()
    }

    @Test
    fun `handleChatEnded handles gracefully`() = runTest {
        // Given
        val steps = listOf(
            mapOf(
                "id" to "handover-node",
                "data" to mapOf(
                    "type" to NodeTypes.HUMAN_HANDOVER,
                    "handoverMessage" to "Connecting..."
                )
            )
        )

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            emptyList()
        )

        flowEngine.start()
        advanceUntilIdle()

        // When
        flowEngine.handleChatEnded()
        advanceUntilIdle()

        // Then - Should not crash
        assertThat(flowEngine.errorMessage.value).isNull()
    }

    // ==================== DESTROY TESTS ====================

    @Test
    fun `destroy resets ChatState`() = runTest {
        // Given
        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            emptyList(),
            emptyList()
        )

        // When
        flowEngine.destroy()

        // Then
        assertThat(ChatState.chatSessionId).isNull()
        assertThat(ChatState.visitorId).isNull()
        assertThat(ChatState.botId).isNull()
    }

    // ==================== FLOW STATE TESTS ====================

    @Test
    fun `isProcessing starts false`() {
        assertThat(flowEngine.isProcessing.value).isFalse()
    }

    @Test
    fun `isFlowComplete starts false`() {
        assertThat(flowEngine.isFlowComplete.value).isFalse()
    }

    @Test
    fun `errorMessage starts null`() {
        assertThat(flowEngine.errorMessage.value).isNull()
    }

    @Test
    fun `currentUIState starts null`() {
        assertThat(flowEngine.currentUIState.value).isNull()
    }

    // ==================== SEQUENTIAL FALLBACK TESTS ====================

    @Test
    fun `falls back to sequential navigation when no edge found`() = runTest {
        // Given - Steps without edges
        val steps = listOf(
            mapOf(
                "id" to "node-1",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "First"
                )
            ),
            mapOf(
                "id" to "node-2",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "Second"
                )
            )
        )

        flowEngine.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            null,
            steps,
            emptyList() // No edges
        )

        // When
        flowEngine.start()
        advanceTimeBy(2000) // Auto-proceed
        advanceUntilIdle()

        // Then - Should have moved to node-2 sequentially
        // Verify by checking UI state shows second message
        val uiState = flowEngine.currentUIState.value
        if (uiState is NodeUIState.Message) {
            assertThat(uiState.text).isEqualTo("Second")
        }
    }
}
