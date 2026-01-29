package com.conferbot.sdk.core.queue

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Callback interface for offline mode events
 */
interface OfflineManagerListener {
    /**
     * Called when online status changes
     */
    fun onOnlineStatusChanged(isOnline: Boolean)

    /**
     * Called when messages are successfully synced after coming back online
     */
    fun onQueueSynced(successCount: Int, failCount: Int)

    /**
     * Called when a message fails permanently
     */
    fun onMessageFailed(message: QueuedMessage)
}

/**
 * Central manager for offline mode functionality.
 * Coordinates NetworkMonitor, OfflineMessageQueue, QueueProcessor, and QueuePersistence.
 */
class OfflineManager(
    private val context: Context,
    private val enabled: Boolean = true
) : QueueProcessorCallback {

    companion object {
        private const val TAG = "OfflineManager"
    }

    // Core components
    private val networkMonitor = NetworkMonitor(context)
    private val messageQueue = OfflineMessageQueue()
    private val persistence = QueuePersistence(context)
    private lateinit var processor: QueueProcessor

    // Coroutine scope for background operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // State flows
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _pendingMessageCount = MutableStateFlow(0)
    val pendingMessageCount: StateFlow<Int> = _pendingMessageCount.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Listener
    private var listener: OfflineManagerListener? = null

    // Message sender callback - set by SocketClient
    private var messageSender: (suspend (QueuedMessage) -> Boolean)? = null

    private var isInitialized = false

    /**
     * Initialize the offline manager
     */
    fun initialize() {
        if (!enabled) {
            Log.d(TAG, "Offline mode is disabled")
            return
        }

        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }

        // Initialize processor with this as callback
        processor = QueueProcessor(messageQueue, this, scope)

        // Load persisted messages
        val persistedMessages = persistence.loadMessages()
        if (persistedMessages.isNotEmpty()) {
            messageQueue.loadFromList(persistedMessages)
            _pendingMessageCount.value = messageQueue.size()
        }

        // Start network monitoring
        networkMonitor.startMonitoring()

        // Observe network status changes
        scope.launch {
            networkMonitor.isOnline.collect { online ->
                handleNetworkChange(online)
            }
        }

        isInitialized = true
        Log.d(TAG, "Offline manager initialized, ${messageQueue.size()} pending messages")
    }

    /**
     * Set the message sender callback
     */
    fun setMessageSender(sender: suspend (QueuedMessage) -> Boolean) {
        messageSender = sender
    }

    /**
     * Set the event listener
     */
    fun setListener(listener: OfflineManagerListener) {
        this.listener = listener
    }

    /**
     * Handle network status change
     */
    private fun handleNetworkChange(online: Boolean) {
        val wasOffline = !_isOnline.value
        _isOnline.value = online

        Log.d(TAG, "Network status changed: online=$online, wasOffline=$wasOffline")

        // Notify listener
        listener?.onOnlineStatusChanged(online)

        // If we just came back online and have pending messages, process them
        if (online && wasOffline && !messageQueue.isEmpty()) {
            Log.d(TAG, "Back online with pending messages, processing queue")
            processQueue()
        }
    }

    /**
     * Queue a message for later delivery
     */
    fun queueMessage(message: QueuedMessage) {
        if (!enabled) {
            Log.d(TAG, "Offline mode disabled, message not queued")
            return
        }

        messageQueue.enqueue(message)
        _pendingMessageCount.value = messageQueue.size()

        // Persist immediately
        saveQueueToStorage()

        Log.d(TAG, "Message queued: ${message.type}, total: ${messageQueue.size()}")
    }

    /**
     * Check if currently online
     */
    fun isCurrentlyOnline(): Boolean = _isOnline.value

    /**
     * Check if offline mode is enabled
     */
    fun isEnabled(): Boolean = enabled

    /**
     * Manually trigger queue processing
     */
    fun processQueue() {
        if (!enabled || messageQueue.isEmpty()) {
            return
        }

        if (!_isOnline.value) {
            Log.d(TAG, "Cannot process queue: offline")
            return
        }

        processor.processQueue()
    }

    /**
     * Save current queue to persistent storage
     */
    private fun saveQueueToStorage() {
        scope.launch {
            try {
                persistence.saveMessages(messageQueue.peekAll())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save queue to storage", e)
            }
        }
    }

    /**
     * Clear all pending messages
     */
    fun clearQueue() {
        messageQueue.clear()
        persistence.clear()
        _pendingMessageCount.value = 0
    }

    /**
     * Clear messages for a specific session
     */
    fun clearSessionQueue(chatSessionId: String) {
        messageQueue.clearSession(chatSessionId)
        persistence.removeMessagesForSession(chatSessionId)
        _pendingMessageCount.value = messageQueue.size()
    }

    /**
     * Get pending message count
     */
    fun getPendingCount(): Int = messageQueue.size()

    /**
     * Check if there are pending messages
     */
    fun hasPendingMessages(): Boolean = !messageQueue.isEmpty()

    // QueueProcessorCallback implementation

    override suspend fun sendMessage(message: QueuedMessage): Boolean {
        val sender = messageSender ?: run {
            Log.e(TAG, "No message sender configured")
            return false
        }

        return try {
            sender(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            false
        }
    }

    override fun onMessageProcessed(message: QueuedMessage) {
        Log.d(TAG, "Message processed successfully: ${message.id}")
        _pendingMessageCount.value = messageQueue.size()
        saveQueueToStorage()
    }

    override fun onMessageFailed(message: QueuedMessage) {
        Log.w(TAG, "Message failed after retries: ${message.id}")
        listener?.onMessageFailed(message)
        _pendingMessageCount.value = messageQueue.size()
        saveQueueToStorage()
    }

    override fun onProcessingStarted(queueSize: Int) {
        Log.d(TAG, "Started processing $queueSize messages")
        _isSyncing.value = true
    }

    override fun onProcessingCompleted(successCount: Int, failCount: Int) {
        Log.d(TAG, "Queue processing completed: $successCount success, $failCount failed")
        _isSyncing.value = false
        listener?.onQueueSynced(successCount, failCount)
        saveQueueToStorage()
    }

    /**
     * Shutdown the offline manager
     */
    fun shutdown() {
        if (!isInitialized) {
            return
        }

        // Save current queue before shutdown
        saveQueueToStorage()

        // Stop monitoring
        networkMonitor.stopMonitoring()

        // Cancel processor
        if (::processor.isInitialized) {
            processor.destroy()
        }

        // Cancel scope
        scope.cancel()

        isInitialized = false
        Log.d(TAG, "Offline manager shutdown")
    }
}
