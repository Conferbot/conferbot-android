package com.conferbot.sdk.data

import android.content.Context
import android.util.Log
import com.conferbot.sdk.core.state.AnswerVariable
import com.conferbot.sdk.core.state.RecordEntry
import com.conferbot.sdk.core.state.TranscriptEntry
import com.conferbot.sdk.core.state.UserMetadata
import com.conferbot.sdk.data.entities.AnswerVariableEntity
import com.conferbot.sdk.data.entities.ChatSessionEntity
import com.conferbot.sdk.data.entities.MessageEntity
import com.conferbot.sdk.data.entities.RecordEntity
import com.conferbot.sdk.data.entities.TranscriptEntity
import com.conferbot.sdk.data.entities.UserMetadataEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for managing session persistence
 * Provides high-level API for saving and restoring chat state
 */
class SessionRepository(context: Context) {

    companion object {
        private const val TAG = "SessionRepository"

        // Default session expiry: 30 minutes
        const val SESSION_EXPIRY_MS = 30 * 60 * 1000L

        @Volatile
        private var INSTANCE: SessionRepository? = null

        /**
         * Get the singleton repository instance
         */
        fun getInstance(context: Context): SessionRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = SessionRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val database = ConferbotDatabase.getInstance(context)
    private val chatSessionDao = database.chatSessionDao()
    private val messageDao = database.messageDao()
    private val answerVariableDao = database.answerVariableDao()
    private val userMetadataDao = database.userMetadataDao()
    private val transcriptDao = database.transcriptDao()
    private val recordDao = database.recordDao()
    private val gson = Gson()

    // ==================== Session Operations ====================

    /**
     * Save or update a chat session
     */
    suspend fun saveSession(
        sessionId: String,
        visitorId: String,
        botId: String,
        workspaceId: String? = null,
        currentIndex: Int = 0,
        isActive: Boolean = true
    ) = withContext(Dispatchers.IO) {
        try {
            val existingSession = chatSessionDao.getSession(sessionId)
            if (existingSession != null) {
                // Update existing session
                chatSessionDao.update(
                    existingSession.copy(
                        visitorId = visitorId,
                        botId = botId,
                        workspaceId = workspaceId,
                        currentIndex = currentIndex,
                        isActive = isActive,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                // Create new session
                chatSessionDao.insert(
                    ChatSessionEntity(
                        sessionId = sessionId,
                        visitorId = visitorId,
                        botId = botId,
                        workspaceId = workspaceId,
                        currentIndex = currentIndex,
                        isActive = isActive
                    )
                )
            }
            Log.d(TAG, "Session saved: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session: $sessionId", e)
        }
    }

    /**
     * Get a session by ID
     */
    suspend fun getSession(sessionId: String): ChatSessionEntity? = withContext(Dispatchers.IO) {
        chatSessionDao.getSession(sessionId)
    }

    /**
     * Get the latest valid session for a bot
     * Returns null if no valid session exists or session is expired
     */
    suspend fun getLatestValidSession(botId: String): ChatSessionEntity? = withContext(Dispatchers.IO) {
        val session = chatSessionDao.getLatestActiveSession(botId)
        if (session != null && isSessionValid(session)) {
            session
        } else {
            null
        }
    }

    /**
     * Get the latest session for a bot (regardless of validity)
     */
    suspend fun getLatestSession(botId: String): ChatSessionEntity? = withContext(Dispatchers.IO) {
        chatSessionDao.getLatestSession(botId)
    }

    /**
     * Check if a session is still valid (not expired)
     */
    fun isSessionValid(session: ChatSessionEntity): Boolean {
        val currentTime = System.currentTimeMillis()
        val sessionAge = currentTime - session.updatedAt
        return session.isActive && sessionAge < SESSION_EXPIRY_MS
    }

    /**
     * Update session current index
     */
    suspend fun updateCurrentIndex(sessionId: String, index: Int) = withContext(Dispatchers.IO) {
        chatSessionDao.updateCurrentIndex(sessionId, index)
    }

    /**
     * Mark session as inactive
     */
    suspend fun deactivateSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatSessionDao.updateActiveStatus(sessionId, false)
    }

    /**
     * Touch session to update timestamp
     */
    suspend fun touchSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatSessionDao.touch(sessionId)
    }

    /**
     * Delete a session and all associated data
     */
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatSessionDao.delete(sessionId)
        Log.d(TAG, "Session deleted: $sessionId")
    }

