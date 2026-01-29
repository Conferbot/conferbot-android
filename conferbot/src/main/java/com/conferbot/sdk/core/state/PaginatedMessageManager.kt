package com.conferbot.sdk.core.state

import android.content.Context
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
import kotlinx.coroutines.withContext

/**
 * Manager class for handling paginated message loading and memory management.
 * This class provides a clean API for UI components to load and display messages
 * with automatic pagination and memory optimization.
 */
class PaginatedMessageManager(
    private val context: Context,
    private val sessionId: String,
    private val config: PaginationConfig = PaginationConfig()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository = MessageRepository(
        context = context,
        sessionId = sessionId,
        maxMemoryMessages = config.maxMemoryMessages,
        pageSize = config.pageSize
    )

    // Current messages visible to UI
    private val _messages = MutableStateFlow<List<RecordItem>>(emptyList())
    val messages: StateFlow<List<RecordItem>> = _messages.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Loading more (pagination) state
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // Has more messages to load
    private val _hasMoreMessages = MutableStateFlow(true)
    val hasMoreMessages: StateFlow<Boolean> = _hasMoreMessages.asStateFlow()

    // Total message count
    private val _totalMessageCount = MutableStateFlow(0)
    val totalMessageCount: StateFlow<Int> = _totalMessageCount.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Track the oldest loaded message index for pagination
    private var oldestLoadedIndex = Int.MAX_VALUE

    /**
     * Initialize and load initial messages
     */
    suspend fun initialize(): List<RecordItem> {
        _isLoading.value = true
        _error.value = null

        return try {
            val initialMessages = repository.initialize()
            _messages.value = initialMessages
            _totalMessageCount.value = repository.totalMessageCount
            _hasMoreMessages.value = repository.hasMoreMessages()
            oldestLoadedIndex = repository.getOldestLoadedIndex()
            _isLoading.value = false
            initialMessages
        } catch (e: Exception) {
            _error.value = "Failed to load messages: ${e.message}"
            _isLoading.value = false
            emptyList()
        }
    }

    /**
     * Add a new message
     */
    suspend fun addMessage(message: RecordItem) {
        withContext(Dispatchers.Main) {
            try {
                repository.addMessage(message)

                val currentMessages = _messages.value.toMutableList()
                currentMessages.add(message)

                // Trim if needed
                if (currentMessages.size > config.maxMemoryMessages) {
                    _messages.value = currentMessages.takeLast(config.maxMemoryMessages)
                    _hasMoreMessages.value = true
                } else {
                    _messages.value = currentMessages
                }

                _totalMessageCount.value = repository.totalMessageCount
            } catch (e: Exception) {
                _error.value = "Failed to add message: ${e.message}"
            }
        }
    }

    /**
     * Add multiple messages
     */
    suspend fun addMessages(newMessages: List<RecordItem>) {
        withContext(Dispatchers.Main) {
            try {
                repository.addMessages(newMessages)

                val currentMessages = _messages.value.toMutableList()
                currentMessages.addAll(newMessages)

                // Trim if needed
                if (currentMessages.size > config.maxMemoryMessages) {
                    _messages.value = currentMessages.takeLast(config.maxMemoryMessages)
                    _hasMoreMessages.value = true
                } else {
                    _messages.value = currentMessages
                }

                _totalMessageCount.value = repository.totalMessageCount
            } catch (e: Exception) {
                _error.value = "Failed to add messages: ${e.message}"
            }
        }
    }

    /**
     * Load more (older) messages
     * Returns the newly loaded messages
     */
    suspend fun loadMoreMessages(): List<RecordItem> {
        if (_isLoadingMore.value || !_hasMoreMessages.value) {
            return emptyList()
        }

        _isLoadingMore.value = true
        _error.value = null

        return try {
            val olderMessages = repository.loadOlderMessages(oldestLoadedIndex)

            if (olderMessages.isNotEmpty()) {
                val currentMessages = _messages.value.toMutableList()
                currentMessages.addAll(0, olderMessages)

                // Trim from end if needed (keep older messages visible during pagination)
                if (currentMessages.size > config.maxMemoryMessages * 2) {
                    _messages.value = currentMessages.take(config.maxMemoryMessages * 2)
                } else {
                    _messages.value = currentMessages
                }

                oldestLoadedIndex = repository.getOldestLoadedIndex()
            }

            _hasMoreMessages.value = repository.hasMoreMessages()
            _isLoadingMore.value = false
            olderMessages
        } catch (e: Exception) {
            _error.value = "Failed to load more messages: ${e.message}"
            _isLoadingMore.value = false
            emptyList()
        }
    }

    /**
     * Load more messages as a Flow (for reactive UI)
     */
    fun loadMoreMessagesFlow(): Flow<LoadMoreResult> = flow {
        emit(LoadMoreResult.Loading)

        if (!_hasMoreMessages.value) {
            emit(LoadMoreResult.NoMoreMessages)
            return@flow
        }

        val olderMessages = loadMoreMessages()

        if (olderMessages.isEmpty() && _hasMoreMessages.value) {
            emit(LoadMoreResult.Error("Failed to load messages"))
        } else if (olderMessages.isEmpty()) {
            emit(LoadMoreResult.NoMoreMessages)
        } else {
            emit(LoadMoreResult.Success(olderMessages))
        }
    }

    /**
     * Clear old messages from memory (keep only recent)
     */
    suspend fun clearOldMessages(keepLast: Int = config.maxMemoryMessages) {
        repository.clearOldMessagesFromMemory(keepLast)

        val currentMessages = _messages.value
        if (currentMessages.size > keepLast) {
            _messages.value = currentMessages.takeLast(keepLast)
        }

        _hasMoreMessages.value = repository.hasMoreMessages()
        oldestLoadedIndex = repository.getOldestLoadedIndex()
    }

    /**
     * Clear all messages from memory (for low memory situations)
     */
    suspend fun clearMemoryCache() {
        repository.clearMemoryCache()
        _messages.value = emptyList()
        _hasMoreMessages.value = true
        oldestLoadedIndex = Int.MAX_VALUE
    }

    /**
     * Reload messages after memory clear
     */
    suspend fun reload() {
        initialize()
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Get current memory usage
     */
    fun getMemoryUsage(): MemoryUsage {
        return MemoryUsage(
            messagesInMemory = _messages.value.size,
            totalMessages = _totalMessageCount.value,
            cacheSize = repository.getMemoryCacheSize()
        )
    }

    /**
     * Trim memory (call when app goes to background)
     */
    suspend fun trimMemory() {
        clearOldMessages(config.backgroundMemoryLimit)
    }

    /**
     * Handle low memory callback
     */
    suspend fun onLowMemory() {
        clearMemoryCache()
    }

    /**
     * Delete all messages (including from disk)
     */
    suspend fun deleteAll() {
        repository.clearAll()
        _messages.value = emptyList()
        _totalMessageCount.value = 0
        _hasMoreMessages.value = false
    }

    companion object {
        private val instances = mutableMapOf<String, PaginatedMessageManager>()

        /**
         * Get or create a manager for a session
         */
        fun getInstance(
            context: Context,
            sessionId: String,
            config: PaginationConfig = PaginationConfig()
        ): PaginatedMessageManager {
            return instances.getOrPut(sessionId) {
                PaginatedMessageManager(context, sessionId, config)
            }
        }

        /**
         * Clear a specific session's manager
         */
        fun clearSession(sessionId: String) {
            instances.remove(sessionId)
        }

        /**
         * Clear all managers
         */
        fun clearAll() {
            instances.clear()
        }
    }
}

/**
 * Configuration for pagination behavior
 */
data class PaginationConfig(
    val pageSize: Int = 50,
    val maxMemoryMessages: Int = 100,
    val backgroundMemoryLimit: Int = 50,
    val paginationThreshold: Int = 10  // Load more when within this many items from top
)

/**
 * Result type for load more operation
 */
sealed class LoadMoreResult {
    object Loading : LoadMoreResult()
    object NoMoreMessages : LoadMoreResult()
    data class Success(val messages: List<RecordItem>) : LoadMoreResult()
    data class Error(val message: String) : LoadMoreResult()
}

/**
 * Memory usage statistics
 */
data class MemoryUsage(
    val messagesInMemory: Int,
    val totalMessages: Int,
    val cacheSize: Int
)
