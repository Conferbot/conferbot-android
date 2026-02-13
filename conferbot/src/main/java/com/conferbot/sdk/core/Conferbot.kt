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
import com.conferbot.sdk.services.ApiClient
import com.conferbot.sdk.services.FileUploadManager
import com.conferbot.sdk.services.FileUploadService
import com.conferbot.sdk.services.KnowledgeBaseService
import com.conferbot.sdk.services.SocketClient
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

    private val _isAgentTyping = MutableStateFlow(false)
    val isAgentTyping: StateFlow<Boolean> = _isAgentTyping.asStateFlow()

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
        })

        socketClient?.on(SocketEvents.DISCONNECT, Emitter.Listener {
            _isConnected.value = false
            Log.d(TAG, "Socket disconnected")
        })

        // Bot response - process through NodeFlowEngine for node-based messages
        socketClient?.on(SocketEvents.BOT_RESPONSE, Emitter.Listener { args ->
            handleBotResponse(args)
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
                // Notify flow engine about agent connection
                nodeFlowEngine?.handleAgentAccepted(agent.name)
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
            nodeFlowEngine?.handleChatEnded()
            eventListener?.onSessionEnded(_chatSessionId.value ?: "")
        })

        // No agents available (for human handover)
        socketClient?.on(SocketEvents.NO_AGENTS_AVAILABLE, Emitter.Listener {
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
     * Convert JSONObject to Map for flow engine processing
     */
    @Suppress("UNCHECKED_CAST")
    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            val value = json.get(key)
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

                // Initialize ChatState for flow engine with context
                ChatState.initializeWithContext(
                    context = context,
                    chatSessionId = session.chatSessionId,
                    visitorId = session.visitorId ?: user?.id ?: "",
                    botId = botId ?: "",
                    workspaceId = null,
                    maxMessages = paginationConfig.maxMemoryMessages,
                    pageSizeConfig = paginationConfig.pageSize
                )

                // Initialize analytics tracking for this session
                ChatAnalytics.initializeChatAnalytics(
                    sessionId = session.chatSessionId,
                    botIdentifier = botId ?: "",
                    visitorIdentifier = session.visitorId ?: user?.id ?: ""
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
     * Send typing status
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
                visitorIdentifier = result.visitorId ?: user?.id ?: ""
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
        val visitorId = user?.id ?: ""
        val currentBotId = botId ?: return

        nodeFlowEngine?.initialize(
            chatSessionId = sessionId,
            visitorId = visitorId,
            botId = currentBotId,
            workspaceId = null,
            stepsData = steps,
            edgesData = edges
        )
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
        // FIX 6: Cancel coroutine scope to prevent leaked coroutines
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        _isConnected.value = false
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
    fun getKnowledgeBaseService(): KnowledgeBaseService? {
        val sessionId = _chatSessionId.value ?: return null
        val visitorId = user?.id ?: ""
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
        getKnowledgeBaseService()?.fetchKnowledgeBase()
    }

    /**
     * Search Knowledge Base articles
     *
     * @param query Search query string
     * @return Flow of matching articles
     */
    fun searchKnowledgeBase(query: String) = getKnowledgeBaseService()?.searchArticles(query)

    /**
     * Track article view in Knowledge Base
     * Only tracks once per session per article
     *
     * @param article The article being viewed
     */
    fun trackKnowledgeBaseArticleView(article: KnowledgeBaseArticle) {
        getKnowledgeBaseService()?.trackArticleView(article)
    }

    /**
     * Start engagement tracking for a Knowledge Base article
     *
     * @param articleId The article ID
     */
    fun startKnowledgeBaseArticleEngagement(articleId: String) {
        getKnowledgeBaseService()?.startArticleEngagement(articleId)
    }

    /**
     * Update scroll depth for article engagement tracking
     *
     * @param scrollDepth Scroll percentage (0-100)
     */
    fun updateKnowledgeBaseScrollDepth(scrollDepth: Int) {
        getKnowledgeBaseService()?.updateScrollDepth(scrollDepth)
    }

    /**
     * Rate a Knowledge Base article
     *
     * @param articleId The article ID
     * @param helpful Whether the article was helpful
     * @return Flow<Boolean> indicating success
     */
    fun rateKnowledgeBaseArticle(articleId: String, helpful: Boolean) =
        getKnowledgeBaseService()?.rateArticle(articleId, helpful)

    /**
     * Check if a Knowledge Base article has been rated in this session
     *
     * @param articleId The article ID
     * @return true if already rated
     */
    fun hasRatedKnowledgeBaseArticle(articleId: String): Boolean {
        return getKnowledgeBaseService()?.hasRatedArticle(articleId) ?: false
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
