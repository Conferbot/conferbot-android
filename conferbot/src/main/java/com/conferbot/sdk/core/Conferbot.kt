package com.conferbot.sdk.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.conferbot.sdk.models.*
import com.conferbot.sdk.services.ApiClient
import com.conferbot.sdk.services.SocketClient
import com.conferbot.sdk.ui.views.ChatActivity
import com.conferbot.sdk.utils.Constants
import com.google.gson.Gson
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Main Conferbot SDK singleton
 */
object Conferbot {
    private const val TAG = "ConferBot"

    // Configuration
    private var apiKey: String? = null
    private var botId: String? = null
    private var config: ConferBotConfig = ConferBotConfig()
    private var customization: ConferBotCustomization? = null
    private var user: ConferBotUser? = null
    private var baseUrl: String? = null
    private var socketUrl: String? = null

    // Services
    private var apiClient: ApiClient? = null
    private var socketClient: SocketClient? = null

    // State
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _chatSessionId = MutableStateFlow<String?>(null)
    val chatSessionId: StateFlow<String?> = _chatSessionId.asStateFlow()

    private val _record = MutableStateFlow<List<RecordItem>>(emptyList())
    val record: StateFlow<List<RecordItem>> = _record.asStateFlow()

    private val _currentAgent = MutableStateFlow<Agent?>(null)
    val currentAgent: StateFlow<Agent?> = _currentAgent.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isAgentTyping = MutableStateFlow(false)
    val isAgentTyping: StateFlow<Boolean> = _isAgentTyping.asStateFlow()

    // Event listener
    private var eventListener: ConferBotEventListener? = null

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Gson for JSON parsing
    private val gson = Gson()

    /**
     * Initialize the SDK
     */
    fun initialize(
        context: Context,
        apiKey: String,
        botId: String,
        config: ConferBotConfig = ConferBotConfig(),
        customization: ConferBotCustomization? = null,
        user: ConferBotUser? = null,
        baseUrl: String? = null,
        socketUrl: String? = null
    ) {
        if (_isInitialized.value) {
            Log.w(TAG, "SDK already initialized")
            return
        }

        this.apiKey = apiKey
        this.botId = botId
        this.config = config
        this.customization = customization
        this.user = user
        this.baseUrl = baseUrl
        this.socketUrl = socketUrl

        // Initialize API client
        apiClient = ApiClient(
            apiKey = apiKey,
            botId = botId,
            baseUrl = baseUrl ?: Constants.DEFAULT_API_BASE_URL
        )

        // Initialize Socket client
        socketClient = SocketClient(
            apiKey = apiKey,
            botId = botId,
            socketUrl = socketUrl ?: Constants.DEFAULT_SOCKET_URL
        )

        if (config.autoConnect) {
            connectSocket()
        }

        _isInitialized.value = true
        Log.d(TAG, "SDK initialized")
    }

    /**
     * Connect to socket server
     */
    private fun connectSocket() {
        socketClient?.connect()
        setupSocketListeners()
    }

    /**
     * Setup socket event listeners
     */
    private fun setupSocketListeners() {
        // Connection events
        socketClient?.on(SocketEvents.CONNECT, Emitter.Listener {
            _isConnected.value = true
            Log.d(TAG, "Socket connected")
        })

        socketClient?.on(SocketEvents.DISCONNECT, Emitter.Listener {
            _isConnected.value = false
            Log.d(TAG, "Socket disconnected")
        })

        // Bot response
        socketClient?.on(SocketEvents.BOT_RESPONSE, Emitter.Listener { args ->
            handleMessageReceived(args)
        })

        // Agent message
        socketClient?.on(SocketEvents.AGENT_MESSAGE, Emitter.Listener { args ->
            handleMessageReceived(args)
        })

        // Agent accepted (embed-server sends agentDetails, not agent)
        socketClient?.on(SocketEvents.AGENT_ACCEPTED, Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject
            data?.optJSONObject("agentDetails")?.let { agentJson ->
                // Map agentDetails to Agent
                val agent = Agent(
                    id = agentJson.optString("_id"),
                    name = agentJson.optString("name"),
                    email = agentJson.optString("email"),
                    avatar = agentJson.optString("avatar", null),
                    title = agentJson.optString("title", null)
                )
                _currentAgent.value = agent
                eventListener?.onAgentJoined(agent)
            }
        })

        // Agent left
        socketClient?.on(SocketEvents.AGENT_LEFT, Emitter.Listener {
            val agent = _currentAgent.value
            _currentAgent.value = null
            agent?.let { eventListener?.onAgentLeft(it) }
        })

