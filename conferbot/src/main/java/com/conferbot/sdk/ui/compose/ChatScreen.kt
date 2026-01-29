package com.conferbot.sdk.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.models.RecordItem
import kotlinx.coroutines.launch

/**
 * Main chat screen composable
 *
 * Integrates NodeFlowEngine for displaying and handling all node types
 * using the NodeRenderer component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferBotChatScreen(
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val messages by Conferbot.record.collectAsState()
    val isConnected by Conferbot.isConnected.collectAsState()
    val currentAgent by Conferbot.currentAgent.collectAsState()
    val isAgentTyping by Conferbot.isAgentTyping.collectAsState()

    // Flow engine state
    val flowEngine = Conferbot.flowEngine
    val currentUIState by flowEngine?.currentUIState?.collectAsState() ?: remember { mutableStateOf<NodeUIState?>(null) }
    val isProcessing by flowEngine?.isProcessing?.collectAsState() ?: remember { mutableStateOf(false) }
    val errorMessage by flowEngine?.errorMessage?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    val isFlowComplete by flowEngine?.isFlowComplete?.collectAsState() ?: remember { mutableStateOf(false) }

    // Primary color from customization or default
    val primaryColor = Color(0xFF0100EC)

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
                isConnected = isConnected
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Connection status
            if (!isConnected) {
                ConnectionStatusBanner()
            }

            // Error message banner
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                ErrorBanner(
                    message = errorMessage ?: "",
                    onDismiss = { flowEngine?.clearError() }
                )
            }

            // Messages list
            MessageList(
                messages = messages,
                isAgentTyping = isAgentTyping || isProcessing,
                modifier = Modifier.weight(1f)
            )

            // Current node UI - rendered at the bottom for interactive nodes
            AnimatedVisibility(
                visible = currentUIState != null && !isFlowComplete,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                currentUIState?.let { uiState ->
                    NodeInteractionArea(
                        uiState = uiState,
                        primaryColor = primaryColor,
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
        is NodeUIState.HumanHandover -> true
        else -> false
    }
}

/**
 * Error message banner
 */
@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
 * Chat header
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader(
    title: String,
    onBackClick: () -> Unit,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                if (!isConnected) {
                    Text(
                        text = "Reconnecting...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
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
            containerColor = Color(0xFF0100EC),
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White
        ),
        modifier = modifier
    )
}

/**
 * Connection status banner
 */
@Composable
fun ConnectionStatusBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFA726))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Reconnecting...",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Message list
 */
@Composable
fun MessageList(
    messages: List<RecordItem>,
    isAgentTyping: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message = message)
        }

        if (isAgentTyping) {
            item {
                TypingIndicator()
            }
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
}

/**
 * Typing indicator
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = Color(0xFFF5F5F5),
                shape = MaterialTheme.shapes.medium
            )
            .padding(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = Color(0xFF999999),
                            shape = MaterialTheme.shapes.extraLarge
                        )
                )
            }
        }
    }
}

/**
 * Chat input
 */
@Composable
fun ChatInput(
    onSendMessage: (String) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText
                    onTypingChanged(newText.isNotEmpty())
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                maxLines = 4,
                shape = MaterialTheme.shapes.large
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
                    tint = if (text.isNotBlank()) Color(0xFF0100EC) else Color.Gray
                )
            }
        }
    }
}
