package com.conferbot.sdk.ui.compose.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.PaginationState
import com.conferbot.sdk.models.RecordItem
import com.conferbot.sdk.ui.compose.NodeRenderer
import com.conferbot.sdk.ui.compose.messages.BotAvatar
import com.conferbot.sdk.ui.compose.messages.MessageBubble
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient
import kotlinx.coroutines.launch

/**
 * Paginated message list with inline node interaction.
 *
 * Bot avatar is shown next to every bot-originated item:
 * bot messages, typing indicator, and interactive node prompts.
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

    val hasInlineNode = currentUIState != null && onNodeResponse != null

    // Pagination trigger
    val shouldLoadMore by remember {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            first < 10 && paginationState.hasMoreMessages &&
                    !isLoadingMore && !paginationState.isLoading && messages.isNotEmpty()
        }
    }

    // Show scroll-to-bottom FAB when not near the end
    val showScrollToBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible < total - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
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
                item(key = "loading_more") { LoadingMoreIndicator() }
            }

            // "Load more" hint
            if (paginationState.hasMoreMessages && !isLoadingMore) {
                item(key = "load_more_hint") {
                    LoadMoreHint(
                        totalCount = paginationState.totalMessageCount,
                        loadedCount = messages.size
                    )
                }
            }

            // Messages
            items(items = messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }

            // Typing indicator
            if (isAgentTyping) {
                item(key = "typing_indicator") {
                    TypingIndicator()
                }
            }

            // Inline node — wrapped with bot avatar so it looks like a bot response
            if (hasInlineNode) {
                item(key = "inline_node_interaction") {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                                expandVertically(tween(300, easing = FastOutSlowInEasing))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            BotAvatar()
                            Spacer(Modifier.width(spacing.sm))
                            NodeRenderer(
                                uiState = currentUIState!!,
                                onResponse = onNodeResponse!!,
                                primaryColor = colors.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = spacing.xs)
                            )
                        }
                    }
                }
            }
        }

        // Scroll-to-bottom FAB
        AnimatedVisibility(
            visible = showScrollToBottom,
            enter = fadeIn(tween(animations.scrollDuration)) +
                    slideInVertically(tween(animations.scrollDuration)) { it },
            exit = fadeOut(tween(animations.scrollDuration)) +
                    slideOutVertically(tween(animations.scrollDuration)) { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(spacing.lg)
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                    }
                },
                modifier = Modifier.size(40.dp),
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
    }

    // Auto-scroll to bottom on new messages / node UI change
    LaunchedEffect(messages.size, currentUIState) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0 && !showScrollToBottom) {
            coroutineScope.launch { listState.animateScrollToItem(count - 1) }
        }
    }
}

/**
 * Backwards-compatible wrapper.
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

// ─── Small helpers ──────────────────────────────────────────────────

@Composable
private fun LoadingMoreIndicator(modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = theme.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(theme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = theme.colors.primary
            )
            Text(
                text = "Loading older messages...",
                style = TextStyle(fontFamily = theme.typography.fontFamily, fontSize = theme.typography.captionSize),
                color = theme.colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadMoreHint(totalCount: Int, loadedCount: Int, modifier: Modifier = Modifier) {
    val remaining = totalCount - loadedCount
    if (remaining <= 0) return
    val theme = ConferbotThemeAmbient.current
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = theme.spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Scroll up to load $remaining more messages",
            style = TextStyle(fontFamily = theme.typography.fontFamily, fontSize = theme.typography.captionSize),
            color = theme.colors.onSurfaceVariant
        )
    }
}
