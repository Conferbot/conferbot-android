package com.conferbot.sdk.core.state

import android.content.Context
import android.util.LruCache
import com.conferbot.sdk.models.RecordItem
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

/**
 * Repository for persistent message storage with pagination support.
 * Stores messages to disk and maintains an LRU cache in memory.
 */
class MessageRepository(
    private val context: Context,
    private val sessionId: String,
    private val maxMemoryMessages: Int = 100,
    private val pageSize: Int = 50
) {
    private val mutex = Mutex()
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    // LRU cache for recently accessed messages
    private val messageCache: LruCache<String, RecordItem> = LruCache(maxMemoryMessages)

    // In-memory list of message IDs in chronological order
    private val messageIds = mutableListOf<String>()

    // Total message count (including those only on disk)
    private var _totalMessageCount = 0
    val totalMessageCount: Int get() = _totalMessageCount

    // Track which pages are loaded in memory
    private val loadedPages = mutableSetOf<Int>()

    private val storageDir: File
        get() = File(context.filesDir, "chat_messages/$sessionId").also {
            if (!it.exists()) it.mkdirs()
        }

    private val indexFile: File
        get() = File(storageDir, "index.json")

    private val messagesFile: File
        get() = File(storageDir, "messages.json")

    /**
     * Initialize repository and load message index from disk
     */
    suspend fun initialize(): List<RecordItem> = mutex.withLock {
        withContext(Dispatchers.IO) {
            loadIndex()
            // Load the most recent page into memory
            loadRecentMessages()
        }
    }

    /**
     * Load message index from disk
     */
    private fun loadIndex() {
        if (indexFile.exists()) {
            try {
                val indexJson = indexFile.readText()
                val indexData = gson.fromJson(indexJson, MessageIndex::class.java)
                messageIds.clear()
                messageIds.addAll(indexData.messageIds)
                _totalMessageCount = indexData.totalCount
            } catch (e: Exception) {
                // Index corrupted, reset
                messageIds.clear()
                _totalMessageCount = 0
            }
        }
    }

    /**
     * Load most recent messages into memory
     */
    private fun loadRecentMessages(): List<RecordItem> {
        if (!messagesFile.exists() || messageIds.isEmpty()) {
            return emptyList()
        }

        try {
            val allMessages = loadAllMessagesFromDisk()
            val startIndex = maxOf(0, allMessages.size - maxMemoryMessages)
            val recentMessages = allMessages.subList(startIndex, allMessages.size)

            // Populate cache with recent messages
            recentMessages.forEach { message ->
                messageCache.put(message.id, message)
            }

            // Mark recent pages as loaded
            val startPage = startIndex / pageSize
            val endPage = (allMessages.size - 1) / pageSize
            for (page in startPage..endPage) {
                loadedPages.add(page)
            }

            return recentMessages
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * Load all messages from disk (internal use)
     */
    private fun loadAllMessagesFromDisk(): List<RecordItem> {
        if (!messagesFile.exists()) return emptyList()

        return try {
            val json = messagesFile.readText()
            val type = object : TypeToken<List<SerializableMessage>>() {}.type
            val serializedMessages: List<SerializableMessage> = gson.fromJson(json, type)
            serializedMessages.mapNotNull { it.toRecordItem() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Add a new message
     */
    suspend fun addMessage(message: RecordItem) = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Add to in-memory structures
            messageIds.add(message.id)
            messageCache.put(message.id, message)
            _totalMessageCount++

            // Persist to disk
            persistMessage(message)
            saveIndex()
        }
    }

    /**
     * Add multiple messages (batch operation)
     */
    suspend fun addMessages(messages: List<RecordItem>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            messages.forEach { message ->
                messageIds.add(message.id)
                messageCache.put(message.id, message)
                _totalMessageCount++
            }

            // Persist all messages
            persistMessages(messages)
            saveIndex()
        }
    }

    /**
     * Persist a single message to disk
     */
    private fun persistMessage(message: RecordItem) {
        val allMessages = loadAllMessagesFromDisk().toMutableList()
        allMessages.add(message)
        saveMessagesToDisk(allMessages)
    }

    /**
     * Persist multiple messages to disk
     */
    private fun persistMessages(messages: List<RecordItem>) {
        val allMessages = loadAllMessagesFromDisk().toMutableList()
        allMessages.addAll(messages)
        saveMessagesToDisk(allMessages)
    }

    /**
     * Save messages to disk
     */
    private fun saveMessagesToDisk(messages: List<RecordItem>) {
        val serializedMessages = messages.map { SerializableMessage.fromRecordItem(it) }
        val json = gson.toJson(serializedMessages)
        messagesFile.writeText(json)
    }

    /**
     * Save index to disk
     */
    private fun saveIndex() {
        val indexData = MessageIndex(messageIds.toList(), _totalMessageCount)
        val json = gson.toJson(indexData)
        indexFile.writeText(json)
    }

    /**
     * Get messages for display (most recent, within memory limit)
     */
    suspend fun getRecentMessages(): List<RecordItem> = mutex.withLock {
        val result = mutableListOf<RecordItem>()
        val startIndex = maxOf(0, messageIds.size - maxMemoryMessages)

        for (i in startIndex until messageIds.size) {
            val id = messageIds[i]
            messageCache.get(id)?.let { result.add(it) }
        }

        result
    }

    /**
     * Load older messages (pagination)
     * @param beforeIndex Load messages before this index
     * @return List of older messages
     */
    suspend fun loadOlderMessages(beforeIndex: Int = -1): List<RecordItem> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val targetIndex = if (beforeIndex < 0) {
                maxOf(0, messageIds.size - maxMemoryMessages)
            } else {
                beforeIndex
            }

            if (targetIndex <= 0) {
                return@withContext emptyList()
            }

            val startIndex = maxOf(0, targetIndex - pageSize)
            val endIndex = targetIndex

            // Calculate page number
            val pageNum = startIndex / pageSize

            // Check if already loaded
            if (loadedPages.contains(pageNum)) {
                return@withContext getMessagesInRange(startIndex, endIndex)
            }

            // Load from disk
            val allMessages = loadAllMessagesFromDisk()
            if (startIndex >= allMessages.size) {
                return@withContext emptyList()
            }

            val actualEnd = minOf(endIndex, allMessages.size)
            val olderMessages = allMessages.subList(startIndex, actualEnd)

            // Add to cache
            olderMessages.forEach { message ->
                messageCache.put(message.id, message)
            }

            loadedPages.add(pageNum)
            olderMessages
        }
    }

    /**
     * Get messages in a specific range from cache
     */
    private fun getMessagesInRange(startIndex: Int, endIndex: Int): List<RecordItem> {
        val result = mutableListOf<RecordItem>()
        val actualEnd = minOf(endIndex, messageIds.size)

        for (i in startIndex until actualEnd) {
            val id = messageIds[i]
            messageCache.get(id)?.let { result.add(it) }
        }

        return result
    }

    /**
     * Check if there are more messages to load
     */
    fun hasMoreMessages(): Boolean {
        val oldestLoadedIndex = loadedPages.minOrNull()?.times(pageSize) ?: messageIds.size
        return oldestLoadedIndex > 0
    }

    /**
     * Get the index of the oldest loaded message
     */
    fun getOldestLoadedIndex(): Int {
        return loadedPages.minOrNull()?.times(pageSize) ?: messageIds.size
    }

    /**
     * Clear old messages from memory (keep only recent)
     */
    suspend fun clearOldMessagesFromMemory(keepLast: Int = 100) = mutex.withLock {
        if (messageIds.size <= keepLast) return@withLock

        val startIndex = messageIds.size - keepLast

        // Remove old messages from cache
        for (i in 0 until startIndex) {
            val id = messageIds[i]
            messageCache.remove(id)
        }

        // Clear loaded pages tracking for old pages
        val firstKeptPage = startIndex / pageSize
        loadedPages.removeAll { it < firstKeptPage }
    }

    /**
     * Clear all messages from memory (for low memory situations)
     * Messages remain on disk
     */
    suspend fun clearMemoryCache() = mutex.withLock {
        messageCache.evictAll()
        loadedPages.clear()
    }

    /**
     * Clear all data (memory and disk)
     */
    suspend fun clearAll() = mutex.withLock {
        withContext(Dispatchers.IO) {
            messageCache.evictAll()
            messageIds.clear()
            loadedPages.clear()
            _totalMessageCount = 0

            // Delete files
            messagesFile.delete()
            indexFile.delete()
        }
    }

    /**
     * Get memory usage estimate in bytes
     */
    fun getMemoryCacheSize(): Int {
        return messageCache.size()
    }

    companion object {
        private val repositories = mutableMapOf<String, MessageRepository>()

        /**
         * Get or create a repository for a session
         */
        fun getInstance(context: Context, sessionId: String): MessageRepository {
            return repositories.getOrPut(sessionId) {
                MessageRepository(context, sessionId)
            }
        }

        /**
         * Clear a specific session's repository
         */
        fun clearSession(sessionId: String) {
            repositories.remove(sessionId)
        }

        /**
         * Clear all repositories
         */
        fun clearAll() {
            repositories.clear()
        }
    }
}

