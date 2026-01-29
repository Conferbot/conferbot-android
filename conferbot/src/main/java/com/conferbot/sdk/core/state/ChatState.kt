package com.conferbot.sdk.core.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * Central state manager for the chat conversation
 * Manages all state following the web widget's architecture
 */
object ChatState {

    // Answer variables - stores all user responses
    private val _answerVariables = MutableStateFlow<MutableList<AnswerVariable>>(mutableListOf())
    val answerVariables: StateFlow<List<AnswerVariable>> = _answerVariables.asStateFlow()

    // Variables - temporary calculation storage
    private val _variables = MutableStateFlow<MutableMap<String, Any>>(mutableMapOf())
    val variables: StateFlow<Map<String, Any>> = _variables.asStateFlow()

    // User metadata
    private val _userMetadata = MutableStateFlow(UserMetadata())
    val userMetadata: StateFlow<UserMetadata> = _userMetadata.asStateFlow()

    // Transcript - conversation history
    private val _transcript = MutableStateFlow<MutableList<TranscriptEntry>>(mutableListOf())
    val transcript: StateFlow<List<TranscriptEntry>> = _transcript.asStateFlow()

    // Record - full conversation record for server sync
    private val _record = MutableStateFlow<MutableList<RecordEntry>>(mutableListOf())
    val record: StateFlow<List<RecordEntry>> = _record.asStateFlow()

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
    }

    /**
     * Set specific index (for jumping)
     */
    fun setCurrentIndex(index: Int) {
        _currentIndex.value = index
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
    }

    /**
     * Update or create answer variable by key
     */
    fun setAnswerVariableByKey(key: String, value: Any?) {
        val list = _answerVariables.value.toMutableList()
        val variable = list.find { it.key == key }
        if (variable != null) {
            variable.value = value
        } else {
            list.add(AnswerVariable("column_mapped_$key", key, value))
        }
        _answerVariables.value = list
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
     * Add entry to transcript
     */
    fun addToTranscript(by: String, message: String) {
        val list = _transcript.value.toMutableList()
        list.add(TranscriptEntry(by, message))
        _transcript.value = list
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
    }

    /**
     * Get record as JSON-serializable list
     */
    fun getRecordForServer(): List<Map<String, Any?>> {
        return _record.value.map { entry ->
            mutableMapOf<String, Any?>(
                "id" to entry.id,
                "shape" to entry.shape,
                "type" to entry.type,
                "text" to entry.text,
                "time" to entry.time
            ).apply {
                putAll(entry.data)
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
            "workspaceId" to _workspaceId
        )
    }

    // ========== Reset ==========

    /**
     * Reset all state for new conversation
     */
    fun reset() {
        _answerVariables.value = mutableListOf()
        _variables.value = mutableMapOf()
        _userMetadata.value = UserMetadata()
        _transcript.value = mutableListOf()
        _record.value = mutableListOf()
        _currentIndex.value = 0
        _steps.value = emptyList()
        _chatSessionId = null
        _visitorId = null
        _botId = null
        _workspaceId = null
    }
}
