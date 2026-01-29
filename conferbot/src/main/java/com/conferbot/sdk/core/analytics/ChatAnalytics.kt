package com.conferbot.sdk.core.analytics

import android.os.Build
import android.util.Log
import com.conferbot.sdk.services.SocketClient
import kotlinx.coroutines.*
import java.util.*

/**
 * Node visit data for tracking dwell time and interaction patterns
 */
data class NodeVisitData(
    val nodeId: String,
    val nodeType: String,
    val nodeName: String,
    val enteredAt: Long
)

/**
 * Session metrics for tracking overall engagement
 */
data class SessionMetrics(
    val startedAt: Long,
    var firstMessageAt: Long? = null,
    var lastMessageAt: Long? = null,
    var totalDuration: Long = 0,
    var activeDuration: Long = 0,
    var idleTime: Long = 0
)

/**
 * Typing behavior metrics
 */
data class TypingBehavior(
    var totalTypingTime: Long = 0,
    var deletions: Int = 0,
    var abandonedMessages: Int = 0,
    var avgMessageLength: Int = 0
)

/**
 * Device and environment information
 */
data class DeviceInfo(
    val deviceType: String,
    val osVersion: String,
    val sdkVersion: Int,
    val deviceModel: String,
    val manufacturer: String,
    val language: String,
    val timezone: String
)

/**
 * UTM attribution parameters
 */
data class UtmParameters(
    var utmSource: String? = null,
    var utmMedium: String? = null,
    var utmCampaign: String? = null,
    var utmTerm: String? = null,
    var utmContent: String? = null,
    var referrer: String? = null,
    var landingPage: String? = null
)

/**
 * Chat analytics tracking for Android SDK
 * Matches web widget's chatAnalytics.ts functionality
 *
 * Tracks:
 * - Session start/end time and duration
 * - Node visits (entry time, exit time, dwell time)
 * - User message count and length
 * - Typing time and behavior
 * - Interaction frequency
 * - Device info (type, OS version)
 * - UTM parameters if available
 */
object ChatAnalytics {
    private const val TAG = "ChatAnalytics"

    // Analytics state variables
    private var chatSessionId: String? = null
    private var botId: String? = null
    private var visitorId: String? = null
    private var sessionStartTime: Long = 0
    private var lastActivityTime: Long = 0
    private var currentNodeData: NodeVisitData? = null
    private var totalIdleTime: Long = 0
    private var typingStartTime: Long = 0
    private var totalTypingTime: Long = 0
    private var deletionCount: Int = 0
    private var messageCount: Int = 0
    private var totalMessageLength: Int = 0
    private var isInitialized: Boolean = false

    // UTM parameters (can be set externally)
    private var utmParameters: UtmParameters = UtmParameters()

    // Socket client reference for emitting events
    private var socketClient: SocketClient? = null

    // Coroutine scope for periodic engagement tracking
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var engagementJob: Job? = null

    // Engagement tracking interval (30 seconds)
    private const val ENGAGEMENT_INTERVAL_MS = 30000L

    // Idle threshold (60 seconds)
    private const val IDLE_THRESHOLD_MS = 60000L

    /**
     * Set the socket client for emitting analytics events
     */
    fun setSocketClient(client: SocketClient) {
        socketClient = client
    }

    /**
     * Set UTM parameters from external source (deep link, etc.)
     */
    fun setUtmParameters(
        utmSource: String? = null,
        utmMedium: String? = null,
        utmCampaign: String? = null,
        utmTerm: String? = null,
        utmContent: String? = null,
        referrer: String? = null,
        landingPage: String? = null
    ) {
        utmParameters = UtmParameters(
            utmSource = utmSource,
            utmMedium = utmMedium,
            utmCampaign = utmCampaign,
            utmTerm = utmTerm,
            utmContent = utmContent,
            referrer = referrer,
            landingPage = landingPage
        )
    }

