package com.conferbot.sdk.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.PaginationState
import com.conferbot.sdk.models.RecordItem
import com.conferbot.sdk.ui.theme.ConferbotBackgroundContainer
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient
import kotlinx.coroutines.launch

/**
 * Main chat screen composable
 *
 * Integrates NodeFlowEngine for displaying and handling all node types
 * using the NodeRenderer component.
 *
 * Enhanced with pagination support for memory-efficient message loading.
 * Now uses the Conferbot theme system for consistent styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferBotChatScreen(
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val animations = theme.animations

    val messages by Conferbot.record.collectAsState()
    val isConnected by Conferbot.isConnected.collectAsState()
    val currentAgent by Conferbot.currentAgent.collectAsState()
    val isAgentTyping by Conferbot.isAgentTyping.collectAsState()

    // Offline mode state
    val isOnline by Conferbot.isOnline.collectAsState()
    val pendingMessageCount by Conferbot.pendingMessageCount.collectAsState()
    val isSyncingQueue by Conferbot.isSyncingQueue.collectAsState()

    // Pagination state
    val paginationState by Conferbot.paginationState.collectAsState()
    val isLoadingMore by Conferbot.isLoadingMore.collectAsState()

    // Flow engine state
    val flowEngine = Conferbot.flowEngine
    val currentUIState by flowEngine?.currentUIState?.collectAsState() ?: remember { mutableStateOf<NodeUIState?>(null) }
    val isProcessing by flowEngine?.isProcessing?.collectAsState() ?: remember { mutableStateOf(false) }
    val errorMessage by flowEngine?.errorMessage?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    val isFlowComplete by flowEngine?.isFlowComplete?.collectAsState() ?: remember { mutableStateOf(false) }

    // Ensure session is initialized
    LaunchedEffect(Unit) {
        if (Conferbot.chatSessionId.value == null) {
            Conferbot.initializeSession()
        }
    }

    Scaffold(
        topBar = {
            ChatHeader(
                title = currentAgent?.name ?: "Support Chat",
                onBackClick = onDismiss,
                isConnected = isConnected,
                isOnline = isOnline,
                pendingMessageCount = pendingMessageCount,
                isSyncing = isSyncingQueue
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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Offline status banner
                if (!isOnline) {
                    OfflineStatusBanner(pendingMessageCount = pendingMessageCount)
                } else if (!isConnected) {
                    ConnectionStatusBanner()
                }

                // Syncing queue banner
                AnimatedVisibility(
                    visible = isSyncingQueue && pendingMessageCount > 0,
                    enter = fadeIn(tween(animations.statusBannerDuration)) + slideInVertically(tween(animations.statusBannerDuration)),
                    exit = fadeOut(tween(animations.statusBannerDuration)) + slideOutVertically(tween(animations.statusBannerDuration))
                ) {
                    SyncingQueueBanner(pendingCount = pendingMessageCount)
                }

                // Error message banner
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(tween(animations.statusBannerDuration)) + slideInVertically(tween(animations.statusBannerDuration)),
                    exit = fadeOut(tween(animations.statusBannerDuration)) + slideOutVertically(tween(animations.statusBannerDuration))
                ) {
                    ErrorBanner(
                        message = errorMessage ?: "",
                        onDismiss = { flowEngine?.clearError() }
                    )
                }

                // Messages list with pagination
                PaginatedMessageList(
                    messages = messages,
                    isAgentTyping = isAgentTyping || isProcessing,
                    paginationState = paginationState,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = { Conferbot.loadMoreMessages() },
                    modifier = Modifier.weight(1f)
                )

                // Current node UI - rendered at the bottom for interactive nodes
                AnimatedVisibility(
                    visible = currentUIState != null && !isFlowComplete,
                    enter = fadeIn(tween(animations.transitionDuration)) + slideInVertically(tween(animations.transitionDuration)) { it },
                    exit = fadeOut(tween(animations.transitionDuration)) + slideOutVertically(tween(animations.transitionDuration)) { it }
                ) {
                    currentUIState?.let { uiState ->
                        NodeInteractionArea(
                            uiState = uiState,
                            primaryColor = colors.primary,
                            onResponse = { response ->
                                flowEngine?.submitResponse(response)
                            }
                        )
                    }
                }

                // Chat input - show when no interactive node is displayed or flow is complete
                if (currentUIState == null || isFlowComplete || !isInteractiveNode(currentUIState)) {
                    ChatInput(
                        onSendMessage = { text ->
                            Conferbot.sendMessage(text)
                        },
                        onTypingChanged = { isTyping ->
                            Conferbot.sendTypingStatus(isTyping)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Check if the current UI state requires user interaction
 */
private fun isInteractiveNode(uiState: NodeUIState?): Boolean {
    return when (uiState) {
        is NodeUIState.TextInput,
        is NodeUIState.FileUpload,
        is NodeUIState.SingleChoice,
        is NodeUIState.MultipleChoice,
        is NodeUIState.Rating,
        is NodeUIState.Dropdown,
        is NodeUIState.Range,
        is NodeUIState.Calendar,
        is NodeUIState.ImageChoice,
        is NodeUIState.Quiz,
        is NodeUIState.MultipleQuestions,
        is NodeUIState.HumanHandover,
        is NodeUIState.PostChatSurvey -> true
        else -> false
    }
}

