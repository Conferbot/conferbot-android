package com.conferbot.sdk.ui.compose.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient

/**
 * Reusable bot avatar composable.
 *
 * Shows the server-customized avatar image when available,
 * otherwise falls back to a themed circle with the bot's initial letter.
 */
@Composable
fun BotAvatar(modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    val spacing = theme.spacing
    val colors = theme.colors
    val typography = theme.typography
    val serverCustomization by Conferbot.serverCustomization.collectAsState()
    val avatarUrl = serverCustomization?.avatarUrl
    val botName = serverCustomization?.botName ?: "B"
    val avatarSize = spacing.avatarSize

    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Bot avatar",
            modifier = modifier
                .size(avatarSize)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(colors.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = botName.first().uppercase(),
                color = colors.onPrimary,
                style = TextStyle(
                    fontFamily = typography.fontFamily,
                    fontSize = typography.captionSize,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}
