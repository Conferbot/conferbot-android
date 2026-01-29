package com.conferbot.sdk.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.conferbot.sdk.ui.theme.ConferbotTheme
import com.conferbot.sdk.ui.theme.ConferbotThemeProvider
import com.conferbot.sdk.ui.theme.DarkTheme
import com.conferbot.sdk.ui.theme.LightTheme

/**
 * Composable chat view that can be embedded in any Compose screen
 *
 * This is the main entry point for the chat UI with theming support.
 *
 * @param modifier Modifier for the composable
 * @param lightTheme Custom light theme (defaults to SDK light theme)
 * @param darkTheme Custom dark theme (defaults to SDK dark theme)
 * @param useDarkTheme Whether to use dark theme. If null, follows system settings
 * @param onMessageSent Callback when user sends a message
 * @param onMessageReceived Callback when a message is received
 * @param onAgentJoined Callback when an agent joins the chat
 */
@Composable
fun ConferBotChatView(
    modifier: Modifier = Modifier,
    lightTheme: ConferbotTheme = LightTheme,
    darkTheme: ConferbotTheme = DarkTheme,
    useDarkTheme: Boolean? = null,
    onMessageSent: ((String) -> Unit)? = null,
    onMessageReceived: ((String) -> Unit)? = null,
    onAgentJoined: ((String) -> Unit)? = null
) {
    ConferbotThemeProvider(
        lightTheme = lightTheme,
        darkTheme = darkTheme,
        useDarkTheme = useDarkTheme
    ) {
        ConferBotChatScreen(
            onDismiss = {},
            modifier = modifier
        )
    }
}

/**
 * Composable chat view with a single custom theme
 *
 * Use this when you want to provide a single theme regardless of system settings.
 *
 * @param theme The theme to use
 * @param modifier Modifier for the composable
 * @param onMessageSent Callback when user sends a message
 * @param onMessageReceived Callback when a message is received
 * @param onAgentJoined Callback when an agent joins the chat
 */
@Composable
fun ConferBotChatView(
    theme: ConferbotTheme,
    modifier: Modifier = Modifier,
    onMessageSent: ((String) -> Unit)? = null,
    onMessageReceived: ((String) -> Unit)? = null,
    onAgentJoined: ((String) -> Unit)? = null
) {
    ConferbotThemeProvider(theme = theme) {
        ConferBotChatScreen(
            onDismiss = {},
            modifier = modifier
        )
    }
}

/**
 * Themed chat screen with dismiss callback
 *
 * Use this when the chat is presented as a modal or standalone screen.
 *
 * @param onDismiss Callback when the chat should be dismissed
 * @param lightTheme Custom light theme
 * @param darkTheme Custom dark theme
 * @param useDarkTheme Whether to use dark theme. If null, follows system settings
 * @param modifier Modifier for the composable
 */
@Composable
fun ConferBotThemedChatScreen(
    onDismiss: () -> Unit,
    lightTheme: ConferbotTheme = LightTheme,
    darkTheme: ConferbotTheme = DarkTheme,
    useDarkTheme: Boolean? = null,
    modifier: Modifier = Modifier
) {
    ConferbotThemeProvider(
        lightTheme = lightTheme,
        darkTheme = darkTheme,
        useDarkTheme = useDarkTheme
    ) {
        ConferBotChatScreen(
            onDismiss = onDismiss,
            modifier = modifier
        )
    }
}
