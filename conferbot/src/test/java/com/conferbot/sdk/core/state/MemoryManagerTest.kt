package com.conferbot.sdk.core.state

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MemoryManager
 * Tests memory limits, cleanup triggers, and state management
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryManagerTest {

    private lateinit var context: Context
    private lateinit var activityManager: ActivityManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        activityManager = mockk(relaxed = true)

        every { context.applicationContext } returns context
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager

        // Setup default memory info
        val memoryInfo = ActivityManager.MemoryInfo()
        memoryInfo.availMem = 1_000_000_000L // 1GB
        memoryInfo.totalMem = 4_000_000_000L // 4GB
        memoryInfo.lowMemory = false
        memoryInfo.threshold = 500_000_000L // 500MB

        every { activityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.availMem = memoryInfo.availMem
            info.totalMem = memoryInfo.totalMem
            info.lowMemory = memoryInfo.lowMemory
            info.threshold = memoryInfo.threshold
        }

        // Mock ChatState
        mockkObject(ChatState)
        every { ChatState.trimMemory() } just Runs
        every { ChatState.clearOldMessages(any()) } just Runs
        every { ChatState.onLowMemory() } just Runs
        every { ChatState.getMemoryUsageInfo() } returns MemoryUsageInfo(
            answerVariables = 10,
            transcriptEntries = 50,
            recordEntries = 20,
            tempVariables = 5,
            totalSteps = 100
        )

        // Mock PaginatedMessageManager
        mockkObject(PaginatedMessageManager)
        every { PaginatedMessageManager.clearAll() } just Runs

        // Mock MessageRepository
        mockkObject(MessageRepository)
        every { MessageRepository.clearAll() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ChatState)
        unmockkObject(PaginatedMessageManager)
        unmockkObject(MessageRepository)
    }

    // ==================== INITIALIZATION TESTS ====================

    @Test
    fun `initialize sets application context and config`() {
        // Given
        val config = MemoryConfig(
            memoryWarningThreshold = 80,
            lowMemoryMessageLimit = 50,
            criticalMemoryMessageLimit = 20
        )

        // When
        MemoryManager.initialize(context, config)

        // Then - No exception, config is stored internally
        // We can verify by calling getMemoryInfo which uses the context
        val memoryInfo = MemoryManager.getMemoryInfo()
        assertThat(memoryInfo).isNotNull()
    }

    @Test
    fun `initial memory state is NORMAL`() {
        // Then
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.NORMAL)
    }

    // ==================== TRIM MEMORY TESTS ====================

    @Test
    fun `handleTrimMemory TRIM_MEMORY_UI_HIDDEN sets BACKGROUND state`() = runTest {
        // Given
        MemoryManager.initialize(context)

        // When
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        advanceUntilIdle()

        // Then
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.BACKGROUND)
        verify { ChatState.trimMemory() }
    }

    @Test
    fun `handleTrimMemory TRIM_MEMORY_RUNNING_LOW sets LOW state`() = runTest {
        // Given
        MemoryManager.initialize(context)

        // When
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        advanceUntilIdle()

        // Then
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.LOW)
        verify { ChatState.clearOldMessages(any()) }
    }

    @Test
    fun `handleTrimMemory TRIM_MEMORY_RUNNING_CRITICAL sets CRITICAL state`() = runTest {
        // Given
        MemoryManager.initialize(context)

        // When
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        advanceUntilIdle()

        // Then
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.CRITICAL)
        verify { ChatState.onLowMemory() }
        verify { PaginatedMessageManager.clearAll() }
        verify { MessageRepository.clearAll() }
    }

    @Test
    fun `handleTrimMemory TRIM_MEMORY_BACKGROUND sets BACKGROUND_LOW state`() = runTest {
        // Given
        MemoryManager.initialize(context)

        // When
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        advanceUntilIdle()

        // Then
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.BACKGROUND_LOW)
    }

    @Test
    fun `handleTrimMemory TRIM_MEMORY_MODERATE sets LOW state`() = runTest {
        // Given
        MemoryManager.initialize(context)

        // When
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)
        advanceUntilIdle()

        // Then
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.LOW)
    }

    @Test
    fun `handleTrimMemory TRIM_MEMORY_COMPLETE sets CRITICAL state`() = runTest {
        // Given
        MemoryManager.initialize(context)

        // When
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        advanceUntilIdle()

        // Then
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.CRITICAL)
    }

    @Test
    fun `handleTrimMemory TRIM_MEMORY_RUNNING_MODERATE sets MODERATE state`() = runTest {
        // Given
        MemoryManager.initialize(context)

        // When
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        advanceUntilIdle()

        // Then
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.MODERATE)
    }

    // ==================== LOW MEMORY HANDLER TESTS ====================

    @Test
    fun `handleLowMemory sets CRITICAL state`() = runTest {
        // Given
        MemoryManager.initialize(context)

        // When
        MemoryManager.handleLowMemory()
        advanceUntilIdle()

        // Then
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.CRITICAL)
        verify { ChatState.onLowMemory() }
    }

    // ==================== CLEANUP HANDLER TESTS ====================

    @Test
    fun `registerCleanupHandler adds handler`() = runTest {
        // Given
        MemoryManager.initialize(context)
        val handler = mockk<MemoryCleanupHandler>(relaxed = true)

        // When
        MemoryManager.registerCleanupHandler(handler)
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        advanceUntilIdle()

        // Then
        verify { handler.onMemoryPressure(MemoryPressureLevel.LIGHT) }
    }

    @Test
    fun `unregisterCleanupHandler removes handler`() = runTest {
        // Given
        MemoryManager.initialize(context)
        val handler = mockk<MemoryCleanupHandler>(relaxed = true)

        MemoryManager.registerCleanupHandler(handler)
        MemoryManager.unregisterCleanupHandler(handler)

        // When
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        advanceUntilIdle()

        // Then
        verify(exactly = 0) { handler.onMemoryPressure(any()) }
    }

    @Test
    fun `multiple handlers are notified`() = runTest {
        // Given
        MemoryManager.initialize(context)
        val handler1 = mockk<MemoryCleanupHandler>(relaxed = true)
        val handler2 = mockk<MemoryCleanupHandler>(relaxed = true)

        MemoryManager.registerCleanupHandler(handler1)
        MemoryManager.registerCleanupHandler(handler2)

        // When
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        advanceUntilIdle()

        // Then
        verify { handler1.onMemoryPressure(MemoryPressureLevel.HIGH) }
        verify { handler2.onMemoryPressure(MemoryPressureLevel.HIGH) }

        // Cleanup
        MemoryManager.unregisterCleanupHandler(handler1)
        MemoryManager.unregisterCleanupHandler(handler2)
    }

    @Test
    fun `handler exception does not affect other handlers`() = runTest {
        // Given
        MemoryManager.initialize(context)
        val failingHandler = mockk<MemoryCleanupHandler>()
        val workingHandler = mockk<MemoryCleanupHandler>(relaxed = true)

        every { failingHandler.onMemoryPressure(any()) } throws RuntimeException("Handler error")

        MemoryManager.registerCleanupHandler(failingHandler)
        MemoryManager.registerCleanupHandler(workingHandler)

        // When
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        advanceUntilIdle()

        // Then - Working handler should still be called
        verify { workingHandler.onMemoryPressure(any()) }

        // Cleanup
        MemoryManager.unregisterCleanupHandler(failingHandler)
        MemoryManager.unregisterCleanupHandler(workingHandler)
    }

    // ==================== MEMORY INFO TESTS ====================

    @Test
    fun `getMemoryInfo returns current memory information`() {
        // Given
        MemoryManager.initialize(context)

        // When
        val memoryInfo = MemoryManager.getMemoryInfo()

        // Then
        assertThat(memoryInfo.availableSystemMemory).isEqualTo(1_000_000_000L)
        assertThat(memoryInfo.totalSystemMemory).isEqualTo(4_000_000_000L)
        assertThat(memoryInfo.isLowMemory).isFalse()
    }

    @Test
    fun `getMemoryInfo returns default when no context`() {
        // Don't initialize - no context set

        // When
        val memoryInfo = MemoryManager.getMemoryInfo()

        // Then
        assertThat(memoryInfo.availableSystemMemory).isEqualTo(0L)
    }

    // ==================== SHOULD FREE MEMORY TESTS ====================

    @Test
    fun `shouldFreeMemory returns true when low memory`() {
        // Given
        val lowMemoryInfo = ActivityManager.MemoryInfo()
        every { activityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.lowMemory = true
        }
        MemoryManager.initialize(context)

        // When
        val result = MemoryManager.shouldFreeMemory()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldFreeMemory returns false when memory is healthy`() {
        // Given - Default setup has healthy memory
        MemoryManager.initialize(context, MemoryConfig(memoryWarningThreshold = 90))

        // When
        val result = MemoryManager.shouldFreeMemory()

        // Then
        assertThat(result).isFalse()
    }

    // ==================== CHAT MEMORY USAGE TESTS ====================

    @Test
    fun `getChatMemoryUsage returns ChatState memory info`() {
        // Given
        MemoryManager.initialize(context)

        // When
        val usage = MemoryManager.getChatMemoryUsage()

        // Then
        assertThat(usage.answerVariables).isEqualTo(10)
        assertThat(usage.transcriptEntries).isEqualTo(50)
        assertThat(usage.recordEntries).isEqualTo(20)
    }

    // ==================== RESET TO NORMAL TESTS ====================

    @Test
    fun `resetToNormal sets state to NORMAL from non-critical`() = runTest {
        // Given
        MemoryManager.initialize(context)
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        advanceUntilIdle()
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.BACKGROUND)

        // When
        MemoryManager.resetToNormal()

        // Then
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.NORMAL)
    }

    @Test
    fun `resetToNormal does not change CRITICAL state`() = runTest {
        // Given
        MemoryManager.initialize(context)
        MemoryManager.handleLowMemory()
        advanceUntilIdle()
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.CRITICAL)

        // When
        MemoryManager.resetToNormal()

        // Then - State should remain CRITICAL
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.CRITICAL)
    }

    // ==================== GARBAGE COLLECTION TESTS ====================

    @Test
    fun `suggestGarbageCollection does not throw`() {
        // When - Should not throw
        MemoryManager.suggestGarbageCollection()

        // Then - No exception
    }

    // ==================== MEMORY STATE ENUM TESTS ====================

    @Test
    fun `MemoryState enum has all expected values`() {
        // Then
        assertThat(MemoryState.values()).asList().containsExactly(
            MemoryState.NORMAL,
            MemoryState.MODERATE,
            MemoryState.BACKGROUND,
            MemoryState.BACKGROUND_LOW,
            MemoryState.LOW,
            MemoryState.CRITICAL
        )
    }

    // ==================== MEMORY PRESSURE LEVEL TESTS ====================

    @Test
    fun `MemoryPressureLevel enum has all expected values`() {
        // Then
        assertThat(MemoryPressureLevel.values()).asList().containsExactly(
            MemoryPressureLevel.LIGHT,
            MemoryPressureLevel.MODERATE,
            MemoryPressureLevel.HIGH,
            MemoryPressureLevel.CRITICAL
        )
    }

    // ==================== MEMORY CONFIG TESTS ====================

    @Test
    fun `MemoryConfig has correct defaults`() {
        // When
        val config = MemoryConfig()

        // Then
        assertThat(config.memoryWarningThreshold).isEqualTo(75)
        assertThat(config.lowMemoryMessageLimit).isEqualTo(30)
        assertThat(config.criticalMemoryMessageLimit).isEqualTo(10)
    }

    @Test
    fun `MemoryConfig accepts custom values`() {
        // When
        val config = MemoryConfig(
            memoryWarningThreshold = 80,
            lowMemoryMessageLimit = 50,
            criticalMemoryMessageLimit = 20
        )

        // Then
        assertThat(config.memoryWarningThreshold).isEqualTo(80)
        assertThat(config.lowMemoryMessageLimit).isEqualTo(50)
        assertThat(config.criticalMemoryMessageLimit).isEqualTo(20)
    }

    // ==================== MEMORY INFO FORMATTING TESTS ====================

    @Test
    fun `MemoryInfo formatMemory formats GB correctly`() {
        // Given
        val info = MemoryInfo()

        // Then
        assertThat(info.formatMemory(2_147_483_648L)).contains("GB")
    }

    @Test
    fun `MemoryInfo formatMemory formats MB correctly`() {
        // Given
        val info = MemoryInfo()

        // Then
        assertThat(info.formatMemory(104_857_600L)).contains("MB")
    }

    @Test
    fun `MemoryInfo formatMemory formats KB correctly`() {
        // Given
        val info = MemoryInfo()

        // Then
        assertThat(info.formatMemory(10_240L)).contains("KB")
    }

    @Test
    fun `MemoryInfo formatMemory formats bytes correctly`() {
        // Given
        val info = MemoryInfo()

        // Then
        assertThat(info.formatMemory(500L)).contains("B")
    }

    @Test
    fun `MemoryInfo toString contains all fields`() {
        // Given
        val info = MemoryInfo(
            availableSystemMemory = 1_000_000_000L,
            totalSystemMemory = 4_000_000_000L,
            isLowMemory = false,
            appUsedMemory = 100_000_000L,
            appMaxMemory = 500_000_000L,
            memoryUsagePercent = 20
        )

        // When
        val result = info.toString()

        // Then
        assertThat(result).contains("Available System")
        assertThat(result).contains("Total System")
        assertThat(result).contains("Is Low Memory")
        assertThat(result).contains("App Used")
        assertThat(result).contains("App Max")
        assertThat(result).contains("Usage")
    }

    // ==================== EXTENSION FUNCTION TESTS ====================

    @Test
    fun `handleConferbotMemory extension calls MemoryManager`() = runTest {
        // Given
        MemoryManager.initialize(context)
        val callbacks = mockk<ComponentCallbacks2>(relaxed = true)

        // When
        callbacks.handleConferbotMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        advanceUntilIdle()

        // Then
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.BACKGROUND)
    }

    // ==================== STATE FLOW TESTS ====================

    @Test
    fun `memoryState StateFlow emits state changes`() = runTest {
        // Given
        MemoryManager.initialize(context)

        // Initial state
        assertThat(MemoryManager.memoryState.value).isIn(
            listOf(MemoryState.NORMAL, MemoryState.BACKGROUND, MemoryState.CRITICAL)
        )

        // When
        MemoryManager.handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        advanceUntilIdle()

        // Then
        assertThat(MemoryManager.memoryState.value).isEqualTo(MemoryState.LOW)
    }
}
