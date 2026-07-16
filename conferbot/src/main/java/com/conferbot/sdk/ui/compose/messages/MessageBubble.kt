@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.conferbot.sdk.ui.compose.messages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.models.RecordItem
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient
import java.text.SimpleDateFormat
import java.util.*

/**
 * Top-level dispatcher — renders the correct bubble for any RecordItem variant.
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
    } else 0

    when (message) {
        is RecordItem.UserMessage -> UserMessageBubble(message, modifier, animationDuration)
        is RecordItem.UserInputResponse -> UserMessageBubble(
            RecordItem.UserMessage(id = message.id, time = message.time, text = message.text),
            modifier, animationDuration
        )
        is RecordItem.UserLiveMessage -> UserMessageBubble(
            RecordItem.UserMessage(id = message.id, time = message.time, text = message.text),
            modifier, animationDuration
        )
        is RecordItem.BotMessage -> BotMessageBubble(message, modifier, animationDuration)
        is RecordItem.AgentMessage -> AgentMessageBubble(message, modifier, animationDuration)
        is RecordItem.SystemMessage -> SystemMessageBubble(message, modifier, animationDuration)
        is RecordItem.AgentJoinedMessage -> SystemMessageBubble(
            RecordItem.SystemMessage(
                id = message.id,
                time = message.time,
                text = "${message.agentDetails.name} joined the chat"
            ),
            modifier, animationDuration
        )
        else -> {}
    }
}

// ─── User bubble ────────────────────────────────────────────────────
// Web widget: border-radius 1.15rem 1.15rem 0 1.15rem  (rounded except bottom-right)

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

    // Matches web widget: all corners rounded except bottom-right (user's side)
    val bubbleShape = remember(shapes.bubbleRadius) {
        RoundedCornerShape(
            topStart = shapes.bubbleRadius,
            topEnd = shapes.bubbleRadius,
            bottomStart = shapes.bubbleRadius,
            bottomEnd = 0.dp
        )
    }
    val dur = if (animationDuration > 0) 300 else 0

    AnimatedVisibility(
        visible = true,
        enter = if (dur > 0) {
            fadeIn(tween(dur, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it / 4 }
        } else fadeIn(tween(0))
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = spacing.maxBubbleWidth)
                    .shadow(1.dp, bubbleShape, clip = false)
                    .background(colors.userBubble, bubbleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = message.text,
                    color = colors.userBubbleText,
                    style = TextStyle(
                        fontFamily = typography.fontFamily,
                        fontSize = typography.messageSize,
                        lineHeight = typography.messageSize * 1.4f
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTime(message.time),
                    color = colors.userBubbleText.copy(alpha = 0.6f),
                    style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.timestampSize)
                )
            }
        }
    }
}

// ─── Bot bubble ─────────────────────────────────────────────────────
// Web widget: border-radius 1.15rem 0.8rem 0.8rem 0  (rounded except bottom-left)

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

    // Matches web widget: rounded except bottom-left (bot's side)
    val bubbleShape = remember(shapes.bubbleRadius) {
        RoundedCornerShape(
            topStart = shapes.bubbleRadius,
            topEnd = shapes.bubbleRadius,
            bottomStart = 0.dp,
            bottomEnd = shapes.bubbleRadius
        )
    }
    val dur = if (animationDuration > 0) 300 else 0

    AnimatedVisibility(
        visible = true,
        enter = if (dur > 0) {
            fadeIn(tween(dur, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 4 }
        } else fadeIn(tween(0))
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            if (!message.text.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Bottom
                ) {
                    BotAvatar()
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .widthIn(max = spacing.maxBubbleWidth)
                            .shadow(1.dp, bubbleShape, clip = false)
                            .background(colors.botBubble, bubbleShape)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = message.text,
                            color = colors.botBubbleText,
                            style = TextStyle(
                                fontFamily = typography.fontFamily,
                                fontSize = typography.messageSize,
                                lineHeight = typography.messageSize * 1.4f
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatTime(message.time),
                            color = colors.botBubbleText.copy(alpha = 0.6f),
                            style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.timestampSize)
                        )
                    }
                }
            }
            // Answered choice nodes keep their chips visible but disabled,
            // with the selection highlighted (web widget behavior).
            @Suppress("UNCHECKED_CAST")
            val persistedChoices = message.nodeData?.get("choices") as? List<String>
            if (!persistedChoices.isNullOrEmpty()) {
                val selected = message.nodeData?.get("selected")?.toString()
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(50.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        persistedChoices.forEach { choiceText ->
                            val isSelected = choiceText == selected
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) colors.primary
                                        else colors.optionBubble.copy(alpha = 0.55f),
                                contentColor = if (isSelected) Color.White
                                        else colors.optionBubbleText.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = choiceText,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = TextStyle(
                                        fontFamily = typography.fontFamily,
                                        fontSize = typography.messageSize * 0.9f
                                    )
                                )
                            }
                        }
                    }
                }
            }
            // Web widget parity: media renders as its own block below the
            // text bubble, indented past the avatar, full bubble width.
            message.imageUrl?.let { url ->
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (message.text.isNullOrBlank()) {
                        BotAvatar()
                        Spacer(Modifier.width(10.dp))
                    } else {
                        Spacer(Modifier.width(50.dp))
                    }
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(
                            androidx.compose.ui.platform.LocalContext.current
                        ).data(url).crossfade(true).build(),
                        contentDescription = message.text,
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                        modifier = Modifier
                            .width(spacing.maxBubbleWidth * 0.75f)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }
    }
}

// ─── Agent bubble ───────────────────────────────────────────────────

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

    // Same shape as bot bubble — rounded except bottom-left
    val bubbleShape = remember(shapes.bubbleRadius) {
        RoundedCornerShape(
            topStart = shapes.bubbleRadius,
            topEnd = shapes.bubbleRadius,
            bottomStart = 0.dp,
            bottomEnd = shapes.bubbleRadius
        )
    }
    val dur = if (animationDuration > 0) 300 else 0

    AnimatedVisibility(
        visible = true,
        enter = if (dur > 0) {
            fadeIn(tween(dur, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 4 }
        } else fadeIn(tween(0))
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            BotAvatar()
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .widthIn(max = spacing.maxBubbleWidth)
                    .shadow(1.dp, bubbleShape, clip = false)
                    .background(colors.agentBubble, bubbleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
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
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = message.text,
                    color = colors.agentBubbleText,
                    style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.messageSize)
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = formatTime(message.time),
                    color = colors.botBubbleText.copy(alpha = 0.7f),
                    style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.timestampSize)
                )
            }
        }
    }
}

// ─── System bubble ──────────────────────────────────────────────────

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
        enter = if (animationDuration > 0) fadeIn(tween(animationDuration)) else fadeIn(tween(0))
    ) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.text,
                color = colors.systemMessageText,
                style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.captionSize),
                modifier = Modifier.padding(spacing.sm)
            )
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────────

private fun formatTime(date: Date): String {
    val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return format.format(date)
}
