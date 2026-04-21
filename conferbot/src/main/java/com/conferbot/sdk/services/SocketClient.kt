package com.conferbot.sdk.services

import android.util.Log
import com.conferbot.sdk.core.queue.MessageType
import com.conferbot.sdk.core.queue.OfflineManager
import com.conferbot.sdk.core.queue.QueuedMessage
import com.conferbot.sdk.models.SocketEvents
import com.conferbot.sdk.utils.ConferBotNetworkConfig
import com.conferbot.sdk.utils.Constants
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * Result from a native integration execution
 */
data class IntegrationResult(
    val success: Boolean,
    val error: String? = null,
    val nodeId: String,
    val operation: String? = null,
    val data: Map<String, Any?>? = null,
    val message: String? = null,
    val answerVariable: String? = null,
    val answerValue: Any? = null
)

/**
 * Socket client for real-time communication with offline queue support
 */
class SocketClient(
    private val apiKey: String,
    private val botId: String,
    socketUrl: String = Constants.DEFAULT_SOCKET_URL
) {
    private var socket: Socket? = null
    private var _isConnected = false

    val isConnected: Boolean
        get() = _isConnected

    // Offline manager reference (set by Conferbot during initialization)
    private var offlineManager: OfflineManager? = null

    // StateFlow for connection status
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Reconnection listener
    private var reconnectionListener: (() -> Unit)? = null

    init {
        try {
            val options = IO.Options().apply {
                transports = arrayOf("websocket", "polling")
                reconnection = true
                reconnectionAttempts = ConferBotNetworkConfig.reconnectionAttempts
                reconnectionDelay = ConferBotNetworkConfig.reconnectionDelay.toLong()
                reconnectionDelayMax = ConferBotNetworkConfig.reconnectionDelayMax.toLong()
                timeout = ConferBotNetworkConfig.socketTimeout
                extraHeaders = mapOf(
                    Constants.HEADER_API_KEY to listOf(apiKey),
                    Constants.HEADER_BOT_ID to listOf(botId),
                    Constants.HEADER_PLATFORM to listOf(Constants.PLATFORM_IDENTIFIER)
                )
            }

            socket = IO.socket(socketUrl, options)
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to create socket", e)
        }
    }

    /**
     * Connection state enumeration
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING
    }

    /**
     * Set the offline manager for queue support
     */
    fun setOfflineManager(manager: OfflineManager) {
        offlineManager = manager

        // Set up the message sender callback
        manager.setMessageSender { message ->
            sendQueuedMessage(message)
        }
    }

    /**
     * Set reconnection listener
     */
    fun setReconnectionListener(listener: () -> Unit) {
        reconnectionListener = listener
    }

    /**
     * Connect to socket server
     */
    fun connect() {
        if (socket?.connected() == true) {
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        setupConnectionHandlers()
        socket?.connect()
    }

    /**
     * Setup connection event handlers
     */
    private fun setupConnectionHandlers() {
        socket?.on(SocketEvents.CONNECT) {
            _isConnected = true
            _connectionState.value = ConnectionState.CONNECTED
            Log.d(TAG, "Connected")

            // Trigger queue processing on connection
            offlineManager?.processQueue()
        }

        socket?.on(SocketEvents.DISCONNECT) {
            _isConnected = false
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.d(TAG, "Disconnected")
        }

        socket?.on(SocketEvents.CONNECT_ERROR) { args ->
            _isConnected = false
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.e(TAG, "Connection error: ${args.firstOrNull()}")
        }

        socket?.on(SocketEvents.RECONNECT) {
            _isConnected = true
            _connectionState.value = ConnectionState.CONNECTED
            Log.d(TAG, "Reconnected")

            // Notify reconnection listener
            reconnectionListener?.invoke()

            // Process queued messages
            offlineManager?.processQueue()
        }

        socket?.on(SocketEvents.RECONNECT_ATTEMPT) {
            _connectionState.value = ConnectionState.RECONNECTING
            Log.d(TAG, "Reconnecting...")
        }
    }

    /**
     * Join chat room as visitor with offline queue support
     * This is the primary method to join a chat - no separate mobile-init needed
     */
    fun joinChatRoom(
        chatSessionId: String,
        deviceInfo: Map<String, String>? = null
    ) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
            put("platform", Constants.PLATFORM_IDENTIFIER)
            deviceInfo?.let {
                put("deviceInfo", JSONObject(it))
            }
        }

        if (socket?.connected() == true) {
            emit(SocketEvents.JOIN_CHAT_ROOM, data)
        } else if (offlineManager?.isEnabled() == true) {
            // Queue join request for when we reconnect
            val payload = mapOf(
                "chatSessionId" to chatSessionId,
                "platform" to Constants.PLATFORM_IDENTIFIER,
                "deviceInfo" to (deviceInfo ?: emptyMap<String, String>())
            )
            queueMessage(MessageType.JOIN_CHAT_ROOM, payload, chatSessionId)
        }
    }

    /**
     * Queue a message for later delivery
     */
    private fun queueMessage(type: MessageType, payload: Map<String, Any>, chatSessionId: String) {
        val message = QueuedMessage(
            type = type,
            payload = payload,
            chatSessionId = chatSessionId
        )
        offlineManager?.queueMessage(message)
    }

    /**
     * Leave chat room
     */
    fun leaveChatRoom(chatSessionId: String) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
        }
        emit(SocketEvents.LEAVE_CHAT_ROOM, data)
    }

    /**
     * Send visitor response/message with offline queue support
     * Uses 'response-record' event matching embed-server
     */
    fun sendResponseRecord(
        chatSessionId: String,
        record: List<Map<String, Any>>,
        answerVariables: List<Any> = emptyList(),
        visitorMeta: Map<String, Any>? = null
    ) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
            put("record", org.json.JSONArray(record.map { JSONObject(it) }))
            put("answerVariables", org.json.JSONArray(answerVariables))
            put("botId", botId)
            visitorMeta?.let { put("visitorMeta", JSONObject(it)) }
        }

        if (socket?.connected() == true) {
            emit(SocketEvents.RESPONSE_RECORD, data)
        } else if (offlineManager?.isEnabled() == true) {
            // Queue the message for later delivery
            val payload = mapOf(
                "chatSessionId" to chatSessionId,
                "record" to record,
                "answerVariables" to answerVariables,
                "botId" to botId,
                "visitorMeta" to (visitorMeta ?: emptyMap<String, Any>())
            )
            queueMessage(MessageType.RESPONSE_RECORD, payload, chatSessionId)
            Log.d(TAG, "Message queued for offline delivery")
        } else {
            Log.w(TAG, "Cannot send response - not connected and offline mode disabled")
        }
    }

    /**
     * Send visitor response/message with pre-built data map
     * Alternative method used by NodeFlowEngine
     */
    @Suppress("UNCHECKED_CAST")
    fun sendResponseRecord(data: Map<String, Any?>) {
        val jsonData = JSONObject().apply {
            data.forEach { (key, value) ->
                when (value) {
                    is List<*> -> {
                        val jsonArray = org.json.JSONArray()
                        value.forEach { item ->
                            when (item) {
                                is Map<*, *> -> jsonArray.put(JSONObject(item as Map<String, Any?>))
                                else -> jsonArray.put(item)
                            }
                        }
                        put(key, jsonArray)
                    }
                    is Map<*, *> -> put(key, JSONObject(value as Map<String, Any?>))
                    null -> { /* skip null values */ }
                    else -> put(key, value)
                }
            }
            // Ensure botId is always included
            if (!has("botId")) {
                put("botId", botId)
            }
        }
        emit(SocketEvents.RESPONSE_RECORD, jsonData)
    }

    /**
     * Send visitor typing status
     */
    fun sendTypingStatus(chatSessionId: String, isTyping: Boolean) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
            put("isTyping", isTyping)
        }
        emit(SocketEvents.VISITOR_TYPING, data)
    }

    /**
     * Initiate handover to live agent with offline queue support
     */
    fun initiateHandover(chatSessionId: String, message: String? = null) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
            message?.let { put("message", it) }
        }

        if (socket?.connected() == true) {
            emit(SocketEvents.INITIATE_HANDOVER, data)
        } else if (offlineManager?.isEnabled() == true) {
            val payload = mutableMapOf<String, Any>(
                "chatSessionId" to chatSessionId
            )
            message?.let { payload["message"] = it }
            queueMessage(MessageType.INITIATE_HANDOVER, payload, chatSessionId)
        }
    }

    /**
     * End chat with offline queue support
     */
    fun endChat(chatSessionId: String) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
        }

        if (socket?.connected() == true) {
            emit(SocketEvents.END_CHAT, data)
        } else if (offlineManager?.isEnabled() == true) {
            val payload = mapOf("chatSessionId" to chatSessionId)
            queueMessage(MessageType.END_CHAT, payload, chatSessionId)
        }
    }

    /**
     * Send post-chat survey response
     * Called after user completes the survey in human handover flow
     *
     * @param chatSessionId Current chat session ID
     * @param surveyResponses Map of question IDs to their answers
     */
    fun sendPostChatSurveyResponse(
        chatSessionId: String,
        surveyResponses: Map<String, Any>
    ) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
            put("botId", botId)
            put("surveyResponses", JSONObject(surveyResponses))
            put("submittedAt", System.currentTimeMillis())
        }
        emit(SocketEvents.POST_CHAT_SURVEY_RESPONSE, data)
        Log.d(TAG, "Sent post-chat survey response with ${surveyResponses.size} answers")
    }

    /**
     * Emit event
     */
    fun emit(event: String, data: Any) {
        if (socket?.connected() != true) {
            Log.w(TAG, "Cannot emit - not connected")
            return
        }
        socket?.emit(event, data)
    }

    /**
     * Listen to event
     */
    fun on(event: String, callback: Emitter.Listener) {
        socket?.on(event, callback)
    }

    /**
     * Remove event listener
     */
    fun off(event: String, callback: Emitter.Listener? = null) {
        if (callback != null) {
            socket?.off(event, callback)
        } else {
            socket?.off(event)
        }
    }

    /**
     * Emit analytics event
     * Generic method for sending analytics data to server
     */
    fun emitAnalyticsEvent(event: String, data: Map<String, Any?>) {
        if (socket?.connected() != true) {
            Log.w(TAG, "Cannot emit analytics - not connected")
            return
        }
        val jsonData = JSONObject(data.filterValues { it != null })
        socket?.emit(event, jsonData)
    }

    /**
     * Execute a native integration via socket
     * Used for Stripe, Google integrations, etc. that require server-side processing
     *
     * @param nodeType The type of integration node (e.g., "stripe-node")
     * @param nodeId The unique identifier of the node
     * @param nodeData The node's configuration data
     * @param chatSessionId Current chat session ID
     * @param chatbotId The chatbot ID
     * @param workspaceId The workspace ID (optional)
     * @param answerVariables Current answer variables for variable resolution
     * @param callback Callback invoked with the integration result
     * @param timeoutMs Timeout in milliseconds (default 30 seconds)
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
        if (socket?.connected() != true) {
            Log.w(TAG, "Cannot execute integration - not connected")
            callback(IntegrationResult(
                success = false,
                error = "Socket not connected",
                nodeId = nodeId
            ))
            return
        }

        // Build the request data
        val data = JSONObject().apply {
            put("nodeType", nodeType)
            put("nodeId", nodeId)
            put("nodeData", JSONObject(nodeData.filterValues { it != null }))
            put("chatSessionId", chatSessionId)
            put("chatbotId", chatbotId)
            workspaceId?.let { put("workspaceId", it) }
            put("answerVariables", JSONObject(answerVariables.filterValues { it != null }))
            put("visitorData", JSONObject())
        }

        // Track if callback has been invoked to prevent double-invocation
        var callbackInvoked = false

        // Create one-time listener for the result
        val resultListener = Emitter.Listener { args ->
            try {
                val result = args.firstOrNull() as? JSONObject ?: return@Listener
                val resultNodeId = result.optString("nodeId")

                // Only handle result for this specific node
                if (resultNodeId == nodeId && !callbackInvoked) {
                    callbackInvoked = true

                    val integrationResult = IntegrationResult(
                        success = result.optBoolean("success", false),
                        error = result.optString("error", null),
                        nodeId = resultNodeId,
                        operation = result.optString("operation", null),
                        data = result.optJSONObject("data")?.let { jsonToMap(it) },
                        message = result.optString("message", null),
                        answerVariable = result.optString("answerVariable", null),
                        answerValue = result.opt("answerValue")
                    )

                    // Remove listener to prevent memory leaks
                    socket?.off(SocketEvents.INTEGRATION_RESULT, resultListener)

                    callback(integrationResult)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing integration result", e)
            }
        }

        // Listen for result
        socket?.on(SocketEvents.INTEGRATION_RESULT, resultListener)

        // Emit the request
        socket?.emit(SocketEvents.EXECUTE_INTEGRATION, data)
        Log.d(TAG, "Emitted execute-integration for $nodeType ($nodeId)")

        // Setup timeout
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!callbackInvoked) {
                callbackInvoked = true
                socket?.off(SocketEvents.INTEGRATION_RESULT, resultListener)
                callback(IntegrationResult(
                    success = false,
                    error = "Integration execution timed out",
                    nodeId = nodeId
                ))
            }
        }, timeoutMs)
    }

    /**
     * Convert JSONObject to Map
     */
    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            val value = json.get(key)
            map[key] = when (value) {
                is JSONObject -> jsonToMap(value)
                is org.json.JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    /**
     * Convert JSONArray to List
     */
    private fun jsonArrayToList(array: org.json.JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until array.length()) {
            val value = array.get(i)
            list.add(when (value) {
                is JSONObject -> jsonToMap(value)
                is org.json.JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            })
        }
        return list
    }

    /**
     * Send a queued message (called by QueueProcessor via OfflineManager)
     */
    private suspend fun sendQueuedMessage(message: QueuedMessage): Boolean {
        if (socket?.connected() != true) {
            Log.d(TAG, "Cannot send queued message - not connected")
            return false
        }

        return try {
            when (message.type) {
                MessageType.RESPONSE_RECORD -> {
                    val chatSessionId = message.payload["chatSessionId"] as? String ?: return false
                    @Suppress("UNCHECKED_CAST")
                    val record = message.payload["record"] as? List<Map<String, Any>> ?: return false
                    @Suppress("UNCHECKED_CAST")
                    val answerVariables = message.payload["answerVariables"] as? List<Any> ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val visitorMeta = message.payload["visitorMeta"] as? Map<String, Any>

                    val data = JSONObject().apply {
                        put("chatSessionId", chatSessionId)
                        put("record", org.json.JSONArray(record.map { JSONObject(it) }))
                        put("answerVariables", org.json.JSONArray(answerVariables))
                        put("botId", botId)
                        visitorMeta?.let { put("visitorMeta", JSONObject(it)) }
                    }
                    socket?.emit(SocketEvents.RESPONSE_RECORD, data)
                    true
                }

                MessageType.JOIN_CHAT_ROOM -> {
                    val chatSessionId = message.payload["chatSessionId"] as? String ?: return false
                    @Suppress("UNCHECKED_CAST")
                    val deviceInfo = message.payload["deviceInfo"] as? Map<String, String>

                    val data = JSONObject().apply {
                        put("chatSessionId", chatSessionId)
                        put("platform", Constants.PLATFORM_IDENTIFIER)
                        deviceInfo?.let { put("deviceInfo", JSONObject(it)) }
                    }
                    socket?.emit(SocketEvents.JOIN_CHAT_ROOM, data)
                    true
                }

                MessageType.INITIATE_HANDOVER -> {
                    val chatSessionId = message.payload["chatSessionId"] as? String ?: return false
                    val handoverMessage = message.payload["message"] as? String

                    val data = JSONObject().apply {
                        put("chatSessionId", chatSessionId)
                        handoverMessage?.let { put("message", it) }
                    }
                    socket?.emit(SocketEvents.INITIATE_HANDOVER, data)
                    true
                }

                MessageType.END_CHAT -> {
                    val chatSessionId = message.payload["chatSessionId"] as? String ?: return false

                    val data = JSONObject().apply {
                        put("chatSessionId", chatSessionId)
                    }
                    socket?.emit(SocketEvents.END_CHAT, data)
                    true
                }

                MessageType.CUSTOM_EVENT -> {
                    val event = message.payload["event"] as? String ?: return false
                    val eventData = message.payload["data"] ?: JSONObject()
                    socket?.emit(event, eventData)
                    true
                }

                else -> {
                    Log.w(TAG, "Unhandled message type: ${message.type}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending queued message", e)
            false
        }
    }

    /**
     * Emit event with offline queue support for custom events
     */
    fun emitWithQueue(event: String, data: Map<String, Any>, chatSessionId: String) {
        if (socket?.connected() == true) {
            socket?.emit(event, JSONObject(data))
        } else if (offlineManager?.isEnabled() == true) {
            val payload = mapOf(
                "event" to event,
                "data" to data
            )
            queueMessage(MessageType.CUSTOM_EVENT, payload, chatSessionId)
        }
    }

    /**
     * Disconnect from socket
     */
    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        _isConnected = false
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Dispose of resources
     */
    fun dispose() {
        disconnect()
        offlineManager = null
        reconnectionListener = null
    }

    /**
     * Check if offline queue has pending messages
     */
    fun hasPendingMessages(): Boolean = offlineManager?.hasPendingMessages() == true

    /**
     * Get pending message count
     */
    fun getPendingMessageCount(): Int = offlineManager?.getPendingCount() ?: 0

    companion object {
        private const val TAG = "ConferBot-Socket"
    }
}