    /**
     * Initialize chat analytics tracking
     * Call this when a new chat session starts
     *
     * @param sessionId The chat session ID
     * @param botIdentifier The bot ID
     * @param visitorIdentifier The visitor ID
     */
    fun initializeChatAnalytics(
        sessionId: String,
        botIdentifier: String,
        visitorIdentifier: String
    ) {
        if (isInitialized) {
            Log.w(TAG, "Analytics already initialized, resetting...")
            resetState()
        }

        chatSessionId = sessionId
        botId = botIdentifier
        visitorId = visitorIdentifier
        sessionStartTime = System.currentTimeMillis()
        lastActivityTime = sessionStartTime
        isInitialized = true

        Log.d(TAG, "Chat analytics initialized for session: $sessionId")

        // Emit initialization event
        emitTrackChatStart()

        // Start periodic engagement tracking
        startPeriodicEngagement()
    }

    /**
     * Track node entry - call when a new node is displayed
     *
     * @param nodeId The unique identifier of the node
     * @param nodeType The type of the node (e.g., "display-text", "ask-question")
     * @param nodeName Optional human-readable name for the node
     */
    fun trackNodeEntry(nodeId: String, nodeType: String, nodeName: String = "") {
        if (!isInitialized) {
            Log.w(TAG, "Analytics not initialized, skipping node entry tracking")
            return
        }

        // Exit previous node if exists
        if (currentNodeData != null) {
            trackNodeExitInternal("proceeded")
        }

        currentNodeData = NodeVisitData(
            nodeId = nodeId,
            nodeType = nodeType,
            nodeName = nodeName.ifEmpty { nodeType },
            enteredAt = System.currentTimeMillis()
        )

        Log.d(TAG, "Node entry tracked: $nodeId ($nodeType)")

        // Emit node visit event
        emitTrackNodeVisit()

        updateActivity()
    }

    /**
     * Track node exit - call when user leaves/completes a node
     *
     * @param nodeId The node ID being exited
     * @param exitType The type of exit (proceeded, abandoned, back_pressed, skipped, timeout, error)
     * @param userInput Optional user input value
     * @param selectedOption Optional selected option value
     */
    fun trackNodeExit(
        nodeId: String,
        exitType: String = "proceeded",
        userInput: String? = null,
        selectedOption: String? = null
    ) {
        if (currentNodeData?.nodeId == nodeId) {
            trackNodeExitInternal(exitType, userInput, selectedOption)
        }
    }

    /**
     * Internal node exit tracking
     */
    private fun trackNodeExitInternal(
        exitType: String,
        userInput: String? = null,
        selectedOption: String? = null
    ) {
        val nodeData = currentNodeData ?: return

        val exitedAt = System.currentTimeMillis()
        val dwellTime = (exitedAt - nodeData.enteredAt) / 1000 // Convert to seconds

        Log.d(TAG, "Node exit tracked: ${nodeData.nodeId} (dwell: ${dwellTime}s, exit: $exitType)")

        // Emit node exit event
        emitTrackNodeExit(
            nodeId = nodeData.nodeId,
            exitedAt = exitedAt,
            exitType = exitType,
            dwellTime = dwellTime,
            userInput = userInput,
            selectedOption = selectedOption
        )

        currentNodeData = null
        updateActivity()
    }

    /**
     * Track user message for sentiment and engagement
     *
     * @param messageLength The length of the message in characters
     * @param messageText Optional message text for sentiment analysis
     * @param messageIndex The index of this message in the conversation
     */
    fun trackUserMessage(
        messageLength: Int,
        messageText: String? = null,
        messageIndex: Int = messageCount
    ) {
        if (!isInitialized) return

        // Update first message time if this is the first message
        if (messageCount == 0) {
            // Note: firstMessageAt would be tracked here if we had access to sessionMetrics state
        }

        messageCount++
        totalMessageLength += messageLength

        Log.d(TAG, "User message tracked: length=$messageLength, count=$messageCount")

        // Emit sentiment tracking if text provided
        if (messageText != null) {
            emitTrackSentiment(messageIndex, messageText)
        }

        updateActivity()
    }

    /**
     * Track various interaction types
     *
     * @param type The type of interaction (linksClicked, buttonsClicked, filesUploaded, etc.)
     * @param data Additional data about the interaction
     */
    fun trackInteraction(type: String, data: Map<String, Any> = emptyMap()) {
        if (!isInitialized) return

        Log.d(TAG, "Interaction tracked: type=$type, data=$data")

        emitTrackInteraction(type, data)

        updateActivity()
    }

