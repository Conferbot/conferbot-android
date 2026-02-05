package com.conferbot.sdk.core.queue

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for QueueProcessor
 * Tests message processing, retry logic with exponential backoff, and failure handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueProcessorTest {

    private lateinit var messageQueue: OfflineMessageQueue
    private lateinit var callback: QueueProcessorCallback
    private lateinit var processor: QueueProcessor
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        messageQueue = OfflineMessageQueue()
        callback = mockk(relaxed = true)
        processor = QueueProcessor(messageQueue, callback, testScope)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        processor.destroy()
    }

    // ==================== BASIC PROCESSING TESTS ====================

    @Test
    fun `processQueue does nothing when queue is empty`() = testScope.runTest {
        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then
        verify(exactly = 0) { callback.onProcessingStarted(any()) }
    }

    @Test
    fun `processQueue processes all messages in order`() = testScope.runTest {
        // Given
        val msg1 = createTestMessage("msg-1")
        val msg2 = createTestMessage("msg-2")
        val msg3 = createTestMessage("msg-3")
        messageQueue.enqueue(msg1)
        messageQueue.enqueue(msg2)
        messageQueue.enqueue(msg3)

        coEvery { callback.sendMessage(any()) } returns true

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then
        coVerifyOrder {
            callback.onProcessingStarted(3)
            callback.sendMessage(match { it.id == "msg-1" })
            callback.onMessageProcessed(match { it.id == "msg-1" })
            callback.sendMessage(match { it.id == "msg-2" })
            callback.onMessageProcessed(match { it.id == "msg-2" })
            callback.sendMessage(match { it.id == "msg-3" })
            callback.onMessageProcessed(match { it.id == "msg-3" })
            callback.onProcessingCompleted(3, 0)
        }
    }

    @Test
    fun `processQueue calls onProcessingStarted with queue size`() = testScope.runTest {
        // Given
        messageQueue.enqueue(createTestMessage("msg-1"))
        messageQueue.enqueue(createTestMessage("msg-2"))
        coEvery { callback.sendMessage(any()) } returns true

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then
        verify { callback.onProcessingStarted(2) }
    }

    @Test
    fun `processQueue calls onProcessingCompleted with success and fail counts`() = testScope.runTest {
        // Given
        messageQueue.enqueue(createTestMessage("msg-1"))
        messageQueue.enqueue(createTestMessage("msg-2"))
        messageQueue.enqueue(createTestMessage("msg-3"))

        coEvery { callback.sendMessage(match { it.id == "msg-1" }) } returns true
        coEvery { callback.sendMessage(match { it.id == "msg-2" }) } returns false
        coEvery { callback.sendMessage(match { it.id == "msg-3" }) } returns true

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then
        verify { callback.onProcessingCompleted(2, 1) }
    }

    // ==================== SUCCESS HANDLING TESTS ====================

    @Test
    fun `successful message processing calls onMessageProcessed`() = testScope.runTest {
        // Given
        val message = createTestMessage("msg-1")
        messageQueue.enqueue(message)
        coEvery { callback.sendMessage(any()) } returns true

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then
        verify { callback.onMessageProcessed(match { it.id == "msg-1" }) }
    }

    @Test
    fun `successful processing empties the queue`() = testScope.runTest {
        // Given
        messageQueue.enqueue(createTestMessage("msg-1"))
        messageQueue.enqueue(createTestMessage("msg-2"))
        coEvery { callback.sendMessage(any()) } returns true

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then
        assertThat(messageQueue.isEmpty()).isTrue()
    }

    // ==================== RETRY LOGIC TESTS ====================

    @Test
    fun `failed message is retried up to max retries`() = testScope.runTest {
        // Given
        val message = createTestMessage("msg-1")
        messageQueue.enqueue(message)
        coEvery { callback.sendMessage(any()) } returns false

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then - Should be called 4 times (1 initial + 3 retries)
        coVerify(exactly = 4) { callback.sendMessage(any()) }
    }

    @Test
    fun `successful retry stops further retries`() = testScope.runTest {
        // Given
        val message = createTestMessage("msg-1")
        messageQueue.enqueue(message)

        var attemptCount = 0
        coEvery { callback.sendMessage(any()) } answers {
            attemptCount++
            attemptCount >= 2 // Succeed on second attempt
        }

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then
        coVerify(exactly = 2) { callback.sendMessage(any()) }
        verify { callback.onMessageProcessed(any()) }
    }

    @Test
    fun `retry count is incremented between retries`() = testScope.runTest {
        // Given
        val message = createTestMessage("msg-1", retryCount = 0)
        messageQueue.enqueue(message)

        val retryCounts = mutableListOf<Int>()
        coEvery { callback.sendMessage(any()) } answers {
            val msg = firstArg<QueuedMessage>()
            retryCounts.add(msg.retryCount)
            false
        }

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then - Retry counts: 0, 1, 2, 3 (original + 3 retries)
        assertThat(retryCounts).containsExactly(0, 1, 2, 3).inOrder()
    }

    // ==================== EXPONENTIAL BACKOFF TESTS ====================

    @Test
    fun `exponential backoff delays increase between retries`() = testScope.runTest {
        // Given
        val message = createTestMessage("msg-1")
        messageQueue.enqueue(message)

        var lastCallTime = 0L
        val delays = mutableListOf<Long>()

        coEvery { callback.sendMessage(any()) } answers {
            val currentTime = testScheduler.currentTime
            if (lastCallTime > 0) {
                delays.add(currentTime - lastCallTime)
            }
            lastCallTime = currentTime
            false
        }

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then - Delays should increase (exponential backoff: 2000, 4000, 8000 approx)
        assertThat(delays).hasSize(3)
        assertThat(delays[1]).isGreaterThan(delays[0])
        assertThat(delays[2]).isGreaterThan(delays[1])
    }

    // ==================== FAILURE HANDLING TESTS ====================

    @Test
    fun `onMessageFailed called after all retries exhausted`() = testScope.runTest {
        // Given
        val message = createTestMessage("msg-1")
        messageQueue.enqueue(message)
        coEvery { callback.sendMessage(any()) } returns false

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then
        verify { callback.onMessageFailed(match { it.id == "msg-1" }) }
    }

    @Test
    fun `exception during send triggers retry`() = testScope.runTest {
        // Given
        val message = createTestMessage("msg-1")
        messageQueue.enqueue(message)

        var callCount = 0
        coEvery { callback.sendMessage(any()) } answers {
            callCount++
            if (callCount < 3) {
                throw RuntimeException("Network error")
            }
            true
        }

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then - Eventually succeeds
        verify { callback.onMessageProcessed(any()) }
    }

    @Test
    fun `repeated exceptions cause failure after max retries`() = testScope.runTest {
        // Given
        val message = createTestMessage("msg-1")
        messageQueue.enqueue(message)
        coEvery { callback.sendMessage(any()) } throws RuntimeException("Persistent error")

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then
        verify { callback.onMessageFailed(any()) }
    }

    // ==================== STATE FLOW TESTS ====================

    @Test
    fun `isProcessing is true during processing`() = testScope.runTest {
        // Given
        messageQueue.enqueue(createTestMessage("msg-1"))
        coEvery { callback.sendMessage(any()) } coAnswers {
            // Verify state during processing
            assertThat(processor.isProcessing.value).isTrue()
            true
        }

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then
        assertThat(processor.isProcessing.value).isFalse()
    }

    @Test
    fun `isProcessing flow emits correct values`() = testScope.runTest {
        // Given
        messageQueue.enqueue(createTestMessage("msg-1"))
        coEvery { callback.sendMessage(any()) } returns true

        // When & Then
        processor.isProcessing.test {
            assertThat(awaitItem()).isFalse() // Initial

            processor.processQueue()

            assertThat(awaitItem()).isTrue() // Processing started

            advanceUntilIdle()

            assertThat(awaitItem()).isFalse() // Processing completed
        }
    }

    @Test
    fun `pendingCount decreases as messages are processed`() = testScope.runTest {
        // Given
        messageQueue.enqueue(createTestMessage("msg-1"))
        messageQueue.enqueue(createTestMessage("msg-2"))
        messageQueue.enqueue(createTestMessage("msg-3"))
        coEvery { callback.sendMessage(any()) } returns true

        // When & Then
        processor.pendingCount.test {
            assertThat(awaitItem()).isEqualTo(0) // Initial

            processor.processQueue()

            assertThat(awaitItem()).isEqualTo(3) // Processing started
            assertThat(awaitItem()).isEqualTo(2) // First message done
            assertThat(awaitItem()).isEqualTo(1) // Second message done
            assertThat(awaitItem()).isEqualTo(0) // All done (final update)

            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== CONCURRENT PROCESSING TESTS ====================

    @Test
    fun `processQueue ignores second call while already processing`() = testScope.runTest {
        // Given
        messageQueue.enqueue(createTestMessage("msg-1"))
        messageQueue.enqueue(createTestMessage("msg-2"))
        coEvery { callback.sendMessage(any()) } returns true

        // When
        processor.processQueue()
        processor.processQueue() // Second call while first is running
        advanceUntilIdle()

        // Then - Only one batch processed
        verify(exactly = 1) { callback.onProcessingStarted(2) }
    }

    // ==================== CANCEL PROCESSING TESTS ====================

    @Test
    fun `cancelProcessing stops ongoing processing`() = testScope.runTest {
        // Given
        repeat(10) {
            messageQueue.enqueue(createTestMessage("msg-$it"))
        }

        var processedCount = 0
        coEvery { callback.sendMessage(any()) } coAnswers {
            processedCount++
            if (processedCount == 3) {
                processor.cancelProcessing()
            }
            true
        }

        // When
        processor.processQueue()
        advanceUntilIdle()

        // Then - Processing was stopped early
        assertThat(processor.isProcessing.value).isFalse()
    }

    @Test
    fun `cancelProcessing sets isProcessing to false`() = testScope.runTest {
        // Given
        messageQueue.enqueue(createTestMessage("msg-1"))
        coEvery { callback.sendMessage(any()) } coAnswers {
            delay(1000)
            true
        }

        // When
        processor.processQueue()
        advanceTimeBy(100) // Start processing but don't finish
        processor.cancelProcessing()

        // Then
        assertThat(processor.isProcessing.value).isFalse()
    }

    // ==================== SINGLE MESSAGE PROCESSING TESTS ====================

    @Test
    fun `processSingleMessage returns true on success`() = testScope.runTest {
        // Given
        val message = createTestMessage("msg-1")
        coEvery { callback.sendMessage(any()) } returns true

        // When
        val result = processor.processSingleMessage(message)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `processSingleMessage returns false after max retries`() = testScope.runTest {
        // Given
        val message = createTestMessage("msg-1")
        coEvery { callback.sendMessage(any()) } returns false

        // When
        val result = processor.processSingleMessage(message)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `processSingleMessage retries on failure`() = testScope.runTest {
        // Given
        val message = createTestMessage("msg-1")
        var attemptCount = 0
        coEvery { callback.sendMessage(any()) } answers {
            attemptCount++
            attemptCount >= 2
        }

        // When
        val result = processor.processSingleMessage(message)

        // Then
        assertThat(result).isTrue()
        assertThat(attemptCount).isEqualTo(2)
    }

    // ==================== HELPER METHODS ====================

    @Test
    fun `hasPendingMessages returns true when queue has messages`() {
        // Given
        messageQueue.enqueue(createTestMessage("msg-1"))

        // Then
        assertThat(processor.hasPendingMessages()).isTrue()
    }

    @Test
    fun `hasPendingMessages returns false when queue is empty`() {
        // Then
        assertThat(processor.hasPendingMessages()).isFalse()
    }

    @Test
    fun `getPendingCount returns correct count`() {
        // Given
        messageQueue.enqueue(createTestMessage("msg-1"))
        messageQueue.enqueue(createTestMessage("msg-2"))
        messageQueue.enqueue(createTestMessage("msg-3"))

        // Then
        assertThat(processor.getPendingCount()).isEqualTo(3)
    }

    // ==================== DESTROY TESTS ====================

    @Test
    fun `destroy cancels processing and scope`() = testScope.runTest {
        // Given
        messageQueue.enqueue(createTestMessage("msg-1"))
        coEvery { callback.sendMessage(any()) } coAnswers {
            delay(10000)
            true
        }

        // When
        processor.processQueue()
        advanceTimeBy(100)
        processor.destroy()

        // Then
        assertThat(processor.isProcessing.value).isFalse()
    }

    // ==================== HELPER METHODS ====================

    private fun createTestMessage(
        id: String,
        type: MessageType = MessageType.RESPONSE_RECORD,
        chatSessionId: String = "test-session",
        retryCount: Int = 0
    ): QueuedMessage {
        return QueuedMessage(
            id = id,
            type = type,
            payload = mapOf("message" to "test"),
            chatSessionId = chatSessionId,
            retryCount = retryCount
        )
    }
}