        // Agent typing
        socketClient?.on(SocketEvents.AGENT_TYPING_STATUS, Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject
            val isTyping = data?.optBoolean("isTyping", false) ?: false
            _isAgentTyping.value = isTyping
            eventListener?.onTypingIndicator(isTyping)
        })

        // Chat ended
        socketClient?.on(SocketEvents.CHAT_ENDED, Emitter.Listener {
            _currentAgent.value = null
            eventListener?.onSessionEnded(_chatSessionId.value ?: "")
        })
    }

    /**
     * Handle message received from socket
     */
    private fun handleMessageReceived(args: Array<Any>) {
        val data = args.firstOrNull() as? JSONObject ?: return
        try {
            val recordItem = gson.fromJson(data.toString(), RecordItem::class.java)
            val currentRecord = _record.value.toMutableList()
            currentRecord.add(recordItem)
            _record.value = currentRecord

            // Increment unread count if chat is not open
            // Note: You would need to track if chat is open via ChatActivity state
            _unreadCount.value = _unreadCount.value + 1

            // Notify listener based on message type
            when (recordItem) {
                is RecordItem.BotMessage -> {
                    // Bot message received
                }
                is RecordItem.AgentMessage -> {
                    eventListener?.onMessageReceived(recordItem)
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message", e)
        }
    }

    /**
     * Open chat UI
     */
    fun openChat(context: Context) {
        if (!_isInitialized.value) {
            Log.e(TAG, "SDK not initialized. Call initialize() first.")
            return
        }

        scope.launch {
            // Initialize session if needed
            if (_chatSessionId.value == null) {
                initializeSession()
            }

            // Launch ChatActivity
            val intent = Intent(context, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            // Clear unread count
            _unreadCount.value = 0
        }
    }

    /**
     * Initialize chat session
     */
    suspend fun initializeSession(): Boolean {
        val client = apiClient ?: return false

        return try {
            val response = client.initSession(userId = user?.id)
            if (response.success && response.data != null) {
                val session = response.data
                _chatSessionId.value = session.chatSessionId
                _record.value = session.record

                // Join chat room via socket with device info
                // This is the only call needed - no separate mobileInit
                socketClient?.joinChatRoom(
                    chatSessionId = session.chatSessionId,
                    deviceInfo = mapOf(
                        "os" to "Android",
                        "osVersion" to Build.VERSION.RELEASE,
                        "sdkVersion" to Build.VERSION.SDK_INT.toString(),
                        "deviceModel" to Build.MODEL
                    )
                )

                eventListener?.onSessionStarted(session.chatSessionId)
                true
            } else {
                Log.e(TAG, "Failed to initialize session: ${response.error}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize session", e)
            false
        }
    }

    /**
     * Send a message
     */
    fun sendMessage(text: String) {
        val sessionId = _chatSessionId.value
        if (sessionId == null || text.trim().isEmpty()) {
            Log.w(TAG, "Cannot send message: no session or empty text")
            return
        }

        // Create user message (use user-input-response type for chatbot flow)
        val userMessage = RecordItem.UserInputResponse(
            id = System.currentTimeMillis().toString(),
            time = java.util.Date(),
            text = text
        )

        // Add to record optimistically
        val currentRecord = _record.value.toMutableList()
        currentRecord.add(userMessage)
        _record.value = currentRecord

        // Send entire record via socket (embed-server expects full record)
        socketClient?.sendResponseRecord(
            chatSessionId = sessionId,
            record = currentRecord.map { item ->
                mapOf(
                    "_id" to item.id,
                    "type" to item.type.value,
                    "time" to item.time.toString(),
                    "text" to when (item) {
                        is RecordItem.UserMessage -> item.text
                        is RecordItem.UserInputResponse -> item.text
                        is RecordItem.BotMessage -> item.text ?: ""
                        is RecordItem.AgentMessage -> item.text
                        is RecordItem.SystemMessage -> item.text
                        else -> ""
                    }
                )
            },
            answerVariables = emptyList()
        )

        eventListener?.onMessageSent(userMessage)
    }

    /**
     * Identify current user
     */
    fun identify(user: ConferBotUser) {
        this.user = user
    }

    /**
     * Register push notification token
     */
    fun registerPushToken(token: String) {
        val sessionId = _chatSessionId.value ?: return
        scope.launch {
            apiClient?.registerPushToken(token, sessionId)
        }
    }

    /**
     * Handle push notification
     */
    fun handlePushNotification(data: Map<String, String>): Boolean {
        // Check if notification is from Conferbot
        return data["type"] == "conferbot_message"
    }

    /**
     * Send typing status
     */
    fun sendTypingStatus(isTyping: Boolean) {
        val sessionId = _chatSessionId.value ?: return
        socketClient?.sendTypingStatus(sessionId, isTyping)
    }

    /**
     * Initiate handover to live agent
     */
    fun initiateHandover(message: String? = null) {
        val sessionId = _chatSessionId.value ?: return
        socketClient?.initiateHandover(sessionId, message)
    }

    /**
     * End chat
     */
    fun endChat() {
        val sessionId = _chatSessionId.value ?: return
        socketClient?.endChat(sessionId)
    }

    /**
     * Clear chat history
     */
    fun clearHistory() {
        _record.value = emptyList()
        _chatSessionId.value = null
        _currentAgent.value = null
        _unreadCount.value = 0
    }

    /**
     * Set event listener
     */
    fun setEventListener(listener: ConferBotEventListener) {
        this.eventListener = listener
    }

    /**
     * Disconnect socket
     */
    fun disconnect() {
        val sessionId = _chatSessionId.value
        if (sessionId != null) {
            socketClient?.leaveChatRoom(sessionId)
        }
        socketClient?.disconnect()
        _isConnected.value = false
    }

    /**
     * Get customization
     */
    fun getCustomization(): ConferBotCustomization? = customization

    /**
     * Listen to custom socket event
     */
    fun on(event: String, callback: Emitter.Listener) {
        socketClient?.on(event, callback)
    }

    /**
     * Remove socket event listener
     */
    fun off(event: String, callback: Emitter.Listener? = null) {
        socketClient?.off(event, callback)
    }
}

/**
 * Event listener interface for Conferbot events
 */
interface ConferBotEventListener {
    fun onMessageReceived(message: RecordItem) {}
    fun onMessageSent(message: RecordItem) {}
    fun onAgentJoined(agent: Agent) {}
    fun onAgentLeft(agent: Agent) {}
    fun onSessionStarted(sessionId: String) {}
    fun onSessionEnded(sessionId: String) {}
    fun onTypingIndicator(isTyping: Boolean) {}
    fun onUnreadCountChanged(count: Int) {}
}
