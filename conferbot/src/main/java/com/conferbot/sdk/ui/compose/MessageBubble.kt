package com.conferbot.sdk.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.models.RecordItem
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient
import java.text.SimpleDateFormat
import java.util.*

/**
 * Message bubble composable
 *
 * Uses the current Conferbot theme for colors, typography, shapes, and animations.
 */
@Composable
fun MessageBubble(
    message: RecordItem,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val theme = ConferbotThemeAmbient.current
    val animationDuration = if (animate && theme.animations.enabled) {
        theme.animations.messageFadeDuration
    } else {
        0
    }

    when (message) {
        is RecordItem.UserMessage -> UserMessageBubble(
            message = message,
            modifier = modifier,
            animationDuration = animationDuration
        )
        is RecordItem.BotMessage -> BotMessageBubble(
            message = message,
            modifier = modifier,
            animationDuration = animationDuration
        )
        is RecordItem.AgentMessage -> AgentMessageBubble(
            message = message,
            modifier = modifier,
            animationDuration = animationDuration
        )
        is RecordItem.SystemMessage -> SystemMessageBubble(
            message = message,
            modifier = modifier,
            animationDuration = animationDuration
        )
        is RecordItem.AgentJoinedMessage -> SystemMessageBubble(
            message = RecordItem.SystemMessage(
                id = message.id,
                time = message.time,
                text = "${message.agentDetails.name} joined the chat"
            ),
            modifier = modifier,
            animationDuration = animationDuration
        )
        else -> {}
    }
}

/**
 * User message bubble
 *
 * Displays messages sent by the user with themed styling.
 */
@Composable
fun UserMessageBubble(
    message: RecordItem.UserMessage,
    modifier: Modifier = Modifier,
    animationDuration: Int = 0
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val shapes = theme.shapes
    val spacing = theme.spacing
    val typography = theme.typography

    val bubbleShape = remember(shapes.bubbleRadius, shapes.bubbleRadiusSmall) {
        RoundedCornerShape(
            topStart = shapes.bubbleRadius,
            topEnd = shapes.bubbleRadius,
            bottomStart = shapes.bubbleRadius,
            bottomEnd = shapes.bubbleRadiusSmall
        )
    }

    AnimatedVisibility(
        visible = true,
        enter = if (animationDuration > 0) {
            fadeIn(animationSpec = tween(animationDuration)) +
                    slideInHorizontally(
                        animationSpec = tween(animationDuration),
                        initialOffsetX = { it / 4 }
                    )
        } else fadeIn(tween(0))
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = spacing.maxBubbleWidth)
                    .background(
                        color = colors.userBubble,
                        shape = bubbleShape
                    )
                    .padding(
                        horizontal = spacing.bubblePaddingHorizontal,
                        vertical = spacing.bubblePaddingVertical
                    ),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = message.text,
                    color = colors.userBubbleText,
                    style = TextStyle(
                        fontFamily = typography.fontFamily,
                        fontSize = typography.messageSize
                    )
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = formatTime(message.time),
                    color = colors.userBubbleText.copy(alpha = 0.7f),
                    style = TextStyle(
                        fontFamily = typography.fontFamily,
                        fontSize = typography.timestampSize
                    )
                )
            }
        }
    }
}

/**
 * Bot message bubble
 *
 * Displays messages from the bot with themed styling.
 */
