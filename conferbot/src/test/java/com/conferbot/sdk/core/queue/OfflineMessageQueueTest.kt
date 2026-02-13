package com.conferbot.sdk.core.queue

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OfflineMessageQueue
 * Tests message queuing, dequeuing, persistence, and queue management
 */
class OfflineMessageQueueTest {

    private lateinit var queue: OfflineMessageQueue

    @Before
    fun setUp() {
        queue = OfflineMessageQueue()
    }

    // ==================== BASIC QUEUE OPERATIONS ====================

    @Test
    fun `enqueue adds message to queue`() {
        // Given
        val message = createTestMessage("msg-1")

        // When
        queue.enqueue(message)

        // Then
        assertThat(queue.size()).isEqualTo(1)
        assertThat(queue.isEmpty()).isFalse()
    }

    @Test
    fun `multiple enqueues increase queue size`() {
        // Given & When
        queue.enqueue(createTestMessage("msg-1"))
        queue.enqueue(createTestMessage("msg-2"))
        queue.enqueue(createTestMessage("msg-3"))

        // Then
        assertThat(queue.size()).isEqualTo(3)
    }

    @Test
    fun `isEmpty returns true for empty queue`() {
        // Then
        assertThat(queue.isEmpty()).isTrue()
        assertThat(queue.size()).isEqualTo(0)
    }

    // ==================== DEQUEUE OPERATIONS ====================

    @Test
    fun `dequeueAll removes all messages from queue`() {
        // Given
        queue.enqueue(createTestMessage("msg-1"))
        queue.enqueue(createTestMessage("msg-2"))
        queue.enqueue(createTestMessage("msg-3"))

        // When
        val messages = queue.dequeueAll()

        // Then
        assertThat(messages).hasSize(3)
        assertThat(queue.isEmpty()).isTrue()
    }

    @Test
    fun `dequeueAll returns messages in FIFO order`() {
        // Given
        val msg1 = createTestMessage("msg-1")
        val msg2 = createTestMessage("msg-2")
        val msg3 = createTestMessage("msg-3")

        queue.enqueue(msg1)
        queue.enqueue(msg2)
        queue.enqueue(msg3)

        // When
        val messages = queue.dequeueAll()

        // Then
        assertThat(messages[0].id).isEqualTo("msg-1")
        assertThat(messages[1].id).isEqualTo("msg-2")
        assertThat(messages[2].id).isEqualTo("msg-3")
    }

    @Test
    fun `dequeueAll on empty queue returns empty list`() {
        // When
        val messages = queue.dequeueAll()

        // Then
        assertThat(messages).isEmpty()
    }

    // ==================== PEEK OPERATIONS ====================

    @Test
    fun `peekAll returns all messages without removing`() {
        // Given
        queue.enqueue(createTestMessage("msg-1"))
        queue.enqueue(createTestMessage("msg-2"))

        // When
        val messages = queue.peekAll()

        // Then
        assertThat(messages).hasSize(2)
        assertThat(queue.size()).isEqualTo(2) // Queue unchanged
    }

    @Test
    fun `peekAll returns messages in FIFO order`() {
        // Given
        queue.enqueue(createTestMessage("msg-1"))
        queue.enqueue(createTestMessage("msg-2"))
        queue.enqueue(createTestMessage("msg-3"))

        // When
        val messages = queue.peekAll()

        // Then
        assertThat(messages[0].id).isEqualTo("msg-1")
        assertThat(messages[1].id).isEqualTo("msg-2")
        assertThat(messages[2].id).isEqualTo("msg-3")
    }

    // ==================== REMOVE OPERATIONS ====================

    @Test
    fun `remove removes specific message by ID`() {
        // Given
        queue.enqueue(createTestMessage("msg-1"))
        queue.enqueue(createTestMessage("msg-2"))
        queue.enqueue(createTestMessage("msg-3"))

        // When
        val removed = queue.remove("msg-2")

        // Then
        assertThat(removed).isTrue()
        assertThat(queue.size()).isEqualTo(2)
        val remaining = queue.peekAll()
        assertThat(remaining.map { it.id }).containsExactly("msg-1", "msg-3")
    }