/**
 * Index data structure for message persistence
 */
private data class MessageIndex(
    val messageIds: List<String>,
    val totalCount: Int
)

/**
 * Serializable message wrapper for Gson
 */
private data class SerializableMessage(
    val id: String,
    val type: String,
    val time: Long,
    val text: String?,
    val agentDetailsId: String? = null,
    val agentDetailsName: String? = null,
    val agentDetailsEmail: String? = null,
    val agentDetailsAvatar: String? = null,
    val file: String? = null,
    val url: String? = null,
    val metadata: Map<String, Any>? = null,
    val nodeData: Map<String, Any>? = null
) {
    fun toRecordItem(): RecordItem? {
        val date = Date(time)
        return when (type) {
            "user-message" -> RecordItem.UserMessage(
                id = id,
                time = date,
                text = text ?: "",
                metadata = metadata
            )
            "user-input-response" -> RecordItem.UserInputResponse(
                id = id,
                time = date,
                text = text ?: "",
                metadata = metadata
            )
            "bot-message" -> RecordItem.BotMessage(
                id = id,
                time = date,
                text = text,
                nodeData = nodeData
            )
            "agent-message" -> RecordItem.AgentMessage(
                id = id,
                time = date,
                text = text ?: "",
                agentDetails = com.conferbot.sdk.models.AgentDetails(
                    id = agentDetailsId ?: "",
                    name = agentDetailsName ?: "",
                    email = agentDetailsEmail,
                    avatar = agentDetailsAvatar
                )
            )
            "agent-message-file" -> RecordItem.AgentMessageFile(
                id = id,
                time = date,
                file = file ?: "",
                agentDetails = if (agentDetailsId != null) {
                    com.conferbot.sdk.models.AgentDetails(
                        id = agentDetailsId,
                        name = agentDetailsName ?: "",
                        email = agentDetailsEmail,
                        avatar = agentDetailsAvatar
                    )
                } else null
            )
            "agent-message-audio" -> RecordItem.AgentMessageAudio(
                id = id,
                time = date,
                url = url ?: "",
                agentDetails = com.conferbot.sdk.models.AgentDetails(
                    id = agentDetailsId ?: "",
                    name = agentDetailsName ?: "",
                    email = agentDetailsEmail,
                    avatar = agentDetailsAvatar
                )
            )
            "agent-joined-message" -> RecordItem.AgentJoinedMessage(
                id = id,
                time = date,
                agentDetails = com.conferbot.sdk.models.AgentDetails(
                    id = agentDetailsId ?: "",
                    name = agentDetailsName ?: "",
                    email = agentDetailsEmail,
                    avatar = agentDetailsAvatar
                )
            )
            "system-message" -> RecordItem.SystemMessage(
                id = id,
                time = date,
                text = text ?: ""
            )
            else -> RecordItem.SystemMessage(
                id = id,
                time = date,
                text = text ?: ""
            )
        }
    }

    companion object {
        fun fromRecordItem(item: RecordItem): SerializableMessage {
            return when (item) {
                is RecordItem.UserMessage -> SerializableMessage(
                    id = item.id,
                    type = "user-message",
                    time = item.time.time,
                    text = item.text,
                    metadata = item.metadata
                )
                is RecordItem.UserInputResponse -> SerializableMessage(
                    id = item.id,
                    type = "user-input-response",
                    time = item.time.time,
                    text = item.text,
                    metadata = item.metadata
                )
                is RecordItem.BotMessage -> SerializableMessage(
                    id = item.id,
                    type = "bot-message",
                    time = item.time.time,
                    text = item.text,
                    nodeData = item.nodeData
                )
                is RecordItem.AgentMessage -> SerializableMessage(
                    id = item.id,
                    type = "agent-message",
                    time = item.time.time,
                    text = item.text,
                    agentDetailsId = item.agentDetails.id,
                    agentDetailsName = item.agentDetails.name,
                    agentDetailsEmail = item.agentDetails.email,
                    agentDetailsAvatar = item.agentDetails.avatar
                )
                is RecordItem.AgentMessageFile -> SerializableMessage(
                    id = item.id,
                    type = "agent-message-file",
                    time = item.time.time,
                    text = null,
                    file = item.file,
                    agentDetailsId = item.agentDetails?.id,
                    agentDetailsName = item.agentDetails?.name,
                    agentDetailsEmail = item.agentDetails?.email,
                    agentDetailsAvatar = item.agentDetails?.avatar
                )
                is RecordItem.AgentMessageAudio -> SerializableMessage(
                    id = item.id,
                    type = "agent-message-audio",
                    time = item.time.time,
                    text = null,
                    url = item.url,
                    agentDetailsId = item.agentDetails.id,
                    agentDetailsName = item.agentDetails.name,
                    agentDetailsEmail = item.agentDetails.email,
                    agentDetailsAvatar = item.agentDetails.avatar
                )
                is RecordItem.AgentJoinedMessage -> SerializableMessage(
                    id = item.id,
                    type = "agent-joined-message",
                    time = item.time.time,
                    text = null,
                    agentDetailsId = item.agentDetails.id,
                    agentDetailsName = item.agentDetails.name,
                    agentDetailsEmail = item.agentDetails.email,
                    agentDetailsAvatar = item.agentDetails.avatar
                )
                is RecordItem.SystemMessage -> SerializableMessage(
                    id = item.id,
                    type = "system-message",
                    time = item.time.time,
                    text = item.text
                )
            }
        }
    }
}
