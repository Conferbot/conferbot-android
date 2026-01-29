package com.conferbot.sdk.testutils

import com.conferbot.sdk.services.IntegrationResult
import io.socket.emitter.Emitter

/**
 * Fake SocketClient for unit testing
 * Captures all emitted events and allows verification
 */
class FakeSocketClient(
    val apiKey: String = TestFixtures.TEST_API_KEY,
    val botId: String = TestFixtures.TEST_BOT_ID
) {
    // Pending integration callbacks for simulation
    private val pendingIntegrationCallbacks = mutableMapOf<String, (IntegrationResult) -> Unit>()
    private var _isConnected = false
    val isConnected: Boolean get() = _isConnected

    // Event tracking
    private val emittedEvents = mutableListOf<EmittedEvent>()
    private val eventListeners = mutableMapOf<String, MutableList<Emitter.Listener>>()

    // Response record tracking
    private val sentResponseRecords = mutableListOf<Map<String, Any?>>()

    /**
     * Represents an emitted event with its data
     */
    data class EmittedEvent(
        val event: String,
        val data: Any?
    )

    /**
     * Simulate connecting to the socket server
     */
    fun connect() {
        _isConnected = true
        triggerEvent("connect", arrayOf())
    }

    /**
     * Simulate disconnecting from the socket server
     */
    fun disconnect() {
        _isConnected = false
        triggerEvent("disconnect", arrayOf())
    }

    /**
     * Simulate connection error
     */
    fun simulateConnectionError(error: String = "Connection failed") {
        _isConnected = false
        triggerEvent("connect_error", arrayOf(error))
    }

    /**
     * Simulate reconnection
     */
    fun simulateReconnect() {
        _isConnected = true
        triggerEvent("reconnect", arrayOf())
    }

    /**
     * Record emitted event
     */
    fun emit(event: String, data: Any) {
        if (!_isConnected) {
            return
        }
        emittedEvents.add(EmittedEvent(event, data))
    }

    /**
     * Register event listener
     */
    fun on(event: String, callback: Emitter.Listener) {
        eventListeners.getOrPut(event) { mutableListOf() }.add(callback)
    }

    /**
     * Remove event listener
     */
    fun off(event: String, callback: Emitter.Listener? = null) {
        if (callback != null) {
            eventListeners[event]?.remove(callback)
        } else {
            eventListeners.remove(event)
        }
    }

    /**
     * Simulate receiving an event from server
     */
    fun triggerEvent(event: String, args: Array<Any?>) {
        eventListeners[event]?.forEach { listener ->
            listener.call(*args)
        }
    }

    /**
     * Simulate joining chat room
     */
    fun joinChatRoom(chatSessionId: String, deviceInfo: Map<String, String>? = null) {
        emit("join-chat-room", mapOf(
            "chatSessionId" to chatSessionId,
            "platform" to "android",
            "deviceInfo" to deviceInfo
        ))
    }

    /**
     * Simulate leaving chat room
     */
    fun leaveChatRoom(chatSessionId: String) {
        emit("leave-chat-room", mapOf("chatSessionId" to chatSessionId))
    }

    /**
     * Simulate sending response record
     */
    fun sendResponseRecord(responseData: Map<String, Any?>) {
        sentResponseRecords.add(responseData)
        emit("response-record", responseData)
    }

    /**
     * Simulate sending typing status
     */
    fun sendTypingStatus(chatSessionId: String, isTyping: Boolean) {
        emit("visitor-typing", mapOf(
            "chatSessionId" to chatSessionId,
            "isTyping" to isTyping
        ))
    }

    /**
     * Simulate initiating handover
     */
    fun initiateHandover(chatSessionId: String, message: String? = null) {
        emit("initiate-handover", mapOf(
            "chatSessionId" to chatSessionId,
            "message" to message
        ))
    }

    /**
     * Simulate ending chat
     */
    fun endChat(chatSessionId: String) {
        emit("end-chat", mapOf("chatSessionId" to chatSessionId))
    }

    /**
     * Execute a native integration via socket (for testing)
     * Stores the callback for later simulation of response
     */
    fun executeIntegration(
        nodeType: String,
        nodeId: String,
        nodeData: Map<String, Any?>,
        chatSessionId: String,
        chatbotId: String,
        workspaceId: String?,
        answerVariables: Map<String, Any?>,
        callback: (IntegrationResult) -> Unit,
        timeoutMs: Long = 30000
    ) {
        if (!_isConnected) {
            callback(IntegrationResult(
                success = false,
                error = "Socket not connected",
                nodeId = nodeId
            ))
            return
        }

        // Record the integration request
        emit("execute-integration", mapOf(
            "nodeType" to nodeType,
            "nodeId" to nodeId,
            "nodeData" to nodeData,
            "chatSessionId" to chatSessionId,
            "chatbotId" to chatbotId,
            "workspaceId" to workspaceId,
            "answerVariables" to answerVariables
        ))

        // Store callback for simulation
        pendingIntegrationCallbacks[nodeId] = callback
    }

    /**
     * Simulate integration result from server
     */
    fun simulateIntegrationResult(
        nodeId: String,
        success: Boolean,
        data: Map<String, Any?>? = null,
        error: String? = null,
        message: String? = null
    ) {
        val callback = pendingIntegrationCallbacks.remove(nodeId)
        callback?.invoke(IntegrationResult(
            success = success,
            error = error,
            nodeId = nodeId,
            data = data,
            message = message
        ))
    }

    /**
     * Send post-chat survey response
     */
    fun sendPostChatSurveyResponse(chatSessionId: String, surveyResponses: Map<String, Any>) {
        emit("post-chat-survey-response", mapOf(
            "chatSessionId" to chatSessionId,
            "surveyResponses" to surveyResponses
        ))
    }

    /**
     * Dispose resources
     */
    fun dispose() {
        disconnect()
        eventListeners.clear()
        emittedEvents.clear()
        sentResponseRecords.clear()
        pendingIntegrationCallbacks.clear()
    }

    // ==================== VERIFICATION METHODS ====================

    /**
     * Get all emitted events
     */
    fun getEmittedEvents(): List<EmittedEvent> = emittedEvents.toList()

    /**
     * Get emitted events for a specific event type
     */
    fun getEmittedEvents(eventType: String): List<EmittedEvent> {
        return emittedEvents.filter { it.event == eventType }
    }

    /**
     * Check if an event was emitted
     */
    fun wasEventEmitted(eventType: String): Boolean {
        return emittedEvents.any { it.event == eventType }
    }

    /**
     * Get number of times an event was emitted
     */
    fun getEventEmitCount(eventType: String): Int {
        return emittedEvents.count { it.event == eventType }
    }

    /**
     * Get sent response records
     */
    fun getSentResponseRecords(): List<Map<String, Any?>> = sentResponseRecords.toList()

    /**
     * Get the last sent response record
     */
    fun getLastSentResponseRecord(): Map<String, Any?>? = sentResponseRecords.lastOrNull()

    /**
     * Clear all tracked data
     */
    fun clearTrackedData() {
        emittedEvents.clear()
        sentResponseRecords.clear()
    }

    /**
     * Verify event was emitted with specific data
     */
    fun verifyEventEmitted(eventType: String, dataMatcher: (Any?) -> Boolean): Boolean {
        return emittedEvents.any { it.event == eventType && dataMatcher(it.data) }
    }

    // ==================== SERVER RESPONSE SIMULATION ====================

    /**
     * Simulate receiving a bot message from server
     */
    fun simulateBotMessage(text: String, nodeId: String = "test-node") {
        val messageData = mapOf(
            "_id" to nodeId,
            "type" to "message-node",
            "text" to text,
            "nodeData" to mapOf(
                "type" to "message-node",
                "text" to text
            )
        )
        triggerEvent("bot-message", arrayOf(messageData))
    }

    /**
     * Simulate agent accepted event
     */
    fun simulateAgentAccepted(agentName: String = "Test Agent") {
        val agentData = mapOf(
            "agentName" to agentName,
            "agentId" to "agent-123"
        )
        triggerEvent("agent-accepted", arrayOf(agentData))
    }

    /**
     * Simulate no agents available event
     */
    fun simulateNoAgentsAvailable() {
        triggerEvent("no-agents-available", arrayOf())
    }

    /**
     * Simulate chat ended event
     */
    fun simulateChatEnded() {
        triggerEvent("chat-ended", arrayOf())
    }

    /**
     * Simulate agent message
     */
    fun simulateAgentMessage(text: String, agentName: String = "Test Agent") {
        val messageData = mapOf(
            "_id" to "agent-msg-${System.currentTimeMillis()}",
            "type" to "agent-message",
            "text" to text,
            "agentDetails" to mapOf(
                "name" to agentName,
                "id" to "agent-123"
            )
        )
        triggerEvent("agent-message", arrayOf(messageData))
    }
}
