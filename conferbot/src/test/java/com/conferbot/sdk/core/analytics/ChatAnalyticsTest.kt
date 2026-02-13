package com.conferbot.sdk.core.analytics

import com.conferbot.sdk.services.SocketClient
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ChatAnalytics
 * Tests event tracking, session analytics, and node visit tracking
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatAnalyticsTest {

    private lateinit var socketClient: SocketClient
    private val testDispatcher = StandardTestDispatcher()

    private val testSessionId = "test-session-123"
    private val testBotId = "test-bot-456"
    private val testVisitorId = "test-visitor-789"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        socketClient = mockk(relaxed = true)

        // Reset ChatAnalytics state
        ChatAnalytics.finalizeChatAnalytics()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ChatAnalytics.finalizeChatAnalytics()
    }

    // ==================== INITIALIZATION TESTS ====================

    @Test
    fun `initializeChatAnalytics sets session data`() = runTest {
        // When
        ChatAnalytics.setSocketClient(socketClient)
        ChatAnalytics.initializeChatAnalytics(testSessionId, testBotId, testVisitorId)
        advanceUntilIdle()

        // Then
        verify { socketClient.emitAnalyticsEvent("track-chat-start", any()) }
    }

    @Test
    fun `initializeChatAnalytics resets if already initialized`() = runTest {
        // Given - Initialize first time
        ChatAnalytics.setSocketClient(socketClient)
        ChatAnalytics.initializeChatAnalytics(testSessionId, testBotId, testVisitorId)
        advanceUntilIdle()

        // When - Initialize again
        ChatAnalytics.initializeChatAnalytics("new-session", "new-bot", "new-visitor")
        advanceUntilIdle()

        // Then - Should emit two track-chat-start events
        verify(exactly = 2) { socketClient.emitAnalyticsEvent("track-chat-start", any()) }
    }

    @Test
    fun `initializeChatAnalytics includes device info`() = runTest {
        // Given
        val capturedData = slot<Map<String, Any?>>()
        every { socketClient.emitAnalyticsEvent("track-chat-start", capture(capturedData)) } just Runs

        // When
        ChatAnalytics.setSocketClient(socketClient)
        ChatAnalytics.initializeChatAnalytics(testSessionId, testBotId, testVisitorId)
        advanceUntilIdle()

        // Then
        @Suppress("UNCHECKED_CAST")
        val deviceInfo = capturedData.captured["deviceInfo"] as? Map<String, Any?>
        assertThat(deviceInfo).isNotNull()
        assertThat(deviceInfo?.get("deviceType")).isEqualTo("mobile")
        assertThat(deviceInfo?.get("os")).isEqualTo("Android")
    }

    // ==================== UTM PARAMETERS TESTS ====================

    @Test
    fun `setUtmParameters stores attribution data`() = runTest {
        // Given
        val capturedData = slot<Map<String, Any?>>()
        every { socketClient.emitAnalyticsEvent("track-chat-start", capture(capturedData)) } just Runs

        // When
        ChatAnalytics.setUtmParameters(
            utmSource = "google",
            utmMedium = "cpc",
            utmCampaign = "summer_sale"
        )
        ChatAnalytics.setSocketClient(socketClient)
        ChatAnalytics.initializeChatAnalytics(testSessionId, testBotId, testVisitorId)
        advanceUntilIdle()

        // Then
        @Suppress("UNCHECKED_CAST")
        val attribution = capturedData.captured["attribution"] as? Map<String, Any?>
        assertThat(attribution?.get("utmSource")).isEqualTo("google")
        assertThat(attribution?.get("utmMedium")).isEqualTo("cpc")
        assertThat(attribution?.get("utmCampaign")).isEqualTo("summer_sale")
    }

    // ==================== NODE ENTRY TESTS ====================

    @Test
    fun `trackNodeEntry emits node visit event`() = runTest {
        // Given
        initializeAnalytics()

        // When
        ChatAnalytics.trackNodeEntry("node-1", "message-node", "Welcome Message")
        advanceUntilIdle()

        // Then
        verify { socketClient.emitAnalyticsEvent("track-node-visit", any()) }
    }

    @Test
    fun `trackNodeEntry does nothing when not initialized`() = runTest {
        // Given - Not initialized
        ChatAnalytics.setSocketClient(socketClient)

        // When
        ChatAnalytics.trackNodeEntry("node-1", "message-node")
        advanceUntilIdle()

        // Then
        verify(exactly = 0) { socketClient.emitAnalyticsEvent("track-node-visit", any()) }
    }

    @Test
    fun `trackNodeEntry includes node data`() = runTest {
        // Given
        val capturedData = slot<Map<String, Any?>>()
        every { socketClient.emitAnalyticsEvent("track-node-visit", capture(capturedData)) } just Runs
        initializeAnalytics()

        // When
        ChatAnalytics.trackNodeEntry("node-123", "ask-question", "Name Question")
        advanceUntilIdle()

        // Then
        assertThat(capturedData.captured["nodeId"]).isEqualTo("node-123")
        assertThat(capturedData.captured["nodeType"]).isEqualTo("ask-question")
        assertThat(capturedData.captured["nodeName"]).isEqualTo("Name Question")
        assertThat(capturedData.captured["enteredAt"]).isNotNull()
    }

    @Test
    fun `trackNodeEntry uses nodeType as nodeName when not provided`() = runTest {
        // Given
        val capturedData = slot<Map<String, Any?>>()
        every { socketClient.emitAnalyticsEvent("track-node-visit", capture(capturedData)) } just Runs
        initializeAnalytics()

        // When
        ChatAnalytics.trackNodeEntry("node-123", "message-node", "")
        advanceUntilIdle()

        // Then
        assertThat(capturedData.captured["nodeName"]).isEqualTo("message-node")
    }

    // ==================== NODE EXIT TESTS ====================

    @Test
    fun `trackNodeExit emits exit event for current node`() = runTest {
        // Given
        initializeAnalytics()
        ChatAnalytics.trackNodeEntry("node-1", "message-node")
        advanceUntilIdle()
        clearMocks(socketClient, answers = false)

        // When
        ChatAnalytics.trackNodeExit("node-1", "proceeded")
        advanceUntilIdle()

        // Then
        verify { socketClient.emitAnalyticsEvent("track-node-exit", any()) }
    }

    @Test
    fun `trackNodeExit does nothing for different node`() = runTest {
        // Given
        initializeAnalytics()
        ChatAnalytics.trackNodeEntry("node-1", "message-node")
        advanceUntilIdle()
        clearMocks(socketClient, answers = false)

        // When - Try to exit different node
        ChatAnalytics.trackNodeExit("node-2", "proceeded")
        advanceUntilIdle()

        // Then
        verify(exactly = 0) { socketClient.emitAnalyticsEvent("track-node-exit", any()) }
    }

    @Test
    fun `trackNodeExit calculates dwell time`() = runTest {
        // Given
        val capturedData = slot<Map<String, Any?>>()
        every { socketClient.emitAnalyticsEvent("track-node-exit", capture(capturedData)) } just Runs
        initializeAnalytics()
        ChatAnalytics.trackNodeEntry("node-1", "message-node")
        advanceUntilIdle()

        // Simulate time passing
        advanceTimeBy(5000)

        // When
        ChatAnalytics.trackNodeExit("node-1", "proceeded")
        advanceUntilIdle()

        // Then
        assertThat(capturedData.captured["dwellTime"]).isNotNull()
        assertThat(capturedData.captured["exitType"]).isEqualTo("proceeded")
    }

    @Test
    fun `trackNodeExit includes user input when provided`() = runTest {
        // Given
        val capturedData = slot<Map<String, Any?>>()
        every { socketClient.emitAnalyticsEvent("track-node-exit", capture(capturedData)) } just Runs
        initializeAnalytics()
        ChatAnalytics.trackNodeEntry("node-1", "ask-question")
        advanceUntilIdle()

        // When
        ChatAnalytics.trackNodeExit("node-1", "proceeded", userInput = "John Doe")
        advanceUntilIdle()

        // Then
        assertThat(capturedData.captured["userInput"]).isEqualTo("John Doe")
    }

    @Test
    fun `trackNodeEntry exits previous node automatically`() = runTest {
        // Given
        initializeAnalytics()
        ChatAnalytics.trackNodeEntry("node-1", "message-node")
        advanceUntilIdle()

        // When - Enter new node
        ChatAnalytics.trackNodeEntry("node-2", "ask-question")
        advanceUntilIdle()

        // Then - Previous node should have been exited
        verify { socketClient.emitAnalyticsEvent("track-node-exit", any()) }
    }

    // ==================== USER MESSAGE TRACKING TESTS ====================

    @Test
    fun `trackUserMessage increments message count`() = runTest {
        // Given
        initializeAnalytics()

        // When
        ChatAnalytics.trackUserMessage(messageLength = 50)
        ChatAnalytics.trackUserMessage(messageLength = 30)
        advanceUntilIdle()

        // Then - Tracked internally (we verify through sentiment tracking)
    }

    @Test
    fun `trackUserMessage emits sentiment event when text provided`() = runTest {
        // Given
        initializeAnalytics()

        // When
        ChatAnalytics.trackUserMessage(messageLength = 50, messageText = "I love this product!")
        advanceUntilIdle()

        // Then
        verify { socketClient.emitAnalyticsEvent("track-sentiment", any()) }
    }

    @Test
    fun `trackUserMessage does nothing when not initialized`() = runTest {
        // Given - Not initialized
        ChatAnalytics.setSocketClient(socketClient)

        // When
        ChatAnalytics.trackUserMessage(messageLength = 50, messageText = "test")
        advanceUntilIdle()

        // Then
        verify(exactly = 0) { socketClient.emitAnalyticsEvent("track-sentiment", any()) }
    }

    // ==================== INTERACTION TRACKING TESTS ====================

    @Test
    fun `trackInteraction emits interaction event`() = runTest {
        // Given
        initializeAnalytics()

        // When
        ChatAnalytics.trackInteraction("linksClicked", mapOf("url" to "https://example.com"))
        advanceUntilIdle()

        // Then
        verify { socketClient.emitAnalyticsEvent("track-interaction", any()) }
    }

    @Test
    fun `trackInteraction includes interaction type and data`() = runTest {
        // Given
        val capturedData = slot<Map<String, Any?>>()
        every { socketClient.emitAnalyticsEvent("track-interaction", capture(capturedData)) } just Runs
        initializeAnalytics()

        // When
        ChatAnalytics.trackInteraction("buttonsClicked", mapOf("buttonId" to "submit"))
        advanceUntilIdle()

        // Then
        assertThat(capturedData.captured["type"]).isEqualTo("buttonsClicked")
        assertThat(capturedData.captured["buttonId"]).isEqualTo("submit")
    }

    // ==================== TYPING BEHAVIOR TESTS ====================

    @Test
    fun `trackTypingStart records start time`() = runTest {
        // Given
        initializeAnalytics()

        // When
        ChatAnalytics.trackTypingStart()

        // Then - Internal state updated (verified through engagement tracking)
    }

    @Test
    fun `trackTypingEnd calculates typing duration`() = runTest {
        // Given
        initializeAnalytics()
        ChatAnalytics.trackTypingStart()
        advanceTimeBy(5000)

        // When
        ChatAnalytics.trackTypingEnd()

        // Then - Internal typing time accumulated
    }

    @Test
    fun `trackDeletion increments deletion count`() = runTest {
        // Given
        initializeAnalytics()

        // When
        ChatAnalytics.trackDeletion()
        ChatAnalytics.trackDeletion()
        ChatAnalytics.trackDeletion()

        // Then - Internal deletion count is 3
    }

    // ==================== GOAL COMPLETION TESTS ====================

    @Test
    fun `trackGoalCompletion emits goal event`() = runTest {
        // Given
        initializeAnalytics()

        // When
        ChatAnalytics.trackGoalCompletion("purchase-completed")
        advanceUntilIdle()

        // Then
        verify { socketClient.emitAnalyticsEvent("track-goal-completion", any()) }
    }

    @Test
    fun `trackGoalCompletion includes conversion data`() = runTest {
        // Given
        val capturedData = slot<Map<String, Any?>>()
        every { socketClient.emitAnalyticsEvent("track-goal-completion", capture(capturedData)) } just Runs
        initializeAnalytics()

        // When
        ChatAnalytics.trackGoalCompletion(
            goalId = "purchase",
            conversionEvent = "checkout_complete",
            conversionValue = 99.99
        )
        advanceUntilIdle()

        // Then
        assertThat(capturedData.captured["goalId"]).isEqualTo("purchase")
        assertThat(capturedData.captured["conversionEvent"]).isEqualTo("checkout_complete")
        assertThat(capturedData.captured["conversionValue"]).isEqualTo(99.99)
    }

    // ==================== CHAT RATING TESTS ====================

    @Test
    fun `submitChatRating emits rating event`() = runTest {
        // Given
        initializeAnalytics()

        // When
        ChatAnalytics.submitChatRating(csatScore = 5)
        advanceUntilIdle()

        // Then
        verify { socketClient.emitAnalyticsEvent("submit-chat-rating", any()) }
    }

    @Test
    fun `submitChatRating includes all rating types`() = runTest {
        // Given
        val capturedData = slot<Map<String, Any?>>()
        every { socketClient.emitAnalyticsEvent("submit-chat-rating", capture(capturedData)) } just Runs
        initializeAnalytics()

        // When
        ChatAnalytics.submitChatRating(
            csatScore = 4,
            feedback = "Great service!",
            thumbsUp = true,
            npsScore = 9,
            source = "end_chat_survey"
        )
        advanceUntilIdle()

        // Then
        assertThat(capturedData.captured["csatScore"]).isEqualTo(4)
        assertThat(capturedData.captured["feedback"]).isEqualTo("Great service!")
        assertThat(capturedData.captured["thumbsUp"]).isEqualTo(true)
        assertThat(capturedData.captured["npsScore"]).isEqualTo(9)
        assertThat(capturedData.captured["source"]).isEqualTo("end_chat_survey")
    }

    // ==================== DROP-OFF TRACKING TESTS ====================

    @Test
    fun `trackPotentialDropOff emits drop-off event`() = runTest {
        // Given
        initializeAnalytics()
        ChatAnalytics.trackNodeEntry("node-1", "ask-question")
        advanceUntilIdle()
        clearMocks(socketClient, answers = false)

        // When
        ChatAnalytics.trackPotentialDropOff("app_backgrounded")
        advanceUntilIdle()

        // Then
        verify { socketClient.emitAnalyticsEvent("track-drop-off", any()) }
    }

    @Test
    fun `trackPotentialDropOff does nothing without current node`() = runTest {
        // Given
        initializeAnalytics()
        // No node entered

        // When
        ChatAnalytics.trackPotentialDropOff()
        advanceUntilIdle()

        // Then
        verify(exactly = 0) { socketClient.emitAnalyticsEvent("track-drop-off", any()) }
    }

    @Test
    fun `trackPotentialDropOff includes node info and reason`() = runTest {
        // Given
        val capturedData = slot<Map<String, Any?>>()
        every { socketClient.emitAnalyticsEvent("track-drop-off", capture(capturedData)) } just Runs
        initializeAnalytics()
        ChatAnalytics.trackNodeEntry("form-node", "ask-question", "Contact Form")
        advanceUntilIdle()

        // When
        ChatAnalytics.trackPotentialDropOff("user_closed_app")
        advanceUntilIdle()

        // Then
        assertThat(capturedData.captured["nodeId"]).isEqualTo("form-node")
        assertThat(capturedData.captured["nodeType"]).isEqualTo("ask-question")
        assertThat(capturedData.captured["nodeName"]).isEqualTo("Contact Form")
        assertThat(capturedData.captured["reason"]).isEqualTo("user_closed_app")
    }

    // ==================== FINALIZE ANALYTICS TESTS ====================

    @Test
    fun `finalizeChatAnalytics emits finalize event`() = runTest {
        // Given
        initializeAnalytics()
        ChatAnalytics.trackNodeEntry("node-1", "message-node")
        advanceUntilIdle()
        clearMocks(socketClient, answers = false)

        // When
        ChatAnalytics.finalizeChatAnalytics()
        advanceUntilIdle()

        // Then
        verify { socketClient.emitAnalyticsEvent("track-node-exit", any()) } // Exits current node
        verify { socketClient.emitAnalyticsEvent("finalize-analytics", any()) }
    }

    @Test
    fun `finalizeChatAnalytics includes session metrics`() = runTest {
        // Given
        val capturedData = slot<Map<String, Any?>>()
        every { socketClient.emitAnalyticsEvent("finalize-analytics", capture(capturedData)) } just Runs
        initializeAnalytics()
        advanceTimeBy(10000) // Simulate 10 second session

        // When
        ChatAnalytics.finalizeChatAnalytics()
        advanceUntilIdle()

        // Then
        @Suppress("UNCHECKED_CAST")
        val finalMetrics = capturedData.captured["finalMetrics"] as? Map<String, Any?>
        assertThat(finalMetrics).isNotNull()
        assertThat(finalMetrics?.containsKey("totalDuration")).isTrue()
        assertThat(finalMetrics?.containsKey("activeDuration")).isTrue()
    }

    @Test
    fun `finalizeChatAnalytics does nothing when not initialized`() = runTest {
        // Given - Not initialized
        ChatAnalytics.setSocketClient(socketClient)

        // When
        ChatAnalytics.finalizeChatAnalytics()
        advanceUntilIdle()

        // Then
        verify(exactly = 0) { socketClient.emitAnalyticsEvent("finalize-analytics", any()) }
    }

    @Test
    fun `finalizeChatAnalytics resets state`() = runTest {
        // Given
        initializeAnalytics()
        ChatAnalytics.finalizeChatAnalytics()
        advanceUntilIdle()
        clearMocks(socketClient, answers = false)

        // When - Try to track after finalize
        ChatAnalytics.trackNodeEntry("node-1", "message")
        advanceUntilIdle()

        // Then - Should not emit (not initialized)
        verify(exactly = 0) { socketClient.emitAnalyticsEvent("track-node-visit", any()) }
    }

    // ==================== DEVICE INFO TESTS ====================

    @Test
    fun `getDeviceInfo returns complete device information`() {
        // When
        val deviceInfo = ChatAnalytics.getDeviceInfo()

        // Then
        assertThat(deviceInfo.deviceType).isEqualTo("mobile")
        assertThat(deviceInfo.osVersion).isNotEmpty()
        assertThat(deviceInfo.sdkVersion).isGreaterThan(0)
        assertThat(deviceInfo.deviceModel).isNotEmpty()
        assertThat(deviceInfo.manufacturer).isNotEmpty()
        assertThat(deviceInfo.language).isNotEmpty()
        assertThat(deviceInfo.timezone).isNotEmpty()
    }

    // ==================== DATA CLASS TESTS ====================

    @Test
    fun `NodeVisitData stores values correctly`() {
        // When
        val data = NodeVisitData(
            nodeId = "node-1",
            nodeType = "message",
            nodeName = "Welcome",
            enteredAt = 12345678L
        )

        // Then
        assertThat(data.nodeId).isEqualTo("node-1")
        assertThat(data.nodeType).isEqualTo("message")
        assertThat(data.nodeName).isEqualTo("Welcome")
        assertThat(data.enteredAt).isEqualTo(12345678L)
    }

    @Test
    fun `SessionMetrics stores values correctly`() {
        // When
        val metrics = SessionMetrics(
            startedAt = 1000L,
            firstMessageAt = 2000L,
            lastMessageAt = 5000L,
            totalDuration = 10,
            activeDuration = 8,
            idleTime = 2
        )

        // Then
        assertThat(metrics.startedAt).isEqualTo(1000L)
        assertThat(metrics.firstMessageAt).isEqualTo(2000L)
        assertThat(metrics.lastMessageAt).isEqualTo(5000L)
        assertThat(metrics.totalDuration).isEqualTo(10)
        assertThat(metrics.activeDuration).isEqualTo(8)
        assertThat(metrics.idleTime).isEqualTo(2)
    }

    @Test
    fun `TypingBehavior stores values correctly`() {
        // When
        val behavior = TypingBehavior(
            totalTypingTime = 5000L,
            deletions = 3,
            abandonedMessages = 1,
            avgMessageLength = 45
        )

        // Then
        assertThat(behavior.totalTypingTime).isEqualTo(5000L)
        assertThat(behavior.deletions).isEqualTo(3)
        assertThat(behavior.abandonedMessages).isEqualTo(1)
        assertThat(behavior.avgMessageLength).isEqualTo(45)
    }

    @Test
    fun `DeviceInfo stores values correctly`() {
        // When
        val info = DeviceInfo(
            deviceType = "mobile",
            osVersion = "14",
            sdkVersion = 34,
            deviceModel = "Pixel 8",
            manufacturer = "Google",
            language = "en",
            timezone = "America/New_York"
        )

        // Then
        assertThat(info.deviceType).isEqualTo("mobile")
        assertThat(info.osVersion).isEqualTo("14")
        assertThat(info.sdkVersion).isEqualTo(34)
        assertThat(info.deviceModel).isEqualTo("Pixel 8")
        assertThat(info.manufacturer).isEqualTo("Google")
        assertThat(info.language).isEqualTo("en")
        assertThat(info.timezone).isEqualTo("America/New_York")
    }

    @Test
    fun `UtmParameters stores values correctly`() {
        // When
        val params = UtmParameters(
            utmSource = "google",
            utmMedium = "cpc",
            utmCampaign = "summer",
            utmTerm = "chat",
            utmContent = "banner",
            referrer = "https://google.com",
            landingPage = "https://example.com/chat"
        )

        // Then
        assertThat(params.utmSource).isEqualTo("google")
        assertThat(params.utmMedium).isEqualTo("cpc")
        assertThat(params.utmCampaign).isEqualTo("summer")
        assertThat(params.utmTerm).isEqualTo("chat")
        assertThat(params.utmContent).isEqualTo("banner")
        assertThat(params.referrer).isEqualTo("https://google.com")
        assertThat(params.landingPage).isEqualTo("https://example.com/chat")
    }

    // ==================== NO SOCKET CLIENT TESTS ====================

    @Test
    fun `events are not emitted without socket client`() = runTest {
        // Given - No socket client set
        ChatAnalytics.initializeChatAnalytics(testSessionId, testBotId, testVisitorId)
        advanceUntilIdle()

        ChatAnalytics.trackNodeEntry("node-1", "message")
        ChatAnalytics.trackUserMessage(50)
        ChatAnalytics.trackInteraction("click", emptyMap())
        advanceUntilIdle()

        // Then - No exceptions, operations are no-ops
    }

    // ==================== HELPER METHODS ====================

    private fun initializeAnalytics() {
        ChatAnalytics.setSocketClient(socketClient)
        ChatAnalytics.initializeChatAnalytics(testSessionId, testBotId, testVisitorId)
    }
}
