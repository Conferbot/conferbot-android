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
    val repliesOff = lockedBy != null || !inputEnabled

    // The bot's settings as the web applies them (core/customization).
    val look by Conferbot.customizations.collectAsState()
    val hasLook = customizationMap.isNotEmpty()
    val headerTitle = currentAgent?.name
        ?: look.title.takeIf { hasLook }
        ?: "Support Chat"

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
                agentName = currentAgent?.name,
                agentAvatar = currentAgent?.avatar
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
                    visible = errorMessage != null,
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

                // Messages + inline node interaction
                PaginatedMessageList(
                    messages = messages,
                    isAgentTyping = isAgentTyping || isProcessing,
                    paginationState = paginationState,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = { Conferbot.loadMoreMessages() },
                    currentUIState = if (!isFlowComplete) currentUIState else null,
                    onNodeResponse = { response -> Conferbot.submitNodeResponse(response) },
                    modifier = Modifier.weight(1f),
                    footer = { LiveChatFooterCards(onUpload = { pickFile.launch("*/*") }) },
                    footerKey = listOf(noAgents, handoverWaiting, fileRequestedBy, leadOpen, csatOpen, csatSent, leftMessageTo)
                )

                lockedBy?.let { SystemLine("This conversation has been locked") }
                agentLost?.let { SystemLine("$it's connection dropped. Hang on, they should be right back.") }
                QuickActionsStrip()

                // Unified input + footer — one seamless bottom bar
                ChatBottomBar(
                    onSendMessage = { text -> Conferbot.sendMessage(text) },
                    onTypingChanged = { isTyping -> Conferbot.sendTypingStatus(isTyping) },
                    serverCustomization = serverCustomization,
                    enabled = !repliesOff,
                    placeholder = if (repliesOff) "Replies are turned off for now" else "Type a message...",
                    onAttach = if (canAttach) ({ pickFile.launch("*/*") }) else null
                )
            }
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
