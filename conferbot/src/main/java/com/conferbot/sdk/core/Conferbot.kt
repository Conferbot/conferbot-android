package com.conferbot.sdk.core

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.conferbot.sdk.core.analytics.ChatAnalytics
import com.conferbot.sdk.core.nodes.NodeHandlerRegistry
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.queue.OfflineManager
import com.conferbot.sdk.core.queue.OfflineManagerListener
import com.conferbot.sdk.core.queue.QueuedMessage
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.core.state.MemoryUsageInfo
import com.conferbot.sdk.core.state.PaginatedMessageManager
import com.conferbot.sdk.core.state.PaginationConfig
import com.conferbot.sdk.core.state.PaginationState
import com.conferbot.sdk.models.*
import com.conferbot.sdk.notifications.ConferbotNotification
import com.conferbot.sdk.notifications.ConferbotNotificationManager
import com.conferbot.sdk.notifications.NotificationHandler
import com.conferbot.sdk.notifications.NotificationListener
import com.conferbot.sdk.notifications.NotificationSettings
import com.conferbot.sdk.notifications.NotificationTokenListener
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.services.ApiClient
import com.conferbot.sdk.services.FileUploadManager
import com.conferbot.sdk.services.FileUploadService
import com.conferbot.sdk.services.KnowledgeBaseService
import com.conferbot.sdk.services.SocketClient
import com.conferbot.sdk.ui.theme.ConferbotBackground
import com.conferbot.sdk.ui.theme.ConferbotTheme
import com.conferbot.sdk.ui.theme.ConferbotThemeBuilder
import com.conferbot.sdk.ui.views.ChatActivity
import com.conferbot.sdk.utils.ConferBotEndpoints
import com.conferbot.sdk.utils.Constants
import com.google.gson.Gson
import io.socket.emitter.Emitter
import com.conferbot.sdk.core.agent.AIReplyExtras
import com.conferbot.sdk.core.agent.AgentBot
import com.conferbot.sdk.core.agent.AgentBotParser
import com.conferbot.sdk.core.agent.AnswerFeedback
import com.conferbot.sdk.core.agent.HandoverPayload
import com.conferbot.sdk.utils.ChatMarkdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Parsed server-side chatbot customization data.
 *
 * Populated from the `chatbotData.customizations` object received in the
 * `fetched-chatbot-data` socket event.
 */
data class ServerChatbotCustomization(
    val avatarUrl: String? = null,
    val avatarSize: Int? = null,
    val hideAvatar: Boolean = false,
    val logoUrl: String? = null,
    val logoText: String? = null,
    val botName: String? = null,
    val font: String? = null,
    val fontSize: Int? = null,
    val bubbleBorderRadius: Int? = null,
    val chatBgType: String? = null,
    val hideBrand: Boolean = false,
    val enableCustomBrand: Boolean = false,
    val customBrand: String? = null,
    val enableTagline: Boolean = false,
    val tagline: String? = null,
    val gradientBgOne: String? = null,
    val gradientBgTwo: String? = null,
    val chatBgImg: String? = null,
    // Widget FAB customizations
    val widgetIconBgColor: String? = null,
    val headerBgColor: String? = null,       // fallback for FAB color
    val widgetBorderRadius: Int? = null,
    val widgetSize: Int? = null,
    val widgetPosition: String? = null,       // "left" or "right"
    val widgetOffsetRight: Int? = null,
    val widgetOffsetLeft: Int? = null,
    val widgetOffsetBottom: Int? = null,
    val widgetIconSVG: String? = null,        // e.g. "WidgetBubbleIcon10"
    val widgetIconType: String? = null,       // "Icon" or "Image"
    val widgetIconImage: String? = null,      // URL when widgetIconType = "Image"
    val chatIconCtaText: String? = null,
    val widgetIconThemeType: String? = null,  // "Solid" or "Gradient"
)

/**
 * Main Conferbot SDK singleton
 *
 * Enhanced with:
 * - Message pagination support to prevent OOM crashes
 * - Memory management for background/low memory scenarios
 * - Efficient message loading with lazy pagination
 */
object Conferbot {
    private const val TAG = "ConferBot"

    /** How long the "writing a reply" dots may wait for the AI. */
    const val AWAIT_REPLY_MS = AgentBotParser.AWAIT_REPLY_MS

    // Configuration
    private var apiKey: String? = null
    private var botId: String? = null

    /** The bot this SDK was initialized with (per-bot state such as the greeting card's). */
    val activeBotId: String? get() = botId
    private var config: ConferBotConfig = ConferBotConfig()
    private var customization: ConferBotCustomization? = null
    private var user: ConferBotUser? = null
    private var baseUrl: String? = null
    private var socketUrl: String? = null

    /** The server's origin: relative file links (the /media proxy) open on it. */
    val serverOrigin: String
        get() = (socketUrl ?: ConferBotEndpoints.socketUrl).trimEnd('/')

    // Application context for memory management
    private var appContext: Context? = null

    // Services
    private var apiClient: ApiClient? = null
    private var socketClient: SocketClient? = null

    // Paginated message manager
    private var messageManager: PaginatedMessageManager? = null

    // Knowledge Base service
    private var _knowledgeBaseService: KnowledgeBaseService? = null

    // File Upload service
    private var _fileUploadService: FileUploadService? = null
    private var _fileUploadManager: FileUploadManager? = null

    /**
     * Access to file upload manager for UI components
     */
    val fileUploadManager: FileUploadManager?
        get() = _fileUploadManager

    /**
     * Access to file upload service for direct uploads
     */
    val fileUploadService: FileUploadService?
        get() = _fileUploadService

    // Offline Manager for queue support
    private var _offlineManager: OfflineManager? = null

    // Notification components
    private var _notificationManager: ConferbotNotificationManager? = null
    private var _notificationHandler: NotificationHandler? = null

    // Current push token
    private var currentPushToken: String? = null

    // Pagination configuration
    private var paginationConfig = PaginationConfig()

    // Node flow engine for processing chatbot nodes
    private var _flowEngine: NodeFlowEngine? = null

    // Cached chatbot data (nodes/edges) from fetched-chatbot-data event
    private var cachedChatbotNodes: List<Map<String, Any>>? = null
    private var cachedChatbotEdges: List<Map<String, Any>>? = null
    private var cachedWorkspaceId: String? = null
    private var flowStarted = false
    // The session came back from the device (restorePersistedSession).
    private var restoredConversation = false

    /**
     * The visitor already has a conversation here: one put back after a
     * restart, or one they have written in (auto-open leaves it to them).
     */
    val chatStarted: Boolean
        get() = restoredConversation || _record.value.any {
            it is RecordItem.UserMessage || it is RecordItem.UserInputResponse || it is RecordItem.UserLiveMessage || it is RecordItem.UserLiveMessageFile
        }

    /**
     * Access to the flow engine for UI components
     * Allows direct observation of node states and submission of responses
     */
    val flowEngine: NodeFlowEngine?
        get() = _flowEngine

    // Keep backwards compatible alias
    private val nodeFlowEngine: NodeFlowEngine?
        get() = _flowEngine

    // State
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _chatSessionId = MutableStateFlow<String?>(null)
    val chatSessionId: StateFlow<String?> = _chatSessionId.asStateFlow()

    private val _record = MutableStateFlow<List<RecordItem>>(emptyList())
    val record: StateFlow<List<RecordItem>> = _record.asStateFlow()

    private val _currentAgent = MutableStateFlow<Agent?>(null)
    val currentAgent: StateFlow<Agent?> = _currentAgent.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isChatVisible = MutableStateFlow(false)
    /**
     * Whether the chat overlay (widget) is currently visible on screen.
     * Updated by [ConferBotWidget] when the overlay opens or closes.
     */
    val isChatVisible: StateFlow<Boolean> = _isChatVisible.asStateFlow()

    private val _isAgentTyping = MutableStateFlow(false)
    val isAgentTyping: StateFlow<Boolean> = _isAgentTyping.asStateFlow()

    private val _isLiveChatMode = MutableStateFlow(false)
    val isLiveChatMode: StateFlow<Boolean> = _isLiveChatMode.asStateFlow()

    // ========== AI Agent Bots (Agent Studio) ==========
    // A bot with no flow, answered by an AI agent from the first message
    // (core/agent/AgentBot.kt). Null for every flow bot.

    private val _agentBot = MutableStateFlow<AgentBot?>(null)
    val agentBot: StateFlow<AgentBot?> = _agentBot.asStateFlow()

    /** The AI reply as it is being written (one live bubble), or null. */
    data class AgentStream(val id: String, val text: String, val agentDetails: AgentDetails)
    private val _agentStream = MutableStateFlow<AgentStream?>(null)
    val agentStream: StateFlow<AgentStream?> = _agentStream.asStateFlow()

    /** One-tap questions: the greeting's starters, then each answer's follow-ups. */
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    /** Waiting for the AI's reply (the typing dots), at most [AWAIT_REPLY_MS]. */
    private val _awaitingReply = MutableStateFlow(false)
    val awaitingReply: StateFlow<Boolean> = _awaitingReply.asStateFlow()
    private var awaitReplyJob: Job? = null

    /** The server customizations as sent (hideAnswerFeedback, chatQuickActions, ...). */
    private val _serverCustomizationMap = MutableStateFlow<Map<String, Any?>>(emptyMap())
    val serverCustomizationMap: StateFlow<Map<String, Any?>> = _serverCustomizationMap.asStateFlow()

    /**
     * The help centre's categories and recent articles, from
     * fetched-chatbot-data (core/helpcentre/HelpCentre.kt). Empty while the
     * owner has it off.
     */
    private val _knowledgeBase = MutableStateFlow<com.conferbot.sdk.models.KnowledgeBaseData?>(null)
    val knowledgeBase: StateFlow<com.conferbot.sdk.models.KnowledgeBaseData?> = _knowledgeBase.asStateFlow()

    /** An article to open in the help centre (an AI source, an article card). */
    data class HelpArticleRequest(val articleId: String, val title: String?, val fromChat: Boolean, val at: Long = System.nanoTime())

    private val _helpArticleRequest = MutableStateFlow<HelpArticleRequest?>(null)
    val helpArticleRequest: StateFlow<HelpArticleRequest?> = _helpArticleRequest.asStateFlow()

    /** The bot's settings as the web widget applies them (core/customization); defaults until it loads. */
    private val _customizations = MutableStateFlow(com.conferbot.sdk.core.customization.ResolvedCustomizations())
    val customizations: StateFlow<com.conferbot.sdk.core.customization.ResolvedCustomizations> = _customizations.asStateFlow()

    /** The settings exactly as sent, before the widget theme's palette (applyLook). */
    private var rawCustomizationMap: Map<String, Any?> = emptyMap()
    private var rawCustomizationJson: JSONObject? = null
    /** The phone's dark mode: a theme set to "auto" follows it. */
    @Volatile private var prefersDark = false

    /**
     * The phone switched between light and dark: a bot whose theme follows
     * the phone ("auto") repaints. The chat screens call this; apps need not.
     */
    fun setSystemDarkMode(dark: Boolean) {
        if (dark == prefersDark) return
        prefersDark = dark
        if (rawCustomizationJson != null) applyLook()
    }

    /**
     * The widget theme over the bot's settings, as the web widget does
     * (core/customization/WidgetTheme): the theme palette in place of the
     * owner's colours when chosen, then the resolved look and the chat theme.
     */
    private fun applyLook() {
        val raw = rawCustomizationJson ?: return
        val themed = personalized(com.conferbot.sdk.core.customization.WidgetTheme.withThemePalette(rawCustomizationMap, prefersDark))
        val widgetTheme = com.conferbot.sdk.core.customization.WidgetTheme.resolve(themed, prefersDark)
        _serverCustomizationMap.value = themed
        _customizations.value = com.conferbot.sdk.core.customization.Customizations.resolve(themed, theme = widgetTheme)
        // The owner's font over the theme's, fetched at run time (core/customization/OwnerFont.kt).
        appContext?.let { com.conferbot.sdk.ui.theme.ChatFontLoader.apply(it, themed["font"], widgetTheme?.font, widgetTheme?.fontRole) }
        val json = if (themed === rawCustomizationMap) raw else JSONObject(raw.toString()).also { j ->
            themed.forEach { (k, v) -> if (rawCustomizationMap[k] != v && (v == null || v is String)) j.put(k, v ?: JSONObject.NULL) }
        }
        parseServerCustomizations(json, widgetTheme)
    }

    /**
     * "Hi {{first_name|there}}": the app's user and a name the chat asked
     * for fill the owner's texts - the greeting card and the home screen
     * (core/customization/Personalize.kt, web ownerLayout.ts).
     */
    private fun personalized(c: Map<String, Any?>): Map<String, Any?> =
        com.conferbot.sdk.core.customization.Personalize.customizations(
            c,
            com.conferbot.sdk.core.customization.Personalize.visitorVars(
                com.conferbot.sdk.core.customization.Personalize.userContext(user?.metadata, user?.name),
                ChatState.getUserMetadata("name")
            )
        )

    /**
     * The language the SDK draws its own words in: the app's
     * `config.widgetLanguage`, else the owner's `widgetLanguage`, "auto" (and
     * every bot that never chose) the phone's languages, else English (web
     * index.ts setWidgetLanguage). Screens redraw on [com.conferbot.sdk.core.i18n.I18n.languageFlow].
     */
    private fun applyWidgetLanguage(ownerSetting: Any?) {
        com.conferbot.sdk.core.i18n.I18n.setWidgetLanguage(config.widgetLanguage ?: ownerSetting)
    }

    /** The language the SDK's own words are drawn in ("en", "nl", "fr", "de" or "es"). */
    val widgetLanguage: String get() = com.conferbot.sdk.core.i18n.I18n.language

    /** True once the bot's own settings arrived (the screens keep their host-app look until then). */
    val hasServerCustomizations: Boolean get() = _serverCustomizationMap.value.isNotEmpty()

    // The silent handover waits for the visitor to engage (every record sent
    // becomes a Response - a chat on the customer's plan); live sends wait
    // for its acceptance.
    private var agentPendingHandover: Map<String, Any?>? = null
    private var agentHandoverPending = false
    private var agentStartedFor: String? = null

    // ========== Server Theme & Customization State ==========

    private val _serverTheme = MutableStateFlow<ConferbotTheme?>(null)
    /**
     * Theme built from server-side customization data.
     * Null until the `fetched-chatbot-data` event is received and parsed.
     * Collect this in Compose UI and wrap content with [ConferbotThemeProvider]
     * to apply server colors automatically.
     */
    val serverTheme: StateFlow<ConferbotTheme?> = _serverTheme.asStateFlow()

    /** The server theme before the chat's fonts are put in (they load later). */
    private var serverThemeBase: ConferbotTheme? = null

    private val _serverCustomization = MutableStateFlow<ServerChatbotCustomization?>(null)
    /**
     * Parsed server customization metadata (avatar URL, logo, bot name, etc.)
     * that doesn't map directly to theme colors but is useful for the chat header.
     */
    val serverCustomization: StateFlow<ServerChatbotCustomization?> = _serverCustomization.asStateFlow()

    // ========== Offline Mode State ==========

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _pendingMessageCount = MutableStateFlow(0)
    val pendingMessageCount: StateFlow<Int> = _pendingMessageCount.asStateFlow()

    private val _isSyncingQueue = MutableStateFlow(false)
    val isSyncingQueue: StateFlow<Boolean> = _isSyncingQueue.asStateFlow()

    // ========== Pagination State ==========

    private val _paginationState = MutableStateFlow(PaginationState())
    val paginationState: StateFlow<PaginationState> = _paginationState.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // Node flow engine state flows (exposed for UI consumption)
    val currentUIState: StateFlow<NodeUIState?>
        get() = nodeFlowEngine?.currentUIState ?: MutableStateFlow(null)

    val isProcessingNode: StateFlow<Boolean>
        get() = nodeFlowEngine?.isProcessing ?: MutableStateFlow(false)

    val nodeErrorMessage: StateFlow<String?>
        get() = nodeFlowEngine?.errorMessage ?: MutableStateFlow(null)

    val isFlowComplete: StateFlow<Boolean>
        get() = nodeFlowEngine?.isFlowComplete ?: MutableStateFlow(false)

    // Event listener
    private var eventListener: ConferBotEventListener? = null

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ========== Live chat extras ==========
    // Queue position, leave a message, receipts, locks, file requests, the
    // lead card and the post-chat rating (core/livechat).
    val liveChat: com.conferbot.sdk.core.livechat.LiveChatExtras by lazy {
        com.conferbot.sdk.core.livechat.LiveChatExtras(
            scope = scope,
            socket = { socketClient },
            sessionId = { _chatSessionId.value },
            addMessage = { addMessageToRecord(it) },
            continueFlow = { nodeFlowEngine?.continueAfterHandover() },
            postTranscriptEmail = { sid, email ->
                apiClient?.sendTranscriptEmail(sid, email) ?: com.conferbot.sdk.core.livechat.EmailTranscript.Result(false)
            },
            // queue-status's help: the server's pick for the help card.
            onQueueStatus = { waiting.onQueueStatus(it) }
        )
    }

