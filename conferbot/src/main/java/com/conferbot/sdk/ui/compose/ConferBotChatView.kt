package com.conferbot.sdk.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Composable chat view that can be embedded in any Compose screen
 */
@Composable
fun ConferBotChatView(
    modifier: Modifier = Modifier,
    onMessageSent: ((String) -> Unit)? = null,
    onMessageReceived: ((String) -> Unit)? = null,
    onAgentJoined: ((String) -> Unit)? = null
) {
    ConferBotChatScreen(
        onDismiss = {},
        modifier = modifier
    )
}
