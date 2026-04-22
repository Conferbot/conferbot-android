package com.conferbot.sdk.ui.compose.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient

/**
 * Error message banner with themed styling.
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
 * Connection status banner — "Reconnecting..."
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
 * Offline status banner — shown when device has no network.
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
 * Syncing queue banner — shown when queued messages are being sent.
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