    // ========== Handover lifecycle ==========
    // The "Connecting you with the team…" line and its outcome, the 20s
    // waiting signal, leaving the queue, and a live chat picked back up after
    // a restart (core/livechat/HandoverState.kt, HandoverSignals.kt).
    private val handover: com.conferbot.sdk.core.livechat.HandoverLifecycle by lazy {
        com.conferbot.sdk.core.livechat.HandoverLifecycle(
            socket = { socketClient?.let { HandoverSocketAdapter(it) } },
            flow = { nodeFlowEngine },
            host = handoverHost,
            timer = mainTimer,
            // Where a wait for a person begins and ends: the help card 6s in.
            waitHooks = object : com.conferbot.sdk.core.livechat.WaitHooks {
                override fun start() = waiting.startWait()
                override fun end() = waiting.endWait()
            },
            // An agent bot's inactivity close only pauses it (no flow to go on with).
            isAgentBot = { _agentBot.value != null },
            // The escalation's handover-timeout names the bot's workspace.
            workspaceId = { cachedWorkspaceId ?: ChatState.workspaceId },
            // A restored wait's countdown and max wait go on from the server's seconds left.
            onRestoredWait = { liveChat.resumeWaitWith(it) }
        )
    }

    private val mainTimer = object : com.conferbot.sdk.core.livechat.SignalTimer {
        override fun set(ms: Long, fn: () -> Unit): Any = scope.launch { delay(ms); fn() }
        override fun clear(handle: Any) { (handle as? Job)?.cancel() }
    }

    // ========== Post-chat redirect ==========
    // The conversation ended (the flow's end, or an agent ending a chat with
    // no flow going on from it) and the post-chat rating is sent or put away
    // (or there is none): the app's onPostChatRedirect, else "Taking you to
    // example.com in 10s" with Cancel, then the system browser
    // (core/livechat/PostChatRedirect.kt; web ts/chat/postChatRedirect.ts).
    /** The bot's redirectUrl (fetched-chatbot-data). */
    @Volatile private var redirectUrl: Any? = null

    private val redirectStore = object : com.conferbot.sdk.core.livechat.RedirectStore {
        private fun prefs() = appContext?.getSharedPreferences("conferbot_redirect", Context.MODE_PRIVATE)
        override fun read(key: String): String? = prefs()?.getString(key, null)
        override fun write(key: String, value: String) { prefs()?.edit()?.putString(key, value)?.apply() }
    }

    /** The redirect's line under the conversation (null: none), its Cancel and Continue. */
    val postChatRedirect: com.conferbot.sdk.core.livechat.PostChatRedirectController by lazy {
        com.conferbot.sdk.core.livechat.PostChatRedirectController(
            hooks = com.conferbot.sdk.core.livechat.PostChatRedirectController.Hooks(
                redirectUrl = { redirectUrl },
                sessionId = { _chatSessionId.value },
                // On screen, on the conversation (not the home screen or the help centre).
                chatVisible = { _isChatVisible.value && homeView.value == com.conferbot.sdk.core.home.HomeView.CHAT },
                blocked = { liveChat.blocked.value != null },
                // A person in the chat, or one on their way.
                live = {
                    _isLiveChatMode.value || !com.conferbot.sdk.core.livechat.HandoverState.talkToPersonAllowed(
                        com.conferbot.sdk.core.livechat.HandoverState.entriesOfItems(_record.value)
                    )
                },
                ratingPending = { liveChat.ratingPending },
                open = { href -> openOutsideChat(href) },
                disabled = { config.disablePostChatRedirect },
                onPostChatRedirect = { config.onPostChatRedirect },
                store = { if (appContext != null) redirectStore else null },
                appInForeground = {
                    try {
                        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                            .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                    } catch (e: Exception) {
                        true
                    }
                }
            ),
            timer = mainTimer
        )
    }

