package com.conferbot.sdk.core.queue

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min
import kotlin.math.pow

/**
 * Callback interface for message processing
 */
interface QueueProcessorCallback {
    /**
     * Called when a message should be sent
     * @return true if message was sent successfully, false otherwise
     */
    suspend fun sendMessage(message: QueuedMessage): Boolean

    /**
     * Called when a message is successfully processed
     */
    fun onMessageProcessed(message: QueuedMessage)

    /**
     * Called when a message fails after all retries
     */
    fun onMessageFailed(message: QueuedMessage)

    /**
     * Called when queue processing starts
     */
    fun onProcessingStarted(queueSize: Int)

    /**
     * Called when queue processing completes
     */
    fun onProcessingCompleted(successCount: Int, failCount: Int)
}

/**
 * Processes queued messages when network becomes available.
 * Implements exponential backoff retry logic.
 */
class QueueProcessor(
    private val messageQueue: OfflineMessageQueue,
    private val callback: QueueProcessorCallback,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "QueueProcessor"
        private const val BASE_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 30000L
        private const val MAX_RETRIES = 3
    }

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private var processingJob: Job? = null

    /**
     * Process all queued messages.
     * Messages are processed sequentially to maintain order.
     */
    fun processQueue() {
        if (_isProcessing.value) {
            Log.d(TAG, "Already processing queue")
            return
        }

        if (messageQueue.isEmpty()) {
            Log.d(TAG, "Queue is empty, nothing to process")
            return
        }

        processingJob = scope.launch {
            _isProcessing.value = true
            val messages = messageQueue.dequeueAll()
            _pendingCount.value = messages.size

            Log.d(TAG, "Processing ${messages.size} queued messages")
            callback.onProcessingStarted(messages.size)

            var successCount = 0
            var failCount = 0

            for (message in messages) {
                try {
                    val success = processMessage(message)
                    if (success) {
                        successCount++
                        callback.onMessageProcessed(message)
                    } else {
                        failCount++
                        callback.onMessageFailed(message)
                    }
                } catch (e: CancellationException) {
                    // Re-queue remaining messages on cancellation
                    Log.d(TAG, "Processing cancelled, re-queueing message")
                    messageQueue.enqueue(message)
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error processing message", e)
                    failCount++
                    callback.onMessageFailed(message)
                }

                _pendingCount.value = _pendingCount.value - 1
            }

            Log.d(TAG, "Queue processing completed: $successCount success, $failCount failed")
            callback.onProcessingCompleted(successCount, failCount)

            _isProcessing.value = false
            _pendingCount.value = 0
        }
    }

    /**
     * Process a single message with retry logic
     */
    private suspend fun processMessage(message: QueuedMessage): Boolean {
        var currentMessage = message
        var attempt = 0

        while (attempt <= MAX_RETRIES) {
            try {
                Log.d(TAG, "Attempting to send message ${message.id}, attempt ${attempt + 1}")

                val success = callback.sendMessage(currentMessage)
                if (success) {
                    Log.d(TAG, "Message sent successfully: ${message.id}")
                    return true
                }

                // Increment retry count
                currentMessage = currentMessage.withIncrementedRetry()
                attempt++

                if (attempt <= MAX_RETRIES) {
                    val delay = calculateBackoffDelay(attempt)
                    Log.d(TAG, "Message send failed, retrying in ${delay}ms")
                    delay(delay)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message, attempt ${attempt + 1}", e)
                currentMessage = currentMessage.withIncrementedRetry()
                attempt++

                if (attempt <= MAX_RETRIES) {
                    val delay = calculateBackoffDelay(attempt)
                    Log.d(TAG, "Retrying in ${delay}ms after error")
                    delay(delay)
                }
            }
        }

        Log.w(TAG, "Message failed after $MAX_RETRIES retries: ${message.id}")
        return false
    }

    /**
     * Calculate exponential backoff delay
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = BASE_DELAY_MS * 2.0.pow(attempt.toDouble()).toLong()
        return min(exponentialDelay, MAX_DELAY_MS)
    }

    /**
     * Cancel ongoing processing
     */
    fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        _isProcessing.value = false
    }

    /**
     * Stop the processor and cleanup resources
     */
    fun destroy() {
        cancelProcessing()
        scope.cancel()
    }

    /**
     * Process a single message immediately (for high-priority messages)
     */
    suspend fun processSingleMessage(message: QueuedMessage): Boolean {
        return processMessage(message)
    }

    /**
     * Check if there are pending messages
     */
    fun hasPendingMessages(): Boolean = !messageQueue.isEmpty()

    /**
     * Get count of pending messages
     */
    fun getPendingCount(): Int = messageQueue.size()
}