    /**
     * Track typing behavior - call when user starts typing
     */
    fun trackTypingStart() {
        typingStartTime = System.currentTimeMillis()
    }

    /**
     * Track typing behavior - call when user stops typing
     */
    fun trackTypingEnd() {
        if (typingStartTime > 0) {
            totalTypingTime += System.currentTimeMillis() - typingStartTime
            typingStartTime = 0
        }
    }

    /**
     * Track typing behavior - call when user deletes text
     */
    fun trackDeletion() {
        deletionCount++
    }

    /**
     * Track goal completion
     *
     * @param goalId The ID of the completed goal
     * @param conversionEvent Optional conversion event name
     * @param conversionValue Optional conversion value (e.g., monetary value)
     */
    fun trackGoalCompletion(
        goalId: String,
        conversionEvent: String? = null,
        conversionValue: Double? = null
    ) {
        if (!isInitialized) return

        Log.d(TAG, "Goal completion tracked: $goalId")

        emitTrackGoalCompletion(goalId, conversionEvent, conversionValue)
    }

    /**
     * Submit chat rating/feedback
     *
     * @param csatScore Customer satisfaction score (1-5)
     * @param feedback Optional text feedback
     * @param thumbsUp Optional thumbs up/down indicator
     * @param npsScore Optional NPS score (0-10)
     * @param source Source of the rating (e.g., "post_chat_survey")
     */
    fun submitChatRating(
        csatScore: Int? = null,
        feedback: String? = null,
        thumbsUp: Boolean? = null,
        npsScore: Int? = null,
        source: String = "post_chat_survey"
    ) {
        if (!isInitialized) return

        Log.d(TAG, "Chat rating submitted: csat=$csatScore, nps=$npsScore")

        emitSubmitChatRating(csatScore, feedback, thumbsUp, npsScore, source)
    }

    /**
     * Finalize analytics when chat ends
     * Call this when the chat session is ending
     */
    fun finalizeChatAnalytics() {
        if (!isInitialized) {
            Log.w(TAG, "Analytics not initialized, nothing to finalize")
            return
        }

        // Stop periodic engagement
        stopPeriodicEngagement()

        // Exit current node if any
        if (currentNodeData != null) {
            trackNodeExitInternal("abandoned")
        }

        val endTime = System.currentTimeMillis()
        val totalDuration = (endTime - sessionStartTime) / 1000 // seconds
        val activeDuration = totalDuration - (totalIdleTime / 1000)

        Log.d(TAG, "Finalizing analytics: duration=${totalDuration}s, active=${activeDuration}s")

        // Emit finalize event
        emitFinalizeAnalytics(totalDuration, activeDuration)

        // Reset state
        resetState()
    }

    /**
     * Track potential drop-off when app goes to background
     */
    fun trackPotentialDropOff(reason: String = "app_backgrounded") {
        if (!isInitialized || currentNodeData == null) return

        val nodeData = currentNodeData ?: return
        val timeBeforeDropOff = (System.currentTimeMillis() - lastActivityTime) / 1000

        Log.d(TAG, "Potential drop-off tracked: ${nodeData.nodeId}, reason=$reason")

        emitTrackDropOff(
            nodeId = nodeData.nodeId,
            nodeType = nodeData.nodeType,
            nodeName = nodeData.nodeName,
            reason = reason,
            timeBeforeDropOff = timeBeforeDropOff
        )
    }

    /**
     * Update activity timestamp and track idle time
     */
    private fun updateActivity() {
        val now = System.currentTimeMillis()
        val timeSinceLastActivity = now - lastActivityTime

        // If idle for more than threshold, count as idle time
        if (timeSinceLastActivity > IDLE_THRESHOLD_MS) {
            totalIdleTime += timeSinceLastActivity - IDLE_THRESHOLD_MS
        }

        lastActivityTime = now
    }

    /**
     * Start periodic engagement tracking
     */
    private fun startPeriodicEngagement() {
        engagementJob = scope.launch {
            while (isActive) {
                delay(ENGAGEMENT_INTERVAL_MS)
                sendPeriodicEngagement()
            }
        }
    }

    /**
     * Stop periodic engagement tracking
     */
    private fun stopPeriodicEngagement() {
        engagementJob?.cancel()
        engagementJob = null
    }

