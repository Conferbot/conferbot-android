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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier,
    /** Live chat cards below the messages (leave a message, rating...). */
    footer: (@Composable () -> Unit)? = null,
    /** Changes when those cards change, to keep them in view. */
    footerKey: Any? = null
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val spacing = theme.spacing
    val animations = theme.animations

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // AI agent bots: the reply being written, one-tap questions, the wait.
    val agentStream by com.conferbot.sdk.core.Conferbot.agentStream.collectAsState()
    val suggestions by com.conferbot.sdk.core.Conferbot.suggestions.collectAsState()
    val awaitingReply by com.conferbot.sdk.core.Conferbot.awaitingReply.collectAsState()
    val showTyping = (isAgentTyping || awaitingReply) && agentStream == null

    val receiptMarks by com.conferbot.sdk.core.Conferbot.liveChat.receiptMarks.collectAsState()
    val currentAgent by com.conferbot.sdk.core.Conferbot.currentAgent.collectAsState()
    val receipt = remember(messages, receiptMarks, currentAgent) {
        val person = currentAgent?.let { !it.id.startsWith("ai-agent-") } == true
        if (!person) null
        else com.conferbot.sdk.core.livechat.LiveChat.visitorReceiptOf(
            com.conferbot.sdk.core.livechat.LiveChatExtras.entriesOf(messages), receiptMarks
        )
    }

    // TextInput nodes are answered via the unified bottom bar (web parity),
    // so they render no inline UI and must not reserve an avatar row.
    val hasInlineNode = currentUIState != null && onNodeResponse != null &&
        // A person joined: the chat's own line says so.
        !(currentUIState is NodeUIState.HumanHandover && currentUIState.state == NodeUIState.HumanHandover.HandoverState.AGENT_CONNECTED) &&
        // A step card that takes the typing (OTP, address, signature) is drawn here.
        (currentUIState !is NodeUIState.TextInput || com.conferbot.sdk.ui.compose.steps.hasStepCard(currentUIState)) &&
        currentUIState !is NodeUIState.Message &&
        currentUIState !is NodeUIState.Image &&
        currentUIState !is NodeUIState.Video

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

    Box(
        modifier = modifier
            .fillMaxSize()
            // The keyboard opened (the list got shorter): the latest stays in view.
            .onSizeChanged {
                val count = listState.layoutInfo.totalItemsCount
                if (count > 0 && !showScrollToBottom) coroutineScope.launch { listState.scrollToItem(count - 1) }
            }
    ) {
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

            // Messages, and "Sent / Delivered / Seen" under the visitor's
            // latest live message while a person is on the other end.
            items(items = messages, key = { it.id }) { message ->
                Column {
                MessageBubble(message = message)
                if (receipt?.id == message.id) {
                    Text(
                        text = com.conferbot.sdk.core.livechat.LiveChat.receiptLabel(receipt.state),
                        color = colors.onSurfaceVariant,
                        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 2.dp)
                    )
                }
                }
            }

            // The AI reply as it is written
            agentStream?.let { stream ->
                item(key = "agent_stream") {
                    MessageBubble(
                        message = RecordItem.AgentMessage(id = "stream-${stream.id}", time = java.util.Date(), text = stream.text, agentDetails = stream.agentDetails),
                        animate = false
                    )
                }
            }

            // Typing indicator
            if (showTyping) {
                item(key = "typing_indicator") {
                    TypingIndicator()
                }
            }

            // Starters under the greeting, follow-ups under the latest answer
            if (suggestions.isNotEmpty() && !showTyping && agentStream == null) {
                item(key = "agent_suggestions") {
                    com.conferbot.sdk.ui.compose.messages.SuggestionChips(
                        items = suggestions,
                        onPick = { com.conferbot.sdk.core.Conferbot.sendMessage(it) }
                    )
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
                            val card = com.conferbot.sdk.ui.compose.steps.hasStepCard(currentUIState)
                            // A step card sits under its question's bubble, in the avatar's column.
                            if (card) StepAvatarSpace() else BotAvatar()
                            Spacer(Modifier.width(spacing.sm))
                            if (card) {
                                // The step's own card, under the bot's question; kept in
                                // view as it grows (results, a form, a day's times).
                                Box(Modifier.weight(1f).padding(top = spacing.xs).onSizeChanged {
                                    val count = listState.layoutInfo.totalItemsCount
                                    if (count > 0 && !showScrollToBottom) coroutineScope.launch { listState.animateScrollToItem(count - 1, Int.MAX_VALUE / 2) }
                                }) {
                                    com.conferbot.sdk.ui.compose.steps.FlowStepView(currentUIState!!)
                                }
                            } else NodeRenderer(
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
            footer?.let { cards ->
                item(key = "live_chat_cards") {
                    // A card grew (a rating opened its feedback box): kept in view.
                    Box(Modifier.onSizeChanged {
                        val count = listState.layoutInfo.totalItemsCount
                        if (count > 0 && !showScrollToBottom) coroutineScope.launch { listState.animateScrollToItem(count - 1, Int.MAX_VALUE / 2) }
                    }) { cards() }
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
    // ...and as a reply is written, or suggestions and the dots appear.
    LaunchedEffect(messages.size, currentUIState, agentStream?.text?.length, suggestions.size, showTyping, footerKey) {
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

/** The width the bot's avatar takes (nothing when it is hidden), so a step card lines up with the bubbles. */
@Composable
private fun StepAvatarSpace() {
    val look by com.conferbot.sdk.core.Conferbot.customizations.collectAsState()
    val map by com.conferbot.sdk.core.Conferbot.serverCustomizationMap.collectAsState()
    val loaded = map.isNotEmpty()
    if (loaded && look.hideAvatar) return
    Spacer(Modifier.width(if (loaded) look.avatarSize.dp else ConferbotThemeAmbient.current.spacing.avatarSize))
}
