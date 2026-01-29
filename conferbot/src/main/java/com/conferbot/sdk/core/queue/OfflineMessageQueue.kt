package com.conferbot.sdk.core.queue

import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Represents a message queued for later delivery when offline
 */
data class QueuedMessage(
    val id: String = UUID.randomUUID().toString(),
    val type: MessageType,
    val payload: Map<String, Any>,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val chatSessionId: String
) {
    /**
     * Create a copy with incremented retry count
     */
    fun withIncrementedRetry(): QueuedMessage = copy(retryCount = retryCount + 1)

    /**
     * Check if message has exceeded max retries
     */
    fun hasExceededMaxRetries(maxRetries: Int = MAX_RETRIES): Boolean = retryCount >= maxRetries

    companion object {
        const val MAX_RETRIES = 3
    }
}

/**
 * Types of messages that can be queued
 */
enum class MessageType {
    RESPONSE_RECORD,    // User response/message
    TYPING_STATUS,      // Typing indicator
    JOIN_CHAT_ROOM,     // Join chat room request
    LEAVE_CHAT_ROOM,    // Leave chat room request
    INITIATE_HANDOVER,  // Handover request
    END_CHAT,           // End chat request
    CUSTOM_EVENT        // Custom socket event
}

/**
 * Thread-safe offline message queue for storing messages when device is offline.
 * Messages are stored in memory and can be persisted via QueuePersistence.
 */
class OfflineMessageQueue {
    private val queue = ConcurrentLinkedQueue<QueuedMessage>()

    companion object {
        private const val TAG = "OfflineMessageQueue"
    }

    /**
     * Add a message to the queue
     */
    fun enqueue(message: QueuedMessage) {
        queue.offer(message)
        Log.d(TAG, "Message enqueued: ${message.type}, Queue size: ${queue.size}")
    }

    /**
     * Remove and return all messages from the queue
     */
    fun dequeueAll(): List<QueuedMessage> {
        val messages = mutableListOf<QueuedMessage>()
        while (true) {
            val message = queue.poll() ?: break
            messages.add(message)
        }
        Log.d(TAG, "Dequeued ${messages.size} messages")
        return messages
    }

    /**
     * Peek at all messages without removing them
     */
    fun peekAll(): List<QueuedMessage> {
        return queue.toList()
    }

    /**
     * Remove a specific message by ID
     */
    fun remove(messageId: String): Boolean {
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == messageId) {
                iterator.remove()
                Log.d(TAG, "Message removed: $messageId")
                return true
            }
        }
        return false
    }

    /**
     * Re-enqueue a message (typically after a failed retry)
     */
    fun requeue(message: QueuedMessage) {
        if (!message.hasExceededMaxRetries()) {
            queue.offer(message.withIncrementedRetry())
            Log.d(TAG, "Message requeued: ${message.id}, retry count: ${message.retryCount + 1}")
        } else {
            Log.w(TAG, "Message exceeded max retries, discarding: ${message.id}")
        }
    }

    /**
     * Check if the queue is empty
     */
    fun isEmpty(): Boolean = queue.isEmpty()

    /**
     * Get the current queue size
     */
    fun size(): Int = queue.size

    /**
     * Clear all messages from the queue
     */
    fun clear() {
        queue.clear()
        Log.d(TAG, "Queue cleared")
    }

    /**
     * Get messages for a specific chat session
     */
    fun getMessagesForSession(chatSessionId: String): List<QueuedMessage> {
        return queue.filter { it.chatSessionId == chatSessionId }
    }

    /**
     * Remove all messages for a specific chat session
     */
    fun clearSession(chatSessionId: String) {
        val iterator = queue.iterator()
        var removedCount = 0
        while (iterator.hasNext()) {
            if (iterator.next().chatSessionId == chatSessionId) {
                iterator.remove()
                removedCount++
            }
        }
        Log.d(TAG, "Cleared $removedCount messages for session: $chatSessionId")
    }

    /**
     * Load messages from persistence (called during initialization)
     */
    fun loadFromList(messages: List<QueuedMessage>) {
        messages.forEach { queue.offer(it) }
        Log.d(TAG, "Loaded ${messages.size} messages from persistence")
    }
}
