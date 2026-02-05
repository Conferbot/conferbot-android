package com.conferbot.sdk.core.queue

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OfflineManager
 * Tests message queuing when offline, queue persistence, automatic retry on reconnection,
 * and queue processing order
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineManagerTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var offlineManager: OfflineManager
    private lateinit var listener: OfflineManagerListener
    private lateinit var messageSender: (suspend (QueuedMessage) -> Boolean)

    private val testDispatcher = StandardTestDispatcher()
    private var capturedCallback: ConnectivityManager.NetworkCallback? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)
        listener = mockk(relaxed = true)

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { context.applicationContext } returns context

        // Capture the network callback
        every {
            connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>())
        } answers {
            capturedCallback = secondArg()
        }

        // Default to online state
        setupOnlineState()

        // Default successful message sender
        messageSender = { true }

        // Mock SharedPreferences for QueuePersistence
        val sharedPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val editor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.getString(any(), any()) } returns "[]"
        every { sharedPrefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        capturedCallback = null
    }

    // ==================== INITIALIZATION TESTS ====================

    @Test
    fun `initialize starts network monitoring`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)

        // When
        offlineManager.initialize()
        advanceUntilIdle()

        // Then
        verify { connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>()) }
    }

    @Test
    fun `initialize does nothing when disabled`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = false)

        // When
        offlineManager.initialize()
        advanceUntilIdle()

        // Then
        verify(exactly = 0) { connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>()) }
    }

    @Test
    fun `initialize only runs once`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)

        // When
        offlineManager.initialize()
        offlineManager.initialize()
        advanceUntilIdle()

        // Then - Only one callback registration
        verify(exactly = 1) { connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>()) }
    }

    @Test
    fun `isEnabled returns correct value`() {
        // Given
        val enabledManager = OfflineManager(context, enabled = true)
        val disabledManager = OfflineManager(context, enabled = false)

        // Then
        assertThat(enabledManager.isEnabled()).isTrue()
        assertThat(disabledManager.isEnabled()).isFalse()
    }

    // ==================== MESSAGE QUEUING TESTS ====================

    @Test
    fun `queueMessage adds message when enabled`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.initialize()
        advanceUntilIdle()

        val message = createTestMessage("msg-1")

        // When
        offlineManager.queueMessage(message)
        advanceUntilIdle()

        // Then
        assertThat(offlineManager.getPendingCount()).isEqualTo(1)
    }

    @Test
    fun `queueMessage does not add message when disabled`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = false)
        offlineManager.initialize()
        advanceUntilIdle()

        val message = createTestMessage("msg-1")

        // When
        offlineManager.queueMessage(message)
        advanceUntilIdle()

        // Then
        assertThat(offlineManager.getPendingCount()).isEqualTo(0)
    }

    @Test
    fun `queueMessage updates pending message count`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.initialize()
        advanceUntilIdle()

        // When
        offlineManager.queueMessage(createTestMessage("msg-1"))
        offlineManager.queueMessage(createTestMessage("msg-2"))
        offlineManager.queueMessage(createTestMessage("msg-3"))
        advanceUntilIdle()

        // Then
        assertThat(offlineManager.pendingMessageCount.value).isEqualTo(3)
    }

    @Test
    fun `hasPendingMessages returns true when queue has messages`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.initialize()
        offlineManager.queueMessage(createTestMessage("msg-1"))
        advanceUntilIdle()

        // Then
        assertThat(offlineManager.hasPendingMessages()).isTrue()
    }

    @Test
    fun `hasPendingMessages returns false when queue is empty`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.initialize()
        advanceUntilIdle()

        // Then
        assertThat(offlineManager.hasPendingMessages()).isFalse()
    }

    // ==================== ONLINE STATUS TESTS ====================

    @Test
    fun `isOnline reflects network status`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.initialize()
        advanceUntilIdle()

        // Initially online (from setup)
        assertThat(offlineManager.isOnline.value).isTrue()
        assertThat(offlineManager.isCurrentlyOnline()).isTrue()
    }

    @Test
    fun `network status change updates isOnline`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.setListener(listener)
        offlineManager.initialize()
        advanceUntilIdle()

        val network = mockk<Network>()

        // When network becomes unavailable
        setupOfflineState()
        capturedCallback?.onLost(network)
        advanceUntilIdle()

        // Then
        assertThat(offlineManager.isOnline.value).isFalse()
        verify { listener.onOnlineStatusChanged(false) }
    }

    @Test
    fun `listener notified of online status changes`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.setListener(listener)
        offlineManager.initialize()
        advanceUntilIdle()

        val network = mockk<Network>()

        // When
        capturedCallback?.onAvailable(network)
        advanceUntilIdle()

        // Then
        verify { listener.onOnlineStatusChanged(true) }
    }

    // ==================== AUTOMATIC RETRY ON RECONNECTION TESTS ====================

    @Test
    fun `queue is processed when coming back online`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.initialize()
        offlineManager.setMessageSender(messageSender)
        advanceUntilIdle()

        // Start offline
        setupOfflineState()
        capturedCallback?.onLost(mockk())
        advanceUntilIdle()

        // Queue messages while offline
        offlineManager.queueMessage(createTestMessage("msg-1"))
        offlineManager.queueMessage(createTestMessage("msg-2"))
        advanceUntilIdle()

        assertThat(offlineManager.getPendingCount()).isEqualTo(2)

        // When - Come back online
        setupOnlineState()
        capturedCallback?.onAvailable(mockk())
        advanceUntilIdle()

        // Then - Messages should be processed
        assertThat(offlineManager.getPendingCount()).isEqualTo(0)
    }

    @Test
    fun `listener notified when queue synced`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.setListener(listener)
        offlineManager.setMessageSender(messageSender)
        offlineManager.initialize()
        advanceUntilIdle()

        // Start offline and queue messages
        setupOfflineState()
        capturedCallback?.onLost(mockk())
        advanceUntilIdle()

        offlineManager.queueMessage(createTestMessage("msg-1"))
        offlineManager.queueMessage(createTestMessage("msg-2"))
        advanceUntilIdle()

        // When - Come back online
        setupOnlineState()
        capturedCallback?.onAvailable(mockk())
        advanceUntilIdle()

        // Then
        verify { listener.onQueueSynced(any(), any()) }
    }

    // ==================== PROCESS QUEUE TESTS ====================

    @Test
    fun `processQueue does nothing when offline`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        var sendCount = 0
        offlineManager.setMessageSender {
            sendCount++
            true
        }
        offlineManager.initialize()
        advanceUntilIdle()

        // Go offline
        setupOfflineState()
        capturedCallback?.onLost(mockk())
        advanceUntilIdle()

        offlineManager.queueMessage(createTestMessage("msg-1"))
        advanceUntilIdle()

        // When
        offlineManager.processQueue()
        advanceUntilIdle()

        // Then - Message not sent
        assertThat(sendCount).isEqualTo(0)
        assertThat(offlineManager.getPendingCount()).isEqualTo(1)
    }

    @Test
    fun `processQueue does nothing when queue is empty`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        var sendCount = 0
        offlineManager.setMessageSender {
            sendCount++
            true
        }
        offlineManager.initialize()
        advanceUntilIdle()

        // When
        offlineManager.processQueue()
        advanceUntilIdle()

        // Then
        assertThat(sendCount).isEqualTo(0)
    }

    @Test
    fun `processQueue sends messages when online`() = runTest {
        // Given
        val sentMessages = mutableListOf<QueuedMessage>()
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.setMessageSender { message ->
            sentMessages.add(message)
            true
        }
        offlineManager.initialize()
        advanceUntilIdle()

        offlineManager.queueMessage(createTestMessage("msg-1"))
        offlineManager.queueMessage(createTestMessage("msg-2"))
        advanceUntilIdle()

        // When
        offlineManager.processQueue()
        advanceUntilIdle()

        // Then
        assertThat(sentMessages).hasSize(2)
        assertThat(sentMessages.map { it.id }).containsExactly("msg-1", "msg-2").inOrder()
    }

    // ==================== ISYNCING STATE TESTS ====================

    @Test
    fun `isSyncing is true during queue processing`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.setMessageSender { message ->
            // Verify syncing state during processing
            assertThat(offlineManager.isSyncing.value).isTrue()
            true
        }
        offlineManager.initialize()
        advanceUntilIdle()

        offlineManager.queueMessage(createTestMessage("msg-1"))
        advanceUntilIdle()

        // When
        offlineManager.processQueue()
        advanceUntilIdle()

        // Then - Should be false after processing
        assertThat(offlineManager.isSyncing.value).isFalse()
    }

    @Test
    fun `isSyncing StateFlow emits correct values`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.setMessageSender { true }
        offlineManager.initialize()
        advanceUntilIdle()

        offlineManager.queueMessage(createTestMessage("msg-1"))
        advanceUntilIdle()

        // When & Then
        offlineManager.isSyncing.test {
            assertThat(awaitItem()).isFalse() // Initial

            offlineManager.processQueue()

            assertThat(awaitItem()).isTrue() // Processing started

            advanceUntilIdle()

            assertThat(awaitItem()).isFalse() // Processing completed

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== FAILURE HANDLING TESTS ====================

    @Test
    fun `listener notified when message fails`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.setListener(listener)
        offlineManager.setMessageSender { false } // Always fail
        offlineManager.initialize()
        advanceUntilIdle()

        val message = createTestMessage("msg-1")
        offlineManager.queueMessage(message)
        advanceUntilIdle()

        // When
        offlineManager.processQueue()
        advanceUntilIdle()

        // Then
        verify { listener.onMessageFailed(match { it.id == "msg-1" }) }
    }

    @Test
    fun `sendMessage returns false when no sender configured`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        // Don't set message sender
        offlineManager.initialize()
        advanceUntilIdle()

        offlineManager.queueMessage(createTestMessage("msg-1"))
        advanceUntilIdle()

        // When
        offlineManager.processQueue()
        advanceUntilIdle()

        // Then - Message should fail due to no sender
        assertThat(offlineManager.getPendingCount()).isEqualTo(0) // Processed (failed)
    }

    @Test
    fun `exception in message sender is handled`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.setMessageSender { throw RuntimeException("Network error") }
        offlineManager.initialize()
        advanceUntilIdle()

        offlineManager.queueMessage(createTestMessage("msg-1"))
        advanceUntilIdle()

        // When - Should not throw
        offlineManager.processQueue()
        advanceUntilIdle()

        // Then - Message should be marked as failed
        assertThat(offlineManager.getPendingCount()).isEqualTo(0)
    }

    // ==================== CLEAR QUEUE TESTS ====================

    @Test
    fun `clearQueue removes all pending messages`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.initialize()
        advanceUntilIdle()

        offlineManager.queueMessage(createTestMessage("msg-1"))
        offlineManager.queueMessage(createTestMessage("msg-2"))
        offlineManager.queueMessage(createTestMessage("msg-3"))
        advanceUntilIdle()

        assertThat(offlineManager.getPendingCount()).isEqualTo(3)

        // When
        offlineManager.clearQueue()
        advanceUntilIdle()

        // Then
        assertThat(offlineManager.getPendingCount()).isEqualTo(0)
        assertThat(offlineManager.pendingMessageCount.value).isEqualTo(0)
    }

    // ==================== CLEAR SESSION QUEUE TESTS ====================

    @Test
    fun `clearSessionQueue removes only messages for specific session`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.initialize()
        advanceUntilIdle()

        offlineManager.queueMessage(createTestMessage("msg-1", chatSessionId = "session-A"))
        offlineManager.queueMessage(createTestMessage("msg-2", chatSessionId = "session-B"))
        offlineManager.queueMessage(createTestMessage("msg-3", chatSessionId = "session-A"))
        advanceUntilIdle()

        // When
        offlineManager.clearSessionQueue("session-A")
        advanceUntilIdle()

        // Then
        assertThat(offlineManager.getPendingCount()).isEqualTo(1)
    }

    // ==================== SHUTDOWN TESTS ====================

    @Test
    fun `shutdown stops network monitoring`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.initialize()
        advanceUntilIdle()

        // When
        offlineManager.shutdown()
        advanceUntilIdle()

        // Then
        verify { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }

    @Test
    fun `shutdown does nothing when not initialized`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        // Don't initialize

        // When
        offlineManager.shutdown()
        advanceUntilIdle()

        // Then - No exception
        verify(exactly = 0) { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }

    // ==================== STATE FLOW TESTS ====================

    @Test
    fun `pendingMessageCount StateFlow updates correctly`() = runTest {
        // Given
        offlineManager = OfflineManager(context, enabled = true)
        offlineManager.setMessageSender { true }
        offlineManager.initialize()
        advanceUntilIdle()

        // When & Then
        offlineManager.pendingMessageCount.test {
            assertThat(awaitItem()).isEqualTo(0) // Initial

            offlineManager.queueMessage(createTestMessage("msg-1"))
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(1)

            offlineManager.queueMessage(createTestMessage("msg-2"))
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(2)

            offlineManager.processQueue()
            advanceUntilIdle()

            // After processing, count should decrease
            skipItems(1) // Skip intermediate updates
            assertThat(expectMostRecentItem()).isEqualTo(0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== HELPER METHODS ====================

    private fun setupOnlineState() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasTransport(any()) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
    }

    private fun setupOfflineState() {
        every { connectivityManager.activeNetwork } returns null
        every { connectivityManager.getNetworkCapabilities(any()) } returns null
    }

    private fun createTestMessage(
        id: String,
        type: MessageType = MessageType.RESPONSE_RECORD,
        chatSessionId: String = "test-session"
    ): QueuedMessage {
        return QueuedMessage(
            id = id,
            type = type,
            payload = mapOf("message" to "test"),
            chatSessionId = chatSessionId
        )
    }
}
