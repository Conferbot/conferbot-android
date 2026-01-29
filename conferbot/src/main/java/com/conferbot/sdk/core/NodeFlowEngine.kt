package com.conferbot.sdk.core

import com.conferbot.sdk.core.analytics.ChatAnalytics
import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.services.SocketClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Core engine that processes the chatbot flow
 * Orchestrates node handlers, manages state, and coordinates with socket
 */
class NodeFlowEngine(
    private val socketClient: SocketClient
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Current UI state to render
    private val _currentUIState = MutableStateFlow<NodeUIState?>(null)
    val currentUIState: StateFlow<NodeUIState?> = _currentUIState.asStateFlow()

    // Loading state for typing indicator
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Error state
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Flow is complete
    private val _isFlowComplete = MutableStateFlow(false)
    val isFlowComplete: StateFlow<Boolean> = _isFlowComplete.asStateFlow()

    // Current node being processed
    private var currentNodeId: String? = null
    private var currentNodeData: Map<String, Any?>? = null

    // Reference to steps from server
    private var steps: List<Map<String, Any>> = emptyList()

    // Edge mapping for port-based routing
    private var edges: List<Map<String, Any>> = emptyList()

    /**
     * Initialize the flow engine with bot data from server
     */
    fun initialize(
        chatSessionId: String,
        visitorId: String,
        botId: String,
        workspaceId: String?,
        stepsData: List<Map<String, Any>>,
        edgesData: List<Map<String, Any>>
    ) {
        ChatState.initialize(chatSessionId, visitorId, botId, workspaceId)
        steps = stepsData
        edges = edgesData
        ChatState.setSteps(stepsData)
    }

    /**
     * Start processing from the first node
     */
    fun start() {
        if (steps.isEmpty()) {
            _isFlowComplete.value = true
            return
        }

        scope.launch {
            processNodeAtIndex(0)
        }
    }

    /**
     * Process node at given index
     */
    private suspend fun processNodeAtIndex(index: Int) {
        if (index < 0 || index >= steps.size) {
            _isFlowComplete.value = true
            return
        }

        ChatState.setCurrentIndex(index)
        val node = steps[index]

        @Suppress("UNCHECKED_CAST")
        val nodeId = node["id"]?.toString() ?: return
        val nodeType = (node["data"] as? Map<String, Any?>)?.get("type")?.toString()
            ?: node["type"]?.toString()
            ?: return

        @Suppress("UNCHECKED_CAST")
        val nodeData = node["data"] as? Map<String, Any?> ?: emptyMap()

        currentNodeId = nodeId
        currentNodeData = nodeData

        processNode(nodeId, nodeType, nodeData)
    }

    /**
     * Process a specific node
     */
    private suspend fun processNode(
        nodeId: String,
        nodeType: String,
        nodeData: Map<String, Any?>
    ) {
        _isProcessing.value = true
        _errorMessage.value = null

        // Track node entry for analytics
        val nodeName = nodeData["label"]?.toString()
            ?: nodeData["name"]?.toString()
            ?: nodeType
        ChatAnalytics.trackNodeEntry(nodeId, nodeType, nodeName)

        val handler = NodeHandlerRegistry.getHandler(nodeType)

        if (handler == null) {
            // Unknown node type, skip to next
            _isProcessing.value = false
            ChatAnalytics.trackNodeExit(nodeId, "skipped")
            proceedToNextNode(null)
            return
        }

        try {
            val result = handler.process(nodeData, nodeId)
            handleNodeResult(result, nodeData)
        } catch (e: Exception) {
            _errorMessage.value = "Error processing node: ${e.message}"
            _isProcessing.value = false
            ChatAnalytics.trackNodeExit(nodeId, "error")
            // Try to proceed anyway
            proceedToNextNode(null)
        }
    }

    /**
     * Handle the result from a node handler
     */
    private suspend fun handleNodeResult(result: NodeResult, nodeData: Map<String, Any?>) {
        when (result) {
            is NodeResult.DisplayUI -> {
                _isProcessing.value = false
                _currentUIState.value = result.uiState

                // For message-only nodes, auto-proceed after delay
                if (result.uiState is NodeUIState.Message ||
                    result.uiState is NodeUIState.Image ||
                    result.uiState is NodeUIState.Video ||
                    result.uiState is NodeUIState.Audio ||
                    result.uiState is NodeUIState.File ||
                    result.uiState is NodeUIState.Html
                ) {
                    delay(1000)  // Show message for 1 second
                    // Track node exit for auto-proceed nodes
                    currentNodeId?.let { ChatAnalytics.trackNodeExit(it, "proceeded") }
                    sendResponseToServer()
                    proceedToNextNode(null)
                }
            }

            is NodeResult.Proceed -> {
                _isProcessing.value = false
                // Track node exit
                currentNodeId?.let { ChatAnalytics.trackNodeExit(it, "proceeded") }
                sendResponseToServer()
                proceedToNextNode(result.targetPort)
            }

            is NodeResult.DelayedProceed -> {
                _isProcessing.value = true
                delay(result.delayMs)
                _isProcessing.value = false
                // Track node exit after delay
                currentNodeId?.let { ChatAnalytics.trackNodeExit(it, "proceeded") }
                sendResponseToServer()
                proceedToNextNode(result.targetPort)
            }

            is NodeResult.JumpTo -> {
                _isProcessing.value = false
                // Track node exit for jump
                currentNodeId?.let { ChatAnalytics.trackNodeExit(it, "proceeded") }
                sendResponseToServer()
                jumpToNode(result.targetNodeId)
            }

            is NodeResult.Error -> {
                _isProcessing.value = false
                _errorMessage.value = result.message
                // Track node exit with error
                currentNodeId?.let { ChatAnalytics.trackNodeExit(it, "error") }
                if (result.shouldProceed) {
                    sendResponseToServer()
                    proceedToNextNode(null)
                }
            }

            is NodeResult.ExecuteIntegration -> {
                // Execute integration via socket and wait for result
                _isProcessing.value = true
                executeIntegration(result, nodeData)
            }
        }
    }

    /**
     * Execute a native integration via socket
     */
    private fun executeIntegration(result: NodeResult.ExecuteIntegration, nodeData: Map<String, Any?>) {
        val chatSessionId = ChatState.chatSessionId ?: return
        val botId = ChatState.botId ?: return
        val workspaceId = ChatState.workspaceId
        val answerVariables = ChatState.getAnswerVariablesMap()

        socketClient.executeIntegration(
            nodeType = result.nodeType,
            nodeId = result.nodeId,
            nodeData = result.nodeData,
            chatSessionId = chatSessionId,
            chatbotId = botId,
            workspaceId = workspaceId,
            answerVariables = answerVariables,
            callback = { integrationResult ->
                // Convert to IntegrationResultData and call the handler's callback
                val resultData = IntegrationResultData(
                    success = integrationResult.success,
                    error = integrationResult.error,
                    data = integrationResult.data,
                    message = integrationResult.message,
                    answerVariable = integrationResult.answerVariable,
                    answerValue = integrationResult.answerValue
                )

                // Store answer variable if provided
                if (integrationResult.answerVariable != null && integrationResult.answerValue != null) {
                    ChatState.setAnswerVariableByKey(
                        integrationResult.answerVariable,
                        integrationResult.answerValue
                    )
                }

                // Call the handler's callback to get the next result
                val nextResult = result.onResult(resultData)

                // Handle the next result on the main thread
                scope.launch {
                    handleNodeResult(nextResult, nodeData)
                }
            }
        )
    }

    /**
     * Handle user response for current interactive node
     */
    fun submitResponse(response: Any) {
        val nodeId = currentNodeId ?: return
        val nodeData = currentNodeData ?: return

        scope.launch {
            _isProcessing.value = true
            _errorMessage.value = null

            // Track user message for analytics if it's a text response
            if (response is String) {
                ChatAnalytics.trackUserMessage(
                    messageLength = response.length,
                    messageText = response
                )
            }

            @Suppress("UNCHECKED_CAST")
            val nodeType = nodeData["type"]?.toString() ?: return@launch

            val handler = NodeHandlerRegistry.getHandler(nodeType)
            if (handler == null) {
                _isProcessing.value = false
                ChatAnalytics.trackNodeExit(nodeId, "skipped")
                proceedToNextNode(null)
                return@launch
            }

            try {
                val result = handler.handleResponse(response, nodeData, nodeId)
                // Track node exit with user input
                val userInput = if (response is String) response else response.toString()
                ChatAnalytics.trackNodeExit(nodeId, "proceeded", userInput = userInput)
                handleNodeResult(result, nodeData)
            } catch (e: Exception) {
                _errorMessage.value = e.message
                _isProcessing.value = false
                ChatAnalytics.trackNodeExit(nodeId, "error")
            }
        }
    }

    /**
     * Clear current error
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Proceed to the next node in the flow
     */
    private suspend fun proceedToNextNode(targetPort: String?) {
        val currentId = currentNodeId ?: return

        // Find next node based on edges
        val nextNodeId = if (targetPort != null) {
            findNextNodeByPort(currentId, targetPort)
        } else {
            findNextNodeByDefaultEdge(currentId)
        }

        if (nextNodeId != null) {
            val nextIndex = steps.indexOfFirst { it["id"] == nextNodeId }
            if (nextIndex >= 0) {
                processNodeAtIndex(nextIndex)
                return
            }
        }

        // Try sequential fallback
        val currentIndex = ChatState.currentIndex.value
        if (currentIndex + 1 < steps.size) {
            processNodeAtIndex(currentIndex + 1)
        } else {
            _isFlowComplete.value = true
        }
    }

    /**
     * Jump to a specific node by ID
     */
    private suspend fun jumpToNode(targetNodeId: String) {
        val targetIndex = steps.indexOfFirst { it["id"] == targetNodeId }
        if (targetIndex >= 0) {
            processNodeAtIndex(targetIndex)
        } else {
            // Node not found, proceed sequentially
            proceedToNextNode(null)
        }
    }

    /**
     * Find next node using edge with specific source port
     */
    private fun findNextNodeByPort(sourceId: String, sourcePort: String): String? {
        // Look for edge from this node's specific port
        val edge = edges.find { edge ->
            edge["source"] == sourceId &&
                    (edge["sourceHandle"] == sourcePort || edge["sourceHandle"] == sourcePort.removePrefix("source-"))
        }
        return edge?.get("target")?.toString()
    }

    /**
     * Find next node using default edge (any edge from source)
     */
    private fun findNextNodeByDefaultEdge(sourceId: String): String? {
        val edge = edges.find { it["source"] == sourceId }
        return edge?.get("target")?.toString()
    }

    /**
     * Send current state to server via socket
     */
    private fun sendResponseToServer() {
        val responseData = ChatState.buildResponseData()
        socketClient.sendResponseRecord(responseData)
    }

    /**
     * Handle incoming bot message from server
     */
    fun handleServerMessage(message: Map<String, Any?>) {
        scope.launch {
            val nodeData = message["nodeData"] as? Map<String, Any?>
            val nodeType = nodeData?.get("type")?.toString()
                ?: message["type"]?.toString()

            if (nodeType != null && nodeData != null) {
                val nodeId = message["_id"]?.toString() ?: message["id"]?.toString() ?: ""
                currentNodeId = nodeId
                currentNodeData = nodeData
                processNode(nodeId, nodeType, nodeData)
            } else {
                // Plain text message
                val text = message["text"]?.toString() ?: return@launch
                _currentUIState.value = NodeUIState.Message(
                    text = text,
                    nodeId = message["_id"]?.toString() ?: ""
                )
            }
        }
    }

    /**
     * Handle agent-related events
     */
    fun handleAgentAccepted(agentName: String) {
        val nodeId = currentNodeId ?: return
        val nodeData = currentNodeData ?: return

        if (nodeData["type"]?.toString() == NodeTypes.HUMAN_HANDOVER) {
            val handler = NodeHandlerRegistry.getHandler(NodeTypes.HUMAN_HANDOVER)
                    as? com.conferbot.sdk.core.nodes.handlers.HumanHandoverNodeHandler

            handler?.onAgentAccepted(nodeId, agentName)

            _currentUIState.value = NodeUIState.HumanHandover(
                state = NodeUIState.HumanHandover.HandoverState.AGENT_CONNECTED,
                agentName = agentName,
                nodeId = nodeId
            )
        }
    }

    /**
     * Handle no agents available
     */
    fun handleNoAgentsAvailable() {
        val nodeId = currentNodeId ?: return
        val nodeData = currentNodeData ?: return

        if (nodeData["type"]?.toString() == NodeTypes.HUMAN_HANDOVER) {
            val handler = NodeHandlerRegistry.getHandler(NodeTypes.HUMAN_HANDOVER)
                    as? com.conferbot.sdk.core.nodes.handlers.HumanHandoverNodeHandler

            scope.launch {
                val result = handler?.onNoAgentsAvailable(nodeId, nodeData)
                if (result != null) {
                    handleNodeResult(result, nodeData)
                }
            }
        }
    }

    /**
     * Handle chat ended (for handover)
     * Triggers post-chat survey if configured
     */
    fun handleChatEnded() {
        val nodeId = currentNodeId ?: return
        val nodeData = currentNodeData ?: return

        if (nodeData["type"]?.toString() == NodeTypes.HUMAN_HANDOVER) {
            val handler = NodeHandlerRegistry.getHandler(NodeTypes.HUMAN_HANDOVER)
                    as? com.conferbot.sdk.core.nodes.handlers.HumanHandoverNodeHandler

            scope.launch {
                val result = handler?.onChatEnded(nodeId, nodeData)
                if (result != null) {
                    // Track survey display for analytics
                    if (result is NodeResult.DisplayUI && result.uiState is NodeUIState.PostChatSurvey) {
                        ChatAnalytics.trackNodeEntry(
                            nodeId = "${nodeId}_survey",
                            nodeType = "post-chat-survey",
                            nodeName = "Post Chat Survey"
                        )
                    }
                    handleNodeResult(result, nodeData)
                }
            }
        }
    }

    /**
     * Handle post-chat survey response submission
     * Sends survey responses to server via socket
     */
    fun handleSurveySubmission(nodeId: String, responses: List<NodeUIState.SurveyResponse>) {
        val handler = NodeHandlerRegistry.getHandler(NodeTypes.HUMAN_HANDOVER)
                as? com.conferbot.sdk.core.nodes.handlers.HumanHandoverNodeHandler

        // Get survey responses map for socket
        val surveyData = handler?.getSurveyResponsesForSocket(nodeId)

        // Send survey responses to server
        if (surveyData != null && surveyData.isNotEmpty()) {
            socketClient.sendPostChatSurveyResponse(
                chatSessionId = ChatState.chatSessionId.value ?: "",
                surveyResponses = surveyData
            )
        }

        // Track survey completion for analytics
        ChatAnalytics.trackNodeExit(
            nodeId = "${nodeId}_survey",
            exitType = "proceeded",
            userInput = "Survey completed with ${responses.size} responses"
        )
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        // Finalize analytics before cleanup
        ChatAnalytics.finalizeChatAnalytics()
        scope.cancel()
        ChatState.reset()
    }
}