    /** The system browser (never the chat's own WebView); false when nothing can open it. */
    private fun openOutsideChat(href: String): Boolean {
        val context = appContext ?: return false
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(href)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /** The conversation on screen ended: the redirect, once the rating is done. */
    private fun conversationEnded() {
        postChatRedirect.conversationEnded()
    }

    /** What the redirect reads changed, and the app going to the background stops it. */
    private fun startPostChatRedirect() {
        scope.launch {
            kotlinx.coroutines.flow.merge(
                liveChat.csatOpen, liveChat.csatSent, liveChat.csatDue, liveChat.blocked,
                _isChatVisible, _isLiveChatMode, _chatSessionId, _record, homeView
            ).collect { postChatRedirect.update() }
        }
        scope.launch {
            try {
                androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
                    androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) postChatRedirect.cancel()
                    }
                )
            } catch (e: Exception) {
                // no process lifecycle (tests): the chat's own close stops it
            }
        }
    }

    // ========== While you wait ==========
    // The help card 6s into a wait for a person (or the server's queue-help
    // pick with "That solved it") and the waiting games 2.5s after the
    // waiting signal (core/waiting). Screen-only: never saved or recorded.
    @Volatile private var waitingCardsShown = 0

    /** A chat that draws the while-you-wait cards is on screen (the Compose chat). */
    internal fun setWaitingCardsShown(on: Boolean) {
        waitingCardsShown = (waitingCardsShown + if (on) 1 else -1).coerceAtLeast(0)
    }

    val waiting: com.conferbot.sdk.core.waiting.WaitingExtras by lazy {
        com.conferbot.sdk.core.waiting.WaitingExtras(
            socket = { socketClient?.let { HandoverSocketAdapter(it) } },
            sessionId = { _chatSessionId.value },
            customizations = { _serverCustomizationMap.value },
            record = { ChatState.getRecordForServer() },
            joined = { handover.phase() == com.conferbot.sdk.core.livechat.HandoverPhase.JOINED },
            lastItemId = { _record.value.lastOrNull()?.id },
            leftQueueHelped = { handover.leftQueueHelped() },
            reducedMotion = {
                appContext?.let {
                    runCatching { android.provider.Settings.Global.getFloat(it.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
                } ?: false
            },
            // The XML chat draws no cards: there "play" is sent as words.
            canDraw = { waitingCardsShown > 0 },
            botId = { botId },
            timer = mainTimer,
            post = { fn -> scope.launch { fn() } }
        ).also { w ->
            listOf(
                com.conferbot.sdk.core.livechat.HandoverSignals.WAITING_FOR_HUMAN_EVENT,
                com.conferbot.sdk.core.livechat.HandoverSignals.HUMAN_JOINED_EVENT,
                com.conferbot.sdk.core.livechat.HandoverSignals.WAIT_OVER_EVENT
            ).forEach { name -> handover.onWidgetSignal(name) { w.signal(it) } }
        }
    }

    // ========== Sounds ==========
    // A message arriving, the chat opening and closing, the visitor sending,
    // under the owner's messageSound / soundPack / launcher sound, and the
    // visitor's Sound on / off kept on the device (core/sound).
    val sounds: com.conferbot.sdk.core.sound.ChatSounds by lazy {
        com.conferbot.sdk.core.sound.ChatSounds(
            prefs = { appContext?.getSharedPreferences("conferbot_prefs", Context.MODE_PRIVATE) },
            context = { appContext },
            customizations = { _serverCustomizationMap.value }
        )
    }

    // ========== Human touches ==========
    // The bot's tapback, the celebration, the waiting nudge and "Edit" on the
    // latest answer (core/delight; web ts/chat/delight/). Screen-only.
    val delight: com.conferbot.sdk.core.delight.DelightExtras by lazy {
        com.conferbot.sdk.core.delight.DelightExtras(
            customizations = { _serverCustomizationMap.value },
            engine = {
                nodeFlowEngine?.let { e ->
                    object : com.conferbot.sdk.core.delight.ChangeAnswerEngine {
                        override fun points() = e.questionPoints().map { Triple(it.id, it.nodeId, it.type) }
                        override fun typesSince(pointId: Int) = e.questionPoints().firstOrNull { it.id == pointId }?.let { e.typesSince(it) } ?: listOf("agent")
                        override fun rewindTo(pointId: Int) = e.rewindTo(pointId)
                    }
                }
            },
            record = { _record.value },
            setRecord = { fn ->
                val before = _record.value
                val after = fn(before)
                _record.value = after
                // The cut rows leave the stored chat too, or a restart brings them back (#88).
                val kept = after.mapTo(HashSet()) { it.id }
                val cut = before.map { it.id }.filterNot { it in kept }
                if (cut.isNotEmpty()) scope.launch { messageManager?.removeMessages(cut) }
            },
            locked = { _isLiveChatMode.value || handover.phase() != com.conferbot.sdk.core.livechat.HandoverPhase.NONE || nodeFlowEngine?.isEnded == true },
            chatVisible = { _isChatVisible.value && homeView.value == com.conferbot.sdk.core.home.HomeView.CHAT },
            reducedMotion = {
                appContext?.let {
                    runCatching { android.provider.Settings.Global.getFloat(it.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
                } ?: false
            },
            timer = mainTimer
        )
    }

    /** "Edit" under the visitor's latest answer: back to that question, asked again. */
    fun changeAnswer() = delight.changeAnswer()

    /** The visitor touched or scrolled the chat (the waiting nudge waits). */
    fun noteChatActivity() = delight.noteActivity()

    // ========== Typing preview ==========
    // While a person chats with the visitor, the agent sees the draft
    // (core/livechat/TypingPreview.kt; web index.ts updateVisitorDraft).
    private val draftSender by lazy {
        com.conferbot.sdk.core.livechat.TypingPreview.DraftSender(
            send = { draft ->
                val sid = _chatSessionId.value
                if (sid != null && socketClient?.isConnected == true) {
                    socketClient?.emit("visitor-draft", JSONObject().put("chatSessionId", sid).put("draft", draft))
                }
            },
            timer = mainTimer
        )
    }

    /** A person (not the AI agent) is in the conversation. */
    private val chattingWithPerson: Boolean
        get() = _isLiveChatMode.value && _currentAgent.value?.let { !it.id.startsWith("ai-agent-") } == true

    /**
     * The composer's text as it changes ("" once sent): the agent's typing
     * preview, never for a secret field ([sensitive]), and the waiting nudge.
     */
    fun updateVisitorDraft(text: String, sensitive: Boolean = false) {
        delight.setComposerValue(text)
        if (!sensitive && com.conferbot.sdk.core.livechat.TypingPreview.on(_serverCustomizationMap.value, chattingWithPerson)) draftSender.update(text)
        else draftSender.clear()
    }

    /**
     * A row of the while-you-wait card: an article opens in the chat's help
     * centre while it is on, else it is fetched by id and shown on its own
     * (`waiting.article`, #75); a page opens in the browser, from the card.
     */
    fun openWaitHelpItem(item: com.conferbot.sdk.core.waiting.WaitArticles.HelpItem) {
        waiting.opened(item)
        if (item.kind != "article") return
        val known = waiting.findArticle(item.id)?.get("title") as? String
        val title = known ?: item.title
        when (com.conferbot.sdk.core.helpcentre.HelpCentre.waitArticleRoute(_serverCustomizationMap.value, _knowledgeBase.value, item.id)) {
            com.conferbot.sdk.core.helpcentre.HelpCentre.WaitArticleRoute.HELP_CENTRE -> openHelpArticle(item.id, title, fromChat = true)
            com.conferbot.sdk.core.helpcentre.HelpCentre.WaitArticleRoute.BY_ID -> waiting.openArticleById(item.id, title)
            com.conferbot.sdk.core.helpcentre.HelpCentre.WaitArticleRoute.NOWHERE -> {}
        }
    }

    // ========== Your requests ==========
    // The visitor's own conversations with the team: the list, one request
    // with its reply box, live team replies, the unread count for a badge
    // (core/requests; web ts/home/requests/). In the chat menu once they
    // have one; [openRequests] for an app's own entry.
    /** The visitor id the handshake carried (the user's id, else this device's). */
    private var handshakeVisitorId: String? = null

    private val requestsStore = object : com.conferbot.sdk.core.requests.RequestsStore {
        private fun prefs() = appContext?.getSharedPreferences("conferbot_requests", Context.MODE_PRIVATE)
        override fun read(key: String): String? = prefs()?.getString(key, null)
        override fun write(key: String, value: String) { prefs()?.edit()?.putString(key, value)?.apply() }
    }

    val requests: com.conferbot.sdk.core.requests.RequestsExtras by lazy {
        com.conferbot.sdk.core.requests.RequestsExtras(
            botId = { botId },
            socket = { socketClient?.let { RequestsSocketAdapter(it) } },
            connected = { socketClient?.isConnected == true },
            // The id the records carry, else the handshake's.
            visitorId = { ChatState.visitorId ?: handshakeVisitorId },
            user = { user?.let { com.conferbot.sdk.core.requests.RequestsUser(it.id, it.email, it.hash) } },
            store = { requestsStore },
            chatSessionId = { _chatSessionId.value },
            chatEnded = {
                com.conferbot.sdk.core.livechat.LiveEvents.endedSinceLastLive(
                    com.conferbot.sdk.core.livechat.LiveEvents.kindsOf(ChatState.record.value)
                )
            },
            // A request that is the chat on screen: the conversation (past the home screen).
            openChat = { home.showChat() },
            post = { fn -> scope.launch { fn() } }
        )
    }

    // ========== Home screen ==========
    // What the chat opens on when the owner has it on (enableInitialPage):
    // the view (home, conversation, help centre), "Welcome back", the help
    // centre's data and the server's search inside articles (core/home; web
    // ts/home/). The home's requests card reads again when it comes into view.
    private val homeStore = object : com.conferbot.sdk.core.home.HomeStore {
        private fun prefs() = appContext?.getSharedPreferences("conferbot_home", Context.MODE_PRIVATE)
        override fun read(key: String): String? = prefs()?.getString(key, null)
        override fun write(key: String, value: String) { prefs()?.edit()?.putString(key, value)?.apply() }
    }

    val home: com.conferbot.sdk.core.home.HomeExtras by lazy {
        com.conferbot.sdk.core.home.HomeExtras(
            botId = { botId },
            customizations = { _serverCustomizationMap.value },
            chatSessionId = { _chatSessionId.value },
            store = { homeStore },
            socket = { socketClient?.let { RequestsSocketAdapter(it) } },
            connected = { socketClient?.isConnected == true },
            // A question from the home goes out once the chat has a session.
            send = { text ->
                scope.launch {
                    kotlinx.coroutines.withTimeoutOrNull(8_000) { _chatSessionId.first { it != null } }
                    sendMessage(text)
                }
            },
            onHomeShown = { requests.onHomeShown() },
            timer = mainTimer,
            post = { fn -> scope.launch { fn() } }
        )
    }

    /** The view the open chat shows: the home screen, the conversation or the help centre. */
    val homeView: StateFlow<com.conferbot.sdk.core.home.HomeView> by lazy {
        combine(_serverCustomizationMap, home.chosen) { c, _ -> home.viewFor(c) }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, home.view())
    }

    /** Back to the home screen (nothing when the owner has none). */
    fun showHome() = home.showHome()

    /** To the conversation; with [text], asked there as the visitor. */
    fun showChat(text: String? = null) = home.showChat(text)

    /** The help centre, on one topic (its category id) or its list. */
    fun showHelp(categoryId: String? = null) = home.showHelp(categoryId)

    /** The AI agent as the home screen offers it (null for a flow bot). */
    internal fun homeAgent(bot: AgentBot? = _agentBot.value): com.conferbot.sdk.core.home.HomeAgent? =
        bot?.let { com.conferbot.sdk.core.home.HomeAgent(it.displayName, it.starters, it.avatar) }

    /** "Your requests": the list, or one request by its id (the chat menu's item, or an app's own button). */
    fun openRequests(requestId: String? = null) = requests.open(requestId)

    /** Leave "Your requests". */
    fun closeRequests() = requests.close()

    /** Requests with a team reply not read on this device: for an app's badge. */
    val requestsUnread: StateFlow<Int> get() = requests.unread

    /** When the list is read: connected, the chat shown or hidden, and the chats the visitor wrote in remembered. */
    private fun startRequests() {
        // A name a flow asked for: "Welcome back, Jamie" (web _setUserMetaData "name").
        scope.launch { ChatState.userMetadata.collect { home.rememberName(it.name) } }
        requests.attach()
        scope.launch { _isConnected.collect { if (it) requests.onConnected() } }
        scope.launch { _isChatVisible.drop(1).collect { requests.onChatShown() } }
        scope.launch {
            combine(_chatSessionId, ChatState.record) { id, record -> id to record }.collect { (id, record) ->
                requests.onRecord(id, com.conferbot.sdk.core.livechat.LiveEvents.kindsOf(record))
            }
        }
    }

    private class RequestsSocketAdapter(private val client: SocketClient) : com.conferbot.sdk.core.requests.RequestsSocket {
        override fun ask(event: String, data: Map<String, Any?>, timeoutMs: Long, onAnswer: (Map<String, Any?>?) -> Unit) =
            client.emitWithAck(event, JSONObject(data), timeoutMs) { onAnswer(com.conferbot.sdk.core.livechat.LiveChatExtras.toMap(it as? JSONObject)) }
        override fun emit(event: String, data: Map<String, Any?>) = client.emit(event, JSONObject(data))
        override fun on(event: String, fn: (Map<String, Any?>?) -> Unit): () -> Unit {
            val listener = Emitter.Listener { args -> fn(com.conferbot.sdk.core.livechat.LiveChatExtras.toMap(args.firstOrNull() as? JSONObject)) }
            client.on(event, listener)
            return { client.off(event, listener) }
        }
    }

    private class HandoverSocketAdapter(private val client: SocketClient) : com.conferbot.sdk.core.livechat.HandoverSocket {
        override fun on(event: String, fn: (Map<String, Any?>?) -> Unit) {
            client.on(event, Emitter.Listener { args -> fn(com.conferbot.sdk.core.livechat.LiveChatExtras.toMap(args.firstOrNull() as? JSONObject)) })
        }
        override fun emit(event: String, data: Map<String, Any?>) = client.emit(event, JSONObject(data))
        override fun ask(event: String, data: Map<String, Any?>, timeoutMs: Long, onAnswer: (Map<String, Any?>?) -> Unit) =
            client.emitWithAck(event, JSONObject(data), timeoutMs) { onAnswer(com.conferbot.sdk.core.livechat.LiveChatExtras.toMap(it as? JSONObject)) }
        override fun joinRoom(chatSessionId: String) = client.joinChatRoom(chatSessionId, deviceInfo())
    }

    private val handoverHost = object : com.conferbot.sdk.core.livechat.HandoverHost {
        override fun items(): List<RecordItem> = _record.value
        override fun updateItems(transform: (List<RecordItem>) -> List<RecordItem>) {
            var before: List<RecordItem> = emptyList()
            var after: List<RecordItem> = emptyList()
            _record.update { cur -> before = cur; transform(cur).also { after = it } }
            if (after === before) return
            val old = before.associateBy { it.id }
            val changed = after.filter { old[it.id] != null && old[it.id] != it }
            if (changed.isNotEmpty()) scope.launch { changed.forEach { messageManager?.addMessage(it) } }
        }
        override fun addItem(item: RecordItem) {
            var added = false
            _record.update { cur ->
                if (cur.any { it.id == item.id }) cur
                else (cur + item).let { if (it.size > paginationConfig.maxMemoryMessages) it.takeLast(paginationConfig.maxMemoryMessages) else it }
                    .also { added = true }
            }
            if (added) scope.launch { messageManager?.addMessage(item) }
        }
        override fun record() = ChatState.record.value
        override fun saveEntry(entry: com.conferbot.sdk.core.state.RecordEntry) = ChatState.pushToRecord(entry)
        override fun sessionId(): String? = _chatSessionId.value
        override fun chatOpen(): Boolean = _isChatVisible.value
        override fun flowWaiting(): Boolean {
            val ui = nodeFlowEngine?.currentUIState?.value
            return !_isLiveChatMode.value && ui is NodeUIState.HumanHandover &&
                ui.state == NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT
        }
        override fun setLiveMode(on: Boolean) {
            _isLiveChatMode.value = on
            ChatState.setLiveChatMode(on)
        }
        override fun setAgent(agent: Agent?) { _currentAgent.value = agent }
    }

    private fun deviceInfo() = mapOf(
        "os" to "Android",
        "osVersion" to Build.VERSION.RELEASE,
        "sdkVersion" to Build.VERSION.SDK_INT.toString(),
        "deviceModel" to Build.MODEL
    )

    /**
     * The visitor leaves the queue (the wait card's Cancel): queue-self-resolve,
     * so the team stops seeing them waiting, then "You left the queue" and the
     * flow carries on. [onDone] gets false when a person had already picked the
     * chat up (the visitor stays).
     */
    fun leaveQueue(onDone: (Boolean) -> Unit = {}) = handover.leaveQueue { left -> scope.launch { onDone(left) } }

    /**
     * Listen to the widget's handover signals, as the web widget dispatches
     * them on its window: `cb:waiting-for-human` (20s into a wait for a
     * person, or the server's own handover-waiting), `cb:human-joined` and
     * `cb:wait-over` (com.conferbot.sdk.core.livechat.HandoverSignals). Returns
     * the unsubscribe.
     */
    fun onWidgetSignal(name: String, listener: (com.conferbot.sdk.core.livechat.WidgetSignal) -> Unit): () -> Unit =
        handover.onWidgetSignal(name, listener)

    /**
     * "Talk to a person" was tapped, or a handover took it off: it does not
     * come back on the quick actions strip for this conversation.
     */
    private val _talkToPersonRetired = MutableStateFlow(false)
    val talkToPersonRetired: StateFlow<Boolean> = _talkToPersonRetired.asStateFlow()
    fun retireTalkToPerson() { _talkToPersonRetired.value = true }

    // Gson for JSON parsing
    private val gson = Gson()

    /**
     * Get or create a persistent visitor ID that survives across sessions and app restarts.
     * The visitor ID is stored in SharedPreferences and is only cleared on app uninstall.
     */
    private fun getOrCreateVisitorId(context: Context): String {
        val prefs = context.getSharedPreferences("conferbot_prefs", Context.MODE_PRIVATE)
        val key = "conferbot_visitor_id"
        val existing = prefs.getString(key, null)
        if (!existing.isNullOrEmpty()) {
            return existing
        }
        val newId = "v_${System.currentTimeMillis().toString(36)}_${java.util.UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString(key, newId).apply()
        return newId
    }

    /**
     * Initialize the SDK
     */
    fun initialize(
        context: Context,
        apiKey: String,
        botId: String,
        config: ConferBotConfig = ConferBotConfig(),
        customization: ConferBotCustomization? = null,
        user: ConferBotUser? = null,
        baseUrl: String? = null,
        socketUrl: String? = null,
        paginationConfig: PaginationConfig = PaginationConfig()
    ) {
        // FIX 4: Throw on double-initialization instead of silently returning
        if (_isInitialized.value) {
            throw IllegalStateException("Conferbot SDK is already initialized. Call disconnect() before re-initializing.")
        }

        // FIX 4: Validate apiKey and botId
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        require(botId.isNotBlank()) { "botId must not be blank" }
        if (apiKey.length < 8) {
            Log.w(TAG, "API key looks unusual. Any non-empty key is accepted - the bot ID is the operative credential.")
        }

        // FIX 5: Wrap initialization in try-catch for cleanup on failure
        try {
            this.appContext = context.applicationContext
            prefersDark = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            this.apiKey = apiKey
            this.botId = botId
            this.config = config
            // The SDK's own words: the app's choice, else "auto" until the
            // bot's settings say otherwise (core/i18n/I18n.kt).
            applyWidgetLanguage(null)
            this.customization = customization
            this.user = user
            ChatState.visitorContext = user?.toVisitorContext()
            this.baseUrl = baseUrl
            this.socketUrl = socketUrl
            this.paginationConfig = paginationConfig

            // Initialize API client
            apiClient = ApiClient(
                apiKey = apiKey,
                botId = botId,
                baseUrl = baseUrl ?: ConferBotEndpoints.apiBaseUrl
            )

            // Initialize Socket client
            socketClient = SocketClient(
                apiKey = apiKey,
                botId = botId,
                socketUrl = socketUrl ?: ConferBotEndpoints.socketUrl,
                // The visitor as the records name it: the server refuses a
                // blocked one at the handshake (web core/socket.ts).
                visitorId = (user?.id ?: getOrCreateVisitorId(context.applicationContext)).also { handshakeVisitorId = it }
            ).also { client ->
                client.onVisitorBlocked = { scope.launch { onVisitorBlocked(mapOf("handshake" to true)) } }
            }

            // Initialize Node Flow Engine
            socketClient?.let { client ->
                _flowEngine = NodeFlowEngine(
                    client,
                    apiOrigin = { serverOrigin },
                    customizations = { _serverCustomizationMap.value },
                    readFile = { uri ->
                        try {
                            appContext?.contentResolver?.openInputStream(android.net.Uri.parse(uri))?.use { it.readBytes() }
                        } catch (e: Exception) {
                            null
                        }
                    }
                )
                // The flow's end is the conversation's end: the post-chat
                // rating 1.5 s later (web index.ts), then the redirect.
                _flowEngine?.onFlowEnd = {
                    liveChat.flowEnded()
                    conversationEnded()
                }
                // Initialize ChatAnalytics with socket client
                ChatAnalytics.setSocketClient(client)
            }

            // Initialize File Upload Service
            _fileUploadService = FileUploadService(
                apiKey = apiKey,
                botId = botId,
                context = context.applicationContext,
                baseUrl = baseUrl ?: ConferBotEndpoints.apiBaseUrl
            )

            // Initialize File Upload Manager
            _fileUploadService?.let { service ->
                _fileUploadManager = FileUploadManager(
                    context = context.applicationContext,
                    uploadService = service
                )
            }

            // Initialize Offline Manager if offline mode is enabled
            if (config.enableOfflineMode) {
                initializeOfflineManager(context.applicationContext)
            }

            // Initialize Notification components if enabled
            if (config.enableNotifications) {
                initializeNotifications(context.applicationContext)
            }

            // Configure ChatState pagination
            ChatState.configurePagination(
                maxMessages = paginationConfig.maxMemoryMessages,
                pageSizeConfig = paginationConfig.pageSize
            )

            if (config.autoConnect) {
                connectSocket()
            }
            startRequests()
            startPostChatRedirect()
            // The chat's fonts loaded: the theme draws with them (core/customization/OwnerFont.kt).
            scope.launch {
                com.conferbot.sdk.ui.theme.ChatFontLoader.fonts.collect { f ->
                    serverThemeBase?.let { _serverTheme.value = com.conferbot.sdk.ui.theme.ChatFontLoader.withFonts(it, f) }
                }
            }
            // A message arriving plays the owner's message sound (core/sound).
            scope.launch { _record.collect { sounds.onRecord(it) } }
            // The bot's tapback, the burst and "Edit" (core/delight).
            scope.launch { _record.collect { delight.onRecord(it) } }
            _flowEngine?.let { e ->
                scope.launch { e.currentUIState.collect { delight.onUiState(it) } }
                scope.launch { e.isProcessing.collect { delight.onBusy(it) } }
            }

            _isInitialized.value = true
            Log.d(TAG, "SDK initialized with pagination support")
        } catch (e: Exception) {
            // FIX 5: Clean up partial state on initialization failure
            Log.e(TAG, "SDK initialization failed, cleaning up", e)
            _isInitialized.value = false
            socketClient?.disconnect()
            socketClient = null
            apiClient = null
            _flowEngine = null
            _fileUploadService = null
            _fileUploadManager = null
            _offlineManager?.shutdown()
            _offlineManager = null
            _notificationManager = null
            _notificationHandler = null
            appContext = null
            throw e
        }
    }

    /**
     * Connect to socket server
     */
    private fun connectSocket() {
        socketClient?.connect()
        setupSocketListeners()
    }

    /**
     * Setup socket event listeners
     */
    private fun setupSocketListeners() {
        // Connection events
        socketClient?.on(SocketEvents.CONNECT, Emitter.Listener {
            _isConnected.value = true
            Log.d(TAG, "Socket connected")
            // Request chatbot data (nodes/edges) on connect, like the web widget does
            socketClient?.getChatbotData()
        })

        socketClient?.on(SocketEvents.DISCONNECT, Emitter.Listener {
            _isConnected.value = false
            Log.d(TAG, "Socket disconnected")
        })

        // Re-join chat room on reconnect (socket.io drops room membership on disconnect)
        socketClient?.setReconnectionListener {
            _isConnected.value = true
            val sessionId = _chatSessionId.value
            if (sessionId != null) {
                socketClient?.joinChatRoom(
                    chatSessionId = sessionId,
                    deviceInfo = mapOf(
                        "os" to "Android",
                        "osVersion" to Build.VERSION.RELEASE,
                        "sdkVersion" to Build.VERSION.SDK_INT.toString(),
                        "deviceModel" to Build.MODEL
                    )
                )
                Log.d(TAG, "Rejoined chat room on reconnect: $sessionId")
            }
            socketClient?.getChatbotData()
        }

        // Fetched chatbot data - contains nodes/edges for the flow graph
        socketClient?.on(SocketEvents.FETCHED_CHATBOT_DATA, Emitter.Listener { args ->
            handleFetchedChatbotData(args)
        })

        // Bot response - process through NodeFlowEngine for node-based messages
        socketClient?.on(SocketEvents.BOT_RESPONSE, Emitter.Listener { args ->
            handleBotResponse(args)
        })

        // Agent message - server sends {message, agentDetails, isFileInput, isAudioInput, agentMessageId}
        socketClient?.on(SocketEvents.AGENT_MESSAGE, Emitter.Listener { args ->
            handleAgentMessage(args)
        })

        // Agent accepted (embed-server sends agentDetails, not agent)
        socketClient?.on(SocketEvents.AGENT_ACCEPTED, Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject
            // The wait is over: the Connecting line settles (joined, or
            // unavailable when the AI takes the chat back) and the waiting
            // signal stops - before the agent bot's own check, as the web does.
            val plan = handover.onAccepted(data?.let { jsonObjectToMap(it) })
            // An agent bot's own AI agent taking the chat it already greeted:
            // nothing to announce.
            if (onAgentBotAccepted(data)) return@Listener
            data?.optJSONObject("agentDetails")?.let { agentJson ->
                // Map agentDetails to Agent
                val agent = Agent(
                    id = agentJson.optString("_id"),
                    name = agentJson.optString("name"),
                    email = agentJson.optString("email"),
                    avatar = agentJson.optString("avatar", null),
                    title = agentJson.optString("title", null)
                )
                _currentAgent.value = agent
                _isLiveChatMode.value = true
                ChatState.setLiveChatMode(true)

                // "<name> joined the chat" - not again for the same person (a
                // repeat after a reconnect), with "They can see your
                // conversation so far" after an AI handover.
                if (plan.announce) handover.addJoin(
                    AgentDetails(id = agent.id, name = agent.name, email = agent.email, avatar = agent.avatar, isAIAgent = plan.ai),
                    plan.afterAI
                )

                // Notify flow engine about agent connection
                nodeFlowEngine?.handleAgentAccepted(agent.name)
                eventListener?.onAgentJoined(agent)
            }
        })

        // Agent left
        socketClient?.on(SocketEvents.AGENT_LEFT, Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject
            if (!isOwnSession(data)) return@Listener
            // No reply is coming, so no "took too long" notice either (web endAwaitingReply).
            setAwaitingReply(false)
            val agent = _currentAgent.value
            // An AI handing the visitor to the team is not leaving them: the
            // Connecting line, not "<name> left the chat"; an AI pausing an
            // idle visitor (visitor_inactive) draws nothing (HandoverState).
            val kind = handover.onAgentLeft(data?.let { jsonObjectToMap(it) }, agent?.name)
            if (kind == com.conferbot.sdk.core.livechat.AgentLeftKind.LEFT) {
                _currentAgent.value = null
                agent?.let { eventListener?.onAgentLeft(it) }
            }
        })

        // Agent typing - server sends {chatSessionId, isTyping, agentDetails}
        socketClient?.on(SocketEvents.AGENT_TYPING_STATUS, Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject
            val isTyping = data?.optBoolean("isTyping", false) ?: false
            // Only apply if chatSessionId matches (or not present)
            val eventSessionId = data?.optString("chatSessionId", null)
            if (eventSessionId != null && eventSessionId != _chatSessionId.value) {
                return@Listener
            }
            _isAgentTyping.value = isTyping
            ChatState.setAgentTyping(isTyping)
            eventListener?.onTypingIndicator(isTyping)
        })

        setupAgentBotListeners()
        liveChat.setupListeners()
        handover.setupListeners()
        waiting.setupListeners()
        watchLiveChat()

        // Blocked by the workspace mid-chat (the handshake's refusal comes
        // through SocketClient.onVisitorBlocked).
        socketClient?.on("visitor-blocked", Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject
            if (!isOwnSession(data)) return@Listener
            val map = data?.let { jsonObjectToMap(it) }
            scope.launch { onVisitorBlocked(map) }
        })

        // Chat ended
        socketClient?.on(SocketEvents.CHAT_ENDED, Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject
            if (!isOwnSession(data)) return@Listener
            setAwaitingReply(false)
            // The same end again (an agent's double click, an AI close racing
            // a person's): one "Chat has ended" line and one survey, not two
            // (web endedSinceLastLive).
            if (com.conferbot.sdk.core.livechat.LiveEvents.endedSinceLastLive(
                    com.conferbot.sdk.core.livechat.LiveEvents.kindsOf(ChatState.record.value)
                )
            ) return@Listener
            _currentAgent.value = null
            _isLiveChatMode.value = false
            _isAgentTyping.value = false
            ChatState.setLiveChatMode(false)
            ChatState.setAgentTyping(false)

            // Add "Chat has ended" system message
            // Its own id per end: under one id a second live chat's end
            // merged into the first one's entry (web agentListeners.ts).
            val endedMessage = RecordItem.SystemMessage(
                id = com.conferbot.sdk.core.livechat.LiveEvents.eventId("agent-chat-ended"),
                time = java.util.Date(),
                // Drawn in the visitor's language; the record below keeps the English line.
                text = com.conferbot.sdk.core.livechat.HandoverLifecycle.chatEndedLine(),
                kind = "agent-chat-ended"
            )
            addMessageToRecord(endedMessage)

            // Add to ChatState record for server sync (the web's agent-chat-ended)
            ChatState.pushToRecord(
                com.conferbot.sdk.core.state.RecordEntry(
                    id = endedMessage.id,
                    shape = "agent-chat-ended",
                    type = "agent-chat-ended",
                    text = "Chat has ended"
                )
            )

            // As on the web: a flow at its handover step goes on from it (its
            // post-chat survey questions, else its next step). With no flow
            // going on from it, the conversation is over (the redirect).
            val flowGoesOn = nodeFlowEngine?.isAtHandover == true
            nodeFlowEngine?.handleChatEnded()
            if (!flowGoesOn) scope.launch { conversationEnded() }
            eventListener?.onSessionEnded(_chatSessionId.value ?: "")
        })

        // No agents available (for human handover)
        socketClient?.on(SocketEvents.NO_AGENTS_AVAILABLE, Emitter.Listener {
            // The handover step says it with the owner's fallback message.
            // A flow at its handover step, as the web widget does it: the
            // owner's fallback as the bot's message, then "Leave a message"
            // (the flow carries on once it is sent or skipped), or straight
            // on when the owner turned that off.
            val engine = nodeFlowEngine
            if (engine != null && engine.isAtHandover) {
                // The step itself listens: it says the owner's fallback and
                // carries the flow on when "leave a message" is off.
                scope.launch { engine.clearHandoverUI() }
                return@Listener
            }
            // Add "No agents available" system message
            val noAgentsMessage = RecordItem.SystemMessage(
                id = "no-agents-${System.currentTimeMillis()}",
                time = java.util.Date(),
                text = com.conferbot.sdk.core.i18n.t("sdk.flow.noAgents")
            )
            addMessageToRecord(noAgentsMessage)

            nodeFlowEngine?.handleNoAgentsAvailable()
        })
    }

    /**
     * Handle bot response from socket - processes through NodeFlowEngine
     */
    private fun handleBotResponse(args: Array<Any>) {
        val data = args.firstOrNull() as? JSONObject ?: return
        try {
            // Convert JSONObject to Map for flow engine
            val messageMap = jsonObjectToMap(data)

            // Check if this is a node-based message (has nodeData or type field)
            val hasNodeData = messageMap.containsKey("nodeData") ||
                              (messageMap.containsKey("type") && NodeHandlerRegistry.hasHandler(messageMap["type"].toString()))

            if (hasNodeData && nodeFlowEngine != null) {
                // Process through flow engine for node-based UI
                nodeFlowEngine?.handleServerMessage(messageMap)
                return  // Don't also call handleMessageReceived to avoid duplicate messages
            }

            // Only add to record for non-node messages
            handleMessageReceived(args)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle bot response", e)
            // Fall back to standard message handling
            handleMessageReceived(args)
        }
    }

    /**
     * Handle message received from socket
     */
    private fun handleMessageReceived(args: Array<Any>) {
        val data = args.firstOrNull() as? JSONObject ?: return
        try {
            val recordItem = gson.fromJson(data.toString(), RecordItem::class.java)

            // Add to paginated message manager if available
            scope.launch {
                messageManager?.addMessage(recordItem)

                // Also update _record for backwards compatibility
                // Deduplication: skip if message with this ID already exists
                val existingIds = _record.value.map { it.id }.toSet()
                if (recordItem.id in existingIds) {
                    return@launch // Already exists, skip duplicate
                }

                val currentRecord = _record.value.toMutableList()
                currentRecord.add(recordItem)

                // Trim to prevent memory issues
                if (currentRecord.size > paginationConfig.maxMemoryMessages) {
                    _record.value = currentRecord.takeLast(paginationConfig.maxMemoryMessages)
                    _paginationState.value = _paginationState.value.copy(hasMoreMessages = true)
                } else {
                    _record.value = currentRecord
                }
            }

            // Increment unread count if chat is not open
            _unreadCount.value = _unreadCount.value + 1

            // Notify listener based on message type
            when (recordItem) {
                is RecordItem.BotMessage -> {
                    eventListener?.onMessageReceived(recordItem)
                }
                is RecordItem.AgentMessage -> {
                    eventListener?.onMessageReceived(recordItem)
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message", e)
        }
    }

    /**
     * Handle agent message from socket
     * Server sends: {message, agentDetails, isFileInput, isAudioInput, agentMessageId}
     */
    private fun handleAgentMessage(args: Array<Any>) {
        val data = args.firstOrNull() as? JSONObject ?: return
        try {
            val eventSessionId = data.optString("chatSessionId", "")
            if (eventSessionId.isNotEmpty() && _chatSessionId.value != null && eventSessionId != _chatSessionId.value) return
            val rawMessage = data.optString("message", "")
            // The inbox editor's HTML and the AI's markdown, as one markdown
            // subset the bubbles format (utils/ChatMarkdown.kt).
            val message = ChatMarkdown.htmlToMarkdown(rawMessage)
            val dataMap = jsonObjectToMap(data)
            // An AI reply's sources, buttons and cards; its follow-ups shown.
            val extras = AgentBotParser.aiReplyExtrasOf(dataMap)
            onAgentBotMessage(dataMap)
            val isFileInput = data.optBoolean("isFileInput", false)
            val isAudioInput = data.optBoolean("isAudioInput", false)
            val agentMessageId = data.optString("agentMessageId", "agent-msg-${System.currentTimeMillis()}")

            val agentDetailsJson = data.optJSONObject("agentDetails")
            val agentDetails = if (agentDetailsJson != null) {
                AgentDetails(
                    id = agentDetailsJson.optString("_id", ""),
                    name = agentDetailsJson.optString("name", "Agent"),
                    email = agentDetailsJson.optString("email", null),
                    avatar = agentDetailsJson.optString("avatar", null)?.takeIf { it.isNotBlank() },
                    isAIAgent = agentDetailsJson.optBoolean("isAIAgent", false) ||
                        agentDetailsJson.optString("_id", "").startsWith("ai-agent-")
                )
            } else {
                val currentAgentVal = _currentAgent.value
                AgentDetails(
                    id = currentAgentVal?.id ?: "",
                    name = currentAgentVal?.name ?: "Agent"
                )
            }

            val recordItem: RecordItem = when {
                isFileInput -> RecordItem.AgentMessageFile(
                    id = agentMessageId,
                    time = java.util.Date(),
                    file = message,
                    agentDetails = agentDetails
                )
                isAudioInput -> RecordItem.AgentMessageAudio(
                    id = agentMessageId,
                    time = java.util.Date(),
                    url = message,
                    agentDetails = agentDetails
                )
                else -> RecordItem.AgentMessage(
                    id = agentMessageId,
                    time = java.util.Date(),
                    text = message,
                    agentDetails = agentDetails,
                    extras = extras?.takeUnless { it.isEmpty && !it.isFallback },
                    translation = com.conferbot.sdk.core.livechat.LiveChat.agentTranslationOf(dataMap)
                )
            }

            addMessageToRecord(recordItem)

            // Add to ChatState record for server sync
            ChatState.pushToRecord(
                com.conferbot.sdk.core.state.RecordEntry(
                    id = agentMessageId,
                    shape = "agent-message",
                    type = recordItem.type.value,
                    text = message,
                    data = mutableMapOf(
                        "agentDetails" to mapOf(
                            "_id" to agentDetails.id,
                            "name" to agentDetails.name,
                            "email" to agentDetails.email,
                            "avatar" to agentDetails.avatar
                        )
                    )
                )
            )

            // Add to transcript
            ChatState.addToTranscript("agent", message)

            // Hide typing indicator when message arrives
            _isAgentTyping.value = false
            ChatState.setAgentTyping(false)

            // Increment unread count if chat is not visible
            if (!_isChatVisible.value) {
                _unreadCount.value = _unreadCount.value + 1
            }

            eventListener?.onMessageReceived(recordItem)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle agent message", e)
        }
    }

    // ==================== AI Agent Bots (Agent Studio) ====================

    private fun agentDetailsOf(bot: AgentBot) = AgentDetails(
        id = bot.agentDetailsId,
        name = bot.displayName,
        avatar = bot.avatar,
        isAIAgent = true
    )

    private fun setAwaitingReply(on: Boolean) {
        awaitReplyJob?.cancel()
        awaitReplyJob = null
        _awaitingReply.value = on
        if (on) awaitReplyJob = scope.launch {
            delay(AWAIT_REPLY_MS)
            awaitReplyJob = null
            _awaitingReply.value = false
            // Say so, instead of silently dropping the dots (web
            // agentListeners.ts): on screen only, not in the record.
            val notice = RecordItem.SystemMessage(id = AgentBotParser.replyTimeoutId(), time = java.util.Date(), text = AgentBotParser.replyTimeoutText())
            _record.update { it + notice }
        }
    }

    /**
     * Blocked by the workspace (visitor-blocked, or the handshake's refusal):
     * "This conversation has been closed.", the wait and the flow over, no
     * input and no new chat (web _applyVisitorBlock, LiveEvents).
     */
    private fun onVisitorBlocked(data: Map<String, Any?>?) {
        setAwaitingReply(false)
        _suggestions.value = emptyList()
        liveChat.block(data)
        handover.blocked()
        _currentAgent.value = null
        _isAgentTyping.value = false
    }

    /**
     * "Start a new chat" once a chat has closed: a fresh conversation, as
     * the chat menu's Restart Chat.
     */
    fun startNewChat() {
        clearHistory()
        scope.launch { initializeSession() }
    }

    /**
     * Open the agent chat for this session on screen: the greeting and the
     * starters. Nothing is sent yet - only a person starts a conversation
     * (see [engageAgent]). A restored session already has its conversation:
     * back in the room only, nothing greeted or handed over again.
     */
    private fun startAgentBotChat(sessionId: String, bot: AgentBot, nodes: List<Map<String, Any>>) {
        if (agentStartedFor == sessionId) return
        agentStartedFor = sessionId
        val details = agentDetailsOf(bot)
        val resumed = _record.value.any {
            (it is RecordItem.AgentMessage && it.id.startsWith("agent-greeting-")) || it is RecordItem.UserLiveMessage
        }
        // A restored chat a person has (the AI handed it on): not the AI.
        if (!(resumed && handover.phase() == com.conferbot.sdk.core.livechat.HandoverPhase.JOINED)) {
            _currentAgent.value = Agent(id = details.id, name = details.name, avatar = details.avatar)
        }
        // Visitor messages go the live way from the start; they wait for the
        // agent's acceptance (agentHandoverPending).
        _isLiveChatMode.value = true
        ChatState.setLiveChatMode(true)

        if (resumed) {
            agentPendingHandover = null
            agentHandoverPending = false
            return
        }

        agentHandoverPending = true
        val greeting = bot.greeting.ifBlank { AgentBotParser.defaultGreeting() }
        @Suppress("UNCHECKED_CAST")
        val shell = AgentBotParser.agentShellNodes(nodes as List<Map<String, Any?>>, bot.agentId)
        val (welcome, handover) = shell
        val welcomeId = welcome?.get("id")?.toString() ?: "agent-welcome"
        @Suppress("UNCHECKED_CAST")
        val welcomeData = ((welcome?.get("data") as? Map<String, Any?>) ?: emptyMap()).toMutableMap().apply {
            put("text", greeting)
            put("disableImage", true)
            put("agentDetails", AgentBotParser.agentDetailsOf(bot))
        }
        ChatState.pushToRecord(
            com.conferbot.sdk.core.state.RecordEntry(id = welcomeId, shape = "welcome-node", type = "welcome-node", text = greeting, data = welcomeData)
        )
        ChatState.addToTranscript("bot", greeting)
        addMessageToRecord(
            RecordItem.AgentMessage(id = "agent-greeting-$sessionId", time = java.util.Date(), text = greeting, agentDetails = details)
        )
        _suggestions.value = bot.starters

        @Suppress("UNCHECKED_CAST")
        val shellData = handover?.get("data") as? Map<String, Any?>
        agentPendingHandover = mapOf(
            "id" to (handover?.get("id")?.toString() ?: "agent-handover"),
            "data" to AgentBotParser.agentHandoverData(shellData, bot.agentId)
        )
    }

    /**
     * The visitor is engaging (typing, a suggestion tapped, a message sent):
     * create the Response from the record, then the silent handover. Once.
     */
    fun engageAgent() {
        val step = agentPendingHandover ?: return
        if (socketClient?.isConnected != true) return
        agentPendingHandover = null
        @Suppress("UNCHECKED_CAST")
        val data = step["data"] as Map<String, Any?>
        socketClient?.sendResponseRecord(ChatState.buildResponseData())
        val id = step["id"].toString()
        ChatState.pushToRecord(
            com.conferbot.sdk.core.state.RecordEntry(id = id, shape = "human-handover-node", type = "human-handover-node", data = data.toMutableMap())
        )
        HandoverPayload.build(data, mapOf("silentStart" to true))?.let {
            socketClient?.emit("initiate-handover", JSONObject(it))
        }
    }

    /** agent-accepted for this bot's own AI agent: nothing drawn, held messages sent. */
    private fun onAgentBotAccepted(data: JSONObject?): Boolean {
        if (_agentBot.value == null) return false
        val json = data?.optJSONObject("agentDetails") ?: return false
        if (!json.optBoolean("isAIAgent", false) && !json.optString("_id", "").startsWith("ai-agent-")) return false
        val wasPending = agentHandoverPending
        agentHandoverPending = false
        _currentAgent.value = Agent(
            id = json.optString("_id"),
            name = json.optString("name"),
            avatar = json.optString("avatar", "").takeIf { it.isNotBlank() }
        )
        _isLiveChatMode.value = true
        ChatState.setLiveChatMode(true)
        if (wasPending && ChatState.getRecordForServer().any { it["shape"] == "user-live-message" }) {
            socketClient?.sendResponseRecord(ChatState.buildResponseData())
            setAwaitingReply(true)
        }
        return true
    }

    /** An agent message arrived: the wait and the live bubble end, its follow-ups show. */
    private fun onAgentBotMessage(data: Map<String, Any?>) {
        setAwaitingReply(false)
        _agentStream.value = null
        @Suppress("UNCHECKED_CAST")
        val details = data["agentDetails"] as? Map<String, Any?>
        _suggestions.value = if (AgentBotParser.isAIAgent(details) && data["isFallback"] != true) {
            AgentBotParser.followUpsOf(data["followUps"])
        } else emptyList()
    }

    /** The visitor said something: the chips go, the dots wait for the AI. */
    private fun noteVisitorMessage() {
        _suggestions.value = emptyList()
        if (_agentBot.value != null &&
            AgentBotParser.isAgentAIActive(ChatState.getRecordForServer().map { it["type"] as? String })
        ) setAwaitingReply(true)
    }

    private fun isOwnSession(data: JSONObject?): Boolean {
        val sid = data?.optString("chatSessionId", "") ?: ""
        return sid.isEmpty() || _chatSessionId.value == null || sid == _chatSessionId.value
    }

    private fun agentDetailsFrom(json: JSONObject?): AgentDetails = AgentDetails(
        id = json?.optString("_id", "") ?: "",
        name = json?.optString("name", "Agent") ?: "Agent",
        avatar = json?.optString("avatar", "")?.takeIf { it.isNotBlank() },
        isAIAgent = json?.optBoolean("isAIAgent", false) == true || json?.optString("_id", "")?.startsWith("ai-agent-") == true
    )

    private fun setupAgentBotListeners() {
        // The reply as it is written: one live bubble, replaced by the finished agent-message.
        socketClient?.on("agent-message-stream", Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@Listener
            if (!isOwnSession(data)) return@Listener
            val text = data.optString("text", "")
            val streamId = data.optString("streamId", "")
            if (text.isBlank() || streamId.isEmpty()) return@Listener
            setAwaitingReply(false)
            _isAgentTyping.value = false
            _agentStream.value = AgentStream(streamId, ChatMarkdown.htmlToMarkdown(text), agentDetailsFrom(data.optJSONObject("agentDetails")))
        })

        // An article, quick replies or a card from the agent.
        socketClient?.on("agent-message-rich", Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@Listener
            if (!isOwnSession(data)) return@Listener
            val entry = data.optJSONObject("entry") ?: return@Listener
            val id = entry.optString("id", "")
            val entryMap = jsonObjectToMap(entry)
            val rich = AgentBotParser.safeRich(entryMap["rich"]) ?: return@Listener
            if (id.isEmpty()) return@Listener
            setAwaitingReply(false)
            _agentStream.value = null
            val text = entry.optString("text", "")
            addMessageToRecord(
                RecordItem.AgentRichMessage(id = id, time = java.util.Date(), rich = rich, text = text, agentDetails = agentDetailsFrom(entry.optJSONObject("agentDetails")))
            )
            @Suppress("UNCHECKED_CAST")
            ChatState.pushToRecord(
                com.conferbot.sdk.core.state.RecordEntry(
                    id = id, shape = "agent-message-rich", type = "agent-message-rich", text = text,
                    data = mutableMapOf("rich" to entryMap["rich"], "agentDetails" to entryMap["agentDetails"])
                )
            )
        })

        // An agent edited or unsent a message: the bubble changes in place.
        fun change(deleted: Boolean) = Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@Listener
            if (!isOwnSession(data)) return@Listener
            val id = data.optString("agentMessageId", "")
            if (id.isEmpty()) return@Listener
            val newText = data.optString("text", data.optString("message", ""))
            _record.value = _record.value.map { item ->
                if (item is RecordItem.AgentMessage && item.id == id) {
                    if (deleted) item.copy(text = "", deleted = true)
                    else item.copy(text = if (newText.isNotEmpty()) ChatMarkdown.htmlToMarkdown(newText) else item.text, edited = true)
                } else item
            }
        }
        socketClient?.on("agent-message-edited", change(false))
        socketClient?.on("agent-message-deleted", change(true))

        // Paused (or, for continue_flow, ended) for inactivity. The line and its
        // record entry are the handover's (HandoverLifecycle.inactiveClosed):
        // recorded once.
        socketClient?.on("ai-agent-inactive-close", Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject
            if (!isOwnSession(data)) return@Listener
            setAwaitingReply(false)
            _suggestions.value = emptyList()
        })
        socketClient?.on("ai-agent-resumed", Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject
            if (!isOwnSession(data)) return@Listener
            ChatState.pushToRecord(
                com.conferbot.sdk.core.state.RecordEntry(id = "ai-resumed-${System.currentTimeMillis()}", shape = "ai-agent-resumed", type = "ai-agent-resumed")
            )
            _isLiveChatMode.value = true
            ChatState.setLiveChatMode(true)
        })
    }

    private fun resetAgentBotChat() {
        agentStartedFor = null
        agentPendingHandover = null
        agentHandoverPending = false
        _agentStream.value = null
        _suggestions.value = emptyList()
        setAwaitingReply(false)
    }

    /** "Helpful?" on an AI answer: kept on the message, sent to the server. */
    fun sendAnswerFeedback(agentMessageId: String, helpful: Boolean, reason: String? = null) =
        sendAnswerFeedback(agentMessageId, AnswerFeedback(helpful, reason?.takeIf { !helpful }))

    /**
     * A vote on an AI answer (core/agent/AnswerFeedback.kt): kept on the
     * message and on its record entry (`feedback`, as the web keeps it, so
     * the conversation's record says how it was rated), and sent as
     * ai-answer-feedback.
     */
    fun sendAnswerFeedback(agentMessageId: String, feedback: AnswerFeedback) {
        _record.value = _record.value.map {
            if (it is RecordItem.AgentMessage && it.id == agentMessageId) it.copy(feedback = feedback) else it
        }
        scope.launch {
            _record.value.firstOrNull { it.id == agentMessageId }?.let { messageManager?.addMessage(it) }
        }
        val at = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        ChatState.setEntryField(agentMessageId, "feedback", com.conferbot.sdk.core.agent.Helpful.entryFeedback(feedback, at))
        socketClient?.emitWithAck("ai-answer-feedback", JSONObject(com.conferbot.sdk.core.agent.Helpful.payload(_chatSessionId.value, agentMessageId, feedback)), 10_000L)
    }

    // "Ask another way": the composer takes the focus (a counter the input watches).
    private val _composerFocus = MutableStateFlow(0)
    val composerFocus: StateFlow<Int> = _composerFocus.asStateFlow()
    fun focusComposer() { _composerFocus.value = _composerFocus.value + 1 }

    /**
     * The visitor's Confirm / Not now on an action the AI is about to take.
     * [onResult] gets (ok, code) on the main thread's caller scope.
     */
    fun decideAction(token: String, accept: Boolean, onResult: (Boolean, String?) -> Unit) {
        val payload = JSONObject().apply {
            put("chatSessionId", _chatSessionId.value)
            put("token", token)
            put("accept", accept)
        }
        socketClient?.emitWithAck("ai-action-decision", payload) { res ->
            val json = res as? JSONObject
            val ok = json?.optBoolean("ok", false) == true
            if (ok && accept) setAwaitingReply(true)
            scope.launch(Dispatchers.Main) { onResult(ok, json?.optString("code", null)) }
        } ?: onResult(false, null)
    }

    /** A tapped answer button or quick action, as a goal. */
    fun trackGoal(goalId: String, conversionEvent: String) {
        val payload = JSONObject().apply {
            put("chatSessionId", _chatSessionId.value)
            put("goalId", goalId)
            put("conversionEvent", conversionEvent)
        }
        socketClient?.emit("track-goal-completion", payload)
    }

    /** A quick reply chosen on a rich message: the chips freeze, the label is sent. */
    fun answerRichMessage(messageId: String, label: String) {
        _record.value = _record.value.map {
            if (it is RecordItem.AgentRichMessage && it.id == messageId) it.copy(answered = label) else it
        }
        sendMessage(label)
    }

    /**
     * Add a message to the record list and persist it
     */
    private var liveChatWatchers: List<Job> = emptyList()

    /**
     * The visitor's file in a live chat: copied from the picker, uploaded,
     * then sent as the web widget sends it. [onDone] gets an error message,
     * or null when it went, on the main thread.
     */
    fun sendLiveFile(uri: android.net.Uri, onDone: (String?) -> Unit = {}) {
        val context = appContext ?: return onDone(com.conferbot.sdk.core.i18n.t("sdk.live.notReady"))
        val sessionId = _chatSessionId.value ?: return onDone(com.conferbot.sdk.core.i18n.t("sdk.live.notReady"))
        val service = _fileUploadService ?: return onDone(com.conferbot.sdk.core.i18n.t("sdk.live.uploadsUnavailable"))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    var name = "file"
                    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) name = c.getString(0) ?: name
                    }
                    val tmp = java.io.File(context.cacheDir, "cb-upload-${System.currentTimeMillis()}-$name")
                    resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    val up = service.uploadDirect(tmp, name, mime, sessionId)
                    tmp.delete()
                    Triple(up, name, mime)
                }.getOrNull()
            }
            val (up, name, mime) = result ?: return@launch onDone(com.conferbot.sdk.core.i18n.t("sdk.live.fileFailed"))
            val url = up.url
            if (!up.success || url.isNullOrBlank()) return@launch onDone(com.conferbot.sdk.core.i18n.t("sdk.live.fileFailed"))
            liveChat.sendLiveFile(url, name, mime)
            onDone(null)
        }
    }

    /**
     * The queue is asked while a handover waits for a person; the visitor's
     * delivered / seen receipts go out as agent messages arrive and are read.
     */
    private fun watchLiveChat() {
        liveChatWatchers.forEach { it.cancel() }
        val ui = nodeFlowEngine?.currentUIState
        liveChatWatchers = listOfNotNull(
            ui?.let {
                scope.launch {
                    var wasWaiting = false
                    kotlinx.coroutines.flow.combine(it, _isLiveChatMode) { state, live -> state to live }.collect { (state, live) ->
                        val waiting = state is com.conferbot.sdk.core.nodes.NodeUIState.HumanHandover &&
                            state.state == com.conferbot.sdk.core.nodes.NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT
                        // A flow's handover waiting for a person: the 20s waiting signal.
                        if (waiting && !live && !wasWaiting) handover.flowWaitStarted(nodeFlowEngine?.handoverNodeData)
                        wasWaiting = waiting && !live
                        if (waiting && !live) {
                            liveChat.startQueuePoll()
                            liveChat.startWaitTimer(nodeFlowEngine?.handoverNodeData, cachedWorkspaceId ?: ChatState.workspaceId)
                        } else {
                            liveChat.stopQueuePoll()
                            liveChat.stopWaitTimer()
                        }
                    }
                }
            },
            scope.launch {
                // The home screen and the help centre are not the conversation (web
                // index.ts: the home screen must not count agent messages as seen).
                kotlinx.coroutines.flow.combine(_record, _isChatVisible, homeView) { r, v, view ->
                    r to (v && view == com.conferbot.sdk.core.home.HomeView.CHAT)
                }.collect { (r, v) ->
                    liveChat.reportReceipts(r, v)
                }
            }
        )
    }

    private fun addMessageToRecord(message: RecordItem) {
        scope.launch {
            // On screen first, in arrival order; saving suspends, and two
            // messages close together used to swap places while it did.
            val existingIds = _record.value.map { it.id }.toSet()
            if (message.id in existingIds) {
                return@launch
            }

            val currentRecord = _record.value.toMutableList()
            currentRecord.add(message)

            if (currentRecord.size > paginationConfig.maxMemoryMessages) {
                _record.value = currentRecord.takeLast(paginationConfig.maxMemoryMessages)
            } else {
                _record.value = currentRecord
            }

            messageManager?.addMessage(message)
        }
    }

    /**
     * Handle fetched-chatbot-data from socket
     * Parses the elements array to extract nodes and edges, then starts the flow
     * if the session is already initialized.
     *
     * Server sends: { chatbotData: { elements: [{ nodes: [...], edges: [...] }], ... }, knowledgeBaseData: {...} }
     */
    @Suppress("UNCHECKED_CAST")
    private fun handleFetchedChatbotData(args: Array<Any>) {
        val data = args.firstOrNull() as? JSONObject ?: return
        try {
            Log.d(TAG, "Received fetched-chatbot-data")

            // Server wraps chatbot data inside a 'chatbotData' key
            val chatbotData = data.optJSONObject("chatbotData") ?: data

            // The help centre: categories and recent articles, no bodies.
            val kbData = data.optJSONObject("knowledgeBaseData")?.let { jsonObjectToMap(it) }
            _knowledgeBase.value = com.conferbot.sdk.core.helpcentre.HelpCentre.parse(
                kbData,
                chatbotData.optJSONObject("customizations")?.let { jsonObjectToMap(it) }
            )
            // ...and as sent, for the while-you-wait articles and the quiz.
            waiting.setKnowledgeBase(kbData)
            // ...and for the home screen's cards and its ask box.
            home.setKnowledgeBase(kbData)
            // The bot's bookings: a calendar's taken slots (web index.ts setCalendarBookings).
            (chatbotData.optJSONArray("calendarBookings") ?: data.optJSONArray("calendarBookings"))?.let {
                nodeFlowEngine?.setCalendarBookings(jsonArrayToList(it))
            }

            // Parse elements array: elements is an array with one object containing nodes and edges
            val elementsArray = chatbotData.optJSONArray("elements")
            if (elementsArray == null || elementsArray.length() == 0) {
                Log.w(TAG, "fetched-chatbot-data has no elements array")
                return
            }

            val firstElement = elementsArray.optJSONObject(0) ?: return

            // No-flow AI agent bot (Agent Studio)? null for every flow bot.
            val chatbotMap = jsonObjectToMap(chatbotData)
            _agentBot.value = AgentBotParser.parseAgentBot(chatbotMap)
            @Suppress("UNCHECKED_CAST")
            (chatbotMap["customizations"] as? Map<String, Any?>)?.let {
                // Before the look is resolved: the stock English texts are drawn in the language.
                applyWidgetLanguage(it["widgetLanguage"])
                rawCustomizationMap = it
                val filled = personalized(it)
                _serverCustomizationMap.value = filled
                _customizations.value = com.conferbot.sdk.core.customization.Customizations.resolve(filled)
            }
            liveChat.setChatbotData(chatbotMap)
            // The owner's post-chat redirect (checked again when it is used).
            redirectUrl = chatbotMap["redirectUrl"]

            // Extract nodes array
            val nodesArray = firstElement.optJSONArray("nodes")
            val nodes = mutableListOf<Map<String, Any>>()
            if (nodesArray != null) {
                for (i in 0 until nodesArray.length()) {
                    val nodeJson = nodesArray.optJSONObject(i) ?: continue
                    nodes.add(jsonObjectToMap(nodeJson) as Map<String, Any>)
                }
            }

            // Extract edges array
            val edgesArray = firstElement.optJSONArray("edges")
            val edges = mutableListOf<Map<String, Any>>()
            if (edgesArray != null) {
                for (i in 0 until edgesArray.length()) {
                    val edgeJson = edgesArray.optJSONObject(i) ?: continue
                    edges.add(jsonObjectToMap(edgeJson) as Map<String, Any>)
                }
            }

            Log.d(TAG, "Parsed chatbot data: ${nodes.size} nodes, ${edges.size} edges")

            if (nodes.isEmpty() && _agentBot.value == null) {
                Log.w(TAG, "No nodes found in chatbot data")
                return
            }

            // Cache the data
            cachedChatbotNodes = nodes
            cachedChatbotEdges = edges

            // Extract workspaceId from server chatbot data
            val serverWorkspaceId = chatbotData.optString("workspaceId", "")
            if (serverWorkspaceId.isNotBlank()) {
                cachedWorkspaceId = serverWorkspaceId
            }

            // Store Zapier integration webhooks (matched by nodeId at zapier-node runtime)
            chatbotData.optJSONArray("integrationWebhooks")?.let { webhooksArray ->
                val webhooks = mutableListOf<Map<String, Any?>>()
                for (i in 0 until webhooksArray.length()) {
                    val webhookJson = webhooksArray.optJSONObject(i) ?: continue
                    webhooks.add(jsonObjectToMap(webhookJson))
                }
                ChatState.integrationWebhooks = webhooks
            }

            // Parse server customizations and build theme
            rawCustomizationJson = chatbotData.optJSONObject("customizations")
            if (rawCustomizationJson != null) applyLook() else parseServerCustomizations(null)

            // Try to start the flow immediately if session is ready
            tryStartFlow()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse fetched-chatbot-data", e)
        }
    }

    /**
     * Parse the server customizations JSON object and build a [ConferbotTheme].
     *
     * The customizations object comes from `chatbotData.customizations` in the
     * `fetched-chatbot-data` socket event. Color values are hex strings (e.g. "#0100EC").
     * Malformed values are silently skipped so the default theme color is kept.
     */
    private fun parseServerCustomizations(
        customizations: JSONObject?,
        widgetTheme: com.conferbot.sdk.core.customization.ResolvedWidgetTheme? = null
    ) {
        if (customizations == null) {
            Log.d(TAG, "No customizations object in chatbot data")
            return
        }

        try {
            // Helper: optString returns "" for missing keys, normalize to null
            fun JSONObject.optStringOrNull(key: String): String? {
                val value = optString(key, "")
                return value.ifBlank { null }
            }

            // --- 1. Store non-color metadata ---
            val serverCustom = ServerChatbotCustomization(
                avatarUrl = customizations.optStringOrNull("avatar"),
                avatarSize = if (customizations.has("avatarSize")) customizations.optInt("avatarSize") else null,
                hideAvatar = customizations.optBoolean("hideAvatar", false),
                logoUrl = customizations.optStringOrNull("logo"),
                logoText = customizations.optStringOrNull("logoText"),
                botName = customizations.optStringOrNull("botName"),
                font = customizations.optStringOrNull("font"),
                fontSize = if (customizations.has("fontSize")) customizations.optInt("fontSize") else null,
                bubbleBorderRadius = if (customizations.has("bubbleBorderRadius")) customizations.optInt("bubbleBorderRadius") else null,
                chatBgType = customizations.optStringOrNull("chatBgType"),
                hideBrand = customizations.optBoolean("hideBrand", false),
                enableCustomBrand = customizations.optBoolean("enableCustomBrand", false),
                customBrand = customizations.optStringOrNull("customBrand"),
                enableTagline = customizations.optBoolean("enableTagline", false),
                tagline = customizations.optStringOrNull("tagline"),
                gradientBgOne = customizations.optStringOrNull("gradientBgOne"),
                gradientBgTwo = customizations.optStringOrNull("gradientBgTwo"),
                chatBgImg = customizations.optStringOrNull("chatBgImg"),
                widgetIconBgColor = customizations.optStringOrNull("widgetIconBgColor"),
                headerBgColor = customizations.optStringOrNull("headerBgColor"),
                widgetBorderRadius = if (customizations.has("widgetBorderRadius")) customizations.optInt("widgetBorderRadius") else null,
                widgetSize = if (customizations.has("widgetSize")) customizations.optInt("widgetSize") else null,
                widgetPosition = customizations.optStringOrNull("widgetPosition"),
                widgetOffsetRight = if (customizations.has("widgetOffsetRight")) customizations.optInt("widgetOffsetRight") else null,
                widgetOffsetLeft = if (customizations.has("widgetOffsetLeft")) customizations.optInt("widgetOffsetLeft") else null,
                widgetOffsetBottom = if (customizations.has("widgetOffsetBottom")) customizations.optInt("widgetOffsetBottom") else null,
                widgetIconSVG = customizations.optStringOrNull("widgetIconSVG"),
                widgetIconType = customizations.optStringOrNull("widgetIconType"),
                widgetIconImage = customizations.optStringOrNull("widgetIconImage"),
                chatIconCtaText = customizations.optStringOrNull("chatIconCtaText"),
                widgetIconThemeType = customizations.optStringOrNull("widgetIconThemeType")
            )
            _serverCustomization.value = serverCustom

            // --- 2. Build a theme from color values ---
            val builder = ConferbotThemeBuilder.create()
                .name("ServerCustomized")
            // A dark widget theme starts from the SDK's dark theme (disabled
            // states, overlays); the theme's own surfaces go on top below.
            if (widgetTheme?.isDark == true) builder.baseTheme(com.conferbot.sdk.ui.theme.DarkTheme)

            // Helper to safely parse a hex color string to Compose Color
            fun parseHexColor(raw: String?): androidx.compose.ui.graphics.Color? {
                if (raw.isNullOrBlank()) return null
                var hex = raw.trim()
                // rgb()/rgba() and short-hex forms come from the flow builder
                val rgb = Regex("rgba?\\((\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*([\\d.]+))?\\)").find(hex)
                if (rgb != null) {
                    val (r, g, b) = rgb.destructured
                    val a = rgb.groupValues[4].toFloatOrNull() ?: 1f
                    return androidx.compose.ui.graphics.Color(
                        red = r.toInt() / 255f, green = g.toInt() / 255f,
                        blue = b.toInt() / 255f, alpha = a.coerceIn(0f, 1f)
                    )
                }
                if (hex.startsWith("#") && (hex.length == 4 || hex.length == 5)) {
                    hex = "#" + hex.drop(1).map { "$it$it" }.joinToString("")
                }
                return try {
                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse color: $raw", e)
                    null
                }
            }

            // Header
            val headerBg = parseHexColor(customizations.optStringOrNull("headerBgColor"))
            val headerText = parseHexColor(customizations.optStringOrNull("headerTextColor"))
            if (headerBg != null || headerText != null) {
                builder.headerColors(
                    background = headerBg ?: com.conferbot.sdk.ui.theme.LightTheme.colors.headerBackground,
                    text = headerText ?: com.conferbot.sdk.ui.theme.LightTheme.colors.headerText
                )
            }
            // If headerBgColor is provided, also use it as the primary brand color
            if (headerBg != null) {
                builder.primaryColor(headerBg)
            }

            // Bot message bubble - web widget contract defaults
            // (conferbot-widget/src/index.ts): #1b55f3 bubbles, white text.
            val webBubbleDefault = androidx.compose.ui.graphics.Color(0xFF1B55F3)
            val webTextDefault = androidx.compose.ui.graphics.Color.White
            val botBubble = parseHexColor(customizations.optStringOrNull("botMsgColor")) ?: webBubbleDefault
            val botText = parseHexColor(customizations.optStringOrNull("botTextColor")) ?: webTextDefault
            builder.botBubbleColors(background = botBubble, text = botText)
            // An agent - person or AI - speaks in the bot's colours, as on the web.
            builder.agentBubbleColors(background = botBubble, text = botText)

            // The visitor's bubble, option buttons: the web's defaults and
            // fallbacks (light grey visitor bubble; choices in the bot's colours).
            val resolved = com.conferbot.sdk.core.customization.Customizations.resolve(jsonObjectToMap(customizations))
            builder.userBubbleColors(
                background = parseHexColor(resolved.userBubbleColor) ?: androidx.compose.ui.graphics.Color(0xFFF0F0F0),
                text = parseHexColor(resolved.userTextColor) ?: androidx.compose.ui.graphics.Color.Black
            )
            builder.optionBubbleColors(
                background = parseHexColor(resolved.optionBubbleColor) ?: androidx.compose.ui.graphics.Color(0xFFE7E4E4),
                text = parseHexColor(resolved.optionTextColor) ?: androidx.compose.ui.graphics.Color.Black
            )

            // Chat background
            val chatBgColor = parseHexColor(customizations.optStringOrNull("chatBgColor"))
            val chatBgType = customizations.optStringOrNull("chatBgType") ?: "solid"
            val gradientColor1 = parseHexColor(serverCustom.gradientBgOne)
            val gradientColor2 = parseHexColor(serverCustom.gradientBgTwo)
            val chatBgImgUrl = serverCustom.chatBgImg

            when (chatBgType) {
                "gradient" -> {
                    if (gradientColor1 != null && gradientColor2 != null) {
                        builder.gradientBackground(gradientColor1, gradientColor2)
                    } else if (gradientColor1 != null && chatBgColor != null) {
                        builder.gradientBackground(gradientColor1, chatBgColor)
                    } else if (chatBgColor != null) {
                        val lighterVariant = chatBgColor.copy(alpha = 0.6f)
                        builder.gradientBackground(chatBgColor, lighterVariant)
                    }
                }
                "image" -> {
                    if (!chatBgImgUrl.isNullOrBlank()) {
                        builder.imageBackground(chatBgImgUrl)
                    } else if (chatBgColor != null) {
                        builder.background(chatBgColor)
                    }
                }
                else -> {
                    if (chatBgColor != null) {
                        builder.background(chatBgColor)
                    }
                }
            }

            // Bubble corners come from the resolved settings (MessageBubble):
            // 10 is the web's unset value, drawn as 18/18/18/6.

            // Font size -> message size (14 counts as unset: 14.5 on the web)
            builder.messageSize(resolved.fontSize.sp)
            builder.bodySize(resolved.fontSize.sp)

            // The widget theme's surfaces and text (every theme but classic
            // in light): chat, composer, cards, quiet text, lines. The
            // owner's own chat background stays theirs.
            widgetTheme?.tokens?.let { t ->
                fun tc(v: String) = parseHexColor(v) ?: androidx.compose.ui.graphics.Color.Unspecified
                if (!com.conferbot.sdk.core.customization.WidgetTheme.ownsChatBackground(jsonObjectToMap(customizations))) {
                    builder.background(tc(t.bg))
                }
                builder.backgroundColor(tc(t.bg))
                builder.surfaceColor(tc(t.bg))
                builder.inputColors(background = tc(t.field), text = tc(t.ink), border = tc(t.line2))
            }

            val built = builder.build()
            val theme = widgetTheme?.tokens?.let { t ->
                fun tc(v: String) = parseHexColor(v) ?: androidx.compose.ui.graphics.Color.Unspecified
                built.copy(
                    isDarkTheme = widgetTheme.isDark,
                    colors = built.colors.copy(
                        surfaceVariant = tc(t.card2),
                        onBackground = tc(t.ink),
                        onSurface = tc(t.ink),
                        onSurfaceVariant = tc(t.muted),
                        systemMessageText = tc(t.muted),
                        inputPlaceholder = tc(t.muted),
                        divider = tc(t.line),
                        outline = tc(t.line2),
                        timestamp = tc(t.muted),
                    )
                )
            } ?: built
            serverThemeBase = theme
            _serverTheme.value = com.conferbot.sdk.ui.theme.ChatFontLoader.withFonts(theme)
            Log.d(TAG, "Built server theme from customizations")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse server customizations", e)
        }
    }

    /**
     * Attempt to start the chatbot flow.
     * Requires both: chatbot data (nodes/edges) cached AND a valid chat session.
     * Called from handleFetchedChatbotData (when data arrives) and
     * initializeSession (when session becomes ready).
     */
    private fun tryStartFlow() {
        if (flowStarted) return

        val nodes = cachedChatbotNodes ?: return
        val edges = cachedChatbotEdges ?: return
        val sessionId = _chatSessionId.value ?: return
        val currentBotId = botId ?: return

        // An agent bot opens into the agent chat, never the shell flow.
        _agentBot.value?.let { bot ->
            flowStarted = true
            startAgentBotChat(sessionId, bot, nodes)
            // Restored after a restart: a person (or the wait for one) is
            // picked back up from the server (live-chat-state).
            if (restoredConversation) handover.resumeRestored(null)
            return
        }

        // A restored conversation is not replayed from the first node. A
        // live chat, or a handover still in the queue, is picked back up from
        // the server (live-chat-state) instead of the flow running again.
        if (restoredConversation) {
            flowStarted = true
            initializeFlowEngine(nodes, edges)
            observeFlowEngineMessages()
            val current = nodes.getOrNull(ChatState.currentIndex.value)
            @Suppress("UNCHECKED_CAST")
            val type = (current?.get("data") as? Map<String, Any?>)?.get("type")?.toString() ?: current?.get("type")?.toString()
            val handoverNodeId = current?.get("id")?.toString()?.takeIf { type == com.conferbot.sdk.core.nodes.NodeTypes.HUMAN_HANDOVER }
            handover.resumeRestored(handoverNodeId)
            return
        }

        Log.d(TAG, "Starting chatbot flow with ${nodes.size} nodes and ${edges.size} edges")
        flowStarted = true

        initializeFlowEngine(nodes, edges)

        // Observe flow engine UI state changes and add display-only messages to record
        // so they persist in the chat message list (both Compose and XML UIs)
        observeFlowEngineMessages()

        startFlow()
    }

    /**
     * Observe flow engine currentUIState and add message-type nodes to the record
     * so they appear persistently in the message list.
     */
    /** Strip HTML tags from text (e.g. "<p>Hello</p>" → "Hello") */
    private fun stripHtml(html: String): String {
        return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
    }

    /**
     * The collectors [observeFlowEngineMessages] starts. The engine is reused
     * across a Clear History / Start a new chat, so each start replaces the
     * last start's collectors instead of adding to them (#63: every line
     * drawn twice after a restart).
     */
    private var flowMessageWatchers: List<Job> = emptyList()

    private fun stopFlowMessageWatchers() {
        flowMessageWatchers.forEach { it.cancel() }
        flowMessageWatchers = emptyList()
    }

    private fun observeFlowEngineMessages() {
        stopFlowMessageWatchers()
        // Bot lines besides a step's own card (a greeting, an answer, a
        // refused answer's reason, the team's fallback).
        val bot = scope.launch {
            nodeFlowEngine?.botMessages?.collect { ui ->
                val text = (ui as? NodeUIState.Message)?.text ?: return@collect
                addMessageToRecord(RecordItem.BotMessage(id = "flow-${ui.nodeId}-${System.nanoTime()}", time = java.util.Date(), text = text, nodeId = ui.nodeId))
            }
        }
        // The visitor's reply to a step card (its own line: "Agreed to Terms").
        val replies = scope.launch {
            nodeFlowEngine?.visitorAnswers?.collect { line ->
                // A signature's line carries its link; the bubble just says so.
                val text = if (line.text.startsWith("Signed: ")) "Signed" else line.text
                // What it answered: the bot's tapback and "Edit" read it (core/delight).
                val of = com.conferbot.sdk.core.delight.AnswerOf(line.nodeId, line.type, text)
                if (text.isNotBlank()) addMessageToRecord(RecordItem.UserInputResponse(id = "step-reply-${System.nanoTime()}", time = java.util.Date(), text = text, answerOf = of))
            }
        }
        // The question is said once per visit to the step, not again when an
        // answer is refused or the card moves on a stage (an OTP's code).
        var askedVisit: String? = null
        val steps = scope.launch {
            nodeFlowEngine?.currentUIState?.collectLatest { uiState ->
                if (uiState == null) return@collectLatest
                uiState.step?.let { st ->
                    // A card: once per visit. A typed question: once per question
                    // (ask-multiple asks several in one visit).
                    val at = if (com.conferbot.sdk.core.flow.FlowStepCards.cardStep(uiState) != null) "${st.visit}"
                        else "${st.visit}|${(uiState as? NodeUIState.TextInput)?.questionText}"
                    if (askedVisit == at) return@collectLatest
                    askedVisit = at
                }
                // A step drawn by its own card: its question as the bot's line above it.
                com.conferbot.sdk.core.flow.FlowStepCards.cardStep(uiState)?.let { st ->
                    val q = com.conferbot.sdk.core.flow.FlowStepCards.question(st)?.let { com.conferbot.sdk.core.nodes.handlers.FlowKit.shownText(it) }
                    if (!q.isNullOrBlank()) addMessageToRecord(RecordItem.BotMessage(id = "flow-q-${System.nanoTime()}", time = java.util.Date(), text = stripHtml(q), nodeId = uiState.step?.nodeId))
                    return@collectLatest
                }

                val botMessage: RecordItem.BotMessage? = when (uiState) {
                    is NodeUIState.Message -> RecordItem.BotMessage(
                        id = "flow-${uiState.nodeId}-${System.currentTimeMillis()}",
                        time = java.util.Date(),
                        text = stripHtml(uiState.text)
                    )
                    is NodeUIState.Image -> RecordItem.BotMessage(
                        id = "flow-${uiState.nodeId}-${System.currentTimeMillis()}",
                        time = java.util.Date(),
                        text = uiState.caption,
                        imageUrl = uiState.url
                    )
                    is NodeUIState.Video -> RecordItem.BotMessage(
                        id = "flow-${uiState.nodeId}-${System.currentTimeMillis()}",
                        time = java.util.Date(),
                        text = uiState.caption ?: "[Video]"
                    )
                    // For interactive nodes that have a question text, add as bot message
                    is NodeUIState.TextInput -> RecordItem.BotMessage(
                        id = "flow-${uiState.nodeId}-${System.currentTimeMillis()}",
                        time = java.util.Date(),
                        text = stripHtml(uiState.questionText),
                        nodeId = uiState.nodeId
                    )
                    // Interactive nodes render their own question text inline via NodeRenderer
                    // Don't duplicate as a separate BotMessage
                    is NodeUIState.SingleChoice -> null
                    is NodeUIState.MultipleChoice -> null
                    is NodeUIState.Rating -> null
                    is NodeUIState.Dropdown -> null
                    else -> null
                }

                if (botMessage != null) {
                    val currentRecord = _record.value.toMutableList()
                    currentRecord.add(botMessage)

                    if (currentRecord.size > paginationConfig.maxMemoryMessages) {
                        _record.value = currentRecord.takeLast(paginationConfig.maxMemoryMessages)
                    } else {
                        _record.value = currentRecord
                    }

                    // Also persist to message manager
                    messageManager?.addMessage(botMessage)
                }
            }
        }
        flowMessageWatchers = listOf(bot, replies, steps)
    }

    /**
     * Convert JSONObject to Map for flow engine processing
     */
    @Suppress("UNCHECKED_CAST")
    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key: String = keys.next() as String
            val value: Any? = json.opt(key)
            map[key] = when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is org.json.JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    /**
     * Convert JSONArray to List
     */
    @Suppress("UNCHECKED_CAST")
    private fun jsonArrayToList(array: org.json.JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until array.length()) {
            val value = array.get(i)
            list.add(when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is org.json.JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            })
        }
        return list
    }

    /**
     * Open chat UI
     */
    fun openChat(context: Context) {
        if (!_isInitialized.value) {
            Log.e(TAG, "SDK not initialized. Call initialize() first.")
            return
        }

        scope.launch {
            // Initialize session if needed
            if (_chatSessionId.value == null) {
                initializeSession()
            }

            // Launch ChatActivity
            val intent = Intent(context, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            // Clear unread count
            _unreadCount.value = 0
        }
    }

    /**
     * Initialize chat session
     */
    suspend fun initializeSession(): Boolean {
        val client = apiClient ?: return false
        val context = appContext ?: return false

        return try {
            val response = client.initSession(userId = user?.id)
            if (response.success && response.data != null) {
                val session = response.data
                _chatSessionId.value = session.chatSessionId

                // Initialize paginated message manager
                messageManager = PaginatedMessageManager.getInstance(
                    context = context,
                    sessionId = session.chatSessionId,
                    config = paginationConfig
                )

                // Initialize and load messages
                val initialMessages = messageManager?.initialize() ?: emptyList()

                // Set initial record (may be from server or local storage)
                _record.value = if (session.record.isNotEmpty()) {
                    // Add server messages to manager
                    scope.launch {
                        messageManager?.addMessages(session.record)
                    }
                    session.record
                } else {
                    initialMessages
                }

                // Update pagination state
                updatePaginationState()

                // Join chat room via socket with device info
                // This is the only call needed - no separate mobileInit
                socketClient?.joinChatRoom(
                    chatSessionId = session.chatSessionId,
                    deviceInfo = mapOf(
                        "os" to "Android",
                        "osVersion" to Build.VERSION.RELEASE,
                        "sdkVersion" to Build.VERSION.SDK_INT.toString(),
                        "deviceModel" to Build.MODEL
                    )
                )

                // Resolve visitor ID: server > user > persistent device ID
                val resolvedVisitorId = session.visitorId ?: user?.id ?: getOrCreateVisitorId(context)

                // Initialize ChatState for flow engine with context
                ChatState.initializeWithContext(
                    context = context,
                    chatSessionId = session.chatSessionId,
                    visitorId = resolvedVisitorId,
                    botId = botId ?: "",
                    workspaceId = cachedWorkspaceId,
                    maxMessages = paginationConfig.maxMemoryMessages,
                    pageSizeConfig = paginationConfig.pageSize
                )

                // Initialize analytics tracking for this session
                ChatAnalytics.initializeChatAnalytics(
                    sessionId = session.chatSessionId,
                    botIdentifier = botId ?: "",
                    visitorIdentifier = resolvedVisitorId
                )

                try {
                    eventListener?.onSessionStarted(session.chatSessionId)
                } catch (e: Exception) {
                    Log.e(TAG, "Event listener onSessionStarted failed", e)
                }

                // Try to start the chatbot flow if chatbot data is already cached
                tryStartFlow()

                true
            } else {
                Log.e(TAG, "Failed to initialize session: ${response.error}")
                startLocalSession(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize session", e)
            startLocalSession(context)
        }
    }

    /**
     * Fallback when the REST session API is unavailable: generate a local
     * session so the socket-driven flow still runs (web widget parity -
     * the Flutter SDK does the same).
     */
    private suspend fun startLocalSession(context: android.content.Context): Boolean {
        return try {
            val localSessionId = "mobile_${System.currentTimeMillis()}"
            _chatSessionId.value = localSessionId

            messageManager = PaginatedMessageManager.getInstance(
                context = context,
                sessionId = localSessionId,
                config = paginationConfig
            )
            _record.value = messageManager?.initialize() ?: emptyList()
            updatePaginationState()

            socketClient?.joinChatRoom(
                chatSessionId = localSessionId,
                deviceInfo = mapOf(
                    "os" to "Android",
                    "osVersion" to Build.VERSION.RELEASE,
                    "sdkVersion" to Build.VERSION.SDK_INT.toString(),
                    "deviceModel" to Build.MODEL
                )
            )

            val resolvedVisitorId = user?.id ?: getOrCreateVisitorId(context)
            ChatState.initializeWithContext(
                context = context,
                chatSessionId = localSessionId,
                visitorId = resolvedVisitorId,
                botId = botId ?: "",
                workspaceId = cachedWorkspaceId,
                maxMessages = paginationConfig.maxMemoryMessages,
                pageSizeConfig = paginationConfig.pageSize
            )
            ChatAnalytics.initializeChatAnalytics(
                sessionId = localSessionId,
                botIdentifier = botId ?: "",
                visitorIdentifier = resolvedVisitorId
            )
            try {
                eventListener?.onSessionStarted(localSessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Event listener onSessionStarted failed", e)
            }

            tryStartFlow()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Local session fallback failed", e)
            false
        }
    }

    /**
     * Update pagination state from message manager
     */
    private fun updatePaginationState() {
        messageManager?.let { manager ->
            _paginationState.value = PaginationState(
                isLoading = manager.isLoading.value,
                hasMoreMessages = manager.hasMoreMessages.value,
                totalMessageCount = manager.totalMessageCount.value
            )
        }
    }

    /**
     * Load more (older) messages
     */
    fun loadMoreMessages() {
        if (_isLoadingMore.value) return

        scope.launch {
            _isLoadingMore.value = true
            try {
                val olderMessages = messageManager?.loadMoreMessages() ?: emptyList()

                if (olderMessages.isNotEmpty()) {
                    // Prepend to record
                    val currentRecord = _record.value.toMutableList()
                    currentRecord.addAll(0, olderMessages)
                    _record.value = currentRecord
                }

                updatePaginationState()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load more messages", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * Check if there are more messages to load
     */
    fun hasMoreMessages(): Boolean {
        return messageManager?.hasMoreMessages?.value ?: false
    }

    /**
     * Send a message
     */
    fun sendMessage(text: String) {
        val sessionId = _chatSessionId.value
        if (sessionId == null || text.trim().isEmpty()) {
            Log.w(TAG, "Cannot send message: no session or empty text")
            return
        }

        // A sound pack's send sound (web messageSound.ts mountSoundPack).
        sounds.play(com.conferbot.sdk.core.sound.MessageSound.Event.SEND)
        // The agent's typing preview goes (web typingPreview.ts).
        draftSender.clear()

        // Waiting for a person: "play" or "games" opens the games menu instead
        // of being sent (core/waiting/WaitingGames.kt, web games/wire.ts).
        if (waiting.interceptPlay(text)) return

        // What the visitor types in a live or agent chat is what they came
        // for, for "Welcome back" (web special.ts _handleHumanHandoverInputSubmit).
        if (_isLiveChatMode.value || _agentBot.value != null) home.rememberText(text)

        // Track user message in analytics
        ChatAnalytics.trackUserMessage(
            messageLength = text.length,
            messageText = text
        )

        Log.d(TAG, "sendMessage route: live=${_isLiveChatMode.value} awaiting=${nodeFlowEngine?.isAwaitingInput} ui=${nodeFlowEngine?.currentUIState?.value?.javaClass?.simpleName}")
        if (_isLiveChatMode.value) {
            sendLiveChatMessage(text, sessionId)
        } else if (nodeFlowEngine?.isAwaitingInput == true) {
            // Web widget parity: typed text answers the active question node
            // through the unified bottom bar.
            submitNodeResponse(text)
        } else {
            sendBotFlowMessage(text, sessionId)
        }
    }

    /**
     * Send a message during live chat (agent handover) mode
     */
    private fun sendLiveChatMessage(text: String, sessionId: String) {
        // An agent bot's conversation starts with the visitor's first word.
        engageAgent()

        // Create user live message
        val userMessage = RecordItem.UserLiveMessage(
            id = System.currentTimeMillis().toString(),
            time = java.util.Date(),
            text = text
        )

        // Add to record (synchronous for socket send)
        val currentRecord = _record.value.toMutableList()
        currentRecord.add(userMessage)

        if (currentRecord.size > paginationConfig.maxMemoryMessages) {
            _record.value = currentRecord.takeLast(paginationConfig.maxMemoryMessages)
        } else {
            _record.value = currentRecord
        }

        // Add to message manager asynchronously for persistence
        scope.launch {
            messageManager?.addMessage(userMessage)
        }

        // Add to ChatState record for server sync
        ChatState.pushToRecord(
            com.conferbot.sdk.core.state.RecordEntry(
                id = userMessage.id,
                shape = "user-live-message",
                type = "user-live-message",
                text = text
            )
        )

        // Add to transcript
        ChatState.addToTranscript("user", text)

        // Send via response-record with full ChatState record (same as bot
        // flow) - held while an agent bot's handover is being accepted; the
        // acceptance sends it.
        if (!agentHandoverPending) {
            socketClient?.sendResponseRecord(ChatState.buildResponseData())
        }
        noteVisitorMessage()

        // Stop visitor typing indicator
        socketClient?.sendTypingStatus(sessionId, false)

        eventListener?.onMessageSent(userMessage)
    }

    /**
     * Send a message during bot flow mode
     */
    private fun sendBotFlowMessage(text: String, sessionId: String) {
        // Create user message (use user-input-response type for chatbot flow)
        val userMessage = RecordItem.UserInputResponse(
            id = System.currentTimeMillis().toString(),
            time = java.util.Date(),
            text = text
        )

        // Add to record (synchronous for socket send)
        val currentRecord = _record.value.toMutableList()
        currentRecord.add(userMessage)

        // Update record with trimming if needed
        if (currentRecord.size > paginationConfig.maxMemoryMessages) {
            _record.value = currentRecord.takeLast(paginationConfig.maxMemoryMessages)
        } else {
            _record.value = currentRecord
        }

        // Add to message manager asynchronously for persistence
        scope.launch {
            messageManager?.addMessage(userMessage)
        }

        // Send entire record via socket (embed-server expects full record)
        socketClient?.sendResponseRecord(
            chatSessionId = sessionId,
            record = _record.value.map { item ->
                mapOf(
                    "_id" to item.id,
                    "type" to item.type.value,
                    "time" to item.time.toString(),
                    "text" to when (item) {
                        is RecordItem.UserMessage -> item.text
                        is RecordItem.UserInputResponse -> item.text
                        is RecordItem.UserLiveMessage -> item.text
                        is RecordItem.BotMessage -> item.text ?: ""
                        is RecordItem.AgentMessage -> item.text
                        is RecordItem.AgentLeftMessage -> item.text
                        is RecordItem.SystemMessage -> item.text
                        else -> ""
                    }
                )
            },
            answerVariables = emptyList()
        )

        eventListener?.onMessageSent(userMessage)
    }

    /**
     * Sign the app's user in, or out with `null` (a shared device: the next
     * person must not see the last one's requests or name). Call it on the
     * main thread.
     */
    fun identify(user: ConferBotUser?) {
        this.user = user
        ChatState.visitorContext = user?.toVisitorContext()
        // Their name in the owner's texts ("Hi {{first_name|there}}"), or the fallback once signed out.
        if (rawCustomizationJson != null) applyLook()
        // The last user's requests go at once; a list still on its way for
        // them is ignored, and the new one's is read (a hash: from any device, #79).
        if (_isInitialized.value) requests.onUserChanged()
    }

    /**
     * Reset the unread message count to zero.
     *
     * Called automatically by [ConferBotWidget] when the chat overlay opens.
     * Can also be called manually when the host app considers messages "read".
     */
    fun resetUnreadCount() {
        _unreadCount.value = 0
    }

    /**
     * Update the chat-visible flag.
     *
     * This is called internally by [ConferBotWidget] and should not normally
     * be called by host application code.
     *
     * @param visible true when the chat overlay is showing
     */
    fun setChatVisible(visible: Boolean) {
        _isChatVisible.value = visible
    }

    // ==================== Push Notification Methods ====================

    /**
     * Initialize notification components
     */
    private fun initializeNotifications(context: Context) {
        _notificationManager = ConferbotNotificationManager.getInstance(context)
        _notificationHandler = NotificationHandler.getInstance(context)

        // Set ChatActivity as default for deep linking
        _notificationHandler?.setChatActivityClass(ChatActivity::class.java)
        _notificationManager?.setChatActivityClass(ChatActivity::class.java)

        // Listen for token refresh
        NotificationTokenListener.addListener { token ->
            registerPushToken(token)
        }

        Log.d(TAG, "Notification components initialized")
    }

    /**
     * Register push notification token with both API and socket
     *
     * @param token FCM token
     */
    fun registerPushToken(token: String) {
        currentPushToken = token
        val sessionId = _chatSessionId.value

        // Register via API
        scope.launch {
            sessionId?.let {
                apiClient?.registerPushToken(token, it)
            }
        }

        // Also register via socket for real-time notifications
        if (sessionId != null && socketClient?.isConnected == true) {
            registerPushTokenViaSocket(token, sessionId)
        }

        Log.d(TAG, "Push token registered")
    }

    /**
     * Register push token via socket connection
     */
    private fun registerPushTokenViaSocket(token: String, sessionId: String) {
        val data = JSONObject().apply {
            put("token", token)
            put("platform", "android")
            put("sessionId", sessionId)
            put("deviceInfo", JSONObject().apply {
                put("os", "Android")
                put("osVersion", Build.VERSION.RELEASE)
                put("sdkVersion", Build.VERSION.SDK_INT)
                put("deviceModel", Build.MODEL)
            })
        }
        socketClient?.emit("register-push-token", data)
    }

    /**
     * Unregister push token (call when user logs out or disables notifications)
     */
    fun unregisterPushToken() {
        val token = currentPushToken ?: return
        val sessionId = _chatSessionId.value ?: return

        scope.launch {
            try {
                val data = JSONObject().apply {
                    put("token", token)
                    put("sessionId", sessionId)
                    put("platform", "android")
                }
                socketClient?.emit("unregister-push-token", data)
                currentPushToken = null
                Log.d(TAG, "Push token unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister push token", e)
            }
        }
    }

    /**
     * Handle push notification data
     * Returns true if the notification was handled by Conferbot
     *
     * @param data Push notification data payload
     * @return true if handled, false if not a Conferbot notification
     */
    fun handlePushNotification(data: Map<String, String>): Boolean {
        // Check if notification is from Conferbot
        val isConferbotNotification = data["source"] == "conferbot" ||
                data["type"] in listOf(
                    "conferbot_message",
                    "new_message",
                    "agent_joined",
                    "agent_left",
                    "chat_ended",
                    "handover_queued",
                    "queue_position_update"
                )

        if (!isConferbotNotification) {
            return false
        }

        // Parse and handle the notification
        val notification = ConferbotNotification.fromPushData(data)
        if (notification != null) {
            _notificationHandler?.handleNotification(notification)
            return true
        }

        return false
    }

    /**
     * Handle notification tap (when user taps on a notification)
     *
     * @param data Notification data
     */
    fun handleNotificationTap(data: Map<String, String>) {
        _notificationHandler?.handleNotificationTap(data)
    }

    /**
     * Set the current activity for in-app notification banners
     * Call this in your Activity's onResume
     *
     * @param activity Current activity
     */
    fun setCurrentActivity(activity: Activity?) {
        _notificationHandler?.setCurrentActivity(activity)
    }

    /**
     * Set custom chat activity class for deep linking from notifications
     *
     * @param activityClass Activity class to launch
     */
    fun setNotificationChatActivity(activityClass: Class<*>) {
        _notificationHandler?.setChatActivityClass(activityClass)
        _notificationManager?.setChatActivityClass(activityClass)
    }

    /**
     * Get notification manager for advanced customization
     */
    fun getNotificationManager(): ConferbotNotificationManager? = _notificationManager

    /**
     * Get notification handler for advanced customization
     */
    fun getNotificationHandler(): NotificationHandler? = _notificationHandler

    /**
     * Update notification settings
     *
     * @param settings New notification settings
     */
    fun updateNotificationSettings(settings: NotificationSettings) {
        val context = appContext ?: return
        _notificationManager?.updateSettings(settings)
        settings.save(context)
    }

    /**
     * Get current notification settings
     */
    fun getNotificationSettings(): NotificationSettings {
        return _notificationManager?.getSettings()
            ?: appContext?.let { NotificationSettings.load(it) }
            ?: NotificationSettings.default()
    }

    /**
     * Add a notification listener
     *
     * @param listener Listener to add
     */
    fun addNotificationListener(listener: NotificationListener) {
        _notificationHandler?.addNotificationListener(listener)
    }

    /**
     * Remove a notification listener
     *
     * @param listener Listener to remove
     */
    fun removeNotificationListener(listener: NotificationListener) {
        _notificationHandler?.removeNotificationListener(listener)
    }

    /**
     * Check if notifications are enabled at system level
     */
    fun areNotificationsEnabled(): Boolean {
        return _notificationManager?.areNotificationsEnabled() ?: false
    }

    /**
     * Cancel all Conferbot notifications
     */
    fun cancelAllNotifications() {
        _notificationManager?.cancelAllNotifications()
    }

    /**
     * Cancel a specific notification
     *
     * @param notificationId Notification ID to cancel
     */
    fun cancelNotification(notificationId: Int) {
        _notificationManager?.cancelNotification(notificationId)
    }

    /**
     * Show a custom notification (for testing or custom use cases)
     *
     * @param title Notification title
     * @param body Notification body
     * @param data Additional data
     */
    fun showNotification(title: String, body: String, data: Map<String, String> = emptyMap()) {
        _notificationManager?.showMessageNotification(title, body, data)
    }

    /**
     * Send typing status (emits visitor-typing event for live chat)
     */
    fun sendTypingStatus(isTyping: Boolean) {
        // Typing in the composer: a person is here.
        if (isTyping) engageAgent()
        val sessionId = _chatSessionId.value ?: return
        socketClient?.sendTypingStatus(sessionId, isTyping)

        // Track typing behavior for analytics
        if (isTyping) {
            ChatAnalytics.trackTypingStart()
        } else {
            ChatAnalytics.trackTypingEnd()
        }
    }

    /**
     * Initiate handover to live agent
     */
    fun initiateHandover(message: String? = null) {
        val sessionId = _chatSessionId.value ?: return
        socketClient?.initiateHandover(sessionId, message)
    }

    /**
     * End chat
     */
    fun endChat() {
        val sessionId = _chatSessionId.value ?: return
        // Finalize analytics before ending chat
        ChatAnalytics.finalizeChatAnalytics()
        // The server has no visitor end-chat (only an agent ends a live chat,
        // embed-server handoverHandler.js end-chat). Still in the queue: leave
        // it, as the web widget does; queue-self-resolve closes only a waiting
        // ticket, a person on the chat keeps it.
        if (handover.inWait()) handover.leaveQueue()
        else socketClient?.emit("queue-self-resolve", JSONObject(com.conferbot.sdk.core.livechat.HandoverState.queueSelfResolvePayload(sessionId)))
    }

    /**
     * Clear chat history
     */
    fun clearHistory() {
        // A countdown from the conversation being left behind stops here.
        postChatRedirect.reset()
        liveChat.reset()
        handover.reset()
        waiting.reset()
        requests.reset()
        home.reset()
        _talkToPersonRetired.value = false
        _record.value = emptyList()
        _chatSessionId.value = null
        _currentAgent.value = null
        _unreadCount.value = 0
        _isLiveChatMode.value = false
        _isAgentTyping.value = false
        // Reset flow state so it can be re-triggered with same bot data;
        // the last flow's collectors go with it (#63).
        stopFlowMessageWatchers()
        flowStarted = false
        restoredConversation = false
        resetAgentBotChat()
        // Keep cachedChatbotNodes/Edges — same bot, new session
        // Clean up Knowledge Base service when history is cleared
        disposeKnowledgeBaseService()
        ChatState.reset()
    }

    // ==================== Session Persistence Methods ====================

    /**
     * Check if there's a valid persisted session for the current bot
     * Call this before openChat to determine if session restoration is available
     *
     * @return true if a valid session exists that can be restored
     */
    suspend fun hasPersistedSession(): Boolean {
        val context = appContext ?: return false
        val currentBotId = botId ?: return false
        return SessionPersistenceManager.hasValidSession(context, currentBotId)
    }

    /**
     * Restore a persisted session from the Room database
     * Call this before openChat to restore a previous session
     *
     * @return SessionRestoreResult indicating success and session details
     */
    suspend fun restorePersistedSession(): SessionRestoreResult {
        val context = appContext ?: return SessionRestoreResult(
            success = false,
            reason = "SDK not initialized"
        )
        val currentBotId = botId ?: return SessionRestoreResult(
            success = false,
            reason = "Bot ID not set"
        )

        val result = SessionPersistenceManager.restoreSession(
            context = context,
            botId = currentBotId,
            paginationConfig = paginationConfig
        )

        if (result.success && result.sessionId != null) {
            _chatSessionId.value = result.sessionId

            // The conversation on screen again: it was restored to the
            // database only, and the chat opened empty.
            messageManager = PaginatedMessageManager.getInstance(
                context = context,
                sessionId = result.sessionId,
                config = paginationConfig
            )
            val restoredMessages = messageManager?.initialize() ?: emptyList()
            if (restoredMessages.isNotEmpty()) _record.value = restoredMessages
            updatePaginationState()
            restoredConversation = restoredMessages.isNotEmpty()

            // Rejoin socket room with restored session
            socketClient?.joinChatRoom(
                chatSessionId = result.sessionId,
                deviceInfo = mapOf(
                    "os" to "Android",
                    "osVersion" to Build.VERSION.RELEASE,
                    "sdkVersion" to Build.VERSION.SDK_INT.toString(),
                    "deviceModel" to Build.MODEL
                )
            )

            // Initialize analytics for restored session
            ChatAnalytics.initializeChatAnalytics(
                sessionId = result.sessionId,
                botIdentifier = currentBotId,
                visitorIdentifier = result.visitorId ?: user?.id ?: appContext?.let { getOrCreateVisitorId(it) } ?: ""
            )

            eventListener?.onSessionStarted(result.sessionId)
            Log.d(TAG, "Restored persisted session: ${result.sessionId}")

            // An agent bot picks its conversation back up; a flow bot keeps
            // what is on screen (see tryStartFlow).
            tryStartFlow()
        }

        return result
    }

    /**
     * Get information about a persisted session without restoring it
     *
     * @return SessionInfo or null if no valid session exists
     */
    suspend fun getPersistedSessionInfo(): SessionInfo? {
        val context = appContext ?: return null
        val currentBotId = botId ?: return null
        return SessionPersistenceManager.getSessionInfo(context, currentBotId)
    }

    /**
     * Invalidate the current session (mark as ended)
     * The session data will remain for 30 minutes before expiring
     */
    suspend fun invalidateCurrentSession() {
        val context = appContext ?: return
        val sessionId = _chatSessionId.value ?: return
        SessionPersistenceManager.invalidateSession(context, sessionId)
    }

    /**
     * Delete the current session and all its data
     */
    suspend fun deleteCurrentSession() {
        val context = appContext ?: return
        val sessionId = _chatSessionId.value ?: return
        SessionPersistenceManager.deleteSession(context, sessionId)
        clearHistory()
    }

    /**
     * Clear expired sessions to free up storage
     * Call this periodically (e.g., on app startup)
     */
    suspend fun clearExpiredSessions() {
        val context = appContext ?: return
        SessionPersistenceManager.clearExpiredSessions(context)
    }

    // ==================== Node Flow Engine Methods ====================

    /**
     * Initialize the flow engine with bot configuration data
     * Call this when you receive steps and edges from the server
     *
     * @param steps List of node step configurations
     * @param edges List of edge connections between nodes
     */
    fun initializeFlowEngine(
        steps: List<Map<String, Any>>,
        edges: List<Map<String, Any>>
    ) {
        val sessionId = _chatSessionId.value ?: return
        val visitorId = user?.id ?: appContext?.let { getOrCreateVisitorId(it) } ?: ""
        val currentBotId = botId ?: return

        nodeFlowEngine?.initialize(
            chatSessionId = sessionId,
            visitorId = visitorId,
            botId = currentBotId,
            workspaceId = cachedWorkspaceId,
            stepsData = steps,
            edgesData = edges
        )

        // Set _botName variable for handover handler
        val resolvedBotName = _serverCustomization.value?.botName
            ?: _serverCustomization.value?.logoText ?: ""
        if (resolvedBotName.isNotBlank()) {
            ChatState.setVariable("_botName", resolvedBotName)
        }
    }

    /**
     * Start the flow engine processing from the first node
     * Call this after initializing with steps/edges
     */
    fun startFlow() {
        nodeFlowEngine?.start()
    }

    /**
     * Submit a response to the current interactive node
     * Use this for text input, button selections, ratings, etc.
     *
     * @param response The user's response (String, Int, List, or other type depending on node)
     */
    /**
     * Answer the waiting step from its own card, in the spec's shape (build it
     * with core/flow/FlowStepAnswer). Unlike [submitNodeResponse] nothing is
     * guessed from the answer: the visitor's bubble is the step's own reply
     * ("Agreed to Terms", "Verified: a@b.io"), and a refused answer leaves
     * the card in place with its reason.
     */
    fun answerStep(response: Any) {
        nodeFlowEngine?.submitStepResponse(response)
    }

    fun submitNodeResponse(response: Any) {
        // Web widget parity: persist the choice question in the transcript
        // before the answer, so it does not vanish when the node advances.
        val displayTextEarly = when (response) {
            is String -> response
            is Map<*, *> -> (response["text"] ?: response["label"] ?: response["selectedChoice"])?.toString()
            is List<*> -> response.joinToString(", ")
            else -> response.toString()
        }
        when (val ui = nodeFlowEngine?.currentUIState?.value) {
            is NodeUIState.SingleChoice -> ui.questionText to ui.choices.map { it.text }
            is NodeUIState.MultipleChoice -> ui.questionText to ui.options.map { it.text }
            // The image choice's and dropdown's question stays too (drawn only in the card).
            is NodeUIState.ImageChoice -> ui.questionText to ui.images.map { it.label }
            is NodeUIState.Dropdown -> ui.questionText to ui.options.map { it.text }
            else -> null
        }?.let { (question, choiceTexts) ->
            addMessageToRecord(RecordItem.BotMessage(
                id = "flow-q-${System.currentTimeMillis()}",
                time = java.util.Date(),
                text = question,
                nodeData = mapOf(
                    "choices" to choiceTexts,
                    "selected" to (displayTextEarly ?: "")
                ),
                nodeId = nodeFlowEngine?.currentUIState?.value?.step?.nodeId
            ))
        }

        // Extract the display label from the response to show as a user message bubble
        val displayText = when (response) {
            is String -> response
            is Map<*, *> -> (response["text"] ?: response["label"] ?: response["selectedChoice"])?.toString()
            is List<*> -> response.joinToString(", ")
            else -> response.toString()
        }

        // Add user message to the visible record (right-aligned bubble)
        if (!displayText.isNullOrBlank()) {
            val userMessage = RecordItem.UserInputResponse(
                id = "user-choice-${System.currentTimeMillis()}",
                time = java.util.Date(),
                text = displayText,
                answerOf = answerOfFor(nodeFlowEngine?.currentUIState?.value, response, displayText)
            )
            addMessageToRecord(userMessage)
        }

        nodeFlowEngine?.submitResponse(response)
    }

    /**
     * What an answer row says it answered (core/delight reads it): the
     * question's step and type, and its text as a score where it is one
     * ("4/5"), so the bot's tapback reads it as the web reads its bubble.
     */
    private fun answerOfFor(ui: NodeUIState?, response: Any, text: String): com.conferbot.sdk.core.delight.AnswerOf? {
        val step = ui?.step ?: return null
        if (step.nodeId.isEmpty() || step.type.isEmpty()) return null
        val value = when (response) {
            is Number -> response.toDouble()
            is Map<*, *> -> (response["value"] ?: response["rating"])?.toString()?.toDoubleOrNull()
            else -> response.toString().trim().toDoubleOrNull()
        }
        val max = (ui as? NodeUIState.Rating)?.maxValue?.toDouble()
        return com.conferbot.sdk.core.delight.AnswerOf(step.nodeId, step.type, com.conferbot.sdk.core.delight.Delight.scoreText(step.type, value, max).ifEmpty { text })
    }

    /**
     * Clear any current error message from the flow engine
     */
    fun clearNodeError() {
        nodeFlowEngine?.clearError()
    }

    /**
     * Get the ChatState singleton for direct access to conversation state
     * Useful for advanced scenarios where you need to read/modify answer variables
     */
    fun getChatState(): ChatState = ChatState

    /**
     * Set event listener
     */
    fun setEventListener(listener: ConferBotEventListener) {
        this.eventListener = listener
    }

    /**
     * Disconnect socket and clean up flow engine
     */
    fun disconnect() {
        val sessionId = _chatSessionId.value
        if (sessionId != null) {
            // Track potential drop-off before disconnecting
            ChatAnalytics.trackPotentialDropOff("disconnected")
            socketClient?.leaveChatRoom(sessionId)
        }
        // Finalize analytics
        ChatAnalytics.finalizeChatAnalytics()
        // Clean up Knowledge Base service
        disposeKnowledgeBaseService()
        // Clean up File Upload service
        _fileUploadManager?.dispose()
        _fileUploadService?.cancelUpload()
        // Shutdown offline manager
        _offlineManager?.shutdown()
        socketClient?.disconnect()
        nodeFlowEngine?.destroy()
        // Reset flow state
        flowStarted = false
        cachedChatbotNodes = null
        cachedChatbotEdges = null
        // Reset server theme/customization
        _serverTheme.value = null
        serverThemeBase = null
        _serverCustomization.value = null
        // Reset widget state
        _isChatVisible.value = false
        _isLiveChatMode.value = false
        _isAgentTyping.value = false
        // FIX 6: Cancel coroutine scope to prevent leaked coroutines
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        _isConnected.value = false
        _isInitialized.value = false
    }

    /**
     * Get customization
     */
    fun getCustomization(): ConferBotCustomization? = customization

    // ==================== Offline Mode Methods ====================

    /**
     * Initialize the offline manager
     */
    private fun initializeOfflineManager(context: Context) {
        _offlineManager = OfflineManager(context, config.enableOfflineMode).apply {
            // Set up listener for offline events
            setListener(object : OfflineManagerListener {
                override fun onOnlineStatusChanged(isOnline: Boolean) {
                    _isOnline.value = isOnline
                    Log.d(TAG, "Online status changed: $isOnline")
                }

                override fun onQueueSynced(successCount: Int, failCount: Int) {
                    Log.d(TAG, "Queue synced: $successCount success, $failCount failed")
                    _pendingMessageCount.value = getPendingMessageCount()
                    _isSyncingQueue.value = false
                }

                override fun onMessageFailed(message: QueuedMessage) {
                    Log.w(TAG, "Message failed after retries: ${message.id}")
                    eventListener?.onMessageFailed(message.id)
                }
            })

            // Initialize and connect to socket client
            initialize()

            // Observe offline manager states
            scope.launch {
                isOnline.collectLatest { online ->
                    _isOnline.value = online
                }
            }

            scope.launch {
                pendingMessageCount.collectLatest { count ->
                    _pendingMessageCount.value = count
                }
            }

            scope.launch {
                isSyncing.collectLatest { syncing ->
                    _isSyncingQueue.value = syncing
                }
            }
        }

        // Connect offline manager to socket client
        _offlineManager?.let { manager ->
            socketClient?.setOfflineManager(manager)
        } ?: Log.e(TAG, "Offline manager is null after initialization")
    }

    /**
     * Check if offline mode is enabled
     */
    fun isOfflineModeEnabled(): Boolean = config.enableOfflineMode

    /**
     * Check if currently online
     */
    fun isCurrentlyOnline(): Boolean = _isOnline.value

    /**
     * Get count of pending messages in offline queue
     */
    fun getPendingMessageCount(): Int = _offlineManager?.getPendingCount() ?: 0

    /**
     * Check if there are pending messages in offline queue
     */
    fun hasPendingMessages(): Boolean = _offlineManager?.hasPendingMessages() ?: false

    /**
     * Manually trigger processing of queued messages
     * Useful when you want to retry sending failed messages
     */
    fun processOfflineQueue() {
        _offlineManager?.processQueue()
    }

    /**
     * Clear all pending messages in the offline queue
     */
    fun clearOfflineQueue() {
        _offlineManager?.clearQueue()
        _pendingMessageCount.value = 0
    }

    /**
     * Clear pending messages for current session only
     */
    fun clearCurrentSessionQueue() {
        val sessionId = _chatSessionId.value ?: return
        _offlineManager?.clearSessionQueue(sessionId)
        _pendingMessageCount.value = _offlineManager?.getPendingCount() ?: 0
    }

    /**
     * Get the offline manager for advanced usage
     */
    fun getOfflineManager(): OfflineManager? = _offlineManager

    // ==================== Analytics Methods ====================

    /**
     * Set UTM parameters for analytics tracking
     * Call this before starting a chat session if you have UTM data
     * from deep links or other sources
     *
     * @param utmSource UTM source parameter
     * @param utmMedium UTM medium parameter
     * @param utmCampaign UTM campaign parameter
     * @param utmTerm UTM term parameter
     * @param utmContent UTM content parameter
     * @param referrer Referrer URL/source
     * @param landingPage Landing page URL
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
        ChatAnalytics.setUtmParameters(
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
     * Track a custom interaction in analytics
     *
     * @param type Interaction type (e.g., "linksClicked", "buttonsClicked", "filesUploaded")
     * @param data Additional data about the interaction
     */
    fun trackInteraction(type: String, data: Map<String, Any> = emptyMap()) {
        ChatAnalytics.trackInteraction(type, data)
    }

    /**
     * Track goal completion in analytics
     *
     * @param goalId The ID of the completed goal
     * @param conversionEvent Optional conversion event name
     * @param conversionValue Optional conversion value (monetary value, etc.)
     */
    fun trackGoalCompletion(
        goalId: String,
        conversionEvent: String? = null,
        conversionValue: Double? = null
    ) {
        ChatAnalytics.trackGoalCompletion(goalId, conversionEvent, conversionValue)
    }

    /**
     * Submit chat rating/feedback
     *
     * @param csatScore Customer satisfaction score (1-5)
     * @param feedback Optional text feedback
     * @param thumbsUp Optional thumbs up/down
     * @param npsScore Optional NPS score (0-10)
     * @param source Source of the rating
     */
    fun submitChatRating(
        csatScore: Int? = null,
        feedback: String? = null,
        thumbsUp: Boolean? = null,
        npsScore: Int? = null,
        source: String = "post_chat_survey"
    ) {
        ChatAnalytics.submitChatRating(csatScore, feedback, thumbsUp, npsScore, source)
    }

    /**
     * Track text deletion for typing behavior analytics
     * Call this when user deletes text in the input field
     */
    fun trackTextDeletion() {
        ChatAnalytics.trackDeletion()
    }

    /**
     * Notify analytics that app is going to background
     * This helps track potential drop-offs
     */
    fun onAppBackgrounded() {
        ChatAnalytics.trackPotentialDropOff("app_backgrounded")
    }

    /**
     * Get the ChatAnalytics singleton for direct access
     * Use this for advanced analytics scenarios
     */
    fun getAnalytics(): ChatAnalytics = ChatAnalytics

    // ==================== Knowledge Base Methods ====================

    /**
     * Get the Knowledge Base service
     * Returns null if session is not initialized or KB is not enabled
     */
    val knowledgeBaseService: KnowledgeBaseService?
        get() = _knowledgeBaseService

    /**
     * Initialize and get the Knowledge Base service
     * Call this after session is initialized
     *
     * @return KnowledgeBaseService instance or null if not available
     */
    fun initKnowledgeBaseService(): KnowledgeBaseService? {
        val sessionId = _chatSessionId.value ?: return null
        val visitorId = user?.id ?: appContext?.let { getOrCreateVisitorId(it) } ?: ""
        val socket = socketClient ?: return null

        if (_knowledgeBaseService == null) {
            _knowledgeBaseService = KnowledgeBaseService(
                socketClient = socket,
                visitorId = visitorId,
                chatSessionId = sessionId,
                chatbotId = botId ?: "",
                knowledgeBase = knowledgeBase
            )
        }

        return _knowledgeBaseService
    }

    /** Is the help centre on, with something published (enableKnowledgeBase)? */
    val isHelpCentreOn: Boolean
        get() = com.conferbot.sdk.core.helpcentre.HelpCentre.visible(_serverCustomizationMap.value, _knowledgeBase.value)

    /**
     * Open a help centre article by id in the chat's help centre (the web's
     * openArticle). False, and nothing opens, while the help centre is off.
     * [title] names an article the payload does not list; its body loads on open.
     */
    fun openHelpArticle(articleId: String, title: String? = null, fromChat: Boolean = true): Boolean {
        if (!com.conferbot.sdk.core.helpcentre.HelpCentre.canOpen(_serverCustomizationMap.value, _knowledgeBase.value, articleId)) return false
        _helpArticleRequest.value = HelpArticleRequest(articleId, title, fromChat)
        return true
    }

    // A chat that can show an article is on screen (the Compose chat's
    // article sheet): counted, as more than one can be up.
    private val _articleHosts = MutableStateFlow(0)
    internal val articleHosts: StateFlow<Int> = _articleHosts.asStateFlow()

    /** A chat that can show an article by id comes on screen (true) or goes (false). */
    internal fun setArticlesShown(on: Boolean) {
        _articleHosts.update { (it + if (on) 1 else -1).coerceAtLeast(0) }
    }

    /**
     * Can an AI source or an article card open its article in the app? While
     * the help centre is on (enableKnowledgeBase) and a chat that can show
     * one is on screen (RN agentBot.ts canOpenArticles).
     */
    fun canOpenChatArticles(customizations: Map<String, Any?>? = _serverCustomizationMap.value, hosts: Int = _articleHosts.value): Boolean =
        hosts > 0 && com.conferbot.sdk.core.helpcentre.HelpCentre.on(customizations)

    /**
     * An AI source or article card opens its article: in the chat's help
     * centre when it lists it, else fetched by id and shown on its own (the
     * wait's article sheet, #75). False while articles cannot open in the app.
     */
    fun openChatArticle(articleId: String, title: String? = null): Boolean {
        if (!canOpenChatArticles() || articleId.isBlank()) return false
        if (openHelpArticle(articleId, title, fromChat = true)) return true
        waiting.openArticleById(articleId, title)
        return true
    }

    /** The help centre took the open request. */
    fun consumeHelpArticleRequest(request: HelpArticleRequest) {
        if (_helpArticleRequest.value == request) _helpArticleRequest.value = null
    }

    /**
     * The help centre comes with the bot's data, so there is nothing to ask
     * for; kept for existing callers.
     */
    fun fetchKnowledgeBase() {
        initKnowledgeBaseService()?.fetchKnowledgeBase()
    }

    /**
     * Search Knowledge Base articles
     *
     * @param query Search query string
     * @return Flow of matching articles
     */
    fun searchKnowledgeBase(query: String) = initKnowledgeBaseService()?.searchArticles(query)

    /**
     * Track article view in Knowledge Base
     * Only tracks once per session per article
     *
     * @param article The article being viewed
     */
    fun trackKnowledgeBaseArticleView(article: KnowledgeBaseArticle) {
        initKnowledgeBaseService()?.trackArticleView(article)
    }

    /**
     * Start engagement tracking for a Knowledge Base article
     *
     * @param articleId The article ID
     */
    fun startKnowledgeBaseArticleEngagement(articleId: String) {
        initKnowledgeBaseService()?.startArticleEngagement(articleId)
    }

    /**
     * Update scroll depth for article engagement tracking
     *
     * @param scrollDepth Scroll percentage (0-100)
     */
    fun updateKnowledgeBaseScrollDepth(scrollDepth: Int) {
        initKnowledgeBaseService()?.updateScrollDepth(scrollDepth)
    }

    /**
     * Rate a Knowledge Base article
     *
     * @param articleId The article ID
     * @param helpful Whether the article was helpful
     * @return Flow<Boolean> indicating success
     */
    fun rateKnowledgeBaseArticle(articleId: String, helpful: Boolean) =
        initKnowledgeBaseService()?.rateArticle(articleId, helpful)

    /**
     * Check if a Knowledge Base article has been rated in this session
     *
     * @param articleId The article ID
     * @return true if already rated
     */
    fun hasRatedKnowledgeBaseArticle(articleId: String): Boolean {
        return initKnowledgeBaseService()?.hasRatedArticle(articleId) ?: false
    }

    /**
     * Clean up Knowledge Base service
     */
    private fun disposeKnowledgeBaseService() {
        _knowledgeBaseService?.dispose()
        _knowledgeBaseService = null
    }

    /**
     * Listen to custom socket event
     */
    fun on(event: String, callback: Emitter.Listener) {
        socketClient?.on(event, callback)
    }

    /**
     * Remove socket event listener
     */
    fun off(event: String, callback: Emitter.Listener? = null) {
        socketClient?.off(event, callback)
    }
}

/**
 * Event listener interface for Conferbot events
 */
interface ConferBotEventListener {
    fun onMessageReceived(message: RecordItem) {}
    fun onMessageSent(message: RecordItem) {}
    fun onAgentJoined(agent: Agent) {}
    fun onAgentLeft(agent: Agent) {}
    fun onSessionStarted(sessionId: String) {}
    fun onSessionEnded(sessionId: String) {}
    fun onTypingIndicator(isTyping: Boolean) {}
    fun onUnreadCountChanged(count: Int) {}
    fun onOnlineStatusChanged(isOnline: Boolean) {}
    fun onMessageFailed(messageId: String) {}
    fun onQueueSynced(successCount: Int, failCount: Int) {}
}
