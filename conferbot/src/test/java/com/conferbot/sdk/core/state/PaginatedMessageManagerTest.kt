package com.conferbot.sdk.core.state

import android.content.Context
import app.cash.turbine.test
import com.conferbot.sdk.models.RecordItem
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PaginatedMessageManager
 * Tests message pagination, memory limits, and cleanup triggers
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaginatedMessageManagerTest {

    private lateinit var context: Context
    private lateinit var manager: PaginatedMessageManager
    private lateinit var mockRepository: MessageRepository

    private val testDispatcher = StandardTestDispatcher()
    private val testSessionId = "test-session-123"
    private val testConfig = PaginationConfig(
        pageSize = 10,
        maxMemoryMessages = 20,
        backgroundMemoryLimit = 10,
        paginationThreshold = 5
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)

        // Clear static instances
        PaginatedMessageManager.clearAll()

        // Setup default repository behavior
        coEvery { mockRepository.initialize() } returns emptyList()
        every { mockRepository.totalMessageCount } returns 0
        every { mockRepository.hasMoreMessages() } returns false
        every { mockRepository.getOldestLoadedIndex() } returns Int.MAX_VALUE
        every { mockRepository.getMemoryCacheSize() } returns 0
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        PaginatedMessageManager.clearAll()
    }

    // ==================== INITIALIZATION TESTS ====================

    @Test
    fun `initialize loads initial messages`() = runTest {
        // Given
        val initialMessages = createTestMessages(5)
        coEvery { mockRepository.initialize() } returns initialMessages
        every { mockRepository.totalMessageCount } returns 5

        manager = createManagerWithMockedRepository()

        // When
        val result = manager.initialize()
        advanceUntilIdle()

        // Then
        assertThat(result).hasSize(5)
        assertThat(manager.messages.value).hasSize(5)
        assertThat(manager.totalMessageCount.value).isEqualTo(5)
    }

    @Test
    fun `initialize sets loading state`() = runTest {
        // Given
        manager = createManagerWithMockedRepository()

        // When & Then
        manager.isLoading.test {
            assertThat(awaitItem()).isFalse() // Initial

            manager.initialize()

            assertThat(awaitItem()).isTrue() // Loading started
            advanceUntilIdle()
            assertThat(awaitItem()).isFalse() // Loading completed

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initialize handles errors gracefully`() = runTest {
        // Given
        coEvery { mockRepository.initialize() } throws RuntimeException("Database error")
        manager = createManagerWithMockedRepository()

        // When
        val result = manager.initialize()
        advanceUntilIdle()

        // Then
        assertThat(result).isEmpty()
        assertThat(manager.error.value).contains("Failed to load messages")
    }

    @Test
    fun `initialize updates hasMoreMessages`() = runTest {
        // Given
        coEvery { mockRepository.initialize() } returns createTestMessages(10)
        every { mockRepository.hasMoreMessages() } returns true
        every { mockRepository.totalMessageCount } returns 50

        manager = createManagerWithMockedRepository()

        // When
        manager.initialize()
        advanceUntilIdle()

        // Then
        assertThat(manager.hasMoreMessages.value).isTrue()
    }

    // ==================== ADD MESSAGE TESTS ====================

    @Test
    fun `addMessage adds single message`() = runTest {
        // Given
        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        val newMessage = createTestMessage("new-msg")

        // When
        manager.addMessage(newMessage)
        advanceUntilIdle()

        // Then
        assertThat(manager.messages.value).hasSize(1)
        assertThat(manager.messages.value.last().id).isEqualTo("new-msg")
    }

    @Test
    fun `addMessage trims when exceeding maxMemoryMessages`() = runTest {
        // Given
        val config = PaginationConfig(maxMemoryMessages = 5)
        manager = createManagerWithMockedRepository(config)
        manager.initialize()
        advanceUntilIdle()

        // Add initial messages
        repeat(5) {
            manager.addMessage(createTestMessage("msg-$it"))
            advanceUntilIdle()
        }
        assertThat(manager.messages.value).hasSize(5)

        // When - Add one more
        manager.addMessage(createTestMessage("msg-new"))
        advanceUntilIdle()

        // Then - Should trim to maxMemoryMessages
        assertThat(manager.messages.value.size).isAtMost(5)
        assertThat(manager.hasMoreMessages.value).isTrue()
    }

    @Test
    fun `addMessage handles errors`() = runTest {
        // Given
        manager = createManagerWithMockedRepository()
        coEvery { mockRepository.addMessage(any()) } throws RuntimeException("Insert error")
        manager.initialize()
        advanceUntilIdle()

        // When
        manager.addMessage(createTestMessage("msg-1"))
        advanceUntilIdle()

        // Then
        assertThat(manager.error.value).contains("Failed to add message")
    }

    @Test
    fun `addMessages adds multiple messages`() = runTest {
        // Given
        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        val newMessages = createTestMessages(5)

        // When
        manager.addMessages(newMessages)
        advanceUntilIdle()

        // Then
        assertThat(manager.messages.value).hasSize(5)
    }

    @Test
    fun `addMessages trims when exceeding maxMemoryMessages`() = runTest {
        // Given
        val config = PaginationConfig(maxMemoryMessages = 10)
        manager = createManagerWithMockedRepository(config)
        manager.initialize()
        advanceUntilIdle()

        // When - Add more than max
        manager.addMessages(createTestMessages(15))
        advanceUntilIdle()

        // Then
        assertThat(manager.messages.value.size).isAtMost(10)
        assertThat(manager.hasMoreMessages.value).isTrue()
    }

    // ==================== LOAD MORE MESSAGES TESTS ====================

    @Test
    fun `loadMoreMessages loads older messages`() = runTest {
        // Given
        val initialMessages = createTestMessages(10, startId = 10)
        val olderMessages = createTestMessages(10, startId = 0)

        coEvery { mockRepository.initialize() } returns initialMessages
        coEvery { mockRepository.loadOlderMessages(any()) } returns olderMessages
        every { mockRepository.hasMoreMessages() } returns true

        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        // When
        val result = manager.loadMoreMessages()
        advanceUntilIdle()

        // Then
        assertThat(result).hasSize(10)
        assertThat(manager.messages.value).hasSize(20)
    }

    @Test
    fun `loadMoreMessages sets isLoadingMore state`() = runTest {
        // Given
        coEvery { mockRepository.loadOlderMessages(any()) } coAnswers {
            delay(100)
            createTestMessages(5)
        }
        every { mockRepository.hasMoreMessages() } returns true

        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        // When & Then
        manager.isLoadingMore.test {
            assertThat(awaitItem()).isFalse() // Initial

            manager.loadMoreMessages()

            assertThat(awaitItem()).isTrue() // Loading
            advanceUntilIdle()
            assertThat(awaitItem()).isFalse() // Done

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadMoreMessages does nothing when already loading`() = runTest {
        // Given
        coEvery { mockRepository.loadOlderMessages(any()) } coAnswers {
            delay(1000)
            createTestMessages(5)
        }
        every { mockRepository.hasMoreMessages() } returns true

        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        // When - Call twice
        manager.loadMoreMessages()
        advanceTimeBy(100)
        val result = manager.loadMoreMessages() // Should return immediately
        advanceUntilIdle()

        // Then - Second call returns empty
        assertThat(result).isEmpty()
    }

    @Test
    fun `loadMoreMessages does nothing when no more messages`() = runTest {
        // Given
        every { mockRepository.hasMoreMessages() } returns false

        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        // When
        val result = manager.loadMoreMessages()
        advanceUntilIdle()

        // Then
        assertThat(result).isEmpty()
        coVerify(exactly = 0) { mockRepository.loadOlderMessages(any()) }
    }

    @Test
    fun `loadMoreMessages handles errors`() = runTest {
        // Given
        coEvery { mockRepository.loadOlderMessages(any()) } throws RuntimeException("Load error")
        every { mockRepository.hasMoreMessages() } returns true

        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        // When
        val result = manager.loadMoreMessages()
        advanceUntilIdle()

        // Then
        assertThat(result).isEmpty()
        assertThat(manager.error.value).contains("Failed to load more messages")
    }

    // ==================== LOAD MORE MESSAGES FLOW TESTS ====================

    @Test
    fun `loadMoreMessagesFlow emits Loading then Success`() = runTest {
        // Given
        val olderMessages = createTestMessages(5)
        coEvery { mockRepository.loadOlderMessages(any()) } returns olderMessages
        every { mockRepository.hasMoreMessages() } returns true

        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        // When & Then
        manager.loadMoreMessagesFlow().test {
            assertThat(awaitItem()).isEqualTo(LoadMoreResult.Loading)
            advanceUntilIdle()

            val result = awaitItem()
            assertThat(result).isInstanceOf(LoadMoreResult.Success::class.java)
            assertThat((result as LoadMoreResult.Success).messages).hasSize(5)

            awaitComplete()
        }
    }

    @Test
    fun `loadMoreMessagesFlow emits NoMoreMessages when done`() = runTest {
        // Given
        every { mockRepository.hasMoreMessages() } returns false

        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        // When & Then
        manager.loadMoreMessagesFlow().test {
            assertThat(awaitItem()).isEqualTo(LoadMoreResult.Loading)
            assertThat(awaitItem()).isEqualTo(LoadMoreResult.NoMoreMessages)
            awaitComplete()
        }
    }

    // ==================== CLEAR OLD MESSAGES TESTS ====================

    @Test
    fun `clearOldMessages keeps only recent messages`() = runTest {
        // Given
        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        // Add many messages
        manager.addMessages(createTestMessages(50))
        advanceUntilIdle()

        // When
        manager.clearOldMessages(keepLast = 10)
        advanceUntilIdle()

        // Then
        assertThat(manager.messages.value.size).isAtMost(10)
    }

    @Test
    fun `clearOldMessages updates hasMoreMessages`() = runTest {
        // Given
        every { mockRepository.hasMoreMessages() } returns true

        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        manager.addMessages(createTestMessages(20))
        advanceUntilIdle()

        // When
        manager.clearOldMessages(keepLast = 5)
        advanceUntilIdle()

        // Then
        assertThat(manager.hasMoreMessages.value).isTrue()
    }

    // ==================== CLEAR MEMORY CACHE TESTS ====================

    @Test
    fun `clearMemoryCache empties messages`() = runTest {
        // Given
        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        manager.addMessages(createTestMessages(10))
        advanceUntilIdle()

        // When
        manager.clearMemoryCache()
        advanceUntilIdle()

        // Then
        assertThat(manager.messages.value).isEmpty()
        assertThat(manager.hasMoreMessages.value).isTrue()
    }

    // ==================== RELOAD TESTS ====================

    @Test
    fun `reload reinitializes manager`() = runTest {
        // Given
        var initializeCallCount = 0
        coEvery { mockRepository.initialize() } answers {
            initializeCallCount++
            createTestMessages(5)
        }

        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        // When
        manager.reload()
        advanceUntilIdle()

        // Then
        assertThat(initializeCallCount).isEqualTo(2)
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun `clearError clears error state`() = runTest {
        // Given
        coEvery { mockRepository.initialize() } throws RuntimeException("Error")
        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        assertThat(manager.error.value).isNotNull()

        // When
        manager.clearError()

        // Then
        assertThat(manager.error.value).isNull()
    }

    // ==================== MEMORY USAGE TESTS ====================

    @Test
    fun `getMemoryUsage returns correct values`() = runTest {
        // Given
        every { mockRepository.totalMessageCount } returns 100
        every { mockRepository.getMemoryCacheSize() } returns 50

        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        manager.addMessages(createTestMessages(20))
        advanceUntilIdle()

        // When
        val usage = manager.getMemoryUsage()

        // Then
        assertThat(usage.messagesInMemory).isEqualTo(20)
        assertThat(usage.cacheSize).isEqualTo(50)
    }

    // ==================== TRIM MEMORY TESTS ====================

    @Test
    fun `trimMemory reduces messages to backgroundMemoryLimit`() = runTest {
        // Given
        val config = PaginationConfig(
            maxMemoryMessages = 50,
            backgroundMemoryLimit = 10
        )
        manager = createManagerWithMockedRepository(config)
        manager.initialize()
        advanceUntilIdle()

        manager.addMessages(createTestMessages(30))
        advanceUntilIdle()

        // When
        manager.trimMemory()
        advanceUntilIdle()

        // Then
        assertThat(manager.messages.value.size).isAtMost(10)
    }

    // ==================== LOW MEMORY TESTS ====================

    @Test
    fun `onLowMemory clears memory cache`() = runTest {
        // Given
        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        manager.addMessages(createTestMessages(20))
        advanceUntilIdle()

        // When
        manager.onLowMemory()
        advanceUntilIdle()

        // Then
        assertThat(manager.messages.value).isEmpty()
    }

    // ==================== DELETE ALL TESTS ====================

    @Test
    fun `deleteAll clears all messages and resets state`() = runTest {
        // Given
        every { mockRepository.totalMessageCount } returns 50

        manager = createManagerWithMockedRepository()
        manager.initialize()
        advanceUntilIdle()

        manager.addMessages(createTestMessages(20))
        advanceUntilIdle()

        // When
        manager.deleteAll()
        advanceUntilIdle()

        // Then
        assertThat(manager.messages.value).isEmpty()
        assertThat(manager.totalMessageCount.value).isEqualTo(0)
        assertThat(manager.hasMoreMessages.value).isFalse()
        coVerify { mockRepository.clearAll() }
    }

    // ==================== COMPANION OBJECT TESTS ====================

    @Test
    fun `getInstance returns same instance for same session`() {
        // Given & When
        val instance1 = PaginatedMessageManager.getInstance(context, "session-1")
        val instance2 = PaginatedMessageManager.getInstance(context, "session-1")

        // Then
        assertThat(instance1).isSameInstanceAs(instance2)
    }

    @Test
    fun `getInstance returns different instances for different sessions`() {
        // Given & When
        val instance1 = PaginatedMessageManager.getInstance(context, "session-1")
        val instance2 = PaginatedMessageManager.getInstance(context, "session-2")

        // Then
        assertThat(instance1).isNotSameInstanceAs(instance2)
    }

    @Test
    fun `clearSession removes specific session manager`() {
        // Given
        val instance1 = PaginatedMessageManager.getInstance(context, "session-1")

        // When
        PaginatedMessageManager.clearSession("session-1")
        val instance2 = PaginatedMessageManager.getInstance(context, "session-1")

        // Then - Should be a new instance
        assertThat(instance1).isNotSameInstanceAs(instance2)
    }

    @Test
    fun `clearAll removes all managers`() {
        // Given
        val instance1 = PaginatedMessageManager.getInstance(context, "session-1")
        val instance2 = PaginatedMessageManager.getInstance(context, "session-2")

        // When
        PaginatedMessageManager.clearAll()
        val newInstance1 = PaginatedMessageManager.getInstance(context, "session-1")
        val newInstance2 = PaginatedMessageManager.getInstance(context, "session-2")

        // Then
        assertThat(instance1).isNotSameInstanceAs(newInstance1)
        assertThat(instance2).isNotSameInstanceAs(newInstance2)
    }

    // ==================== PAGINATION CONFIG TESTS ====================

    @Test
    fun `PaginationConfig has correct defaults`() {
        // When
        val config = PaginationConfig()

        // Then
        assertThat(config.pageSize).isEqualTo(50)
        assertThat(config.maxMemoryMessages).isEqualTo(100)
        assertThat(config.backgroundMemoryLimit).isEqualTo(50)
        assertThat(config.paginationThreshold).isEqualTo(10)
    }

    // ==================== LOAD MORE RESULT TESTS ====================

    @Test
    fun `LoadMoreResult sealed class has all types`() {
        // Then
        assertThat(LoadMoreResult.Loading).isInstanceOf(LoadMoreResult::class.java)
        assertThat(LoadMoreResult.NoMoreMessages).isInstanceOf(LoadMoreResult::class.java)
        assertThat(LoadMoreResult.Success(emptyList())).isInstanceOf(LoadMoreResult::class.java)
        assertThat(LoadMoreResult.Error("error")).isInstanceOf(LoadMoreResult::class.java)
    }

    // ==================== MEMORY USAGE DATA CLASS TESTS ====================

    @Test
    fun `MemoryUsage data class stores values correctly`() {
        // When
        val usage = MemoryUsage(
            messagesInMemory = 50,
            totalMessages = 100,
            cacheSize = 75
        )

        // Then
        assertThat(usage.messagesInMemory).isEqualTo(50)
        assertThat(usage.totalMessages).isEqualTo(100)
        assertThat(usage.cacheSize).isEqualTo(75)
    }

    // ==================== HELPER METHODS ====================

    private fun createManagerWithMockedRepository(
        config: PaginationConfig = testConfig
    ): PaginatedMessageManager {
        // Create manager that uses our mocked repository
        return PaginatedMessageManager(context, testSessionId, config)
    }

    private fun createTestMessage(id: String): RecordItem {
        return mockk<RecordItem>(relaxed = true).also {
            every { it.id } returns id
        }
    }

    private fun createTestMessages(count: Int, startId: Int = 0): List<RecordItem> {
        return (startId until startId + count).map { createTestMessage("msg-$it") }
    }
}