    /**
     * Clear old sessions (older than specified time)
     */
    suspend fun clearOldSessions(olderThan: Long = System.currentTimeMillis() - SESSION_EXPIRY_MS) =
        withContext(Dispatchers.IO) {
            val deletedCount = chatSessionDao.getSessionCount("") // Get count before
            chatSessionDao.deleteOldSessions(olderThan)
            Log.d(TAG, "Cleared old sessions (older than ${olderThan}ms)")
        }

    // ==================== Message Operations ====================

    /**
     * Save a message
     */
    suspend fun saveMessage(
        id: String,
        sessionId: String,
        content: String,
        sender: String,
        timestamp: Long = System.currentTimeMillis(),
        nodeId: String? = null,
        nodeType: String? = null,
        metadata: Map<String, Any?>? = null
    ) = withContext(Dispatchers.IO) {
        try {
            messageDao.insert(
                MessageEntity(
                    id = id,
                    sessionId = sessionId,
                    content = content,
                    sender = sender,
                    timestamp = timestamp,
                    nodeId = nodeId,
                    nodeType = nodeType,
                    metadata = metadata?.let { gson.toJson(it) }
                )
            )
            // Touch session to update timestamp
            chatSessionDao.touch(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message: $id", e)
        }
    }

    /**
     * Get all messages for a session
     */
    suspend fun getMessages(sessionId: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        messageDao.getMessages(sessionId)
    }

    /**
     * Get messages as Flow for live updates
     */
    fun getMessagesFlow(sessionId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesFlow(sessionId)
    }

    // ==================== Answer Variable Operations ====================

    /**
     * Save an answer variable
     */
    suspend fun saveAnswerVariable(
        sessionId: String,
        nodeId: String,
        key: String,
        value: Any?
    ) = withContext(Dispatchers.IO) {
        try {
            val valueJson = value?.let { gson.toJson(it) }
            val existing = answerVariableDao.getVariableByNodeId(sessionId, nodeId)

            if (existing != null) {
                answerVariableDao.updateValueByNodeId(sessionId, nodeId, valueJson)
            } else {
                answerVariableDao.insert(
                    AnswerVariableEntity(
                        sessionId = sessionId,
                        nodeId = nodeId,
                        key = key,
                        value = valueJson
                    )
                )
            }
            // Touch session to update timestamp
            chatSessionDao.touch(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save answer variable: $nodeId", e)
        }
    }

    /**
     * Save multiple answer variables
     */
    suspend fun saveAnswerVariables(sessionId: String, variables: List<AnswerVariable>) =
        withContext(Dispatchers.IO) {
            try {
                val entities = variables.map { variable ->
                    AnswerVariableEntity(
                        sessionId = sessionId,
                        nodeId = variable.nodeId,
                        key = variable.key,
                        value = variable.value?.let { gson.toJson(it) }
                    )
                }
                answerVariableDao.insertAll(entities)
                chatSessionDao.touch(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save answer variables", e)
            }
        }

    /**
     * Get all answer variables for a session
     */
    suspend fun getAnswerVariables(sessionId: String): List<AnswerVariable> =
        withContext(Dispatchers.IO) {
            answerVariableDao.getVariables(sessionId).map { entity ->
                AnswerVariable(
                    nodeId = entity.nodeId,
                    key = entity.key,
                    value = entity.value?.let { parseJsonValue(it) }
                )
            }
        }

    /**
     * Get answer variables as Flow for live updates
     */
    fun getAnswerVariablesFlow(sessionId: String): Flow<List<AnswerVariable>> {
        return answerVariableDao.getVariablesFlow(sessionId).map { entities ->
            entities.map { entity ->
                AnswerVariable(
                    nodeId = entity.nodeId,
                    key = entity.key,
                    value = entity.value?.let { parseJsonValue(it) }
                )
            }
        }
    }

    // ==================== User Metadata Operations ====================

    /**
     * Save user metadata
     */
    suspend fun saveUserMetadata(
        sessionId: String,
        name: String? = null,
        email: String? = null,
        phone: String? = null,
        customData: Map<String, Any>? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val customDataJson = customData?.let { gson.toJson(it) }
            userMetadataDao.insert(
                UserMetadataEntity(
                    sessionId = sessionId,
                    name = name,
                    email = email,
                    phone = phone,
                    customData = customDataJson
                )
            )
            chatSessionDao.touch(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user metadata for session: $sessionId", e)
        }
    }

    /**
     * Save user metadata from UserMetadata object
     */
    suspend fun saveUserMetadata(sessionId: String, metadata: UserMetadata) =
        withContext(Dispatchers.IO) {
            saveUserMetadata(
                sessionId = sessionId,
                name = metadata.name,
                email = metadata.email,
                phone = metadata.phone,
                customData = metadata.metadata
            )
        }

    /**
     * Update user metadata field
     */
    suspend fun updateUserMetadataField(sessionId: String, field: String, value: String?) =
        withContext(Dispatchers.IO) {
            try {
                when (field.lowercase()) {
                    "name" -> userMetadataDao.updateName(sessionId, value)
                    "email" -> userMetadataDao.updateEmail(sessionId, value)
                    "phone", "mobile" -> userMetadataDao.updatePhone(sessionId, value)
                    else -> {
                        // For custom fields, update the customData JSON
                        val existing = userMetadataDao.getMetadata(sessionId)
                        if (existing != null) {
                            val customData = existing.customData?.let {
                                gson.fromJson<MutableMap<String, Any>>(
                                    it,
                                    object : TypeToken<MutableMap<String, Any>>() {}.type
                                )
                            } ?: mutableMapOf()

                            if (value != null) {
                                customData[field] = value
                            } else {
                                customData.remove(field)
                            }

                            userMetadataDao.updateCustomData(sessionId, gson.toJson(customData))
                        }
                    }
                }
                chatSessionDao.touch(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update user metadata field: $field", e)
            }
        }

    /**
     * Get user metadata for a session
     */
    suspend fun getUserMetadata(sessionId: String): UserMetadata? = withContext(Dispatchers.IO) {
        userMetadataDao.getMetadata(sessionId)?.let { entity ->
            UserMetadata(
                name = entity.name,
                email = entity.email,
                phone = entity.phone,
                metadata = entity.customData?.let {
                    gson.fromJson(it, object : TypeToken<MutableMap<String, Any>>() {}.type)
                } ?: mutableMapOf()
            )
        }
    }

    /**
     * Get user metadata as Flow for live updates
     */
    fun getUserMetadataFlow(sessionId: String): Flow<UserMetadata?> {
        return userMetadataDao.getMetadataFlow(sessionId).map { entity ->
            entity?.let {
                UserMetadata(
                    name = it.name,
                    email = it.email,
                    phone = it.phone,
                    metadata = it.customData?.let { json ->
                        gson.fromJson(json, object : TypeToken<MutableMap<String, Any>>() {}.type)
                    } ?: mutableMapOf()
                )
            }
        }
    }

    // ==================== Transcript Operations ====================

    /**
     * Save a transcript entry
     */
    suspend fun saveTranscriptEntry(
        sessionId: String,
        by: String,
        message: String,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        try {
            transcriptDao.insert(
                TranscriptEntity(
                    sessionId = sessionId,
                    by = by,
                    message = message,
                    timestamp = timestamp
                )
            )
            chatSessionDao.touch(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save transcript entry", e)
        }
    }

    /**
     * Save multiple transcript entries
     */
    suspend fun saveTranscriptEntries(sessionId: String, entries: List<TranscriptEntry>) =
        withContext(Dispatchers.IO) {
            try {
                val entities = entries.map { entry ->
                    TranscriptEntity(
                        sessionId = sessionId,
                        by = entry.by,
                        message = entry.message,
                        timestamp = entry.timestamp
                    )
                }
                transcriptDao.insertAll(entities)
                chatSessionDao.touch(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save transcript entries", e)
            }
        }

    /**
     * Get transcript for a session
     */
    suspend fun getTranscript(sessionId: String): List<TranscriptEntry> =
        withContext(Dispatchers.IO) {
            transcriptDao.getTranscript(sessionId).map { entity ->
                TranscriptEntry(
                    by = entity.by,
                    message = entity.message,
                    timestamp = entity.timestamp
                )
            }
        }

    /**
     * Get transcript as Flow for live updates
     */
    fun getTranscriptFlow(sessionId: String): Flow<List<TranscriptEntry>> {
        return transcriptDao.getTranscriptFlow(sessionId).map { entities ->
            entities.map { entity ->
                TranscriptEntry(
                    by = entity.by,
                    message = entity.message,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    // ==================== Record Operations ====================

    /**
     * Save a record entry
     */
    suspend fun saveRecordEntry(sessionId: String, entry: RecordEntry) =
        withContext(Dispatchers.IO) {
            try {
                val existing = recordDao.getRecordById(sessionId, entry.id)
                if (existing != null) {
                    // Merge data
                    val existingData = existing.data?.let {
                        gson.fromJson<MutableMap<String, Any?>>(
                            it,
                            object : TypeToken<MutableMap<String, Any?>>() {}.type
                        )
                    } ?: mutableMapOf()
                    existingData.putAll(entry.data)
                    recordDao.updateData(sessionId, entry.id, gson.toJson(existingData))
                } else {
                    recordDao.insert(
                        RecordEntity(
                            sessionId = sessionId,
                            recordId = entry.id,
                            shape = entry.shape,
                            type = entry.type,
                            text = entry.text,
                            time = entry.time,
                            data = if (entry.data.isNotEmpty()) gson.toJson(entry.data) else null
                        )
                    )
                }
                chatSessionDao.touch(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save record entry: ${entry.id}", e)
            }
        }

    /**
     * Save multiple record entries
     */
    suspend fun saveRecordEntries(sessionId: String, entries: List<RecordEntry>) =
        withContext(Dispatchers.IO) {
            entries.forEach { entry ->
                saveRecordEntry(sessionId, entry)
            }
        }

    /**
     * Get records for a session
     */
    suspend fun getRecords(sessionId: String): List<RecordEntry> =
        withContext(Dispatchers.IO) {
            recordDao.getRecords(sessionId).map { entity ->
                RecordEntry(
                    id = entity.recordId,
                    shape = entity.shape,
                    type = entity.type,
                    text = entity.text,
                    time = entity.time,
                    data = entity.data?.let {
                        gson.fromJson(it, object : TypeToken<MutableMap<String, Any?>>() {}.type)
                    } ?: mutableMapOf()
                )
            }
        }

    /**
     * Get records as Flow for live updates
     */
    fun getRecordsFlow(sessionId: String): Flow<List<RecordEntry>> {
        return recordDao.getRecordsFlow(sessionId).map { entities ->
            entities.map { entity ->
                RecordEntry(
                    id = entity.recordId,
                    shape = entity.shape,
                    type = entity.type,
                    text = entity.text,
                    time = entity.time,
                    data = entity.data?.let {
                        gson.fromJson(it, object : TypeToken<MutableMap<String, Any?>>() {}.type)
                    } ?: mutableMapOf()
                )
            }
        }
    }

    // ==================== Full Session Restore ====================

    /**
     * Data class representing a complete restored session
     */
    data class RestoredSession(
        val session: ChatSessionEntity,
        val messages: List<MessageEntity>,
        val answerVariables: List<AnswerVariable>,
        val userMetadata: UserMetadata?,
        val transcript: List<TranscriptEntry>,
        val records: List<RecordEntry>
    )

    /**
     * Restore a complete session with all associated data
     */
    suspend fun restoreFullSession(sessionId: String): RestoredSession? =
        withContext(Dispatchers.IO) {
            val session = chatSessionDao.getSession(sessionId) ?: return@withContext null

            if (!isSessionValid(session)) {
                Log.d(TAG, "Session $sessionId is expired, not restoring")
                return@withContext null
            }

            RestoredSession(
                session = session,
                messages = getMessages(sessionId),
                answerVariables = getAnswerVariables(sessionId),
                userMetadata = getUserMetadata(sessionId),
                transcript = getTranscript(sessionId),
                records = getRecords(sessionId)
            )
        }

    /**
     * Restore the latest valid session for a bot
     */
    suspend fun restoreLatestSession(botId: String): RestoredSession? =
        withContext(Dispatchers.IO) {
            val session = getLatestValidSession(botId) ?: return@withContext null
            restoreFullSession(session.sessionId)
        }

    // ==================== Helper Functions ====================

    /**
     * Parse a JSON string back to its original value
     */
    private fun parseJsonValue(json: String): Any? {
        return try {
            // Try to parse as different types
            when {
                json == "null" -> null
                json.startsWith("\"") && json.endsWith("\"") -> {
                    // String value
                    gson.fromJson(json, String::class.java)
                }
                json.startsWith("[") -> {
                    // Array
                    gson.fromJson<List<Any?>>(json, object : TypeToken<List<Any?>>() {}.type)
                }
                json.startsWith("{") -> {
                    // Object
                    gson.fromJson<Map<String, Any?>>(json, object : TypeToken<Map<String, Any?>>() {}.type)
                }
                json.toBooleanStrictOrNull() != null -> {
                    // Boolean
                    json.toBoolean()
                }
                json.toIntOrNull() != null -> {
                    // Integer
                    json.toInt()
                }
                json.toDoubleOrNull() != null -> {
                    // Double
                    json.toDouble()
                }
                else -> json
            }
        } catch (e: Exception) {
            json
        }
    }
}
