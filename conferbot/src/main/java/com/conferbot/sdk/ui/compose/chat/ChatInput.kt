package com.conferbot.sdk.ui.compose.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient

/**
 * Chat input bar matching the web widget style exactly.
 *
 * Web widget CSS reference:
 * - Container: box-shadow 0 -4px 8px -4px rgba(99,99,99,0.2), border-radius 4px 4px 0 0, bg #fff
 * - Input: border none, outline none, padding 8px 5px, font-size 16px, border-radius 25px, bg transparent
 * - Placeholder: color #4d4d4d, font-size 16px
 * - Send button: themed circle with arrow icon
 */
@Composable
fun ChatInput(
    onSendMessage: (String) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors

    var text by remember { mutableStateOf("") }
    val canSend = text.isNotBlank()

    val sendBgColor by animateColorAsState(
        targetValue = if (canSend) colors.headerBackground else colors.headerBackground.copy(alpha = 0.3f),
        animationSpec = tween(200),
        label = "send_bg"
    )

    // Input container with upward shadow like web widget
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Input field — transparent, no border, matching web widget
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 120.dp)
                    .padding(horizontal = 5.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        "Type a message...",
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = Color(0xFF4D4D4D) // #4d4d4d from web widget
                        )
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { newText ->
                        text = newText
                        onTypingChanged(newText.isNotEmpty())
                    },
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = Color.Black, // #000000 from web widget
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Send button — themed circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(sendBgColor),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        if (canSend) {
                            onSendMessage(text.trim())
                            text = ""
                            onTypingChanged(false)
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
