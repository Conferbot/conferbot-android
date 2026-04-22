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

    val content: @Composable () -> Unit = {
        ConferBotChatScreenContent(
            onDismiss = onDismiss,
            serverCustomization = serverCustom,
            modifier = modifier
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

    val headerTitle = currentAgent?.name
        ?: serverCustomization?.botName?.takeIf { it.isNotBlank() }
        ?: serverCustomization?.logoText?.takeIf { it.isNotBlank() }
        ?: "Support Chat"

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
                serverCustomization = serverCustomization
            )
        },
        modifier = modifier
    ) { paddingValues ->
        ConferbotBackgroundContainer(
            background = theme.background,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                    onNodeResponse = { response -> flowEngine?.submitResponse(response) },
                    modifier = Modifier.weight(1f)
                )

                // Unified input + footer — one seamless bottom bar
                ChatBottomBar(
                    onSendMessage = { text -> Conferbot.sendMessage(text) },
                    onTypingChanged = { isTyping -> Conferbot.sendTypingStatus(isTyping) },
                    serverCustomization = serverCustomization
                )
            }
        }
    }
}
