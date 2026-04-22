package com.conferbot.sdk.ui.compose.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
        Box(
            modifier = Modifier
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
}