@Composable
fun BotMessageBubble(
    message: RecordItem.BotMessage,
    modifier: Modifier = Modifier,
    animationDuration: Int = 0
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val shapes = theme.shapes
    val spacing = theme.spacing
    val typography = theme.typography

    val bubbleShape = remember(shapes.bubbleRadius, shapes.bubbleRadiusSmall) {
        RoundedCornerShape(
            topStart = shapes.bubbleRadiusSmall,
            topEnd = shapes.bubbleRadius,
            bottomStart = shapes.bubbleRadius,
            bottomEnd = shapes.bubbleRadius
        )
    }

    AnimatedVisibility(
        visible = true,
        enter = if (animationDuration > 0) {
            fadeIn(animationSpec = tween(animationDuration)) +
                    slideInHorizontally(
                        animationSpec = tween(animationDuration),
                        initialOffsetX = { -it / 4 }
                    )
        } else fadeIn(tween(0))
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = spacing.maxBubbleWidth)
                    .background(
                        color = colors.botBubble,
                        shape = bubbleShape
                    )
                    .padding(
                        horizontal = spacing.bubblePaddingHorizontal,
                        vertical = spacing.bubblePaddingVertical
                    )
            ) {
                Text(
                    text = message.text ?: "",
                    color = colors.botBubbleText,
                    style = TextStyle(
                        fontFamily = typography.fontFamily,
                        fontSize = typography.messageSize
                    )
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = formatTime(message.time),
                    color = colors.timestamp,
                    style = TextStyle(
                        fontFamily = typography.fontFamily,
                        fontSize = typography.timestampSize
                    )
                )
            }
        }
    }
}

/**
 * Agent message bubble
 *
 * Displays messages from human agents with themed styling.
 */
@Composable
fun AgentMessageBubble(
    message: RecordItem.AgentMessage,
    modifier: Modifier = Modifier,
    animationDuration: Int = 0
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val shapes = theme.shapes
    val spacing = theme.spacing
    val typography = theme.typography

    val bubbleShape = remember(shapes.bubbleRadius, shapes.bubbleRadiusSmall) {
        RoundedCornerShape(
            topStart = shapes.bubbleRadiusSmall,
            topEnd = shapes.bubbleRadius,
            bottomStart = shapes.bubbleRadius,
            bottomEnd = shapes.bubbleRadius
        )
    }

    AnimatedVisibility(
        visible = true,
        enter = if (animationDuration > 0) {
            fadeIn(animationSpec = tween(animationDuration)) +
                    slideInHorizontally(
                        animationSpec = tween(animationDuration),
                        initialOffsetX = { -it / 4 }
                    )
        } else fadeIn(tween(0))
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = spacing.maxBubbleWidth)
                    .background(
                        color = colors.agentBubble,
                        shape = bubbleShape
                    )
                    .padding(
                        horizontal = spacing.bubblePaddingHorizontal,
                        vertical = spacing.bubblePaddingVertical
                    )
            ) {
                Text(
                    text = message.agentDetails.name,
                    color = colors.primary,
                    style = TextStyle(
                        fontFamily = typography.fontFamily,
                        fontSize = typography.captionSize,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = message.text,
                    color = colors.agentBubbleText,
                    style = TextStyle(
                        fontFamily = typography.fontFamily,
                        fontSize = typography.messageSize
                    )
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = formatTime(message.time),
                    color = colors.timestamp,
                    style = TextStyle(
                        fontFamily = typography.fontFamily,
                        fontSize = typography.timestampSize
                    )
                )
            }
        }
    }
}

/**
 * System message bubble
 *
 * Displays system notifications and status messages.
 */
@Composable
fun SystemMessageBubble(
    message: RecordItem.SystemMessage,
    modifier: Modifier = Modifier,
    animationDuration: Int = 0
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val spacing = theme.spacing
    val typography = theme.typography

    AnimatedVisibility(
        visible = true,
        enter = if (animationDuration > 0) {
            fadeIn(animationSpec = tween(animationDuration))
        } else fadeIn(tween(0))
    ) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.text,
                color = colors.systemMessageText,
                style = TextStyle(
                    fontFamily = typography.fontFamily,
                    fontSize = typography.captionSize
                ),
                modifier = Modifier.padding(spacing.sm)
            )
        }
    }
}

/**
 * Format time for display in message bubbles
 */
private fun formatTime(date: Date): String {
    val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return format.format(date)
}
