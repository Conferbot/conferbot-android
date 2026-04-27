package com.conferbot.sdk.core

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.conferbot.sdk.core.analytics.ChatAnalytics
import com.conferbot.sdk.core.nodes.NodeHandlerRegistry
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.queue.OfflineManager
import com.conferbot.sdk.core.queue.OfflineManagerListener
import com.conferbot.sdk.core.queue.QueuedMessage
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.core.state.MemoryUsageInfo
import com.conferbot.sdk.core.state.PaginatedMessageManager
import com.conferbot.sdk.core.state.PaginationConfig
import com.conferbot.sdk.core.state.PaginationState
import com.conferbot.sdk.models.*
import com.conferbot.sdk.notifications.ConferbotNotification
import com.conferbot.sdk.notifications.ConferbotNotificationManager
import com.conferbot.sdk.notifications.NotificationHandler
import com.conferbot.sdk.notifications.NotificationListener
import com.conferbot.sdk.notifications.NotificationSettings
import com.conferbot.sdk.notifications.NotificationTokenListener
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.services.ApiClient
import com.conferbot.sdk.services.FileUploadManager
import com.conferbot.sdk.services.FileUploadService
import com.conferbot.sdk.services.KnowledgeBaseService
import com.conferbot.sdk.services.SocketClient
import com.conferbot.sdk.ui.theme.ConferbotBackground
import com.conferbot.sdk.ui.theme.ConferbotTheme
import com.conferbot.sdk.ui.theme.ConferbotThemeBuilder
import com.conferbot.sdk.ui.views.ChatActivity
import com.conferbot.sdk.utils.ConferBotEndpoints
import com.conferbot.sdk.utils.Constants
import com.google.gson.Gson
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Parsed server-side chatbot customization data.
 *
 * Populated from the `chatbotData.customizations` object received in the
 * `fetched-chatbot-data` socket event.
 */
data class ServerChatbotCustomization(
    val avatarUrl: String? = null,
    val avatarSize: Int? = null,
    val hideAvatar: Boolean = false,
    val logoUrl: String? = null,
    val logoText: String? = null,
    val botName: String? = null,
    val font: String? = null,
    val fontSize: Int? = null,
    val bubbleBorderRadius: Int? = null,
    val chatBgType: String? = null,
    val hideBrand: Boolean = false,
    val enableCustomBrand: Boolean = false,
    val customBrand: String? = null,
    val enableTagline: Boolean = false,
    val tagline: String? = null,
    val gradientBgOne: String? = null,
    val gradientBgTwo: String? = null,
    val chatBgImg: String? = null,
    // Widget FAB customizations
    val widgetIconBgColor: String? = null,
    val headerBgColor: String? = null,       // fallback for FAB color
    val widgetBorderRadius: Int? = null,
    val widgetSize: Int? = null,
    val widgetPosition: String? = null,       // "left" or "right"
    val widgetOffsetRight: Int? = null,
    val widgetOffsetLeft: Int? = null,
    val widgetOffsetBottom: Int? = null,
    val widgetIconSVG: String? = null,        // e.g. "WidgetBubbleIcon10"
    val widgetIconType: String? = null,       // "Icon" or "Image"
    val widgetIconImage: String? = null,      // URL when widgetIconType = "Image"
    val chatIconCtaText: String? = null,
    val widgetIconThemeType: String? = null,  // "Solid" or "Gradient"
)