    /**
     * Send periodic engagement update
     */
    private fun sendPeriodicEngagement() {
        if (!isInitialized) return

        val now = System.currentTimeMillis()
        val sessionMetrics = SessionMetrics(
            startedAt = sessionStartTime,
            lastMessageAt = lastActivityTime,
            totalDuration = (now - sessionStartTime) / 1000,
            activeDuration = (now - sessionStartTime - totalIdleTime) / 1000,
            idleTime = totalIdleTime / 1000
        )

        val typingBehavior = TypingBehavior(
            totalTypingTime = totalTypingTime / 1000,
            deletions = deletionCount,
            abandonedMessages = 0,
            avgMessageLength = if (messageCount > 0) totalMessageLength / messageCount else 0
        )

        emitTrackChatEngagement(sessionMetrics, typingBehavior)
    }

    /**
     * Get device information
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            deviceType = "mobile",
            osVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            language = Locale.getDefault().language,
            timezone = TimeZone.getDefault().id
        )
    }

    /**
     * Reset all analytics state
     */
    private fun resetState() {
        chatSessionId = null
        botId = null
        visitorId = null
        sessionStartTime = 0
        lastActivityTime = 0
        currentNodeData = null
        totalIdleTime = 0
        typingStartTime = 0
        totalTypingTime = 0
        deletionCount = 0
        messageCount = 0
        totalMessageLength = 0
        isInitialized = false
    }

    // ==================== Socket Emit Methods ====================

    private fun emitTrackChatStart() {
        val client = socketClient ?: return

        val deviceInfo = getDeviceInfo()
        val data = mapOf(
            "chatSessionId" to chatSessionId,
            "botId" to botId,
            "visitorId" to visitorId,
            "attribution" to mapOf(
                "utmSource" to utmParameters.utmSource,
                "utmMedium" to utmParameters.utmMedium,
                "utmCampaign" to utmParameters.utmCampaign,
                "utmTerm" to utmParameters.utmTerm,
                "utmContent" to utmParameters.utmContent,
                "referrer" to utmParameters.referrer,
                "landingPage" to utmParameters.landingPage
            ),
            "deviceInfo" to mapOf(
                "deviceType" to deviceInfo.deviceType,
                "os" to "Android",
                "osVersion" to deviceInfo.osVersion,
                "sdkVersion" to deviceInfo.sdkVersion,
                "deviceModel" to deviceInfo.deviceModel,
                "manufacturer" to deviceInfo.manufacturer,
                "language" to deviceInfo.language,
                "timezone" to deviceInfo.timezone
            )
        )

        client.emitAnalyticsEvent("track-chat-start", data)
    }

    private fun emitTrackNodeVisit() {
        val client = socketClient ?: return
        val nodeData = currentNodeData ?: return

        val data = mapOf(
            "chatSessionId" to chatSessionId,
            "nodeId" to nodeData.nodeId,
            "nodeType" to nodeData.nodeType,
            "nodeName" to nodeData.nodeName,
            "enteredAt" to nodeData.enteredAt
        )

        client.emitAnalyticsEvent("track-node-visit", data)
    }

    private fun emitTrackNodeExit(
        nodeId: String,
        exitedAt: Long,
        exitType: String,
        dwellTime: Long,
        userInput: String?,
        selectedOption: String?
    ) {
        val client = socketClient ?: return

        val data = mutableMapOf<String, Any?>(
            "chatSessionId" to chatSessionId,
            "nodeId" to nodeId,
            "exitedAt" to exitedAt,
            "exitType" to exitType,
            "dwellTime" to dwellTime
        )

        userInput?.let { data["userInput"] = it }
        selectedOption?.let { data["selectedOption"] = it }

        client.emitAnalyticsEvent("track-node-exit", data)
    }

    private fun emitTrackSentiment(messageIndex: Int, text: String) {
        val client = socketClient ?: return

        val data = mapOf(
            "chatSessionId" to chatSessionId,
            "messageIndex" to messageIndex,
            "text" to text,
            "messageType" to "user"
        )

        client.emitAnalyticsEvent("track-sentiment", data)
    }

