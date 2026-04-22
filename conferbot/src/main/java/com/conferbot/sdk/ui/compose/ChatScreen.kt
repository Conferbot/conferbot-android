package com.conferbot.sdk.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.ServerChatbotCustomization
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.PaginationState
import com.conferbot.sdk.models.RecordItem
import com.conferbot.sdk.ui.theme.ConferbotBackgroundContainer
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient
import com.conferbot.sdk.ui.theme.ConferbotThemeProvider
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
    // Collect server theme -- when non-null it overrides whatever the parent provided
    val serverTheme by Conferbot.serverTheme.collectAsState()
    val serverCustom by Conferbot.serverCustomization.collectAsState()

    val content: @Composable () -> Unit = {
        ConferBotChatScreenContent(
            onDismiss = onDismiss,
            serverCustomization = serverCustom,
            modifier = modifier
        )
    }

    // If the server sent customizations, wrap the whole screen in the server theme
    val currentServerTheme = serverTheme
    if (currentServerTheme != null) {
        ConferbotThemeProvider(theme = currentServerTheme) {
            content()
        }
    } else {
        content()
    }
}

/**
 * Inner content of the chat screen, separated so the server-theme provider
 * can be applied around it without duplicating the composable body.
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

    // Derive header title: agent name > server bot name > fallback
    val headerTitle = currentAgent?.name
        ?: serverCustomization?.botName?.takeIf { it.isNotBlank() }
        ?: serverCustomization?.logoText?.takeIf { it.isNotBlank() }
        ?: "Support Chat"

    // Ensure session is initialized
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

                // Messages list with pagination + inline node interaction
                PaginatedMessageList(
                    messages = messages,
                    isAgentTyping = isAgentTyping || isProcessing,
                    paginationState = paginationState,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = { Conferbot.loadMoreMessages() },
                    currentUIState = if (!isFlowComplete) currentUIState else null,
                    onNodeResponse = { response ->
                        flowEngine?.submitResponse(response)
                    },
                    modifier = Modifier.weight(1f)
                )

                // Chat input - show when no node is actively displayed or flow is complete
                if (currentUIState == null || isFlowComplete || !shouldHideChatInput(currentUIState)) {
                    ChatInput(
                        onSendMessage = { text ->
                            Conferbot.sendMessage(text)
                        },
                        onTypingChanged = { isTyping ->
                            Conferbot.sendTypingStatus(isTyping)
                        }
                    )
                }

                // Powered by Conferbot footer
                PoweredByFooter(serverCustomization = serverCustomization)
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
 * Check if chat input should be hidden for the current node.
 * This includes interactive nodes AND display-only nodes that auto-advance,
 * so the input field doesn't flash while display nodes are showing.
 */
