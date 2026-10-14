package com.conferbot.sdk.ui.compose.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.Conferbot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.ui.compose.messages.BotAvatar
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient

/**
 * Typing indicator with animated bouncing dots and bot avatar.
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

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        BotAvatar()
        Spacer(modifier = Modifier.width(spacing.sm))
        // The web's typing bubble: 36 tall, at least 54 wide, the bot's
        // bubble shape and colours; dots (default), wave or shimmer.
        val look by Conferbot.customizations.collectAsState()
        val map by Conferbot.serverCustomizationMap.collectAsState()
        val loaded = map.isNotEmpty()
        val shape = com.conferbot.sdk.ui.compose.messages.botBubbleShape()
        val ink = if (loaded) colors.botBubbleText else colors.typing
        Box(
            modifier = Modifier
                .shadow(1.dp, shape)
                .background(color = colors.botBubble, shape = shape)
                .heightIn(min = 36.dp)
                .widthIn(min = 54.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            when (if (loaded) look.typingStyle else "dots") {
                "shimmer" -> androidx.compose.material3.Text(
                    text = "Typing...",
                    color = ink.copy(alpha = 0.55f + 0.45f * dotAlphas[0].value),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                )
                "wave" -> Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (0..4).forEach { index ->
                        val a = dotAlphas[index % dotAlphas.size].value
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height((16f * (0.3f + 0.7f * a)).dp)
                                .background(ink.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                        )
                    }
                }
                else -> Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dotOffsets.forEachIndexed { index, animatedOffset ->
                        Box(
                            modifier = Modifier
                                .size(if (loaded) 6.dp else 7.dp)
                                .offset(y = animatedOffset.value.dp)
                                .background(
                                    color = ink.copy(alpha = dotAlphas[index].value),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}
