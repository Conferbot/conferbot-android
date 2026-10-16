package com.conferbot.sdk.ui.compose.messages

import com.conferbot.sdk.core.i18n.I18n
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
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
 * With the bot's settings loaded (core/customization) it is drawn as the web
 * draws it: the avatar, else the logo, else the Conferbot logo; the owner's
 * size and shape (circle, rounded 28%, square 4); nothing when hidden.
 * Before that, the server avatar or a themed circle with the bot's initial.
 */
@Composable
fun BotAvatar(modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    val spacing = theme.spacing
    val colors = theme.colors
    val typography = theme.typography
    val serverCustomization by Conferbot.serverCustomization.collectAsState()
    val look by Conferbot.customizations.collectAsState()
    val map by Conferbot.serverCustomizationMap.collectAsState()
    val loaded = map.isNotEmpty()
    if (loaded && look.hideAvatar) return
    val avatarUrl = if (loaded) look.avatar else serverCustomization?.avatarUrl
    val botName = serverCustomization?.botName ?: "B"
    val avatarSize = if (loaded) look.avatarSize.dp else spacing.avatarSize
    val shape = if (loaded && look.avatarShape != "circle") RoundedCornerShape(look.avatarRadius.dp) else CircleShape

    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = I18n.t("sdk.message.botAvatar"),
            modifier = modifier
                .size(avatarSize)
                .clip(shape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(avatarSize)
                .clip(shape)
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