/**
 * Main Conferbot SDK singleton
 *
 * Enhanced with:
 * - Message pagination support to prevent OOM crashes
 * - Memory management for background/low memory scenarios
 * - Efficient message loading with lazy pagination
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

    // Application context for memory management
    private var appContext: Context? = null

    // Services
    private var apiClient: ApiClient? = null
    private var socketClient: SocketClient? = null

    // Paginated message manager
    private var messageManager: PaginatedMessageManager? = null

    // Knowledge Base service
    private var _knowledgeBaseService: KnowledgeBaseService? = null

    // File Upload service
    private var _fileUploadService: FileUploadService? = null
    private var _fileUploadManager: FileUploadManager? = null

    /**
     * Access to file upload manager for UI components
     */
    val fileUploadManager: FileUploadManager?
        get() = _fileUploadManager

    /**
     * Access to file upload service for direct uploads
     */
    val fileUploadService: FileUploadService?
        get() = _fileUploadService

    // Offline Manager for queue support
    private var _offlineManager: OfflineManager? = null

    // Notification components
    private var _notificationManager: ConferbotNotificationManager? = null
    private var _notificationHandler: NotificationHandler? = null

    // Current push token
    private var currentPushToken: String? = null

    // Pagination configuration
    private var paginationConfig = PaginationConfig()

    // Node flow engine for processing chatbot nodes
    private var _flowEngine: NodeFlowEngine? = null

    // Cached chatbot data (nodes/edges) from fetched-chatbot-data event
    private var cachedChatbotNodes: List<Map<String, Any>>? = null
    private var cachedChatbotEdges: List<Map<String, Any>>? = null
    private var cachedWorkspaceId: String? = null
    private var flowStarted = false

    /**
     * Access to the flow engine for UI components
     * Allows direct observation of node states and submission of responses
     */
    val flowEngine: NodeFlowEngine?
        get() = _flowEngine

    // Keep backwards compatible alias
    private val nodeFlowEngine: NodeFlowEngine?
        get() = _flowEngine

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

    private val _isChatVisible = MutableStateFlow(false)
    /**
     * Whether the chat overlay (widget) is currently visible on screen.
     * Updated by [ConferBotWidget] when the overlay opens or closes.
     */
    val isChatVisible: StateFlow<Boolean> = _isChatVisible.asStateFlow()

    private val _isAgentTyping = MutableStateFlow(false)
    val isAgentTyping: StateFlow<Boolean> = _isAgentTyping.asStateFlow()

    private val _isLiveChatMode = MutableStateFlow(false)
    val isLiveChatMode: StateFlow<Boolean> = _isLiveChatMode.asStateFlow()

    // ========== Server Theme & Customization State ==========

    private val _serverTheme = MutableStateFlow<ConferbotTheme?>(null)
    /**
     * Theme built from server-side customization data.
     * Null until the `fetched-chatbot-data` event is received and parsed.
     * Collect this in Compose UI and wrap content with [ConferbotThemeProvider]
     * to apply server colors automatically.
     */
    val serverTheme: StateFlow<ConferbotTheme?> = _serverTheme.asStateFlow()

    private val _serverCustomization = MutableStateFlow<ServerChatbotCustomization?>(null)
    /**
     * Parsed server customization metadata (avatar URL, logo, bot name, etc.)
     * that doesn't map directly to theme colors but is useful for the chat header.
     */
    val serverCustomization: StateFlow<ServerChatbotCustomization?> = _serverCustomization.asStateFlow()

    // ========== Offline Mode State ==========

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _pendingMessageCount = MutableStateFlow(0)
    val pendingMessageCount: StateFlow<Int> = _pendingMessageCount.asStateFlow()

    private val _isSyncingQueue = MutableStateFlow(false)
    val isSyncingQueue: StateFlow<Boolean> = _isSyncingQueue.asStateFlow()

    // ========== Pagination State ==========

    private val _paginationState = MutableStateFlow(PaginationState())
    val paginationState: StateFlow<PaginationState> = _paginationState.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // Node flow engine state flows (exposed for UI consumption)
    val currentUIState: StateFlow<NodeUIState?>
        get() = nodeFlowEngine?.currentUIState ?: MutableStateFlow(null)

    val isProcessingNode: StateFlow<Boolean>
        get() = nodeFlowEngine?.isProcessing ?: MutableStateFlow(false)

    val nodeErrorMessage: StateFlow<String?>
        get() = nodeFlowEngine?.errorMessage ?: MutableStateFlow(null)

    val isFlowComplete: StateFlow<Boolean>
        get() = nodeFlowEngine?.isFlowComplete ?: MutableStateFlow(false)

    // Event listener
    private var eventListener: ConferBotEventListener? = null

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Gson for JSON parsing
    private val gson = Gson()

    /**
     * Get or create a persistent visitor ID that survives across sessions and app restarts.
     * The visitor ID is stored in SharedPreferences and is only cleared on app uninstall.
     */
    private fun getOrCreateVisitorId(context: Context): String {
        val prefs = context.getSharedPreferences("conferbot_prefs", Context.MODE_PRIVATE)
        val key = "conferbot_visitor_id"
        val existing = prefs.getString(key, null)
        if (!existing.isNullOrEmpty()) {
            return existing
        }
        val newId = "v_${System.currentTimeMillis().toString(36)}_${java.util.UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString(key, newId).apply()
        return newId
    }

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
        socketUrl: String? = null,
        paginationConfig: PaginationConfig = PaginationConfig()
    ) {
        // FIX 4: Throw on double-initialization instead of silently returning
        if (_isInitialized.value) {
            throw IllegalStateException("Conferbot SDK is already initialized. Call disconnect() before re-initializing.")
        }

        // FIX 4: Validate apiKey and botId
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        require(botId.isNotBlank()) { "botId must not be blank" }
        require(apiKey.length >= 8) { "apiKey appears invalid (too short)" }

        // FIX 5: Wrap initialization in try-catch for cleanup on failure
        try {
            this.appContext = context.applicationContext
            this.apiKey = apiKey
            this.botId = botId
            this.config = config
            this.customization = customization
            this.user = user
            this.baseUrl = baseUrl
            this.socketUrl = socketUrl
            this.paginationConfig = paginationConfig

            // Initialize API client
            apiClient = ApiClient(
                apiKey = apiKey,
                botId = botId,
                baseUrl = baseUrl ?: ConferBotEndpoints.apiBaseUrl
            )

            // Initialize Socket client
            socketClient = SocketClient(
                apiKey = apiKey,
                botId = botId,
                socketUrl = socketUrl ?: ConferBotEndpoints.socketUrl
            )

            // Initialize Node Flow Engine
            socketClient?.let { client ->
                _flowEngine = NodeFlowEngine(client)
                // Initialize ChatAnalytics with socket client
                ChatAnalytics.setSocketClient(client)
            }

            // Initialize File Upload Service
            _fileUploadService = FileUploadService(
                apiKey = apiKey,
                botId = botId,
                context = context.applicationContext,
                baseUrl = baseUrl ?: ConferBotEndpoints.apiBaseUrl
            )

            // Initialize File Upload Manager
            _fileUploadService?.let { service ->
                _fileUploadManager = FileUploadManager(
                    context = context.applicationContext,
                    uploadService = service
                )
            }

            // Initialize Offline Manager if offline mode is enabled
            if (config.enableOfflineMode) {
                initializeOfflineManager(context.applicationContext)
            }

            // Initialize Notification components if enabled
            if (config.enableNotifications) {
                initializeNotifications(context.applicationContext)
            }

            // Configure ChatState pagination
            ChatState.configurePagination(
                maxMessages = paginationConfig.maxMemoryMessages,
                pageSizeConfig = paginationConfig.pageSize
            )

            if (config.autoConnect) {
                connectSocket()
            }

            _isInitialized.value = true
            Log.d(TAG, "SDK initialized with pagination support")
        } catch (e: Exception) {
            // FIX 5: Clean up partial state on initialization failure
            Log.e(TAG, "SDK initialization failed, cleaning up", e)
            _isInitialized.value = false
            socketClient?.disconnect()
            socketClient = null
            apiClient = null
            _flowEngine = null
            _fileUploadService = null
            _fileUploadManager = null
            _offlineManager?.shutdown()
            _offlineManager = null
            _notificationManager = null
            _notificationHandler = null
            appContext = null
            throw e
        }
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
            // Request chatbot data (nodes/edges) on connect, like the web widget does
            socketClient?.getChatbotData()
        })

        socketClient?.on(SocketEvents.DISCONNECT, Emitter.Listener {
            _isConnected.value = false
            Log.d(TAG, "Socket disconnected")
        })

        // Re-join chat room on reconnect (socket.io drops room membership on disconnect)
        socketClient?.setReconnectionListener {
            _isConnected.value = true
            val sessionId = _chatSessionId.value
            if (sessionId != null) {
                socketClient?.joinChatRoom(chatSessionId = sessionId, deviceInfo = "Android")
                Log.d(TAG, "Rejoined chat room on reconnect: $sessionId")
            }
            socketClient?.getChatbotData()
        }

        // Fetched chatbot data - contains nodes/edges for the flow graph
        socketClient?.on(SocketEvents.FETCHED_CHATBOT_DATA, Emitter.Listener { args ->
            handleFetchedChatbotData(args)
        })

        // Bot response - process through NodeFlowEngine for node-based messages
        socketClient?.on(SocketEvents.BOT_RESPONSE, Emitter.Listener { args ->
            handleBotResponse(args)
        })

        // Agent message - server sends {message, agentDetails, isFileInput, isAudioInput, agentMessageId}
        socketClient?.on(SocketEvents.AGENT_MESSAGE, Emitter.Listener { args ->
            handleAgentMessage(args)
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
                _isLiveChatMode.value = true
                ChatState.setLiveChatMode(true)

                // Add system message: "{agent name} has joined the chat"
                val agentDetails = AgentDetails(
                    id = agent.id,
                    name = agent.name,
                    email = agent.email,
                    avatar = agent.avatar
                )
                val joinedMessage = RecordItem.AgentJoinedMessage(
                    id = "agent-joined-${System.currentTimeMillis()}",
                    time = java.util.Date(),
                    agentDetails = agentDetails
                )
                addMessageToRecord(joinedMessage)

                // Add to ChatState record for server sync
                ChatState.pushToRecord(
                    com.conferbot.sdk.core.state.RecordEntry(
                        id = joinedMessage.id,
                        shape = "agent-joined-message",
                        type = "agent-joined-message",
                        text = "${agent.name} has joined the chat",
                        data = mutableMapOf("name" to agent.name)
                    )
                )

                // Notify flow engine about agent connection
                nodeFlowEngine?.handleAgentAccepted(agent.name)
                eventListener?.onAgentJoined(agent)
            }
        })

        // Agent left
        socketClient?.on(SocketEvents.AGENT_LEFT, Emitter.Listener { args ->
            val agent = _currentAgent.value
            _currentAgent.value = null

            // Add system message: "{agent name} has left the chat"
            val agentName = agent?.name ?: "Agent"
            val leftMessage = RecordItem.AgentLeftMessage(
                id = "agent-left-${System.currentTimeMillis()}",
                time = java.util.Date(),
                text = "$agentName has left the chat",
                agentDetails = agent?.let {
                    AgentDetails(id = it.id, name = it.name, email = it.email, avatar = it.avatar)
                }
            )
            addMessageToRecord(leftMessage)

            // Add to ChatState record for server sync
            ChatState.pushToRecord(
                com.conferbot.sdk.core.state.RecordEntry(
                    id = leftMessage.id,
                    shape = "agent-left-chat",
                    type = "agent-left-chat",
                    text = "$agentName has left the chat"
                )
            )

            agent?.let { eventListener?.onAgentLeft(it) }
        })

        // Agent typing - server sends {chatSessionId, isTyping, agentDetails}
        socketClient?.on(SocketEvents.AGENT_TYPING_STATUS, Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject
            val isTyping = data?.optBoolean("isTyping", false) ?: false
            // Only apply if chatSessionId matches (or not present)
            val eventSessionId = data?.optString("chatSessionId", null)
            if (eventSessionId != null && eventSessionId != _chatSessionId.value) {
                return@Listener
            }
            _isAgentTyping.value = isTyping
            ChatState.setAgentTyping(isTyping)
            eventListener?.onTypingIndicator(isTyping)
        })

        // Chat ended
        socketClient?.on(SocketEvents.CHAT_ENDED, Emitter.Listener {
            _currentAgent.value = null
            _isLiveChatMode.value = false
            _isAgentTyping.value = false
            ChatState.setLiveChatMode(false)
            ChatState.setAgentTyping(false)

            // Add "Chat has ended" system message
            val endedMessage = RecordItem.SystemMessage(
                id = "chat-ended-${System.currentTimeMillis()}",
                time = java.util.Date(),
                text = "Chat has ended"
            )
            addMessageToRecord(endedMessage)

            // Add to ChatState record for server sync
            ChatState.pushToRecord(
                com.conferbot.sdk.core.state.RecordEntry(
                    id = endedMessage.id,
                    shape = "system-message",
                    type = "system-message",
                    text = "Chat has ended"
                )
            )

            nodeFlowEngine?.handleChatEnded()
            eventListener?.onSessionEnded(_chatSessionId.value ?: "")
        })

        // No agents available (for human handover)
        socketClient?.on(SocketEvents.NO_AGENTS_AVAILABLE, Emitter.Listener {
            // Add "No agents available" system message
            val noAgentsMessage = RecordItem.SystemMessage(
                id = "no-agents-${System.currentTimeMillis()}",
                time = java.util.Date(),
                text = "No agents available"
            )
            addMessageToRecord(noAgentsMessage)

            nodeFlowEngine?.handleNoAgentsAvailable()
        })
    }

    /**
     * Handle bot response from socket - processes through NodeFlowEngine
     */
    private fun handleBotResponse(args: Array<Any>) {
        val data = args.firstOrNull() as? JSONObject ?: return
        try {
            // Convert JSONObject to Map for flow engine
            val messageMap = jsonObjectToMap(data)

            // Check if this is a node-based message (has nodeData or type field)
            val hasNodeData = messageMap.containsKey("nodeData") ||
                              (messageMap.containsKey("type") && NodeHandlerRegistry.hasHandler(messageMap["type"].toString()))

            if (hasNodeData && nodeFlowEngine != null) {
                // Process through flow engine for node-based UI
                nodeFlowEngine?.handleServerMessage(messageMap)
                return  // Don't also call handleMessageReceived to avoid duplicate messages
            }

            // Only add to record for non-node messages
            handleMessageReceived(args)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle bot response", e)
            // Fall back to standard message handling
            handleMessageReceived(args)
        }
    }

    /**
     * Handle message received from socket
     */
    private fun handleMessageReceived(args: Array<Any>) {
        val data = args.firstOrNull() as? JSONObject ?: return
        try {
            val recordItem = gson.fromJson(data.toString(), RecordItem::class.java)

            // Add to paginated message manager if available
            scope.launch {
                messageManager?.addMessage(recordItem)

                // Also update _record for backwards compatibility
                // Deduplication: skip if message with this ID already exists
                val existingIds = _record.value.map { it.id }.toSet()
                if (recordItem.id in existingIds) {
                    return@launch // Already exists, skip duplicate
                }

                val currentRecord = _record.value.toMutableList()
                currentRecord.add(recordItem)

                // Trim to prevent memory issues
                if (currentRecord.size > paginationConfig.maxMemoryMessages) {
                    _record.value = currentRecord.takeLast(paginationConfig.maxMemoryMessages)
                    _paginationState.value = _paginationState.value.copy(hasMoreMessages = true)
                } else {
                    _record.value = currentRecord
                }
            }

            // Increment unread count if chat is not open
            _unreadCount.value = _unreadCount.value + 1

            // Notify listener based on message type
            when (recordItem) {
                is RecordItem.BotMessage -> {
                    eventListener?.onMessageReceived(recordItem)
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
     * Handle agent message from socket
     * Server sends: {message, agentDetails, isFileInput, isAudioInput, agentMessageId}
     */
    private fun handleAgentMessage(args: Array<Any>) {
        val data = args.firstOrNull() as? JSONObject ?: return
        try {
            val rawMessage = data.optString("message", "")
            // Strip HTML tags from agent messages (admin sends via rich text editor with <p> tags)
            val message = rawMessage.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace("&amp;", "&").trim()
            val isFileInput = data.optBoolean("isFileInput", false)
            val isAudioInput = data.optBoolean("isAudioInput", false)
            val agentMessageId = data.optString("agentMessageId", "agent-msg-${System.currentTimeMillis()}")

            val agentDetailsJson = data.optJSONObject("agentDetails")
            val agentDetails = if (agentDetailsJson != null) {
                AgentDetails(
                    id = agentDetailsJson.optString("_id", ""),
                    name = agentDetailsJson.optString("name", "Agent"),
                    email = agentDetailsJson.optString("email", null),
                    avatar = agentDetailsJson.optString("avatar", null)
                )
            } else {
                val currentAgentVal = _currentAgent.value
                AgentDetails(
                    id = currentAgentVal?.id ?: "",
                    name = currentAgentVal?.name ?: "Agent"
                )
            }

            val recordItem: RecordItem = when {
                isFileInput -> RecordItem.AgentMessageFile(
                    id = agentMessageId,
                    time = java.util.Date(),
                    file = message,
                    agentDetails = agentDetails
                )
                isAudioInput -> RecordItem.AgentMessageAudio(
                    id = agentMessageId,
                    time = java.util.Date(),
                    url = message,
                    agentDetails = agentDetails
                )
                else -> RecordItem.AgentMessage(
                    id = agentMessageId,
                    time = java.util.Date(),
                    text = message,
                    agentDetails = agentDetails
                )
            }

            addMessageToRecord(recordItem)

            // Add to ChatState record for server sync
            ChatState.pushToRecord(
                com.conferbot.sdk.core.state.RecordEntry(
                    id = agentMessageId,
                    shape = "agent-message",
                    type = recordItem.type.value,
                    text = message,
                    data = mutableMapOf(
                        "agentDetails" to mapOf(
                            "_id" to agentDetails.id,
                            "name" to agentDetails.name,
                            "email" to agentDetails.email,
                            "avatar" to agentDetails.avatar
                        )
                    )
                )
            )

            // Add to transcript
            ChatState.addToTranscript("agent", message)

            // Hide typing indicator when message arrives
            _isAgentTyping.value = false
            ChatState.setAgentTyping(false)

            // Increment unread count if chat is not visible
            if (!_isChatVisible.value) {
                _unreadCount.value = _unreadCount.value + 1
            }

            eventListener?.onMessageReceived(recordItem)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle agent message", e)
        }
    }

    /**
     * Add a message to the record list and persist it
     */
    private fun addMessageToRecord(message: RecordItem) {
        scope.launch {
            messageManager?.addMessage(message)

            // Deduplication: skip if message with this ID already exists
            val existingIds = _record.value.map { it.id }.toSet()
            if (message.id in existingIds) {
                return@launch
            }

            val currentRecord = _record.value.toMutableList()
            currentRecord.add(message)

            if (currentRecord.size > paginationConfig.maxMemoryMessages) {
                _record.value = currentRecord.takeLast(paginationConfig.maxMemoryMessages)
            } else {
                _record.value = currentRecord
            }
        }
    }

    /**
     * Handle fetched-chatbot-data from socket
     * Parses the elements array to extract nodes and edges, then starts the flow
     * if the session is already initialized.
     *
     * Server sends: { chatbotData: { elements: [{ nodes: [...], edges: [...] }], ... }, knowledgeBaseData: {...} }
     */
    @Suppress("UNCHECKED_CAST")
    private fun handleFetchedChatbotData(args: Array<Any>) {
        val data = args.firstOrNull() as? JSONObject ?: return
        try {
            Log.d(TAG, "Received fetched-chatbot-data")

            // Server wraps chatbot data inside a 'chatbotData' key
            val chatbotData = data.optJSONObject("chatbotData") ?: data

            // Parse elements array: elements is an array with one object containing nodes and edges
            val elementsArray = chatbotData.optJSONArray("elements")
            if (elementsArray == null || elementsArray.length() == 0) {
                Log.w(TAG, "fetched-chatbot-data has no elements array")
                return
            }

            val firstElement = elementsArray.optJSONObject(0) ?: return

            // Extract nodes array
            val nodesArray = firstElement.optJSONArray("nodes")
            val nodes = mutableListOf<Map<String, Any>>()
            if (nodesArray != null) {
                for (i in 0 until nodesArray.length()) {
                    val nodeJson = nodesArray.optJSONObject(i) ?: continue
                    nodes.add(jsonObjectToMap(nodeJson) as Map<String, Any>)
                }
            }

            // Extract edges array
            val edgesArray = firstElement.optJSONArray("edges")
            val edges = mutableListOf<Map<String, Any>>()
            if (edgesArray != null) {
                for (i in 0 until edgesArray.length()) {
                    val edgeJson = edgesArray.optJSONObject(i) ?: continue
                    edges.add(jsonObjectToMap(edgeJson) as Map<String, Any>)
                }
            }

            Log.d(TAG, "Parsed chatbot data: ${nodes.size} nodes, ${edges.size} edges")

            if (nodes.isEmpty()) {
                Log.w(TAG, "No nodes found in chatbot data")
                return
            }

            // Cache the data
            cachedChatbotNodes = nodes
            cachedChatbotEdges = edges

            // Extract workspaceId from server chatbot data
            val serverWorkspaceId = chatbotData.optString("workspaceId", "")
            if (serverWorkspaceId.isNotBlank()) {
                cachedWorkspaceId = serverWorkspaceId
            }

            // Parse server customizations and build theme
            parseServerCustomizations(chatbotData.optJSONObject("customizations"))

            // Try to start the flow immediately if session is ready
            tryStartFlow()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse fetched-chatbot-data", e)
        }
    }

    /**
     * Parse the server customizations JSON object and build a [ConferbotTheme].
     *
     * The customizations object comes from `chatbotData.customizations` in the
     * `fetched-chatbot-data` socket event. Color values are hex strings (e.g. "#0100EC").
     * Malformed values are silently skipped so the default theme color is kept.
     */
    private fun parseServerCustomizations(customizations: JSONObject?) {
        if (customizations == null) {
            Log.d(TAG, "No customizations object in chatbot data")
            return
        }

        try {
            // Helper: optString returns "" for missing keys, normalize to null
            fun JSONObject.optStringOrNull(key: String): String? {
                val value = optString(key, "")
                return value.ifBlank { null }
            }

            // --- 1. Store non-color metadata ---
            val serverCustom = ServerChatbotCustomization(
                avatarUrl = customizations.optStringOrNull("avatar"),
                avatarSize = if (customizations.has("avatarSize")) customizations.optInt("avatarSize") else null,
                hideAvatar = customizations.optBoolean("hideAvatar", false),
                logoUrl = customizations.optStringOrNull("logo"),
                logoText = customizations.optStringOrNull("logoText"),
                botName = customizations.optStringOrNull("botName"),
                font = customizations.optStringOrNull("font"),
                fontSize = if (customizations.has("fontSize")) customizations.optInt("fontSize") else null,
                bubbleBorderRadius = if (customizations.has("bubbleBorderRadius")) customizations.optInt("bubbleBorderRadius") else null,
                chatBgType = customizations.optStringOrNull("chatBgType"),
                hideBrand = customizations.optBoolean("hideBrand", false),
                enableCustomBrand = customizations.optBoolean("enableCustomBrand", false),
                customBrand = customizations.optStringOrNull("customBrand"),
                enableTagline = customizations.optBoolean("enableTagline", false),
                tagline = customizations.optStringOrNull("tagline"),
                gradientBgOne = customizations.optStringOrNull("gradientBgOne"),
                gradientBgTwo = customizations.optStringOrNull("gradientBgTwo"),
                chatBgImg = customizations.optStringOrNull("chatBgImg"),
                widgetIconBgColor = customizations.optStringOrNull("widgetIconBgColor"),
                headerBgColor = customizations.optStringOrNull("headerBgColor"),
                widgetBorderRadius = if (customizations.has("widgetBorderRadius")) customizations.optInt("widgetBorderRadius") else null,
                widgetSize = if (customizations.has("widgetSize")) customizations.optInt("widgetSize") else null,
                widgetPosition = customizations.optStringOrNull("widgetPosition"),
                widgetOffsetRight = if (customizations.has("widgetOffsetRight")) customizations.optInt("widgetOffsetRight") else null,
                widgetOffsetLeft = if (customizations.has("widgetOffsetLeft")) customizations.optInt("widgetOffsetLeft") else null,
                widgetOffsetBottom = if (customizations.has("widgetOffsetBottom")) customizations.optInt("widgetOffsetBottom") else null,
                widgetIconSVG = customizations.optStringOrNull("widgetIconSVG"),
                widgetIconType = customizations.optStringOrNull("widgetIconType"),
                widgetIconImage = customizations.optStringOrNull("widgetIconImage"),
                chatIconCtaText = customizations.optStringOrNull("chatIconCtaText"),
                widgetIconThemeType = customizations.optStringOrNull("widgetIconThemeType")
            )
            _serverCustomization.value = serverCustom

            // --- 2. Build a theme from color values ---
            val builder = ConferbotThemeBuilder.create()
                .name("ServerCustomized")

            // Helper to safely parse a hex color string to Compose Color
            fun parseHexColor(hex: String?): androidx.compose.ui.graphics.Color? {
                if (hex.isNullOrBlank()) return null
                return try {
                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse color: $hex", e)
                    null
                }
            }

            // Header
            val headerBg = parseHexColor(customizations.optStringOrNull("headerBgColor"))
            val headerText = parseHexColor(customizations.optStringOrNull("headerTextColor"))
            if (headerBg != null || headerText != null) {
                builder.headerColors(
                    background = headerBg ?: com.conferbot.sdk.ui.theme.LightTheme.colors.headerBackground,
                    text = headerText ?: com.conferbot.sdk.ui.theme.LightTheme.colors.headerText
                )
            }
            // If headerBgColor is provided, also use it as the primary brand color
            if (headerBg != null) {
                builder.primaryColor(headerBg)
            }

            // Bot message bubble
            val botBubbleBg = parseHexColor(customizations.optStringOrNull("botMsgColor"))
            val botBubbleText = parseHexColor(customizations.optStringOrNull("botTextColor"))
            if (botBubbleBg != null || botBubbleText != null) {
                builder.botBubbleColors(
                    background = botBubbleBg ?: com.conferbot.sdk.ui.theme.LightTheme.colors.botBubble,
                    text = botBubbleText ?: com.conferbot.sdk.ui.theme.LightTheme.colors.botBubbleText
                )
            }

            // User message bubble
            val userBubbleBg = parseHexColor(customizations.optStringOrNull("userMsgColor"))
            val userBubbleText = parseHexColor(customizations.optStringOrNull("userTextColor"))
            if (userBubbleBg != null || userBubbleText != null) {
                builder.userBubbleColors(
                    background = userBubbleBg ?: com.conferbot.sdk.ui.theme.LightTheme.colors.userBubble,
                    text = userBubbleText ?: com.conferbot.sdk.ui.theme.LightTheme.colors.userBubbleText
                )
            }

            // Option bubble colors -> mapped to button colors
            val optionBubbleBg = parseHexColor(customizations.optStringOrNull("optionBubbleMsgColor"))
            val optionBubbleText = parseHexColor(customizations.optStringOrNull("optionBubbleTextColor"))
            // The builder doesn't have a direct setter for button colors individually,
            // but we can use primaryColor for button background (already done via headerBg).
            // For more granular control we note these are available in serverCustomization.

            // Chat background
            val chatBgColor = parseHexColor(customizations.optStringOrNull("chatBgColor"))
            val chatBgType = customizations.optStringOrNull("chatBgType") ?: "solid"
            val gradientColor1 = parseHexColor(serverCustom.gradientBgOne)
            val gradientColor2 = parseHexColor(serverCustom.gradientBgTwo)
            val chatBgImgUrl = serverCustom.chatBgImg

            when (chatBgType) {
                "gradient" -> {
                    if (gradientColor1 != null && gradientColor2 != null) {
                        builder.gradientBackground(gradientColor1, gradientColor2)
                    } else if (gradientColor1 != null && chatBgColor != null) {
                        builder.gradientBackground(gradientColor1, chatBgColor)
                    } else if (chatBgColor != null) {
                        val lighterVariant = chatBgColor.copy(alpha = 0.6f)
                        builder.gradientBackground(chatBgColor, lighterVariant)
                    }
                }
                "image" -> {
                    if (!chatBgImgUrl.isNullOrBlank()) {
                        builder.imageBackground(chatBgImgUrl)
                    } else if (chatBgColor != null) {
                        builder.background(chatBgColor)
                    }
                }
                else -> {
                    if (chatBgColor != null) {
                        builder.background(chatBgColor)
                    }
                }
            }

            // Bubble border radius
            if (serverCustom.bubbleBorderRadius != null && serverCustom.bubbleBorderRadius > 0) {
                builder.bubbleRadius(serverCustom.bubbleBorderRadius.dp)
            }

            // Font size -> message size
            if (serverCustom.fontSize != null && serverCustom.fontSize > 0) {
                builder.messageSize(serverCustom.fontSize.sp)
                builder.bodySize(serverCustom.fontSize.sp)
            }

            val theme = builder.build()
            _serverTheme.value = theme
            Log.d(TAG, "Built server theme from customizations")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse server customizations", e)
        }
    }

    /**
     * Attempt to start the chatbot flow.
     * Requires both: chatbot data (nodes/edges) cached AND a valid chat session.
     * Called from handleFetchedChatbotData (when data arrives) and
     * initializeSession (when session becomes ready).
     */
    private fun tryStartFlow() {
        if (flowStarted) return

        val nodes = cachedChatbotNodes ?: return
        val edges = cachedChatbotEdges ?: return
        val sessionId = _chatSessionId.value ?: return
        val currentBotId = botId ?: return

        Log.d(TAG, "Starting chatbot flow with ${nodes.size} nodes and ${edges.size} edges")
        flowStarted = true

        initializeFlowEngine(nodes, edges)

        // Observe flow engine UI state changes and add display-only messages to record
        // so they persist in the chat message list (both Compose and XML UIs)
        observeFlowEngineMessages()

        startFlow()
    }

    /**
     * Observe flow engine currentUIState and add message-type nodes to the record
     * so they appear persistently in the message list.
     */
    /** Strip HTML tags from text (e.g. "<p>Hello</p>" → "Hello") */
    private fun stripHtml(html: String): String {
        return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
    }

    private fun observeFlowEngineMessages() {
        scope.launch {
            nodeFlowEngine?.currentUIState?.collectLatest { uiState ->
                if (uiState == null) return@collectLatest

                val botMessage: RecordItem.BotMessage? = when (uiState) {
                    is NodeUIState.Message -> RecordItem.BotMessage(
                        id = "flow-${uiState.nodeId}-${System.currentTimeMillis()}",
                        time = java.util.Date(),
                        text = stripHtml(uiState.text)
                    )
                    is NodeUIState.Image -> RecordItem.BotMessage(
                        id = "flow-${uiState.nodeId}-${System.currentTimeMillis()}",
                        time = java.util.Date(),
                        text = uiState.caption ?: "[Image]"
                    )
                    is NodeUIState.Video -> RecordItem.BotMessage(
                        id = "flow-${uiState.nodeId}-${System.currentTimeMillis()}",
                        time = java.util.Date(),
                        text = uiState.caption ?: "[Video]"
                    )
                    // For interactive nodes that have a question text, add as bot message
                    is NodeUIState.TextInput -> RecordItem.BotMessage(
                        id = "flow-${uiState.nodeId}-${System.currentTimeMillis()}",
                        time = java.util.Date(),
                        text = stripHtml(uiState.questionText)
                    )
                    // Interactive nodes render their own question text inline via NodeRenderer
                    // Don't duplicate as a separate BotMessage
                    is NodeUIState.SingleChoice -> null
                    is NodeUIState.MultipleChoice -> null
                    is NodeUIState.Rating -> null
                    is NodeUIState.Dropdown -> null
                    else -> null
                }

                if (botMessage != null) {
                    val currentRecord = _record.value.toMutableList()
                    currentRecord.add(botMessage)

                    if (currentRecord.size > paginationConfig.maxMemoryMessages) {
                        _record.value = currentRecord.takeLast(paginationConfig.maxMemoryMessages)
                    } else {
                        _record.value = currentRecord
                    }

                    // Also persist to message manager
                    messageManager?.addMessage(botMessage)
                }
            }
        }
    }

    /**
     * Convert JSONObject to Map for flow engine processing
     */
    @Suppress("UNCHECKED_CAST")
    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key: String = keys.next() as String
            val value: Any? = json.opt(key)
            map[key] = when (value) {
                is JSONObject -> jsonObjectToMap(value)
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
    @Suppress("UNCHECKED_CAST")
    private fun jsonArrayToList(array: org.json.JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until array.length()) {
            val value = array.get(i)
            list.add(when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is org.json.JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            })
        }
        return list
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
        val context = appContext ?: return false

        return try {
            val response = client.initSession(userId = user?.id)
            if (response.success && response.data != null) {
                val session = response.data
                _chatSessionId.value = session.chatSessionId

                // Initialize paginated message manager
                messageManager = PaginatedMessageManager.getInstance(
                    context = context,
                    sessionId = session.chatSessionId,
                    config = paginationConfig
                )

                // Initialize and load messages
                val initialMessages = messageManager?.initialize() ?: emptyList()

                // Set initial record (may be from server or local storage)
                _record.value = if (session.record.isNotEmpty()) {
                    // Add server messages to manager
                    scope.launch {
                        messageManager?.addMessages(session.record)
                    }
                    session.record
                } else {
                    initialMessages
                }

                // Update pagination state
                updatePaginationState()

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

                // Resolve visitor ID: server > user > persistent device ID
                val resolvedVisitorId = session.visitorId ?: user?.id ?: getOrCreateVisitorId(context)

                // Initialize ChatState for flow engine with context
                ChatState.initializeWithContext(
                    context = context,
                    chatSessionId = session.chatSessionId,
                    visitorId = resolvedVisitorId,
                    botId = botId ?: "",
                    workspaceId = cachedWorkspaceId,
                    maxMessages = paginationConfig.maxMemoryMessages,
                    pageSizeConfig = paginationConfig.pageSize
                )

                // Initialize analytics tracking for this session
                ChatAnalytics.initializeChatAnalytics(
                    sessionId = session.chatSessionId,
                    botIdentifier = botId ?: "",
                    visitorIdentifier = resolvedVisitorId
                )

                try {
                    eventListener?.onSessionStarted(session.chatSessionId)
                } catch (e: Exception) {
                    Log.e(TAG, "Event listener onSessionStarted failed", e)
                }

                // Try to start the chatbot flow if chatbot data is already cached
                tryStartFlow()

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
     * Update pagination state from message manager
     */
    private fun updatePaginationState() {
        messageManager?.let { manager ->
            _paginationState.value = PaginationState(
                isLoading = manager.isLoading.value,
                hasMoreMessages = manager.hasMoreMessages.value,
                totalMessageCount = manager.totalMessageCount.value
            )
        }
    }

    /**
     * Load more (older) messages
     */
    fun loadMoreMessages() {
        if (_isLoadingMore.value) return

        scope.launch {
            _isLoadingMore.value = true
            try {
                val olderMessages = messageManager?.loadMoreMessages() ?: emptyList()

                if (olderMessages.isNotEmpty()) {
                    // Prepend to record
                    val currentRecord = _record.value.toMutableList()
                    currentRecord.addAll(0, olderMessages)
                    _record.value = currentRecord
                }

                updatePaginationState()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load more messages", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * Check if there are more messages to load
     */
    fun hasMoreMessages(): Boolean {
        return messageManager?.hasMoreMessages?.value ?: false
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

        // Track user message in analytics
        ChatAnalytics.trackUserMessage(
            messageLength = text.length,
            messageText = text
        )

        if (_isLiveChatMode.value) {
            sendLiveChatMessage(text, sessionId)
        } else {
            sendBotFlowMessage(text, sessionId)
        }
    }

    /**
     * Send a message during live chat (agent handover) mode
     */
    private fun sendLiveChatMessage(text: String, sessionId: String) {
        // Create user live message
        val userMessage = RecordItem.UserLiveMessage(
            id = System.currentTimeMillis().toString(),
            time = java.util.Date(),
            text = text
        )

        // Add to record (synchronous for socket send)
        val currentRecord = _record.value.toMutableList()
        currentRecord.add(userMessage)

        if (currentRecord.size > paginationConfig.maxMemoryMessages) {
            _record.value = currentRecord.takeLast(paginationConfig.maxMemoryMessages)
        } else {
            _record.value = currentRecord
        }

        // Add to message manager asynchronously for persistence
        scope.launch {
            messageManager?.addMessage(userMessage)
        }

        // Add to ChatState record for server sync
        ChatState.pushToRecord(
            com.conferbot.sdk.core.state.RecordEntry(
                id = userMessage.id,
                shape = "user-live-message",
                type = "user-live-message",
                text = text
            )
        )

        // Add to transcript
        ChatState.addToTranscript("user", text)

        // Send via response-record with full ChatState record (same as bot flow)
        socketClient?.sendResponseRecord(ChatState.buildResponseData())

        // Stop visitor typing indicator
        socketClient?.sendTypingStatus(sessionId, false)

        eventListener?.onMessageSent(userMessage)
    }

    /**
     * Send a message during bot flow mode
     */
    private fun sendBotFlowMessage(text: String, sessionId: String) {
        // Create user message (use user-input-response type for chatbot flow)
        val userMessage = RecordItem.UserInputResponse(
            id = System.currentTimeMillis().toString(),
            time = java.util.Date(),
            text = text
        )

        // Add to record (synchronous for socket send)
        val currentRecord = _record.value.toMutableList()
        currentRecord.add(userMessage)

        // Update record with trimming if needed
        if (currentRecord.size > paginationConfig.maxMemoryMessages) {
            _record.value = currentRecord.takeLast(paginationConfig.maxMemoryMessages)
        } else {
            _record.value = currentRecord
        }

        // Add to message manager asynchronously for persistence
        scope.launch {
            messageManager?.addMessage(userMessage)
        }

        // Send entire record via socket (embed-server expects full record)
        socketClient?.sendResponseRecord(
            chatSessionId = sessionId,
            record = _record.value.map { item ->
                mapOf(
                    "_id" to item.id,
                    "type" to item.type.value,
                    "time" to item.time.toString(),
                    "text" to when (item) {
                        is RecordItem.UserMessage -> item.text
                        is RecordItem.UserInputResponse -> item.text
                        is RecordItem.UserLiveMessage -> item.text
                        is RecordItem.BotMessage -> item.text ?: ""
                        is RecordItem.AgentMessage -> item.text
                        is RecordItem.AgentLeftMessage -> item.text
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
     * Reset the unread message count to zero.
     *
     * Called automatically by [ConferBotWidget] when the chat overlay opens.
     * Can also be called manually when the host app considers messages "read".
     */
    fun resetUnreadCount() {
        _unreadCount.value = 0
    }

    /**
     * Update the chat-visible flag.
     *
     * This is called internally by [ConferBotWidget] and should not normally
     * be called by host application code.
     *
     * @param visible true when the chat overlay is showing
     */
    fun setChatVisible(visible: Boolean) {
        _isChatVisible.value = visible
    }

    // ==================== Push Notification Methods ====================

    /**
     * Initialize notification components
     */
    private fun initializeNotifications(context: Context) {
        _notificationManager = ConferbotNotificationManager.getInstance(context)
        _notificationHandler = NotificationHandler.getInstance(context)

        // Set ChatActivity as default for deep linking
        _notificationHandler?.setChatActivityClass(ChatActivity::class.java)
        _notificationManager?.setChatActivityClass(ChatActivity::class.java)

        // Listen for token refresh
        NotificationTokenListener.addListener { token ->
            registerPushToken(token)
        }

        Log.d(TAG, "Notification components initialized")
    }

    /**
     * Register push notification token with both API and socket
     *
     * @param token FCM token
     */
    fun registerPushToken(token: String) {
        currentPushToken = token
        val sessionId = _chatSessionId.value

        // Register via API
        scope.launch {
            sessionId?.let {
                apiClient?.registerPushToken(token, it)
            }
        }

        // Also register via socket for real-time notifications
        if (sessionId != null && socketClient?.isConnected == true) {
            registerPushTokenViaSocket(token, sessionId)
        }

        Log.d(TAG, "Push token registered")
    }

    /**
     * Register push token via socket connection
     */
    private fun registerPushTokenViaSocket(token: String, sessionId: String) {
        val data = JSONObject().apply {
            put("token", token)
            put("platform", "android")
            put("sessionId", sessionId)
            put("deviceInfo", JSONObject().apply {
                put("os", "Android")
                put("osVersion", Build.VERSION.RELEASE)
                put("sdkVersion", Build.VERSION.SDK_INT)
                put("deviceModel", Build.MODEL)
            })
        }
        socketClient?.emit("register-push-token", data)
    }

    /**
     * Unregister push token (call when user logs out or disables notifications)
     */
    fun unregisterPushToken() {
        val token = currentPushToken ?: return
        val sessionId = _chatSessionId.value ?: return

        scope.launch {
            try {
                val data = JSONObject().apply {
                    put("token", token)
                    put("sessionId", sessionId)
                    put("platform", "android")
                }
                socketClient?.emit("unregister-push-token", data)
                currentPushToken = null
                Log.d(TAG, "Push token unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister push token", e)
            }
        }
    }

    /**
     * Handle push notification data
     * Returns true if the notification was handled by Conferbot
     *
     * @param data Push notification data payload
     * @return true if handled, false if not a Conferbot notification
     */
    fun handlePushNotification(data: Map<String, String>): Boolean {
        // Check if notification is from Conferbot
        val isConferbotNotification = data["source"] == "conferbot" ||
                data["type"] in listOf(
                    "conferbot_message",
                    "new_message",
                    "agent_joined",
                    "agent_left",
                    "chat_ended",
                    "handover_queued",
                    "queue_position_update"
                )

        if (!isConferbotNotification) {
            return false
        }

        // Parse and handle the notification
        val notification = ConferbotNotification.fromPushData(data)
        if (notification != null) {
            _notificationHandler?.handleNotification(notification)
            return true
        }

        return false
    }

    /**
     * Handle notification tap (when user taps on a notification)
     *
     * @param data Notification data
     */
    fun handleNotificationTap(data: Map<String, String>) {
        _notificationHandler?.handleNotificationTap(data)
    }

    /**
     * Set the current activity for in-app notification banners
     * Call this in your Activity's onResume
     *
     * @param activity Current activity
     */
    fun setCurrentActivity(activity: Activity?) {
        _notificationHandler?.setCurrentActivity(activity)
    }

    /**
     * Set custom chat activity class for deep linking from notifications
     *
     * @param activityClass Activity class to launch
     */
    fun setNotificationChatActivity(activityClass: Class<*>) {
        _notificationHandler?.setChatActivityClass(activityClass)
        _notificationManager?.setChatActivityClass(activityClass)
    }

    /**
     * Get notification manager for advanced customization
     */
    fun getNotificationManager(): ConferbotNotificationManager? = _notificationManager

    /**
     * Get notification handler for advanced customization
     */
    fun getNotificationHandler(): NotificationHandler? = _notificationHandler

    /**
     * Update notification settings
     *
     * @param settings New notification settings
     */
    fun updateNotificationSettings(settings: NotificationSettings) {
        val context = appContext ?: return
        _notificationManager?.updateSettings(settings)
        settings.save(context)
    }

    /**
     * Get current notification settings
     */
    fun getNotificationSettings(): NotificationSettings {
        return _notificationManager?.getSettings()
            ?: appContext?.let { NotificationSettings.load(it) }
            ?: NotificationSettings.default()
    }

    /**
     * Add a notification listener
     *
     * @param listener Listener to add
     */
    fun addNotificationListener(listener: NotificationListener) {
        _notificationHandler?.addNotificationListener(listener)
    }

    /**
     * Remove a notification listener
     *
     * @param listener Listener to remove
     */
    fun removeNotificationListener(listener: NotificationListener) {
        _notificationHandler?.removeNotificationListener(listener)
    }

    /**
     * Check if notifications are enabled at system level
     */
    fun areNotificationsEnabled(): Boolean {
        return _notificationManager?.areNotificationsEnabled() ?: false
    }

    /**
     * Cancel all Conferbot notifications
     */
    fun cancelAllNotifications() {
        _notificationManager?.cancelAllNotifications()
    }

    /**
     * Cancel a specific notification
     *
     * @param notificationId Notification ID to cancel
     */
    fun cancelNotification(notificationId: Int) {
        _notificationManager?.cancelNotification(notificationId)
    }

    /**
     * Show a custom notification (for testing or custom use cases)
     *
     * @param title Notification title
     * @param body Notification body
     * @param data Additional data
     */
    fun showNotification(title: String, body: String, data: Map<String, String> = emptyMap()) {
        _notificationManager?.showMessageNotification(title, body, data)
    }

    /**
     * Send typing status (emits visitor-typing event for live chat)
     */
    fun sendTypingStatus(isTyping: Boolean) {
        val sessionId = _chatSessionId.value ?: return
        socketClient?.sendTypingStatus(sessionId, isTyping)

        // Track typing behavior for analytics
        if (isTyping) {
            ChatAnalytics.trackTypingStart()
        } else {
            ChatAnalytics.trackTypingEnd()
        }
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
        // Finalize analytics before ending chat
        ChatAnalytics.finalizeChatAnalytics()
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
        _isLiveChatMode.value = false
        _isAgentTyping.value = false
        // Reset flow state so it can be re-triggered with same bot data
        flowStarted = false
        // Keep cachedChatbotNodes/Edges — same bot, new session
        // Clean up Knowledge Base service when history is cleared
        disposeKnowledgeBaseService()
        ChatState.reset()
    }

    // ==================== Session Persistence Methods ====================

    /**
     * Check if there's a valid persisted session for the current bot
     * Call this before openChat to determine if session restoration is available
     *
     * @return true if a valid session exists that can be restored
     */
    suspend fun hasPersistedSession(): Boolean {
        val context = appContext ?: return false
        val currentBotId = botId ?: return false
        return SessionPersistenceManager.hasValidSession(context, currentBotId)
    }

    /**
     * Restore a persisted session from the Room database
     * Call this before openChat to restore a previous session
     *
     * @return SessionRestoreResult indicating success and session details
     */
    suspend fun restorePersistedSession(): SessionRestoreResult {
        val context = appContext ?: return SessionRestoreResult(
            success = false,
            reason = "SDK not initialized"
        )
        val currentBotId = botId ?: return SessionRestoreResult(
            success = false,
            reason = "Bot ID not set"
        )

        val result = SessionPersistenceManager.restoreSession(
            context = context,
            botId = currentBotId,
            paginationConfig = paginationConfig
        )

        if (result.success && result.sessionId != null) {
            _chatSessionId.value = result.sessionId

            // Rejoin socket room with restored session
            socketClient?.joinChatRoom(
                chatSessionId = result.sessionId,
                deviceInfo = mapOf(
                    "os" to "Android",
                    "osVersion" to Build.VERSION.RELEASE,
                    "sdkVersion" to Build.VERSION.SDK_INT.toString(),
                    "deviceModel" to Build.MODEL
                )
            )

            // Initialize analytics for restored session
            ChatAnalytics.initializeChatAnalytics(
                sessionId = result.sessionId,
                botIdentifier = currentBotId,
                visitorIdentifier = result.visitorId ?: user?.id ?: appContext?.let { getOrCreateVisitorId(it) } ?: ""
            )

            eventListener?.onSessionStarted(result.sessionId)
            Log.d(TAG, "Restored persisted session: ${result.sessionId}")
        }

        return result
    }

    /**
     * Get information about a persisted session without restoring it
     *
     * @return SessionInfo or null if no valid session exists
     */
    suspend fun getPersistedSessionInfo(): SessionInfo? {
        val context = appContext ?: return null
        val currentBotId = botId ?: return null
        return SessionPersistenceManager.getSessionInfo(context, currentBotId)
    }

    /**
     * Invalidate the current session (mark as ended)
     * The session data will remain for 30 minutes before expiring
     */
    suspend fun invalidateCurrentSession() {
        val context = appContext ?: return
        val sessionId = _chatSessionId.value ?: return
        SessionPersistenceManager.invalidateSession(context, sessionId)
    }

    /**
     * Delete the current session and all its data
     */
    suspend fun deleteCurrentSession() {
        val context = appContext ?: return
        val sessionId = _chatSessionId.value ?: return
        SessionPersistenceManager.deleteSession(context, sessionId)
        clearHistory()
    }

    /**
     * Clear expired sessions to free up storage
     * Call this periodically (e.g., on app startup)
     */
    suspend fun clearExpiredSessions() {
        val context = appContext ?: return
        SessionPersistenceManager.clearExpiredSessions(context)
    }

    // ==================== Node Flow Engine Methods ====================

    /**
     * Initialize the flow engine with bot configuration data
     * Call this when you receive steps and edges from the server
     *
     * @param steps List of node step configurations
     * @param edges List of edge connections between nodes
     */
    fun initializeFlowEngine(
        steps: List<Map<String, Any>>,
        edges: List<Map<String, Any>>
    ) {
        val sessionId = _chatSessionId.value ?: return
        val visitorId = user?.id ?: appContext?.let { getOrCreateVisitorId(it) } ?: ""
        val currentBotId = botId ?: return

        nodeFlowEngine?.initialize(
            chatSessionId = sessionId,
            visitorId = visitorId,
            botId = currentBotId,
            workspaceId = cachedWorkspaceId,
            stepsData = steps,
            edgesData = edges
        )

        // Set _botName variable for handover handler
        val resolvedBotName = _serverCustomization.value?.botName
            ?: _serverCustomization.value?.logoText ?: ""
        if (resolvedBotName.isNotBlank()) {
            ChatState.setVariable("_botName", resolvedBotName)
        }
    }

    /**
     * Start the flow engine processing from the first node
     * Call this after initializing with steps/edges
     */
    fun startFlow() {
        nodeFlowEngine?.start()
    }

    /**
     * Submit a response to the current interactive node
     * Use this for text input, button selections, ratings, etc.
     *
     * @param response The user's response (String, Int, List, or other type depending on node)
     */
    fun submitNodeResponse(response: Any) {
        // Extract the display label from the response to show as a user message bubble
        val displayText = when (response) {
            is String -> response
            is Map<*, *> -> (response["text"] ?: response["label"] ?: response["selectedChoice"])?.toString()
            is List<*> -> response.joinToString(", ")
            else -> response.toString()
        }

        // Add user message to the visible record (right-aligned bubble)
        if (!displayText.isNullOrBlank()) {
            val userMessage = RecordItem.UserInputResponse(
                id = "user-choice-${System.currentTimeMillis()}",
                time = java.util.Date(),
                text = displayText
            )
            addMessageToRecord(userMessage)
        }

        nodeFlowEngine?.submitResponse(response)
    }

    /**
     * Clear any current error message from the flow engine
     */
    fun clearNodeError() {
        nodeFlowEngine?.clearError()
    }

    /**
     * Get the ChatState singleton for direct access to conversation state
     * Useful for advanced scenarios where you need to read/modify answer variables
     */
    fun getChatState(): ChatState = ChatState

    /**
     * Set event listener
     */
    fun setEventListener(listener: ConferBotEventListener) {
        this.eventListener = listener
    }

    /**
     * Disconnect socket and clean up flow engine
     */
    fun disconnect() {
        val sessionId = _chatSessionId.value
        if (sessionId != null) {
            // Track potential drop-off before disconnecting
            ChatAnalytics.trackPotentialDropOff("disconnected")
            socketClient?.leaveChatRoom(sessionId)
        }
        // Finalize analytics
        ChatAnalytics.finalizeChatAnalytics()
        // Clean up Knowledge Base service
        disposeKnowledgeBaseService()
        // Clean up File Upload service
        _fileUploadManager?.dispose()
        _fileUploadService?.cancelUpload()
        // Shutdown offline manager
        _offlineManager?.shutdown()
        socketClient?.disconnect()
        nodeFlowEngine?.destroy()
        // Reset flow state
        flowStarted = false
        cachedChatbotNodes = null
        cachedChatbotEdges = null
        // Reset server theme/customization
        _serverTheme.value = null
        _serverCustomization.value = null
        // Reset widget state
        _isChatVisible.value = false
        _isLiveChatMode.value = false
        _isAgentTyping.value = false
        // FIX 6: Cancel coroutine scope to prevent leaked coroutines
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        _isConnected.value = false
        _isInitialized.value = false
    }

    /**
     * Get customization
     */
    fun getCustomization(): ConferBotCustomization? = customization

    // ==================== Offline Mode Methods ====================

    /**
     * Initialize the offline manager
     */
    private fun initializeOfflineManager(context: Context) {
        _offlineManager = OfflineManager(context, config.enableOfflineMode).apply {
            // Set up listener for offline events
            setListener(object : OfflineManagerListener {
                override fun onOnlineStatusChanged(isOnline: Boolean) {
                    _isOnline.value = isOnline
                    Log.d(TAG, "Online status changed: $isOnline")
                }

                override fun onQueueSynced(successCount: Int, failCount: Int) {
                    Log.d(TAG, "Queue synced: $successCount success, $failCount failed")
                    _pendingMessageCount.value = getPendingMessageCount()
                    _isSyncingQueue.value = false
                }

                override fun onMessageFailed(message: QueuedMessage) {
                    Log.w(TAG, "Message failed after retries: ${message.id}")
                    eventListener?.onMessageFailed(message.id)
                }
            })

            // Initialize and connect to socket client
            initialize()

            // Observe offline manager states
            scope.launch {
                isOnline.collectLatest { online ->
                    _isOnline.value = online
                }
            }

            scope.launch {
                pendingMessageCount.collectLatest { count ->
                    _pendingMessageCount.value = count
                }
            }

            scope.launch {
                isSyncing.collectLatest { syncing ->
                    _isSyncingQueue.value = syncing
                }
            }
        }

        // Connect offline manager to socket client
        _offlineManager?.let { manager ->
            socketClient?.setOfflineManager(manager)
        } ?: Log.e(TAG, "Offline manager is null after initialization")
    }

    /**
     * Check if offline mode is enabled
     */
    fun isOfflineModeEnabled(): Boolean = config.enableOfflineMode

    /**
     * Check if currently online
     */
    fun isCurrentlyOnline(): Boolean = _isOnline.value

    /**
     * Get count of pending messages in offline queue
     */
    fun getPendingMessageCount(): Int = _offlineManager?.getPendingCount() ?: 0

    /**
     * Check if there are pending messages in offline queue
     */
    fun hasPendingMessages(): Boolean = _offlineManager?.hasPendingMessages() ?: false

    /**
     * Manually trigger processing of queued messages
     * Useful when you want to retry sending failed messages
     */
    fun processOfflineQueue() {
        _offlineManager?.processQueue()
    }

    /**
     * Clear all pending messages in the offline queue
     */
    fun clearOfflineQueue() {
        _offlineManager?.clearQueue()
        _pendingMessageCount.value = 0
    }

    /**
     * Clear pending messages for current session only
     */
    fun clearCurrentSessionQueue() {
        val sessionId = _chatSessionId.value ?: return
        _offlineManager?.clearSessionQueue(sessionId)
        _pendingMessageCount.value = _offlineManager?.getPendingCount() ?: 0
    }

    /**
     * Get the offline manager for advanced usage
     */
    fun getOfflineManager(): OfflineManager? = _offlineManager

    // ==================== Analytics Methods ====================

    /**
     * Set UTM parameters for analytics tracking
     * Call this before starting a chat session if you have UTM data
     * from deep links or other sources
     *
     * @param utmSource UTM source parameter
     * @param utmMedium UTM medium parameter
     * @param utmCampaign UTM campaign parameter
     * @param utmTerm UTM term parameter
     * @param utmContent UTM content parameter
     * @param referrer Referrer URL/source
     * @param landingPage Landing page URL
     */
    fun setUtmParameters(
        utmSource: String? = null,
        utmMedium: String? = null,
        utmCampaign: String? = null,
        utmTerm: String? = null,
        utmContent: String? = null,
        referrer: String? = null,
        landingPage: String? = null
    ) {
        ChatAnalytics.setUtmParameters(
            utmSource = utmSource,
            utmMedium = utmMedium,
            utmCampaign = utmCampaign,
            utmTerm = utmTerm,
            utmContent = utmContent,
            referrer = referrer,
            landingPage = landingPage
        )
    }

    /**
     * Track a custom interaction in analytics
     *
     * @param type Interaction type (e.g., "linksClicked", "buttonsClicked", "filesUploaded")
     * @param data Additional data about the interaction
     */
    fun trackInteraction(type: String, data: Map<String, Any> = emptyMap()) {
        ChatAnalytics.trackInteraction(type, data)
    }

    /**
     * Track goal completion in analytics
     *
     * @param goalId The ID of the completed goal
     * @param conversionEvent Optional conversion event name
     * @param conversionValue Optional conversion value (monetary value, etc.)
     */
    fun trackGoalCompletion(
        goalId: String,
        conversionEvent: String? = null,
        conversionValue: Double? = null
    ) {
        ChatAnalytics.trackGoalCompletion(goalId, conversionEvent, conversionValue)
    }

    /**
     * Submit chat rating/feedback
     *
     * @param csatScore Customer satisfaction score (1-5)
     * @param feedback Optional text feedback
     * @param thumbsUp Optional thumbs up/down
     * @param npsScore Optional NPS score (0-10)
     * @param source Source of the rating
     */
    fun submitChatRating(
        csatScore: Int? = null,
        feedback: String? = null,
        thumbsUp: Boolean? = null,
        npsScore: Int? = null,
        source: String = "post_chat_survey"
    ) {
        ChatAnalytics.submitChatRating(csatScore, feedback, thumbsUp, npsScore, source)
    }

    /**
     * Track text deletion for typing behavior analytics
     * Call this when user deletes text in the input field
     */
    fun trackTextDeletion() {
        ChatAnalytics.trackDeletion()
    }

    /**
     * Notify analytics that app is going to background
     * This helps track potential drop-offs
     */
    fun onAppBackgrounded() {
        ChatAnalytics.trackPotentialDropOff("app_backgrounded")
    }

    /**
     * Get the ChatAnalytics singleton for direct access
     * Use this for advanced analytics scenarios
     */
    fun getAnalytics(): ChatAnalytics = ChatAnalytics

    // ==================== Knowledge Base Methods ====================

    /**
     * Get the Knowledge Base service
     * Returns null if session is not initialized or KB is not enabled
     */
    val knowledgeBaseService: KnowledgeBaseService?
        get() = _knowledgeBaseService

    /**
     * Initialize and get the Knowledge Base service
     * Call this after session is initialized
     *
     * @return KnowledgeBaseService instance or null if not available
     */
    fun initKnowledgeBaseService(): KnowledgeBaseService? {
        val sessionId = _chatSessionId.value ?: return null
        val visitorId = user?.id ?: appContext?.let { getOrCreateVisitorId(it) } ?: ""
        val socket = socketClient ?: return null

        if (_knowledgeBaseService == null) {
            _knowledgeBaseService = KnowledgeBaseService(
                socketClient = socket,
                visitorId = visitorId,
                chatSessionId = sessionId
            ).also { it.initialize() }
        }

        return _knowledgeBaseService
    }

    /**
     * Fetch Knowledge Base data
     * Triggers socket event to fetch categories and articles
     */
    fun fetchKnowledgeBase() {
        initKnowledgeBaseService()?.fetchKnowledgeBase()
    }

    /**
     * Search Knowledge Base articles
     *
     * @param query Search query string
     * @return Flow of matching articles
     */
    fun searchKnowledgeBase(query: String) = initKnowledgeBaseService()?.searchArticles(query)

    /**
     * Track article view in Knowledge Base
     * Only tracks once per session per article
     *
     * @param article The article being viewed
     */
    fun trackKnowledgeBaseArticleView(article: KnowledgeBaseArticle) {
        initKnowledgeBaseService()?.trackArticleView(article)
    }

    /**
     * Start engagement tracking for a Knowledge Base article
     *
     * @param articleId The article ID
     */
    fun startKnowledgeBaseArticleEngagement(articleId: String) {
        initKnowledgeBaseService()?.startArticleEngagement(articleId)
    }

    /**
     * Update scroll depth for article engagement tracking
     *
     * @param scrollDepth Scroll percentage (0-100)
     */
    fun updateKnowledgeBaseScrollDepth(scrollDepth: Int) {
        initKnowledgeBaseService()?.updateScrollDepth(scrollDepth)
    }

    /**
     * Rate a Knowledge Base article
     *
     * @param articleId The article ID
     * @param helpful Whether the article was helpful
     * @return Flow<Boolean> indicating success
     */
    fun rateKnowledgeBaseArticle(articleId: String, helpful: Boolean) =
        initKnowledgeBaseService()?.rateArticle(articleId, helpful)

    /**
     * Check if a Knowledge Base article has been rated in this session
     *
     * @param articleId The article ID
     * @return true if already rated
     */
    fun hasRatedKnowledgeBaseArticle(articleId: String): Boolean {
        return initKnowledgeBaseService()?.hasRatedArticle(articleId) ?: false
    }

    /**
     * Clean up Knowledge Base service
     */
    private fun disposeKnowledgeBaseService() {
        _knowledgeBaseService?.dispose()
        _knowledgeBaseService = null
    }

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
    fun onOnlineStatusChanged(isOnline: Boolean) {}
    fun onMessageFailed(messageId: String) {}
    fun onQueueSynced(successCount: Int, failCount: Int) {}
}