    @Test
    fun `remove returns false for non-existent message`() {
        // Given
        queue.enqueue(createTestMessage("msg-1"))

        // When
        val removed = queue.remove("non-existent")

        // Then
        assertThat(removed).isFalse()
        assertThat(queue.size()).isEqualTo(1)
    }

    // ==================== REQUEUE OPERATIONS ====================

    @Test
    fun `requeue adds message back with incremented retry count`() {
        // Given
        val message = createTestMessage("msg-1", retryCount = 0)
        queue.enqueue(message)
        val dequeued = queue.dequeueAll().first()

        // When
        queue.requeue(dequeued)

        // Then
        assertThat(queue.size()).isEqualTo(1)
        val requeued = queue.peekAll().first()
        assertThat(requeued.retryCount).isEqualTo(1)
    }

    @Test
    fun `requeue does not add message that exceeded max retries`() {
        // Given
        val message = createTestMessage("msg-1", retryCount = QueuedMessage.MAX_RETRIES)

        // When
        queue.requeue(message)

        // Then
        assertThat(queue.isEmpty()).isTrue()
    }

    @Test
    fun `requeue allows messages below max retries`() {
        // Given
        val message = createTestMessage("msg-1", retryCount = QueuedMessage.MAX_RETRIES - 1)

        // When
        queue.requeue(message)

        // Then
        assertThat(queue.size()).isEqualTo(1)
    }

    // ==================== CLEAR OPERATIONS ====================

    @Test
    fun `clear removes all messages`() {
        // Given
        queue.enqueue(createTestMessage("msg-1"))
        queue.enqueue(createTestMessage("msg-2"))
        queue.enqueue(createTestMessage("msg-3"))

        // When
        queue.clear()

        // Then
        assertThat(queue.isEmpty()).isTrue()
        assertThat(queue.size()).isEqualTo(0)
    }

    // ==================== SESSION FILTERING ====================

    @Test
    fun `getMessagesForSession returns messages for specific session`() {
        // Given
        queue.enqueue(createTestMessage("msg-1", chatSessionId = "session-A"))
        queue.enqueue(createTestMessage("msg-2", chatSessionId = "session-B"))
        queue.enqueue(createTestMessage("msg-3", chatSessionId = "session-A"))
        queue.enqueue(createTestMessage("msg-4", chatSessionId = "session-B"))

        // When
        val sessionAMessages = queue.getMessagesForSession("session-A")

        // Then
        assertThat(sessionAMessages).hasSize(2)
        assertThat(sessionAMessages.map { it.id }).containsExactly("msg-1", "msg-3")
    }

    @Test
    fun `getMessagesForSession returns empty list for non-existent session`() {
        // Given
        queue.enqueue(createTestMessage("msg-1", chatSessionId = "session-A"))

        // When
        val messages = queue.getMessagesForSession("non-existent")

        // Then
        assertThat(messages).isEmpty()
    }

    @Test
    fun `clearSession removes only messages for specific session`() {
        // Given
        queue.enqueue(createTestMessage("msg-1", chatSessionId = "session-A"))
        queue.enqueue(createTestMessage("msg-2", chatSessionId = "session-B"))
        queue.enqueue(createTestMessage("msg-3", chatSessionId = "session-A"))

        // When
        queue.clearSession("session-A")

        // Then
        assertThat(queue.size()).isEqualTo(1)
        val remaining = queue.peekAll()
        assertThat(remaining.first().chatSessionId).isEqualTo("session-B")
    }

    @Test
    fun `clearSession does nothing for non-existent session`() {
        // Given
        queue.enqueue(createTestMessage("msg-1", chatSessionId = "session-A"))

        // When
        queue.clearSession("non-existent")

        // Then
        assertThat(queue.size()).isEqualTo(1)
    }

    // ==================== LOAD FROM LIST ====================

