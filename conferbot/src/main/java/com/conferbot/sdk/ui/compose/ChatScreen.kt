package com.conferbot.sdk.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.ServerChatbotCustomization
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.ui.compose.chat.*
import com.conferbot.sdk.ui.theme.ConferbotBackgroundContainer
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient
import com.conferbot.sdk.ui.theme.ConferbotThemeProvider

/**
 * Main chat screen composable.
 *
 * Integrates NodeFlowEngine, server theming, pagination, and all
 * chat UI components from the `chat/` and `messages/` subpackages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferBotChatScreen(
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val serverTheme by Conferbot.serverTheme.collectAsState()
    val serverCustom by Conferbot.serverCustomization.collectAsState()

    // The keyboard pushes the composer up instead of covering it (or panning
    // the header off screen): the host window resizes while the chat is up,
    // and an edge-to-edge host gets the keyboard's inset as padding.
    KeyboardResizesWindow()

    val content: @Composable () -> Unit = {
        ConferBotChatScreenContent(
            onDismiss = onDismiss,
            serverCustomization = serverCustom,
            modifier = modifier.imePadding()
        )
    }

    val currentServerTheme = serverTheme
    if (currentServerTheme != null) {
        ConferbotThemeProvider(theme = currentServerTheme) { content() }
    } else {
        content()
    }
}

/**
 * Inner content — separated so the server-theme provider wraps without duplicating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConferBotChatScreenContent(
    onDismiss: () -> Unit,
    serverCustomization: ServerChatbotCustomization?,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val animations = theme.animations

    val messages by Conferbot.record.collectAsState()
    val isConnected by Conferbot.isConnected.collectAsState()
    val currentAgent by Conferbot.currentAgent.collectAsState()
    val isAgentTyping by Conferbot.isAgentTyping.collectAsState()
    val isOnline by Conferbot.isOnline.collectAsState()
    val pendingMessageCount by Conferbot.pendingMessageCount.collectAsState()
    val isSyncingQueue by Conferbot.isSyncingQueue.collectAsState()
    val paginationState by Conferbot.paginationState.collectAsState()
    val isLoadingMore by Conferbot.isLoadingMore.collectAsState()

    val flowEngine = Conferbot.flowEngine
    val currentUIState by flowEngine?.currentUIState?.collectAsState()
        ?: remember { mutableStateOf<NodeUIState?>(null) }
    val isProcessing by flowEngine?.isProcessing?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val errorMessage by flowEngine?.errorMessage?.collectAsState()
        ?: remember { mutableStateOf<String?>(null) }
    val isFlowComplete by flowEngine?.isFlowComplete?.collectAsState()
        ?: remember { mutableStateOf(false) }

    // Live chat extras (core/livechat)
    val live = Conferbot.liveChat
    val isLiveChatMode by Conferbot.isLiveChatMode.collectAsState()
    val lockedBy by live.lockedBy.collectAsState()
    val inputEnabled by live.visitorInputEnabled.collectAsState()
    val agentLost by live.agentConnectionLost.collectAsState()
    val fileRequestedBy by live.fileRequestedBy.collectAsState()
    val noAgents by live.noAgentsAvailable.collectAsState()
    val handoverWaiting by live.handoverWaiting.collectAsState()
    val leadOpen by live.leadCardOpen.collectAsState()
    val csatOpen by live.csatOpen.collectAsState()
    val csatSent by live.csatSent.collectAsState()
    val leftMessageTo by live.leftMessageTo.collectAsState()
    val customizationMap by Conferbot.serverCustomizationMap.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    // The system file picker: no extra library in the host app.
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) Conferbot.sendLiveFile(uri) { error ->
            if (error != null) android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val canAttach = isLiveChatMode && (com.conferbot.sdk.core.livechat.LiveChat.visitorUploadsOn(customizationMap) || fileRequestedBy != null)
    val blocked by live.blocked.collectAsState()
    val mailOpen by live.mailOpen.collectAsState()
    val mailSentTo by live.mailSentTo.collectAsState()
    val serverRecord by com.conferbot.sdk.core.state.ChatState.record.collectAsState()
    // Blocked, or the live chat ended with nothing going on: the closed
    // state (core/livechat/LiveEvents.kt), with a new chat for an ended one.
    val closed = remember(serverRecord, blocked, currentUIState, isProcessing, isLiveChatMode, currentAgent) {
        com.conferbot.sdk.core.livechat.LiveEvents.closedStateOf(
            com.conferbot.sdk.core.livechat.LiveEvents.kindsOf(serverRecord),
            blocked = blocked,
            stepWaiting = currentUIState != null || isProcessing,
            live = isLiveChatMode && currentAgent != null
        )
    }
    val repliesOff = lockedBy != null || !inputEnabled || closed?.inputOff == true

    // The bot's settings as the web applies them (core/customization).
    val look by Conferbot.customizations.collectAsState()
    val hasLook = customizationMap.isNotEmpty()
    // The header keeps the bot (never the agent), as the web widget does.
    val headerTitle = look.title.takeIf { hasLook } ?: "Support Chat"
    // "Online", the team's reply time, or plain "Online" once a person is in
    // the chat (core/livechat/ReplyTime.kt, the web's replyTime.ts / headerPresence.ts).
    val agentBot by Conferbot.agentBot.collectAsState()
    val statusText = remember(messages, currentAgent, customizationMap, agentBot) {
        val personInChat = com.conferbot.sdk.core.livechat.HandoverState.phase(
            com.conferbot.sdk.core.livechat.HandoverState.entriesOfItems(messages)
        ) == com.conferbot.sdk.core.livechat.HandoverPhase.JOINED ||
            (currentAgent != null && currentAgent?.id?.startsWith("ai-agent-") != true && agentBot == null)
        com.conferbot.sdk.core.livechat.ReplyTime.headerStatus(
            com.conferbot.sdk.core.livechat.ReplyTime.of(customizationMap["replyTime"]),
            personInChat = personInChat,
            agentBot = agentBot != null
        ) ?: com.conferbot.sdk.core.livechat.ReplyTime.ONLINE
    }
    // On screen: the waiting signal and receipts count it as open.
    DisposableEffect(Unit) {
        Conferbot.setChatVisible(true)
        onDispose { Conferbot.setChatVisible(false) }
    }

    // The help centre (core/helpcentre): the web's Chat / Articles switch
    // under the header while the owner has it on (enableKnowledgeBase).
    val knowledgeBase by Conferbot.knowledgeBase.collectAsState()
    val helpOn = com.conferbot.sdk.core.helpcentre.HelpCentre.visible(customizationMap, knowledgeBase)
    val showRecentArticles = com.conferbot.sdk.core.helpcentre.HelpCentre.showsRecent(customizationMap, knowledgeBase)
    val articleRequest by Conferbot.helpArticleRequest.collectAsState()
    val sessionId by Conferbot.chatSessionId.collectAsState()
    var showArticles by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(helpOn) { if (!helpOn) showArticles = false }
    // An AI source or an article card asked for an article: the help pane shows it.
    LaunchedEffect(articleRequest, helpOn) { if (articleRequest != null && helpOn) showArticles = true }

    // A theme set to "auto" follows the phone's light / dark setting.
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    LaunchedEffect(systemDark) { Conferbot.setSystemDarkMode(systemDark) }

    LaunchedEffect(Unit) {
        if (Conferbot.chatSessionId.value == null) {
            Conferbot.initializeSession()
        }
    }

    Scaffold(
        topBar = {
            ChatHeader(
                title = headerTitle,
                onBackClick = onDismiss,
                onDismiss = onDismiss,
                isConnected = isConnected,
                isOnline = isOnline,
                pendingMessageCount = pendingMessageCount,
                isSyncing = isSyncingQueue,
                serverCustomization = serverCustomization,
                look = look.takeIf { hasLook },
                statusText = statusText,
                // The conversation to the visitor's inbox (web chat menu, emailTranscript.ts).
                onEmailChat = if (sessionId != null && blocked == null) ({ live.openEmailTranscript() }) else null
            )
        },
        modifier = modifier
    ) { paddingValues ->
        // The bot's chat background (core/customization ChatBackground) once its
        // settings arrived; the host app's theme background before that.
        // A widget theme paints it when the owner kept the default white.
        val chatBackground = remember(customizationMap, look.surface) {
            look.surface?.let { com.conferbot.sdk.core.customization.ChatBackground.Solid(it) }
                ?: com.conferbot.sdk.core.customization.ChatBackgrounds.resolve(customizationMap)
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(paddingValues)) {
        if (hasLook) com.conferbot.sdk.ui.compose.chat.ChatBackgroundLayer(chatBackground)
        ConferbotBackgroundContainer(
            background = if (hasLook) com.conferbot.sdk.ui.theme.ConferbotBackground.SolidColor(androidx.compose.ui.graphics.Color.Transparent) else theme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Status banners
                if (!isOnline) {
                    OfflineStatusBanner(pendingMessageCount = pendingMessageCount)
                } else if (!isConnected) {
                    ConnectionStatusBanner()
                }

                AnimatedVisibility(
                    visible = isSyncingQueue && pendingMessageCount > 0,
                    enter = fadeIn(tween(animations.statusBannerDuration)) +
                            slideInVertically(tween(animations.statusBannerDuration)),
                    exit = fadeOut(tween(animations.statusBannerDuration)) +
                            slideOutVertically(tween(animations.statusBannerDuration))
                ) {
                    SyncingQueueBanner(pendingCount = pendingMessageCount)
                }

                AnimatedVisibility(
                    // A step card says why it refused an answer on itself.
                    visible = errorMessage != null && com.conferbot.sdk.core.flow.FlowStepCards.cardStep(currentUIState)?.error != errorMessage,
                    enter = fadeIn(tween(animations.statusBannerDuration)) +
                            slideInVertically(tween(animations.statusBannerDuration)),
                    exit = fadeOut(tween(animations.statusBannerDuration)) +
                            slideOutVertically(tween(animations.statusBannerDuration))
                ) {
                    ErrorBanner(
                        message = errorMessage ?: "",
                        onDismiss = { flowEngine?.clearError() }
                    )
                }

                if (helpOn) {
                    HelpCentreTabs(articles = showArticles, onSelect = { showArticles = it })
                }

                if (helpOn && showArticles) {
                    val kbService = remember(sessionId) { if (sessionId != null) Conferbot.initKnowledgeBaseService() else null }
                    if (kbService == null) {
                        androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            CircularProgressIndicator(color = colors.primary)
                        }
                    } else {
                        com.conferbot.sdk.ui.compose.knowledgebase.KnowledgeBaseScreen(
                            knowledgeBaseService = kbService,
                            onDismiss = { showArticles = false },
                            primaryColor = colors.primary,
                            embedded = true,
                            showRecent = showRecentArticles,
                            openRequest = articleRequest,
                            onOpenRequestHandled = { Conferbot.consumeHelpArticleRequest(it) },
                            onBackToChat = { showArticles = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                // Messages + inline node interaction
                PaginatedMessageList(
                    messages = messages,
                    isAgentTyping = isAgentTyping || isProcessing,
                    paginationState = paginationState,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = { Conferbot.loadMoreMessages() },
                    currentUIState = if (!isFlowComplete && blocked == null) currentUIState else null,
                    onNodeResponse = { response -> Conferbot.submitNodeResponse(response) },
                    modifier = Modifier.weight(1f),
                    footer = { LiveChatFooterCards(onUpload = { pickFile.launch("*/*") }, closed = closed) },
                    footerKey = listOf(noAgents, handoverWaiting, fileRequestedBy, leadOpen, csatOpen, csatSent, leftMessageTo, mailOpen, mailSentTo, closed?.kind)
                )

                lockedBy?.let { SystemLine("This conversation has been locked") }
                agentLost?.let { SystemLine("$it's connection dropped. Hang on, they should be right back.") }
                QuickActionsStrip()

                // Unified input + footer — one seamless bottom bar; none under
                // a step card that takes the typing itself (OTP, address, signature).
                if (!com.conferbot.sdk.core.flow.FlowStepCards.hidesComposer(if (!isFlowComplete) currentUIState else null)) ChatBottomBar(
                    onSendMessage = { text -> Conferbot.sendMessage(text) },
                    onTypingChanged = { isTyping -> Conferbot.sendTypingStatus(isTyping) },
                    serverCustomization = serverCustomization,
                    enabled = !repliesOff,
                    placeholder = when {
                        closed?.inputOff == true -> closed.text
                        repliesOff -> "Replies are turned off for now"
                        else -> "Type a message..."
                    },
                    onAttach = if (canAttach) ({ pickFile.launch("*/*") }) else null
                )
                }
            }
        }
        }
    }
}