private fun shouldHideChatInput(uiState: NodeUIState?): Boolean {
    return when (uiState) {
        // Interactive nodes
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
        is NodeUIState.PostChatSurvey,
        // Display-only nodes that auto-advance
        is NodeUIState.Message,
        is NodeUIState.Image,
        is NodeUIState.Video,
        is NodeUIState.Audio,
        is NodeUIState.File,
        is NodeUIState.Html -> true
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
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(topStart = shapes.dialogRadius, topEnd = shapes.dialogRadius)),
        shadowElevation = 4.dp,
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
 * Chat header with offline mode indicator, bot avatar, tagline,
 * close button, and overflow menu with themed styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader(
    title: String,
    onBackClick: () -> Unit,
    onDismiss: () -> Unit,
    isConnected: Boolean,
    isOnline: Boolean = true,
    pendingMessageCount: Int = 0,
    isSyncing: Boolean = false,
    serverCustomization: ServerChatbotCustomization? = null,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val typography = theme.typography
    val spacing = theme.spacing

    // Overflow menu state
    var showMenu by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                // Bot avatar
                val avatarUrl = serverCustomization?.avatarUrl
                    ?: serverCustomization?.logoUrl
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Bot avatar",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Fallback: colored circle with initial letter
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(colors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title.firstOrNull()?.uppercase() ?: "B",
                            style = TextStyle(
                                fontFamily = typography.fontFamily,
                                fontSize = 14.sp,
                                fontWeight = typography.headerWeight
                            ),
                            color = colors.onPrimary
                        )
                    }
                }

                // Title and subtitle column
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
                        // Tagline: show only when no status subtitle is displayed
                        serverCustomization?.enableTagline == true &&
                                !serverCustomization.tagline.isNullOrBlank() -> {
                            Text(
                                text = serverCustomization.tagline,
                                style = TextStyle(
                                    fontFamily = typography.fontFamily,
                                    fontSize = typography.captionSize
                                ),
                                color = colors.headerText.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
        actions = {
            // Close / minimize button
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close chat",
                    tint = colors.headerIcon
                )
            }
            // Three-dot overflow menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = colors.headerIcon
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Restart Chat") },
                        onClick = {
                            showMenu = false
                            Conferbot.clearHistory()
                            // Re-initialize session after clearing
                            coroutineScope.launch {
                                Conferbot.initializeSession()
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("End Chat") },
                        onClick = {
                            showMenu = false
                            onDismiss()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (soundEnabled) "Sound Off" else "Sound On") },
                        onClick = {
                            soundEnabled = !soundEnabled
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.headerBackground,
            titleContentColor = colors.headerText,
            navigationIconContentColor = colors.headerIcon,
            actionIconContentColor = colors.headerIcon
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
    currentUIState: NodeUIState? = null,
    onNodeResponse: ((Any) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val spacing = theme.spacing
    val animations = theme.animations

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Count total items for scroll calculations
    val hasInlineNode = currentUIState != null && onNodeResponse != null
    val totalItemCount = messages.size +
        (if (isAgentTyping) 1 else 0) +
        (if (hasInlineNode) 1 else 0)

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

            // Typing indicator
            if (isAgentTyping) {
                item(key = "typing_indicator") {
                    TypingIndicator()
                }
            }

            // Inline node interaction — rendered as the last item in the message stream
            if (hasInlineNode) {
                item(key = "inline_node_interaction") {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                                expandVertically(tween(300, easing = FastOutSlowInEasing))
                    ) {
                        NodeRenderer(
                            uiState = currentUIState!!,
                            onResponse = onNodeResponse!!,
                            primaryColor = colors.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm)
                        )
                    }
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
                        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                    }
                }
            )
        }
    }

    // Auto-scroll to bottom when new messages arrive or node UI appears
    LaunchedEffect(messages.size, currentUIState) {
        val itemCount = listState.layoutInfo.totalItemsCount
        if (itemCount > 0 && !showScrollToBottom) {
            coroutineScope.launch {
                listState.animateScrollToItem(itemCount - 1)
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
    modifier: Modifier = Modifier,
    currentUIState: NodeUIState? = null,
    onNodeResponse: ((Any) -> Unit)? = null
) {
    PaginatedMessageList(
        messages = messages,
        isAgentTyping = isAgentTyping,
        paginationState = PaginationState(),
        isLoadingMore = false,
        onLoadMore = {},
        currentUIState = currentUIState,
        onNodeResponse = onNodeResponse,
        modifier = modifier
    )
}

/**
 * Typing indicator with animated bouncing dots
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val shapes = theme.shapes
    val spacing = theme.spacing

    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    val dotOffsets = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -5f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 500,
                    delayMillis = index * 160,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_$index"
        )
    }

    val dotAlphas = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 500,
                    delayMillis = index * 160,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_alpha_$index"
        )
    }

    Box(
        modifier = modifier
            .shadow(1.dp, RoundedCornerShape(shapes.bubbleRadius))
            .background(
                color = colors.botBubble,
                shape = RoundedCornerShape(shapes.bubbleRadius)
            )
            .padding(horizontal = spacing.md, vertical = spacing.sm)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dotOffsets.forEachIndexed { index, animatedOffset ->
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .offset(y = animatedOffset.value.dp)
                        .background(
                            color = colors.typing.copy(alpha = dotAlphas[index].value),
                            shape = CircleShape
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

    Column(modifier = modifier.fillMaxWidth()) {
        // Subtle top border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.divider.copy(alpha = 0.3f))
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = spacing.sm, vertical = spacing.sm)
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

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(
                            elevation = if (text.isNotBlank()) 2.dp else 0.dp,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(
                            if (text.isNotBlank()) colors.headerBackground
                            else colors.headerBackground.copy(alpha = 0.4f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                onSendMessage(text.trim())
                                text = ""
                                onTypingChanged(false)
                            }
                        },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Powered by Conferbot" footer bar.
 *
 * Hidden when `hideBrand` is true. When `enableCustomBrand` is true and
 * `customBrand` is set, that text is shown instead.
 */
@Composable
fun PoweredByFooter(
    serverCustomization: ServerChatbotCustomization?,
    modifier: Modifier = Modifier
) {
    // Hide completely when brand is suppressed
    if (serverCustomization?.hideBrand == true) return

    val brandText = if (serverCustomization?.enableCustomBrand == true &&
        !serverCustomization.customBrand.isNullOrBlank()
    ) {
        serverCustomization.customBrand
    } else {
        "Powered by Conferbot"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = brandText,
            style = TextStyle(fontSize = 10.sp),
            color = Color.Gray
        )
    }
}

/**
 * Extension to check if LazyListState is scrolled to the bottom
 */
fun LazyListState.isScrolledToEnd(): Boolean {
    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
    return lastVisibleItem?.index == layoutInfo.totalItemsCount - 1
}
