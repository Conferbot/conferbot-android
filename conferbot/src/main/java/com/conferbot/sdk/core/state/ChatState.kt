package com.conferbot.sdk.core.state

import android.content.Context
import android.util.Log
import com.conferbot.sdk.data.SessionRepository
import com.conferbot.sdk.models.RecordItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Answer variable stored during conversation flow
 * Mirrors the web widget's answerVariables structure
 */
data class AnswerVariable(
    val nodeId: String,
    val key: String,
    var value: Any? = null
)

/**
 * Transcript entry for conversation history
 */
data class TranscriptEntry(
    val by: String,  // "bot", "user", or "agent"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * User metadata collected during conversation
 */
data class UserMetadata(
    var name: String? = null,
    var email: String? = null,
    var phone: String? = null,
    var metadata: MutableMap<String, Any> = mutableMapOf()
)

/**
 * Record entry for each interaction
 */
data class RecordEntry(
    val id: String,
    val shape: String,
    val type: String? = null,
    val text: String? = null,
    val time: String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(java.util.Date()),
    val data: MutableMap<String, Any?> = mutableMapOf()
)

/**
 * Pagination state for message loading
 */
data class PaginationState(
    val isLoading: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val oldestLoadedIndex: Int = Int.MAX_VALUE,
    val totalMessageCount: Int = 0
)

/**
 * Central state manager for the chat conversation
 * Manages all state following the web widget's architecture
 *
 * Enhanced with pagination support to prevent OOM crashes with large chats.
 */
object ChatState {

    private const val TAG = "ChatState"

    /** Maximum number of transcript entries to keep in memory. */
    private const val MAX_TRANSCRIPT_ENTRIES = 500

    // Coroutine scope for async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Message repository for persistent storage
    private var messageRepository: MessageRepository? = null

    // Session repository for Room database persistence
    private var sessionRepository: SessionRepository? = null

    // Flag to enable/disable auto-persistence
    private var autoPersistEnabled = true

    // Answer variables - stores all user responses
    private val _answerVariables = MutableStateFlow<MutableList<AnswerVariable>>(mutableListOf())
    val answerVariables: StateFlow<List<AnswerVariable>> = _answerVariables.asStateFlow()

    // Variables - temporary calculation storage
    private val _variables = MutableStateFlow<MutableMap<String, Any>>(mutableMapOf())
    val variables: StateFlow<Map<String, Any>> = _variables.asStateFlow()

    // User metadata
    private val _userMetadata = MutableStateFlow(UserMetadata())
    val userMetadata: StateFlow<UserMetadata> = _userMetadata.asStateFlow()

    // Transcript - conversation history (limited for memory efficiency)
    private val _transcript = MutableStateFlow<MutableList<TranscriptEntry>>(mutableListOf())
    val transcript: StateFlow<List<TranscriptEntry>> = _transcript.asStateFlow()

    // Record - full conversation record for server sync
    private val _record = MutableStateFlow<MutableList<RecordEntry>>(mutableListOf())
    val record: StateFlow<List<RecordEntry>> = _record.asStateFlow()

    // ========== Paginated Messages ==========

    // Messages with pagination support (RecordItem type for UI)
    private val _messages = MutableStateFlow<List<RecordItem>>(emptyList())
    val messages: StateFlow<List<RecordItem>> = _messages.asStateFlow()

    // Pagination state
    private val _paginationState = MutableStateFlow(PaginationState())
    val paginationState: StateFlow<PaginationState> = _paginationState.asStateFlow()

    // Configuration
    private var maxMemoryMessages = 100
    private var pageSize = 50

    // Current node index in the flow
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // Current flow steps
    private val _steps = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val steps: StateFlow<List<Map<String, Any>>> = _steps.asStateFlow()

    // Chat session ID
    private var _chatSessionId: String? = null
    val chatSessionId: String? get() = _chatSessionId

    // Visitor ID
    private var _visitorId: String? = null
    val visitorId: String? get() = _visitorId

    // Bot ID
    private var _botId: String? = null
    val botId: String? get() = _botId

    // Workspace ID
    private var _workspaceId: String? = null
    val workspaceId: String? get() = _workspaceId

    /**
     * Initialize chat state with session info
     */
    fun initialize(
        chatSessionId: String,
        visitorId: String,
        botId: String,
        workspaceId: String? = null
    ) {
        _chatSessionId = chatSessionId
        _visitorId = visitorId
        _botId = botId
        _workspaceId = workspaceId
    }

    /**
     * Initialize with context for persistent storage
     */
    fun initializeWithContext(
        context: Context,
        chatSessionId: String,
        visitorId: String,
        botId: String,
        workspaceId: String? = null,
        maxMessages: Int = 100,
        pageSizeConfig: Int = 50,
        enableAutoPersist: Boolean = true
    ) {
        _chatSessionId = chatSessionId
        _visitorId = visitorId
        _botId = botId
        _workspaceId = workspaceId
        maxMemoryMessages = maxMessages
        pageSize = pageSizeConfig
        autoPersistEnabled = enableAutoPersist

        // Initialize message repository
        messageRepository = MessageRepository.getInstance(context, chatSessionId)

        // Initialize session repository for Room database persistence
        sessionRepository = SessionRepository.getInstance(context)

        // Load recent messages
        scope.launch {
            val recentMessages = messageRepository?.initialize() ?: emptyList()
            _messages.value = recentMessages

            // Update pagination state
            messageRepository?.let { repo ->
                _paginationState.value = PaginationState(
                    isLoading = false,
                    hasMoreMessages = repo.hasMoreMessages(),
                    oldestLoadedIndex = repo.getOldestLoadedIndex(),
                    totalMessageCount = repo.totalMessageCount
                )
            }

            // Persist session to Room database
            if (autoPersistEnabled) {
                persistSession()
            }
        }
    }

    /**
     * Persist current session state to Room database
     */
    private fun persistSession() {
        val sessionId = _chatSessionId ?: return
        val visitor = _visitorId ?: return
        val bot = _botId ?: return

        scope.launch {
            try {
                sessionRepository?.saveSession(
                    sessionId = sessionId,
                    visitorId = visitor,
                    botId = bot,
                    workspaceId = _workspaceId,
                    currentIndex = _currentIndex.value,
                    isActive = true
                )
                Log.d(TAG, "Session persisted: $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist session", e)
            }
        }
    }

    /**
     * Persist answer variables to Room database
     */
    private fun persistAnswerVariables() {
        val sessionId = _chatSessionId ?: return
        if (!autoPersistEnabled || sessionRepository == null) return

        scope.launch {
            try {
                sessionRepository?.saveAnswerVariables(sessionId, _answerVariables.value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist answer variables", e)
            }
        }
    }

    /**
     * Persist user metadata to Room database
     */
    private fun persistUserMetadata() {
        val sessionId = _chatSessionId ?: return
        if (!autoPersistEnabled || sessionRepository == null) return

        scope.launch {
            try {
                sessionRepository?.saveUserMetadata(sessionId, _userMetadata.value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist user metadata", e)
            }
        }
    }

    /**
     * Persist transcript entry to Room database
     */
    private fun persistTranscriptEntry(by: String, message: String, timestamp: Long) {
        val sessionId = _chatSessionId ?: return
        if (!autoPersistEnabled || sessionRepository == null) return

        scope.launch {
            try {
                sessionRepository?.saveTranscriptEntry(sessionId, by, message, timestamp)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist transcript entry", e)
            }
        }
    }

    /**
     * Persist record entry to Room database
     */
    private fun persistRecordEntry(entry: RecordEntry) {
        val sessionId = _chatSessionId ?: return
        if (!autoPersistEnabled || sessionRepository == null) return

        scope.launch {
            try {
                sessionRepository?.saveRecordEntry(sessionId, entry)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist record entry", e)
            }
        }
    }

    /**
     * Persist current index to Room database
     */
    private fun persistCurrentIndex() {
        val sessionId = _chatSessionId ?: return
        if (!autoPersistEnabled || sessionRepository == null) return

        scope.launch {
            try {
                sessionRepository?.updateCurrentIndex(sessionId, _currentIndex.value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist current index", e)
            }
        }
    }

    /**
     * Restore session from Room database
     * Returns true if session was restored, false otherwise
     */
    suspend fun restoreSession(context: Context, botId: String): Boolean {
        sessionRepository = SessionRepository.getInstance(context)

        val restoredSession = sessionRepository?.restoreLatestSession(botId) ?: return false

        Log.d(TAG, "Restoring session: ${restoredSession.session.sessionId}")

        // Restore session info
        _chatSessionId = restoredSession.session.sessionId
        _visitorId = restoredSession.session.visitorId
        _botId = restoredSession.session.botId
        _workspaceId = restoredSession.session.workspaceId
        _currentIndex.value = restoredSession.session.currentIndex

        // Restore answer variables
        _answerVariables.value = restoredSession.answerVariables.toMutableList()

        // Restore user metadata
        restoredSession.userMetadata?.let { metadata ->
            _userMetadata.value = metadata
        }

        // Restore transcript
        _transcript.value = restoredSession.transcript.toMutableList()

        // Restore record
        _record.value = restoredSession.records.toMutableList()

        // Initialize message repository and load messages
        messageRepository = MessageRepository.getInstance(context, restoredSession.session.sessionId)
        val recentMessages = messageRepository?.initialize() ?: emptyList()
        _messages.value = recentMessages

        // Update pagination state
        messageRepository?.let { repo ->
            _paginationState.value = PaginationState(
                isLoading = false,
                hasMoreMessages = repo.hasMoreMessages(),
                oldestLoadedIndex = repo.getOldestLoadedIndex(),
                totalMessageCount = repo.totalMessageCount
            )
        }

        Log.d(TAG, "Session restored successfully: ${restoredSession.session.sessionId}")
        return true
    }

    /**
     * Check if a valid session exists for a bot
     */
    suspend fun hasValidSession(context: Context, botId: String): Boolean {
        val repo = SessionRepository.getInstance(context)
        return repo.getLatestValidSession(botId) != null
    }

    /**
     * Enable or disable auto-persistence
     */
    fun setAutoPersist(enabled: Boolean) {
        autoPersistEnabled = enabled
    }

    /**
     * Get session repository (for advanced usage)
     */
    fun getSessionRepository(): SessionRepository? = sessionRepository

    /**
     * Configure pagination settings
     */
    fun configurePagination(maxMessages: Int = 100, pageSizeConfig: Int = 50) {
        maxMemoryMessages = maxMessages
        pageSize = pageSizeConfig
    }

    /**
     * Set the flow steps from server response
     */
    fun setSteps(steps: List<Map<String, Any>>) {
        _steps.value = steps
    }

    /**
     * Get current node from steps
     */
    fun getCurrentNode(): Map<String, Any>? {
        val index = _currentIndex.value
        val stepsList = _steps.value
        return if (index >= 0 && index < stepsList.size) stepsList[index] else null
    }

    /**
     * Move to next node
     */
    fun incrementIndex() {
        _currentIndex.value++
        persistCurrentIndex()
    }

    /**
     * Set specific index (for jumping)
     */
    fun setCurrentIndex(index: Int) {
        _currentIndex.value = index
        persistCurrentIndex()
    }

    // ========== Paginated Message Management ==========

    /**
     * Add a new message with pagination support
     */
    fun addMessage(message: RecordItem) {
        scope.launch {
            // Add to repository for persistence
            messageRepository?.addMessage(message)

            // Add to in-memory list
            val currentMessages = _messages.value.toMutableList()
            currentMessages.add(message)

            // Trim if exceeds max memory limit
            if (currentMessages.size > maxMemoryMessages) {
                val trimmedMessages = currentMessages.takeLast(maxMemoryMessages)
                _messages.value = trimmedMessages
            } else {
                _messages.value = currentMessages
            }

            // Update pagination state
            updatePaginationState()
        }
    }

    /**
     * Add multiple messages (batch operation)
     */
    fun addMessages(newMessages: List<RecordItem>) {
        scope.launch {
            // Add to repository
            messageRepository?.addMessages(newMessages)

            // Add to in-memory list
            val currentMessages = _messages.value.toMutableList()
            currentMessages.addAll(newMessages)

            // Trim if exceeds max memory limit
            if (currentMessages.size > maxMemoryMessages) {
                _messages.value = currentMessages.takeLast(maxMemoryMessages)
            } else {
                _messages.value = currentMessages
            }

            updatePaginationState()
        }
    }

    /**
     * Load more (older) messages for pagination
     * @return Flow emitting loaded messages
     */
    fun loadMoreMessages(): Flow<List<RecordItem>> = flow {
        if (_paginationState.value.isLoading || !_paginationState.value.hasMoreMessages) {
            emit(emptyList())
            return@flow
        }

        _paginationState.value = _paginationState.value.copy(isLoading = true)

        try {
            val oldestIndex = _paginationState.value.oldestLoadedIndex
            val olderMessages = messageRepository?.loadOlderMessages(oldestIndex) ?: emptyList()

            if (olderMessages.isNotEmpty()) {
                // Prepend older messages to current list
                val currentMessages = _messages.value.toMutableList()
                currentMessages.addAll(0, olderMessages)

                // Trim from the end if exceeds limit
                if (currentMessages.size > maxMemoryMessages * 2) {
                    // Keep more messages temporarily during pagination
                    _messages.value = currentMessages.take(maxMemoryMessages * 2)
                } else {
                    _messages.value = currentMessages
                }
            }

            updatePaginationState()
            emit(olderMessages)
        } catch (e: Exception) {
            _paginationState.value = _paginationState.value.copy(isLoading = false)
            emit(emptyList())
        }
    }

    /**
     * Check if there are more messages to load
     */
    fun hasMoreMessages(): Boolean {
        return messageRepository?.hasMoreMessages() ?: false
    }

    /**
     * Clear old messages from memory, keeping only the most recent
     * @param keepLast Number of messages to keep in memory
     */
    fun clearOldMessages(keepLast: Int = 100) {
        scope.launch {
            messageRepository?.clearOldMessagesFromMemory(keepLast)

            val currentMessages = _messages.value
            if (currentMessages.size > keepLast) {
                _messages.value = currentMessages.takeLast(keepLast)
            }

            updatePaginationState()
        }
    }

    /**
     * Update pagination state from repository
     */
    private fun updatePaginationState() {
        messageRepository?.let { repo ->
            _paginationState.value = PaginationState(
                isLoading = false,
                hasMoreMessages = repo.hasMoreMessages(),
                oldestLoadedIndex = repo.getOldestLoadedIndex(),
                totalMessageCount = repo.totalMessageCount
            )
        }
    }

    /**
     * Clear all messages from memory (for low memory situations)
     * Messages remain persisted on disk
     */
    fun clearMessageMemoryCache() {
        scope.launch {
            messageRepository?.clearMemoryCache()
            _messages.value = emptyList()
            _paginationState.value = PaginationState(
                isLoading = false,
                hasMoreMessages = true,
                oldestLoadedIndex = Int.MAX_VALUE,
                totalMessageCount = messageRepository?.totalMessageCount ?: 0
            )
        }
    }

    /**
     * Reload recent messages after memory clear
     */
    fun reloadRecentMessages() {
        scope.launch {
            val recentMessages = messageRepository?.initialize() ?: emptyList()
            _messages.value = recentMessages
            updatePaginationState()
        }
    }

    // ========== Answer Variables ==========

    /**
     * Add a new answer variable (when node is displayed)
     */
    fun addAnswerVariable(nodeId: String, key: String, value: Any? = null) {
        val list = _answerVariables.value.toMutableList()
        // Check if already exists
        val existing = list.find { it.nodeId == nodeId }
        if (existing != null) {
            existing.value = value
        } else {
            list.add(AnswerVariable(nodeId, key, value))
        }
        _answerVariables.value = list

        // Persist to database
        val sessionId = _chatSessionId
        if (sessionId != null && autoPersistEnabled) {
            scope.launch {
                sessionRepository?.saveAnswerVariable(sessionId, nodeId, key, value)
            }
        }
    }

    /**
     * Update answer variable by nodeId
     */
    fun setAnswerVariable(nodeId: String, value: Any?) {
        val list = _answerVariables.value.toMutableList()
        val variable = list.find { it.nodeId == nodeId }
        if (variable != null) {
            variable.value = value
        }
        _answerVariables.value = list

        // Persist to database
        val sessionId = _chatSessionId
        if (sessionId != null && variable != null && autoPersistEnabled) {
            scope.launch {
                sessionRepository?.saveAnswerVariable(sessionId, nodeId, variable.key, value)
            }
        }
    }

    /**
     * Update or create answer variable by key
     */
    fun setAnswerVariableByKey(key: String, value: Any?) {
        val list = _answerVariables.value.toMutableList()
        val variable = list.find { it.key == key }
        val nodeId: String
        if (variable != null) {
            variable.value = value
            nodeId = variable.nodeId
        } else {
            nodeId = "column_mapped_$key"
            list.add(AnswerVariable(nodeId, key, value))
        }
        _answerVariables.value = list

        // Persist to database
        val sessionId = _chatSessionId
        if (sessionId != null && autoPersistEnabled) {
            scope.launch {
                sessionRepository?.saveAnswerVariable(sessionId, nodeId, key, value)
            }
        }
    }

    /**
     * Get answer variable value by key
     */
    fun getAnswerVariableValue(key: String): Any? {
        return _answerVariables.value.find { it.key == key }?.value
    }

    /**
     * Get all answer variables as map
     */
    fun getAnswerVariablesMap(): Map<String, Any?> {
        return _answerVariables.value.associate { it.key to it.value }
    }

    // ========== Variables (Temporary Calculations) ==========

    /**
     * Set a temporary variable
     */
    fun setVariable(name: String, value: Any) {
        val map = _variables.value.toMutableMap()
        map[name] = value
        _variables.value = map
    }

    /**
     * Get a temporary variable
     */
    fun getVariable(name: String): Any? {
        return _variables.value[name]
    }

    /**
     * Resolve a value that might be a variable reference
     * Format: {{variableName}} or ${variableName}
     */
    fun resolveValue(value: String): Any {
        // Check if it's a variable reference
        val variablePattern = Regex("\\{\\{(.+?)\\}\\}|\\$\\{(.+?)\\}")
        val match = variablePattern.find(value)

        if (match != null) {
            val varName = match.groupValues[1].ifEmpty { match.groupValues[2] }
            // First check answer variables
            val answerValue = getAnswerVariableValue(varName)
            if (answerValue != null) return answerValue
            // Then check temp variables
            return getVariable(varName) ?: value
        }

        return value
    }

    // ========== User Metadata ==========

    /**
     * Set user metadata field
     */
    fun setUserMetadata(type: String, value: String) {
        val metadata = _userMetadata.value.copy()
        when (type.lowercase()) {
            "name" -> metadata.name = value
            "email" -> metadata.email = value
            "phone", "mobile" -> metadata.phone = value
            else -> metadata.metadata[type] = value
        }
        _userMetadata.value = metadata

        // Persist to database
        val sessionId = _chatSessionId
        if (sessionId != null && autoPersistEnabled) {
            scope.launch {
                sessionRepository?.updateUserMetadataField(sessionId, type, value)
            }
        }
    }

    /**
     * Get user metadata field
     */
    fun getUserMetadata(type: String): String? {
        return when (type.lowercase()) {
            "name" -> _userMetadata.value.name
            "email" -> _userMetadata.value.email
            "phone", "mobile" -> _userMetadata.value.phone
            else -> _userMetadata.value.metadata[type] as? String
        }
    }

    // ========== Transcript ==========

    /**
     * Add entry to transcript (with memory limit)
     */
    fun addToTranscript(by: String, message: String) {
        val timestamp = System.currentTimeMillis()
        val list = _transcript.value.toMutableList()
        list.add(TranscriptEntry(by, message, timestamp))

        // FIX 8: Keep transcript limited to MAX_TRANSCRIPT_ENTRIES to prevent memory issues
        if (list.size > MAX_TRANSCRIPT_ENTRIES) {
            _transcript.value = list.takeLast(MAX_TRANSCRIPT_ENTRIES).toMutableList()
        } else {
            _transcript.value = list
        }

        // Persist to database
        persistTranscriptEntry(by, message, timestamp)
    }

    /**
     * Get full transcript for GPT context
     */
    fun getTranscriptForGPT(): List<Map<String, String>> {
        return _transcript.value.map {
            mapOf(
                "role" to when (it.by) {
                    "bot" -> "assistant"
                    "agent" -> "assistant"
                    else -> "user"
                },
                "content" to it.message
            )
        }
    }

    // ========== Record ==========

    /**
     * Push data to record (with auto-merge if same ID exists)
     */
    fun pushToRecord(entry: RecordEntry) {
        val list = _record.value.toMutableList()
        val existingIndex = list.indexOfFirst { it.id == entry.id }

        if (existingIndex != -1) {
            // Merge with existing record
            val existing = list[existingIndex]
            val mergedData = existing.data.toMutableMap()
            mergedData.putAll(entry.data)
            list[existingIndex] = entry.copy(data = mergedData)
        } else {
            list.add(entry)
        }

        _record.value = list

        // Persist to database
        persistRecordEntry(entry)
    }

    /**
     * Get record as JSON-serializable list
     */
    fun getRecordForServer(): List<Map<String, Any?>> {
        return _record.value.map { entry ->
            mutableMapOf<String, Any?>(
                "_id" to entry.id,
                "id" to entry.id,
                "type" to entry.type,
                "time" to entry.time
            ).apply {
                // For user responses, keep flat shape/text format
                if (entry.shape.startsWith("user-")) {
                    put("shape", entry.shape)
                    put("text", entry.text)
                } else {
                    // For bot messages, nest data as sub-object (web widget format)
                    val dataMap = entry.data.toMutableMap()
                    if (entry.text != null) dataMap["text"] = entry.text
                    put("data", dataMap)
                }
            }
        }
    }

    /**
     * Build full response data object for socket emit
     */
    fun buildResponseData(): Map<String, Any?> {
        return mapOf(
            "version" to "v2",
            "chatSessionId" to _chatSessionId,
            "visitorId" to _visitorId,
            "botId" to _botId,
            "chatDate" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date()),
            "deviceInfo" to android.os.Build.MODEL,
            "location" to java.util.TimeZone.getDefault().id,
            "record" to getRecordForServer(),
            "answerVariables" to _answerVariables.value.map {
                mapOf("nodeId" to it.nodeId, "key" to it.key, "value" to it.value)
            },
            "workspaceId" to _workspaceId,
            "channel" to "mobile"
        )
    }

    // ========== Memory Management ==========

    /**
     * Get current memory usage estimate
     */
    fun getMemoryUsageInfo(): MemoryUsageInfo {
        return MemoryUsageInfo(
            messagesInMemory = _messages.value.size,
            transcriptEntries = _transcript.value.size,
            recordEntries = _record.value.size,
            answerVariables = _answerVariables.value.size,
            totalMessageCount = messageRepository?.totalMessageCount ?: _messages.value.size
        )
    }

    /**
     * Trim memory usage (call when app goes to background)
     */
    fun trimMemory() {
        scope.launch {
            // Clear old messages, keep only recent 50
            clearOldMessages(50)

            // Trim transcript
            if (_transcript.value.size > 100) {
                _transcript.value = _transcript.value.takeLast(100).toMutableList()
            }
        }
    }

    /**
     * Handle low memory situation
     */
    fun onLowMemory() {
        scope.launch {
            // Clear message memory cache
            messageRepository?.clearMemoryCache()
            _messages.value = emptyList()

            // Clear transcript to minimum
            if (_transcript.value.size > 20) {
                _transcript.value = _transcript.value.takeLast(20).toMutableList()
            }

            // Update pagination state to allow reload
            _paginationState.value = PaginationState(
                isLoading = false,
                hasMoreMessages = true,
                oldestLoadedIndex = Int.MAX_VALUE,
                totalMessageCount = messageRepository?.totalMessageCount ?: 0
            )
        }
    }

    // ========== Reset ==========

    /**
     * Reset all state for new conversation
     */
    fun reset() {
        // Deactivate current session in database before resetting
        val oldSessionId = _chatSessionId
        if (oldSessionId != null && sessionRepository != null) {
            scope.launch {
                sessionRepository?.deactivateSession(oldSessionId)
            }
        }

        _answerVariables.value = mutableListOf()
        _variables.value = mutableMapOf()
        _userMetadata.value = UserMetadata()
        _transcript.value = mutableListOf()
        _record.value = mutableListOf()
        _messages.value = emptyList()
        _paginationState.value = PaginationState()
        _currentIndex.value = 0
        _steps.value = emptyList()
        _chatSessionId = null
        _visitorId = null
        _botId = null
        _workspaceId = null

        // Clear repository
        scope.launch {
            messageRepository?.clearAll()
            messageRepository = null
        }
    }

    /**
     * Reset state but keep session (for conversation restart)
     */
    fun softReset() {
        _answerVariables.value = mutableListOf()
        _variables.value = mutableMapOf()
        _transcript.value = mutableListOf()
        _record.value = mutableListOf()
        _currentIndex.value = 0
        // Keep messages and session info
    }
}

/**
 * Memory usage information
 */
data class MemoryUsageInfo(
    val messagesInMemory: Int,
    val transcriptEntries: Int,
    val recordEntries: Int,
    val answerVariables: Int,
    val totalMessageCount: Int
)