/**
 * Error message banner with themed styling
 */
@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val spacing = theme.spacing
    val typography = theme.typography

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.error.copy(alpha = 0.1f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = colors.error,
                style = TextStyle(
                    fontFamily = typography.fontFamily,
                    fontSize = typography.bodySize
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = colors.error
                )
            }
        }
    }
}

/**
 * Node interaction area - displays the current interactive node
 */
@Composable
fun NodeInteractionArea(
    uiState: NodeUIState,
    primaryColor: Color,
    onResponse: (Any) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val shapes = theme.shapes
    val spacing = theme.spacing
    val colors = theme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = shapes.dialogRadius, topEnd = shapes.dialogRadius),
        color = colors.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg)
        ) {
            NodeRenderer(
                uiState = uiState,
                onResponse = onResponse,
                primaryColor = primaryColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Chat header with offline mode indicator and themed styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader(
    title: String,
    onBackClick: () -> Unit,
    isConnected: Boolean,
    isOnline: Boolean = true,
    pendingMessageCount: Int = 0,
    isSyncing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val typography = theme.typography
    val spacing = theme.spacing

    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = typography.fontFamily,
                        fontSize = typography.headerSize,
                        fontWeight = typography.headerWeight
                    )
                )
                when {
                    !isOnline -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = colors.headerText.copy(alpha = 0.7f)
                            )
                            Text(
                                text = if (pendingMessageCount > 0)
                                    "Offline - $pendingMessageCount pending"
                                else
                                    "Offline",
                                style = TextStyle(
                                    fontFamily = typography.fontFamily,
                                    fontSize = typography.captionSize
                                ),
                                color = colors.headerText.copy(alpha = 0.7f)
                            )
                        }
                    }
                    isSyncing -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = colors.headerText.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "Syncing messages...",
                                style = TextStyle(
                                    fontFamily = typography.fontFamily,
                                    fontSize = typography.captionSize
                                ),
                                color = colors.headerText.copy(alpha = 0.7f)
                            )
                        }
                    }
                    !isConnected -> {
                        Text(
                            text = "Reconnecting...",
                            style = TextStyle(
                                fontFamily = typography.fontFamily,
                                fontSize = typography.captionSize
                            ),
                            color = colors.headerText.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.headerBackground,
            titleContentColor = colors.headerText,
            navigationIconContentColor = colors.headerIcon
        ),
        modifier = modifier
    )
}

/**
 * Connection status banner with themed styling
 */
@Composable
fun ConnectionStatusBanner(modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val spacing = theme.spacing
    val typography = theme.typography

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.offline)
            .padding(spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Reconnecting...",
            color = colors.onPrimary,
            style = TextStyle(
                fontFamily = typography.fontFamily,
                fontSize = typography.captionSize
            )
        )
    }
}

/**
 * Offline status banner - shown when device is offline
 */
@Composable
fun OfflineStatusBanner(
    pendingMessageCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val spacing = theme.spacing
    val typography = theme.typography

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.offline)
            .padding(horizontal = spacing.lg, vertical = spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.onPrimary
            )
            Spacer(modifier = Modifier.width(spacing.sm))
            Text(
                text = if (pendingMessageCount > 0)
                    "You're offline. $pendingMessageCount message${if (pendingMessageCount > 1) "s" else ""} will be sent when you reconnect."
                else
                    "You're offline. Messages will be sent when you reconnect.",
                color = colors.onPrimary,
                style = TextStyle(
                    fontFamily = typography.fontFamily,
                    fontSize = typography.captionSize
                )
            )
        }
    }
}

/**
 * Syncing queue banner - shown when messages are being synced after reconnection
 */
@Composable
fun SyncingQueueBanner(
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val spacing = theme.spacing
    val typography = theme.typography

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.online)
            .padding(horizontal = spacing.lg, vertical = spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = colors.onPrimary
            )
            Spacer(modifier = Modifier.width(spacing.sm))
            Text(
                text = "Sending $pendingCount queued message${if (pendingCount > 1) "s" else ""}...",
                color = colors.onPrimary,
                style = TextStyle(
                    fontFamily = typography.fontFamily,
                    fontSize = typography.captionSize
                )
            )
        }
    }
}

/**
 * Paginated message list with automatic loading and themed styling
 */