    private fun emitTrackInteraction(type: String, data: Map<String, Any>) {
        val client = socketClient ?: return

        val eventData = mutableMapOf<String, Any?>(
            "chatSessionId" to chatSessionId,
            "type" to type,
            "nodeId" to currentNodeData?.nodeId
        )
        eventData.putAll(data)

        client.emitAnalyticsEvent("track-interaction", eventData)
    }

    private fun emitTrackGoalCompletion(
        goalId: String,
        conversionEvent: String?,
        conversionValue: Double?
    ) {
        val client = socketClient ?: return

        val data = mutableMapOf<String, Any?>(
            "chatSessionId" to chatSessionId,
            "goalId" to goalId
        )

        conversionEvent?.let { data["conversionEvent"] = it }
        conversionValue?.let { data["conversionValue"] = it }

        client.emitAnalyticsEvent("track-goal-completion", data)
    }

    private fun emitSubmitChatRating(
        csatScore: Int?,
        feedback: String?,
        thumbsUp: Boolean?,
        npsScore: Int?,
        source: String
    ) {
        val client = socketClient ?: return

        val data = mutableMapOf<String, Any?>(
            "chatSessionId" to chatSessionId,
            "source" to source
        )

        csatScore?.let { data["csatScore"] = it }
        feedback?.let { data["feedback"] = it }
        thumbsUp?.let { data["thumbsUp"] = it }
        npsScore?.let { data["npsScore"] = it }

        client.emitAnalyticsEvent("submit-chat-rating", data)
    }

    private fun emitTrackChatEngagement(
        sessionMetrics: SessionMetrics,
        typingBehavior: TypingBehavior
    ) {
        val client = socketClient ?: return

        val data = mapOf(
            "chatSessionId" to chatSessionId,
            "sessionMetrics" to mapOf(
                "startedAt" to sessionMetrics.startedAt,
                "lastMessageAt" to sessionMetrics.lastMessageAt,
                "totalDuration" to sessionMetrics.totalDuration,
                "activeDuration" to sessionMetrics.activeDuration,
                "idleTime" to sessionMetrics.idleTime
            ),
            "typingBehavior" to mapOf(
                "totalTypingTime" to typingBehavior.totalTypingTime,
                "deletions" to typingBehavior.deletions,
                "abandonedMessages" to typingBehavior.abandonedMessages,
                "avgMessageLength" to typingBehavior.avgMessageLength
            )
        )

        client.emitAnalyticsEvent("track-chat-engagement", data)
    }

    private fun emitTrackDropOff(
        nodeId: String,
        nodeType: String,
        nodeName: String,
        reason: String,
        timeBeforeDropOff: Long
    ) {
        val client = socketClient ?: return

        val data = mapOf(
            "chatSessionId" to chatSessionId,
            "nodeId" to nodeId,
            "nodeType" to nodeType,
            "nodeName" to nodeName,
            "reason" to reason,
            "timeBeforeDropOff" to timeBeforeDropOff,
            "lastUserAction" to "none"
        )

        client.emitAnalyticsEvent("track-drop-off", data)
    }

    private fun emitFinalizeAnalytics(totalDuration: Long, activeDuration: Long) {
        val client = socketClient ?: return

        val deviceInfo = getDeviceInfo()

        val data = mapOf(
            "chatSessionId" to chatSessionId,
            "finalMetrics" to mapOf(
                "totalDuration" to totalDuration,
                "activeDuration" to activeDuration,
                "idleTime" to totalIdleTime / 1000,
                "messageCounts" to mapOf(
                    "user" to messageCount
                ),
                "typingBehavior" to mapOf(
                    "totalTypingTime" to totalTypingTime / 1000,
                    "deletions" to deletionCount,
                    "abandonedMessages" to 0,
                    "avgMessageLength" to if (messageCount > 0) totalMessageLength / messageCount else 0
                ),
                "environment" to mapOf(
                    "deviceType" to deviceInfo.deviceType,
                    "os" to "Android",
                    "osVersion" to deviceInfo.osVersion,
                    "sdkVersion" to deviceInfo.sdkVersion,
                    "deviceModel" to deviceInfo.deviceModel,
                    "manufacturer" to deviceInfo.manufacturer,
                    "language" to deviceInfo.language,
                    "timezone" to deviceInfo.timezone
                )
            )
        )

        client.emitAnalyticsEvent("finalize-analytics", data)
    }
}