    @Test
    fun `loadFromList populates queue from list`() {
        // Given
        val messages = listOf(
            createTestMessage("msg-1"),
            createTestMessage("msg-2"),
            createTestMessage("msg-3")
        )

        // When
        queue.loadFromList(messages)

        // Then
        assertThat(queue.size()).isEqualTo(3)
    }

    @Test
    fun `loadFromList preserves message order`() {
        // Given
        val messages = listOf(
            createTestMessage("msg-1"),
            createTestMessage("msg-2"),
            createTestMessage("msg-3")
        )

        // When
        queue.loadFromList(messages)

        // Then
        val queuedMessages = queue.peekAll()
        assertThat(queuedMessages[0].id).isEqualTo("msg-1")
        assertThat(queuedMessages[1].id).isEqualTo("msg-2")
        assertThat(queuedMessages[2].id).isEqualTo("msg-3")
    }

    @Test
    fun `loadFromList adds to existing queue`() {
        // Given
        queue.enqueue(createTestMessage("existing-1"))
        val newMessages = listOf(
            createTestMessage("new-1"),
            createTestMessage("new-2")
        )

        // When
        queue.loadFromList(newMessages)

        // Then
        assertThat(queue.size()).isEqualTo(3)
    }

    // ==================== QUEUED MESSAGE TESTS ====================

    @Test
    fun `QueuedMessage withIncrementedRetry returns new instance with incremented count`() {
        // Given
        val original = createTestMessage("msg-1", retryCount = 0)

        // When
        val incremented = original.withIncrementedRetry()

        // Then
        assertThat(incremented.retryCount).isEqualTo(1)
        assertThat(incremented.id).isEqualTo(original.id)
        assertThat(original.retryCount).isEqualTo(0) // Original unchanged
    }

    @Test
    fun `QueuedMessage hasExceededMaxRetries returns false below limit`() {
        // Given
        val message = createTestMessage("msg-1", retryCount = 0)

        // Then
        assertThat(message.hasExceededMaxRetries()).isFalse()
    }

    @Test
    fun `QueuedMessage hasExceededMaxRetries returns true at limit`() {
        // Given
        val message = createTestMessage("msg-1", retryCount = QueuedMessage.MAX_RETRIES)

        // Then
        assertThat(message.hasExceededMaxRetries()).isTrue()
    }

    @Test
    fun `QueuedMessage hasExceededMaxRetries returns true above limit`() {
        // Given
        val message = createTestMessage("msg-1", retryCount = QueuedMessage.MAX_RETRIES + 1)

        // Then
        assertThat(message.hasExceededMaxRetries()).isTrue()
    }

    @Test
    fun `QueuedMessage hasExceededMaxRetries works with custom limit`() {
        // Given
        val message = createTestMessage("msg-1", retryCount = 5)

        // Then
        assertThat(message.hasExceededMaxRetries(maxRetries = 10)).isFalse()
        assertThat(message.hasExceededMaxRetries(maxRetries = 5)).isTrue()
    }

    // ==================== CONCURRENT ACCESS TESTS ====================

    @Test
    fun `queue handles concurrent enqueues`() {
        // Given & When
        val threads = (1..10).map { threadNum ->
            Thread {
                repeat(100) { i ->
                    queue.enqueue(createTestMessage("thread-$threadNum-msg-$i"))
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Then
        assertThat(queue.size()).isEqualTo(1000)
    }

    // ==================== MESSAGE TYPE TESTS ====================

    @Test
    fun `queue stores messages of all types`() {
        // Given & When
        MessageType.values().forEach { type ->
            queue.enqueue(
                QueuedMessage(
                    id = "msg-${type.name}",
                    type = type,
                    payload = mapOf("test" to true),
                    chatSessionId = "session-1"
                )
            )
        }

        // Then
        assertThat(queue.size()).isEqualTo(MessageType.values().size)
        val messages = queue.peekAll()
        val types = messages.map { it.type }.toSet()
        assertThat(types).containsExactlyElementsIn(MessageType.values().toList())
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