@Composable
fun PaginatedMessageList(
    messages: List<RecordItem>,
    isAgentTyping: Boolean,
    paginationState: PaginationState,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val spacing = theme.spacing
    val animations = theme.animations

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Track if user is near top for pagination trigger
    val shouldLoadMore by remember {
        derivedStateOf {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val hasMoreMessages = paginationState.hasMoreMessages
            val notLoading = !isLoadingMore && !paginationState.isLoading

            // Trigger when scrolled near top (within 10 items)
            firstVisibleItemIndex < 10 && hasMoreMessages && notLoading && messages.isNotEmpty()
        }
    }

    // Track if user has scrolled away from bottom (to show scroll-to-bottom button)
    val showScrollToBottom by remember {
        derivedStateOf {
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisibleItemIndex < totalItems - 3
        }
    }

    // Load more messages when near top
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.chatContentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.messageSpacing),
            contentPadding = PaddingValues(vertical = spacing.lg)
        ) {
            // Loading indicator at top
            if (isLoadingMore) {
                item(key = "loading_more") {
                    LoadingMoreIndicator()
                }
            }

            // "Load more" hint when there are more messages
            if (paginationState.hasMoreMessages && !isLoadingMore) {
                item(key = "load_more_hint") {
                    LoadMoreHint(
                        totalCount = paginationState.totalMessageCount,
                        loadedCount = messages.size
                    )
                }
            }

            // Messages with stable keys for efficient diffing
            items(
                items = messages,
                key = { message -> message.id }
            ) { message ->
                MessageBubble(message = message)
            }

            // Typing indicator at bottom
            if (isAgentTyping) {
                item(key = "typing_indicator") {
                    TypingIndicator()
                }
            }
        }

        // Scroll to bottom FAB
        AnimatedVisibility(
            visible = showScrollToBottom,
            enter = fadeIn(tween(animations.scrollDuration)) + slideInVertically(tween(animations.scrollDuration)) { it },
            exit = fadeOut(tween(animations.scrollDuration)) + slideOutVertically(tween(animations.scrollDuration)) { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(spacing.lg)
        ) {
            ScrollToBottomButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            )
        }
    }

    // Auto-scroll to bottom when new messages arrive (only if already at bottom)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !showScrollToBottom) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
}

/**
 * Loading more indicator shown at top during pagination
 */
@Composable
fun LoadingMoreIndicator(modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val spacing = theme.spacing
    val typography = theme.typography

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colors.primary
            )
            Text(
                text = "Loading older messages...",
                style = TextStyle(
                    fontFamily = typography.fontFamily,
                    fontSize = typography.captionSize
                ),
                color = colors.onSurfaceVariant
            )
        }
    }
}

/**
 * Hint showing more messages are available
 */
@Composable
fun LoadMoreHint(
    totalCount: Int,
    loadedCount: Int,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val spacing = theme.spacing
    val typography = theme.typography

    val remaining = totalCount - loadedCount
    if (remaining > 0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = spacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Scroll up to load $remaining more messages",
                style = TextStyle(
                    fontFamily = typography.fontFamily,
                    fontSize = typography.captionSize
                ),
                color = colors.onSurfaceVariant
            )
        }
    }
}

/**
 * Scroll to bottom floating action button with themed styling
 */
@Composable
fun ScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        containerColor = colors.primary,
        contentColor = colors.onPrimary
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Scroll to bottom",
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Message list (backwards compatible - delegates to paginated version)
 */
@Composable
fun MessageList(
    messages: List<RecordItem>,
    isAgentTyping: Boolean,
    modifier: Modifier = Modifier
) {
    PaginatedMessageList(
        messages = messages,
        isAgentTyping = isAgentTyping,
        paginationState = PaginationState(),
        isLoadingMore = false,
        onLoadMore = {},
        modifier = modifier
    )
}

/**
 * Typing indicator with themed styling
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val shapes = theme.shapes
    val spacing = theme.spacing

    Box(
        modifier = modifier
            .background(
                color = colors.botBubble,
                shape = RoundedCornerShape(shapes.bubbleRadius)
            )
            .padding(spacing.md)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = colors.typing,
                            shape = RoundedCornerShape(50)
                        )
                )
            }
        }
    }
}

/**
 * Chat input with themed styling
 */
@Composable
fun ChatInput(
    onSendMessage: (String) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val shapes = theme.shapes
    val spacing = theme.spacing
    val typography = theme.typography

    var text by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 4.dp,
        color = colors.surface
    ) {
        Row(
            modifier = Modifier
                .padding(spacing.sm)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText
                    onTypingChanged(newText.isNotEmpty())
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Type a message...",
                        style = TextStyle(
                            fontFamily = typography.fontFamily,
                            fontSize = typography.inputSize
                        ),
                        color = colors.inputPlaceholder
                    )
                },
                textStyle = TextStyle(
                    fontFamily = typography.fontFamily,
                    fontSize = typography.inputSize,
                    color = colors.inputText
                ),
                maxLines = 4,
                shape = RoundedCornerShape(shapes.inputRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.inputBackground,
                    unfocusedContainerColor = colors.inputBackground,
                    focusedBorderColor = colors.inputBorderFocused,
                    unfocusedBorderColor = colors.inputBorder,
                    cursorColor = colors.primary
                )
            )

            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSendMessage(text.trim())
                        text = ""
                        onTypingChanged(false)
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank()) colors.primary else colors.inputPlaceholder
                )
            }
        }
    }
}

/**
 * Extension to check if LazyListState is scrolled to the bottom
 */
fun LazyListState.isScrolledToEnd(): Boolean {
    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
    return lastVisibleItem?.index == layoutInfo.totalItemsCount - 1
}