/**
 * The web's Chat / Articles switch (ts/layout/viewTabs.ts): the selected tab
 * in the header's colours, the other light grey.
 */
@Composable
private fun HelpCentreTabs(articles: Boolean, onSelect: (Boolean) -> Unit) {
    val colors = ConferbotThemeAmbient.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(false to "Chat", true to "Articles").forEach { (isArticles, label) ->
            val selected = isArticles == articles
            Surface(
                onClick = { onSelect(isArticles) },
                shape = RoundedCornerShape(9.dp),
                color = if (selected) colors.headerBackground else Color(0xFFF0F0F0),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    color = if (selected) colors.headerText else Color(0xFF333333),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

/** While shown: the host window resizes for the keyboard; its own mode comes back after. */
@Composable
internal fun KeyboardResizesWindow() {
    val activity = LocalContext.current.findActivity() ?: return
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(activity, lifecycleOwner) {
        val window = activity.window
        val previous = window.attributes.softInputMode
        val resize = {
            window.setSoftInputMode(
                (previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }
        resize()
        // Back from another screen (the file picker): the system puts the
        // window's own mode back after resuming, so it is set again once the
        // window has focus.
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resize()
        }
        val focus = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus -> if (hasFocus) resize() }
        val tree = window.decorView.viewTreeObserver
        lifecycleOwner.lifecycle.addObserver(observer)
        tree.addOnWindowFocusChangeListener(focus)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (window.decorView.viewTreeObserver.isAlive) window.decorView.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            window.setSoftInputMode(previous)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
